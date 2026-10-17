{-# LANGUAGE OverloadedStrings #-}
-- Calls the actual pinned Builder table family, never reconstructed expectations.
import Text.Pandoc.Builder
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text
import Control.Exception
import System.IO
field n(Object o)=maybe(error "field")id(K.lookup n o)
as v=case fromJSON v of Success x->x;Error e->error e
native :: Read a=>Value->a
native=read.as
observe r=let
 width=as(field "width" r)::Int
 rows=native(field "rows" r)::[Row]
 cells=native(field "cells" r)::[Cell]
 hang=map RowSpan(as(field "hang" r)::[Int])
 pad=as(field "pad" r)::Bool
 stream=if pad then cells++repeat emptyCell else cells
 limit=as(field "limit" r)::Int
 showPrefix xs=show(take limit xs)
 in case as(field "operation" r)::String of
 "place"->let(a,b,c)=placeRowSection hang stream in object["overhang" .= show a,"cells" .= show b,"remaining" .= showPrefix c,"remainingTruncated" .= not(null(drop limit c))]
 "clip"->String(fromStringText(show(clipRows rows)))
 "head"->String(fromStringText(show(normalizeTableHead width(native(field "head" r)))))
 "body"->String(fromStringText(show(normalizeTableBody width(native(field "body" r)))))
 "foot"->String(fromStringText(show(normalizeTableFoot width(native(field "foot" r)))))
 "cellWith"->String(fromStringText(show(cellWith(native(field "attr" r))(native(field "alignment" r))(RowSpan(as(field "rowSpan" r)))(ColSpan(as(field "colSpan" r)))(fromList(native(field "blocks" r))))))
 "cell"->String(fromStringText(show(cell(native(field "alignment" r))(RowSpan(as(field "rowSpan" r)))(ColSpan(as(field "colSpan" r)))(fromList(native(field "blocks" r))))))
 "simpleCell"->String(fromStringText(show(simpleCell(fromList(native(field "blocks" r))))))
 "emptyCell"->String(fromStringText(show emptyCell))
 "tableWith"->String(fromStringText(show(tableWith(native(field "attr" r))(native(field "caption" r))(native(field "specs" r))(native(field "head" r))(native(field "bodies" r))(native(field "foot" r)))))
 "table"->String(fromStringText(show(table(native(field "caption" r))(native(field "specs" r))(native(field "head" r))(native(field "bodies" r))(native(field "foot" r)))))
 "simpleTable"->String(fromStringText(show(simpleTable(map fromList(native(field "headers" r)))(map(map fromList)(native(field "simpleRows" r))))))
 _->error "operation"
fromStringText=Data.Text.pack
respond line=do
 let r=either error id(eitherDecode line)
 result<-try(evaluate(let bytes=encode(observe r)in B.length bytes `seq` bytes))::IO(Either SomeException B.ByteString)
 B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result)
main=do hSetBuffering stdout LineBuffering;input<-B.getContents;mapM_ respond(B.lines input)
