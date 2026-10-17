{-# LANGUAGE OverloadedStrings,FlexibleInstances #-}
module Main where
import Crypto.Hash(SHA1(..))
import qualified Crypto.PubKey.RSA as R
import qualified Crypto.PubKey.RSA.PKCS15 as P
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
  modifyIORef' draws(++[n])
  if index>=number o "budget"8 then error "rng-budget"else return()
  let raw=case mode of
       "fieldsBottom"->error "bytes-bottom"
       "bytesBottom"->error "bytes-bottom"
       "zero"->B.replicate(max 0 n)0
       "empty"->B.empty
       "short"->B.replicate(max 0(n-1))7
       "long"->B.replicate(max 0(n+1))7
       "retrySuccess"->B.replicate(max 0 n)(if index<=number o "retries"2 then 0 else 7)
       _->B.replicate(max 0 n)7
      next=if mode`elem`["fieldsBottom","stateBottom"]then error "state-bottom"else Drg(index+1)
  return(A.convert raw,next)
view(Left e)=object["error".=show e]
view(Right b)=object["bytes".=B.unpack(b::B.ByteString)]
run o=do
 writeIORef config o;writeIORef draws[]
 let k=number o "size"32
     key=R.PrivateKey(R.PublicKey k (2^(8*max 0 k)-1)1)1 0 0 0 0 0
     message=if text o "bottom"""=="message"then error "message-bottom"else B.pack(maybe [1,2,3] id(field o "message"))
     input=B.pack(maybe [] id(field o "input"))
     action=case text o "op""pad"of
      "encrypt"->P.encrypt(R.private_pub key)message
      "signSafer"->P.signSafer(Just SHA1)key message
      "decryptSafer"->P.decryptSafer key input
      _->P.pad k message
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
