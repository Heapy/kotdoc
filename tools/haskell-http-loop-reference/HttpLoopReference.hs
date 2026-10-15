{-# LANGUAGE OverloadedStrings #-}
module Main where
import HttpRedirectProjection
import Control.Exception
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Types (parseMaybe)
import Data.Maybe (fromJust)
import Data.IORef
import Data.Time (UTCTime(..),Day(..))
import qualified Data.CaseInsensitive as CI
import Network.HTTP.Client.Internal
import Network.HTTP.Types (mkStatus,HttpVersion(..),statusCode,statusMessage)
import System.IO
import System.IO.Error

raise :: String -> IO a
raise tag = case tag of
 "content-closed" -> throwHttp ConnectionClosed
 "request-closed" -> throwIO $ HttpExceptionRequest defaultRequest ConnectionClosed
 "content-too-many" -> throwHttp $ TooManyRedirects []
 "request-too-many" -> throwIO $ HttpExceptionRequest defaultRequest (TooManyRedirects [])
 "request-invalid-status" -> throwIO $ HttpExceptionRequest defaultRequest (InvalidStatusLine "bad")
 "content-timeout" -> throwHttp ResponseTimeout
 "request-timeout" -> throwIO $ HttpExceptionRequest defaultRequest ResponseTimeout
 "vanished" -> ioError $ mkIOError resourceVanishedErrorType "script-location" Nothing Nothing
 "io" -> ioError $ ioeSetErrorString (mkIOError userErrorType "script-location" Nothing Nothing) "user-io"
 "async" -> throwIO ThreadKilled
 _ -> throwIO $ ErrorCall tag
chunk :: Value -> B.ByteString
chunk x@(Array _) = bs x
chunk x = B.replicate (field x "size") (field x "byte")
cookies :: Value -> CookieJar
cookies s = if optional s "latentCookie" == Just True then createCookieJar [Cookie "latent" "value" (error "latent-expiry") "example.com" "/" t t False True False False]
            else maybe mempty jar (optional s "jar")
 where t=UTCTime (ModifiedJulianDay 60000) 0
viewMeta r = ["status" .= [toJSON (statusCode $ responseStatus r),bytes (statusMessage $ responseStatus r)]
 ,"version" .= [httpMajor (responseVersion r),httpMinor (responseVersion r)],"headers" .= viewHeaders (responseHeaders r)
 ,"jarNames" .= map (bytes . cookie_name) (destroyCookieJar $ responseCookieJar r)
 ,"original" .= viewReq (getOriginalRequest r),"early" .= viewHeaders (responseEarlyHints r)]
viewLbs withShow r = object $ viewMeta r ++ ["body" .= map bytes (L.toChunks $ responseBody r)] ++ if withShow then ["show" .= show r,"showPrec11" .= showsPrec 11 r "TAIL"] else []
viewError withShow se
 | Just (TooManyRedirects rs) <- fmap unHttpExceptionContentWrapper (fromException se) = object $
     ["kind" .= ("too-many"::String),"history" .= map (viewLbs withShow) rs] ++ if withShow then ["show" .= show se] else []
 | Just e <- fmap unHttpExceptionContentWrapper (fromException se) = object ["kind" .= ("content"::String),"content" .= show e,"show" .= show se]
 | Just (HttpExceptionRequest r e) <- fromException se = object ["kind" .= ("request"::String),"request" .= viewReq r,"content" .= show e,"show" .= show se]
 | Just e <- fromException se :: Maybe IOError = object ["kind" .= ("io"::String),"vanished" .= (ioeGetErrorType e == resourceVanishedErrorType),"message" .= ioeGetErrorString e,"location" .= ioeGetLocation e]
 | Just ThreadKilled <- fromException se = object ["kind" .= ("async"::String),"message" .= ("thread killed"::String)]
 | Just (ErrorCall msg) <- fromException se = object ["kind" .= ("script"::String),"message" .= takeWhile (/='\n') msg]
 | otherwise = error $ "unclassified exception: " ++ show se
run :: Value -> IO Value
run v = do
 logRef <- newIORef [];indexRef <- newIORef (0::Int)
 let log x=modifyIORef' logRef (x:)
     scripts=array $ value v "responses"
     withShow=optional v "withShow" /= Just False
     initial=req $ value v "initial"
     callback current=do
       i<-atomicModifyIORef' indexRef (\i -> (i+1,i))
       log $ object ["event" .= ("callback"::String),"index" .= i,"request" .= viewReq current]
       case optional v "callbackFailAt" of {Just n | n==i -> raise (field v "callbackFailure");_ -> pure ()}
       if i>=length scripts then raise "callback-exhausted" else pure ()
       let s=scripts!!i
       readRef<-newIORef (0::Int);closedRef<-newIORef False
       let actions=maybe [] array (optional s "chunks")
           reader=do
             n<-atomicModifyIORef' readRef (\n -> (n+1,n))
             log $ object ["event" .= ("read"::String),"index" .= i,"chunk" .= n]
             closed<-readIORef closedRef
             if closed then raise $ maybe "content-closed" id (optional s "onClosed") else pure ()
             case drop n actions of {String tag:_ -> raise (fromJust $ parseMaybe parseJSON $ String tag);a:_ -> pure $ chunk a;[] -> pure B.empty}
           close=do
             log $ object ["event" .= ("close"::String),"index" .= i]
             writeIORef closedRef True
             case optional s "closeFailure" of {Just tag -> raise tag;Nothing -> pure ()}
           original=maybe current req (optional s "original")
           res=Response {responseStatus=mkStatus (maybe (300+i) id (optional s "status")) (maybe "message" bs (optional s "message"))
              ,responseVersion=case optional s "version" of {Just [a,b] -> HttpVersion a b;_ -> HttpVersion 1 1}
              ,responseHeaders=maybe [] headers (optional s "headers"),responseBody=reader,responseCookieJar=cookies s
              ,responseClose'=ResponseClose close,responseOriginalRequest=original {requestBody=RequestBodyBS B.empty}
              ,responseEarlyHints=maybe [] headers (optional s "early")}
           next=maybe current req (optional s "next")
       if optional s "preclose" == Just True then close else pure ()
       pure (res,next,field s "redirect")
     execute=do
       (r,res)<-if field v "api" == ("simple"::String) then do
           res<-httpRedirect (field v "count") (\r->do (res,next,yes)<-callback r;pure (res,if yes then Just next else Nothing)) initial
           pure (Nothing,res)
         else do
           (r,res)<-httpRedirect' (field v "count") callback initial
           pure (Just r,res)
       consumed<-if optional v "consumeFinal" == Just True then fmap (Just . map bytes) (brConsume $ responseBody res) else pure Nothing
       if optional v "closeFinal" == Just True then responseClose res else pure ()
       pure $ object ["kind" .= ("success"::String),"request" .= fmap viewReq r,"response" .= object (viewMeta res),"consumed" .= consumed]
 result<-try (execute >>= \x->evaluate (let encoded=encode x in L.length encoded `seq` x)) :: IO (Either SomeException Value)
 logs<-reverse <$> readIORef logRef
 let resultValue=either (viewError withShow) id result
 pure $ object ["result" .= resultValue,"events" .= logs]
main :: IO ()
main = do
 hSetBuffering stdout LineBuffering
 let loop = do
      eof <- hIsEOF stdin
      if eof then pure () else do
       input <- B.getLine
       let parsed=fromJust $ decodeStrict input
       result <- try (run parsed >>= \v -> evaluate (let out=encode v in L.length out `seq` out)) :: IO (Either ErrorCall L.ByteString)
       L.putStrLn $ case result of {Right output -> output;Left (ErrorCall message) -> encode $ object ["projectionError" .= takeWhile (/='\n') message]}
       loop
 loop
