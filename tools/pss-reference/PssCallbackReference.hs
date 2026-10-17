{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,GeneralizedNewtypeDeriving #-}
module Main where
import Crypto.Hash
import qualified Crypto.PubKey.RSA as R
import qualified Crypto.PubKey.RSA.PSS as P
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
  if text o "mode"=="callbackBeforeLength"then error "callback-before-length"else return()
  if text o "mode"=="ignoreLength"then modifyIORef' events(++[-777])else evaluate n>>modifyIORef' events(++[n])
  if length before>=num o "budget"then error "rng-budget"else return()
  if text o "mode"=="callbackBottom"then error "callback-bottom"else return()
  return(if text o "mode"=="bytesBottom"then error "bytes-bottom"else A.convert(case text o "mode"of
   "ignoreLength"->B.pack[9,8]
   "short"->B.replicate(max 0(n-1))7
   "long"->B.replicate(max 0(n+1))7
   "zero"->B.replicate(max 0 n)0
   _->B.replicate(max 0 n)7))
run o=do
 writeIORef config o;writeIORef events[]
 let bad name value=if text o "bottom"==name then error name else value
     k=num o "size"
     integer name fallback=case fromJSON(get o name)of Success str->read str::Integer;_->fallback
     key=bad "key"(R.PrivateKey(R.PublicKey(bad "size"k)(bad "n"(integer "n"(2^(8*max 0 k)-1)))(integer "e"1))(integer "d"1)(integer "p"0)(integer "q"0)(integer "dP"0)(integer "dQ"0)(integer "qinv"0))
     params=bad "params"((P.defaultPSSParams SHA1){P.pssSaltLength=bad "saltLength"(num o "saltLength"),P.pssTrailerField=bad "trailer"188})
     msg=bad "message"(B.pack[1,2,3])
     digest=bad "digest"(hash B.empty::Digest SHA1)
     action=case text o "operation"of
      "signDigest"->P.signDigest Nothing params key digest
      "signSafer"->P.signSafer params key msg
      "signDigestSafer"->P.signDigestSafer params key digest
      _->P.sign Nothing params key msg
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
