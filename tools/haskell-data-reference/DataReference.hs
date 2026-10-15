{-# LANGUAGE FlexibleContexts, GeneralizedNewtypeDeriving, OverloadedStrings, FlexibleInstances, MultiParamTypeClasses, ScopedTypeVariables #-}
module Main where
import Control.Monad (unless)
import Control.Monad.Except
import Control.Monad.Reader
import qualified Control.Exception as E
import Data.Aeson
import qualified Data.Vector as V
import qualified Data.Text as T
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import Data.Char(ord)
import Data.String(fromString)
import Data.IORef
import qualified Data.Map as Map
import Data.Time.Clock
import Data.Time.Calendar
import Data.Time.Clock.POSIX hiding(getPOSIXTime)
import Data.Fixed (Pico)
import Data.Ratio ((%))
import Crypto.Hash(hashlazy,Digest,SHA256)
import System.IO
import qualified System.FilePath.Posix as P
import qualified System.FilePath.Windows as W
import Text.Pandoc.Class
import qualified Text.Pandoc.ClassWindows as CW
import Text.Pandoc.Class.CommonState
import qualified Text.Pandoc.DataEmbedded as DE
import qualified Text.Pandoc.DataInstalled as DI
import qualified Text.Pandoc.DataEmbeddedWindows as DW
import qualified Text.Pandoc.DataInstalledWindows as IW
import qualified Text.Pandoc.Data.BakedIn as Baked
import Text.Pandoc.Shared(safeRead)
import Text.Pandoc.Logging
import Text.Pandoc.Error
import Text.Pandoc.Translations
import qualified Hooks as H
as :: FromJSON a=>Value->a
as=H.as
chars=H.chars
arr (Array v)=V.toList v
arr _=error "array"
cp=toJSON.map ord
text=T.pack.chars
optional _ Null=Nothing
optional f v=Just(f v)
sha b=show(hashlazy b::Digest SHA256)
stateValue s=object["user" .= fmap(map ord)(stUserDataDir s),"headers" .= stRequestHeaders s,"verbosity" .= show(stVerbosity s),"log" .= stLog s,
 "translations" .= fmap(\(l,t)->(show l,fmap show t))(stTranslations s)]
data Env=Env {common::IORef CommonState,events::IORef [Value],responses::IORef(Map.Map (String,Maybe [String]) [Value]),buildMode::Int}
newtype M a=M {unM::ExceptT PandocError(ReaderT Env IO) a}
 deriving(Functor,Applicative,Monad,MonadError PandocError,MonadReader Env,MonadIO)
instance PandocMonad M where
 getCommonState=asks common >>= liftIO.readIORef
 putCommonState s=do
  emit(toJSON[String "put",stateValue s]);asks common >>= \r->liftIO(writeIORef r s)
 logOutput m=emit(toJSON[String "output",toJSON m])
 readDataFileEffect p=readData p
 readFileStrict p=BS.pack.map as.arr <$> effect "strict" [p] (toJSON([]::[Int]))
 readFileLazy p=BL.pack.map as.arr <$> effect "lazy" [p] (toJSON([]::[Int]))
 fileExists p=as <$> effect "exists" [p] (Bool False)
 getDataFileName p=chars <$> effect "fileName" [p] (cp("installed/"++p))
 lookupEnv n=optional text <$> effect "env" [T.unpack n] Null
 getCurrentTime=do
  v<-effect "time" [] (toJSON[String "40587",String "0"])
  case arr v of [d,p]->pure(UTCTime(ModifiedJulianDay(read(as d)))(picosecondsToDiffTime(read(as p))));_->error "time"
emit v=do
 liftIO(E.evaluate(BL.length(encode v)))
 asks events >>= \r->liftIO(modifyIORef' r(v:))
effect name args fallback=do
 s<-getCommonState;emit(toJSON[String(T.pack name),toJSON(map cp args),stateValue s])
 ref<-asks responses;rs<-liftIO(readIORef ref)
 let exact=(name,Just args)
     choices=Map.findWithDefault (Map.findWithDefault [] (name,Nothing) rs) exact rs
     key=if Map.member exact rs then exact else (name,Nothing)
     response=case choices of x:_->x;[]->toJSON[String "ok",fallback,toJSON([]::[Value])]
 liftIO(writeIORef ref(Map.insert key (case choices of _:y:ys->y:ys;_->choices) rs))
 case arr response of
  [String "ok",value,ops]->operations ops>>pure value
  [String "typed",msg,ops]->operations ops>>throwError(PandocSomeError(text msg))
  [String "host",ops]->operations ops>>liftIO(E.throwIO(E.ErrorCall "host-error"))
  _->error "effect response"
readData p=do k<-asks buildMode;case k of 0->DE.readDataFile p;1->DI.readDataFile p;2->DW.readDataFile p;_->IW.readDataFile p
readDefault p=do k<-asks buildMode;case k of 0->DE.readDefaultDataFile p;1->DI.readDefaultDataFile p;2->DW.readDefaultDataFile p;_->IW.readDefaultDataFile p
check p=do k<-asks buildMode;if k<2 then checkUserDataDir p else CW.checkUserDataDir p
ioAction action=do env<-ask;liftIO action
operations=mapM_ operation.arr
operation v=case arr v of
 [String "user",p]->modifyCommonState(\s->s{stUserDataDir=optional chars p})
 [String "header",a,b]->modifyCommonState(\s->s{stRequestHeaders=(text a,text b):stRequestHeaders s})
 [String "check",p]->check(chars p) >>= \r->emit(toJSON[String "check",maybe Null cp r])
 [String "read",p]->readData(chars p) >>= result "read"
 [String "default",p]->readDefault(chars p) >>= result "default"
 [String "timestamp"]->getTimestamp >>= \t->emit(toJSON[String "timestamp",String(T.pack(show(toRational(utcTimeToPOSIXSeconds t))))])
 [String "posix"]->getPOSIXTime >>= \t->emit(toJSON[String "posix",String(T.pack(show(toRational t)))])
 [String "names"]->do
  k<-asks buildMode
  ns<-liftIO(case k of 0->DE.getDataFileNames;1->DI.getDataFileNames;2->DW.getDataFileNames;_->IW.getDataFileNames)
  emit(toJSON[String "names",toJSON(map cp ns)])
 [String "userDefault"]->do
  d<-liftIO DE.defaultUserDataDir;emit(toJSON[String "userDefault",cp d])
 [String "translations",l]->setTranslations(fromString(as l)) >> getTranslations >>= \t->emit(toJSON[String "translations",toJSON(show t)])
 [String "caught",ops]->operations ops `catchError` (\e->emit(toJSON[String "caught",toJSON(show e)]))
 [String "snapshot"]->getCommonState >>= \s->emit(toJSON[String "snapshot",stateValue s])
 [String "throwTyped",s]->throwError(PandocSomeError(text s))
 [String "throwHost"]->liftIO(E.throwIO(E.ErrorCall "host-error"))
 _->error "operation"
 where result name b=emit(toJSON[String name,object["length" .= BS.length b,"sha" .= sha(BL.fromStrict b)]])
response req=case arr req of
 [String "catalog"]->pure(toJSON[toJSON[cp p,toJSON(BS.unpack b)]|(p,b)<-Baked.dataFiles])
 [String "epoch",s]->pure(maybe Null (toJSON.show)(safeRead(text s)::Maybe Integer))
 [String "run",mode,ops,specs]->do
  s<-newIORef defaultCommonState;ev<-newIORef[]
  rs<-newIORef(Map.fromList[((as key,optional (map chars.arr) args),arr values)|spec<-arr specs,let[key,args,values]=arr spec])
  let env=Env s ev rs(as mode)
      invoke m=runReaderT(runExceptT(unM m))env
      ioHook name args=do
       let fallback=case name of
            "directory"->Bool False
            "list"->toJSON([]::[Value])
            "dataDir"->cp "installed"
            "xdg"->cp "xdg/pandoc"
            "legacy"->cp "legacy/pandoc"
            _->Null
       r<-invoke(effect name args fallback)
       either E.throwIO pure r
  writeIORef H.callback ioHook
  result<-E.try(invoke(operations ops))::IO(Either E.SomeException(Either PandocError ()))
  st<-readIORef s;es<-reverse <$> readIORef ev
  pure(object["status" .= (case result of Left _->"host";Right(Left e)->show e;Right(Right _)->"success"),"state" .= stateValue st,"events" .= es])
 _->error "request"
main=loop where
 loop=do
  end<-isEOF
  unless end $ do
   line<-BS.getLine;r<-response(either error id(eitherDecode(BL.fromStrict line)))
   C.putStrLn(encode r);hFlush stdout;loop
