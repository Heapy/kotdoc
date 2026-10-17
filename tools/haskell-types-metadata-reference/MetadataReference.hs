{-# LANGUAGE OverloadedStrings, ExistentialQuantification, ScopedTypeVariables #-}
-- Calls the actual pinned Builder/Definition metadata dictionaries.
import Text.Pandoc.Builder
import Data.Aeson
import Data.Aeson.Types (Parser,parseEither)
import qualified Data.Aeson.KeyMap as K
import qualified Data.Text as T
import qualified Data.Map as M
import qualified Data.ByteString.Lazy.Char8 as B
import Data.Char(chr)
import Data.Semigroup(stimes,sconcat)
import Data.List.NonEmpty(NonEmpty(..))
import Control.Exception
import System.IO
field n(Object o)=maybe(error "field")id(K.lookup n o)
as v=case fromJSON v of Success x->x;Error e->error e
readValue :: Read a=>Value->Parser a
readValue v=case reads(as v)of [(x,"")]->pure x;_->fail "native input"
data Decoder=forall a.ToMetaValue a=>Decoder(Value->Parser a)
decoder spec=case as(field "type" spec)::String of
 "meta"->Decoder(readValue::Value->Parser MetaValue)
 "inlines"->Decoder(fmap fromList.(readValue::Value->Parser [Inline]))
 "blocks"->Decoder(fmap fromList.(readValue::Value->Parser [Block]))
 "bool"->Decoder(parseJSON::Value->Parser Bool)
 "text"->Decoder(fmap(T.pack.map chr).(parseJSON::Value->Parser [Int]))
 "string"->Decoder(fmap(map chr).(parseJSON::Value->Parser [Int]))
 "list"->case decoder(field "element" spec)of Decoder f->Decoder(\v->(parseJSON v::Parser [Value]) >>= traverse f)
 "mapText"->case decoder(field "element" spec)of Decoder f->Decoder(\v->do pairs<-parseJSON v::Parser [(Value,Value)];M.fromList <$> traverse(\(k,x)->do key<-T.pack.map chr <$> (parseJSON k::Parser [Int]);value<-f x;pure(key,value))pairs)
 "mapString"->case decoder(field "element" spec)of Decoder f->Decoder(\v->do pairs<-parseJSON v::Parser [(Value,Value)];M.fromList <$> traverse(\(k,x)->do key<-map chr <$> (parseJSON k::Parser [Int]);value<-f x;pure(key,value))pairs)
 _->error "type"
convert r=case decoder(field "spec" r)of Decoder f->case parseEither f(field "value" r)of Right a->toMetaValue a;Left e->error e
native :: Read a=>Value->a
native=read.as
observe r=let key=T.pack(map chr(as(field "key" r)::[Int]));meta=native(field "meta" r)::Meta;docu=native(field "document" r)::Pandoc in case as(field "operation" r)::String of
 "convert"->show(convert r)
 "setMetaMeta"->show(setMeta key(convert r)meta)
 "setMetaPandoc"->show(setMeta key(convert r)docu)
 "deleteMetaMeta"->show(deleteMeta key meta)
 "deleteMetaPandoc"->show(deleteMeta key docu)
 "nullMeta"->show nullMeta
 "isNullMeta"->show(isNullMeta meta)
 "lookupMeta"->show(lookupMeta key meta)
 "docTitle"->show(docTitle meta)
 "docAuthors"->show(docAuthors meta)
 "docDate"->show(docDate meta)
 "doc"->show(doc(fromList(native(field "blocks" r)::[Block])))
 "setTitle"->show(setTitle(fromList(native(field "inlines" r)::[Inline]))docu)
 "setAuthors"->show(setAuthors(map fromList(native(field "authors" r)::[[Inline]]))docu)
 "setDate"->show(setDate(fromList(native(field "inlines" r)::[Inline]))docu)
 "metaMappend"->show(mappend meta(native(field "otherMeta" r)::Meta))
 "pandocMappend"->show(mappend docu(native(field "otherDocument" r)::Pandoc))
 "metaAppend"->show(meta <> (native(field "otherMeta" r)::Meta))
 "pandocAppend"->show(docu <> (native(field "otherDocument" r)::Pandoc))
 "metaEmpty"->show(mempty::Meta)
 "pandocEmpty"->show(mempty::Pandoc)
 "metaStimes"->show(stimes(read(as(field "count" r)::String)::Integer)meta)
 "pandocStimes"->show(stimes(read(as(field "count" r)::String)::Integer)docu)
 "metaSconcat"->show(sconcat(meta:|native(field "metas" r)))
 "pandocSconcat"->show(sconcat(docu:|native(field "documents" r)))
 "metaConcat"->show(mconcat(native(field "metas" r)::[Meta]))
 "pandocConcat"->show(mconcat(native(field "documents" r)::[Pandoc]))
 _->error "operation"
respond line=do
 let r=either error id(eitherDecode line)
 result<-try(evaluate(let bytes=encode(observe r)in B.length bytes `seq` bytes))::IO(Either SomeException B.ByteString)
 B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result)
main=do hSetBuffering stdout LineBuffering;input<-B.getContents;mapM_ respond(B.lines input)
