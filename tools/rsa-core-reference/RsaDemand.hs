{-# LANGUAGE ScopedTypeVariables #-}
module Main where
import qualified Crypto.Random as G
import qualified Crypto.PubKey.RSA as R
import qualified Data.ByteArray as B
import qualified Data.ByteString as S
import Control.Exception
import Data.List(isPrefixOf)
import System.Environment(getArgs)
data Probe = Probe String Int deriving Show
instance G.DRG Probe where
 randomBytesGenerate n (Probe mode step)
  | mode=="root" = error "pair root"
  | otherwise = (if mode=="bytes"then error "bytes field"else B.convert(S.replicate n 1),if mode=="state"then error "state field"else Probe mode(step+1))
observe mode n projection = case projection of
 "ignore" -> show(fst(G.withDRG(Probe mode 0)(fmap(const(42::Int))(R.generateBlinder n))))
 "state" -> show(snd(G.withDRG(Probe mode 0)(R.generateBlinder n)))
 "value" -> show(fst(G.withDRG(Probe mode 0)(R.generateBlinder n)))
main = mapM_ run [(m,n,p)|m<-["root","bytes","state","good"],n<-[-1,0,1,2,3233],p<-["ignore","state","value"]]
 where run (m,n,p)=do
        r<-try(evaluate(length(observe m n p)))::IO(Either SomeException Int)
        putStrLn(show(m,n,p,either(takeWhile(/='\n').displayException)(const(observe m n p))r))
