module System.Win32.Types where
import Data.Word
import Data.Char (ord)
import Foreign.Ptr
import Foreign.Marshal.Array
type DWORD=Word32
type BYTE=Word8
type LPCTSTR=Ptr Word16
withTString s=withArray0 0 (map (fromIntegral . ord) s)
