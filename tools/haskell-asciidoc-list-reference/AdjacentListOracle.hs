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
cases=[("figure-caption-prefix",[Figure nullAttr(Caption Nothing(error"caption-head":error"caption-tail"))[]])
 ,("line-prefix",[LineBlock(error"line-head":error"line-tail")])
 ,("span-class-prefix",[Para[Span("",error"class-head":error"class-tail",[])[Str"x"]]])
 ,("table-column-prefix",[Table nullAttr(Caption Nothing[])(error"column-head":error"column-tail")(TableHead nullAttr[])[](TableFoot nullAttr[])])]

main=mapM_ run cases
 where run(name,bs)=do
        r<-try(evaluate(case runPure(writeAsciiDoc def(Pandoc nullMeta bs))of Left e->error(show e);Right t->T.length t))::IO(Either SomeException Int)
        B.putStrLn$encode$case r of Left e->object["name".=name,"thrown".=takeWhile(/='\n')(displayException e)];Right n->object["name".=name,"length".=n]
