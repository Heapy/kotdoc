{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.ByteString as B
import qualified Data.Text as T
import qualified Data.Map as M
import qualified Data.List.NonEmpty as NE
import Data.Word (Word64)
import GHC.Float (castWord64ToDouble)
import Text.Pandoc.Definition
import qualified Text.DocLayout as D
import qualified PandocOptionsModels as O
import qualified Text.Pandoc.Writers.AnnotatedTable as Ann
import qualified TemplateAdapter as A
import qualified SourceGridTable as G
import Control.Monad.State.Strict
import Control.Monad.Except
import Control.Exception (SomeException,try,evaluate,displayException)
import System.IO (hSetBuffering,stdout,BufferMode(LineBuffering))
as :: FromJSON a => Value -> a
as = A.as
arr = A.arr
j :: ToJSON a => a -> Value
j = toJSON
sty v = [G.NoLine,G.SingleLine,G.DoubleLine,G.SingleHeaderLine,G.DoubleHeaderLine] !! as v
encSty s = j(case s of G.NoLine->0::Int;G.SingleLine->1;G.DoubleLine->2;G.SingleHeaderLine->3;G.DoubleHeaderLine->4)
opts n w = O.WriterOptions{O.writerColumns=as n,O.writerWrapText=[O.WrapAuto,O.WrapNone,O.WrapPreserve]!!as w,
                          O.writerTabStop=13,O.writerIdentifierPrefix="forwarded"}
cell v = case arr v of
 [n,span,cs,a,rs,w,d,b,t] -> G.RenderedCell(as n)(as span)(NE.fromList(as cs))(as a)(as rs)(as w)(A.doc d)(sty b)(sty t)
 _ -> error "cell"
encCell c = j[j(G.cellColNum c),j(G.cellColSpan c),j(NE.toList(G.cellColSpecs c)),j(G.cellAlign c),j(G.cellRowSpan c),j(G.cellWidth c),A.encDoc(G.cellContents c),encSty(G.cellBottomBorder c),encSty(G.cellTopBorder c)]
rows = map (map cell . arr) . arr
encRows = j . map (map encCell)
info v = case arr v of [s,f,m,u]->G.ColWidthInfo(as s)(as f)(as m)(as u);_->error "width info"
encInfo c = j[j(G.colWidthSpecified c),j(G.colWidthFull c),j(G.colWidthMin c),j(G.colWidthUsed c)]
observeDoc n d = object["doc" .= A.encDoc d,"render" .= D.render Nothing d,"wrapped" .= D.render (Just n) d,"offset" .= D.offset d,"minOffset" .= D.minOffset d]
spans v = M.fromList[(as k,(RowSpan(as r),ColSpan(as c))) | x<-arr v,let[k,r,c]=arr x]
encSpans m = j[j[j k,j r,j c] | (k,(RowSpan r,ColSpan c))<-M.toList m]
process :: (T.Text,Value) -> Value
process (op,v) = case (op,arr v) of
 ("grid",[n,w,b,ds]) -> case as b :: Block of
   Table _ _ cs h bs f ->
    let o=opts n w;docs=arr ds
        callback :: O.WriterOptions -> [Block] -> State [Value] (D.Doc T.Text)
        callback o' blocks=do
          trace<-get;let i=length trace
          modify(<>[j[j i,j blocks,j(O.writerColumns o'),j(show(O.writerWrapText o')),j(O.writerTabStop o'),j(O.writerIdentifierPrefix o')]])
          pure(A.doc(docs!!(i `mod` length docs)))
        (d,trace)=runState(G.gridTable o callback cs h bs f)[]
    in object["result" .= observeDoc(as n)d,"trace" .= trace]
   _ -> error "table"
 ("pipeline",[n,w,cs,rs]) ->
   let o=opts n w;original=rows rs;dummy=G.addDummies original;updated=G.redoWidths o(as cs)dummy
   in object["dummy" .= encRows dummy,"widths" .= map encInfo(G.extractColWidths o dummy),"updated" .= encRows updated,"result" .= observeDoc(as n)(G.gridRows updated)]
 ("reset",[ws,cs]) -> j(map encCell(G.resetWidths(as ws)(map cell(arr cs))))
 ("widths",[n,w,rs]) -> j(map encInfo(G.extractColWidths(opts n w)(rows rs)))
 ("recalculate",[available,is]) ->
   -- The local source function is extracted unchanged by the source preparer.
   j(map encInfo(G.recalculateWidthsProbe(as available)(map info(arr is))))
 ("borders",[s,markers,rs]) -> let r=rows rs;line=sty s in object[
   "border" .= map (A.encDoc . G.formatBorder (const line)(as markers)) r,
   "header" .= A.encDoc(G.formatHeaderLine line r),"isHeader" .= G.isHeaderStyle line,
   "top" .= encRows(G.setTopBorder line r),"bottom" .= encRows(G.setBottomBorder line r)]
 ("combine",[x,y]) -> j(G.combineBorders(as x)(as y))
 ("char-width",[n,bits]) -> j(G.toCharWidth(opts n(j(0::Int)))(castWord64ToDouble(as bits::Word64)))
 ("row",[n,w,cs,ds]) ->
   let o=opts n w;docs=arr ds
       cells=[Ann.Cell(NE.fromList(as ss))(Ann.ColNumber(as at))(as c) | x<-arr cs,let[ss,at,c]=arr x]
       callback :: O.WriterOptions -> [Block] -> State Int (D.Doc T.Text)
       callback _ _=do i<-get;put(i+1);pure(A.doc(docs!!(i `mod` length docs)))
   in j(map encCell(evalState(G.gridRow o callback cells)0))
 ("failure",[n,w,b,failAt]) -> case as b :: Block of
   Table _ _ cs h bs f ->
    let o=opts n w
        callback :: O.WriterOptions -> [Block] -> ExceptT T.Text (State [Value]) (D.Doc T.Text)
        callback _ blocks=do
          trace<-get;let i=length trace
          modify(<>[j blocks])
          if i==as failAt then throwError "renderer failed" else pure(D.literal "ok")
        (result,trace)=runState(runExceptT(G.gridTable o callback cs h bs f))[]
    in object["trace" .= trace,"result" .= either j (observeDoc(as n)) result]
   _ -> error "table"
 ("spans",[action,column,m,r,c]) -> let n=as column;previous=spans m in case as action::T.Text of
   "insert" -> encSpans(G.insertCurrentSpansAtColumn n previous(RowSpan(as r))(ColSpan(as c)))
   "take" -> maybe Null (\(ColSpan s,next)->j[j s,encSpans next])(G.takePreviousSpansAtColumn n previous)
   "trailing" -> encSpans(G.decrementTrailingRowSpans n previous)
   _ -> error "spans action"
 ("tables",[b]) -> case as b::Block of
   Table _ _ _ (TableHead _ hs) bs (TableFoot _ fs) -> let body=G.tableBodiesToRows bs in object["body" .= body,"emptyHead" .= G.allRowsEmpty hs,"emptyBody" .= G.allRowsEmpty body,"emptyFoot" .= G.allRowsEmpty fs]
   _ -> error "table"
 _ -> error "operation"
observe x = do
 r<-try(evaluate(L.toStrict(encode x)))::IO(Either SomeException B.ByteString)
 pure$case r of Left e->object["error" .= takeWhile(/='\n')(displayException e)];Right b->either error id(eitherDecode(L.fromStrict b))
main = do
 hSetBuffering stdout LineBuffering
 L.getContents >>= mapM_(\line->case eitherDecode line of Right req->observe(process req)>>=L.putStrLn.encode;Left e->error e).L.lines
