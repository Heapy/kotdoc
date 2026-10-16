{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,GeneralizedNewtypeDeriving #-}
module Main where
import qualified Crypto.ECC.Edwards25519 as X
import qualified Crypto.ECC as E
import Crypto.Error
import Crypto.Random.Types
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.Text as T
import Data.Word
import qualified OriginalEdwards25519Spec as O
import qualified OriginalEdwardsModernSpec as M
import Test.Hspec(hspec)
import qualified Utils
import Test.QuickCheck hiding(Success)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import Control.Exception
import Control.DeepSeq
import Control.Monad.Trans.State.Strict
import System.Environment(getArgs,withArgs)
arg o k=case K.lookup k o of Just(String s)->T.unpack s;_->""
bytes o k=case K.lookup k o of Just v->case fromJSON v of Success xs->B.pack(xs::[Word8]);_->error "bytes";_->B.empty
number o k=case K.lookup k o of Just(Number v)->round v;_->0
bs x=toJSON(B.unpack(x::B.ByteString))
sv s=object["bytes" .= bs(X.scalarEncode s),"show" .= show s]
pv p=object["bytes" .= bs(X.pointEncode p),"show" .= show p,"prime" .= X.pointHasPrimeOrder p]
failable f(CryptoFailed e)=object["error" .= show e]
failable f(CryptoPassed x)=object["value" .= f x]
scalar o k=throwCryptoError(X.scalarDecodeLong(bytes o k))
point o k=throwCryptoError(X.pointDecode(bytes o k))
prx=Just E.Curve_Edwards25519
newtype RandomProbe a=RandomProbe{probe::StateT(B.ByteString,[Int])IO a}deriving(Functor,Applicative,Monad)
instance MonadRandom RandomProbe where
 getRandomBytes n=RandomProbe$do(payload,ns)<-get;put(payload,ns++[n]);return(A.convert payload)
newtype FailProbe a=FailProbe{runFailProbe::IO a}deriving(Functor,Applicative,Monad)
instance MonadRandom FailProbe where
 getRandomBytes _=FailProbe(throwIO(ErrorCall "rng-failure"))
rng o action render=do(x,(_,ns))<-runStateT(probe action)(bytes o "bytes",[]);evaluate$force$object["value" .= render x,"requests" .= ns]
observe o=case arg o "op"of
 "scalarDecodeLong"->pure$failable sv(X.scalarDecodeLong(bytes o "bytes"))
 "scalarEncode"->pure$bs(X.scalarEncode(scalar o "bytes"))
 "scalarShow"->pure$toJSON(showsPrec(number o "precedence")(scalar o "bytes")"")
 "scalarEq"->pure$toJSON(scalar o "bytes"==scalar o "other")
 "scalarAdd"->pure$sv(X.scalarAdd(scalar o "bytes")(scalar o "other"))
 "scalarMul"->pure$sv(X.scalarMul(scalar o "bytes")(scalar o "other"))
 "scalarGenerate"->rng o X.scalarGenerate sv
 "generateThrow"->runFailProbe X.scalarGenerate>>=pure.sv
 "pointDecode"->pure$failable pv(X.pointDecode(bytes o "bytes"))
 "pointEncode"->pure$bs(X.pointEncode(point o "point"))
 "pointShow"->pure$toJSON(showsPrec(number o "precedence")(point o "point")"")
 "pointEq"->pure$toJSON(point o "point"==point o "otherPoint")
 "toPoint"->pure$pv(X.toPoint(scalar o "bytes"))
 "pointHasPrimeOrder"->pure$toJSON(X.pointHasPrimeOrder(point o "point"))
 "pointNegate"->pure$pv(X.pointNegate(point o "point"))
 "pointAdd"->pure$pv(X.pointAdd(point o "point")(point o "otherPoint"))
 "pointDouble"->pure$pv(X.pointDouble(point o "point"))
 "pointMul"->pure$pv(X.pointMul(scalar o "bytes")(point o "point"))
 "pointMulByCofactor"->pure$pv(X.pointMulByCofactor(point o "point"))
 "pointsMulVarTime"->pure$pv(X.pointsMulVarTime(scalar o "bytes")(scalar o "other")(point o "point"))
 "force"->evaluate(rnf(scalar o "bytes",point o "point"))>>pure Null
 "scalarShowBottom"->pure$toJSON(showsPrec(number o "precedence")(error "scalar-bottom"::X.Scalar)"")
 "pointShowBottom"->pure$toJSON(show(error "point-bottom"::X.Point))
 "scalarForceBottom"->evaluate(rnf(error "scalar-bottom"::X.Scalar))>>pure Null
 "pointForceBottom"->evaluate(rnf(error "point-bottom"::X.Point))>>pure Null
 "property"->let a=scalar o "bytes";b=scalar o "other";c=scalar o "third";p=point o "point";q=point o "otherPoint";r=point o "thirdPoint";prime=point o "prime";small n=throwCryptoError(X.scalarDecodeLong(B.singleton n));z=X.toPoint(small 0);nm1=throwCryptoError(E.scalarFromInteger prx(2^252+27742317777372353535851937790883648492))in pure$toJSON$case number o "family"of
  0->X.scalarDecodeLong(X.scalarEncode a::B.ByteString)==CryptoPassed a
  1->X.scalarAdd(small 0)a==a && X.scalarAdd a(small 0)==a
  2->X.scalarAdd a(X.scalarAdd b c)==X.scalarAdd(X.scalarAdd a b)c
  3->X.scalarAdd a b==X.scalarAdd b a
  4->X.scalarMul(small 0)a==small 0 && X.scalarMul a(small 0)==small 0
  5->X.scalarMul(small 1)a==a && X.scalarMul a(small 1)==a
  6->X.scalarMul a(X.scalarMul b c)==X.scalarMul(X.scalarMul a b)c
  7->X.scalarMul a b==X.scalarMul b a
  8->X.scalarMul a(X.scalarAdd b c)==X.scalarAdd(X.scalarMul a b)(X.scalarMul a c) && X.scalarMul(X.scalarAdd b c)a==X.scalarAdd(X.scalarMul b a)(X.scalarMul c a)
  9->X.pointDecode(X.pointEncode p::B.ByteString)==CryptoPassed p
  10->fmap(X.pointEncode::X.Point->B.ByteString)(X.pointDecode(X.pointEncode p::B.ByteString))==CryptoPassed(X.pointEncode p)
  11->X.pointAdd z p==p && X.pointAdd p z==p
  12->X.pointAdd p(X.pointAdd q r)==X.pointAdd(X.pointAdd p q)r
  13->X.pointAdd p q==X.pointAdd q p
  14->X.pointAdd p(X.pointNegate p)==z
  15->X.pointAdd p p==X.pointDouble p
  16->X.pointMul(small 8)p==X.pointMulByCofactor p
  17->X.pointHasPrimeOrder prime
  18->X.pointMul(small 0)p==z
  19->X.pointMul(small 1)p==p
  20->X.pointMul(small 2)p==X.pointDouble p
  21->X.pointHasPrimeOrder p==(X.pointNegate p==X.pointMul nm1 p)
  22->X.pointMul a(X.toPoint b)==X.pointMul b(X.toPoint a)
  23->X.pointAdd(X.pointMul a prime)(X.pointMul b prime)==X.pointMul(X.scalarAdd a b)prime
  24->X.pointAdd(X.toPoint a)(X.pointMul b p)==X.pointsMulVarTime a b p
  _->error "family"
 "modernProperty"->let a=scalar o "bytes";b=scalar o "other";c=scalar o "third";p=E.pointBaseSmul prx c in pure$toJSON$case number o "family"of
  0->E.decodeScalar prx(E.encodeScalar prx a::B.ByteString)==CryptoPassed a
  1->E.scalarFromInteger prx(E.scalarToInteger prx a)==CryptoPassed a
  2->E.pointSmul prx(E.scalarAdd prx a b)p==E.pointAdd prx(E.pointSmul prx a p)(E.pointSmul prx b p)
  3->E.pointSmul prx(E.scalarMul prx a b)p==E.pointSmul prx a(E.pointSmul prx b p)
  _->error "family"
 "sizes"->pure$toJSON[E.curveSizeBits prx,E.curveOrderBits prx]
 "decodeScalar"->pure$failable sv(E.decodeScalar prx(bytes o "bytes"))
 "decodePoint"->pure$failable pv(E.decodePoint prx(bytes o "bytes"))
 "encodeScalar"->pure$bs(E.encodeScalar prx(scalar o "bytes"))
 "encodePoint"->pure$bs(E.encodePoint prx(point o "point"))
 "scalarFromInteger"->pure$failable sv(E.scalarFromInteger prx(read(arg o "integer")))
 "scalarToInteger"->pure$toJSON(show(E.scalarToInteger prx(scalar o "bytes")))
 "scalarToPoint"->pure$pv(E.scalarToPoint prx(scalar o "bytes"))
 "pointBaseSmul"->pure$pv(E.pointBaseSmul prx(scalar o "bytes"))
 "pointSmul"->pure$pv(E.pointSmul prx(scalar o "bytes")(point o "point"))
 "modernPointAdd"->pure$pv(E.pointAdd prx(point o "point")(point o "otherPoint"))
 "modernPointNegate"->pure$pv(E.pointNegate prx(point o "point"))
 "modernScalarAdd"->pure$sv(E.scalarAdd prx(scalar o "bytes")(scalar o "other"))
 "modernScalarMul"->pure$sv(E.scalarMul prx(scalar o "bytes")(scalar o "other"))
 "pointsSmulVarTime"->pure$pv(E.pointsSmulVarTime prx(scalar o "bytes")(scalar o "other")(point o "point"))
 "generateScalar"->rng o(E.curveGenerateScalar prx)sv
 "generateKeyPair"->rng o(E.curveGenerateKeyPair prx)(\x->object["public" .= pv(E.keypairGetPublic x),"private" .= sv(E.keypairGetPrivate x)])
 _->error "operation"
main=do
 args<-getArgs
 case args of
  "--original":rest->withArgs rest(hspec O.spec)
  "--modern":rest->withArgs rest(hspec M.spec)
  ["--samples"]->L.putStrLn$encode[let drg=unGen arbitrary(mkQCGen(9001+i))(i`mod`101);(a,b,c)=Utils.withTestDRG drg$ (,,)<$>E.curveGenerateScalar prx<*>E.curveGenerateScalar prx<*>E.curveGenerateScalar prx in object["bytes" .= bs(X.scalarEncode a),"other" .= bs(X.scalarEncode b),"third" .= bs(X.scalarEncode c)]|i<-[0..99]]
  _->L.getContents>>=mapM_ run.L.lines
 where
 run line=case eitherDecode line of
  Left e->error e
  Right o->do result<-try(observe o>>=evaluate.force);L.putStrLn$encode$case result of Left(e::SomeException)->object["exception" .= takeWhile(/='\n')(displayException e)];Right v->v
