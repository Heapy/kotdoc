{-# LANGUAGE GeneralizedNewtypeDeriving, OverloadedStrings, FlexibleInstances, MultiParamTypeClasses, FlexibleContexts #-}
-- JSON transport and observable state/effect boundary only.
module Main where
import Control.Monad (unless)
import Control.Monad.Except
import Control.Monad.State.Strict
import Control.Monad.Reader
import qualified Control.Exception as E
import Data.Aeson
import qualified Data.Vector as V
import Data.IORef
import qualified Data.Text as T
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Char (chr,ord)
import System.IO
import Text.Pandoc.Class.CommonState
import Text.Pandoc.Class.PandocMonad
import Text.Pandoc.Logging
import Text.Pandoc.Error
import Text.Pandoc.Definition
import Text.Collate.Lang (Lang(..),parseLang)
import Text.Pandoc.Shared
import SourceWriterEnvironment
as :: FromJSON a => Value -> a
as v=case fromJSON v of Success x->x;Error e->error e
arr (Array v)=V.toList v
arr _=error "array"
chars=map(chr.as).arr
txt=T.pack.chars
optional _ Null=Nothing
optional f v=Just(f v)
lang (String t)=either(error.show)id(parseLang t)
lang v=case arr v of
 [l,s,r,vs,es,ps]->Lang(txt l)(optional txt s)(optional txt r)(map txt(arr vs))[(txt k,[(txt a,txt b)|pair<-arr vals,let[a,b]=arr pair])|item<-arr es,let[k,vals]=arr item](map txt(arr ps))
 _->error "lang"
stateValue s=object["log" .= stLog s,"userDataDir" .= fmap(map ord)(stUserDataDir s),"sourceURL" .= stSourceURL s,"headers" .= stRequestHeaders s,"noCheckCertificate" .= stNoCheckCertificate s,"mediaBag" .= show(stMediaBag s),"translations" .= fmap(\(l,t)->(show l,fmap show t))(stTranslations s),"inputFiles" .= map(map ord)(stInputFiles s),"outputFile" .= fmap(map ord)(stOutputFile s),"resourcePath" .= map(map ord)(stResourcePath s),"manager" .= (Null::Value),"verbosity" .= show(stVerbosity s),"trace" .= stTrace s]
data Env=Env {mirror::IORef CommonState,events::IORef [Value],hook::Int}
newtype M a=M {unM::ExceptT PandocError(StateT CommonState(ReaderT Env IO)) a}
 deriving(Functor,Applicative,Monad,MonadError PandocError,MonadState CommonState,MonadReader Env,MonadIO)
instance PandocMonad M where
 getCommonState=get
 putCommonState s=do
  emit(toJSON[String "put",stateValue s]);ref<-asks mirror;liftIO(writeIORef ref s);put s
 logOutput msg=do
  s<-getCommonState;emit(toJSON[String "output",toJSON msg,stateValue s]);k<-asks hook
  case k of
   1->modifyCommonState(\st->st{stTranslations=Just(Lang "de" Nothing Nothing [] [] [],Just mempty),stUserDataDir=Just "callback",stVerbosity=ERROR,stLog=NoLangSpecified:stLog st})
   2->do
    setVerbosity ERROR;report(ScriptingInfo "nested" Nothing)
    modifyCommonState(\st->st{stUserDataDir=Just "nested-callback"})
   3->do
    setUserDataDir(Just "typed-callback");throwError(PandocSomeError "callback-error")
   4->do
    setUserDataDir(Just "host-callback");liftIO(E.throwIO(E.ErrorCall "host-error"))
   5->setupTranslations(Meta mempty)
   _->pure()
emit v=asks events >>= \r->liftIO(modifyIORef' r(v:))
response req=case arr req of
 [String "date",s]->pure(toJSON(normalizeDate(txt s)))
 [String "date-string",s]->pure(toJSON(normalizeDate'(chars s)))
 [String "setup",doc,cache,verbosity,k]->do
  let Pandoc meta _=as doc
      initial=defaultCommonState{stLog=[NoLangSpecified],stUserDataDir=Just "seed-dir",stSourceURL=Just "seed-source",stRequestHeaders=[("x-seed","value")],stNoCheckCertificate=True,stInputFiles=["seed-in"],stOutputFile=Just "seed-out",stResourcePath=["seed-resource"],stVerbosity=toEnum(as verbosity),stTrace=True,stTranslations=optional(\v->case arr v of [l,t]->(lang l,optional as t);_->error "cache")cache}
  ref<-newIORef initial;es<-newIORef[];let env=Env ref es(as k)
  result<-E.try(runReaderT(runStateT(runExceptT(unM(setupTranslations meta)))initial)env)::IO(Either E.SomeException(Either PandocError (),CommonState))
  st<-readIORef ref;evs<-reverse <$> readIORef es
  let status=case result of Left _->"host";Right(Left e,_)->T.pack(show e);Right(Right _,_)->"success"
  pure(object["state" .= stateValue st,"events" .= evs,"status" .= status,"lookup" .= lookupMetaString "lang" meta])
 _->error "request"
main=loop where
 loop=do
  end<-isEOF
  unless end $ do
   line<-B.getLine;result<-response(either error id(eitherDecode(BL.fromStrict line)))
   L.putStrLn(encode result);hFlush stdout;loop
