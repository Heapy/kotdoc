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
data Probe=Probe String Int Int String Int Int deriving Show
calls :: IORef [Int]
calls=unsafePerformIO(newIORef [])
{-# NOINLINE calls #-}
instance G.DRG Probe where
 randomBytesGenerate n (Probe mode retry budget fault at i)=unsafePerformIO $ do
  modifyIORef' calls(n:)
  if i>=budget then error "budget" else if fault=="root"&&i==at then error "pair fault" else do
   let byte=case mode of
        "outer"->if even i then 0 else 2
        "q"->0
        "outerSuccess"->if i<2*retry then (if even i then 0 else 2)else(if even i then 2 else 32)
        "qSuccess"->if i<=retry then 0 else 2
        _->error "mode"
   return(if fault=="bytes"&&i==at then error "bytes fault"else B.convert(S.replicate n byte),
          if fault=="state"&&i==at then error "state fault"else Probe mode retry budget fault at(i+1))
view (pub,key)=show(R.public_size pub,R.public_n pub,R.public_e pub,R.private_d key,R.private_p key,R.private_q key,R.private_dP key,R.private_dQ key,R.private_qinv key)
run mode retry budget fault at projection=do
 writeIORef calls[]
 let e=if mode=="outer"then 2 else if mode=="outerSuccess"then 3 else 17
     action=R.generate 2 e
     pair=G.withDRG(Probe mode retry budget fault at 0)action
     root=fst pair `seq` "42"
     expressions=case projection of
      "ignore"->[show(fst(G.withDRG(Probe mode retry budget fault at 0)(fmap(const(42::Int))action)))]
      "root"->[root]
      "state"->[show(snd pair)]
      "key"->[view(fst pair)]
      "repeatRootState"->[root,root,show(snd pair)]
      _->error "projection"
 results<-mapM capture expressions
 L.putStrLn(encode(object["mode".=mode,"retry".=retry,"budget".=budget,"fault".=fault,"at".=at,"projection".=projection,"results".=results]))
 where capture expression=do
        value<-try(evaluate(force expression))::IO(Either SomeException String)
        trace<-reverse <$>readIORef calls
        return(object["value".=either(takeWhile(/='\n').displayException)id value,"calls".=trace])
main=do
 mapM_(\(m,b,p)->run m 0 b "none" (-1)p)[(m,b,p)|m<-["outer","q"],b<-[100,1000,5000],p<-["ignore","root","state","repeatRootState"]]
 mapM_(\(m,r,p)->run m r 12000 "none" (-1)p)[(m,r,p)|m<-["outerSuccess","qSuccess"],r<-[0,1,100,1000,2500],p<-["root","state","key","repeatRootState"]]
 mapM_(\(m,f,p)->run m 1000 12000 f 1001 p)[(m,f,p)|m<-["outerSuccess","qSuccess"],f<-["root","bytes","state"],p<-["ignore","root","state","repeatRootState"]]
