{-# LANGUAGE OverloadedStrings #-}
module Main where
import Crypto.Hash
import qualified Crypto.PubKey.RSA as R
import qualified Crypto.PubKey.RSA.PSS as P
import qualified Crypto.PubKey.MaskGenFunction as M
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Types
import Control.Exception
field o k=parseMaybe(.:k)o
text o k d=maybe d id(field o k)::String
bad o name=elem name(maybe []id(field o "faults")::[String])
arg o name v=if bad o name then error name else v
bytes o k=B.pack(maybe []id(field o k))
key o=arg o "key"$R.PrivateKey(R.PublicKey(arg o "size"128)(arg o "n"(2^1024-1))(arg o "e"1))(arg o "d"1)0 0 0 0 0
params o=arg o "params"$P.PSSParams(arg o "hash"SHA1)(arg o "mask"(M.mgf1 SHA1))(arg o "saltLength"20)(arg o "trailer"188)
root r=r `seq`42::Int
payload r=case r of Left e->e `seq`42;Right b->B.length b `seq`42
run o=do
 let k=key o;p=params o;s=arg o "salt"(bytes o "salt");m=arg o "message"(bytes o "message");sg=arg o "signature"(bytes o "signature");bl=arg o "blinder"Nothing
     dg=arg o "digest"(hash B.empty::Digest SHA1)
     value=case text o "op""signRoot"of
      "signRoot"->root(P.signWithSalt s bl p k m)
      "signBytes"->payload(P.signWithSalt s bl p k m)
      "digestRoot"->root(P.signDigestWithSalt s bl p k dg)
      "digestBytes"->payload(P.signDigestWithSalt s bl p k dg)
      "verify"->P.verify p(R.private_pub k)m sg `seq`42
      "verifyDigest"->P.verifyDigest p(R.private_pub k)dg sg `seq`42
      _->error "unknown op"
 first<-try(evaluate value)::IO(Either SomeException Int)
 second<-try(evaluate value)::IO(Either SomeException Int)
 return(toJSON(map(either(takeWhile(/='\n').displayException)show)[first,second]))
main=L.getContents>>=mapM_(\line->case eitherDecode line of Left e->error e;Right o->run o>>=L.putStrLn.encode).L.lines
