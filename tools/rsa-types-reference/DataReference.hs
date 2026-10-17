{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,RankNTypes,TypeApplications #-}
module Main where
import qualified Crypto.PubKey.RSA.Types as R
import Data.Data
import Data.Aeson
import Data.Aeson.Types
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Functor.Const
import Data.Functor.Identity
import Data.Maybe
import Control.Exception
import Control.DeepSeq
import System.IO
field o k=parseMaybe(.:k)o
text o k d=maybe d id(field o k)::String
bad o k x=if k `elem` (maybe [] id(field o "bad")::[String])then errorWithoutStackTrace k else x
pub o=bad o "root"$R.PublicKey(bad o "size"1)(bad o "n"2)(bad o "e"3)
priv o=bad o "root"$R.PrivateKey(bad o "pub"(R.PublicKey 1 2 3))(bad o "d"4)(bad o "p"5)(bad o "q"6)(bad o "dP"7)(bad o "dQ"8)(bad o "qinv"9)
shape :: Data a=>a->Value
shape a=case constrRep(toConstr a)of IntConstr n->object["integer".=show n];_->object["constructor".=showConstr(toConstr a),"fields".=gmapQ shape a]
qType :: forall a.Data a=>a->String
qType _=show(typeRep(Proxy::Proxy a))
methods :: forall a.Data a=>Object->a->Value
methods o a=let op=text o "op""shape";con=bad o "constr"(if text o "constructor""bool"=="integer"then toConstr(bad o "constructorValue"(read(text o "integer""23")::Integer))else toConstr True);query x=bad o "query"(qType x);identity x=bad o "transform"x in case op of
 "shape"->shape a
 "gfoldl"->toJSON(getConst(gfoldl(\(Const xs)x->Const(xs++[query x]))(const(Const[]))a))
 "gfoldlIgnore"->toJSON(getConst(gfoldl(\_ _->Const("k"::String))(\_->bad o "z"(Const"z"))a))
 "gunfold"->toJSON(getConst(gunfold(\(Const n)->Const(n+1::Int))(const(Const 0))con::Const Int a))
 "gunfoldIgnore"->toJSON(getConst(gunfold(\_->Const("k"::String))(\_->bad o "z"(Const"z"))con::Const String a))
 "gmapT"->shape(gmapT identity a)
 "gmapTRoot"->gmapT(\_->errorWithoutStackTrace"transform")a `seq`toJSON(42::Int)
 "gmapQl"->toJSON(gmapQl(++)[] (\x->[query x])a)
 "gmapQr"->toJSON(gmapQr(++)[] (\x->[query x])a)
 "gmapQ"->toJSON(gmapQ query a)
 "gmapQi"->toJSON(gmapQi(read(text o "index""0"))query a)
 "gmapM"->shape(runIdentity(gmapM(\x->Identity(identity x))a))
 "gmapMp"->toJSON$case gmapMp(\x->Just(identity x))a of Nothing->Null;Just v->shape v
 "gmapMo"->toJSON$case gmapMo(\x->Just(identity x))a of Nothing->Null;Just v->shape v
 "gmapMpFail"->toJSON(isNothing(gmapMp(\_->Nothing)a::Maybe a))
 "gmapMoFail"->toJSON(isNothing(gmapMo(\_->Nothing)a::Maybe a))
 "dataCast1"->toJSON(isNothing(dataCast1(undefined::forall d.Data d=>Const String [d])::Maybe(Const String a)))
 "dataCast2"->toJSON(isNothing(dataCast2(undefined::forall d e.(Data d,Data e)=>Const String(d,e))::Maybe(Const String a)))
 _->errorWithoutStackTrace"unknown"
run o=do let v=case text o "type""PublicKey"of "PrivateKey"->methods o(priv o);"KeyPair"->methods o(R.KeyPair(priv o));"Integer"->methods o(bad o "root"(read(text o "integer""23")::Integer));_->methods o(pub o)
         r<-try(evaluate(force v))::IO(Either SomeException Value)
         return$either(\e->object["exception".=takeWhile(/='\n')(displayException e)])id r
main=do hSetBuffering stdout LineBuffering;s<-L.getContents;mapM_(\line->case eitherDecode line of Left e->error e;Right o->run o>>=L.putStrLn.encode)(L.lines s)
