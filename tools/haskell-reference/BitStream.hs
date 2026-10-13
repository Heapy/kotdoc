-- Test-only adapter: execute the unmodified, retained JuicyPixels module.
-- JSON-compatible numeric list output, Haskell Read input (no Aeson dependency).
{-# LANGUAGE CPP #-}
module Main where
import Codec.Picture.BitWriter
import Control.Monad.ST (runST)
import qualified Control.Monad.Trans.State.Strict as S
import System.Environment (getArgs)
import System.Info (compilerVersion)
import Data.Version (showVersion)
import Data.Word (Word8)
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as L

type Op = (String, Integer, Integer, [Word8])
type Request = (String, [Word8], [Op])

reader :: [Op] -> Maybe BoolState -> BoolReader s [Integer]
reader [] _ = pure []
reader ((op,n,_,bs):rest) saved = case op of
  "lsb" -> value (fromIntegral <$> getNextBitsLSBFirst (fromInteger n))
  "msb" -> value (fromIntegral <$> getNextBitsMSBFirst (fromInteger n))
  "jpeg" -> value (fromIntegral <$> getNextIntJpg (fromInteger n))
  "bit" -> value ((\b -> if b then 1 else 0) <$> getNextBitJpg)
  "align" -> byteAlignJpg >> reader rest saved
  "setLsb" -> setDecodedString (B.pack bs) >> reader rest saved
  "setMsb" -> setDecodedStringMSB (B.pack bs) >> reader rest saved
  "setJpeg" -> setDecodedStringJpg (B.pack bs) >> reader rest saved
  "save" -> S.get >>= \s -> reader rest (Just s)
  "restore" -> maybe (error "no saved state") S.put saved >> reader rest saved
  _ -> error ("unknown reader op " ++ op)
  where value action = do
          x <- action
          xs <- reader rest saved
          pure (x:xs)

runReader :: Request -> [Integer]
runReader (mode,bytes,ops) = runST $ do
  let action = reader ops Nothing
  case mode of
    "lsb" -> fst <$> runBoolReaderWith (initBoolState $ B.pack bytes) action
    "jpeg" -> fst <$> runBoolReaderWith (initBoolStateJpg $ B.pack bytes) action
    "msb" -> runBoolReader (setDecodedStringMSB (B.pack bytes) >> action)
    "default" -> runBoolReader action
    "empty" -> fst <$> runBoolReaderWith emptyBoolState action
    _ -> error ("unknown initial mode " ++ mode)

runWriter :: [Op] -> [[Word8]]
runWriter ops = runST $ do
  writer <- newWriteStateRef
  let go [] = pure []
      go ((op,value,bits,_):rest) = case op of
        "jpeg" -> writeBits' writer (fromInteger value) (fromInteger bits) >> go rest
        "gif" -> writeBitsGif writer (fromInteger value) (fromInteger bits) >> go rest
        "finishJpeg" -> do
          bytes <- L.unpack <$> finalizeBoolWriter writer
          restBytes <- go rest
          pure (bytes:restBytes)
        "finishGif" -> do
          bytes <- L.unpack <$> finalizeBoolWriterGif writer
          restBytes <- go rest
          pure (bytes:restBytes)
        _ -> error ("unknown writer op " ++ op)
  go ops

main :: IO ()
main = do
  args <- getArgs
  if args == ["--reference-info"]
    then print ["ghc",showVersion compilerVersion,"JuicyPixels","3.3.9",
                "vector",VERSION_vector,"primitive",VERSION_primitive]
    else interact $ unlines . map (process . read) . lines
  where process request@(mode,_,ops)
          | mode == "writer" = show (runWriter ops)
          | otherwise = show (runReader request)
