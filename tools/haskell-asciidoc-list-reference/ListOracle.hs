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
cases=[("block-root",error"block-head":error"block-tail")
 ,("inline-prefix",[Para(error"inline-head":Str"second":error"unused-tail")])
 ,("plain-prefix",[Plain(error"inline-head":Str"second":error"unused-tail")])
 ,("quote-prefix",[BlockQuote(error"block-head":error"block-tail")])
 ,("div-prefix",[Div nullAttr(error"block-head":error"block-tail")])
 ,("inline-tail-before-head",[Para(error"inline-head":error"inline-tail")])
 ,("inline-second-before-tail",[Para(Str"first":error"second-head":error"unused-tail")])
 ,("inline-space-second",[Para(Space:error"second-head":error"unused-tail")])
 ,("header-query-root",Header 1 nullAttr[Str"h"]:error"block-head":error"unused-tail")
 ,("header-query-note",[Header 1 nullAttr[Note(error"note-head":error"note-tail")]])
 ,("header-query-citation",[Header 1 nullAttr[Cite[Citation "x" [Note(error"note-head":error"note-tail")] [] NormalCitation 0 0][]]])
 ,("header-query-figure",[Header 1 nullAttr[],Figure nullAttr(Caption Nothing(error"caption-head":error"caption-tail"))[]])
 ,("header-query-table",[Header 1 nullAttr[],Table nullAttr(Caption Nothing[])[](TableHead nullAttr[Row nullAttr[Cell nullAttr AlignDefault(RowSpan 1)(ColSpan 1)(error"cell-head":error"cell-tail")]])[](TableFoot nullAttr[])])
 ,("empty",[]),("finite",[Para[Str"first",Space,Str"second"]])]
main=mapM_ run cases
 where run(name,bs)=do
        r<-try(evaluate(case runPure(writeAsciiDoc def(Pandoc nullMeta bs))of Left e->error(show e);Right t->T.length t))::IO(Either SomeException Int)
        B.putStrLn$encode$case r of Left e->object["name".=name,"thrown".=takeWhile(/='\n')(displayException e)];Right n->object["name".=name,"length".=n]
