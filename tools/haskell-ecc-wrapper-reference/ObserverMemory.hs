{-# LANGUAGE OverloadedStrings #-}
module ObserverMemory where
import qualified Foreign.Marshal.Alloc as A
import qualified Foreign.ForeignPtr as F
import Foreign.Marshal.Utils(fillBytes)
import Foreign.ForeignPtr(ForeignPtr,withForeignPtr,plusForeignPtr)
import Foreign.Ptr
import Foreign.Storable
import Foreign.Marshal.Array
import Data.Word
import Data.IORef
import Data.Aeson
import qualified Data.ByteString as B
import Crypto.Hash(SHA256,Digest,hash)
import System.IO.Unsafe(unsafePerformIO)
import Control.Exception(finally)

{-# NOINLINE events #-}
events :: IORef [Value]
events=unsafePerformIO(newIORef [])
{-# NOINLINE tables #-}
tables :: IORef [(ForeignPtr Word8,Int)]
tables=unsafePerformIO(newIORef [])
{-# NOINLINE poison #-}
poison :: IORef Word8
poison=unsafePerformIO(newIORef 165)
record v=modifyIORef' events (++[v])
reset p=writeIORef poison p >> writeIORef events [] >> writeIORef tables []
allocaBytes :: Int -> (Ptr a -> IO b) -> IO b
allocaBytes size action = A.allocaBytes(size+7) $ \base -> do
 let p=base `plusPtr` 3
 byte<-readIORef poison
 pokeArray base ([17,34,51] :: [Word8]);fillBytes p byte size
 pokeArray(p `plusPtr` size)([68,85,102,119] :: [Word8])
 record(object["allocate" .= size])
 action(castPtr p) `finally` (do bytes<-peekArray(size+7)base;record(object["released" .= bytes]))
mallocForeignPtrBytes :: Int -> IO(ForeignPtr a)
mallocForeignPtrBytes size = do
 base<-F.mallocForeignPtrBytes(size+32)
 byte<-readIORef poison
 withForeignPtr base $ \p -> fillBytes p byte(size+32)
 modifyIORef' tables (++[(base,size)])
 record(object["tableAllocate" .= size])
 return(F.castForeignPtr(plusForeignPtr base 16))
tableHash fp size = withForeignPtr fp $ \p -> do
 bs<-B.packCStringLen(castPtr p,size)
 return(show(hash bs :: Digest SHA256))
snapshot = do
 es<-readIORef events
 ts<-readIORef tables
 snapshots<-mapM (\(base,size)->withForeignPtr base $ \p -> do
  bs<-B.packCStringLen(castPtr(p `plusPtr` 16),size)
  guards<-mapM (\i->peekByteOff p i :: IO Word8)([0..15]++[size+16..size+31])
  return(object["hash" .= show(hash bs :: Digest SHA256),"guards" .= guards])) ts
 return(object["events" .= es,"tables" .= snapshots])
