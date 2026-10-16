{-# LANGUAGE OverloadedStrings #-}
module OriginalSend where
import Control.Monad
import qualified Data.ByteString as B
import Data.ByteString (ByteString)
import Data.ByteString.Unsafe (unsafeUseAsCStringLen)
import Foreign
import Foreign.C.Types
import Network.Socket (Socket,withFdSocket)
import Data.IORef
import System.IO.Unsafe
import Foreign.C.Error
import Control.Exception
state :: IORef ([Either Int Int], [[Int]])
state=unsafePerformIO $ newIORef ([],[])
{-# NOINLINE state #-}
reset xs=writeIORef state (xs,[])
events=snd <$> readIORef state
sendBuf :: Socket -> Ptr Word8 -> Int -> IO Int
sendBuf _ p n=do
 raw<-map fromIntegral <$> peekArray n p
 (xs,es)<-readIORef state
 case xs of
  []->error "send script exhausted"
  x:rest->do
   writeIORef state (rest,es++[raw])
   either (\e->throwIO $ errnoToIOError "Network.Socket.sendBuf" (Errno $ fromIntegral e) Nothing Nothing) pure x
rtsSupportsBoundThreads=True
threadWaitWrite :: Int -> IO ()
threadWaitWrite _=modifyIORef' state (\(xs,es)->(xs,es++[[-1]]))
send :: Socket     -- ^ Connected socket
     -> ByteString  -- ^ Data to send
     -> IO Int      -- ^ Number of bytes sent
send s xs = unsafeUseAsCStringLen xs $ \(str, len) ->
    sendBuf s (castPtr str) len

waitWhen0 :: Int -> Socket -> IO ()
waitWhen0 0 s = when rtsSupportsBoundThreads $
    withFdSocket s $ \fd -> threadWaitWrite $ fromIntegral fd
waitWhen0 _ _ = return ()

-- | Send data to the socket.  The socket must be connected to a
-- remote socket.  Unlike 'send', this function continues to send data
-- until either all data has been sent or an error occurs.  On error,
-- an exception is raised, and there is no way to determine how much
-- data, if any, was successfully sent.
sendAll :: Socket     -- ^ Connected socket
        -> ByteString  -- ^ Data to send
        -> IO ()
sendAll _ "" = return ()
sendAll s bs0 = loop bs0
  where
    loop bs = do
        -- "send" throws an exception.
        sent <- send s bs
        waitWhen0 sent s
        when (sent /= B.length bs) $ loop $ B.drop sent bs

