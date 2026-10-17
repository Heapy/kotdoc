{-# LANGUAGE RankNTypes, ScopedTypeVariables #-}
-- Independent original Data methods, including foreign constructor representations.
import Data.Functor.Identity
import Data.Proxy
import Data.Maybe(isNothing)
import Data.Data
import Text.Pandoc.Definition
import Text.Pandoc.Builder (Many)
import Control.Exception
import Data.Aeson(encode)
import qualified Data.ByteString.Lazy.Char8 as B
newtype K a=K String
probe :: String -> String -> IO ()
probe name value=do
 r<-try(evaluate(length value))::IO(Either SomeException Int)
 B.putStrLn(encode(name,either (const "error") (const value) r))
run :: K a -> String
run(K s)=s
step :: Data b => K(b->r)->K r
step _=K "field"
zero :: a->K a
zero _=K "zero"
foreignConstructor i=mkConstrTag (mkDataType "Foreign" []) "Foreign" i [] Prefix
instanceDemand :: forall a. Data a => Proxy a -> IO ()
instanceDemand _=do
 let name=dataTypeName(dataTypeOf(undefined::a));bad=error("root "++name)::a
 probe (name++"/gfoldl-root") (run(gfoldl (\f _->step f) zero bad))
 probe (name++"/toConstr-root") (showConstr(toConstr bad))
 probe (name++"/gmapQ-root") (show(gmapQ (\_->"child")bad))
 probe (name++"/gmapQl-root") (gmapQl (\left _->left) "initial" (\_->error "query")bad)
 probe (name++"/gmapQr-root") (gmapQr (\_ right->right) "initial" (\_->error "query")bad)
 probe (name++"/gmapQi-root") (gmapQi 0 (\_->"child")bad)
 probe (name++"/gmapM-failure") (show(isNothing(gmapM (\_->Nothing)bad)))
 probe (name++"/gmapMp-failure") (show(isNothing(gmapMp (\_->Nothing)bad)))
 probe (name++"/gmapMo-failure") (show(isNothing(gmapMo (\_->Nothing)bad)))
main=do
 probe "toConstr-Pandoc-bottom" (showConstr(toConstr(error "root"::Pandoc)))
 probe "toConstr-Meta-bottom" (showConstr(toConstr(error "root"::Meta)))
 probe "toConstr-Row-bottom" (showConstr(toConstr(error "root"::Row)))
 probe "toConstr-Format-bottom" (showConstr(toConstr(error "root"::Format)))
 probe "gunfold-Pandoc-bottomConstr" (run(gunfold step zero(error "constructor")::K Pandoc))
 probe "gunfold-Many-bottomConstr" (run(gunfold step zero(error "constructor")::K(Many Inline)))
 mapM_ (\i->probe("gunfold-Alignment-foreign-"++show i)(show(runIdentity(gunfold (\_->error "unused field") Identity(foreignConstructor i)::Identity Alignment)))) [-1,0,1,2,3,4,5,100]
 probe "gfoldl-Meta-bottom" (run(gfoldl (\f _->step f) zero(error "root"::Meta)))
 probe "gfoldl-Pandoc-bottom" (run(gfoldl (\f _->step f) zero(error "root"::Pandoc)))
 probe "gfoldl-Format-bottom" (run(gfoldl (\f _->step f) zero(error "root"::Format)))

 mapM_ id [instanceDemand(Proxy::Proxy Pandoc),instanceDemand(Proxy::Proxy Meta),instanceDemand(Proxy::Proxy MetaValue)
  ,instanceDemand(Proxy::Proxy ListNumberStyle),instanceDemand(Proxy::Proxy ListNumberDelim),instanceDemand(Proxy::Proxy Format)
  ,instanceDemand(Proxy::Proxy RowHeadColumns),instanceDemand(Proxy::Proxy Alignment),instanceDemand(Proxy::Proxy ColWidth)
  ,instanceDemand(Proxy::Proxy Row),instanceDemand(Proxy::Proxy TableHead),instanceDemand(Proxy::Proxy TableBody)
  ,instanceDemand(Proxy::Proxy TableFoot),instanceDemand(Proxy::Proxy Caption),instanceDemand(Proxy::Proxy Cell)
  ,instanceDemand(Proxy::Proxy RowSpan),instanceDemand(Proxy::Proxy ColSpan),instanceDemand(Proxy::Proxy Block)
  ,instanceDemand(Proxy::Proxy QuoteType),instanceDemand(Proxy::Proxy MathType),instanceDemand(Proxy::Proxy Inline)
  ,instanceDemand(Proxy::Proxy Citation),instanceDemand(Proxy::Proxy CitationMode),instanceDemand(Proxy::Proxy(Many Inline))]

