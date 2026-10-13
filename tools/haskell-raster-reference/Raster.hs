{-# LANGUAGE CPP, FlexibleContexts, TypeFamilies, ScopedTypeVariables #-}
module Main where
import Codec.Picture.Types
import qualified Data.Vector.Storable as V
import qualified Data.Vector.Storable.Mutable as M
import Control.Exception(SomeException,try,evaluate,displayException)
import Control.DeepSeq(force)
import Control.Monad(forM_,when)
import Control.Monad.ST(runST)
import GHC.Exts(RealWorld)
import Data.Word(Word8,Word16,Word32)
import Data.IORef
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

-- Callbacks supplied to unchanged original algorithms. Integer arithmetic avoids
-- host rounding in callbacks; Float components are serialized as raw Word32.
type Request=(String,String,String,String,String,[Integer],[Integer],[Integer],[Integer])
data Bracket=Bracket String
instance Semigroup Bracket where Bracket a <> Bracket b=Bracket("("++a++"|"++b++")")
instance Monoid Bracket where mempty=Bracket "E"

one :: forall p. Pixel p => p -> V.Vector(PixelBaseComponent p)
one value=runST $ do
 out<-M.new(componentCount value)
 unsafeWritePixel out 0 value
 V.freeze out

runRaster :: forall p q. (Pixel p,Eq(PixelBaseComponent p),Pixel q)
 => (V.Vector(PixelBaseComponent p)->String) -> (Integer->PixelBaseComponent p) -> (PixelBaseComponent p->Integer) -> p
 -> (V.Vector(PixelBaseComponent q)->String) -> (Integer->PixelBaseComponent q) -> q
 -> Request -> IO String
runRaster pack convert bits proxy outPack outConvert outProxy (op,_,_,mode,observe,params,left,right,third)=do
 let [w,h,offset,len,seed,w2,h2]=take 7(map fromIntegral(params++repeat 0))
     raw=V.fromList(map convert left)
     vec=if observe=="bad-data" then error "source data forced" else V.slice offset len raw
     src=Image w h vec::Image p
     fp pixel=sum[bits value*fromIntegral(i+1)| (i,value)<-zip[0..](V.toList(one pixel))] `mod` 4294967296
     make value=unsafePixelAt(V.fromList[outConvert(value+fromIntegral(c*17))|c<-[0..componentCount outProxy-1]])0::q
     generate x y=if mode=="fail" then error "callback forced" else make(fromIntegral(x*37+y*257+seed))
     transform x y pixel=if mode=="fail" then error "callback forced" else make((if mode=="ignore" then 91 else fp pixel)+fromIntegral(x*37+y*257))
     summary img=if observe=="metadata" then object[field "width"(show(imageWidth img)),field "height"(show(imageHeight img))]
                 else object[field "width"(show(imageWidth img)),field "height"(show(imageHeight img)),field "hex"(quoted(outPack(imageData img)))]
     step acc x y pixel=if mode=="ignore" then acc*31+fromIntegral(x*17+y*257+1) else if mode=="fail" then error "callback forced" else acc*31+fromIntegral(x*17+y*257)+fp pixel
     traceField r=do t<-readIORef r;pure(field "trace"("["++joinComma["["++show x++","++show y++"]"|(x,y)<-reverse t]++"]"))
 case op of
  "generate"->pure(summary(generateImage generate w h))
  "with"->do
    trace<-newIORef[]
    result<-try(withImage w h(\x y->modifyIORef' trace ((x,y):)>>pure(generate x y))>>=evaluate.force.summary)::IO(Either SomeException String)
    t<-traceField trace
    pure$case result of Left e->object[t,field "exception"(quoted(err e))];Right value->init value++","++t++"}"
  "generate-fold"->let (state,img)=generateFoldImage(\acc x y->let next=acc*31+fromIntegral(x*17+y*257+1) in (next,make(next+fromIntegral seed))) (fromIntegral seed::Integer) w h
                   in pure(if observe=="metadata" then summary img else init(summary img)++","++field "state"(show state)++"}")
  "map"->pure(summary(pixelMap(transform 0 0)src))
  "mapxy"->pure(summary(pixelMapXY transform src))
  "fold"->pure(object[field "state"(show(pixelFold step (fromIntegral seed::Integer)src))])
  "foldm"->do
    trace<-newIORef[]
    result<-try(pixelFoldM(\acc x y p->modifyIORef' trace ((x,y):)>>pure(step acc x y p))(fromIntegral seed::Integer)src >>= evaluate.force)::IO(Either SomeException Integer)
    t<-traceField trace
    pure$object(t:case result of Left e->[field "exception"(quoted(err e))];Right value->[field "state"(show value)])
  "foldmap"->pure$if mode=="any" then object[field "bool"(if getAny(pixelFoldMap(\_ -> Any True)src) then "true" else "false")]
             else if mode=="bracket" then let Bracket value=pixelFoldMap(\p->Bracket(show(fp p)))src in object[field "value"(quoted value)]
             else object[field "state"(show(getSum(pixelFoldMap(\p->Sum(if mode=="ignore" then 1 else fp p))src))) ]
  "zip"->let b=Image w2 h2(V.fromList(map convert right))::Image p
             c=Image w h(V.fromList(map convert third))::Image p
             combined=zipPixelComponent3(\a b c->convert((bits a*3+bits b*5+bits c*7) `mod` 4294967296))src b c
          in pure(if observe=="metadata" then object[field "width"(show(imageWidth combined)),field "height"(show(imageHeight combined))] else object[field "hex"(quoted(pack(imageData combined)))])
  "eq"->let b=Image w2 h2(V.fromList(map convert right))::Image p in pure(object[field "bool"(if (if mode=="self" then src==src else src==b) then "true" else "false")])
  "base"->pure(object[field "index"(show(pixelBaseIndex src w2 h2))])
  "pixel"->pure(object[field "hex"(quoted(pack(one(pixelAt src w2 h2))))])
  "read"->do
    m<-V.thaw raw
    let img=MutableImage w h(M.slice offset len m)::MutableImage RealWorld p
    value<-readPixel img w2 h2
    pure(object[field "hex"(quoted(pack(one value)))])
  "write"->do
    m<-V.thaw raw
    let img=MutableImage w h(M.slice offset len m)::MutableImage RealWorld p
        replacement=unsafePixelAt(V.fromList(map convert right))0::p
    result<-try(writePixel img w2 h2 replacement)::IO(Either SomeException ())
    after<-V.freeze m
    pure(object(field "parent"(quoted(pack after)):case result of Left e->[field "exception"(quoted(err e))];Right _->[]))
  "slice"->pure(object[field "hex"(quoted(pack(imageData src)))])
  "freeze"->do
    m<-V.thaw raw
    let sliced=M.slice offset len m; img=MutableImage w h sliced::MutableImage RealWorld p
    snap<-freezeImage img
    when(len>0)(M.write sliced 0(convert(fromIntegral seed)))
    after<-V.freeze m
    pure(object[field "hex"(quoted(pack(imageData snap))),field "parent"(quoted(pack after))])
  "thaw"->do
    m<-thawImage src
    when(len>0)(M.write(mutableImageData m)0(convert(fromIntegral seed)))
    after<-freezeImage m
    pure(object[field "hex"(quoted(pack(imageData after))),field "parent"(quoted(pack raw))])
  _->error "unknown raster operation"

dispatchTarget :: forall p. (Pixel p,Eq(PixelBaseComponent p)) => (V.Vector(PixelBaseComponent p)->String) -> (Integer->PixelBaseComponent p) -> (PixelBaseComponent p->Integer) -> p -> Request -> IO String
dispatchTarget pack convert bits proxy request@(_,_,target,_,_,_,_,_,_)=case target of
 "y8"->runRaster pack convert bits proxy pack8 fromIntegral (undefined::Pixel8) request
 "y16"->runRaster pack convert bits proxy pack16 fromIntegral (undefined::Pixel16) request
 "y32"->runRaster pack convert bits proxy pack32 fromIntegral (undefined::Pixel32) request
 "yf"->runRaster pack convert bits proxy packF (castWord32ToFloat.fromIntegral) (undefined::PixelF) request
 "ya8"->runRaster pack convert bits proxy pack8 fromIntegral (undefined::PixelYA8) request
 "ya16"->runRaster pack convert bits proxy pack16 fromIntegral (undefined::PixelYA16) request
 "rgb8"->runRaster pack convert bits proxy pack8 fromIntegral (undefined::PixelRGB8) request
 "rgb16"->runRaster pack convert bits proxy pack16 fromIntegral (undefined::PixelRGB16) request
 "rgbf"->runRaster pack convert bits proxy packF (castWord32ToFloat.fromIntegral) (undefined::PixelRGBF) request
 "rgba8"->runRaster pack convert bits proxy pack8 fromIntegral (undefined::PixelRGBA8) request
 "rgba16"->runRaster pack convert bits proxy pack16 fromIntegral (undefined::PixelRGBA16) request
 "ycbcr8"->runRaster pack convert bits proxy pack8 fromIntegral (undefined::PixelYCbCr8) request
 "ycbcrk8"->runRaster pack convert bits proxy pack8 fromIntegral (undefined::PixelYCbCrK8) request
 "cmyk8"->runRaster pack convert bits proxy pack8 fromIntegral (undefined::PixelCMYK8) request
 "cmyk16"->runRaster pack convert bits proxy pack16 fromIntegral (undefined::PixelCMYK16) request
 _->error "unknown target"
process :: Request -> IO String
process request@(_,typ,_,_,_,_,_,_,_)=case typ of
 "y8"->dispatchTarget pack8 fromIntegral fromIntegral (undefined::Pixel8) request
 "y16"->dispatchTarget pack16 fromIntegral fromIntegral (undefined::Pixel16) request
 "y32"->dispatchTarget pack32 fromIntegral fromIntegral (undefined::Pixel32) request
 "yf"->dispatchTarget packF (castWord32ToFloat.fromIntegral) (fromIntegral.castFloatToWord32) (undefined::PixelF) request
 "ya8"->dispatchTarget pack8 fromIntegral fromIntegral (undefined::PixelYA8) request
 "ya16"->dispatchTarget pack16 fromIntegral fromIntegral (undefined::PixelYA16) request
 "rgb8"->dispatchTarget pack8 fromIntegral fromIntegral (undefined::PixelRGB8) request
 "rgb16"->dispatchTarget pack16 fromIntegral fromIntegral (undefined::PixelRGB16) request
 "rgbf"->dispatchTarget packF (castWord32ToFloat.fromIntegral) (fromIntegral.castFloatToWord32) (undefined::PixelRGBF) request
 "rgba8"->dispatchTarget pack8 fromIntegral fromIntegral (undefined::PixelRGBA8) request
 "rgba16"->dispatchTarget pack16 fromIntegral fromIntegral (undefined::PixelRGBA16) request
 "ycbcr8"->dispatchTarget pack8 fromIntegral fromIntegral (undefined::PixelYCbCr8) request
 "ycbcrk8"->dispatchTarget pack8 fromIntegral fromIntegral (undefined::PixelYCbCrK8) request
 "cmyk8"->dispatchTarget pack8 fromIntegral fromIntegral (undefined::PixelCMYK8) request
 "cmyk16"->dispatchTarget pack16 fromIntegral fromIntegral (undefined::PixelCMYK16) request
 _->error "unknown source"
main=do
 args<-getArgs
 if args==["--reference-info"] then print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector]
 else getContents >>= mapM_ respond . lines
 where respond s=do
        result<-try(process(read s)>>=evaluate.force)::IO(Either SomeException String)
        putStrLn$either(\e->object[field "exception"(quoted(err e))])id result
