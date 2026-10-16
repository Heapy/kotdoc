{-# LANGUAGE CPP, OverloadedStrings, ScopedTypeVariables #-}
#include <sys/types.h>
#include <fcntl.h>
#include <errno.h>
#include <time.h>
#ifdef darwin_HOST_OS
#include <sys/event.h>
#ifndef NOTE_EOF
#define NOTE_EOF 0
#endif
#else
#include <sys/epoll.h>
#endif
module Main where
import qualified GHC.Internal.Event.Manager as M
import qualified GHC.Internal.Event.Internal as I
import qualified GHC.Internal.Event.IntTable as IT
import qualified GHC.Internal.Event.Control as C
import qualified GHC.Internal.IO.Exception as GE
import GHC.Internal.System.Posix.Types (Fd(..))
import qualified GHC.Internal.Event.Unique as U
import qualified Data.Aeson as J
import Data.Aeson (Value(..),object,(.=),toJSON)
import qualified Data.Aeson.KeyMap as K
import Data.Aeson.Types (parseMaybe)
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Map.Strict as Map
import Data.IORef
import Data.Maybe
import Data.List (nub,sort)
import Control.Exception hiding (mask)
import Control.Monad
import Control.Concurrent.MVar
import Control.Concurrent (forkIO,threadDelay,killThread)
import qualified OriginalThreadWait as OW
import qualified GHC.Internal.System.Posix.Internals as P
import Foreign hiding (void)
import System.Timeout (timeout)
import Foreign.C.Error
import System.IO
import System.Info (fullCompilerVersion)
import Data.Version
field :: J.FromJSON a => Value -> J.Key -> a
field (Object o) k=fromJust $ K.lookup k o >>= parseMaybe J.parseJSON
field _ _=error "object"
opt :: J.FromJSON a => Value -> J.Key -> Maybe a
opt (Object o) k=K.lookup k o >>= parseMaybe J.parseJSON
opt _ _=Nothing
val (Object o) k=fromMaybe Null $ K.lookup k o
mask :: Int -> I.Event
mask n=mconcat $ [I.evtRead|n `mod` 2==1]++[I.evtWrite|n `div` 2 `mod` 2==1]++[I.evtClose|n `div` 4 `mod` 2==1]
number :: I.Event -> Int
number e=sum [n|(bit,n)<-[(I.evtRead,1),(I.evtWrite,2),(I.evtClose,4)],e `I.eventIs` bit]
err e
 | Just (x::IOException)<-fromException e=object ["kind" .= ("io"::String),"type" .= show (GE.ioe_type x),"location" .= GE.ioe_location x,"description" .= GE.ioe_description x,"errno" .= fmap (\n->fromIntegral n::Int) (GE.ioe_errno x),"show" .= show x]
 | Just (ErrorCall s)<-fromException e=object ["kind" .= ("fatal"::String),"show" .= s]
 | Just ThreadKilled<-fromException e=object ["kind" .= ("async"::String),"show" .= ("thread killed"::String)]
 | otherwise=error $ show e
observe action=do
 x<-try $ do {v<-action;_<-evaluate $ L.length $ J.encode v;pure v}
 pure $ either (\e->object ["error" .= err e]) id x
abi=object
 ["EINTR" .= (#{const EINTR}::Int),"ENOENT" .= (#{const ENOENT}::Int),"EINVAL" .= (#{const EINVAL}::Int),"ENOTSUP" .= (#{const ENOTSUP}::Int),"EBADF" .= (#{const EBADF}::Int),"EAGAIN" .= (#{const EAGAIN}::Int),"EWOULDBLOCK" .= (#{const EWOULDBLOCK}::Int)
 ,"F_GETFL" .= (#{const F_GETFL}::Int),"F_SETFL" .= (#{const F_SETFL}::Int),"F_SETFD" .= (#{const F_SETFD}::Int),"O_NONBLOCK" .= (#{const O_NONBLOCK}::Int),"FD_CLOEXEC" .= (#{const FD_CLOEXEC}::Int)
 ,"timespecSize" .= (#{size struct timespec}::Int),"timespecSec" .= (#{offset struct timespec, tv_sec}::Int),"timespecNsec" .= (#{offset struct timespec, tv_nsec}::Int)
#ifdef darwin_HOST_OS
 ,"backend" .= ("kqueue"::String),"haveOneShot" .= False
 ,"eventSize" .= (#{size struct kevent}::Int),"eventFd" .= (#{offset struct kevent, ident}::Int),"eventFilter" .= (#{offset struct kevent, filter}::Int),"eventFlags" .= (#{offset struct kevent, flags}::Int),"eventFflags" .= (#{offset struct kevent, fflags}::Int),"eventData" .= (#{offset struct kevent, data}::Int),"eventUdata" .= (#{offset struct kevent, udata}::Int)
 ,"EVFILT_READ" .= (#{const EVFILT_READ}::Int),"EVFILT_WRITE" .= (#{const EVFILT_WRITE}::Int),"EV_ADD" .= (#{const EV_ADD}::Int),"EV_DELETE" .= (#{const EV_DELETE}::Int),"EV_ONESHOT" .= (#{const EV_ONESHOT}::Int),"NOTE_EOF" .= (#{const NOTE_EOF}::Int)
#else
 ,"backend" .= ("epoll"::String),"haveOneShot" .= True
 ,"eventSize" .= (#{size struct epoll_event}::Int),"eventFd" .= (#{offset struct epoll_event, data.fd}::Int),"eventTypes" .= (#{offset struct epoll_event, events}::Int)
 ,"EPOLL_CTL_ADD" .= (#{const EPOLL_CTL_ADD}::Int),"EPOLL_CTL_MOD" .= (#{const EPOLL_CTL_MOD}::Int),"EPOLL_CTL_DEL" .= (#{const EPOLL_CTL_DEL}::Int)
 ,"EPOLLIN" .= (#{const EPOLLIN}::Int),"EPOLLOUT" .= (#{const EPOLLOUT}::Int),"EPOLLERR" .= (#{const EPOLLERR}::Int),"EPOLLHUP" .= (#{const EPOLLHUP}::Int),"EPOLLONESHOT" .= (#{const EPOLLONESHOT}::Integer)
#endif
 ]
program native v=do
 events<-newIORef ([]::[Value]);answers<-newIORef ([]::[Value]);cookies<-newIORef Map.empty
 polls<-newIORef ([]::[Value]);managerRef<-newIORef Nothing
 originalBackend<-if native then Just <$> M.newDefaultBackend else pure Nothing
 let emit x=modifyIORef' events (++[x])
     effect x=do
       emit x
       xs<-readIORef answers
       case xs of
        []->pure True
        Bool b:rest->writeIORef answers rest >> pure b
        Number n:rest->do {writeIORef answers rest;throwIO $ errnoToIOError "backend" (Errno $ fromIntegral $ (round n::Int)) Nothing Nothing}
        _->error "backend result"
     poll _ timeout callback=do
       emit $ object ["op" .= ("poll"::String),"timeout" .= maybe "nonblock" show timeout]
       case originalBackend of
        Just be->I.poll be timeout callback
        Nothing->do
         xs<-readIORef polls
         case xs of
          []->pure 0
          x:rest->do
           writeIORef polls rest
           Just mgr<-readIORef managerRef
           case opt x "control" :: Maybe String of
            Just "release"->M.release mgr
            Just "shutdown"->M.shutdown mgr
            _->pure ()
           let notifications=fromMaybe [] (opt x "events") :: [[Int]]
           forM_ notifications $ \[fd,event]->callback (fromIntegral fd) (mask event)
           pure $ fromMaybe (length notifications) (opt x "result")
     modify _ fd old new=do
       let e=object ["op" .= ("modify"::String),"fd" .= (fromIntegral fd::Int),"old" .= number old,"new" .= number new]
       case originalBackend of {Nothing->effect e;Just be->emit e >> I.modifyFd be fd old new}
     once _ fd ev=do
       let e=object ["op" .= ("once"::String),"fd" .= (fromIntegral fd::Int),"events" .= number ev]
       case originalBackend of {Nothing->effect e;Just be->emit e >> I.modifyFdOnce be fd ev}
     delete _=do
       emit $ object ["op" .= ("delete"::String)]
       maybe (pure ()) I.delete originalBackend
     backend=I.backend poll modify once delete ()
 mgr<-M.newWith backend
 writeIORef managerRef (Just mgr)
 let actions=field v "actions" :: [Value]
     fds=sort $ nub $ map (\x->fromMaybe 123 (opt x "fd")::Int) actions
     snapshot=do
       tables<-forM fds $ \fd->withMVar (M.callbackTableVar mgr $ fromIntegral fd) $ \tbl->do
        xs<-IT.lookup fd tbl
        pure $ object ["fd" .= fd,"entries" .= fmap (map (\x->object ["events" .= number (I.elEvent $ M.fdEvents x),"multi" .= (I.elLifetime (M.fdEvents x)==I.MultiShot)])) xs]
       state<-readIORef (M.emState mgr)
       pure (tables,show state)
     act a=do
       writeIORef events [];writeIORef answers (fromMaybe [] $ opt a "responses");writeIORef polls (fromMaybe [] $ opt a "polls")
       let fd=fromMaybe 123 (opt a "fd")::Int
       result<-observe $ case field a "op" :: String of
        "register"->do
          let label=field a "id" :: String
              callback _ ev=do
               emit $ object ["op" .= ("callback"::String),"id" .= label,"events" .= number ev]
               when (fromMaybe False $ opt a "callbackError") $ ioError $ userError ("callback:"++label)
          key<-M.registerFd mgr callback (fromIntegral fd) (mask $ field a "events") (if fromMaybe False (opt a "multi") then I.MultiShot else I.OneShot)
          modifyIORef' cookies (Map.insert label key);pure Null
        "unregister"->do
          keys<-readIORef cookies
          let key=fromMaybe (M.FdKey (fromIntegral fd) (U.Unique (-1))) $ Map.lookup (fromMaybe "unknown" $ opt a "id") keys
          M.unregisterFd mgr key;pure Null
        "dispatch"->M.onFdEvent mgr (fromIntegral fd) (mask $ field a "events") >> pure Null
        "close"->M.closeFd mgr (\_->emit $ object ["op" .= ("close"::String),"fd" .= fd]) (fromIntegral fd) >> pure Null
        "loop"->M.loop mgr >> pure Null
        "step"->toJSON . show <$> M.step mgr
        "shutdown"->M.shutdown mgr >> pure Null
        "release"->M.release mgr >> pure Null
        _->error "action"
       es<-readIORef events;(tables,state)<-snapshot
       pure $ object ["result" .= result,"effects" .= es,"tables" .= tables,"state" .= state]
 output<-mapM act actions `finally` (M.finished mgr >>= \done->unless done $ M.cleanup mgr)
 pure $ toJSON output
waiters v=do
 let count=field v "count" :: Int;managerCount=field v "managers" :: Int;mode=field v "mode" :: String
     cancelled=if mode=="cancel" then field v "cancel" else 0
     replacement=fromMaybe False (opt v "replace")
 managers<-replicateM managerCount M.new
 OW.configure managers
 loops<-forM managers $ \mgr->do {done<-newEmptyMVar;_<-forkIO $ try (M.loop mgr) >>= putMVar done;pure done}
 closed<-newIORef False;closeCount<-newIORef (0::Int)
 (rd,wr)<-allocaArray 2 $ \p->do
  throwErrnoIfMinus1_ "pipe" (P.c_pipe p)
  r<-peekElemOff p 0;w<-peekElemOff p 1;pure (fromIntegral r, w)
 let snapshot=forM managers $ \mgr->withMVar (M.callbackTableVar mgr rd) $ \tbl->do
        entries<-IT.lookup (fromIntegral rd) tbl
        pure $ fmap (map (\x->object ["events" .= number (I.elEvent $ M.fdEvents x),"multi" .= (I.elLifetime (M.fdEvents x)==I.MultiShot)])) entries
     awaitCount n=do
       entries<-forM managers $ \mgr->withMVar (M.callbackTableVar mgr rd) $ \tbl->maybe 0 length <$> IT.lookup (fromIntegral rd) tbl
       unless (sum entries==n) $ threadDelay 1000 >> awaitCount n
     spawn i=do
       done<-newEmptyMVar
       tid<-forkIO $ do
        OW.setCurrentManager (managers!!(i `mod` managerCount))
        result<-try (OW.threadWait I.evtRead rd) :: IO (Either SomeException ())
        putMVar done result
       pure (i,tid,done)
     collect (i,_,done)=do
       result<-takeMVar done
       pure $ object ["id" .= i,"result" .= either (\e->object ["error" .= err e]) (const Null) result]
     rawClose _=do
       writeIORef closed True;modifyIORef' closeCount (+1)
       _<-P.c_close (fromIntegral rd)
       when (fromMaybe False (opt v "closeError")) $ ioError $ userError "raw-close"
     closeRead=OW.closeFdWith rawClose rd
     cleanup=do
       isClosed<-readIORef closed
       unless isClosed $ void $ try (closeRead) `asTypeOf` pure (Right ()::Either IOException ())
       _<-P.c_close wr
       forM_ (zip managers loops) $ \(mgr,done)->do
         M.shutdown mgr
         outcome<-takeMVar done :: IO (Either SomeException ())
         either throwIO pure outcome
 action<-timeout 5000000 (flip finally cleanup $ do
   workers<-mapM spawn [0..count-1]
   awaitCount count;before<-snapshot
   forM_ (take cancelled workers) $ \(_,tid,_)->killThread tid
   cancelledResults<-mapM collect (take cancelled workers)
   afterCancel<-snapshot
   extra<-if replacement then (:[]) <$> spawn count else pure []
   awaitCount (count-cancelled+length extra)
   closeResult<-if mode=="close" then observe (closeRead >> pure Null) else do
     with (1::Word8) $ \p->throwErrnoIfMinus1_ "write" (P.c_write wr (castPtr p) 1)
     pure Null
   results<-mapM collect (drop cancelled workers++extra)
   after<-snapshot
   isClosed<-readIORef closed
   unless isClosed $ closeRead
   closures<-readIORef closeCount
   pure $ object ["before" .= before,"afterCancel" .= afterCancel,"after" .= after,"results" .= (cancelledResults++results),"closeResult" .= closeResult,"closeCount" .= closures])
 maybe (error "native waiter scenario timed out") pure action
lifecycle=do
 mgr<-M.new
 let awaitState expected=do {actual<-readIORef (M.emState mgr);unless (actual==expected) $ threadDelay 1000 >> awaitState expected}
     launch=do {done<-newEmptyMVar;_<-forkIO $ try (M.loop mgr) >>= putMVar done;pure done}
     finish done=takeMVar done >>= either (throwIO :: SomeException -> IO ()) pure
 first<-launch;awaitState M.Running;M.release mgr;finish first
 released<-readIORef (M.emState mgr)
 second<-launch;awaitState M.Running;M.shutdown mgr;finish second
 stopped<-readIORef (M.emState mgr)
 pure $ toJSON [show released,show stopped]
burst v=do
 mgr<-M.new;OW.configure [mgr]
 let count=field v "count" :: Int
 pipes<-replicateM count $ allocaArray 2 $ \p->do
  throwErrnoIfMinus1_ "pipe" (P.c_pipe p)
  r<-peekElemOff p 0;w<-peekElemOff p 1;pure (fromIntegral r,w)
 seen<-newIORef ([]::[Int])
 let cleanup=do
       forM_ pipes $ \(rd,wr)->do
        OW.closeFdWith (\fd->P.c_close (fromIntegral fd) >> pure ()) rd
        _<-P.c_close wr;pure ()
       M.cleanup mgr
 flip finally cleanup $ do
  forM_ (zip [0..] pipes) $ \(i,(rd,_))->M.registerFd mgr (\_ _->modifyIORef' seen (++[i])) rd I.evtRead I.OneShot
  forM_ pipes $ \(_,wr)->with (1::Word8) $ \p->throwErrnoIfMinus1_ "write" (P.c_write wr (castPtr p) 1)
  let steps=do
       before<-length <$> readIORef seen
       if before==count then pure [] else do
        _<-M.step mgr
        after<-length <$> readIORef seen
        ((after-before):) <$> steps
  ns<-steps;ids<-readIORef seen
  pure $ object ["steps" .= ns,"ids" .= sort ids]
run v=case field v "api" :: String of
 "abi"->pure abi
 "program"->program False v
 "native-program"->program True v
 "waiters"->waiters v
 "lifecycle"->lifecycle
 "burst"->burst v
 _->error "api"
main=do
 unless (showVersion fullCompilerVersion=="9.12.2") $ error "GHC 9.12.2 required"
 hSetBuffering stdout LineBuffering
 L.getContents >>= mapM_ (\line->case J.eitherDecode line of {Left e->error e;Right v->observe (run v) >>= L.putStrLn . J.encode}) . L.lines
