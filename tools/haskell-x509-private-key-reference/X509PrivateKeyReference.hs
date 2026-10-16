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
import qualified OriginalPrivateKeyTests as U
import Test.QuickCheck hiding (Success,elements)
import Test.QuickCheck.Gen(unGen)
import Test.QuickCheck.Random(mkQCGen)
import System.Environment(getArgs)
import System.Exit(exitFailure)
import Data.X509.PublicKey(SerializedPoint(..))
import Data.X509.PrivateKey
import Crypto.Debug(DebugShow(..))
import Crypto.Number.Serialize(i2osp)
import qualified Crypto.Number.ModArithmetic as Mod
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



readNumber J.Null=error "integer demanded"
readNumber v=read $ convert v
readData J.Null=error "content demanded"
readData v=bytes v
readKey (Array a)=case V.toList a of
 [String "rsa",sz,n,e,d,p,q,dp,dq,qi]->PrivKeyRSA $ RSA.PrivateKey (RSA.PublicKey (convert sz) (readNumber n) (readNumber e)) (readNumber d) (readNumber p) (readNumber q) (readNumber dp) (readNumber dq) (readNumber qi)
 [String "dsa",p,g,q,x]->PrivKeyDSA $ DSA.PrivateKey (DSA.Params (readNumber p) (readNumber g) (readNumber q)) (readNumber x)
 [String "ecNamed",c,d]->PrivKeyEC $ PrivKeyEC_Named (toEnum $ convert c) (readNumber d)
 [String "ecPrime",d,a,b,p,g,n,h,seed]->PrivKeyEC $ PrivKeyEC_Prime (readNumber d) (readNumber a) (readNumber b) (readNumber p) (SerializedPoint $ readData g) (readNumber n) (readNumber h) (readNumber seed)
 [String "x25519",b]->PrivKeyX25519 $ throwCryptoError $ X25519.secretKey $ readData b
 [String "x448",b]->PrivKeyX448 $ throwCryptoError $ X448.secretKey $ readData b
 [String "ed25519",b]->PrivKeyEd25519 $ throwCryptoError $ Ed25519.secretKey $ readData b
 [String "ed448",b]->PrivKeyEd448 $ throwCryptoError $ Ed448.secretKey $ readData b
 _->error "key"
numberJSON=string . show
keyJSON k=case k of
 PrivKeyRSA (RSA.PrivateKey (RSA.PublicKey sz n e) d p q dp dq qi)->arr[String "rsa",toJSON sz,numberJSON n,numberJSON e,numberJSON d,numberJSON p,numberJSON q,numberJSON dp,numberJSON dq,numberJSON qi]
 PrivKeyDSA (DSA.PrivateKey (DSA.Params p g q) x)->arr[String "dsa",numberJSON p,numberJSON g,numberJSON q,numberJSON x]
 PrivKeyEC (PrivKeyEC_Named c d)->arr[String "ecNamed",toJSON $ fromEnum c,numberJSON d]
 PrivKeyEC (PrivKeyEC_Prime d a b p (SerializedPoint g) n h seed)->arr[String "ecPrime",numberJSON d,numberJSON a,numberJSON b,numberJSON p,jsbytes g,numberJSON n,numberJSON h,numberJSON seed]
 PrivKeyX25519 b->arr[String "x25519",jsbytes $ BA.convert b]
 PrivKeyX448 b->arr[String "x448",jsbytes $ BA.convert b]
 PrivKeyEd25519 b->arr[String "ed25519",jsbytes $ BA.convert b]
 PrivKeyEd448 b->arr[String "ed448",jsbytes $ BA.convert b]
kind x=case x of Start _->"start";End _->"end";IntVal _->"integer";OID _->"oid";Null->"null";BitString _->"bits";OctetString _->"octets";_->"other"
readValues v=map readValue (field v "values")++if field v "tailError" then error "tail demanded" else []
capture action=do
 r<-try $ do x<-action;evaluate $ B.length $ L.toStrict $ encode x;pure x
 case r of Right x->pure x;Left (e::SomeException)->capture $ pure $ object["fatal" .= takeWhile (/='\n') (displayException e)]
