{-# LANGUAGE OverloadedStrings,ScopedTypeVariables #-}
module Main where
import Crypto.Hash
import qualified Crypto.PubKey.RSA as R
import qualified Crypto.PubKey.RSA.OAEP as O
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
data DRG=DRG Int
instance G.DRG DRG where
 randomBytesGenerate n g=unsafePerformIO $ do
  o<-readIORef config
  if text o "mode"=="pairBottom"then error "pair-bottom"else return()
  let DRG i=if text o "mode"=="generatorBottom"then error "generator-bottom"else g
  evaluate i
  modifyIORef' events(++[n])
  if text o "mode"=="fieldsBottom"then return(error "bytes-bottom",error "state-bottom")else do
   let b=if text o "mode"=="zero"then B.replicate(max 0 n)0 else if text o "mode"=="ff"||(text o "mode"=="retrySuccess"&&i<100)then B.replicate(max 0 n)255 else B.pack[fromIntegral(i+j)|j<-[0..n-1]]
   return(A.convert b,DRG(i+1))
run o=do
 writeIORef config o;writeIORef events[]
 let k=num o "size"
     n=2^(8*max 0 k)-1
     key=R.PrivateKey(R.PublicKey k n 1)1 0 0 0 0 0
     params=O.defaultOAEPParams SHA1
     msg=if get o "messageBottom"==Bool True then error "message-bottom"else bytes(get o "message")
     cipher=bytes(get o "cipher")
     action=if text o "operation"=="encrypt"then O.encrypt params(R.private_pub key)msg else O.decryptSafer params key cipher
     pair=G.withDRG(DRG 1)action
     evaluation=case text o "projection"of
      "ignore"->toJSON(fst(G.withDRG(DRG 1)(fmap(const(42::Int))action)))
      "root"->fst pair `seq`toJSON(42::Int)
      "state"->case snd pair of DRG i->toJSON i
      _->case fst pair of Left e->object["error".=show e];Right b->object["bytes".=B.unpack(b::B.ByteString)]
 results<-replicateM 2(try(evaluate(force evaluation))::IO(Either SomeException Value))
 es<-readIORef events
 return(object["results".=map(either(\e->object["exception".=takeWhile(/='\n')(displayException e)])id)results,"draws".=es])
main=L.getContents>>= \s -> case eitherDecode s of Left e->error e;Right o->run o>>=L.putStrLn.encode
