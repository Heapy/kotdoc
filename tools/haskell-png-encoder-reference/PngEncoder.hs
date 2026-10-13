{-# LANGUAGE CPP, GADTs, FlexibleContexts, TypeFamilies, ScopedTypeVariables #-}
{-# OPTIONS_GHC -fno-specialise -fno-spec-constr #-}
module Main where
import Codec.Picture.Png
import Codec.Picture.Types
import Codec.Picture.Metadata.Exif
import qualified Codec.Picture.Metadata as M
import qualified Data.Vector as V
import qualified Data.Vector.Storable as VS
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as L
import GHC.Float(castWord64ToDouble)
import Data.Char(chr,ord)
import Data.Word(Word8,Word16)
import Numeric(showHex)
import Control.Exception(SomeException,try,evaluate,displayException)
import Control.DeepSeq(force)
import System.Environment(getArgs)
import System.IO(openBinaryTempFile,hClose)
import System.Directory(removeFile)
import System.Info(compilerVersion)
import Data.Version(showVersion)
type Entry=(String,String,String,[Integer])
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

type Request=(String,String,String,String,[Integer],[Integer],[Integer],[Entry])
hex :: [Word8] -> String
hex=concatMap(\v->let s=showHex v "" in if length s==1 then '0':s else s)
field k v=show k++":"++v
object []="{}"
object(x:xs)="{"++x++concatMap(',':)xs++"}"
text=show.map ord
result observe out=case out of
 Left e->object[field "error"(text e)]
 Right b->if observe=="either" then "{}" else object[field "hex"(show(hex(L.unpack b)))]
runTyped :: forall p. (PngSavable p,Pixel p,Integral(PixelBaseComponent p)) => (Image p->DynamicImage) -> Request -> String
runTyped wrap (op,typ,mode,observe,params,values,paletteValues,entries)=result observe out
 where
 [w,h,offset,len,_,_,_,_]=take 8(map fromIntegral(params++repeat 0))
 vec=if mode=="bad-data" then error "image data forced" else VS.slice offset len(VS.fromList(map fromInteger values))
 src=if mode=="bad-image" then error "image forced" else Image w h vec::Image p
 metadata=if mode=="bad-metadata" then error "metadata forced" else metas entries
 out=if op=="dynamic" then encodeDynamicPng(wrap src) else Right(encodePngWithMetadata metadata src)
runPalette :: forall p. (PngPaletteSaveable p,Pixel p,Integral(PixelBaseComponent p)) => p -> Request -> String
runPalette witness (_,typ,mode,observe,params,values,paletteValues,entries)=result observe out
 where
 [w,h,offset,len,pw,ph,po,pl]=take 8(map fromIntegral(params++repeat 0))
 src=if mode=="bad-image" then error "image forced" else Image w h (if mode=="bad-data" then error "image data forced" else VS.slice offset len(VS.fromList(map fromInteger values)))::Image Pixel8
 pal=if mode=="bad-palette" then error "palette forced" else Image pw ph (if mode=="bad-palette-data" then error "palette data forced" else VS.slice po pl(VS.fromList(map fromInteger paletteValues)))::Image p
 metadata=if mode=="bad-metadata" then error "metadata forced" else metas entries
 out=encodePalettedPngWithMetadata metadata pal src
process :: Request -> String
process req@(op,typ,mode,observe,params,values,paletteValues,entries)
 | op=="palette" = case typ of
    "rgb8"->runPalette(undefined::PixelRGB8)req
    "rgba8"->runPalette(undefined::PixelRGBA8)req
 | otherwise = case typ of
    "y8"->runTyped ImageY8 req
    "y16"->runTyped ImageY16 req
    "ya8"->runTyped ImageYA8 req
    "ya16"->runTyped ImageYA16 req
    "rgb8"->runTyped ImageRGB8 req
    "rgb16"->runTyped ImageRGB16 req
    "rgba8"->runTyped ImageRGBA8 req
    "rgba16"->runTyped ImageRGBA16 req
    _->result observe(encodeDynamicPng(unsupported typ))
 where
 unsupported "y32"=ImageY32(error "image forced")
 unsupported "yf"=ImageYF(error "image forced")
 unsupported "rgbf"=ImageRGBF(error "image forced")
 unsupported "ycbcr8"=ImageYCbCr8(error "image forced")
 unsupported "cmyk8"=ImageCMYK8(error "image forced")
 unsupported "cmyk16"=ImageCMYK16(error "image forced")
processBytes (op,_,_,observe,params,_,_,_) bytes=case decodePng bytes of
 Left e->object[field "error"(text e)]
 Right img->if op=="transcode" then result observe(encodeDynamicPng img) else
   let component=fromInteger(head params)
   in result observe(Right(case img of
      ImageRGB8 i->encodePng(unsafeExtractComponent component i)
      ImageRGBA8 i->encodePng(unsafeExtractComponent component i)
      _->error "unexpected plane input"))
fileProbe mode=do
 (path,handle)<-openBinaryTempFile "." "kotdoc-png-probe"
 hClose handle
 B.writeFile path(B.pack[9,8,7])
 r<-try(if mode=="typed" then writePng path (error "image forced"::Image PixelRGB8) else writeDynamicPng path(ImageRGB8(error "image forced"))>>pure())::IO(Either SomeException ())
 bytes<-B.readFile path
 removeFile path
 pure(object[field "exception"(text(either(takeWhile(/='\n').displayException)(const "no exception")r)),field "after"(show(hex(B.unpack bytes)))])
main=do
 args<-getArgs
 if args==["--reference-info"] then print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector,"zlib",VERSION_zlib]
 else getContents >>= mapM_ respond . lines
 where respond s=do
        let req@(op,_,mode,_,_,_,_,_)=read s::Request
        r<-try(if op=="write" then fileProbe mode else if op `elem` ["transcode","plane"] then B.readFile mode >>= evaluate.force.processBytes req else evaluate(force(process req)))::IO(Either SomeException String)
        putStrLn$either(\e->object[field "exception"(text(takeWhile(/='\n')(displayException e)))])id r
