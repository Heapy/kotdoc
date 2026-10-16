{-# LANGUAGE ScopedTypeVariables, OverloadedStrings #-}
-- Independent calls to pinned pandoc-types Generic and source Data dictionaries.
import qualified Text.Pandoc.Generic as G
import Text.Pandoc.Definition
import qualified Text.Pandoc.Builder as P
import qualified Data.Text as T
import Data.Data
import Data.Typeable
import Data.Char
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import Control.Monad.State.Strict
import Control.Exception
import System.IO
field n(Object o)=maybe(error "field")id(K.lookup n o)
as v=case fromJSON v of Success x->x;Error e->error e
shape :: forall a. Data a => a -> Value
typeInfo t=let (c,args)=splitTyConApp t in object["module" .= tyConModule c,"name" .= tyConName c,"package" .= tyConPackage c,"arguments" .= map typeInfo args]
shape x=object (["type" .= show(typeOf x),"typeInfo" .= typeInfo(typeOf x),"constructor" .= showConstr(toConstr x),"fields" .= gmapQ shape x] ++ scalar)
 where scalar=case cast x::Maybe Int of
        Just v->["scalar" .= v]
        Nothing->case cast x::Maybe Double of
         Just v->["scalar" .= show v]
         Nothing->case cast x::Maybe Char of
          Just v->["scalar" .= ord v]
          Nothing->case cast x::Maybe T.Text of
           Just v->["scalar" .= v]
           Nothing->[]
observe :: forall a. (Data a,Read a,Show a) => a -> Value -> Value
observe _ req | Object o<-req, Just target<-K.lookup "target" o = let x=read(as(field "input" req))::a in case as target::String of
 "text"->observeTransform ((<>"!")::T.Text->T.Text) x
 "int"->observeTransform ((+1)::Int->Int) x
 "bool"->observeTransform (not::Bool->Bool) x
 "string"->observeTransform (reverse::String->String) x
 "target"->observeTransform ((\(a,b)->(a<>"!",b))::Target->Target) x
 "char-surrogate"->observeTransform (const '\55296'::Char->Char) x
 "text-collapse"->observeTransform (const "a"::T.Text->T.Text) x
 _->error "target"
observe _ req=let { x=read(as(field "input" req))::a; change c=if c=='a' then 'X' else c; (m,seen)=runState(G.bottomUpM(\(c::Char)->modify(++[c])>>pure(change c))x)([]::String) }
             in object["inputShape" .= shape x,"bottomUp" .= show(G.bottomUp change x),"topDown" .= show(G.topDown change x),"bottomUpM" .= show m,"effects" .= seen,"queryWith" .= G.queryWith(\(c::Char)->[ord c])x]
observeTransform :: forall a b. (Data a,Show a,Data b,Show b) => (b->b) -> a -> Value
observeTransform f x=let (m,seen)=runState(G.bottomUpM(\(v::b)->modify(++[show v])>>pure(f v))x)([]::[String])
 in object["bottomUp" .= show(G.bottomUp f x),"topDown" .= show(G.topDown f x),"bottomUpM" .= show m,"effects" .= seen,"queryWith" .= G.queryWith(\(v::b)->[show v])x]
operation req=case as(field "kind" req)::String of
 "citationMode" -> observe (undefined::CitationMode) req
 "tableHead" -> observe (undefined::TableHead) req
 "tableBody" -> observe (undefined::TableBody) req
 "tableFoot" -> observe (undefined::TableFoot) req
 "pandoc" -> observe (undefined::Pandoc) req
 "meta" -> observe (undefined::Meta) req
 "metaValue" -> observe (undefined::MetaValue) req
 "inline" -> observe (undefined::Inline) req
 "block" -> observe (undefined::Block) req
 "style" -> observe (undefined::ListNumberStyle) req
 "delim" -> observe (undefined::ListNumberDelim) req
 "format" -> observe (undefined::Format) req
 "rowHeadColumns" -> observe (undefined::RowHeadColumns) req
 "alignment" -> observe (undefined::Alignment) req
 "colWidth" -> observe (undefined::ColWidth) req
 "row" -> observe (undefined::Row) req
 "head" -> observe (undefined::TableHead) req
 "body" -> observe (undefined::TableBody) req
 "foot" -> observe (undefined::TableFoot) req
 "caption" -> observe (undefined::Caption) req
 "cell" -> observe (undefined::Cell) req
 "rowSpan" -> observe (undefined::RowSpan) req
 "colSpan" -> observe (undefined::ColSpan) req
 "quote" -> observe (undefined::QuoteType) req
 "math" -> observe (undefined::MathType) req
 "citation" -> observe (undefined::Citation) req
 "mode" -> observe (undefined::CitationMode) req
 "inlines" -> observe (undefined::P.Inlines) req
 "blocks" -> observe (undefined::P.Blocks) req
 "attr" -> observe (undefined::Attr) req
 "target" -> observe (undefined::Target) req
 "listAttributes" -> observe (undefined::ListAttributes) req
 "colSpec" -> observe (undefined::ColSpec) req
 "shortCaption" -> observe (undefined::ShortCaption) req
 "text" -> observe (undefined::T.Text) req
 "int" -> observe (undefined::Int) req
 "double" -> observe (undefined::Double) req
 "bool" -> observe (undefined::Bool) req
 "char" -> observe (undefined::Char) req
 _->error "kind"
respond line=do
 let r=either error id(eitherDecode line)
 result<-try(evaluate(let b=encode(operation r)in B.length b `seq` b))::IO(Either SomeException B.ByteString)
 B.putStrLn(either(encode.object.pure.("exception" .=).takeWhile(/='\n').displayException)id result)
main=do hSetBuffering stdout LineBuffering;input<-B.getContents;mapM_ respond(B.lines input)
