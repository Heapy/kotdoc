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
pack16 :: V.Vector Word16 -> String
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

type Request=(String,String,String,[Integer],[Integer])
process :: Request -> IO String
process (op,mode,observe,params,values)=do
 let [w,h,param,offset,len]=take 5(map fromIntegral(params++repeat 0))
     parameter=if mode=="bad-parameter" then error "parameter forced" else castWord32ToFloat(fromIntegral param)
     src=if mode=="bad-image" then error "image forced" else Image w h (if mode=="bad-data" then error "data forced" else V.slice offset len(V.fromList(map(castWord32ToFloat.fromIntegral)values)))::Image PixelRGBF
     out=if op=="gamma" then gammaCorrection parameter src else if op=="tone-pixels" then
           Image w h(V.concat[imageData(toneMapping parameter(Image 1 1(V.slice i 3(imageData src))))|i<-[0,3..len-3]])
           else toneMapping parameter src
 case observe of
  "metadata"->pure(object[field "width"(show(imageWidth out)),field "height"(show(imageHeight out))])
  _->pure(object[field "width"(show(imageWidth out)),field "height"(show(imageHeight out)),field "hex"(quoted(packF(imageData out)))])
main=do
 args<-getArgs
 if args==["--reference-info"] then print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector]
 else getContents >>= mapM_ respond . lines
 where respond s=do
        result<-try(process(read s)>>=evaluate.force)::IO(Either SomeException String)
        putStrLn$either(\e->object[field "exception"(quoted(err e))])id result
