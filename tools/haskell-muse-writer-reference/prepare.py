#!/usr/bin/env python3
from pathlib import Path
import hashlib
ROOT=Path(__file__).resolve().parents[2];HERE=Path(__file__).resolve().parent
source=ROOT/'upstream/pandoc/src/Text/Pandoc/Writers/Muse.hs'
assert hashlib.sha256(source.read_bytes()).hexdigest()=='c4e39ce829af38c8ace2f6c562e3934eae6c4310f12fd0c4058dbcefe431062f'
text=source.read_text().replace('import Text.Pandoc.URI','import SourceUriPosix (escapeURI)').replace('module Text.Pandoc.Writers.Muse (writeMuse) where','module SourceMuseWriter (writeMuse) where').replace('import Text.Pandoc.Class.PandocMonad (PandocMonad)','import Text.Pandoc.Class (PandocMonad)')
(HERE/'SourceMuseWriter.hs').write_text(text)
source=ROOT/'upstream/pandoc/test/Tests/Writers/Muse.hs'
assert hashlib.sha256(source.read_bytes()).hexdigest()=='42804bfe9fa28fe61e5e641f0ad936a550b1c700b53bd4a1c26f8c1957c642ed'
text=source.read_text().replace('module Tests.Writers.Muse','module OriginalTests').replace('import Test.Tasty\n','import OracleTree\n').replace('import Test.Tasty.HUnit (HasCallStack)\n','').replace('import Tests.Helpers','import OracleHelpers').replace('import Text.Pandoc\n','import Text.Pandoc hiding (writeMuse)\n')
text=text.replace('muse :: (ToPandoc a) => a -> Text','muse :: WriterFunction').replace('museWithOpts :: (ToPandoc a) => WriterOptions -> a -> Text','museWithOpts :: WriterOptions -> WriterFunction').replace('museWithOpts opts = purely (writeMuse opts) . toPandoc','museWithOpts = WriterFunction').replace(', HasCallStack','')
(HERE/'OriginalTests.hs').write_text(text)
