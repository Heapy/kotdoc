{-# LANGUAGE OverloadedStrings, GeneralizedNewtypeDeriving, MultiParamTypeClasses #-}
-- Development-only effect observation around the unchanged original RST module.
import Data.Aeson
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
import Text.Pandoc hiding(readRST)
import FullSourceRstReader
import Text.Pandoc.Class
import Text.Pandoc.Error(renderError,PandocError)
import Text.Pandoc.Sources
import Data.Default
import Control.Monad.Except(MonadError,catchError)
import Control.Monad(when)
import Data.Time.Clock(UTCTime)
import Data.Aeson.Types(parseEither)
import Data.Word(Word8)
newtype Observed a=Observed{unObserved::PandocPure a}deriving(Functor,Applicative,Monad,MonadError PandocError)
record value=modifyPureState $ \s->s{stEnv=("__RST_CALLBACK__",value):stEnv s}
instance PandocMonad Observed where
 lookupEnv n=Observed$when(n=="SOURCE_DATE_EPOCH")(record "timestamp")>>lookupEnv n
 getCurrentTime=Observed getCurrentTime
 getCurrentTimeZone=Observed getCurrentTimeZone
 newStdGen=Observed newStdGen
 newUniqueHash=Observed newUniqueHash
 openURL u=Observed$record("openURL:"<>u)>>openURL u
 readFileLazy p=Observed$record("readFileLazy:"<>T.pack p)>>readFileLazy p
 readFileStrict p=Observed$record("readFile:"<>T.pack p)>>readFileStrict p
 readStdinStrict=Observed readStdinStrict
 glob p=Observed(glob p)
 fileExists p=Observed(fileExists p)
 getDataFileName p=Observed(getDataFileName p)
 getModificationTime p=Observed(getModificationTime p)
 getCommonState=Observed getCommonState
 putCommonState s=Observed(putCommonState s)
 logOutput msg=Observed(logOutput msg)
request=withObject "request"$ \o->do
 name<-o.:"name";input<-o.:"input";opts<-o.:?"options".!=def;exts<-o.:?"extensions".!=[];files<-o.:?"files".!=[];env<-o.:?"environment".!=[]
 pure(name::T.Text,input::T.Text,opts{readerExtensions=extensionsFromList exts},files::[(FilePath,[Word8])],env::[(T.Text,T.Text)])
main=B.getContents>>=mapM_ observe.B.lines
observe line=case eitherDecode line>>=parseEither request of
 Left e->error e
 Right(name,input,opts,files,env)->B.putStrLn$encode$case runPure(do
   let stamp=read "1970-01-01 00:00:00 UTC"::UTCTime
   modifyPureState$ \s->s{stTime=stamp,stEnv=env++stEnv s,stFiles=foldr(\(p,bs)->insertInFileTree p(FileInfo stamp(BS.pack bs)))mempty files}
   result<-(Right <$>unObserved(readRST opts(toSources input)))`catchError`(pure.Left)
   ps<-getPureState;logs<-getLog
   pure(result,logs,reverse[v|(k,v)<-stEnv ps,k=="__RST_CALLBACK__"]))of
  Left e->error(show e)
  Right(result,logs,events)->object$["name".=name,"events".=events,"logs".=logs]++case result of
    Left e->["error".=renderError e,"showError".=show e]
    Right d->["document".=d]
