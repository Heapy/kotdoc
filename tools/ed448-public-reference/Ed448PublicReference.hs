{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,GeneralizedNewtypeDeriving #-}
module Main where
import qualified Crypto.PubKey.Ed448 as E
import Crypto.Debug
import Crypto.Error
import Crypto.Random.Types
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Data.Word
import Data.String(fromString)
import Foreign.Ptr
import Foreign.Marshal.Alloc
import Foreign.Marshal.Array
import qualified Data.Text as T
import qualified OriginalEd448Spec as O
import Test.Hspec(hspec)
import Control.Exception
import Control.DeepSeq
import Control.Monad.Trans.State.Strict
import System.Environment(getArgs,withArgs)
arg o k=case K.lookup k o of Just(String s)->T.unpack s;_->""
bytes o k=case K.lookup k o of
 Just v->case fromJSON v of Success xs->B.pack(xs::[Word8]);_->error "bytes"
 Nothing->B.empty
number o k=case K.lookup k o of Just(Number v)->round v;_->0
bs x=toJSON(B.unpack(A.convert x::B.ByteString))
val x=object["bytes" .= bs x,"show" .= show x]
failable f(CryptoFailed e)=object["error" .= show e]
failable f(CryptoPassed x)=object["value" .= f x]
secret o k=throwCryptoError(E.secretKey(bytes o k))
public o k=throwCryptoError(E.publicKey(bytes o k))
signature o k=throwCryptoError(E.signature(bytes o k))
data Source=Source(Ptr Word8)Int
instance A.ByteArrayAccess Source where
 length(Source _ n)=n
 withByteArray(Source ptr _) f=f(castPtr ptr)
newtype RandomProbe a=RandomProbe{probe::StateT(B.ByteString,[Int])IO a}deriving(Functor,Applicative,Monad)
instance MonadRandom RandomProbe where
 getRandomBytes n=RandomProbe$do(payload,ns)<-get;put(payload,ns++[n]);return(A.convert payload)
newtype FailProbe a=FailProbe{runFailProbe::IO a}deriving(Functor,Applicative,Monad)
instance MonadRandom FailProbe where
 getRandomBytes _=FailProbe(throwIO(ErrorCall "rng-failure"))
rng o action render=do(x,(_,ns))<-runStateT(probe action)(bytes o "bytes",[]);evaluate$force$object["value" .= render x,"requests" .= ns]
observe o=case arg o "op"of
 "sizes"->pure$toJSON[E.publicKeySize,E.secretKeySize,E.signatureSize]
 "ownership"->allocaBytes 114$ \ptr->do
  pokeArray ptr(B.unpack(bytes o "bytes"))
  let src=Source ptr 57;wide=Source ptr 114;sec=throwCryptoError(E.secretKey src);pub=throwCryptoError(E.publicKey src);sig=throwCryptoError(E.signature wide)
  evaluate(rnf(sec,pub,sig));pokeArray ptr(replicate 114(99::Word8))
  pure$object["secret" .= val sec,"public" .= val pub,"signature" .= val sig]
 "secretKey"->pure$failable val(E.secretKey(bytes o "bytes"))
 "publicKey"->pure$failable val(E.publicKey(bytes o "bytes"))
 "signature"->pure$failable val(E.signature(bytes o "bytes"))
 "secretShow"->pure$toJSON(showsPrec(number o "precedence")(secret o "bytes")"")
 "publicShow"->pure$toJSON(showsPrec(number o "precedence")(public o "bytes")"")
 "signatureShow"->pure$toJSON(showsPrec(number o "precedence")(signature o "bytes")"")
 "debugShow"->pure$toJSON(debugShow(secret o "bytes"))
 "secretEq"->pure$toJSON(secret o "bytes"==secret o "other")
 "publicEq"->pure$toJSON(public o "bytes"==public o "other")
 "signatureEq"->pure$toJSON(signature o "bytes"==signature o "other")
 "secretForce"->evaluate(rnf(secret o "bytes"))>>pure Null
 "publicForce"->evaluate(rnf(public o "bytes"))>>pure Null
 "signatureForce"->evaluate(rnf(signature o "bytes"))>>pure Null
 "generate"->rng o E.generateSecretKey val
 "generateThrow"->runFailProbe E.generateSecretKey>>=pure.val
 "toPublic"->pure$val(E.toPublic(secret o "bytes"))
 "sign"->pure$val(E.sign(secret o "bytes")(public o "point")(bytes o "message"))
 "unsafeSign"->pure$val(E.unsafeSign(secret o "bytes")(public o "point")(bytes o "message"))
 "verify"->pure$toJSON(E.verify(public o "point")(bytes o "message")(signature o "signature"))
 "signVerify"->let sec=secret o "bytes";supplied=public o "point";message=bytes o "message";actual=E.toPublic sec;safe=E.sign sec supplied message;unsafe=E.unsafeSign sec supplied message in pure$object["public" .= val actual,"safe" .= val safe,"unsafe" .= val unsafe,"safeValid" .= E.verify actual message safe,"unsafeValid" .= E.verify actual message unsafe]
 "secretBottomShow"->pure$toJSON(showsPrec(number o "precedence")(error "secret-bottom"::E.SecretKey)"")
 "publicBottomShow"->pure$toJSON(show(error "public-bottom"::E.PublicKey))
 "signatureBottomShow"->pure$toJSON(show(error "signature-bottom"::E.Signature))
 "secretBottomForce"->evaluate(rnf(error "secret-bottom"::E.SecretKey))>>pure Null
 "publicBottomForce"->evaluate(rnf(error "public-bottom"::E.PublicKey))>>pure Null
 "signatureBottomForce"->evaluate(rnf(error "signature-bottom"::E.Signature))>>pure Null
 "signDemand"->pure$val(E.sign(demand "secret"(secret o "bytes"))(demand "public"(public o "point"))(demand "message"(bytes o "message")))
 "unsafeSignDemand"->pure$val(E.unsafeSign(demand "secret"(secret o "bytes"))(demand "public"(public o "point"))(demand "message"(bytes o "message")))
 "verifyDemand"->pure$toJSON(E.verify(demand "public"(public o "point"))(demand "message"(bytes o "message"))(demand "signature"(signature o "signature")))
 _->error "operation"
 where demand name value=if maybe False(==Bool True)(K.lookup(fromString("bottom_"++name)) o)then error(name++"-bottom")else value
main=do
 args<-getArgs
 case args of
  "--original":rest->withArgs rest(hspec O.spec)
  _->L.getContents>>=mapM_ run.L.lines
 where
 run line=case eitherDecode line of
  Left e->error e
  Right o->do result<-try(observe o>>=evaluate.force);L.putStrLn$encode$case result of Left(e::SomeException)->object["exception" .= takeWhile(/='\n')(displayException e)];Right v->v
