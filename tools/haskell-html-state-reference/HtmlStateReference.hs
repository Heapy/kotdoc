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
import Text.Pandoc.Parsing hiding(uri)
import qualified Text.Pandoc.Parsing.Capabilities as C
import qualified Text.Pandoc.Builder as B
import Text.Pandoc.Definition
import Text.Pandoc.Options
import Text.Pandoc.Extensions(readExtension)
import Text.Pandoc.Readers.Native(readNative)
import Text.Pandoc.Logging
import qualified Text.Pandoc.Class as PC
import System.IO
import Control.Monad.Reader(ReaderT,runReaderT,ask,asks,local)
import qualified Network.URI as U
import Text.Pandoc.Readers.HTML.Types
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
raw v=map(chr.as)(arr v)
rawValue s=toJSON(map fromEnum s)
uriValue u=toJSON[rawValue(U.uriScheme u),maybe Null authValue(U.uriAuthority u),rawValue(U.uriPath u),rawValue(U.uriQuery u),rawValue(U.uriFragment u)]
authValue a=toJSON[rawValue(U.uriUserInfo a),rawValue(U.uriRegName a),rawValue(U.uriPort a)]
uri v=case arr v of [s,a,p,q,f]->U.URI(raw s)(if a==Null then Nothing else Just(auth a))(raw p)(raw q)(raw f);_->error "uri"
auth v=case arr v of [u,h,p]->U.URIAuth(raw u)(raw h)(raw p);_->error "authority"
localValue x=toJSON[toJSON(fromEnumQuote(quoteContext x)),toJSON(inChapter x),toJSON(inPlain x),toJSON(inListItem x)]
 where fromEnumQuote InSingleQuote=0::Int;fromEnumQuote InDoubleQuote=1;fromEnumQuote NoQuote=2
localState v=case arr v of [q,c,p,l]->HTMLLocal(quote q)(as c)(as p)(as l);_->error "local"
localChange s v=case arr v of
 [String "quote",q]->s{quoteContext=quote q}
 [String "chapter",x]->s{inChapter=as x}
 [String "plain",x]->s{inPlain=as x}
 [String "list",x]->s{inListItem=as x}
 _->error "local change"
htmlValue s=object["parserState" .= stateValue(parserState s),"notes" .= [(k,B.toList b)|(k,b)<-M.toAscList(noteTable s)]
 ,"notePositions" .= [(k,pv p)|(k,p)<-M.toAscList(noteRefPos s)],"baseHref" .= fmap uriValue(baseHref s)
 ,"identifiers" .= S.toAscList(extractIdentifierList s),"logMessages" .= logMessages s
 ,"macros" .= [(k,show m)|(k,m)<-M.toAscList(extractMacros s)],"readerOpts" .= readerOpts s
 ,"inFootnotes" .= inFootnotes s,"inPre" .= inPre s,"iframeDepth" .= iframeDepth s]
initial options depth=HTMLState def{stateOptions=options} M.empty M.empty Nothing S.empty [] M.empty options False False depth
htmlChange s v=case arr v of
 [String "nested",c]->s{parserState=change(parserState s)c}
 [String "note",k,bs]->s{noteTable=M.insert(as k)(B.fromList(as bs))(noteTable s)}
 [String "note-pos",k,p]->s{noteRefPos=M.insert(as k)(pos p)(noteRefPos s)}
 [String "base",u]->s{baseHref=if u==Null then Nothing else Just(uri u)}
 [String "identifier",x]->updateIdentifierList(S.insert(as x))s
 [String "clear-identifiers"]->updateIdentifierList(const S.empty)s
 [String "macro",k,n]->let macro=stateMacros(change def(toJSON[String "macro",k,n])) M.! as k in updateMacros(M.insert(as k)macro)s
 [String "clear-macros"]->updateMacros(const M.empty)s
 [String "log",x]->addLogMessage(SkippedContent(as x)(Pos.newPos "html-log" 2 3))s
 [String "reader-opts",x]->s{readerOpts=as x}
 [String "options",x]->s{parserState=(parserState s){stateOptions=as x}}
 [String "set-meta",k,x]->B.setMeta(as k)(as x::MetaValue)s
 [String "delete-meta",k]->B.deleteMeta(as k)s
 [String "position",p]->setLastStrPos(if p==Null then Nothing else Just(pos p))s
 [String "footnotes",x]->s{inFootnotes=as x}
 [String "pre",x]->s{inPre=as x}
 [String "depth",x]->s{iframeDepth=as x}
 _->error "html change"
