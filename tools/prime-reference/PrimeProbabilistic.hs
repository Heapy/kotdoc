-- Development-only fixed-secret/DRG dictionary observer. Original derivation is unchanged.
-- Crypto.Random.Probabilistic, crypton 2.0.1, BSD-3-Clause.
module PrimeProbabilistic (probabilisticFrom,resetTrace,readTrace) where
import Crypto.Hash (SHA512(..),hashWith)
import Crypto.Internal.ByteArray (ByteArrayAccess,ScrubbedBytes)
import qualified Crypto.Internal.ByteArray as B
import Crypto.Random
import qualified Crypto.Random.ChaChaDRG as D
import Data.IORef
import System.Environment(lookupEnv)
import Data.Word(Word8)
import System.IO.Unsafe (unsafePerformIO)
import qualified Data.ByteString as BS
traceRef :: IORef [(Int,[Int])]
traceRef = unsafePerformIO(newIORef [])
{-# NOINLINE traceRef #-}
resetTrace = writeIORef traceRef []
readTrace = readIORef traceRef
data TraceDRG = TraceDRG ChaChaDRG
instance DRG TraceDRG where
 randomBytesGenerate n (TraceDRG g) = unsafePerformIO $ do
  let (bytes,next)=randomBytesGenerate n g
  modifyIORef' traceRef (++[(n,map fromIntegral(BS.unpack(B.convert bytes)))])
  return (bytes,TraceDRG next)
probabilisticFrom :: ByteArrayAccess seed => seed -> MonadPseudoRandom TraceDRG a -> a
probabilisticFrom material f = fst $ withDRG drg f
 where
  drg = TraceDRG (D.initialize (B.take seedLength (B.convert digest :: ScrubbedBytes)))
  digest = hashWith SHA512 (B.append secret (B.convert material) :: ScrubbedBytes)
  secret = fixedSecret
  seedLength = 40

-- One explicit secret per observer process, never changed between requests.
fixedSecret :: ScrubbedBytes
fixedSecret = unsafePerformIO $ do
 value <- lookupEnv "KOTDOC_PRIME_OBSERVER_SECRET"
 return (B.pack (maybe [0..31] read value :: [Word8]))
{-# NOINLINE fixedSecret #-}
