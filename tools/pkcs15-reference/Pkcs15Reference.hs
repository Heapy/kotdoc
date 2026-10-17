{-# LANGUAGE OverloadedStrings,RankNTypes,ScopedTypeVariables #-}
module Main where
import Crypto.Hash
import qualified Crypto.PubKey.RSA as R
import qualified Crypto.PubKey.RSA.PKCS15 as P
import qualified Crypto.Random as G
import qualified Crypto.Random.ChaChaDRG as D
import qualified Data.ByteString as B
import qualified Data.ByteArray as A
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import Data.Aeson.Types
import Data.Word
import Control.Exception
import Control.DeepSeq
import System.Environment
import Test.Hspec
import qualified OriginalPkcs15Spec as S
field o k=parseMaybe(.:k)o
text o k d=maybe d id(field o k)::String
number o k d=maybe d id(field o k)::Int
integer o k d=read(text o k(show d))::Integer
bytes o k=B.pack(maybe [] id(field o k)::[Word8])
pub o=R.PublicKey(number o "size"128)(integer o "n"(2^(8*max 0(number o "size"128))-1))(integer o "e"1)
key o=R.PrivateKey(pub o)(integer o "d"1)(integer o "p"0)(integer o "q"0)(integer o "dP"0)(integer o "dQ"0)(integer o "qinv"0)
blinder o=if text o "blinder""none"=="none"then Nothing else Just(R.Blinder(integer o "r"1)(integer o "rinv"1))
view(Left e)=object["error".=show e]
view(Right b)=object["bytes".=B.unpack(b::B.ByteString)]
random o action=let
 g=D.initialize(B.pack[fromIntegral((number o "seed"0+i)`mod`256)|i<-[0..39]])
 (r,g')=G.withDRG g action
 (next,_)=G.randomBytesGenerate 65 g'::(B.ByteString,D.ChaChaDRG)
 in object["value".=view r,"next".=B.unpack next]
observe o alg=let h=if text o "hashMode""hash"=="none"then Nothing else Just alg in case text o "op""sign"of
 "padSignature"->view(P.padSignature(number o "size"128)(bytes o "message"))
 "unpad"->view(P.unpad(bytes o "input"))
 "pad"->random o(P.pad(number o "size"128)(bytes o "message"))
 "encrypt"->random o(P.encrypt(pub o)(bytes o "message"))
 "decrypt"->view(P.decrypt(blinder o)(key o)(bytes o "cipher")::Either R.Error B.ByteString)
 "decryptSafer"->random o(P.decryptSafer(key o)(bytes o "cipher"))
 "sign"->view(P.sign(blinder o)h(key o)(bytes o "message"))
 "signSafer"->random o(P.signSafer h(key o)(bytes o "message"))
 "verify"->object["valid".=P.verify h(pub o)(bytes o "message")(bytes o "signature")]
 _->error "unknown op"
choose :: String -> (forall a.P.HashAlgorithmASN1 a=>a->IO Value)->IO Value
choose name f=case name of
 "MD2"->f MD2;"MD5"->f MD5;"SHA1"->f SHA1;"SHA224"->f SHA224;"SHA256"->f SHA256;"SHA384"->f SHA384;"SHA512"->f SHA512;"SHA512t_224"->f SHA512t_224;"SHA512t_256"->f SHA512t_256;"SHA3_224"->f SHA3_224;"SHA3_256"->f SHA3_256;"SHA3_384"->f SHA3_384;"SHA3_512"->f SHA3_512;"RIPEMD160"->f RIPEMD160
 _->error "unknown hash"
run o=do
 result<-try(choose(text o "algorithm""SHA1")(\alg->evaluate(force(observe o alg))))::IO(Either SomeException Value)
 return(either(\e->object["exception".=takeWhile(/='\n')(displayException e)])id result)
main=do
 args<-getArgs
 case args of
  "--suite":rest->withArgs rest(hspec S.spec)
  _->L.getContents>>=mapM_(\line->case eitherDecode line of Left e->error e;Right o->run o>>=L.putStrLn.encode).L.lines
