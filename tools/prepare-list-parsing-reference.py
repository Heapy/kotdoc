#!/usr/bin/env python3
"""Verify unchanged source functions and the test-only state projection."""
import argparse,re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
r=root/'tools/haskell-list-parsing-reference'
s=(root/'upstream/pandoc/src/Text/Pandoc/Shared.hs').read_text()
w=(root/'upstream/pandoc/src/Text/Pandoc/Writers/Shared.hs').read_text()
state=(root/'upstream/pandoc/src/Text/Pandoc/Parsing/State.hs').read_text()
lists=(root/'upstream/pandoc/src/Text/Pandoc/Parsing/Lists.hs').read_text()
assert set(re.findall(r'\bstate[A-Z]\w*',lists))=={'stateNextExample','stateExamples'}
assert re.search(r'stateNextExample\s+:: Int',state) and re.search(r'stateExamples\s+:: M.Map Text Int',state)
assert re.search(r'stateNextExample\s+= 1',state) and re.search(r'stateExamples\s+= M.empty',state)
assert not (r/'Text/Pandoc/Parsing/Lists.hs').exists() and not (r/'Text/Pandoc/Sources.hs').exists()
shared="""-- Unchanged safeRead/safeStrRead from Pandoc 3.12.1, GPL-2.0-or-later.
-- Copyright (C) 2006-2024 John MacFarlane.
module Text.Pandoc.Shared (safeRead) where
import Control.Monad (MonadPlus,mzero)
import Data.Char (isSpace)
import qualified Data.Text as T
"""+s[s.index('safeRead ::'):]
marker="""-- Original Writers.Shared body, GPL-2.0-or-later.
-- Copyright (C) 2013-2024 John MacFarlane.
module SourceMarker where
import Data.Text (Text)
import qualified Data.Text as T
import Data.Either (isRight)
import Text.Parsec (runParser,eof)
import Text.Pandoc.Parsing.Lists (anyOrderedListMarker)
import Text.Pandoc.Parsing.State (defaultParserState)
"""+w[w.index('isOrderedListMarker ::'):w.index('toTaskListItem ::')]
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
for name,value in [('Text/Pandoc/Shared.hs',shared),('SourceMarker.hs',marker)]:
 if args.check:assert (r/name).read_text()==value,(name,'source drift')
 else:(r/name).write_text(value)
print('Verified unchanged safeRead, marker predicate and exact two-field access inventory; Lists/Sources compile directly from upstream')
