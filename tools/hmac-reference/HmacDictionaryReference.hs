{-# LANGUAGE DataKinds,KindSignatures,ScopedTypeVariables,TypeApplications,TypeFamilies,FlexibleInstances,UndecidableInstances,OverloadedStrings #-}
module Main where
import Crypto.Hash
import Crypto.Hash.IO(HashAlgorithm(..))
import qualified Crypto.MAC.HMAC as H
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Data.IORef
import System.IO.Unsafe(unsafePerformIO)
import Control.Exception
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
run _ o=do
 writeIORef config o;writeIORef events[];writeIORef initCount 0
 let key=if get o "keyBottom"==Bool True then error "key-bottom"else bytes(get o "key")
     msg=if get o "messageBottom"==Bool True then error "message-bottom"else bytes(get o "message")
     ctx=H.initialize key::H.Context(Probe b d)
     action=case text o "operation"of
      "ignored"->const 42(H.hmac key msg::H.HMAC(Probe b d))
      "initialize"->ctx `seq`42
      "update"->H.update ctx msg `seq`42
      "finalize"->H.finalize ctx `seq`42
      "hmac"->(H.hmac key msg::H.HMAC(Probe b d)) `seq`42
      "outerBottom"->H.Context(error "outer-bottom")(error "inner-bottom") `seq`42
      "innerBottom"->H.Context(hashInit::Context(Probe b d))(error "inner-bottom") `seq`42
      "equalBottom"->((error "left-bottom"::H.HMAC(Probe b d))==(error "right-bottom")) `seq`42
      _->error "operation"
 outcome<-try(evaluate action)::IO(Either SomeException Int)
 es<-readIORef events
 return$object["result" .= either(takeWhile(/='\n').displayException)show outcome,"events" .= es]
main=do
 input<-L.getContents
 case eitherDecode input of
  Left e->error e
  Right o->do
   value<-case(number o "block"::Int,number o "digest"::Int)of
    (0,4)->run(Probe::Probe 0 4)o
    (1,1)->run(Probe::Probe 1 1)o
    (8,4)->run(Probe::Probe 8 4)o
    (8,8)->run(Probe::Probe 8 8)o
    (8,12)->run(Probe::Probe 8 12)o
    _->error "profile"
   L.putStrLn(encode value)
