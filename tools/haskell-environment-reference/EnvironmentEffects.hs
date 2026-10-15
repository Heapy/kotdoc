module EnvironmentEffects where
import Foreign
import Foreign.C.String (CString)
import Data.IORef
import System.IO.Unsafe
import GHC.Internal.IO.Encoding (mkTextEncoding)
{-# NOINLINE block #-}
block :: IORef (Ptr CString)
block = unsafePerformIO $ newIORef nullPtr
{-# NOINLINE wideBlock #-}
wideBlock :: IORef (Ptr Word16)
wideBlock = unsafePerformIO $ newIORef nullPtr
{-# NOINLINE encodingName #-}
encodingName :: IORef String
encodingName = unsafePerformIO $ newIORef "UTF-8"
getEnvBlock = readIORef block
getFileSystemEncoding = readIORef encodingName >>= mkTextEncoding . (++"//ROUNDTRIP")
c_GetEnvironmentStrings = readIORef wideBlock
c_FreeEnvironmentStrings :: Ptr Word16 -> IO Bool
c_FreeEnvironmentStrings p = pure (p/=nullPtr)
