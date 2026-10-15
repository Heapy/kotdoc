{-# LANGUAGE OverloadedStrings #-}
module CitationMathObserver where
import qualified KernelObserver as K
import qualified StateObserver as S
import qualified OriginalParsing as P
import qualified OriginalMacro as M
import qualified OriginalCitation as C
import qualified OriginalMath as MT
import qualified OriginalLaTeXInputs as Inputs
import qualified Text.Pandoc.Builder as B
import Text.Pandoc.Definition
import Text.Pandoc.TeX
import Text.Pandoc.Class (PandocPure,runPure,getLog)
import Text.Pandoc.Walk (walk)
import qualified Text.Parsec as Q
import Control.Applicative ((<|>))
import Control.Monad (forM_,replicateM)
import Control.Monad.Reader (runReaderT)
import qualified Control.Exception as E
import Data.Aeson
import qualified Data.ByteString.Char8 as BS
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Map as Map
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import qualified Text.Parsec.Pos as Pos
import Data.List (foldl')
import Data.Int (Int64)
import System.IO
import Crypto.Hash (Digest,SHA256,hashlazy)

-- Explicit callback contracts. This is not a replacement document reader.
rich :: P.LP PandocPure B.Inlines
rich = M.macroDef (B.rawInline "fixture-definition") <|> Q.try command <|> atom
 where
  command = do
   Tok _ (CtrlSeq name) _ <- P.anyControlSeq
   if name=="emph" then B.emph <$> P.tokWith rich
    else if name=="textbf" then B.strong <$> P.tokWith rich
    else do
     starred<-Q.option False (True <$ P.symbol '*')
     Map.findWithDefault (fail "unknown callback command") (name<>if starred then "*" else "") (C.citationCommands rich)
  atom = P.grouped rich <|> do
   Tok _ kind txt<-P.satisfyTok (\(Tok _ k t)->not(k==Symbol && (t=="}" || t=="{")))
   pure $ case kind of
    Spaces->B.space;Newline->B.softbreak;Comment->mempty;_->B.str txt
change v = case K.arr v of
 [String "report",s] -> (() <$ (P.ignore (K.text s) :: P.LP PandocPure B.Inlines))
 _ -> Q.modifyState (\s->S.change s v)
changes v = mapM_ change (K.arr v)
inline :: Value -> P.LP PandocPure B.Inlines
inline v = case K.arr v of
 [String "atom"] -> rich
 [String "sequence"] -> mconcat <$> Q.many rich
 [String "bracket",x] -> P.bracketed (inline x)
 [String "pure",value,cs] -> changes cs >> pure(B.fromList(K.as value))
 [String "chunk",value,cs] -> P.anyTok >> Q.skipMany P.anyTok >> changes cs >> pure(B.fromList(K.as value))
 [String "fail",n,cs] -> replicateM (K.as n) P.anyTok >> changes cs >> fail "callback failure"
 _ -> error "inline callback"
blocks :: Value -> P.LP PandocPure B.Blocks
blocks v = case K.arr v of
 [String "pure",value,cs] -> changes cs >> pure(B.fromList(K.as value))
 [String "take",n,value,cs] -> replicateM (K.as n) P.anyTok >> changes cs >> pure(B.fromList(K.as value))
 [String "fail",n,cs] -> replicateM (K.as n) P.anyTok >> changes cs >> fail "callback failure"
 [String "scan"] -> B.para . mconcat <$> Q.many (Q.notFollowedBy(P.controlSeq "end") >> rich)
 _ -> error "block callback"
mode v = [AuthorInText,SuppressAuthor,NormalCitation]!!K.as v
ils p = toJSON . B.toList <$> p
bs p = toJSON . B.toList <$> p
parser :: Value -> P.LP PandocPure Value
parser v = case K.arr v of
 [String "cites",m,multi,cb] -> toJSON <$> C.cites (inline cb) (mode m) (K.as multi)
 [String "citation",name,cb] -> ils $ Map.findWithDefault Q.parserZero (K.text name) (C.citationCommands(inline cb))
 [String "rich"] -> ils (mconcat <$> Q.many rich)
 [String "dollars"] -> ils MT.dollarsMath
 [String "math-env"] -> ils MT.inlineEnvironment
 [String "env-entry",name] -> ils $ Map.findWithDefault Q.parserZero (K.text name) MT.inlineEnvironments
 [String "math-mode"] -> toJSON . P.sMathMode <$> Q.getState
 [String "with-math",p] -> MT.withMathMode(parser p)
 [String "style"] -> bs MT.theoremstyle
 [String "newtheorem"] -> bs MT.newtheorem
 [String "theorem",name,b,i,o] -> bs $ MT.theoremEnvironment (blocks b) (inline i) (inline o) (K.text name)
 [String "proof",b,o] -> bs $ MT.proof (blocks b) (inline o)
 [String "then",a,b] -> parser a >> parser b
 [String "pair",a,b] -> do x<-parser a;y<-parser b;pure(toJSON[x,y])
 [String "or",a,b] -> parser a <|> parser b
 [String "try",p] -> Q.try(parser p)
 [String "look",p] -> Q.lookAhead(parser p)
 [String "raw",p] -> do (x,ts)<-P.withRaw(parser p);pure(toJSON[x,K.tvs ts])
 [String "from",ts,p] -> P.parseFromToks(parser p)(K.tokens ts)
 [String "verbatim",p] -> P.withVerbatimMode(parser p)
 [String "disable",p] -> P.disablingWithRaw(parser p)
 [String "many",p] -> toJSON <$> Q.many(parser p)
 [String "repeat",n,p] -> toJSON <$> replicateM(K.as n)(parser p)
 _ -> S.parser v
run cs ts expr = case runPure $ do
 result<-runReaderT(Q.runParserT action (foldl S.change P.defaultLaTeXState(K.arr cs)) "chunk" (P.TokStream False(K.tokens ts))) P.emptyLaTeXEnv
 logs<-getLog
 pure(result,logs)
 of
 Left e->object["pandocError" .= show e]
 Right(Left e,logs)->object["ok" .= False,"error" .= show e,"logs" .= logs]
 Right(Right(x,s,p,P.TokStream flag rest),logs)->object["ok" .= True,"value" .= x,"state" .= S.sv s,"position" .= K.pv p,"expanded" .= flag,"rest" .= K.tvs rest,"logs" .= logs]
 where action=do x<-parser expr;s<-Q.getState;p<-Q.getPosition;rest<-Q.getInput;pure(x,s,p,rest)
observe v = case K.arr v of
 [String "stress",kind,n] -> stress (K.as kind) (K.as n)
 [String "registries"] -> toJSON [Map.keys(C.citationCommands rich),MT.inlineEnvironmentNames]
 [String "inputs"] -> toJSON[(name,K.pt t)|group<-Inputs.tests,(name,t)<-group]
 [String "parse",cs,ts,p] -> run cs ts p
 [String "math-inline",t] -> toJSON(B.toList(MT.mathInline(K.text t)))
 [String "math-display",t] -> toJSON(B.toList(MT.mathDisplay(K.text t)))
 [String "prefix",p,cs] -> toJSON(C.addPrefix(K.as p)(K.as cs))
 [String "suffix",p,cs] -> toJSON(C.addSuffix(K.as p)(K.as cs))
 [String "part",p] -> toJSON(C.handleCitationPart(B.fromList(K.as p)))
 [String "note",p] -> toJSON(B.toList(C.inNote(B.fromList(K.as p))))
 [String "extract-label",b] -> toJSON(MT.extractLabelFromBlock(K.as b))
 [String "italicize",b] -> toJSON(MT.italicize(K.as b))
 [String "walk-italicize",b] -> toJSON(walk MT.italicize (K.as b::[Block]))
 [String "title",i,b] -> toJSON(B.toList(MT.addTitle(B.fromList(K.as i))(B.fromList(K.as b))))
 [String "qed",b] -> toJSON(B.toList(MT.addQed(B.fromList(K.as b))))
 _->K.dispatch v
stress :: Int -> Int -> Value
stress kind n = let
 namepos=Pos.newPos "name" 1 1
 spec=P.TheoremSpec(P.tokenize namepos "Theorem") P.PlainStyle Nothing Nothing True (P.DottedNum[0])
 initial=P.defaultLaTeXState{P.sTheoremMap=Map.singleton "thm" spec}
 source=case kind of
  0->"$"<>T.replicate n "{"<>"x"<>T.replicate n "}"<>"$tail"
  1->"{"<>T.intercalate "," ["a"<>T.pack(show i)|i<-[0..n-1]]<>"}tail"
  3->"\\end{thm}tail"
  _->""
 body=foldl' (\bs _->[BlockQuote bs]) [Para[Str "body"]] [1..n]
 p=case kind of
  0->show . B.toList <$> MT.dollarsMath
  1->show <$> C.cites rich NormalCitation False
  2->show <$> foldl' (\q _->MT.withMathMode q) (P.sMathMode <$> Q.getState) [1..n]
  3->show . B.toList <$> MT.theoremEnvironment (pure(B.fromList body)) (inline(toJSON[String "sequence"])) Q.parserZero "thm"
  _->error "stress kind"
 action=do out<-p;s<-Q.getState;position<-Q.getPosition;rest<-Q.getInput;pure(out,s,position,rest)
 in case runPure $ do
  result<-runReaderT(Q.runParserT action initial "stress" (P.TokStream False(P.tokenize(Pos.newPos "stress" 1 1)source))) P.emptyLaTeXEnv
  logs<-getLog
  pure(result,logs)
 of
  Right(Right(out,s,p,P.TokStream flag rest),logs)->object["hash" .= show(hashlazy(L.fromStrict(TE.encodeUtf8(T.pack out)))::Digest SHA256),"state" .= S.sv s,"position" .= K.pv p,"expanded" .= flag,"rest" .= K.tvs rest,"logs" .= logs]
  other->error(show other)
main = do
 hSetBuffering stdout LineBuffering
 let loop=do
      eof<-hIsEOF stdin
      if eof then pure () else do
       line<-BS.getLine
       let output=encode(observe(either error id(eitherDecodeStrict line)))
       result<-E.try(E.evaluate(L.length output))::IO(Either E.SomeException Int64)
       case result of
        Left e->L.putStrLn(encode(object["exception" .= takeWhile (/='\n')(E.displayException e)]))
        Right _->L.putStrLn output
       loop
 loop
