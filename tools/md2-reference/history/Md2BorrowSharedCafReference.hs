{-# LANGUAGE OverloadedStrings #-}
module Main where
import Crypto.Hash
import qualified Crypto.Hash.IO as I
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Types
import Data.Word
import Foreign.Storable
import Foreign.Marshal.Array
import Foreign.Ptr
import Control.DeepSeq
import Control.Exception
field o k=parseMaybe(.:k)o
bytes o=B.pack(maybe [] id(field o "message")::[Word8])
snapshot x=evaluate(force(B.unpack(A.convert x::B.ByteString)))
fillTail x=A.withByteArray x $ \p->mapM_(\i->pokeByteOff p i(123::Word8))[56..95]
flipAt x i=A.withByteArray x $ \p->do v<-peekByteOff p i::IO Word8;pokeByteOff p i(v+1)
run o=do
 let input=bytes o;c=hashInitWith MD2;nonempty=hashUpdate c(B.singleton 7);empty=hashUpdate c B.empty
 evaluate nonempty
 fillTail c
 before<-snapshot c
 flipAt c 24
 after<-snapshot c
 let nc=hashFinalize nonempty;ec=hashFinalize empty
 n<-snapshot nc;e<-snapshot ec
 let digest=hashWith MD2 input
 original<-snapshot digest
 flipAt digest 0
 changed<-snapshot digest
 copy<-(A.alloc(A.length digest+4)$ \p->do pokeArray p(replicate(A.length digest+4)(99::Word8));A.copyByteArrayToPtr digest(p `plusPtr`2))::IO B.ByteString
 mutable<-I.hashMutableInitWith MD2
 fillTail mutable
 I.hashMutableUpdate mutable input
 flipAt mutable 24
 mutated<-I.hashMutableFinalize mutable >>=snapshot
 I.hashMutableReset mutable
 reset<-snapshot mutable
 return(object["before".=before,"after".=after,"nonemptyCopy".=n,"emptyAlias".=e,"digestOriginal".=original,"digestChanged".=changed,"copy".=B.unpack copy,"mutableDigest".=mutated,"resetContext".=reset])
main=L.getContents>>=mapM_(\s->case eitherDecode s of Left e->error e;Right o->run o>>=L.putStrLn.encode).L.lines
