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
import qualified Data.ByteString.Base64 as Base64
import Data.Base64.Types (extractBase64)
import qualified Data.Text as T
import Data.Char(chr,ord)
import Data.Word
import Data.List.NonEmpty(toList)
import Control.Exception
import Control.Applicative(empty)
import Control.Monad
import Data.PEM
import Data.ASN1.Types.Lowlevel
import Data.ASN1.Error
import qualified Data.ASN1.BinaryEncoding.Raw as Raw
import qualified Data.ASN1.BinaryEncoding.Parse as P
import qualified Data.ASN1.Get as G
import qualified Data.ASN1.Serialize as S
import qualified Data.ASN1.Internal as I

field v k=case fromJSON (maybe (error "missing field") id (K.lookup k v)) of Success a->a;Error e->error e
option v k def=case K.lookup k v of Nothing->def;Just x->case fromJSON x of Success a->a;Error e->error e
bytes v k=B.pack $ map fromIntegral (field v k::[Int])
jsbytes=toJSON . map (fromIntegral::Word8->Int) . B.unpack
jsstr=toJSON . map ord
left e=object ["left" .= e]
right x=object ["right" .= x]
eitherJSON f= either (left.show) (right.f)
pemJSON (PEM name headers content)=object ["name" .= jsstr name,"headers" .= [toJSON [jsstr k,jsbytes v] | (k,v)<-headers],"content" .= jsbytes content]
readPem v=PEM (map chr $ field v "name") [(map chr k,B.pack $ map fromIntegral b)|(k,b)<-(field v "headers"::[([Int],[Int])])] (bytes v "content")
pemResult=either left (right . map pemJSON)
lengthJSON (LenShort n)=toJSON [0,n]
lengthJSON (LenLong count n)=toJSON [1,count,n]
lengthJSON LenIndefinite=toJSON ([2]::[Int])
headerJSON (ASN1Header cl tag pc len)=object ["class" .= fromEnum cl,"tag" .= tag,"constructed" .= pc,"length" .= lengthJSON len]
readLength [0,n]=LenShort n
readLength [1,c,n]=LenLong c n
readLength [2]=LenIndefinite
readLength _=error "length"
readHeader v=ASN1Header (toEnum $ field v "class") (field v "tag") (field v "constructed") (readLength $ field v "length")
eventJSON (Header h)=object ["header" .= headerJSON h]
eventJSON (Primitive b)=object ["primitive" .= jsbytes b]
eventJSON ConstructionBegin=String "begin"
eventJSON ConstructionEnd=String "end"
readEvent (String "begin")=ConstructionBegin
readEvent (String "end")=ConstructionEnd
readEvent (Object v)=case K.lookup "header" v of Just (Object h)->Header $ readHeader h;_->Primitive $ bytes v "primitive"
readEvent _=error "event"
rawTrace state []=pure []
rawTrace state (b:bs)=case P.runParseState state b of
 Left e->pure [left (show e)]
 Right (evs,next)->(object ["events" .= map eventJSON evs,"done" .= P.isParseDone next]:) <$> rawTrace next bs
resultJSON f (G.Fail s)=object ["kind" .= ("fail"::String),"message" .= s]
resultJSON f (G.Partial _)=object ["kind" .= ("partial"::String)]
resultJSON f (G.Done a pos rest)=object ["kind" .= ("done"::String),"value" .= f a,"position" .= pos,"remaining" .= jsbytes rest]
getTrace f result []=[resultJSON f result]
getTrace f result (b:bs)=resultJSON f result:case result of G.Partial k->getTrace f (k b) bs;_->[]
program v=case field v "op"::String of
 "word"->toJSON <$> G.getWord8
 "bytes"->jsbytes <$> G.getBytes (field v "count")
 "copy"->jsbytes <$> G.getBytesCopy (field v "count")
 "pure"->pure $ field v "value"
 "fail"->fail $ field v "message"
 "empty"->empty
 "zero"->mzero
 "choice"->mplus (program $ field v "first") (program $ field v "second")
 "sequence"->toJSON <$> mapM program (field v "programs"::[Object])
 "map"->(\x->object ["mapped" .= x]) <$> program (field v "program")
 _->error "program"
