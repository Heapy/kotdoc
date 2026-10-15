{-# LANGUAGE GeneralizedNewtypeDeriving, OverloadedStrings, FlexibleInstances, MultiParamTypeClasses, FlexibleContexts, ScopedTypeVariables #-}
module Main where
import Control.Monad(unless)
import Control.Monad.Except
import Control.Monad.State.Strict
import Control.Monad.Reader
import qualified Control.Exception as E
import Data.Aeson
import qualified Data.Vector as V
import Data.IORef
import Data.Int(Int64)
import qualified Data.Text as T
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Char(chr,ord)
import System.IO
import System.IO.Unsafe(unsafePerformIO)
import Text.Pandoc.Class.CommonState
import Text.Pandoc.Class.PandocMonad
import Text.Pandoc.Logging
import Text.Pandoc.Error
import Text.Pandoc.Definition
import qualified Text.Pandoc.Writers.Math as P
import Text.TeXMath(DisplayType(..),Exp,writePandoc)
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success x->x;Error e->error e
arr (Array v)=V.toList v
arr _=error "array"
txt v=T.pack(map(chr.as)(arr v))
stateValue s=object["log" .= stLog s,"userDataDir" .= fmap(map ord)(stUserDataDir s),"sourceURL" .= stSourceURL s,"headers" .= stRequestHeaders s,"noCheckCertificate" .= stNoCheckCertificate s,"mediaBag" .= show(stMediaBag s),"translations" .= fmap(\(l,t)->(show l,fmap show t))(stTranslations s),"inputFiles" .= map(map ord)(stInputFiles s),"outputFile" .= fmap(map ord)(stOutputFile s),"resourcePath" .= map(map ord)(stResourcePath s),"manager" .= (Null::Value),"verbosity" .= show(stVerbosity s),"trace" .= stTrace s]
data Env=Env {mirror::IORef CommonState,events::IORef [Value],hook::Int}
newtype M a=M {unM::ExceptT PandocError(StateT CommonState(ReaderT Env IO)) a}
 deriving(Functor,Applicative,Monad,MonadError PandocError,MonadState CommonState,MonadReader Env,MonadIO)
instance PandocMonad M where
 getCommonState=get
 putCommonState s=do ref<-asks mirror;liftIO(writeIORef ref s);put s
 logOutput msg=do
  s<-getCommonState
  emit(toJSON[String "output",toJSON msg,stateValue s])
  k<-asks hook
  case k of
   1->do
    modifyCommonState(\st->st{stUserDataDir=Just "callback",stLog=NoLangSpecified:stLog st})
    setVerbosity ERROR
   2->do
    setVerbosity ERROR
    report(ScriptingInfo "nested" Nothing)
    modifyCommonState(\st->st{stUserDataDir=Just "nested-callback"})
   3->throwError(PandocSomeError "callback-error")
   4->liftIO(E.throwIO(E.ErrorCall "host-error"))
   _->pure()
emit v=asks events >>= \ref->liftIO(modifyIORef' ref(v:))
{-# NOINLINE observed #-}
observed :: Env -> DisplayType -> [Exp] -> a -> a
observed env dt es result=unsafePerformIO $ do
 st<-readIORef(mirror env)
 modifyIORef'(events env)(toJSON[String "writer",toJSON(show dt),toJSON(show es),stateValue st]:)
 pure result
writer :: Env -> String -> DisplayType -> [Exp] -> Value
writer env kind dt es=observed env dt es $ case kind of
 "pandoc"->toJSON(writePandoc dt es)
 "probe"->object["display" .= show dt,"expressions" .= show es]
 "nothing"->Null
 "constant"->String "constant"
 "throw"->error "writer-error"
 _->error "writer kind"
data Payload=Inlines [Inline] | Converted(Either Inline Value)
encodePayload demand p=case p of
 Inlines ils->toJSON[String "Inlines",toJSON ils,toJSON(show ils)]
 Converted(Left il)->toJSON[String "Left",toJSON il,toJSON(show il)]
 Converted(Right v)->if demand then toJSON[String "Right",v] else toJSON[String "Right-undemanded"]
response request=case arr request of
 [String "urls"]->pure(toJSON[P.defaultMathJaxURL,P.defaultKaTeXURL])
 [String "run",which,mt,input,verbosity,callback,seed,demand,kind]->do
  let initial=defaultCommonState{stLog=if as seed then [NoLangSpecified] else [],stUserDataDir=Just "seed-dir",stSourceURL=Just "seed-source",stRequestHeaders=[("x-seed","value")],stNoCheckCertificate=True,stInputFiles=["seed-in"],stOutputFile=Just "seed-out",stResourcePath=["seed-resource"],stVerbosity=toEnum(as verbosity),stTrace=True}
  ref<-newIORef initial;ev<-newIORef[]
  let env=Env ref ev(as callback)
      mathType=read(as mt)::MathType
      action=if (as which::String)=="inline"
                then Inlines <$> P.texMathToInlines mathType(txt input)
                else Converted <$> P.convertMath(writer env(as kind)) mathType(txt input)
  run<-E.try(runReaderT(runStateT(runExceptT(unM action))initial)env)::IO(Either E.SomeException(Either PandocError Payload,CommonState))
  (status,value)<-case run of
   Left _->pure("host"::String,Null)
   Right(Left e,_)->pure(show e,Null)
   Right(Right payload,_)->do
    let value=encodePayload(as demand)payload
    forced<-E.try(E.evaluate(BL.length(encode value)))::IO(Either E.SomeException Int64)
    _<-E.try(E.evaluate(BL.length(encode(encodePayload(as demand)payload))))::IO(Either E.SomeException Int64)
    pure(case forced of Left _->("host",Null);Right _->("success",value))
  final<-readIORef ref;es<-reverse <$> readIORef ev
  pure(object["status" .= status,"value" .= value,"state" .= stateValue final,"events" .= es,"chronology" .= reverse(stLog final)])
 _->error "request"
main=loop where
 loop=do
  end<-isEOF
  unless end $ do
   line<-B.getLine
   r<-response(either error id(eitherDecode(BL.fromStrict line)))
   L.putStrLn(encode r);hFlush stdout;loop
