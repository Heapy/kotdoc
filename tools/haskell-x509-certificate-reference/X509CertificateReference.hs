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
import qualified OriginalCertificateTests as U
import Test.QuickCheck hiding (Success,elements)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import System.Environment(getArgs)
import System.Exit(exitFailure)
import Data.X509.PublicKey
import Data.X509.Cert
import Data.X509.AlgorithmIdentifier
import Data.X509.OID
import Crypto.Error(throwCryptoError)
import qualified Data.ByteArray as BA
import qualified Crypto.PubKey.RSA.Types as RSA
import qualified Crypto.PubKey.DSA as DSA
import qualified Crypto.PubKey.ECC.Types as ECC
import qualified Crypto.PubKey.Curve25519 as X25519
import qualified Crypto.PubKey.Curve448 as X448
import qualified Crypto.PubKey.Ed25519 as Ed25519
import qualified Crypto.PubKey.Ed448 as Ed448
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
readDateTime J.Null=error "datetime demanded"
readDateTime (Array a)=case V.toList a of [y,m,d,h,mi,s,ns]->DateTime (Date (convert y) (toEnum $ convert m) (convert d)) (TimeOfDay (Hours $ convert h) (Minutes $ convert mi) (Seconds $ convert s) (NanoSeconds $ convert ns));_->error "dt"
dateTimeJSON (DateTime (Date y m d) (TimeOfDay (Hours h) (Minutes mi) (Seconds s) (NanoSeconds ns)))=toJSON [y,fromEnum m,d,fromIntegral h,fromIntegral mi,fromIntegral s,fromIntegral ns]
readValue (Array a)=case V.toList a of
 [String "timeError"]->ASN1Time (error "kind demanded") (error "datetime demanded") (error "zone demanded")
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



readNumber J.Null=error "integer demanded"
readNumber v=read $ convert v
readData J.Null=error "content demanded"
readData v=bytes v
readOid J.Null=error "oid demanded"
readOid v=map read $ convert v
readKey J.Null=error "key demanded"
readKey (Array a)=case V.toList a of
 [String "rsa",sz,n,e]->PubKeyRSA $ RSA.PublicKey (convert sz) (readNumber n) (readNumber e)
 [String "dsa",p,g,q,y]->PubKeyDSA $ DSA.PublicKey (DSA.Params (readNumber p) (readNumber g) (readNumber q)) (readNumber y)
 [String "ecNamed",c,b]->PubKeyEC $ PubKeyEC_Named (toEnum $ convert c) (SerializedPoint $ readData b)
 [String "ecPrime",pub,a,b,p,g,n,h,seed]->PubKeyEC $ PubKeyEC_Prime (SerializedPoint $ readData pub) (readNumber a) (readNumber b) (readNumber p) (SerializedPoint $ readData g) (readNumber n) (readNumber h) (readNumber seed)
 [String "x25519",b]->PubKeyX25519 $ throwCryptoError $ X25519.publicKey $ readData b
 [String "x448",b]->PubKeyX448 $ throwCryptoError $ X448.publicKey $ readData b
 [String "ed25519",b]->PubKeyEd25519 $ throwCryptoError $ Ed25519.publicKey $ readData b
 [String "ed448",b]->PubKeyEd448 $ throwCryptoError $ Ed448.publicKey $ readData b
 [String "dh",p,g,q,j,seed,c]->PubKeyDH (readNumber p,readNumber g,readNumber q,if j==J.Null then Nothing else Just $ readNumber j,(convert seed,readNumber c))
 [String "unknown",o,b]->PubKeyUnknown (readOid o) (readData b)
 _->error "key"
