{-# LANGUAGE OverloadedStrings,ScopedTypeVariables,FlexibleInstances,MultiParamTypeClasses #-}
-- Original pandoc-types/syb calls only; copyright/licenses retained beside source.
import qualified Text.Pandoc.Generic as G
import qualified Text.Pandoc.Walk as W
import Text.Pandoc.Definition
import qualified Text.Pandoc.Builder as P
import qualified Data.Map as M
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Aeson.KeyMap as K
import Control.Exception
import Data.Monoid
import System.IO
data DemandBox = DemandBox Inline deriving Show
instance W.Walkable Inline DemandBox where
 walkM f (DemandBox x)=DemandBox <$> f x
 query f (DemandBox x)=f x
walkInlineBlock :: Block -> Block
walkInlineBlock = W.walk(const Space::Inline->Inline)
definitionItems (DefinitionList xs)=xs
definitionItems _=error "constructor"
lineItems (LineBlock xs)=xs
lineItems _=error "constructor"
bulletItems (BulletList xs)=xs
bulletItems _=error "constructor"
orderedItems (OrderedList _ xs)=xs
orderedItems _=error "constructor"
field n(Object o)=maybe(error "field")id(K.lookup n o)
as v=case fromJSON v of Success x->x;Error e->error e
operation r=case as(field "probe" r)::String of
 "walk-constant-bad-child"->toJSON(show(W.walk(const Space)(Emph[error "child"])))
 "bottom-up-constant-bad-child"->toJSON(show(G.bottomUp(const Space)(Emph[error "child"])))
 "top-down-constant-bad-child"->toJSON(show(G.topDown(const Space)(Emph[error "child"])))
 "walk-constant-bottom-root"->toJSON(show(W.walk(const Space)(error "root"::Inline)))
 "bottom-up-constant-bottom-root"->toJSON(show(G.bottomUp(const Space)(error "root"::Inline)))
 "top-down-constant-bottom-root"->toJSON(show(G.topDown(const Space)(error "root"::Inline)))
 "generic-first-bottom-inline"->toJSON(show(getFirst(G.queryWith(\(_::Inline)->First(Just "hit"::Maybe String))(error "root"::Inline))))
 "walk-first-bottom-inline"->toJSON(show(getFirst(W.query(\(_::Inline)->First(Just "hit"::Maybe String))(error "root"::Inline))))
 "walk-constructor-bad-child"->toJSON(show(W.walk(\x->case x of Emph _->Space;_->x)(Emph[error "child"])))
 "walk-as-pattern-drop-children"->toJSON(show(W.walk(\x->case x of Emph xs->Emph(take 0 xs);_->x)(Emph[error "child"])))
 "walk-id-bad-child"->toJSON(show(W.walk(id::Inline->Inline)(Emph[error "child"])))
 "walk-forcing-head"->toJSON(show(W.walk(\x->case x of Emph (Space:_)->Space;_->x)(Emph[error "child"])))
 "walk-list-constant-bad-child"->toJSON(show(W.walk(const []::[Inline]->[Inline])(Emph[error "child"])))
 "walk-list-prefix-bad-tail-element"->toJSON(show(W.walk(take 1::[Inline]->[Inline])(Emph[Str "a",error "tail"])))
 "walk-list-constant-bottom-spine"->toJSON(show(W.walk(const []::[Inline]->[Inline])(error "root"::[Inline])))
 "walk-list-constant-partial-spine"->toJSON(show(W.walk(const []::[Inline]->[Inline])(Space:error "tail")))
 "walk-block-constant-bad-child"->toJSON(show(W.walk(const HorizontalRule::Block->Block)(BlockQuote[error "child"])))
 "walk-block-constructor-bad-child"->toJSON(show(W.walk(\x->case x of BlockQuote _->HorizontalRule;_->x)(BlockQuote[error "child"])))
 "walk-inline-in-block-constant"->toJSON(show(W.walk(const Space::Inline->Inline)(Para[error "child"])))
 "walk-block-in-inline-constant"->toJSON(show(W.walk(const HorizontalRule::Block->Block)(Note[error "child"])))
 "walk-meta-value-constant"->toJSON(show(W.walk(const(MetaString "done")::MetaValue->MetaValue)(MetaList[error "child"])))
 "walk-meta-inline-constant"->toJSON(show(W.walk(const Space::Inline->Inline)(MetaInlines[error "child"])))
 "walk-traversable-constant"->toJSON(show(W.walk(const Space::Inline->Inline)([error "child"]::[Inline])))
 "walk-many-constant"->toJSON(show(W.walk(const Space::Inline->Inline)(P.fromList[error "child"]::P.Inlines)))
 "walk-pair-first"->toJSON(show(fst(W.walk(const Space::Inline->Inline)(Space,Emph[error "child"]))))
 "walk-map-keys"->toJSON(show(M.keys(W.walk(\x->case x of Str _->error "callback";_->x)(M.fromList[("a",Str "a"),("b",Str "b")])::M.Map String Inline)))
 "walk-map-first-value"->toJSON(show((W.walk(\x->case x of Str "b"->error "callback";_->x)(M.fromList[("a",Str "a"),("b",Str "b")])::M.Map String Inline) M.! "a"))
 "walk-map-all-values"->toJSON(show(M.elems(W.walk(\x->case x of Str "b"->error "callback";_->x)(M.fromList[("a",Str "a"),("b",Str "b")])::M.Map String Inline)))
 "walk-pair-first-bottom-right"->toJSON(show(fst(W.walk(id::Inline->Inline)(Space,error "right"::Inline))))
 "walk-custom-constant"->toJSON(show(W.walk(const Space::Inline->Inline)(DemandBox(error "child"))))
 "walk-custom-list-constant"->toJSON(show(W.walk(const Space::Inline->Inline)([DemandBox(error "child")])))
 "walk-meta-constant-bottom-root"->toJSON(show(W.walk(const nullMeta::Meta->Meta)(error "root"::Meta)))
 "walk-pandoc-constant-bottom-root"->toJSON(show(W.walk(const(Pandoc nullMeta [])::Pandoc->Pandoc)(error "root"::Pandoc)))
 "walk-blocks-constant-bottom-root"->toJSON(show(W.walk(const []::[Block]->[Block])(error "root"::[Block])))
 "walkM-pair-left-failure"->toJSON(show(W.walkM(const(Left "stop")::Inline->Either String Inline)(Space,error "right"::Inline)))
 "walk-large-list-prefix"->toJSON(show(take 2(W.walk(const Space::Inline->Inline)(replicate 1073741824(error "child"::Inline)))))
 "walk-pair-query-first"->toJSON(show(getFirst(W.query(\(_::Inline)->First(Just "hit"::Maybe String))(Space,error "right"::Inline))))
 "walk-custom-pair-first"->toJSON(show(fst(W.walk(const Space::Inline->Inline)(DemandBox(error "child"),error "right"::Inline))))
 "walk-definition-outer-prefix"->toJSON(show(take 1(definitionItems(walkInlineBlock(DefinitionList[([Str "a"],[]),error "tail"])))))
 "walk-definition-terms-ignore-defs"->toJSON(show(fst(head(definitionItems(walkInlineBlock(DefinitionList[([Str "a"],error "defs")]))))))
 "walk-definition-defs-ignore-terms"->toJSON(show(snd(head(definitionItems(walkInlineBlock(DefinitionList[(error "terms",[[Plain[Str "a"]]])]))))))
 "walk-definition-terms-prefix"->toJSON(show(take 1(fst(head(definitionItems(walkInlineBlock(DefinitionList[([Str "a",error "term-tail"],error "defs")])))))))
 "walk-definition-defs-prefix"->toJSON(show(take 1(snd(head(definitionItems(walkInlineBlock(DefinitionList[(error "terms",[[Plain[Str "a"]],error "defs-tail"])])))))))
 "walk-definition-def-block-prefix"->toJSON(show(take 1(head(snd(head(definitionItems(walkInlineBlock(DefinitionList[(error "terms",[[Plain[Str "a"],error "block-tail"]])]))))))))
 "walk-definition-force-outer-tail"->toJSON(show(definitionItems(walkInlineBlock(DefinitionList[([Str "a"],[]),error "tail"]))))
 "walk-definition-force-defs"->toJSON(show(snd(head(definitionItems(walkInlineBlock(DefinitionList[([Str "a"],error "defs")]))))))
 "walk-definition-force-terms"->toJSON(show(fst(head(definitionItems(walkInlineBlock(DefinitionList[(error "terms",[]) ]))))))
 "walk-definition-empty-prefix"->toJSON(show(take 0(definitionItems(walkInlineBlock(DefinitionList[error "pair"])))))
 "walk-line-inner-spine-undemanded"->toJSON(show(length(take 1(lineItems(walkInlineBlock(LineBlock[error "inner"]))))))
 "walk-bullet-inner-spine-undemanded"->toJSON(show(length(take 1(bulletItems(walkInlineBlock(BulletList[error "inner"]))))))
 "walk-ordered-inner-spine-undemanded"->toJSON(show(length(take 1(orderedItems(walkInlineBlock(OrderedList(1,DefaultStyle,DefaultDelim)[error "inner"]))))))
 "walk-definition-def-inner-spine-undemanded"->toJSON(show(length(take 1(snd(head(definitionItems(walkInlineBlock(DefinitionList[(error "terms",[error "inner"]) ]))))))))
 _->error "probe"
respond line=do
 let r=either error id(eitherDecode line)
 result<-try(evaluate(let b=encode(operation r)in B.length b `seq` b))::IO(Either SomeException B.ByteString)
 B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result)
main=do hSetBuffering stdout LineBuffering;input<-B.getContents;mapM_ respond(B.lines input)
