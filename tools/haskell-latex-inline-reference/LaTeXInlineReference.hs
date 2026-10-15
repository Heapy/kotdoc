{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified CitationMathObserver as C
import qualified KernelObserver as K
import qualified StateObserver as S
import qualified OriginalParsing as P
import qualified OriginalMacro as M
import qualified OriginalLang as LL
import qualified OriginalSIunitx as SI
import qualified OriginalInline as I
import qualified Data.Text.Normalize as N
import qualified Unicode.Char.Normalization as UN
import qualified Data.ByteString as Bytes
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
import Control.Monad.Trans.Class (lift)
import qualified Control.Exception as E
import Data.Aeson
import qualified Data.ByteString.Char8 as BS
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Map as Map
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import Data.List (foldl')
import Data.Char (chr,ord)
import Data.Binary.Put
import Text.Pandoc.Walk (walk)
import Data.Int (Int64)
import Data.String (fromString)
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
     let commands=Map.unions[Map.unions [entries k tok|k<-kinds],LL.enquoteCommands tok,LL.inlineLanguageCommands tok,SI.siunitxCommands tok,CC.citationCommands rich]
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
kinds=["acronym","verb","char","accent","misc","name","biblatex","ref"]
entries kind tok=case kind of
 "acronym"->I.acronymCommands
 "verb"->I.verbCommands
 "char"->I.charCommands
 "accent"->I.accentCommands tok
 "misc"->I.miscCommands
 "name"->I.nameCommands
 "biblatex"->I.biblatexInlineCommands tok
 "ref"->I.refCommands
 _->Map.empty
fileTree ps fs=foldl' (\tree item->let [path,bytes]=K.arr item in insertInFileTree (T.unpack(K.text path))(FileInfo(stTime ps)(Bytes.pack(map K.as(K.arr bytes)))) tree) mempty(K.arr fs)
setFiles fs=do ps<-getPureState;putPureState ps{stFiles=fileTree ps fs}
parser v=case K.arr v of
 [String "entry",kind,name,cb]->C.ils $ Map.findWithDefault Q.parserZero(K.text name)(entries(K.as kind)(inline cb))
 [String "accent-direct",mark,fallback,cb]->C.ils $ I.accentWith(inline cb)(chr(K.as mark))(optional(chr.K.as)fallback)
 [String "raw-inline",name,cb]->C.ils $ I.rawInlineOr(K.text name)(inline cb)
 [String "roman"]->toJSON <$> I.romanNumeralArg
 [String "files",fs]->Null <$ lift(lift(setFiles fs))
 [String "select",value]->Null <$ lift(lift(TR.setTranslations(lang value)))
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
run cs cv fs ts expr = case runPure $ do
 putCommonState(seed cv)
 setFiles fs
 result<-runReaderT(Q.runParserT action (foldl S.change P.defaultLaTeXState(K.arr cs)) "chunk" (P.TokStream False(K.tokens ts))) P.emptyLaTeXEnv
 shared<-getCommonState
 pure(result,common shared)
 of
 Left e->object["pandocError" .= show e]
 Right(Left e,shared)->object["ok" .= False,"error" .= show e,"common" .= shared]
 Right(Right(x,s,p,P.TokStream flag rest),shared)->object["ok" .= True,"value" .= x,"state" .= S.sv s,"position" .= K.pv p,"expanded" .= flag,"rest" .= K.tvs rest,"common" .= shared]
 where action=do x<-parser expr;s<-Q.getState;p<-Q.getPosition;rest<-Q.getInput;pure(x,s,p,rest)
observe v=case K.arr v of
 [String "registries"]->toJSON[(k,Map.keys(entries k rich))|k<-kinds]
 [String "listings",options]->toJSON(I.listingsLanguage[(K.text k,K.text v)|item<-K.arr options,let[k,v]=K.arr item])
 [String "parsed-lang",value]->case fromString(T.unpack(K.text value)) of
   Lang a b c ds es fs->toJSON[K.pt a,maybe Null K.pt b,maybe Null K.pt c,toJSON(map K.pt ds),toJSON[(K.pt k,[(K.pt x,K.pt y)|(x,y)<-vs])|(k,vs)<-es],toJSON(map K.pt fs)]
 [String "nfc-data",pairs,starterPairs]->object
   ["classes" .= [(cp,UN.combiningClass(chr cp))|cp<-[0..0x10ffff],UN.combiningClass(chr cp)/=0]
   ,"combining" .= [cp|cp<-[0..0x10ffff],UN.isCombining(chr cp)]
   ,"starters" .= [cp|cp<-[0..0x10ffff],UN.isCombiningStarter(chr cp)]
   ,"decompositions" .= [(cp,map ord(UN.decompose UN.Canonical(chr cp)))|cp<-[0..0x10ffff],UN.isDecomposable UN.Canonical(chr cp)]
   ,"compositions" .= [let[a,b]=map K.as(K.arr pair) in [a,b,maybe (-1) ord(UN.compose(chr a)(chr b))]|pair<-K.arr pairs]
   ,"starterCompositions" .= [let[a,b]=map K.as(K.arr pair) in [a,b,maybe (-1) ord(UN.composeStarters(chr a)(chr b))]|pair<-K.arr starterPairs]]
 [String "normalize",value]->K.pt(N.normalize N.NFC(K.text value))
 [String "parse",cs,cv,fs,ts,p]->run cs cv fs ts p
 [String "unicode-nfc",start,count,marks]->toJSON(show(hashlazy(runPut $ mapM_ (\cp->mapM_ (\m->do
   let output=TE.encodeUtf8(N.normalize N.NFC(T.pack[chr cp,chr(K.as m)]))
   putWord32be(fromIntegral(BS.length output));putByteString output) (K.arr marks)) [K.as start..K.as start+K.as count-1])::Digest SHA256))
 [String "stress",kind,n]->stress(K.as kind)(K.as n)
 _->C.observe v
stress :: Int -> Int -> Value
stress kind n=let
 body=foldl' (\xs _->[Emph xs]) [Str "x"] [1..n]
 source=case kind of
  0->"|"<>T.replicate n "abc"<>"|tail"
  1->"{Python}|"<>T.replicate n "a\nb"<>"|tail"
  2->"{123}tail"
  3->T.replicate n "a"
  _->""
 p=case kind of
  0->I.verbCommands Map.! "verb"
  1->I.verbCommands Map.! "mintinline"
  2->I.accentWith (pure(B.fromList body)) (chr 769) Nothing
  3->I.accentWith (P.anyTok *> pure(B.str(T.replicate n "e"))) (chr 769) Nothing
  _->error "stress kind"
 action=do out<-p;s<-Q.getState;position<-Q.getPosition;rest<-Q.getInput;pure(show(B.toList out),s,position,rest)
 in case runPure $ do
  putCommonState(seed Null)
  result<-runReaderT(Q.runParserT action P.defaultLaTeXState "stress" (P.TokStream False(P.tokenize(Pos.newPos "stress" 1 1)source))) P.emptyLaTeXEnv
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
