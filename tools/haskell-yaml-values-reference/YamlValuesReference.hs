{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE DeriveDataTypeable #-}
module Main where
import Data.Aeson
import qualified Data.Aeson.Key as K
import qualified Data.Aeson.KeyMap as KM
import Data.Aeson.Types (JSONPathElement(..))
import qualified Data.Scientific as S
import qualified Data.Vector as V
import qualified Data.Text as T
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import Data.Char (chr,ord)
import Data.Conduit
import qualified Data.Conduit.List as CL
import qualified Data.Yaml.Internal as I
import qualified Text.Libyaml as Y
import System.IO
import Control.Monad (unless)
import Control.Monad.IO.Class (liftIO)
import Control.Exception
import Data.Typeable
import Data.IORef
as :: FromJSON a => Value -> a
as v=case fromJSON v of Success x->x;Error e->error e
arr (Array v)=V.toList v
arr _=error "array"
chars=map (chr.as).arr
txt=T.pack.chars
anchor Null=Nothing
anchor a=Just(chars a)
tag v=case arr v of [String "UriTag",s]->Y.UriTag(chars s);[s]->read(as s);_->error "tag"
ev v=case arr v of
 [String "StreamStart"]->Y.EventStreamStart
 [String "StreamEnd"]->Y.EventStreamEnd
 [String "DocumentStart"]->Y.EventDocumentStart
 [String "DocumentEnd"]->Y.EventDocumentEnd
 [String "Alias",a]->Y.EventAlias(chars a)
 [String "Scalar",bytes,t,s,a]->Y.EventScalar(BS.pack(map as(arr bytes)))(tag t)(toEnum(as s))(anchor a)
 [String "SequenceStart",t,s,a]->Y.EventSequenceStart(tag t)(toEnum(as s))(anchor a)
 [String "SequenceEnd"]->Y.EventSequenceEnd
 [String "MappingStart",t,s,a]->Y.EventMappingStart(tag t)(toEnum(as s))(anchor a)
 [String "MappingEnd"]->Y.EventMappingEnd
 _->error "event"
path=map element.arr where
 element v=case arr v of [String "key",s]->Key(K.fromText(txt s));[String "index",n]->Index(as n);_->error "path"
shape v=case v of
 Null->toJSON [String "null"]
 Bool b->toJSON [String "bool",Bool b]
 String s->toJSON [String "text",String s]
 Number n->toJSON [String "number",toJSON(show(S.coefficient n)),toJSON(S.base10Exponent n)]
 Array xs->toJSON [String "array",toJSON(map shape(V.toList xs))]
 Object o->toJSON [String "object",toJSON [toJSON [String(K.toText k),shape x]|(k,x)<-KM.toAscList o]]
value v=case arr v of
 [String "null"]->Null
 [String "bool",b]->Bool(as b)
 [String "text",s]->String(txt s)
 [String "number",n,e]->Number(S.scientific(read(as n))(as e))
 [String "array",xs]->Array(V.fromList(map value(arr xs)))
 [String "object",xs]->Object(KM.fromList[case arr x of [k,v]->(K.fromText(txt k),value v);_->error "pair"|x<-arr xs])
 _->error "value"
warning (I.DuplicateKey ps)=toJSON(map elem ps) where
 elem (Key k)=toJSON[String "key",toJSON(K.toText k)]
 elem (Index n)=toJSON[String "index",toJSON n]
errorName e=case e of
 I.NonScalarKey->"NonScalarKey";I.UnknownAlias{}->"UnknownAlias";I.UnexpectedEvent{}->"UnexpectedEvent"
 I.InvalidYaml{}->"InvalidYaml";I.MultipleDocuments->"MultipleDocuments";I.AesonException{}->"AesonException"
 I.OtherParseException{}->"OtherParseException";I.NonStringKey{}->"NonStringKey";I.NonStringKeyAlias{}->"NonStringKeyAlias"
 I.CyclicIncludes->"CyclicIncludes";I.LoadSettingsException{}->"LoadSettingsException"
errorResult e=object["error" .= (errorName e::String),"pretty" .= map ord(I.prettyPrintParseException e)]
data ProbeException=ProbeException String deriving Typeable
instance Show ProbeException where show (ProbeException s)=s
instance Exception ProbeException
exception v=case arr v of
 [String "NonScalarKey"]->I.NonScalarKey
 [String "UnknownAlias",a]->I.UnknownAlias(chars a)
 [String "UnexpectedEvent",r,e]->I.UnexpectedEvent(eventMaybe r)(eventMaybe e)
 [String "InvalidYaml",Null]->I.InvalidYaml Nothing
 [String "InvalidYaml",a]->I.InvalidYaml(Just(detail a))
 [String "MultipleDocuments"]->I.MultipleDocuments
 [String "AesonException",s]->I.AesonException(chars s)
 [String "OtherParseException",s]->I.OtherParseException(toException(ProbeException(chars s)))
 [String "NonStringKey",p]->I.NonStringKey(path p)
 [String "NonStringKeyAlias",a,v]->I.NonStringKeyAlias(chars a)(value v)
 [String "CyclicIncludes"]->I.CyclicIncludes
 [String "LoadSettingsException",f,e]->I.LoadSettingsException(chars f)(exception e)
 _->error "exception"
 where
 eventMaybe Null=Nothing
 eventMaybe v=Just(ev v)
 detail v=case arr v of
  [String "Generic",s]->Y.YamlException(chars s)
  [String "Parse",p,c,m]->case arr m of [i,l,k]->Y.YamlParseException(chars p)(chars c)(Y.YamlMark(as i)(as l)(as k));_->error "mark"
  _->error "detail"
response req=case arr req of
 [String "exception",e]->pure(errorResult(exception e))
 [String "warning",p,d]->pure(toJSON(map ord(showsPrec (as d) (I.DuplicateKey(path p)) "")))
 [String "literal",s]->pure(toJSON(BS.unpack(BS.pack(map (fromIntegral.ord)(read(as s)::String)))))
 [mode,kind,payload]->do
  let allDocs=as mode::Bool
  calls<-newIORef(0::Int)
  let src=case as kind::String of
       "bytes"->Y.decode(BS.pack(map as(arr payload)))
       "events"->CL.sourceList(map ev(arr payload))
       _->error "kind"
      observed=src .| awaitForever (\e->liftIO(modifyIORef' calls(+1))>>yield e)
  r<-if allDocs then fmap(fmap(\(ws,vs)->(ws,map shape vs)))(I.decodeAllHelper_ observed::IO(Either I.ParseException([I.Warning],[Value])))
     else fmap(fmap(\(ws,v)->(ws,[shape v])))(I.decodeHelper_ observed::IO(Either I.ParseException([I.Warning],Value)))
  count<-readIORef calls
  pure(object["result" .= either errorResult (\(ws,vs)->object["warnings" .= map warning ws,"warningsShown" .= map show ws,"values" .= vs]) r,"consumed" .= count])
 _->error "request"
main=loop where
 loop=do
  end<-isEOF
  unless end $ do
   line<-BS.getLine;result<-response(either error id(eitherDecode(BL.fromStrict line)))
   C.putStrLn(encode result);hFlush stdout;loop
