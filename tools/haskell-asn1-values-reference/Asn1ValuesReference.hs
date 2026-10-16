{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,CPP #-}
#if __GLASGOW_HASKELL__ != 912 || __GLASGOW_HASKELL_PATCHLEVEL1__ != 2 || __GLASGOW_HASKELL_PATCHLEVEL2__ != 0
#error GHC 9.12.2 required
#endif
module Main where
import Data.Aeson hiding (Null)
import qualified Data.Aeson as J (Value(Null))
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as L
import qualified Data.ByteString.Lazy.Char8 as LC
import qualified Data.Vector as V
import qualified Data.Text as T
import Data.Word
import Data.Char(chr,ord)
import Data.ASN1.Types
import Data.ASN1.Types.Lowlevel
import Data.ASN1.BitArray
import Data.ASN1.Stream
import Data.ASN1.Parse
import Data.Hourglass
import GHC.Float(castWord64ToDouble,castDoubleToWord64)
import Control.Exception
import Control.Applicative(empty,(<|>))
import Control.Monad

field v k=convert $ maybe (error "missing field") id (K.lookup k v)
convert v=case fromJSON v of Success a->a;Error e->error e
bytes v=B.pack $ map fromIntegral (convert v::[Int])
jsbytes=toJSON . map (fromIntegral::Word8->Int) . B.unpack
arr=toJSON::[Value]->Value
string=toJSON::String->Value
readConstruction (Array a)=case V.toList a of [String "sequence"]->Sequence;[String "set"]->Set;[String "container",c,t]->Container (toEnum $ convert c) (convert t);_->error "construction"
constructionJSON Sequence=arr [String "sequence"]
constructionJSON Set=arr [String "set"]
constructionJSON (Container c t)=arr [String "container",toJSON $ fromEnum c,toJSON t]
readDateTime (Array a)=case V.toList a of [y,m,d,h,mi,s,ns]->DateTime (Date (convert y) (toEnum $ convert m) (convert d)) (TimeOfDay (Hours $ convert h) (Minutes $ convert mi) (Seconds $ convert s) (NanoSeconds $ convert ns));_->error "dt"
dateTimeJSON (DateTime (Date y m d) (TimeOfDay (Hours h) (Minutes mi) (Seconds s) (NanoSeconds ns)))=toJSON [y,fromEnum m,d,fromIntegral h,fromIntegral mi,fromIntegral s,fromIntegral ns]
readValue (Array a)=case V.toList a of
 [String "boolean",x]->Boolean $ convert x
 [String "integer",x]->IntVal $ read $ convert x
 [String "bits",n,b]->BitString $ BitArray (read $ convert n) (bytes b)
 [String "octets",b]->OctetString $ bytes b
 [String "null"]->Null
 [String "oid",x]->OID $ map read $ convert x
 [String "real",x]->Real $ castWord64ToDouble $ read $ convert x
 [String "enumerated",x]->Enumerated $ read $ convert x
 [String "string",e,b]->ASN1String $ ASN1CharacterString (encodings!!convert e) (bytes b)
 [String "time",ty,dt,tz]->ASN1Time (if convert ty== (0::Int) then TimeUTC else TimeGeneralized) (readDateTime dt) (case tz of J.Null->Nothing;_->Just $ TimezoneOffset $ convert tz)
 [String "other",c,t,b]->Other (toEnum $ convert c) (convert t) (bytes b)
 [String "start",c]->Start $ readConstruction c
 [String "end",c]->End $ readConstruction c
 _->error "value"
encodings=[IA5,UTF8,General,Graphic,Numeric,Printable,VideoTex,Visible,T61,UTF32,Character,BMP]
valueJSON a=case a of
 Boolean b->arr [String "boolean",toJSON b]
 IntVal n->arr [String "integer",string $ show n]
 BitString (BitArray n b)->arr [String "bits",string $ show n,jsbytes b]
 OctetString b->arr [String "octets",jsbytes b]
 Null->arr [String "null"]
 OID xs->arr [String "oid",toJSON $ map show xs]
 Real x->arr [String "real",string $ show $ castDoubleToWord64 x]
 Enumerated n->arr [String "enumerated",string $ show n]
 ASN1String (ASN1CharacterString e b)->arr [String "string",toJSON $ maybe (error "encoding") id $ lookup e (zip encodings [0::Int ..]),jsbytes b]
 ASN1Time ty dt tz->arr [String "time",toJSON (if ty==TimeUTC then 0 else 1::Int),dateTimeJSON dt,toJSON $ fmap timezoneOffsetToMinutes tz]
 Other c t b->arr [String "other",toJSON $ fromEnum c,toJSON t,jsbytes b]
 Start c->arr [String "start",constructionJSON c]
 End c->arr [String "end",constructionJSON c]
valuesJSON=toJSON . map valueJSON
readEvent (String "begin")=ConstructionBegin
readEvent (String "end")=ConstructionEnd
readEvent v=Primitive $ bytes v
reprJSON (v,es)=arr [valueJSON v,toJSON $ map show es]
capture action=do
 r<-try $ do x<-action;evaluate $ L.length $ encode x;pure x
 pure $ case r of Right x->x;Left (e::SomeException)->object ["fatal" .= takeWhile (/='\n') (displayException e)]
left e=object ["left" .= e]
right v=object ["right" .= v]
resultJSON f=either left (right.f)
select v a=case field v "select"::String of "error"->error "selector demanded";"bottomSome"->Just $ error "selected demanded";s->if accepts s a then Just (valueJSON a) else Nothing
accepts "any" _=True
accepts "integer" (IntVal _)=True
accepts "boolean" (Boolean _)=True
accepts "start" (Start _)=True
accepts "null" Null=True
accepts _ _=False
mapper v a=case field v "mapper"::String of "wrap"->object ["mapped" .= a];"constant"->field v "value";"error"->error $ field v "message";_->error "mapper"
data Witness=Witness Integer
instance ASN1Object Witness where
 toASN1 (Witness n)=(IntVal n:)
 fromASN1 (IntVal n:xs)=Right (Witness n,xs)
 fromASN1 _=Left "witness: expected integer"
program v=case field v "op"::String of
 "next"->valueJSON <$> getNext
 "optional"->toJSON <$> getNextMaybe (select v)
 "has"->toJSON <$> hasNext
 "container"->valuesJSON <$> getNextContainer (readConstruction $ field v "kind")
 "containerMaybe"->maybe J.Null valuesJSON <$> getNextContainerMaybe (readConstruction $ field v "kind")
 "on"->onNextContainer (readConstruction $ field v "kind") (program $ field v "program")
 "onMaybe"->toJSON <$> onNextContainerMaybe (readConstruction $ field v "kind") (program $ field v "program")
 "pure"->pure $ field v "value"
 "bottom"->pure $ error $ field v "message"
 "fail"->throwParseError $ field v "message"
 "failPoints"->throwParseError $ map chr $ field v "points"
 "monadFail"->fail $ field v "message"
 "empty"->empty
 "choice"->program (field v "first") <|> program (field v "second")
 "then"->program (field v "first") >> program (field v "second")
 "map"->mapper v <$> program (field v "program")
 "apply"->functions (field v "function") <*> program (field v "program")
 "bind"->program (field v "program") >>= \x->program (field v (if x==field v "equals" then "yes" else "no"))
 "sequence"->toJSON <$> mapM program (field v "programs"::[Object])
 "many"->toJSON <$> getMany (program $ field v "program")
 "object"->(\(Witness n)->string $ show n) <$> getObject
 _->error "op"
functions v=case field v "op"::String of
 "pure"->pure $ mapper v
 "bottom"->pure $ error $ field v "message"
 "fail"->throwParseError $ field v "message"
 "consume"->getNext >> pure (mapper v)
 _->error "function"
run v=case field v "api"::String of
 "values"->pure $ let xs=map readValue (field v "values") in object ["values" .= valuesJSON xs,"shown" .= map show xs,"equal" .= [[a==b|b<-xs]|a<-xs]]
 "timeModels"->do
  let a=readDateTime $ field v "first";b=readDateTime $ field v "second";tz1=TimezoneOffset $ field v "tz1";tz2=TimezoneOffset $ field v "tz2"
      shownDT dt=case dt of DateTime d t->object ["date" .= show d,"time" .= show t,"dateTime" .= show dt]
  shown1<-capture $ pure $ toJSON $ show tz1;shown2<-capture $ pure $ toJSON $ show tz2
  pure $ object ["first" .= shownDT a,"second" .= shownDT b,"date" .= show (compare (dtDate a) (dtDate b)),"time" .= show (compare (dtTime a) (dtTime b)),"dateTime" .= show (compare a b),"equal" .= (a==b),"timezone" .= show (compare tz1 tz2),"timezoneShown" .= [shown1,shown2],"timezoneSeconds" .= object ["first" .= show (timezoneOffsetToSeconds tz1),"second" .= show (timezoneOffsetToSeconds tz2)],"utc" .= show timezone_UTC]
 "stream"->pure $ let xs=map readValue (field v "values");reps=zip xs (map (map readEvent) (field v "events"));(inside,rest)=getConstructedEnd (field v "depth") xs;(ri,rr)=getConstructedEndRepr reps in object ["inside" .= valuesJSON inside,"rest" .= valuesJSON rest,"reprInside" .= map reprJSON ri,"reprRest" .= map reprJSON rr,"shownRepr" .= map show reps]
 "parse"->do
  let xs=map readValue (field v "values");p=program $ field v "program"
  state<-capture $ pure $ resultJSON (\(x,rest)->arr [x,valuesJSON rest]) $ runParseASN1State p xs
  whole<-capture $ pure $ resultJSON id $ runParseASN1 p xs
  pure $ object ["state" .= state,"whole" .= whole]
 "parsePoints"->do
  let xs=map readValue (field v "values");p=program $ field v "program";project f=either (\s->object ["left" .= map ord s]) (right.f)
  state<-capture $ pure $ project (\(x,rest)->arr [x,valuesJSON rest]) $ runParseASN1State p xs
  whole<-capture $ pure $ project id $ runParseASN1 p xs
  pure $ object ["state" .= state,"whole" .= whole]
 "object"->pure $ let n=read (field v "integer");xs=map readValue (field v "values") in object ["written" .= valuesJSON (toASN1 (Witness n) xs),"parsed" .= resultJSON (\(Witness k,rest)->arr [string $ show k,valuesJSON rest]) (fromASN1 xs)]
 _->error "api"
main=LC.getContents >>= mapM_ (\l->case eitherDecode l of Left e->error e;Right v->capture (run v) >>= LC.putStrLn . encode) . LC.lines
