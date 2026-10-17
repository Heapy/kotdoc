{-# LANGUAGE DataKinds,KindSignatures,ScopedTypeVariables,TypeApplications,TypeFamilies,FlexibleInstances,UndecidableInstances,OverloadedStrings #-}
module Main where
import Crypto.Hash
import Crypto.Hash.IO(HashAlgorithm(..))
import qualified Crypto.PubKey.RSA.OAEP as O
import qualified Crypto.PubKey.RSA as R
import Crypto.PubKey.MaskGenFunction
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Key(Key)
import qualified Data.Aeson.KeyMap as K
import Data.IORef
import System.IO.Unsafe(unsafePerformIO)
import Control.DeepSeq(force)
import Control.Exception
import Control.Monad(replicateM)
import Data.Word
import Data.Proxy
import GHC.TypeLits
import Foreign.Ptr
import Foreign.Storable
import Foreign.Marshal.Array

data Probe(b::Nat)(d::Nat)=Probe
{-# NOINLINE config #-}
config=unsafePerformIO(newIORef(undefined::Object))
{-# NOINLINE events #-}
events=unsafePerformIO(newIORef([]::[String]))
{-# NOINLINE initCount #-}
initCount=unsafePerformIO(newIORef(0::Int))
get o k=case K.lookup k o of Just v->v;_->Null
number o k=case fromJSON(get o k)of Success n->n;_->0
text o k=case fromJSON(get o k)of Success n->n;_->""
bytes v=case fromJSON v of Success xs->B.pack(xs::[Word8]);_->B.empty
record label=evaluate(force label)>>modifyIORef' events(\xs->xs++[label])
fault label=do o<-readIORef config;if text o "fault"==label then error(label++"-fault")else return()
instance(KnownNat b,KnownNat d)=>HashAlgorithm(Probe b d)where
 type HashBlockSize(Probe b d)=b
 type HashDigestSize(Probe b d)=d
 type HashInternalContextSize(Probe b d)=8
 hashBlockSize _=unsafePerformIO(fault "blockSize" >> return(fromInteger(natVal(Proxy::Proxy b))))
 hashDigestSize _=unsafePerformIO(fault "digestSize" >> return(fromInteger(natVal(Proxy::Proxy d))))
 hashInternalContextSize _=unsafePerformIO(fault "contextSize" >> return 8)
 hashInternalInit p=do
  n<-atomicModifyIORef' initCount(\n->(n+1,n+1));record("init"++show n);fault("init"++show n)
  pokeArray(castPtr p::Ptr Word8)[0..7]
 hashInternalUpdate p input len=do
  xs<-peekArray(fromIntegral len)input;record("update:"++show xs);fault "update"
  acc<-peek(castPtr p::Ptr Word8);poke(castPtr p)(foldl(\a b->a*33+b)acc xs)
 hashInternalFinalize p output=do
  record "finalize";fault "finalize";acc<-peek(castPtr p::Ptr Word8)
  pokeArray(castPtr output::Ptr Word8)[acc+fromIntegral(17*i)|i<-[0..fromInteger(natVal(Proxy::Proxy d))-1]]
run :: forall b d.(KnownNat b,KnownNat d)=>Probe b d->Object->IO Value
run alg o=do
 writeIORef config o;writeIORef events[];writeIORef initCount 0
 let demand :: Key -> a -> a
     demand k value=if get o(k<>"Bottom")==Bool True then error(show k++"-bottom")else value
     seed=demand "seed"(bytes(get o "seed"))
     msg=demand "message"(bytes(get o "message"))
     len=demand "length"(number o "length"::Int)
     size=demand "size"(number o "size"::Int)
     modulus=demand "n"(2^(8*max 0 size)-1)
     public=R.PublicKey size modulus(demand "e"1)
     private=R.PrivateKey public(demand "d"1)0 0 0 0 0
     label=demand "label"(bytes(get o "label"))
     mask seed n=unsafePerformIO $ do
       record("mask:"++show n++":"++show(B.unpack seed))
       fault "mask"
       return(mgf1 alg seed n::B.ByteString)
     params=O.OAEPParams alg mask(Just label)
     encrypted=O.encryptWithSeed seed params public msg
     decrypted=O.decrypt Nothing params private(demand "cipher"(bytes(get o "cipher")))::Either R.Error B.ByteString
     action=case text o "operation"of
       "ignore"->const 42 encrypted
       "mgf"->B.length(mgf1 alg seed len::B.ByteString) `seq`42
       "encryptRoot"->encrypted `seq`42
       "encryptBytes"->either(const 42)(\b->B.length b `seq`42)encrypted
       "decryptRoot"->decrypted `seq`42
       _->error "operation"
 outcomes<-replicateM 2(try(evaluate action)::IO(Either SomeException Int))
 es<-readIORef events
 let results=map(either(takeWhile(/='\n').displayException)show)outcomes
 return(object["results".=results,"events".=es])
main=do
 input<-L.getContents
 case eitherDecode input of
  Left e->error e
  Right o->do
   value<-case(number o "digest"::Int)of
    0->run(Probe::Probe 8 0)o
    1->run(Probe::Probe 8 1)o
    4->run(Probe::Probe 8 4)o
    12->run(Probe::Probe 8 12)o
    _->error "profile"
   L.putStrLn(encode value)
