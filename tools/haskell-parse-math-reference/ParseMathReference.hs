{-# LANGUAGE OverloadedStrings, FlexibleContexts, ScopedTypeVariables #-}
-- Observation only: the complete original Pandoc library supplies all parsers.
module Main where
import Control.Monad(unless,when)
import Data.Functor.Identity
import Data.Aeson
import qualified Data.Aeson.Key as K
import qualified Data.Aeson.KeyMap as KM
import Data.Char(chr,ord)
import qualified Data.Text as T
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy as BL
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Vector as V
import qualified Text.Parsec as P
import qualified Text.Parsec.Prim as PP
import qualified Text.Parsec.Error as PE
import qualified Text.Parsec.Pos as Pos
import Text.Pandoc.Parsing(HasReaderOptions(..),mathInline,mathDisplay,guardEnabled,guardDisabled)
import qualified Text.Pandoc.Sources as S
import Text.Pandoc.Options
import Text.Pandoc.Shared(trimMath)
import Text.Pandoc.Readers.Native(readNative)
import Text.Pandoc.Class(runPure)
import Data.Default
import Data.Binary.Put
import Crypto.Hash(SHA256,Digest,hashlazy)
import System.Environment(getArgs,withArgs)
import System.IO
import Test.Tasty(defaultMain,testGroup)
import qualified Tests.Readers.HTML as OriginalHTML
import qualified Tests.Readers.Markdown as OriginalMarkdown
import qualified Control.Exception as E
as :: FromJSON a=>Value->a
as v=case fromJSON v of Success x->x;Error e->error e
arr (Array v)=V.toList v
arr _=error "array"
get k (Object o)=maybe (error("missing "++T.unpack k)) id(KM.lookup(K.fromText k)o)
get _ _=error "object"
raw=map(chr.as).arr
text=T.pack.raw
points=toJSON.map ord
pos v=case arr v of [n,l,c]->Pos.newPos(raw n)(as l)(as c);_->error "position"
pv p=toJSON[points(P.sourceName p),toJSON(P.sourceLine p),toJSON(P.sourceColumn p)]
err e=object["position" .= pv(PE.errorPos e),"show" .= show e,"characters" .= points(show e),"messages" .= [[toJSON(fromEnum m),toJSON(PE.messageString m)]|m<-PE.errorMessages e]]
data DefaultState=DefaultState ReaderOptions T.Text Int
instance HasReaderOptions DefaultState where
 extractReaderOptions(DefaultState opts _ _)=opts
data OverrideState=OverrideState ReaderOptions T.Text Int T.Text
instance HasReaderOptions OverrideState where
 extractReaderOptions(OverrideState opts _ _ _)=opts
 getOption f=do
  OverrideState opts sentinel n mode<-P.getState
  when(mode=="consume") $ () <$ P.tokenPrim(const "token")(\p _ _->p) Just
  P.putState(OverrideState opts sentinel (n+1) mode)
  if mode=="fail" then P.parserFail "reader option failure" else pure(f opts)
core :: (P.Stream s Identity Char,S.UpdateSourcePos s Char,HasReaderOptions st)=>Value->P.Parsec s st Value
core v=case arr v of
 [String "inline"]->toJSON <$> mathInline
 [String "display"]->toJSON <$> mathDisplay
 [String "either"]->toJSON <$> (mathDisplay P.<|> mathInline)
 [String "enabled",ext]->String "()" <$ guardEnabled(as ext)
 [String "disabled",ext]->String "()" <$ guardDisabled(as ext)
 [String "option"]->toJSON <$> getOption id
 [String "eof",p]->core p <* P.eof
 [String "fallback",p]->core p P.<|> pure(String "fallback")
 [String "look",p]->P.lookAhead(core p)
 [String "attempt",p]->P.try(core p)
 [String "many",p]->toJSON <$> P.many(core p)
 [String "sequence",ps]->toJSON <$> mapM core(arr ps)
 _->error "parser"
observe :: (P.Stream s Identity Char,S.UpdateSourcePos s Char,HasReaderOptions st)=>Value->s->(s->Value)->P.SourcePos->st->(st->Value)->Value
observe parser input remaining position user userValue=
 let result=runIdentity(PP.runParsecT(core parser)(PP.State input position user))
     (consumed,reply)=case result of PP.Consumed r->(True,runIdentity r);PP.Empty r->(False,runIdentity r)
 in case reply of
  PP.Error e->object["ok" .= False,"consumed" .= consumed,"error" .= err e]
  PP.Ok value state e->object["ok" .= True,"consumed" .= consumed,"value" .= value,"error" .= err e,"position" .= pv(P.statePos state),"remaining" .= remaining(P.stateInput state),"user" .= userValue(P.stateUser state)]
run v=
 let position=pos(get "position" v);mode=as(get "access" v)::T.Text;parser=get "parser" v
     opts=as(get "options" v);sentinel=text(get "sentinel" v);n=as(get "calls" v)
     userValue opts' sentinel' n'=object["options" .= opts',"sentinel" .= sentinel',"calls" .= n']
     withStream input remaining=if mode=="default"
       then observe parser input remaining position (DefaultState opts sentinel n)(\(DefaultState a b c)->userValue a b c)
       else observe parser input remaining position (OverrideState opts sentinel n mode)(\(OverrideState a b c _)->userValue a b c)
     input=get "input" v
 in case as(get "stream" v)::T.Text of
  "text"->withStream(text input)(points.T.unpack)
  "chars"->withStream(raw input)points
  "bytes"->withStream(B.pack(map as(arr input)))(toJSON.B.unpack)
  "lazy-bytes"->withStream(BL.fromChunks[B.pack(map as(arr a))|a<-arr input])(toJSON.map B.unpack.BL.toChunks)
  "sources"->let source=S.Sources[(pos p,text t)|x<-arr input,let[p,t]=arr x]
             in withStream source (\(S.Sources xs)->toJSON[[pv p,points(T.unpack t)]|(p,t)<-xs])
  _->error "stream"
