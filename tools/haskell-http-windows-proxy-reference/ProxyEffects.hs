{-# LANGUAGE OverloadedStrings #-}
module ProxyEffects where
import Data.Aeson
import qualified Data.Aeson.Key as Key
import Data.IORef
import System.IO.Unsafe (unsafePerformIO)
import Control.Exception
import Data.Char (chr)
import ProxyProjection (field,optional)

{-# NOINLINE config #-}
config :: IORef Value
config = unsafePerformIO $ newIORef Null
{-# NOINLINE events #-}
events :: IORef [Value]
events = unsafePerformIO $ newIORef []
event :: [Value] -> IO ()
event v = modifyIORef' events (toJSON v:)
raise :: String -> IO ()
raise key = do
 v<-readIORef config
 case optional v "failures" >>= (\o -> optional o (fromString key)) of
  Just "io" -> ioError $ userError key
  Just "fatal" -> throwIO $ ErrorCall key
  Just "async" -> throwIO ThreadKilled
  _ -> pure ()
getEnvironment :: IO [(String,String)]
getEnvironment = do
 event [String "environment"]
 raise "environment"
 v<-readIORef config
 pure $ map (\[a,b] -> (map chr a,map chr b)) (field v "env" :: [[[Int]]])

fromString = Key.fromString
