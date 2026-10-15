{-# LANGUAGE OverloadedStrings #-}
module Main where
import Data.Aeson
import qualified Data.Aeson.Key as K
import qualified Data.Aeson.KeyMap as KM
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
as :: FromJSON a => Value -> a
as v=case fromJSON v of Success x->x;Error e->error e
arr (Array v)=V.toList v
arr _=error "array"
chars=map (chr.as).arr
txt=T.pack.chars
tag v=case as v::String of
 "StrTag"->Y.StrTag;"FloatTag"->Y.FloatTag;"NullTag"->Y.NullTag;"BoolTag"->Y.BoolTag
 "SetTag"->Y.SetTag;"IntTag"->Y.IntTag;"SeqTag"->Y.SeqTag;"MapTag"->Y.MapTag;"NoTag"->Y.NoTag
 _->Y.UriTag "!custom"
shape v=case v of
 Null->toJSON [String "null"]
 Bool b->toJSON [String "bool",Bool b]
 String s->toJSON [String "text",String s]
 Number n->toJSON [String "number",toJSON(show(S.coefficient n)),toJSON(S.base10Exponent n)]
 Array xs->toJSON [String "array",toJSON(map shape(V.toList xs))]
 Object o->toJSON [String "object",toJSON [toJSON [String(K.toText k),shape x]|(k,x)<-KM.toList o]]
event (Y.EventScalar b t s a)=object ["bytes" .= BS.unpack b,"tag" .= show t,"style" .= show s,"anchor" .= fmap(map ord)a]
event _=error "scalar"
response :: Value -> IO Value
response req=case arr req of
 [String "scientific",text]->do
  let t=txt text
      num=case I.textToScientific t of Right n->toJSON [String "success",toJSON(show(S.coefficient n)),toJSON(S.base10Exponent n)];Left e->toJSON[String "failure",toJSON e]
      (tg,sty)=I.defaultStringStyle t
  pure(object["numeric" .= num,"isNumeric" .= I.isNumeric t,"special" .= I.isSpecialString t,"tag" .= show tg,"style" .= show sty])
 [String "scalar",bytes,style,t]->do
  let ev=Y.EventScalar(BS.pack(map as(arr bytes)))(tag t)(toEnum(as style))Nothing
  r<-I.decodeHelper_(CL.sourceList[Y.EventStreamStart,Y.EventDocumentStart,ev,Y.EventDocumentEnd,Y.EventStreamEnd])::IO(Either I.ParseException ([I.Warning],Value))
  pure(case r of Left e->object["error" .= I.prettyPrintParseException e];Right(ws,v)->object["value" .= shape v,"warnings" .= map show ws])
 [String "string",text,anchor]->pure(event(I.stringScalar I.defaultStringStyle (case anchor of Null->Nothing;_->Just(txt anchor))(txt text)))
 _->error "request"
main=loop where
 loop=do
  end<-isEOF
  unless end $ do
   line<-BS.getLine;result<-response(either error id(eitherDecode(BL.fromStrict line)))
   C.putStrLn(encode result);hFlush stdout;loop
