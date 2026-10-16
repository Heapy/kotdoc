{-# LANGUAGE OverloadedStrings #-}
module ConnectionTlsEffects
 (module TLS, Context(..), contextNew, handshake, recvData, sendData, bye, contextClose,
  config, chunks, events, reset, emit) where
import Network.TLS as TLS hiding (Context, contextNew, handshake, recvData, sendData, bye, contextClose)
import Control.Exception
import Data.Aeson
import Data.Aeson.Types
import Data.IORef
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as L
import System.IO.Unsafe
data Context = Context
{-# NOINLINE config #-}
config :: IORef Value
config = unsafePerformIO $ newIORef Null
{-# NOINLINE chunks #-}
chunks :: IORef [B.ByteString]
chunks = unsafePerformIO $ newIORef []
{-# NOINLINE events #-}
events :: IORef [Value]
events = unsafePerformIO $ newIORef []
reset = writeIORef config Null >> writeIORef chunks [] >> writeIORef events []
emit v = evaluate (L.length $ encode v) >> modifyIORef' events (v:)
effect name = do
 emit $ toJSON name
 v <- readIORef config
 let failures = maybe [] id (parseMaybe (withObject "command" (.: "failures")) v) ++
                maybe [] (:[]) (parseMaybe (withObject "command" (.: "failure")) v)
 if name ++ "-io" `elem` failures then ioError $ userError $ name ++ "-io"
 else if name ++ "-fatal" `elem` failures then throwIO $ AssertionFailed $ name ++ "-fatal"
 else pure ()
contextNew :: HasBackend b => b -> ClientParams -> IO Context
contextNew _ params = do
 let (host,port) = clientServerIdentification params
 emit $ object ["contextNew" .= map fromEnum host,"port" .= B.unpack port]
 effect "contextNew"
 pure Context
handshake _ = effect "handshake"
recvData _ = do
 effect "receive"
 atomicModifyIORef' chunks $ \xs -> case xs of [] -> ([],B.empty); x:rest -> (rest,x)
sendData _ bytes = emit (object ["send" .= B.unpack (L.toStrict bytes)]) >> effect "send"
bye _ = effect "bye"
contextClose _ = effect "close"
