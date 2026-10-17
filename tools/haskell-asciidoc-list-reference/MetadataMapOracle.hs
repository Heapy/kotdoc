{-# LANGUAGE OverloadedStrings #-}
-- Original AsciiDoc templated metadata mapM callbacks, GPL-2.0-or-later.
import SourceAsciiDocWriter
import Text.Pandoc.Definition
import Text.Pandoc.Class
import Text.Pandoc.Options
import Text.DocTemplates(compileTemplate)
import qualified Data.Map as M
import qualified Data.Map.Lazy as ML
import Data.Default
import Control.Exception
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
badInline=MetaInlines(error"inline-head":Str"second":error"inline-tail")
cases :: [(T.Text,Meta,[Block])]
cases=[("meta/map-value-order",Meta$ML.fromList[("z",error"map-z"),("a",error"map-a")],[])]

main=do
 Right tpl<-compileTemplate"oracle.asciidoc""$body$"
 mapM_(\(name,meta,blocks)->do
  r<-try(evaluate(case runPure(writeAsciiDoc def{writerTemplate=Just tpl}(Pandoc meta blocks))of Left e->error(show e);Right t->T.length t))::IO(Either SomeException Int)
  B.putStrLn$encode$case r of Left e->object["name".=name,"thrown".=takeWhile(/='\n')(displayException e)];Right n->object["name".=name,"length".=n])cases
