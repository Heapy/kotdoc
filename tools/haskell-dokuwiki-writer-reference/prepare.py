#!/usr/bin/env python3
"""Compile the exact pinned writer body under an isolated module name."""
from pathlib import Path
import hashlib
ROOT=Path(__file__).resolve().parents[2]
source=ROOT/'upstream/pandoc/src/Text/Pandoc/Writers/DokuWiki.hs'
assert hashlib.sha256(source.read_bytes()).hexdigest()=="e0601efac714a840467d1f10a1bcf961457e3c1e61c21fa88a9fa40bfdb4ef06", "Pinned writer source changed"
text=source.read_text()
old='module Text.Pandoc.Writers.DokuWiki ( writeDokuWiki ) where'
assert text.count(old)==1
Path(__file__).with_name('SourceDokuWiki.hs').write_text(text.replace(old,'module SourceDokuWiki ( writeDokuWiki ) where').replace('import Text.Pandoc.Class.PandocMonad (PandocMonad, report)', 'import Text.Pandoc.Class (PandocMonad, report)').replace('import Text.Pandoc.URI (escapeURI, isURI)', 'import SourceUriPosix (escapeURI, isURI)'))
print(hashlib.sha256(source.read_bytes()).hexdigest())
