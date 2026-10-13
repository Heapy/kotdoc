{-# LANGUAGE CPP, GADTs #-}
module Main where
import Codec.Picture.Jpg
import Codec.Picture.Types
import Codec.Picture.Metadata.Exif
import qualified Codec.Picture.Metadata as M
import qualified Codec.Picture.Jpg.Internal.Metadata as JM
import qualified Codec.Picture.Jpg.Internal.Types as J
import qualified Codec.Picture.Tiff.Internal.Metadata as TM
import Codec.Picture.Tiff.Internal.Types
import Data.Binary(encode)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as L
import qualified Data.Vector as V
import qualified Data.Vector.Storable as VS
import Data.Char(chr)
import GHC.Float(castWord64ToDouble)
import Control.Exception(SomeException,try,evaluate,displayException)
import Control.DeepSeq(force)
import System.Environment(getArgs)
import System.Info(compilerVersion)
import Data.Version(showVersion)

type Entry=(String,String,String,[Integer])
type Field=(Int,Int,Integer,Integer,String,[Integer])
type Request=(String,[Entry],[Entry],[Field],[Integer])
obj xs="{"++commas [show k++":"++v |(k,v)<-xs]++"}"
commas []="";commas [x]=x;commas (x:xs)=x++","++commas xs
str=map(chr.fromInteger)
tag n | n<0=TagUnknown(fromIntegral(-n-1))
      | otherwise=tagOfWord16(fromIntegral n)
exif kind ns=case(kind,ns)of
 ("none",_)->ExifNone
 ("short",[n])->ExifShort(fromIntegral n)
 ("long",[n])->ExifLong(fromIntegral n)
 ("shorts",_)->ExifShorts(V.fromList(map fromIntegral ns))
 ("longs",_)->ExifLongs(V.fromList(map fromIntegral ns))
 ("string",_)->ExifString(B.pack(map fromIntegral ns))
 ("undefined",_)->ExifUndefined(B.pack(map fromIntegral ns))
 ("rational",[a,b])->ExifRational(fromIntegral a)(fromIntegral b)
 ("signed",[a,b])->ExifSignedRational(fromIntegral a)(fromIntegral b)
 ("ifd",_)->ExifIFD [(tag t,ExifLong(fromIntegral v)) | (t,v)<-zip ns(drop 1 ns)]
 _->error "bad exif"
entry :: Entry -> M.Elem M.Keys
entry(k,arg,kind,ns)=case k of
 "Gamma"->M.Gamma M.:=> dbl
 "ColorSpace"->M.ColorSpace M.:=> case arg of "SRGB"->M.SRGB;"ICCProfile"->M.ICCProfile bytes;_->M.WindowsBitmapColorSpace bytes
 "Format"->M.Format M.:=> format(read arg)
 "DpiX"->M.DpiX M.:=> word
 "DpiY"->M.DpiY M.:=> word
 "Width"->M.Width M.:=> word
 "Height"->M.Height M.:=> word
 "Title"->M.Title M.:=> str ns
 "Description"->M.Description M.:=> str ns
 "Author"->M.Author M.:=> str ns
 "Copyright"->M.Copyright M.:=> str ns
 "Software"->M.Software M.:=> str ns
 "Comment"->M.Comment M.:=> str ns
 "Disclaimer"->M.Disclaimer M.:=> str ns
 "Source"->M.Source M.:=> str ns
 "Warning"->M.Warning M.:=> str ns
 "Exif"->M.Exif(tag(read arg)) M.:=> exif kind ns
 "Unknown"->M.Unknown arg M.:=> case kind of "int"->M.Int(fromInteger(head ns));"double"->M.Double dbl;_->M.String(str ns)
 _->error "bad key"
 where word=fromInteger(head ns);dbl=castWord64ToDouble(fromInteger(head ns));bytes=B.pack(map fromIntegral ns)
format n=[M.SourceJpeg,M.SourceGif,M.SourceBitmap,M.SourceTiff,M.SourcePng,M.SourceHDR,M.SourceTGA]!!n
metas=foldl (\m (k M.:=> v)->M.insert k v m) M.empty . map entry
fields=map(\(t,typ,count,offset,kind,ns)->ImageFileDirectory(tag(fromIntegral t))([TypeByte,TypeAscii,TypeShort,TypeLong,TypeRational,TypeSByte,TypeUndefined,TypeSignedShort,TypeSignedLong,TypeSignedRational,TypeFloat,TypeDouble]!!(typ-1))(fromIntegral count)(fromIntegral offset)(exif kind ns))
shown x=show(show x)
packed=show.L.unpack
run :: Request -> String
run(op,left,right,fs,ns)=case op of
 "values"->let vs=[v | M.Unknown _ M.:=> v<-map entry left]
                in obj[("show",show(map show vs)),("equal","["++commas [if x==y then "true" else "false"|x<-vs,y<-vs]++"]")]
 "colors"->let vs=[v | M.ColorSpace M.:=> v<-map entry left]
                in obj[("show",show(map show vs)),("equal","["++commas [if x==y then "true" else "false"|x<-vs,y<-vs]++"]")]
 "originalWrite"->obj[("encoded",packed(encodeDirectJpegAtQualityWithMetadata 50 a (convertImage(generateImage(\_ _->PixelRGB8 255 255 255)16 16)::Image PixelYCbCr8)))]
 "store"->obj [("left",shown a),("right",shown b),("union",shown(a<>b)),("deleted",shown(foldl(\m(k M.:=> _)->M.delete k m)a(map entry right))),
   ("lookups",show[show(M.lookup k a)|k M.:=> _<-map entry right]),("exif",shown(M.extractExifMetas(a<>b))),
   ("fold",show(M.foldl' (\acc e->acc++[show e]) [] (a<>b))), ("foldMap",show(M.foldMap(\e->[show e])(a<>b)))]
 "helpers"->obj [("dpi",shown(M.mkDpiMetadata(fromInteger(ns!!0)))),("size",shown(M.mkSizeMetadata(ns!!0)(ns!!1))),
   ("basic",shown(M.basicMetadata(format(fromInteger(ns!!4)))(ns!!0)(ns!!1))),
   ("simple",shown(M.simpleMetadata(format(fromInteger(ns!!4)))(ns!!0)(ns!!1)(ns!!2)(ns!!3))),
   ("conversions",show[[M.dotsPerMeterToDotPerInch w,M.dotPerInchToDotsPerMeter w,M.dotsPerCentiMeterToDotPerInch w]|n<-take 4 ns,let w=fromInteger n])]
 "jfif"->obj[("metadata",shown(JM.extractMetadatas(J.JpgJFIFApp0 ([J.JFifUnitUnknown,J.JFifPixelsPerInch,J.JFifPixelsPerCentimeter]!!fromInteger(ns!!0))(fromInteger(ns!!1))(fromInteger(ns!!2))Nothing)))]
 "extract"->obj[("metadata",shown(TM.extractTiffMetadata(fields fs)))]
 "encode"->let f=TM.encodeTiffStringMetadata a; frames=JM.encodeMetadatas a++if null f then [] else [J.JpgExif f]
            in obj[("fields",shown f),("frames",packed(encode(J.JpgImage frames)))]
 "jpeg"->obj[("encoded",packed(encodeDirectJpegAtQualityWithMetadata 50 a (generateImage(\_ _->PixelYCbCr8 255 128 128)16 16)))]
 "roundtrip"->let raw=encodeDirectJpegAtQualityWithMetadata 50 a (generateImage(\_ _->PixelYCbCr8 255 128 128)16 16)
                 in case decodeJpegWithMetadata(L.toStrict raw)of
                     Left e->obj[("error",show e)]
                     Right(_,m)->obj[("metadata",shown m)]
 "pixelOnly"->let raw=encodeDirectJpegAtQualityWithMetadata 50 a (generateImage(\_ _->PixelYCbCr8 255 128 128)16 16)
                 in case decodeJpeg(L.toStrict raw)of
                     Left e->obj[("error",show e)]
                     Right(ImageYCbCr8 i)->obj[("length",show(VS.length(imageData i)))]
                     _->error "unexpected image"
 "decode"->case decodeJpegWithMetadata(B.pack(map fromInteger ns))of
   Left e->obj[("error",show e)]
   Right(_,m)->obj[("metadata",shown m)]
 "decodePixels"->case decodeJpeg(B.pack(map fromInteger ns))of
   Left e->obj[("error",show e)]
   Right(ImageYCbCr8 i)->obj[("length",show(VS.length(imageData i)))]
   _->error "unexpected image"
 "offset"->obj[("field",shown TM.exifOffsetIfd)]
 _->error "bad operation"
 where a=metas left;b=metas right
file input output=do
 raw<-B.readFile input
 case decodeJpegWithMetadata raw of
  Left e->pure(obj[("error",show e)])
  Right (im,m)->do
   let encoded=case im of
        ImageY8 i->encodeDirectJpegAtQualityWithMetadata 90 m i
        ImageYCbCr8 i->encodeDirectJpegAtQualityWithMetadata 90 m i
        ImageRGB8 i->encodeDirectJpegAtQualityWithMetadata 90 m i
        ImageCMYK8 i->encodeDirectJpegAtQualityWithMetadata 90 m i
        _->error "unsupported image"
   L.writeFile output encoded
   pure(obj[("metadata",shown m)])
respond action=do
 result<-try(action >>= evaluate.force)::IO(Either SomeException String)
 putStrLn$either(\e->obj[("exception",show(takeWhile(/='\n')(displayException e)))])id result
main=do
 args<-getArgs
 case args of
  ["--reference-info"]->print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector]
  ["--file",input,output]->respond(file input output)
  _->getContents >>= mapM_(\s->respond(pure(run(read s)))) . lines
