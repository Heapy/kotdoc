{-# LANGUAGE CPP, FlexibleContexts, TypeFamilies, ScopedTypeVariables #-}
{-# OPTIONS_GHC -fno-specialise -fno-spec-constr #-}
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
import Data.Monoid(Any(..),Last(..))
import Data.Functor.Identity
import Data.Functor.Const
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

-- Only supplied callbacks and observation/serialization live here. Traversals,
-- Applicative instances and allocation/writes are unchanged upstream code.
type Request=(String,String,String,String,String,String,[Integer],[Integer])
data Bracket=Bracket String
instance Semigroup Bracket where Bracket a <> Bracket b=Bracket("("++a++"|"++b++")")
instance Monoid Bracket where mempty=Bracket "E"
one :: forall p. Pixel p => p -> V.Vector(PixelBaseComponent p)
one value=runST $ do
 out<-M.new(componentCount value)
 unsafeWritePixel out 0 value
 V.freeze out

runTraversal :: forall p q. (Pixel p,Pixel q)
 => (Integer->PixelBaseComponent p) -> (PixelBaseComponent p->Integer) -> p
 -> (V.Vector(PixelBaseComponent q)->String) -> (Integer->PixelBaseComponent q) -> q
 -> Request -> IO String
runTraversal convert bits proxy outPack outConvert outProxy (op,_,_,effect,mode,observe,params,left)=do
 let [w,h,offset,len,fx,fy]=take 6(map fromIntegral(params++repeat 0))
     raw=V.fromList(map convert left)
     vec=if take 4 observe=="bad-" then error "source data forced" else V.slice offset len raw
     src=Image w h vec::Image p
     fp p=sum[bits v*fromIntegral(i+1)|(i,v)<-zip[0..](V.toList(one p))] `mod` 4294967296
     value x y p=if mode=="prefix-fail" && fp p==0 then error "prefix forced" else if mode=="ignore" || mode=="missing" then 91 else fp p+fromIntegral(x*37+y*257)
     make v=unsafePixelAt(V.fromList[outConvert(v+fromIntegral(c*17))|c<-[0..componentCount outProxy-1]])0::q
     pixel x y p=if mode=="value-fail" then error "pixel value forced" else make(value x y p)
     guardEffect :: forall r. Int -> Int -> r -> r
     guardEffect x y result=if mode=="effect-fail" || (mode=="effect-fail-at" && x==fx && y==fy) then error "effect forced" else result
     traversal :: forall f. Applicative f => (Int->Int->p->f q)->f(Image q)
     traversal f=if op=="pixels" then imagePixels(f 0 0)src else imageIPixels(\(x,y,p)->f x y p)src
     summary img=object([field "width"(show(imageWidth img)),field "height"(show(imageHeight img))]++if observe `elem` ["metadata","bad-metadata"] then [] else [field "hex"(quoted(outPack(imageData img)))])
     jsonTrace t="["++joinComma["["++show x++","++show y++"]"|(x,y)<-t]++"]"
 case effect of
  "identity"->pure(summary(runIdentity(traversal(\x y p->guardEffect x y(Identity(pixel x y p))))))
  "const-list"->let values=getConst(traversal(\x y p->guardEffect x y(Const[(x,y,value x y p)])))
                in pure(object[field "values"("["++joinComma["["++show x++","++show y++","++show v++"]"|(x,y,v)<-values]++"]")])
  "const-any"->pure(object[field "bool"(if getAny(getConst(traversal(\x y _->guardEffect x y(Const(Any True))))) then "true" else "false")])
  "const-last"->let last=if op=="foldmap" then getLast(pixelFoldMap(\p->Last(Just(value 0 0 p)))src) else getLast(getConst(traversal(\x y p->guardEffect x y(Const(Last(Just(value x y p)))))))
                in pure(object[field "last"(maybe "null" show last)])
  "const-bracket"->let Bracket text=getConst(traversal(\x y p->guardEffect x y(Const(Bracket(show(value x y p))))))
                   in pure(object[field "value"(quoted text)])
  "maybe"->pure(object[field "image"(maybe "null" summary(traversal(\x y p->guardEffect x y(if mode=="missing" || (mode=="missing-at" && x==fx && y==fy) then Nothing else Just(pixel x y p)))))])
  "list"->pure(object[field "images"("["++joinComma(map summary(traversal(\x y p->guardEffect x y[pixel x y p,make(value x y p+1009)])))++"]")])
  "io"->do
    trace<-newIORef[]
    let callback x y p=guardEffect x y $ do
          modifyIORef' trace ((x,y):)
          when(mode=="action-fail" && x==fx && y==fy)(error "IO action forced")
          pure(pixel x y p)
    result<-try(traversal callback>>=evaluate.force.summary)::IO(Either SomeException String)
    t<-readIORef trace
    pure$object(field "trace"(jsonTrace(reverse t)):case result of Left e->[field "exception"(quoted(err e))];Right value->[field "image" value])
  _->error "unknown Applicative"

dispatchTarget :: forall p. Pixel p => (Integer->PixelBaseComponent p) -> (PixelBaseComponent p->Integer) -> p -> Request -> IO String
dispatchTarget convert bits proxy request@(_,_,target,_,_,_,_,_)=case target of
 "y8"->runTraversal convert bits proxy pack8 fromIntegral (undefined::Pixel8) request
 "y16"->runTraversal convert bits proxy pack16 fromIntegral (undefined::Pixel16) request
 "y32"->runTraversal convert bits proxy pack32 fromIntegral (undefined::Pixel32) request
 "yf"->runTraversal convert bits proxy packF (castWord32ToFloat.fromIntegral) (undefined::PixelF) request
 "ya8"->runTraversal convert bits proxy pack8 fromIntegral (undefined::PixelYA8) request
 "ya16"->runTraversal convert bits proxy pack16 fromIntegral (undefined::PixelYA16) request
 "rgb8"->runTraversal convert bits proxy pack8 fromIntegral (undefined::PixelRGB8) request
 "rgb16"->runTraversal convert bits proxy pack16 fromIntegral (undefined::PixelRGB16) request
 "rgbf"->runTraversal convert bits proxy packF (castWord32ToFloat.fromIntegral) (undefined::PixelRGBF) request
 "rgba8"->runTraversal convert bits proxy pack8 fromIntegral (undefined::PixelRGBA8) request
 "rgba16"->runTraversal convert bits proxy pack16 fromIntegral (undefined::PixelRGBA16) request
 "ycbcr8"->runTraversal convert bits proxy pack8 fromIntegral (undefined::PixelYCbCr8) request
 "ycbcrk8"->runTraversal convert bits proxy pack8 fromIntegral (undefined::PixelYCbCrK8) request
 "cmyk8"->runTraversal convert bits proxy pack8 fromIntegral (undefined::PixelCMYK8) request
 "cmyk16"->runTraversal convert bits proxy pack16 fromIntegral (undefined::PixelCMYK16) request
 _->error "unknown target"
process :: Request -> IO String
process request@(_,typ,_,_,_,_,_,_)=case typ of
 "y8"->dispatchTarget fromIntegral fromIntegral (undefined::Pixel8) request
 "y16"->dispatchTarget fromIntegral fromIntegral (undefined::Pixel16) request
 "y32"->dispatchTarget fromIntegral fromIntegral (undefined::Pixel32) request
 "yf"->dispatchTarget (castWord32ToFloat.fromIntegral) (fromIntegral.castFloatToWord32) (undefined::PixelF) request
 "ya8"->dispatchTarget fromIntegral fromIntegral (undefined::PixelYA8) request
 "ya16"->dispatchTarget fromIntegral fromIntegral (undefined::PixelYA16) request
 "rgb8"->dispatchTarget fromIntegral fromIntegral (undefined::PixelRGB8) request
 "rgb16"->dispatchTarget fromIntegral fromIntegral (undefined::PixelRGB16) request
 "rgbf"->dispatchTarget (castWord32ToFloat.fromIntegral) (fromIntegral.castFloatToWord32) (undefined::PixelRGBF) request
 "rgba8"->dispatchTarget fromIntegral fromIntegral (undefined::PixelRGBA8) request
 "rgba16"->dispatchTarget fromIntegral fromIntegral (undefined::PixelRGBA16) request
 "ycbcr8"->dispatchTarget fromIntegral fromIntegral (undefined::PixelYCbCr8) request
 "ycbcrk8"->dispatchTarget fromIntegral fromIntegral (undefined::PixelYCbCrK8) request
 "cmyk8"->dispatchTarget fromIntegral fromIntegral (undefined::PixelCMYK8) request
 "cmyk16"->dispatchTarget fromIntegral fromIntegral (undefined::PixelCMYK16) request
 _->error "unknown source"
main=do
 args<-getArgs
 if args==["--reference-info"] then print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector]
 else getContents >>= mapM_ respond . lines
 where respond s=do
        result<-try(process(read s)>>=evaluate.force)::IO(Either SomeException String)
        putStrLn$either(\e->object[field "exception"(quoted(err e))])id result
