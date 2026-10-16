-- Unchanged GHC 9.12.2 mkGlobal and three bindings, parameterized only at the initial values.
-- University of Glasgow; retained licenses/GHC-base-BSD-3-Clause.txt.
module OriginalEncodingGlobals where
import GHC.IO.Encoding (TextEncoding, utf8)
import Data.IORef
import System.IO.Unsafe (unsafePerformIO)

data Globals a = Globals { getLocale :: IO a, setLocale :: a -> IO (), getFilesystem :: IO a,
                           setFilesystem :: a -> IO (), getForeign :: IO a, setForeign :: a -> IO () }
newGlobals initLocaleEncoding initFileSystemEncoding initForeignEncoding =
 Globals getLocaleEncoding setLocaleEncoding getFileSystemEncoding setFileSystemEncoding getForeignEncoding setForeignEncoding
 where
  (getLocaleEncoding, setLocaleEncoding)         = mkGlobal initLocaleEncoding
  (getFileSystemEncoding, setFileSystemEncoding) = mkGlobal initFileSystemEncoding
  (getForeignEncoding, setForeignEncoding)       = mkGlobal initForeignEncoding

mkGlobal :: a -> (IO a, a -> IO ())
mkGlobal x = unsafePerformIO $ do
    x_ref <- newIORef x
    return (readIORef x_ref, writeIORef x_ref)
{-# NOINLINE mkGlobal #-}

windowsArgvEncoding :: IO TextEncoding
windowsArgvEncoding = return utf8
posixArgvEncoding globals = getFileSystemEncoding
 where getFileSystemEncoding = getFilesystem globals
