{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, FlexibleContexts, MultiParamTypeClasses, RankNTypes, FlexibleInstances, TypeSynonymInstances #-}
-- Actual unchanged Text.Pandoc.Walk dictionaries, ©John MacFarlane2013–2023, BSD3.
import qualified Text.Pandoc.Walk as W
import Text.Pandoc.Definition
import qualified Text.Pandoc.Builder as Builder
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
import qualified Data.Map as M
import qualified Data.Sequence as S
import Data.Monoid (First(..),Last(..),Sum(..),Any(..),All(..))
import Data.Functor.Identity (Identity(..))
import Control.Monad.State.Strict
import Control.Monad.Except
import Control.Exception (SomeException,try,evaluate,displayException)
import System.IO (stdout,hSetBuffering,BufferMode(LineBuffering))
newtype Probe=Probe String deriving(Read,Show)
fProbe n(Probe s)=Probe(s++T.unpack(marker n))
instance {-# OVERLAPPING #-} W.Walkable Probe ([Inline]) where
 walk f xs=let Probe t=f(Probe "pure-inlines")in xs++[Str(T.pack t)]
 walkM f x=do Probe t<-f(Probe "inlines");pure((\xs t->xs++[Str(T.pack t)])x t)
 query f _=f(Probe "query-inlines")
instance {-# OVERLAPPING #-} W.Walkable Probe ([Block]) where
 walk f xs=let Probe t=f(Probe "pure-blocks")in xs++[Plain[Str(T.pack t)]]
 walkM f x=do Probe t<-f(Probe "blocks");pure((\xs t->xs++[Plain[Str(T.pack t)]])x t)
 query f _=f(Probe "query-blocks")
instance {-# OVERLAPPING #-} W.Walkable Probe (Citation) where
 walkM f x=do Probe t<-f(Probe "citation");pure((\x t->x{citationId=T.pack t})x t)
 query f _=f(Probe "query-citation")
instance {-# OVERLAPPING #-} W.Walkable Probe (Row) where
 walkM f x=do Probe t<-f(Probe "row");pure((\(Row _ xs)t->Row(T.pack t,[],[])xs)x t)
 query f _=f(Probe "query-row")
instance {-# OVERLAPPING #-} W.Walkable Probe (TableHead) where
 walkM f x=do Probe t<-f(Probe "head");pure((\(TableHead _ xs)t->TableHead(T.pack t,[],[])xs)x t)
 query f _=f(Probe "query-head")
instance {-# OVERLAPPING #-} W.Walkable Probe (TableBody) where
 walkM f x=do Probe t<-f(Probe "body");pure((\(TableBody _ n xs ys)t->TableBody(T.pack t,[],[])n xs ys)x t)
 query f _=f(Probe "query-body")
instance {-# OVERLAPPING #-} W.Walkable Probe (TableFoot) where
 walkM f x=do Probe t<-f(Probe "foot");pure((\(TableFoot _ xs)t->TableFoot(T.pack t,[],[])xs)x t)
 query f _=f(Probe "query-foot")
instance {-# OVERLAPPING #-} W.Walkable Probe (Cell) where
 walkM f x=do Probe t<-f(Probe "cell");pure((\(Cell _ a r c bs)t->Cell(T.pack t,[],[])a r c bs)x t)
 query f _=f(Probe "query-cell")
instance {-# OVERLAPPING #-} W.Walkable Probe (Caption) where
 walkM f x=do Probe t<-f(Probe "caption");pure((\(Caption sc bs)t->Caption sc(bs++[Plain[Str(T.pack t)]]))x t)
 query f _=f(Probe "query-caption")
instance {-# OVERLAPPING #-} W.Walkable Probe (Meta) where
 walkM f x=do Probe t<-f(Probe "meta");pure((\(Meta m)t->Meta(M.insert(T.pack t)(MetaString(T.pack t))m))x t)
 query f _=f(Probe "query-meta")
instance {-# OVERLAPPING #-} W.Walkable Probe (MetaValue) where
 walkM f x=do Probe t<-f(Probe "metaValue");pure((\_ t->MetaString(T.pack t))x t)
 query f _=f(Probe "query-metaValue")
instance {-# OVERLAPPING #-} W.Walkable Probe (Maybe [Inline]) where
 walkM f x=do Probe t<-f(Probe "shortCaption");pure((\x t->Just(maybe[]id x++[Str(T.pack t)]))x t)
 query f _=f(Probe "query-shortCaption")
instance W.Walkable Meta Block where
 walkM f _=do Meta m<-f(Meta(M.singleton "external"(MetaString "block")));pure(Plain[Str(T.intercalate ","(M.keys m))])
 query f _=f(Meta(M.singleton "external"(MetaString "block")))
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success x->x;Error e->error e
field n(Object o)=maybe(error "field")id(K.lookup n o)
marker n=T.pack(if n<0 then "pure" else "visit"++show n)
fInline n x=case x of Str s->Str(s<>marker n);Emph xs->Strong(xs++[Str(marker n)]);_->x
fInlines n xs=xs++[Str(marker n)]
fBlock n x=case x of Plain xs->Para(xs++[Str(marker n)]);BlockQuote bs->Div nullAttr(bs++[Plain[Str(marker n)]]);_->x
fBlocks n xs=xs++[Plain[Str(marker n)]]
fMeta n(Meta m)=Meta(M.insert(marker n)(MetaString(marker n))m)
fMetaValue n x=case x of MetaString s->MetaString(s<>marker n);MetaBool b->MetaBool(not b);MetaList xs->MetaList(xs++[MetaString(marker n)]);_->x
fPandoc n(Pandoc m bs)=Pandoc m(bs++[RawBlock(Format "walk")(marker n)])
observe :: forall a b.(Read b,Show b,Show a,W.Walkable a b)=>(Int->a->a)->b->Value->Value
observe change _ r=observeWith (W.walk::(a->a)->b->b) (W.walkM::(a->State [String] a)->b->State [String] b)
 (W.walkM::(a->ExceptT String(State [String]) a)->b->ExceptT String(State [String]) b)
 (W.walkM::(a->[a])->b->[b]) (W.query::(a->[String])->b->[String]) (W.query::(a->First String)->b->First String)
 (W.query::(a->Last String)->b->Last String) (W.query::(a->Sum Int)->b->Sum Int) change r
observeWith :: forall a b.(Read b,Show b,Show a)=>
 ((a->a)->b->b)->((a->State [String] a)->b->State [String] b)->
 ((a->ExceptT String(State [String]) a)->b->ExceptT String(State [String]) b)->
 ((a->[a])->b->[b])->((a->[String])->b->[String])->((a->First String)->b->First String)->
 ((a->Last String)->b->Last String)->((a->Sum Int)->b->Sum Int)->(Int->a->a)->Value->Value
observeWith pureWalk stateWalk failWalk branchWalk traceQuery firstQuery lastQuery countQuery change r=case as(field "method" r)::String of
 "walk"->toJSON(show(pureWalk(change(-1))input))
 "walkM"->let (v,trace)=runState(stateWalk stateStep input)[] in object["value" .= show v,"trace" .= trace]
 "failure"->let (v,trace)=runState(runExceptT(failWalk failStep input))[] in object["result" .= either id show v,"failed" .= either(const True)(const False)v,"trace" .= trace]
 "branching"->let xs=take(limit+1)(branchWalk(\x->[change 0 x,change 1 x])input) in object["values" .= map show(take limit xs),"truncated" .= (length xs>limit)]
 "query"->toJSON(traceQuery(\x->[show x])input)
 "first"->toJSON(getFirst(firstQuery(First . Just . show)input))
 "last"->toJSON(getLast(lastQuery(Last . Just . show)input))
 "count"->toJSON(getSum(countQuery(const(Sum 1))input))
 _->error "method"
 where
 input=read(as(field "input" r))::b
 limit=4
 stateStep x=do old<-get;put(old++[show x]);pure(change(length old)x)
 failStep x=do old<-get;put(old++[show x]);if length old==as(field "stop" r)then throwError("stop"++show(length old))else pure(change(length old)x)

observeHelper :: forall a b.(Read b,Show b,Show a)=>
 (forall m.Monad m=>(a->m a)->b->m b)->
 (forall c.Monoid c=>(a->c)->b->c)->(Int->a->a)->b->Value->Value
observeHelper h q change _=observeWith (\f->runIdentity . h(Identity . f)) h h h q q q q change

dictionary r=case (as(field "target" r)::String,as(field "root" r)::String)of
 ("inline","inline")->observe (fInline::Int->(Inline)->(Inline)) (undefined::Inline) r
 ("inlines","inlines")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::[Inline]) r
 ("inlines","inline")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::Inline) r
 ("inline","block")->observe (fInline::Int->(Inline)->(Inline)) (undefined::Block) r
 ("inlines","block")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::Block) r
 ("block","block")->observe (fBlock::Int->(Block)->(Block)) (undefined::Block) r
 ("blocks","block")->observe (fBlocks::Int->([Block])->([Block])) (undefined::Block) r
 ("blocks","blocks")->observe (fBlocks::Int->([Block])->([Block])) (undefined::[Block]) r
 ("block","inline")->observe (fBlock::Int->(Block)->(Block)) (undefined::Inline) r
 ("blocks","inline")->observe (fBlocks::Int->([Block])->([Block])) (undefined::Inline) r
 ("block","pandoc")->observe (fBlock::Int->(Block)->(Block)) (undefined::Pandoc) r
 ("blocks","pandoc")->observe (fBlocks::Int->([Block])->([Block])) (undefined::Pandoc) r
 ("inline","pandoc")->observe (fInline::Int->(Inline)->(Inline)) (undefined::Pandoc) r
 ("inlines","pandoc")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::Pandoc) r
 ("meta","pandoc")->observe (fMeta::Int->(Meta)->(Meta)) (undefined::Pandoc) r
 ("metaValue","pandoc")->observe (fMetaValue::Int->(MetaValue)->(MetaValue)) (undefined::Pandoc) r
 ("pandoc","pandoc")->observe (fPandoc::Int->(Pandoc)->(Pandoc)) (undefined::Pandoc) r
 ("meta","meta")->observe (fMeta::Int->(Meta)->(Meta)) (undefined::Meta) r
 ("inline","meta")->observe (fInline::Int->(Inline)->(Inline)) (undefined::Meta) r
 ("inlines","meta")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::Meta) r
 ("block","meta")->observe (fBlock::Int->(Block)->(Block)) (undefined::Meta) r
 ("blocks","meta")->observe (fBlocks::Int->([Block])->([Block])) (undefined::Meta) r
 ("metaValue","meta")->observe (fMetaValue::Int->(MetaValue)->(MetaValue)) (undefined::Meta) r
 ("metaValue","metaValue")->observe (fMetaValue::Int->(MetaValue)->(MetaValue)) (undefined::MetaValue) r
 ("inline","metaValue")->observe (fInline::Int->(Inline)->(Inline)) (undefined::MetaValue) r
 ("inlines","metaValue")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::MetaValue) r
 ("block","metaValue")->observe (fBlock::Int->(Block)->(Block)) (undefined::MetaValue) r
 ("blocks","metaValue")->observe (fBlocks::Int->([Block])->([Block])) (undefined::MetaValue) r
 ("inline","row")->observe (fInline::Int->(Inline)->(Inline)) (undefined::Row) r
 ("inlines","row")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::Row) r
 ("block","row")->observe (fBlock::Int->(Block)->(Block)) (undefined::Row) r
 ("blocks","row")->observe (fBlocks::Int->([Block])->([Block])) (undefined::Row) r
 ("inline","head")->observe (fInline::Int->(Inline)->(Inline)) (undefined::TableHead) r
 ("inlines","head")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::TableHead) r
 ("block","head")->observe (fBlock::Int->(Block)->(Block)) (undefined::TableHead) r
 ("blocks","head")->observe (fBlocks::Int->([Block])->([Block])) (undefined::TableHead) r
 ("inline","body")->observe (fInline::Int->(Inline)->(Inline)) (undefined::TableBody) r
 ("inlines","body")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::TableBody) r
 ("block","body")->observe (fBlock::Int->(Block)->(Block)) (undefined::TableBody) r
 ("blocks","body")->observe (fBlocks::Int->([Block])->([Block])) (undefined::TableBody) r
 ("inline","foot")->observe (fInline::Int->(Inline)->(Inline)) (undefined::TableFoot) r
 ("inlines","foot")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::TableFoot) r
 ("block","foot")->observe (fBlock::Int->(Block)->(Block)) (undefined::TableFoot) r
 ("blocks","foot")->observe (fBlocks::Int->([Block])->([Block])) (undefined::TableFoot) r
 ("inline","caption")->observe (fInline::Int->(Inline)->(Inline)) (undefined::Caption) r
 ("inlines","caption")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::Caption) r
 ("block","caption")->observe (fBlock::Int->(Block)->(Block)) (undefined::Caption) r
 ("blocks","caption")->observe (fBlocks::Int->([Block])->([Block])) (undefined::Caption) r
 ("inline","cell")->observe (fInline::Int->(Inline)->(Inline)) (undefined::Cell) r
 ("inlines","cell")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::Cell) r
 ("block","cell")->observe (fBlock::Int->(Block)->(Block)) (undefined::Cell) r
 ("blocks","cell")->observe (fBlocks::Int->([Block])->([Block])) (undefined::Cell) r
 ("inline","citation")->observe (fInline::Int->(Inline)->(Inline)) (undefined::Citation) r
 ("inlines","citation")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::Citation) r
 ("block","citation")->observe (fBlock::Int->(Block)->(Block)) (undefined::Citation) r
 ("blocks","citation")->observe (fBlocks::Int->([Block])->([Block])) (undefined::Citation) r
 _->error "dictionary"
