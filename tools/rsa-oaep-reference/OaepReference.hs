{-# LANGUAGE OverloadedStrings, RankNTypes, ScopedTypeVariables #-}
module Main where
import Data.Bits(xor)
import Crypto.Number.Serialize(os2ip,i2ospOf_)
import Crypto.Hash
import qualified Crypto.PubKey.RSA as R
import qualified Crypto.PubKey.RSA.OAEP as O
import qualified Crypto.PubKey.RSA.Prim as P
import Crypto.PubKey.MaskGenFunction
import qualified Crypto.Random as G
import qualified Crypto.Random.ChaChaDRG as D
import qualified Data.ByteString as B
import qualified Data.ByteArray as BA
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Types
import Data.Word
import Control.Exception hiding(mask)
import Control.DeepSeq
import Data.IORef
import System.IO.Unsafe
import System.Environment
import Test.Hspec
import qualified OriginalOaepSpec as S
field o k=parseMaybe(.:k)o
text o k d=maybe d id(field o k)::String
number o k d=maybe d id(field o k)::Int
integer o k d=read(text o k(show d))::Integer
bytes o k=B.pack(maybe [] id(field o k)::[Word8])
pub o=R.PublicKey(number o "size" 128)(integer o "n"(R.public_n(R.private_pub S.rsaKey1)))(integer o "e" 65537)
key o=R.PrivateKey(pub o)(integer o "d"(R.private_d S.rsaKey1))(integer o "p" 0)(integer o "q" 0)(integer o "dP" 0)(integer o "dQ" 0)(integer o "qinv" 0)
refs :: IORef [Value]
refs=unsafePerformIO(newIORef [])
{-# NOINLINE refs #-}
mask o alg seed len=unsafePerformIO $ do
 event<-evaluate(force(object["length".=len,"seed".=B.unpack seed]))
 modifyIORef' refs (++[event])
 let result=case text o "mask" "default"of
       "empty" -> B.empty
       "short" -> B.replicate(max 0(len-1)) 7
       "long" -> B.replicate(max 0(len+1)) 7
       "zero" -> B.replicate(max 0 len)0
       "error" -> error "mask error"
       _ -> mgf1 alg seed len
 return result
params o alg=O.OAEPParams{O.oaepHash=alg,O.oaepLabel=if text o "labelMode" "none"=="none"then Nothing else Just(bytes o "label"),O.oaepMaskGenAlg=mask o alg}
view(Left e)=object["error".=show e]
view(Right b)=object["bytes".=B.unpack(b::B.ByteString)]
observe o alg=case text o "op" "encrypt"of
 "plan" -> let n=number o "length"0;d=number o "digest"1;(chunks,left)=n `divMod`d;maximum=if left>0 then chunks+1 else chunks in object["chunks".=chunks,"remainder".=left,"maximum".=maximum,"last".=B.unpack(if maximum>0 then i2ospOf_ 4(toInteger(maximum-1))else B.empty)]
 "mgf" -> object["bytes".=B.unpack(mgf1 alg(bytes o "seed")(number o "length" 0)::B.ByteString)]
 "encrypt" -> view(O.encryptWithSeed(bytes o "seed")(params o alg)(pub o)(bytes o "message"))
 "decrypt" -> view(O.decrypt Nothing(params o alg)(key o)(bytes o "cipher")::Either R.Error B.ByteString)
 "decryptEncoded" -> let k=number o "size"128;pk=R.PrivateKey(R.PublicKey k (2^(8*max 0 k)-1)1)1 0 0 0 0 0 in view(O.decrypt Nothing(params o alg)pk(bytes o "cipher")::Either R.Error B.ByteString)
 _ -> error "unknown operation"
choose :: String -> (forall a.HashAlgorithm a=>a->IO Value)->IO Value
choose n f=case n of
 "MD5"->f MD5;"SHA1"->f SHA1;"SHA224"->f SHA224;"SHA256"->f SHA256;"SHA384"->f SHA384;"SHA512"->f SHA512;"SHA512t_224"->f SHA512t_224;"SHA512t_256"->f SHA512t_256;"SHA3_224"->f SHA3_224;"SHA3_256"->f SHA3_256;"SHA3_384"->f SHA3_384;"SHA3_512"->f SHA3_512;"Keccak_224"->f Keccak_224;"Keccak_256"->f Keccak_256;"Keccak_384"->f Keccak_384;"Keccak_512"->f Keccak_512
 _->error "unknown algorithm"
run o=do
 writeIORef refs []
 result<-try(choose(text o "algorithm" "SHA1")(\a->evaluate(force(observe o a))))::IO(Either SomeException Value)
 events<-readIORef refs
 return(object["result".=either(\e->object["exception".=takeWhile(/='\n')(displayException e)])id result,"maskCalls".=events])
public k=object["size".=R.public_size k,"n".=show(R.public_n k),"e".=show(R.public_e k)]
private k=object["d".=show(R.private_d k),"p".=show(R.private_p k),"q".=show(R.private_q k),"dP".=show(R.private_dP k),"dQ".=show(R.private_dQ k),"qinv".=show(R.private_qinv k)]
vector k v=object["key".=public(R.private_pub k),"private".=private k,"seed".=B.unpack(S.seed v),"message".=B.unpack(S.message v),"cipher".=B.unpack(S.cipherText v)]
keyFields k=["size".=R.public_size(R.private_pub k),"n".=show(R.public_n(R.private_pub k)),"e".=show(R.public_e(R.private_pub k)),"d".=show(R.private_d k),"p".=show(R.private_p k),"q".=show(R.private_q k),"dP".=show(R.private_dP k),"dQ".=show(R.private_dQ k),"qinv".=show(R.private_qinv k)]
sourceCases=let
 vectors=[(S.rsaKeyInt,S.vectorInt)]++map(\v->(S.rsaKey1,v))S.vectorsKey1
 enc(k,v)=object(keyFields k++["op".=String "encrypt","seed".=B.unpack(S.seed v),"message".=B.unpack(S.message v)])
 dec k c=object(keyFields k++["op".=String "decrypt","cipher".=B.unpack c])
 key=S.rsaKey1
 vec=head S.vectorsKey1
 em=P.dp Nothing key(S.cipherText vec)
 poke i w bs=B.concat[B.take i bs,B.singleton w,B.drop(i+1)bs]
 flipBit i bs=poke i(B.index bs i `xor`1)bs
 rejects=[P.ep(R.private_pub key)bad|bad<-[em,poke 0 1 em,flipBit 3 em,flipBit 60 em,flipBit(B.length em-1)em]]++[B.drop 1(S.cipherText vec)]
 modulus=R.public_n(R.private_pub key)
 c=head[S.cipherText v|v<-S.vectorsKey1,os2ip(S.cipherText v)+modulus<2^(8*128)]
 in concatMap(\x->[enc x,dec(fst x)(S.cipherText(snd x))])vectors++map(dec key)rejects++map(dec key)[c,i2ospOf_ 128(os2ip c+modulus),i2ospOf_ 128 modulus]
main=do
 args<-getArgs
 case args of
  ["--suite"]->withArgs["--seed=1729","--qc-max-success=100","--no-color"](hspec S.spec)
  ["--source-cases"]->L.putStrLn(encode sourceCases)
  ["--vectors"]->L.putStrLn(encode([vector S.rsaKeyInt S.vectorInt]++map(vector S.rsaKey1)S.vectorsKey1))
  _->L.getContents>>=mapM_(\s->case eitherDecode s of Left e->error e;Right o->run o>>=L.putStrLn.encode).L.lines
