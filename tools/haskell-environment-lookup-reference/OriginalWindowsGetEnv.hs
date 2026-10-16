module OriginalWindowsGetEnv where
import EnvironmentLookupEffects (withCWString,allocaArray,c_GetEnvironmentVariable,c_GetLastError,peekCWString,throwGetLastError)
import GHC.IO.Exception (IOException(..),IOErrorType(..),ioException)
import Data.Word
type DWORD = Word32
getEnv :: String -> IO String
getEnv name = lookupEnv name >>= maybe handleError return
  where
    handleError = do
        err <- c_GetLastError
        if err == eRROR_ENVVAR_NOT_FOUND
            then ioe_missingEnvVar name
            else throwGetLastError "getEnv"

eRROR_ENVVAR_NOT_FOUND :: DWORD
eRROR_ENVVAR_NOT_FOUND = 203


lookupEnv :: String -> IO (Maybe String)
lookupEnv name = withCWString name $ \s -> try_size s 256
  where
    try_size s size = allocaArray (fromIntegral size) $ \p_value -> do
      res <- c_GetEnvironmentVariable s p_value size
      case res of
        0 -> return Nothing
        _ | res > size -> try_size s res -- Rare: size increased between calls to GetEnvironmentVariable
          | otherwise  -> peekCWString p_value >>= return . Just


ioe_missingEnvVar :: String -> IO a
ioe_missingEnvVar name = ioException (IOError Nothing NoSuchThing "getEnv"
    "no environment variable" Nothing (Just name))

