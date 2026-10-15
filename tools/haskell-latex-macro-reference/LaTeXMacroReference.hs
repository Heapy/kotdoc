{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified KernelObserver as K
import qualified OriginalParsing as P
import qualified OriginalMacro as M
import Control.Applicative ((<|>))
import Control.Monad (forM_,replicateM)
import Control.Monad.Reader (runReaderT)
import qualified Control.Exception as E
import Data.Aeson
import qualified Data.ByteString as B
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import qualified Data.List.NonEmpty as NE
import qualified Data.Map as Map
import Text.Pandoc.Class (runPure,getLog)
import qualified Text.Parsec as C
import qualified Text.Parsec.Pos as Pos
import Crypto.Hash (Digest,SHA256,hashlazy)
import Data.Binary.Put
import Data.Char (chr)
import Data.Int (Int64)
import Data.List (foldl')
import qualified Data.Vector as V
import Text.Pandoc.Options (ReaderOptions(..))
import Text.Pandoc.Extensions (enableExtension,Extension(..))
import System.IO

step = M.macroDef (\s->[object["definition" .= K.pt s]]) <|>
       ((:[]) . K.tv <$> (P.bgroup <|> P.egroup <|> P.anyTok))
parser v = case K.arr v of
 [String "definition"] -> toJSON <$> M.macroDef (\s->[K.pt s])
 [String "program"] -> toJSON . concat <$> C.many step
 [String "specs",body] -> do (specs,b)<-M.xparseArgSpecs(K.as body);pure(toJSON[toJSON(map show specs),toJSON b])
 [String "pair",a,b] -> do x<-parser a;y<-parser b;pure(toJSON[x,y])
 [String "then",a,b] -> parser a >> parser b
 [String "or",a,b] -> parser a <|> parser b
 [String "try",p] -> C.try(parser p)
 [String "look",p] -> C.lookAhead(parser p)
 [String "raw",p] -> do (x,ts)<-P.withRaw(parser p);pure(toJSON[x,K.tvs ts])
 [String "verbatim",p] -> P.withVerbatimMode(parser p)
 [String "disable",p] -> P.disablingWithRaw(parser p)
 [String "from",ts,p] -> P.parseFromToks(parser p)(K.tokens ts)
 [String "many",p] -> toJSON <$> C.many(parser p)
 [String "repeat",n,p] -> toJSON <$> replicateM(K.as n)(parser p)
 [String "env",name,p] -> P.env (K.text name) (parser p)
 _ -> K.parser v
run changes ts expr = case runPure $ do
 result<-runReaderT(C.runParserT action (foldl K.change P.defaultLaTeXState (K.arr changes)) "chunk" (P.TokStream False(K.tokens ts))) P.emptyLaTeXEnv
 logs<-getLog
 pure(result,logs)
 of
 Left e -> object ["pandocError" .= show e]
 Right(Left err,logs) -> object ["ok" .= False,"error" .= show err,"logs" .= logs]
 Right(Right(x,s,p,P.TokStream flag rest),logs) -> object ["ok" .= True,"value" .= x,"state" .= K.sv s,"position" .= K.pv p,"expanded" .= flag,"rest" .= K.tvs rest,"logs" .= logs]
 where action=do x<-parser expr;s<-C.getState;p<-C.getPosition;rest<-C.getInput;pure(x,s,p,rest)
observe v = case K.arr v of
 [String "deep",depth,updates] -> deep (K.as depth) (K.as updates)
 [String "parse",changes,ts,p] -> run changes ts p
 [String "letterize",s] -> K.pt(M.letterize(K.text s))
 [String "unicode",start,count] -> toJSON(show(hashlazy(runPut $ forM_ [K.as start..K.as start+K.as count-1] $ \cp->do
   let bs=encode(K.pt(M.letterize(T.singleton(chr cp))))
   putWord32be(fromIntegral(L.length bs));putLazyByteString bs)::Digest SHA256))
 _ -> K.dispatch v
deep depth updates = let
 initial=P.defaultLaTeXState{P.sOptions=(P.sOptions P.defaultLaTeXState){readerExtensions=enableExtension Ext_latex_macros(readerExtensions(P.sOptions P.defaultLaTeXState))}}
 scoped=foldl' (\s _->s{P.sMacros=NE.cons(NE.head(P.sMacros s))(P.sMacros s)}) initial [1..depth]
 text=T.replicate updates "\\global\\def\\a{G}"<>T.replicate depth "}"
 action=do x<-parser(toJSON[String "program"]);s<-C.getState;p<-C.getPosition;rest<-C.getInput;pure(x,s,p,rest)
 in case runPure $ do
  result<-runReaderT(C.runParserT action scoped "deep" (P.TokStream False(P.tokenize(Pos.newPos "deep" 1 1)text))) P.emptyLaTeXEnv
  logs<-getLog
  pure(result,logs)
 of
  Right(Right(x,s,p,P.TokStream flag rest),logs)->object["count" .= length(K.arr x),"hash" .= show(hashlazy(encode x)::Digest SHA256),"state" .= K.sv s,"position" .= K.pv p,"expanded" .= flag,"rest" .= K.tvs rest,"logs" .= logs]
  other->error(show other)
main = do
 hSetBuffering stdout LineBuffering
 let loop = do
      eof<-hIsEOF stdin
      if eof then pure () else do
       line<-B.getLine
       let output=encode(observe(either error id(eitherDecodeStrict line)))
       result<-E.try(E.evaluate(L.length output))::IO(Either E.SomeException Int64)
       case result of
        Left e->L.putStrLn(encode(object["exception" .= takeWhile (/='\n')(E.displayException e)]))
        Right _->L.putStrLn output
       loop
 loop
