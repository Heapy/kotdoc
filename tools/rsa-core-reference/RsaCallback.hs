{-# LANGUAGE GeneralizedNewtypeDeriving #-}
module Main where
import qualified ObservedRsa as R
import qualified Crypto.Random as G
import Crypto.PubKey.RSA.Types(PublicKey,PrivateKey)
import qualified Data.ByteArray as B
import qualified Data.ByteString as S
import Control.Exception
import Data.IORef
import System.IO.Unsafe(unsafePerformIO)
newtype Callback a=Callback{runCallback::IO a} deriving(Functor,Applicative,Monad)
calls :: IORef [Int]
calls=unsafePerformIO(newIORef [])
{-# NOINLINE calls #-}
instance G.MonadRandom Callback where
 getRandomBytes n=Callback $ do modifyIORef' calls(++[n]);error "rng budget"
main=mapM_ probe [0,1,2,17]
 where probe e=do
        writeIORef calls[]
        result<-try(runCallback(R.generate 2 e)>>=evaluate)::IO(Either SomeException(PublicKey,PrivateKey))
        trace<-readIORef calls
        print(e,either(takeWhile(/='\n').displayException)(const "returned")result,trace)
