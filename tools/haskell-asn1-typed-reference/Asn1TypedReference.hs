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
import qualified UpstreamTests as U
import Test.QuickCheck(Gen,arbitrary)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import qualified GHC.Num.Backend as Backend
import qualified GHC.Num.Backend.Native as NativeBackend
import GHC.Num.Integer(Integer(..))
import GHC.Exts(Double(D#),Int(I#))
import System.Environment(getArgs)
#ifdef MODEL
import qualified Model
#endif

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
lengthJSON l=case l of LenShort n->arr [String "short",toJSON n];LenLong n v->arr [String "long",toJSON n,toJSON v];LenIndefinite->arr [String "indefinite"]
readLength (Array a)=case V.toList a of [String "short",v]->LenShort $ convert v;[String "long",n,v]->LenLong (convert n) (convert v);_->LenIndefinite
readLength _=error "length"
headerJSON (ASN1Header c t pc l)=arr [toJSON $ fromEnum c,toJSON t,toJSON pc,lengthJSON l]
readHeader (Array a)=case V.toList a of [c,t,pc,l]->ASN1Header (toEnum $ convert c) (convert t) (convert pc) (readLength l);_->error "header"
readHeader _=error "header"
eventJSON e=case e of Header h->arr [String "header",headerJSON h];Primitive b->arr [String "primitive",jsbytes b];ConstructionBegin->String "begin";ConstructionEnd->String "end"
readEvent (String "begin")=ConstructionBegin
readEvent (String "end")=ConstructionEnd
readEvent (Array a)=case V.toList a of [String "header",h]->Header $ readHeader h;[String "primitive",b]->Primitive $ bytes b;_->error "event"
readEvent _=error "event"
eventsJSON=toJSON . map eventJSON
reprJSON (v,es)=arr [valueJSON v,eventsJSON es]
left e=object ["left" .= show e]
right x=object ["right" .= x]
result f=either left (right . f)
capture action=do
 r<-try $ do x<-action;evaluate $ L.length $ encode x;pure x
 pure $ case r of Right x->x;Left (e::SomeException)->object ["fatal" .= takeWhile (/='\n') (displayException e)]
readValues v=map readValue $ field v "values"
nativeFloat m e@(I# n)=case m of IS _->encodeFloat m e;IP b->D# (NativeBackend.bignat_encode_double b n);IN b->negate $ D# (NativeBackend.bignat_encode_double b n)
run v=case field v "api"::String of
 "get"->result (\x->object ["value" .= valueJSON x,"shown" .= show x]) $ case field v "method"::String of
  "boolean"->P.getBoolean (field v "der") b
  "integer"->P.getInteger b;"double"->P.getDouble b;"bits"->P.getBitString b;"octets"->P.getOctetString b;"null"->P.getNull b;"oid"->P.getOID b
  "time"->P.getTime (if field v "type"==(0::Int) then TimeUTC else TimeGeneralized) b
  _->error "get"
 "decodePrimitive"->result (\x->object ["value" .= valueJSON x,"shown" .= show x]) $ P.decodePrimitive (readHeader $ field v "header") b
 "put"->jsbytes $ case field v "method"::String of
  "integer"->P.putInteger $ read $ field v "integer"
  "double"->P.putDouble $ castWord64ToDouble $ read $ field v "bits"
  "bits"->P.putBitString $ BitArray (read $ field v "length") b
  "string"->P.putString b
  "oid"->P.putOID $ map read $ field v "oid"
  "time"->P.putTime (if field v "type"==(0::Int) then TimeUTC else TimeGeneralized) (readDateTime $ field v "dt") (fmap TimezoneOffset $ field v "timezone")
  _->error "put"
 "float"->let m=read $ field v "mantissa";e=field v "exponent";d=if field v "native" then nativeFloat m e else encodeFloat m e in object ["bits" .= show (castDoubleToWord64 d),"shown" .= show d]
 "header"->let h=P.encodeHeader (field v "constructed") (readLength $ field v "length") (readValue $ field v "value") in object ["header" .= headerJSON h,"shown" .= show h]
 "smallest"->lengthJSON $ P.mkSmallestLength $ field v "length"
 "encode"->let (n,es)=case field v "method"::String of "primitive"->P.encodePrimitive a;"one"->P.encodeOne a;"constructed"->P.encodeConstructed a (readValues v);_->P.encodeList $ readValues v in case field v "part"::String of
  "length"->toJSON n
  "events"->eventsJSON es
  "prefix"->eventsJSON $ take (field v "count") es
  _->object ["length" .= n,"events" .= eventsJSON es]
 "binary"->let chunks=L.fromChunks $ map bytes $ field v "chunks";der=field v "der";part=field v "part" in
  if field v "repr" then observeResult reprJSON part (if der then decodeASN1Repr DER chunks else decodeASN1Repr BER chunks) else observeResult valueJSON part (if der then decodeASN1 DER chunks else decodeASN1 BER chunks)
 "write"->let out=encodeASN1 DER $ readValues v in case field v "part"::String of
  "chunks"->toJSON $ map jsbytes $ L.toChunks out
  "prefix"->jsbytes $ L.toStrict $ L.take (field v "count") out
  _->jsbytes $ L.toStrict out
 "property"->property v
 _->error "api"
 where
 b=bytes $ field v "bytes"
 a=readValue $ field v "value"
 observeResult f part r=case r of Left e->left e;Right xs->case part::String of "tag"->toJSON True;"prefix"->right $ toJSON $ map f $ take (field v "count") xs;_->right $ toJSON $ map f xs
property v=case field v "kind"::String of
 "header"->let h=readHeader $ field v "header" in object ["property" .= U.prop_header_marshalling_id h,"encoded" .= jsbytes (putHeader h)]
 "event"->let es=map readEvent $ field v "events" in object ["property" .= U.prop_event_marshalling_id (U.ASN1Events es),"encoded" .= jsbytes (toByteString es)]
 "der"->let vs=readValues v in object ["property" .= U.prop_asn1_der_marshalling_id vs,"encoded" .= jsbytes (encodeASN1' DER vs),"decoded" .= result valuesJSON (decodeASN1 DER $ encodeASN1 DER vs)]
 "real"->let d=castWord64ToDouble $ read $ field v "bits" in object ["property" .= U.prop_real_der_marshalling_id d,"encoded" .= jsbytes (encodeASN1' DER [Real d]),"decoded" .= result valuesJSON (decodeASN1 DER $ encodeASN1 DER [Real d])]
 _->let n=read $ field v "integer";d=fromInteger n in object ["property" .= U.prop_integral_real_der_marshalling_id n,"encoded" .= jsbytes (encodeASN1' DER [Real d]),"decoded" .= result valuesJSON (decodeASN1 DER $ encodeASN1 DER [Real d])]
generated n=forM_ [0..n-1] $ \i->do
 let gen :: Gen a->a
     gen g=unGen g (mkQCGen $ 1095978545+i) (i `mod` 100)
     header=gen arbitrary::ASN1Header
     U.ASN1Events es=gen arbitrary
     vs=gen arbitrary::[ASN1]
     d=gen arbitrary::Double
     integer=gen arbitrary::Integer
     emit kind xs=LC.putStrLn $ encode $ object ["group" .= ("original-Tests.hs-"++kind++"-generator-and-property"),"model" .= ("native"::String),"request" .= object (["api" .= ("property"::String),"kind" .= kind]++xs)]
 emit "header" ["header" .= headerJSON header]
 emit "event" ["events" .= eventsJSON es]
 emit "der" ["values" .= valuesJSON vs]
 emit "real" ["bits" .= show (castDoubleToWord64 d)]
 emit "integralReal" ["integer" .= show integer]
observe v=do
#ifdef MODEL
 Model.configure v
 output<-capture $ pure $ run v
 trace<-Model.trace
 pure $ object ["output" .= output,"trace" .= trace]
#else
 capture $ pure $ run v
#endif
main=do
 unless (Backend.backendName=="gmp") $ error "Pinned GMP backend required"
 args<-getArgs
 case args of
  ["--generate-tests",n]->generated $ read n
  _->LC.getContents >>= mapM_ (\line->case eitherDecode line of Left e->error e;Right v->observe v >>= LC.putStrLn . encode) . LC.lines
