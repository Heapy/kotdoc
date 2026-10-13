{-# LANGUAGE CPP #-}
module Main where
import Codec.Picture.Jpg.Internal.FastDct(referenceDct)
import qualified Data.Vector.Storable as V
import Control.Monad.ST(runST)
import Control.Exception(SomeException,try,evaluate,displayException)
import Control.DeepSeq(force)
import System.Environment(getArgs)
import System.Info(compilerVersion)
import Data.Version(showVersion)

type Request=(String,[Int],[Int])
runBlock initial values=runST $ do
 input<-V.thaw(V.fromList(map fromIntegral values))
 work<-V.thaw(V.fromList(map fromIntegral initial))
 result<-referenceDct work input
 out<-V.freeze result
 after<-V.freeze input
 pure (V.toList out,V.toList after)
process :: Request -> String
process(op,initial,values)
 | op=="constants"="{\"coefficients\":"++show[fst(runBlock initial(replicate 64 n))|n<-values]++"}"
 | otherwise=let (coeffs,input)=runBlock initial values
             in "{\"coefficients\":"++show coeffs++",\"input\":"++show input++"}"
main=do
 args<-getArgs
 if args==["--reference-info"] then print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector]
 else getContents >>= mapM_ respond . lines
 where respond s=do
        result<-try(evaluate(force(process(read s))))::IO(Either SomeException String)
        putStrLn$either(\e->"{\"exception\":"++show(takeWhile(/='\n')(displayException e))++"}")id result
