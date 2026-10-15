{-# LANGUAGE OverloadedStrings, ScopedTypeVariables, FlexibleContexts #-}
-- Observation only; state operations and parsing come from original Pandoc.
module Main where
import Control.Monad(void,when)
import Control.Exception as E
import Data.Aeson hiding(Key)
import qualified Text.Pandoc.TeX as TeX
import qualified Data.Aeson.KeyMap as KM
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.ByteString.Lazy as BL
import Data.Binary.Put
import Crypto.Hash(SHA256,Digest,hashlazy)
import qualified Data.Text as T
import qualified Data.Map as M
import qualified Data.Set as S
import Data.Default
import Data.Char(chr)
import qualified Data.Vector as V
import qualified Text.Parsec as P
import qualified Text.Parsec.Prim as PP
import qualified Text.Parsec.Error as PE
import qualified Text.Parsec.Pos as Pos
import Text.Pandoc.Parsing
import qualified Text.Pandoc.Parsing.Capabilities as C
import qualified Text.Pandoc.Builder as B
import Text.Pandoc.Definition
import Text.Pandoc.Options
import Text.Pandoc.Extensions(readExtension)
import Text.Pandoc.Readers.Native(readNative)
import Text.Pandoc.Logging
import qualified Text.Pandoc.Class as PC
import System.IO
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success x->x;Error e->error e
arr(Array v)=V.toList v
arr _=error "array"
pv p=toJSON[toJSON(map fromEnum(P.sourceName p)),toJSON(P.sourceLine p),toJSON(P.sourceColumn p)]
pos v=case arr v of [n,l,c]->Pos.newPos(map(chr.as)(arr n))(as l)(as c);_->error "pos"
quote v=case as v::Int of 0->InSingleQuote;1->InDoubleQuote;2->NoQuote;_->error "quote"
keytext(Key t)=t
stateValue s=object
 ["options" .= stateOptions s,"parserContext" .= show(stateParserContext s),"quoteContext" .= show(stateQuoteContext s)
 ,"allowLinks" .= stateAllowLinks s,"allowLineBreaks" .= stateAllowLineBreaks s,"lastStrPos" .= fmap pv(stateLastStrPos s)
 ,"keys" .= [(keytext k,toJSON v)|(k,v)<-M.toAscList(stateKeys s)],"headerKeys" .= [(keytext k,toJSON v)|(k,v)<-M.toAscList(stateHeaderKeys s)]
 ,"substitutions" .= [(keytext k,B.toList v)|(k,v)<-M.toAscList(stateSubstitutions s)]
 ,"notes" .= stateNotes s,"parsedNotes" .= [(k,pv p,B.toList(runF f s))|(k,(p,f))<-M.toAscList(stateNotes' s)]
 ,"noteRefs" .= S.toAscList(stateNoteRefs s),"inNote" .= stateInNote s,"noteNumber" .= stateNoteNumber s
 ,"meta" .= stateMeta s,"futureMeta" .= runF(stateMeta' s)s,"citations" .= M.toAscList(stateCitations s)
 ,"headerTable" .= map show(stateHeaderTable s),"identifiers" .= S.toAscList(extractIdentifierList s)
 ,"nextExample" .= stateNextExample s,"examples" .= M.toAscList(stateExamples s),"macros" .= [(k,show m)|(k,m)<-M.toAscList(extractMacros s)]
 ,"rstDefaultRole" .= stateRstDefaultRole s,"rstHighlight" .= stateRstHighlight s,"rstCustomRoles" .= M.toAscList(stateRstCustomRoles s)
 ,"caption" .= fmap B.toList(stateCaption s),"inHtmlBlock" .= stateInHtmlBlock s,"fencedDivLevel" .= stateFencedDivLevel s
 ,"containers" .= stateContainers s,"logMessages" .= stateLogMessages s,"markdownAttribute" .= stateMarkdownAttribute s]
change s v=case arr v of
 [String "options",x]->s{stateOptions=as x}
 [String "quote",q]->s{stateQuoteContext=quote q}
 [String "context",b]->s{stateParserContext=if as b then ListItemState else NullState}
 [String "allow-links",b]->s{stateAllowLinks=as b}
 [String "allow-line-breaks",b]->s{stateAllowLineBreaks=as b}
 [String "position",p]->setLastStrPos(if p==Null then Nothing else Just(pos p))s
 [String "key",k,t,a]->s{stateKeys=M.insert(toKey(as k))(as t,as a)(stateKeys s)}
 [String "header-key",k,t,a]->s{stateHeaderKeys=M.insert(toKey(as k))(as t,as a)(stateHeaderKeys s)}
 [String "substitution",k,bs]->s{stateSubstitutions=M.insert(toKey(as k))(B.fromList(as bs))(stateSubstitutions s)}
 [String "note",k,t]->s{stateNotes=stateNotes s++[(as k,as t)]}
 [String "parsed-note",k,p,bs]->s{stateNotes'=M.insert(as k)(pos p,pure(B.fromList(as bs)))(stateNotes' s)}
 [String "parsed-note-state",k,p]->s{stateNotes'=M.insert(as k)(pos p,B.plain . B.str . stateRstDefaultRole <$> askF)(stateNotes' s)}
 [String "note-ref",k]->s{stateNoteRefs=S.insert(as k)(stateNoteRefs s)}
 [String "in-note",b]->s{stateInNote=as b}
 [String "note-number",n]->s{stateNoteNumber=as n}
 [String "set-meta",k,x]->B.setMeta(as k)(as x::MetaValue)s
 [String "delete-meta",k]->B.deleteMeta(as k)s
 [String "future-meta",m]->s{stateMeta'=pure(as m)}
 [String "future-meta-state"]->s{stateMeta'=stateMeta <$> askF}
 [String "citation",k,t]->s{stateCitations=M.insert(as k)(as t)(stateCitations s)}
 [String "header",b,c]->s{stateHeaderTable=stateHeaderTable s++[if as b then DoubleHeader(chr(as c)) else SingleHeader(chr(as c))]}
 [String "identifier",x]->updateIdentifierList(S.insert(as x))s
 [String "clear-identifiers"]->updateIdentifierList(const S.empty)s
 [String "next-example",n]->s{stateNextExample=as n}
 [String "example",k,n]->s{stateExamples=M.insert(as k)(as n)(stateExamples s)}
 [String "macro",k,n]->let token=TeX.Tok(Pos.newPos "macro" (-1) 0)(TeX.CtrlSeq(as k))(as k);macro=TeX.Macro TeX.GlobalScope TeX.ExpandWhenUsed [TeX.ArgNum(as n),TeX.Pattern[token]](Just[])[token] in updateMacros(M.insert(as k)macro)s
 [String "clear-macros"]->updateMacros(const M.empty)s
 [String "role",x]->s{stateRstDefaultRole=as x}
 [String "highlight",x]->s{stateRstHighlight=as x}
 [String "custom-role",k,x]->s{stateRstCustomRoles=M.insert(as k)(as x)(stateRstCustomRoles s)}
 [String "caption",x]->s{stateCaption=if x==Null then Nothing else Just(B.fromList(as x))}
 [String "html-block",x]->s{stateInHtmlBlock=as x}
 [String "fenced-level",n]->s{stateFencedDivLevel=as n}
 [String "include",x]->addIncludeFile(as x)s
 [String "drop-include"]->dropLatestIncludeFile s
 [String "log",x]->addLogMessage(SkippedContent(as x)(Pos.newPos "log" 2 3))s
 [String "markdown",b]->s{stateMarkdownAttribute=as b}
 _->error "change"
err e=object["position" .= pv(PE.errorPos e),"characters" .= map fromEnum(show e),"messages" .= [[toJSON(fromEnum m),toJSON(PE.messageString m)]|m<-PE.errorMessages e]]
core :: Show t=>(P.SourcePos->t->[t]->P.SourcePos)->Value->P.ParsecT [t] ParserState PC.PandocPure Value
core advance v=case arr v of
 [String "nest-quotes",n]->if as n==(0::Int) then toJSON . show <$> getQuoteContext else do
   x<-withQuoteContext(quote(toJSON(as n `mod` 3::Int)))(core advance(toJSON[String "nest-quotes",toJSON(as n-1::Int)]))
   P.modifyState(\s->s{stateNextExample=stateNextExample s+1});pure x
 [String "pure",x]->pure x
 [String "state"]->stateValue <$> P.getState
 [String "change",x]->P.modifyState(\s->change s x) >> pure Null
 [String "consume"]->PP.tokenPrim show advance(Just . toJSON . show)
 [String "set-position",p]->P.setPosition(pos p) >> pure Null
 [String "update-last"]->updateLastStrPos >> pure Null
 [String "boundary"]->toJSON <$> notAfterString
 [String "get-quote"]->toJSON . show <$> getQuoteContext
 [String "with-quote",q,p]->withQuoteContext(quote q)(go p)
 [String "fail-in-quote",q]->C.failIfInQuoteContext(quote q) >> pure Null
 [String "get-columns"]->toJSON <$> getOption readerColumns
 [String "guard",b,e]->(if as b then guardEnabled else guardDisabled)(readExtension(as e)) >> pure Null
 [String "log",x]->logMessage(SkippedContent(as x)(Pos.newPos "log" 2 3)) >> pure Null
 [String "get-logs"]->toJSON . getLogMessages <$> P.getState
 [String "report"]->reportLogMessages >> pure Null
 [String "get-includes"]->toJSON . getIncludeFiles <$> P.getState
 [String "fail",msg]->P.parserFail(as msg)
 [String "zero"]->P.parserZero
 [String "attempt",p]->P.try(go p)
 [String "look",p]->P.lookAhead(go p)
 [String "label",name,p]->go p P.<?> as name
 [String "or",p,q]->go p P.<|> go q
 [String "sequence",ps]->toJSON <$> mapM go(arr ps)
 [String "before",p,q]->go p <* go q
 _->error "parser"
 where go=core advance
observe :: Show t=>(P.SourcePos->t->[t]->P.SourcePos)->[t]->ParserState->P.SourcePos->Value->Value
observe advance input user position parser=either(error.show)id $ PC.runPure $ do
 c<-PP.runParsecT(core advance parser)(PP.State input position user)
 let finish consumed action=do
      r<-action
      pure $ case r of
       PP.Error e->object["ok" .= False,"consumed" .= consumed,"error" .= err e]
       PP.Ok x s e->object["ok" .= True,"consumed" .= consumed,"value" .= x,"position" .= pv(P.statePos s),"remaining" .= map show(P.stateInput s),"user" .= stateValue(P.stateUser s),"error" .= err e]
 x<-case c of PP.Consumed a->finish True a;PP.Empty a->finish False a
 logs<-PC.getLog
 pure $ case x of Object fields->Object(KM.insert "effects"(toJSON logs)fields);_->error "reply"
data Override = Override ReaderOptions ReaderOptions Int String
instance HasReaderOptions Override where
 extractReaderOptions(Override original _ _ _)=original
 getOption select=do
  Override original alternate calls mode<-P.getState
  P.putState(Override original alternate(calls+1)mode)
  when(mode=="consume" || mode=="consume-fail")(void(PP.tokenPrim(const "")(\p _ _->p)Just))
  when(mode=="fail" || mode=="consume-fail")(P.parserFail "getter failed")
  pure(select alternate)
overrideValue(Override original alternate calls mode)=object["original" .= original,"alternate" .= alternate,"calls" .= calls,"mode" .= mode]
overrideRun original alternate mode wrapper input=case PC.runPure $ do
 c<-PP.runParsecT action(PP.State input(Pos.newPos "override" 1 1)(Override original alternate 0 mode))
 let finish consumed r=do
      reply<-r
      pure $ case reply of
       PP.Error e->object["ok" .= False,"consumed" .= consumed,"error" .= err e]
       PP.Ok x state e->object["ok" .= True,"consumed" .= consumed,"value" .= x,"user" .= overrideValue(P.stateUser state),"remaining" .= (P.stateInput state::[Int]),"error" .= err e]
 case c of PP.Consumed r->finish True r;PP.Empty r->finish False r
 of Left e->error(show e);Right x->x
 where
 base=case wrapper of
  "enabled"->guardEnabled(readExtension "smart") >> pure Null
  "disabled"->guardDisabled(readExtension "smart") >> pure Null
  _->toJSON <$> getOption readerColumns
 action=case wrapper of
  "fallback"->base P.<|> pure(String "fallback")
  "attempt-fallback"->P.try base P.<|> pure(String "fallback")
  _->base
response v=case arr v of
 [String "override",original,alternate,mode,wrapper,input]->overrideRun(as original)(as alternate)(as mode)(as wrapper)(as input)
 [String "native-state",text]->case PC.runPure(readNative def(as text::T.Text)) of
   Left e->object["nativeError" .= show e]
   Right(Pandoc meta bs)->let s=def{stateMeta=meta,stateMeta'=stateMeta <$> askF,stateSubstitutions=M.singleton(toKey "[BOOK]")(B.fromList bs),stateNotes'=M.singleton "book"(Pos.newPos "native" 1 1,pure(B.fromList bs))} in stateValue s
 [String "state",changes]->stateValue(foldl change def(arr changes))
 [String "header",b,c,p]->toJSON(showsPrec(as p)(if as b then DoubleHeader(chr(as c)) else SingleHeader(chr(as c))) "")
 [String "key",s,p]->let k=toKey(as s) in object["text" .= keytext k,"show" .= showsPrec(as p)k ""]
 [String "compare",a,b]->let x=Key(as a);y=Key(as b) in toJSON[show(compare x y),show(x==y)]
 [String "read",s,p]->toJSON[(keytext k,map fromEnum rest)|(k,rest)<-(readsPrec(as p)(map(chr.as)(arr s))::[(Key,String)])]
 [String "read-list",s]->toJSON[([keytext k|k<-ks],map fromEnum rest)|(ks,rest)<-(readList(map(chr.as)(arr s))::[([Key],String)])]
 [String "run",stream,input,position,changes,p]->let s=foldl change def(arr changes) in case as stream::String of
   "characters"->observe (\p c _->Pos.updatePosChar p c)(map(chr.as)(arr input))s(pos position)p
   "tokens"->observe (\p (_::Int) rest->Pos.newPos(P.sourceName p)(P.sourceLine p+length rest)(P.sourceColumn p+1))(as input)s(pos position)p
   _->error "stream"
 [String "unicode",first,count]->let
   outputs=[keytext(toKey(T.singleton(chr n)<>" A "<>T.singleton(chr n)))|n<-[as first..as first+as count-1]]
   bytes=runPut(mapM_(\t->putWord32be(fromIntegral(T.length t)) >> mapM_(putWord32be.fromIntegral.fromEnum)(T.unpack t))outputs)
   in toJSON(show(hashlazy bytes::Digest SHA256))
 _->error "request"
main=loop where
 loop=do
  done<-isEOF
  if done then pure() else do
   line<-L.pack <$> getLine
   result<-E.try $ case eitherDecode line of Left e->error e;Right v->E.evaluate(force(response v))
   L.putStrLn $ case result of Left(e::SomeException)->encode(object["host" .= displayException e]);Right b->b
   hFlush stdout;loop
 force x=let b=encode x in BL.length b `seq` b
