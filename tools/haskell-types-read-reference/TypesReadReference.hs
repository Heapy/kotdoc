{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
-- Actual original Read dictionaries; pandoc-types BSD3 and GHC-base BSD3 licenses/.
import Text.Pandoc.Definition
import qualified Text.Pandoc.Builder as P
import OriginalExamples (examples)
import Text.Read (Read(..),readPrec_to_S)
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Vector as V
import qualified Data.Map as M
import qualified Data.Text as T
import Data.Char (chr,ord)
import Data.Word (Word64)
import GHC.Float (castDoubleToWord64)
import Data.Data (Data,dataTypeOf,dataTypeConstrs,showConstr,constrFields,Constr,gunfold)
import Control.Exception (SomeException,try,evaluate,displayException)
import System.IO (stdout,hSetBuffering,BufferMode(LineBuffering))

as :: FromJSON a => Value -> a
as v = case fromJSON v of Success x -> x; Error e -> error e
field name (Object o) = maybe (error "missing field") id (K.lookup name o)
record :: Show a => (a -> [Word64]) -> (a -> Value) -> (a,String) -> Value
record bits enc (v,rest) = object ["value" .= enc v,"shown" .= show v,"remainder" .= map ord rest,"ieeeBits" .= map show (bits v)]
lean (Object o) = K.lookup "lean" o == Just (Bool True)
observe :: forall a. (Read a,Show a) => (a -> Value) -> a -> Value -> Value
observe = observeWithBits (const [])
observeWithBits :: forall a. (Read a,Show a) => (a -> [Word64]) -> (a -> Value) -> a -> Value -> Value
observeWithBits bits enc _ req = let { p=as(field "precedence" req); s=map chr (as(field "input" req)); op=as(field "operation" req) :: String;
                         encodeValue=if lean req then const Null else enc } in case op of
  "readsPrec" -> toJSON $ map (record bits encodeValue) (readsPrec p s :: [(a,String)])
  "readPrec" -> toJSON $ map (record bits encodeValue) (readPrec_to_S readPrec p s :: [(a,String)])
  "readListPrec" -> toJSON $ map (record (concatMap bits) (toJSON . map encodeValue)) (readPrec_to_S readListPrec p s :: [([a],String)])
  "readList" -> toJSON $ map (record (concatMap bits) (toJSON . map encodeValue)) (readList s :: [([a],String)])
  "read" -> object ["value" .= encodeValue (read s :: a),"shown" .= show (read s :: a),"ieeeBits" .= map show (bits (read s :: a))]
  _ -> error op
operation req = case as(field "kind" req) :: String of
 "pandoc" -> observe toJSON (undefined::Pandoc) req
 "meta" -> observe toJSON (undefined::Meta) req
 "metaValue" -> observe toJSON (undefined::MetaValue) req
 "inline" -> observe toJSON (undefined::Inline) req
 "block" -> observe toJSON (undefined::Block) req
 "style" -> observe toJSON (undefined::ListNumberStyle) req
 "delim" -> observe toJSON (undefined::ListNumberDelim) req
 "format" -> observe toJSON (undefined::Format) req
 "rowHeadColumns" -> observe toJSON (undefined::RowHeadColumns) req
 "alignment" -> observe toJSON (undefined::Alignment) req
 "colWidth" -> observeWithBits (\w -> case w of ColWidth d -> [castDoubleToWord64 d]; ColWidthDefault -> []) toJSON (undefined::ColWidth) req
 "row" -> observe toJSON (undefined::Row) req
 "head" -> observe toJSON (undefined::TableHead) req
 "body" -> observe toJSON (undefined::TableBody) req
 "foot" -> observe toJSON (undefined::TableFoot) req
 "caption" -> observe toJSON (undefined::Caption) req
 "cell" -> observe toJSON (undefined::Cell) req
 "rowSpan" -> observe toJSON (undefined::RowSpan) req
 "colSpan" -> observe toJSON (undefined::ColSpan) req
 "quote" -> observe toJSON (undefined::QuoteType) req
 "math" -> observe toJSON (undefined::MathType) req
 "citation" -> observe toJSON (undefined::Citation) req
 "mode" -> observe toJSON (undefined::CitationMode) req
 "inlines" -> observe (toJSON . P.toList) (undefined::P.Inlines) req
 "blocks" -> observe (toJSON . P.toList) (undefined::P.Blocks) req
 "attr" -> observe toJSON (undefined::Attr) req
 "target" -> observe toJSON (undefined::Target) req
 "listAttributes" -> observe toJSON (undefined::ListAttributes) req
 "colSpec" -> observe toJSON (undefined::ColSpec) req
 "shortCaption" -> observe toJSON (undefined::ShortCaption) req
 "text" -> observe toJSON (undefined::T.Text) req
 "int" -> observe toJSON (undefined::Int) req
 "double" -> observeWithBits (\d -> [castDoubleToWord64 d]) toJSON (undefined::Double) req
 "bool" -> observe toJSON (undefined::Bool) req
 "char" -> observe (toJSON . ord) (undefined::Char) req
 _ -> error "unknown kind"
example kind x = object ["kind" .= (kind::String),"input" .= show x]
extra = [example "pandoc" (Pandoc nullMeta []),example "inline" (Math InlineMath "x"),example "block" HorizontalRule,
 example "format" (Format "HTML"),example "style" DefaultStyle,example "style" Example,example "style" Decimal,
 example "style" LowerRoman,example "style" UpperRoman,example "style" LowerAlpha,example "style" UpperAlpha,
 example "delim" DefaultDelim,example "delim" Period,example "delim" OneParen,example "delim" TwoParens,
 example "alignment" AlignLeft,example "alignment" AlignRight,example "alignment" AlignCenter,example "alignment" AlignDefault,
 example "colWidth" ColWidthDefault,example "colWidth" (ColWidth 0.3),example "attr" (("A",["B"],[("k","v")])::Attr),
 example "target" (("url","title")::Target),example "listAttributes" ((-3,Example,TwoParens)::ListAttributes),
 example "colSpec" ((AlignLeft,ColWidth 0.3)::ColSpec),example "shortCaption" ([Space,Str "x"]::ShortCaption),
 example "inlines" (P.fromList [Space,Str "x"]::P.Inlines),example "blocks" (P.fromList [HorizontalRule]::P.Blocks)]
newtype Arity a = Arity Int
constructorArity :: forall a. Data a => a -> Constr -> Int
constructorArity _ c = let Arity n = gunfold (\(Arity n) -> Arity (n+1)) (\_ -> Arity 0) c :: Arity a in n
census :: Data a => String -> a -> Value
census name x = object ["kind" .= name,"constructors" .= [object ["name" .= showConstr c,"fields" .= constrFields c,"arity" .= constructorArity x c] | c<-dataTypeConstrs(dataTypeOf x)]]
dictionaries = [census "pandoc" (undefined::Pandoc),census "meta" (undefined::Meta),census "metaValue" (undefined::MetaValue),
 census "inline" (undefined::Inline),census "block" (undefined::Block),census "style" (undefined::ListNumberStyle),
 census "delim" (undefined::ListNumberDelim),census "format" (undefined::Format),census "rowHeadColumns" (undefined::RowHeadColumns),
 census "alignment" (undefined::Alignment),census "colWidth" (undefined::ColWidth),census "row" (undefined::Row),
 census "head" (undefined::TableHead),census "body" (undefined::TableBody),census "foot" (undefined::TableFoot),
 census "caption" (undefined::Caption),census "cell" (undefined::Cell),census "rowSpan" (undefined::RowSpan),
 census "colSpan" (undefined::ColSpan),census "quote" (undefined::QuoteType),census "math" (undefined::MathType),
 census "citation" (undefined::Citation),census "mode" (undefined::CitationMode),
 census "inlines" (undefined::P.Inlines),census "blocks" (undefined::P.Blocks)]
main = do
 hSetBuffering stdout LineBuffering
 B.getContents >>= mapM_ one . B.lines
 where one line | B.null line = pure ()
                | otherwise = case eitherDecode line of
                   Left e -> error e
                   Right (String "census") -> B.putStrLn $ encode dictionaries
                   Right (String "examples") -> mapM_ (B.putStrLn . encode) $
                     [ object ["kind" .= (req V.! 1),"input" .= field "expected" v]
                       | v<-examples,let req=case field "request" v of Array a->a,as(req V.! 2)==(0::Int)] ++ extra
                   Right req -> do
                    result<-try(evaluate(let b=encode(operation req) in B.length b `seq` b)) :: IO (Either SomeException B.ByteString)
                    B.putStrLn $ either (encode . object . (:[]) . ("exception" .=) . takeWhile (/='\n') . displayException) id result
