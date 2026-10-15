{-# LANGUAGE RankNTypes #-}
{-# LANGUAGE OverloadedStrings #-}
module PoolClock where
import qualified Control.Concurrent as C
import Control.Exception
import Data.IORef
import Data.Aeson
import Data.Time (UTCTime(..),Day(..))
import System.IO.Error
import System.IO.Unsafe (unsafePerformIO)

data Env = Env { timeRef :: IORef UTCTime, failureRef :: IORef (Maybe String)
 , finals :: IORef [(Bool,IO ())], permit :: C.Chan (), feedback :: C.Chan String
 , finished :: IORef Bool, logger :: Value -> IO () }
{-# NOINLINE environment #-}
environment :: IORef Env
environment = unsafePerformIO $ newEnv (const $ pure ()) >>= newIORef
newEnv log = Env <$> newIORef (UTCTime (ModifiedJulianDay 60000) 0) <*> newIORef Nothing <*> newIORef [] <*> C.newChan <*> C.newChan <*> newIORef False <*> pure log
reset log = newEnv log >>= writeIORef environment
setTime t = readIORef environment >>= \e -> writeIORef (timeRef e) t
failTime tag = readIORef environment >>= \e -> writeIORef (failureRef e) (Just tag)
getCurrentTime = do
 e<-readIORef environment
 t@(UTCTime (ModifiedJulianDay d) p)<-readIORef (timeRef e)
 logger e $ object ["event" .= ("time"::String),"value" .= [show d,show (floor (p*1000000000000)::Integer)]]
 failure<-atomicModifyIORef' (failureRef e) (\x -> (Nothing,x))
 maybe (pure t) raise failure
mkWeakIORef _ action = do
 e<-readIORef environment
 n<-atomicModifyIORef' (finals e) (\xs -> (xs++[(False,action)],length xs))
 logger e $ object ["event" .= ("register"::String),"index" .= n]
fire n = do
 e<-readIORef environment
 action<-atomicModifyIORef' (finals e) $ \xs ->
  let (before,(done,f):after)=splitAt n xs in (before++(True,f):after,if done then pure () else f)
 action
forkIOWithUnmask :: ((forall a. IO a -> IO a) -> IO ()) -> IO C.ThreadId
forkIOWithUnmask f = do
 e<-readIORef environment
 logger e $ object ["event" .= ("fork"::String)]
 C.forkIOWithUnmask $ \restore -> f restore `finally` (writeIORef (finished e) True >> C.writeChan (feedback e) "finished")
threadDelay _ = do
 e<-readIORef environment
 C.writeChan (feedback e) "delay"
 C.readChan (permit e)
await = readIORef environment >>= C.readChan . feedback
signal = readIORef environment >>= \e -> C.writeChan (permit e) ()
tick = signal >> await
stop = do
 e<-readIORef environment
 fire 0
 done<-readIORef (finished e)
 if done then pure () else do
  signal
  message<-await
  if message=="finished" then pure () else error "reaper failed to stop"
raise :: String -> IO a
raise "async" = throwIO ThreadKilled
raise "io" = ioError $ userError "pool-io"
raise tag = throwIO $ ErrorCall tag
failure :: SomeException -> Value
failure se
 | Just ThreadKilled <- fromException se = object ["error" .= ("async"::String)]
 | Just e <- fromException se :: Maybe IOError = object ["error" .= ("io"::String),"message" .= ioeGetErrorString e]
 | Just (ErrorCall msg) <- fromException se = object ["error" .= ("script"::String),"message" .= takeWhile (/='\n') msg]
 | otherwise = error $ "unclassified exception " ++ show se
