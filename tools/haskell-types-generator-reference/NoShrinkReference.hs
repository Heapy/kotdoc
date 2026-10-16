{-# LANGUAGE OverloadedStrings #-}
-- Original implicit Arbitrary.shrink implementations, without overrides.
import Text.Pandoc.Arbitrary ()
import Text.Pandoc.Definition
import Test.QuickCheck
import Test.QuickCheck.Gen (unGen)
import Test.QuickCheck.Random (mkQCGen)
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
observe :: (Arbitrary a, ToJSON a) => String -> Gen a -> Int -> Int -> Value
observe kind gen seed size = let x = unGen gen (mkQCGen seed) size in object
  ["kind" .= kind,"seed" .= seed,"size" .= size,"input" .= x,"expected" .= shrink x]
main = mapM_ (B.putStrLn . encode) $ concat
  [[observe "pandoc" (arbitrary :: Gen Pandoc) seed size,
    observe "meta" (arbitrary :: Gen Meta) seed size,
    observe "citation" (arbitrary :: Gen Citation) seed size,
    observe "mode" (arbitrary :: Gen CitationMode) seed size,
    observe "math" (arbitrary :: Gen MathType) seed size,
    observe "quote" (arbitrary :: Gen QuoteType) seed size,
    observe "alignment" (arbitrary :: Gen Alignment) seed size,
    observe "style" (arbitrary :: Gen ListNumberStyle) seed size,
    observe "delim" (arbitrary :: Gen ListNumberDelim) seed size]
   | seed <- [1..10], size <- [0,3,8]]
