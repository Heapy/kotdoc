{-# LANGUAGE OverloadedStrings #-}
module Main where
import qualified InlineObserver as X
import qualified CitationMathObserver as C
import qualified KernelObserver as K
import qualified StateObserver as S
import qualified OriginalParsing as P
import qualified OriginalTable as T
import qualified OriginalMath as MT
import qualified Text.Pandoc.Builder as B
import Text.Pandoc.Definition
import Text.Pandoc.TeX
import Text.Pandoc.Class
import Text.Pandoc.Class.CommonState
import Text.Pandoc.Shared (compactifyTable)
import qualified Text.Pandoc.Shared as Shared
import Data.Binary.Put
import Data.Char (chr)
import Text.Pandoc.Parsing (withQuoteContext)
import qualified Text.Parsec as Q
import Control.Applicative ((<|>))
import Control.Monad (replicateM)
import Control.Monad.Reader (runReaderT)
import Control.Monad.Trans.Class (lift)
import qualified Control.Exception as E
import Data.Aeson
import qualified Data.ByteString.Char8 as BS
import qualified Data.ByteString.Lazy.Char8 as L
import qualified Data.Map as Map
import qualified Data.Text as Text
import qualified Data.Text.Encoding as TE
import qualified Text.Parsec.Pos as Pos
import Data.List (foldl')
import Data.Int (Int64)
import System.IO
import Crypto.Hash (Digest,SHA256,hashlazy)

-- Explicit finite component callbacks, not the full readLaTeX grammar.
inline v=case K.arr v of
 [String "cell"]->cellInline
 _->X.inline v
blocks v=case K.arr v of
 [String "cell"]->cellBlock
 _->C.blocks v
cellInline=Q.try MT.dollarsMath <|> Q.try MT.inlineEnvironment <|> X.rich
cellBlock=Q.try table <|> caption <|> label <|> parbox <|> paragraph
 where
  table=do
   P.controlSeq "begin"
   name<-P.untokenize <$> P.braced
   Map.findWithDefault Q.parserZero name(T.tableEnvironments cellBlock cellInline)
  caption=P.controlSeq "caption" *> P.setCaption cellInline *> pure mempty
  label=P.label *> pure mempty
  parbox=P.controlSeq "parbox" *> P.skipopts *> P.braced *> P.grouped cellBlock
  stop=P.satisfyTok(\(Tok _ k t)->case k of CtrlSeq n->n `elem` ["begin","end","caption","label","parbox"];_->k==Symbol && t=="}")
  paragraph=B.para . mconcat <$> Q.many1(Q.notFollowedBy stop *> cellInline)
columns xs=toJSON[(a,w,K.tvs p,K.tvs s)|(a,w,(p,s))<-xs]
prefs value=[(K.tokens a,K.tokens b)|x<-K.arr value,let[a,b]=K.arr x]
parser v=case K.arr v of
 [String "entry",name,b,i]->C.bs $ Map.findWithDefault Q.parserZero(K.text name)(T.tableEnvironments(blocks b)(inline i))
 [String "aligns"]->columns <$> T.parseAligns
 [String "split"]->Null <$ T.splitWordTok
 [String "hline"]->Null <$ T.hline
 [String "lbreak"]->K.tv <$> T.lbreak
 [String "alignment"]->toJSON <$> T.cellAlignment
 [String "cell",b]->toJSON <$> T.parseTableCell(blocks b)
 [String "multirow",b]->toJSON <$> T.multirowCell(blocks b)
 [String "multicolumn",b]->toJSON <$> T.multicolumnCell(blocks b)
 [String "row",name,p,b,i]->toJSON <$> T.parseTableRow(blocks b)(inline i)(K.text name)(prefs p)
 [String "simple",name,w,b,i]->C.bs $ T.simpTable(blocks b)(inline i)(K.text name)(K.as w)
 [String "caption",value]->C.bs $ T.addTableCaption(B.fromList(K.as value))
 [String "component"]->C.bs $ mconcat <$> Q.many cellBlock
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
 _->X.parser v
run cs cv fs ts expr=case runPure $ do
 putCommonState(X.seed cv)
 X.setFiles fs
 result<-runReaderT(Q.runParserT action(foldl S.change P.defaultLaTeXState(K.arr cs))"chunk"(P.TokStream False(K.tokens ts)))P.emptyLaTeXEnv
 shared<-getCommonState
 pure(result,X.common shared)
 of
 Left e->object["pandocError" .= show e]
 Right(Left e,shared)->object["ok" .= False,"error" .= show e,"common" .= shared]
 Right(Right(x,s,p,P.TokStream flag rest),shared)->object["ok" .= True,"value" .= x,"state" .= S.sv s,"position" .= K.pv p,"expanded" .= flag,"rest" .= K.tvs rest,"common" .= shared]
 where action=do x<-parser expr;s<-Q.getState;p<-Q.getPosition;rest<-Q.getInput;pure(x,s,p,rest)
compact kind value=case kind of
 "document"->toJSON(compactifyTable(K.as value::Pandoc))
 "blocks"->toJSON(compactifyTable(K.as value::[Block]))
 "many-blocks"->toJSON(B.toList(compactifyTable(B.fromList(K.as value)::B.Blocks)))
 "block"->toJSON(compactifyTable(K.as value::Block))
 "inlines"->toJSON(compactifyTable(K.as value::[Inline]))
 "inline"->toJSON(compactifyTable(K.as value::Inline))
 "cell"->toJSON(compactifyTable(K.as value::Cell))
 "row"->toJSON(compactifyTable(K.as value::Row))
 "head"->toJSON(compactifyTable(K.as value::TableHead))
 "body"->toJSON(compactifyTable(K.as value::TableBody))
 "foot"->toJSON(compactifyTable(K.as value::TableFoot))
 _->error "compact kind"
observe v=case K.arr v of
 [String "parse",cs,cv,fs,ts,p]->run cs cv fs ts p
 [String "registries"]->toJSON(Map.keys(T.tableEnvironments cellBlock cellInline))
 [String "fix-rows",value]->toJSON(T.fixTableRows(K.as value))
 [String "fix-row",hang,cells]->toJSON(T.fixTableRow(K.as hang)(K.as cells))
 [String "fix-row-prefix",limit,hang,cells]->let(h,c)=T.fixTableRow(K.as hang)(K.as cells) in toJSON(take(K.as limit)h,c)
 [String "fix-head",value]->toJSON(T.fixTableHead(K.as value))
 [String "fix-body",value]->toJSON(T.fixTableBody(K.as value))
 [String "compact",kind,value]->compact(K.as kind)value
 [String "stress",kind,n]->stress(K.as kind)(K.as n)
 [String "shared-text",value]->toJSON(map (K.pt . ($ K.text value)) [Shared.trim,Shared.triml,Shared.trimr,Shared.stripTrailingNewlines])
 [String "shared-text-chars",start,count]->toJSON(show(hashlazy(runPut $ mapM_ (\cp->do
   putWord8(if Text.null(Shared.trim(Text.singleton(chr cp)))then 1 else 0)
   mapM_ (\input->mapM_ (\f->let bytes=TE.encodeUtf8(f input) in putWord32be(fromIntegral(BS.length bytes)) >> putByteString bytes)
      [Shared.trim,Shared.triml,Shared.trimr,Shared.stripTrailingNewlines])
      [Text.singleton(chr cp),Text.pack[chr cp,'x',chr cp],Text.pack[chr cp,'\n']])
   [K.as start..K.as start+K.as count-1])::Digest SHA256))
 _->X.observe v
stress :: Int -> Int -> Value
stress kind n=case kind of
 0->digest(show(T.fixTableRows(replicate n(Row nullAttr[Cell nullAttr AlignDefault(RowSpan 2)(ColSpan 1)[Plain[Str "x"]],B.emptyCell]))))
 1->let (hs,cs)=T.fixTableRow(replicate n(Just(ColSpan 1,RowSpan 2)))[Cell nullAttr AlignDefault(RowSpan 1)(ColSpan 1)[]] in digest(show(map (maybe (-1,-1) (\(ColSpan c,RowSpan r)->(c,r))) hs,cs))
 2->digest(show(compactifyTable(foldl'(\x _->Emph[x])(Note[Plain[Str "x"]])[1..n])))
 3->let source="{"<>Text.replicate n "l"<>"}tail" in parseStress source T.parseAligns (show . map(\(a,w,_)->(a,w)))
 _->error "stress kind"
 where
 digest s=toJSON(show(hashlazy(L.fromStrict(TE.encodeUtf8(Text.pack s)))::Digest SHA256))
 parseStress source p render=case runPure(runReaderT(Q.runParserT p P.defaultLaTeXState "stress"(P.TokStream False(P.tokenize(Pos.newPos "stress" 1 1)source)))P.emptyLaTeXEnv) of
  Right(Right x)->digest(render x)
  other->error(show other)
main=do
 hSetBuffering stdout LineBuffering
 let loop=do
      eof<-hIsEOF stdin
      if eof then pure() else do
       line<-BS.getLine
       let output=encode(observe(either error id(eitherDecodeStrict line)))
       result<-E.try(E.evaluate(L.length output))::IO(Either E.SomeException Int64)
       case result of
        Left e->L.putStrLn(encode(object["exception" .= takeWhile(/='\n')(E.displayException e)]))
        Right _->L.putStrLn output
       loop
 loop
