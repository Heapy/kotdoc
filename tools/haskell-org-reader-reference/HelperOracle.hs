{-# LANGUAGE OverloadedStrings #-}
import SourceOrgReader.ParserState
import SourceOrgReader.Parsing
import SourceOrgReader.BlockStarts
import SourceOrgReader.ExportSettings
import qualified SourceOrgReader.Shared as Shared
import Text.Pandoc hiding(readOrg)
import Data.Aeson
import Data.Aeson.Types(parseEither)
import qualified Data.ByteString.Lazy.Char8 as B
import qualified Data.Text as T
import Text.Pandoc.Sources hiding(newline)
import Text.Parsec.Pos(sourceLine,sourceColumn)
import Control.Monad.Reader(runReaderT)
import Control.Monad(forM_)
import System.IO(isEOF)
export es=object["archived".=(case exportArchivedTrees es of ArchivedTreesExport->"export"::T.Text;ArchivedTreesNoExport->"none";ArchivedTreesHeadlineOnly->"headline"),"drawers".=(case exportDrawers es of Left x->object["exclude".=x];Right x->object["include".=x]),"emphasizedText".=exportEmphasizedText es,"headlineLevels".=exportHeadlineLevels es,"preserveBreaks".=exportPreserveBreaks es,"smartQuotes".=exportSmartQuotes es,"specialStrings".=exportSpecialStrings es,"subSuperscripts".=(case exportSubSuperscripts es of SubSupAll->"all"::T.Text;SubSupBraced->"braced";SubSupNone->"none"),"withAuthor".=exportWithAuthor es,"withCreator".=exportWithCreator es,"withEmail".=exportWithEmail es,"withEntities".=exportWithEntities es,"withFootnotes".=exportWithFootnotes es,"withLatex".=(case exportWithLatex es of TeXExport->"export"::T.Text;TeXIgnore->"ignore";TeXVerbatim->"verbatim"),"withPlanning".=exportWithPlanning es,"withTags".=exportWithTags es,"withTables".=exportWithTables es,"withTodoKeywords".=exportWithTodoKeywords es]
pos p=object["name".=sourceName p,"line".=sourceLine p,"column".=sourceColumn p]
parser mode=case mode of
 "export"->exportSettings>>return Null
 "hline"->hline>>return Null
 "headerStart"->toJSON <$> headerStart
 "tableStart"->toJSON . T.singleton <$> tableStart
 "latexEnvStart"->toJSON <$> latexEnvStart
 "bulletListStart"->toJSON <$> bulletListStart
 "orderedListStart"->toJSON <$> orderedListStart
 "drawerStart"->toJSON <$> drawerStart
 "metaLineStart"->metaLineStart>>return Null
 "commentLineStart"->commentLineStart>>return Null
 "exampleLineStart"->exampleLineStart>>return Null
 "noteMarker"->toJSON <$> noteMarker
 "endOfBlock"->endOfBlock>>return Null
 "anyLine"->toJSON <$> anyLine
 "newline"->toJSON . T.singleton <$> newline
 "blanklines"->toJSON <$> blanklines
 "argKey"->toJSON <$> orgArgKey
 "argWord"->toJSON <$> orgArgWord
 "tagWord"->toJSON <$> orgTagWord
 "anchor"->toJSON <$> orgAnchor
 _->fail "unknown helper mode"
observe value=case parseEither (withObject "request" $ \o -> (,,) <$> o.:"mode" <*> o.:"input" <*> (o.:?"extensions" .!= extensionsToList(getDefaultExtensions "org"))) value of
 Left e->error e
 Right(mode,input,exts)->do
  let direct=case mode of
       "isImageFilename"->Just(toJSON(Shared.isImageFilename input))
       "cleanLinkText"->Just(toJSON(Shared.cleanLinkText input))
       "translateLang"->Just(toJSON(Shared.translateLang input))
       "originalLang"->Just(toJSON(Shared.originalLang input))
       _->Nothing
  case direct of
   Just result->B.putStrLn$encode$object["request".=value,"expected".=result]
   Nothing->B.putStrLn$encode$object["request".=value,"expected".=(case runPure(do
      result<-flip runReaderT def$runParserT (do v<-parser mode;s<-getState;p<-getPosition;r<-getInput;return(v,s,p,r)) (optionsToParserState def{readerExtensions=extensionsFromList exts}) "source" (toSources input)
      logs<-getLog;return(result,logs))of
       Left e->object["error".=show e]
       Right(Left e,logs)->object["parseError".=show e,"logs".=logs]
       Right(Right(v,s,p,r),logs)->object["value".=v,"export".=export(orgStateExportSettings s),"position".=pos p,"remainder".=(let Sources xs=r in mconcat(map snd xs)),"lastPre".=fmap pos(orgStateLastPreCharPos s),"lastForbidden".=fmap pos(orgStateLastForbiddenCharPos s),"logs".=logs])]
main=B.getContents>>=mapM_(observe . either error id . eitherDecode) . B.lines
