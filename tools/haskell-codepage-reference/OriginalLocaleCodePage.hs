-- Scoped instance of the unchanged GHC currentCodePage CAF and locale binding.
module OriginalLocaleCodePage where
import Data.Word
import GHC.IO.Encoding.Types
import GHC.IO.Encoding.Failure
import System.IO.Unsafe
import OriginalCodePage (mkCodePageEncoding)
{-# NOINLINE newLocale #-}
newLocale :: IO Word32 -> CodingFailureMode -> TextEncoding
newLocale getCurrentCodePage = mkLocaleEncoding
 where
  {-# NOINLINE currentCodePage #-}
  currentCodePage :: Word32
  currentCodePage = unsafePerformIO getCurrentCodePage
  
  localeEncoding :: TextEncoding
  localeEncoding = mkLocaleEncoding ErrorOnCodingFailure
  
  mkLocaleEncoding :: CodingFailureMode -> TextEncoding
  mkLocaleEncoding cfm = mkCodePageEncoding cfm currentCodePage
