{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
-- Actual original NFData dictionaries; no reconstructed expectations.
import Text.Pandoc.Definition
import Control.DeepSeq
import Control.Exception
import Data.Proxy
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import System.IO
field n(Object o)=maybe(error "field")id(K.lookup n o)
as v=case fromJSON v of Success x->x;Error e->error e
observe :: forall a. (Read a,Show a,NFData a)=>Proxy a->Value->IO Value
observe _ request = do
 let input=read(as(field "input" request))::a
     op=as(field "operation" request)::String
 case op of
  "rnf"->evaluate(rnf input)>>pure(String "()")
  "force"->evaluate(force input)>>pure(String "forced")
  "deepseq"->evaluate(input `deepseq` (7::Int))>>pure(Number 7)
  "rwhnf"->evaluate(rwhnf input)>>pure(String "()")
  _->error "operation"
run request=case as(field "kind" request)::String of
 "Pandoc"->observe(Proxy::Proxy Pandoc) request
 "Meta"->observe(Proxy::Proxy Meta) request
 "MetaValue"->observe(Proxy::Proxy MetaValue) request
 "ListNumberStyle"->observe(Proxy::Proxy ListNumberStyle) request
 "ListNumberDelim"->observe(Proxy::Proxy ListNumberDelim) request
 "Format"->observe(Proxy::Proxy Format) request
 "RowHeadColumns"->observe(Proxy::Proxy RowHeadColumns) request
 "Alignment"->observe(Proxy::Proxy Alignment) request
 "ColWidth"->observe(Proxy::Proxy ColWidth) request
 "Row"->observe(Proxy::Proxy Row) request
 "TableHead"->observe(Proxy::Proxy TableHead) request
 "TableBody"->observe(Proxy::Proxy TableBody) request
 "TableFoot"->observe(Proxy::Proxy TableFoot) request
 "Caption"->observe(Proxy::Proxy Caption) request
 "Cell"->observe(Proxy::Proxy Cell) request
 "RowSpan"->observe(Proxy::Proxy RowSpan) request
 "ColSpan"->observe(Proxy::Proxy ColSpan) request
 "Block"->observe(Proxy::Proxy Block) request
 "QuoteType"->observe(Proxy::Proxy QuoteType) request
 "MathType"->observe(Proxy::Proxy MathType) request
 "Inline"->observe(Proxy::Proxy Inline) request
 "Citation"->observe(Proxy::Proxy Citation) request
 "CitationMode"->observe(Proxy::Proxy CitationMode) request
 _->error "kind"
main=do
 hSetBuffering stdout LineBuffering
 input<-B.getContents
 mapM_ (\line->do
  let request=case eitherDecode line of Left e->error e;Right r->r
  result<-try(run request)
  let response=case result of Right value->object["result" .= value];Left(e::SomeException)->object["exception" .= takeWhile(/='\n')(displayException e)]
  B.putStrLn(encode response)) (B.lines input)
