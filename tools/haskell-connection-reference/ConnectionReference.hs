{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, CPP #-}
#if __GLASGOW_HASKELL__ != 912 || __GLASGOW_HASKELL_PATCHLEVEL1__ != 2 || __GLASGOW_HASKELL_PATCHLEVEL2__ != 0
#error GHC 9.12.2 required
#endif
module Main where
import qualified OriginalConnection as C
import qualified OriginalConnectionTypes as T
import Control.Exception
import Control.Concurrent.MVar
import Control.Monad
import Data.Aeson
import qualified Data.Aeson.Key
import Data.Aeson.Types (parseMaybe)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Map as M
import qualified Network.TLS as TLS
import qualified ConnectionTlsEffects as F
import Data.IORef
import System.IO
import System.IO.Error
import System.Directory

field :: FromJSON a => Value -> String -> a
field v k = maybe (error $ "missing " ++ k) id $ parseMaybe (withObject "object" (.: fromString k)) v
 where fromString = Data.Aeson.Key.fromString
optional :: FromJSON a => Value -> String -> Maybe a
optional v k = parseMaybe (withObject "object" (.: fromString k)) v
 where fromString = Data.Aeson.Key.fromString
bytes :: Value -> B.ByteString
bytes = B.pack . fieldBytes
 where fieldBytes v = case fromJSON v of Success x -> x; Error x -> error x
view b = toJSON $ B.unpack b
failure (e :: SomeException) = case fromException e of
 Just io -> object ["error" .= ("io" :: String),"type" .= show (ioeGetErrorType io),
                    "location" .= ioeGetLocation io,"description" .= ioeGetErrorString io,
                    "filename" .= ioeGetFileName io]
 Nothing -> case fromException e of
  Just C.LineTooLong -> object ["error" .= ("LineTooLong" :: String)]
  Nothing -> object ["error" .= ("exception" :: String), "message" .= displayException e]

-- Complete original algorithms; only the observer controls the MVar input cell
-- and the real file contents. No expected buffer algorithm is reimplemented.
run v | optional v "factory" == Just True = runFactory v
run v | optional v "session" == Just True = runSession v
run v = bracket (openBinaryTempFile "/tmp" "kotdoc-connection") cleanup $ \(path,h) -> do
 F.reset
 writeIORef F.chunks $ map bytes $ maybe [] id $ optional v "tlsChunks"
 B.hPut h $ bytes $ field v "input"
 hSeek h AbsoluteSeek 0
 let host = map toEnum (maybe (map fromEnum "host") id $ optional v "host")
     port = fromInteger (maybe 443 id $ optional v "port" :: Integer)
 conn <- C.connectionNew (host,port) (if optional v "tls" == Just True then T.ConnectionTLS F.Context else T.ConnectionStream h)
 case optional v "buffer" :: Maybe Value of
  Nothing -> pure ()
  Just Null -> modifyMVar_ (T.connectionBuffer conn) $ const $ pure Nothing
  Just b -> modifyMVar_ (T.connectionBuffer conn) $ const $ pure $ Just $ bytes b
 results <- forM (field v "commands" :: [Value]) $ \cmd -> do
  writeIORef F.config cmd
  r <- try $ case field cmd "op" :: String of
   "get" -> view <$> C.connectionGet conn (field cmd "size")
   "exact" -> view <$> C.connectionGetExact conn (field cmd "size")
   "chunk" -> view <$> C.connectionGetChunk conn
   "line" -> view <$> C.connectionGetLine (field cmd "limit") conn
   "wait" -> toJSON <$> C.connectionWaitForInput conn (field cmd "milliseconds")
   "upgrade" -> do
    let params = if optional cmd "paramsFail" == Just True then throw (userError "params-fail")
                 else T.TLSSettings (TLS.defaultParamsClient "ignored" B.empty)
    C.connectionSetSecure (error "context-forced") conn params
    pure Null
   "put" -> C.connectionPut conn (bytes $ field cmd "bytes") >> pure Null
   "secure" -> toJSON <$> C.connectionIsSecure conn
   "close" -> C.connectionClose conn >> pure Null
   "split" -> do
    let n = field cmd "size"
        mode = maybe "normal" id $ optional cmd "mode"
        split b | mode == "pair-fail" = throw $ userError "pair-fail"
                | otherwise = (if mode == "value-fail" then throw $ userError "value-fail" else B.take n b,
                               if mode == "rest-fail" then throw $ userError "rest-fail" else B.drop n b)
    b <- C.connectionGetChunk' conn split
    evaluate $ B.length b
    pure $ view b
   _ -> error "unknown op"
  buf <- readMVar $ T.connectionBuffer conn
  pure $ object ["result" .= either failure id r,"buffer" .= fmap view buf]
 es <- reverse <$> readIORef F.events
 pure $ object ["steps" .= results,"events" .= es]
 where cleanup (path,h) = (hClose h `catch` \(_::IOException) -> pure ()) `finally` removeFile path

runFactory v = bracket (openBinaryTempFile "/tmp" "kotdoc-connection") cleanup $ \(path,h) -> do
 F.reset
 writeIORef F.config v
 let settings = if optional v "paramsFail" == Just True then throw (userError "params-fail")
                else T.TLSSettings (TLS.defaultParamsClient "ignored" B.empty)
     secure = if optional v "secure" == Just True then Just settings else Nothing
     params = T.ConnectionParams "factory" 123 secure (throw $ userError "socks-forced")
 r <- try $ do
  connection <- C.connectFromHandle (error "context-forced") h params
  before <- hIsClosed h
  tls <- C.connectionIsSecure connection
  C.connectionClose connection
  after <- hIsClosed h
  pure $ object ["secure" .= tls,"closedBefore" .= before,"closedAfter" .= after]
 closed <- hIsClosed h
 es <- reverse <$> readIORef F.events
 pure $ object ["result" .= either failure id r,"streamClosed" .= closed,"events" .= es]
 where cleanup (path,h) = (hClose h `catch` \(_::IOException) -> pure ()) `finally` removeFile path

runSession v = do
 cell <- newMVar M.empty
 let manager = C.connectionSessionManager cell
 results <- forM (field v "commands" :: [Value]) $ \cmd -> do
  let key = bytes $ field cmd "id"
      viewSession action = do
       session <- action
       case session of
        Nothing -> pure Null
        Just s -> evaluate s >> error "session sentinel unexpectedly forced"
  r <- try $ case field cmd "op" :: String of
   "establish" -> do
    result <- TLS.sessionEstablish manager key (throw $ userError $ field cmd "value")
    pure $ toJSON $ maybe False (const True) result
   "resume" -> viewSession $ TLS.sessionResume manager key
   "once" -> viewSession $ TLS.sessionResumeOnlyOnce manager key
   "invalidate" -> TLS.sessionInvalidate manager key >> pure Null
   _ -> error "session op"
  pure $ either failure id r
 pure $ object ["steps" .= results,"useTicket" .= TLS.sessionUseTicket manager]

main = do
 hSetBuffering stdout LineBuffering
 input <- L.getContents
 forM_ (L.lines input) $ \line -> case eitherDecode line of
  Left e -> error e
  Right v -> run v >>= L.putStrLn . encode
