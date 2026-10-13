{-# LANGUAGE CPP, FlexibleContexts, TypeFamilies, ScopedTypeVariables #-}
{-# LANGUAGE RankNTypes #-}
{-# OPTIONS_GHC -fno-specialise -fno-spec-constr #-}
module Main where
import Codec.Picture.Types
import qualified Data.Vector.Storable as V
import qualified Data.Vector.Storable.Mutable as M
import Control.Exception(SomeException,try,evaluate,displayException)
import Control.DeepSeq(force,rnf)
import Control.Monad(forM_,when)
import Control.Monad.ST(runST)
import GHC.Exts(RealWorld)
import Data.Word(Word8,Word16,Word32)
import Data.IORef
import System.Mem.StableName
import Data.Monoid(Sum(..),Any(..))
import Foreign.ForeignPtr(withForeignPtr)
import Foreign.Ptr(plusPtr,castPtr)
import Foreign.Storable(sizeOf)
import GHC.Float(castWord32ToFloat,castFloatToWord32)
import System.Environment(getArgs)
import System.Info(compilerVersion)
import Data.Version(showVersion)
import Numeric(showHex)

hex :: [Word8] -> String
hex=concatMap(\v->let s=showHex v "" in if length s==1 then '0':s else s)
pack8=hex.V.toList
pack16=hex.concatMap(\v->[fromIntegral v,fromIntegral(v `div` 256)]).V.toList
bytes32 :: Word32 -> [Word8]
bytes32 v=[fromIntegral(v `div` 2^n)|n<-[0,8,16,24]]
pack32=hex.concatMap bytes32.V.toList
packF=hex.concatMap(bytes32.castFloatToWord32).V.toList
err e=takeWhile(/='\n')(displayException e)
quoted s="\""++s++"\""
field key value=quoted key++":"++value
object fields="{"++joinComma fields++"}"
joinComma []=""
joinComma(x:xs)=x++concatMap(',':)xs

type Request=(String,String,String,String,[Integer],[Integer],[Integer])
imageResult :: Pixel p => String -> (V.Vector(PixelBaseComponent p)->String) -> String -> Image p -> String
imageResult tag pack observe img=object([field "tag"(quoted tag)]++if observe=="tag" then [] else [field "width"(show(imageWidth img)),field "height"(show(imageHeight img))]++if observe `elem` ["tag","metadata"] then [] else [field "hex"(quoted(pack(imageData img)))])
dynamicResult observe img=case img of
 ImageY8 i->imageResult "y8" pack8 observe i
 ImageY16 i->imageResult "y16" pack16 observe i
 ImageY32 i->imageResult "y32" pack32 observe i
 ImageYF i->imageResult "yf" packF observe i
 ImageYA8 i->imageResult "ya8" pack8 observe i
 ImageYA16 i->imageResult "ya16" pack16 observe i
 ImageRGB8 i->imageResult "rgb8" pack8 observe i
 ImageRGB16 i->imageResult "rgb16" pack16 observe i
 ImageRGBF i->imageResult "rgbf" packF observe i
 ImageRGBA8 i->imageResult "rgba8" pack8 observe i
 ImageRGBA16 i->imageResult "rgba16" pack16 observe i
 ImageYCbCr8 i->imageResult "ycbcr8" pack8 observe i
 ImageCMYK8 i->imageResult "cmyk8" pack8 observe i
 ImageCMYK16 i->imageResult "cmyk16" pack16 observe i

runDynamic :: forall p. (Pixel p, Eq(PixelBaseComponent p)) => (V.Vector(PixelBaseComponent p)->String) -> (Integer->PixelBaseComponent p) -> (Image p->DynamicImage) -> (Image Pixel8->Palette' p->PalettedImage) -> Request -> IO String
runDynamic pack convert wrap wrapPalette (op,typ,mode,observe,params,left,right)=do
 let [w,h,offset,len,size,po,pl]=take 7(map fromIntegral(params++repeat 0))
     raw=V.fromList(map convert left)
     vec=if mode `elem` ["bad-data","both-data"] then error "source data forced" else V.slice offset len raw
     src=if mode=="bad-image" then error "source image forced" else Image w h vec::Image p
     dyn=wrap src
     palette=if mode=="bad-palette" then error "palette forced" else Palette' size (if mode `elem` ["bad-palette-data","both-data"] then error "palette data forced" else V.slice po pl(V.fromList(map convert right)))::Palette' p
     indexed=if mode=="bad-image" then error "source image forced" else Image w h (if mode `elem` ["bad-data","both-data"] then error "source data forced" else V.slice offset len(V.fromList(map fromIntegral left)))::Image Pixel8
     transform :: forall px. Pixel px => Image px -> Image px
     transform img=case mode of
       "fail"->error "transform forced"
       "empty"->Image (-17) 0 V.empty
       "swap"->Image(imageHeight img)(imageWidth img)(imageData img)
       "map"->pixelMap id img
       _->img
     metric :: forall px. Pixel px => Image px -> String
     metric img=case observe of
       "constant"->"91"
       "metadata"->"["++show(imageWidth img)++","++show(imageHeight img)++"]"
       _->show(V.length(imageData img))
 case op of
  "force"->evaluate(rnf dyn)>>pure(object[field "forced" "true"])
  "map"->pure(object[field "value"(dynamicMap metric dyn)])
  "transform"->pure(dynamicResult observe(dynamicPixelMap transform dyn))
  "palette-image"->do
    let img=palettedAsImage palette
    out<-evaluate(force(imageResult typ pack observe img))
    let (a,ao,_)=V.unsafeToForeignPtr(_paletteData palette);(b,bo,_)=V.unsafeToForeignPtr(imageData img)
    pure(init out++","++field "shared"(if a==b && ao==bo then "true" else "false")++"}")
  "expand"->pure(dynamicResult observe(palettedToTrueColor(wrapPalette indexed palette)))
  "fallback"->do
    let out=palettedToTrueColor(TrueColorImage dyn)
    a<-makeStableName =<< evaluate dyn;b<-makeStableName =<< evaluate out
    result<-evaluate(force(dynamicResult observe out))
    pure(init result++","++field "same"(if a==b then "true" else "false")++"}")
  "eq"->let other=wrap(Image w h(V.fromList(map convert right))) in pure(object[field "bool"(if (if mode=="self" then dyn==dyn else dyn==other) then "true" else "false")])
  "tag-eq"->pure(object[field "bool"(if dyn==ImageRGB8(error "other image forced") then "true" else "false")])
  _->error "unknown operation"
process request@(_,typ,_,_,_,_,_)=case typ of
 "y8"->runDynamic pack8 fromIntegral ImageY8 PalettedY8 request
 "y16"->runDynamic pack16 fromIntegral ImageY16 (\_ _->error "no paletted variant") request
 "y32"->runDynamic pack32 fromIntegral ImageY32 (\_ _->error "no paletted variant") request
 "yf"->runDynamic packF (castWord32ToFloat.fromIntegral) ImageYF (\_ _->error "no paletted variant") request
 "ya8"->runDynamic pack8 fromIntegral ImageYA8 (\_ _->error "no paletted variant") request
 "ya16"->runDynamic pack16 fromIntegral ImageYA16 (\_ _->error "no paletted variant") request
 "rgb8"->runDynamic pack8 fromIntegral ImageRGB8 PalettedRGB8 request
 "rgb16"->runDynamic pack16 fromIntegral ImageRGB16 PalettedRGB16 request
 "rgbf"->runDynamic packF (castWord32ToFloat.fromIntegral) ImageRGBF (\_ _->error "no paletted variant") request
 "rgba8"->runDynamic pack8 fromIntegral ImageRGBA8 PalettedRGBA8 request
 "rgba16"->runDynamic pack16 fromIntegral ImageRGBA16 (\_ _->error "no paletted variant") request
 "ycbcr8"->runDynamic pack8 fromIntegral ImageYCbCr8 (\_ _->error "no paletted variant") request
 "ycbcrk8"->runDynamic pack8 fromIntegral (\(_::Image PixelYCbCrK8)->error "no dynamic YCbCrK8") (\_ _->error "no paletted variant") request
 "cmyk8"->runDynamic pack8 fromIntegral ImageCMYK8 (\_ _->error "no paletted variant") request
 "cmyk16"->runDynamic pack16 fromIntegral ImageCMYK16 (\_ _->error "no paletted variant") request
 _->error "unknown type"
main=do
 args<-getArgs
 if args==["--reference-info"] then print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector]
 else getContents >>= mapM_ respond . lines
 where respond s=do
        result<-try(process(read s)>>=evaluate.force)::IO(Either SomeException String)
        putStrLn$either(\e->object[field "exception"(quoted(err e))])id result
