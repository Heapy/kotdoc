{-# LANGUAGE OverloadedStrings,DataKinds,TypeFamilies #-}
module Main where
import Crypto.Hash
import Crypto.Hash.IO(HashAlgorithm(..))
import qualified Crypto.PubKey.RSA.PSS as P
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
record s=evaluate(force s)>>modifyIORef' events(++[s])
fault label=do o<-readIORef config;if text o "fault"""==label then error label else return()
prop label n=unsafePerformIO(fault label>>return n)
returned label=do o<-readIORef config;return(if text o "returnedBottom"""==label then error(label++"-result")else())
data Probe=Probe
instance HashAlgorithm Probe where
 type HashBlockSize Probe=8
 type HashDigestSize Probe=4
 type HashInternalContextSize Probe=8
 hashBlockSize _=prop "blockSize"8
 hashDigestSize w=unsafePerformIO $ do o<-readIORef config;if text o "strictWitness""no"=="yes"then evaluate w>>return()else return();return(prop "digestSize"4)
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
maskGen seed count=unsafePerformIO $ do
 record "mask";fault "mask";o<-readIORef config
 if text o "maskMode""normal"=="ignore"then return(B.pack[7,8])else do
  record("maskLength:"++show count);fault "maskLength"
  let raw=seed::B.ByteString
  if text o "maskMode""normal"=="ignoreSeed"then return()else record("seed:"++show(B.unpack raw))
  return(case text o "maskMode""normal"of
   "valid"->B.replicate(max 0(count-3))0`B.append`B.pack[1,9,8]
   "empty"->B.empty
   "short"->B.replicate(max 0(count-1))7
   "long"->B.replicate(max 0(count+1))7
   _->B.replicate(max 0 count)7)
view(Left e)=object["error".=show e]
view(Right b)=object["bytes".=B.unpack(b::B.ByteString)]
run o=do
 writeIORef config o;writeIORef events[]
 let bad :: String -> a -> a
     bad k v=if text o "bottom"""==k then error k else v
     k=maybe 32 id(field o "size")::Int
     key=bad "key"(R.PrivateKey(R.PublicKey(bad "size"k)(2^(8*max 0 k)-1)1)1 0 0 0 0 0)
     params=bad "params"(P.PSSParams(bad "witness"Probe)(bad "maskField"maskGen)(bad "saltLength"2)(bad "trailer"188))
     message=bad "message"(B.pack(maybe[1,2,3]id(field o "message")))
     salt=bad "salt"(B.pack[9,8])
     digest=bad "digest"(hash B.empty::Digest Probe)
     signature=B.pack(maybe[]id(field o "signature"))
     value=case text o "op""sign"of
      "root"->P.signWithSalt salt Nothing params key message `seq`object["root".=True]
      "signDigest"->view(P.signDigestWithSalt salt Nothing params key digest)
      "verify"->object["valid".=P.verify params(R.private_pub key)message signature]
      "defaultRoot"->(P.defaultPSSParams(bad "witness"Probe)::P.PSSParams Probe B.ByteString B.ByteString) `seq`object["root".=True]
      "defaultSalt"->object["saltLength".=P.pssSaltLength((P.defaultPSSParams(bad "witness"Probe)::P.PSSParams Probe B.ByteString B.ByteString))]
      _->view(P.signWithSalt salt Nothing params key message)
 first<-try(evaluate(force value))::IO(Either SomeException Value)
 second<-try(evaluate(force value))::IO(Either SomeException Value)
 es<-readIORef events
 return(object["results".=map(either(\e->object["exception".=takeWhile(/='\n')(displayException e)])id)[first,second],"events".=es])
main=L.getContents>>=mapM_(\line->case eitherDecode line of Left e->error e;Right o->run o>>=L.putStrLn.encode).L.lines
