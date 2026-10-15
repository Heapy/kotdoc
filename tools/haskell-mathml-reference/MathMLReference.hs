{-# LANGUAGE OverloadedStrings, ScopedTypeVariables #-}
module Main where
import qualified MathWire as W
import Text.TeXMath.Types
import Text.TeXMath.Writers.MathML
import qualified Text.TeXMath.Readers.MathML.MMLDict as D
import qualified Text.XML.Light as X
import qualified Data.Text as T
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as C
import Data.Aeson
import qualified Data.Vector as V
import Data.Char(chr,ord)
import Data.List(sort)
import Control.Monad(unless)
import qualified Control.Exception as E
import System.IO
import Text.Printf(printf)
import GHC.Float(castDoubleToWord64)
import Numeric(showHex)
import Data.Binary.Put(runPut,putWord32be)
import Crypto.Hash(hashlazy,Digest,SHA256)
as :: FromJSON a=>Value->a
as=W.as
arr=W.arr
str=W.str
chars v=map(chr.as)(arr v)
cp=toJSON.map ord
optional _ Null=Nothing
optional f v=Just(f v)
opt f=maybe Null f
line=optional(read.as)
qn v=case arr v of [a,b,c]->X.QName(chars a)(optional chars b)(optional chars c);_->error "name"
qv n=toJSON[cp(X.qName n),opt cp(X.qURI n),opt cp(X.qPrefix n)]
attribute v=case arr v of [a,b]->X.Attr(qn a)(chars b);_->error "attribute"
av a=toJSON[qv(X.attrKey a),cp(X.attrVal a)]
xml v=case arr v of
 [String "element",a,b,c,d]->X.Elem(X.Element(qn a)(map attribute(arr b))(map xml(arr c))(line d))
 [String "text",a,b,c]->X.Text(X.CData(kind(as a))(chars b)(line c))
 [String "ref",a]->X.CRef(chars a)
 _->error "content"
 where kind "CDataText"=X.CDataText;kind "CDataVerbatim"=X.CDataVerbatim;kind _=X.CDataRaw
xv (X.Elem e)=toJSON[String "element",qv(X.elName e),toJSON(map av(X.elAttribs e)),toJSON(map xv(X.elContent e)),opt(toJSON.show)(X.elLine e)]
xv (X.Text d)=toJSON[String "text",toJSON(show(X.cdVerbatim d)),cp(X.cdData d),opt(toJSON.show)(X.cdLine d)]
xv (X.CRef s)=toJSON[String "ref",cp s]
ov o=object["oper" .= oper o,"description" .= description o,"form" .= show(form o),"priority" .= show(priority o),"left" .= show(lspace o),"right" .= show(rspace o),"properties" .= properties o,"show" .= show o]
formOf v=case as v::String of "FPrefix"->FPrefix;"FPostfix"->FPostfix;_->FInfix
displayOf v=if (as v::String)=="DisplayBlock" then DisplayBlock else DisplayInline
witness out=object["length" .= length out,"sha" .= show(hashlazy(runPut(mapM_(putWord32be.fromIntegral.ord)out))::Digest SHA256)]
xmlResult c=object(["tree" .= xv c,"compact" .= cp(X.showContent c),"pretty" .= cp(X.ppContent c),"long" .= cp(X.ppcContent(X.useShortEmptyTags(const False) X.defaultConfigPP)c)]++case c of X.Elem e->["topRaw" .= cp(X.showTopElement e),"topPretty" .= cp(X.ppTopElement e)];_->[])
response req=case arr req of
 [String "parse",_,_]->W.response req
 [String "mml",dt,xs]->do
  let e=writeMathML(displayOf dt)(map W.expr(arr xs))
  pure(object["tree" .= xv(X.Elem e),"compact" .= witness(X.showElement e),"pretty" .= witness(X.ppElement e),"top" .= witness(X.ppTopElement e),"topRaw" .= witness(X.showTopElement e),"long" .= witness(X.ppcElement(X.useShortEmptyTags(const False) X.defaultConfigPP)e)])
 [String "dictionary-table"]->pure(toJSON(map ov D.operators))
 [String "dictionary",qs]->pure(toJSON[opt ov(D.getMathMLOperator(str s)(formOf f))|q<-arr qs,let[s,f]=arr q])
 [String "dictionary-range",form,start,count]->pure(toJSON[toJSON[toJSON c,ov o]|c<-[as start..as start+as count-1],Just o<-[D.getMathMLOperator(T.singleton(chr c))(formOf form)]])
 [String "numbers",xs]->pure(toJSON[let r=W.rat x;d=fromRational r::Double;b=showHex(castDoubleToWord64 d) "" in toJSON[replicate(16-length b)'0'++b,printf "%.3f" d,show(round(100*r)::Int)]|x<-arr xs])
 [String "xml",v]->pure(xmlResult(xml v))
 [String "config",v,pretty,mode]->do
  let pred n=case as mode::String of "even"->even(length(X.qName n));"named"->X.qName n=="empty";"throw"->if X.qName n=="throw" then error "predicate" else True;_->False
      config=X.useExtraWhiteSpace(as pretty)(X.useShortEmptyTags pred X.defaultConfigPP)
  pure(cp(X.ppcContent config(xml v)))
 [String "names",xs]->do
  let ns=map qn(arr xs)
  pure(object["render" .= map(cp.X.showQName) ns,"ordering" .= [show(compare a b)|a<-ns,b<-ns],"eq" .= [a==b|a<-ns,b<-ns]])
 [String "attributes",xs]->do
  let attrs=map attribute(arr xs)
  pure(object["render" .= map(cp.X.showAttr) attrs,"ordering" .= [show(compare a b)|a<-attrs,b<-attrs],"eq" .= [a==b|a<-attrs,b<-attrs]])
 [String "escape-range",start,count]->do
  let out=X.showCData(X.blank_cdata{X.cdData=map chr[as start..as start+as count-1]})
      bytes=runPut(mapM_(putWord32be.fromIntegral.ord)out)
  pure(object["length" .= length out,"sha" .= show(hashlazy bytes::Digest SHA256)])
 _->error "request"
main=loop where
 loop=do
  end<-isEOF
  unless end $ do
   b<-B.getLine
   result<-E.try $ do
    v<-response(either error id(eitherDecode(BL.fromStrict b)))
    E.evaluate(BL.length(encode v));pure v
   C.putStrLn(encode(either(\(_::E.SomeException)->String "host") id result));hFlush stdout;loop
