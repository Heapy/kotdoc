{-# LANGUAGE ScopedTypeVariables, RankNTypes #-}
-- Partial container fields, source result WHNF and finite deep/Long bounds.
import Data.Data
import Data.Maybe
import Data.Functor.Identity
import qualified Data.Text as T
import qualified Data.Map as M
import qualified Data.Sequence as S
import Text.Pandoc.Definition
import Text.Pandoc.Builder(Many(..))
import Control.Exception
import Data.Aeson(encode)
import qualified Data.ByteString.Lazy.Char8 as B
probe :: String->String->IO()
probe name value=do
 result<-try(evaluate(length value))::IO(Either SomeException Int)
 B.putStrLn(encode(name,either(const "error")(const value)result))
replaceInt :: Data a=>a->a
replaceInt x=fromMaybe x(cast(17::Int))
replaceString :: Data a=>a->a
replaceString x=fromMaybe x(cast("new"::String))
replaceEntries :: Data a=>a->a
replaceEntries x=fromMaybe x(cast([]::[(T.Text,Int)]))
replaceSeq :: Data a=>a->a
replaceSeq x=fromMaybe x(cast(S.empty::S.Seq Int))
firstQuery :: forall a. Data a=>a->String
firstQuery x=case cast x::Maybe Int of Just _->"head";Nothing->error "tail query"
lastQuery :: forall a. Data a=>a->String
lastQuery x=case cast x::Maybe Int of Just _->error "head query";Nothing->"tail"
main=do
 let xs=error "head":error "tail"::[Int]
 probe "list-map-outer-with-bottom-transform" (case gmapT(\_->error "transform")xs of _:_->"cons";_->"nil")
 probe "list-map-head-replacement" (show(head(gmapT replaceInt xs)))
 probe "list-map-head-forced-transform" (head(gmapT(\_->error "transform")xs)`seq`"head")
 probe "list-map-tail-forced-transform" (tail(gmapT(\_->error "transform")xs)`seq`"tail")
 probe "list-query-head-ignores-fields" (gmapQi 0(\_->"child")xs)
 probe "list-query-tail-ignores-fields" (gmapQi 1(\_->"child")xs)
 probe "list-gmapQ-first-only" (head(gmapQ firstQuery xs))
 probe "list-left-ignores-previous-query" (gmapQl(\_ right->right)(error "initial")lastQuery xs)
 probe "list-right-ignores-following-query" (gmapQr const(error "initial")firstQuery xs)
 probe "list-query-negative-index" (gmapQi(-1)(\_->"child")xs)
 probe "tuple-map-first-replacement" (show(fst(gmapT replaceInt((error "first",error "second")::(Int,Int)))))
 probe "tuple-query-first-ignores-fields" (gmapQi 0(\_->"child")((error "first",error "second")::(Int,Int)))
 probe "tuple-query-second-ignores-fields" (gmapQi 1(\_->"child")((error "first",error "second")::(Int,Int)))
 probe "maybe-map-payload-replacement" (show(fromJust(gmapT replaceInt(Just(error "payload"::Int)))))
 probe "maybe-query-ignores-payload" (gmapQi 0(\_->"child")(Just(error "payload"::Int)))
 probe "text-query-ignores-root" (gmapQi 0(\_->"child")(error "text"::T.Text))
 probe "text-map-replacement-ignores-root" (T.unpack(gmapT replaceString(error "text"::T.Text)))
 probe "map-query-ignores-root" (gmapQi 0(\_->"child")(error "map"::M.Map T.Text Int))
 probe "map-map-replacement-ignores-root" (show(M.size(gmapT replaceEntries(error "map"::M.Map T.Text Int))))
 probe "seq-query-head-ignores-payload" (gmapQi 0(\_->"child")(S.singleton(error "head"::Int)))
 probe "seq-query-tail-ignores-payload" (gmapQi 1(\_->"child")(S.singleton(error "head"::Int)))
 probe "seq-query-forces-root" (gmapQi 0(\_->"child")(error "seq"::S.Seq Int))
 probe "many-query-ignores-root" (gmapQi 0(\_->"child")(error "many"::Many Int))
 probe "many-map-replacement-ignores-root" (show(S.length(unMany(gmapT replaceSeq(error "many"::Many Int)))))
 probe "StrictData-toConstr-forces-list-root" (showConstr(toConstr(Emph(error "list"))))
 probe "StrictData-toConstr-ignores-list-head" (showConstr(toConstr(Emph[error "head"])))
 probe "result-gmapQi-list-root" (gmapQi 0(\_->error "result"::[Int])(Just(1::Int))`seq`"returned")
 probe "result-gmapQl-list-root" (gmapQl(\_ _->error "result"::[Int])[](\_->())(Just(1::Int))`seq`"returned")
 probe "result-gmapQr-list-root" (gmapQr(\_ _->error "result"::[Int])[](\_->())(Just(1::Int))`seq`"returned")
 probe "result-gmapQ-outer-ignores-head" (case gmapQ(\_->error "result"::[Int])(Just(1::Int))of _:_->"outer";_->"empty")
 probe "result-gmapQ-head-forces-result" (head(gmapQ(\_->error "result"::[Int])(Just(1::Int)))`seq`"returned")
 mapM_(\n->probe("deep-list-tail-length-"++show n)(show(gmapQi 1(\v->maybe(-1)length(cast v::Maybe[Int]))(replicate n(1::Int)))))[1,17,10000,100000]
 mapM_(\n->probe("Long-seq-tail-length-"++show n)(show(gmapQi 1(\v->maybe(-1)S.length(cast v::Maybe(S.Seq Int)))(S.replicate n(error "head"::Int))))) [1,17,4096,34359738368]
