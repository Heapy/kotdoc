{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,GeneralizedNewtypeDeriving #-}
module Main where
import Crypto.Hash
import qualified Crypto.PubKey.RSA as R
import qualified Crypto.PubKey.RSA.PKCS15 as P
import qualified Crypto.Random as G
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Data.IORef
import System.IO.Unsafe
import Control.Exception
import Control.DeepSeq
import Control.Monad
{-# NOINLINE events #-}
events=unsafePerformIO(newIORef([]::[Int]))
{-# NOINLINE config #-}
config=unsafePerformIO(newIORef(undefined::Object))
get o k=case K.lookup k o of Just v->v;_->Null
text o k=case fromJSON(get o k)of Success n->n;_->""
num o k=case fromJSON(get o k)of Success n->n;_->0::Int
bytes v=case fromJSON v of Success xs->B.pack(map fromIntegral(xs::[Int]));_->B.empty
newtype Callback a=Callback{runCallback::IO a}deriving(Functor,Applicative,Monad)
instance G.MonadRandom Callback where
 getRandomBytes n=Callback $ do
  o<-readIORef config
  before<-readIORef events
  modifyIORef' events(++[n])
  if length before>=num o "budget"then error "rng-budget"else return()
  if text o "mode"=="callbackBottom"then error "callback-bottom"else return()
  return(if text o "mode"=="bytesBottom"then error "bytes-bottom"else A.convert(B.replicate(max 0 n)(if text o "mode"=="zero"||(text o "mode"=="retrySuccess"&&length before<num o "retries")then 0 else 7)))
run o=do
 writeIORef config o;writeIORef events[]
 let k=num o "size"
     n=2^(8*max 0 k)-1
     key=R.PrivateKey(R.PublicKey k n 1)1 0 0 0 0 0
     msg=if get o "messageBottom"==Bool True then error "message-bottom"else bytes(get o "message")
     cipher=bytes(get o "cipher")
     action=case text o "operation"of
      "encrypt"->P.encrypt(R.private_pub key)msg
      "decryptSafer"->P.decryptSafer key cipher
      "signSafer"->P.signSafer(Just SHA1)key msg
      _->P.pad k msg
 outcome<-try(runCallback action)::IO(Either SomeException(Either R.Error B.ByteString))
 let evaluation=case outcome of
      Left e->throw e
      Right value->case text o "projection"of
       "ignore"->toJSON(42::Int)
       "root"->value `seq`toJSON(42::Int)
       _->case value of Left e->object["error".=show e];Right b->object["bytes".=B.unpack b]
 results<-replicateM 2(try(evaluate(force evaluation))::IO(Either SomeException Value))
 es<-readIORef events
 return(object["results".=map(either(\e->object["exception".=takeWhile(/='\n')(displayException e)])id)results,"draws".=es])
main=L.getContents>>= \s -> case eitherDecode s of Left e->error e;Right o->run o>>=L.putStrLn.encode
