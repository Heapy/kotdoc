{-# LANGUAGE OverloadedStrings #-}
module Main where
import Crypto.Hash
import qualified Crypto.MAC.HMAC as H
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Data.Word
import Data.Bits(xor)
import Data.Maybe(fromJust)
import Foreign.Ptr
import Foreign.Storable
import Foreign.Marshal.Array
import Control.Exception(evaluate)
get o k=case K.lookup k o of Just v->v;_->Null
bytes v=case fromJSON v of Success xs->B.pack(xs::[Word8]);_->B.empty
snapshot value=evaluate(A.convert value::B.ByteString)
observe o=do
 let mac=H.hmac(bytes(get o "key"))(bytes(get o "message"))::H.HMAC SHA256
 before<-snapshot mac
 A.withByteArray mac$ \p->do v<-peekByteOff p 0::IO Word8;pokeByteOff p 0(v`xor`255)
 first<-snapshot mac
 getter<-snapshot(H.hmacGetDigest mac)
 A.withByteArray(H.hmacGetDigest mac)$ \p->do v<-peekByteOff p 1::IO Word8;pokeByteOff p 1(v`xor`255)
 second<-snapshot mac
 copied<-A.alloc(A.length mac+4)$ \p->do pokeArray(castPtr p::Ptr Word8)(replicate(A.length mac+4)99);A.copyByteArrayToPtr mac(p`plusPtr`2)
 let original=H.HMAC(fromJust(digestFromByteString before))::H.HMAC SHA256
 return$object["before" .= B.unpack before,"first" .= B.unpack first,"getter" .= B.unpack getter,"second" .= B.unpack second,"copied" .= B.unpack(copied::B.ByteString),"equalsOriginal" .= (mac==original)]
main=L.getContents>>=mapM_ run.L.lines
 where run line=case eitherDecode line of Left e->error e;Right o->observe o>>=L.putStrLn.encode
