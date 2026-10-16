{-# LANGUAGE OverloadedStrings #-}
-- Development-only native boundary; no hourglass arithmetic/conversion algorithm.
module System.Win32.Time where
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import Data.IORef
import Data.Int
import Data.Word
import System.IO.Unsafe(unsafePerformIO)
import Control.Exception
newtype FILETIME=FILETIME Word64
data SYSTEMTIME=SYSTEMTIME Word16 Word16 Word16 Word16 Word16 Word16 Word16 Word16
data TimeZoneId=TzIdUnknown|TzIdStandard|TzIdDaylight
data TIME_ZONE_INFORMATION=TZI {tziBias::Int32,tziStandardBias::Int32,tziDaylightBias::Int32}
state :: IORef (Object,[Value])
state=unsafePerformIO $ newIORef (K.empty,[]::[Value])
{-# NOINLINE state #-}
field v k=case fromJSON (maybe (error "missing") id (K.lookup k v)) of Success a->a;Error e->error e
configure v=writeIORef state (field v "config",[])
trace=toJSON . snd <$> readIORef state
record x=atomicModifyIORef' state (\(v,xs)->((v,xs++[x]),v))
fileTimeToSystemTime (FILETIME word)=do
 c<-record $ toJSON [String "file",toJSON $ show word]
 if not (field c "fileOk") then throwIO (ErrorCall "native failure") else do
  let [a,b,d,e,f,g,h,i]=map fromIntegral (field c "systemTime"::[Integer])
  pure $ SYSTEMTIME a b d e f g h i
getSystemTimeAsFileTime=do
 c<-record $ toJSON [String "fileNow"]
 pure $ FILETIME $ read $ field c "fileTime"
getTimeZoneInformation=do
 c<-record $ toJSON [String "timezone"]
 if not (field c "zoneOk") then throwIO (ErrorCall "native failure") else pure (case field c "mode"::Int of 1->TzIdStandard;2->TzIdDaylight;_->TzIdUnknown,TZI (fromIntegral (field c "bias"::Integer)) (fromIntegral (field c "standardBias"::Integer)) (fromIntegral (field c "daylightBias"::Integer)))
