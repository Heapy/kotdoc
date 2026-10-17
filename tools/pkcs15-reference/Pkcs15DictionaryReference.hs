{-# LANGUAGE OverloadedStrings,DataKinds,TypeFamilies #-}
module Main where
import Crypto.Hash
import Crypto.Hash.IO(HashAlgorithm(..))
import qualified ObservedPkcs15 as P
import qualified Crypto.PubKey.RSA as R
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Types
import Data.IORef
import System.IO.Unsafe
import Foreign.Ptr
import Foreign.Storable
import Foreign.Marshal.Utils
import Data.Word
import Control.Exception
import Control.DeepSeq
field o k=parseMaybe(.:k)o
text o k d=maybe d id(field o k)::String
{-# NOINLINE config #-}
config=unsafePerformIO(newIORef(undefined::Object))
{-# NOINLINE events #-}
events=unsafePerformIO(newIORef([]::[String]))
record s=modifyIORef' events(++[s])
fault label=do o<-readIORef config;if text o "fault"""==label then error label else return()
prop label n=unsafePerformIO(fault label>>return n)
returned label=do o<-readIORef config;return(if text o "returnedBottom"""==label then error(label++"-result")else())
data Probe=Probe
instance HashAlgorithm Probe where
 type HashBlockSize Probe=8
 type HashDigestSize Probe=4
 type HashInternalContextSize Probe=8
 hashBlockSize _=prop "blockSize"8
 hashDigestSize _=prop "digestSize"4
 hashInternalContextSize _=prop "contextSize"8
 hashInternalInit p=do record "init";fault "init";fillBytes p 0 8;returned "init"
 hashInternalUpdate p input count=do
  record("update:"++show count);fault "update"
  mapM_(\i->do w<-peekByteOff input i::IO Word8;v<-peekByteOff p 0::IO Word8;n<-peekByteOff p 1::IO Word8;pokeByteOff p 0(v+w);pokeByteOff p 1(n+1))[0..fromIntegral count-1]
  returned "update"
 hashInternalFinalize p output=do
  record "finalize";fault "finalize"
  mapM_(\i->(peekByteOff p i::IO Word8)>>=pokeByteOff output i)[0..3]
  returned "finalize"
instance P.HashAlgorithmASN1 Probe where
 hashDigestASN1 digest=unsafePerformIO $ do
  record "asn1";fault "asn1";o<-readIORef config
  let raw=A.convert digest::B.ByteString
      b=case text o "encoding""prefix"of
       "ignore"->B.pack[9,8,7]
       "empty"->B.empty
       "raw"->raw
       _->B.pack[9,8]`B.append`raw
  return(A.convert b)
view(Left e)=object["error".=show e]
view(Right b)=object["bytes".=B.unpack(b::B.ByteString)]
run o=do
 writeIORef config o;writeIORef events[]
 let bad :: String -> a -> a
     bad k value=if text o "bottom"""==k then error k else value
     k=maybe 32 id(field o "size")::Int
     key=bad "key"(R.PrivateKey(R.PublicKey(bad "size" k)(2^(8*max 0 k)-1)1)1 0 0 0 0 0)
     message=bad "message"(B.pack(maybe [1,2,3] id(field o "message"))::B.ByteString)
     hash=bad "hash"(if text o "hashMode""hash"=="none"then Nothing else Just(bad "witness"Probe))
     value=case text o "op""sign"of
      "root"->(P.sign Nothing hash key message `seq`object["root".=True])
      "make"->view(P.makeSignature hash(bad "size"k)message)
      "encode"->object["bytes".=B.unpack(P.hashDigestASN1(hashWith Probe message)::B.ByteString)]
      _->view(P.sign Nothing hash key message)
 first<-try(evaluate(force value))::IO(Either SomeException Value)
 second<-try(evaluate(force value))::IO(Either SomeException Value)
 es<-readIORef events
 return(object["results".=map(either(\e->object["exception".=takeWhile(/='\n')(displayException e)])id)[first,second],"events".=es])
main=L.getContents>>=mapM_(\line->case eitherDecode line of Left e->error e;Right o->run o>>=L.putStrLn.encode).L.lines
