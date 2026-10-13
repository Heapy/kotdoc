# Additional upstream dependency sources

`mime-types-0.1.2.2/Network/Mime.hs` comes from the Hackage source release:
https://hackage.haskell.org/package/mime-types-0.1.2.2/mime-types-0.1.2.2.tar.gz

It supplies the complete default MIME table accepted by Pandoc 3.12.1's
`mime-types >= 0.1.1 && < 0.2` constraint. The 0.1.2.2 table matches the
independent Pandoc 3.12.1 oracle, including `js`/`mjs` as `text/javascript`.
The source is MIT licensed; see `licenses/mime-types-MIT.txt`.

`tools/import-resource-tables.py` imports this table in ascending extension
order, then the overrides from the pinned Pandoc `Text.Pandoc.MIME` source.
It also imports the known URI schemes from `Text.Pandoc.URI`.
Use `--check` to verify generated Kotlin runtime resources without a network
connection or an installed Pandoc executable. The original Haskell source is
retained for attribution and reproducibility; it is not used at runtime.

`zip-archive-0.5/Zip.hs` is the original `src/Codec/Archive/Zip.hs` from:
https://hackage.haskell.org/package/zip-archive-0.5/zip-archive-0.5.tar.gz

Pandoc 3.12.1 requires `zip-archive >= 0.5 && < 0.6`. The Kotlin media ZIP
writer ports the serialization path used by `extractMedia`: `toEntry`,
`addEntryToArchive`, local/central headers and the end record. This is not yet
a port of the complete ZIP library. The source is BSD-3-Clause licensed;
see `licenses/zip-archive-BSD-3-Clause.txt`. Expected complete ZIP bytes are
generated independently by Pandoc, and decompression is also checked with JDK ZIP.

`emojis-0.1.5/` is the complete original source release from:
https://hackage.haskell.org/package/emojis-0.1.5/emojis-0.1.5.tar.gz

This version is explicitly pinned by Pandoc's stack.yaml. Its alias list retains
source order; the base and ZWJ lists contain Unicode Emoji 17.0 sequences. Only
sequences with aliases participate in `replaceEmojis`, matching the original
trie. The seven original test assertions are translated in IdentifiersTest.
`tools/import-emoji-tables.py` produces runtime JSON directly from `emojis.inc`.
The complete upstream license (BSD-3-Clause and GitHub gemoji MIT) is retained in
`licenses/emojis-BSD-3-Clause-and-gemoji-MIT.txt` and in the source directory.

`text-2.1.3/` retains the original CaseMapping.hs, Fusion/Common.hs, package
manifest and Tests/Properties/Text.hs from:
https://hackage.haskell.org/package/text-2.1.3/text-2.1.3.tar.gz

This version satisfies Pandoc's `text >= 2.0 && < 2.2` constraint. The generated
case tables use Unicode 16.0 and match the independent pinned binary across all
Unicode scalars for lower/upper. `tools/import-case-mappings.py` unpacks the
original 21-bit character representation; it does not approximate these tables
using the host Unicode version. The original uppercase/casefold exception
baselines are separately imported from the tests. BSD-2-Clause attribution is
retained in `licenses/text-BSD-2-Clause.txt`. Only these case operations are
ported so far; retaining sources does not implement all of Haskell text.


`tagsoup-0.14.8/` is the complete original source release from:
https://hackage.haskell.org/package/tagsoup-0.14.8/tagsoup-0.14.8.tar.gz

This version satisfies Pandoc's `tagsoup >= 0.14.6 && < 0.15` constraint.
Generated.hs delegates to Manual.hs, whose implementation uses Specification.hs
and Implementation.hs. The finite parser and entity operations are translated
into Kotlin; the remaining dependency APIs and lazy-input tests are recorded
in docs/PORTING.md. `tools/import-html-entities.py` imports all 2,231 entities,
and `tools/import-tagsoup-fixtures.py` retains hashes for every release file
and transcribes the original parsing/position/entity expectations. The original
BSD-3-Clause license is retained here and in `licenses/tagsoup-BSD-3-Clause.txt`.


`ghc-9.12.2/` retains the numeric Read, decimal rendering and IEEE integer-rounding
sources read for the Kotlin port, from tag `ghc-9.12.2-release` at:
https://github.com/ghc/ghc/tree/ghc-9.12.2-release

`GHC/Internal/` files come from `libraries/ghc-internal/src/GHC/Internal/`,
`GHC/Num/Integer.hs` from `libraries/ghc-bignum/src/GHC/Num/Integer.hs`, and
`rts/StgPrimFloat.c` keeps its original path. Original copyright headers and the
BSD notices in `LICENSE.txt` are retained. These are reference sources, never
runtime dependencies. Only the numeric paths recorded in docs/PORTING.md have
been translated; this is not a complete GHC/base port. The image-dimension
fixture checker verifies these source hashes together with the Pandoc sources.

`JuicyPixels-3.3.9/` is the complete original source release from:
https://hackage.haskell.org/package/JuicyPixels-3.3.9/JuicyPixels-3.3.9.tar.gz

Archive SHA-256: `3e44ac5d3e684b65e9efaf60ca9a907a86edc879dfcf63f86eebc721e542864d`.
This satisfies Pandoc's `JuicyPixels >= 3.1.6.1 && < 3.4` constraint; it is not
claimed to be the exact dependency linked into the independent oracle binary.
The archive has no test tree. Its cabal Source-Repository this tag is v3.3.8;
there is no corresponding v3.3.9 Git tag. The GIF implementation (including LZW)
is identical to v3.3.8 after CRLF normalization, but other modules have real
changes, so these releases must not be treated as entirely interchangeable.

`JuicyPixels-tests-v3.3.8/` retains the original `test-src/main.hs`, all 12
`tests/gif/` files, and LICENSE from Git commit
`11494bccce01183c9d04931d34c296afc5d8c5f8` (tag v3.3.8):
https://github.com/Twinside/Juicy.Pixels/tree/v3.3.8

The original license is BSD-3-Clause, also retained in
`licenses/JuicyPixels-BSD-3-Clause.txt`. The GIF fixture importer verifies hashes
of every retained source and test file. Metadata expectations come independently
from Pandoc, never Kotlin. Retained frame/animation/palette/encoder tests have
not yet been translated or passed by the metadata-only port; see docs/PORTING.md.
