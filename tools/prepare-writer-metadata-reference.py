#!/usr/bin/env python3
"""Retain original metadata functions and their stringify dependencies verbatim."""
import argparse, re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
out=root/'tools/haskell-writer-metadata-reference'
writer=(root/'upstream/pandoc/src/Text/Pandoc/Writers/Shared.hs').read_text()
shared=(root/'upstream/pandoc/src/Text/Pandoc/Shared.hs').read_text()
options=(root/'tools/haskell-model-show-reference/PandocOptionsModels.hs').read_text()
original=(root/'upstream/pandoc/src/Text/Pandoc/Options.hs').read_text()
decl=re.search(r'^data WriterOptions\b.*?deriving \([^)]*\)',original,re.M|re.S).group()
assert decl in options
body=writer[writer.index('-- | Create template Context'):writer.index('-- | Produce an HTML tag')]
lookups=writer[writer.index('-- | Retrieve the metadata value'):writer.index('-- | Tries to convert a character')]
assert 'lookupMetaBool ::' in lookups
stringify=shared[shared.index('stringify ::'):shared.index('-- | Bring all regular text')]
header='''{-# LANGUAGE OverloadedStrings, FlexibleContexts, MultiParamTypeClasses #-}
-- Original Text.Pandoc.Writers.Shared metadata functions, GPL-2.0-or-later.
-- Copyright (C) 2013-2024 John MacFarlane. Module/import wiring only is changed.
module SourceWriterMetadata where
import qualified Data.ByteString.Lazy as BL
import Data.Aeson (ToJSON(..), encode)
import qualified Data.Map as M
import qualified Data.Text as T
import Data.Text (Text)
import Data.Text.Conversions (FromText(..))
import qualified Text.Pandoc.Builder as Builder
import Text.Pandoc.Definition
import PandocOptionsModels
import Text.DocLayout
import Text.DocTemplates (Context(..), Val(..), TemplateTarget, ToContext(..), FromContext(..))
import qualified Text.Pandoc.UTF8 as UTF8
import Text.Pandoc.Walk (Walkable(..))
'''
value=header+body+lookups+stringify
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
target=out/'SourceWriterMetadata.hs'
if args.check:assert target.read_text()==value,'metadata body drift'
else:out.mkdir(parents=True,exist_ok=True);target.write_text(value)
print('Verified' if args.check else 'Extracted','original metadata/context and stringify bodies')
