module Main where
import qualified Crypto.Random as G
import qualified Crypto.PubKey.RSA as R
import qualified Data.ByteArray as BA
import qualified Data.ByteString as B
import Control.Exception
data Probe=Probe Int
instance G.DRG Probe where
 randomBytesGenerate n (Probe i) | i>=5000=error "budget"
                               | otherwise=(BA.convert(B.replicate n (if even i then 0 else 2)),Probe(i+1))
main=do
 r<-try(evaluate(fst(G.withDRG(Probe 0)(R.generate 2 2))))::IO(Either SomeException(R.PublicKey,R.PrivateKey))
 putStrLn(either(takeWhile(/='\n').displayException)(const "returned")r)
