module OriginalPosixGetEnv where
import EnvironmentLookupEffects (withCString,c_getenv,getFileSystemEncoding)
import qualified GHC.Internal.Foreign.C.String.Encoding as Enc
import Foreign.Ptr
import GHC.IO.Exception (IOException(..),IOErrorType(..),ioException)
getEnv :: String -> IO String
getEnv name = lookupEnv name >>= maybe handleError return
  where
    handleError = ioe_missingEnvVar name

lookupEnv :: String -> IO (Maybe String)
lookupEnv name =
    withCString name $ \s -> do
      litstring <- c_getenv s
      if litstring /= nullPtr
        then do enc <- getFileSystemEncoding
                result <- Enc.peekCString enc litstring
                return $ Just result
        else return Nothing


ioe_missingEnvVar :: String -> IO a
ioe_missingEnvVar name = ioException (IOError Nothing NoSuchThing "getEnv"
    "no environment variable" Nothing (Just name))

