{-# LANGUAGE OverloadedStrings,GeneralizedNewtypeDeriving #-}
-- Dev-only effect observer. Production reader source is copied verbatim except imports.
-- Recording lives in a private PureState environment key so Parsec rollback and
-- catchError do not erase already performed callback observations.
import SourceOrgReader
import Text.Pandoc hiding(readOrg)
import qualified Text.Pandoc.Class as P
import Text.Pandoc.Error(renderError)
import Text.Pandoc.Sources
import Data.Aeson
import Data.Aeson.Types(parseEither)
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
import Control.Monad(forM_)
import Control.Monad.Except
import Data.Time(UTCTime(..),secondsToDiffTime)
import Data.Time.Calendar(Day(..))
newtype Observed a=Observed{unObserved::PandocPure a}deriving(Functor,Applicative,Monad,MonadError PandocError)
key="\NULorg-reader-dev-events"
record event=Observed$modifyPureState$ \s -> s{stEnv=(key,event):stEnv s}
effect event action=record event>>Observed action
instance PandocMonad Observed where
 lookupEnv name=effect("lookupEnv:"<>name)(P.lookupEnv name)
 getCurrentTime=effect"getCurrentTime" P.getCurrentTime
 getCurrentTimeZone=effect"getCurrentTimeZone" P.getCurrentTimeZone
 newStdGen=effect"newStdGen" P.newStdGen
 newUniqueHash=effect"newUniqueHash" P.newUniqueHash
 openURL uri=effect("openURL:"<>uri)(P.openURL uri)
 readFileLazy path=effect("readFileLazy:"<>T.pack path)(P.readFileLazy path)
 readFileStrict path=effect("readFileStrict:"<>T.pack path)(P.readFileStrict path)
 readStdinStrict=effect"readStdinStrict" P.readStdinStrict
 glob path=effect("glob:"<>T.pack path)(P.glob path)
 fileExists path=effect("fileExists:"<>T.pack path)(P.fileExists path)
 getDataFileName path=effect("getDataFileName:"<>T.pack path)(P.getDataFileName path)
 getModificationTime path=effect("getModificationTime:"<>T.pack path)(P.getModificationTime path)
 getCommonState=Observed P.getCommonState
 putCommonState=Observed . P.putCommonState
 logOutput message=effect"logOutput"(P.logOutput message)
observe value=case parseEither(withObject"request"$ \o -> (,,) <$>o.:"input" <*>o.:"extensions" <*>(o.:?"files".!=[]))value of
 Left e -> error e
 Right(input,exts,files) -> B.putStrLn$encode$object["request".=value,"expected".=(case runPure(do
  forM_ files$ \file -> case parseEither(withObject"file"$ \o -> (,) <$>o.:"path" <*>o.:"bytes")file of
   Left e -> error e
   Right(path,bytes) -> modifyPureState$ \s -> s{stFiles=insertInFileTree path(FileInfo(UTCTime(ModifiedJulianDay 125)(secondsToDiffTime 0))(BS.pack bytes))(stFiles s)}
  result<-catchError(Right <$>unObserved(readOrg def{readerExtensions=extensionsFromList exts}(input::T.Text)))(pure.Left)
  logs<-P.getLog
  events<-reverse . map snd . filter((==key).fst) . stEnv <$>getPureState
  pure(result,logs,events))of
   Left e -> object["fatal".=renderError e]
   Right(result,logs,events) -> object["events".=events,"result".=(case result of Left e -> object["error".=renderError e,"showError".=show e];Right doc -> object["document".=doc,"logs".=logs])])]
main=B.getContents>>=mapM_(observe.either error id.eitherDecode).B.lines
