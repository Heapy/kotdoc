{-# LANGUAGE OverloadedStrings, FlexibleContexts #-}
-- Verbatim original property bodies from pandoc-types/test/test-pandoc-types.hs.
-- Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; licenses/.
module OriginalRemainingProperties where
import Text.Pandoc.Arbitrary ()
import Text.Pandoc.Definition
import Text.Pandoc.Builder (normalizeTableHead, normalizeTableBody, normalizeTableFoot,
                            emptyCell, simpleFigureWith)
import qualified Text.Pandoc.Builder as Builder
import Data.Aeson (decode, encode)
import Test.QuickCheck (forAll, choose, Property, Arbitrary, Testable, arbitrary, Gen)
prop_roundtrip :: Pandoc -> Bool
prop_roundtrip doc = case decode $ encode doc :: (Maybe Pandoc) of
  Just doc' -> doc == doc'
  _         -> False


withWidth :: Testable prop => (Int -> prop) -> Property
withWidth = forAll $ choose (2 :: Int, 16)

widthNormIsIdempotent :: (Arbitrary a, Show a, Eq a)
                      => (Int -> a -> a) -> Property
widthNormIsIdempotent f = withWidth $
  \n a -> let a' = f n a in f n a' == a'

p_tableNormHeadIdempotent :: Property
p_tableNormHeadIdempotent = widthNormIsIdempotent normalizeTableHead

p_tableNormBodyIdempotent :: Property
p_tableNormBodyIdempotent = widthNormIsIdempotent normalizeTableBody

p_tableNormFootIdempotent :: Property
p_tableNormFootIdempotent = widthNormIsIdempotent normalizeTableFoot

cellSubset :: Cell -> Cell -> Bool
cellSubset (Cell attr1 align1 rs1 cs1 body1) (Cell attr2 align2 rs2 cs2 body2)
  = and [ attr1 == attr2
        , align1 == align2
        , dimValid rs1 rs2
        , dimValid cs1 cs2
        , body1 == body2 ]
  where
    dimValid x y = (y < 1 && x == 1) || (x >= 1 && x <= y)

-- True when the first list is an initial segment of the second,
-- modulo cell subsetting and the appending of padding cells onto the
-- second.
cellsSubsetPad :: [Cell] -> [Cell] -> Bool
cellsSubsetPad (x:xs) (y:ys) = cellSubset x y && cellsSubsetPad xs ys
cellsSubsetPad xs _ = all isPadCell xs
  where
    isPadCell = (== emptyCell)

-- Only valid for the TableHead and TableFoot. See also
-- p_tableNormBodyIsSubset.
rowSubset :: Row -> Row -> Bool
rowSubset (Row a1 x1) (Row a2 x2) = a1 == a2 && cellsSubsetPad x1 x2

-- The remarks in rowSubset apply.
rowsSubset :: [Row] -> [Row] -> Bool
rowsSubset (x:xs) (y:ys) = rowSubset x y && rowsSubset xs ys
rowsSubset []     _      = True
rowsSubset (_:_)  []     = False

normIsSubset :: (Arbitrary a, Show a, Eq a)
             => (Int -> a -> a)
             -> (a -> [Row])
             -> Property
normIsSubset f proj = withWidth $
  \n a -> let a' = f n a in proj a' `rowsSubset` proj a

p_tableNormHeadIsSubset :: Property
p_tableNormHeadIsSubset = normIsSubset normalizeTableHead thproj
  where
    thproj (TableHead _ r) = r

-- Checking that each row is a subset of its unnormalized version is a
-- little onerous in the TableBody (because of the row head/row body
-- distinction), so we settle for testing it only for the first row of
-- the intermediate body. The intermediate head is still checked
-- fully.
p_tableNormBodyIsSubset :: Property
p_tableNormBodyIsSubset = withWidth $
  \n tb -> checkBody n (normalizeTableBody n tb) tb
  where
    cellLength (Cell _ _ _ (ColSpan w) _) = w
    cellLengths = sum . map cellLength
    gatherLen n = gatherLen' n 0
    gatherLen' n count (c:cs) | count < n
      = let (beg, end) = gatherLen' n (count + cellLength c) cs
        in (c : beg, end)
    gatherLen' _ _ cs = ([], cs)
    -- Gather as much of the head as we can from the new and old rows,
    -- then make sure the dimensions line up and the subsetting is
    -- correct.
    checkRow n rhc (Row _ r') (Row _ r)
      = let (rhead', rbody') = gatherLen rhc r'
            (rhead, rbody) = gatherLen rhc r
        in and [ cellLengths rhead' == rhc
               , rhc + cellLengths rbody' == n
               , cellsSubsetPad rhead' rhead
               , cellsSubsetPad rbody' rbody ]
    checkRows n rhc (r':_) (r:_) = checkRow n rhc r' r
    checkRows _ _   []     []    = True
    checkRows _ _   _      _     = False
    checkBody n (TableBody _ (RowHeadColumns rhc) th' tb') (TableBody _ _ th tb)
      = rowsSubset th' th && checkRows n rhc tb' tb

p_tableNormFootIsSubset :: Property
p_tableNormFootIsSubset = normIsSubset normalizeTableFoot tfproj
  where
    tfproj (TableFoot _ r) = r

-- True when the first row in a section (table head, table foot,
-- intermediate header, body of table body) has the correct
-- width. Only with the first row is it easy to check.
firstRowCorrectWidth :: Int -> [Row] -> [Row] -> Bool
firstRowCorrectWidth n (Row _ cs:_) (_:_) = n == sum (map cellLength cs)
  where cellLength (Cell _ _ _ (ColSpan w) _) = w
firstRowCorrectWidth _ []           []    = True
firstRowCorrectWidth _ _            _     = False

testRowCorrectWidth :: (Arbitrary a, Show a, Eq a)
                     => (Int -> a -> a)
                     -> (a -> [Row])
                     -> Property
testRowCorrectWidth f proj = withWidth $
  \n a -> let a' = f n a in firstRowCorrectWidth n (proj a') (proj a)

p_tableNormHeadRowWidth :: Property
p_tableNormHeadRowWidth = testRowCorrectWidth normalizeTableHead thproj
  where
    thproj (TableHead _ r) = r

p_tableNormBodyRowWidth :: Property
p_tableNormBodyRowWidth = withWidth $
  \n tb -> compBody n tb $ normalizeTableBody n tb
  where
    compBody n (TableBody _ _ th tb) (TableBody _ _ th' tb')
      = firstRowCorrectWidth n th' th && firstRowCorrectWidth n tb' tb

p_tableNormFootRowWidth :: Property
p_tableNormFootRowWidth = testRowCorrectWidth normalizeTableFoot tfproj
  where
    tfproj (TableFoot _ r) = r


p_figureRepresentation :: Property
p_figureRepresentation = forAll (arbitrary :: Gen [Inline]) $ \figureCaption ->
  simpleFigureWith
      ("test", [], [])
      (Builder.fromList figureCaption)
      "url"
      "title" ==
      Builder.fromList
        [Figure
         nullAttr
         (Caption Nothing [Plain figureCaption | not (null figureCaption)])
         [Plain [Image ("test", [], []) mempty ("url", "title") ]]]


-- Mechanical exposure of the original forall body; body below is verbatim.
sourceWidthNormIsIdempotent f n a = let a' = f n a in f n a' == a'


-- Mechanical exposure of the original forall body; body below is verbatim.
sourceNormIsSubset f proj n a = let a' = f n a in proj a' `rowsSubset` proj a


-- Mechanical exposure of the original forall body; body below is verbatim.
sourceRowCorrectWidth f proj n a = let a' = f n a in firstRowCorrectWidth n (proj a') (proj a)


-- Mechanical exposure of the original forall body; body below is verbatim.
sourceBodyIsSubset n tb = checkBody n (normalizeTableBody n tb) tb
  where
    cellLength (Cell _ _ _ (ColSpan w) _) = w
    cellLengths = sum . map cellLength
    gatherLen n = gatherLen' n 0
    gatherLen' n count (c:cs) | count < n
      = let (beg, end) = gatherLen' n (count + cellLength c) cs
        in (c : beg, end)
    gatherLen' _ _ cs = ([], cs)
    -- Gather as much of the head as we can from the new and old rows,
    -- then make sure the dimensions line up and the subsetting is
    -- correct.
    checkRow n rhc (Row _ r') (Row _ r)
      = let (rhead', rbody') = gatherLen rhc r'
            (rhead, rbody) = gatherLen rhc r
        in and [ cellLengths rhead' == rhc
               , rhc + cellLengths rbody' == n
               , cellsSubsetPad rhead' rhead
               , cellsSubsetPad rbody' rbody ]
    checkRows n rhc (r':_) (r:_) = checkRow n rhc r' r
    checkRows _ _   []     []    = True
    checkRows _ _   _      _     = False
    checkBody n (TableBody _ (RowHeadColumns rhc) th' tb') (TableBody _ _ th tb)
      = rowsSubset th' th && checkRows n rhc tb' tb


-- Mechanical exposure of the original forall body; body below is verbatim.
sourceBodyRowWidth n tb = compBody n tb $ normalizeTableBody n tb
  where
    compBody n (TableBody _ _ th tb) (TableBody _ _ th' tb')
      = firstRowCorrectWidth n th' th && firstRowCorrectWidth n tb' tb


-- Mechanical exposure of the original forall body; body below is verbatim.
sourceFigureRepresentation figureCaption =
  simpleFigureWith
      ("test", [], [])
      (Builder.fromList figureCaption)
      "url"
      "title" ==
      Builder.fromList
        [Figure
         nullAttr
         (Caption Nothing [Plain figureCaption | not (null figureCaption)])
         [Plain [Image ("test", [], []) mempty ("url", "title") ]]]

