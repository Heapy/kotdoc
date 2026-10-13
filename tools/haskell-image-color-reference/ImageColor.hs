{-# LANGUAGE CPP #-}
module Main where
import Codec.Picture.Types
import Codec.Picture.Jpg(encodeJpegAtQuality)
import qualified Data.Vector.Storable as V
import qualified Data.ByteString.Lazy as L
import Control.Exception(SomeException,try,evaluate,displayException)
import Control.DeepSeq(force)
import System.Environment(getArgs)
import System.Info(compilerVersion)
import Data.Version(showVersion)
import Data.Word(Word8)
import Numeric(showHex)

type Request=(String,String,Int,Int,[Int])
hex :: [Word8] -> String
hex=concatMap(\v->let s=showHex v "" in if length s==1 then '0':s else s)
process :: Request -> String
process(mode,direction,w,h,values)=
 let input=if mode=="exhaustive-image" || mode=="exhaustive-pixel"
           then V.generate (256*256*3) (\i->fromIntegral$case i `mod` 3 of 0->head values;1->i `div` 768;_->(i `div` 3) `mod` 256)
           else V.fromList(map fromIntegral values)
     rgb=Image w h input :: Image PixelRGB8
     ycc=Image w h input :: Image PixelYCbCr8
     pixels=mode=="pixel" || mode=="exhaustive-pixel"
     toYcc=if pixels then pixelMap convertPixel rgb else convertImage rgb :: Image PixelYCbCr8
     toRgb=if pixels then pixelMap convertPixel ycc else convertImage ycc :: Image PixelRGB8
     result=if mode=="jpeg" then L.unpack(encodeJpegAtQuality 100 toYcc)
            else V.toList(if direction=="rgb" then imageData toYcc else imageData toRgb)
     (rw,rh)=if direction=="rgb" then (imageWidth toYcc,imageHeight toYcc) else (imageWidth toRgb,imageHeight toRgb)
 in "{\"width\":"++show rw++",\"height\":"++show rh++",\"hex\":\""++hex result++"\"}"
main=do
 args<-getArgs
 if args==["--reference-info"] then print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector]
 else getContents >>= mapM_ respond . lines
 where respond s=do
        result<-try(evaluate(force(process(read s))))::IO(Either SomeException String)
        putStrLn$either(\e->"{\"exception\":"++show(takeWhile(/='\n')(displayException e))++"}")id result
