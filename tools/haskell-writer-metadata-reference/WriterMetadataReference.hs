{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, FlexibleContexts, GeneralizedNewtypeDeriving #-}
module Main where
import Prelude hiding (String)
import qualified Prelude as P
import qualified Data.Map as M
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.ByteString as B
import qualified Data.Text as T
import qualified Data.Vector as V
import Data.Char (chr, ord)
import Data.Foldable (toList)
import Data.Functor.Identity (Identity, runIdentity)
import Data.String (IsString)
import Data.Text.Conversions (FromText(..), ToText(..))
import qualified Text.DocLayout as D
import qualified Text.DocLayout.Attributed as DA
import Text.DocTemplates.Internal
import Text.Pandoc.Definition
import qualified PandocOptionsModels as O
import qualified TemplateAdapter as A
import qualified SourceWriterMetadata as W
import qualified SourceWriterHtml as H
import qualified Text.Pandoc.UTF8 as U
import Control.Monad.State.Strict
import Control.Exception (SomeException, try, evaluate, displayException)
import System.IO (hSetBuffering, stdout, BufferMode(LineBuffering))

-- Independently chosen dictionaries distinguish FromText, IsString and ToText.
newtype Wrapped = Wrapped T.Text deriving (Show, Semigroup, Monoid, IsString, D.HasChars)
instance FromText Wrapped where fromText t = Wrapped ("!" <> t)
instance ToText Wrapped where toText (Wrapped t) = "?" <> t
unwrap (Wrapped t) = t
as :: FromJSON a => Value -> a
as v = case fromJSON v of Success x -> x; Error e -> error e
arr = A.arr
j :: ToJSON a => a -> Value
j = toJSON
str :: T.Text -> Value
str = j

doc :: D.HasChars a => (T.Text -> a) -> ([Int] -> a) -> Value -> D.Doc a
doc raw ps v = case v of
 Array a | V.length a == 3 && a V.! 0 == String "point-text" -> D.Text(as(a V.! 1))(ps(as(a V.! 2)))
 _ -> fmap raw (A.doc v)
val :: D.HasChars a => (T.Text -> a) -> ([Int] -> a) -> Value -> Val a
val raw ps v = case arr v of
 [String "simple",d] -> SimpleVal(doc raw ps d)
 [String "bool",b] -> BoolVal(as b)
 [String "null"] -> NullVal
 [String "list",xs] -> ListVal(map (val raw ps)(arr xs))
 [String "map",xs] -> MapVal(Context(M.fromList[ (as k,val raw ps x) | pair<-arr xs,let [k,x]=arr pair]))
 _ -> error "value"
context raw ps v = case val raw ps v of MapVal c -> c; _ -> error "context"
encAttributed f (DA.Attributed xs) = j[j[j l,A.encFont ft,f a] | DA.Attr l ft a <- toList xs]
encDoc f d = case d of
 D.Text w x -> j[str "text",j w,f x]
 D.Block w xs -> j[str "block",j w,j(map(encAttributed f)xs)]
 D.VFill w x -> j[str "vfill",j w,f x]
 D.CookedText w x -> j[str "cooked",j w,encAttributed f x]
 D.Prefixed p x -> j[str "prefix",j p,encDoc f x]
 D.BeforeNonBlank x -> j[str "before",encDoc f x]
 D.Flush x -> j[str "flush",encDoc f x]
 D.BreakingSpace -> j[str "space"]
 D.AfterBreak x -> j[str "after",j x]
 D.CarriageReturn -> j[str "cr"]
 D.NewLine -> j[str "newline"]
 D.BlankLines n -> j[str "blanklines",j n]
 D.Concat x y -> j[str "concat",encDoc f x,encDoc f y]
 D.Styled r x -> j[str "styled",A.encStyle r,encDoc f x]
 D.Linked l x -> j[str "link",j l,encDoc f x]
 D.Empty -> j[str "empty"]
encVal f v = case v of
 NullVal -> j[str "null"]
 BoolVal b -> j[str "bool",j b]
 SimpleVal d -> j[str "simple",encDoc f d]
 ListVal xs -> j[str "list",j(map(encVal f)xs)]
 MapVal(Context m) -> j[str "map",j[j[j k,encVal f x] | (k,x)<-M.toList m]]
observeContext f c@(Context m) = object["context" .= encVal f(MapVal c),"json" .= toJSON c,"jsonBytes" .= U.toText(L.toStrict(encode(toJSON c))),"keys" .= M.keys m]

options enabled vars = O.WriterOptions{O.writerTemplate=if enabled then Just mempty else Nothing,O.writerVariables=vars}

run :: forall a. (TemplateTarget a, FromContext a a, ToContext a a, ToContext a Bool, ToContext a Value) => (T.Text -> a) -> ([Int] -> a) -> (a -> Value) -> T.Text -> Value -> Value
run raw ps enc op v = case op of
 "metadata" -> case arr v of
  [enabled,vars,m,bd,idoc] ->
   let opts=options(as enabled)(context id (T.pack.map chr)vars)
       meta=as m :: Meta
       bw xs=do trace<-get;modify(<>[j[str "blocks",j xs]])
                pure(D.literal(raw("B"<>T.pack(show(length trace))))<>doc raw ps bd)
       iw xs=do trace<-get;modify(<>[j[str "inlines",j xs]])
                pure(D.literal(raw("I"<>T.pack(show(length trace))))<>doc raw ps idoc)
       (c,trace)=runState(W.metaToContext opts bw iw meta)[]
       (c',trace')=runState(W.metaToContext' bw iw meta)[]
   in object["normal" .= observeContext enc c,"prime" .= observeContext enc c',"trace" .= trace,"primeTrace" .= trace',"lang" .= W.getLang opts meta]
  _ -> error "metadata"
 "add" -> case arr v of
  [vars,c] -> observeContext enc(W.addVariablesToContext(options False(context id (T.pack.map chr)vars))(context raw ps c))
  _ -> error "add"
 "field" -> case arr v of
  [mode,c,key,x] ->
   let original=context raw ps c;field=as key
       apply :: forall b. ToContext a b => b -> Context a
       apply value = case as mode :: T.Text of
         "set" -> W.setField field value original
         "reset" -> W.resetField field value original
         "default" -> W.defField field value original
         _ -> error "field mode"
       changed = case arr x of
        [String "val",value] -> apply(val raw ps value)
        [String "target",value] -> apply(raw(as value))
        [String "doc",value] -> apply(doc raw ps value)
        [String "bool",value] -> apply(as value :: Bool)
        [String "list",value] -> apply(map(val raw ps)(arr value))
        [String "map",value] -> apply(case context raw ps value of Context m -> m)
        [String "json",value] -> apply value
        _ -> error "conversion"
   in object["changed" .= observeContext enc changed,
     "target" .= fmap enc(W.getField field changed :: Maybe a),
     "targets" .= fmap (map enc)(W.getField field changed :: Maybe [a]),
     "document" .= fmap(encDoc enc)(W.getField field changed :: Maybe(D.Doc a)),
     "value" .= fmap(encVal enc)(W.getField field changed :: Maybe(Val a))]
  _ -> error "field"
 "html-dictionary" -> case arr v of
  [tag,attr] -> let a=as attr;d=H.tagWithAttrs(raw(as tag))a
               in object["tag" .= encDoc enc d,"attrs" .= encDoc enc(H.htmlAttrs a::D.Doc a),"render" .= enc(D.render Nothing d)]
  _ -> error "HTML dictionary"
 "unicode" ->
  let cs=map chr(as v);t=T.pack cs
      c=Context(M.fromList[(t,SimpleVal(D.literal(ps(as v))))])
      result=W.addVariablesToContext(options False mempty)c
  in case W.getField "meta-json" result :: Maybe(D.Doc a) of
      Just d -> object["doc" .= encDoc enc d,"render" .= enc(D.render Nothing d)]
      _ -> error "meta-json"
 "deep" ->
  let n=as v :: Int
      meta=Meta(M.singleton "root"(foldr (\_ x -> MetaMap(M.singleton "child" x))(MetaString "leaf")[1..n]))
      c=runIdentity(W.metaToContext' (const(pure mempty)) (const(pure(D.literal(raw "leaf"))))meta)
      result=W.addVariablesToContext(options False mempty)c
  in case W.getField "meta-json" result :: Maybe(D.Doc a) of Just d -> enc(D.render Nothing d);_ -> error "meta-json"
 _ -> error "operation"

lookupMetadata v = case arr v of
 [key,m] -> let k=as key;meta=as m in object["bool" .= W.lookupMetaBool k meta,"blocks" .= W.lookupMetaBlocks k meta,"inlines" .= W.lookupMetaInlines k meta,"string" .= W.lookupMetaString k meta]
 _ -> error "lookup"
partial v = case as v :: T.Text of
 "default-present" -> let c=Context(M.singleton "key"(BoolVal False))::Context T.Text in encVal j(MapVal(W.defField "key" (error "new value"::Val T.Text)c))
 "missing-field" -> j(W.getField "missing" (Context(M.singleton "key"(error "old value"::Val T.Text))) :: Maybe(Val T.Text))
 "no-template" -> let { o=O.WriterOptions{O.writerTemplate=Nothing,O.writerVariables=error "variables"};
                        c=runIdentity(W.metaToContext o (\_->error "blocks") (\_->error "inlines") (error "metadata")::Identity(Context T.Text)) }
                   in encVal j(MapVal c)
 _ -> error "partial"
process (op,kind,v)
 | op=="lookup" = lookupMetadata v
 | op=="partial" = partial v
 | otherwise = case kind :: T.Text of
     "text" -> run id (T.pack.map chr) j op v
     "string" -> run T.unpack (map chr) (j.map ord) op v
     "wrapped" -> run Wrapped (Wrapped . T.pack . map chr) (j.unwrap) op v
     _ -> error "target"
observe x = do
 r<-try(evaluate(L.toStrict(encode x)))::IO(Either SomeException B.ByteString)
 pure$case r of Left e->object["error" .= takeWhile(/='\n')(displayException e)];Right b->either error id(eitherDecode(L.fromStrict b))
main = do
 hSetBuffering stdout LineBuffering
 L.getContents >>= mapM_ (\line -> case eitherDecode line of Right req -> observe(process req) >>= L.putStrLn.encode; Left e -> error e) . L.lines
