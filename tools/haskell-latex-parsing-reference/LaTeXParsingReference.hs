{-# LANGUAGE OverloadedStrings #-}
module Main where
import Control.Exception as E
import Control.Monad(forM_,replicateM,void)
import Control.Monad.Reader(runReaderT)
import Data.Aeson
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import qualified Data.Map as M
import qualified Data.Set as S
import qualified Data.List.NonEmpty as NE
import qualified Data.Vector as V
import Data.Char(chr,ord)
import Data.Int(Int64)
import Data.List(foldl')
import qualified Data.Text.Encoding as TE
import Data.Default
import Crypto.Hash(SHA256,Digest,hashlazy)
import Data.Binary.Put
import qualified Text.Parsec as C
import qualified Text.Parsec.Pos as Pos
import Text.Pandoc.Sources
import Text.Pandoc.TeX
import Text.Pandoc.Parsing hiding(many)
import Text.Pandoc.Options
import Text.Pandoc.Definition
import qualified Text.Pandoc.Builder as Builder
import Text.Pandoc.Class(runPure,PandocPure)
import Text.Pandoc.Logging
import qualified OriginalParsing as P
import qualified OriginalLaTeXInputs as Original
import System.IO
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success x->x;Error e->error e
arr(Array v)=V.toList v
arr _=error "array"
raw=map(chr.as).arr
text=T.pack.raw
pt=toJSON.map ord.T.unpack
pos v=case arr v of [name,line,col]->Pos.newPos(raw name)(as line)(as col);_->error "position"
pv p=toJSON[toJSON(map ord(Pos.sourceName p)),toJSON(Pos.sourceLine p),toJSON(Pos.sourceColumn p)]
tt v=case arr v of
 [String "CtrlSeq",s]->CtrlSeq(text s)
 [String "Arg",n]->Arg(as n)
 [String "DeferredArg",n]->DeferredArg(as n)
 [String "Spaces"]->Spaces
 [String "Newline"]->Newline
 [String "Symbol"]->Symbol
 [String "Word"]->Word
 [String "Comment"]->Comment
 [String "Esc1"]->Esc1
 [String "Esc2"]->Esc2
 _->error "type"
ttv t=case t of
 CtrlSeq s->toJSON[String "CtrlSeq",pt s]
 Arg n->toJSON[String "Arg",toJSON n]
 DeferredArg n->toJSON[String "DeferredArg",toJSON n]
 _->toJSON[show t]
tok v=case arr v of [p,t,s]->Tok(pos p)(tt t)(text s);_->error "token"
tv(Tok p t s)=toJSON[pv p,ttv t,pt s]
tokens=map tok.arr
tvs=toJSON.map tv
src v=Sources[(pos p,text t)|x<-arr v,let [p,t]=arr x]
quote v=case as v::Int of 0->InSingleQuote;1->InDoubleQuote;_->NoQuote
theorem v=case arr v of [ts,st,se,sy,n,num]->P.TheoremSpec(tokens ts)([P.PlainStyle,P.DefinitionStyle,P.RemarkStyle]!!as st)(as se)(as sy)(as n)(P.DottedNum(as num));_->error "theorem"
thv t=toJSON[tvs(P.theoremName t),toJSON(show(P.theoremStyle t)),toJSON(P.theoremSeries t),toJSON(P.theoremSyncTo t),toJSON(P.theoremNumber t),toJSON(P.renderDottedNum(P.theoremLastNum t))]
macro k n=let token=Tok(Pos.newPos "macro" (-1) 0)(CtrlSeq k) k in Macro GlobalScope ExpandWhenUsed[ArgNum n,Pattern[token]](Just[])[token]
sv s=object
 ["options" .= P.sOptions s,"meta" .= P.sMeta s,"quoteContext" .= show(P.sQuoteContext s)
 ,"macros" .= [[(k,show m)|(k,m)<-M.toAscList scope]|scope<-NE.toList(P.sMacros s)]
 ,"containers" .= P.sContainers s,"logMessages" .= P.sLogMessages s,"identifiers" .= S.toAscList(P.sIdentifiers s)
 ,"verbatimMode" .= P.sVerbatimMode s,"mathMode" .= P.sMathMode s,"caption" .= P.sCaption s
 ,"inListItem" .= P.sInListItem s,"inTableCell" .= P.sInTableCell s
 ,"lastHeaderNum" .= P.renderDottedNum(P.sLastHeaderNum s),"lastFigureNum" .= P.renderDottedNum(P.sLastFigureNum s)
 ,"lastTableNum" .= P.renderDottedNum(P.sLastTableNum s),"lastNoteNum" .= P.sLastNoteNum s
 ,"footnoteTexts" .= [(k,Builder.toList bs)|(k,bs)<-M.toAscList(P.sFootnoteTexts s)]
 ,"theoremMap" .= [(k,thv t)|(k,t)<-M.toAscList(P.sTheoremMap s)],"lastTheoremStyle" .= show(P.sLastTheoremStyle s)
 ,"lastLabel" .= P.sLastLabel s,"labels" .= M.toAscList(P.sLabels s),"hasChapters" .= P.sHasChapters s
 ,"toggles" .= M.toAscList(P.sToggles s),"fileContents" .= M.toAscList(P.sFileContents s),"enableWithRaw" .= P.sEnableWithRaw s
 ,"rawTokens" .= tvs(P.sRawTokens s),"rawTokenCount" .= P.sRawTokenCount s,"rawScopes" .= P.sRawScopes s
 ,"ligatures" .= P.sLigatures s,"caseExclusions" .= [(k,S.toAscList vs)|(k,vs)<-M.toAscList(P.sCaseExclusions s)]
 ,"reportedLogs" .= getLogMessages s]
change s v=case arr v of
 [String "options",x]->s{P.sOptions=as x}
 [String "meta",k,x]->Builder.setMeta(as k)(as x::MetaValue)s
 [String "delete-meta",k]->Builder.deleteMeta(as k)s
 [String "quote",x]->s{P.sQuoteContext=quote x}
 [String "macro",k,n]->updateMacros(M.insert(as k)(macro(as k)(as n)))s
 [String "push-macros"]->s{P.sMacros=NE.cons(NE.head(P.sMacros s))(P.sMacros s)}
 [String "pop-macros"]->s{P.sMacros=maybe(P.sMacros s)id(NE.nonEmpty(NE.tail(P.sMacros s)))}
 [String "clear-macros"]->updateMacros(const M.empty)s
 [String "include",x]->addIncludeFile(as x)s
 [String "drop-include"]->dropLatestIncludeFile s
 [String "log",x]->addLogMessage(SkippedContent(as x)(Pos.newPos "log" 2 3))s
 [String "identifier",x]->updateIdentifierList(S.insert(as x))s
 [String "verbatim",x]->s{P.sVerbatimMode=as x}
 [String "math",x]->s{P.sMathMode=as x}
 [String "caption",x]->s{P.sCaption=as x}
 [String "list-item",x]->s{P.sInListItem=as x}
 [String "table-cell",x]->s{P.sInTableCell=as x}
 [String "header",x]->s{P.sLastHeaderNum=P.DottedNum(as x)}
 [String "figure",x]->s{P.sLastFigureNum=P.DottedNum(as x)}
 [String "table",x]->s{P.sLastTableNum=P.DottedNum(as x)}
 [String "note-num",x]->s{P.sLastNoteNum=as x}
 [String "footnote",k,bs]->s{P.sFootnoteTexts=M.insert(as k)(Builder.fromList(as bs))(P.sFootnoteTexts s)}
 [String "theorem",k,t]->s{P.sTheoremMap=M.insert(as k)(theorem t)(P.sTheoremMap s)}
 [String "theorem-style",x]->s{P.sLastTheoremStyle=[P.PlainStyle,P.DefinitionStyle,P.RemarkStyle]!!as x}
 [String "last-label",x]->s{P.sLastLabel=as x}
 [String "label",k,x]->s{P.sLabels=M.insert(as k)(as x)(P.sLabels s)}
 [String "chapters",x]->s{P.sHasChapters=as x}
 [String "toggle",k,x]->s{P.sToggles=M.insert(as k)(as x)(P.sToggles s)}
 [String "file",k,x]->s{P.sFileContents=M.insert(as k)(as x)(P.sFileContents s)}
 [String "enable-raw",x]->s{P.sEnableWithRaw=as x}
 [String "raw",ts,n,sc]->s{P.sRawTokens=tokens ts,P.sRawTokenCount=as n,P.sRawScopes=as sc}
 [String "ligatures",x]->s{P.sLigatures=as x}
 [String "exclusion",k,x]->s{P.sCaseExclusions=M.insert(as k)(S.fromList(as x))(P.sCaseExclusions s)}
 _->error "change"
state=foldl' change P.defaultLaTeXState.arr
parser :: Value -> P.LP PandocPure Value
parser v=case arr v of
 [String "state"]->sv <$> C.getState
 [String "pure",x]->pure x
 [String "fail"]->fail "failure"
 [String "then",a,b]->parser a *> parser b
 [String "pair",a,b]->(\x y->toJSON[x,y]) <$> parser a <*> parser b
 [String "or",a,b]->parser a C.<|> parser b
 [String "try",a]->C.try(parser a)
 [String "look",a]->C.lookAhead(parser a)
 [String "verbatim",a]->P.withVerbatimMode(parser a)
 [String "disable",a]->P.disablingWithRaw(parser a)
 [String "raw",a]->(\(x,ts)->toJSON[x,tvs ts]) <$> P.withRaw(parser a)
 [String "from",ts,a]->P.parseFromToks(parser a)(tokens ts)
 [String "change",x]->C.modifyState(\s->change s x) *> pure Null
 [String "number",kind]->toJSON . P.renderDottedNum <$> P.getNextNumber([P.sLastHeaderNum,P.sLastFigureNum,P.sLastTableNum]!!as kind)
 [String "reset-caption"]->P.resetCaption *> pure Null
 [String "add-meta",k,x]->P.addMeta(as k)(as x::MetaValue) *> pure Null
 [String "quote",q,a]->withQuoteContext(quote q)(parser a)
 [String "consume",n]->tvs <$> replicateM(as n)(C.anyToken::P.LP PandocPure Tok)
 [String "mark",t]->do
   C.modifyState(\s->s{P.sRawTokens=tok t:P.sRawTokens s,P.sRawTokenCount=P.sRawTokenCount s+1})
   pure Null
 _->error "parser"
run p st ts=case runPure(runReaderT(C.runParserT action st "chunk" (P.TokStream False ts)) P.emptyLaTeXEnv) of
 Left e->error(show e)
 Right(Left err)->object["ok" .= False,"error" .= show err]
 Right(Right(x,s,position,P.TokStream expanded rest))->object["ok" .= True,"value" .= x,"state" .= sv s,"position" .= pv position,"expanded" .= expanded,"rest" .= tvs rest]
 where action=do x<-parser p;s<-C.getState;position<-C.getPosition;rest<-C.getInput;pure(x,s,position,rest)
stream(P.TokStream flag ts)=toJSON[toJSON flag,tvs ts]
observe v=case arr v of
 [String "original-inputs"]->toJSON[(name,pt t)|group<-Original.tests,(name,t)<-group]
 [String "tokens",p,s]->let ts=P.tokenize(pos p)(text s) in toJSON[tvs ts,pt(P.untokenize ts)]
 [String "sources",s]->let ts=P.tokenizeSources(src s) in toJSON[tvs ts,pt(P.untokenize ts)]
 [String "get-input",p,s]->case runPure(C.runParserT (C.setPosition(pos p) >> P.getInputTokens) () "outer" (src s)) of
   Left e->error(show e)
   Right result->let Right ts=result in tvs ts
 [String "untokenize",ts]->pt(P.untokenize(tokens ts))
 [String "stream",a,ts,b,us]->let joined=P.TokStream(as a)(tokens ts)<>P.TokStream(as b)(tokens us) in toJSON[stream joined, toJSON[stream tail|tail<-tails joined]]
 [String "dotted",xs,level,p]->let d=P.DottedNum(as xs);i=P.incrementDottedNum(as level)d in toJSON[toJSON(P.renderDottedNum d),toJSON(P.renderDottedNum i),toJSON(showsPrec(as p)d ""),toJSON(i==d)]
 [String "theorem",a,b]->toJSON(theorem a==theorem b)
 [String "state",changes]->sv(state changes)
 [String "parse",changes,ts,p]->run p(state changes)(tokens ts)
 [String "stress",kind,n]->let
   count=as n::Int;p=Pos.newPos "stress" 1 1
   ts=case as kind::Int of
    0->P.tokenize p(T.replicate count "x ")
    1->P.tokenize p(T.replicate count "\\makeatletter\\x@y\\makeatother\\x@y")
    2->P.tokenizeSources(Sources(replicate count(p,"")++[(p,"x")]))
    3->let P.TokStream _ xs=foldr (<>) mempty (replicate count(P.TokStream True[Tok p Word "x"])) in xs
    4->concat(replicate count[Tok p(CtrlSeq "a")"\\a",Tok p Word "",Tok p Word "b"])
    _->error "stress"
   t=P.untokenize ts
   in toJSON[toJSON(length ts),if null ts then Null else tv(last ts),toJSON(T.length t),toJSON(show(hashlazy(L.fromStrict(TE.encodeUtf8 t))::Digest SHA256))]
 [String "macro-stack",n]->let
   count=as n::Int
   original=updateMacros(M.insert "outer"(macro "outer" 1)) P.defaultLaTeXState
   nested=foldl' (\st _->st{P.sMacros=NE.cons(NE.head(P.sMacros st))(P.sMacros st)})original[1..count]
   changed=updateMacros(M.insert "inner"(macro "inner" 2))nested
   popped=foldl' (\st _->st{P.sMacros=maybe(P.sMacros st)id(NE.nonEmpty(NE.tail(P.sMacros st)))})changed[1..count+1]
   in toJSON[toJSON(length(P.sMacros nested)),toJSON(M.keys(extractMacros changed)),sv popped]
 [String "deep-scopes",n]->let
   p=foldl' (\q _->fst <$> P.withRaw q) (pure Null) [1..(as n::Int)]
   action=do x<-p;s<-C.getState;pure(x,sv s)
   in case runPure(runReaderT(C.runParserT action P.defaultLaTeXState "deep" (P.TokStream False[]))P.emptyLaTeXEnv) of
    Right(Right(x,s))->toJSON[x,s]
    other->error(show other)
 [String "demand",kind]->let
   bad=errorWithoutStackTrace
   p=Pos.newPos "demand" 1 1
   t=P.tokenize (bad "bad-position") "abc"
   in case as kind::Int of
    0->pt(P.untoken(head t))
    1->tvs(P.tokenize(bad "bad-position") "")
    2->tvs(P.tokenizeSources(Sources[(bad "bad-position",""),(p,"x")]))
    3->pt(P.untokenize[Tok p(bad "bad-type") "x"])
    4->pt(P.untokenize(Tok p Word(bad "bad-text"):bad "bad-tail"))
    5->pt(P.untokenize[Tok p(CtrlSeq "x") "\\x",Tok p(bad "bad-type") "y"])
    6->case runPure(C.uncons(P.TokStream False[bad "bad-head"])) of Right(Just _)->Bool True;_->Bool False
    7->let P.TokStream flag _=P.TokStream True[bad "bad-head"]<>P.TokStream False[] in Bool flag
    8->pt(P.untokenize[Tok(bad "bad-position")Word "x",Tok(bad "bad-position")Symbol "y"])
    _->error "demand"
 [String "unicode",start,count]->toJSON(show(hashlazy(runPut $ forM_ [as start..as start+as count-1] $ \cp->do
   let t=T.singleton(chr cp);p=Pos.newPos "unicode" 2 3
       value=toJSON[tvs(P.tokenize p t),tvs(P.tokenize p("\\"<>t)),pt(P.untokenize(P.tokenize p t))]
       bs=encode value
   putWord32be(fromIntegral(L.length bs));putLazyByteString bs)::Digest SHA256))
 _->error "request"
 where
 tails :: P.TokStream -> [P.TokStream]
 tails input=case runPure(C.uncons input) of
   Right(Just(_,rest))->rest:tails rest
   Right Nothing->[]
   Left e->error(show e)
main=do
 hSetBuffering stdout LineBuffering
 let loop=do
       eof<-hIsEOF stdin
       if eof then pure () else do
         line<-B.getLine
         case eitherDecodeStrict line of
           Left e->error e
           Right v->do
             let output=encode(observe v)
             forced<-E.try(E.evaluate(L.length output))::IO(Either E.SomeException Int64)
             case forced of
               Left e->L.putStrLn(encode(object["error" .= takeWhile (/='\n') (E.displayException e)]))
               Right _->L.putStrLn output
         loop
 loop
