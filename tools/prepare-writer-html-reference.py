#!/usr/bin/env python3
"""Extract original dependency bodies; change module/import wiring only."""
import argparse
from pathlib import Path

root = Path(__file__).resolve().parents[1]
folder = root / 'tools/haskell-writer-html-reference'
css = (root / 'upstream/pandoc/src/Text/Pandoc/CSS.hs').read_text()
shared = (root / 'upstream/pandoc/src/Text/Pandoc/Shared.hs').read_text()
writer = (root / 'upstream/pandoc/src/Text/Pandoc/Writers/Shared.hs').read_text()
trim = shared[shared.index('isWS ::'):shared.index('\n', shared.index('trim = T.dropAround isWS')) + 1]
css_body = css[css.index('type Parser ='):]
html_body = writer[writer.index('-- | Produce an HTML tag'):writer.index("-- | Returns 'True' iff")]
files = {
    'SourceCss.hs': '''{-# LANGUAGE OverloadedStrings #-}
-- Original Text.Pandoc.CSS + Shared.trim/isWS bodies, GPL-2.0-or-later.
-- Copyright (C) 2006-2024 John MacFarlane, 2015-2016 Mauro Bieg,
-- 2015 Ophir Lifshitz. Original notices retained in upstream/.
module SourceCss where
import Data.Either (fromRight)
import Data.Maybe (mapMaybe, listToMaybe)
import Data.Text (Text, pack)
import qualified Data.Text as T
import Text.Parsec
''' + trim + '\n' + css_body,
    'SourceWriterHtml.hs': '''{-# LANGUAGE OverloadedStrings, LambdaCase, FlexibleContexts #-}
-- Original Text.Pandoc.Writers.Shared HTML bodies, GPL-2.0-or-later.
-- Copyright (C) 2013-2024 John MacFarlane; upstream/ retains the notice.
module SourceWriterHtml where
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Set as Set
import Data.Text.Conversions (FromText(..))
import Text.DocLayout
import Text.Pandoc.Definition
import Text.Pandoc.XML (escapeStringForXML, rdfaAttributes, html5Attributes)
import SourceCss (cssAttributes)
''' + html_body,
}
p = argparse.ArgumentParser(); p.add_argument('--check', action='store_true'); args = p.parse_args()
for name, value in files.items():
    target = folder / name
    if args.check: assert target.read_text() == value, f'{name}: original body drift'
    else: folder.mkdir(parents=True, exist_ok=True); target.write_text(value)
print('Verified' if args.check else 'Extracted', 'unchanged CSS/trim and writer HTML bodies')
