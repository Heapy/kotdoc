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
import Data.X509.DistinguishedName
import Data.X509.Internal
import qualified OriginalDnTests as U
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
readDn (Array a)=DistinguishedName $ map entry $ V.toList a
 where entry (Array e)=let [o,en,b]=V.toList e in (readOid o,ASN1CharacterString (encodings!!convert en) (bytes b))
dnJSON (DistinguishedName es)=toJSON $ map (\(o,ASN1CharacterString en b)->arr [toJSON $ map show o,toJSON $ maybe (error "enc") id $ lookup en (zip encodings [0::Int ..]),jsbytes b]) es
csJSON (ASN1CharacterString en b)=arr [toJSON $ maybe (error "enc") id $ lookup en (zip encodings [0::Int ..]),jsbytes b]
elements=[DnCommonName,DnCountry,DnOrganization,DnOrganizationUnit,DnEmailAddress]
kind x=case x of Boolean _->"boolean";IntVal _->"integer";OID _->"oid";ASN1String _->"string";Null->"null";Start _->"start";End _->"end";_->"other"
readValues v k=map readValue $ field v k
capture action=do
 r<-try $ do x<-action;evaluate $ L.length $ encode x;pure x
 pure $ case r of Right x->x;Left (e::SomeException)->object ["fatal" .= takeWhile (/='\n') (displayException e)]
fromDn inner vs=if inner then fmap (\(DistinguishedNameInner dn,rest)->(dn,rest)) (fromASN1 vs) else fromASN1 vs
run v=case field v "api"::String of
 "element"->let e=elements!!field v "element" in object ["shown" .= show e,"oid" .= map show (getObjectID e),"equal" .= (e==elements!!field v "other")]
 "value"->let a=readDn $ field v "value";b=readDn $ field v "other" in object ["shown" .= show a,"innerShown" .= show (DistinguishedNameInner a),"equal" .= (a==b),"innerEqual" .= (DistinguishedNameInner a==DistinguishedNameInner b),"ordering" .= show (compare a b),"append" .= dnJSON (a<>b),"emptyLeft" .= (mempty<>a==a),"emptyRight" .= (a<>mempty==a)]
 "lookup"->toJSON $ fmap csJSON $ getDnElement (elements!!field v "element") (readDn $ field v "value")
 "to"->let dn=readDn $ field v "value";tail=readValues v "tail"++if field v "tailError" then error "tail demanded" else [];out=if field v "inner" then toASN1 (DistinguishedNameInner dn) tail else toASN1 dn tail;prefix=take (field v "count") out in case field v "part"::String of
  "spine"->toJSON $ length prefix
  "kinds"->toJSON $ map kind prefix
  "shown"->toJSON $ show prefix
  "field"->case out!!field v "index" of OID o->toJSON $ map show o;ASN1String cs->csJSON cs;_->String "other"
  "bytes"->jsbytes $ L.toStrict $ L.take (field v "count") $ encodeASN1 DER out
  _->valuesJSON prefix
 "from"->case fromDn (field v "inner") (readValues v "values") of
  Left e->if field v "part"==("tag"::String) then object ["left" .= True] else object ["left" .= e]
  Right (dn,rest)->case field v "part"::String of
   "tag"->object ["right" .= True]
   "remaining"->object ["remaining" .= valuesJSON rest]
   "lookup"->object ["lookup" .= fmap csJSON (getDnElement (elements!!field v "element") dn)]
   _->object ["value" .= dnJSON dn,"shown" .= show dn,"remaining" .= valuesJSON rest]
 "container"->let out=asn1Container (readConstruction $ field v "kind") (readValues v "values"++if field v "tailError" then error "tail demanded" else []) in valuesJSON $ take (field v "count") out
 "property"->let dn=readDn $ field v "value" in object ["property" .= U.property_unmarshall_marshall_id dn,"values" .= valuesJSON (toASN1 dn [])]
 _->error "api"
generated n=forM_ [0..n-1] $ \i->do
 let dn=unGen arbitrary (mkQCGen $ 1145978545+i) (i `mod` 100)::DistinguishedName
 LC.putStrLn $ encode $ object ["group" .= ("original-DN-generator-and-generic-roundtrip-property"::String),"request" .= object ["api" .= ("property"::String),"value" .= dnJSON dn]]
main=do
 args<-getArgs
 case args of
  ["--generate-tests",n]->generated $ read n
  ["--original-test"]->do
   r<-quickCheckWithResult stdArgs {maxSuccess=1000,replay=Just(mkQCGen 1145978545,0)} (U.property_unmarshall_marshall_id::DistinguishedName->Bool)
   unless (isSuccess r) exitFailure
  _->LC.getContents >>= mapM_ (\line->case eitherDecode line of Left e->error e;Right v->capture (pure $ run v) >>= LC.putStrLn . encode) . LC.lines
