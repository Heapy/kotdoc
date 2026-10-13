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
import Foreign.ForeignPtr(withForeignPtr)
import Foreign.Ptr(plusPtr,castPtr)
import Foreign.Storable(sizeOf)
import GHC.Float(castWord32ToFloat,castFloatToWord32)
import System.Environment(getArgs)
import System.Info(compilerVersion)
import Data.Version(showVersion)
import Numeric(showHex)

type Request=(String,String,String,[Integer],[Integer],[Integer])
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

-- Supplied test callbacks; original Pixel methods decide their component routing.
binary :: Num a => String -> Int -> a -> a -> a
binary name i a b=case name of
 "left"->a; "right"->b; "sum"->a+b; "difference"->a-b
 "weighted"->fromIntegral(i+1)*17+a*3-b*5
 "index"->fromIntegral(i+123)
 "fail1"->if i==1 then error "color callback channel 1" else a
 _->error "color callback failure"
alpha :: Num a => String -> a -> a -> a
alpha name a b=case name of
 "left"->a;"right"->b;"difference"->a-b;"tagged"->219
 _->error "alpha callback forced"
unary :: Num a => String -> a -> a
unary name a=case name of
 "identity"->a;"negate"->negate a;"offset"->a+37;"scale"->a*3-11
 _->error "map callback failure"

runPixel :: forall p. Pixel p
         => (V.Vector(PixelBaseComponent p)->String)
         -> (Integer->PixelBaseComponent p) -> p -> Request -> IO String
runPixel pack convert proxy (op,_,mode,params,left,right)=do
 let n=componentCount proxy
     a=V.fromList(map convert left)
     b=V.fromList(map convert right)
     [w,h,x,y,index]=take 5(map fromIntegral(params++repeat 0))
     src=Image w h a::Image p
     replacement=unsafePixelAt b 0::p
     one value=runST $ do
       out<-M.new n
       unsafeWritePixel out 0 value
       V.freeze out
     serial value=pack(one value)
     (colorMode,rest)=break(=='/')mode
     alphaMode=drop 1 rest
     purePixel :: Int -> p
     purePixel i=case op of
       "mix"->mixWith(binary mode)(unsafePixelAt a i)(unsafePixelAt b i)
       "mix-alpha"->mixWithAlpha(binary colorMode)(alpha alphaMode)(unsafePixelAt a i)(unsafePixelAt b i)
       "map"->colorMap(unary mode)(unsafePixelAt a i)
       _->error "unknown pure pixel operation"
     pureVector=runST $ do
       out<-M.new((V.length a `div` n)*n)
       forM_ [0,n..V.length a-n] $ \i->unsafeWritePixel out i(purePixel i)
       V.freeze out
 if op `elem` ["mix","mix-alpha","map"] then pure(object[field "hex"(quoted(pack pureVector))])
 else if op=="meta" then pure(object[field "components"(show n),field "hex"(quoted(pack(V.fromList[pixelOpacity(unsafePixelAt a i::p)|i<-[0,n..V.length a-n]])))])
 else if op=="base" then pure(object[field "index"(show(pixelBaseIndex src x y))])
 else do
   mutable<-V.thaw a
   let img=MutableImage w h mutable::MutableImage RealWorld p
       attempt action=try(action>>=evaluate.force)::IO(Either SomeException String)
       pixelResult result=case result of Left e->[field "exception"(quoted(err e))];Right value->[field "pixelHex"(quoted value)]
       state result=do
         after<-V.freeze mutable
         pure(object(field "dataHex"(quoted(pack after)):pixelResult result))
       pointer v=let(fp,offset,_)=V.unsafeToForeignPtr v in withForeignPtr fp(\p->pure(castPtr(p `plusPtr` (offset*sizeOf(undefined::PixelBaseComponent p)))))
       mutablePointer v=let(fp,offset,_)=M.unsafeToForeignPtr v in withForeignPtr fp(\p->pure(castPtr(p `plusPtr` (offset*sizeOf(undefined::PixelBaseComponent p)))))
       lifecycle shared sourceHex final=do
         frozen<-unsafeFreezeImage final
         ptr<-pointer(imageData frozen)
         origPtr<-pointer a
         pure(object[field "shared"(if shared && ptr==origPtr then "true" else "false"),field "sourceHex"(quoted sourceHex),field "dataHex"(quoted(pack(imageData frozen))),field "width"(show(imageWidth frozen)),field "height"(show(imageHeight frozen))])
   case op of
    "mutable-base"->pure(object[field "index"(show(mutablePixelBaseIndex img x y))])
    "pixel"->attempt(pure(serial(pixelAt src x y)))>>=state
    "unsafe-pixel"->attempt(pure(serial(unsafePixelAt a index::p)))>>=state
    "read"->attempt(readPixel img x y>>=pure.serial)>>=state
    "unsafe-read"->attempt(unsafeReadPixel mutable index>>=pure.serial)>>=state
    "write"->attempt(writePixel img x y replacement>>pure "")>>=state
    "unsafe-write"->attempt(unsafeWritePixel mutable index replacement>>pure "")>>=state
    "thaw"->do
       before<-evaluate(force(pack a))
       m<-thawImage src
       when(mode/="view")(unsafeWritePixel(mutableImageData m)index replacement)
       origPtr<-pointer a;newPtr<-mutablePointer(mutableImageData m)
       result<-freezeImage m
       pure(object[field "shared"(if origPtr==newPtr then "true" else "false"),field "sourceHex"(quoted(pack a)),field "dataHex"(quoted(pack(imageData result))),field "width"(show w),field "height"(show h)])
    "unsafe-thaw"->do
       before<-evaluate(force(pack a))
       m<-unsafeThawImage src
       when(mode/="view")(unsafeWritePixel(mutableImageData m)index replacement)
       lifecycle True before m
    "freeze"->do
       mutPtr<-mutablePointer mutable
       snap<-freezeImage img
       when(mode/="view")(unsafeWritePixel mutable index replacement)
       after<-V.freeze mutable
       snapPtr<-pointer(imageData snap)
       pure(object[field "shared"(if snapPtr==mutPtr then "true" else "false"),field "imageHex"(quoted(pack(imageData snap))),field "dataHex"(quoted(pack after)),field "width"(show w),field "height"(show h)])
    "unsafe-freeze"->do
       origPtr<-mutablePointer mutable
       frozen<-unsafeFreezeImage img
       newPtr<-pointer(imageData frozen)
       pure(object[field "shared"(if origPtr==newPtr then "true" else "false"),field "dataHex"(quoted(pack(imageData frozen))),field "width"(show w),field "height"(show h)])
    "create"->do
       m<-createMutableImage w h replacement
       frozen<-freezeImage(m::MutableImage RealWorld p)
       pure(object[field "dataHex"(quoted(pack(imageData frozen))),field "width"(show w),field "height"(show h)])
    "new"->do
       m<-newMutableImage w h::IO(MutableImage RealWorld p)
       V.copy(mutableImageData m)a
       frozen<-unsafeFreezeImage m
       pure(object[field "dataHex"(quoted(pack(imageData frozen))),field "width"(show w),field "height"(show h)])
    _->error "unknown request"