response v=case arr v of
 [String "defaults"]->pure(toJSON(def::ReaderOptions))
 [String "native",s]->pure(either (\e->object["error" .= show e]) toJSON(runPure(readNative def(text s))))
 [String "run",a]->pure(run a)
 [String "trim",s]->pure(toJSON(trimMath(text s)))
 [String "unicode",first,count]->do
  let opts=def{readerExtensions=extensionsFromList[Ext_tex_math_dollars]}
      state=DefaultState opts "" 0
      outcome display n=let s=T.singleton(chr n);input=if display then "$$"<>s<>"$$" else "$"<>s<>"$"
                        in either (T.pack.("L"++).show) ("R"<>)(P.runParser (if display then mathDisplay else mathInline) state "unicode" input)
      outputs=[outcome display n|n<-[as first..as first+as count-1],display<-[False,True]]
      bytes=runPut(mapM_(\t->putWord32be(fromIntegral(T.length t)) >> mapM_(putWord32be.fromIntegral.ord)(T.unpack t))outputs)
  pure(toJSON(show(hashlazy bytes::Digest SHA256)))
 _->error "request"
main=do
 args<-getArgs
 case args of
  ["--upstream-tests"]->withArgs ["--quickcheck-replay","314159"](defaultMain(testGroup "Original HTML reader" OriginalHTML.tests))
  ["--markdown-math-tests"]->withArgs ["--pattern","$2 == \"math groups\""](defaultMain(testGroup "Original Markdown reader" OriginalMarkdown.tests))
  _->loop
 where
 loop=do
  end<-isEOF
  unless end $ do
   line<-B.getLine
   out<-E.try $ do
    value<-response(either error id(eitherDecode(BL.fromStrict line)))
    E.evaluate(BL.length(encode value));pure value
   L.putStrLn(encode(either (\(e::E.SomeException)->object["host" .= show e]) id out));hFlush stdout;loop
