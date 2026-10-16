{-# LANGUAGE CPP,OverloadedStrings,ScopedTypeVariables #-}
#if __GLASGOW_HASKELL__ != 912 || __GLASGOW_HASKELL_PATCHLEVEL1__ != 2 || __GLASGOW_HASKELL_PATCHLEVEL2__ != 0
#error GHC 9.12.2 required
#endif
module Main where
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy as L
import qualified Data.ByteString.Lazy.Char8 as LC
import Data.Hourglass
import Time.Epoch
import Data.Char(chr,ord)
import Foreign.C.Types(CTime(..))
import Control.Exception
import CustomTimeFormatFct(customTimeFormatFct)
import qualified Data.Time.Format as T
import qualified Data.Time.Clock.POSIX as T
#ifdef MODEL
import qualified Model
#endif
field v k=case fromJSON (maybe (error "missing field") id (K.lookup k v)) of Success a->a;Error e->error e
(!) v k=maybe (error "missing value") id (K.lookup k v)
points=map ord
chars v=map chr (fromJSONValue v::[Int])
fromJSONValue v=case fromJSON v of Success a->a;Error e->error e
readDT [y,m,d,h,mi,s,ns]=DateTime (Date (fromIntegral y) (toEnum $ fromIntegral m) (fromIntegral d)) (TimeOfDay (Hours h) (Minutes mi) (Seconds s) (NanoSeconds ns))
readDT _=error "dateTime"
dtJSON (DateTime (Date y m d) (TimeOfDay (Hours h) (Minutes mi) (Seconds s) (NanoSeconds ns)))=toJSON [fromIntegral y,fromIntegral $ fromEnum m,fromIntegral d,h,mi,s,ns]
dateJSON (Date y m d)=toJSON [y,fromEnum m,d]
todJSON (TimeOfDay (Hours h) (Minutes mi) (Seconds s) (NanoSeconds ns))=toJSON [h,mi,s,ns]
readEP [s,ns]=ElapsedP (Elapsed $ Seconds s) (NanoSeconds ns)
readEP _=error "ep"
custom v=let name=chars (v ! "name"); kind=field v "kind"::String in if kind=="original" then customTimeFormatFct else TimeFormatFct name (parse kind) (printF kind)
 where
 parse "reject" _ _=Left $ chars (v ! "message")
 parse "error" _ _=error $ field v "error"
 parse "replace" _ _=Right ((readDT $ field v "dt",TimezoneOffset $ field v "timezone"),chars $ v ! "suffix")
 parse "partialDate" _ _=let dt=readDT $ field v "dt" in Right ((DateTime (error "date demanded") (dtTime dt),TimezoneOffset $ field v "timezone"),chars $ v ! "suffix")
 parse "partialTime" _ _=let dt=readDT $ field v "dt" in Right ((DateTime (dtDate dt) (error "time demanded"),TimezoneOffset $ field v "timezone"),chars $ v ! "suffix")
 parse _ acc s=Right (acc,if field v "replaceSuffix" then chars (v ! "suffix") else drop (field v "drop") s)
 printF "error" _ _=error $ field v "error"
 printF _ _ _=chars $ v ! "printed"
element (Object v)
 | Just x<-K.lookup "precision" v=Format_Precision $ fromJSONValue x
 | Just x<-K.lookup "text" v=Format_Text $ chr $ fromJSONValue x
 | Just x<-K.lookup "function" v=Format_Fct $ custom $ fromJSONValue x
element (String s)=case s of
 "Year2"->Format_Year2;"Year4"->Format_Year4;"Year"->Format_Year;"Month"->Format_Month;"Month2"->Format_Month2;"MonthName_Short"->Format_MonthName_Short
 "DayYear"->Format_DayYear;"DayYear3"->Format_DayYear3;"Day"->Format_Day;"Day2"->Format_Day2;"Hour"->Format_Hour;"Minute"->Format_Minute;"Second"->Format_Second
 "UnixSecond"->Format_UnixSecond;"MilliSecond"->Format_MilliSecond;"MicroSecond"->Format_MicroSecond;"NanoSecond"->Format_NanoSecond;"TimezoneName"->Format_TimezoneName
 "TzHM_Colon_Z"->Format_TzHM_Colon_Z;"TzHM_Colon"->Format_TzHM_Colon;"TzHM"->Format_TzHM;"Tz_Offset"->Format_Tz_Offset;"Spaces"->Format_Spaces
 _->error "element"
element _=error "element"
format (Object v)
 | Just s<-K.lookup "string" v=toFormat $ chars s
 | Just b<-K.lookup "builtin" v=if (fromJSONValue b::Int)==0 then toFormat ISO8601_Date else toFormat ISO8601_DateAndTime
 | Just es<-K.lookup "elements" v=TimeFormatString $ map element $ fromJSONValue es
format _=error "format"
failure :: (TimeFormatElem,String)->Value
failure (e,m)=object ["left" .= [points $ show e,points m]]
localResult :: String->Either (TimeFormatElem,String) (LocalTime DateTime,String)->Value
localResult part r=case r of
 Left e->failure e
 Right (local,rest)->let dt=localTimeUnwrap local;tz=localTimeGetTimezone local in case part::String of
  "tag"->toJSON True
  "remaining"->toJSON $ points rest
  "timezone"->toJSON $ timezoneOffsetToMinutes tz
  "date"->dateJSON $ dtDate dt
  "time"->todJSON $ dtTime dt
  _->object ["right" .= [dtJSON dt,toJSON $ timezoneOffsetToMinutes tz,toJSON $ points rest]]
globalResult :: String->Either (TimeFormatElem,String) (DateTime,String)->Value
globalResult part r=case r of
 Left e->failure e
 Right (dt,rest)->case part::String of
  "tag"->toJSON True
  "remaining"->toJSON $ points rest
  "date"->dateJSON $ dtDate dt
  "time"->todJSON $ dtTime dt
  _->object ["right" .= [dtJSON dt,toJSON $ points rest]]
run v=let fmt=format $ v ! "format";TimeFormatString es=fmt in case field v "api"::String of
 "meta"->object ["shown" .= points (show fmt),"elements" .= map (points . show) es,"equal" .= (fmt==format (v ! "other")),"isoShown" .= [show ISO8601_Date,show ISO8601_DateAndTime]]
 "parse"->let s=chars $ v ! "input";part=field v "part" in case field v "action"::String of
  "localE"->localResult part $ localTimeParseE fmt s
  "globalE"->globalResult part $ timeParseE fmt s
  "local"->maybe Null (\local->localResult part $ Right (local,"")) $ localTimeParse fmt s
  "global"->maybe Null (\dt->globalResult part $ Right (dt,"")) $ timeParse fmt s
  _->error "parse action"
 "roundtrip"->let dt=readDT $ field v "dt";s=timePrint fmt dt in object ["printed" .= points s,"parsed" .= globalResult "full" (timeParseE fmt s),"equal" .= (fmap fst (timeParseE fmt s)==Right dt)]
 "propertyPrint"->let s=field v "scalar";e=Elapsed $ Seconds s;utc=T.posixSecondsToUTCTime $ fromIntegral s;actual=[timePrint ISO8601_Date e,timePrint "EPOCH" e];expected=[T.formatTime T.defaultTimeLocale "%F" utc,T.formatTime T.defaultTimeLocale "%s" utc] in if actual==expected then toJSON $ map points expected else error $ "original format property failed: "++show (s,actual,expected)
 "print"->dispatch v fmt
 _->error "api"
dispatch v fmt=case field v "kind"::Int of
 0->printOp v fmt (CTime $ field v "scalar")
 1->printOp v fmt (Elapsed $ Seconds $ field v "scalar")
 2->printOp v fmt (readEP $ field v "ep")
 3->printOp v fmt (dtDate $ readDT $ field v "dt")
 4->printOp v fmt (readDT $ field v "dt")
 5->printOp v fmt (ElapsedSince (Seconds $ field v "scalar")::ElapsedSince UnixEpoch)
 6->printOp v fmt (ElapsedSince (Seconds $ field v "scalar")::ElapsedSince WindowsEpoch)
 7->printOp v fmt (ElapsedSince (Seconds $ field v "scalar")::ElapsedSince MJDEpoch)
 8->printOp v fmt (let [s,ns]=field v "ep" in ElapsedSinceP (ElapsedSince $ Seconds s) (NanoSeconds ns)::ElapsedSinceP UnixEpoch)
 9->printOp v fmt (let [s,ns]=field v "ep" in ElapsedSinceP (ElapsedSince $ Seconds s) (NanoSeconds ns)::ElapsedSinceP WindowsEpoch)
 _->printOp v fmt (let [s,ns]=field v "ep" in ElapsedSinceP (ElapsedSince $ Seconds s) (NanoSeconds ns)::ElapsedSinceP MJDEpoch)
printOp :: Time t=>Object->TimeFormatString->t->Value
printOp v fmt t=toJSON $ points $ if field v "local" then localTimePrint fmt (localTime (TimezoneOffset $ field v "timezone") t) else timePrint fmt t
{-# NOINLINE printOp #-}
capture action=do
 r<-try $ do x<-action;evaluate $ L.length $ encode x;pure x
 pure $ case r of Right x->x;Left (e::SomeException)->object ["fatal" .= takeWhile (/='\n') (displayException e)]
observe v=do
#ifdef MODEL
 Model.configure v
 result<-capture $ pure $ run v
 trace<-Model.trace
 pure $ object ["output" .= result,"trace" .= trace]
#else
 capture $ pure $ run v
#endif
main=LC.getContents >>= mapM_ (\line->case eitherDecode line of Left e->error e;Right v->observe v >>= LC.putStrLn . encode) . LC.lines
