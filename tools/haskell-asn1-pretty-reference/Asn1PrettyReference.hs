{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,CPP,MagicHash #-}
#if __GLASGOW_HASKELL__ != 912 || __GLASGOW_HASKELL_PATCHLEVEL1__ != 2 || __GLASGOW_HASKELL_PATCHLEVEL2__ != 0
#error GHC 9.12.2 required
#endif
-- JSON observations only; Pretty and Prim are compiled unchanged from vendor.
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
import Data.ASN1.Pretty
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
 [String "timeError",part]->ASN1Time TimeUTC (DateTime (if convert part == ("date"::String) then error "date demanded" else Date 2026 October 16) (if convert part == ("time"::String) then error "time demanded" else TimeOfDay (Hours 5) (Minutes 34) (Seconds 12) (NanoSeconds 0))) Nothing
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

readMode v=if field v "single" then SingleLine else Multiline $ field v "indent"
readValues v=let vs=map readValue $ field v "values" in if field v "tailError" then vs++error "tail demanded" else vs
kind (Header _)="header"
kind (Primitive _)="primitive"
kind ConstructionBegin="begin"
kind ConstructionEnd="end"
headerTag (Header (ASN1Header _ t _ _))=t
headerTag _=0
run v=case field v "api"::String of
 "mode"->object ["shown" .= show (readMode v),"equal" .= (readMode v == if field v "otherSingle" then SingleLine else Multiline (field v "otherIndent"))]
 "pretty"->let output=pretty (readMode v) (readValues v) in toJSON $ map ord $ if field v "prefix" then take (field v "count") output else output
 "encode"->let (n,es)=case field v "method"::String of
                 "primitive"->P.encodePrimitive $ readValue $ field v "value"
                 "one"->P.encodeOne $ readValue $ field v "value"
                 "constructed"->P.encodeConstructed (readValue $ field v "value") (readValues v)
                 _->P.encodeList $ readValues v
               part=field v "part"::String
               prefix=take (field v "count") es
           in case part of
            "length"->toJSON n
            "spine"->toJSON $ length prefix
            "kinds"->toJSON $ map kind prefix
            "headerTags"->toJSON $ map headerTag prefix
            _->toJSON $ show prefix
 _->error "api"
capture action=do
 r<-try $ do x<-action;evaluate $ L.length $ encode x;pure x
 pure $ case r of Right x->x;Left (e::SomeException)->object ["fatal" .= takeWhile (/='\n') (displayException e)]
main=LC.getContents >>= mapM_ (\line->case eitherDecode line of Left e->error e;Right v->capture (pure $ run v) >>= LC.putStrLn . encode) . LC.lines
