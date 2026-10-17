{-# LANGUAGE DataKinds,KindSignatures,ScopedTypeVariables,TypeApplications,TypeFamilies,FlexibleInstances,UndecidableInstances,OverloadedStrings #-}
module Main where
import Crypto.Hash
import Crypto.Hash.IO(HashAlgorithm(..))
import qualified Crypto.Hash.IO as I
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
run o=do
 writeIORef config o;writeIORef events[];writeIORef initCount 0
 let seed=Access(bytes(get o "seed"))"key"
     context=hashInitWith MD2
     value=case text o "operation"of
      "hashUpdateRoot"->hashUpdate context seed `seq`42
      "hashUpdatesRoot"->hashUpdates context [seed,seed] `seq`42
      "digestMaybeRoot"->(digestFromByteString seed::Maybe(Digest MD2)) `seq`42
      "digestRoot"->case(digestFromByteString seed::Maybe(Digest MD2))of Nothing->42;Just d->d `seq`42
      "digestBytes"->case(digestFromByteString seed::Maybe(Digest MD2))of Nothing->42;Just d->A.length d `seq`42
      _->42
 first<-if text o "operation"=="mutableUpdate"then do c<-I.hashMutableInitWith MD2;try(I.hashMutableUpdate c seed>>return(42::Int))else try(evaluate value)
 second<-case(first::Either SomeException Int)of Left e->return(Left e);Right _->try(evaluate value)
 es<-readIORef events
 return(object["results".=map(either(takeWhile(/='\n').displayException)show)[first,second],"events".=es])
main=do
 input<-L.getContents
 case eitherDecode input of Left e->error e;Right o->run o>>=L.putStrLn.encode
