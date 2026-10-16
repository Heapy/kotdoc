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

`JuicyPixels-tiff-tests-v3.3.8/` retains the complete `tests/tiff/` tree and
LICENSE from the same commit `11494bccce01183c9d04931d34c296afc5d8c5f8`.
It includes 68 TIFF images, the original libtiff image-description/provenance
files, a raw G3 fixture and the depth-image generator. The existing retained
`test-src/main.hs` selects 32 images for `tiffValidTests`. All 68 TIFF images
are checked for metadata against Pandoc; the original full pixel conversion
suite is still pending. The TIFF importer verifies all retained source/test
bytes independently of the GIF importer, preserving both existing manifests.

`JuicyPixels-png-tests-v3.3.8/` retains the complete `tests/pngsuite/` tree
and LICENSE from the same v3.3.8 commit above: 176 PNG files, original README,
PngSuite permission notice and .gitignore. PngSuite images are copyright
Willem van Schaik (1996, 2011) with permission to use/copy/modify/distribute for
any purpose without fee. The original suite selects 161 valid and 14 invalid
PNGs, leaving huge.png outside those lists. All 176 are probed through the
metadata fallback using explicitly recorded header transformations; their
original full-image tests still require the pixel codec. See docs/PORTING.md.

`zlib-0.7.1.1/` retains the complete Hackage source archive:
https://hackage.haskell.org/package/zlib-0.7.1.1/zlib-0.7.1.1.tar.gz
Archive SHA-256: `bf95ab01ed924be800addea195fba5ca97ec69f378368f6ff466bdc29666c1c1`.
This version is identified in exceptions from the original Pandoc executable.
The source is used to preserve lazy output/error forcing in PNG metadata, not
as a runtime dependency. Its original license is retained both in the source
and `licenses/zlib-LICENSE.txt`. The PNG importer hashes this tree independently
of the older GIF and TIFF fixture manifests.

`base64-bytestring-1.2.1.0/` retains the complete unchanged Hackage release,
plus provenance.json. The syntax-model milestone ports its strict standard
Base64 decoder for Skylighting RE metadata; other APIs and original tests remain
pending. `binary-re-0.8.9.3/` and `bytestring-re-0.12.2.0/` retain only the unchanged
source files needed to establish RE Binary/Show/Read behavior, with licenses and
archive provenance. Their full APIs/tests have not been ported. The independent
syntax fixture checker hashes these sources and every original Skylighting XML.


Future's Reader dependencies retain the complete original mtl 2.3.1 and
transformers 0.6.1.2 Hackage releases in mtl-future-2.3.1 and
transformers-future-0.6.1.2. Sources came from
https://hackage-content.haskell.org/package/mtl-2.3.1/mtl-2.3.1.tar.gz and
https://hackage-content.haskell.org/package/transformers-0.6.1.2/transformers-0.6.1.2.tar.gz.
The independent Future oracle builds these local packages and compiles the
unchanged pinned Pandoc Future module. All release file hashes are retained in
parsing/future.jsonl.gz; notices are copied byte-for-byte into licenses and the
core JAR. Neither Haskell nor these packages are production dependencies.


Text 2.1.2: complete source release retained from
https://hackage.haskell.org/package/text-2.1.2/text-2.1.2.tar.gz.
The TeX oracle uses this exact GHC-provided version. Its Monoid/concat singleton
lookahead explains a demand-order regression in token grouping. All 119 release
files retain original bytes, including tests and notices. LICENSE matches the
already retained licenses/text-BSD-2-Clause.txt byte for byte. This source retention
is not a claim of completion for the complete Text library and its tests.


HTTP request dependencies: `http-client-0.7.19/`,
`case-insensitive-1.2.1.0/` and `bytestring-http-0.12.2.0/` retain complete,
unchanged Hackage source releases, including their original tests. Each
provenance.json records the archive URL and SHA256. The request observer uses
these same versions from the original Pandoc frozen build. Existing
`http-types-0.12.6/`, `network-uri-2.6.4.2/` and `blaze-builder-0.4.4.1/` remain
unchanged. MIT and BSD notices are retained in the releases and licenses/.
The new corpus hashes every retained file. These are development/oracle
sources, not production Haskell dependencies; remaining transport/dependency
APIs and tests are listed in docs/PORTING.md.


`streaming-commons-0.2.3.1/` retains the complete unchanged Hackage release and
archive provenance.json. Its Data.Streaming.Zlib inflater and http-client
0.7.19 response readers supply the new incremental HTTP body algorithms.
The original MIT license is also in licenses/streaming-commons-MIT.txt.
The Kotlin inflater calls the system C zlib API through the already packaged
JNA dependency; no Haskell code is a production dependency. Whole streaming-
commons (including deflate/network/process/filesystem APIs and tests) is not
claimed complete; see docs/PORTING.md for the remaining full objective.

The four `http-client-0.7.19/publicsuffixlist/Network/PublicSuffixList/` modules
also supply the cookie-domain validation dependency. Their original 2015-04-29
embedded tree is retained byte-for-byte as a 60,137-byte production resource.
The development observer compiles those vendored modules directly; no source
algorithm is rewritten in the observer. Lookup, tree models and serialization
are translated to Kotlin. The original http-client MIT notice already ships
with the core JAR. HTTP manager/transport integration is still pending.

`cookie-0.5.1/` retains all six unchanged Hackage release files plus archive
provenance.json (SHA256 `1adde62726e3a3bbe147c02514fe0c4f5f2c5a2ff63842deac10c451a2a6f7a0`).
The original MIT notice is also copied byte-for-byte to licenses/cookie-MIT.txt
and the core JAR. Its complete Spec.hs supplies all nine native test families
and the original Arbitrary/property definitions for the Kotlin cookie corpus.
Date behavior is specialized from the already retained time-1.14 source. The
observer links those exact original versions and checks the actual cookie
archive SHA from its frozen Cabal plan. These sources remain development/oracle
inputs; production cookie behavior is Kotlin.

`iproute-1.7.15/` (16 original files) and `appar-0.1.8/` (nine original files)
retain complete unchanged Hackage releases and archive provenance.json.
Their archive SHA256 values are respectively
`18a331a7e0e6f9dc89a2da95577b0d76bd2690b8f832b72b46d6cc9b667b4ba5` and
`c4ceeddc26525b58d82c41b6d3e32141371a200a6794aae185b6266ccc81631f`.
Full BSD notices are also retained in licenses and the core JAR. The HTTP
cookie observer links these exact original archives. Kotlin specializes their
IPv4/IPv6 Read paths and used String parser combinators, including consumed-
failure alternatives, rather than substituting another IP parser. Generic
Data.IP/Appar APIs and their remaining suites stay pending. CookieJar sorting
reuses the unchanged Kotlin translation of `ghc-pretty-sort-9.12.2/OldList.hs`.

`crypton-2.0.1/` retains all 438 original files plus SOURCE.json for the complete
unchanged Hackage release used by the X509 public-key observer. Archive SHA-256:
`9e2fcd5c9326d85076cb48cee7fdd9add98384d9d9b79af0ccba0ae9d99c5914`.
The importer checks every retained file and the complete license against
`licenses/crypton-BSD-3-Clause.txt`, also packaged in the core JAR. The reference
build compiles this unchanged local package, including the real curve/EdDSA
public-key generation used by original test generators. Kotlin currently ports
the public key records, used byte-key smart constructors and RSA size/unsigned
integer conversions needed by Data.X509.PublicKey. This is not a claim that
crypton's remaining cryptographic operations or test suite have been ported.
