{-# LANGUAGE CPP #-}
module Main where
import Codec.Picture.Jpg.Internal.Progressive
import Codec.Picture.Jpg.Internal.Types
import Codec.Picture.Jpg.Internal.DefaultTable
import Codec.Picture.Types
import Control.DeepSeq(force)
import Control.Exception(SomeException,try,evaluate,displayException)
import Control.Monad.ST(runST)
import qualified Data.Vector as V
import qualified Data.Vector.Storable as VS
import qualified Data.ByteString.Lazy as L
import System.Environment(getArgs)
import System.Info(compilerVersion)
import Data.Version(showVersion)

type Parameter=([Int],[Int],[Int])
type Request=([Int],[[Int]],[[Int]],[([Parameter],[Int])])
-- dc/ac arrays are packed Huffman trees, options are the original record fields.
parameter (dc,ac,ns)=JpgUnpackerParameter (VS.fromList(map fromIntegral dc)) (VS.fromList(map fromIntegral ac))
 (ns!!0)(ns!!1)(ns!!2)(ns!!3)(ns!!4,ns!!5)(ns!!6,ns!!7)(ns!!8,ns!!9)(ns!!10)(ns!!11)(ns!!12)(ns!!13)(ns!!14)
process :: Request -> [Int]
process (dims,components,quants,scans)=runST $ do
 let component [i,w,h,q]=JpgComponent(fromIntegral i)(fromIntegral w)(fromIntegral h)(fromIntegral q)
     component _=error "bad component"
     frame=JpgFrameHeader 0 8 (fromIntegral(dims!!1)) (fromIntegral(dims!!0)) (fromIntegral(length components)) (map component components)
     qs=V.fromList[VS.fromList(map fromIntegral q)|q<-quants]
     ss=[([(parameter p,())|p<-ps],L.pack(map fromIntegral bs))|(ps,bs)<-scans]
 image<-progressiveUnpack(dims!!2,dims!!3)frame qs ss
 map fromIntegral . VS.toList <$> VS.freeze(mutableImageData image)
main=do
 args<-getArgs
 if args==["--reference-info"] then print["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector]
 else getContents >>= mapM_ respond . lines
 where respond line=do
        result<-try(evaluate(force(process(read line))))::IO(Either SomeException [Int])
        putStrLn $ either (\e->"{\"error\":"++show(takeWhile (/='\n')(displayException e))++"}") (\v->"{\"pixels\":"++show v++"}") result
