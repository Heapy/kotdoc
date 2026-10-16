{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, GeneralizedNewtypeDeriving #-}
module Main where
import qualified Crypto.PubKey.ECC.P256 as P
import Crypto.Error
import Crypto.Random.Types
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.Text as T
import qualified OriginalP256Spec as O
import qualified Utils
import Test.Hspec(hspec)
import Test.QuickCheck hiding(Success)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import Control.Exception
import Control.DeepSeq
import Control.Monad.Trans.State.Strict
import System.Environment(getArgs,withArgs)
import Data.Word
arg o k=case K.lookup k o of Just(String s)->T.unpack s;_->""
integer o k=case K.lookup k o of Just(String s)->read(T.unpack s);Just(Number s)->round s;_->0
bytes o k=case K.lookup k o of Just v->case fromJSON v of Success xs->B.pack(xs::[Word8]);Error e->error e;_->B.empty
s o k=throwCryptoError(P.scalarFromInteger(integer o k))
p o k=case K.lookup k o of Just v->case fromJSON v of Success [x,y]->P.pointFromIntegers(read x,read y);_->error "point input";_->P.pointFromIntegers(0,0)
bs b=toJSON(B.unpack(b::B.ByteString))
scalar x=object["binary" .= bs(P.scalarToBinary x),"integer" .= show(P.scalarToInteger x),"show" .= show x,"raw" .= bs(A.convert x)]
point x=let(a,b)=P.pointToIntegers x in object["binary" .= bs(P.pointToBinary x),"x" .= show a,"y" .= show b,"show" .= show x,"infinity" .= P.pointIsAtInfinity x,"valid" .= P.pointIsValid x]
failable f (CryptoFailed e)=object["error" .= show e]
failable f (CryptoPassed x)=object["value" .= f x]
newtype RandomProbe a=RandomProbe{probe::StateT(B.ByteString,[Int])IO a}deriving(Functor,Applicative,Monad)
instance MonadRandom RandomProbe where
 getRandomBytes n=RandomProbe$do (payload,ns)<-get;put(payload,ns++[n]);return(A.convert payload)
newtype FailProbe a=FailProbe{runFailProbe::IO a}deriving(Functor,Applicative,Monad)
instance MonadRandom FailProbe where
 getRandomBytes _=FailProbe(throwIO(ErrorCall "rng-failure"))
observe o=case arg o "op" of
 "scalarGenerateThrow"->runFailProbe P.scalarGenerate >>= pure.scalar
 "originalInvSafeMul"->pure$scalar(P.scalarMul(s o "a")(P.scalarInvSafe(s o "a")))
 "originalMarshalPoint"->pure$failable point(P.unsafePointFromBinary(P.pointToBinary(P.pointFromIntegers(integer o "a",integer o "b"))::B.ByteString))
 "originalPointAdd"->pure$point(P.pointAdd(P.toPoint(s o "a"))(P.toPoint(s o "b")))
 "originalPointAddInfinity"->pure$point(P.pointAdd(P.pointFromIntegers(0,0))(P.toPoint(s o "a")))
 "originalPointAddInverse"->let x=P.toPoint(s o "a") in pure$point(P.pointAdd x(P.pointNegate x))
 "originalPointNegate"->pure$point(P.pointNegate(P.toPoint(s o "a")))
 "originalPointMul"->pure$point(P.pointMul(P.scalarReduce(s o "a"))(P.toPoint(P.scalarReduce(s o "b"))))
 "scalarFromBinary"->pure$failable scalar(P.scalarFromBinary(bytes o "bytes"))
 "scalarFromInteger"->pure$failable scalar(P.scalarFromInteger(integer o "a"))
 "scalarZero"->pure$scalar P.scalarZero
 "scalarN"->pure$scalar P.scalarN
 "scalar"->pure$scalar(s o "a")
 "scalarReduce"->pure$scalar(P.scalarReduce(s o "a"))
 "scalarAdd"->pure$scalar(P.scalarAdd(s o "a")(s o "b"))
 "scalarSub"->pure$scalar(P.scalarSub(s o "a")(s o "b"))
 "scalarMul"->pure$scalar(P.scalarMul(s o "a")(s o "b"))
 "scalarInv"->pure$scalar(P.scalarInv(s o "a"))
 "scalarInvSafe"->pure$scalar(P.scalarInvSafe(s o "a"))
 "scalarCmp"->pure$toJSON(show(P.scalarCmp(s o "a")(s o "b")))
 "scalarIsZero"->pure$toJSON(P.scalarIsZero(s o "a"))
 "scalarShow"->pure$toJSON(showsPrec(fromInteger(integer o "precedence"))(s o "a")"")
 "scalarEq"->pure$toJSON(s o "a"==s o "b")
 "scalarForce"->evaluate(rnf(s o "a"))>>pure Null
 "scalarGenerate"->do (x,(_,ns))<-runStateT(probe P.scalarGenerate)(bytes o "bytes",[]);v<-evaluate$force(scalar x);return(object["value" .= v,"requests" .= ns])
 "pointBase"->pure$point P.pointBase
 "toPoint"->pure$point(P.toPoint(s o "a"))
 "pointFromIntegers"->pure$point(P.pointFromIntegers(integer o "x",integer o "y"))
 "unsafePointFromBinary"->pure$failable point(P.unsafePointFromBinary(bytes o "bytes"))
 "pointFromBinary"->pure$failable point(P.pointFromBinary(bytes o "bytes"))
 "point"->pure$point(p o "p")
 "pointAdd"->pure$point(P.pointAdd(p o "p")(p o "q"))
 "pointNegate"->pure$point(P.pointNegate(p o "p"))
 "pointMul"->pure$point(P.pointMul(s o "a")(p o "p"))
 "pointDh"->pure$bs(P.pointDh(s o "a")(p o "p"))
 "pointsMulVarTime"->pure$point(P.pointsMulVarTime(s o "a")(s o "b")(p o "p"))
 "pointIsValid"->pure$toJSON(P.pointIsValid(p o "p"))
 "pointIsAtInfinity"->pure$toJSON(P.pointIsAtInfinity(p o "p"))
 "pointX"->pure$maybe Null scalar(P.pointX(p o "p"))
 "pointShow"->pure$toJSON(showsPrec(fromInteger(integer o "precedence"))(p o "p")"")
 "pointEq"->pure$toJSON(p o "p"==p o "q")
 "pointForce"->evaluate(rnf(p o "p"))>>pure Null
 _->error "unknown operation"
main=do
 args<-getArgs
 case args of
  "--original":rest->withArgs rest(hspec O.spec)
  ["--generators"]->L.putStrLn$encode[let O.P256Scalar a=unGen arbitrary(mkQCGen(1729+i*3))(i`mod`101);O.P256Scalar b=unGen arbitrary(mkQCGen(1730+i*3))(i`mod`101);Utils.QAInteger c=unGen arbitrary(mkQCGen(1731+i*3))(i`mod`101)in object["a" .= show a,"b" .= show b,"qa" .= show c]|i<-[0..399]]
  _->L.getContents>>=mapM_ run.L.lines
 where
 run line=case eitherDecode line of
  Left e->error e
  Right o->do result<-try(observe o>>=evaluate.force);L.putStrLn$encode$case result of Left(e::SomeException)->object["exception" .= takeWhile(/='\n')(displayException e)];Right v->v
