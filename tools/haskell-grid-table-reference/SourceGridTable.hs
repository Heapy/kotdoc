{-# LANGUAGE OverloadedStrings, FlexibleContexts #-}
-- Original Text.Pandoc.Writers.Shared bodies, GPL-2.0-or-later.
-- Copyright (C) 2013-2024 John MacFarlane. Only module/import wiring changed.
module SourceGridTable where
import qualified Data.List as L
import Data.List (transpose, zipWith4, intersperse)
import Data.List.NonEmpty (NonEmpty(..))
import qualified Data.Map as M
import qualified Data.Text as T
import Data.Text (Text)
import Data.Maybe (fromMaybe)
import Safe (maximumMay)
import Text.DocLayout
import Text.Pandoc.Definition
import qualified Text.Pandoc.Writers.AnnotatedTable as Ann
import PandocOptionsModels
-- | Writes a grid table.
gridTable :: Monad m
           => WriterOptions
           -> (WriterOptions -> [Block] -> m (Doc Text)) -- ^ format Doc writer
           -> [ColSpec]
           -> TableHead
           -> [TableBody]
           -> TableFoot
           -> m (Doc Text)
gridTable opts blocksToDoc colspecs' thead' tbodies' tfoot' = do
  let Ann.Table _ _ colspecs thead tbodies tfoot =
        Ann.toTable mempty (Caption Nothing mempty)
                    colspecs' thead' tbodies' tfoot'
  let renderRows = fmap addDummies . mapM (gridRow opts blocksToDoc)
  let getHeadCells (Ann.HeaderRow _ _ cells) = cells
  let getHeadRows (Ann.TableHead _ rs) = map getHeadCells rs
  headCells <- renderRows (getHeadRows thead)
  let getFootRows (Ann.TableFoot _ xs) = map getHeadCells xs
  footCells <- renderRows (getFootRows tfoot)
  -- We don't distinguish between row head and regular cells here:
  let getBodyCells (Ann.BodyRow _ _ rhcells cells) = rhcells ++ cells
  let getBody (Ann.TableBody _ _ hs xs) = map getHeadCells hs <> map getBodyCells xs
  bodyCells <- mapM (renderRows . getBody) tbodies
  let rows = (setTopBorder SingleLine . setBottomBorder DoubleHeaderLine) headCells ++
             (setTopBorder (if null headCells
                               then SingleHeaderLine
                               else SingleLine) . setBottomBorder SingleLine)
                   (mconcat bodyCells) ++
             (setTopBorder DoubleLine . setBottomBorder DoubleLine) footCells
  pure $ gridRows $ redoWidths opts colspecs rows

data ColWidthInfo =
  ColWidthInfo
  { colWidthSpecified :: Int
  , colWidthFull :: Int
  , colWidthMin :: Int
  , colWidthUsed :: Int }
  deriving (Show, Ord, Eq)

-- Returns (current widths, full widths, min widths)
extractColWidths :: WriterOptions -> [[RenderedCell Text]] -> [ColWidthInfo]
extractColWidths opts rows =
  zipWith4 ColWidthInfo specifiedwidths fullwidths minwidths currentwidths
 where
   getWidths calcOffset =
     map (fromMaybe 0 . maximumMay) (transpose (map (concatMap (getCellWidths calcOffset)) rows))
   getCellWidths calcOffset c = replicate (cellColSpan c)
                                 (calcOffset c `div` (cellColSpan c) +
                                  calcOffset c `rem` (cellColSpan c))
   fullwidths = getWidths (max 1 . offset . cellContents)
   minwidths =
     case writerWrapText opts of
       WrapNone -> fullwidths
       _ -> getWidths (minOffset . cellContents)
   specifiedwidths = getWidths cellWidth
   currentwidths = zipWith (\specw minw -> if specw == 0
                                              then 0
                                              else max specw minw)
                           specifiedwidths minwidths

resetWidths :: [Int] -> [RenderedCell Text] -> [RenderedCell Text]
resetWidths _ [] = []
resetWidths [] cs = cs
resetWidths (w:ws) (c:cs) =
  case cellColSpan c of
    1 -> c{ cellWidth = w } : resetWidths ws cs
    n | n < 1 -> c : resetWidths ws cs
      | otherwise -> c{ cellWidth = w + sum (take (n - 1) ws) + (3 * (n-1)) }
                               : resetWidths (drop (n - 1) ws) cs

redoWidths :: WriterOptions -> [ColSpec] -> [[RenderedCell Text]] -> [[RenderedCell Text]]
redoWidths _ _ [] = []
redoWidths opts colspecs rows = map (resetWidths newwidths) rows
 where
  numcols = length colspecs
  widthInfos = extractColWidths opts rows
  colsAvailable = writerColumns opts - (3 * numcols) - 1
  -- now, we need to change colWidthUsed 0 to an appropriate number;
  -- either the full width if it fits easily, or an appropriate fraction
  -- of the remaining width.  This must be done recursively, because
  -- once we decide that the full width can fit, that may leave less
  -- space for the remaining columns with default width to fill.
  recalculateWidths finalRun numRuns infos =
    let numUnassigned = length (filter ((== 0) . colWidthUsed) infos)
        unusedCols = colsAvailable - sum (map colWidthUsed infos)
        defwidth = if numUnassigned == 0
                      then 0
                      else unusedCols `div` numUnassigned
        infos' = map (\info -> if colWidthUsed info == 0
                                  then if finalRun
                                          then info{ colWidthUsed =
                                                      max (colWidthMin info) defwidth }
                                          else if colWidthFull info <= defwidth
                                               then info{ colWidthUsed = colWidthFull info }
                                               else info
                                  else info) infos
     in if finalRun || numRuns > 4
           then infos'
           else if infos == infos'
                 then recalculateWidths True (numRuns + 1) infos
                       -- run again, filling in unassigned widths with a fraction of the
                       -- remaining width
                 else recalculateWidths False (numRuns + 1) infos'
                       -- run again, filling in unassigned widths only if the full with
                       -- would be less than the fraction of remaining width
  newwidths = map colWidthUsed $ recalculateWidths False (1 :: Int) widthInfos

