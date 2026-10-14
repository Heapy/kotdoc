{-# LANGUAGE OverloadedStrings, BangPatterns #-}
module Main where
import Regex.KDE
import qualified Regex.KDE.Regex as R
import OriginalRegexTests
import Data.Aeson
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.ByteString.Base64 as B64
import qualified Data.ByteString.Builder as BB
import qualified Data.ByteString.UTF8 as U
import qualified Data.Text as T
import qualified Data.Text.Encoding as E
import qualified Data.Text.Encoding.Error as EE
import qualified Data.IntMap.Strict as M
import qualified Data.Vector as V
import Data.Char
import Numeric(readHex)
import Control.Exception(SomeException,try,evaluate,displayException)
import System.Environment(getArgs)
fromHex s = B.pack $ go (T.unpack s) where
 go []=[]
 go (a:b:rest)=case readHex [a,b] of [(n,"")]->n:go rest;_->error "hex"
 go _=error "hex"
hex = T.pack . concatMap (\w -> ["0123456789abcdef"!!fromIntegral(w `div` 16),"0123456789abcdef"!!fromIntegral(w `mod` 16)]) . B.unpack
as v=case fromJSON v of Success a->a;Error e->error e
arr (Array v)=V.toList v
arr _=error "array"
utf8 bs=object["decoded" .= U.decode bs,"text" .= U.toString bs]
result _ Nothing=Null
result bytes (Just(m,cs))=object["bytes" .= hex m,"captures" .= [[toJSON n,toJSON off,toJSON len,toJSON(hex(B.take len(B.drop off bytes))),toJSON(U.toString(B.take len(B.drop off bytes)))] | (n,(off,len))<-M.toList cs]]
process (op,v)=case op::String of
 "original" -> object["tests" .= [object["regex" .= re,"input" .= inp,"expected" .= expected,"actual" .= testRegex True re inp] | (re,inp,expected)<-regexTests],
                      "errors" .= [object["regex" .= re,"actual" .= either (\e->object["error" .= e]) (\r->object["show" .= show r]) (compileRegex True False(U.fromString re))] | re<-regexErrorTests]]
 "compile" -> case arr v of
  [pattern,cs,minimal,subjects] -> let bs=fromHex(as pattern) in case compileRegex(as cs)(as minimal) bs of
   Left e->object["error" .= e]
   Right re->let groups=extractCapturingGroups re in object["show" .= show re,"groups" .= [[toJSON n,toJSON(show r)] | (n,r)<-M.toList groups],
     "matches" .= [result bytes(matchRegex re bytes) | input<-arr subjects,let bytes=fromHex(as input)],
     "cachedMatches" .= [result bytes(matchRegexWithGroups groups re bytes) | input<-arr subjects,let bytes=fromHex(as input)]]
  _->error "compile args"
 "compile-compact" -> case arr v of
  [pattern,cs,minimal,subjects] -> let bs=fromHex(as pattern) in case compileRegex(as cs)(as minimal) bs of
   Left e->object["error" .= e]
   Right re->let groups=extractCapturingGroups re in object["flat" .= flatBytes re,"groups" .= [[toJSON n,toJSON(flatBytes r)] | (n,r)<-M.toList groups],
     "matches" .= [result bytes(matchRegex re bytes) | input<-arr subjects,let bytes=fromHex(as input)],
     "cachedMatches" .= [result bytes(matchRegexWithGroups groups re bytes) | input<-arr subjects,let bytes=fromHex(as input)]]
  _->error "compile compact args"
 "ast" -> case arr v of
  [node,provided,input] ->
    let re=ast node
        groups=if provided==Null then extractCapturingGroups re else M.fromList[(as n,ast r) | row<-arr provided,let [n,r]=arr row]
        bytes=fromHex(as input)
    in object["show" .= show re,"groups" .= [[toJSON n,toJSON(show r)] | (n,r)<-M.toList groups],"match" .= result bytes(matchRegexWithGroups groups re bytes)]
  _->error "ast args"
 "utf8" -> toJSON[utf8(fromHex(as h)) | h<-arr v]
 "text-decode" -> toJSON[T.unpack(E.decodeUtf8With EE.lenientDecode(fromHex(as h))) | h<-arr v]
 "chars" -> case arr v of
  [start,end] ->
    let fields c=[fromIntegral(fromEnum(generalCategory c)),fromIntegral(ord(toLower c)),fromIntegral(ord(toUpper c)),flags c]
        builder=mconcat[BB.word32LE w | n<-[as start..as end-1],w<-fields(chr n)]
    in String(E.decodeUtf8(B64.encode(L.toStrict(BB.toLazyByteString builder))))
  _->error "chars"
 "predicates" -> case arr v of
  [pattern,cs,start,end] -> case compileRegex(as cs)False(fromHex(as pattern)) of
    Left e->error e
    Right re->let ps=predicates re in object["count" .= length ps,"bytes" .= E.decodeUtf8(B64.encode(B.pack[if f(chr n) then 1 else 0 | n<-[as start..as end-1],f<-ps]))]
  _->error "predicate args"
 _->error("operation "++op)
flatBytes = E.decodeUtf8 . B64.encode . L.toStrict . encode . flat
flat regex=go [regex] where
 go []=[]
 go (r:rs)=let (tag,children)=describe r in toJSON tag:go(children++rs)
 describe r=case r of
               MatchAnyChar->([toJSON("any"::String)],[])
               MatchDynamic n->([toJSON("dyn"::String),toJSON n],[])
               MatchChar _->([toJSON("char"::String)],[])
               MatchSome x->([toJSON("some"::String)],[x])
               MatchAlt x y->([toJSON("alt"::String)],[x,y])
               MatchConcat x y->([toJSON("concat"::String)],[x,y])
               MatchCapture n x->([toJSON("cap"::String),toJSON n],[x])
               MatchCaptured n cs->([toJSON("ref"::String),toJSON n,toJSON cs],[])
               AssertWordBoundary->([toJSON("boundary"::String)],[])
               AssertBeginning->([toJSON("start"::String)],[])
               AssertEnd->([toJSON("end"::String)],[])
               AssertPositive d x->([toJSON("positive"::String),toJSON(show d)],[x])
               AssertNegative d x->([toJSON("negative"::String),toJSON(show d)],[x])
               Possessive x->([toJSON("poss"::String)],[x])
               Lazy x->([toJSON("lazy"::String)],[x])
               Subroutine n->([toJSON("sub"::String),toJSON n],[])
               MatchNull->([toJSON("null"::String)],[])
ast v=case arr v of
 [String "any"]->MatchAnyChar
 [String "null"]->MatchNull
 [String "start"]->AssertBeginning
 [String "end"]->AssertEnd
 [String "boundary"]->AssertWordBoundary
 [String "char",n]->MatchChar(==chr(as n))
 [String "sat",String name]->MatchChar(case name of "word"->isWordChar;"alpha"->isAlpha;"digit"->isDigit;"space"->isSpace;_->const True)
 [String "dyn",n]->MatchDynamic(as n)
 [String "ref",n,cs]->MatchCaptured(as n)(as cs)
 [String "sub",n]->Subroutine(as n)
 [String "cap",n,r]->MatchCapture(as n)(ast r)
 [String "concat",a,b]->MatchConcat(ast a)(ast b)
 [String "alt",a,b]->MatchAlt(ast a)(ast b)
 [String "some",r]->MatchSome(ast r)
 [String "lazy",r]->Lazy(ast r)
 [String "poss",r]->Possessive(ast r)
 [String "positive",d,r]->AssertPositive(direction d)(ast r)
 [String "negative",d,r]->AssertNegative(direction d)(ast r)
 _->error "node"
 where direction (String "backward")=R.Backward
       direction _=R.Forward
predicates re=case re of
 MatchChar f->[f]
 MatchConcat x y->predicates x++predicates y
 MatchAlt x y->predicates x++predicates y
 MatchSome x->predicates x
 MatchCapture _ x->predicates x
 R.AssertPositive _ x->predicates x
 R.AssertNegative _ x->predicates x
 R.Possessive x->predicates x
 R.Lazy x->predicates x
 _->[]
flags c=sum[n | (p,n)<-[(isAlpha,1),(isAlphaNum,2),(isSpace,4),(isDigit,8),(isLower,16),(isUpper,32),(isPrint,64),(isPunctuation,128),(isSymbol,256),(isControl,512),(isWordChar,1024),(isHorizSpace,2048),(isAscii,4096),(isHexDigit,8192)],p c]
isHorizSpace c =
  c == '\t' || c == ' ' || c == '\xA0' || c == '\x1680' || c == '\x180E' ||
  (c >= '\x2000' && c <= '\x200A') || c == '\x202F' || c == '\x205F' ||
  c == '\x3000'
main = do
 args<-getArgs
 if args==["--original-tests"] then do
   let bad=[(re,inp) | (re,inp,expected)<-regexTests,testRegex True re inp/=expected]
       accepted=[re | re<-regexErrorTests,either (const False)(const True)(compileRegex True False(U.fromString re))]
   if null bad && null accepted then print(length regexTests,length regexErrorTests) else error(show(bad,accepted))
 else L.getContents >>= mapM_ respond . L.lines
 where respond line=do
        let request=either error id(eitherDecode line)
        r<-try(evaluate(L.toStrict(encode(process request))))::IO(Either SomeException B.ByteString)
        either (L.putStrLn . encode . object . (:[]) . ("exception" .=) . displayException) (L.putStrLn . L.fromStrict) r
