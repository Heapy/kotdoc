{-# LANGUAGE BangPatterns #-}
module OriginalThreadWait where
import Control.Concurrent
import Control.Concurrent.MVar
import Control.Exception
import Control.Monad
import Data.IORef
import qualified Data.Map.Strict as Map
import System.IO.Unsafe
import GHC.Internal.IOArray (IOArray,newIOArray,readIOArray,writeIOArray,boundsIOArray)
import GHC.Internal.System.Posix.Types (Fd)
import Foreign.C.Error (eBADF,errnoToIOError)
import GHC.Internal.Event.Internal (Event,eventIs,evtClose)
import GHC.Internal.Event.Manager (EventManager,registerFd,unregisterFd_)
import qualified GHC.Internal.Event.Manager as M

eventManager :: IORef (IOArray Int (Maybe (ThreadId,EventManager)))
eventManager=unsafePerformIO $ newIOArray (0,-1) Nothing >>= newIORef
{-# NOINLINE eventManager #-}
threadManagers :: IORef (Map.Map ThreadId EventManager)
threadManagers=unsafePerformIO $ newIORef Map.empty
{-# NOINLINE threadManagers #-}
configure managers=do
 tid<-myThreadId
 array<-newIOArray (0,length managers-1) Nothing
 forM_ (zip [0..] managers) $ \(i,mgr)->writeIOArray array i (Just (tid,mgr))
 writeIORef eventManager array
 writeIORef threadManagers Map.empty
setCurrentManager mgr=myThreadId >>= \tid->atomicModifyIORef' threadManagers (\m->(Map.insert tid mgr m,()))
getSystemEventManager_=do
 tid<-myThreadId;managers<-readIORef threadManagers
 case Map.lookup tid managers of
  Just mgr->pure mgr
  Nothing->do {array<-readIORef eventManager;Just (_,mgr)<-readIOArray array 0;pure mgr}

closeFdWith :: (Fd -> IO ())        -- ^ Action that performs the close.
            -> Fd                   -- ^ File descriptor to close.
            -> IO ()
closeFdWith close fd = close_loop
  where
    finish mgr table cbApp = putMVar (M.callbackTableVar mgr fd) table >> cbApp
    zipWithM f xs ys = sequence (zipWith f xs ys)
      -- The array inside 'eventManager' can be swapped out at any time, see
      -- 'ioManagerCapabilitiesChanged'. See #21651. We detect this case by
      -- checking the array bounds before and after. When such a swap has
      -- happened we cleanup and try again
    close_loop = do
      eventManagerArray <- readIORef eventManager
      let ema_bounds@(low, high) = boundsIOArray eventManagerArray
      mgrs <- flip mapM [low..high] $ \i -> do
        Just (_,!mgr) <- readIOArray eventManagerArray i
        return mgr

      -- 'takeMVar', and 'M.closeFd_' might block, although for a very short time.
      -- To make 'closeFdWith' safe in presence of asynchronous exceptions we have
      -- to use uninterruptible mask.
      join $ uninterruptibleMask_ $ do
        tables <- flip mapM mgrs $ \mgr -> takeMVar $ M.callbackTableVar mgr fd
        new_ema_bounds <- boundsIOArray `fmap` readIORef eventManager
        -- Here we exploit Note [The eventManager Array]
        if new_ema_bounds /= ema_bounds
          then do
            -- the array has been modified.
            -- mgrs still holds the right EventManagers, by the Note.
            -- new_ema_bounds must be larger than ema_bounds, by the note.
            -- return the MVars we took and try again
            sequence_ $ zipWith (\mgr table -> finish mgr table (pure ())) mgrs tables
            pure close_loop
          else do
            -- We surely have taken all the appropriate MVars. Even if the array
            -- has been swapped, our mgrs is still correct.
            -- Remove the Fd from all callback tables, close the Fd, and run all
            -- callbacks.
            cbApps <- zipWithM (\mgr table -> M.closeFd_ mgr table fd) mgrs tables
            close fd `finally` sequence_ (zipWith3 finish mgrs tables cbApps)
            pure (pure ())

threadWait :: Event -> Fd -> IO ()
threadWait evt fd = mask_ $ do
  m <- newEmptyMVar
  mgr <- getSystemEventManager_
  reg <- registerFd mgr (\_ e -> putMVar m e) fd evt M.OneShot
  evt' <- takeMVar m `onException` unregisterFd_ mgr reg
  if evt' `eventIs` evtClose
    then ioError $ errnoToIOError "threadWait" eBADF Nothing Nothing
    else return ()

