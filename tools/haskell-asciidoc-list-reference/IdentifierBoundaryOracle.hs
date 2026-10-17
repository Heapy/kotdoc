{-# LANGUAGE OverloadedStrings #-}
-- Shared.inlineListToIdentifier unEmoji pattern through original AsciiDoc writer.
-- Pandoc authors, GPL-2.0-or-later; Walk fields BSD-3-Clause.
import SourceAsciiDocWriter
import Text.Pandoc.Definition
import Text.Pandoc.Class
import Text.Pandoc.Options
import Text.Pandoc.Extensions
import Data.Default
import Control.Exception
import Data.Aeson
import qualified Data.Text as T
import qualified Data.ByteString.Lazy.Char8 as B
variants=[("alias","alias",[Span("",["emoji"],[("data-emoji","alias")])[Str"x"]]),("two-cons","x",[Span("",["emoji"],("data-emoji","alias"):error"unused-kv-head":error"unused-kv-tail")[Str"x"]]),("wrong-key","x",[Span("",["emoji"],("other","alias"):error"unused-kv-tail")[Str"x"]]),("unicode-alias","section",[Span("",["emoji"],[("data-emoji","❤")])[Str"x"]]),("plain-emoji","heart",[Str"❤"])]
main=mapM_(\(flag,ext)->mapM_(\(name,ident,ils)->do
 let opts=def{writerExtensions=enableExtension Ext_auto_identifiers(enableExtension ext mempty)}
 r<-try(do let output=case runPure(writeAsciiDoc opts(Pandoc nullMeta[Header 1(ident,[],[])ils]))of Left e->error(show e);Right t->t
           evaluate(T.length output);return output)::IO(Either SomeException T.Text)
 B.putStrLn$encode$case r of Left e->object["name".=(("identifier/"<>flag<>"/"<>name)::T.Text),"thrown".=takeWhile(/='\n')(displayException e)];Right t->object["name".=("identifier/"<>flag<>"/"<>name),"output".=t])variants)[("gfm",Ext_gfm_auto_identifiers),("ascii",Ext_ascii_identifiers)]
