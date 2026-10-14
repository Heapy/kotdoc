#!/usr/bin/env python3
"""Extract unchanged grid and shared table bodies from pinned Writers.Shared."""
import argparse, re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
source = (root/'upstream/pandoc/src/Text/Pandoc/Writers/Shared.hs').read_text()
options = (root/'upstream/pandoc/src/Text/Pandoc/Options.hs').read_text()
decl = re.search(r'^data WriterOptions\b.*?deriving \([^)]*\)', options, re.M|re.S).group()
assert decl in (root/'tools/haskell-model-show-reference/PandocOptionsModels.hs').read_text()
body = source[source.index('-- | Writes a grid table.'):source.index('-- | Retrieve the metadata value')]
tables = source[source.index('-- | Determine whether all rows and their cells are empty.'):]
assert tables.rstrip().endswith('| otherwise = Just previousSpan')
header = '''{-# LANGUAGE OverloadedStrings, FlexibleContexts #-}
-- Original Text.Pandoc.Writers.Shared bodies, GPL-2.0-or-later.
-- Copyright (C) 2013-2024 John MacFarlane. Only module/import wiring changed.
module SourceGridTable where
import qualified Data.List as L
import Data.List (transpose, zipWith4, intersperse)
import Data.List.NonEmpty (NonEmpty(..))
import qualified Data.Map as M
import qualified Data.Text as T
import Data.Text (Text)
import Data.Maybe (fromMaybe)
import Safe (maximumMay)
import Text.DocLayout
import Text.Pandoc.Definition
import qualified Text.Pandoc.Writers.AnnotatedTable as Ann
import PandocOptionsModels
'''
# Expose the unchanged local recurrence for bounded numeric observations; this
# avoids rendering/allocating a table with out-of-Int64 specified widths.
local = body[body.index('  recalculateWidths finalRun'):body.index('  newwidths =')]
probe = '\nrecalculateWidthsProbe :: Int -> [ColWidthInfo] -> [ColWidthInfo]\nrecalculateWidthsProbe colsAvailable = recalculateWidths False (1 :: Int)\n where\n'+local
value = header+body+tables+probe
p = argparse.ArgumentParser(); p.add_argument('--check', action='store_true'); args = p.parse_args()
target = root/'tools/haskell-grid-table-reference/SourceGridTable.hs'
if args.check: assert target.read_text() == value, 'grid/table body drift'
else: target.write_text(value)
print('Verified' if args.check else 'Extracted', 'unchanged grid and shared table bodies')
