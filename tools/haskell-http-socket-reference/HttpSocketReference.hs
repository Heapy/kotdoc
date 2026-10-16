{-# LANGUAGE CPP, OverloadedStrings, ScopedTypeVariables #-}
#if __GLASGOW_HASKELL__ != 912 || __GLASGOW_HASKELL_PATCHLEVEL1__ != 2 || __GLASGOW_HASKELL_PATCHLEVEL2__ != 0
#error GHC 9.12.2 required
#endif
module Main where
import qualified OriginalSocketConnection as O
import qualified OriginalSocketFactories as F
import qualified SocketEffects as E
import Network.HTTP.Client.Internal (Connection(..),unHttpExceptionContentWrapper)
import qualified Control.Concurrent.Async as A
import Control.Exception
import Control.Monad
import qualified Data.Aeson as J
import Data.Aeson (Value(..),(.=),object,toJSON)
import qualified Data.ByteString.Lazy.Char8 as L
import Data.Maybe
import System.Timeout
import System.IO

failure e
 | Just (_::IOException)<-fromException e=object ["kind" .= ("io"::String),"show" .= show e]
 | Just content<-unHttpExceptionContentWrapper <$> fromException e=object ["kind" .= ("content"::String),"show" .= show content]
 | Just (ErrorCall s)<-fromException e=object ["kind" .= ("fatal"::String),"show" .= s]
 | Just ThreadKilled<-fromException e=object ["kind" .= ("async"::String)]
 | Just A.AsyncCancelled<-fromException e=object ["kind" .= ("async"::String)]
 | otherwise=error $ "unhandled "++show e
observe action=do
 r<-try action
 pure $ either (\e->object ["error" .= failure e]) id r
run v=do
 E.configure v
 let tweak (E.Socket i)=E.perform i "tweak" [] $ pure ()
     final (E.Socket i)=E.perform i "final" [] $ pure $ toJSON i
     host=fromMaybe "localhost" $ E.optional v "host"
     port=fromMaybe 80 $ E.optional v "port"
     address=E.optional v "address"
     action=case E.field v "api" :: String of
      "racer"->toJSON <$> O.firstSuccessful [E.defaultHints {E.addrProtocol=fromIntegral i} | i<-[0..length (E.array $ E.value v "attempts")-1]] (E.attempt . fromIntegral . E.addrProtocol)
      "with"->O.withSocket tweak address host port final
      "open"->do
        conn<-if E.optional v "managerFactory"==Just True
          then if E.optional v "defaultSize"==Just True
            then do {factory<-F.rawConnectionModifySocket tweak;factory address host port}
            else do {factory<-F.rawConnectionModifySocketSize tweak;factory (fromMaybe 8192 $ E.optional v "chunkSize") address host port}
          else if E.optional v "defaultSize"==Just True then O.openSocketConnection tweak address host port
            else O.openSocketConnectionSize tweak (fromMaybe 8192 $ E.optional v "chunkSize") address host port
        results<-forM (E.array $ E.value v "commands") $ \command->observe $ case E.field command "op" :: String of
          "read"->toJSON . E.bytes <$> connectionRead conn
          "write"->connectionWrite conn (E.bs $ E.value command "bytes") >> pure Null
          "unread"->connectionUnread conn (E.bs $ E.value command "bytes") >> pure Null
          "close"->connectionClose conn >> pure Null
          _->error "command"
        pure $ toJSON results
      _->error "api"
 result<-A.withAsync (observe action) $ \worker->do
  mapM_ E.step $ maybe [] E.array (E.optional v "steps")
  if E.optional v "interrupt"==Just True then A.cancelWith worker ThreadKilled >> pure (object ["error" .= object ["kind" .= ("async"::String)]])
   else do
    done<-timeout (if E.optional v "blocked"==Just True then 30000 else 5000000) $ A.wait worker
    case done of
     Just r->pure r
     Nothing | E.optional v "blocked"==Just True->pure $ object ["blocked" .= True]
     Nothing->error "observer timed out"
 es<-E.events
 pure $ object ["result" .= result,"events" .= es]
main=do
 hSetBuffering stdout LineBuffering
 L.getContents >>= mapM_ (\line->case J.eitherDecode line of {Left e->error e;Right v->run v >>= L.putStrLn . J.encode}) . L.lines
