{-# LANGUAGE ScopedTypeVariables, RankNTypes, DeriveDataTypeable, TypeApplications #-}
-- Explicit sharing and result-carrier observations of original Data operations.
import Data.Data
import Data.IORef
import System.IO.Unsafe
import Control.Exception
import Data.Functor.Const
import Data.Functor.Identity
import qualified Data.Map as M
import qualified Data.Sequence as S
import qualified Data.Text as T
import Text.Pandoc.Definition(Format(..))
import Data.Aeson(encode)
import qualified Data.ByteString.Lazy.Char8 as B
counted :: IORef Int->a->a
{-# NOINLINE counted #-}
counted ref value=unsafePerformIO(modifyIORef' ref(+1)>>pure value)
tryUnit :: a->IO()
tryUnit value=do _<-try(evaluate(value `seq` ()))::IO(Either SomeException ());pure()
record name action=action >>= \value->B.putStrLn(encode(name,show(value::Int)))
probe :: String->String->IO()
probe name value=do
 result<-try(evaluate(length value))::IO(Either SomeException Int)
 B.putStrLn(encode(name,either(const "error")(const value)result))
newtype OutList=OutList[Int] deriving Typeable
instance Data OutList where
 gfoldl _ z x=z x
 gunfold _ z _=z(OutList[])
 toConstr _=error "unused"
 dataTypeOf _=error "unused"
 gmapT _ _=OutList(error "result root")
 gmapM _ _=return(OutList(error "result root"))
 gmapMp _ _=return(OutList(error "result root"))
 gmapMo _ _=return(OutList(error "result root"))
newtype OutSeq=OutSeq(S.Seq Int) deriving Typeable
instance Data OutSeq where
 gfoldl _ z x=z x
 gunfold _ z _=z(OutSeq S.empty)
 toConstr _=error "unused"
 dataTypeOf _=error "unused"
 gmapT _ _=OutSeq(error "result root")
newtype OutMap=OutMap(M.Map Int Int) deriving Typeable
instance Data OutMap where
 gfoldl _ z x=z x
 gunfold _ z _=z(OutMap M.empty)
 toConstr _=error "unused"
 dataTypeOf _=error "unused"
 gmapT _ _=OutMap(error "result root")
data OutStrict=OutStrict ![Int] deriving Typeable
instance Data OutStrict where
 gfoldl _ z x=z x
 gunfold _ z _=z(OutStrict[])
 toConstr _=error "unused"
 dataTypeOf _=error "unused"
 gmapT _ _=OutStrict(error "result field")
main=do
 record "shared-input-success" $do
  ref<-newIORef 0
  let input=counted ref[1::Int,2];out=head(gmapQ(\_->"child")input)
  tryUnit out;tryUnit out;readIORef ref
 record "shared-input-failure" $do
  ref<-newIORef 0
  let input=counted ref(error "input"::[Int]);out=head(gmapQ(\_->"child")input)
  tryUnit out;tryUnit out;readIORef ref
 record "shared-query-head-twice" $do
  ref<-newIORef 0
  let query :: forall a. Data a=>a->String
      query value=counted ref(show(typeOf value))
      out=gmapQ query[1::Int,2]
  tryUnit(head out);tryUnit(head out);readIORef ref
 record "shared-query-head-and-tail" $do
  ref<-newIORef 0
  let query :: forall a. Data a=>a->String
      query value=counted ref(show(typeOf value))
      out=gmapQ query[1::Int,2]
  tryUnit(head out);tryUnit(head(tail out));readIORef ref
 record "shared-query-failure" $do
  ref<-newIORef 0
  let query :: forall a. Data a=>a->String
      query value=counted ref(error("query "++show(typeOf value)))
      out=head(gmapQ query[1::Int])
  tryUnit out;tryUnit out;readIORef ref
 record "unused-query-function-factory" $do
  ref<-newIORef 0
  let query :: forall a. Data a=>a->String
      query=counted ref(error "query function")
  tryUnit(gmapQ query True);readIORef ref
 record "unused-k-function-factory" $do
  ref<-newIORef 0
  let k :: forall d b. Data d=>Const String(d->b)->d->Const String b
      k=counted ref(error "k function")
  tryUnit(getConst(gfoldl k(const(Const "zero"))([]::[Int])));readIORef ref
 record "unused-z-function-factory" $do
  ref<-newIORef 0
  let z :: forall a. a->Const String a
      z=counted ref(error "z function")
  tryUnit(getConst(gfoldl(\_ _->Const "field")z(error "value"::Format)));readIORef ref
 record "wrong-tag-ignores-Constr-name" $do
  ref<-newIORef 0
  let c=mkConstrTag(mkDataType "Foreign" [])(counted ref(error "name"))1[]Prefix
  tryUnit(runIdentity(gunfold(\_->error "field")Identity c)::Int);readIORef ref
 record "constrIndex-ignores-datatype-name" $do
  ref<-newIORef 0
  let dt=mkIntType(counted ref(error "name"));c=mkIntegralConstr dt(1::Int)
  tryUnit(constrIndex c);readIORef ref
 record "dataTypeConstrs-ignores-name" $do
  ref<-newIORef 0
  let dt=mkIntType(counted ref(error "name"))
  tryUnit(dataTypeConstrs dt);readIORef ref
 probe "returned-list-gmapT-root" (gmapT id(OutList[])`seq`"returned")
 probe "returned-list-gmapM-Identity-root" ((gmapM(Identity . id)(OutList[])::Identity OutList)`seq`"returned")
 probe "returned-list-gmapM-Maybe-outer" ((gmapM Just(OutList[])::Maybe OutList)`seq`"outer")
 probe "returned-list-gmapM-Maybe-inner" (case(gmapM Just(OutList[])::Maybe OutList)of Just value->value `seq` "returned";_->"none")
 probe "returned-list-gmapMp-Maybe-outer" ((gmapMp Just(OutList[])::Maybe OutList)`seq`"outer")
 probe "returned-list-gmapMp-Maybe-inner" (case(gmapMp Just(OutList[])::Maybe OutList)of Just value->value `seq` "returned";_->"none")
 probe "returned-list-gmapMo-Maybe-outer" ((gmapMo Just(OutList[])::Maybe OutList)`seq`"outer")
 probe "returned-list-gmapMo-Maybe-inner" (case(gmapMo Just(OutList[])::Maybe OutList)of Just value->value `seq` "returned";_->"none")
 probe "returned-Seq-newtype-root" (gmapT id(OutSeq S.empty)`seq`"returned")
 probe "returned-Map-newtype-root" (gmapT id(OutMap M.empty)`seq`"returned")
 probe "returned-StrictData-field" (gmapT id(OutStrict[])`seq`"returned")
 probe "returned-tuple-root-ignores-fields" (gmapQi 0(\_->(error "first"::Int,error "second"::Int))(Just(1::Int))`seq`"tuple")
 probe "returned-tuple-first-forces-field" (fst(gmapQi 0(\_->(error "first"::Int,error "second"::Int))(Just(1::Int)))`seq`"returned")
 probe "returned-function-root-ignores-body" (gmapQi 0(\_->(\(_::Int)->error "body"::Int))(Just(1::Int))`seq`"function")
 probe "returned-function-application-forces-body" (gmapQi 0(\_->(\(_::Int)->error "body"::Int))(Just(1::Int))1`seq`"returned")
