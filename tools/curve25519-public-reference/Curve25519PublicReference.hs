{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,GeneralizedNewtypeDeriving #-}
module Main where
import qualified Crypto.PubKey.Curve25519 as X
import qualified Crypto.ECC as E
import Crypto.Debug
import Crypto.Error
import Crypto.Random.Types
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.Text as T
import Data.Word
import qualified OriginalCurve25519Spec as O
import qualified OriginalX25519ECCSpec as M
import Test.Hspec(hspec)
import Control.Exception
import Control.DeepSeq
import Control.Monad.Trans.State.Strict
import System.Environment(getArgs,withArgs)
arg o k=case K.lookup k o of Just(String s)->T.unpack s;_->""
bytes o k=case K.lookup k o of Just v->case fromJSON v of Success xs->B.pack(xs::[Word8]);_->error "bytes";_->B.empty
number o k=case K.lookup k o of Just(Number v)->round v;_->0
bs x=toJSON(B.unpack(A.convert x::B.ByteString))
val x=object["bytes" .= bs x,"show" .= show x]
failable f(CryptoFailed e)=object["error" .= show e]
failable f(CryptoPassed x)=object["value" .= f x]
secret o k=throwCryptoError(X.secretKey(bytes o k))
public o k=throwCryptoError(X.publicKey(bytes o k))
newtype RandomProbe a=RandomProbe{probe::StateT(B.ByteString,[Int])IO a}deriving(Functor,Applicative,Monad)
instance MonadRandom RandomProbe where
 getRandomBytes n=RandomProbe$do(payload,ns)<-get;put(payload,ns++[n]);return(A.convert payload)
newtype FailProbe a=FailProbe{runFailProbe::IO a}deriving(Functor,Applicative,Monad)
instance MonadRandom FailProbe where
 getRandomBytes _=FailProbe(throwIO(ErrorCall "rng-failure"))
prx=Just E.Curve_X25519
rng o action render=do(x,(_,ns))<-runStateT(probe action)(bytes o "bytes",[]);evaluate$force$object["value" .= render x,"requests" .= ns]
observe o=case arg o "op"of
 "secretKey"->pure$failable val(X.secretKey(bytes o "bytes"))
 "publicKey"->pure$failable val(X.publicKey(bytes o "bytes"))
 "dhSecret"->pure$failable val(X.dhSecret(bytes o "bytes"))
 "secretShow"->pure$toJSON(showsPrec(number o "precedence")(secret o "bytes")"")
 "publicShow"->pure$toJSON(showsPrec(number o "precedence")(public o "bytes")"")
 "dhShow"->pure$toJSON(showsPrec(number o "precedence")(throwCryptoError(X.dhSecret(bytes o "bytes")))"")
 "debugShow"->pure$toJSON(debugShow(secret o "bytes"))
 "secretEq"->pure$toJSON(secret o "bytes"==secret o "other")
 "publicEq"->pure$toJSON(public o "bytes"==public o "other")
 "dhEq"->pure$toJSON(throwCryptoError(X.dhSecret(bytes o "bytes"))==throwCryptoError(X.dhSecret(bytes o "other")))
 "force"->evaluate(rnf(secret o "bytes",public o "bytes",throwCryptoError(X.dhSecret(bytes o "bytes"))))>>pure Null
 "generate"->rng o X.generateSecretKey val
 "generateThrow"->runFailProbe X.generateSecretKey>>=pure.val
 "toPublic"->pure$val(X.toPublic(secret o "bytes"))
 "dh"->pure$val(X.dh(public o "point")(secret o "bytes"))
 "modernSizes"->pure$toJSON(E.curveSizeBits prx)
 "decodePoint"->pure$failable val(E.decodePoint prx(bytes o "bytes"))
 "decodeScalar"->pure$failable val(E.decodeScalar prx(bytes o "bytes"))
 "encodePoint"->pure$bs(E.encodePoint prx(public o "bytes")::B.ByteString)
 "encodeScalar"->pure$bs(E.encodeScalar prx(secret o "bytes")::B.ByteString)
 "scalarToPoint"->pure$val(E.scalarToPoint prx(secret o "bytes"))
 "generateScalar"->rng o(E.curveGenerateScalar prx)val
 "generateKeyPair"->rng o(E.curveGenerateKeyPair prx)(\x->object["public" .= val(E.keypairGetPublic x),"private" .= val(E.keypairGetPrivate x)])
 "ecdhRaw"->pure$bs(E.ecdhRaw prx(secret o "bytes")(public o "point"))
 "ecdh"->pure$failable bs(E.ecdh prx(secret o "bytes")(public o "point"))
 "pointRoundtrip"->let p=E.scalarToPoint prx(secret o "bytes")in pure$toJSON(E.decodePoint prx(E.encodePoint prx p::B.ByteString)==CryptoPassed p)
 "commutes"->let a=secret o "bytes";b=secret o "other";ap=E.scalarToPoint prx a;bp=E.scalarToPoint prx b;ab=E.ecdh prx a bp;ba=E.ecdh prx b ap in pure$toJSON(ab==ba && ab==CryptoPassed(E.ecdhRaw prx a bp) && ba==CryptoPassed(E.ecdhRaw prx b ap))
 _->error "operation"
main=do
 args<-getArgs
 case args of
  "--original":rest->withArgs rest(hspec O.spec)
  "--modern":rest->withArgs rest(hspec M.spec)
  _->L.getContents>>=mapM_ run.L.lines
 where
 run line=case eitherDecode line of
  Left e->error e
  Right o->do result<-try(observe o>>=evaluate.force);L.putStrLn$encode$case result of Left(e::SomeException)->object["exception" .= takeWhile(/='\n')(displayException e)];Right v->v