helper r=case (as(field "target" r)::String,as(field "root" r)::String)of
 ("meta","pandoc")->observeHelper W.walkPandocM W.queryPandoc fMeta (undefined::Pandoc) r
 ("probe","inline")->observeHelper W.walkInlineM W.queryInline fProbe (undefined::Inline) r
 ("probe","block")->observeHelper W.walkBlockM W.queryBlock fProbe (undefined::Block) r
 ("probe","metaValue")->observeHelper W.walkMetaValueM W.queryMetaValue fProbe (undefined::MetaValue) r
 ("probe","citation")->observeHelper W.walkCitationM W.queryCitation fProbe (undefined::Citation) r
 ("probe","row")->observeHelper W.walkRowM W.queryRow fProbe (undefined::Row) r
 ("probe","head")->observeHelper W.walkTableHeadM W.queryTableHead fProbe (undefined::TableHead) r
 ("probe","body")->observeHelper W.walkTableBodyM W.queryTableBody fProbe (undefined::TableBody) r
 ("probe","foot")->observeHelper W.walkTableFootM W.queryTableFoot fProbe (undefined::TableFoot) r
 ("probe","caption")->observeHelper W.walkCaptionM W.queryCaption fProbe (undefined::Caption) r
 ("probe","cell")->observeHelper W.walkCellM W.queryCell fProbe (undefined::Cell) r
 ("probe","pandoc")->observeHelper W.walkPandocM W.queryPandoc fProbe (undefined::Pandoc) r
 ("inline","inline")->observeHelper W.walkInlineM W.queryInline (fInline::Int->(Inline)->(Inline)) (undefined::Inline) r
 ("inlines","inline")->observeHelper W.walkInlineM W.queryInline (fInlines::Int->([Inline])->([Inline])) (undefined::Inline) r
 ("block","inline")->observeHelper W.walkInlineM W.queryInline (fBlock::Int->(Block)->(Block)) (undefined::Inline) r
 ("blocks","inline")->observeHelper W.walkInlineM W.queryInline (fBlocks::Int->([Block])->([Block])) (undefined::Inline) r
 ("inline","block")->observeHelper W.walkBlockM W.queryBlock (fInline::Int->(Inline)->(Inline)) (undefined::Block) r
 ("inlines","block")->observeHelper W.walkBlockM W.queryBlock (fInlines::Int->([Inline])->([Inline])) (undefined::Block) r
 ("block","block")->observeHelper W.walkBlockM W.queryBlock (fBlock::Int->(Block)->(Block)) (undefined::Block) r
 ("blocks","block")->observeHelper W.walkBlockM W.queryBlock (fBlocks::Int->([Block])->([Block])) (undefined::Block) r
 ("inline","metaValue")->observeHelper W.walkMetaValueM W.queryMetaValue (fInline::Int->(Inline)->(Inline)) (undefined::MetaValue) r
 ("inlines","metaValue")->observeHelper W.walkMetaValueM W.queryMetaValue (fInlines::Int->([Inline])->([Inline])) (undefined::MetaValue) r
 ("block","metaValue")->observeHelper W.walkMetaValueM W.queryMetaValue (fBlock::Int->(Block)->(Block)) (undefined::MetaValue) r
 ("blocks","metaValue")->observeHelper W.walkMetaValueM W.queryMetaValue (fBlocks::Int->([Block])->([Block])) (undefined::MetaValue) r
 ("inline","citation")->observeHelper W.walkCitationM W.queryCitation (fInline::Int->(Inline)->(Inline)) (undefined::Citation) r
 ("inlines","citation")->observeHelper W.walkCitationM W.queryCitation (fInlines::Int->([Inline])->([Inline])) (undefined::Citation) r
 ("block","citation")->observeHelper W.walkCitationM W.queryCitation (fBlock::Int->(Block)->(Block)) (undefined::Citation) r
 ("blocks","citation")->observeHelper W.walkCitationM W.queryCitation (fBlocks::Int->([Block])->([Block])) (undefined::Citation) r
 ("inline","row")->observeHelper W.walkRowM W.queryRow (fInline::Int->(Inline)->(Inline)) (undefined::Row) r
 ("inlines","row")->observeHelper W.walkRowM W.queryRow (fInlines::Int->([Inline])->([Inline])) (undefined::Row) r
 ("block","row")->observeHelper W.walkRowM W.queryRow (fBlock::Int->(Block)->(Block)) (undefined::Row) r
 ("blocks","row")->observeHelper W.walkRowM W.queryRow (fBlocks::Int->([Block])->([Block])) (undefined::Row) r
 ("inline","head")->observeHelper W.walkTableHeadM W.queryTableHead (fInline::Int->(Inline)->(Inline)) (undefined::TableHead) r
 ("inlines","head")->observeHelper W.walkTableHeadM W.queryTableHead (fInlines::Int->([Inline])->([Inline])) (undefined::TableHead) r
 ("block","head")->observeHelper W.walkTableHeadM W.queryTableHead (fBlock::Int->(Block)->(Block)) (undefined::TableHead) r
 ("blocks","head")->observeHelper W.walkTableHeadM W.queryTableHead (fBlocks::Int->([Block])->([Block])) (undefined::TableHead) r
 ("inline","body")->observeHelper W.walkTableBodyM W.queryTableBody (fInline::Int->(Inline)->(Inline)) (undefined::TableBody) r
 ("inlines","body")->observeHelper W.walkTableBodyM W.queryTableBody (fInlines::Int->([Inline])->([Inline])) (undefined::TableBody) r
 ("block","body")->observeHelper W.walkTableBodyM W.queryTableBody (fBlock::Int->(Block)->(Block)) (undefined::TableBody) r
 ("blocks","body")->observeHelper W.walkTableBodyM W.queryTableBody (fBlocks::Int->([Block])->([Block])) (undefined::TableBody) r
 ("inline","foot")->observeHelper W.walkTableFootM W.queryTableFoot (fInline::Int->(Inline)->(Inline)) (undefined::TableFoot) r
 ("inlines","foot")->observeHelper W.walkTableFootM W.queryTableFoot (fInlines::Int->([Inline])->([Inline])) (undefined::TableFoot) r
 ("block","foot")->observeHelper W.walkTableFootM W.queryTableFoot (fBlock::Int->(Block)->(Block)) (undefined::TableFoot) r
 ("blocks","foot")->observeHelper W.walkTableFootM W.queryTableFoot (fBlocks::Int->([Block])->([Block])) (undefined::TableFoot) r
 ("inline","caption")->observeHelper W.walkCaptionM W.queryCaption (fInline::Int->(Inline)->(Inline)) (undefined::Caption) r
 ("inlines","caption")->observeHelper W.walkCaptionM W.queryCaption (fInlines::Int->([Inline])->([Inline])) (undefined::Caption) r
 ("block","caption")->observeHelper W.walkCaptionM W.queryCaption (fBlock::Int->(Block)->(Block)) (undefined::Caption) r
 ("blocks","caption")->observeHelper W.walkCaptionM W.queryCaption (fBlocks::Int->([Block])->([Block])) (undefined::Caption) r
 ("inline","cell")->observeHelper W.walkCellM W.queryCell (fInline::Int->(Inline)->(Inline)) (undefined::Cell) r
 ("inlines","cell")->observeHelper W.walkCellM W.queryCell (fInlines::Int->([Inline])->([Inline])) (undefined::Cell) r
 ("block","cell")->observeHelper W.walkCellM W.queryCell (fBlock::Int->(Block)->(Block)) (undefined::Cell) r
 ("blocks","cell")->observeHelper W.walkCellM W.queryCell (fBlocks::Int->([Block])->([Block])) (undefined::Cell) r
 ("inline","pandoc")->observeHelper W.walkPandocM W.queryPandoc (fInline::Int->(Inline)->(Inline)) (undefined::Pandoc) r
 ("inlines","pandoc")->observeHelper W.walkPandocM W.queryPandoc (fInlines::Int->([Inline])->([Inline])) (undefined::Pandoc) r
 ("block","pandoc")->observeHelper W.walkPandocM W.queryPandoc (fBlock::Int->(Block)->(Block)) (undefined::Pandoc) r
 ("blocks","pandoc")->observeHelper W.walkPandocM W.queryPandoc (fBlocks::Int->([Block])->([Block])) (undefined::Pandoc) r
 ("metaValue","metaValuePrime")->observeHelper W.walkMetaValueM' W.queryMetaValue' fMetaValue (undefined::MetaValue) r
 _->error "helper"
