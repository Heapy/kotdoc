-- Unchanged POSIX GHC 9.12.2 mkTextEncoding' and unknownEncodingErr.
-- University of Glasgow; complete licenses/GHC-base-BSD-3-Clause.txt.
module OriginalWindowsEncoding where
import Data.Char (toUpper)
import GHC.IO.Exception (ioException,IOException(..),IOErrorType(..))
import GHC.IO.Encoding.Types (TextEncoding)
import GHC.IO.Encoding.Failure (CodingFailureMode(..),codingFailureModeSuffix)
import qualified GHC.IO.Encoding.UTF8 as UTF8
import qualified GHC.IO.Encoding.UTF16 as UTF16
import qualified GHC.IO.Encoding.UTF32 as UTF32
import qualified GHC.IO.Encoding.Latin1 as Latin1
import qualified OriginalCodePage as CodePage

mkTextEncoding' :: CodingFailureMode -> String -> IO TextEncoding
mkTextEncoding' cfm enc =
  case [toUpper c | c <- enc, c /= '-'] of
  -- UTF-8 and friends we can handle ourselves
    "UTF8"    -> return $ UTF8.mkUTF8 cfm
    "UTF16"   -> return $ UTF16.mkUTF16 cfm
    "UTF16LE" -> return $ UTF16.mkUTF16le cfm
    "UTF16BE" -> return $ UTF16.mkUTF16be cfm
    "UTF32"   -> return $ UTF32.mkUTF32 cfm
    "UTF32LE" -> return $ UTF32.mkUTF32le cfm
    "UTF32BE" -> return $ UTF32.mkUTF32be cfm
    -- On AIX, we want to avoid iconv, because it is either
    -- a) totally broken, or b) non-reentrant, or c) actually works.
    -- Detecting b) is difficult as you'd have to trigger the reentrancy
    -- corruption.
    -- Therefore, on AIX, we handle the popular ASCII and latin1 encodings
    -- ourselves. For consistency, we do the same on other platforms.
    -- We use `mkLatin1_checked` instead of `mkLatin1`, since the latter
    -- completely ignores the CodingFailureMode (TEST=encoding005).
    _ | isAscii -> return (Latin1.mkAscii cfm)
    _ | isLatin1 -> return (Latin1.mkLatin1_checked cfm)
    'C':'P':n | [(cp,"")] <- reads n -> return $ CodePage.mkCodePageEncoding cfm cp
    _ -> unknownEncodingErr (enc ++ codingFailureModeSuffix cfm)

  where
    isAscii = enc `elem` asciiEncNames
    isLatin1 = enc `elem` latin1EncNames
    asciiEncNames = -- ASCII aliases specified by RFC 1345 and RFC 3808.
      [ "ANSI_X3.4-1968", "iso-ir-6", "ANSI_X3.4-1986", "ISO_646.irv:1991"
      , "US-ASCII", "us", "IBM367", "cp367", "csASCII", "ASCII", "ISO646-US"
      ]
    latin1EncNames = -- latin1 aliases specified by RFC 1345 and RFC 3808.
      [ "ISO_8859-1:1987", "iso-ir-100", "ISO_8859-1", "ISO-8859-1", "latin1",
        "l1", "IBM819", "CP819", "csISOLatin1"
      ]


unknownEncodingErr :: String -> IO a
unknownEncodingErr e = ioException (IOError Nothing NoSuchThing "mkTextEncoding"
                                            ("unknown encoding:" ++ e)  Nothing Nothing)
