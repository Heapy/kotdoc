{-# LANGUAGE OverloadedStrings, CPP #-}
#if __GLASGOW_HASKELL__ != 912 || __GLASGOW_HASKELL_PATCHLEVEL1__ != 2 || __GLASGOW_HASKELL_PATCHLEVEL2__ != 0
#error GHC 9.12.2 required
#endif
module Main where
import HttpRedirectProjection hiding (req,viewReq)
import qualified HttpRedirectProjection as P
import qualified OriginalExecution as E
import qualified ExecutionEffects as F
import Network.HTTP.Client.Internal hiding (responseOpen,withResponse,httpRaw,httpRaw',httpLbs,httpNoBody,withConnection,requestAction)
import Data.KeyedPool
import Network.HTTP.Types (statusCode,statusMessage)
import Control.Exception
import Control.Monad
import Data.IORef
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.CaseInsensitive as CI
import Data.Aeson
import Data.Maybe (fromJust)
import Data.Time.Clock.POSIX
import Data.Ratio
import System.IO
import System.IO.Error
import Foreign.StablePtr

raise :: String -> IO a
raise tag=case tag of
 "closed" -> throwHttp ConnectionClosed
 "nodata" -> throwHttp NoResponseDataReceived
 "incomplete" -> throwHttp IncompleteHeaders
 "response-timeout" -> throwHttp ResponseTimeout
 "request-closed" -> throwIO $ HttpExceptionRequest defaultRequest ConnectionClosed
 "vanished" -> ioError $ mkIOError resourceVanishedErrorType "script-location" Nothing Nothing
 "io" -> ioError $ ioeSetErrorString (mkIOError userErrorType "script-location" Nothing Nothing) "user-io"
 "async" -> throwIO ThreadKilled
 _ -> throwIO $ ErrorCall tag
readTimeout :: Value -> ResponseTimeout
readTimeout (String "default")=ResponseTimeoutDefault
readTimeout (String "none")=ResponseTimeoutNone
readTimeout x=ResponseTimeoutMicro $ fromJust $ decode $ encode x
viewReq r=object ["request" .= P.viewReq r,"check" .= ("opaque"::String)]
viewResponse r=object ["status" .= statusCode (responseStatus r),"message" .= bytes (statusMessage $ responseStatus r)
 ,"headers" .= viewHeaders (responseHeaders r),"jar" .= show (responseCookieJar r),"original" .= P.viewReq (getOriginalRequest r)]
viewError se
 | Just (HttpExceptionRequest r (StatusCodeException res b))<-fromException se=object ["kind" .= ("request-status"::String),"request" .= P.viewReq r,"response" .= viewResponse res,"body" .= bytes b,"show" .= show se]
 | Just (HttpExceptionRequest r e)<-fromException se=object ["kind" .= ("request"::String),"request" .= P.viewReq r,"content" .= show e,"show" .= show se]
 | Just e<-fmap unHttpExceptionContentWrapper (fromException se)=object ["kind" .= ("content"::String),"content" .= show e,"show" .= show se]
 | Just e<-fromException se::Maybe IOError=object ["kind" .= ("io"::String),"vanished" .= (ioeGetErrorType e==resourceVanishedErrorType),"message" .= ioeGetErrorString e,"location" .= ioeGetLocation e]
 | Just ThreadKilled<-fromException se=object ["kind" .= ("async"::String)]
 | Just (ErrorCall msg)<-fromException se=object ["kind" .= ("script"::String),"message" .= takeWhile (/='\n') msg]
 | otherwise=error $ show se
run v=do
 logRef<-newIORef [];clockRef<-newIORef (maybe [] id (optional v "times")::[Integer]);connRef<-newIORef (0::Int)
 let log x=modifyIORef' logRef (x:)
     now=do
       p<-atomicModifyIORef' clockRef $ \ps->case ps of {[]->([],1700000000000000000000);a:rest->(rest,a)}
       log $ object ["event" .= ("clock"::String),"picoseconds" .= p]
       pure $ posixSecondsToUTCTime $ fromRational $ p % 1000000000000
     hookFailure s key=case optional s key of {Just tag->raise tag;Nothing->pure ()}
     scripts=array $ value v "connections"
     connector _ host port=do
       i<-atomicModifyIORef' connRef (\i->(i+1,i))
       log $ object ["event" .= ("connect"::String),"index" .= i,"host" .= host,"port" .= port]
       when (i>=length scripts) $ raise "connections-exhausted"
       let s=scripts!!i
       hookFailure s "connectFailure"
       readRef<-newIORef (0::Int);writeRef<-newIORef (0::Int)
       makeConnection
        (do n<-atomicModifyIORef' readRef (\n->(n+1,n))
            log $ object ["event" .= ("read"::String),"index" .= i,"chunk" .= n]
            case drop n (maybe [] array $ optional s "chunks") of {String tag:_->raise $ fromJust $ decode $ encode tag;a:_->pure $ bs a;[]->pure B.empty})
        (\b->do log $ object ["event" .= ("write"::String),"index" .= i,"bytes" .= bytes b]
                n<-atomicModifyIORef' writeRef (\n->(n+1,n))
                case optional s "writeFailAt" of {Just x | x==n->raise (field s "writeFailure");Nothing->hookFailure s "writeFailure";_ ->pure ()})
        (do log $ object ["event" .= ("close"::String),"index" .= i];hookFailure s "closeFailure")
     conf i=case drop i (maybe [] array $ optional v "managers") of {s:_->s;[]->object []}
     mkManager i alternate=do
       let s=conf i
           modify r=do
             log $ object ["event" .= ("modify-request"::String),"manager" .= i,"request" .= P.viewReq r]
             hookFailure s "modifyFailure"
             pure r {path=path r <> maybe B.empty bs (optional s "suffix")
                    ,requestHeaders=requestHeaders r ++ maybe [] headers (optional s "appendHeaders")
                    ,redirectCount=maybe (redirectCount r) id (optional s "redirectCount")
                    ,requestManagerOverride=if optional s "override"==Just True then alternate else requestManagerOverride r}
           settings=defaultManagerSettings {managerRawConnection=pure connector,managerTlsConnection=pure connector
             ,managerProxyInsecure=proxyFromRequest,managerProxySecure=proxyFromRequest
             ,managerResponseTimeout=maybe ResponseTimeoutDefault readTimeout (optional s "timeout")
             ,managerModifyRequest=modify
             ,managerModifyResponse= \r->do log $ object ["event" .= ("modify-response"::String),"manager" .= i];hookFailure s "responseFailure";pure r
             ,managerWrapException= \r a->do log $ object ["event" .= ("wrap"::String),"manager" .= i,"request" .= P.viewReq r];managerWrapException defaultManagerSettings r a}
       m<-newManager settings
       _<-newStablePtr m
       pure m
 writeIORef F.state $ F.Effects now log (optional v "acquireExpires"==Just True)
 m1<-mkManager 1 Nothing;m0<-mkManager 0 (Just m1)
 let readReq x=let r=P.req x in r {responseTimeout=maybe ResponseTimeoutNone readTimeout (optional x "timeout")
        ,requestManagerOverride=if optional x "override"==Just True then Just m1 else Nothing
        ,checkResponse= \req res->do
           log $ object ["event" .= ("check"::String),"request" .= P.viewReq req,"status" .= statusCode (responseStatus res)]
           hookFailure x "checkFailure"
           when (optional x "checkStatus"==Just True) $ throwErrorStatusCodes req res}
     initial=readReq $ value v "initial"
     consume res=do
       chunks<-if optional v "consume"==Just True then Just <$> brConsume (responseBody res) else pure Nothing
       when (optional v "close"==Just True) $ responseClose res
       hookFailure v "actionFailure"
       pure $ object ["kind" .= ("success"::String),"response" .= viewResponse res,"body" .= fmap (map bytes) chunks]
     consumeHistory hr=do
       result<-consume $ E.hrFinalResponse hr
       let shown=E.HistoriedResponse (E.hrRedirects hr) (E.hrFinalRequest hr) ((E.hrFinalResponse hr) {responseBody=()})
       pure $ object ["showUnit" .= show shown,"showUnitPrec11" .= showsPrec 11 shown "TAIL","result" .= result,"finalRequest" .= P.viewReq (E.hrFinalRequest hr)
          ,"redirects" .= [object ["request" .= P.viewReq r,"response" .= viewResponse res,"body" .= map bytes (L.toChunks $ responseBody res)] | (r,res)<-E.hrRedirects hr]]
     execute=do
       when (optional v "prime"==Just True) $ do
          res<-E.httpRaw initial m0;brConsume (responseBody res);pure ()
       case field v "api"::String of
        "raw" -> E.httpRaw initial m0 >>= consume
        "raw-request" -> do (r,res)<-E.httpRaw' initial m0;x<-consume res;pure $ object ["request" .= P.viewReq r,"result" .= x]
        "open" -> E.responseOpen initial m0 >>= consume
        "with" -> E.withResponse initial m0 consume
        "lbs" -> do res<-E.httpLbs initial m0;pure $ object ["kind" .= ("success"::String),"response" .= viewResponse res,"body" .= map bytes (L.toChunks $ responseBody res)]
        "no-body" -> do res<-E.httpNoBody initial m0;pure $ object ["kind" .= ("success"::String),"response" .= viewResponse res]
        "history" -> E.responseOpenHistory initial m0 >>= consumeHistory
        "with-history" -> E.withResponseHistory initial m0 consumeHistory
        "connection" -> E.withConnection initial m0 $ \c->do connectionWrite c "interactive";hookFailure v "actionFailure";pure $ object ["kind" .= ("success"::String)]
        _ -> error "api"
 result<-try (execute >>= \x->evaluate (let encoded=encode x in L.length encoded `seq` x))::IO (Either SomeException Value)
 logs<-reverse <$> readIORef logRef
 pure $ object ["result" .= either viewError id result,"events" .= logs]
main=do
 hSetBuffering stdout LineBuffering
 let loop=do
       eof<-hIsEOF stdin
       unless eof $ do
         input<-B.getLine
         out<-run $ fromJust $ decodeStrict input
         L.putStrLn $ encode out
         loop
 loop
