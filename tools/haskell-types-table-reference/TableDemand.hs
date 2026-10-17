{-# LANGUAGE OverloadedStrings #-}
import Text.Pandoc.Builder
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import Control.Exception
import System.IO
bad n=error n
root x=x `seq` "root"
first3(a,_,_)=a
second3(_,a,_)=a
third3(_,_,a)=a
r=Row nullAttr[emptyCell]
probes :: [(String,String)]
probes=[
 ("place-ignore-root",show(const True(placeRowSection(bad "hang")(bad "stream")))),
 ("place-force-hang",root(placeRowSection(bad "hang")(bad "stream"))),
 ("place-skip-root",root(placeRowSection[2,bad "tail"](bad "stream"))),
 ("place-skip-first-prefix",show(take 1(first3(placeRowSection[2,bad "tail"](bad "stream"))))),
 ("place-skip-cells",root(second3(placeRowSection[2,bad "tail"](bad "stream")))),
 ("place-skip-remnant",root(third3(placeRowSection[2,bad "tail"](bad "stream")))),
 ("place-empty-hang-stream",root(placeRowSection[](bad "stream"))),
 ("place-empty-hang-cell",root(placeRowSection[][bad "cell"])),
 ("place-invalid-hang-cell",root(placeRowSection[0][bad "cell"])),
 ("place-empty-hang-no-cell-tail",root(placeRowSection[](emptyCell:bad "tail"))),
 ("place-empty-hang-remnant-prefix",show(take 1(third3(placeRowSection[](emptyCell:bad "tail"))))),
 ("place-fit-root",root(placeRowSection[1](emptyCell:bad "tail"))),
 ("place-fit-hang-prefix",show(take 1(first3(placeRowSection[1](emptyCell:bad "tail"))))),
 ("place-fit-cell-prefix",show(take 1(second3(placeRowSection[1](emptyCell:bad "tail"))))),
 ("place-fit-remnant",root(third3(placeRowSection[1](emptyCell:bad "tail")))),
 ("place-predicate-before-bound",root(placeRowSection[1,bad "boundary"][emptyCell])),
 ("place-boundary-tail",root(placeRowSection(1:bad "boundary")[emptyCell])),
 ("place-cell-body-root",root(placeRowSection[][Cell nullAttr AlignDefault 1 1(bad "body")])),
 ("place-cell-body-head",root(placeRowSection[][Cell nullAttr AlignDefault 1 1[bad "block"]])),
 ("place-max-skip",show(take 1(first3(placeRowSection[RowSpan(maxBound::Int)](bad "stream"))))),
 ("clip-ignore",show(take 0(clipRows(bad "rows")))),
 ("clip-force",root(clipRows(bad "rows"))),
 ("clip-head-lazy",root(clipRows[bad "row",r])),
 ("clip-prefix-ignores-second-row",show(take 1(clipRows[r,bad "second"]))),
 ("clip-length-before-head",root(clipRows(bad "head":bad "tail"))),
 ("clip-row-attr-ignores-cell",case head(clipRows[Row nullAttr[bad "cell"]])of Row a _->show a),
 ("head-empty-ignores-width",root(normalizeTableHead(bad "width")(TableHead nullAttr[]))),
 ("foot-empty-ignores-width",root(normalizeTableFoot(bad "width")(TableFoot nullAttr[]))),
 ("body-empty-forces-width",root(normalizeTableBody(bad "width")(TableBody nullAttr 0[][]))),
 ("head-nonempty-root-ignores-width",root(normalizeTableHead(bad "width")(TableHead nullAttr[r]))),
 ("head-row-forces-width",case normalizeTableHead(bad "width")(TableHead nullAttr[r])of TableHead _ xs->root(head xs)),
 ("head-zero-unused-second-cell",case normalizeTableHead 0(TableHead nullAttr[Row nullAttr[emptyCell,bad "second"]])of TableHead _ xs->show xs),
 ("foot-zero-unused-second-cell",case normalizeTableFoot 0(TableFoot nullAttr[Row nullAttr[emptyCell,bad "second"]])of TableFoot _ xs->show xs),
 ("body-zero-unused-second-cell",case normalizeTableBody 0(TableBody nullAttr 0[][Row nullAttr[emptyCell,bad "second"]])of TableBody _ _ _ xs->show xs),
 ("head-prefix-ignores-second-row",case normalizeTableHead 1(TableHead nullAttr[r,bad "second"])of TableHead _ xs->show(take 1 xs)),
 ("foot-prefix-ignores-second-row",case normalizeTableFoot 1(TableFoot nullAttr[r,bad "second"])of TableFoot _ xs->show(take 1 xs)),
 ("cell-ignore-root",show(const True(cellWith(bad "attr")(bad "align")(bad "r")(bad "c")(bad "blocks")))),
 ("cell-strict-fields-order",root(cellWith(bad "attr")(bad "align")(bad "r")(bad "c")(bad "blocks"))),
 ("cell-strict-after-rowspan",root(cellWith(bad "attr")(bad "align")1(bad "c")(bad "blocks"))),
 ("cell-strict-after-spans",root(cellWith(bad "attr")(bad "align")1 1(bad "blocks"))),
 ("cell-strict-after-attr",root(cellWith nullAttr(bad "align")1 1(bad "blocks"))),
 ("legacy-tableWith-ignores-head",show(length(toList(tableWith nullAttr emptyCaption[(AlignDefault,ColWidthDefault)](TableHead nullAttr[r,bad "second"])[](TableFoot nullAttr[]))))),
 ("legacy-table-ignores-head",show(length(toList(table emptyCaption[(AlignDefault,ColWidthDefault)](TableHead nullAttr[r,bad "second"])[](TableFoot nullAttr[]))))),
 ("legacy-simpleTable-ignores-header",show(length(toList(simpleTable[bad "header"][])))),
 ("head-max-width-first-cell",case normalizeTableHead maxBound(TableHead nullAttr[r])of TableHead _ xs->case head xs of Row _ cs->show(take 1 cs)),
 ("foot-min-width-empty-cells",case normalizeTableFoot minBound(TableFoot nullAttr[r])of TableFoot _ xs->case head xs of Row _ cs->show cs),
 ("body-max-width-first-cell",case normalizeTableBody maxBound(TableBody nullAttr 1[][r])of TableBody _ _ _ xs->case head xs of Row _ cs->show(take 1 cs)),
 ("body-min-width-clamp",case normalizeTableBody minBound(TableBody nullAttr (RowHeadColumns(maxBound::Int))[][])of TableBody _ rhc _ _->show rhc),
 ("clip-deep-count",show(length(clipRows(replicate 50000 r)))),
 ("head-deep-count",case normalizeTableHead 1(TableHead nullAttr(replicate 50000 r))of TableHead _ xs->show(length xs)),
 ("table-many-ignores-fields",show(length(toList(tableWith(bad "attr")(bad "caption")(bad "specs")(bad "head")(bad "bodies")(bad "foot"))))),
 ("simple-table-many-ignores-fields",show(length(toList(simpleTable(bad "headers")(bad "rows")))))
 ]
main=do
 hSetBuffering stdout LineBuffering
 mapM_ respond probes
 where
  respond(name,value)=do
   result<-try(evaluate(let bytes=encode value in B.length bytes `seq` bytes))::IO(Either SomeException B.ByteString)
   B.putStrLn(encode name)
   B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result)
