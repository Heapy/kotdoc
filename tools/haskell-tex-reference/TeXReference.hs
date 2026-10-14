{-# LANGUAGE OverloadedStrings #-}
-- Observation transport only. Both upstream modules compile unchanged.
module Main where
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Map as M
import qualified Data.Vector as V
import qualified Data.Text as T
import Data.Char (chr,ord)
import Data.Functor.Identity
import Text.Pandoc.TeX
import qualified Text.Pandoc.Sources as S
import qualified Text.Parsec as P
import qualified Text.Parsec.Pos as PP
import System.IO
import Control.Monad (unless)
import Control.Exception

arr (Array x)=V.toList x
arr x=error(show x)
as :: FromJSON a => Value -> a
as v=case fromJSON v of Success x->x;Error x->error x
get k (Object o)=as (Object o) M.! k :: Value
get _ v=error(show v)
pack v=T.pack(map chr(as v::[Int]))
pos v=case arr v of [n,l,c]->PP.newPos(as n)(as l)(as c);_->error(show v)
position p=toJSON [toJSON(P.sourceName p),toJSON(P.sourceLine p),toJSON(P.sourceColumn p)]
toktype v=case arr v of
 [String "CtrlSeq",t]->CtrlSeq(pack t)
 [String "Spaces"]->Spaces
 [String "Newline"]->Newline
 [String "Symbol"]->Symbol
 [String "Word"]->Word
 [String "Comment"]->Comment
 [String "Esc1"]->Esc1
 [String "Esc2"]->Esc2
 [String "Arg",n]->Arg(as n)
 [String "DeferredArg",n]->DeferredArg(as n)
 _->error(show v)
token v=case arr v of [p,t,s]->Tok(pos p)(toktype t)(pack s);_->error(show v)
tokens=map token.arr
defaults Null=Nothing
defaults v=Just(tokens v)
spec v=case arr v of
 [String "ArgNum",n]->ArgNum(as n)
 [String "Pattern",ts]->Pattern(tokens ts)
 [String "BoolArg",b,t]->BoolArg(as b)(token t)
 [String "DelimArg",b,t,u,def]->DelimArg(as b)(token t)(token u)(defaults def)
 [String "VerbArg"]->VerbArg
 [String "EmbellishArg",es]->EmbellishArg [(token t,defaults d)|e<-arr es,let [t,d]=arr e]
 [String "ProcessedArg",ps,arg]->ProcessedArg(map tokens(arr ps))(spec arg)
 [String "BodyArg",b,n]->BodyArg(as b)(as n)
 [String "VerbBodyArg",b,n]->VerbBodyArg(as b)(as n)
 _->error(show v)
-- Enum types deliberately derive no Read; transport constructors are explicit.
scope v=case as v::String of "GlobalScope"->GlobalScope;"GroupScope"->GroupScope;_->error(show v)
expansion v=case as v::String of "ExpandWhenDefined"->ExpandWhenDefined;"ExpandWhenUsed"->ExpandWhenUsed;_->error(show v)
mkMacro v=case arr v of [s,e,args,d,ts]->Macro(scope s)(expansion e)(map spec(arr args))(defaults d)(tokens ts);_->error(show v)
relations :: (Eq a,Ord a) => a -> a -> Value
relations a b=object ["eq" .= (a==b),"ne" .= (a/=b),"compare" .= show(compare a b),
 "lt" .= (a<b),"le" .= (a<=b),"gt" .= (a>b),"ge" .= (a>=b)]
encSource (S.Sources xs)=toJSON [toJSON [position p,toJSON(map ord(T.unpack t))]|(p,t)<-xs]
source v=let s=S.toSources(tokens v) in object ["chunks" .= encSource s,"text" .= S.sourcesToText s,"name" .= S.initialSourceName s,"show" .= show s]
showValue p typ v=case as typ::String of
 "type"->showsPrec p(toktype v)""
 "token"->showsPrec p(token v)""
 "scope"->showsPrec p(scope v)""
 "expansion"->showsPrec p(expansion v)""
 "spec"->showsPrec p(spec v)""
 "macro"->showsPrec p(mkMacro v)""
 _->error(show typ)
partial kind count=let p=PP.initialPos "first";t=Tok p Word "abc";other=Tok(PP.initialPos "next") Symbol "!"
 in case kind of
 "type-tag-eq"->toJSON(CtrlSeq(error "unused text") == Spaces)
 "type-tag-ord"->relations(CtrlSeq(error "unused text")) Spaces
 "type-arg-demand"->toJSON(Arg(error "demanded number") == Arg 1)
 "type-self-demand"->let a=CtrlSeq(error "demanded text") in toJSON(a==a)
 "token-position-eq"->toJSON(Tok(PP.initialPos "a")(error "unused type")(error "unused text") == Tok(PP.initialPos "b")(error "unused type")(error "unused text"))
 "token-position-ord"->relations (Tok(PP.initialPos "a")(error "unused type")(error "unused text")) (Tok(PP.initialPos "b")(error "unused type")(error "unused text"))
 "token-type-eq"->toJSON(Tok p Spaces(error "unused text") == Tok p Word(error "unused text"))
 "token-type-ord"->relations (Tok p Spaces(error "unused text")) (Tok p Word(error "unused text"))
 "source-ignore-types"->toJSON(S.sourcesToText(S.toSources [Tok p(error "unused type")"a",Tok p(error "unused type")"b"]))
 "source-single-ignore-pos"->toJSON(S.sourcesToText(S.toSources [Tok(error "unused pos")(error "unused type")"a"]))
 "source-initial-ignore-tail"->toJSON(S.initialSourceName(S.toSources(Tok p(error "unused type")(error "unused text"):error "unused tail")))
 "source-initial-ignore-fields"->toJSON(S.initialSourceName(S.toSources [Tok p(error "unused type")(error "unused text")]))
 "source-count-ignore-text"->toJSON(length(S.unSources(S.toSources [Tok p(error "unused type")(error "unused text"),other,Tok p(error "unused type")(error "unused text")])))
 "source-text-before-next-pos"->toJSON(S.sourcesToText(S.toSources [Tok p Word(error "first text"),Tok(error "next pos") Word "b"]))
 "source-two-text-before-third-pos"->toJSON(S.sourcesToText(S.toSources [Tok p Word(error "first text"),Tok p Word "b",Tok(error "third pos") Word "c"]))
 "source-single-text-before-tail"->toJSON(S.sourcesToText(S.toSources(Tok p Word(error "first text"):error "token tail")))
 "source-name-demand-token"->toJSON(S.initialSourceName(S.toSources [error "demanded token"::Tok]))
 "prefix-type-text"->toJSON(take count(show(CtrlSeq(error "demanded text"))))
 "prefix-type-number"->toJSON(take count(show(Arg(error "demanded number"))))
 "prefix-token-fields"->toJSON(take count(show(Tok(error "demanded pos")(error "unused type")(error "unused text"))))
 "prefix-pattern-list"->toJSON(take count(show(Pattern(error "demanded list"))))
 "prefix-pattern-head"->toJSON(take count(show(Pattern [error "demanded token"::Tok])))
 "prefix-maybe"->toJSON(take count(show(Macro GlobalScope ExpandWhenDefined [] (Just(error "demanded default")) [])))
 "prefix-macro-scope"->toJSON(take count(show(Macro(error "demanded scope")(error "unused expansion")(error "unused args")(error "unused default")(error "unused replacement"))))
 "infinite-processed"->let a=ProcessedArg [] a in toJSON(take count(show a))
 "infinite-token-list"->toJSON(take count(show(Pattern(repeat t))))
 "infinite-source-name"->toJSON(S.initialSourceName(S.toSources(repeat t)))
 "infinite-source-groups"->toJSON [P.sourceName p| (p,_)<-take count(S.unSources(S.toSources(cycle [t,other])))]
 _->error kind
operation [String "show",typ,p,v]=toJSON(showValue(as p)typ v)
operation [String "prefix",typ,p,n,v]=toJSON(take(as n)(showValue(as p)typ v))
operation [String "relations",String "type",a,b]=relations(toktype a)(toktype b)
operation [String "relations",String "token",a,b]=relations(token a)(token b)
operation [String "relations",String "scope",a,b]=relations(scope a)(scope b)
operation [String "relations",String "expansion",a,b]=relations(expansion a)(expansion b)
operation [String "sources",ts]=source ts
operation [String "partial",String k,n]=partial(T.unpack k)(as n)
operation [String "group-text",n,ft,fp]=let ts=[Tok (if i==as fp then error ("position:"++show i) else PP.initialPos "same") Word (if i==as ft then error ("text:"++show i) else "x")|i<-[0..as n-1]]
                                      in case S.unSources(S.toSources ts) of []->Null;(_,t):_->toJSON t
operation [String "plain",n,ft,fp,fs]=toJSON(S.sourcesToText(S.Sources(input 0)))
 where
  input i | i==as fs = error ("spine:"++show i)
          | i==as n = []
          | otherwise = (if i==as fp then error ("pair:"++show i) else (error "unused pos",if i==as ft then error ("text:"++show i) else "x")):input(i+1)
operation [String "priority",n,j]=toJSON(S.sourcesToText(S.toSources [Tok (if i==as j then error ("position:"++show i) else PP.initialPos "same") Word (if i==0 then error "first text" else "x")|i<-[0..as n-1]]))
operation [String "deep",n]=let a=iterate (ProcessedArg []) VerbArg !! as n in toJSON(show a)
operation v=error(show v)
main=do
 hSetBuffering stdout LineBuffering
 let loop=do
       done<-isEOF
       unless done $ do
         line<-B.pack <$> getLine
         let request=case eitherDecode line of Right x->x;Left e->error e
         reply<-try(evaluate(let b=encode(operation(arr request)) in B.length b `seq` b))::IO(Either SomeException B.ByteString)
         B.putStrLn(case reply of Right b->b;Left e->encode(object ["exception" .= takeWhile (/='\n')(displayException e)]))
         loop
 loop
