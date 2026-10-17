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
cases=[("link-label-prefix",[Para[Link nullAttr(error"inline-head":Str"second":error"unused-tail")("x","")]])
 ,("link-emph-prefix",[Para[Link nullAttr[Emph(error"inline-head":Str"second":error"unused-tail")]("x","")]])
 ,("link-note-prefix",[Para[Link nullAttr[Note(error"block-head":error"block-tail")]("x","")]])
 ,("link-comma-prefix",[Para[Link nullAttr(Str"a,b":error"second-head":error"unused-tail")("x","")]])
 ,("link-comma-finite",[Para[Link nullAttr[Emph[Str"a,b"],Str"c,d"]("x","")]])]

main=mapM_ run cases
 where run(name,bs)=do
        r<-try(evaluate(case runPure(writeAsciiDoc def(Pandoc nullMeta bs))of Left e->error(show e);Right t->T.length t))::IO(Either SomeException Int)
        B.putStrLn$encode$case r of Left e->object["name".=name,"thrown".=takeWhile(/='\n')(displayException e)];Right n->object["name".=name,"length".=n]
