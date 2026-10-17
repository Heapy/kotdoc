{-# LANGUAGE OverloadedStrings #-}
import Data.Aeson
import qualified Data.Aeson.KeyMap as KM
import Data.Aeson.Types(parseEither,Parser)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
import qualified Text.GridTable as G
import qualified Text.GridTable.Trace as G
import Data.Array(elems,listArray,bounds)
import qualified Text.GridTable.Parse as GP
import SourceCSV
import Text.Parsec
import System.Environment(getArgs)
encodeGrid gt=object["rows" .= [[object["lines" .= G.cellContent c,"rowSpan" .= (case G.cellRowSpan c of G.RowSpan n->n),"colSpan" .= (case G.cellColSpan c of G.ColSpan n->n)]|c<-row]|row<-G.rows gt],"head" .= fmap G.fromRowIndex(G.arrayTableHead gt),"foot" .= fmap G.fromRowIndex(G.arrayTableFoot gt),"specs" .= [(show a,n)|(a,n)<-elems(G.arrayTableColSpecs gt)]]
encodeRaw gt=let ((r1,c1),(r2,c2))=bounds(G.arrayTableCells gt) in object["table" .= encodeGrid gt,"bounds" .= ((G.fromRowIndex r1,G.fromColIndex c1),(G.fromRowIndex r2,G.fromColIndex c2)),"cells" .= [case c of G.ContentCell (G.RowSpan rs)(G.ColSpan cs)ls->object["content" .= ls,"rowSpan" .= rs,"colSpan" .= cs];G.ContinuationCell(r,c)->object["continuation" .= (G.fromRowIndex r,G.fromColIndex c)]|c<-elems(G.arrayTableCells gt)]]
main=B.getContents >>= mapM_ observe . B.lines
observe line=case eitherDecode line >>= parseEither request of
 Left e->error e
 Right(name,kind,input,opts)->B.putStrLn $ encode $ object["name" .= name,"result" .= case kind of
  "csv"->case parseCSV opts input of Left e->object["error" .= show e];Right rows->object["rows" .= rows]
  "grid-raw"->case runParser G.gridTable () "grid" input of Left e->object["error" .= show e];Right gt->encodeRaw gt
  "grid-follow"->case runParser (G.gridTable *> many1 letter) () "grid" input of Left e->object["error" .= show e];Right t->object["text" .= t]
  "grid-follow-blank"->case runParser (G.gridTable *> newline *> many1(letter <|> space)) () "grid" input of Left e->object["error" .= show e];Right t->object["text" .= t]
  "lines"->case runParser ((,) <$> many GP.tableLine <*> getInput) () "grid" input of Left e->object["error" .= show e];Right(ls,rest)->object["lines" .= ls,"rest" .= rest]
  "grid-rows"->encodeGrid $ G.ArrayTable (listArray ((1,1),(2,2)) [G.ContentCell 2 1 ["1"],G.ContentCell 1 1 ["2"],G.ContinuationCell(1,1),G.ContentCell 1 1 ["3"]]) Nothing Nothing (listArray(1,2)[(G.AlignDefault,5),(G.AlignDefault,7)])
  "grid-trace"->maybe Null encodeGrid(G.traceLines(T.lines input))
  "grid"->case runParser ((,) <$> G.gridTable <*> getInput) () "grid" input of Left e->object["error" .= show e];Right(gt,rest)->object["table" .= encodeGrid gt,"rest" .= rest]
  _->error "unknown helper"]
request :: Value -> Parser(T.Text,T.Text,T.Text,CSVOptions)
request=withObject "request" $ \o->do
 name<-o .: "name";kind<-o .: "kind";input<-o .: "input";delim<-o .:? "delimiter" .!= ',';quote<-if KM.member "quote" o then o .: "quote" else pure (Just '"');keep<-o .:? "keepSpace" .!= False;escape<-o .:? "escape"
 pure(name,kind,input,CSVOptions delim quote keep escape)
