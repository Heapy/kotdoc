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
import Data.X509.AlgorithmIdentifier
import qualified OriginalSignatureTests as U
import Test.QuickCheck hiding (Success)
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

keys=[PubKeyALG_RSA,PubKeyALG_RSAPSS,PubKeyALG_DSA,PubKeyALG_EC,PubKeyALG_X25519,PubKeyALG_X448,PubKeyALG_Ed25519,PubKeyALG_Ed448,PubKeyALG_DH]
hashes=[HashMD2,HashMD5,HashSHA1,HashSHA224,HashSHA256,HashSHA384,HashSHA512]
readKey (Array a)=PubKeyALG_Unknown $ map read $ convert $ V.last a
readKey x=keys!!convert x
keyJSON (PubKeyALG_Unknown o)=arr [String "unknown",toJSON $ map show o]
keyJSON x=toJSON $ maybe (error "key") id $ lookup x $ zip keys [0::Int ..]
readSig (Array a)=case V.toList a of
 [String "with",h,k]->SignatureALG (hashes!!convert h) (readKey k)
 [String "intrinsic",k]->SignatureALG_IntrinsicHash $ readKey k
 [String "unknown",o]->SignatureALG_Unknown $ map read $ convert o
 _->error "sig"
sigJSON s=case s of
 SignatureALG h k->arr [String "with",toJSON $ maybe (error "hash") id $ lookup h $ zip hashes [0::Int ..],keyJSON k]
 SignatureALG_IntrinsicHash k->arr [String "intrinsic",keyJSON k]
 SignatureALG_Unknown o->arr [String "unknown",toJSON $ map show o]
kind x=case x of Boolean _->"boolean";IntVal _->"integer";OID _->"oid";Null->"null";Start _->"start";End _->"end";_->"other"
readValues v k=map readValue $ field v k
capture action=do
 r<-try $ do x<-action;evaluate $ L.length $ encode x;pure x
 pure $ case r of Right x->x;Left (e::SomeException)->object ["fatal" .= takeWhile (/='\n') (displayException e)]
run v=case field v "api"::String of
 "hash"->let h=hashes!!field v "hash" in object ["shown" .= show h,"equal" .= (h==hashes!!field v "other")]
 "key"->let k=readKey $ field v "key" in object ["shown" .= show k,"oid" .= map show (getObjectID k),"equal" .= (k==readKey (field v "other"))]
 "signature"->let s=readSig $ field v "signature" in object ["shown" .= show s,"equal" .= (s==readSig (field v "other"))]
 "to"->let tail=readValues v "tail"++if field v "tailError" then error "tail demanded" else [];out=toASN1 (readSig $ field v "signature") tail;prefix=take (field v "count") out in case field v "part"::String of
  "spine"->toJSON $ length prefix
  "kinds"->toJSON $ map kind prefix
  "shown"->toJSON $ show prefix
  "field"->case out!!field v "index" of OID o->toJSON $ map show o;IntVal n->toJSON $ show n;_->String "other"
  "bytes"->jsbytes $ L.toStrict $ L.take (field v "count") $ encodeASN1 DER out
  _->valuesJSON prefix
 "from"->case fromASN1 (readValues v "values")::Either String (SignatureALG,[ASN1]) of
  Left e->object ["left" .= e]
  Right (s,rest)->case field v "part"::String of
   "tag"->object ["right" .= True]
   "remaining"->object ["remaining" .= valuesJSON rest]
   _->object ["signature" .= sigJSON s,"shown" .= show s,"remaining" .= valuesJSON rest]
 "property"->let s=readSig $ field v "signature" in object ["property" .= U.property_unmarshall_marshall_id s,"values" .= valuesJSON (toASN1 s []),"encoded" .= jsbytes (encodeASN1' DER $ toASN1 s [])]
 _->error "api"
generated n=forM_ [0..n-1] $ \i->do
 let s=unGen arbitrary (mkQCGen $ 1095978545+i) (i `mod` 100)::SignatureALG
 LC.putStrLn $ encode $ object ["group" .= ("original-signature-generator-and-roundtrip-property"::String),"request" .= object ["api" .= ("property"::String),"signature" .= sigJSON s]]
main=do
 args<-getArgs
 case args of
  ["--generate-tests",n]->generated $ read n
  ["--original-test"]->do
   r<-quickCheckWithResult stdArgs {maxSuccess=1000,replay=Just(mkQCGen 1095978545,0)} (U.property_unmarshall_marshall_id::SignatureALG->Bool)
   unless (isSuccess r) exitFailure
  _->LC.getContents >>= mapM_ (\line->case eitherDecode line of Left e->error e;Right v->capture (pure $ run v) >>= LC.putStrLn . encode) . LC.lines
