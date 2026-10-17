{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
-- Actual pinned Definition dictionaries; no reconstructed numeric expectation.
import Text.Pandoc.Definition
import Data.Aeson
import qualified Data.Text
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import Control.Exception
import System.IO
field n(Object o)=maybe(error "field")id(K.lookup n o)
as v=case fromJSON v of Success x->x;Error e->error e
run :: forall a.(Num a,Enum a,Show a)=>(Int->a)->(a->Int)->Value->Value
run wrap unwrap r=let
 x=as(field "x" r)::Int;y=as(field "y" r)::Int;z=as(field "z" r)::Int
 a=wrap x;b=wrap y;c=wrap z
 scalar :: Show b=>b->Value
 scalar=String . fromStringText . show
 fromStringText=Data.Text.pack
 limit=as(field "limit" r)::Int
 range xs=let prefix=take(limit+1)xs in object["values" .= map show(take limit prefix),"truncated" .= (length prefix>limit)]
 in case as(field "operation" r)::String of
 "constructor"->scalar a
 "pattern"->scalar(unwrap a)
 "plus"->scalar(a+b)
 "minus"->scalar(a-b)
 "times"->scalar(a*b)
 "negate"->scalar(negate a)
 "abs"->scalar(abs a)
 "signum"->scalar(signum a)
 "fromInteger"->scalar(fromInteger(read(as(field "integer" r)::String))::a)
 "succ"->scalar(succ a)
 "pred"->scalar(pred a)
 "toEnum"->scalar(toEnum x::a)
 "fromEnum"->scalar(fromEnum a)
 "enumFrom"->range(enumFrom a)
 "enumFromThen"->range(enumFromThen a b)
 "enumFromTo"->range(enumFromTo a c)
 "enumFromThenTo"->range(enumFromThenTo a b c)
 _->error "operation"
bridge r=let x=as(field "x" r)::Int;y=as(field "y" r)::Int in case as(field "kind" r)::String of
 "RowHeadColumns"->let value=TableBody nullAttr(RowHeadColumns x)[][];extracted=case value of TableBody _ (RowHeadColumns n) _ _->n in object["constructed" .= show value,"extracted" .= show extracted]
 "RowSpan"->let value=Cell nullAttr AlignDefault(RowSpan x)(ColSpan y)[];extracted=case value of Cell _ _ (RowSpan n) _ _->n in object["constructed" .= show value,"extracted" .= show extracted]
 "ColSpan"->let value=Cell nullAttr AlignDefault(RowSpan y)(ColSpan x)[];extracted=case value of Cell _ _ _ (ColSpan n) _->n in object["constructed" .= show value,"extracted" .= show extracted]
observe r | (as(field "operation" r)::String)=="bridge"=bridge r
observe r=case as(field "kind" r)::String of
 "RowHeadColumns"->run RowHeadColumns(\(RowHeadColumns x)->x)r
 "RowSpan"->run RowSpan(\(RowSpan x)->x)r
 "ColSpan"->run ColSpan(\(ColSpan x)->x)r
 _->error "kind"
respond line=do
 let r=either error id(eitherDecode line)
 result<-try(evaluate(let bytes=encode(observe r)in B.length bytes `seq` bytes))::IO(Either SomeException B.ByteString)
 B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result)
main=do hSetBuffering stdout LineBuffering;input<-B.getContents;mapM_ respond(B.lines input)