generic r=case (as(field "target" r)::String,as(field "root" r)::String)of
 ("meta","pairMetaBlockMixed")->observe fMeta (undefined::(Meta,Block)) r
 ("inline","manyInlines")->observe (fInline::Int->(Inline)->(Inline)) (undefined::Builder.Inlines) r
 ("inlines","manyInlines")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::Builder.Inlines) r
 ("block","manyInlines")->observe (fBlock::Int->(Block)->(Block)) (undefined::Builder.Inlines) r
 ("blocks","manyInlines")->observe (fBlocks::Int->([Block])->([Block])) (undefined::Builder.Inlines) r
 ("inline","manyBlocks")->observe (fInline::Int->(Inline)->(Inline)) (undefined::Builder.Blocks) r
 ("inlines","manyBlocks")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::Builder.Blocks) r
 ("block","manyBlocks")->observe (fBlock::Int->(Block)->(Block)) (undefined::Builder.Blocks) r
 ("blocks","manyBlocks")->observe (fBlocks::Int->([Block])->([Block])) (undefined::Builder.Blocks) r
 ("probe","seqInlines")->observe fProbe (undefined::S.Seq [Inline]) r
 ("probe","pairInlinesBlocks")->observe fProbe (undefined::([Inline],[Block])) r
 ("inline","maybeBlock")->observe (fInline::Int->(Inline)->(Inline)) (undefined::Maybe Block) r
 ("inlines","maybeBlock")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::Maybe Block) r
 ("block","maybeBlock")->observe (fBlock::Int->(Block)->(Block)) (undefined::Maybe Block) r
 ("blocks","maybeBlock")->observe (fBlocks::Int->([Block])->([Block])) (undefined::Maybe Block) r
 ("inline","mapBlock")->observe (fInline::Int->(Inline)->(Inline)) (undefined::M.Map T.Text Block) r
 ("inlines","mapBlock")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::M.Map T.Text Block) r
 ("block","mapBlock")->observe (fBlock::Int->(Block)->(Block)) (undefined::M.Map T.Text Block) r
 ("blocks","mapBlock")->observe (fBlocks::Int->([Block])->([Block])) (undefined::M.Map T.Text Block) r
 ("inline","seqBlock")->observe (fInline::Int->(Inline)->(Inline)) (undefined::S.Seq Block) r
 ("inlines","seqBlock")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::S.Seq Block) r
 ("block","seqBlock")->observe (fBlock::Int->(Block)->(Block)) (undefined::S.Seq Block) r
 ("blocks","seqBlock")->observe (fBlocks::Int->([Block])->([Block])) (undefined::S.Seq Block) r
 ("inline","identityBlock")->observe (fInline::Int->(Inline)->(Inline)) (undefined::Identity Block) r
 ("inlines","identityBlock")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::Identity Block) r
 ("block","identityBlock")->observe (fBlock::Int->(Block)->(Block)) (undefined::Identity Block) r
 ("blocks","identityBlock")->observe (fBlocks::Int->([Block])->([Block])) (undefined::Identity Block) r
 ("inline","nestedBlock")->observe (fInline::Int->(Inline)->(Inline)) (undefined::Maybe (S.Seq Block)) r
 ("inlines","nestedBlock")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::Maybe (S.Seq Block)) r
 ("block","nestedBlock")->observe (fBlock::Int->(Block)->(Block)) (undefined::Maybe (S.Seq Block)) r
 ("blocks","nestedBlock")->observe (fBlocks::Int->([Block])->([Block])) (undefined::Maybe (S.Seq Block)) r
 ("inline","pairInlineBlock")->observe (fInline::Int->(Inline)->(Inline)) (undefined::(Inline,Block)) r
 ("inlines","pairInlineBlock")->observe (fInlines::Int->([Inline])->([Inline])) (undefined::(Inline,Block)) r
 ("block","pairInlineBlock")->observe (fBlock::Int->(Block)->(Block)) (undefined::(Inline,Block)) r
 ("blocks","pairInlineBlock")->observe (fBlocks::Int->([Block])->([Block])) (undefined::(Inline,Block)) r
 ("meta","pairMetaPandoc")->observe (fMeta::Int->Meta->Meta) (undefined::(Meta,Pandoc)) r
 ("metaValue","pairMetaPandoc")->observe (fMetaValue::Int->MetaValue->MetaValue) (undefined::(Meta,Pandoc)) r
 ("pandoc","pairPandoc")->observe (fPandoc::Int->Pandoc->Pandoc) (undefined::(Pandoc,Pandoc)) r
 _->error "generic"
