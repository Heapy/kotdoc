{-# LANGUAGE OverloadedStrings,FlexibleInstances #-}
module Main where
import Crypto.Hash(SHA1(..),Digest,hash)
import qualified Crypto.PubKey.RSA as R
import qualified Crypto.PubKey.RSA.PSS as P
import qualified Crypto.Random as G
import qualified Crypto.Random.Types as T
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Types
import Data.IORef
import System.IO.Unsafe
import Control.Exception
import Control.DeepSeq
field o k=parseMaybe(.:k)o
text o k d=maybe d id(field o k)::String
number o k d=maybe d id(field o k)::Int
{-# NOINLINE config #-}
config=unsafePerformIO(newIORef(undefined::Object))
{-# NOINLINE draws #-}
draws=unsafePerformIO(newIORef([]::[Int]))
data Drg=Drg Int
instance G.DRG Drg where
 randomBytesGenerate n previous=unsafePerformIO $ do
  o<-readIORef config
  let mode=text o "mode""normal"
  if mode=="pairBottom"then error "pair-bottom"else return()
  let Drg index=previous
  evaluate index
  if mode=="ignoreLength"then modifyIORef' draws(++[-777])else evaluate n>>modifyIORef' draws(++[n])
  if index>=number o "budget"8 then error "rng-budget"else return()
  let raw=case mode of
       "ignoreLength"->B.pack[9,8]
       "fieldsBottom"->error "bytes-bottom"
       "bytesBottom"->error "bytes-bottom"
       "zero"->B.replicate(max 0 n)0
       "empty"->B.empty
       "short"->B.replicate(max 0(n-1))7
       "long"->B.replicate(max 0(n+1))7
       "longThenNormal"->B.replicate(max 0(n+if index<=number o "retries"99 then 1 else 0))7
       "retrySuccess"->B.replicate(max 0 n)(if index<=number o "retries"2 then 0 else 7)
       _->B.replicate(max 0 n)7
      next=if mode`elem`["fieldsBottom","stateBottom"]then error "state-bottom"else Drg(index+1)
  return(A.convert raw,next)
view(Left e)=object["error".=show e]
view(Right b)=object["bytes".=B.unpack(b::B.ByteString)]
run o=do
 writeIORef config o;writeIORef draws[]
 let bad name value=if text o "bottom"""==name then error name else value
     k=number o "size"64
     integer name fallback=read(text o name(show fallback))::Integer
     key=bad "key"(R.PrivateKey(R.PublicKey(bad "size"k)(bad "n"(integer "n"(2^(8*max 0 k)-1)))(integer "e"1))(integer "d"1)(integer "p"0)(integer "q"0)(integer "dP"0)(integer "dQ"0)(integer "qinv"0))
     message=bad "message"(B.pack(maybe[1,2,3]id(field o "message")))
     digest=bad "digest"(hash B.empty::Digest SHA1)
     params=bad "params"((P.defaultPSSParams SHA1){P.pssSaltLength=bad "saltLength"(number o "saltLength"20),P.pssTrailerField=bad "trailer"188})
     action=case text o "op""sign"of
      "signDigest"->P.signDigest Nothing params key digest
      "signSafer"->P.signSafer params key message
      "signDigestSafer"->P.signDigestSafer params key digest
      _->P.sign Nothing params key message
     initial=if text o "mode"""=="generatorBottom"then error "generator-bottom"else Drg 1
     pair=G.withDRG initial action
     value=case text o "projection""bytes"of
      "ignore"->toJSON(fst(G.withDRG initial(fmap(const(42::Int))action)))
      "root"->case fst pair of Left _->String "Left";Right _->String "Right"
      "state"->case snd pair of Drg index->toJSON index
      _->view(fst pair)
 first<-try(evaluate(force value))::IO(Either SomeException Value)
 second<-try(evaluate(force value))::IO(Either SomeException Value)
 ds<-readIORef draws
 return(object["results".=map(either(\e->object["exception".=takeWhile(/='\n')(displayException e)])id)[first,second],"draws".=ds])
main=L.getContents>>=mapM_(\line->case eitherDecode line of Left e->error e;Right o->run o>>=L.putStrLn.encode).L.lines
