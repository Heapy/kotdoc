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
cases=[("protocol/note-wrong-head",[Para[Note(BlockQuote[]:error"unused-tail")]])
 ,("protocol/note-plain",[Para[Note[Plain[Str"x"]]]])
 ,("protocol/span-empty",[Para[Span nullAttr[Str"x"]]])
 ,("protocol/span-mark",[Para[Span("",["mark"],[])[Str"x"]]])
 ,("protocol/span-roles",[Para[Span("",["a","b"],[])[Str"x"]]])
 ,("protocol/code-classes",[CodeBlock("",["numberLines","kotlin"],[])"x"])
 ,("protocol/line-outer",[LineBlock[[Str"x"],[Str"y"]]])
 ,("protocol/quote-recheck",[BlockQuote[Plain[Str"x"]]])
 ,("protocol/caption-blocks",[Figure nullAttr(Caption Nothing[Plain[Str"x"]])[Plain[Image nullAttr[Str"a"]("x.png","")]]])
 ,("protocol/caption-list-merge",[Figure nullAttr(Caption Nothing[BulletList[[Plain[Emph[Str"x"]]],[Plain[Emph[Str"y"]]]]])[Plain[Image nullAttr[Str"a"]("x.png","")]]])
 ,("protocol/caption-merge",[Figure nullAttr(Caption Nothing[Plain[Emph[Str"x"]],Plain[Emph[Str"y"]]])[Plain[Image nullAttr[Str"a"]("x.png","")]]])]


main=mapM_ run cases
 where run(name,bs)=do
        r<-try(evaluate(case runPure(writeAsciiDoc def(Pandoc nullMeta bs))of Left e->error(show e);Right t->T.length t))::IO(Either SomeException Int)
        B.putStrLn$encode$case r of Left e->object["name".=name,"thrown".=takeWhile(/='\n')(displayException e)];Right n->object["name".=name,"length".=n]
