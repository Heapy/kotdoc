#!/usr/bin/env python3
"""Exact source copies with module/import changes only; expected expressions untouched."""
from pathlib import Path
import hashlib,json
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1];pins=json.loads((HERE/'pins.json').read_text())
for name,pin in pins.items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==pin,name
s=(ROOT/'upstream/pandoc/src/Text/Pandoc/Readers/RST.hs').read_text().replace('module Text.Pandoc.Readers.RST ( readRST ) where','module SourceRstReader ( readRST ) where').replace('import Text.Pandoc.URI','import SourceUriPosix').replace('import Text.Pandoc.CSV','import SourceCSV');(HERE/'SourceRstReader.hs').write_text(s)
s=(ROOT/'upstream/pandoc/src/Text/Pandoc/CSV.hs').read_text().replace('module Text.Pandoc.CSV','module SourceCSV');(HERE/'SourceCSV.hs').write_text(s)
s=(ROOT/'upstream/pandoc/test/Tests/Readers/RST.hs').read_text().replace('module Tests.Readers.RST (tests) where','module OriginalTests (tests) where').replace('import Test.Tasty\n','import OracleTree\n').replace('import Tests.Helpers\n','import OracleHelpers\n').replace('import Text.Pandoc\n','import Text.Pandoc hiding(readRST)\nimport SourceRstReader\n');(HERE/'OriginalTests.hs').write_text(s)

s=(HERE/'original-gridtables/test/test-gridtables.hs').read_text().replace('module Main (main) where','module Main (main) where\nimport System.IO.Unsafe(unsafePerformIO)\nimport qualified Data.Aeson as A\nimport qualified Data.ByteString.Lazy.Char8 as B')
s=s.replace('parse\' p = runParser p () "<test input>"','parse\' p text = unsafePerformIO $ do\n  B.putStrLn $ B.pack "__INPUT__" <> A.encode text\n  pure $ runParser p () "<test input>" text')
(HERE/'OriginalGridTests.hs').write_text('{-# LANGUAGE OverloadedStrings #-}\n'+s)
