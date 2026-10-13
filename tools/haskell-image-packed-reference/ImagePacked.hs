{-# LANGUAGE CPP, FlexibleContexts, TypeFamilies, ScopedTypeVariables #-}
module Main where
import Codec.Picture.Types
import qualified Data.Vector.Storable as V
import qualified Data.Vector.Storable.Mutable as M
import Control.Exception(SomeException,try,evaluate,displayException)
import Control.DeepSeq(force)
import Control.Monad(forM_)
import Control.Monad.ST(runST)
import Data.Word(Word8,Word16,Word32,Word64)
import Foreign.Storable(Storable,poke,peek)
import Foreign.Marshal.Alloc(alloca)
import Foreign.Ptr(Ptr,castPtr)
import GHC.Exts(RealWorld)
import GHC.Float(castWord32ToFloat,castFloatToWord32)
import System.Environment(getArgs)
import System.Info(compilerVersion)
import Data.Version(showVersion)
import Numeric(showHex)

type Request=(String,String,[Integer],[Integer],[Integer])
hex :: [Word8] -> String
hex=concatMap(\v->let s=showHex v "" in if length s==1 then '0':s else s)
pack8=hex.V.toList
pack16=hex.concatMap(\v->[fromIntegral v,fromIntegral(v `div` 256)]).V.toList
bytes32 :: Word32 -> [Word8]
bytes32 v=[fromIntegral(v `div` 2^n)|n<-[0,8,16,24]]
bytes64 :: Word64 -> [Word8]
bytes64 v=[fromIntegral(v `div` 2^n)|n<-[0,8..56]]
pack32=hex.concatMap bytes32.V.toList
pack64=hex.concatMap bytes64.V.toList
packF=hex.concatMap(bytes32.castFloatToWord32).V.toList
quoted s="\""++s++"\""
field key value=quoted key++":"++value
object []="{}"
object(x:xs)="{"++x++concatMap(',':)xs++"}"
err e=takeWhile(/='\n')(displayException e)

runPacked :: forall p. (Pixel p,PackeablePixel p,Storable(PackedRepresentation p))
          => (V.Vector(PixelBaseComponent p)->String)
          -> (Integer->PixelBaseComponent p)
          -> (V.Vector(PackedRepresentation p)->String)
          -> (Integer->PackedRepresentation p) -> p -> Request -> IO String
runPacked packBase baseValue packWord wordValue proxy (op,_,params,storage,values)=do
 let n=componentCount proxy
     base=V.fromList(map baseValue values)
     words=V.fromList(map wordValue values)
     packed=V.generate(V.length base `div` n)(\i->packPixel(unsafePixelAt base(i*n)::p))
     unpacked input=runST $ do
       out<-M.new(V.length input*n)
       forM_ [0..V.length input-1] $ \i->unsafeWritePixel out(i*n)(unpackPixel(input V.! i)::p)
       V.freeze out
     one pixel=runST $ do
       out<-M.new n
       unsafeWritePixel out 0(pixel::p)
       V.freeze out
     [w,h,offset,len,index,count]=take 6(map fromIntegral(params++repeat 0))
     replacement=unsafePixelAt base 0::p
 if op=="pack" then pure(object[field "packedHex"(quoted(packWord packed)),field "pixelHex"(quoted(packBase(unpacked packed)))])
 else if op=="unpack" then do
   let pixels=unpacked words
       again=V.generate(V.length words)(\i->packPixel(unsafePixelAt pixels(i*n)::p))
   pure(object[field "packedHex"(quoted(packWord again)),field "pixelHex"(quoted(packBase pixels))])
 else do
   parent<-V.thaw(V.fromList(map baseValue storage))
   let view=M.slice offset len parent
       image=MutableImage w h view::MutableImage RealWorld p
       action=case op of
         "fill"->fillImageWith image replacement>>pure ""
         "write"->writePackedPixelAt image index replacement>>pure ""
         "read"->readPackedPixelAt image index>>=pure.packBase.one
         "between"->unsafeWritePixelBetweenAt image replacement index count>>pure ""
         _->error "unknown packed operation"
   result<-try(action>>=evaluate.force)::IO(Either SomeException String)
   after<-V.freeze parent
   pure(object(field "dataHex"(quoted(packBase after)):case result of Left e->[field "exception"(quoted(err e))];Right value->[field "pixelHex"(quoted value)]))

process :: Request -> IO String
process request@(_,typ,_,_,_)=case typ of
 "y8"->runPacked pack8 fromIntegral pack8 fromIntegral(undefined::Pixel8)request
 "y16"->runPacked pack16 fromIntegral pack16 fromIntegral(undefined::Pixel16)request
 "y32"->runPacked pack32 fromIntegral pack32 fromIntegral(undefined::Pixel32)request
 "yf"->runPacked packF(castWord32ToFloat.fromIntegral)packF(castWord32ToFloat.fromIntegral)(undefined::PixelF)request
 "ya8"->runPacked pack8 fromIntegral pack16 fromIntegral(undefined::PixelYA8)request
 "ya16"->runPacked pack16 fromIntegral pack32 fromIntegral(undefined::PixelYA16)request
 "rgba8"->runPacked pack8 fromIntegral pack32 fromIntegral(undefined::PixelRGBA8)request
 "rgba16"->runPacked pack16 fromIntegral pack64 fromIntegral(undefined::PixelRGBA16)request
 "cmyk8"->runPacked pack8 fromIntegral pack32 fromIntegral(undefined::PixelCMYK8)request
 "cmyk16"->runPacked pack16 fromIntegral pack64 fromIntegral(undefined::PixelCMYK16)request
 _->error "unknown packed pixel type"
memoryOrder=alloca $ \(p::Ptr Word16)->do
 poke p 0x0102
 byte<-peek(castPtr p)::IO Word8
 pure(if byte==2 then "little" else "big")
main=do
 args<-getArgs
 if args==["--reference-info"] then do
   order<-memoryOrder
   print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector,"memory",order]
 else getContents >>= mapM_ respond . lines
 where respond s=do
        result<-try(process(read s)>>=evaluate.force)::IO(Either SomeException String)
        putStrLn$either(\e->object[field "exception"(quoted(err e))])id result
