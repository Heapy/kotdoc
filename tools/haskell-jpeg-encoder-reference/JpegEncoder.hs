{-# LANGUAGE CPP #-}
module Main where
import Codec.Picture.Jpg
import Codec.Picture.Jpg.Internal.FastDct
import Codec.Picture.Types
import Codec.Picture.Metadata.Exif
import qualified Codec.Picture.Metadata as Met
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as L
import qualified Data.Vector.Storable as V
import Control.Monad.ST(runST)
import Control.Exception(SomeException,try,evaluate,displayException)
import Control.DeepSeq(force)
import System.Environment(getArgs)
import System.Info(compilerVersion)
import Data.Version(showVersion)

type Request=(String,String,[Int],[Int],[Integer],[Int])
metas dpi exif=addExif $ case dpi of
 []->mempty
 [x]->Met.insert Met.DpiX (fromInteger x) mempty
 [x,y]->Met.insert Met.DpiX (fromInteger x) $ Met.insert Met.DpiY (fromInteger y) mempty
 _->error "bad dpi"
 where addExif m=if null exif then m else Met.insert (Met.Exif(TagUnknown 65000)) (ExifString(B.pack(map fromIntegral exif))) m
encode color w h q values meta=case color of
 "Y8"->encodeDirectJpegAtQualityWithMetadata(fromIntegral q)meta(Image w h vec::Image Pixel8)
 "YCbCr8"->encodeDirectJpegAtQualityWithMetadata(fromIntegral q)meta(Image w h vec::Image PixelYCbCr8)
 "RGB8"->encodeDirectJpegAtQualityWithMetadata(fromIntegral q)meta(Image w h vec::Image PixelRGB8)
 "CMYK8"->encodeDirectJpegAtQualityWithMetadata(fromIntegral q)meta(Image w h vec::Image PixelCMYK8)
 _->error "bad color"
 where vec=V.fromList(map fromIntegral values)
process :: Request -> String
process(op,color,dims,values,dpi,exif)
 | op=="dct"=let result=runST $ do
                  input<-V.thaw(V.fromList(map fromIntegral values))
                  work<-V.thaw(V.replicate 64 0)
                  result<-fastDctLibJpeg work input
                  out<-V.freeze result
                  after<-V.freeze input
                  pure (V.toList out,V.toList after)
             in "{\"coefficients\":"++show(fst result)++",\"input\":"++show(snd result)++"}"
 | otherwise="{\"encoded\":"++show(L.unpack(encode color (dims!!0)(dims!!1)(dims!!2)values(metas dpi exif)))++"}"
file quality path output=do
 input<-B.readFile path
 case decodeJpeg input of
  Left err->error err
  Right im->do
   let q=fromIntegral quality
       encoded=case im of
        ImageY8 i->encodeDirectJpegAtQualityWithMetadata q mempty i
        ImageRGB8 i->encodeDirectJpegAtQualityWithMetadata q mempty i
        ImageYCbCr8 i->encodeDirectJpegAtQualityWithMetadata q mempty i
        ImageCMYK8 i->encodeDirectJpegAtQualityWithMetadata q mempty i
        _->error "unsupported original image"
   L.writeFile output encoded
   pure "{}"
respond action=do
 result<-try(action >>= evaluate . force)::IO(Either SomeException String)
 putStrLn $ either (\e->"{\"exception\":"++show(takeWhile (/='\n')(displayException e))++"}") id result
main=do
 args<-getArgs
 case args of
  ["--reference-info"]->print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector]
  ["--file",quality,path,out]->respond(file(read quality::Int)path out)
  _->getContents >>= mapM_ (\s->respond(pure(process(read s)))) . lines
