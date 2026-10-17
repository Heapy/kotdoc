{-# LANGUAGE ScopedTypeVariables, RankNTypes, DeriveDataTypeable, TypeApplications #-}
-- Caller-defined, deliberately non-lawful Data dictionaries. No laws are assumed.
import Data.Data
import Data.Functor.Const
import Data.Functor.Identity
import Control.Monad
import Control.Exception
import Data.Aeson(encode)
import qualified Data.ByteString.Lazy.Char8 as B

data Custom=Custom Int Int deriving(Show,Typeable)
customType=mkDataType "Test.Custom" [customConstr]
customConstr=mkConstrTag customType "Custom" 1 [] Prefix
instance Data Custom where
 gfoldl _ z _=z(Custom 7 9)
 gunfold _ z _=z(Custom 11 13)
 toConstr _=customConstr
 dataTypeOf _=customType
 gmapT _ _=Custom 101 102
 gmapQl _ initial _ _=initial
 gmapQr _ initial _ _=initial
 gmapQi _ _ _=error "custom index"
 gmapM _ _=return(Custom 103 104)
 gmapMp _ _=mzero
 gmapMo _ _=mzero

data LazyPair=LazyPair Int Int deriving(Show,Typeable)
pairType=mkDataType "Test.LazyPair" [pairConstr]
pairConstr=mkConstrTag pairType "LazyPair" 1 [] Prefix
instance Data LazyPair where
 gfoldl k z ~(LazyPair a b)=z LazyPair `k` a `k` b
 gunfold k z _=k(k(z LazyPair))
 toConstr _=pairConstr
 dataTypeOf _=pairType
data RootProbe=RootProbe deriving(Show,Typeable)
rootType=mkDataType "Test.RootProbe" [rootConstr]
rootConstr=mkConstrTag rootType "RootProbe" 1 [] Prefix
instance Data RootProbe where
 gfoldl k z _=k @Int @RootProbe `seq` z @RootProbe `seq` z RootProbe
 gunfold k z _=k @Int @RootProbe `seq` z @RootProbe `seq` z RootProbe
 toConstr _=rootConstr
 dataTypeOf _=rootType
 gmapT f _=f @Int `seq` RootProbe
 gmapQ f _=f @Int `seq` []
 gmapM f _=f @Int `seq` return RootProbe
 gmapMp f _=f @Int `seq` mzero
 gmapMo f _=f @Int `seq` mzero
probe :: String -> String -> IO ()
probe name value=do
 result<-try(evaluate(length value))::IO(Either SomeException Int)
 B.putStrLn(encode(name,either(const "error")(const value)result))
main=do
 let bad=error "root"::Custom
 probe "custom-gfoldl-ignored-root" (getConst(gfoldl (\_ _->error "unused step") (const(Const "zero")) bad))
 probe "custom-gunfold-ignored-constr" (getConst(gunfold (\_->error "unused step") (const(Const "zero")) (error "constr")::Const String Custom))
 probe "custom-toConstr" (showConstr(toConstr bad))
 probe "custom-dataTypeOf" (dataTypeName(dataTypeOf bad))
 probe "custom-gmapT" (show(gmapT (\_->error "unused transform")bad))
 probe "custom-gmapQl" (gmapQl (\_ _->error "unused combine") "initial" (\_->error "unused query") bad)
 probe "custom-gmapQr" (gmapQr (\_ _->error "unused combine") "initial" (\_->error "unused query") bad)
 probe "custom-default-gmapQ-dispatch" (show(gmapQ (\_->error "unused query"::String) bad))
 probe "custom-gmapQi" (gmapQi (error "index") (\_->error "query") bad::String)
 probe "custom-gmapM" (show(runIdentity(gmapM (\_->error "unused transform")bad)))
 probe "custom-gmapMp" (show(gmapMp (\_->error "unused transform")bad::Maybe Custom))
 probe "custom-gmapMo" (show(gmapMo (\_->error "unused transform")bad::Maybe Custom))
 let bottomPair=error "pair root"::LazyPair
 probe "default-gfoldl-irrefutable-root" (getConst(gfoldl (\_ _->Const "field") (const(Const "zero")) bottomPair))
 probe "default-gmapQ-irrefutable-root" (show(gmapQ (\_->"child")bottomPair))
 probe "default-gmapQl-ignore-fields" (gmapQl (\r _->r) "initial" (\_->error "query")bottomPair)
 probe "default-gmapQr-ignore-fields" (gmapQr (\_ r->r) "initial" (\_->error "query")bottomPair)
 probe "default-gmapQi-unused-index-nullary" (gmapQi (error "index") (\_->"child") True)
 probe "default-gmapT-unused-fields" (case gmapT (\_->error "field")bottomPair of LazyPair _ _->"constructor")
 probe "default-gmapM-Identity-unused-fields" (case runIdentity(gmapM (\_->Identity(error "field"))bottomPair) of LazyPair _ _->"constructor")
 probe "default-gmapMp-Maybe-failure" (show(isNothing(gmapMp (\_->Nothing)bottomPair)))
 probe "default-gmapMo-Maybe-failure" (show(isNothing(gmapMo (\_->Nothing)bottomPair)))
 probe "function-root-gfoldl-k" (getConst(gfoldl(error "k") (const(Const "zero"))RootProbe))
 probe "function-root-gfoldl-z" (getConst(gfoldl(\_ _->Const "field")(error "z")RootProbe))
 probe "function-root-gunfold-k" (getConst(gunfold(error "k")(const(Const "zero"))(error "constr")::Const String RootProbe))
 probe "function-root-gunfold-z" (getConst(gunfold(\_->Const "field")(error "z")(error "constr")::Const String RootProbe))
 probe "function-root-gunfold-ignored-constr" (getConst(gunfold(\_->Const "field")(const(Const "zero"))(error "constr")::Const String RootProbe))
 probe "function-root-gmapT" (show(gmapT(error "transform")RootProbe))
 probe "function-root-gmapQ" (show(gmapQ(error "query")RootProbe::[String]))
 probe "function-root-gmapQr-ignored-query" (gmapQr (\_ right->right) "initial" (error "query") RootProbe)
 probe "function-root-gmapM" (show(runIdentity(gmapM(error "transform")RootProbe)))
 probe "function-root-gmapMp" (show(gmapMp(error "transform")RootProbe::Maybe RootProbe))
 probe "function-root-gmapMo" (show(gmapMo(error "transform")RootProbe::Maybe RootProbe))
 let choices :: forall a. Data a=>a->[a]
     choices value=case cast value::Maybe Int of
      Nothing->[]
      Just n->[case cast(n+10)of Just out->out;Nothing->error "cast",case cast(n+20)of Just out->out;Nothing->error "cast"]
 probe "list-Mp-choice-order" (show(gmapMp choices(LazyPair 1 2)::[LazyPair]))
 probe "list-Mo-choice-order" (show(gmapMo choices(LazyPair 1 2)::[LazyPair]))
 probe "list-Mo-first-result-unused-second" (case head(gmapMo choices(LazyPair 1(error "second"))::[LazyPair])of LazyPair a _->show a)
 probe "list-Mp-first-result-demands-second" (case head(gmapMp choices(LazyPair 1(error "second"))::[LazyPair])of LazyPair a _->show a)
 where isNothing Nothing=True;isNothing(Just _)=False
