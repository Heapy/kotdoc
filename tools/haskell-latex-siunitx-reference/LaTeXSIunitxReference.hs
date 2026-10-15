{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified CitationMathObserver as C
import qualified KernelObserver as K
import qualified StateObserver as S
import qualified OriginalParsing as P
import qualified OriginalMacro as M
import qualified OriginalLang as LL
import qualified OriginalSIunitx as SI
import qualified OriginalCitation as CC
import qualified Text.Pandoc.Builder as B
import Text.Pandoc.Definition
import Text.Pandoc.TeX
import Text.Pandoc.Class
import Text.Pandoc.Parsing (withQuoteContext)
import Text.Pandoc.Logging (LogMessage(..), Verbosity(..))
import Text.Pandoc.MediaBag (mediaDirectory)
import Text.Pandoc.Class.CommonState
import qualified Text.Pandoc.Translations as TR
import Text.Collate.Lang (Lang(..),renderLang)
import qualified Text.Parsec as Q
import qualified Text.Parsec.Pos as Pos
import qualified Text.Parsec.Error as PE
import Control.Applicative ((<|>))
import Control.Monad (replicateM)
import Control.Monad.Reader (runReaderT)
import qualified Control.Exception as E
import Data.Aeson
import qualified Data.ByteString.Char8 as BS
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Map as Map
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import Data.List (foldl')
import Data.Char (chr)
import Data.Binary.Put
import Text.Pandoc.Walk (walk)
import Data.Int (Int64)
import Data.Maybe (isJust)
import System.IO
import Crypto.Hash (Digest,SHA256,hashlazy)

optional _ Null=Nothing
optional f x=Just(f x)
lang v=case K.arr v of
 [l,s,r,vs,es,ps]->Lang(K.text l)(optional K.text s)(optional K.text r)(map K.text(K.arr vs))[(K.text k,[(K.text a,K.text b)|pair<-K.arr vals,let[a,b]=K.arr pair])|item<-K.arr es,let[k,vals]=K.arr item](map K.text(K.arr ps))
 _->error "lang"
langValue l=toJSON[show l,T.unpack(renderLang l)]
cache Null=Nothing
cache v=case K.arr v of [l,t]->Just(lang l,optional K.as t);_->error "cache"
cacheValue s=maybe Null (\(l,t)->toJSON[langValue l,maybe Null (String . T.pack . show) t])(stTranslations s)
seed value=defaultCommonState{stTranslations=cache value,stLog=[NoLangSpecified],stUserDataDir=Just "user",stSourceURL=Just "https://example.test/source",stRequestHeaders=[("X-Test","yes")],stNoCheckCertificate=True,stInputFiles=["input"],stOutputFile=Just "output",stResourcePath=["assets","."],stVerbosity=ERROR}
common s=object["cache" .= cacheValue s,"log" .= stLog s,"user" .= stUserDataDir s,"source" .= stSourceURL s,"headers" .= stRequestHeaders s,"noCheckCertificate" .= stNoCheckCertificate s,"inputs" .= stInputFiles s,"output" .= stOutputFile s,"resources" .= stResourcePath s,"verbosity" .= show(stVerbosity s),"trace" .= stTrace s,"managerPresent" .= isJust(stManager s),"mediaEmpty" .= null(mediaDirectory(stMediaBag s))]

-- A component callback, not the complete readLaTeX document grammar.
rich :: P.LP PandocPure B.Inlines
rich=M.macroDef(B.rawInline "fixture-definition") <|> Q.try command <|> atom
 where
  tok=P.tokWith rich
  command=do
   Tok _ (CtrlSeq name) _<-P.anyControlSeq
   if name=="emph" then B.emph <$> tok
    else if name=="textbf" then B.strong <$> tok
    else if name=="setdefaultlanguage" then mempty <$ LL.setDefaultLanguage
    else do
     star<-Q.option False(True <$ P.symbol '*')
     let commands=Map.unions[LL.enquoteCommands tok,LL.inlineLanguageCommands tok,SI.siunitxCommands tok,CC.citationCommands rich]
     Map.findWithDefault (fail "unknown callback command") (name<>if star then "*" else "") commands
  atom=P.grouped rich <|> do
   Tok _ kind txt<-P.satisfyTok(\(Tok _ k t)->not(k==Symbol && (t=="}" || t=="{")))
   pure $ case kind of Spaces->B.space;Newline->B.softbreak;Comment->mempty;_->B.str txt
inline v=case K.arr v of
 [String "atom"]->rich
 [String "sequence"]->mconcat <$> Q.many rich
 [String "tok"]->P.tokWith rich
 [String "quote"]->B.str . T.pack . show . P.sQuoteContext <$> Q.getState
 [String "bracket",x]->P.bracketed(inline x)
 _->C.inline v
parser v=case K.arr v of
 [String "si",name,cb]->C.ils $ Map.findWithDefault Q.parserZero(K.text name)(SI.siunitxCommands(inline cb))
 [String "si-unit",options,cb]->C.ils $ SI.siUnit [(K.text k,K.text v)|x<-K.arr options,let[k,v]=K.arr x] (inline cb)
 [String "default"]->C.bs LL.setDefaultLanguage
 [String "enquote",name,cb]->C.ils $ Map.findWithDefault Q.parserZero(K.text name)(LL.enquoteCommands(inline cb))
 [String "language",name,cb]->C.ils $ Map.findWithDefault Q.parserZero(K.text name)(LL.inlineLanguageCommands(inline cb))
 [String "rich"]->C.ils(mconcat <$> Q.many rich)
 [String "then",a,b]->parser a >> parser b
 [String "pair",a,b]->do x<-parser a;y<-parser b;pure(toJSON[x,y])
 [String "or",a,b]->parser a <|> parser b
 [String "try",p]->Q.try(parser p)
 [String "look",p]->Q.lookAhead(parser p)
 [String "raw",p]->do(x,ts)<-P.withRaw(parser p);pure(toJSON[x,K.tvs ts])
 [String "from",ts,p]->P.parseFromToks(parser p)(K.tokens ts)
 [String "verbatim",p]->P.withVerbatimMode(parser p)
 [String "disable",p]->P.disablingWithRaw(parser p)
 [String "quote",ctx,p]->withQuoteContext(S.quote ctx)(parser p)
 [String "many",p]->toJSON <$> Q.many(parser p)
 [String "repeat",n,p]->toJSON <$> replicateM(K.as n)(parser p)
 _->C.parser v
run cs cv ts expr = case runPure $ do
 putCommonState(seed cv)
 result<-runReaderT(Q.runParserT action (foldl S.change P.defaultLaTeXState(K.arr cs)) "chunk" (P.TokStream False(K.tokens ts))) P.emptyLaTeXEnv
 shared<-getCommonState
 pure(result,common shared)
 of
 Left e->object["pandocError" .= show e]
 Right(Left e,shared)->object["ok" .= False,"error" .= show e,"common" .= shared]
 Right(Right(x,s,p,P.TokStream flag rest),shared)->object["ok" .= True,"value" .= x,"state" .= S.sv s,"position" .= K.pv p,"expanded" .= flag,"rest" .= K.tvs rest,"common" .= shared]
 where action=do x<-parser expr;s<-Q.getState;p<-Q.getPosition;rest<-Q.getInput;pure(x,s,p,rest)
observe v=case K.arr v of
 [String "registries"]->toJSON[Map.keys(SI.siunitxCommands rich),Map.keys SI.siUnitModifierMap,Map.keys SI.siUnitMap]
 [String "maps"]->toJSON[[(k,B.toList v)|(k,v)<-Map.toAscList SI.siUnitModifierMap],[(k,B.toList v)|(k,v)<-Map.toAscList SI.siUnitMap]]
 [String "tonum",value]->toJSON(B.toList(SI.tonum(K.text value)))
 [String "parse-num",value]->number(K.text value)
 [String "walk",value]->toJSON(walk SI.hyphenToMinus(K.as value::[Inline]))
 [String "hyphen",value]->toJSON(SI.hyphenToMinus(K.as value))
 [String "empty-space",value]->toJSON(B.toList(SI.emptyOr160(B.fromList(K.as value))))
 [String "unicode",start,count]->toJSON(show(hashlazy(runPut $ mapM_ (\cp->do
   let output=TE.encodeUtf8(T.pack(show(B.toList(SI.tonum(T.singleton(chr cp))))))
   putWord32be(fromIntegral(BS.length output));putByteString output) [K.as start..K.as start+K.as count-1])::Digest SHA256))
 [String "parse",cs,cv,ts,p]->run cs cv ts p
 [String "stress",kind,n]->stress(K.as kind)(K.as n)
 _->C.observe v
number value=case Q.runParser action () "" value of
 Left e->object["ok" .= False,"error" .= show e,"position" .= K.pv(PE.errorPos e),"messages" .= [(fromEnum m,PE.messageString m)|m<-PE.errorMessages e]]
 Right(out,pos,rest)->object["ok" .= True,"value" .= out,"position" .= K.pv pos,"rest" .= K.pt rest]
 where action=do x<-SI.parseNum;position<-Q.getPosition;rest<-Q.getInput;pure(B.toList x,position,rest)
stress :: Int -> Int -> Value
stress kind n=let
 initial=P.defaultLaTeXState
 body=foldl' (\xs _->[Emph xs]) [Str "x-y"] [1..n]
 source=case kind of
  1->T.replicate n "\\kilo"<>"\\metre"
  2->"m\\tothe{X}"
  3->"m"<>T.replicate n "\\per m"
  _->""
 p=case kind of
  0->pure(SI.tonum("1."<>T.replicate n "2"<>"(1)"))
  1->SI.siUnit [] (fail "no callback")
  2->SI.siUnit [] (P.braced *> pure(B.fromList body))
  3->SI.siUnit [] (fail "no callback")
  _->error "stress kind"
 action=do out<-p;s<-Q.getState;position<-Q.getPosition;rest<-Q.getInput;pure(show(B.toList out),s,position,rest)
 in case runPure $ do
  putCommonState(seed Null)
  result<-runReaderT(Q.runParserT action initial "stress" (P.TokStream False(P.tokenize(Pos.newPos "stress" 1 1)source))) P.emptyLaTeXEnv
  shared<-getCommonState
  pure(result,common shared)
 of
  Right(Right(out,s,p,P.TokStream flag rest),shared)->object["hash" .= show(hashlazy(L.fromStrict(TE.encodeUtf8(T.pack out)))::Digest SHA256),"state" .= S.sv s,"position" .= K.pv p,"expanded" .= flag,"rest" .= K.tvs rest,"common" .= shared]
  other->error(show other)
main=do
 hSetBuffering stdout LineBuffering
 let loop=do
      eof<-hIsEOF stdin
      if eof then pure () else do
       line<-BS.getLine
       let output=encode(observe(either error id(eitherDecodeStrict line)))
       result<-E.try(E.evaluate(L.length output))::IO(Either E.SomeException Int64)
       case result of
        Left e->L.putStrLn(encode(object["exception" .= takeWhile(/='\n')(E.displayException e)]))
        Right _->L.putStrLn output
       loop
 loop
