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
