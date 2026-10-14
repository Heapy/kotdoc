{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import Data.Aeson
import qualified Data.Aeson.Key as K
import qualified Data.Aeson.KeyMap as KM
import qualified Data.Aeson.Text as AT
import qualified Data.Aeson.Encode.Pretty as AP
import qualified Data.Scientific as S
import qualified Data.Text as T
import qualified Data.Text.Lazy as TL
import qualified Data.Text.Lazy.Builder as TB
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import qualified Data.Vector as V
import Data.Char (chr,ord)
import Data.Word (Word64)
import Data.Int (Int64)
import GHC.Float (castWord64ToDouble)
import Text.Pandoc.Definition
import Text.Pandoc.Logging
import Text.Parsec.Pos
import Control.Exception (try,SomeException,evaluate,displayException)
import System.IO (stdout,hFlush,isEOF)
as :: FromJSON a => Value -> a
as v = case fromJSON v of Success x -> x; Error e -> error e
arr (Array v) = V.toList v
arr _ = error "array"
str (String s) = T.unpack s
str _ = error "string"
chars v = map (chr . as) (arr v)
txt = T.pack . chars
position v = case arr v of [n,l,c] -> newPos (chars n) (as l) (as c); _ -> error "position"
optional Null = Nothing
optional v = Just (position v)
project :: Value -> Value
project x = case x of
 Null -> toJSON [String "z"]
 Bool b -> toJSON [String "b",Bool b]
 String s -> toJSON [String "s",String s]
 Number n -> toJSON [String "n",toJSON (show (S.coefficient n)),toJSON (S.base10Exponent n)]
 Array a -> toJSON [String "a",toJSON (map project (V.toList a))]
 Object o -> toJSON [String "o",toJSON [toJSON [String (K.toText k),project v] | (k,v) <- KM.toList o]]
messageFields p m = object ["verbosity" .= show (messageVerbosity m),"pretty" .= showLogMessage m,
 "shown" .= T.pack (showsPrec p m ""),"value" .= project (toJSON m),"encoded" .= BL.unpack (encodeLogMessages [m])]
relation a b = object ["eq" .= (a==b),"ne" .= (a/=b),"compare" .= show (compare a b),
 "lt" .= (a<b),"le" .= (a<=b),"gt" .= (a>b),"ge" .= (a>=b),
 "minimum" .= T.pack (show (min a b)),"maximum" .= T.pack (show (max a b))]
table d = Table nullAttr (Caption Nothing []) [(AlignDefault,ColWidth d)] (TableHead nullAttr []) [] (TableFoot nullAttr [])
width kind bits = let b=table (castWord64ToDouble (read (str bits) :: Word64)) in if kind=="block" then BlockNotRendered b else InlineNotRendered (Note [b])
posValue p = object ["name" .= map ord (sourceName p),"line" .= sourceLine p,"column" .= sourceColumn p,"shown" .= map ord (show p)]
response x = case arr x of
 [String "message",m,p] -> pure (messageFields (as p) (message m))
 [String "array",ms] -> pure (toJSON (BL.unpack (encodeLogMessages (map message (arr ms)))))
 [String "relations",a,b] -> pure (relation (message a) (message b))
 [String "rawShow",m,p,n] -> pure (toJSON (map ord (take (as n) (showsPrec (as p) (message m) ""))))
 [String "width",String kind,a,b] -> pure (object ["left" .= messageFields 0 (width kind a),"right" .= messageFields 0 (width kind b),"relations" .= relation (width kind a) (width kind b)])
 [String "ast",String kind,v] -> pure (project (case kind of "pandoc" -> toJSON (as v :: Pandoc); "inline" -> toJSON (as v :: Inline); "block" -> toJSON (as v :: Block); "meta" -> toJSON (as v :: Meta); _ -> error "AST kind"))
 [String "position",a,b,ps,dl,dc,n] -> let p=position a;q=position b;u=updatePosString p (chars ps)
    in pure (object ["original" .= posValue p,"updated" .= posValue u,"incremented" .= posValue (incSourceColumn (incSourceLine p (as dl)) (as dc)),
       "renamed" .= posValue (setSourceName p (chars n)),"eq" .= (p==q),"compare" .= show (compare p q)])
 [String "verbosityJSON",v] -> pure (case fromJSON v :: Result Verbosity of Success z -> toJSON ["success",show z]; Error e -> toJSON ["error",e])
 [String "verbosityRead",p,s] -> pure (toJSON [[toJSON (show v),toJSON (map ord rest)] | (v,rest) <- readsPrec (as p) (chars s) :: [(Verbosity,String)]])
 [String "enum",String op,i,j,k,n] -> do
   let a=toEnum (as i)::Verbosity;b=toEnum(as j)::Verbosity;c=toEnum(as k)::Verbosity
       vs=case op of "toEnum"->[a];"succ"->[succ a];"pred"->[pred a];"from"->enumFrom a;"fromTo"->enumFromTo a c;"fromThen"->enumFromThen a b;"fromThenTo"->enumFromThenTo a b c;"bounds"->[minBound,maxBound];_->error "enum"
       result=toJSON(map show (take (as n) vs))
   e<-try(evaluate(BL.length(encode result)))::IO(Either SomeException Int64)
   pure(case e of Right _ -> object["values" .= result];Left err -> object["error" .= takeWhile(/='\n')(displayException err)])
 [String "deep",String kind,n,count] -> let z=as n; m=case kind of "inline"->InlineNotRendered(iterate(\v->Emph[v]) Space!!z);_->BlockNotRendered(iterate(\v->BlockQuote[v]) HorizontalRule!!z)
    in pure(object["prefix" .= map ord(take(as count)(show m)),"compact" .= TL.unpack(TB.toLazyText(AT.encodeToTextBuilder m))])
 _ -> error "operation"
main=loop where
 loop=do
  end<-isEOF
  if end then pure() else do
   line<-BS.getLine
   let v=case eitherDecode(BL.fromStrict line) of Right a->a;Left e->error e
   result<-response v;C.putStrLn(encode result);hFlush stdout;loop

message :: Value -> LogMessage
message v=case arr v of
 [String "SkippedContent",a0,a1] -> SkippedContent (txt a0) (position a1)
 [String "IgnoredElement",a0] -> IgnoredElement (txt a0)
 [String "DuplicateLinkReference",a0,a1] -> DuplicateLinkReference (txt a0) (position a1)
 [String "DuplicateNoteReference",a0,a1] -> DuplicateNoteReference (txt a0) (position a1)
 [String "NoteDefinedButNotUsed",a0,a1] -> NoteDefinedButNotUsed (txt a0) (position a1)
 [String "DuplicateIdentifier",a0,a1] -> DuplicateIdentifier (txt a0) (position a1)
 [String "ReferenceNotFound",a0,a1] -> ReferenceNotFound (txt a0) (position a1)
 [String "CircularReference",a0,a1] -> CircularReference (txt a0) (position a1)
 [String "UndefinedToggle",a0,a1] -> UndefinedToggle (txt a0) (position a1)
 [String "ParsingUnescaped",a0,a1] -> ParsingUnescaped (txt a0) (position a1)
 [String "CouldNotLoadIncludeFile",a0,a1] -> CouldNotLoadIncludeFile (txt a0) (position a1)
 [String "CouldNotParseIncludeFile",a0,a1] -> CouldNotParseIncludeFile (txt a0) (position a1)
 [String "MacroAlreadyDefined",a0,a1] -> MacroAlreadyDefined (txt a0) (position a1)
 [String "InlineNotRendered",a0] -> InlineNotRendered (as a0)
 [String "BlockNotRendered",a0] -> BlockNotRendered (as a0)
 [String "DocxParserWarning",a0] -> DocxParserWarning (txt a0)
 [String "PowerpointTemplateWarning",a0] -> PowerpointTemplateWarning (txt a0)
 [String "IgnoredIOError",a0] -> IgnoredIOError (txt a0)
 [String "CouldNotFetchResource",a0,a1] -> CouldNotFetchResource (txt a0) (txt a1)
 [String "CouldNotDetermineImageSize",a0,a1] -> CouldNotDetermineImageSize (txt a0) (txt a1)
 [String "CouldNotConvertImage",a0,a1] -> CouldNotConvertImage (txt a0) (txt a1)
 [String "CouldNotDetermineMimeType",a0] -> CouldNotDetermineMimeType (txt a0)
 [String "CouldNotConvertTeXMath",a0,a1] -> CouldNotConvertTeXMath (txt a0) (txt a1)
 [String "CouldNotParseCSS",a0] -> CouldNotParseCSS (txt a0)
 [String "Fetching",a0] -> Fetching (txt a0)
 [String "Extracting",a0] -> Extracting (txt a0)
 [String "LoadedResource",a0,a1] -> LoadedResource (chars a0) (chars a1)
 [String "ScriptingInfo",a0,a1] -> ScriptingInfo (txt a0) (optional a1)
 [String "ScriptingWarning",a0,a1] -> ScriptingWarning (txt a0) (optional a1)
 [String "NoTitleElement",a0] -> NoTitleElement (txt a0)
 [String "NoLangSpecified"] -> NoLangSpecified
 [String "InvalidLang",a0] -> InvalidLang (txt a0)
 [String "CouldNotHighlight",a0] -> CouldNotHighlight (txt a0)
 [String "MissingCharacter",a0] -> MissingCharacter (txt a0)
 [String "Deprecated",a0,a1] -> Deprecated (txt a0) (txt a1)
 [String "NoTranslation",a0] -> NoTranslation (txt a0)
 [String "CouldNotLoadTranslations",a0,a1] -> CouldNotLoadTranslations (txt a0) (txt a1)
 [String "UnusualConversion",a0] -> UnusualConversion (txt a0)
 [String "UnexpectedXmlElement",a0,a1] -> UnexpectedXmlElement (txt a0) (txt a1)
 [String "UnknownOrgExportOption",a0] -> UnknownOrgExportOption (txt a0)
 [String "CouldNotDeduceFormat",a0,a1] -> CouldNotDeduceFormat ((map txt . arr) a0) (txt a1)
 [String "RunningFilter",a0] -> RunningFilter (chars a0)
 [String "FilterCompleted",a0,a1] -> FilterCompleted (chars a0) ((read . str) a1)
 [String "CiteprocWarning",a0] -> CiteprocWarning (txt a0)
 [String "ATXHeadingInLHS",a0,a1] -> ATXHeadingInLHS (as a0) (txt a1)
 [String "EnvironmentVariableUndefined",a0] -> EnvironmentVariableUndefined (txt a0)
 [String "DuplicateAttribute",a0,a1] -> DuplicateAttribute (txt a0) (txt a1)
 [String "NotUTF8Encoded",a0] -> NotUTF8Encoded (chars a0)
 [String "MakePDFInfo",a0,a1] -> MakePDFInfo (txt a0) (txt a1)
 [String "MakePDFWarning",a0] -> MakePDFWarning (txt a0)
 [String "UnclosedDiv",a0,a1] -> UnclosedDiv (position a0) (position a1)
 [String "UnsupportedCodePage",a0] -> UnsupportedCodePage (as a0)
 [String "YamlWarning",a0,a1] -> YamlWarning (position a0) (txt a1)
 [String "UnsupportedPdfStandard",a0] -> UnsupportedPdfStandard (txt a0)
 _ -> error "LogMessage constructor"