makeDummy :: RenderedCell Text -> RenderedCell Text
makeDummy c =
    RenderedCell{ cellColNum = cellColNum c,
                  cellColSpan = cellColSpan c,
                  cellColSpecs = cellColSpecs c,
                  cellAlign = AlignDefault,
                  cellRowSpan = cellRowSpan c - 1,
                  cellWidth = cellWidth c,
                  cellContents = mempty,
                  cellBottomBorder = NoLine,
                  cellTopBorder = NoLine }

addDummies :: [[RenderedCell Text]] -> [[RenderedCell Text]]
addDummies = reverse . L.foldl' go []
 where
   go [] cs = [cs]
   go (prevRow:rs) cs = addDummiesToRow prevRow cs : prevRow : rs
   addDummiesToRow [] cs = cs
   addDummiesToRow ds [] = map makeDummy ds
   addDummiesToRow (d:ds) (c:cs) =
     if cellColNum d < cellColNum c
        then makeDummy d : addDummiesToRow ds (c:cs)
        else c : addDummiesToRow
                   (dropWhile (\x ->
                       cellColNum x < cellColNum c + cellColSpan c) (d:ds))
                   cs


setTopBorder :: LineStyle -> [[RenderedCell Text]] -> [[RenderedCell Text]]
setTopBorder _ [] = []
setTopBorder sty (cs:rest) = (map (\c -> c{ cellTopBorder = sty }) cs) : rest

setBottomBorder :: LineStyle -> [[RenderedCell Text]] -> [[RenderedCell Text]]
setBottomBorder _ [] = []
setBottomBorder sty [cs] = [map (\c -> c{ cellBottomBorder = sty }) cs]
setBottomBorder sty (c:cs) = c : setBottomBorder sty cs

gridRows :: [[RenderedCell Text]] -> Doc Text
gridRows [] = mempty
gridRows (x:xs) =
  (case x of
     [] -> mempty
     (c:_) | isHeaderStyle (cellTopBorder c)
            -> formatHeaderLine (cellTopBorder c) (x:xs)
           | otherwise
            -> formatBorder cellTopBorder False x)
  $$
  vcat (zipWith (rowAndBottom (x:xs)) (x:xs) (xs ++ [[]]))
 where
  -- generate wrapped contents. include pipe borders, bottom and left

  renderCellContents c =
    -- we don't use cblock or lblock because the content might
    -- be interpreted as an indented code block...even though it
    -- would look better to right-align right-aligned cells...
    -- (TODO: change this on parsing side?)
    lblock (cellWidth c) (cellContents c)

  formatRow cs = vfill "| " <>
   hcat (intersperse (vfill " | ") (map renderCellContents cs)) <> vfill " |"

  rowAndBottom allRows thisRow nextRow =
    let isLastRow = null nextRow
        border1 = case thisRow of
                     [] -> mempty
                     (c:_) -> if isHeaderStyle (cellBottomBorder c)
                                 then formatHeaderLine (cellBottomBorder c) allRows
                                 else formatBorder cellBottomBorder False thisRow
        border2 = case nextRow of
                     [] -> mempty
                     (c:_) -> if isHeaderStyle (cellTopBorder c)
                                 then formatHeaderLine (cellTopBorder c) allRows
                                 else formatBorder cellTopBorder False nextRow
        combinedBorder = if isLastRow
                            then border1
                            else literal $ combineBorders
                                  (render Nothing border1) (render Nothing border2)
    in formatRow thisRow $$ combinedBorder

