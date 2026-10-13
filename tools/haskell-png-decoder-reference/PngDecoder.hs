{-# LANGUAGE CPP, FlexibleContexts, TypeFamilies, GADTs #-}
{-# OPTIONS_GHC -fno-specialise -fno-spec-constr #-}
module Main where
import Codec.Picture.Png
import Codec.Picture.Types
import qualified Codec.Picture.Metadata as M
import qualified Data.ByteString as B
import qualified Data.Vector.Storable as V
import Control.Exception (SomeException, try, evaluate, displayException)
import Control.DeepSeq (force)
import Data.Word (Word8,Word16)
import Data.Char (ord)
import Numeric (showHex,readHex)
import System.Environment (getArgs)
import System.Info (compilerVersion)
import Data.Version (showVersion)

hex :: [Word8] -> String
hex=concatMap(\v->let s=showHex v "" in if length s==1 then '0':s else s)
unhex []=[]
unhex(a:b:xs)=fst(head(readHex[a,b])):unhex xs
pack8=hex.V.toList
pack16 :: V.Vector Word16 -> String
pack16=hex.concatMap(\v->[fromIntegral v,fromIntegral(v `div` 256)]).V.toList
quoted s="\""++s++"\""
field k v=quoted k++":"++v
object fs="{"++join fs++"}"
join []=""
join(x:xs)=x++concatMap(',':)xs
text=show.map ord
image tag pack img=[field "tag"(quoted tag),field "width"(show(imageWidth img)),field "height"(show(imageHeight img)),field "hex"(quoted(pack(imageData img)))]
dynamic d=case d of
 ImageY8 i->image "y8" pack8 i
 ImageY16 i->image "y16" pack16 i
 ImageYA8 i->image "ya8" pack8 i
 ImageYA16 i->image "ya16" pack16 i
 ImageRGB8 i->image "rgb8" pack8 i
 ImageRGB16 i->image "rgb16" pack16 i
 ImageRGBA8 i->image "rgba8" pack8 i
 ImageRGBA16 i->image "rgba16" pack16 i
 _->error "unexpected PNG type"
tag d=case d of
 ImageY8 _->"y8"; ImageY16 _->"y16"; ImageYA8 _->"ya8"; ImageYA16 _->"ya16"
 ImageRGB8 _->"rgb8"; ImageRGB16 _->"rgb16"; ImageRGBA8 _->"rgba8"; ImageRGBA16 _->"rgba16"
 _->error "unexpected PNG type"
paletteTag p=case p of
 TrueColorImage _->"truecolor";PalettedY8 _ _->"y8";PalettedRGB8 _ _->"rgb8";PalettedRGBA8 _ _->"rgba8";PalettedRGB16 _ _->"rgb16"
palette p=case p of
 TrueColorImage d->dynamic d
 PalettedRGB8 i pl->image "rgb8" pack8 i++[field "palette"(quoted(pack8(_paletteData pl))),field "size"(show(_paletteSize pl))]
 PalettedRGBA8 i pl->image "rgba8" pack8 i++[field "palette"(quoted(pack8(_paletteData pl))),field "size"(show(_paletteSize pl))]
 _->error "unexpected PNG palette"
process :: (String,String) -> String
process (input,observe)=case decodePngWithPaletteAndMetadata(B.pack(unhex input)) of
 Left e->object[field "error"(text e)]
 Right(p,m)->object$case observe of
  "either"->[]
  "palette-tag"->[field "tag"(quoted(paletteTag p))]
  "palette-data"->palette p
  "dynamic-tag"->[field "tag"(quoted(tag(palettedToTrueColor p)))]
  "dynamic-data"->dynamic(palettedToTrueColor p)
  "metadata"->[field "metadata"(text(show m))]
  "metadata-keys"->[field "metadata"(text(show(M.foldMap(\(k M.:=> _)->[show k])m)))]
  "metadata-title"->[field "metadata"(text(show(M.lookup M.Title m)))]
  "metadata-dpi"->[field "metadata"(text(show(M.lookup M.DpiX m)))]
  _->error "unexpected observation"
main=do
 args<-getArgs
 if args==["--reference-info"] then print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector,"zlib",VERSION_zlib]
 else getContents >>= mapM_ respond . lines
 where respond s=do
        result<-try(evaluate(force(process(read s))))::IO(Either SomeException String)
        putStrLn$either(\e->object[field "exception"(text(takeWhile(/='\n')(displayException e)))])id result
