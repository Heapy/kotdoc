{-# LANGUAGE OverloadedStrings #-}
-- Observe all actual original Arbitrary instances, including builder wrappers.
import Text.Pandoc.Arbitrary ()
import Text.Pandoc.Definition
import qualified Text.Pandoc.Builder as Builder
import Test.QuickCheck
import Test.QuickCheck.Gen (unGen)
import Test.QuickCheck.Random (mkQCGen)
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
observe :: ToJSON a => String -> Gen a -> Int -> Int -> Value
observe kind gen seed size = object ["kind" .= kind,"seed" .= seed,"size" .= size,
  "expected" .= unGen gen (mkQCGen seed) size]
main = mapM_ (B.putStrLn . encode) $ concat
  [[observe "inlines" (Builder.toList <$> (arbitrary :: Gen Builder.Inlines)) seed size,
    observe "blocks" (Builder.toList <$> (arbitrary :: Gen Builder.Blocks)) seed size,
    observe "inline" (arbitrary :: Gen Inline) seed size,
    observe "block" (arbitrary :: Gen Block) seed size,
    observe "pandoc" (arbitrary :: Gen Pandoc) seed size,
    observe "mode" (arbitrary :: Gen CitationMode) seed size,
    observe "citation" (arbitrary :: Gen Citation) seed size,
    observe "row" (arbitrary :: Gen Row) seed size,
    observe "head" (arbitrary :: Gen TableHead) seed size,
    observe "body" (arbitrary :: Gen TableBody) seed size,
    observe "foot" (arbitrary :: Gen TableFoot) seed size,
    observe "cell" (arbitrary :: Gen Cell) seed size,
    observe "caption" (arbitrary :: Gen Caption) seed size,
    observe "math" (arbitrary :: Gen MathType) seed size,
    observe "quote" (arbitrary :: Gen QuoteType) seed size,
    observe "meta" (arbitrary :: Gen Meta) seed size,
    observe "alignment" (arbitrary :: Gen Alignment) seed size,
    observe "style" (arbitrary :: Gen ListNumberStyle) seed size,
    observe "delim" (arbitrary :: Gen ListNumberDelim) seed size]
   | seed <- [1..50], size <- [0,1,2,3,8,30]]
