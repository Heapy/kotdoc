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
import qualified Time.Calendar as C
import qualified Time.Diff as D
import qualified Time.Internal as I
import qualified Time.Utils as U
import Time.Epoch
import Time.System
import Data.Ratio
import Data.Int
import Foreign.C.Types(CTime(..))
import Control.Exception
#ifdef MODEL
import qualified Model
#endif

field v k=case fromJSON (maybe (error "missing field") id (K.lookup k v)) of Success a->a;Error e->error e
readDT [y,m,d,h,mi,s,ns]=DateTime (Date (fromIntegral y) (toEnum $ fromIntegral m) (fromIntegral d)) (TimeOfDay (Hours h) (Minutes mi) (Seconds s) (NanoSeconds ns))
readDT _=error "dateTime"
dtJSON (DateTime (Date y m d) (TimeOfDay (Hours h) (Minutes mi) (Seconds s) (NanoSeconds ns)))=toJSON [fromIntegral y,fromIntegral $ fromEnum m,fromIntegral d,h,mi,s,ns]
dateJSON (Date y m d)=toJSON [y,fromEnum m,d]
todJSON (TimeOfDay (Hours h) (Minutes mi) (Seconds s) (NanoSeconds ns))=toJSON [h,mi,s,ns]
readEP [s,ns]=ElapsedP (Elapsed $ Seconds s) (NanoSeconds ns)
readEP _=error "ep"
epJSON (ElapsedP (Elapsed (Seconds s)) (NanoSeconds ns))=toJSON [s,ns]
duration [h,m,s,ns]=Duration (Hours h) (Minutes m) (Seconds s) (NanoSeconds ns)
duration _=error "duration"
durationJSON (Duration (Hours h) (Minutes m) (Seconds s) (NanoSeconds ns))=toJSON [h,m,s,ns]
period [y,m,d]=Period y m d
period _=error "period"
periodJSON (Period y m d)=toJSON [y,m,d]
ratioJSON r=toJSON [show $ numerator r,show $ denominator r]
seconds (Seconds s)=s
nanos (NanoSeconds n)=n
showJSON x=toJSON $ show x
run v=case field v "api"::String of
 "elapsed"->pure $ let a=readEP $ field v "a";b=readEP $ field v "b";[s,ns]=field v "a";r=read (field v "numerator") % read (field v "denominator");(rs,rns)=fromRationalSecondsP r;i=read (field v "integer") in object ["a" .= epJSON a,"b" .= epJSON b,"shown" .= map show [a,b],"equal" .= (a==b),"ordering" .= show (compare a b),"normalized" .= epJSON (mkElapsedP (Seconds s) (NanoSeconds ns)),"plus" .= epJSON (a+b),"minus" .= epJSON (a-b),"times" .= epJSON (a*b),"negate" .= epJSON (negate a),"abs" .= epJSON (abs a),"signum" .= epJSON (signum a),"rational" .= ratioJSON (toRational a),"rationalSeconds" .= [seconds rs,nanos rns],"rationalElapsed" .= epJSON (fromRationalElapsedP r),"integer" .= epJSON (fromInteger i)]
 "duration"->pure $ let a=duration $ field v "a";b=duration $ field v "b";p=period $ field v "p";q=period $ field v "q";(s,ns)=D.durationFlatten a;(fromS,rest)=fromSeconds (Seconds $ field v "seconds")::(Duration,Seconds) in object ["shown" .= map show [a,b],"ordering" .= show (compare a b),"equal" .= (a==b),"flatten" .= [seconds s,nanos ns],"normalized" .= durationJSON (D.durationNormalize a),"sum" .= durationJSON (a<>b),"empty" .= durationJSON mempty,"toSeconds" .= seconds (toSeconds a),"fromSeconds" .= toJSON [durationJSON fromS,toJSON $ seconds rest],"periodShown" .= map show [p,q],"periodOrdering" .= show (compare p q),"periodEqual" .= (p==q),"periodSum" .= periodJSON (p<>q),"periodEmpty" .= periodJSON mempty]
 "interval"->pure $ let n=field v "value";s=Seconds $ field v "seconds";f h=(show $ fst h,seconds $ snd h) in object ["toSeconds" .= [seconds $ toSeconds (NanoSeconds n),n,seconds $ toSeconds (Minutes n),seconds $ toSeconds (Hours n)],"fromSeconds" .= [f (fromSeconds s::(NanoSeconds,Seconds)),f (fromSeconds s::(Seconds,Seconds)),f (fromSeconds s::(Minutes,Seconds)),f (fromSeconds s::(Hours,Seconds))]]
 "calendar"->pure $ let dt@(DateTime d@(Date y m _) tod)=readDT $ field v "dt";p=period $ field v "period";r=D.dateAddPeriod d p in object ["leap" .= isLeapYear y,"days" .= daysInMonth y m,"yearDay" .= getDayOfTheYear d,"weekDay" .= show (getWeekDay d),"dateEpoch" .= show (C.dateToUnixEpoch d),"timeSeconds" .= show (C.todToSeconds tod),"dateTimeEpoch" .= show (C.dateTimeToUnixEpoch dt),"added" .= dateJSON r,"addedShown" .= show r]
 "padding"->pure $ let n=read (field v "value")::Integer;w=field v "width" in toJSON [U.pad2 n,U.pad3 n,U.pad4 n,U.padN w n]
 "pad"->pure $ let n=read (field v "value")::Integer;w=field v "width" in toJSON $ case field v "function"::Int of 0->U.pad2 n;1->U.pad3 n;2->U.pad4 n;_->U.padN w n
 "timezone"->pure $ let n=field v "value";a=TimezoneMinutes n in object ["shown" .= show a,"offset" .= timezoneOffset a,"name" .= timezoneName a,"utc" .= [show UTC,timezoneName UTC],"utcOffset" .= timezoneOffset UTC]
 "native"->case field v "action"::String of
  "epoch"->pure $ dtJSON $ I.dateTimeFromUnixEpoch $ Elapsed $ Seconds $ field v "seconds"
  "epochP"->pure $ dtJSON $ I.dateTimeFromUnixEpochP $ readEP $ field v "elapsed"
  "timezone"->toJSON . timezoneOffsetToMinutes <$> I.systemGetTimezone
  "now"->showJSON <$> I.systemGetElapsed
  "nowP"->epJSON <$> I.systemGetElapsedP
  _->error "native"
 "system"->case field v "action"::String of
  "current"->showJSON <$> timeCurrent
  "currentP"->epJSON <$> timeCurrentP
  "date"->dtJSON <$> dateCurrent
  "timezone"->toJSON . timezoneOffsetToMinutes <$> timezoneCurrent
  "local"->showJSON <$> localDateCurrent
  "localAt"->showJSON <$> localDateCurrentAt (TimezoneOffset $ field v "timezone")
  _->error "system"
 "direct"->pure $ let dt@(DateTime d _)=readDT $ field v "dt" in case field v "action"::String of
  "dt"->dtJSON $ timeGetDateTimeOfDay dt
  "date"->dateJSON $ timeGetDate dt
  "tod"->todJSON $ timeGetTimeOfDay dt
  "identity"->dtJSON (timeConvert dt::DateTime)
  "dateDt"->dtJSON $ timeGetDateTimeOfDay d
  "dateIdentity"->dateJSON (timeConvert d::Date)
  _->error "direct"
 "time"->pure $ dispatch v
 "epoch"->pure $ case field v "epoch"::Int of 0->epochOp UnixEpoch v;1->epochOp WindowsEpoch v;_->epochOp MJDEpoch v
 _->error "api"

