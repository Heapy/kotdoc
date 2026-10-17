{-# LANGUAGE ScopedTypeVariables, OverloadedStrings, RankNTypes #-}
-- All expectations execute the original Data class and pinned instances.
import Text.Pandoc.Definition
import qualified Text.Pandoc.Builder as P
import qualified Data.Text as T
import qualified Data.Map as M
import Data.Data
import Data.Typeable
import Data.Functor.Identity
import Data.Functor.Const
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import Control.Monad.State.Strict
import Control.Exception
import System.IO
field n(Object o)=maybe(error "field")id(K.lookup n o)
as v=case fromJSON v of Success x->x;Error e->error e
typeInfo :: TypeRep -> Value
typeInfo t=let(c,args)=splitTyConApp t in object["module" .=tyConModule c,"name" .=tyConName c,"arguments" .=map typeInfo args]
childType :: forall a. Data a => a -> Value
childType _=typeInfo(typeOf(undefined::a))
foldStep :: Data d => Const [Value](d->b)->d->Const [Value] b
foldStep(Const before) child=Const(before++[childType child])
unfoldStep :: Data d => Const Int(d->b)->Const Int b
unfoldStep(Const before)=Const(before+1)
observe :: forall a. (Data a,Read a,Show a) => a -> Value -> Value
observe _ req=let x=read(as(field "input" req))::a
                  (monadic,trace)=runState(gmapM(\v->modify(++[childType v])>>pure v)x)([]::[Value])
              in object
 ["gfoldl" .=getConst(gfoldl foldStep(const(Const []))x)
 ,"gunfold" .=getConst(gunfold unfoldStep(const(Const 0))(toConstr x)::Const Int a)
 ,"toConstr" .=showConstr(toConstr x),"dataTypeOf" .=dataTypeName(dataTypeOf x)
 ,"dataCast1-list" .=isHit(dataCast1(Const "list"::forall d. Data d=>Const String[d])::Maybe(Const String a))
 ,"dataCast1-many" .=isHit(dataCast1(Const "many"::forall d. Data d=>Const String(P.Many d))::Maybe(Const String a))
 ,"dataCast2-map" .=isHit(dataCast2(Const "map"::forall d e. (Data d,Data e)=>Const String(M.Map d e))::Maybe(Const String a))
 ,"gmapT" .=show(gmapT id x)
 ,"gmapQl" .=gmapQl(\xs item->xs++[item])[]childType x
 ,"gmapQr" .=gmapQr(:)[]childType x
 ,"gmapQ" .=gmapQ childType x
 ,"gmapQi" .=map(\i->gmapQi i childType x)[0..length(gmapQ childType x)-1]
 ,"gmapM-Identity" .=show(runIdentity(gmapM(Identity . id)x))
 ,"gmapM-State" .=show monadic,"gmapM-trace" .=trace
 ,"gmapMp-Maybe" .=show(gmapMp Just x::Maybe a)
 ,"gmapMo-Maybe" .=show(gmapMo Just x::Maybe a)
 ,"gmapMp-List-count" .=length(gmapMp(\v->[v])x::[a])
 ,"gmapMo-List-count" .=length(gmapMo(\v->[v])x::[a])]
 where isHit Nothing=False;isHit(Just _)=True
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
main=do
 hSetBuffering stdout LineBuffering
 input<-B.getContents
 mapM_ (\line->case eitherDecode line of Left e->error e;Right req->B.putStrLn(encode(operation req))) (B.lines input)
