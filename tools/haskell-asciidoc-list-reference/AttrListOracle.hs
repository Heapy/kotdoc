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
h attr=Header 1 attr[Str"h"]
cases=[("class/span-prefix",[Para[Span("",["other",error"class-head"]++error"class-tail",[])[Str"x"]]])
 ,("class/header-unnumbered-ignored-tail",[h("","unnumbered":error"class-tail",[])])
 ,("class/div-section-ignored-tail",[Div("","section":error"class-tail",[])[h nullAttr]])
 ,("kvs/header-ignored-tail",[h("",[],error"kv-head":error"kv-tail")])
 ,("kvs/div-section-ignored-tail",[Div("",["section"],error"kv-head":error"kv-tail")[h nullAttr]])
 ,("class/header-other-head-before-tail",[h("","other":error"class-head":error"class-tail",[])])
 ,("class/div-merge-missing-section-tail",[Div("","column":error"class-tail",[])[h nullAttr]])
 ,("kvs/header-number-present",[h("",[],("number","9"):error"unused-kv-head":error"kv-tail")])]

main=mapM_ run cases
 where run(name,bs)=do
        r<-try(evaluate(case runPure(writeAsciiDoc def(Pandoc nullMeta bs))of Left e->error(show e);Right t->T.length t))::IO(Either SomeException Int)
        B.putStrLn$encode$case r of Left e->object["name".=name,"thrown".=takeWhile(/='\n')(displayException e)];Right n->object["name".=name,"length".=n]