combineBorders :: Text -> Text -> Text
combineBorders t1 t2 =
  if T.null t1
     then t2
     else T.zipWith go t1 t2
 where
   go '+' _ = '+'
   go _ '+' = '+'
   go ':' _ = ':'
   go _ ':' = ':'
   go '|' '-' = '+'
   go '-' '|' = '+'
   go '|' '=' = '+'
   go '=' '|' = '+'
   go '=' _ = '='
   go _ '=' = '='
   go ' ' d = d
   go c _   = c

formatHeaderLine :: Show a => LineStyle -> [[RenderedCell a]] -> Doc Text
formatHeaderLine lineStyle rows =
  literal $ L.foldl'
    (\t row -> combineBorders t (render Nothing $ formatBorder (const lineStyle) True row))
    mempty rows

formatBorder :: Show a => (RenderedCell a -> LineStyle) -> Bool
             -> [RenderedCell a] -> Doc Text
formatBorder borderStyle alignMarkers cs =
  borderParts <> if lastBorderStyle == NoLine
                            then char '|'
                            else char '+'
 where
   (lastBorderStyle, borderParts) = L.foldl' addBorder (NoLine, mempty) cs
   addBorder (prevBorderStyle, accum) c =
     (borderStyle c, accum <> char junctionChar <> toBorderSection c)
      where junctionChar = case (borderStyle c, prevBorderStyle) of
                               (NoLine, NoLine) -> '|'
                               _ -> '+'
   toBorderSection c =
       text $ leftalign : replicate (cellWidth c) lineChar ++ [rightalign]
     where
       lineChar = case borderStyle c of
                     NoLine -> ' '
                     SingleLine -> '-'
                     SingleHeaderLine -> '-'
                     DoubleLine -> '='
                     DoubleHeaderLine -> '='
       (leftalign, rightalign) =
           case cellAlign c of
             _ | not alignMarkers -> (lineChar,lineChar)
             AlignLeft -> (':',lineChar)
             AlignCenter -> (':',':')
             AlignRight -> (lineChar,':')
             AlignDefault -> (lineChar,lineChar)

data LineStyle = NoLine
               | SingleLine
               | DoubleLine
               | SingleHeaderLine
               | DoubleHeaderLine
    deriving (Show, Ord, Eq)

isHeaderStyle :: LineStyle -> Bool
isHeaderStyle SingleHeaderLine = True
isHeaderStyle DoubleHeaderLine = True
isHeaderStyle _ = False

data RenderedCell a =
  RenderedCell{ cellColNum :: Int
              , cellColSpan :: Int
              , cellColSpecs :: NonEmpty ColSpec
              , cellAlign :: Alignment
              , cellRowSpan :: Int
              , cellWidth :: Int
              , cellContents :: Doc a
              , cellBottomBorder :: LineStyle
              , cellTopBorder :: LineStyle
              }
  deriving (Show)

getColWidth :: ColSpec -> Double
getColWidth (_, ColWidth n) = n
getColWidth (_, ColWidthDefault) = 0 -- TODO?

toCharWidth :: WriterOptions -> Double -> Int
toCharWidth opts width =
  max 0 (floor (width * fromIntegral (writerColumns opts)) - 3)

gridRow :: (Monad m, HasChars a)
        => WriterOptions
        -> (WriterOptions -> [Block] -> m (Doc a)) -- ^ format Doc writer
        -> [Ann.Cell]
        -> m [RenderedCell a]
gridRow opts blocksToDoc = mapM renderCell
 where
  renderer = blocksToDoc opts
  renderCell (Ann.Cell cellcolspecs (Ann.ColNumber colnum)
               (Cell _ _ (RowSpan rowspan) _ blocks)) = do
    let ((align,_):|_) = cellcolspecs
    let width = toCharWidth opts $ sum (fmap getColWidth cellcolspecs)
    rendered <- renderer blocks
    pure $ RenderedCell{ cellColNum = colnum,
                         cellColSpan = length cellcolspecs,
                         cellColSpecs = cellcolspecs,
                         cellAlign = align,
                         cellRowSpan = rowspan,
                         cellWidth = width,
                         cellContents = rendered,
                         cellBottomBorder = if rowspan < 2
                                               then SingleLine
                                               else NoLine,
                         cellTopBorder = SingleLine }


