#!/usr/bin/env python3
"""Copy complete pinned original reader/tests; only module/import wiring changes."""
import pathlib,hashlib
HERE=pathlib.Path(__file__).resolve().parent
ROOT=HERE.parents[1]
PINS={'upstream/pandoc/src/Text/Pandoc/Readers/Muse.hs': 'e3f2d6050ed460386cc0e2b2dde7b30af6670fe4cba5f0bf82d5c232b2c02241', 'upstream/pandoc/test/Tests/Readers/Muse.hs': 'e378c2fee2c6c05e4331c74830cd0c0fcd5d7ba6c9e5f01852d2504640d64a38'}
for path,digest in PINS.items():assert hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==digest,path
source=(ROOT/'upstream/pandoc/src/Text/Pandoc/Readers/Muse.hs').read_text()
source=source.replace('module Text.Pandoc.Readers.Muse (readMuse) where','module SourceMuseReader (readMuse) where').replace('import Text.Pandoc.Class.PandocMonad (PandocMonad (..))','import Text.Pandoc.Class (PandocMonad (..))')
(HERE/'SourceMuseReader.hs').write_text(source)
source=(ROOT/'upstream/pandoc/test/Tests/Readers/Muse.hs').read_text().replace('module Tests.Readers.Muse (tests) where','module OriginalTests (tests) where').replace('import Test.Tasty\n','import OracleTree\n').replace('import Tests.Helpers\n','import OracleHelpers\n').replace('import Text.Pandoc\n','import Text.Pandoc hiding (readMuse)\nimport SourceMuseReader\n')
source=source.replace('amuse :: Text -> Pandoc','amuse :: ReaderFunction').replace('amuse = purely $','amuse = ReaderFunction ["amuse"] $ purely $').replace('emacsMuse :: Text -> Pandoc','emacsMuse :: ReaderFunction').replace('emacsMuse = purely $','emacsMuse = ReaderFunction [] $ purely $').replace('test (purely $ readMuse def { readerExtensions = extensionsFromList [Ext_amuse, Ext_auto_identifiers]})','test (ReaderFunction ["amuse", "auto_identifiers"] $ purely $ readMuse def { readerExtensions = extensionsFromList [Ext_amuse, Ext_auto_identifiers]})')
(HERE/'OriginalTests.hs').write_text(source)
