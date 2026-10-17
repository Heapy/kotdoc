{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified Crypto.Random as G
import qualified Crypto.PubKey.RSA as R
import qualified Data.ByteArray as B
import qualified Data.ByteString as S
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.IORef
import Control.Exception
import Control.DeepSeq
import System.IO.Unsafe(unsafePerformIO)

data Probe=Probe String Int deriving Show
calls :: IORef [Int]
calls=unsafePerformIO(newIORef [])
{-# NOINLINE calls #-}
instance G.DRG Probe where
 randomBytesGenerate n (Probe mode step)=unsafePerformIO $ do
  modifyIORef' calls(++[n])
  if mode=="root"then error("pair "++show step)else return
   (if mode=="bytes"then error("bytes "++show step)else B.convert(S.replicate n 1),
    if mode=="state"then error("state "++show step)else Probe mode(step+1))

capture expression=do
 value<-try(evaluate(force expression))::IO(Either SomeException String)
 trace<-readIORef calls
 return(object["value".=either(takeWhile(/='\n').displayException)id value,"calls".=trace])
run mode n projection=do
 writeIORef calls[]
 let pair=G.withDRG(Probe mode 0)(R.generateBlinder n)
     value=fst pair
     root=value `seq` "42"
     r=case value of R.Blinder a _->show a
     inverse=case value of R.Blinder _ b->show b
     state=show(snd pair)
     ignored=show(fst(G.withDRG(Probe mode 0)(fmap(const(42::Int))(R.generateBlinder n))))
     expressions=case projection of
      "root"->[root]
      "r"->[r]
      "inverse"->[inverse]
      "show"->[show value]
      "state"->[state]
      "ignore"->[ignored]
      "repeatedRoot"->[root,root,r,inverse]
      "stateRootState"->[state,root,state]
      _->error "projection"
 results<-mapM capture expressions
 L.putStrLn(encode(object["mode".=mode,"n".=show n,"projection".=projection,"results".=results]))
main=mapM_(\(m,n,p)->run m n p)[(m,n,p)|m<-["root","bytes","state","good"],n<-[-3,0,1,2,3,15,3233],p<-["root","r","inverse","show","state","ignore","repeatedRoot","stateRootState"]]
