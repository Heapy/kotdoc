{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, GeneralizedNewtypeDeriving, FlexibleContexts, TypeFamilies #-}
{-# OPTIONS_GHC -Wno-deprecations #-}
module Main where
import qualified OriginalPrimeECC as E
import qualified OriginalSimpleTypes as S
import qualified OriginalSimplePrim as P
import qualified Crypto.PubKey.ECC.P256 as P256
import qualified Crypto.PubKey.ECC.Types as L
import Crypto.Error
import Crypto.Random.Types
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as BL
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.Text as T
import qualified OriginalModernECCSpec as O
import qualified OriginalLegacyECCSpec as OL
import qualified Utils
import Test.Hspec(hspec)
import Test.QuickCheck hiding(Success)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import Control.Exception
import Control.DeepSeq
import Control.Monad.Trans.State.Strict
import System.Environment(getArgs,withArgs)
import System.IO(stdout,hSetBuffering,BufferMode(LineBuffering))
import Data.Proxy
import Data.Word
arg o k=case K.lookup k o of Just(String s)->T.unpack s;_->""
integer o k=read(arg o k)::Integer
rawPoint o k=case K.lookup k o of Just Null->Nothing;Just(Array xs)->case fromJSON(Array xs)of Success [x,y]->Just(read x,read y);_->error "point input";_->error "point missing"
simplePoint o k=maybe S.PointO(uncurry S.Point)(rawPoint o k)
p256Point o k=maybe(P256.pointFromIntegers(0,0))P256.pointFromIntegers(rawPoint o k)
bytes o k=case K.lookup k o of Just v->case fromJSON v of Success xs->B.pack(xs::[Word8]);Error e->error e;_->B.empty
bs b=toJSON(B.unpack(b::B.ByteString))
num x=toJSON(show x)
point S.PointO=Null
point(S.Point x y)=toJSON[show x,show y]
scalar(S.Scalar x)=num x
failable f(CryptoFailed e)=object["error" .= show e]
failable f(CryptoPassed x)=object["value" .= f x]
newtype Probe a=Probe{probe::StateT([[Word8]],[Int])IO a}deriving(Functor,Applicative,Monad)
instance MonadRandom Probe where
 getRandomBytes n=Probe$do (payloads,ns)<-get;case payloads of []->error "rng exhausted";b:rest->put(rest,ns++[n])>>return(A.convert(B.pack b))
rng o action snap=do
 let payloads=case K.lookup "payloads" o of Just v->case fromJSON v of Success x->x;Error e->error e;_->[]
 (x,(_,ns))<-runStateT(probe action)(payloads,[])
 v<-snap x >>= evaluate.force
 pure$object["value" .= v,"requests" .= ns]
observeSimple::forall c.S.Curve c=>Proxy c->Object->IO Value
observeSimple prx o=let a=S.Scalar(integer o "a")::S.Scalar c;b=S.Scalar(integer o "b")::S.Scalar c;p=simplePoint o "p"::S.Point c;q=simplePoint o "q"::S.Point c;cc=S.curveParameters prx in pure$case arg o "op" of
 "type"->object["type" .= show(S.curveType prx),"parameters" .= show cc,"bits" .= S.curveSizeBits prx,"bytes" .= S.curveSizeBytes prx,"a" .= show(S.curveEccA cc),"b" .= show(S.curveEccB cc),"g" .= point(S.curveEccG cc),"n" .= show(S.curveEccN cc),"h" .= show(S.curveEccH cc)]
 "scalarFromInteger"->failable scalar(P.scalarFromInteger(integer o "a")::CryptoFailable(S.Scalar c))
 "pointFromIntegers"->failable point(P.pointFromIntegers(integer o "a",integer o "b")::CryptoFailable(S.Point c))
 "pointAdd"->point(P.pointAdd p q)
 "pointNegate"->point(P.pointNegate p)
 "pointDouble"->point(P.pointDouble p)
 "pointBaseMul"->point(P.pointBaseMul a)
 "pointMul"->point(P.pointMul a p)
 "pointAddTwoMuls"->point(P.pointAddTwoMuls a p b q)
 "isPointAtInfinity"->toJSON(P.isPointAtInfinity p)
 "isPointValid"->toJSON(P.isPointValid prx(integer o "a")(integer o "b"))
 "isPointInSubgroup"->toJSON(P.isPointInSubgroup prx p)
 "scalarShow"->toJSON(showsPrec(fromInteger(integer o "precedence"))a "")
 "pointShow"->toJSON(show p)
 _->error "simple operation"
runSimple::forall c.S.Curve c=>Proxy c->Object->IO Value
runSimple prx o=if arg o "op"=="scalarGenerate" then rng o(P.scalarGenerate::Probe(S.Scalar c))(pure.scalar) else observeSimple prx o
pointValue::E.EllipticCurve c=>Proxy c->E.Point c->IO Value
pointValue prx p=do
 r<-try(evaluate$force(bs(E.encodePoint prx p)))::IO(Either SomeException Value)
 pure$case r of Right v->object["binary" .= v];Left e->object["encodingException" .= takeWhile(/='\n')(displayException e)]
modern::forall c.(E.EllipticCurveBasepointArith c,E.EllipticCurveDH c)=>Proxy c->(Object->Key->E.Point c)->Object->IO Value
modern prx getPoint o=let s=throwCryptoError(E.scalarFromInteger prx(integer o "a"));t=throwCryptoError(E.scalarFromInteger prx(integer o "b"));p=getPoint o "p";q=getPoint o "q" in case arg o "op" of
 "sizes"->pure$object["bits" .= E.curveSizeBits prx,"orderBits" .= E.curveOrderBits prx]
 "scalarFromInteger"->pure$failable(num.E.scalarToInteger prx)(E.scalarFromInteger prx(integer o "a"))
 "decodeScalar"->pure$failable(num.E.scalarToInteger prx)(E.decodeScalar prx(bytes o "bytes"))
 "encodeScalar"->pure$bs(E.encodeScalar prx s)
 "decodePoint"->case E.decodePoint prx(bytes o "bytes")of CryptoFailed e->pure$object["error" .= show e];CryptoPassed x->object . (:[]) . ("value" .=) <$> pointValue prx x
 "encodePoint"->pure$bs(E.encodePoint prx p)
 "scalarToPoint"->pointValue prx(E.scalarToPoint prx s)
 "pointBaseSmul"->pointValue prx(E.pointBaseSmul prx s)
 "pointAdd"->pointValue prx(E.pointAdd prx p q)
 "pointNegate"->pointValue prx(E.pointNegate prx p)
 "pointSmul"->pointValue prx(E.pointSmul prx s p)
 "pointsSmulVarTime"->pointValue prx(E.pointsSmulVarTime prx s t p)
 "scalarAdd"->pure$num(E.scalarToInteger prx(E.scalarAdd prx s t))
 "scalarMul"->pure$num(E.scalarToInteger prx(E.scalarMul prx s t))
 "ecdh"->pure$failable(\(E.SharedSecret x)->bs(A.convert x))(E.ecdh prx s p)
 "ecdhRaw"->let E.SharedSecret x=E.ecdhRaw prx s p in pure$bs(A.convert x)
 "generateScalar"->rng o(E.curveGenerateScalar prx)(pure.num.E.scalarToInteger prx)
 "generateKeyPair"->rng o(E.curveGenerateKeyPair prx)(\kp->do pv<-pointValue prx(E.keypairGetPublic kp);pure$object["public" .= pv,"private" .= show(E.scalarToInteger prx(E.keypairGetPrivate kp))])
 "property"->pure$toJSON$let u=throwCryptoError(E.scalarFromInteger prx(integer o "s"));base=E.pointBaseSmul prx u;alice=E.scalarToPoint prx s;bob=E.scalarToPoint prx t in case arg o "family" of
   "decodePoint.encodePoint==id"->E.decodePoint prx(E.encodePoint prx base::B.ByteString)==CryptoPassed base
   "ECDH commutes"->let ab=E.ecdh prx s bob;ba=E.ecdh prx t alice in ab==ba && ab==CryptoPassed(E.ecdhRaw prx s bob) && ba==CryptoPassed(E.ecdhRaw prx t alice)
   "decodeScalar.encodeScalar==id"->E.decodeScalar prx(E.encodeScalar prx u::B.ByteString)==CryptoPassed u
   "scalarFromInteger.scalarToInteger==id"->E.scalarFromInteger prx(E.scalarToInteger prx u)==CryptoPassed u
   "(a + b).P = a.P + b.P"->E.pointSmul prx(E.scalarAdd prx s t)base==E.pointAdd prx(E.pointSmul prx s base)(E.pointSmul prx t base)
   "(a * b).P = a.(b.P)"->E.pointSmul prx(E.scalarMul prx s t)base==E.pointSmul prx s(E.pointSmul prx t base)
   _->error "property family"
 _->error "modern operation"
samples::(E.EllipticCurveBasepointArith c)=>Proxy c->[Value]
samples prx=[let drg=unGen arbitrary(mkQCGen(9001+i))(i`mod`101);(s,a,b)=Utils.withTestDRG drg$ (,,)<$>E.curveGenerateScalar prx<*>E.curveGenerateScalar prx<*>E.curveGenerateScalar prx in object["s" .= show(E.scalarToInteger prx s),"a" .= show(E.scalarToInteger prx a),"b" .= show(E.scalarToInteger prx b)]|i<-[0..19]]

simpleDispatch o=case arg o "curve" of
 "SEC_p112r1"->runSimple(Proxy::Proxy S.SEC_p112r1)o
 "SEC_p112r2"->runSimple(Proxy::Proxy S.SEC_p112r2)o
 "SEC_p128r1"->runSimple(Proxy::Proxy S.SEC_p128r1)o
 "SEC_p128r2"->runSimple(Proxy::Proxy S.SEC_p128r2)o
 "SEC_p160k1"->runSimple(Proxy::Proxy S.SEC_p160k1)o
 "SEC_p160r1"->runSimple(Proxy::Proxy S.SEC_p160r1)o
 "SEC_p160r2"->runSimple(Proxy::Proxy S.SEC_p160r2)o
 "SEC_p192k1"->runSimple(Proxy::Proxy S.SEC_p192k1)o
 "SEC_p192r1"->runSimple(Proxy::Proxy S.SEC_p192r1)o
 "SEC_p224k1"->runSimple(Proxy::Proxy S.SEC_p224k1)o
 "SEC_p224r1"->runSimple(Proxy::Proxy S.SEC_p224r1)o
 "SEC_p256k1"->runSimple(Proxy::Proxy S.SEC_p256k1)o
 "SEC_p256r1"->runSimple(Proxy::Proxy S.SEC_p256r1)o
 "SEC_p384r1"->runSimple(Proxy::Proxy S.SEC_p384r1)o
 "SEC_p521r1"->runSimple(Proxy::Proxy S.SEC_p521r1)o
 "SEC_t113r1"->runSimple(Proxy::Proxy S.SEC_t113r1)o
 "SEC_t113r2"->runSimple(Proxy::Proxy S.SEC_t113r2)o
 "SEC_t131r1"->runSimple(Proxy::Proxy S.SEC_t131r1)o
 "SEC_t131r2"->runSimple(Proxy::Proxy S.SEC_t131r2)o
 "SEC_t163k1"->runSimple(Proxy::Proxy S.SEC_t163k1)o
 "SEC_t163r1"->runSimple(Proxy::Proxy S.SEC_t163r1)o
 "SEC_t163r2"->runSimple(Proxy::Proxy S.SEC_t163r2)o
 "SEC_t193r1"->runSimple(Proxy::Proxy S.SEC_t193r1)o
 "SEC_t193r2"->runSimple(Proxy::Proxy S.SEC_t193r2)o
 "SEC_t233k1"->runSimple(Proxy::Proxy S.SEC_t233k1)o
 "SEC_t233r1"->runSimple(Proxy::Proxy S.SEC_t233r1)o
 "SEC_t239k1"->runSimple(Proxy::Proxy S.SEC_t239k1)o
 "SEC_t283k1"->runSimple(Proxy::Proxy S.SEC_t283k1)o
 "SEC_t283r1"->runSimple(Proxy::Proxy S.SEC_t283r1)o
 "SEC_t409k1"->runSimple(Proxy::Proxy S.SEC_t409k1)o
 "SEC_t409r1"->runSimple(Proxy::Proxy S.SEC_t409r1)o
 "SEC_t571k1"->runSimple(Proxy::Proxy S.SEC_t571k1)o
 "SEC_t571r1"->runSimple(Proxy::Proxy S.SEC_t571r1)o
 _->error "simple curve"
observe o=case arg o "layer" of
 "simple"->simpleDispatch o
 "modern"->case arg o "curve" of
  "P256"->modern(Proxy::Proxy E.Curve_P256R1)p256Point o
  "P384"->modern(Proxy::Proxy E.Curve_P384R1)simplePoint o
  "P521"->modern(Proxy::Proxy E.Curve_P521R1)simplePoint o
  _->error "modern curve"
 _->error "layer"
main=do
 hSetBuffering stdout LineBuffering
 args<-getArgs
 case args of
  "--original":rest->withArgs rest(hspec O.spec)
  ["--inventory"]->BL.putStrLn$encode$object["vectors" .= [case O.vpCurve v of O.Curve curve->object["curve" .= show curve,"bytes" .= bs(O.vpEncodedPoint v),"error" .= fmap show(O.vpError v)]|v<-O.vectorsPoint],"KATs" .= [object["private" .= bs(O.unhex a),"public" .= bs(O.unhex b),"shared" .= bs(O.unhex c)]|(a,b,c)<-O.vectorsECDH],"samples" .= object["P256" .= samples(Proxy::Proxy E.Curve_P256R1),"P384" .= samples(Proxy::Proxy E.Curve_P384R1),"P521" .= samples(Proxy::Proxy E.Curve_P521R1)],"subgroups" .= [object["curve" .= show n,"p" .= (case p of L.Point x y->toJSON[show x,show y];L.PointO->Null)]|(n,p)<-OL.outOfSubgroup]]
  _->BL.getContents>>=mapM_ run.BL.lines
 where
 run line=case eitherDecode line of
  Left e->error e
  Right o->do
   value<-try(observe o >>= evaluate.force)::IO(Either SomeException Value)
   BL.putStrLn$encode$case value of Right v->v;Left e->object["exception" .= takeWhile(/='\n')(displayException e)]
