{-# LANGUAGE ScopedTypeVariables, FlexibleContexts, FlexibleInstances, TypeOperators, TypeFamilies, UndecidableInstances, OverloadedStrings #-}
-- Actual pinned derived Generic dictionaries. Original pandoc-types/GHC notices retained.
import Text.Pandoc.Definition
import qualified Text.Pandoc.Builder as P
import GHC.Generics hiding (Meta)
import Data.Typeable
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
import Control.Exception
import System.IO
class RepShape f where shape :: f p -> Value
instance RepShape U1 where shape U1=object["node" .= ("U1"::String)]
instance (RepShape f,RepShape g)=>RepShape(f :+: g) where
 shape(L1 x)=object["node" .= ("L1"::String),"child" .= shape x]
 shape(R1 x)=object["node" .= ("R1"::String),"child" .= shape x]
instance (RepShape f,RepShape g)=>RepShape(f :*: g) where
 shape(a :*: b)=object["node" .= ("Product"::String),"left" .= shape a,"right" .= shape b]
instance (Show a,Typeable a)=>RepShape(K1 i a) where
 shape(K1 x)=object["node" .= ("K1"::String),"type" .= show(typeOf x),"value" .= show x]
instance (Datatype d,RepShape f)=>RepShape(M1 D d f) where
 shape x@(M1 y)=object["node" .= ("D1"::String),"name" .= datatypeName x,"module" .= moduleName x,"package" .= packageName x,"newtype" .= isNewtype x,"child" .= shape y]
instance (Constructor c,RepShape f)=>RepShape(M1 C c f) where
 shape x@(M1 y)=object["node" .= ("C1"::String),"name" .= conName x,"fixity" .= show(conFixity x),"record" .= conIsRecord x,"child" .= shape y]
instance (Selector s,RepShape f)=>RepShape(M1 S s f) where
 shape x@(M1 y)=object["node" .= ("S1"::String),"name" .= selName x,"unpackedness" .= show(selSourceUnpackedness x),"strictness" .= show(selSourceStrictness x),"decided" .= show(selDecidedStrictness x),"child" .= shape y]
field name(Object x)=maybe(error "field")id(K.lookup name x)
as x=case fromJSON x of Success v->v;Error e->error e
observe :: forall a. (Generic a,RepShape(Rep a),Read a,Show a)=>a->Value->Value
observe _ request=let x=read(as(field "input" request))::a; r=from x in object["from" .= shape r,"toFrom" .= show(to r::a),"fromToFrom" .= shape(from(to r::a))]
operation request=case as(field "kind" request)::String of
 "pandoc"->observe(undefined::Pandoc) request
 "meta"->observe(undefined::Meta) request
 "metaValue"->observe(undefined::MetaValue) request
 "style"->observe(undefined::ListNumberStyle) request
 "delim"->observe(undefined::ListNumberDelim) request
 "format"->observe(undefined::Format) request
 "rowHeadColumns"->observe(undefined::RowHeadColumns) request
 "alignment"->observe(undefined::Alignment) request
 "colWidth"->observe(undefined::ColWidth) request
 "row"->observe(undefined::Row) request
 "tableHead"->observe(undefined::TableHead) request
 "tableBody"->observe(undefined::TableBody) request
 "tableFoot"->observe(undefined::TableFoot) request
 "caption"->observe(undefined::Caption) request
 "cell"->observe(undefined::Cell) request
 "rowSpan"->observe(undefined::RowSpan) request
 "colSpan"->observe(undefined::ColSpan) request
 "block"->observe(undefined::Block) request
 "quote"->observe(undefined::QuoteType) request
 "math"->observe(undefined::MathType) request
 "inline"->observe(undefined::Inline) request
 "citation"->observe(undefined::Citation) request
 "citationMode"->observe(undefined::CitationMode) request
 "inlines"->observe(undefined::P.Inlines) request
 "blocks"->observe(undefined::P.Blocks) request
 _->error "kind"
main=do
 hSetBuffering stdout LineBuffering
 input<-B.getContents
 mapM_ (\line->case eitherDecode line of Left e->error e;Right request->B.putStrLn(encode(operation request))) (B.lines input)
