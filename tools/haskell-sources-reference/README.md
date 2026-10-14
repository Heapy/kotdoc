# Original Sources oracle

This executable compiles `Text.Pandoc.Sources` directly from the pinned Pandoc
submodule, without a shadow implementation or production dependency on Haskell.
It runs full Parsec replies over Sources, Text, [Char], strict ByteString and lazy
ByteString. It also observes the complete Sources representation and helpers,
all GHC Char classifications, controlled undefined tuple/list fields, and finite
prefixes of infinite inputs. The JSON code is test transport only. It also exercises the original Lists module
over Sources, using the previously retained two-field test-only ParserState
projection. This does not implement or test the full 31-field ParserState.

With GHC 9.12.2 and the frozen dependencies installed:

```sh
cabal build --offline --builddir=/tmp/kotdoc-sources-build
cabal list-bin sources-reference --builddir=/tmp/kotdoc-sources-build
```

Pass that binary to `python3 tools/import-sources-fixtures.py --oracle PATH` from
the repository root. `--check-source` needs no Haskell runtime: it checks every
retained source/input hash, case digest, operation inventory and source-document
transport. Source documents are inputs for Sources observations, not a claim
that their full reader test suites have been ported.

The upstream module is GPL-2.0-or-later, Copyright (C) 2021-2024 John MacFarlane.
The project keeps its original notices and the Parsec, GHC base, Text and Unicode
notices under `licenses/`. No upstream module is generated or extracted here.