core :: Show t=>(P.SourcePos->t->[t]->P.SourcePos)->Value->P.ParsecT [t] HTMLState (ReaderT HTMLLocal PC.PandocPure) Value
core advance v=case arr v of
 [String "trace"]->do
   c<-ask
   let message=show(quoteContext c)++":"++show(inChapter c)++":"++show(inPlain c)++":"++show(inListItem c)
   PC.report(SkippedContent(T.pack message)(Pos.newPos "trace" 2 3));pure(localValue c)
 [String "ask-ignored"]->const(String "ignored") <$> ask
 [String "quote-ignored"]->const(String "ignored") <$> getQuoteContext
 [String "deep",kind,n]->let
   step p=case as kind::String of
    "map"->id <$> p
    "bind"->p >>= pure
    "then"->pure Null >> p
    "before"->p <* pure Null
    "local"->local id p
    _->error "deep kind"
   in iterate step (localValue <$> ask)!!as n
 [String "pure",x]->pure x
 [String "state"]->htmlValue <$> P.getState
 [String "local"]->localValue <$> ask
 [String "asks-constant"]->asks(const(String "ignored"))
 [String "scope",changes,p]->local(\s->foldl localChange s(arr changes))(go p)
 [String "scope-fail",p]->local(const(error "local environment demanded"))(go p)
 [String "quote"]->toJSON . show <$> getQuoteContext
 [String "with-quote",q,p]->withQuoteContext(quote q)(go p)
 [String "fail-in-quote",q]->C.failIfInQuoteContext(quote q) >> pure Null
 [String "change",x]->P.modifyState(\s->htmlChange s x) >> pure Null
 [String "consume"]->PP.tokenPrim show advance(Just . toJSON . show)
 [String "set-position",p]->P.setPosition(pos p) >> pure Null
 [String "get-last"]->toJSON . fmap pv . getLastStrPos <$> P.getState
 [String "update-last"]->updateLastStrPos >> pure Null
 [String "boundary"]->toJSON <$> notAfterString
 [String "get-options"]->toJSON <$> getOption id
 [String "guard",b,e]->(if as b then guardEnabled else guardDisabled)(readExtension(as e)) >> pure Null
 [String "log",x]->logMessage(SkippedContent(as x)(Pos.newPos "html-log" 2 3)) >> pure Null
 [String "get-logs"]->toJSON . getLogMessages <$> P.getState
 [String "report"]->reportLogMessages >> pure Null
 [String "fail",msg]->P.parserFail(as msg)
 [String "zero"]->P.parserZero
 [String "attempt",p]->P.try(go p)
 [String "look",p]->P.lookAhead(go p)
 [String "label",name,p]->go p P.<?> as name
 [String "or",p,q]->go p P.<|> go q
 [String "sequence",ps]->toJSON <$> mapM go(arr ps)
 [String "before",p,q]->go p <* go q
 [String "many",p]->toJSON <$> P.many(go p)
 [String "many1",p]->toJSON <$> P.many1(go p)
 [String "skip-many",p]->P.skipMany(go p) >> pure Null
 [String "optional",p]->P.optional(go p) >> pure Null
 [String "option",x,p]->P.option x(go p)
 [String "eof"]->P.eof >> pure Null
 [String "nest",n]->if as n==(0::Int) then localValue <$> ask else do
   x<-withQuoteContext(quote(toJSON(as n `mod` 3::Int)))(core advance(toJSON[String "nest",toJSON(as n-1::Int)]))
   P.modifyState(\s->s{iframeDepth=iframeDepth s+1});pure x
 _->error "parser"
 where go=core advance
