{-# LANGUAGE OverloadedStrings, DeriveDataTypeable #-}
module CodePageEffects where
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Data.IORef
import Data.Typeable
import Data.Word
import Foreign
import Foreign.C.Types(CInt)
import Control.Exception
import Control.Monad
import System.IO.Unsafe

type BOOL=Bool
type LPBOOL=Ptr BOOL
type BYTE=Word8
type DWORD=Word32
type UINT=Word32
type LPWSTR=Ptr Word16

data WinFailure=WinFailure String Word32 deriving (Show,Typeable)
instance Exception WinFailure
config :: IORef Object
config=unsafePerformIO $ newIORef K.empty
{-# NOINLINE config #-}
logs :: IORef [Value]
logs=unsafePerformIO $ newIORef []
{-# NOINLINE logs #-}
lastError :: IORef Word32
lastError=unsafePerformIO $ newIORef 0
{-# NOINLINE lastError #-}
option key def=do v<-readIORef config;pure $ case K.lookup key v of Nothing->def;Just x->case fromJSON x of Success a->a;Error e->error e
record x=modifyIORef' logs (x:)
reset v=writeIORef config v >> writeIORef logs [] >> writeIORef lastError 0
getConsoleCP=do n<-option "console" 0;record $ object ["api" .= ("GetConsoleCP"::String),"result" .= (n::Word32)];pure n
getACP=do n<-option "acp" 1252;record $ object ["api" .= ("GetACP"::String),"result" .= (n::Word32)];pure n
getLastError=do n<-readIORef lastError;record $ object ["api" .= ("GetLastError"::String),"result" .= n];pure n
failWith loc code=throwIO (WinFailure loc code)
throwGetLastError loc=getLastError >>= failWith loc
puts _=pure ()
c_GetCPInfo :: UINT -> Ptr a -> IO BOOL
c_GetCPInfo cp p=do
 n<-option "max" (2::Word32);err<-option "infoError" (0::Word32)
 poke (castPtr p) n;pokeArray (castPtr p `plusPtr` 4) ([63,0,129,132]++replicate 10 0::[Word8])
 writeIORef lastError err
 record $ object ["api" .= ("GetCPInfo"::String),"cp" .= cp,"max" .= n,"error" .= err]
 pure (err==0)
c_IsDBCSLeadByteEx :: UINT -> BYTE -> IO BOOL
c_IsDBCSLeadByteEx cp b=do
 let yes=b>=129 && b<=132
 record $ object ["api" .= ("IsDBCSLeadByteEx"::String),"cp" .= cp,"byte" .= b,"result" .= yes]
 pure yes
-- Controlled Win32 effects, not GHC buffer algorithms.
decodeMapping :: [Word8] -> Either Word32 [Word16]
decodeMapping []=Right []
decodeMapping (x:xs)
 | x<128 = (fromIntegral x:) <$> decodeMapping xs
 | x==254 = (0x20ac:) <$> decodeMapping xs
 | x==253 = (0xd800:) <$> decodeMapping xs
 | x>=129 && x<=132 = case xs of
    y:ys | y>=64 && y<=127 -> ((case x of 129->[0x400+fromIntegral y-64];130->[0xd83d,0xde00+fromIntegral y-64];131->[0x8000+fromIntegral y-64];_->[0xdc00,65])++) <$> decodeMapping ys
    _->Left 1113
 | otherwise=Left 1113
encodeMapping :: [Word16] -> ([Word8],Bool)
encodeMapping []=([],False)
encodeMapping (x:xs)
 | x<128=emit [fromIntegral x] False xs
 | x==0x20ac=emit [254] False xs
 | x>=0x400 && x<=0x43f=emit [129,fromIntegral (x-0x400+64)] False xs
 | x>=0x8000 && x<=0x803f=emit [131,fromIntegral (x-0x8000+64)] False xs
 | x==0xd83d = case xs of y:ys | y>=0xde00 && y<=0xde3f->emit [130,fromIntegral (y-0xde00+64)] False ys;_->emit [63] True xs
 | otherwise=emit [63] True xs
 where emit bs bad rest=let (ys,b)=encodeMapping rest in (bs++ys,bad||b)
c_MultiByteToWideChar :: UINT -> DWORD -> Ptr Word8 -> CInt -> LPWSTR -> CInt -> IO CInt
c_MultiByteToWideChar cp flags ip count op capacity=do
 xs<-peekArray (fromIntegral count) ip;forced<-option "decodeError" (0::Word32)
 let mapped=decodeMapping xs
     (out,err)=if forced/=0 then ([],forced) else case mapped of Left e->([],e);Right ys->if length ys>fromIntegral capacity then ([],122) else (ys,0)
 dirty<-option "dirty" False
 when (err/=0 && dirty && capacity>0) $ poke op 0xbeef
 when (err==0) $ pokeArray op out
 writeIORef lastError err
 record $ object ["api" .= ("MultiByteToWideChar"::String),"cp" .= cp,"flags" .= flags,"input" .= xs,"count" .= (fromIntegral count::Int),"capacity" .= (fromIntegral capacity::Int),"result" .= length out,"error" .= err]
 pure $ fromIntegral $ length out
c_WideCharToMultiByte :: UINT -> DWORD -> LPWSTR -> CInt -> Ptr Word8 -> CInt -> Ptr Word8 -> LPBOOL -> IO CInt
c_WideCharToMultiByte cp flags ip count op capacity def used=do
 xs<-peekArray (fromIntegral count) ip;initial<-peek used;forced<-option "encodeError" (0::Word32)
 let (mapped,bad)=encodeMapping xs
     (out,err)=if forced/=0 then ([],forced) else if length mapped>fromIntegral capacity then ([],122) else (mapped,0)
 dirty<-option "dirty" False
 when (err/=0 && dirty && capacity>0) $ poke op 0xee
 when (err==0) $ pokeArray op out
 poke used bad;writeIORef lastError err
 record $ object ["api" .= ("WideCharToMultiByte"::String),"cp" .= cp,"flags" .= flags,"input" .= xs,"count" .= (fromIntegral count::Int),"capacity" .= (fromIntegral capacity::Int),"defaultNull" .= (def==nullPtr),"initialDefault" .= initial,"defaulted" .= bad,"result" .= length out,"error" .= err]
 pure $ fromIntegral $ length out
