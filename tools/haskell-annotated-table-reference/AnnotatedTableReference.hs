{-# LANGUAGE OverloadedStrings, FlexibleContexts, ScopedTypeVariables #-}
module Main where
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.ByteString
import qualified Data.Text as T
import qualified Data.Vector as V
import qualified Data.List.NonEmpty as NE
import Text.Pandoc.Definition
import Text.Pandoc.Walk
import qualified Text.Pandoc.Builder as B
import qualified Text.Pandoc.Writers.AnnotatedTable as A
import Control.Monad.State.Strict
import Control.Exception(SomeException,try,evaluate,displayException)
import System.IO(hSetBuffering,stdout,BufferMode(LineBuffering))

as :: FromJSON a => Value -> a
as v=case fromJSON v of Success x->x;Error e->error e
arr (Array a)=V.toList a
arr _=error "array"
j :: ToJSON a => a -> Value
j=toJSON
cell (A.Cell cs (A.ColNumber n) c)=j[j(NE.toList cs),j n,j c]
header (A.HeaderRow a (A.RowNumber n) cs)=j[j a,j n,j(map cell cs)]
bodyRow (A.BodyRow a (A.RowNumber n) hs bs)=j[j a,j n,j(map cell hs),j(map cell bs)]
head' (A.TableHead a rs)=j[j a,j(map header rs)]
body (A.TableBody a n hs bs)=j[j a,j n,j(map header hs),j(map bodyRow bs)]
foot (A.TableFoot a rs)=j[j a,j(map header rs)]
table (A.Table a c cs h bs f)=j[j a,j c,j cs,head' h,j(map body bs),foot f]
uncell v=case arr v of [cs,n,c]->A.Cell (NE.fromList(as cs)) (A.ColNumber(as n)) (as c);_->error "cell"
unheader v=case arr v of [a,n,cs]->A.HeaderRow(as a)(A.RowNumber(as n))(map uncell(arr cs));_->error "row"
unbodyrow v=case arr v of [a,n,hs,bs]->A.BodyRow(as a)(A.RowNumber(as n))(map uncell(arr hs))(map uncell(arr bs));_->error "bodyrow"
unhead v=case arr v of [a,rs]->A.TableHead(as a)(map unheader(arr rs));_->error "head"
unbody v=case arr v of [a,n,hs,bs]->A.TableBody(as a)(as n)(map unheader(arr hs))(map unbodyrow(arr bs));_->error "body"
unfoot v=case arr v of [a,rs]->A.TableFoot(as a)(map unheader(arr rs));_->error "foot"
untable v=case arr v of [a,c,cs,h,bs,f]->A.Table(as a)(as c)(as cs)(unhead h)(map unbody(arr bs))(unfoot f);_->error "table"
from t=let(a,c,cs,h,bs,f)=A.fromTable t in Table a c cs h bs f
normalize (Table a c cs h bs f)=case B.toList(B.tableWith a c cs h bs f) of [b]->b;_->error "builder"
normalize _=error "table block"
annotate (Table a c cs h bs f)=A.toTable a c cs h bs f
annotate _=error "table block"
fi :: Inline -> State [Value] Inline
fi x=modify(j x:)>>pure(case x of Str s->Strong[Str("walk:"<>s)];_->x)
fb :: Block -> State [Value] Block
fb x=modify(j x:)>>pure(case x of Para xs->BlockQuote[Para xs];_->x)
fis :: [Inline] -> State [Value] [Inline]
fis xs=modify(j xs:)>>pure(xs<>[Str "walk-list"])
fbs :: [Block] -> State [Value] [Block]
fbs xs=modify(j xs:)>>pure(xs<>[HorizontalRule])
oneWalk :: (Walkable a t,ToJSON a) => (t->Value) -> (a->State [Value] a) -> (a->[a]) -> t -> Value
oneWalk enc f q t=let(t',trace)=runState(walkM f t)[] in object["walked" .= enc t',"trace" .= reverse trace,"query" .= query q t]
walks :: (Walkable Inline t,Walkable Block t,Walkable [Inline] t,Walkable [Block] t) => (t->Value) -> t -> Value
walks enc t=object["inline" .= oneWalk enc fi (:[]) t,"block" .= oneWalk enc fb (:[]) t,
                   "inlines" .= oneWalk enc fis (:[]) t,"blocks" .= oneWalk enc fbs (:[]) t]
allRows (A.Table _ _ _ (A.TableHead _ hs) bs (A.TableFoot _ fs))=hs<>concatMap rows bs<>fs where
 rows (A.TableBody _ _ hs ts)=hs<>map(\(A.BodyRow a n x y)->A.HeaderRow a n(x<>y))ts
walkTargets t=let rs=allRows t
                  h=A.TableHead("walk-head",[],[])rs
                  r=case rs of x:_->x;_->A.HeaderRow nullAttr(-17)[]
                  c=case concatMap(\(A.HeaderRow _ _ cs)->cs)rs of x:_->x;_->A.Cell((AlignDefault,ColWidthDefault) NE.:|[])(-13)B.emptyCell
              in object["cell" .= walks cell c,"row" .= walks header r,"head" .= walks head' h]
partial kind (Table a cap cs (TableHead ha rs) bs f)=Table a cap cs (TableHead ha (case rs of Row ra xs:rest->Row ra (change xs):rest;_->error "head row")) bs f
 where change xs=case kind::T.Text of
        "tail"->xs<> (B.emptyCell:error "cell tail")
        "unused-cell"->xs<>[error "unused cell"]
        "cell-list"->error "cell list"
        _->error "partial kind"
partial _ _=error "table block"
process :: (T.Text,Value) -> Value
process (op,v)=case op of
 "annotate"->let b=as v::Block;t=annotate b in object["annotated" .= table t,"from" .= from t,"normalized" .= normalize b,"walks" .= walkTargets t]
 "partial"->case arr v of [kind,b]->let t=annotate(partial(as kind)(as b)) in object["annotated" .= table t,"from" .= from t,"walks" .= walkTargets t];_->error "partial pair"
 "raw"->let t=untable v in object["from" .= from t,"walks" .= walkTargets t]
 _->error "operation"
observe x=do
 r<-try(evaluate(L.toStrict(encode x)))::IO(Either SomeException Data.ByteString.ByteString)
 pure$case r of Left e->object["error" .= takeWhile(/='\n')(displayException e)];Right b->either error id(eitherDecode(L.fromStrict b))
main=do
 hSetBuffering stdout LineBuffering
 L.getContents>>=mapM_(\line->case eitherDecode line of Right req->observe(process req)>>=L.putStrLn.encode;Left e->error e).L.lines
