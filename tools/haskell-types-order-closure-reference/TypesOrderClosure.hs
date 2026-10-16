{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, RankNTypes #-}
-- Original pandoc-types dictionaries only; BSD3 notices retained in licenses/.
import Text.Pandoc.Definition
import qualified Text.Pandoc.Builder as P
import Text.Pandoc.Arbitrary ()
import Test.QuickCheck (Arbitrary,arbitrary)
import Test.QuickCheck.Gen (unGen)
import Test.QuickCheck.Random (mkQCGen)
import Data.Aeson
import qualified Data.Aeson.KeyMap as K
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Map as M
import qualified Data.Sequence as S
import qualified Data.Text as T
import qualified Data.Vector as V
import Data.Data
import Data.Maybe (fromMaybe)
import Control.Monad (msum)
import Control.Monad.State.Strict (runState,state)
import Control.Exception (SomeException,try,evaluate,displayException)
import System.IO (stdout,hSetBuffering,BufferMode(LineBuffering))
field n (Object o)=fromMaybe (error "missing field") (K.lookup n o)
as :: FromJSON a=>Value->a
as x=case fromJSON x of Success v->v;Error e->error e
relation a b=object["eq" .= (a==b),"ne" .= (a/=b),"compare" .= show(compare a b),"lt" .= (a<b),"le" .= (a<=b),"gt" .= (a>b),"ge" .= (a>=b),"minimum" .= show(min a b),"maximum" .= show(max a b)]
relations :: forall a.(Read a,Show a,Ord a)=>a->Value->Value
relations _ r=relation (read(as(field "left" r))::a) (read(as(field "right" r))::a)
-- Change one actual immediate Data field; each alternative is an independently
-- compared original value, not a substitute comparison implementation.
alter :: forall a.Data a=>a->a
alter x=fromMaybe (error ("unhandled field type: "++show(typeOf x))) $ msum
 [modify (T.snoc <$> cast x <*> pure 'x')
 ,modify ((\(v::Int)->if v==maxBound then minBound else v+1) <$> cast x)
 ,modify ((\(v::Double)->v+1) <$> cast x)
 ,modify (not <$> (cast x::Maybe Bool))
 ,modify ((\(v::Format)->case v of Format s->Format(T.snoc s 'x')) <$> cast x)
 ,modify ((\(v::RowHeadColumns)->v+1) <$> cast x)
 ,modify ((\(v::RowSpan)->v+1) <$> cast x)
 ,modify ((\(v::ColSpan)->v+1) <$> cast x)
 ,modify ((\(v::Alignment)->if v==AlignDefault then AlignLeft else AlignDefault) <$> cast x)
 ,modify ((\(v::ListNumberStyle)->if v==DefaultStyle then Decimal else DefaultStyle) <$> cast x)
 ,modify ((\(v::ListNumberDelim)->if v==DefaultDelim then Period else DefaultDelim) <$> cast x)
 ,modify ((\(v::QuoteType)->if v==SingleQuote then DoubleQuote else SingleQuote) <$> cast x)
 ,modify ((\(v::MathType)->if v==InlineMath then DisplayMath else InlineMath) <$> cast x)
 ,modify ((\(v::CitationMode)->if v==NormalCitation then AuthorInText else NormalCitation) <$> cast x)
 ,modify ((\(v::ColWidth)->if v==ColWidthDefault then ColWidth 1 else ColWidthDefault) <$> cast x)
 ,modify ((\((a,b,c)::Attr)->(T.snoc a 'x',b,c)) <$> cast x)
 ,modify ((\((a,b)::Target)->(T.snoc a 'x',b)) <$> cast x)
 ,modify ((\((a,b,c)::ListAttributes)->(a+1,b,c)) <$> cast x)
 ,modify ((\(v::[Inline])->v++[Space]) <$> cast x)
 ,modify ((\(v::[Block])->v++[HorizontalRule]) <$> cast x)
 ,modify ((\(v::[MetaValue])->v++[MetaBool True]) <$> cast x)
 ,modify ((\(v::[T.Text])->v++["x"]) <$> cast x)
 ,modify ((\(v::[(T.Text,T.Text)])->v++[("x","x")]) <$> cast x)
 ,modify ((\(v::[[Inline]])->v++[[Space]]) <$> cast x)
 ,modify ((\(v::[[Block]])->v++[[HorizontalRule]]) <$> cast x)
 ,modify ((\(v::[([Inline],[[Block]])])->v++[([Space],[])]) <$> cast x)
 ,modify ((\(v::[Citation])->v++[Citation "x" [] [] NormalCitation 0 0]) <$> cast x)
 ,modify ((\(v::[ColSpec])->v++[(AlignDefault,ColWidthDefault)]) <$> cast x)
 ,modify ((\(v::[Row])->v++[Row nullAttr []]) <$> cast x)
 ,modify ((\(v::[Cell])->v++[Cell nullAttr AlignDefault 1 1 []]) <$> cast x)
 ,modify ((\(v::[TableBody])->v++[TableBody nullAttr 0 [] []]) <$> cast x)
 ,modify ((\(v::M.Map T.Text MetaValue)->M.insert "_field_probe_" (MetaBool True) v) <$> cast x)
 ,modify ((\(v::Meta)->case v of Meta m->Meta(M.insert "_field_probe_" (MetaBool True)m)) <$> cast x)
 ,modify ((\(v::Caption)->case v of Caption s b->Caption s (b++[HorizontalRule])) <$> cast x)
 ,modify ((\(v::Maybe [Inline])->Just(fromMaybe [] v++[Space])) <$> cast x)
 ,modify ((\(v::TableHead)->case v of TableHead a rs->TableHead a (rs++[Row nullAttr []])) <$> cast x)
 ,modify ((\(v::TableFoot)->case v of TableFoot a rs->TableFoot a (rs++[Row nullAttr []])) <$> cast x)
 ,modify ((\(v::S.Seq Inline)->v S.|> Space) <$> cast x)
 ,modify ((\(v::S.Seq Block)->v S.|> HorizontalRule) <$> cast x)
 ] where modify :: Typeable b=>Maybe b->Maybe a;modify= (>>=cast)
fields :: forall a.(Read a,Show a,Ord a,Data a)=>a->Value->Value
fields _ r=if pairOrder then toJSON [object["fieldIndices" .= [i,j],"left" .= show(variant i),"right" .= show(variant j),"expected" .= relation(variant i)(variant j)] | i<-[0..length types-1],j<-[i+1..length types-1]]
 else toJSON[object["fieldIndex" .= i,"fieldType" .= types!!i,"left" .= show a,"right" .= show(variant i),"expected" .= relation a (variant i)] | i<-[0..length types-1]]
 where
 a=read(as(field "input" r))::a
 types=gmapQ (show.typeOf)a
 variant i=fst(runState(gmapM (\x->state(\n->(if n==i then alter x else x,n+1)))a)0)
 pairOrder=case r of Object o->K.lookup "pairOrder" o==Just(Bool True);_->False

seeded :: forall a.(Arbitrary a,Show a,Ord a)=>a->Value->Value
seeded _ r=let (a,b)=unGen arbitrary (mkQCGen(as(field "seed" r))) (as(field "size" r))::(a,a) in object["left" .= show a,"right" .= show b,"expected" .= relation a b]
byKind :: (forall a.(Read a,Show a,Ord a,Data a)=>a->Value->Value)->Value->Value
byKind f r=case as(field "kind" r)::String of
 "pandoc"->f(undefined::Pandoc)r;"meta"->f(undefined::Meta)r;"metaValue"->f(undefined::MetaValue)r
 "inline"->f(undefined::Inline)r;"block"->f(undefined::Block)r
 "style"->f(undefined::ListNumberStyle)r;"delim"->f(undefined::ListNumberDelim)r
 "format"->f(undefined::Format)r;"rowHeadColumns"->f(undefined::RowHeadColumns)r
 "alignment"->f(undefined::Alignment)r;"colWidth"->f(undefined::ColWidth)r
 "row"->f(undefined::Row)r;"head"->f(undefined::TableHead)r;"body"->f(undefined::TableBody)r
 "foot"->f(undefined::TableFoot)r;"caption"->f(undefined::Caption)r;"cell"->f(undefined::Cell)r
 "rowSpan"->f(undefined::RowSpan)r;"colSpan"->f(undefined::ColSpan)r
 "quote"->f(undefined::QuoteType)r;"math"->f(undefined::MathType)r
 "citation"->f(undefined::Citation)r;"mode"->f(undefined::CitationMode)r
 "inlines"->f(undefined::P.Inlines)r;"blocks"->f(undefined::P.Blocks)r
 _->error "unknown kind"
seedKind r=case as(field "kind" r)::String of
 "pandoc"->seeded(undefined::Pandoc)r;"meta"->seeded(undefined::Meta)r
 "inline"->seeded(undefined::Inline)r;"block"->seeded(undefined::Block)r
 "style"->seeded(undefined::ListNumberStyle)r;"delim"->seeded(undefined::ListNumberDelim)r
 "alignment"->seeded(undefined::Alignment)r;"row"->seeded(undefined::Row)r
 "head"->seeded(undefined::TableHead)r;"body"->seeded(undefined::TableBody)r
 "foot"->seeded(undefined::TableFoot)r;"caption"->seeded(undefined::Caption)r;"cell"->seeded(undefined::Cell)r
 "quote"->seeded(undefined::QuoteType)r;"math"->seeded(undefined::MathType)r
 "citation"->seeded(undefined::Citation)r;"mode"->seeded(undefined::CitationMode)r
 "inlines"->seeded(undefined::P.Inlines)r;"blocks"->seeded(undefined::P.Blocks)r;_->error "seed kind"
one :: Ord a=>(a->String)->String->a->a->Value
one first op a b=case op of
 "eq"->toJSON(a==b);"ne"->toJSON(a/=b);"compare"->toJSON(show(compare a b));"lt"->toJSON(a<b);"le"->toJSON(a<=b);"gt"->toJSON(a>b);"ge"->toJSON(a>=b);"minimum"->toJSON(first(min a b));"maximum"->toJSON(first(max a b));_->error op
partialMany :: forall a.(Ord a,Show a)=>a->a->Value->Value
partialMany small large r=one first (as(field "method" r)) a b
 where
 bad=error "left element";badR=error "right element"
 (xs,ys)=case as(field "probe" r)::String of
  "empty-bad"->([],[bad]);"length-bad"->([bad],[small,large]);"both-bad"->([bad],[badR])
  "later-different"->([small,bad],[large,badR]);"later-equal"->([small,bad],[small,badR])
  "left-bad"->([bad],[small]);"right-bad"->([small],[badR]);_->error "probe"
 a=P.fromList xs;b=P.fromList ys
 first v=case S.viewl(P.unMany v)of S.EmptyL->"[]";x S.:< _->show x
operation r=case as(field "operation" r)::String of
 "relations"->byKind relations r;"fields"->byKind fields r;"seeded"->seedKind r
 "partial"->if as(field "kind" r)==("inlines"::String) then partialMany Space LineBreak r else partialMany HorizontalRule (Plain []) r
 _->error "operation"
respond line=case eitherDecode line of
 Left e->error e
 Right req->do
  result<-try(evaluate(let b=encode(operation req) in B.length b `seq` b))::IO(Either SomeException B.ByteString)
  B.putStrLn(either (encode.object.pure.("exception" .=).head.lines.displayException)id result)
main=do
 hSetBuffering stdout LineBuffering
 input<-B.getContents
 mapM_ respond(B.lines input)
