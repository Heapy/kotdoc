{-# LANGUAGE GeneralizedNewtypeDeriving #-}
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
newtype Callback a=Callback{runCallback::IO a} deriving(Functor,Applicative,Monad)
calls :: IORef [Int]
calls=unsafePerformIO(newIORef [])
{-# NOINLINE calls #-}
byte :: IORef Int
byte=unsafePerformIO(newIORef 0)
{-# NOINLINE byte #-}
instance G.MonadRandom Callback where
 getRandomBytes n=Callback $ do modifyIORef' calls(++[n]);b<-readIORef byte;return(B.convert(S.replicate n(fromIntegral b)))
capture expression=do
 value<-try(evaluate(force expression))::IO(Either SomeException String)
 trace<-readIORef calls
 return(object["value".=either(takeWhile(/='\n').displayException)id value,"calls".=trace])
run b n projection=do
 writeIORef calls[];writeIORef byte b
 value<-runCallback(R.generateBlinder n)
 let root=value `seq` "42"
     r=case value of R.Blinder a _->show a
     inverse=case value of R.Blinder _ v->show v
     expressions=case projection of
      "root"->[root]
      "r"->[r]
      "inverse"->[inverse]
      "show"->[show value]
      "ignore"->["42"]
      "repeatedRoot"->[root,root,r,inverse]
      _->error "projection"
 results<-mapM capture expressions
 L.putStrLn(encode(object["mode".=("callback"++show b),"n".=show n,"projection".=projection,"results".=results]))
main=mapM_(\(b,n,p)->run b n p)[(b,n,p)|b<-[0,1],n<-[-3,0,1,2,3,15,3233],p<-["root","r","inverse","show","ignore","repeatedRoot"]]