-- | Determine whether all rows and their cells are empty.
allRowsEmpty :: [Row] -> Bool
allRowsEmpty = all isEmptyRow
 where
  isEmptyRow (Row _ cells) = all isEmptyCell cells
  isEmptyCell (Cell _ _ _ _ blocks) = null blocks

-- | Concatenates the header and body Rows of a List of TableBody into a flat
-- List of Rows.
tableBodiesToRows :: [TableBody] -> [Row]
tableBodiesToRows = concatMap tableBodyToRows
  where
    tableBodyToRows (TableBody _ _ headerRows bodyRows) = headerRows ++ bodyRows

-- | Insert the current span information of a table cell to keep track of it in
-- subsequent rows.
--
-- If 'RowSpan' @> 1@, the current span information will be inserted. Otherwise
-- the previous span information will be left unchanged.
--
-- Use 'takePreviousSpansAtColumn' to take previous span information at
-- subsequent rows. Use 'decrementTrailingRowSpans' to handle previous trailing
-- spans at the end of a row.
--
-- For writers that need to manually apply the 'RowSpan' of cells over multiple
-- rows or otherwise have to keep track of it.
insertCurrentSpansAtColumn :: Int -> M.Map Int (RowSpan, ColSpan) -> RowSpan -> ColSpan -> M.Map Int (RowSpan, ColSpan)
insertCurrentSpansAtColumn columnPosition previousSpans (RowSpan rowSpan) colSpan =
  if (rowSpan > 1)
    then M.insert columnPosition (RowSpan rowSpan - 1, colSpan) previousSpans -- Minus its own row.
    else previousSpans

-- | Take previous span information at a column position that was added with
-- 'insertCurrentSpansAtColumn' if available.
--
-- If the previous 'RowSpan' @>= 1@, this will return 'Just' the previous
-- 'ColSpan' and an adjusted span information where that 'RowSpan' is either
-- decremented or deleted if it would fall to 0. Otherwise this will return
-- 'Nothing'.
takePreviousSpansAtColumn :: Int -> M.Map Int (RowSpan, ColSpan) -> Maybe (ColSpan, M.Map Int (RowSpan, ColSpan))
takePreviousSpansAtColumn columnPosition previousSpans
  | Just previous@(RowSpan previousRowSpan, previousColSpan) <- M.lookup columnPosition previousSpans
  , previousRowSpan >= 1 = Just (previousColSpan, decrementPreviousRowSpans previous)
  | otherwise = Nothing
 where
  decrementPreviousRowSpans (RowSpan previousRowSpan, previousColSpan) =
    if previousRowSpan > 1
      then M.insert columnPosition (RowSpan previousRowSpan - 1, previousColSpan) previousSpans
      else M.delete columnPosition previousSpans

-- | Decrement all previously tracked trailing 'RowSpan' elements at or after a
-- column position.
--
-- For handling previous row spans that are next to the end of a row's cells
-- that were previously added with 'insertCurrentSpansAtColumn'.
decrementTrailingRowSpans :: Int -> M.Map Int (RowSpan, ColSpan) -> M.Map Int (RowSpan, ColSpan)
decrementTrailingRowSpans columnPosition = M.mapMaybeWithKey decrementTrailing
  where
    decrementTrailing previousColumnPosition previousSpan@(RowSpan rowSpan, colSpan)
      | previousColumnPosition >= columnPosition && rowSpan >= 1 =
          if rowSpan > 1
            then Just (RowSpan rowSpan - 1, colSpan)
            else Nothing  -- span is used up; drop it instead of keeping a 0 entry
      | otherwise = Just previousSpan

recalculateWidthsProbe :: Int -> [ColWidthInfo] -> [ColWidthInfo]
recalculateWidthsProbe colsAvailable = recalculateWidths False (1 :: Int)
 where
  recalculateWidths finalRun numRuns infos =
    let numUnassigned = length (filter ((== 0) . colWidthUsed) infos)
        unusedCols = colsAvailable - sum (map colWidthUsed infos)
        defwidth = if numUnassigned == 0
                      then 0
                      else unusedCols `div` numUnassigned
        infos' = map (\info -> if colWidthUsed info == 0
                                  then if finalRun
                                          then info{ colWidthUsed =
                                                      max (colWidthMin info) defwidth }
                                          else if colWidthFull info <= defwidth
                                               then info{ colWidthUsed = colWidthFull info }
                                               else info
                                  else info) infos
     in if finalRun || numRuns > 4
           then infos'
           else if infos == infos'
                 then recalculateWidths True (numRuns + 1) infos
                       -- run again, filling in unassigned widths with a fraction of the
                       -- remaining width
                 else recalculateWidths False (numRuns + 1) infos'
                       -- run again, filling in unassigned widths only if the full with
                       -- would be less than the fraction of remaining width
