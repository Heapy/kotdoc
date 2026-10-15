module ProxyEnvironment where
import Data.IORef
import System.IO.Unsafe (unsafePerformIO)
{-# NOINLINE environment #-}
environment :: IORef [(String,String)]
environment = unsafePerformIO $ newIORef []
getEnvironment :: IO [(String,String)]
getEnvironment = readIORef environment
