{-# LANGUAGE OverloadedStrings #-}
module KernelObserver where
import Control.Exception as E
import Control.Monad (forM_,void,replicateM)
import Control.Monad.Reader (runReaderT)
import Control.Applicative ((<|>))
import Data.Aeson
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import qualified Data.Vector as V
import Data.Char (chr,ord)
import qualified Data.Map as M
import qualified Data.List.NonEmpty as NE
import qualified Text.Parsec as C
import qualified Text.Parsec.Pos as Pos
import Text.Pandoc.TeX
import Text.Pandoc.Class (runPure,getLog)
import Text.Pandoc.Definition
import Text.Pandoc.Options
import Text.Pandoc.Sources (Sources(..))
import qualified Text.Pandoc.Builder as Builder
import GHC.Float (castWord64ToDouble)
import Numeric (showEFloat,showFFloat)
import qualified OriginalParsing as P
import System.IO

as :: FromJSON a => Value -> a
as v = case fromJSON v of Success a -> a; Error e -> error e
arr (Array v) = V.toList v
arr _ = error "array"
text v = T.pack (map (chr . as) (arr v))
pt = toJSON . map ord . T.unpack
pos v = case arr v of [s,l,c] -> Pos.newPos (map(chr.as)(arr s)) (as l) (as c); _ -> error "pos"
pv p = toJSON [toJSON(map ord(Pos.sourceName p)),toJSON(Pos.sourceLine p),toJSON(Pos.sourceColumn p)]
tt v = case arr v of
 [String "CtrlSeq",s] -> CtrlSeq(text s)
 [String "Arg",n] -> Arg(as n)
 [String "DeferredArg",n] -> DeferredArg(as n)
 [String "Spaces"] -> Spaces
 [String "Newline"] -> Newline
 [String "Symbol"] -> Symbol
 [String "Word"] -> Word
 [String "Comment"] -> Comment
 [String "Esc1"] -> Esc1
 [String "Esc2"] -> Esc2
 _ -> error "type"
ttv t = case t of
 CtrlSeq s -> toJSON [String "CtrlSeq",pt s]
 Arg n -> toJSON [String "Arg",toJSON n]
 DeferredArg n -> toJSON [String "DeferredArg",toJSON n]
 _ -> toJSON [show t]
tok v = case arr v of [p,t,s] -> Tok(pos p)(tt t)(text s); _ -> error "token"
tv (Tok p t s) = toJSON [pv p,ttv t,pt s]
tokens = map tok . arr
tvs = toJSON . map tv
td Null = Nothing
td v = Just (tokens v)
spec v = case arr v of
 [String "ArgNum",n] -> ArgNum(as n)
 [String "Pattern",ts] -> Pattern(tokens ts)
 [String "BoolArg",s,t] -> BoolArg(as s)(tok t)
 [String "DelimArg",s,o,c,d] -> DelimArg(as s)(tok o)(tok c)(td d)
 [String "VerbArg"] -> VerbArg
 [String "BodyArg",b,n] -> BodyArg(as b)(as n)
 [String "VerbBodyArg",b,n] -> VerbBodyArg(as b)(as n)
 [String "EmbellishArg",es] -> EmbellishArg [(tok t,td d)|e<-arr es,let [t,d]=arr e]
 [String "ProcessedArg",ps,s] -> ProcessedArg(map tokens(arr ps))(spec s)
 _ -> error "spec"
macro v = case arr v of
 [scope,expansion,ss,d,ts] -> Macro ([GlobalScope,GroupScope]!!as scope) ([ExpandWhenDefined,ExpandWhenUsed]!!as expansion) (map spec(arr ss)) (td d) (tokens ts)
 _ -> error "macro"
change s v = case arr v of
 [String "options",o] -> s{P.sOptions=as o}
 [String "define",k,m] -> s{P.sMacros=M.insert(as k)(macro m)(NE.head(P.sMacros s)) NE.:| NE.tail(P.sMacros s)}
 [String "push"] -> s{P.sMacros=NE.cons(NE.head(P.sMacros s))(P.sMacros s)}
 [String "pop"] -> s{P.sMacros=maybe(P.sMacros s)id(NE.nonEmpty(NE.tail(P.sMacros s)))}
 [String "verbatim",b] -> s{P.sVerbatimMode=as b}
 [String "math",b] -> s{P.sMathMode=as b}
 [String "enable-raw",b] -> s{P.sEnableWithRaw=as b}
 [String "raw",ts,n,k] -> s{P.sRawTokens=tokens ts,P.sRawTokenCount=as n,P.sRawScopes=as k}
 _ -> error "state change"
sv s = object ["macros" .= [[(k,show m)|(k,m)<-M.toAscList sc]|sc<-NE.toList(P.sMacros s)]
 ,"verbatim" .= P.sVerbatimMode s,"math" .= P.sMathMode s,"enableRaw" .= P.sEnableWithRaw s
 ,"rawTokens" .= tvs(P.sRawTokens s),"rawCount" .= P.sRawTokenCount s,"rawScopes" .= P.sRawScopes s
 ,"caption" .= P.sCaption s,"lastLabel" .= P.sLastLabel s]
parser v = case arr v of
 [String "pure",x] -> pure x
 [String "fail"] -> fail "failure"
 [String "change",x] -> Null <$ C.modifyState (\s -> change s x)
 [String "state"] -> sv <$> C.getState
 [String "then",a,b] -> parser a >> parser b
 [String "pair",a,b] -> do x<-parser a;y<-parser b;pure(toJSON[x,y])
 [String "or",a,b] -> parser a <|> parser b
 [String "try",p] -> C.try(parser p)
 [String "look",p] -> C.lookAhead(parser p)
 [String "many",p] -> toJSON <$> C.many(parser p)
 [String "repeat",n,p] -> toJSON <$> replicateM (as n) (parser p)
 [String "verbatim",p] -> P.withVerbatimMode(parser p)
 [String "disable",p] -> P.disablingWithRaw(parser p)
 [String "raw",p] -> do (x,ts)<-P.withRaw(parser p);pure(toJSON[x,tvs ts])
 [String "from",ts,p] -> P.parseFromToks(parser p)(tokens ts)
 [String "any"] -> tv <$> P.anyTok
 [String "all"] -> tvs <$> C.many P.anyTok
 [String "peek"] -> tv <$> P.peekTok
 [String "control"] -> tv <$> P.anyControlSeq
 [String "control",s] -> tv <$> P.controlSeq(text s)
 [String "symbol"] -> tv <$> P.anySymbol
 [String "symbol",c] -> tv <$> P.symbol(chr(as c))
 [String "symbol-in",cs] -> tv <$> P.symbolIn(map (chr.as)(arr cs))
 [String "infile",cs] -> tv <$> P.infile(map (chr.as)(arr cs))
 [String "single"] -> tv <$> P.singleChar
 [String "escape"] -> toJSON . ord <$> P.primEscape
 [String "spaces"] -> Null <$ P.spaces
 [String "spaces1"] -> Null <$ P.spaces1
 [String "sp"] -> Null <$ P.sp
 [String "blankline"] -> Null <$ P.blankline
 [String "endline"] -> Null <$ P.endline
 [String "whitespace"] -> Null <$ P.whitespace
 [String "newline"] -> Null <$ P.newlineTok
 [String "comment"] -> Null <$ P.comment
 [String "bgroup"] -> tv <$> P.bgroup
 [String "egroup"] -> tv <$> P.egroup
 [String "braced"] -> tvs <$> P.braced
 [String "braced-url"] -> tvs <$> P.bracedUrl
 [String "braced-or-token"] -> tvs <$> P.bracedOrToken
 [String "bracketed"] -> tvs <$> P.bracketed ((:[]) <$> P.anyTok)
 [String "bracketed-toks"] -> tvs <$> P.bracketedToks
 [String "grouped"] -> tvs <$> P.grouped ((:[]) <$> P.singleChar)
 [String "paren"] -> tvs <$> P.parenWrapped ((:[]) <$> P.anyTok)
 [String "verb",c] -> tv <$> P.verbTok(chr(as c))
 [String "verbatim-arg"] -> tvs <$> P.verbatimArg
 [String "delimited",o,c] -> tvs <$> P.delimitedToks(tok o)(tok c)
 [String "dimen"] -> pt <$> P.dimenarg
 [String "keyvals"] -> toJSON . map (\(a,b)->[pt a,pt b]) <$> P.keyvals
 [String "begin",s] -> Null <$ P.begin_(text s)
 [String "end",s] -> Null <$ P.end_(text s)
 [String "verb-env",s] -> pt <$> P.verbEnv(text s)
 [String "overlay"] -> pt <$> P.overlaySpecification
 [String "rawopt"] -> pt <$> P.rawopt
 [String "skipopts"] -> Null <$ P.skipopts
 [String "raw-command",s,t] -> pt <$> P.getRawCommand(text s)(text t)
 [String "label"] -> Null <$ P.label
 [String "caption"] -> Null <$ P.setCaption (Builder.str . P.untoken <$> P.singleChar)
 [String "tok-with"] -> toJSON . Builder.toList <$> P.tokWith (Builder.str . P.untoken <$> P.singleChar)
 [String "env",s,p] -> P.env (text s) (parser p)
 [String "expand",n] -> do P.TokStream _ ts<-C.getInput;tvs <$> P.doMacros'(as n)ts
 [String "do-macros"] -> Null <$ P.doMacros
 [String "processors",ps,ts] -> tvs <$> P.applyArgProcessors (map tokens(arr ps))(tokens ts)
 _ -> error "parser"
run changes ts expr = case runPure $ do
 result<-runReaderT(C.runParserT action (foldl change P.defaultLaTeXState (arr changes)) "chunk" (P.TokStream False(tokens ts))) P.emptyLaTeXEnv
 logs<-getLog
 pure(result,logs)
 of
 Left e -> object ["pandocError" .= show e]
 Right(Left err,logs) -> object ["ok" .= False,"error" .= show err,"logs" .= logs]
 Right(Right(x,s,p,P.TokStream flag rest),logs) -> object ["ok" .= True,"value" .= x,"state" .= sv s,"position" .= pv p,"expanded" .= flag,"rest" .= tvs rest,"logs" .= logs]
 where action=do x<-parser expr;s<-C.getState;p<-C.getPosition;rest<-C.getInput;pure(x,s,p,rest)
sources v = Sources [(pos p,text s)|x<-arr v,let [p,s]=arr x]
sourcev (Sources xs) = toJSON [[pv p,pt s]|(p,s)<-xs]
bridge changes src action = case runPure $ do
 result<-C.runParserT wrapped (foldl change P.defaultLaTeXState (arr changes)) "outer" (sources src)
 logs<-getLog
 pure(result,logs)
 of
 Left e -> object ["pandocError" .= show e]
 Right(Left err,logs) -> object ["ok" .= False,"error" .= show err,"logs" .= logs]
 Right(Right(x,s,p,rest),logs) -> object ["ok" .= True,"value" .= x,"state" .= sv s,"position" .= pv p,"rest" .= sourcev rest,"logs" .= logs]
 where
  wrapped = do
   x<-case arr action of
    [String "apply",s] -> pt <$> P.applyMacros(text s)
    [String "raw",prep,val] -> do
     ts<-P.getInputTokens
     (x,t)<-P.rawLaTeXParser P.emptyLaTeXEnv ts (void(parser prep)) (parser val)
     pure(toJSON[x,pt t])
    _ -> error "bridge action"
   s<-C.getState;p<-C.getPosition;rest<-C.getInput;pure(x,s,p,rest)
dispatch v = case arr v of
 [String "demand",kind] -> demand (as kind)
 [String "default-options"] -> toJSON (P.sOptions P.defaultLaTeXState)
 [String "bridge",changes,src,action] -> bridge changes src action
 [String "tokens",p,s] -> tvs(P.tokenize(pos p)(text s))
 [String "parse",changes,ts,p] -> run changes ts p
 [String "body",b,ts] -> tvs(P.verbatimBodyToks(as b)(tokens ts))
 [String "group",ts] -> toJSON(fmap (\(a,b)->[tvs a,tvs b])(P.tokGroup(tokens ts)))
 [String "split",t,ts] -> toJSON(map tvs(P.splitToksOn(tok t)(tokens ts)))
 [String "remove-label",label,doc] -> toJSON(P.removeLabel (text label) (as doc::Pandoc))
 [String "integer",s] -> toJSON (P.evalInteval (text s))
 [String "float",s] -> toJSON (P.evalFpeval (text s))
 [String "format",bits] -> toJSON (P.formatFp (castWord64ToDouble (as bits)))
 [String "exponent",bits,n] -> toJSON (showEFloat (Just (as n)) (castWord64ToDouble (as bits)) "")
 [String "fixed",bits] -> toJSON (showFFloat Nothing (castWord64ToDouble (as bits)) "")
 _ -> error "request"
demand :: Int -> Value
demand kind = let
 bad = errorWithoutStackTrace
 p = Pos.newPos "demand" 1 1
 a = Tok (bad "bad-position") (CtrlSeq "a") (bad "bad-text")
 word = Tok p Word "x"
 body = case kind of 1 -> bad "bad-body"; 2 -> bad "bad-body"; 4 -> [bad "bad-head",word]; _ -> [word]
 m = Macro GlobalScope (if kind==2 then ExpandWhenUsed else ExpandWhenDefined) []
       (if kind==5 then Just [bad "bad-default"] else Nothing) body
 state = P.defaultLaTeXState{P.sMacros=M.singleton "a" m NE.:| []}
 action = case kind of
  0 -> True <$ P.doMacros' 1 [a]
  1 -> True <$ P.doMacros' 1 [a]
  2 -> True <$ P.doMacros' 1 [a]
  3 -> null <$> P.doMacros' 1 [a]
  4 -> (\ts->P.untoken(head ts)=="x") <$> P.doMacros' 1 [a]
  5 -> (\ts->P.untoken(head ts)=="x") <$> P.doMacros' 1 [a]
  6 -> P.doMacros >> pure True
  7 -> P.doMacros >> pure True
  8 -> True <$ P.doMacros' 21 [a]
  _ -> error "demand kind"
 initial = if kind==0 then P.defaultLaTeXState else if kind==7 then state{P.sVerbatimMode=True} else state
 input = if kind==6 then P.TokStream True(bad "bad-input") else P.TokStream False[a]
 in case runPure(runReaderT(C.runParserT action initial "demand" input)P.emptyLaTeXEnv) of
  Left e -> object ["pandocError" .= show e]
  Right(Left e) -> object ["parseError" .= show e]
  Right(Right b) -> Bool b
main = do
 hSetBuffering stdout LineBuffering
 input <- L.getContents
 forM_ (L.lines input) $ \line -> do
  result <- E.try $ E.evaluate $ L.length $ encode $ dispatch (as (either error id (eitherDecode line) :: Value))
  case result of
   Left e -> L.putStrLn (encode (object ["exception" .= show (e :: E.SomeException)]))
   Right _ -> L.putStrLn (encode (dispatch (either error id (eitherDecode line))))
