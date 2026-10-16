{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, GeneralizedNewtypeDeriving #-}
{-# OPTIONS_GHC -Wno-deprecations #-}
module Main where
import qualified Crypto.PubKey.ECC.Types as T
import qualified Crypto.PubKey.ECC.Prim as P
import qualified Crypto.PubKey.ECC.DH as D
import Crypto.Error
import Crypto.Random.Types
import qualified Data.ByteArray as A
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.Text as Txt
import qualified OriginalECCSpec as O
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
import Data.Word
arg o k=case K.lookup k o of Just(String s)->Txt.unpack s;_->""
integer o k=read(arg o k)::Integer
c o=T.getCurveByName(read(arg o "curve"))
p o k=case K.lookup k o of Just Null->T.PointO;Just v->case fromJSON v of Success [x,y]->T.Point(read x)(read y);_->error "point input";_->error "missing point"
point T.PointO=Null
point(T.Point x y)=toJSON[show x,show y]
num x=toJSON(show x)
maybeNum=maybe Null num
bs b=toJSON(B.unpack(b::B.ByteString))
shared(CryptoFailed e)=object["error" .= show e]
shared(CryptoPassed(D.SharedKey x))=object["value" .= bs(A.convert x)]
newtype Probe a=Probe{probe::StateT([[Word8]],[Int])IO a}deriving(Functor,Applicative,Monad)
instance MonadRandom Probe where
 getRandomBytes n=Probe$do (payloads,ns)<-get;case payloads of []->error "rng exhausted";b:rest->put(rest,ns++[n])>>return(A.convert(B.pack b))
observe o=let curve=c o;a=integer o "a";b=integer o "b";q=p o "p";r=p o "q" in case arg o "op" of
 "curve"->let cc=T.common_curve curve in pure$object["show" .= show curve,"bits" .= T.curveSizeBits curve,"modulus" .= show(case curve of T.CurveFP x->T.ecc_p x;T.CurveF2m x->T.ecc_fx x),"a" .= show(T.ecc_a cc),"b" .= show(T.ecc_b cc),"g" .= point(T.ecc_g cc),"n" .= show(T.ecc_n cc),"h" .= show(T.ecc_h cc)]
 "originalProperty"->pure$toJSON(case arg o "family" of
   "weight-add"->P.pointBaseMul curve(a+b)==P.pointAdd curve(P.pointBaseMul curve a)(P.pointBaseMul curve b)
   "point-add"->P.pointBaseMul curve ((a+b)`mod`T.ecc_n(T.common_curve curve))==P.pointAdd curve(P.pointBaseMul curve a)(P.pointBaseMul curve b)
   "point-negate-add"->P.pointAdd curve q(P.pointNegate curve q)==T.PointO
   "point-negate-negate"->P.pointNegate curve(P.pointNegate curve q)==q
   "point-mul-mul"->P.pointMul curve(a*b)q==P.pointMul curve a(P.pointMul curve b q)
   "point-mul-matches-double-and-add"->let k=1+a`mod`(T.ecc_n(T.common_curve curve)-1);g=T.ecc_g(T.common_curve curve)in P.pointMul curve k g==O.doubleAndAdd curve k g
   "scalar-arithmetic"->let n=T.ecc_n(T.common_curve curve)in P.scalarAdd curve a b==(a+b)`mod`n && P.scalarMul curve a b==(a*b)`mod`n
   "double-scalar-mult"->P.pointAddTwoMuls curve a q b r==P.pointAdd curve(O.doubleAndAdd curve a q)(O.doubleAndAdd curve b r)
   _->error "property family")
 "scalarAdd"->pure$num(P.scalarAdd curve a b)
 "scalarMul"->pure$num(P.scalarMul curve a b)
 "scalarInverse"->pure$maybeNum(P.scalarInverse curve a)
 "pointAdd"->pure$point(P.pointAdd curve q r)
 "pointDouble"->pure$point(P.pointDouble curve q)
 "pointNegate"->pure$point(P.pointNegate curve q)
 "pointBaseMul"->pure$point(P.pointBaseMul curve a)
 "pointMul"->pure$point(P.pointMul curve a q)
 "pointAddTwoMuls"->pure$point(P.pointAddTwoMuls curve a q b r)
 "pointDecompose"->pure$maybe Null(\(i,r,b)->object["index" .= show i,"residue" .= show r,"parity" .= b])(P.pointDecompose curve q)
 "pointCompose"->pure$maybe Null point(P.pointCompose curve a b (case K.lookup "parity" o of Just(Bool x)->x;_->False))
 "isPointAtInfinity"->pure$toJSON(P.isPointAtInfinity q)
 "isPointValid"->pure$toJSON(P.isPointValid curve q)
 "isPointInSubgroup"->pure$toJSON(P.isPointInSubgroup curve q)
 "calculatePublic"->pure$point(D.calculatePublic curve a)
 "tryGetShared"->pure$shared(D.tryGetShared curve a q)
 "getShared"->let D.SharedKey key=D.getShared curve a q in pure$bs(A.convert key)
 "scalarGenerate"->rng o(P.scalarGenerate curve)
 "generatePrivate"->rng o(D.generatePrivate curve)
 _->error "unknown op"
rng o action=do
 let payloads=case K.lookup "payloads" o of Just v->case fromJSON v of Success x->x;Error e->error e;_->[]
 (x,(_,ns))<-runStateT(probe action)(payloads,[])
 pure$object["value" .= show x,"requests" .= ns]
main=do
 hSetBuffering stdout LineBuffering
 args<-getArgs
 case args of
  "--original":rest->withArgs rest(hspec O.spec)
  ["--inventory"]->L.putStrLn$encode$object["curves" .= map show([minBound..maxBound]::[T.CurveName]),"subgroups" .= [object["curve" .= show n,"point" .= point p]|(n,p)<-O.outOfSubgroup],"vectors" .= [object["curve" .= show(head[n|n<-[minBound..maxBound],T.getCurveByName n==O.curve v]),"point" .= point(T.Point(O.x v)(O.y v)),"valid" .= O.valid v]|v<-O.vectorsPoint],"generators" .= [let curve=unGen arbitrary(mkQCGen(3001+i*3))(i`mod`101);Utils.QAInteger a=unGen arbitrary(mkQCGen(3002+i*3))(i`mod`101);Utils.QAInteger b=unGen arbitrary(mkQCGen(3003+i*3))(i`mod`101) in object["curve" .= show(head[n|n<-[minBound..maxBound],T.getCurveByName n==curve]),"a" .= show a,"b" .= show b,"p" .= point(unGen(O.arbitraryPoint curve)(mkQCGen(8001+i*2))(i`mod`101)),"q" .= point(unGen(O.arbitraryPoint curve)(mkQCGen(8002+i*2))(i`mod`101))]|i<-[0..139]]]
  _->L.getContents>>=mapM_ run.L.lines
 where
 run line=case eitherDecode line of
  Left e->error e
  Right o->do
   value<-try(observe o >>= evaluate.force)::IO(Either SomeException Value)
   L.putStrLn$encode$case value of Right v->v;Left e->object["exception" .= takeWhile(/='\n')(displayException e)]
