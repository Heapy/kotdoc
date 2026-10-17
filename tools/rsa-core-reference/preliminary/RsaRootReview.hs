module Main where
import qualified Crypto.Random as G
import qualified Crypto.Random.ChaChaDRG as D
import qualified Crypto.PubKey.RSA as R
import qualified Data.ByteString as B
import Control.Exception
main=do
 let g=D.initialize(B.replicate 40 0)
 r<-try(evaluate(fst(G.withDRG g(R.generateBlinder 0)) `seq` (42::Int)))::IO(Either SomeException Int)
 print(either(takeWhile(/='\n').displayException)show r)
