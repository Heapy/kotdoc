-- Mechanically extracted active expressions from the pinned F2mSpec.
module OriginalF2mProperties (propertyAt) where
import Crypto.Number.F2m
import Crypto.Number.Basic (log2)
import Data.Bits
import Data.Maybe
import Test.QuickCheck (Positive(..),NonNegative(..))
propertyAt :: Int -> Integer -> Integer -> Integer -> Integer -> Bool
propertyAt index m a b c = case index of
  0 ->
    (\a b -> a `addF2m` b == b `addF2m` a) (a) (b)
  1 ->
    (\a b c -> (a `addF2m` b) `addF2m` c == a `addF2m` (b `addF2m` c)) (a) (b) (c)
  2 ->
    (\a -> a `addF2m` 0 == a) (a)
  3 ->
    (\a -> a `addF2m` a == 0) (a)
  4 ->
    (\a b -> (a `addF2m` b) .&. b == (a .&. b) `addF2m` b) (a) (b)
  5 ->
    (\(Positive m) (NonNegative a) -> modF2m m a == modF2m m (modF2m m a)) (Positive m) (NonNegative a)
  6 ->
    (\(Positive m) (NonNegative a) -> modF2m m a < 2 ^ log2 m) (Positive m) (NonNegative a)
  7 ->
    (\(Positive m) -> let a = 2 ^ log2 m - 1 in modF2m m (m `addF2m` a) == a) (Positive m)
  8 ->
    (\(Positive m) (NonNegative a) -> modF2m m a >= 0) (Positive m) (NonNegative a)
  9 ->
    (\(Positive m) -> modF2m m m == 0) (Positive m)
  10 ->
    (\(Positive m) (NonNegative a) (NonNegative b) ->
         modF2m m a `addF2m` modF2m m b == modF2m m (a `addF2m` b)) (Positive m) (NonNegative a) (NonNegative b)
  11 ->
    (\(Positive m) (NonNegative a) (NonNegative b) -> mulF2m m a b == mulF2m m b a) (Positive m) (NonNegative a) (NonNegative b)
  12 ->
    (\(Positive m) (NonNegative a) (NonNegative b) (NonNegative c) ->
         mulF2m m (mulF2m m a b) c == mulF2m m a (mulF2m m b c)) (Positive m) (NonNegative a) (NonNegative b) (NonNegative c)
  13 ->
    (\(Positive m) (NonNegative a) -> mulF2m m a 1 == modF2m m a) (Positive m) (NonNegative a)
  14 ->
    (\(Positive m) (NonNegative a) -> mulF2m m a 0 == 0) (Positive m) (NonNegative a)
  15 ->
    (\(Positive m) (NonNegative a) (NonNegative b) (NonNegative c) ->
         mulF2m m a (b `addF2m` c) == mulF2m m a b `addF2m` mulF2m m a c) (Positive m) (NonNegative a) (NonNegative b) (NonNegative c)
  16 ->
    (\(Positive m) (NonNegative a) -> mulF2m m a a == squareF2m m a) (Positive m) (NonNegative a)
  17 ->
    (let m = 65581 :: Integer
         nums = [0 .. 65535 :: Integer]
      in nums == [let y = sqrtF2m m x in squareF2m m y | x <- nums])
  18 ->
    (\(Positive m) (NonNegative a) -> powF2m m a 2 == squareF2m m a) (Positive m) (NonNegative a)
  19 ->
    (\(Positive m) (NonNegative a) -> powF2m m a 1 == modF2m m a) (Positive m) (NonNegative a)
  20 ->
    (\(Positive m) (NonNegative a) -> powF2m m a 0 == modF2m m 1) (Positive m) (NonNegative a)
  21 ->
    (\(Positive m) (NonNegative a) (NonNegative b) (NonNegative c) ->
         powF2m m (mulF2m m a b) c == mulF2m m (powF2m m a c) (powF2m m b c)) (Positive m) (NonNegative a) (NonNegative b) (NonNegative c)
  22 ->
    (\(Positive m) (NonNegative a) (NonNegative b) (NonNegative c) ->
         powF2m m a (b + c) == mulF2m m (powF2m m a b) (powF2m m a c)) (Positive m) (NonNegative a) (NonNegative b) (NonNegative c)
  23 ->
    (\(Positive m) (NonNegative a) (NonNegative b) (NonNegative c) ->
         powF2m m a (b * c) == powF2m m (powF2m m a b) c) (Positive m) (NonNegative a) (NonNegative b) (NonNegative c)
  24 ->
    (\(Positive m) (NonNegative a) ->
         maybe True (\c -> mulF2m m c a == modF2m m 1) (invF2m m a)) (Positive m) (NonNegative a)
  25 ->
    (\(NonNegative a) -> a < 2 || invF2m (squareF2m' a `addF2m` 1) a == Just a) (NonNegative a)
  26 ->
    (\(Positive m) (NonNegative a) -> divF2m m 1 a == invF2m m a) (Positive m) (NonNegative a)
  27 ->
    (\(Positive m) (NonNegative a) (NonNegative b) ->
         divF2m m a b == (mulF2m m a <$> invF2m m b)) (Positive m) (NonNegative a) (NonNegative b)
  28 ->
    (\(Positive m) (NonNegative a) (NonNegative b) ->
         isNothing (invF2m m b) || divF2m m (mulF2m m a b) b == Just (modF2m m a)) (Positive m) (NonNegative a) (NonNegative b)
  _ -> error "unknown property"
