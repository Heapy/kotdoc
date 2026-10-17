{-# LANGUAGE OverloadedStrings,ScopedTypeVariables #-}
-- Actual pinned count dictionaries, constructor patterns and lazy Enum demand.
import Text.Pandoc.Definition
import Control.Exception
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
probes :: forall a.(Num a,Enum a,Show a)=>(Int->a)->(a->Int)->[(String,String)]
probes wrap unwrap=
 [("forced-pattern",show(unwrap(error "root"::a)))
 ,("num-left",show((error "left"::a)+wrap 1))
 ,("num-right",show(wrap 1+(error "right"::a)))
 ,("num-constructed-field",show(wrap(error "field")+wrap 1))
 ,("enum-take-zero",show(take 0(enumFrom(error "root"::a))))
 ,("enum-null",show(null(enumFrom(error "root"::a))))
 ,("enum-ignore-head",show(take 1(map(const(7::Int))(enumFrom(error "root"::a)))))
 ,("enum-head",show(head(enumFrom(error "root"::a))))
 ,("then-second-root",show(take 1(enumFromThen(wrap 1)(error "second"::a))))
 ,("to-end-root",show(take 1(enumFromTo(wrap 1)(error "end"::a))))
 ,("then-to-end-root",show(take 1(enumFromThenTo(wrap 1)(wrap 2)(error "end"::a))))
 ,("then-to-short",show(take 1(enumFromThenTo(wrap 1)(wrap 3)(wrap 2))))
 ,("zero-step-prefix",show(take 4(enumFromThenTo(wrap 2)(wrap 2)(wrap 5))))
 ,("zero-step-empty",show(take 4(enumFromThenTo(wrap 2)(wrap 2)(wrap 1))))
 ]
cases=concat
 [map(\(name,value)->("RowHeadColumns/"++name,value))(("ignored-root",case(error "root"::RowHeadColumns)of RowHeadColumns _->"matched"):("ignored-field",case RowHeadColumns(error "field")of RowHeadColumns _->"matched"):probes RowHeadColumns(\(RowHeadColumns x)->x))
 ,map(\(name,value)->("RowSpan/"++name,value))(("ignored-root",case(error "root"::RowSpan)of RowSpan _->"matched"):("ignored-field",case RowSpan(error "field")of RowSpan _->"matched"):probes RowSpan(\(RowSpan x)->x))
 ,map(\(name,value)->("ColSpan/"++name,value))(("ignored-root",case(error "root"::ColSpan)of ColSpan _->"matched"):("ignored-field",case ColSpan(error "field")of ColSpan _->"matched"):probes ColSpan(\(ColSpan x)->x))]
main=mapM_(\(name,value)->do
 result<-try(evaluate(let bytes=encode value in B.length bytes `seq` bytes))::IO(Either SomeException B.ByteString)
 B.putStrLn(encode name)
 B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result))cases
