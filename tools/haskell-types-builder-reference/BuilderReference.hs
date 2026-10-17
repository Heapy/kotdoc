{-# LANGUAGE OverloadedStrings #-}
-- Independent observations of the actual pinned Builder, not reconstructed outputs.
import qualified Text.Pandoc.Builder as P
import Text.Pandoc.Definition
import qualified Data.Foldable as F
import qualified Data.Sequence as S
import qualified Data.Text as T
import Data.Char(chr)
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import Control.Exception
import System.IO
field n(Object o)=maybe(error "field")id(K.lookup n o)
as v=case fromJSON v of Success x->x;Error e->error e
native :: Read a=>Value->a
native=read.as
newtype Trace=Trace String
instance Semigroup Trace where Trace x<>Trace y=Trace("("++x++">"++y++")")
instance Monoid Trace where mempty=Trace "e"
shape xs=let Trace result=F.foldMap(const(Trace "x"))xs in result
many xs=object["value" .= show(P.toList xs),"shape" .= shape xs]
observe r=let
 op=as(field "operation" r)::String
 ils=P.fromList(native(field "inlines" r))::P.Inlines
 bs=P.fromList(native(field "blocks" r))::P.Blocks
 txt=T.pack(map chr(as(field "points" r)::[Int]))
 fmt=T.pack(map chr(as(field "format" r)::[Int]))
 url=T.pack(map chr(as(field "url" r)::[Int]))
 title=T.pack(map chr(as(field "title" r)::[Int]))
 attr=native(field "attr" r)::Attr
 items=map P.fromList(native(field "items" r))::[P.Blocks]
 lines=map P.fromList(native(field "lines" r))::[P.Inlines]
 capt=native(field "caption" r)::Caption
 in case op of
 "moduleExample"->String(T.pack(show(P.setTitle (P.text "My title") $ P.doc $ P.para(P.text "This is the first paragraph") <> P.para(P.text "And " <> P.emph(P.text "another") <> P.text ".") <> P.bulletList[P.para(P.text "item one") <> P.para(P.text "continuation"),P.plain(P.text "item two and a " <> P.link "/url" "go to url"(P.text "link"))])))
 "nullAttr"->String(T.pack(show nullAttr))
 "text"->many(P.text txt)
 "str"->many(P.str txt)
 "emph"->many(P.emph ils)
 "underline"->many(P.underline ils)
 "strong"->many(P.strong ils)
 "strikeout"->many(P.strikeout ils)
 "superscript"->many(P.superscript ils)
 "subscript"->many(P.subscript ils)
 "smallcaps"->many(P.smallcaps ils)
 "singleQuoted"->many(P.singleQuoted ils)
 "doubleQuoted"->many(P.doubleQuoted ils)
 "cite"->many(P.cite(native(field "citations" r))ils)
 "codeWith"->many(P.codeWith attr txt)
 "code"->many(P.code txt)
 "space"->many P.space
 "softbreak"->many P.softbreak
 "linebreak"->many P.linebreak
 "math"->many(P.math txt)
 "displayMath"->many(P.displayMath txt)
 "rawInline"->many(P.rawInline fmt txt)
 "linkWith"->many(P.linkWith attr url title ils)
 "link"->many(P.link url title ils)
 "imageWith"->many(P.imageWith attr url title ils)
 "image"->many(P.image url title ils)
 "note"->many(P.note bs)
 "spanWith"->many(P.spanWith attr ils)
 "trimInlines"->many(P.trimInlines ils)
 "para"->many(P.para ils)
 "plain"->many(P.plain ils)
 "lineBlock"->many(P.lineBlock lines)
 "codeBlockWith"->many(P.codeBlockWith attr txt)
 "codeBlock"->many(P.codeBlock txt)
 "rawBlock"->many(P.rawBlock fmt txt)
 "blockQuote"->many(P.blockQuote bs)
 "orderedListWith"->many(P.orderedListWith(native(field "listAttributes" r))items)
 "orderedList"->many(P.orderedList items)
 "bulletList"->many(P.bulletList items)
 "definitionList"->many(P.definitionList(map(\(a,b)->(P.fromList a,map P.fromList b))(native(field "definitions" r))))
 "headerWith"->many(P.headerWith attr(as(field "level" r))ils)
 "header"->many(P.header(as(field "level" r))ils)
 "horizontalRule"->many P.horizontalRule
 "caption"->String(T.pack(show(P.caption(native(field "short" r))bs)))
 "simpleCaption"->String(T.pack(show(P.simpleCaption bs)))
 "emptyCaption"->String(T.pack(show P.emptyCaption))
 "figureWith"->many(P.figureWith attr capt bs)
 "figure"->many(P.figure capt bs)
 "simpleFigureWith"->many(P.simpleFigureWith attr ils url title)
 "simpleFigure"->many(P.simpleFigure ils url title)
 "divWith"->many(P.divWith attr bs)
 "split"->let (a,b)=S.splitAt(as(field "index" r))(S.fromList(as(field "values" r)::[Int]))in object["left" .= many(P.Many a),"right" .= many(P.Many b)]
 _->error "operation"
respond line=do
 let r=either error id(eitherDecode line)
 result<-try(evaluate(let bytes=encode(observe r)in B.length bytes `seq` bytes))::IO(Either SomeException B.ByteString)
 B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result)
main=do hSetBuffering stdout LineBuffering;input<-B.getContents;mapM_ respond(B.lines input)
