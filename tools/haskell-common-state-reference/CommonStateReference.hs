{-# LANGUAGE GeneralizedNewtypeDeriving, OverloadedStrings, FlexibleInstances, MultiParamTypeClasses, FlexibleContexts #-}
module Main where
import Control.Monad (unless)
import Control.Monad.Except
import Control.Monad.State.Strict
import Control.Monad.Reader
import qualified Control.Exception as E
import Data.Aeson
import Data.Char (ord)
import Data.IORef
import qualified Data.Text as T
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import System.IO
import GHC.IO.Handle (hDuplicate, hDuplicateTo)
import System.Directory (removeFile)
import System.Mem.StableName
import Network.HTTP.Client
import Text.Pandoc.Class.CommonState
import Text.Pandoc.Class.PandocMonad
import Text.Pandoc.Logging
import Text.Pandoc.Error
import Text.Collate.Lang (Lang, parseLang)
import Text.Pandoc.Translations.Types
import MessageTransport

data Env = Env { mirror :: IORef CommonState, events :: IORef [Value], managers :: [Manager], mode :: Int }
newtype M a = M { unM :: ExceptT PandocError (StateT CommonState (ReaderT Env IO)) a }
  deriving (Functor, Applicative, Monad, MonadError PandocError, MonadState CommonState, MonadReader Env, MonadIO)
instance PandocMonad M where
 getCommonState = get
 putCommonState s = do
  ref <- asks mirror
  liftIO (writeIORef ref s)
  put s
 logOutput msg = do
  s <- getCommonState
  emitState "output" [toJSON msg] s
  k <- asks mode
  case k of
   1 -> do
    setUserDataDir (Just "callback")
    modifyCommonState (\st -> st { stLog = NoLangSpecified : stLog st })
    setVerbosity ERROR
   2 -> do
    setVerbosity ERROR
    report (ScriptingInfo "nested" Nothing)
    setUserDataDir (Just "nested-callback")
   3 -> throwError (PandocSomeError "callback-error")
   4 -> liftIO (E.throwIO (E.ErrorCall "host-error"))
   _ -> pure ()
emit v = asks events >>= \ref -> liftIO (modifyIORef' ref (v:))
emitState name prefix s = do
 env <- ask
 v <- liftIO (stateValue env s)
 emit (toJSON (String name : prefix ++ [v]))
optional _ Null = Nothing
optional f v = Just (f v)
stateValue env s = do
 mid <- case stManager s of
  Nothing -> pure Null
  Just m -> do
   forced <- E.evaluate m
   a <- makeStableName forced
   ns <- mapM makeStableName (managers env)
   pure (case [i | (i,n) <- zip [0::Int ..] ns, a==n] of i:_ -> toJSON i; _ -> error "manager identity")
 pure (object ["log" .= stLog s, "userDataDir" .= fmap (map ord) (stUserDataDir s),
  "sourceURL" .= stSourceURL s, "headers" .= stRequestHeaders s,
  "noCheckCertificate" .= stNoCheckCertificate s, "mediaBag" .= show (stMediaBag s),
  "translations" .= fmap (\(l,t) -> (show l, fmap show t)) (stTranslations s),
  "inputFiles" .= map (map ord) (stInputFiles s), "outputFile" .= fmap (map ord) (stOutputFile s),
  "resourcePath" .= map (map ord) (stResourcePath s), "manager" .= mid,
  "verbosity" .= show (stVerbosity s), "trace" .= stTrace s])
operations = mapM_ operation . arr
operation v = case arr v of
 [String "verbosity",n] -> setVerbosity (toEnum (as n))
 [String "traceFlag",b] -> setTrace (as b)
 [String "trace",t] -> Text.Pandoc.Class.PandocMonad.trace (txt t)
 [String "header",n,x] -> setRequestHeader (txt n) (txt x)
 [String "headers",hs] -> setRequestHeaders [(txt n,txt x) | h <- arr hs, let [n,x] = arr h]
 [String "certificate",b] -> setNoCheckCertificate (as b)
 [String "manager",n] -> do
  ms <- asks managers
  modifyCommonState (\s -> s { stManager = optional (\x -> ms !! as x) n })
 [String "media",p,m,b] -> insertMedia (chars p) (optional txt m) (BL.pack (map as (arr b)))
 [String "input",fs] -> setInputFiles (map chars (arr fs))
 [String "output",p] -> setOutputFile (optional chars p)
 [String "resource",ps] -> setResourcePath (map chars (arr ps))
 [String "user",p] -> setUserDataDir (optional chars p)
 [String "source",p] -> modifyCommonState (\s -> s { stSourceURL = optional txt p })
 [String "translations",l,t] -> modifyCommonState (\s -> s { stTranslations = optional (\tag ->
     (either (error . show) id (parseLang (txt tag)), optional as t)) l })
 [String "log",ms] -> modifyCommonState (\s -> s { stLog = map message (arr ms) })
 [String "report",m] -> report (message m)
 [String "snapshot"] -> getCommonState >>= emitState "snapshot" []
 [String "getLog"] -> getLog >>= \ms -> emit (toJSON [String "getLog",toJSON ms])
 [String "silent",ops,result] -> do
  (r,ls) <- runSilently (operations ops >> pure result)
  getCommonState >>= emitState "silent" [r,toJSON ls]
 [String "caught",ops] -> operations ops `catchError` (\e -> emit (toJSON [String "caught",String (T.pack (show e))]))
 [String "failPandoc"] -> throwError (PandocSomeError "action-error")
 [String "failHost"] -> liftIO (E.throwIO (E.ErrorCall "host-error"))
 [String "toLang",t] -> toLang (optional txt t) >>= \l -> emit (toJSON [String "lang",toJSON (fmap show l)])
 _ -> error "operation"
response ms x = case arr x of
 [String "run",ops,k] -> do
  ref <- newIORef defaultCommonState
  ev <- newIORef []
  let env = Env ref ev ms (as k)
  (path,h) <- openBinaryTempFile "/private/tmp" "kotdoc-state-trace"
  saved <- hDuplicate stderr
  hDuplicateTo h stderr
  result <- E.try (runReaderT (runStateT (runExceptT (unM (operations ops))) defaultCommonState) env)
       :: IO (Either E.SomeException (Either PandocError (),CommonState))
  hFlush stderr
  hDuplicateTo saved stderr
  hClose saved
  hSeek h AbsoluteSeek 0
  traceBytes <- BS.hGetContents h
  E.evaluate (BS.length traceBytes)
  hClose h
  removeFile path
  let status = case result of Left _ -> "host"; Right (Left e,_) -> T.pack (show e); Right (Right _,_) -> "success"
  final <- readIORef ref >>= stateValue env
  es <- reverse <$> readIORef ev
  pure (object ["state" .= final,"status" .= status,"events" .= es,"traceBytes" .= BS.unpack traceBytes])
 _ -> error "request"
main = do
 ms <- mapM (const (newManager defaultManagerSettings >>= E.evaluate)) [0::Int,1]
 loop ms
 where
 loop ms = do
  end <- isEOF
  unless end $ do
   line <- BS.getLine
   let request = either error id (eitherDecode (BL.fromStrict line))
   result <- response ms request
   C.putStrLn (encode result)
   hFlush stdout
   loop ms
