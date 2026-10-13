# Original highlighting style oracle

This test-only executable calls unchanged Skylighting.Types and Skylighting.Styles
from the retained skylighting-core 0.15 release. PandocHighlightMethod.hs contains
the exact HighlightMethod declaration, patterns and JSON instances from pinned
Pandoc Options.hs, preceded only by module/import declarations. The fixture
importer checks that excerpt byte for byte. HighlightStyle.hs only adapts requests
and observes original JSON, Show, ordering and color conversion operations.

Build with GHC 9.12.2 and Cabal using the committed cabal.project.freeze:

```sh
cabal build highlight-style-reference --project-dir=tools/haskell-highlight-style-reference
cabal list-bin highlight-style-reference --project-dir=tools/haskell-highlight-style-reference
```

Pass the resulting executable to:

```sh
python3 tools/import-highlight-style-fixtures.py --oracle PATH
```

Capture also requires Pandoc 3.12.1 on PATH. Its actual --print-highlight-style
outputs independently verify every built-in theme and exercise name/file lookup.
CI verifies the retained source hashes and observations without requiring Haskell
or Pandoc. The Kotlin production code has no dependency on these executables.

JSON object member order is compared semantically. The distinct original
TokenType toJSON and toEncoding representations are both retained. Invalid raw
JSON must be rejected; exact Aeson syntax-error wording is retained as evidence
but not yet reproduced by Kotlin. Schema errors and Pandoc theme-lookup option
errors are compared exactly. This oracle does not cover the remaining tokenizer,
syntax parser, formatters, Binary/Read instances or Colour package conversions.