numberJSON=string . show
keyJSON k=case k of
 PubKeyRSA (RSA.PublicKey sz n e)->arr[String "rsa",toJSON sz,numberJSON n,numberJSON e]
 PubKeyDSA (DSA.PublicKey (DSA.Params p g q) y)->arr[String "dsa",numberJSON p,numberJSON g,numberJSON q,numberJSON y]
 PubKeyEC (PubKeyEC_Named c (SerializedPoint b))->arr[String "ecNamed",toJSON $ fromEnum c,jsbytes b]
 PubKeyEC (PubKeyEC_Prime (SerializedPoint pub) a b p (SerializedPoint g) n h seed)->arr[String "ecPrime",jsbytes pub,numberJSON a,numberJSON b,numberJSON p,jsbytes g,numberJSON n,numberJSON h,numberJSON seed]
 PubKeyX25519 b->arr[String "x25519",jsbytes $ BA.convert b]
 PubKeyX448 b->arr[String "x448",jsbytes $ BA.convert b]
 PubKeyEd25519 b->arr[String "ed25519",jsbytes $ BA.convert b]
 PubKeyEd448 b->arr[String "ed448",jsbytes $ BA.convert b]
 PubKeyDH (p,g,q,j,(seed,c))->arr[String "dh",numberJSON p,numberJSON g,numberJSON q,toJSON $ fmap show j,toJSON seed,numberJSON c]
 PubKeyUnknown o b->arr[String "unknown",toJSON $ map show o,jsbytes b]
hashes=[HashMD2,HashMD5,HashSHA1,HashSHA224,HashSHA256,HashSHA384,HashSHA512]
algorithms=[PubKeyALG_RSA,PubKeyALG_RSAPSS,PubKeyALG_DSA,PubKeyALG_EC,PubKeyALG_X25519,PubKeyALG_X448,PubKeyALG_Ed25519,PubKeyALG_Ed448,PubKeyALG_DH]
readAlgorithm (Array a)=PubKeyALG_Unknown $ readOid $ V.last a
readAlgorithm n=algorithms!!convert n
algorithmJSON (PubKeyALG_Unknown o)=arr[String "unknown",toJSON $ map show o]
algorithmJSON a=toJSON $ maybe (error "alg") id $ lookup a $ zip algorithms [0::Int ..]
readSig J.Null=error "signature demanded"
readSig (Array a)=case V.toList a of
 [String "with",h,k]->SignatureALG (hashes!!convert h) (readAlgorithm k)
 [String "intrinsic",k]->SignatureALG_IntrinsicHash $ readAlgorithm k
 [String "unknown",o]->SignatureALG_Unknown $ readOid o
 _->error "sig"
sigJSON s=case s of
 SignatureALG h k->arr[String "with",toJSON $ maybe (error "hash") id $ lookup h $ zip hashes [0::Int ..],algorithmJSON k]
 SignatureALG_IntrinsicHash k->arr[String "intrinsic",algorithmJSON k]
 SignatureALG_Unknown o->arr[String "unknown",toJSON $ map show o]
readDn J.Null=DistinguishedName $ error "dn demanded"
readDn (Array a)=DistinguishedName $ map entry $ V.toList a
 where entry (Array e)=let [o,en,b]=V.toList e in (readOid o,ASN1CharacterString (encodings!!convert en) (readData b))
dnJSON (DistinguishedName es)=toJSON $ map (\(o,ASN1CharacterString en b)->arr[toJSON $ map show o,toJSON $ maybe (error "enc") id $ lookup en $ zip encodings [0::Int ..],jsbytes b]) es
readRaw (Array a)=let [o,c,b]=V.toList a in ExtensionRaw (readOid o) (if c==J.Null then error "critical demanded" else convert c) (readData b)
rawJSON (ExtensionRaw o c b)=arr[toJSON $ map show o,toJSON c,jsbytes b]
readExts J.Null=Extensions Nothing
readExts (String "listError")=Extensions $ Just $ error "extension list demanded"
readExts (String _)=error "extensions demanded"
readExts (Array a)=Extensions $ Just $ map readRaw $ V.toList a
extsJSON (Extensions es)=toJSON $ fmap (map rawJSON) es
readValidity J.Null=error "validity demanded"
readValidity (Array a)=let [b,e]=V.toList a in (readDateTime b,readDateTime e)
validityJSON (a,b)=arr[dateTimeJSON a,dateTimeJSON b]
readVersion J.Null=error "version demanded"
readVersion v=fromInteger $ readNumber v
readCert J.Null=error "certificate demanded"
readCert (Array a)=let [v,s,alg,issuer,validity,subject,key,exts]=V.toList a in Certificate (readVersion v) (readNumber s) (readSig alg) (readDn issuer) (readValidity validity) (readDn subject) (readKey key) (readExts exts)
certJSON (Certificate v s alg issuer validity subject key exts)=arr[string $ show v,numberJSON s,sigJSON alg,dnJSON issuer,validityJSON validity,dnJSON subject,keyJSON key,extsJSON exts]
fieldJSON v c=case field v "field"::String of
 "version"->string $ show $ certVersion c
 "serial"->numberJSON $ certSerial c
 "signature"->sigJSON $ certSignatureAlg c
 "issuer"->dnJSON $ certIssuerDN c
 "validity"->validityJSON $ certValidity c
 "subject"->dnJSON $ certSubjectDN c
 "key"->keyJSON $ certPubKey c
 _->extsJSON $ certExtensions c
