{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified LiveKeyedPool as A
import qualified OriginalKeyedPool as S
import qualified PoolClock as Clock
import qualified Control.Concurrent as C
import Control.Exception
import Control.Monad (forM,when)
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Data.Aeson.Types (parseMaybe)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Maybe (fromJust)
import Data.IORef
import qualified Data.Map.Strict as M
import Data.Time (UTCTime(..),Day(..),picosecondsToDiffTime)
import System.IO hiding (Handle)
import System.Mem (performGC)
import Foreign.StablePtr (newStablePtr,freeStablePtr)

field :: FromJSON a => Value -> Key -> a
field (Object o) k = fromJust (K.lookup k o >>= parseMaybe parseJSON)
field _ _ = error "object"
optional :: FromJSON a => Value -> Key -> Maybe a
optional (Object o) k = K.lookup k o >>= parseMaybe parseJSON
optional _ _ = Nothing
array :: Value -> [Value]
array = fromJust . parseMaybe parseJSON
utc v = let [a,b]=field v "time" in UTCTime (ModifiedJulianDay $ read a) (picosecondsToDiffTime $ read b)
data Handle = Handle {resource :: Maybe Int,reused :: Bool,release :: Bool -> IO (),keep :: IO ()}
view h = object ["resource" .= resource h,"reused" .= reused h]
data Worker = Worker C.ThreadId (C.MVar Value)
tryValue :: IO Value -> IO Value
tryValue action = do
 result<-try action :: IO (Either SomeException Value)
 let projected=either Clock.failure id result
 evaluate $ fromJust $ decodeStrict $ L.toStrict $ encode projected
run :: Value -> IO Value
run v = do
 logRef<-newIORef [];counter<-newIORef (0::Int);handles<-newIORef (M.empty :: M.Map String Handle);workers<-newIORef (M.empty :: M.Map String Worker)
 blocked<-C.newChan;gate<-C.newChan
 let controlled=field v "engine" == ("controlled"::String)
     log event=atomicModifyIORef' logRef (\xs -> (event:xs,()))
     numbers k=maybe [] id (optional v k :: Maybe [Int])
     block phase n=when (n `elem` numbers (if phase==("create"::String) then "blockCreate" else "blockDestroy")) $ do
       C.writeChan blocked (phase,n);C.readChan gate
     create key=do
       n<-atomicModifyIORef' counter (\n -> (n+1,n+1))
       log $ object ["event" .= ("create"::String),"key" .= (key::Int),"resource" .= n]
       block "create" n
       when (n `elem` numbers "createFailures") $ Clock.raise $ maybe "create-failure" id (optional v "createFailureKind")
       pure $ if optional v "nullValues" == Just True then Nothing else Just n
     destroy r=do
       log $ object ["event" .= ("destroy"::String),"resource" .= r]
       maybe (pure ()) (block "destroy") r
       when (optional v "destroyAllFailure" == Just True || maybe False (`elem` numbers "destroyFailures") r) $
         Clock.raise $ maybe "destroy-failure" id (optional v "destroyFailureKind")
     onError se=do
       log $ object ["event" .= ("reaper-error"::String),"exception" .= Clock.failure se]
       maybe (pure ()) Clock.raise (optional v "reaperHandlerFailure")
 Clock.reset log
 (takePool,dummy)<-if controlled then do
   p<-S.createKeyedPool create destroy (field v "maxPerKey") (field v "maxTotal") onError
   ready<-Clock.await;when (ready/="delay") $ error "missing initial delay"
   pure ((\k -> do h<-S.takeKeyedPool p k;pure $ Handle (S.managedResource h) (S.managedReused h) (\reuse -> S.managedRelease h $ if reuse then S.Reuse else S.DontReuse) (S.keepAlive h)),
         (\r -> let h=S.dummyManaged r in Handle (S.managedResource h) (S.managedReused h) (\reuse -> S.managedRelease h $ if reuse then S.Reuse else S.DontReuse) (S.keepAlive h)))
  else do
   p<-A.createKeyedPool create destroy (field v "maxPerKey") (field v "maxTotal") onError
   pure ((\k -> do h<-A.takeKeyedPool p k;pure $ Handle (A.managedResource h) (A.managedReused h) (\reuse -> A.managedRelease h $ if reuse then A.Reuse else A.DontReuse) (A.keepAlive h)),
         (\r -> let h=A.dummyManaged r in Handle (A.managedResource h) (A.managedReused h) (\reuse -> A.managedRelease h $ if reuse then A.Reuse else A.DontReuse) (A.keepAlive h)))
 poolRef<-newIORef (Just takePool)
 poolRoot<-newStablePtr poolRef;handlesRoot<-newStablePtr handles
 let get name=fromJust . M.lookup name <$> readIORef handles
     takeAction c=do
       action<-fromJust <$> readIORef poolRef
       h<-action $ field c "key"
       atomicModifyIORef' handles (\hs -> (M.insert (field c "as") h hs,()))
       pure $ view h
     releaseAction c=do h<-get $ field c "handle";release h (field c "reuse");pure Null
     start c action=do
       done<-C.newEmptyMVar
       tid<-C.forkIO $ tryValue action >>= C.putMVar done
       atomicModifyIORef' workers (\ws -> (M.insert (field c "worker") (Worker tid done) ws,()))
       pure Null
     command c=case field c "op" :: String of
       "take" -> takeAction c
       "release" -> releaseAction c
       "keep" -> get (field c "handle") >>= keep >> pure Null
       "peek" -> view <$> get (field c "handle")
       "dummy" -> do
         let h=dummy (field c "resource")
         atomicModifyIORef' handles (\hs -> (M.insert (field c "as") h hs,()))
         pure $ view h
       "forget" -> atomicModifyIORef' handles (\hs -> (M.delete (field c "handle") hs,Null))
       "forget-pool" -> writeIORef poolRef Nothing >> pure Null
       "collect" -> let loop n=do
                         performGC;C.threadDelay 1000
                         events<-readIORef logRef
                         let count=length [()|event<-events,optional event "event"==Just ("destroy"::String)]
                         if count>=field c "destroyCount" then pure $ Bool True else
                           if n<1000 then loop (n+1) else pure $ Bool False
                    in loop (0::Int)
       "time" -> Clock.setTime (utc c) >> pure Null
       "fail-time" -> Clock.failTime (field c "error") >> pure Null
       "finalize" -> Clock.fire (field c "index") >> pure Null
       "tick" -> toJSON <$> Clock.tick
       "start-tick" -> Clock.signal >> pure Null
       "await-tick" -> toJSON <$> Clock.await
       "fork-take" -> start c (takeAction c)
       "fork-release" -> start c (releaseAction c)
       "await-blocked" -> toJSON <$> C.readChan blocked
       "unblock" -> C.writeChan gate () >> pure Null
       "join" -> do
         Worker _ done<-fromJust . M.lookup (field c "worker") <$> readIORef workers
         C.readMVar done
       "interrupt" -> do
         Worker tid _<-fromJust . M.lookup (field c "worker") <$> readIORef workers
         C.throwTo tid ThreadKilled;pure Null
       _ -> error "command"
 results<-forM (array $ field v "commands") $ \c -> tryValue (command c)
 hs<-readIORef handles;mapM_ keep hs
 events<-reverse <$> readIORef logRef
 when controlled Clock.stop
 freeStablePtr poolRoot;freeStablePtr handlesRoot
 pure $ object ["results" .= results,"events" .= events]
main = do
 hSetBuffering stdout LineBuffering
 let loop=do
      eof<-hIsEOF stdin
      if eof then pure () else do
       line<-B.getLine
       result<-run (fromJust $ decodeStrict line)
       L.putStrLn $ encode result
       loop
 loop
