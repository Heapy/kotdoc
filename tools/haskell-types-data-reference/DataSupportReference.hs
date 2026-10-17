{-# LANGUAGE ScopedTypeVariables, RankNTypes, OverloadedStrings #-}
-- Original dependency Data metadata and lazy primitive Constr payloads.
import Data.Data
import Data.Proxy
import qualified Data.Text as T
import qualified Data.Map as M
import qualified Data.Sequence as S
import Data.Functor.Const
import Control.Exception
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
support :: forall a. Data a=>Proxy a->IO()
support _=let dt=dataTypeOf(undefined::a) in B.putStrLn(encode(object
 ["name" .=dataTypeName dt,"rep" .=case dataTypeRep dt of AlgRep _->("AlgRep"::String);IntRep->"IntRep";FloatRep->"FloatRep";CharRep->"CharRep";NoRep->"NoRep"
 ,"constructors" .=if isAlgType dt then [(constrIndex c,showConstr c,constrFields c,show(constrFixity c))|c<-dataTypeConstrs dt]else []]))
probe :: String -> String -> IO ()
probe name value=do
 result<-try(evaluate(length value))::IO(Either SomeException Int)
 B.putStrLn(encode(name,either(const "error")(const value)result))
zero :: a->Const String a
zero _=Const "zero"
step :: Data d=>Const String(d->b)->Const String b
step _=Const "field"
main=do
 support(Proxy::Proxy Int)
 support(Proxy::Proxy Double)
 support(Proxy::Proxy Char)
 support(Proxy::Proxy Bool)
 support(Proxy::Proxy T.Text)
 support(Proxy::Proxy[Int])
 support(Proxy::Proxy(Int,Int))
 support(Proxy::Proxy(Int,Int,Int))
 support(Proxy::Proxy(Maybe Int))
 support(Proxy::Proxy(M.Map T.Text Int))
 support(Proxy::Proxy(S.Seq Int))
 probe "Int-toConstr-WHNF-bottom" (toConstr(error "value"::Int)`seq`"constructor")
 probe "Double-toConstr-WHNF-bottom" (toConstr(error "value"::Double)`seq`"constructor")
 probe "Char-toConstr-WHNF-bottom" (toConstr(error "value"::Char)`seq`"constructor")
 probe "Int-ConstrRep-WHNF-bottom" (constrRep(toConstr(error "value"::Int))`seq`"representation")
 probe "Double-ConstrRep-WHNF-bottom" (constrRep(toConstr(error "value"::Double))`seq`"representation")
 probe "Char-ConstrRep-WHNF-bottom" (constrRep(toConstr(error "value"::Char))`seq`"representation")
 probe "Int-gunfold-z-ignores-payload" (getConst(gunfold step zero(toConstr(error "value"::Int))::Const String Int))
 probe "Double-gunfold-z-ignores-payload" (getConst(gunfold step zero(toConstr(error "value"::Double))::Const String Double))
 probe "Char-gunfold-z-ignores-payload" (getConst(gunfold step zero(toConstr(error "value"::Char))::Const String Char))
 probe "Int-gunfold-wrong-tag" (getConst(gunfold step zero(toConstr('a'))::Const String Int))
 probe "Int-gunfold-bottom-constr" (getConst(gunfold step zero(error "constructor")::Const String Int))
 probe "Int-Constr-name-bottom" (showConstr(toConstr(error "value"::Int)))
 probe "Double-Constr-name-bottom" (showConstr(toConstr(error "value"::Double)))
 probe "Char-Constr-name-bottom" (showConstr(toConstr(error "value"::Char)))
