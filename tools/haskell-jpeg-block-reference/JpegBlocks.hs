-- Adapter only: numerical transforms and entropy operations are upstream code.
{-# LANGUAGE CPP #-}
module Main where
import Codec.Picture.Jpg.Internal.Common
import Codec.Picture.Jpg.Internal.FastIdct
import Codec.Picture.Jpg.Internal.DefaultTable
import Codec.Picture.BitWriter
import Codec.Picture.Types (MutableImage(..))
import Control.DeepSeq (force)
import Control.Exception (SomeException, displayException, evaluate, try)
import Control.Monad (replicateM)
import Control.Monad.ST (runST)
import qualified Control.Monad.Trans.State.Strict as S
import System.Environment (getArgs)
import System.Info (compilerVersion)
import Data.Version (showVersion)
import Data.Int (Int16, Int32)
import Data.Char (ord)
import Data.Word (Word8)
import qualified Data.ByteString as B
import qualified Data.Vector.Storable as V
import qualified Data.Vector.Storable.Mutable as M

type Request = (String, [Int], [Int], [Int], [Integer], [Int])

process :: Request -> [[Integer]]
process (op,block,quant,destination,options,bytes)
  | op == "parameter" = [map (fromIntegral . ord) $ show $
      JpgUnpackerParameter (V.fromList $ map fromIntegral block) (V.fromList $ map fromIntegral quant)
        (option 0) (option 1) (option 2) (option 3) (option 4, option 5)
        (option 6, option 7) (option 8, option 9) (option 10) (option 11)
        (option 12) (option 13) (option 14)]
  | op == "blockSize" = [map (fromIntegral . toBlockSize . fromInteger) options]
  | op == "raster" = [concatMap (\(x,y) -> [fromIntegral x,fromIntegral y]) $
      reverse $ S.execState (rasterMap (option 0) (option 1) (\x y -> S.modify ((x,y):))) []]
  | op == "forwardInt" = forward (map fromIntegral block :: [Int32]) (map fromIntegral destination)
  | op == "forwardByte" = forward (map fromIntegral block :: [Word8]) (map fromIntegral destination)
  | op == "forwardValues" = [map fromIntegral $ V.toList $ zigZagReorderForwardv (V.fromList block)]
  | op `elem` ["decodeInt", "unpackInt", "dc", "ac", "dcDefault", "acDefault", "restart"] =
      [runST $ runBoolReader $ do
        setDecodedStringJpg (B.pack $ map fromIntegral bytes)
        _ <- getNextIntJpg (option 1)
        let tree = packHuffmanTree $ Branch (Leaf (fromIntegral $ option 0)) (Leaf (fromIntegral $ option 0))
            defaultTree = if option 0 == 0 then defaultDcLumaHuffmanTree else defaultDcChromaHuffmanTree
            defaultAc = if option 0 == 0 then defaultAcLumaHuffmanTree else defaultAcChromaHuffmanTree
            action = case op of
              "decodeInt" -> (:[]) . fromIntegral <$> decodeInt (option 0)
              "unpackInt" -> (:[]) . fromIntegral <$> unpackInt (option 0)
              "dc" -> (:[]) . fromIntegral <$> dcCoefficientDecode tree
              "ac" -> (\(a,b) -> [fromIntegral a,fromIntegral b]) <$> decodeRrrrSsss tree
              "dcDefault" -> (:[]) . fromIntegral <$> dcCoefficientDecode (packHuffmanTree defaultTree)
              "acDefault" -> (\(a,b) -> [fromIntegral a,fromIntegral b]) <$> decodeRrrrSsss (packHuffmanTree defaultAc)
              "restart" -> (:[]) . fromIntegral <$> decodeRestartInterval
              _ -> error "bad reader operation"
        values <- concat <$> replicateM (option 2) action
        tailBits <- getNextIntJpg 32
        pure (values ++ [fromIntegral tailBits])]
  | op == "unpack" = [runST $ do
        let components = option 0; width = option 6; height = option 7
        pixels <- M.replicate (components * width * height) (fromIntegral (option 8) :: Word8)
        b <- V.thaw $ V.fromList $ map fromIntegral block
        let image = MutableImage width height pixels
        unpackMacroBlock components (option 1) (option 2) (option 3) (option 4) (option 5) image b
        map fromIntegral . V.toList <$> V.freeze pixels]
  | otherwise = runST $ do
      b <- V.thaw $ V.fromList $ map fromIntegral block
      d <- if option 0 == 1 then pure b else V.thaw $ V.fromList $ map fromIntegral destination
      let q = V.fromList $ map fromIntegral quant
      result <- case op of
        "empty" -> createEmptyMutableMacroBlock
        "fast" -> fastIdct b
        "shift" -> mutableLevelShift b
        "idct" -> inverseDirectCosineTransform b
        "dequant" -> deQuantize q b
        "zig" -> zigZagReorder d b
        "forward" -> zigZagReorderForward d b
        "macro" -> decodeMacroBlock q d b
        _ -> error "unknown block operation"
      r <- V.freeze result
      inputAfter <- V.freeze b
      destinationAfter <- V.freeze d
      pure $ map (map fromIntegral . V.toList) [r,inputAfter,destinationAfter]
  where option i = fromInteger (options !! i)
        forward :: (V.Storable a, Integral a) => [a] -> [a] -> [[Integer]]
        forward input output = runST $ do
          b <- V.thaw (V.fromList input)
          d <- if option 0 == 1 then pure b else V.thaw (V.fromList output)
          r <- zigZagReorderForward d b >>= V.freeze
          after <- V.freeze b
          pure $ map (map fromIntegral . V.toList) [r,after]

main :: IO ()
main = do
  args <- getArgs
  if args == ["--reference-info"]
    then print ["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9","vector",VERSION_vector,"primitive",VERSION_primitive]
    else getContents >>= mapM_ capture . lines
  where capture line = do
          result <- try (evaluate (force (process (read line)))) :: IO (Either SomeException [[Integer]])
          putStrLn $ case result of
            Right values -> "{\"values\":" ++ show values ++ "}"
            Left err -> "{\"error\":" ++ show (takeWhile (/= '\n') (displayException err)) ++ "}"
