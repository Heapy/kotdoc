{-# LANGUAGE CPP, OverloadedStrings, ScopedTypeVariables, PatternSynonyms, ForeignFunctionInterface #-}
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <fcntl.h>
#include <poll.h>
#include <errno.h>
module Main where
import qualified Network.Socket as N
import qualified Network.Socket.Options as O
import qualified Network.Socket.ByteString as B
import qualified OriginalSend as S
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Aeson as J
import Data.Aeson (Value(..),(.=),object,toJSON)
import qualified Data.Aeson.KeyMap as K
import Data.Aeson.Types (parseMaybe)
import qualified GHC.Internal.IO.Exception as GE
import Foreign
import Foreign.C.Types
import Foreign.C.Error
import Control.Exception
import Control.Monad
import Control.Concurrent
import qualified GHC.Conc as W
import System.Timeout (timeout)
import Data.IORef
import Data.Maybe
import System.IO
import System.Info (fullCompilerVersion)
import Data.Version
#ifdef darwin_HOST_OS
foreign import ccall unsafe "__error" errnoPtr :: IO (Ptr CInt)
#else
foreign import ccall unsafe "__errno_location" errnoPtr :: IO (Ptr CInt)
#endif
field :: J.FromJSON a => Value -> J.Key -> a
field (Object o) k=fromJust $ K.lookup k o >>= parseMaybe J.parseJSON
field _ _=error "object"
opt :: J.FromJSON a => Value -> J.Key -> Maybe a
opt (Object o) k=K.lookup k o >>= parseMaybe J.parseJSON
opt _ _=Nothing
sockopt s=case s of {O.CustomSockOpt (a,b)->[fromIntegral a::Int,fromIntegral b]}
abi=object
 ["F_GETFL" .= (#{const F_GETFL}::Int),"F_SETFL" .= (#{const F_SETFL}::Int),"F_GETFD" .= (#{const F_GETFD}::Int),"O_NONBLOCK" .= (#{const O_NONBLOCK}::Int),"FD_CLOEXEC" .= (#{const FD_CLOEXEC}::Int)
#ifdef SOCK_NONBLOCK
 ,"SOCK_NONBLOCK" .= (#{const SOCK_NONBLOCK}::Int)
#else
 ,"SOCK_NONBLOCK" .= (0::Int)
#endif
 ,"EINTR" .= (#{const EINTR}::Int),"EAGAIN" .= (#{const EAGAIN}::Int),"EWOULDBLOCK" .= (#{const EWOULDBLOCK}::Int),"EINPROGRESS" .= (#{const EINPROGRESS}::Int),"EBADF" .= (#{const EBADF}::Int)
 ,"POLLIN" .= (#{const POLLIN}::Int),"POLLOUT" .= (#{const POLLOUT}::Int),"POLLNVAL" .= (#{const POLLNVAL}::Int)
 ,"pollSize" .= (#{size struct pollfd}::Int),"pollFd" .= (#{offset struct pollfd, fd}::Int),"pollEvents" .= (#{offset struct pollfd, events}::Int),"pollRevents" .= (#{offset struct pollfd, revents}::Int)
 ,"sockaddrStorageSize" .= (#{size struct sockaddr_storage}::Int)
 ,"options" .= object ["NoDelay" .= sockopt N.NoDelay,"SoError" .= sockopt N.SoError,"IPv6Only" .= sockopt N.IPv6Only,"DontFragment" .= sockopt N.DontFragment,"Type" .= sockopt N.Type,"ReuseAddr" .= sockopt N.ReuseAddr,"SendBuffer" .= sockopt N.SendBuffer,"RecvBuffer" .= sockopt N.RecvBuffer]
 ]
viewError (e::IOException)=object ["type" .= show (GE.ioe_type e),"location" .= GE.ioe_location e,"description" .= GE.ioe_description e,"errno" .= fmap (\x->fromIntegral x::Int) (GE.ioe_errno e),"filename" .= GE.ioe_filename e,"show" .= show e]
observe action=do
 r<-try $ do {a<-action;_<-evaluate $ L.length $ J.encode a;pure a}
 pure $ either (\e->object ["error" .= viewError e]) id (r::Either IOException Value)
withInvalid=bracket (N.mkSocket (-1)) N.close
readExactly s n=if n<=0 then pure BS.empty else do
 x<-B.recv s n
 if BS.null x then pure x else (x<>) <$> readExactly s (n-BS.length x)
readAll s n=do
 x<-B.recv s n
 if BS.null x then pure x else (x<>) <$> readAll s n
run v=case field v "api" :: String of
 "abi"->pure abi
 "guard"->do
   script<-newIORef (field v "script" :: [[Int]])
   log<-newIORef ([]::[String])
   let action=do
        modifyIORef' log (++["call"])
        xs<-readIORef script
        case xs of
         [r,e]:rest->do {writeIORef script rest;errnoPtr >>= \p->poke p (fromIntegral e);pure r}
         _->error "guard script exhausted"
       block=modifyIORef' log (++["wait"])
   result<-observe $ toJSON <$> case field v "mode" :: String of
     "once"->throwErrnoIfMinus1 "guard" action
     "retry"->throwErrnoIfMinus1Retry "guard" action
     _->throwErrnoIfMinus1RetryMayBlock "guard" action block
   es<-readIORef log;pure $ object ["result" .= result,"events" .= es]
 "send-script"->withInvalid $ \s->do
   S.reset $ map (\[kind,n]->if kind==0 then Right n else Left n) (field v "script" :: [[Int]])
   result<-observe $ S.sendAll s (BS.pack $ map fromIntegral (field v "bytes" :: [Int])) >> pure Null
   es<-S.events;pure $ object ["result" .= result,"events" .= es]
 "wait-invalid"->withInvalid $ \s->observe $ do
   result<-timeout 100000 $ N.withFdSocket s ((if field v "write" then W.threadWaitWrite else W.threadWaitRead) . fromIntegral)
   pure $ toJSON $ isJust result
 "invalid"->withInvalid $ \s->observe $ do
   let n=fromMaybe 1 (opt v "size") :: Int
   case field v "operation" :: String of
    "recv"->toJSON . BS.unpack <$> B.recv s n
    "send"->toJSON <$> B.send s (BS.pack $ map fromIntegral (field v "bytes" :: [Int]))
    "sendAll"->B.sendAll s (BS.pack $ map fromIntegral (field v "bytes" :: [Int])) >> pure Null
    "set"->N.setSocketOption s N.NoDelay 1 >> pure Null
    "get"->toJSON <$> N.getSocketOption s N.NoDelay
    "connect"->N.connect s (N.SockAddrInet 80 (N.tupleToHostAddress (127,0,0,1))) >> pure Null
    "bind"->N.bind s (N.SockAddrInet 0 (N.tupleToHostAddress (127,0,0,1))) >> pure Null
    "listen"->N.listen s 1 >> pure Null
    "name"->N.getSocketName s >>= \(a::N.SockAddr)->pure $ toJSON $ show a
    "peer"->N.getPeerName s >>= \(a::N.SockAddr)->pure $ toJSON $ show a
    "shutdown"->N.shutdown s N.ShutdownSend >> pure Null
    _->error "operation"
 "socket"->observe $ bracket (N.socket (N.unpackFamily $ fromIntegral (field v "family" :: Int)) (N.GeneralSocketType $ fromIntegral (field v "type" :: Int)) (fromIntegral (field v "protocol" :: Int))) N.close $ \s->do
   nb<-N.withFdSocket s N.getNonBlock;ce<-N.withFdSocket s N.getCloseOnExec
   let (fam,typ)=(field v "family" :: Int,field v "type" :: Int)
   val<-if fam==(fromIntegral $ N.packFamily N.AF_INET) then N.getSocketOption s N.DontFragment else if fam==(fromIntegral $ N.packFamily N.AF_INET6) && typ `elem` [1,2] then N.getSocketOption s N.IPv6Only else pure (-99)
   t<-N.getSocketOption s N.Type
   pure $ object ["nonblock" .= nb,"cloexec" .= ce,"type" .= t,"option" .= val]
 "tcp"->observe $ bracket (N.socket N.AF_INET N.Stream 0) N.close $ \listener->do
   N.bind listener (N.SockAddrInet 0 (N.tupleToHostAddress (127,0,0,1)));N.listen listener 1
   address<-N.getSocketName listener
   done<-newEmptyMVar
   let payload=BS.pack $ map fromIntegral (field v "bytes" :: [Int]);response=BS.reverse payload<>BS.pack [0,255,1]
   _<-forkIO $ try (bracket (fst <$> N.accept listener) N.close $ \s->do
         x<-readExactly s (BS.length payload);B.sendAll s response;N.shutdown s N.ShutdownSend;pure x) >>= putMVar done
   bracket (N.socket N.AF_INET N.Stream 0) N.close $ \s->do
    N.connect s (address::N.SockAddr);N.setSocketOption s N.NoDelay 1;B.sendAll s payload
    received<-readAll s (field v "size")
    sent<-takeMVar done >>= either (throwIO :: IOException -> IO BS.ByteString) pure
    pure $ object ["sent" .= BS.unpack sent,"received" .= BS.unpack received]
 _->error "api"
main=do
 unless (showVersion fullCompilerVersion=="9.12.2") $ error "GHC 9.12.2 required"
 hSetBuffering stdout LineBuffering
 L.getContents >>= mapM_ (\line->case J.eitherDecode line of {Left e->error e;Right v->run v >>= L.putStrLn . J.encode}) . L.lines
