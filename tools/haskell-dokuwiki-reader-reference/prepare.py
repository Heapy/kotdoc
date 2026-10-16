#!/usr/bin/env python3
from pathlib import Path
import hashlib
ROOT=Path(__file__).resolve().parents[2]
folder=Path(__file__).resolve().parent
src=ROOT/'upstream/pandoc/src/Text/Pandoc/Readers/DokuWiki.hs'
assert hashlib.sha256(src.read_bytes()).hexdigest()=="3811d6c3f86eeac5f218a3624a00626b0be7fc3f13dff9b710b253d4702fd5c4", "Pinned reader source changed"
text=src.read_text()
(folder/'SourceDokuWikiReader.hs').write_text(text.replace('module Text.Pandoc.Readers.DokuWiki (readDokuWiki) where','module SourceDokuWikiReader (readDokuWiki) where').replace('import Text.Pandoc.Class.PandocMonad (PandocMonad (..))','import Text.Pandoc.Class (PandocMonad (..))'))
tests=(ROOT/'upstream/pandoc/test/Tests/Readers/DokuWiki.hs').read_text()
(folder/'OriginalTests.hs').write_text(tests.replace('module Tests.Readers.DokuWiki (tests) where','module OriginalTests (tests) where').replace('import Test.Tasty\n','import OracleTree\n').replace('import Tests.Helpers\n','import OracleHelpers\n'))
print('reader',len(text.splitlines()),hashlib.sha256(src.read_bytes()).hexdigest())
