{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module SocketEffects
 (module N,Socket(..),getAddrInfo,socket,setSocketOption,connect,close,recv,sendAll,threadDelay
 ,configure,events,perform,gate,step,field,optional,value,bs,bytes,array,raise,phaseFailure,attemptConfig,attempt) where
import Network.Socket as N hiding (Socket,getAddrInfo,socket,setSocketOption,connect,close)
import qualified Control.Concurrent as C
import Control.Exception
import Control.Monad
import qualified Data.Aeson as J
import Data.Aeson (Value(..),(.=),object,toJSON)
import qualified Data.Aeson.KeyMap as K
import Data.Aeson.Types (parseMaybe)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as L
import qualified Data.Map as M
import Data.IORef
import Data.Maybe
import Data.List (sortOn)
import System.IO.Unsafe

field :: J.FromJSON a => Value -> J.Key -> a
field (Object o) k=fromJust $ K.lookup k o >>= parseMaybe J.parseJSON
field _ _=error "object"
optional :: J.FromJSON a => Value -> J.Key -> Maybe a
optional (Object o) k=case K.lookup k o of {Just Null->Nothing;Just x->parseMaybe J.parseJSON x;Nothing->Nothing}
optional _ _=Nothing
value :: Value -> J.Key -> Value
value (Object o) k=fromJust $ K.lookup k o
value _ _=error "object"
bs :: Value -> B.ByteString
bs=B.pack . fromJust . parseMaybe J.parseJSON
bytes=B.unpack
array :: Value -> [Value]
array=fromJust . parseMaybe J.parseJSON
raise :: String -> IO a
raise=raiseAt (-1)
raiseAt i tag=case tag of
 "io"->ioError $ userError $ "socket-io-"++show i
 "async"->throwIO ThreadKilled
 _->throwIO $ ErrorCall tag

data Socket=Socket Int deriving (Eq,Show)
data Gate=Gate (C.MVar ()) (C.MVar ())
data Context=Context Value (IORef [Value]) (C.MVar (M.Map (String,Int) Gate))
{-# NOINLINE context #-}
context :: IORef Context
context=unsafePerformIO $ newIORef $ error "unconfigured effects"
configure v=do {e<-newIORef [];g<-C.newMVar M.empty;writeIORef context (Context v e g)}
config=do {Context v _ _<-readIORef context;pure v}
event x=do
 _<-evaluate $ L.length $ J.encode x
 Context _ e _<-readIORef context
 atomicModifyIORef' e (\xs->(x:xs,()))
-- Cross-address scheduling is not imposed. Preserve complete per-address order.
events=do
 Context _ e _<-readIORef context
 xs<-reverse <$> readIORef e
 let ids=sortOn id $ M.keys $ M.fromList [(field x "id" :: Int,()) | x<-xs]
 pure $ toJSON [object ["id" .= i,"events" .= [x | x<-xs,field x "id"==i]] | i<-ids]
getGate phase i=do
 Context _ _ gates<-readIORef context
 C.modifyMVar gates $ \m -> case M.lookup (phase,i) m of
  Just g->pure (m,g)
  Nothing->do
   a<-C.newEmptyMVar;b<-C.newEmptyMVar
   let g=Gate a b
   pure (M.insert (phase,i) g m,g)
gate phase i=do
 Gate entered permit<-getGate phase i
 _<-C.tryPutMVar entered ();C.takeMVar permit
mark phase i=do
 Gate entered _<-getGate phase i
 void $ C.tryPutMVar entered ()
step v=do
 let phase=field v "phase";i=field v "id"
 Gate entered permit<-getGate phase i
 C.readMVar entered
 unless (optional v "awaitOnly"==Just True) $ C.putMVar permit ()
phaseFailure i phase=do
 cfg<-attemptConfig i
 case optional cfg "failPhase" of
  Just p | p==phase->raiseAt i $ fromMaybe "io" $ optional cfg "failure"
  _->pure ()
attemptConfig i=do
 v<-config
 pure $ if i<0 then object [] else maybe (object []) (\xs->array xs !! i) (optional v "attempts")
perform i phase fields action=do
 event $ object $ ["id" .= i,"phase" .= phase]++fields
 mark phase i
 phaseFailure i phase
 action
attempt i=do
 event $ object ["id" .= i,"phase" .= ("attempt"::String)]
 mark "attempt" i
 v<-config
 when (optional v "controlled"==Just True) $ gate "attempt" i
 cfg<-attemptConfig i
 let result=fromMaybe "success" $ optional cfg "result"
 (if result=="success" then pure i else raiseAt i result)
   `finally` (event (object ["id" .= i,"phase" .= ("finished"::String)]) >> mark "finished" i)
threadDelay :: Int -> IO ()
threadDelay n=do
 let i=n `div` 250000
 event $ object ["id" .= i,"phase" .= ("delay"::String),"microseconds" .= n]
 mark "delay" i
 v<-config
 if optional v "controlled"==Just True then gate "delay" i else C.threadDelay n
getAddrInfo :: Maybe AddrInfo -> Maybe HostName -> Maybe ServiceName -> IO [AddrInfo]
getAddrInfo hints host service=do
 unless (hints==Just (defaultHints {addrSocketType=Stream})) $ error "unexpected hints"
 perform (-1) "resolve" ["host" .= host,"service" .= service] $ do
  v<-config
  case optional v "resolveFailure" of
   Just tag->raise tag
   Nothing->pure $ [AddrInfo [] (if optional cfg "ipv6"==Just True then AF_INET6 else AF_INET) Stream (6+i) (if optional cfg "ipv6"==Just True then SockAddrInet6 (fromIntegral i) 0 (0,0,0,fromIntegral i) 0 else SockAddrInet (fromIntegral i) (fromIntegral i)) Nothing | (i,cfg)<-zip [0..] $ maybe [object []] array (optional v "attempts")]
socket :: Family -> SocketType -> ProtocolNumber -> IO Socket
socket family typ protocol=do
 let i=fromIntegral protocol-6
 perform i "create" ["family" .= show family,"type" .= show typ,"protocol" .= (fromIntegral protocol :: Int)] $ pure (Socket i)
setSocketOption :: Socket -> SocketOption -> Int -> IO ()
setSocketOption (Socket i) option n=perform i "no-delay" ["option" .= show option,"value" .= n] $ pure ()
connect (Socket i) address=do
 let shown=show address
 _<-evaluate $ length shown
 perform i "connect" ["address" .= shown] $ do
  v<-config
  when (optional v "controlled"==Just True) $ gate "attempt" i
  cfg<-attemptConfig i
  case optional cfg "result" of {Nothing->pure ();Just "success"->pure ();Just tag->raiseAt i tag}
close (Socket i)=perform i "close" [] $ pure ()
recv :: Socket -> Int -> IO B.ByteString
recv (Socket i) n=perform i "receive" ["size" .= n] $ pure $ B.pack [0..255]
sendAll (Socket i) b=perform i "send" ["bytes" .= bytes b] $ pure ()
