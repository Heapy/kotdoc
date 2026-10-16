{-# LANGUAGE CPP, OverloadedStrings, ScopedTypeVariables, DeriveDataTypeable #-}
#if __GLASGOW_HASKELL__ != 912 || __GLASGOW_HASKELL_PATCHLEVEL1__ != 2 || __GLASGOW_HASKELL_PATCHLEVEL2__ != 0
#error GHC 9.12.2 required
#endif
module Main where
import Network.HTTP.Client.Internal
import Data.KeyedPool
import Network.HTTP.Types (mkStatus)
import Control.Exception
import Control.Monad
import qualified Data.Aeson as J
import Data.Aeson (Value(..),(.=),object,toJSON)
import qualified Data.Aeson.KeyMap as K
import qualified Data.Aeson.Key as Key
import Data.Aeson.Types (parseMaybe)
import qualified Data.ByteString as B
import qualified Data.ByteString.Char8 as S
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.CaseInsensitive as CI
import qualified Data.Map as M
import qualified Data.Text as T
import Data.Char (ord,toLower)
import Data.Maybe (fromJust)
import Data.IORef
import Foreign.StablePtr
import Data.Typeable (Typeable)
import System.IO
import System.IO.Error
import qualified System.Environment as E

field :: J.FromJSON a => Value -> J.Key -> a
field (Object o) k = fromJust (K.lookup k o >>= parseMaybe J.parseJSON)
field _ _ = error "expected object"
optional :: J.FromJSON a => Value -> J.Key -> Maybe a
optional (Object o) k = case K.lookup k o of
 Just Null -> Nothing
 Just x -> parseMaybe J.parseJSON x
 Nothing -> Nothing
optional _ _ = Nothing
value :: Value -> J.Key -> Value
value (Object o) k = fromJust $ K.lookup k o
value _ _ = error "object"
bs :: Value -> B.ByteString
bs = B.pack . fromJust . parseMaybe J.parseJSON
bytes = B.unpack
array :: Value -> [Value]
array = fromJust . parseMaybe J.parseJSON
readProxy v = Proxy (bs $ value v "host") (field v "port")
headers v = [(CI.mk $ bs a,bs b) | [a,b]<-map array $ array v]
req v = defaultRequest {host=maybe "localhost" bs (optional v "host"),port=maybe 80 id (optional v "port")
 ,secure=maybe False id (optional v "secure"),hostAddress=optional v "address",proxy=readProxy <$> optional v "proxy"
 ,requestHeaders=maybe [] headers (optional v "headers"),method=maybe "GET" bs (optional v "method")
 ,proxySecureMode=if optional v "connect"==Just False then ProxySecureWithoutConnect else ProxySecureWithConnect
 ,requestBody=if optional v "latentBody"==Just True then RequestBodyIO (error "body-forced") else RequestBodyLBS L.empty}
viewReq r = object ["host" .= bytes (host r),"port" .= port r,"secure" .= secure r,"address" .= hostAddress r
 ,"proxy" .= fmap show (proxy r),"connect" .= (proxySecureMode r==ProxySecureWithConnect)
 ,"headers" .= [[bytes $ CI.original a,bytes b] | (a,b)<-requestHeaders r],"method" .= bytes (method r),"show" .= show r]
key v = case field v "kind" :: String of
 "raw" -> CKRaw (optional v "address") (bs $ value v "host") (field v "port")
 "secure" -> CKSecure (optional v "address") (bs $ value v "host") (field v "port")
 "proxy" -> CKProxy (bs $ value v "host") (field v "port") (bs <$> optional v "auth") (bs $ value v "ultimateHost") (field v "ultimatePort")
 _ -> error "key kind"
data ObserverFailure = ObserverFailure String deriving (Show,Typeable)
instance Exception ObserverFailure
exception :: String -> SomeException
exception tag = case tag of
 "io" -> toException $ ioeSetErrorString (mkIOError userErrorType "script-location" Nothing Nothing) "script-io"
 "async" -> toException ThreadKilled
 "incomplete" -> toException $ HttpExceptionContentWrapper IncompleteHeaders
 "nodata" -> toException $ HttpExceptionContentWrapper NoResponseDataReceived
 "timeout" -> toException $ HttpExceptionContentWrapper ResponseTimeout
 "tls" -> toException $ HttpExceptionContentWrapper TlsNotSupported
 "closed" -> toException $ HttpExceptionContentWrapper ConnectionClosed
 "request-nodata" -> toException $ HttpExceptionRequest defaultRequest NoResponseDataReceived
 "request-incomplete" -> toException $ HttpExceptionRequest defaultRequest IncompleteHeaders
 "nested-connection" -> toException $ HttpExceptionContentWrapper $ ConnectionFailure $ exception "io"
 "nested-internal" -> toException $ HttpExceptionContentWrapper $ InternalException $ exception "io"
 _ -> toException $ ObserverFailure tag
failure :: Bool -> SomeException -> Value
failure withShow e = object $ detail ++ if withShow then ["show" .= show e] else []
 where
 detail
  | Just content <- unHttpExceptionContentWrapper <$> fromException e =
      ["kind" .= ("content"::String),"content" .= show content,"prec11" .= showsPrec 11 content "TAIL"]
  | Just (HttpExceptionRequest _ content) <- fromException e = ["kind" .= ("request"::String),"content" .= show content]
  | Just (_::IOException) <- fromException e = ["kind" .= ("io"::String)]
  | Just ThreadKilled <- fromException e = ["kind" .= ("async"::String)]
  | Just (ObserverFailure tag) <- fromException e = ["kind" .= ("fatal"::String),"tag" .= tag]
  | otherwise = error $ "unknown error "++show e
observe action = do
 result<-try $ do {v<-action;_<-evaluate $ L.length $ J.encode v;pure v}
 pure $ either (\e -> object ["error" .= failure True e]) id result
viewSettings s = object ["count" .= managerConnCount s,"idle" .= managerIdleConnectionCount s,"timeout" .= show (managerResponseTimeout s)
 ,"maxLength" .= fmap unMaxHeaderLength (managerMaxHeaderLength s),"maxHeaders" .= fmap unMaxNumberHeaders (managerMaxNumberHeaders s)]

run :: Value -> IO Value
run v = do
 logs<-newIORef [];next<-newIORef (0::Int);roots<-newIORef ([]::[Connection])
 managedRoots<-newIORef ([]::[Managed Connection])
 let log x=modifyIORef' logs (x:)
     event name xs=log $ object (("event" .= (name::String)):xs)
     raise tag=throwIO $ exception tag
     config which=maybe (object []) id (optional v (Key.fromString which))
     selectedFailure cfg name=maybe (pure ()) raise (optional cfg name)
     connect which addr h p maybeProxy = do
       i<-atomicModifyIORef' next (\n -> (n+1,n+1))
       let cfg=config which
       event "connect" $ ["kind" .= which,"id" .= i,"address" .= addr,"host" .= map ord h,"port" .= p] ++ maybe [] (\(wire,ultimate) -> ["wire" .= bytes wire,"ultimate" .= map ord ultimate]) maybeProxy
       selectedFailure cfg "failure"
       chunks<-newIORef $ maybe [toJSON $ bytes "HTTP/1.1 200 OK\r\n\r\n",toJSON $ bytes "body"] array (optional cfg "chunks")
       let read=do
             event "read" ["id" .= i]
             xs<-readIORef chunks
             case xs of
              [] -> pure B.empty
              x:rest -> do
               writeIORef chunks rest
               case x of {String t -> raise (T.unpack t);_ -> pure $ bs x}
           write b=event "write" ["id" .= i,"bytes" .= bytes b] >> selectedFailure cfg "writeFailure"
           close=event "close" ["id" .= i] >> selectedFailure cfg "closeFailure"
       connection<-makeConnection read write close
       modifyIORef' roots (connection:)
       pure connection
     initFactory which action=do
       event "factory" ["kind" .= which]
       selectedFailure (config which) "initFailure"
       pure action
     proxyFactory wire parse ultimate addr h p=do
       connection<-connect "proxy" addr h p (Just (wire,ultimate))
       replicateM_ (maybe 1 id $ optional (config "proxy") "parseCount") (parse connection)
       pure connection
     policy which=ProxyOverride $ \secure' -> do
       event "proxy-prepare" ["kind" .= which,"secure" .= secure']
       selectedFailure (config which) "initFailure"
       pure $ \r -> case optional (config which) "proxy" of
          Just p -> r {proxy=Just $ readProxy p}
          Nothing -> r
     settings=defaultManagerSettings {
       managerRawConnection=initFactory "raw" (\a h p -> connect "raw" a h p Nothing)
      ,managerTlsConnection=if optional v "defaultTls"==Just True then managerTlsConnection defaultManagerSettings else initFactory "tls" (\a h p -> connect "tls" a h p Nothing)
      ,managerTlsProxyConnection=if optional v "defaultTls"==Just True then managerTlsProxyConnection defaultManagerSettings else initFactory "proxy" proxyFactory
      ,managerProxyInsecure=policy "http",managerProxySecure=policy "https"
      ,managerConnCount=maybe 10 id (optional v "count"),managerIdleConnectionCount=maybe 512 id (optional v "idle")
      ,managerMaxHeaderLength=if optional v "unlimitedLength"==Just True then Nothing else Just $ MaxHeaderLength $ maybe 4096 id (optional v "maxLength")
      ,managerMaxNumberHeaders=if optional v "unlimitedHeaders"==Just True then Nothing else Just $ MaxNumberHeaders $ maybe 100 id (optional v "maxHeaders")}
     applyOverride name p=case name :: String of
       "request" -> proxyFromRequest
       "none" -> noProxy
       "use" -> useProxy p
       "without-connect" -> useProxySecureWithoutConnect p
       _ -> error "policy"
 bracket (newStablePtr (roots,managedRoots)) freeStablePtr $ \_ -> do
  result<-case field v "api" :: String of
   "key" -> pure $ object ["key" .= show (connKey $ req $ value v "request"),"prec11" .= showsPrec 11 (connKey $ req $ value v "request") "TAIL"
                           ,"drop" .= viewReq (dropProxyAuthSecure $ req $ value v "request")]
   "compare" -> pure $ object ["eq" .= (key (value v "left")==key (value v "right")),"order" .= show (compare (key $ value v "left") (key $ value v "right"))]
   "defaults" -> pure $ viewSettings defaultManagerSettings
   "retry" -> pure $ toJSON $ managerRetryableException defaultManagerSettings $ exception (field v "exception")
   "wrap" -> observe $ managerWrapException defaultManagerSettings (req $ value v "request") $ maybe (pure (value v "result")) raise (optional v "exception" :: Maybe String)
   "override" -> do
      f<-runProxyOverride (applyOverride (field v "policy") (readProxy $ value v "proxy")) (field v "secure")
      pure $ toJSON $ map (viewReq . f . req) (array $ value v "requests")
   "factory" -> do
      made<-try $ mkCreateConnection settings
      case made of
       Left e -> pure $ object ["constructionError" .= failure True e]
       Right create -> do
        results<-forM (array $ value v "keys") $ \k -> observe $ create (key k) >> pure (Bool True)
        pure $ object ["results" .= results]
   "manager" -> do
      made<-try $ newManager settings
      case made of
       Left e -> pure $ object ["constructionError" .= failure True e]
       Right manager -> bracket (newStablePtr manager) freeStablePtr $ \_ -> do
        handles<-newIORef M.empty
        results<-forM (array $ value v "commands") $ \command -> observe $ case field command "op" :: String of
          "take" -> do
            handle<-getConn (req $ value command "request") manager
            modifyIORef' managedRoots (handle:)
            modifyIORef' handles (M.insert (field command "as" :: String) handle)
            pure $ object ["reused" .= managedReused handle]
          "release" -> do
            handle<-(M.! (field command "handle" :: String)) <$> readIORef handles
            managedRelease handle $ if field command "reuse" then Reuse else DontReuse
            pure Null
          "write" -> do
            handle<-(M.! (field command "handle" :: String)) <$> readIORef handles
            connectionWrite (managedResource handle) (bs $ value command "bytes")
            pure Null
          "read" -> do
            handle<-(M.! (field command "handle" :: String)) <$> readIORef handles
            toJSON . bytes <$> connectionRead (managedResource handle)
          "close" -> closeManager manager >> pure Null
          "proxy" -> pure $ viewReq $ mSetProxy manager $ req $ value command "request"
          _ -> error "command"
        pure $ object ["results" .= results,"timeout" .= show (mResponseTimeout manager)
          ,"maxLength" .= fmap unMaxHeaderLength (mMaxHeaderLength manager),"maxHeaders" .= fmap unMaxNumberHeaders (mMaxNumberHeaders manager)]
   _ -> error "api"
  events<-reverse <$> readIORef logs
  let output=object ["result" .= result,"events" .= events]
  _<-evaluate $ L.length $ J.encode output
  pure output

main :: IO ()
main = do
 hSetBuffering stdout LineBuffering
 L.getContents >>= mapM_ (\line -> case J.eitherDecode line of {Left e -> error e;Right v -> run v >>= L.putStrLn . J.encode}) . L.lines
