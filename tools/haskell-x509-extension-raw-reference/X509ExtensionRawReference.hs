{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,CPP,MagicHash #-}
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
import Data.Hourglass
import GHC.Float(castWord64ToDouble,castDoubleToWord64)
import Control.Exception
import Control.Monad
import qualified Data.ASN1.Prim as P
import Data.ASN1.BinaryEncoding
import Data.ASN1.Encoding
import Data.ASN1.BinaryEncoding.Parse
import Data.ASN1.BinaryEncoding.Writer
import Data.ASN1.Serialize
import Data.ASN1.Get
import Data.X509.ExtensionRaw
import Data.X509.Ext
import Data.X509.Internal
import qualified OriginalExtensionsTests as U
import Test.QuickCheck hiding (Success,elements)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import System.Environment(getArgs)
import System.Exit(exitFailure)
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
 [String "oidError"]->OID $ error "oid demanded"
 [String "integerError"]->IntVal $ error "integer demanded"
 [String "octetsError"]->OctetString $ error "content demanded"
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


readOid J.Null=error "oid demanded"
readOid o=map read $ convert o
readRaw (Array a)=let [o,c,b]=V.toList a in ExtensionRaw (readOid o) (case c of J.Null->error "critical demanded";_->convert c) (case b of J.Null->error "content demanded";_->bytes b)
rawJSON (ExtensionRaw o c b)=arr [toJSON $ map show o,toJSON c,jsbytes b]
readExts J.Null=Extensions Nothing
readExts (String "listError")=Extensions $ Just $ error "extension list demanded"
readExts (Array a)=Extensions $ Just $ map readRaw $ V.toList a
extsJSON (Extensions xs)=toJSON $ fmap (map rawJSON) xs
present (Extensions xs)=case xs of Nothing->False;Just _->True
kind x=case x of Boolean _->"boolean";IntVal _->"integer";OID _->"oid";OctetString _->"octets";ASN1String _->"string";Null->"null";Start _->"start";End _->"end";_->"other"
readValues v k=map readValue $ field v k
capture action=do
 r<-try $ do x<-action;evaluate $ B.length $ L.toStrict $ encode x;pure x
 -- Forcing an exception message may itself throw (e.g. a lazy OID in error).
 case r of Right x->pure x;Left (e::SomeException)->capture $ pure $ object ["fatal" .= takeWhile (/='\n') (displayException e)]
run v=case field v "api"::String of
 "rawEqual"->toJSON $ readRaw (field v "value")==readRaw (field v "other")
 "setEqual"->toJSON $ readExts (field v "value")==readExts (field v "other")
 "rawValue"->let a=readRaw $ field v "value";b=readRaw $ field v "other" in object ["shown" .= show a,"equal" .= (a==b)]
 "setValue"->let a=readExts $ field v "value";b=readExts $ field v "other" in object ["shown" .= show a,"equal" .= (a==b),"present" .= present a]
 "field"->let ExtensionRaw o c b=readRaw $ field v "value" in case field v "part"::String of "oid"->toJSON $ map show o;"critical"->toJSON c;_->jsbytes b
 "to"->let tail=readValues v "tail"++if field v "tailError" then error "tail demanded" else [];out=if field v "set" then toASN1 (readExts $ field v "value") tail else toASN1 (readRaw $ field v "value") tail;prefix=take (field v "count") out in case field v "part"::String of
  "spine"->toJSON $ length prefix
  "kinds"->toJSON $ map kind prefix
  "shown"->toJSON $ show prefix
  "field"->case out!!field v "index" of OID o->toJSON $ map show o;Boolean c->toJSON c;OctetString b->jsbytes b;_->String "other"
  "bytes"->jsbytes $ L.toStrict $ L.take (field v "count") $ encodeASN1 DER out
  _->valuesJSON prefix
 "from"->if field v "set" then observeFrom extsJSON show (fromASN1 $ readValues v "values"::Either String (Extensions,[ASN1])) else observeFrom rawJSON show (fromASN1 $ readValues v "values"::Either String (ExtensionRaw,[ASN1]))
 "try"->case tryExtRawASN1 $ readRaw $ field v "value" of
  Left e->if field v "part"==("tag"::String) then object ["left" .= True] else object ["left" .= e]
  Right xs->if field v "part"==("tag"::String) then object ["right" .= True] else object ["right" .= observeValues xs]
 "unsafe"->observeValues $ extRawASN1 $ readRaw $ field v "value"
 "presence"->toJSON $ present $ readExts $ field v "value"
 "property"->let exts=readExts $ field v "value" in object ["property" .= U.property_unmarshall_marshall_id exts,"values" .= valuesJSON (toASN1 exts []),"encoded" .= jsbytes (encodeASN1' DER $ toASN1 exts [])]
 _->error "api"
 where
  observeValues xs=case field v "part"::String of
   "spine"->toJSON $ length $ take (field v "count") xs
   _->toJSON $ map show $ take (field v "count") xs
  observeFrom json shown r=case r of
   Left e->if field v "part"==("tag"::String) then object ["left" .= True] else object ["left" .= e]
   Right (a,rest)->case field v "part"::String of
    "tag"->object ["right" .= True]
    "remaining"->object ["remaining" .= valuesJSON rest]
    _->object ["value" .= json a,"shown" .= shown a,"remaining" .= valuesJSON rest]
generated n=forM_ [0..n-1] $ \i->do
 let exts=unGen arbitrary (mkQCGen $ 1245978545+i) (i `mod` 100)::Extensions
 LC.putStrLn $ encode $ object ["group" .= ("original-Extensions-generator-and-roundtrip-property"::String),"request" .= object ["api" .= ("property"::String),"value" .= extsJSON exts]]
main=do
 args<-getArgs
 case args of
  ["--generate-tests",n]->generated $ read n
  ["--original-test"]->do
   r<-quickCheckWithResult stdArgs {maxSuccess=1000,replay=Just(mkQCGen 1245978545,0)} (U.property_unmarshall_marshall_id::Extensions->Bool)
   unless (isSuccess r) exitFailure
  _->LC.getContents >>= mapM_ (\line->case eitherDecode line of Left e->error e;Right v->capture (pure $ run v) >>= LC.putStrLn . encode) . LC.lines
