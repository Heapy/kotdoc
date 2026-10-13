{-# LANGUAGE CPP #-}
{-# LANGUAGE FlexibleContexts #-}
module Main where
import Codec.Picture.Tiff.Internal.Types
import Codec.Picture.Metadata.Exif
import qualified Codec.Picture.Jpg.Internal.Types as J
import Data.Binary (Binary(..), encode)
import Data.Binary.Get (Get, runGetOrFail, skip)
import Data.Binary.Put (runPut, putWord16be, putByteString)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as L
import qualified Data.Vector as V
import Data.List (sort)
import Numeric (readHex)
import Data.Int (Int32)
import Data.Word (Word16,Word32)
import Control.Exception (SomeException, try, evaluate, displayException)
import Control.DeepSeq (force)
import System.Environment (getArgs)
import System.Info (compilerName,compilerVersion)
import Data.Version (showVersion)

type Field = (Int,Int,Integer,Integer,String,[Integer])
type Request = (String,Int,[Integer],String,[[Field]])
obj :: [(String,String)] -> String
obj xs = "{" ++ join [show k ++ ":" ++ v | (k,v) <- xs] ++ "}"
 where join []=""; join [x]=x; join (x:xs)=x++","++join xs
packed = show . L.unpack
suffix b = "[" ++ show (L.length b) ++ "," ++ packed (L.take 16 b) ++ "," ++ packed (L.drop (max 0 (L.length b-16)) b) ++ "]"
parse :: Get a -> (a -> [(String,String)]) -> L.ByteString -> String
parse parser describe input = case runGetOrFail parser input of
 Left (rest,n,e) -> obj [("error",show e),("offset",show n),("rest",suffix rest)]
 Right (rest,n,a) -> obj $ [("offset",show n),("rest",suffix rest)] ++ describe a
value :: Show a => (a -> L.ByteString) -> a -> [(String,String)]
value encoder a = [("show",show (show a)),("encoded",packed (encoder a))]
tag n | n < 0 = TagUnknown (fromIntegral (-n-1))
      | otherwise = tagOfWord16 (fromIntegral n)
exif :: String -> [Integer] -> ExifData
exif kind ns = case (kind,ns) of
 ("none",_) -> ExifNone
 ("long",[n]) -> ExifLong (fromIntegral n)
 ("short",[n]) -> ExifShort (fromIntegral n)
 ("string",_) -> ExifString (B.pack $ map fromIntegral ns)
 ("undefined",_) -> ExifUndefined (B.pack $ map fromIntegral ns)
 ("shorts",_) -> ExifShorts (V.fromList $ map fromIntegral ns)
 ("longs",_) -> ExifLongs (V.fromList $ map fromIntegral ns)
 ("rational",[a,b]) -> ExifRational (fromIntegral a) (fromIntegral b)
 ("signed",[a,b]) -> ExifSignedRational (fromIntegral a) (fromIntegral b)
 ("ifd",_) -> ExifIFD [(tag (fromInteger t),ExifLong (fromIntegral v)) | (t,v) <- zip ns (drop 1 ns)]
 _ -> error "bad Exif request"
field :: Field -> ImageFileDirectory
field (t,typ,count,offset,kind,values) = ImageFileDirectory (tag t) ([TypeByte,TypeAscii,TypeShort,TypeLong,TypeRational,TypeSByte,TypeUndefined,TypeSignedShort,TypeSignedLong,TypeSignedRational,TypeFloat,TypeDouble]!!(typ-1)) (fromIntegral count) (fromIntegral offset) (exif kind values)
run :: Request -> String
run (op,en,ns,bytes,rows) = case op of
 "tag" -> obj [("values", "[" ++ commas ["["++show(show t)++","++show(word16OfTag t)++","++(if isInIFD0 t then "true" else "false")++"]" | n<-ns,let t=tag(fromInteger n)] ++ "]"),("sorted",show(show(sort(map (tag . fromInteger) ns))))]
 "deepEq" -> let depth=fromInteger(head ns); make leaf=iterate (\v -> ExifIFD [(TagExifOffset,v)]) leaf !! depth
                 in obj [("values", "["++boolean(make(ExifLong 1)==make(ExifLong 1))++","++boolean(make(ExifLong 1)==make(ExifLong 2))++"]")]
 "endian" -> p (get :: Get Endianness) (value encode)
 "header" -> p (get :: Get TiffHeader) (value encode)
 "type" -> p (getP endian :: Get IfdType) (value enc)
 "field" -> p (getP endian :: Get ImageFileDirectory) (value enc)
 "directory" -> p (getP endian :: Get [ImageFileDirectory]) (value enc)
 "document" -> p (getP raw :: Get (TiffHeader,[[ImageFileDirectory]])) (value (\v -> runPut $ putP B.empty v))
 "extended" -> p (skip (fromInteger (ns!!0)) >> (getP (endian,fromInteger(ns!!1)::Int,head (head fields)) :: Get ExifData)) (value (\v -> runPut $ putP (endian,0::Int,head(head fields)) v))
 "word16" -> p (getP endian :: Get Word16) (value enc)
 "word32" -> p (getP endian :: Get Word32) (value enc)
 "int32" -> p (getP endian :: Get Int32) (value enc)
 "planar" -> p (planarConfgOfConstant (fromInteger(head ns))) (\v -> [("code",show(constantToPlaneConfiguration v))])
 "compression" -> p (unPackCompression (fromInteger(head ns))) (\v -> [("code",show(packCompression v))])
 "sample" -> p (unpackSampleFormat (fromInteger(head ns))) (\v -> [("code",show(packSampleFormat v))])
 "color" -> p (unpackPhotometricInterpretation (fromInteger(head ns))) (\v -> [("code",show(packPhotometricInterpretation v))])
 "predictor" -> p (predictorOfConstant (fromInteger(head ns))) (\v -> [("code",if v==PredictorNone then "1" else "2")])
 "extra" -> obj [("code",show(codeOfExtraSample ([ExtraSampleUnspecified,ExtraSampleAssociatedAlpha,ExtraSampleUnassociatedAlpha]!!fromInteger(head ns))))]
 "writeHeader" -> obj $ value encode (TiffHeader endian (fromInteger(head ns)))
 "writeField" -> obj $ value enc (head(head fields))
 "writeDirectory" -> obj $ value enc (head fields)
 "writeDocument" -> obj $ value (\v -> runPut $ putP raw v) (TiffHeader endian (fromInteger(head ns)),fields)
 "writeExtended" -> let fd=head(head fields) in obj $ value (\v -> runPut $ putP (endian,0::Int,fd) v) (ifdExtended fd)
 "parseExif" -> case runGetOrFail (J.parseFrameOfKind (J.JpgAppSegment 1)) (runPut $ putWord16be (fromIntegral (B.length raw+2)) >> putByteString raw) of
    Right (_,_,Just (J.JpgExif fs)) -> obj [("show",show("Just "++show fs))]
    _ -> obj [("show",show "Nothing")]
 "writeExif" -> obj [("encoded",packed $ L.drop 2 $ L.take (L.length encoded-2) encoded)]
 _ -> error ("unknown operation "++op)
 where
  raw=B.pack(unhex bytes); input=L.fromStrict raw
  unhex []=[]
  unhex (a:b:rest)=fst(head(readHex [a,b])):unhex rest
  unhex _=error "odd hex"
  endian=if en==0 then EndianLittle else EndianBig
  fields=map (map field) rows
  encoded=encode (J.JpgImage [J.JpgExif (head fields)])
  p parser describe = parse parser describe input
  enc a = runPut (putP endian a)
  boolean True="true"; boolean False="false"
  commas []=""; commas [x]=x; commas (x:xs)=x++","++commas xs
main = do
 args<-getArgs
 if args==["--reference-info"] then print [compilerName,showVersion compilerVersion,"JuicyPixels","3.3.9","binary",VERSION_binary]
 else getContents >>= mapM_ respond . lines
 where respond line = do
        result<-try(evaluate(force(run(read line)))) :: IO (Either SomeException String)
        putStrLn $ either (\e -> obj [("exception",show (takeWhile (/='\n') (displayException e)))]) id result