dispatch v=case field v "kind"::Int of
 0->timeOp v (CTime $ field v "scalar")
 1->timeOp v (Elapsed $ Seconds $ field v "scalar")
 2->timeOp v (readEP $ field v "ep")
 3->timeOp v (dtDate $ readDT $ field v "dt")
 4->timeOp v (readDT $ field v "dt")
 5->timeOp v (ElapsedSince (Seconds $ field v "scalar")::ElapsedSince UnixEpoch)
 6->timeOp v (ElapsedSince (Seconds $ field v "scalar")::ElapsedSince WindowsEpoch)
 7->timeOp v (ElapsedSince (Seconds $ field v "scalar")::ElapsedSince MJDEpoch)
 8->timeOp v (let [s,ns]=field v "ep" in ElapsedSinceP (ElapsedSince $ Seconds s) (NanoSeconds ns)::ElapsedSinceP UnixEpoch)
 9->timeOp v (let [s,ns]=field v "ep" in ElapsedSinceP (ElapsedSince $ Seconds s) (NanoSeconds ns)::ElapsedSinceP WindowsEpoch)
 _->timeOp v (let [s,ns]=field v "ep" in ElapsedSinceP (ElapsedSince $ Seconds s) (NanoSeconds ns)::ElapsedSinceP MJDEpoch)
