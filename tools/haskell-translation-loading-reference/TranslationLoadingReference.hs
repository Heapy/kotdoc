{-# LANGUAGE GeneralizedNewtypeDeriving, OverloadedStrings, FlexibleInstances, MultiParamTypeClasses, FlexibleContexts #-}
module Main where
import Control.Monad (unless)
import Control.Monad.Except
import Control.Monad.State.Strict
import Control.Monad.Reader
import qualified Control.Exception as E
import Data.Aeson
import Data.Char (chr,ord)
import Data.IORef
import qualified Data.Text as T
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import qualified Data.Vector as V
import qualified Data.Map.Strict as Map
import Data.String (fromString)
import System.IO
import Text.Pandoc.Class.CommonState
import Text.Pandoc.Class
import Text.Pandoc.Translations
import Text.Pandoc.Logging
import Text.Pandoc.Error
import Text.Collate.Lang (Lang(..))
as :: FromJSON a => Value -> a
as v=case fromJSON v of Success x->x;Error e->error e
arr (Array v)=V.toList v
arr _=error "array"
chars=map(chr.as).arr
txt=T.pack.chars
optional _ Null=Nothing
optional f v=Just(f v)
lang v=case v of
 String t->fromString(T.unpack t)
 _->case arr v of
  [l,s,r,vs,es,ps]->Lang(txt l)(optional txt s)(optional txt r)(map txt(arr vs))[(txt k,[(txt a,txt b)|pair<-arr vals,let[a,b]=arr pair])|item<-arr es,let[k,vals]=arr item](map txt(arr ps))
  _->error "lang"
shape t=object["show" .= show t,"terms" .= [lookupTerm k t|k<-[minBound..maxBound]]]
cache s=fmap(\(l,t)->toJSON[String(T.pack(show l)),maybe Null shape t])(stTranslations s)
stateValue s=object["cache" .= cache s,"user" .= fmap(map ord)(stUserDataDir s),"headers" .= stRequestHeaders s,"resources" .= map(map ord)(stResourcePath s),"verbosity" .= show(stVerbosity s),"log" .= stLog s]
data Env=Env {mirror::IORef CommonState,events::IORef [Value],files::IORef(Map.Map String [Value]),mode::Int,outputs::IORef Int}
newtype M a=M {unM::ExceptT PandocError(StateT CommonState(ReaderT Env IO)) a}
 deriving(Functor,Applicative,Monad,MonadError PandocError,MonadState CommonState,MonadReader Env,MonadIO)
instance PandocMonad M where
 getCommonState=get
 putCommonState s=do
  emit(toJSON[String "put",stateValue s]);asks mirror >>= \r->liftIO(writeIORef r s);put s
 logOutput msg=do
  s<-getCommonState;emit(toJSON[String "output",toJSON msg,stateValue s])
  ref<-asks outputs;n<-liftIO(readIORef ref);liftIO(writeIORef ref(n+1));k<-asks mode
  case k of
   1->modifyCommonState(\s->s{stUserDataDir=Just "callback",stVerbosity=ERROR,stLog=NoLangSpecified:stLog s,stTranslations=Just(fromString "de",Just mempty)})
   2->do
    modifyCommonState(\s->s{stVerbosity=ERROR,stUserDataDir=Just "nested-callback"});report(ScriptingInfo "nested" Nothing)
   3->do
    modifyCommonState(\s->s{stUserDataDir=Just "typed-callback"});throwError(PandocSomeError "callback-error")
   4->do
    modifyCommonState(\s->s{stUserDataDir=Just "host-callback"});liftIO(E.throwIO(E.ErrorCall "host-error"))
   5 | n==0 ->do
    setTranslations(fromString "de");_<-getTranslations;pure()
   _->pure()
 readDataFileEffect fp=do
  s<-getCommonState;emit(toJSON[String "read",toJSON(map ord fp),stateValue s])
  ref<-asks files;fs<-liftIO(readIORef ref)
  let choices=Map.findWithDefault [] fp fs
      response=case choices of x:_->x;[]->toJSON[String "missing"]
  liftIO(writeIORef ref(Map.insert fp (case choices of _:x:xs->x:xs;_->choices) fs))
  case arr response of
   [String "bytes",bytes,ops]->operations ops>>pure(BS.pack(map as(arr bytes)))
   [String "typed",detail,ops]->operations ops>>throwError(PandocSomeError(txt detail))
   [String "host",ops]->operations ops>>liftIO(E.throwIO(E.ErrorCall "host-error"))
   [String "missing"]->throwError(PandocCouldNotFindDataFileError(T.pack fp))
   _->error "file response"
emit v=asks events >>= \r->liftIO(modifyIORef' r(v:))
operations=mapM_ operation.arr
operation v=case arr v of
 [String "set",l]->setTranslations(lang l)
 [String "cache",l,t]->modifyCommonState(\s->s{stTranslations=optional(\l->(lang l,optional as t)) l})
 [String "get"]->getTranslations >>= \t->emit(toJSON[String "get",shape t])
 [String "term",n]->translateTerm(toEnum(as n)) >>= \t->emit(toJSON[String "term",String t])
 [String "snapshot"]->getCommonState >>= \s->emit(toJSON[String "snapshot",stateValue s])
 [String "verbosity",n]->modifyCommonState(\s->s{stVerbosity=toEnum(as n)})
 [String "user",s]->modifyCommonState(\st->st{stUserDataDir=optional chars s})
 [String "headers",hs]->modifyCommonState(\s->s{stRequestHeaders=[(txt n,txt x)|pair<-arr hs,let[n,x]=arr pair]})
 [String "resources",ps]->modifyCommonState(\s->s{stResourcePath=map chars(arr ps)})
 [String "report",s]->report(ScriptingWarning(txt s)Nothing)
 [String "caught",ops]->operations ops `catchError` (\e->emit(toJSON[String "caught",String(T.pack(show e))]))
 [String "failTyped",s]->throwError(PandocSomeError(txt s))
 [String "failHost"]->liftIO(E.throwIO(E.ErrorCall "host-error"))
 _->error "operation"
response req=case arr req of
 [String "read",s]->pure(either (\e->object["error" .= e]) shape(readTranslations(txt s)))
 [String "text",p,b]->do
  env<-environment [] 0
  result<-runReaderT(runStateT(runExceptT(unM(toTextM(chars p)(BS.pack(map as(arr b))))))defaultCommonState)env
  pure(case result of (Right t,_)->object["text" .= t];(Left e,_)->object["error" .= show e])
 [String "run",ops,fs,k]->do
  env<-environment(arr fs)(as k)
  result<-E.try(runReaderT(runStateT(runExceptT(unM(operations ops)))defaultCommonState)env)::IO(Either E.SomeException(Either PandocError (),CommonState))
  let status=case result of Left _->"host";Right(Left e,_)->T.pack(show e);Right(Right _,_)->"success"
  s<-readIORef(mirror env);es<-reverse <$> readIORef(events env)
  pure(object["state" .= stateValue s,"status" .= status,"events" .= es])
 _->error "request"
environment fs k=do
 s<-newIORef defaultCommonState;e<-newIORef[];o<-newIORef 0
 f<-newIORef(Map.fromList[(chars p,arr choices)|item<-fs,let[p,choices]=arr item])
 pure(Env s e f k o)
main=loop where
 loop=do
  end<-isEOF
  unless end $ do
   line<-BS.getLine;result<-response(either error id(eitherDecode(BL.fromStrict line)))
   C.putStrLn(encode result);hFlush stdout;loop
