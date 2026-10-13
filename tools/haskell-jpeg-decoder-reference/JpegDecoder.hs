{-# LANGUAGE CPP #-}
module Main where
import Codec.Picture.Jpg
import Codec.Picture.Types
import qualified Data.ByteString as B
import qualified Data.Vector.Storable as V
import Control.Exception(SomeException,try,evaluate,displayException)
import Control.DeepSeq(force)
import Numeric(readHex)
import System.Environment(getArgs)
import System.Info(compilerVersion)
import Data.Version(showVersion)
raw []=[]
raw(a:b:rest)=fst(head(readHex[a,b])):raw rest
raw _=error "odd hex"
image x=case x of
 ImageY8 i->desc "Y8" i
 ImageYA8 i->desc "YA8" i
 ImageRGB8 i->desc "RGB8" i
 ImageYCbCr8 i->desc "YCbCr8" i
 ImageCMYK8 i->desc "CMYK8" i
 _->error "unexpected image kind"
 where desc name i=(name,imageWidth i,imageHeight i,B.pack(V.toList(imageData i)))
render (Left e)="{\"error\":"++show e++"}"
render (Right im)=let (c,w,h,p)=image im in "{\"color\":"++show c++",\"width\":"++show w++",\"height\":"++show h++",\"pixels\":"++show(B.unpack p)++"}"
file path prefix=do
 input<-B.readFile path
 case decodeJpeg input of
  Left e->pure(render(Left e))
  Right im->do
   let(c,w,h,p)=image im
   B.writeFile prefix p
   pure("{\"color\":"++show c++",\"width\":"++show w++",\"height\":"++show h++"}")
respond action=do
 result<-try(action >>= evaluate . force)::IO(Either SomeException String)
 putStrLn $ either (\e->"{\"exception\":"++show(takeWhile (/='\n')(displayException e))++"}") id result
main=do
 args<-getArgs
 case args of
  ["--reference-info"]->print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector]
  ["--file",path,out]->respond(file path out)
  _->getContents >>= mapM_ (\s->respond(pure(render(decodeJpeg(B.pack(raw s)))))) . lines
