module OriginalPosixEnvironment where
import Foreign
import Foreign.C.String (CString)
import qualified GHC.Internal.Foreign.C.String.Encoding as Enc
import EnvironmentEffects (getEnvBlock,getFileSystemEncoding)
getEnvironment :: IO [(String,String)]
getEnvironment = do
   pBlock <- getEnvBlock
   if pBlock == nullPtr then return []
    else do
      enc <- getFileSystemEncoding
      stuff <- peekArray0 nullPtr pBlock >>= mapM (Enc.peekCString enc)
      return (map divvy stuff)


divvy :: String -> (String, String)
divvy str =
  case break (=='=') str of
    (xs,[])        -> (xs,[]) -- don't barf (like Posix.getEnvironment)
    (name,_:value) -> (name,value)
