{-# LANGUAGE OverloadedStrings #-}
module LookupEffects where
import Control.Exception
import Foreign
import Data.Aeson
import Data.Aeson.Types
import qualified Data.Aeson.Key as K
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as L
import Data.IORef
import System.IO.Unsafe
field :: FromJSON a => Value -> String -> a
field v k = maybe (error k) id $ optional v k
optional :: FromJSON a => Value -> String -> Maybe a
optional v k = parseMaybe (withObject "object" (.: K.fromString k)) v
{-# NOINLINE config #-}
config :: IORef Value
config=unsafePerformIO $ newIORef Null
{-# NOINLINE logs #-}
logs :: IORef [Value]
logs=unsafePerformIO $ newIORef []
{-# NOINLINE valuePtr #-}
valuePtr :: IORef (Ptr Word8)
valuePtr=unsafePerformIO $ newIORef nullPtr
{-# NOINLINE messagePtr #-}
messagePtr :: IORef (Ptr Word16)
messagePtr=unsafePerformIO $ newIORef nullPtr
{-# NOINLINE replies #-}
replies :: IORef [Value]
replies=unsafePerformIO $ newIORef []
{-# NOINLINE errorCodes #-}
errorCodes :: IORef [Word32]
errorCodes=unsafePerformIO $ newIORef []
emit v=evaluate (L.length $ encode v) >> modifyIORef' logs (v:)
raise tag | take 3 tag=="io:" = ioError $ userError tag
          | otherwise = throwIO $ AssertionFailed tag
check key=readIORef config >>= \v->maybe (pure ()) raise $ optional v key
