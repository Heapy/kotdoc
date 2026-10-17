{-# LANGUAGE OverloadedStrings #-}
-- Exact AsciiDoc caller / Definition metadata projections / imageDimensions.
-- Pandoc authors GPL-2.0-or-later; pandoc-types metadata BSD-3-Clause.
import SourceAsciiDocWriter
import Text.Pandoc.Definition
import Text.Pandoc.Class
import Text.Pandoc.Options
import Text.DocTemplates(compileTemplate)
import Data.Default
import Control.Exception
import Data.Aeson
import qualified Data.Map as M
import qualified Data.Text as T
import qualified Data.ByteString.Lazy.Char8 as B
textCases=[("inlines-empty",MetaInlines[]),("inlines",MetaInlines[Str"x"]),("plain-empty",MetaBlocks[Plain[]]),("para",MetaBlocks[Para[Str"x"]]),("wrong-block",MetaBlocks[BlockQuote[]]),("two-blocks",MetaBlocks[Plain[],Plain[]]),("string-empty",MetaString"")]
authorCases=[("inlines-empty",MetaInlines[]),("list-empty",MetaList[]),("ignored",MetaList[MetaBool False]),("list-inlines",MetaList[MetaInlines[]]),("list-plain",MetaList[MetaBlocks[Plain[]]]),("list-para",MetaList[MetaBlocks[Para[]]]),("list-string",MetaList[MetaString""])]
cases=[("metadata/absent",True,nullMeta,[])]++[("metadata/"<>key<>"/"<>name,True,Meta(M.singleton key value),[])|(key,values)<-[("title",textCases),("date",take 2 textCases),("author",authorCases)],(name,value)<-values]++[("dimensions/"<>name,False,nullMeta,[Para[Image("",[],kvs)[Str"a"]("x.png","")]])|(name,kvs)<-[("both",[("width","20px"),("height","30px")]),("empty",[]),("junk",[("other","ignored"),("width","25%")])]]
main=do
 Right tpl<-compileTemplate"oracle.asciidoc""$if(titleblock)$yes$else$no$endif$"
 mapM_(\(name,standalone,meta,blocks)->do
  r<-try(do let output=case runPure(writeAsciiDoc def{writerTemplate=if standalone then Just tpl else Nothing}(Pandoc meta blocks))of Left e->error(show e);Right t->t
            evaluate(T.length output);return output)::IO(Either SomeException T.Text)
  B.putStrLn$encode$case r of Left e->object["name".=(name::T.Text),"thrown".=takeWhile(/='\n')(displayException e)];Right t->object["name".=name,"output".=t])cases
