{-# LANGUAGE OverloadedStrings #-}
module Main where
import Crypto.Hash(SHA1(..))
import qualified Crypto.PubKey.RSA as R
import qualified Crypto.PubKey.RSA.PKCS15 as P
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Types
import Control.Exception
import Control.DeepSeq
field o k=parseMaybe(.:k)o
text o k d=maybe d id(field o k)::String
bad o name=elem name(maybe [] id(field o "faults")::[String])
arg o name value=if bad o name then error name else value
key o=arg o "key"$R.PrivateKey(R.PublicKey(arg o "size"128)(arg o "n"(2^1024-1))(arg o "e"1))(arg o "d"1)0 0 0 0 0
hash o=arg o "hash"(if text o "hashMode""hash"=="none"then Nothing else Just(arg o "witness" SHA1))
message o=arg o "message"(B.pack(maybe [1,2,3] id(field o "message")))
input o=arg o "input"(B.pack(maybe [] id(field o "input")))
blinder o=arg o "blinder"Nothing
root :: Either R.Error B.ByteString->Int
root r=r `seq`42
payload :: Either R.Error B.ByteString->Int
payload r=case r of Left e->e `seq`42;Right b->B.length b `seq`42
run o=do
 let k=key o;m=message o;i=input o
     value=case text o "op""signRoot"of
      "padSignatureRoot"->root(P.padSignature(arg o "size"128)m)
      "padSignatureBytes"->payload(P.padSignature(arg o "size"128)m)
      "signRoot"->root(P.sign(blinder o)(hash o)k m)
      "signBytes"->payload(P.sign(blinder o)(hash o)k m)
      "decryptRoot"->root(P.decrypt(blinder o)k i)
      "decryptBytes"->payload(P.decrypt(blinder o)k i)
      "verify"->P.verify(hash o)(R.private_pub k)m i `seq`42
      _->error "unknown op"
 first<-try(evaluate value)::IO(Either SomeException Int)
 second<-try(evaluate value)::IO(Either SomeException Int)
 return(toJSON(map(either(takeWhile(/='\n').displayException)show)[first,second]))
main=L.getContents>>=mapM_(\line->case eitherDecode line of Left e->error e;Right o->run o>>=L.putStrLn.encode).L.lines
