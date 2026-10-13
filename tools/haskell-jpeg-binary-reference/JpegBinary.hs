{-# LANGUAGE ScopedTypeVariables #-}
{-# LANGUAGE CPP #-}
module Main where
import Codec.Picture.Jpg.Internal.Types
import Codec.Picture.Jpg.Internal.DefaultTable (DctComponent(..))
import Data.Binary (Binary(..), encode)
import Data.Binary.Get (Get, runGetOrFail)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as L
import qualified Data.Vector as V
import qualified Data.Vector.Storable as VS
import qualified Data.Vector.Unboxed as VU
import Control.Exception (SomeException, try, evaluate, displayException)
import Control.DeepSeq (force)
import System.Environment (getArgs)
import System.Info (compilerName, compilerVersion)
import Data.Version (showVersion)

type Request = (String,[Int],[[Int]])
json :: String -> String
json s = show s
fields :: [(String,String)] -> String
fields xs = "{" ++ join [show k ++ ":" ++ v | (k,v) <- xs] ++ "}"
  where join [] = ""; join [x] = x; join (x:rest) = x ++ "," ++ join rest
packed :: L.ByteString -> String
packed = show . L.unpack
value :: (Binary a, Show a) => (a -> Int) -> a -> [(String,String)]
value size a = [("show",json (show a)),("encoded",packed (encode a)),("size",show (size a))]
parse :: Get a -> (a -> [(String,String)]) -> L.ByteString -> String
parse parser describe input = case runGetOrFail parser input of
  Left (rest,n,e) -> fields [("error",json e),("offset",show n),("rest",packed rest)]
  Right (rest,n,a) -> fields $ [("offset",show n),("rest",packed rest)] ++ describe a
run :: Request -> String
run (op,ns,rows) = case op of
  "kind" -> p (get :: Get JpgFrameKind) (value (const (-1)))
  "unit" -> p (get :: Get JFifUnit) (value (const 1))
  "transform" -> p (get :: Get AdobeTransform) (value (const 1))
  "jfif" -> p (get :: Get JpgJFIFApp0) (value (const 14))
  "adobe" -> p (get :: Get JpgAdobeApp14) (value (const 12))
  "component" -> p (get :: Get JpgComponent) (value calculateSize)
  "frame" -> p (get :: Get JpgFrameHeader) (value calculateSize)
  "scanSpec" -> p (get :: Get JpgScanSpecification) (value calculateSize)
  "scan" -> p (get :: Get JpgScanHeader) (value calculateSize)
  "quant" -> p (get :: Get JpgQuantTableSpec) (value calculateSize)
  "huff" -> p (get :: Get JpgHuffmanTableSpec) (value calculateSize)
  "restart" -> p (get :: Get RestartInterval) (\a@(RestartInterval n) -> [("show",json (show n)),("encoded",packed (encode a)),("size","4")])
  "quantList" -> p (get :: Get (TableList JpgQuantTableSpec)) (\a@(TableList xs) -> [("show",json(show xs)),("encoded",packed(encode a)),("size",show(2+sum(map calculateSize xs)))])
  "huffList" -> p (get :: Get (TableList JpgHuffmanTableSpec)) (\a@(TableList xs) -> [("show",json(show xs)),("encoded",packed(encode a)),("size",show(2+sum(map calculateSize xs)))])
  "skipUntil" -> p skipUntilFrames (const [])
  "skipMarker" -> p skipFrameMarker (const [])
  "ecs" -> parse parseECS (\a -> [("bytes",packed a)]) chunks
  "ecsSimple" -> parse parseECS_simple (\a -> [("bytes",packed a)]) chunks
  "writeComponent" -> w calculateSize (component ns)
  "writeSpec" -> w calculateSize (spec ns)
  "writeFrame" -> w calculateSize (JpgFrameHeader (f 0) (f 1) (f 2) (f 3) (f 4) (map component rows))
  "writeScan" -> w calculateSize (JpgScanHeader (f 0) (f 1) (map spec rows) (f 2,f 3) (f 4) (f 5))
  "writeQuant" -> w calculateSize (JpgQuantTableSpec (f 0) (f 1) (VS.fromList $ map fromIntegral $ concat rows))
  "writeHuff" -> w calculateSize (JpgHuffmanTableSpec (if ns!!0==0 then DcComponent else AcComponent) (f 1) (VU.fromList $ map fromIntegral $ head rows) (V.fromList $ map (VU.fromList . map fromIntegral) $ tail rows))
  "writeJfif" -> w (const 14) (JpgJFIFApp0 (unit (ns!!0)) (f 1) (f 2) (if ns!!3==0 then Nothing else Just (ns!!4)))
  "writeAdobe" -> w (const 12) (JpgAdobeApp14 (f 0) (f 1) (f 2) (transform (ns!!3)))
  "writeApp" -> w (const (-1)) (JpgAppSegment (f 0))
  "writeExtension" -> w (const (-1)) (JpgExtensionSegment (f 0))
  "writeRestartEnd" -> w (const (-1)) (JpgRestartIntervalEnd (f 0))
  _ -> error ("Unknown operation " ++ op)
 where
  input = L.pack (map fromIntegral ns)
  chunks = L.fromChunks (map (B.pack . map fromIntegral) rows)
  p parser describe = parse parser describe input
  w size a = fields (value size a)
  f i = fromIntegral (ns!!i)
  component [a,b,c,d] = JpgComponent (fromIntegral a) (fromIntegral b) (fromIntegral c) (fromIntegral d)
  component _ = error "bad component request"
  spec [a,b,c] = JpgScanSpecification (fromIntegral a) (fromIntegral b) (fromIntegral c)
  spec _ = error "bad scan request"
  unit 1 = JFifPixelsPerInch; unit 2 = JFifPixelsPerCentimeter; unit _ = JFifUnitUnknown
  transform 1 = AdobeYCbCr; transform 2 = AdobeYCck; transform _ = AdobeUnknown
main :: IO ()
main = do
 args <- getArgs
 if args == ["--reference-info"] then print [compilerName, showVersion compilerVersion, "JuicyPixels", "3.3.9", "binary", VERSION_binary]
 else getContents >>= mapM_ respond . lines
 where
  respond line = do
   result <- try (evaluate (force (run (read line)))) :: IO (Either SomeException String)
   putStrLn $ either (\e -> fields [("exception",json (takeWhile (/='\n') (displayException e)))]) id result
