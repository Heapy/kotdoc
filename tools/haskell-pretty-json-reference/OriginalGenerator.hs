{-# OPTIONS_GHC -fno-warn-orphans #-}
module OriginalGenerator where
import Data.Scientific as Scientific
import qualified Test.QuickCheck as QC

instance QC.Arbitrary Scientific where
    arbitrary = QC.frequency
      [ (70, scientific <$> QC.arbitrary
                        <*> intGen)
      , (20, scientific <$> QC.arbitrary
                        <*> bigIntGen)
      , (10, scientific <$> pure 0
                        <*> bigIntGen)
      , (10, (\c e' e -> scientific (c * 10 ^ min 10 (abs e')) e) <$> QC.arbitrary <*> intGen <*> intGen)
      ]

    shrink s = 
        [ scientific c e
        | (c, e) <- QC.shrink (Scientific.coefficient s, Scientific.base10Exponent s)
        ]

bigIntGen :: QC.Gen Int
bigIntGen = QC.sized $ \size -> QC.resize (size * 1000) intGen

intGen :: QC.Gen Int
intGen = QC.arbitrary
