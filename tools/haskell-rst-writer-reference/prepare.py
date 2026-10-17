#!/usr/bin/env python3
from pathlib import Path
import hashlib
ROOT=Path(__file__).resolve().parents[2];HERE=Path(__file__).resolve().parent
source=ROOT/'upstream/pandoc/src/Text/Pandoc/Writers/RST.hs'
assert hashlib.sha256(source.read_bytes()).hexdigest()=='75996aa5ec5e370c516b326b6e93c671e1f395bc2d919cd20c944b9f0cd25e4b'
text=source.read_text().replace('module Text.Pandoc.Writers.RST ( writeRST, flatten ) where','module SourceRstWriter ( writeRST, flatten ) where').replace('import Text.Pandoc.Class.PandocMonad (PandocMonad, report)','import Text.Pandoc.Class (PandocMonad, report)').replace('import Text.Pandoc.URI','import SourceUriPosix')
(HERE/'SourceRstWriter.hs').write_text(text)
source=ROOT/'upstream/pandoc/test/Tests/Writers/RST.hs'
assert hashlib.sha256(source.read_bytes()).hexdigest()=='c474c09c4a51aa72b55eaed52ed9f6479c5dccf1a218c756ae2cea143bbeb8a4'
text=source.read_text().replace('module Tests.Writers.RST','module OriginalTests').replace('import Test.Tasty\n','import OracleTree\n').replace('import Test.Tasty.HUnit','import OracleHUnit').replace('import Tests.Helpers','import OracleHelpers').replace('import Text.Pandoc\n','import Text.Pandoc hiding(writeRST)\n').replace('import Text.Pandoc.Writers.RST','import SourceRstWriter').replace(', HasCallStack','')
text=text.replace('test (purely (writeRST def . toPandoc))','test (WriterFunction def)').replace('test (purely (writeRST def{ writerTemplate = Just templ }) . toPandoc)','test (WriterFunction def{ writerTemplate = Just templ })').replace('test (purely (writeRST def{ writerTemplate = Just bodyTemplate })\n                       . toPandoc)','test (WriterFunction def{ writerTemplate = Just bodyTemplate })').replace('test (purely (writeRST def{ writerTemplate = Just bodyTemplate }) . toPandoc)','test (WriterFunction def{ writerTemplate = Just bodyTemplate })')
text=text.replace('flatten (','probeFlatten (')
text=text.replace('WriterFunction def)', 'WriterFunction def Nothing)').replace('WriterFunction def{ writerTemplate = Just templ })','WriterFunction def{ writerTemplate = Just templ } (Just $ T.pack t))').replace('WriterFunction def{ writerTemplate = Just bodyTemplate })','WriterFunction def{ writerTemplate = Just bodyTemplate } (Just "$body$\\n"))')
(HERE/'OriginalTests.hs').write_text(text)
