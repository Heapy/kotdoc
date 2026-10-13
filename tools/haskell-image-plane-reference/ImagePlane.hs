{-# LANGUAGE CPP, FlexibleContexts, TypeFamilies #-}
module Main where
import Codec.Picture.Types
import qualified Data.Vector.Storable as V
import Control.Exception(SomeException,try,evaluate,displayException)
import Control.DeepSeq(force)
import System.Environment(getArgs)
import System.Info(compilerVersion)
import System.Mem.StableName(makeStableName,eqStableName)
import Data.Version(showVersion)
import Data.Word(Word8,Word16,Word32)
import GHC.Float(castWord32ToFloat,castFloatToWord32)
import Numeric(showHex)

type Request=(String,String,String,String,Int,Int,[Integer])
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

emit32 :: (PixelBaseComponent p ~ Word32) => Image p -> String
emit32 (Image w h pixels)=emit w h(concatMap bytes32(V.toList pixels))

observe :: (a -> b) -> (b -> String) -> a -> IO String
observe f render original=do
 a<-evaluate original
 b<-evaluate(f a)
 sa<-makeStableName a
 sb<-makeStableName b
 let json=render b
 pure(init json++",\"shared\":"++(if eqStableName sa sb then "true" else "false")++"}")

samples typ kind first count
 | kind=="ya8-upstream"=concat[[i `mod` 256,((i `div` 256 `div` 4) `mod` 2)*255]|i<-[0..count-1]]
 | kind=="rgb-domain"=concat[[first,i `div` 256,i `mod` 256]++[ (i*73+first) `mod` 256 | typ=="rgba8"]|i<-[0..count-1]]
 | otherwise=concat[[ (i*(2*c+1)+first+c) `mod` limit |c<-[0..components-1]]|i<-[0..count-1]]
 where
  components=case typ of
   "ya8"->2;"ya16"->2;"rgb8"->3;"rgb16"->3;"rgbf"->3;"ycbcr8"->3;"rgba8"->4;"rgba16"->4;"cmyk8"->4;"cmyk16"->4;"ycbcrk8"->4;_->1
  limit=if typ `elem` ["y16","ya16","rgb16","rgba16","cmyk16"] then 65536 else 256

processIO :: Request -> IO String
processIO(op,typ,selector,kind,w,h,values)=
 let input=if kind=="values" then values else samples typ kind (head values)(fromIntegral(w*h))
     v8=V.fromList(map fromIntegral input)
     v16=V.fromList(map fromIntegral input)
     v32=V.fromList(map fromIntegral input)
     vf=V.fromList(map(castWord32ToFloat.fromIntegral)input)
 in case (op,typ,selector) of
  ("unsafe","y8",_) -> observe(unsafeExtractComponent(read selector))emit8 (Image w h v8::Image Pixel8)
  ("luma-image","y8",_) -> observe(extractLumaPlane)emit8 (Image w h v8::Image Pixel8)
  ("luma-pixel","y8",_) -> observe(pixelMap computeLuma)emit8 (Image w h v8::Image Pixel8)
  ("unsafe","y16",_) -> observe(unsafeExtractComponent(read selector))emit16 (Image w h v16::Image Pixel16)
  ("luma-image","y16",_) -> observe(extractLumaPlane)emit16 (Image w h v16::Image Pixel16)
  ("luma-pixel","y16",_) -> observe(pixelMap computeLuma)emit16 (Image w h v16::Image Pixel16)
  ("unsafe","y32",_) -> observe(unsafeExtractComponent(read selector))emit32 (Image w h v32::Image Pixel32)
  ("luma-image","y32",_) -> observe(extractLumaPlane)emit32 (Image w h v32::Image Pixel32)
  ("luma-pixel","y32",_) -> observe(pixelMap computeLuma)emit32 (Image w h v32::Image Pixel32)
  ("unsafe","yf",_) -> observe(unsafeExtractComponent(read selector))emitF (Image w h vf::Image PixelF)
  ("luma-image","yf",_) -> observe(extractLumaPlane)emitF (Image w h vf::Image PixelF)
  ("luma-pixel","yf",_) -> observe(pixelMap computeLuma)emitF (Image w h vf::Image PixelF)
  ("unsafe","ya8",_) -> observe(unsafeExtractComponent(read selector))emit8 (Image w h v8::Image PixelYA8)
  ("named","ya8","luma") -> observe(extractComponent PlaneLuma)emit8 (Image w h v8::Image PixelYA8)
  ("named","ya8","alpha") -> observe(extractComponent PlaneAlpha)emit8 (Image w h v8::Image PixelYA8)
  ("luma-image","ya8",_) -> observe(extractLumaPlane)emit8 (Image w h v8::Image PixelYA8)
  ("luma-pixel","ya8",_) -> observe(pixelMap computeLuma)emit8 (Image w h v8::Image PixelYA8)
  ("drop-image","ya8",_) -> observe(dropAlphaLayer)emit8 (Image w h v8::Image PixelYA8)
  ("drop-pixel","ya8",_) -> observe(pixelMap dropTransparency)emit8 (Image w h v8::Image PixelYA8)
  ("alpha","ya8",_) -> observe(pixelMap getTransparency)emit8 (Image w h v8::Image PixelYA8)
  ("opacity","ya8",_) -> observe(pixelMap pixelOpacity)emit8 (Image w h v8::Image PixelYA8)
  ("unsafe","ya16",_) -> observe(unsafeExtractComponent(read selector))emit16 (Image w h v16::Image PixelYA16)
  ("named","ya16","luma") -> observe(extractComponent PlaneLuma)emit16 (Image w h v16::Image PixelYA16)
  ("named","ya16","alpha") -> observe(extractComponent PlaneAlpha)emit16 (Image w h v16::Image PixelYA16)
  ("drop-image","ya16",_) -> observe(dropAlphaLayer)emit16 (Image w h v16::Image PixelYA16)
  ("drop-pixel","ya16",_) -> observe(pixelMap dropTransparency)emit16 (Image w h v16::Image PixelYA16)
  ("alpha","ya16",_) -> observe(pixelMap getTransparency)emit16 (Image w h v16::Image PixelYA16)
  ("opacity","ya16",_) -> observe(pixelMap pixelOpacity)emit16 (Image w h v16::Image PixelYA16)
  ("unsafe","rgb8",_) -> observe(unsafeExtractComponent(read selector))emit8 (Image w h v8::Image PixelRGB8)
  ("named","rgb8","red") -> observe(extractComponent PlaneRed)emit8 (Image w h v8::Image PixelRGB8)
  ("named","rgb8","green") -> observe(extractComponent PlaneGreen)emit8 (Image w h v8::Image PixelRGB8)
  ("named","rgb8","blue") -> observe(extractComponent PlaneBlue)emit8 (Image w h v8::Image PixelRGB8)
  ("luma-image","rgb8",_) -> observe(extractLumaPlane)emit8 (Image w h v8::Image PixelRGB8)
  ("luma-pixel","rgb8",_) -> observe(pixelMap computeLuma)emit8 (Image w h v8::Image PixelRGB8)
  ("unsafe","rgb16",_) -> observe(unsafeExtractComponent(read selector))emit16 (Image w h v16::Image PixelRGB16)
  ("named","rgb16","red") -> observe(extractComponent PlaneRed)emit16 (Image w h v16::Image PixelRGB16)
  ("named","rgb16","green") -> observe(extractComponent PlaneGreen)emit16 (Image w h v16::Image PixelRGB16)
  ("named","rgb16","blue") -> observe(extractComponent PlaneBlue)emit16 (Image w h v16::Image PixelRGB16)
  ("luma-image","rgb16",_) -> observe(extractLumaPlane)emit16 (Image w h v16::Image PixelRGB16)
  ("luma-pixel","rgb16",_) -> observe(pixelMap computeLuma)emit16 (Image w h v16::Image PixelRGB16)
  ("unsafe","rgbf",_) -> observe(unsafeExtractComponent(read selector))emitF (Image w h vf::Image PixelRGBF)
  ("named","rgbf","red") -> observe(extractComponent PlaneRed)emitF (Image w h vf::Image PixelRGBF)
  ("named","rgbf","green") -> observe(extractComponent PlaneGreen)emitF (Image w h vf::Image PixelRGBF)
  ("named","rgbf","blue") -> observe(extractComponent PlaneBlue)emitF (Image w h vf::Image PixelRGBF)
  ("luma-image","rgbf",_) -> observe(extractLumaPlane)emitF (Image w h vf::Image PixelRGBF)
  ("luma-pixel","rgbf",_) -> observe(pixelMap computeLuma)emitF (Image w h vf::Image PixelRGBF)
  ("unsafe","rgba8",_) -> observe(unsafeExtractComponent(read selector))emit8 (Image w h v8::Image PixelRGBA8)
  ("named","rgba8","red") -> observe(extractComponent PlaneRed)emit8 (Image w h v8::Image PixelRGBA8)
  ("named","rgba8","green") -> observe(extractComponent PlaneGreen)emit8 (Image w h v8::Image PixelRGBA8)
  ("named","rgba8","blue") -> observe(extractComponent PlaneBlue)emit8 (Image w h v8::Image PixelRGBA8)
  ("named","rgba8","alpha") -> observe(extractComponent PlaneAlpha)emit8 (Image w h v8::Image PixelRGBA8)
  ("luma-image","rgba8",_) -> observe(extractLumaPlane)emit8 (Image w h v8::Image PixelRGBA8)
  ("luma-pixel","rgba8",_) -> observe(pixelMap computeLuma)emit8 (Image w h v8::Image PixelRGBA8)
  ("drop-image","rgba8",_) -> observe(dropAlphaLayer)emit8 (Image w h v8::Image PixelRGBA8)
  ("drop-pixel","rgba8",_) -> observe(pixelMap dropTransparency)emit8 (Image w h v8::Image PixelRGBA8)
  ("alpha","rgba8",_) -> observe(pixelMap getTransparency)emit8 (Image w h v8::Image PixelRGBA8)
  ("opacity","rgba8",_) -> observe(pixelMap pixelOpacity)emit8 (Image w h v8::Image PixelRGBA8)
  ("unsafe","rgba16",_) -> observe(unsafeExtractComponent(read selector))emit16 (Image w h v16::Image PixelRGBA16)
  ("named","rgba16","red") -> observe(extractComponent PlaneRed)emit16 (Image w h v16::Image PixelRGBA16)
  ("named","rgba16","green") -> observe(extractComponent PlaneGreen)emit16 (Image w h v16::Image PixelRGBA16)
  ("named","rgba16","blue") -> observe(extractComponent PlaneBlue)emit16 (Image w h v16::Image PixelRGBA16)
  ("named","rgba16","alpha") -> observe(extractComponent PlaneAlpha)emit16 (Image w h v16::Image PixelRGBA16)
  ("drop-image","rgba16",_) -> observe(dropAlphaLayer)emit16 (Image w h v16::Image PixelRGBA16)
  ("drop-pixel","rgba16",_) -> observe(pixelMap dropTransparency)emit16 (Image w h v16::Image PixelRGBA16)
  ("alpha","rgba16",_) -> observe(pixelMap getTransparency)emit16 (Image w h v16::Image PixelRGBA16)
  ("opacity","rgba16",_) -> observe(pixelMap pixelOpacity)emit16 (Image w h v16::Image PixelRGBA16)
  ("unsafe","ycbcr8",_) -> observe(unsafeExtractComponent(read selector))emit8 (Image w h v8::Image PixelYCbCr8)
  ("named","ycbcr8","luma") -> observe(extractComponent PlaneLuma)emit8 (Image w h v8::Image PixelYCbCr8)
  ("named","ycbcr8","cb") -> observe(extractComponent PlaneCb)emit8 (Image w h v8::Image PixelYCbCr8)
  ("named","ycbcr8","cr") -> observe(extractComponent PlaneCr)emit8 (Image w h v8::Image PixelYCbCr8)
  ("luma-image","ycbcr8",_) -> observe(extractLumaPlane)emit8 (Image w h v8::Image PixelYCbCr8)
  ("luma-pixel","ycbcr8",_) -> observe(pixelMap computeLuma)emit8 (Image w h v8::Image PixelYCbCr8)
  ("unsafe","ycbcrk8",_) -> observe(unsafeExtractComponent(read selector))emit8 (Image w h v8::Image PixelYCbCrK8)
  ("unsafe","cmyk8",_) -> observe(unsafeExtractComponent(read selector))emit8 (Image w h v8::Image PixelCMYK8)
  ("named","cmyk8","cyan") -> observe(extractComponent PlaneCyan)emit8 (Image w h v8::Image PixelCMYK8)
  ("named","cmyk8","magenta") -> observe(extractComponent PlaneMagenta)emit8 (Image w h v8::Image PixelCMYK8)
  ("named","cmyk8","yellow") -> observe(extractComponent PlaneYellow)emit8 (Image w h v8::Image PixelCMYK8)
  ("named","cmyk8","black") -> observe(extractComponent PlaneBlack)emit8 (Image w h v8::Image PixelCMYK8)
  ("unsafe","cmyk16",_) -> observe(unsafeExtractComponent(read selector))emit16 (Image w h v16::Image PixelCMYK16)
  ("named","cmyk16","cyan") -> observe(extractComponent PlaneCyan)emit16 (Image w h v16::Image PixelCMYK16)
  ("named","cmyk16","magenta") -> observe(extractComponent PlaneMagenta)emit16 (Image w h v16::Image PixelCMYK16)
  ("named","cmyk16","yellow") -> observe(extractComponent PlaneYellow)emit16 (Image w h v16::Image PixelCMYK16)
  ("named","cmyk16","black") -> observe(extractComponent PlaneBlack)emit16 (Image w h v16::Image PixelCMYK16)
  _ -> error "unknown plane/luma/transparency request"
main=do
 args<-getArgs
 if args==["--reference-info"] then print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector]
 else getContents >>= mapM_ respond . lines
 where respond s=do
        result<-try(processIO(read s)>>=evaluate.force)::IO(Either SomeException String)
        putStrLn$either(\e->"{\"exception\":"++show(takeWhile(/='\n')(displayException e))++"}")id result
