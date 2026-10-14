{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import qualified TemplateAdapter as A
import Text.DocTemplates.Internal
import qualified Text.DocLayout as D
import qualified Text.DocLayout.Attributed as Attr
import qualified Data.Text as T
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.ByteString as S
import Control.Exception(SomeException,try,evaluate,displayException)
import GHC.Float(castWord64ToDouble,castDoubleToWord64)
import Data.Char(chr,ord)
import Data.Foldable(toList)
import System.IO(hSetBuffering,stdout,BufferMode(LineBuffering))

newtype Probe = Probe T.Text
instance Eq Probe where
 Probe a == Probe b = if a=="!" || b=="!" || a=="L!" || b=="R!" then error "forced target" else a==b
instance Ord Probe where
 compare (Probe a)(Probe b) = if a=="!" || b=="!" || a=="L!" || b=="R!" then error "forced target" else compare a b
as v=case fromJSON v of Success a->a;Error e->error e
arr=A.arr
observe :: ToJSON a => a -> IO Value
observe x=do
 res<-try(evaluate(B.toStrict(encode x)))::IO(Either SomeException S.ByteString)
 pure$case res of Left e->object["error" .= takeWhile(/='\n')(displayException e)];Right bytes->either error id(eitherDecode(B.fromStrict bytes))
report :: Ord a => (a->Value) -> a -> a -> IO Value
report enc a b=do
 c<-observe(show(compare a b));e<-observe(a==b);ne<-observe(a/=b);l<-observe(a<b);le<-observe(a<=b);g<-observe(a>b);ge<-observe(a>=b)
 mn<-observe(enc(min a b));mx<-observe(enc(max a b))
 pure(object["compare" .= c,"equal" .= e,"notEqual" .= ne,"less" .= l,"lessEqual" .= le,"greater" .= g,"greaterEqual" .= ge,"minimum" .= mn,"maximum" .= mx])
part v=case A.doc(toJSON[String "cooked",toJSON(0::Int),toJSON[v]]) of
 D.CookedText _ (Attr.Attributed seq)->head(toList seq)
 _->error "part"
attr v=case A.doc(toJSON[String "cooked",toJSON(0::Int),v]) of
 D.CookedText _ a->a
 _->error "attributed"
encAttr a=A.encAttributed a
encPart a=A.encAttributed(Attr.fromList[a])
process(op,kind,input)=case arr input of
 [a,b]->case op::String of
  "template"->case kind::String of
   "text"->report A.encTemplate(A.template a)(A.template b)
   "double"->report(A.encTemplate.fmap(T.pack.show.castDoubleToWord64))(fmap(castWord64ToDouble.read.T.unpack)(A.template a))(fmap(castWord64ToDouble.read.T.unpack)(A.template b))
   "int64"->report(A.encTemplate.fmap(T.pack.show))(fmap(read.T.unpack)(A.template a)::Template Int)(fmap(read.T.unpack)(A.template b)::Template Int)
   "string-points"->report(A.encTemplate.fmap(T.pack.show.map ord))(fmap(map chr.read.T.unpack)(A.template a))(fmap(map chr.read.T.unpack)(A.template b))
   "probe"->report(A.encTemplate.fmap(\(Probe x)->x))(fmap Probe(A.template a))(fmap Probe(A.template b))
   _->error kind
  "doc"->case kind::String of
   "text"->report A.encDoc(A.doc a)(A.doc b)
   "double"->report(A.encDoc.fmap(T.pack.show.castDoubleToWord64))(fmap(castWord64ToDouble.read.T.unpack)(A.doc a))(fmap(castWord64ToDouble.read.T.unpack)(A.doc b))
   "int64"->report(A.encDoc.fmap(T.pack.show))(fmap(read.T.unpack)(A.doc a)::D.Doc Int)(fmap(read.T.unpack)(A.doc b)::D.Doc Int)
   "string-points"->report(A.encDoc.fmap(T.pack.show.map ord))(fmap(map chr.read.T.unpack)(A.doc a))(fmap(map chr.read.T.unpack)(A.doc b))
   "probe"->report(A.encDoc.fmap(\(Probe x)->x))(fmap Probe(A.doc a))(fmap Probe(A.doc b))
   _->error kind
  "part"->case kind of
   "text"->report encPart(part a)(part b)
   "double"->report(encPart.fmap(T.pack.show.castDoubleToWord64))(fmap(castWord64ToDouble.read.T.unpack)(part a))(fmap(castWord64ToDouble.read.T.unpack)(part b))
   "probe"->report(encPart.fmap(\(Probe x)->x))(fmap Probe(part a))(fmap Probe(part b))
   _->error kind
  "attributed"->case kind of
   "text"->report encAttr(attr a)(attr b)
   "double"->report(encAttr.fmap(T.pack.show.castDoubleToWord64))(fmap(castWord64ToDouble.read.T.unpack)(attr a))(fmap(castWord64ToDouble.read.T.unpack)(attr b))
   "probe"->report(encAttr.fmap(\(Probe x)->x))(fmap Probe(attr a))(fmap Probe(attr b))
   _->error kind
  "font"->report A.encFont(A.font a)(A.font b)
  "style"->report A.encStyle(A.style a)(A.style b)
  "variable"->report A.encVariable(A.variable a)(A.variable b)
  "pipe"->report A.encPipe(A.pipe a)(A.pipe b)
  "alignment"->report (toJSON.show)([LeftAligned,Centered,RightAligned]!!as a)([LeftAligned,Centered,RightAligned]!!as b)
  "border"->let [l,r]=arr a;[x,y]=arr b in report(\(Border u v)->toJSON[u,v])(Border(as l)(as r))(Border(as x)(as y))
  _->error op
 _->error "pair"
main=do
 hSetBuffering stdout LineBuffering
 B.getContents>>=mapM_(\line->case eitherDecode line of Right req->process req>>=B.putStrLn.encode;Left e->error e).B.lines
