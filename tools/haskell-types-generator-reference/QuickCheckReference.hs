{-# LANGUAGE OverloadedStrings #-}
-- Observe original QuickCheck 2.16.0.0 / splitmix 0.1.3.2 / random 1.3.1.
import Test.QuickCheck
import Test.QuickCheck.Gen (unGen)
import Test.QuickCheck.Random (mkQCGen, QCGen(..))
import qualified System.Random.SplitMix as SM
import System.Random (split)
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B

state (QCGen g) = let (a,b) = SM.unseedSMGen g in [a,b]
observe seed size = let { r = mkQCGen seed; run g = unGen g r size;
                          (left,right) = split r; q = chooseInt (-7,259) }
  in object
  ["seed" .= seed, "size" .= size, "state" .= state r,
   "left" .= state left, "right" .= state right,
   "chooseInt" .= run q,
   "choose" .= run (choose (-7 :: Int,259)),
   "map" .= run ((+10) <$> q),
   "bind" .= run (q >>= \a -> q >>= \b -> pure (a,b)),
   "apply" .= run ((,) <$> q <*> q),
   "vector" .= run (vectorOf 10 q),
   "list" .= run (listOf q),
   "list1" .= run (listOf1 q),
   "positive" .= run ((arbitrary :: Gen Int) `suchThat` (>0)),
   "integer" .= run (arbitrary :: Gen Int)]
main = mapM_ (B.putStrLn . encode . uncurry observe)
  [(seed,size) | seed <- [minBound,maxBound,-100,-1,0] ++ [1..100], size <- [0,1,3,8]]
