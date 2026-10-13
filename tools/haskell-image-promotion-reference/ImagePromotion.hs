{-# LANGUAGE CPP, FlexibleContexts, TypeFamilies #-}
module Main where
import Codec.Picture.Types
import qualified Data.Vector.Storable as V
import Control.Exception(SomeException,try,evaluate,displayException)
import Control.DeepSeq(force)
import System.Environment(getArgs)
import System.Info(compilerVersion)
import System.Mem.StableName(makeStableName)
import Data.Version(showVersion)
import Data.Word(Word8,Word16,Word32)
import GHC.Float(castWord32ToFloat,castFloatToWord32)
import Numeric(showHex)

type Request=(String,String,String,Int,Int,[Integer])
hex :: [Word8] -> String
hex=concatMap(\v->let s=showHex v "" in if length s==1 then '0':s else s)
emit w h bytes="{\"width\":"++show w++",\"height\":"++show h++",\"hex\":\""++hex bytes++"\"}"
emit8 :: (PixelBaseComponent p ~ Word8) => Image p -> String
emit8 (Image w h pixels)=emit w h(V.toList pixels)
emit16 :: (PixelBaseComponent p ~ Word16) => Image p -> String
emit16 (Image w h pixels)=emit w h(concatMap(\v->[fromIntegral v,fromIntegral(v `div` 256)])(V.toList pixels))
bytes32 :: Word32 -> [Word8]
bytes32 v=[fromIntegral(v `div` 2^n)|n<-[0,8,16,24]]
emitF :: (PixelBaseComponent p ~ Float) => Image p -> String
emitF (Image w h pixels)=emit w h(concatMap(bytes32.castFloatToWord32)(V.toList pixels))

-- Deterministic input generation only, with no promotion formulas.
samples source first i=case source of
 "y8"->[i `mod` 256]
 "y16"->[i `mod` 65536]
 "ya8"->[i `div` 256,i `mod` 256]
 "ya16"->[i,(i*257+first) `mod` 65536]
 "rgb8"->[first,i `div` 256,i `mod` 256]
 "rgb16"->[i,(i*257+first) `mod` 65536,65535-i]
 "rgba8"->[i `div` 256,i `mod` 256,255-i `div` 256,(i*73+first) `mod` 256]
 _->error "unknown generated input"
process :: Request -> String
process(op,kind,path,w,h,values)=
 let source=takeWhile(/='-')op
     input=if kind=="ramp" then concatMap(samples source(head values))[0..fromIntegral(w*h)-1] else values
     v8=V.fromList(map fromIntegral input)
     v16=V.fromList(map fromIntegral input)
     vf=V.fromList(map(castWord32ToFloat.fromIntegral)input)
     promote :: ColorConvertible a b => Image a -> Image b
     promote=if path=="pixel" then pixelMap promotePixel else promoteImage
 in case op of
  "y8-ya8"->emit8(promote(Image w h v8::Image Pixel8)::Image PixelYA8)
  "y8-yf"->emitF(promote(Image w h v8::Image Pixel8)::Image PixelF)
  "y8-y16"->emit16(promote(Image w h v8::Image Pixel8)::Image Pixel16)
  "y8-rgb8"->emit8(promote(Image w h v8::Image Pixel8)::Image PixelRGB8)
  "y8-rgb16"->emit16(promote(Image w h v8::Image Pixel8)::Image PixelRGB16)
  "y8-rgba8"->emit8(promote(Image w h v8::Image Pixel8)::Image PixelRGBA8)
  "y16-ya16"->emit16(promote(Image w h v16::Image Pixel16)::Image PixelYA16)
  "y16-rgb16"->emit16(promote(Image w h v16::Image Pixel16)::Image PixelRGB16)
  "y16-rgba16"->emit16(promote(Image w h v16::Image Pixel16)::Image PixelRGBA16)
  "yf-rgbf"->emitF(promote(Image w h vf::Image PixelF)::Image PixelRGBF)
  "ya8-rgb8"->emit8(promote(Image w h v8::Image PixelYA8)::Image PixelRGB8)
  "ya8-rgb16"->emit16(promote(Image w h v8::Image PixelYA8)::Image PixelRGB16)
  "ya8-rgba8"->emit8(promote(Image w h v8::Image PixelYA8)::Image PixelRGBA8)
  "ya16-rgb16"->emit16(promote(Image w h v16::Image PixelYA16)::Image PixelRGB16)
  "ya16-rgba16"->emit16(promote(Image w h v16::Image PixelYA16)::Image PixelRGBA16)
  "rgb16-rgba16"->emit16(promote(Image w h v16::Image PixelRGB16)::Image PixelRGBA16)
  "rgb8-rgba8"->emit8(promote(Image w h v8::Image PixelRGB8)::Image PixelRGBA8)
  "rgb8-rgbf"->emitF(promote(Image w h v8::Image PixelRGB8)::Image PixelRGBF)
  "rgb8-rgb16"->emit16(promote(Image w h v8::Image PixelRGB8)::Image PixelRGB16)
  "rgb8-rgba16"->emit16(promote(Image w h v8::Image PixelRGB8)::Image PixelRGBA16)
  "rgba8-rgba16"->emit16(promote(Image w h v8::Image PixelRGBA8)::Image PixelRGBA16)
  _->error "unknown promotion"

shared :: (a -> a) -> (a -> String) -> a -> IO String
shared f render original=do
 a<-evaluate original
 b<-evaluate(f a)
 sa<-makeStableName a
 sb<-makeStableName b
 let json=render b
 pure(init json++",\"shared\":"++(if sa==sb then "true" else "false")++"}")
identityImage :: Pixel p => String -> Image p -> Image p
identityImage path=if path=="promote-image" then promoteImage else convertImage
processIO request@(op,_,path,w,h,values)=case op of
 "identity8"->shared(identityImage path)emit8(Image w h(V.fromList(map fromIntegral values))::Image PixelRGB8)
 "identity16"->shared(identityImage path)emit16(Image w h(V.fromList(map fromIntegral values))::Image PixelRGB16)
 "identityf"->shared(identityImage path)emitF(Image w h(V.fromList(map(castWord32ToFloat.fromIntegral)values))::Image PixelRGBF)
 "identity-pixel"->let [r,g,b]=map fromIntegral values
                       render(PixelRGB8 x y z)=emit 1 1[x,y,z]
                       ident=if path=="promote-pixel" then promotePixel else convertPixel
                   in shared ident render(PixelRGB8 r g b)
 _->pure(process request)
main=do
 args<-getArgs
 if args==["--reference-info"] then print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector]
 else getContents >>= mapM_ respond . lines
 where respond s=do
        result<-try(processIO(read s)>>=evaluate.force)::IO(Either SomeException String)
        putStrLn$either(\e->"{\"exception\":"++show(takeWhile(/='\n')(displayException e))++"}")id result
