{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, NoMonomorphismRestriction #-}
module Main where
import Skylighting.Core
import qualified Text.XML as X
import qualified Data.Text.Lazy as TL
import qualified System.FilePath.Windows as W
import System.FilePath((</>),takeExtension)
import qualified System.Directory as D
import Control.Monad(forM,filterM)
import System.IO.Temp(withSystemTempDirectory)
import Data.Int(Int64)
import Text.Read(readMaybe)
import qualified Data.Binary as Bin
import Data.Aeson
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import qualified Data.IntMap.Strict as IM
import qualified Data.Map.Strict as M
import qualified Data.Set as S
import qualified Data.Vector as V
import Data.Char(ord,chr)
import Control.Exception(SomeException,try,evaluate,displayException)
import Numeric(readHex)

as v=case fromJSON v of Success a->a;Error e->error e
arr (Array v)=V.toList v
arr _=error "array"
j=toJSON
hex = T.pack . concatMap (\w -> ["0123456789abcdef"!!fromIntegral(w `div` 16),"0123456789abcdef"!!fromIntegral(w `mod` 16)]) . B.unpack
fromHex s=B.pack(go(T.unpack s)) where
 go []=[]
 go (a:b:rest)=case readHex[a,b] of [(n,"")]->n:go rest;_->error "hex"
 go _=error "hex"
re v=case arr v of [s,c,m]->RE(fromHex(as s))(as c)(as m);_->error "re"
jre x=j[ j(hex(reString x)),j(reCaseSensitive x),j(reMinimal x)]
kw v=case arr v of [c,d]->KeywordAttr(as c)(S.fromList(map (chr.as)(arr d)));_->error "kw"
jkw (KeywordAttr c d)=j[j c,j(map ord(S.toList d))]
ws v=case arr v of [c,ss]->if as c then CaseSensitiveWords(S.fromList(as ss)) else CaseInsensitiveWords(S.fromList(as ss));_->error "ws"
jws w=case w of CaseSensitiveWords ss->j[j True,j(S.toList ss)];CaseInsensitiveWords ss->j[j False,j(S.toList ss)]
ctx v=as v
match v=case arr v of
 [String "DetectChar",c]->DetectChar(chr(as c))
 [String "Detect2Chars",c,d]->Detect2Chars(chr(as c))(chr(as d))
 [String "AnyChar",cs]->AnyChar(S.fromList(map(chr.as)(arr cs)))
 [String "RangeDetect",c,d]->RangeDetect(chr(as c))(chr(as d))
 [String "StringDetect",s]->StringDetect(as s)
 [String "WordDetect",s]->WordDetect(as s)
 [String "RegExpr",r]->RegExpr(re r)
 [String "Keyword",k,Array w]->case V.toList w of
   [String "Left",s]->Keyword(kw k)(Left(as s))
   [String "Right",s]->Keyword(kw k)(Right(ws s))
   _->error "words"
 [String "Int"]->Int
 [String "Float"]->Float
 [String "HlCOct"]->HlCOct
 [String "HlCHex"]->HlCHex
 [String "HlCStringChar"]->HlCStringChar
 [String "HlCChar"]->HlCChar
 [String "LineContinue",c]->LineContinue(chr(as c))
 [String "IncludeRules",c]->IncludeRules(ctx c)
 [String "DetectSpaces"]->DetectSpaces
 [String "DetectIdentifier"]->DetectIdentifier
 _->error "matcher"
jmatch x=j(case x of
 DetectChar c->[j("DetectChar"::String),j(ord c)]
 Detect2Chars c d->[j("Detect2Chars"::String),j(ord c),j(ord d)]
 AnyChar cs->[j("AnyChar"::String),j(map ord(S.toList cs))]
 RangeDetect c d->[j("RangeDetect"::String),j(ord c),j(ord d)]
 StringDetect s->[j("StringDetect"::String),j s]
 WordDetect s->[j("WordDetect"::String),j s]
 RegExpr r->[j("RegExpr"::String),jre r]
 Keyword k w->[j("Keyword"::String),jkw k,either(\s->j[j("Left"::String),j s])(\s->j[j("Right"::String),jws s])w]
 Int->[j("Int"::String)];Float->[j("Float"::String)];HlCOct->[j("HlCOct"::String)];HlCHex->[j("HlCHex"::String)]
 HlCStringChar->[j("HlCStringChar"::String)];HlCChar->[j("HlCChar"::String)]
 LineContinue c->[j("LineContinue"::String),j(ord c)]
 IncludeRules c->[j("IncludeRules"::String),j c]
 DetectSpaces->[j("DetectSpaces"::String)];DetectIdentifier->[j("DetectIdentifier"::String)])
switch v=case arr v of []->Pop;[c]->Push(ctx c);_->error "switch"
jswitch Pop=j([]::[Value]);jswitch(Push c)=j[j c]
item v=case arr v of [String "Item",s]->Item(as s);[String "IncludeList",c]->IncludeList(ctx c);_->error "item"
jitem(Item s)=j[j("Item"::String),j s];jitem(IncludeList c)=j[j("IncludeList"::String),j c]
rule v=case arr v of
 [m,a,i,w,d,c,k,l,f,n,s]->Rule(match m)(read(as a))(as i)(S.fromList(map(chr.as)(arr w)))(as d)(as c)(map rule(arr k))(as l)(as f)(as n)(map switch(arr s))
 _->error "rule"
jrule(Rule m a i w d c k l f n s)=j[jmatch m,j(show a),j i,j(map ord(S.toList w)),j d,j c,j(map jrule k),j l,j f,j n,j(map jswitch s)]
context v=case arr v of
 [n,s,r,a,e,l,f,c,d]->Context(as n)(as s)(map rule(arr r))(read(as a))(map switch(arr e))(map switch(arr l))(as f)(map switch(arr c))(as d)
 _->error "context"
jcontext(Context n s r a e l f c d)=j[j n,j s,j(map jrule r),j(show a),j(map jswitch e),j(map jswitch l),j f,j(map jswitch c),j d]
mapfrom :: (Value -> a) -> Value -> M.Map T.Text a
mapfrom f v=M.fromList[(as k,f x) | row<-arr v,let [k,x]=arr row]
jmap f m=j[j[j k,f v]|(k,v)<-M.toList m]
syntax v=case arr v of
 [n,f,s,l,c,a,v,li,e,st]->Syntax(as n)(as f)(as s)(mapfrom(map item.arr)l)(mapfrom context c)(as a)(as v)(as li)(as e)(as st)
 _->error "syntax"
jsyntax(Syntax n f s l c a v li e st)=j[j n,j f,j s,jmap(j.map jitem)l,jmap jcontext c,j a,j v,j li,j e,j st]

result x=case x of Left e->object["error" .= e];Right s->object["syntax" .= jsyntax s,"show" .= show s]
jname (X.Name l n p)=j[j l,j n,j p]
jn (X.NodeElement el)=j[j("element"::String),jel el]
jn (X.NodeContent t)=j[j("text"::String),j t]
jn (X.NodeComment t)=j[j("comment"::String),j t]
jn (X.NodeInstruction i)=j[j("instruction"::String),ji i]
ji (X.Instruction t d)=j[j t,j d]
jmisc (X.MiscComment t)=j[j("comment"::String),j t]
jmisc (X.MiscInstruction i)=j[j("instruction"::String),ji i]
jeid (X.SystemID s)=j[j("system"::String),j s]
jeid (X.PublicID p s)=j[j("public"::String),j p,j s]
jdoctype (X.Doctype n eid)=j[j n,j(fmap jeid eid)]
jel (X.Element n attrs nodes)=j[jname n,j[[jname key,j value]|(key,value)<-M.toList attrs],j(map jn nodes)]
jdoc (X.Document (X.Prologue before dt after) root epi)=j[j(map jmisc before),j(fmap jdoctype dt),j(map jmisc after),jel root,j(map jmisc epi)]
process (op,v)=case op::String of
 "haskell-string"->pure(j(readMaybe(as v)::Maybe String))
 "document-chunks"->pure(case X.parseText X.def(TL.fromChunks(map T.pack(as v))) of Left e->object["error" .= displayException e];Right d->object["document" .= jdoc d])
 "document"->pure(case X.parseText X.def(TL.pack(as v)) of Left e->object["error" .= displayException e];Right d->object["document" .= jdoc d])
 "syntax-chunks"->case arr v of [p,ts]->pure(result(parseSyntaxDefinitionFromText(as p)(TL.fromChunks(map T.pack(as ts)))));_->error "syntax chunks"
 "syntax"->case arr v of [p,t]->pure(result(parseSyntaxDefinitionFromText(as p)(TL.pack(as t))));_->error "syntax"
 "syntax-windows"->case arr v of [p,t]->pure(result(parseSyntaxDefinitionFromText(W.takeFileName(as p))(TL.pack(as t))));_->error "syntax windows"
 "bytes"->withSystemTempDirectory "kotdoc-syntax-native" $ \dir->do
   let path=dir </> "input.xml"
   B.writeFile path(fromHex(as v))
   parseSyntaxDefinition path >>= pure.result
 "load"->withSystemTempDirectory "kotdoc-syntax-native" $ \dir->do
   let path=dir </> "input.xml"
   B.writeFile path(fromHex(as v))
   x<-loadSyntaxFromFile path
   pure(case x of Left e->object["error" .= T.replace(T.pack path)"<file>"(T.pack e)];Right s->object["syntax" .= jsyntax s,"show" .= show s])
 "directory"->withSystemTempDirectory "kotdoc-syntax-native" $ \dir->do
   mapM_ (\row->case arr row of
     [p,Null]->D.createDirectoryIfMissing True(dir </> as p)
     [p,bytes]->do
       let path=dir </> as p
       B.writeFile path(fromHex(as bytes))
     _->error "directory file") (arr v)
   entries<-D.listDirectory dir
   paths<-filterM D.doesFileExist[dir </> n|n<-entries,takeExtension n==".xml"]
   strict<-loadSyntaxesFromDir dir
   (errors,valid)<-loadValidSyntaxesFromDir dir
   let replace=T.replace(T.pack dir)"<dir>"
       sm m=jmap (j.show)m
   pure(object["files" .= map (replace.T.pack)paths,
     "strict" .= either (\e->object["error" .= replace(T.pack e)]) (\m->object["map" .= sm m])strict,
     "errors" .= [[j(replace(T.pack p)),j(replace(T.pack e))]|(p,e)<-M.toList errors],"valid" .= sm valid])
 _->error op
main=L.getContents >>= mapM_ respond . L.lines where
 respond line=do
  let request=either error id(eitherDecode line :: Either String (String,Value))
  r<-try(process request >>= evaluate.L.toStrict.encode)::IO(Either SomeException B.ByteString)
  either (L.putStrLn.encode.object.(:[]).("exception" .=).head.lines.displayException)(L.putStrLn.L.fromStrict) r
