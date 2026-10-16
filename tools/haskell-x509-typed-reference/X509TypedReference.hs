{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,CPP,MagicHash,RankNTypes,FlexibleContexts #-}
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
import Data.Proxy
import Data.X509.DistinguishedName
import Data.X509.ExtensionRaw
import Data.X509.Ext
import Data.X509.Internal
import qualified OriginalTypedExtensionTests as U
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
 [String "bitsError"]->BitString $ error "bits demanded"
 [String "stringError"]->ASN1String $ error "string demanded"
 [String "otherError",c,t]->Other (toEnum $ convert c) (convert t) $ error "content demanded"
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
readData J.Null=error "content demanded"
readData b=bytes b
readRaw (Array a)=let [o,c,b]=V.toList a in ExtensionRaw (readOid o) (case c of J.Null->error "critical demanded";_->convert c) (readData b)
rawJSON (ExtensionRaw o c b)=arr [toJSON $ map show o,toJSON c,jsbytes b]
readExts J.Null=Extensions Nothing
readExts (String "listError")=Extensions $ Just $ error "extension list demanded"
readExts (Array a)=Extensions $ Just $ map readRaw $ V.toList a
readDn (Array a)=DistinguishedName $ map entry $ V.toList a
 where entry (Array e)=let [o,en,b]=V.toList e in (readOid o,ASN1CharacterString (encodings!!convert en) (readData b))
dnJSON (DistinguishedName es)=toJSON $ map (\(o,ASN1CharacterString en b)->arr [toJSON $ map show o,toJSON $ maybe (error "enc") id $ lookup en (zip encodings [0::Int ..]),jsbytes b]) es
points=toJSON . map ord
readText=map chr . convert
readName (Array a)=case V.toList a of
 [String "rfc822",s]->AltNameRFC822 $ readText s
 [String "dns",s]->AltNameDNS $ readText s
 [String "dn",s]->AltNameDN $ readDn s
 [String "uri",s]->AltNameURI $ readText s
 [String "ip",s]->AltNameIP $ readData s
 [String "xmpp",s]->AltNameXMPP $ readText s
 [String "srv",s]->AltNameDNSSRV $ readText s
 _->error "name"
nameJSON x=case x of AltNameRFC822 s->arr[String "rfc822",points s];AltNameDNS s->arr[String "dns",points s];AltNameDN d->arr[String "dn",dnJSON d];AltNameURI s->arr[String "uri",points s];AltNameIP b->arr[String "ip",jsbytes b];AltNameXMPP s->arr[String "xmpp",points s];AltNameDNSSRV s->arr[String "srv",points s]
purposes=[KeyUsagePurpose_ServerAuth,KeyUsagePurpose_ClientAuth,KeyUsagePurpose_CodeSigning,KeyUsagePurpose_EmailProtection,KeyUsagePurpose_TimeStamping,KeyUsagePurpose_OCSPSigning]
readPurpose (Array a)=KeyUsagePurpose_Unknown $ readOid $ V.last a
readPurpose n=purposes!!convert n
purposeJSON (KeyUsagePurpose_Unknown o)=arr[String "unknown",toJSON $ map show o]
purposeJSON p=toJSON $ maybe (error "purpose") id $ lookup p $ zip purposes [0::Int ..]
readSub (Array a)=let [base,mi,ma]=V.toList a in GeneralSubtree (readName base) (read $ convert mi) (case ma of J.Null->Nothing;_->Just $ read $ convert ma)
subJSON (GeneralSubtree b mi ma)=arr[nameJSON b,toJSON $ show mi,toJSON $ fmap show ma]
readPoint (Array a)=case V.toList a of [String "full",names]->DistributionPointFullName $ map readName $ convert names;[String "relative",dn]->DistributionNameRelative $ readDn dn;_->error "point"
pointJSON (DistributionPointFullName names)=arr[String "full",toJSON $ map nameJSON names]
pointJSON (DistributionNameRelative d)=arr[String "relative",dnJSON d]
class (Extension a,Show a,Eq a) => Observed a where
 readExtension :: Value->a
 extensionJSON :: a->Value
 ordering :: a->a->Value
 ordering _ _=J.Null
 payload :: String->Int->a->Value
 payload _ _ _=J.Null
instance Observed ExtBasicConstraints where
 readExtension (Array a)=let [b,n]=V.toList a in ExtBasicConstraints (convert b) (case n of J.Null->Nothing;_->Just $ read $ convert n)
 extensionJSON (ExtBasicConstraints b n)=arr[toJSON b,toJSON $ fmap show n]
instance Observed ExtKeyUsage where
 readExtension (String "listError")=ExtKeyUsage $ error "flags demanded"
 readExtension v=ExtKeyUsage $ map toEnum $ convert v
 extensionJSON (ExtKeyUsage fs)=toJSON $ map fromEnum fs
 payload part n (ExtKeyUsage fs)=if part=="payloadSpine" then toJSON $ length $ take n fs else toJSON $ map fromEnum $ take n fs
instance Observed ExtExtendedKeyUsage where
 readExtension (String "listError")=ExtExtendedKeyUsage $ error "purposes demanded"
 readExtension v=ExtExtendedKeyUsage $ map readPurpose $ convert v
 extensionJSON (ExtExtendedKeyUsage ps)=toJSON $ map purposeJSON ps
 payload part n (ExtExtendedKeyUsage ps)=if part=="payloadSpine" then toJSON $ length $ take n ps else toJSON $ map purposeJSON $ take n ps
instance Observed ExtSubjectKeyId where
 readExtension v=ExtSubjectKeyId $ readData v
 extensionJSON (ExtSubjectKeyId b)=jsbytes b
instance Observed ExtSubjectAltName where
 readExtension v=ExtSubjectAltName $ map readName $ convert v
 extensionJSON (ExtSubjectAltName ns)=toJSON $ map nameJSON ns
 ordering a b=toJSON $ show $ compare a b
 payload part n (ExtSubjectAltName ns)=if part=="payloadSpine" then toJSON $ length $ take n ns else toJSON $ map nameJSON $ take n ns
instance Observed ExtAuthorityKeyId where
 readExtension v=ExtAuthorityKeyId $ readData v
 extensionJSON (ExtAuthorityKeyId b)=jsbytes b
instance Observed ExtCrlDistributionPoints where
 readExtension v=ExtCrlDistributionPoints $ map readPoint $ convert v
 extensionJSON (ExtCrlDistributionPoints ps)=toJSON $ map pointJSON ps
instance Observed ExtNameConstraints where
 readExtension (Array a)=let [p,e]=V.toList a in ExtNameConstraints (map readSub $ convert p) (map readSub $ convert e)
 extensionJSON (ExtNameConstraints p e)=arr[toJSON $ map subJSON p,toJSON $ map subJSON e]
instance Observed ExtNetscapeComment where
 readExtension v=ExtNetscapeComment $ readData v
 extensionJSON (ExtNetscapeComment b)=jsbytes b
dispatch :: Int->(forall a. Observed a=>Proxy a->r)->r
dispatch k f=case k of 0->f(Proxy::Proxy ExtBasicConstraints);1->f(Proxy::Proxy ExtKeyUsage);2->f(Proxy::Proxy ExtExtendedKeyUsage);3->f(Proxy::Proxy ExtSubjectKeyId);4->f(Proxy::Proxy ExtSubjectAltName);5->f(Proxy::Proxy ExtAuthorityKeyId);6->f(Proxy::Proxy ExtCrlDistributionPoints);7->f(Proxy::Proxy ExtNameConstraints);_->f(Proxy::Proxy ExtNetscapeComment)
kind x=case x of Boolean _->"boolean";IntVal _->"integer";OID _->"oid";OctetString _->"octets";BitString _->"bits";ASN1String _->"string";Null->"null";Start _->"start";End _->"end";Other{}->"other";_->"other"
readValues v=map readValue (field v "values")++if field v "tailError" then error "tail demanded" else []
capture action=do
 r<-try $ do x<-action;evaluate $ B.length $ L.toStrict $ encode x;pure x
 case r of Right x->pure x;Left (e::SomeException)->capture $ pure $ object ["fatal" .= takeWhile (/='\n') (displayException e)]
runFor :: forall a.Observed a=>Proxy a->Object->Value
runFor p v=case field v "api"::String of
 "metadata"->object["oid" .= map show (extOID (undefined::a)),"nested" .= extHasNestedASN1 p]
 "value"->object["shown" .= show value,"data" .= extensionJSON value,"equal" .= (value==readExtension (field v "other")),"ordering" .= ordering value (readExtension $ field v "other")]
 "encode"->case field v "part"::String of
  "spine"->toJSON $ length prefix
  "kinds"->toJSON $ map kind prefix
  "shown"->toJSON $ show prefix
  "bytes"->jsbytes $ extEncodeBs value
  _->valuesJSON prefix
 "roundtrip"->observeEither $ extDecode $ extEncode value
 "roundtripBytes"->observeEither $ extDecodeBs $ extEncodeBs value
 "decode"->observeEither $ extDecode $ readValues v
 "decodeBytes"->observeEither $ extDecodeBs $ readData $ field v "bytes"
 "encodeRaw"->let raw=extensionEncode (field v "critical") value in case field v "part"::String of "oid"->let ExtensionRaw o _ _=raw in toJSON $ map show o;"critical"->let ExtensionRaw _ c _=raw in toJSON c;_->rawJSON raw
 "decodeRaw"->observeMaybeEither $ extensionDecode $ readRaw $ field v "raw"
 "getE"->observeMaybeEither $ extensionGetE $ readExts $ field v "extensions"
 "get"->observeMaybe $ extensionGet $ readExts $ field v "extensions"
 "property"->object["property" .= U.property_extension_id value,"encoded" .= jsbytes (extEncodeBs value),"values" .= valuesJSON (extEncode value)]
 _->error "api"
 where
 value=readExtension (field v "value")::a
 prefix=take (field v "count") $ extEncode value
 observeEither :: Either String a->Value
 observeEither r=case r of
  Left e->if field v "part"==("tag"::String) then object["left" .= True] else object["left" .= e]
  Right x->case field v "part"::String of
   "tag"->object["right" .= True]
   "payloadSpine"->object["right" .= payload "payloadSpine" (field v "count") x]
   "payloadPrefix"->object["right" .= payload "payloadPrefix" (field v "count") x]
   "reencode"->object["right" .= valuesJSON (take (field v "count") $ extEncode x)]
   "rekinds"->object["right" .= map kind (take (field v "count") $ extEncode x)]
   _->object["right" .= object["data" .= extensionJSON x,"shown" .= show x]]
 observeMaybeEither :: Maybe (Either String a)->Value
 observeMaybeEither m=case m of Nothing->J.Null;Just e->if field v "part"==("maybe"::String) then toJSON True else observeEither e
 observeMaybe :: Maybe a->Value
 observeMaybe m=case m of Nothing->J.Null;Just x->if field v "part"==("tag"::String) then toJSON True else object["data" .= extensionJSON x,"shown" .= show x]
run v=case field v "api"::String of
 "enum"->if field v "reason" then observeEnum (undefined::ReasonFlag) v else observeEnum (undefined::ExtKeyUsageFlag) v
 "recognized"->toJSON $ map (map show) recognizedOIDs
 "model"->case field v "type"::String of
  "alt"->let a=readName $ field v "value";b=readName $ field v "other" in object["shown" .= show a,"equal" .= (a==b),"ordering" .= show(compare a b)]
  "purpose"->let a=readPurpose $ field v "value";b=readPurpose $ field v "other" in object["shown" .= show a,"equal" .= (a==b),"ordering" .= show(compare a b)]
  "subtree"->let a=readSub $ field v "value";b=readSub $ field v "other" in object["shown" .= show a,"equal" .= (a==b)]
  _->let a=readPoint $ field v "value";b=readPoint $ field v "other" in object["shown" .= show a,"equal" .= (a==b)]
 _->dispatch (field v "kind") (\p->runFor p v)
observeEnum :: forall a.(Enum a,Show a,Ord a)=>a->Object->Value
observeEnum _ v=case field v "method"::String of
 "value"->let a=toEnum(field v "from")::a;b=toEnum(field v "next")::a in object["shown" .= show a,"ordinal" .= fromEnum a,"equal" .= (a==b),"ordering" .= show(compare a b)]
 _->let a=toEnum(field v "from")::a;b=toEnum(field v "next")::a;e=toEnum(field v "end")::a;xs=case field v "mode"::String of "from"->[a..];"then"->[a,b..];"to"->[a..e];_->[a,b..e] in toJSON $ map fromEnum $ take (field v "count") xs
generated n=forM_ [0..n-1] $ \i->do
 let gen :: Gen a->Int->a
     gen g seed=unGen g (mkQCGen $ seed+i) (i `mod` 100)
     ku=gen arbitrary 1345978545::ExtKeyUsage
     eku=gen arbitrary 1445978545::ExtExtendedKeyUsage
     emit group k payload=LC.putStrLn $ encode $ object["group" .= (group::String),"request" .= object["api" .= ("property"::String),"kind" .= (k::Int),"value" .= payload]]
 emit "original-key-usage-generator-and-property" 1 (extensionJSON ku)
 emit "original-extended-key-usage-generator-and-property" 2 (extensionJSON eku)
main=do
 args<-getArgs
 case args of
  ["--generate-tests",n]->generated $ read n
  ["--original-tests"]->do
   a<-quickCheckWithResult stdArgs {maxSuccess=1000,replay=Just(mkQCGen 1345978545,0)} (U.property_extension_id::ExtKeyUsage->Bool)
   b<-quickCheckWithResult stdArgs {maxSuccess=1000,replay=Just(mkQCGen 1445978545,0)} (U.property_extension_id::ExtExtendedKeyUsage->Bool)
   unless (isSuccess a && isSuccess b) exitFailure
  _->LC.getContents >>= mapM_ (\line->case eitherDecode line of Left e->error e;Right v->capture (pure $ run v) >>= LC.putStrLn . encode) . LC.lines
