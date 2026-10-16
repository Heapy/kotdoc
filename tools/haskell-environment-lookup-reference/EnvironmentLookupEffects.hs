{-# LANGUAGE OverloadedStrings #-}
module EnvironmentLookupEffects where
import LookupEffects
import Foreign hiding (allocaArray)
import qualified Foreign as F
import Data.Aeson
import Data.IORef
import qualified Data.ByteString as B
import GHC.Internal.IO.Encoding (mkTextEncoding)
import qualified GHC.Internal.Foreign.C.String.Encoding as Enc
import qualified OriginalWindowsWide as W
import qualified OriginalWindowsErrors as E
import qualified EnvironmentWindowsEffects as WE
withCString s action=do
 v<-readIORef config
 let name=field v "foreign" :: String
 emit $ object ["foreign" .= name]
 check "foreignFailure"
 enc<-mkTextEncoding (name++"//IGNORE")
 Enc.withCString enc s action
c_getenv p=do
 name<-B.packCString p
 emit $ object ["query" .= B.unpack name]
 check "queryFailure"
 castPtr <$> readIORef valuePtr
getFileSystemEncoding=do
 v<-readIORef config
 let name=field v "filesystem" :: String
 emit $ object ["filesystem" .= name]
 check "filesystemFailure"
 mkTextEncoding (name++"//ROUNDTRIP")
withCWString s=withArray0 0 (W.charsToCWchars s)
peekCWString p=W.cWcharsToChars <$> peekArray0 0 p
allocaArray :: Int -> (Ptr Word16 -> IO a) -> IO a
allocaArray n action=F.allocaArray n $ \p->pokeArray p (replicate n 0) >> action p
c_GetEnvironmentVariable :: Ptr Word16 -> Ptr Word16 -> Word32 -> IO Word32
c_GetEnvironmentVariable name buffer size=do
 ns<-peekArray0 0 name
 emit $ object ["queryWide" .= ns,"size" .= size]
 check "queryFailure"
 reply<-atomicModifyIORef' replies $ \xs->case xs of []->([],object ["result" .= (0::Int)]);x:rest->(rest,x)
 case optional reply "units" of
  Nothing->pure ()
  Just xs->pokeArray buffer (take (fromIntegral size) (map fromIntegral (xs::[Int])++[0]))
 pure $ fromInteger (field reply "result"::Integer)
c_GetLastError=WE.getLastError
throwGetLastError=E.throwGetLastError
