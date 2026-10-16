{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,CPP #-}
#if __GLASGOW_HASKELL__ != 912 || __GLASGOW_HASKELL_PATCHLEVEL1__ != 2 || __GLASGOW_HASKELL_PATCHLEVEL2__ != 0
#error GHC 9.12.2 required
#endif
module Main where
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as L
import qualified Data.ByteString.Lazy.Char8 as LC
import qualified Data.ASN1.BitArray as A
import qualified Data.ASN1.Types.String as S
import Data.Char(chr,ord)
import Data.String(fromString)
import Data.Word
import Control.Exception

field v k=case fromJSON (maybe (error "missing field") id (K.lookup k v)) of Success a->a;Error e->error e
bytes v k=B.pack $ map fromIntegral (field v k::[Int])
jsbytes=toJSON . map (fromIntegral::Word8->Int) . B.unpack
encodings=[S.IA5,S.UTF8,S.General,S.Graphic,S.Numeric,S.Printable,S.VideoTex,S.Visible,S.T61,S.UTF32,S.Character,S.BMP]
encoding v k=encodings!!field v k
readString v=S.ASN1CharacterString (encoding v "encoding") (bytes v "bytes")
readBits v=if field v "constructor"==("raw"::String) then A.BitArray (field v "length") (bytes v "bytes") else A.toBitArray (bytes v "bytes") (field v "skip")
stringJSON s@(S.ASN1CharacterString e b)=object ["encoding" .= show e,"bytes" .= jsbytes b,"shown" .= show s]
bitsJSON b=object ["length" .= A.bitArrayLength b,"bytes" .= jsbytes (A.bitArrayGetData b),"shown" .= show b]
capture action=do
 r<-try $ do x<-action;evaluate $ L.length $ encode x;pure x
 pure $ case r of Right x->x;Left (e::SomeException)->object ["fatal" .= takeWhile (/='\n') (displayException e)]
decodeString limit s=pure $ case S.asn1CharacterToString s of Nothing->Null;Just cs->toJSON $ map ord $ maybe cs (`take` cs) limit
decodePoints limit s=case S.asn1CharacterToString s of Nothing->pure Null;Just cs->toJSON <$> mapM (evaluate . ord) (maybe cs (`take` cs) limit)
run v=case field v "api"::String of
 "encode"->do
  let s=S.asn1CharacterString (encoding v "encoding") (map chr $ field v "points")
  encoded<-capture $ pure $ stringJSON s
  decoded<-capture $ decodeString Nothing s
  pure $ object ["encoded" .= encoded,"decoded" .= decoded]
 "literal"->pure $ stringJSON (fromString (map chr $ field v "points")::S.ASN1CharacterString)
 "decode"->do
  let s=readString v
  decoded<-capture $ decodeString Nothing s
  prefixes<-mapM (\n->capture $ decodeString (Just n) s) (field v "prefixes"::[Int])
  pointwise<-capture $ decodePoints Nothing s
  pointPrefixes<-mapM (\n->capture $ decodePoints (Just n) s) (field v "prefixes"::[Int])
  pure $ object ["raw" .= stringJSON s,"decoded" .= decoded,"prefixes" .= prefixes,"pointwise" .= pointwise,"pointPrefixes" .= pointPrefixes]
 "compare"->pure $ let a=readString (field v "first");b=readString (field v "second") in object ["equal" .= (a==b),"ordering" .= show (compare a b)]
 "bits"->do
  let b=readBits v
  operations<-mapM (\o->capture $ pure $ case field o "op"::String of
    "get"->toJSON $ A.bitArrayGetBit b (field o "index")
    "set"->bitsJSON $ A.bitArraySetBit b (field o "index")
    "clear"->bitsJSON $ A.bitArrayClearBit b (field o "index")
    "value"->bitsJSON $ A.bitArraySetBitValue b (field o "index") (field o "value")
    _->error "op") (field v "operations"::[Object])
  let copy=A.BitArray (A.bitArrayLength b) (A.bitArrayGetData b)
  pure $ object ["raw" .= bitsJSON b,"operations" .= operations,"equal" .= (b==copy),"differentLength" .= (b==A.BitArray (A.bitArrayLength b+1) (A.bitArrayGetData b)),"differentBytes" .= (b==A.BitArray (A.bitArrayLength b) (B.snoc (A.bitArrayGetData b) 0))]
 _->error "api"
main=LC.getContents >>= mapM_ (\l->case eitherDecode l of Left e->error e;Right v->capture (run v) >>= LC.putStrLn . encode) . LC.lines
