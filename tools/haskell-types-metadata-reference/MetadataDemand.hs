{-# LANGUAGE OverloadedStrings #-}
-- Actual Builder/Definition functions, controlled source partial values.
import Text.Pandoc.Builder
import qualified Data.Map as M
import qualified Data.Text as T
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import Data.Semigroup(stimes,sconcat)
import Data.List.NonEmpty(NonEmpty(..))
import Control.Exception
import System.IO
bad n=error n
root x=x `seq` "root"
metaKey k (Meta m)=show(M.lookup k m)
listHead xs=show(take 1 xs)
data Ignored=Ignored Bool
instance ToMetaValue Ignored where toMetaValue _=MetaBool True
nullMetaDoc=Pandoc nullMeta []
probes :: [(String,String)]
probes=[
 ("convert-bool-root",root(toMetaValue(bad "bool"::Bool))),
 ("convert-text-root",root(toMetaValue(bad "text"::T.Text))),
 ("convert-string-root",root(toMetaValue(bad "string"::String))),
 ("convert-string-head",root(toMetaValue([bad "char"]::String))),
 ("convert-list-root",root(toMetaValue(bad "list"::[Bool]))),
 ("convert-list-head",root(toMetaValue([bad "head"]::[Bool]))),
 ("convert-list-tail",root(toMetaValue(True:bad "tail"::[Bool]))),
 ("convert-list-demand-head",case toMetaValue([bad "head"]::[Bool])of MetaList xs->show(head xs);_->"wrong"),
 ("convert-map-value",root(toMetaValue(M.singleton "x"(bad "value"::Bool)::M.Map T.Text Bool))),
 ("convert-map-keys",case toMetaValue(M.singleton "x"(bad "value"::Bool)::M.Map T.Text Bool)of MetaMap m->show(M.keys m);_->"wrong"),
 ("convert-map-demand-value",show(toMetaValue(M.singleton "x"(bad "value"::Bool)::M.Map T.Text Bool))),
 ("convert-inlines-head",root(toMetaValue(singleton(bad "inline"::Inline)))),
 ("convert-blocks-head",root(toMetaValue(singleton(bad "block"::Block)))),
 ("convert-inlines-root",root(toMetaValue(bad "many"::Inlines))),
 ("convert-blocks-root",root(toMetaValue(bad "many"::Blocks))),
 ("meta-set-unused",metaKey "y"(setMeta "x"(bad "value"::Bool)nullMeta)),
 ("meta-set-null",show(isNullMeta(setMeta "x"(bad "value"::Bool)nullMeta))),
 ("meta-set-overwrite",metaKey "x"(setMeta "x" True(Meta(M.singleton "x"(bad "old"))))),
 ("meta-delete-poison",show(deleteMeta "x"(Meta(M.singleton "x"(bad "old"))))),
 ("meta-delete-key-empty",show(deleteMeta(bad "key")nullMeta)),
 ("meta-lookup-key-empty",show(lookupMeta(bad "key")nullMeta)),
 ("meta-insert-key-empty",root(setMeta(bad "key")True nullMeta)),
 ("meta-lookup-found-lazy",root(lookupMeta "x"(Meta(M.singleton "x"(bad "value"))))),
 ("doc-title-list-tail",listHead(docTitle(Meta(M.singleton "title"(MetaInlines(Str "a":bad "tail")))))),
 ("doc-title-block-head-vs-tail",root(docTitle(Meta(M.singleton "title"(MetaBlocks(bad "head":bad "tail")))))),
 ("doc-date-block-head-vs-tail",root(docDate(Meta(M.singleton "date"(MetaBlocks(bad "head":bad "tail")))))),
 ("doc-title-single-payload",listHead(docTitle(Meta(M.singleton "title"(MetaBlocks[Plain(Str "a":bad "tail")]))))),
 ("doc-authors-first-group",show(take 1(docAuthors(Meta(M.singleton "author"(MetaList[MetaString "s",MetaInlines[Str "i"],bad "tail-value"])))))),
 ("doc-authors-early-string",show(take 1(docAuthors(Meta(M.singleton "author"(MetaList[MetaString "s",bad "later"])))))),
 ("doc-author-block-head-vs-tail",root(docAuthors(Meta(M.singleton "author"(MetaList[MetaBlocks(bad "head":bad "tail")]))))),
 ("pandoc-set-unused",case setMeta "x"(bad "value"::Bool)(Pandoc nullMeta[Plain[]])of Pandoc _ bs->show bs),
 ("pandoc-delete-preserve-head",case deleteMeta "x"(Pandoc nullMeta[bad "block"])of Pandoc _ bs->root bs),
 ("doc-ignore-block-head",root(doc(singleton(bad "block"::Block)))),
 ("doc-force-block-root",root(doc(bad "many"::Blocks))),
 ("set-title-unused",case setTitle(bad "title")(Pandoc nullMeta[])of Pandoc m _->show(lookupMeta "other" m)),
 ("set-authors-unused",case setAuthors(bad "authors")(Pandoc nullMeta[])of Pandoc m _->show(lookupMeta "other" m)),
 ("set-date-unused",case setDate(bad "date")(Pandoc nullMeta[])of Pandoc m _->show(lookupMeta "other" m)),
 ("meta-append-winning-value",metaKey "x"(Meta(M.singleton "x"(bad "old"))<>Meta(M.singleton "x"(MetaBool True)))),
 ("meta-append-order",root((bad "left"::Meta)<>(bad "right"::Meta))),
 ("pandoc-append-order",root((bad "left"::Pandoc)<>(bad "right"::Pandoc))),
 ("meta-concat-order",root(mconcat[bad "left",bad "right"]::Meta)),
 ("pandoc-concat-order",root(mconcat[bad "left",bad "right"]::Pandoc)),
 ("meta-sconcat-tail-first",root(sconcat((bad "head"::Meta):|bad "tail"))),
 ("pandoc-sconcat-tail-first",root(sconcat((bad "head"::Pandoc):|bad "tail"))),
 ("pandoc-sconcat-head-before-later-tail",root(sconcat((bad "head"::Pandoc):|(nullMetaDoc:bad "tail")))),
 ("meta-stimes-zero",root(stimes(0::Integer)(bad "value"::Meta))),
 ("pandoc-stimes-negative",root(stimes(-1::Integer)(bad "value"::Pandoc))),
 ("meta-stimes-one",root(stimes(1::Integer)(bad "value"::Meta))),
 ("pandoc-stimes-two-lazy-head",case stimes(2::Integer)(Pandoc nullMeta[bad "head"])of Pandoc _ bs->root bs),
 ("custom-list-ignore-value",show(toMetaValue[bad "ignored"::Ignored])),
 ("custom-hasmeta-ignore-value",metaKey "x"(setMeta "x"(bad "ignored"::Ignored)nullMeta)),
 ("string-map-discarded-value",show(toMetaValue(M.fromList[("\xD800",bad "lost"),("\xFFFD",True)]::M.Map String Bool))),
 ("meta-stimes-count-vs-value",root(stimes(bad "count"::Integer)(bad "value"::Meta))),
 ("pandoc-stimes-count",root(stimes(bad "count"::Integer)nullMetaDoc)),
 ("meta-append-unused-value",show(isNullMeta(Meta(M.singleton "x"(bad "value"))<>nullMeta)))
 ]
main=do
 hSetBuffering stdout LineBuffering
 mapM_ respond probes
 where
  respond(name,value)=do
   result<-try(evaluate(let bytes=encode value in B.length bytes `seq` bytes))::IO(Either SomeException B.ByteString)
   B.putStrLn(encode name)
   B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result)
