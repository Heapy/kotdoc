# Original TeX data oracle

This executable compiles the complete `Text.Pandoc.TeX` and `Text.Pandoc.Sources`
modules directly from pinned Pandoc 3.12.1. No shadow modules, extracted algorithms
or production Kotlin are used to generate expectations. The JSON code constructs
original values and observes derived Eq/Ord/Show and the ToSources instance.
The reader's tokenization, macro expansion and xparse implementation are separate
upstream modules and remain pending; this oracle does not pretend to test them.

Use GHC 9.12.2 with the frozen dependencies:

```sh
cabal build --offline --builddir=/tmp/kotdoc-tex-build
cabal list-bin tex-reference --builddir=/tmp/kotdoc-tex-build
```

Pass the resulting binary to `python3 tools/import-tex-fixtures.py --oracle PATH`
from the repository root. `--check-source` verifies hashes, provenance and corpus
without requiring Haskell. Original TeX documents are Text fields in data-module
observations, not assertions that the full LaTeX reader suites have been ported.

The upstream module is GPL-2.0-or-later, Copyright (C) 2017-2024 John MacFarlane.
Its original source and all dependency notices remain pinned and retained.
