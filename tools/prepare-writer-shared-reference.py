#!/usr/bin/env python3
"""Retain exact original pure Writers.Shared bodies and extension accessors."""
import argparse,re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
w=(root/'upstream/pandoc/src/Text/Pandoc/Writers/Shared.hs').read_text()
o=(root/'upstream/pandoc/src/Text/Pandoc/Options.hs').read_text()
e=(root/'upstream/pandoc/src/Text/Pandoc/Extensions.hs').read_text()
decl=re.search(r'^data WriterOptions\b.*?deriving \([^)]*\)',o,re.M|re.S).group()
assert decl in (root/'tools/haskell-model-show-reference/PandocOptionsModels.hs').read_text()
body=w[w.index("-- | Returns 'True' iff the argument is an inline"):w.index('-- | Writes a grid table.')]
body+=w[w.index('-- | Tries to convert a character'):w.index('-- | Construct table of contents')]
body+=w[w.index("-- | Returns 'True' iff the list of blocks"):w.index('-- | Convert the relevant components')]
body+=w[w.index('splitSentences ::'):w.index('-- | Set translations based')]
body+=w[w.index('toTaskListItem ::'):w.index('-- | Determine whether all rows')]
access=o[o.index('class HasSyntaxExtensions'):o.index('data ReaderOptions')]
access+=o[o.index('instance HasSyntaxExtensions WriterOptions'):o.index('defaultMathJaxURL ::')]
access+=e[e.index('extensionEnabled ::'):e.index('enableExtension ::')]
header='''{-# LANGUAGE OverloadedStrings, FlexibleContexts #-}
-- Original Text.Pandoc.Writers.Shared bodies, GPL-2.0-or-later.
-- Copyright (C) 2013-2024 John MacFarlane; only module/import wiring changed.
module SourceWriterShared where
import Data.Char (chr,ord,isSpace,isLetter,isUpper)
import Data.List (groupBy)
import qualified Data.Set as Set
import qualified Data.Text as T
import Data.Text (Text)
import Control.Monad (MonadPlus,mzero)
import Safe (lastMay)
import Text.DocLayout
import Text.Pandoc.Definition
import Text.Pandoc.Walk (Walkable(..))
import PandocOptionsModels
'''
value=header+access+body
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
target=root/'tools/haskell-writer-shared-reference/SourceWriterShared.hs'
if args.check:assert target.read_text()==value,'pure writer helper source drift'
else:target.write_text(value)
print('Verified' if args.check else 'Extracted','unchanged pure writer helper bodies and extension accessors')
