-- Test adapter only: all algorithms execute unchanged vendored upstream modules.
{-# LANGUAGE CPP #-}
module Main where
import Codec.Picture.Jpg.Internal.DefaultTable
import Codec.Picture.BitWriter
import Control.DeepSeq (force)
import Control.Exception (SomeException, displayException, evaluate, try)
import Control.Monad (replicateM)
import Control.Monad.ST (runST)
import System.Environment (getArgs)
import System.Info (compilerVersion)
import Data.Version (showVersion)
import qualified Data.ByteString as B
import qualified Data.Vector as V
import qualified Data.Vector.Storable as SV

type Request = (String, String, [[Int]], [Int], Integer, [Int], Int)

flatten :: HuffmanTree -> [Integer]
flatten Empty = [-2]
flatten (Leaf v) = [fromIntegral v]
flatten (Branch l r) = -1 : flatten l ++ flatten r

unflatten :: [Int] -> (HuffmanTree, [Int])
unflatten (-2:xs) = (Empty,xs)
unflatten (-1:xs) = let (l,ys) = unflatten xs; (r,zs) = unflatten ys in (Branch l r,zs)
unflatten (v:xs) = (Leaf (fromIntegral v),xs)
unflatten [] = error "missing tree"

process :: Request -> [Integer]
process (op,which,rows,values,quality,bytes,count) = case op of
  "table" -> concatMap (\r -> (-1) : map fromIntegral r) table
  "quant" -> map fromIntegral $ SV.toList quant
  "tree" -> flatten tree
  "pack" -> map fromIntegral $ SV.toList $ packHuffmanTree tree
  "inverse" -> concatMap (\(depth,code) -> [fromIntegral depth,fromIntegral code]) $ V.toList $ makeInverseTable tree
  "decode" -> runST $ runBoolReader $ do
    setDecodedStringJpg (B.pack $ map fromIntegral bytes)
    result <- replicateM count (huffmanPackedDecode packed)
    tailBits <- getNextIntJpg 32
    pure (map fromIntegral result ++ [fromIntegral tailBits])
  "scale" -> map fromIntegral $ SV.toList $ scaleQuantisationMatrix (fromInteger quality) quant
  "macro" -> map fromIntegral $ SV.toList $ makeMacroBlock values
  _ -> error "unknown operation"
  where table = case which of
          "dc-luma" -> defaultDcLumaHuffmanTable
          "dc-chroma" -> defaultDcChromaHuffmanTable
          "ac-luma" -> defaultAcLumaHuffmanTable
          "ac-chroma" -> defaultAcChromaHuffmanTable
          _ -> map (map fromIntegral) rows
        tree = case which of
          "direct" -> fst (unflatten values)
          "dc-luma" -> defaultDcLumaHuffmanTree
          "dc-chroma" -> defaultDcChromaHuffmanTree
          "ac-luma" -> defaultAcLumaHuffmanTree
          "ac-chroma" -> defaultAcChromaHuffmanTree
          _ -> buildHuffmanTree table
        quant = case which of
          "luma" -> defaultLumaQuantizationTable
          "chroma" -> defaultChromaQuantizationTable
          _ -> SV.fromList $ map fromIntegral values
        packed = if which == "packed" then SV.fromList $ map fromIntegral values else packHuffmanTree tree

main :: IO ()
main = do
  args <- getArgs
  if args == ["--reference-info"]
    then print ["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector,"primitive",VERSION_primitive]
    else do
      input <- getContents
      mapM_ capture (lines input)
  where capture line = do
          result <- try (evaluate (force (process (read line)))) :: IO (Either SomeException [Integer])
          putStrLn $ case result of
            Right values -> "{\"values\":" ++ show values ++ "}"
            Left err -> "{\"error\":" ++ show (takeWhile (/= '\n') (displayException err)) ++ "}"
