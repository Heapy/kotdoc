{-# LANGUAGE DataKinds,KindSignatures,ScopedTypeVariables,TypeApplications,TypeFamilies,FlexibleInstances,UndecidableInstances,OverloadedStrings #-}
module Main where
import Crypto.Hash
import Crypto.Hash.IO(HashAlgorithm(..))
import Crypto.PubKey.MaskGenFunction
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Data.IORef
import System.IO.Unsafe(unsafePerformIO)
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
record label=modifyIORef' events(\xs->xs++[label])
fault label=do o<-readIORef config;if text o "fault"==label then error(label++"-fault")else return()
data Access=Access B.ByteString String
accessFault label=do o<-readIORef config;if text o "accessFault"==label then error(label++"-fault")else return()
instance A.ByteArrayAccess Access where
 length(Access bytes label)=unsafePerformIO(accessFault(label++"-length")>>return(B.length bytes))
 withByteArray(Access bytes label) action=do
  record(label++"-enter");accessFault(label++"-before")
  o<-readIORef config
  result<-if get o (if label=="key"then "twiceKey"else "twiceMessage")==Bool True then do
   _<-A.withByteArray bytes action;record(label++"-again");A.withByteArray bytes action
   else A.withByteArray bytes action
  record(label++"-exit");accessFault(label++"-after");return(if get o (if label=="key"then "bottomKeyResult"else "bottomMessageResult")==Bool True then error(label++"-result-bottom")else result)
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
 let seed=Access(bytes(get o "seed"))"key"
     result=mgf1 alg seed(number o "length")::B.ByteString
     action=B.length result `seq`42
 outcomes<-replicateM 2(try(evaluate action)::IO(Either SomeException Int))
 es<-readIORef events
 return(object["results".=map(either(takeWhile(/='\n').displayException)show)outcomes,"events".=es])
main=do
 input<-L.getContents
 case eitherDecode input of
  Left e->error e
  Right o->run(Probe::Probe 8 4)o>>=L.putStrLn.encode