run v=case field v "api"::String of
 "base64"->pure $ object ["encoded" .= jsbytes (extractBase64 $ Base64.encodeBase64' b),"decoded" .= either (left.T.unpack) (right.jsbytes) (Base64.decodeBase64Untyped b),"valid" .= Base64.isValidBase64 b,"correct" .= Base64.isBase64 b] where b=bytes v "bytes"
 "pemParse"->pure $ let chunks=map (B.pack . map fromIntegral) (field v "chunks"::[[Int]]);r=pemParseLBS (L.fromChunks chunks) in object ["parsed" .= pemResult r,"strict" .= pemResult (pemParseBS $ B.concat chunks),"shown" .= either left (right.map show) r]
 "pemWrite"->pure $ let pems=map readPem (field v "sections");written=B.concat $ map pemWriteBS pems in object ["bytes" .= jsbytes written,"parsed" .= pemResult (pemParseBS written),"shown" .= map show pems,"lazyChunks" .= [map jsbytes $ L.toChunks $ pemWriteLBS p | p<-pems],"equal" .= (pems==pems)]
 "lazyDemand"->pure $ let chunks=map (\x->case x of String s->error $ T.unpack s;_->B.pack $ map fromIntegral (case fromJSON x of Success b->b::[Int];Error e->error e)) (field v "chunks"::[Value]);b=L.fromChunks chunks in if field v "kind"==("pem"::String) then pemResult $ pemParseLBS b else eitherJSON (map eventJSON) $ Raw.parseLBS b
 "raw"->do
  let chunks=map (B.pack . map fromIntegral) (field v "chunks"::[[Int]])
  trace<-rawTrace P.newParseState chunks
  pure $ object ["strict" .= eitherJSON (map eventJSON) (Raw.parseBS $ B.concat chunks),"lazy" .= eitherJSON (map eventJSON) (Raw.parseLBS $ L.fromChunks chunks),"trace" .= trace]
 "writeRaw"->pure $ let evs=map readEvent (field v "events") in object ["bytes" .= jsbytes (Raw.toByteString evs),"chunks" .= map jsbytes (L.toChunks $ Raw.toLazyByteString evs),"shown" .= map show evs]
 "putHeader"->pure $ let h=readHeader (field v "header") in object ["bytes" .= jsbytes (S.putHeader h),"shown" .= show h]
 "headerRoundtrip"->pure $ let h=readHeader (field v "header");b=S.putHeader h;r=G.runGet S.getHeader b in object ["bytes" .= jsbytes b,"parsed" .= resultJSON headerJSON r,"identity" .= (case r of G.Done h' _ _->h'==h;_->False)]
 "eventRoundtrip"->pure $ let evs=map readEvent (field v "events");b=Raw.toLazyByteString evs;r=Raw.parseLBS b in object ["chunks" .= map jsbytes (L.toChunks b),"parsed" .= eitherJSON (map eventJSON) r,"identity" .= (r==Right evs)]
 "getHeader"->pure $ let bs:rest=map (B.pack.map fromIntegral) (field v "chunks"::[[Int]]) in toJSON $ getTrace headerJSON (G.runGetPos (field v "position") S.getHeader bs) rest
 "get"->pure $ let bs:rest=map (B.pack.map fromIntegral) (field v "chunks"::[[Int]]) in toJSON $ getTrace id (G.runGetPos (field v "position") (program $ field v "program") bs) rest
 "integer"->pure $ let b=bytes v "bytes";i=read (field v "integer")::Integer;(nu,u)=I.uintOfBytes b;(ns,s)=I.intOfBytes b in object ["unsigned" .= [toJSON nu,toJSON (show u)],"signed" .= [toJSON ns,toJSON (show s)],"unsignedBytes" .= jsbytes (B.pack $ toList $ I.bytesOfUInt i),"signedBytes" .= jsbytes (B.pack $ toList $ I.bytesOfInt i),"variable" .= jsbytes (I.putVarEncodingIntegral i)]
 _->error "api"
capture v=do
 r<-try $ do x<-run v;evaluate $ L.length $ encode x;pure x
 pure $ case r of Right x->x;Left (e::SomeException)->object ["fatal" .= takeWhile (/='\n') (displayException e)]
main=LC.getContents >>= mapM_ (\l->case eitherDecode l of Left e->error e;Right v->capture v >>= LC.putStrLn . encode) . LC.lines
