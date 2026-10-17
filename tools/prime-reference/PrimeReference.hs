{-# LANGUAGE ScopedTypeVariables #-}
module Main where
import qualified Crypto.Number.Prime as P
import qualified ObservedPrime as O
import qualified Crypto.Number.Compat as C
import qualified PrimeProbabilistic as T
import qualified ProcessPrime as Q
import qualified ProcessProbabilistic as S
import qualified Data.ByteArray as BA
import Control.Exception
import System.IO
import Data.IORef
import System.IO.Unsafe(unsafePerformIO)
import qualified Crypto.Random as R
import qualified Crypto.Random.ChaChaDRG as D
import qualified Data.ByteString as B
import Data.Word
import qualified Crypto.Number.ModArithmetic as M
import Test.QuickCheck(choose)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
data Outer = Outer D.ChaChaDRG [Int]
instance R.DRG Outer where
 randomBytesGenerate n (Outer g tr) = let (b,next)=R.randomBytesGenerate n g in (b,Outer next (tr++[n]))
generated safe bits seed = let
 g=D.initialize(B.pack [fromIntegral ((seed+i) `mod` 256)|i<-[0..39]] :: B.ByteString)
 action=if safe then O.generateSafePrime bits else O.generatePrime bits
 (p,Outer final trace)=R.withDRG(Outer g []) action
 (probe,_)=R.randomBytesGenerate 65 final :: (B.ByteString,D.ChaChaDRG)
 in show(p,trace,B.unpack probe)
generatedWords safe bits ws = let
 g=case map read ws of [a,b,c,d,e]->D.initializeWords(a,b,c,d,e);_->error "words"
 action=if safe then O.generateSafePrime bits else O.generatePrime bits
 (p,Outer final trace)=R.withDRG(Outer g []) action
 (probe,_)=R.randomBytesGenerate 65 final :: (B.ByteString,D.ChaChaDRG)
 in (p,trace,B.unpack probe)
main = do
 hSetBuffering stdout LineBuffering
 contents <- getContents
 mapM_ dispatch (lines contents)
dispatch line = do
 T.resetTrace
 r <- try (evaluate (force (run (words line)))) :: IO (Either SomeException String)
 trace <- T.readTrace
 putStrLn ((either (\e -> "error:" ++ takeWhile (/='\n') (displayException e)) id r) ++ "|" ++ show trace)
 where force s = length s `seq` s
run ["demandMR",t,n] = show(P.primalityTestMillerRabin(arg "tries" t)(arg "n" n))
run ["demandFermat",t,a,n] = show(P.primalityTestFermat(arg "tries" t)(arg "a" a)(arg "p" n))
run ["wordsGenerate",b,a,c,d,e,f] = show(generatedWords False(read b)[a,c,d,e,f])
run ["wordsSafe",b,a,c,d,e,f] = show(generatedWords True(read b)[a,c,d,e,f])
run ["square",b,a,c,d,e,f,seed] = let
 (p,trace,probe)=generatedWords False(read b)[a,c,d,e,f]
 g=unGen(choose(1,p-1))(mkQCGen(read seed))100
 in show(p,g,M.squareRoot p g,M.jacobi g p,trace,probe)
run ["generate",b,seed] = generated False (read b)(read seed)
run ["safe",b,seed] = generated True (read b)(read seed)
run ["fixedProbably",n] = show(O.isProbablyPrime(read n))
run ["fixedFind",n] = show(O.findPrimeFrom(read n))
run ["findWith",n,m,r] = show(O.findPrimeFromWith(\p -> p `mod` read m == read r)(read n))
run ["findTrace",rounds,n,m,r] = unsafePerformIO $ do
 ref <- newIORef []
 let predicate x = unsafePerformIO $ do
      modifyIORef' ref (++[x])
      return (x `mod` read m == read r)
 result <- evaluate(O.findPrimeFromWithRounds(read rounds)predicate(read n))
 calls <- readIORef ref
 return (show(result,calls))
run ["probablyRounds",r,n] = show(O.probablyPrime(read r)(read n))
run ["findDrawn",r,n] = show(O.findPrimeFromDrawn(read r)(read n))
run ["findRounds",r,n,m,v] = show(O.findPrimeFromWithRounds(read r)(\p -> p `mod` read m == read v)(read n))
run ["divides",a,b] = show(O.divides(read a)(read b))
run ["table"] = show O.firstPrimes
run ["rounds",b] = show(O.roundsForDrawn(read b))
run ["naive",n] = show(P.primalityTestNaive(read n))
run ["fermat",t,a,n] = show(P.primalityTestFermat(read t)(read a)(read n))
run ["coprime",a,b] = show(P.isCoprime(read a)(read b))
run ["find",n] = show(P.findPrimeFrom(read n))
run ["probably",n] = show(P.isProbablyPrime(read n))
run ["mr",t,n] = show(Q.primalityTestMillerRabin(read t)(read n))
run ["secret"] = show(B.unpack(BA.convert S.secret))
run ["fixed",t,n] = show(O.primalityTestMillerRabin(read t)(read n))
run ["compat",n] = case C.gmpNextPrime(read n) of C.GmpUnsupported -> "unsupported";C.GmpSupported x -> show x
run _ = error "request"

arg :: Read a => String -> String -> a
arg label "bottom" = error(label ++ " demanded")
arg _ value = read value
