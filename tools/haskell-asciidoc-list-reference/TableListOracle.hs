{-# LANGUAGE OverloadedStrings #-}
-- Pandoc authors, GPL-2.0-or-later; additive original demand controls.
import SourceAsciiDocWriter
import Text.Pandoc.Definition
import Text.Pandoc.Class
import Data.Default
import Control.Exception
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
cases :: [(T.Text,[Block])]
cell bs=Cell nullAttr AlignDefault(RowSpan 1)(ColSpan 1)bs
row cs=Row nullAttr cs
goodCell=cell[Plain[Str"x"]]
goodRow=row[goodCell]
badCells=error"cell-head":goodCell:error"cell-tail"
badRows=error"row-head":goodRow:error"row-tail"
body rows=TableBody nullAttr 0[]rows
table h bodies f=Table nullAttr(Caption Nothing[])[(AlignDefault,ColWidthDefault)](TableHead nullAttr h)bodies(TableFoot nullAttr f)
badInline=cell[Plain(error"inline-head":Str"second":error"inline-tail")]
spanCell n=Cell nullAttr AlignDefault(RowSpan n)(ColSpan 1)[Plain(error"inline-head":Str"second":error"inline-tail")]
cases=[("cells/header",[table[row badCells][][]])
 ,("cells/body-head",[table[][TableBody nullAttr 0[row badCells][]][]])
 ,("cells/body",[table[][body[row badCells]][]])
 ,("cells/footer",[table[][][row badCells]])
 ,("rows/header",[table badRows[][]])
 ,("rows/body-head",[table[][TableBody nullAttr 0 badRows[]][]])
 ,("rows/body",[table[][body badRows][]])
 ,("rows/footer",[table[][]badRows])
 ,("bodies/prefix",[table[](error"body-head":body[]:error"body-tail")[]])
 ,("action/body-before-next-row",[table[][body(row[badInline]:error"row-head":error"row-tail")][]])
 ,("action/header-second-before-first-header",[table(row[badInline]:error"row-head":error"row-tail")[][]])
 ,("action/footer-second-before-first-footer",[table[][](row[badInline]:error"row-head":error"row-tail")])
 ,("action/body-before-columns",[Table nullAttr(Caption Nothing[])(error"column-head":error"column-tail")(TableHead nullAttr[])[body[row[badInline]]](TableFoot nullAttr[])])
 ,("action/empty-body-needs-columns",[Table nullAttr(Caption Nothing[])(error"column-head":error"column-tail")(TableHead nullAttr[])[body[row[]]](TableFoot nullAttr[])])
 ,("rowspan/body-consumes-next-row",[table[][body(row[spanCell 2]:error"row-head":error"row-tail")][]])
 ,("rowspan/body-ignored-later-tail",[table[][body(row[spanCell 2]:row[]:error"row-tail")][]])
 ,("rowspan/body-needs-third-tail",[table[][body(row[spanCell 4]:row[]:error"row-tail")][]])
 ,("cells/block-prefix",[table[][body[row[cell(error"block-head":Plain[Str"second"]:error"block-tail")]]][]])
 ,("nested/ignored-wrong-block-tail",[table[][body[row[cell[table[][body[row[cell(BlockQuote[]:error"unused-head":error"unused-tail")]]][]]]]][]])]

main=mapM_ run cases
 where run(name,bs)=do
        r<-try(evaluate(case runPure(writeAsciiDoc def(Pandoc nullMeta bs))of Left e->error(show e);Right t->T.length t))::IO(Either SomeException Int)
        B.putStrLn$encode$case r of Left e->object["name".=name,"thrown".=takeWhile(/='\n')(displayException e)];Right n->object["name".=name,"length".=n]
