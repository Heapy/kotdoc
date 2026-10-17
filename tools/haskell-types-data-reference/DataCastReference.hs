{-# LANGUAGE RankNTypes, ScopedTypeVariables #-}
-- Original dataCast1/2 shape and argument demand for supporting dictionaries.
import Data.Data
import Data.Functor.Const
import Data.Maybe
import Control.Exception
import qualified Data.Sequence as S
import qualified Data.Map as M
import qualified Data.Text as T
import Data.Aeson(encode)
import qualified Data.ByteString.Lazy.Char8 as B
probe name value=do
 result<-try(evaluate(length value))::IO(Either SomeException Int)
 B.putStrLn(encode(name,either(const "error")(const value)result))
main=do
 probe "list-positive-ignores-payload" (show(isJust(dataCast1(error "payload"::forall d.Data d=>Const String[d])::Maybe(Const String[Int]))))
 probe "list-negative-ignores-payload" (show(isJust(dataCast1(error "payload"::forall d.Data d=>Const String(Maybe d))::Maybe(Const String[Int]))))
 probe "maybe-positive-ignores-payload" (show(isJust(dataCast1(error "payload"::forall d.Data d=>Const String(Maybe d))::Maybe(Const String(Maybe Int)))))
 probe "seq-positive-ignores-payload" (show(isJust(dataCast1(error "payload"::forall d.Data d=>Const String(S.Seq d))::Maybe(Const String(S.Seq Int)))))
 probe "tuple-positive-ignores-payload" (show(isJust(dataCast2(error "payload"::forall d e.(Data d,Data e)=>Const String(d,e))::Maybe(Const String(Int,Int)))))
 probe "map-positive-ignores-payload" (show(isJust(dataCast2(error "payload"::forall d e.(Data d,Data e)=>Const String(M.Map d e))::Maybe(Const String(M.Map T.Text Int)))))
 probe "tuple-negative-ignores-payload" (show(isJust(dataCast2(error "payload"::forall d e.(Data d,Data e)=>Const String(M.Map d e))::Maybe(Const String(Int,Int)))))
 probe "int-default-ignores-payload" (show(isJust(dataCast1(error "payload"::forall d.Data d=>Const String[d])::Maybe(Const String Int))))
 probe "text-default-ignores-payload" (show(isJust(dataCast1(error "payload"::forall d.Data d=>Const String[d])::Maybe(Const String T.Text))))
 probe "tuple3-default-ignores-payload" (show(isJust(dataCast2(error "payload"::forall d e.(Data d,Data e)=>Const String(d,e))::Maybe(Const String(Int,Int,Int)))))
 probe "list-positive-demands-payload" (case dataCast1(error "payload"::forall d.Data d=>Const String[d])::Maybe(Const String[Int])of Just x->getConst x;Nothing->"Nothing")
 probe "tuple-positive-demands-payload" (case dataCast2(error "payload"::forall d e.(Data d,Data e)=>Const String(d,e))::Maybe(Const String(Int,Int))of Just x->getConst x;Nothing->"Nothing")
