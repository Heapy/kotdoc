{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import Network.HTTP.Client.Internal
import qualified OriginalResponse as R
import qualified ObserverManaged as Managed
import qualified OriginalHeadersSpec as Headers
import qualified OriginalBodySpec as Body
import qualified OriginalResponseSpec as Response
import qualified OriginalConnectionSpec as Connection
import qualified Test.Hspec as Spec
import qualified Data.Streaming.Zlib as Z
import qualified Codec.Compression.GZip as Gzip
import qualified Codec.Compression.Zlib as Zlib
import qualified Codec.Compression.Zlib.Raw as Raw
import qualified Network.HTTP.Types as H
import qualified Data.CaseInsensitive as CI
import qualified Data.ByteString as B
import qualified Data.ByteString.Char8 as BC
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Types (parseMaybe)
import qualified Data.Vector as V
import Data.Char (ord)
import Data.IORef
import Control.Concurrent (threadDelay)
import Control.Monad
import qualified Control.Exception as E
import System.IO
as :: FromJSON a => Value -> a
as v=case fromJSON v of Success x->x; Error e->error e
arr (Array a)=V.toList a
arr _=error "array"
at v n=arr v!!n
key :: Value -> Key -> Maybe Value
key v n=case parseMaybe (withObject "o" (.:? n)) v of Just x->x;_->Nothing
field v n d=maybe d as (key v n)
bytes :: Value -> B.ByteString
bytes=B.pack.as
bs=B.unpack
headers hs=[(bs(CI.original n),bs v)|(n,v)<-hs]
status (StatusHeaders s v early hs)=object ["code".=H.statusCode s,"message".=bs(H.statusMessage s),"version".=[H.httpMajor v,H.httpMinor v],"early".=headers early,"headers".=headers hs]
attempt action=do
 r<-E.try (action >>= \v->E.evaluate (L.length (encode v)) >> pure v)
 pure $ case r of Right x->object["ok".=x];Left e->object["error".=map ord(show (e::E.SomeException))]
ops v f=mapM (attempt.f) (maybe [] arr (key v "ops"))
observe v=do
 let mode=field v "mode" ("headers"::String)
 if mode=="original-tests" then do
  writeIORef Spec.passed []
  Headers.spec;Body.spec;Response.spec;Connection.spec
  toJSON <$> readIORef Spec.passed
 else if mode=="original-gzip-inputs" then pure $ toJSON
  [map bs (L.toChunks $ Gzip.compress $ L.fromChunks $ replicate 5000 "Hello world!"),map bs (L.toChunks $ Gzip.compress "Compressed Hello World")]
 else if mode=="compress" && key v "dictionary"/=Nothing then do
  def<-Z.initDeflateWithDictionary 7 (bytes $ maybe (error "dict") id(key v "dictionary")) (Z.WindowBits $ field v "bits" 15)
  let drain pop=do z<-pop;case z of Z.PRDone->pure [];Z.PRNext b->(b:) <$> drain pop;Z.PRError e->E.throwIO e
  p<-Z.feedDeflate def (bytes $ maybe (error "data") id(key v "data"))
  a<-drain p;b<-drain (Z.finishDeflate def)
  pure $ toJSON $ bs $ B.concat (a++b)
 else if mode=="compress" then do
  let b=L.fromStrict $ bytes $ maybe (error "data") id (key v "data")
  pure $ toJSON $ bs $ L.toStrict $ if field v "raw" False then Raw.compress b else if field v "gzip" True then Gzip.compress b else Zlib.compress b
 else if mode=="inflate" then do
  inf<-maybe (Z.initInflate $ Z.WindowBits $ field v "bits" 31)
       (Z.initInflateWithDictionary (Z.WindowBits $ field v "bits" 31).bytes) (key v "dictionary")
  poppers<-newIORef []
  out<-ops v $ \op->case as (at op 0)::String of
   "feed"->do p<-Z.feedInflate inf (bytes $ at op 1);ps<-readIORef poppers;modifyIORef poppers (++[p]);pure $ toJSON $ length ps
   "pop"->do ps<-readIORef poppers;z<-ps!!as(at op 1);pure $ case z of Z.PRDone->toJSON [String "done"];Z.PRNext b->toJSON [String "next",toJSON (bs b)];Z.PRError (Z.ZlibException n)->toJSON [String "error",toJSON n]
   "flush"->toJSON.bs <$> Z.flushInflate inf
   "finish"->toJSON.bs <$> Z.finishInflate inf
   "unused"->toJSON.bs <$> Z.getUnusedInflate inf
   "complete"->toJSON <$> Z.isCompleteInflate inf
   _->error "inflate op"
  pure $ toJSON out
 else do
  (base,out,inp)<-dummyConnection $ map bytes (maybe [] arr (key v "input"))
  events<-newIORef ([]::[Value])
  let event x=modifyIORef events (++[x])
      conn=base {connectionRead=do
                  when (field v "readDelay" (0::Int)>0) (event(toJSON[String "read-start"]) >> threadDelay (field v "readDelay" 0))
                  b<-connectionRead base;event(toJSON[String "read",toJSON(bs b)]);pure b,
                 connectionUnread= \b->event(toJSON[String "unread",toJSON(bs b)])>>connectionUnread base b,
                 connectionWrite= \b->event(toJSON[String "write",toJSON(bs b)])>>connectionWrite base b,
                 connectionClose=event(toJSON[String "close"])}
      mhl=MaxHeaderLength <$> field v "maxLength" Nothing
      mnh=MaxNumberHeaders <$> field v "maxHeaders" Nothing
      timeout=field v "timeout" Nothing
      early hs=event(toJSON[String "early",toJSON(headers hs)])
      cont=if field v "continue" False then Just(event(toJSON[String "continue"])>>threadDelay (field v "continueDelay" 0)>>connectionWrite conn "data") else Nothing
      clean=event(toJSON[String "cleanup"])
      request=defaultRequest{method=maybe "GET" bytes(key v "method"),rawBody=field v "raw" False,
              earlyHintHeadersReceived=early,decompress=case field v "decompress" (0::Int) of 1->alwaysDecompress;2->const False;_->browserDecompress}
      managed=Managed.Managed conn (\r->event(toJSON[String "release",toJSON(show r)])) (event(toJSON[String "keepAlive"]))
  result<-attempt $ case mode of
   "headers"->status <$> parseStatusHeaders mhl mnh conn timeout early cont
   "header-pair"->do
     let h x=(CI.mk(bytes(at x 0)),bytes(at x 1))
         a=h $ maybe (error "left") id(key v "left")
         b=h $ maybe (error "right") id(key v "right")
     pure $ toJSON [toJSON(a==b),toJSON(case compare a b of LT->(-1::Int);EQ->0;GT->1)]
   "validate"->pure $ case validateHeaders [(CI.mk(bytes(at p 0)),bytes(at p 1))|p<-maybe [] arr(key v "headers")] of
                GoodHeaders->Null;BadHeaders b->toJSON(bs b)
   "line"->toJSON <$> ops v (\op->case as(at op 0)::String of
                 "read"->toJSON.bs <$> connectionReadLine mhl conn
                 "with"->toJSON.bs <$> connectionReadLineWith mhl conn (bytes(at op 1))
                 "unread"->connectionUnreadLine conn (bytes(at op 1))>>pure Null
                 "drop"->connectionDropTillBlankLine mhl conn>>pure Null
                 _->error "line op")
   "make"->do
     c<-makeConnection (connectionRead conn) (connectionWrite conn) (connectionClose conn)
     toJSON <$> ops v (\op->case as(at op 0)::String of
                 "read"->toJSON.bs <$> connectionRead c
                 "unread"->connectionUnread c (bytes(at op 1))>>pure Null
                 "write"->connectionWrite c (bytes(at op 1))>>pure Null
                 "close"->connectionClose c>>pure Null
                 _->error "make op")
   "host"->pure $ toJSON $ map ord $ strippedHostName $ map (toEnum.as) (arr $ maybe (error "host") id $ key v "host")
   "body"->do
     reader<-case field v "kind" ("chunked"::String) of
      "length"->makeLengthReader clean (field v "length" 0) conn
      "unlimited"->makeUnlimitedReader clean conn
      "const"->constBodyReader (map bytes (maybe [] arr(key v "input")))
      "gzip"->makeGzipReader (connectionRead conn)
      _->makeChunkedReader mhl clean (field v "raw" False) conn
     toJSON <$> ops v (readOp reader)
   "response"->do
     res<-R.getResponse mhl mnh timeout request managed cont
     let sh=StatusHeaders (responseStatus res) (responseVersion res) (responseEarlyHints res) (responseHeaders res)
     results<-ops v $ \op->case as(at op 0)::String of
      "again"->do
        second<-R.getResponse mhl mnh timeout (R.getOriginalRequest res) managed Nothing
        let s=StatusHeaders (responseStatus second) (responseVersion second) (responseEarlyHints second) (responseHeaders second)
        b<-brConsume (responseBody second)
        pure $ object ["status".=status s,"body".=map bs b]
      "close"->responseClose res>>pure Null
      "original"->pure $ toJSON $ map ord $ show $ R.getOriginalRequest res
      "lbs"->do b<-R.lbsResponse res;pure $ toJSON $ map bs $ L.toChunks $ responseBody b
      _->readOp (responseBody res) op
     pure $ object ["status".=status sh,"results".=results]
   _->error "mode"
  es<-readIORef events;remaining<-inp;writes<-out
  pure $ object ["result".=result,"events".=es,"rest".=map bs remaining,"writes".=map bs writes]
 where readOp reader op=case as(at op 0)::String of
        "read"->toJSON.bs <$> brRead reader
        "some"->toJSON.map bs.L.toChunks <$> brReadSome reader (as(at op 1))
        "consume"->toJSON.map bs <$> brConsume reader
        _->error "read op"
main=do
 hSetBuffering stdout LineBuffering
 let loop=do
      eof<-hIsEOF stdin
      unless eof $ do
       line<-L.fromStrict <$> BC.getLine
       input<-either fail pure (eitherDecode line)
       result<-observe input
       L.putStr (encode result<>"\n")
       loop
 loop
