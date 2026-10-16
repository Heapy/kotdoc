{-# LANGUAGE ForeignFunctionInterface #-}
module OriginalWindowsErrors where
import EnvironmentWindowsEffects
import Foreign
import Foreign.C.Types (CInt(..))
import Data.Word
import Data.Char (isSpace)
import Data.List (dropWhileEnd)
import qualified Numeric
import Control.Exception
import GHC.IO.Exception (IOException,IOErrorType(..))
import System.IO.Error (ioeSetErrorString)
type ErrCode = Word32
foreign import ccall unsafe "maperrno_func" c_maperrno_func :: Word32 -> Errno
throwGetLastError :: String -> IO a
throwGetLastError where_from =
    getLastError >>= failWith where_from

-- | Convert a Windows error code to an exception, then throw it.
failWith :: String -> ErrCode -> IO a
failWith fn_name err_code =
    errCodeToIOError fn_name err_code >>= throwIO

-- | Convert a Windows error code to an exception.
errCodeToIOError :: String -> ErrCode -> IO IOError
errCodeToIOError fn_name err_code = do
    msg <- getErrorMessage err_code

    -- turn GetLastError() into errno, which errnoToIOError knows
    -- how to convert to an IOException we can throw.
    -- XXX we should really do this directly.
    let errno = c_maperrno_func err_code

    let msg' = dropWhileEnd isSpace msg -- drop trailing \n
        ioerror = errnoToIOError fn_name errno Nothing Nothing
                    `ioeSetErrorString` msg'
    return ioerror

-- | Get a string describing a Windows error code.  This uses the
-- @FormatMessage@ system call.
getErrorMessage :: ErrCode -> IO String
getErrorMessage err_code =
    mask_ $ do
        c_msg <- c_getErrorMessage err_code
        if c_msg == nullPtr
          then return $ "Error 0x" ++ Numeric.showHex err_code ""
          else do msg <- peekCWString c_msg
                  -- We ignore failure of freeing c_msg, given we're already failing
                  _ <- localFree c_msg
                  return msg

