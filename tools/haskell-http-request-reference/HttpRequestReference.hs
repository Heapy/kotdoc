{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import qualified OriginalRequestSpec as Original
import qualified OriginalRequestBodySpec as OriginalBody
import qualified Test.Hspec as Spec
import Network.HTTP.Client.Internal
import qualified Network.HTTP.Types as H
import Network.URI (URI(..),URIAuth(..))
import qualified Data.CaseInsensitive as CI
import qualified Data.ByteString as B
import qualified Data.ByteString.Char8 as BC
import qualified Data.ByteString.Lazy.Char8 as L
import Blaze.ByteString.Builder (fromByteString)
import qualified Data.Set as S
import Data.Aeson
import Data.Aeson.Types (parseMaybe)
import qualified Data.Vector as V
import Data.Char (ord,chr)
import qualified Data.Text as T
import Data.IORef
import Control.Monad
import qualified Control.Exception as E
import System.IO
import System.Directory
as :: FromJSON a => Value -> a
as v=case fromJSON v of Success x->x; Error e->error e
arr (Array a)=V.toList a
arr _=error "array"
at v n=arr v!!n
key v n=case parseMaybe (withObject "o" (.:? n)) v of Just x->x;_->Nothing
field v n d=maybe d as (key v n)
raw v=map (chr.as) (arr v)
points=map ord
bytes :: Value -> B.ByteString
bytes = B.pack . as
bs = B.unpack
header v=(CI.mk $ bytes $ at v 0, bytes $ at v 1)
headers v=map header (arr v)
uri v=URI (raw $ at v 0) (if at v 1==Null then Nothing else Just (URIAuth (raw $ at (at v 1) 0) (raw $ at (at v 1) 1) (raw $ at (at v 1) 2))) (raw $ at v 2) (raw $ at v 3) (raw $ at v 4)
patch o r=r { method=maybe (method r) bytes (key o "method"),host=maybe (host r) bytes (key o "host"),port=field o "port" (port r),secure=field o "secure" (secure r),path=maybe (path r) bytes (key o "path"),queryString=maybe (queryString r) bytes (key o "query"),requestHeaders=maybe (requestHeaders r) headers (key o "headers"),rawBody=field o "rawBody" (rawBody r),redirectCount=field o "redirectCount" (redirectCount r),decompress=case field o "decompress" (0::Int) of 1->alwaysDecompress;2->const False;_->browserDecompress,requestVersion=maybe (requestVersion r) (\v->H.HttpVersion (as $ at v 0) (as $ at v 1)) (key o "version"),redactHeaders=maybe (redactHeaders r) (S.fromList.map (CI.mk.bytes).arr) (key o "redact"),responseTimeout=maybe (responseTimeout r) (\v->case as (at v 0)::String of "none"->ResponseTimeoutNone;"micro"->ResponseTimeoutMicro (as $ at v 1);_->ResponseTimeoutDefault) (key o "timeout") }
operation r v=case as (at v 0)::String of
 "basic"->pure $ applyBasicAuth (bytes $ at v 1) (bytes $ at v 2) r
 "bearer"->pure $ applyBearerAuth (bytes $ at v 1) r
 "proxy-basic"->pure $ applyBasicProxyAuth (bytes $ at v 1) (bytes $ at v 2) r
 "proxy"->pure $ addProxy (bytes $ at v 1) (as $ at v 2) r
 "proxy-direct"->pure $ addProxySecureWithoutConnect r
 "check"->pure $ setRequestCheckStatus r
 "ignore"->pure $ setRequestIgnoreStatus r
 "uri"->setUri r (uri $ at v 1)
 "relative"->setUriRelative r (uri $ at v 1)
 "query"->pure $ setQueryString (map (\p->(bytes $ at p 0,if at p 1==Null then Nothing else Just $ bytes $ at p 1)) (arr $ at v 1)) r
 "partial"->pure $ setQueryStringPartialEscape (map (\p->(bytes $ at p 0,map (\e->(if as (at e 0)::Bool then H.QE else H.QN) (bytes $ at e 1)) (arr $ at p 1))) (arr $ at v 1)) r
 "form"->pure $ urlEncodedBody (map (\p->(bytes $ at p 0,bytes $ at p 1)) (arr $ at v 1)) r
 _->error "op"
body v=case as (at v 0)::String of
 "bs"->pure $ RequestBodyBS (bytes $ at v 1)
 "lbs"->pure $ RequestBodyLBS (L.fromChunks $ map bytes (arr $ at v 1))
 "builder"->pure $ RequestBodyBuilder (as $ at v 1) (fromByteString $ bytes $ at v 2)
 "stream"->pure $ RequestBodyStream (as $ at v 1) (provider $ arr $ at v 2)
 "chunked"->pure $ RequestBodyStreamChunked (provider $ arr $ at v 1)
 "io"->pure $ RequestBodyIO (body $ at v 1)
 "io-error"->pure $ RequestBodyIO (ioError (userError "body"))
 _->error "body"
 where provider chunks consume=do
        ref<-newIORef chunks
        consume $ do
         item<-atomicModifyIORef ref (\xs->case xs of []->([],Nothing);x:rest->(rest,Just x))
         case item of
          Nothing->pure B.empty
          Just x->case field x "error" (""::String) of
           "io"->ioError (userError "popper")
           "runtime"->E.throwIO (E.ErrorCall "popper")
           _->pure (bytes x)
wire opts r0=do
 (base,out,_)<-dummyConnection []
 trace<-newIORef ([]::[String])
 writes<-newIORef (0::Int)
 let active=field opts "faults" False
     r=r0{onRequestBodyException= \e->do
           modifyIORef trace (++["hook: "++show e])
           case field opts "hook" ("default"::String) of
            "swallow"->pure ()
            "throw"->E.throwIO e
            _->onRequestBodyException r0 e}
     conn=base{connectionWrite= \x->do
          n<-atomicModifyIORef writes (\i->(i+1,i))
          when active (modifyIORef trace (++["write: "++show (B.length x)]))
          if active && n==field opts "failWrite" (-1) then ioError (userError "write") else connectionWrite base x}
     traced value=if not active then pure value else do
       events<-readIORef trace
       case value of Object o->pure (object ["result".=Object o,"trace".=events]);_->error "object"

 result<-E.try $ do
   continuation<-requestBuilder r conn
   before<-out
   maybe (pure ()) id continuation
   after<-out
   pure $ object ["before".=map bs before,"after".=map bs after,"continued".=maybe False (const True) continuation]
 case result of
  Right v->traced v
  Left e->do
   sent<-out
   traced $ object ["error".=points (show (e::E.SomeException)),"sent".=map bs sent]
fileObservation v=do
 tmp<-getTemporaryDirectory
 E.bracket (openBinaryTempFile tmp "kotdoc-http-observer") (\(p,h)->hClose h >> removeFile p) $ \(p,h)->do
  B.hPut h (bytes $ maybe (error "data") id $ key v "data")
  hClose h
  ref<-newIORef []
  b<-observedStreamFile (\x->modifyIORef ref (++[(fileSize x,readSoFar x,thisChunkSize x)])) p
  maybe (pure ()) (B.writeFile p . bytes) (key v "afterData")
  ws<-replicateM (field v "times" (1::Int)) (wire v defaultRequest{requestBody=b})
  events<-readIORef ref
  if field v "times" (1::Int)==1 then pure $ object ["wire".=head ws,"events".=events]
   else pure $ object ["wires".=ws,"events".=events]
observe v=do
 base<-case field v "mode" ("parse"::String) of
  "default"->pure defaultRequest
  "throw"->parseUrlThrow (raw $ maybe (error "url") id $ key v "url")
  "uri"->requestFromURI (uri $ maybe (error "uri") id $ key v "uri")
  _->parseRequest (raw $ maybe (error "url") id $ key v "url")
 let patched=maybe base (`patch` base) (key v "patch")
 r0<-foldM operation patched (maybe [] arr (key v "ops"))
 r<-maybe (pure r0) (\b->do x<-body b;pure r0{requestBody=x}) (key v "body")
 w<-wire v r
 -- Check the actual callback with a non-2xx response, without opening sockets.
 check<-E.try $ checkResponse r r rResponse
 let checkStatus=case check of Left (_::E.SomeException)->True;Right _->False
 pure $ object ["host".=bs(host r),"port".=port r,"secure".=secure r,"path".=bs(path r),"query".=bs(queryString r),"method".=bs(method r),"headers".=[(bs(CI.original n),bs x)|(n,x)<-requestHeaders r],"proxy".=fmap (\p->(bs(proxyHost p),proxyPort p)) (proxy r),"show".=points(show r),"uri".=points(show $ getUri r),"check".=checkStatus,"gunzip".=needsGunzip r (maybe [] headers (key v "responseHeaders")),"wire".=w]
 where rResponse=Response (H.mkStatus 404 "Not Found") H.http11 [] (pure B.empty) mempty (ResponseClose (pure ())) defaultRequest []
main=do
 hSetBuffering stdout LineBuffering
 let loop=do
      eof<-hIsEOF stdin
      unless eof $ do
       line<-L.fromStrict <$> BC.getLine
       input<-either fail pure (eitherDecode line)
       result<-E.try $ if field input "mode" (""::String)=="original-tests" then do
        writeIORef Spec.passed []
        Original.spec
        OriginalBody.spec
        toJSON <$> readIORef Spec.passed
        else if field input "mode" (""::String)=="file" then fileObservation input else observe input
       let out=case result of Right x->x;Left e->object ["error".=points(show (e::E.SomeException))]
       L.putStr (encode out<>"\n")
       loop
 loop