kind x=case x of Start _->"start";End _->"end";IntVal _->"integer";OID _->"oid";Null->"null";BitString _->"bits";OctetString _->"octets";ASN1Time{}->"time";_->"other"
readValues v=map readValue (field v "values")++if field v "tailError" then error "tail demanded" else []
capture action=do
 r<-try $ do x<-action;evaluate $ B.length $ L.toStrict $ encode x;pure x
 case r of Right x->pure x;Left (e::SomeException)->capture $ pure $ object["fatal" .= takeWhile (/='\n') (displayException e)]
run v=case field v "api"::String of
 "value"->object["data" .= certJSON value,"equal" .= (value==readCert(field v "other")),"shown" .= show value]
 "equal"->toJSON $ value==readCert(field v "other")
 "field"->fieldJSON v value
 "encode"->let out=take(field v "count") $ toASN1 value (readValues v) in case field v "part"::String of
  "spine"->toJSON $ length out
  "kinds"->toJSON $ map kind out
  "shown"->toJSON $ show out
  "bytes"->jsbytes $ encodeASN1' DER out
  _->valuesJSON out
 "from"->observed $ fromASN1 $ readValues v
 "roundtrip"->observed $ fromASN1 $ toASN1 value []
 "property"->object["property" .= U.property_unmarshall_marshall_id value,"values" .= valuesJSON(toASN1 value [])]
 _->error "api"
 where
 value=readCert(field v "value")
 observed r=case r of
  Left e->if field v "part"==("tag"::String) then object["left" .= True] else object["left" .= e]
  Right(c,rest)->case field v "part"::String of
   "tag"->object["right" .= True]
   "remaining"->object["remaining" .= valuesJSON(take(field v "count") rest)]
   "field"->object["field" .= fieldJSON v c]
   "rekinds"->object["right" .= map kind (take(field v "count") $ toASN1 c [])]
   "reencode"->object["right" .= valuesJSON(take(field v "count") $ toASN1 c [])]
   _->object["data" .= certJSON c,"remaining" .= valuesJSON rest,"shown" .= show c]
generated n=forM_ [0..n-1] $ \i->do
 let c=unGen arbitrary (mkQCGen $ 1845978545+i) (i `mod` 100)::Certificate
 LC.putStrLn $ encode $ object["group" .= ("original-certificate-generator-and-property"::String),"request" .= object["api" .= ("property"::String),"value" .= certJSON c]]
main=do
 args<-getArgs
 case args of
  ["--generate-tests",n]->generated $ read n
  ["--original-tests"]->do
   r<-quickCheckWithResult stdArgs {maxSuccess=1000,replay=Just(mkQCGen 1845978545,0)} (U.property_unmarshall_marshall_id::Certificate->Bool)
   unless(isSuccess r) exitFailure
  _->LC.getContents >>= mapM_ (\line->case eitherDecode line of Left e->error e;Right v->capture(pure $ run v) >>= LC.putStrLn . encode) . LC.lines