demand r=case as(field "probe" r)::String of
 "list-prefix-bottom-inline"->toJSON(take 1(W.query(\(_::Inline)->["hit"::String])(error "root"::Inline)))
 "list-prefix-bad-tail"->toJSON(take 1(W.query(\(x::Inline)->[show x])[Str "first",error "tail"::Inline]))
 "list-prefix-nested"->toJSON(take 1(W.query(\(x::Inline)->case x of Str t->[T.unpack t];_->[])(Emph[Str "first",error "tail"])))
 "unit-ignores-callback"->toJSON(show(W.query(\(_::Inline)->error "callback"::())(error "root"::Inline)))
 "first-bottom-inline"->toJSON(show(getFirst(W.query(\(_::Inline)->First(Just "hit"::Maybe String))(error "root"::Inline))))
 "first-constant-bad-head"->toJSON(show(getFirst(W.query(\(_::Inline)->First(Just "hit"::Maybe String))[error "head"::Inline])))
 "first-bad-tail"->toJSON(show(getFirst(W.query(\(x::Inline)->First(Just(show x)))[Str "first",error "tail"::Inline])))
 "last-bad-head"->toJSON(show(getLast(W.query(\(x::Inline)->Last(Just(show x)))[error "head"::Inline,Str "last"])))
 "last-constant-bad-head"->toJSON(show(getLast(W.query(\(_::Inline)->Last(Just "hit"::Maybe String))[error "head"::Inline])))
 "meta-skips-block-elements"->toJSON(getSum(W.query(\(_::Meta)->Sum(1::Int))(Pandoc nullMeta[error "block"])))
 "meta-value-skips-block-elements"->toJSON(getSum(W.query(\(_::MetaValue)->Sum(1::Int))(Pandoc(Meta(M.singleton "a"(MetaString "x")))[error "block"])))
 "meta-skips-blocks"->toJSON(getSum(W.query(\(_::Meta)->Sum(1::Int))(Pandoc nullMeta(error "blocks"))))
 "meta-value-skips-blocks"->toJSON(getSum(W.query(\(_::MetaValue)->Sum(1::Int))(Pandoc(Meta(M.singleton "a"(MetaString "x")))(error "blocks"))))
 "pandoc-constant-bottom"->toJSON(show(getFirst(W.query(\(_::Pandoc)->First(Just "hit"::Maybe String))(error "root"::Pandoc))))
 "unit-ignores-bottom"->toJSON(show(W.query(\(_::Inline)->())(error "root"::Inline)))
 "any-ignores-bottom"->toJSON(getAny(W.query(\(_::Inline)->Any True)(error "root"::Inline)))
 "all-ignores-bottom"->toJSON(getAll(W.query(\(_::Inline)->All False)(error "root"::Inline)))
 "failure-before-tail"->toJSON(show(runExcept(W.walkM(\(_::Inline)->throwError("stop"::String))[Str "first",error "tail"::Inline])::Either String [Inline]))
 "deep-walk"->let x=iterate(\v->Emph[v])(Str "leaf")!!10000;y=W.walk(\v->case v of Str t->Str(t<>"done");_->v)x in object["count" .= getSum(W.query(\(_::Inline)->Sum(1::Int))y),"leaf" .= W.query(\v->case v of Str t->T.unpack t;_->"")y]
 "deep-state"->let x=iterate(\v->Emph[v])(Str "leaf")!!10000;(y,n)=runState(W.walkM(\(v::Inline)->modify(+1)>>pure v)x)(0::Int) in toJSON[n,getSum(W.query(\(_::Inline)->Sum(1::Int))y)]
 "deep-count"->toJSON(getSum(W.query(\(_::Inline)->Sum(1::Int))(iterate(\x->Emph[x])(Str "leaf")!!10000)))
 _->error "probe"
operation r=case r of
 Object o | K.lookup "demand" o==Just(Bool True)->demand r
 Object o | K.lookup "generic" o==Just(Bool True)->generic r
 Object o | K.lookup "helper" o==Just(Bool True)->helper r
 _->dictionary r
respond line=case eitherDecode line of
 Left e->error e
 Right req->do
  result<-try(evaluate(let b=encode(operation req)in B.length b `seq` b))::IO(Either SomeException B.ByteString)
  B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result)
main=do
 hSetBuffering stdout LineBuffering
 input<-B.getContents
 mapM_ respond(B.lines input)