observe :: Show t=>(P.SourcePos->t->[t]->P.SourcePos)->[t]->HTMLState->P.SourcePos->HTMLLocal->Value->Value
observe advance input user position environment parser=either(error.show)id $ PC.runPure $ runReaderT action environment
 where
 action=do
  c<-PP.runParsecT(core advance parser)(PP.State input position user)
  let finish consumed r=do
       reply<-r
       pure $ case reply of
        PP.Error e->object["ok" .= False,"consumed" .= consumed,"error" .= err e]
        PP.Ok x s e->object["ok" .= True,"consumed" .= consumed,"value" .= x,"position" .= pv(P.statePos s),"remaining" .= map show(P.stateInput s),"user" .= htmlValue(P.stateUser s),"error" .= err e]
  x<-case c of PP.Consumed r->finish True r;PP.Empty r->finish False r
  logs<-PC.getLog;outer<-ask
  pure $ case x of Object fields->Object(KM.insert "environment"(localValue outer)(KM.insert "effects"(toJSON logs)fields));_->error "reply"
response v=case arr v of
 [String "initial",options,depth]->htmlValue(initial(as options)(as depth))
 [String "state",changes]->htmlValue(foldl htmlChange(initial def 0)(arr changes))
 [String "native-state",text]->case PC.runPure(readNative def(as text::T.Text)) of
   Left e->object["nativeError" .= show e]
   Right(Pandoc meta bs)->htmlValue((initial def 0){parserState=def{stateMeta=meta},noteTable=M.singleton "book"(B.fromList bs),noteRefPos=M.singleton "book"(Pos.newPos "native" 1 1)})
 [String "run",stream,input,position,environment,changes,p]->let s=foldl htmlChange(initial def 0)(arr changes) in case as stream::String of
   "characters"->observe (\p c _->Pos.updatePosChar p c)(map(chr.as)(arr input))s(pos position)(localState environment)p
   "tokens"->observe (\p (_::Int) rest->Pos.newPos(P.sourceName p)(P.sourceLine p+length rest)(P.sourceColumn p+1))(as input)s(pos position)(localState environment)p
   _->error "stream"
 [String "uri-unicode",first,count]->let
   outputs=[s|n<-[as first..as first+as count-1],let c=chr n;u=U.URI[c](Just(U.URIAuth['u',c,':',c,'@'][c][':',c]))[c]['?',c]['#',c],s<-[show u,U.uriToString id u "",show(U.URIAuth[c][c][c])]]
   bytes=runPut(mapM_(\s->putWord32be(fromIntegral(length s)) >> mapM_(putWord32be.fromIntegral.fromEnum)s)outputs)
   in toJSON(show(hashlazy bytes::Digest SHA256))
 [String "uri",u,p]->let x=uri u in object["parts" .= uriValue x,"show" .= rawValue(showsPrec(as p)x ""),"full" .= rawValue(U.uriToString id x ""),"authShow" .= fmap(\a->showsPrec(as p)a "")(U.uriAuthority x),"rectified" .= uriValue(U.rectify x),"absolute" .= U.uriIsAbsolute x,"relative" .= U.uriIsRelative x]
 [String "uri-compare",u,v]->let x=uri u;y=uri v in object["compare" .= show(compare x y),"equal" .= (x==y),"min" .= uriValue(min x y),"max" .= uriValue(max x y)]
 [String "uri-map",mode,u,suffix]->let f=case as mode::String of "identity"->id;"empty"->const "";"constant"->const "mapped";"fail"->const(error "user info demanded");_->error "mapping" in rawValue(U.uriToString f(uri u)(raw suffix))
 _->error "request"
main=loop where
 loop=do
  done<-isEOF
  if done then pure() else do
   line<-L.pack <$> getLine
   result<-E.try $ case eitherDecode line of Left e->error e;Right v->E.evaluate(force(response v))
   L.putStrLn $ case result of Left(e::SomeException)->encode(object["exception" .= takeWhile(/='\n')(displayException e)]);Right b->b
   hFlush stdout;loop
 force x=let b=encode x in BL.length b `seq` b
