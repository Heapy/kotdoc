{-# LANGUAGE OverloadedStrings #-}
-- Original AsciiDoc templated metadata mapM callbacks, GPL-2.0-or-later.
import SourceAsciiDocWriter
import Text.Pandoc.Definition
import Text.Pandoc.Class
import Text.Pandoc.Options
import Text.DocTemplates(compileTemplate)
import qualified Data.Map as M
import Data.Default
import Control.Exception
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
badInline=MetaInlines(error"inline-head":Str"second":error"inline-tail")
cases :: [(T.Text,Meta,[Block])]
cases=[("meta/root",Meta$M.singleton"x"$MetaList(error"meta-head":MetaBool False:error"meta-tail"),[])
 ,("meta/leaf-action",Meta$M.singleton"x"$MetaList(badInline:MetaBool False:error"meta-tail"),[])
 ,("meta/nested-list",Meta$M.singleton"x"$MetaList(MetaList(error"meta-head":MetaBool False:error"inner-tail"):error"meta-tail"),[])
 ,("meta/nested-map",Meta$M.singleton"x"$MetaList(MetaMap(M.singleton"a"badInline):error"meta-tail"),[])
 ,("meta/map-order",Meta$M.fromList[("z",MetaInlines[error"map-z"]),("a",MetaInlines[error"map-a"])],[])
 ,("meta/before-body",Meta$M.singleton"x"badInline,error"body-head":error"body-tail")]
main=do
 Right tpl<-compileTemplate"oracle.asciidoc""$body$"
 mapM_(\(name,meta,blocks)->do
  r<-try(evaluate(case runPure(writeAsciiDoc def{writerTemplate=Just tpl}(Pandoc meta blocks))of Left e->error(show e);Right t->T.length t))::IO(Either SomeException Int)
  B.putStrLn$encode$case r of Left e->object["name".=name,"thrown".=takeWhile(/='\n')(displayException e)];Right n->object["name".=name,"length".=n])cases
