{-# LANGUAGE CPP, FlexibleContexts, TypeFamilies #-}
module Main where
import Codec.Picture.Types
import qualified Data.Vector.Storable as V
import Control.Exception(SomeException,try,evaluate,displayException)
import Control.DeepSeq(force)
import System.Environment(getArgs)
import System.Info(compilerVersion)
import Data.Version(showVersion)
import Data.Word(Word8,Word16)
import Numeric(showHex)

type Request=(String,String,String,Int,Int,[Int])
hex :: [Word8] -> String
hex=concatMap(\v->let s=showHex v "" in if length s==1 then '0':s else s)
emit w h bytes="{\"width\":"++show w++",\"height\":"++show h++",\"hex\":\""++hex bytes++"\"}"
emit8 :: (PixelBaseComponent p ~ Word8) => Image p -> String
emit8 (Image w h pixels)=emit w h(V.toList pixels)
emit16 :: (PixelBaseComponent p ~ Word16) => Image p -> String
emit16 (Image w h pixels)=emit w h(concatMap(\v->[fromIntegral v,fromIntegral(v `div` 256)])(V.toList pixels))

-- Only input generation and output serialization live in this adapter.
samples op first i = case op of
 "rgb8"->[first,i `div` 256,i `mod` 256]
 "cmyk8"->let c=i `div` 256 in [c,255-c,(c*73) `mod` 256,i `mod` 256]
 "rgb16"->[i,(i*257+first) `mod` 65536,65535-i]
 "cmyk16"->[i,65535-i,(i*257) `mod` 65536,first]
 _->[first,i `div` 256,i `mod` 256,(i*73+first) `mod` 256]
process :: Request -> String
process(op,kind,path,w,h,values)=
 let input=if kind=="sweep" then concatMap(samples op(head values))[0..65535] else values
     bytes=V.fromList(map fromIntegral input)
     words16=V.fromList(map fromIntegral input)
     conv :: ColorSpaceConvertible a b => Image a -> Image b
     conv=if path=="pixel" then pixelMap convertPixel else convertImage
 in case op of
  "rgb8"->emit8(conv(Image w h bytes :: Image PixelRGB8)::Image PixelCMYK8)
  "cmyk8"->emit8(conv(Image w h bytes :: Image PixelCMYK8)::Image PixelRGB8)
  "rgb16"->emit16(conv(Image w h words16 :: Image PixelRGB16)::Image PixelCMYK16)
  "cmyk16"->emit16(conv(Image w h words16 :: Image PixelCMYK16)::Image PixelRGB16)
  "ycck-rgb"->emit8(conv(Image w h bytes :: Image PixelYCbCrK8)::Image PixelRGB8)
  "ycck-cmyk"->emit8(conv(Image w h bytes :: Image PixelYCbCrK8)::Image PixelCMYK8)
  _->error "unknown operation"
main=do
 args<-getArgs
 if args==["--reference-info"] then print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector]
 else getContents >>= mapM_ respond . lines
 where respond s=do
        result<-try(evaluate(force(process(read s))))::IO(Either SomeException String)
        putStrLn$either(\e->"{\"exception\":"++show(takeWhile(/='\n')(displayException e))++"}")id result