run v=case field v "api"::String of
 "derValues"->jsbytes $ encodeASN1' DER $ readValues v
 "value"->object["shown" .= show value,"debug" .= debugShow value,"value" .= keyJSON value,"equal" .= (value==readKey(field v "other"))]
 "show"->toJSON $ show value
 "debug"->toJSON $ debugShow value
 "ecShow"->case value of PrivKeyEC e->object["shown" .= show e,"debug" .= debugShow e];_->error "ec"
 "algorithm"->object["shown" .= show(privkeyToAlg value),"oid" .= map show(getObjectID $ privkeyToAlg value)]
 "encode"->let out=take(field v "count") $ toASN1 value (readValues v) in case field v "part"::String of
  "spine"->toJSON $ length out
  "kinds"->toJSON $ map kind out
  "shown"->toJSON $ show out
  "bytes"->jsbytes $ encodeASN1' DER out
  _->valuesJSON out
 "from"->observed $ fromASN1 $ readValues v
 "roundtrip"->observed $ fromASN1 $ toASN1 value []
 "property"->object["property" .= U.property_unmarshall_marshall_id value,"values" .= valuesJSON(toASN1 value []),"bytes" .= jsbytes(encodeASN1' DER $ toASN1 value [])]
 "redaction"->toJSON $ U.property_ec_show_redacts $ U.ECSecret $ readNumber $ field v "scalar"
 "i2osp"->jsbytes(i2osp(readNumber $ field v "number")::B.ByteString)
 "pow"->numberJSON $ Mod.expSafe (readNumber $ field v "base") (readNumber $ field v "exponent") (readNumber $ field v "modulus")
 _->error "api"
 where
 value=readKey(field v "value")
 observed r=case r of
  Left e->if field v "part"==("tag"::String) then object["left" .= True] else object["left" .= e]
  Right(k,rest)->case field v "part"::String of
   "tag"->object["right" .= True]
   "remaining"->object["remaining" .= valuesJSON(take(field v "count") rest)]
   "algorithm"->object["algorithm" .= show(privkeyToAlg k)]
   "shown"->object["shown" .= show k]
   _->object["value" .= keyJSON k,"shown" .= show k,"debug" .= debugShow k,"remaining" .= valuesJSON rest]
generated n=forM_ [0..n-1] $ \i->do
 let key=unGen arbitrary (mkQCGen $ 1645978545+i) (i `mod` 100)::PrivKey
     U.ECSecret scalar=unGen arbitrary (mkQCGen $ 1745978545+i) (i `mod` 100)
 LC.putStrLn $ encode $ object["group" .= ("original-private-key-generator-and-property"::String),"request" .= object["api" .= ("property"::String),"value" .= keyJSON key]]
 LC.putStrLn $ encode $ object["group" .= ("original-ec-redaction-generator-and-property"::String),"request" .= object["api" .= ("redaction"::String),"scalar" .= show scalar]]
main=do
 args<-getArgs
 case args of
  ["--generate-tests",n]->generated $ read n
  ["--original-tests"]->do
   a<-quickCheckWithResult stdArgs {maxSuccess=1000,replay=Just(mkQCGen 1645978545,0)} (U.property_unmarshall_marshall_id::PrivKey->Bool)
   b<-quickCheckWithResult stdArgs {maxSuccess=1000,replay=Just(mkQCGen 1745978545,0)} U.property_ec_show_redacts
   unless(isSuccess a && isSuccess b) exitFailure
  _->LC.getContents >>= mapM_ (\line->case eitherDecode line of Left e->error e;Right v->capture(pure $ run v) >>= LC.putStrLn . encode) . LC.lines