timeOp :: forall t. (Time t,Ord t,Show t) => Object -> t -> Value
timeOp v t=let tz=TimezoneOffset $ field v "timezone";lt=localTime tz t;target=TimezoneOffset $ field v "targetTimezone" in case field v "action"::String of
 "ep"->epJSON $ timeGetElapsedP t
 "e"->showJSON $ timeGetElapsed t
 "ns"->showJSON $ timeGetNanoSeconds t
 "diff"->showJSON $ timeDiff t (readEP $ field v "second")
 "diffP"->let (s,ns)=timeDiffP t (readEP $ field v "second") in toJSON [seconds s,nanos ns]
 "dt"->dtJSON $ timeGetDateTimeOfDay t
 "date"->dateJSON $ timeGetDate t
 "tod"->todJSON $ timeGetTimeOfDay t
 "convert"->convertTo (field v "target") t
 "add"->showJSON $ timeAdd t (duration $ field v "duration")
 "localGlobal"->showJSON $ localTimeToGlobal lt
 "localSet"->showJSON $ localTimeSetTimezone target lt
 "localConvert"->showJSON (localTimeConvert lt::LocalTime ElapsedP)
 "localShow"->showJSON lt
 "localCompare"->showJSON $ compare lt (localTime target t)
 "localEqual"->toJSON $ lt==localTime target t
 _->error "action"
{-# NOINLINE timeOp #-}
convertTo :: Timeable t=>Int->t->Value
convertTo target t=case target of
 0->showJSON (timeConvert t::CTime)
 1->showJSON (timeConvert t::Elapsed)
 2->showJSON (timeConvert t::ElapsedP)
 3->showJSON (timeConvert t::Date)
 4->showJSON (timeConvert t::DateTime)
 5->showJSON (timeConvert t::ElapsedSince UnixEpoch)
 6->showJSON (timeConvert t::ElapsedSince WindowsEpoch)
 7->showJSON (timeConvert t::ElapsedSince MJDEpoch)
 8->showJSON (timeConvert t::ElapsedSinceP UnixEpoch)
 9->showJSON (timeConvert t::ElapsedSinceP WindowsEpoch)
 _->showJSON (timeConvert t::ElapsedSinceP MJDEpoch)
{-# NOINLINE convertTo #-}
epochOp :: forall e. (Epoch e,Show e)=>e->Object->Value
epochOp epoch v=let [s,ns]=field v "a";[s2,ns2]=field v "b";a=ElapsedSinceP (ElapsedSince $ Seconds s) (NanoSeconds ns)::ElapsedSinceP e;b=ElapsedSinceP (ElapsedSince $ Seconds s2) (NanoSeconds ns2)::ElapsedSinceP e;proj (ElapsedSinceP (ElapsedSince (Seconds x)) (NanoSeconds y))=toJSON [x,y] in object ["name" .= epochName epoch,"diff" .= show (epochDiffToUnix epoch),"shownEpoch" .= show epoch,"shown" .= map show [a,b],"plus" .= proj (a+b),"minus" .= proj (a-b),"times" .= proj (a*b),"negate" .= proj (negate a),"abs" .= proj (abs a),"signum" .= proj (signum a),"ordering" .= show (compare a b),"equal" .= (a==b),"rational" .= ratioJSON (toRational a),"integer" .= proj (fromInteger (read $ field v "integer")::ElapsedSinceP e),"unix" .= show (timeConvert a::ElapsedSinceP UnixEpoch),"windows" .= show (timeConvert a::ElapsedSinceP WindowsEpoch),"mjd" .= show (timeConvert a::ElapsedSinceP MJDEpoch),"elapsed" .= epJSON (timeGetElapsedP a)]

capture action=do
 r<-try $ do x<-action;evaluate $ L.length $ encode x;pure x
 pure $ case r of Right x->x;Left (e::SomeException)->object ["fatal" .= takeWhile (/='\n') (displayException e)]
observe v=do
#ifdef MODEL
 Model.configure v
 result<-capture $ run v
 trace<-Model.trace
 pure $ object ["output" .= result,"trace" .= trace]
#else
 capture $ run v
#endif
main=LC.getContents >>= mapM_ (\line->case eitherDecode line of Left e->error e;Right v->observe v >>= LC.putStrLn . encode) . LC.lines