process :: Request -> IO String
process request@(_,typ,_,_,_,_)=case typ of
 "y8"->runPixel pack8 fromIntegral(undefined::Pixel8)request
 "y16"->runPixel pack16 fromIntegral(undefined::Pixel16)request
 "y32"->runPixel pack32 fromIntegral(undefined::Pixel32)request
 "yf"->runPixel packF (castWord32ToFloat.fromIntegral)(undefined::PixelF)request
 "ya8"->runPixel pack8 fromIntegral(undefined::PixelYA8)request
 "ya16"->runPixel pack16 fromIntegral(undefined::PixelYA16)request
 "rgb8"->runPixel pack8 fromIntegral(undefined::PixelRGB8)request
 "rgb16"->runPixel pack16 fromIntegral(undefined::PixelRGB16)request
 "rgbf"->runPixel packF (castWord32ToFloat.fromIntegral)(undefined::PixelRGBF)request
 "rgba8"->runPixel pack8 fromIntegral(undefined::PixelRGBA8)request
 "rgba16"->runPixel pack16 fromIntegral(undefined::PixelRGBA16)request
 "ycbcr8"->runPixel pack8 fromIntegral(undefined::PixelYCbCr8)request
 "ycbcrk8"->runPixel pack8 fromIntegral(undefined::PixelYCbCrK8)request
 "cmyk8"->runPixel pack8 fromIntegral(undefined::PixelCMYK8)request
 "cmyk16"->runPixel pack16 fromIntegral(undefined::PixelCMYK16)request
 _->error "unknown pixel type"
main=do
 args<-getArgs
 if args==["--reference-info"] then print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector]
 else getContents >>= mapM_ respond . lines
 where respond s=do
        result<-try(process(read s)>>=evaluate.force)::IO(Either SomeException String)
        putStrLn$either(\e->object[field "exception"(quoted(err e))])id result
