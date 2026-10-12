# Porting ledger

## Objective

Create public Heapy/kotdoc, port all of Pandoc to Kotlin including all tests, then
port calibre MOBI support and its tests and the other formats absent from Pandoc.
This objective is not satisfied by wrapping upstream binaries, implementing only
an ebook subset, storing untranslated tests, or passing the currently ported tests.

## Baseline

The three `upstream/` Git submodules pin exact source revisions and include their
original test trees. Pandoc 3.12.1's cabal constraint accepts pandoc-types 1.23.1.2.
Kotlin code is built with Kotlin 2.4.21, Gradle 9.8.1, JDK 21, and
kotlinx.serialization-json 1.11.0. JSON syntax uses that library; Pandoc's wire
format is implemented explicitly in Kotlin.

## Completed slices

| Source | Kotlin | Evidence |
|---|---|---|
| pandoc-types `Text/Pandoc/Definition.hs` AST constructors | `core/.../ast/Pandoc.kt` | All node variants represented; JSON tests |
| Definition JSON instances | `core/.../ast/PandocJson.kt` | 56 upstream fixtures × encoding/decoding checks |
| `test/test-pandoc-types.hs` literal JSON examples | `UpstreamJsonTest.kt` | Independent typed expected values, exact JSON bytes |
| `prop_roundtrip` | `PandocJsonTest.kt` | 500 seeded recursive documents (not identical to upstream QuickCheck generator) |
| `Builder.hs` table normalization and simpleTable | `Tables.kt` | Both original examples, nine upstream properties with 500 samples each, independent occupancy oracle |
| `Walk.hs` traversal order and child selection | `PandocVisitor.kt` | Node transforms/queries checked against independent generic JSON traversal over upstream fixtures; list hooks and ordering checks |
| Builder constructors and inline concatenation | `Builder.kt` | Whitespace, boundary merging, composition, metadata conversion and original Figure property |
| Definition metadata, legacy figure, Format and integers | `Metadata.kt`, `Format.kt`, AST/JSON | Grouped author extraction, Unicode folding/order, 64-bit boundaries and exact numeric decoding |
| `Readers.Native` and `Writers.Native` | `formats/NativeReader.kt`, `NativeWriter.kt` | All 266 upstream `.native` files; reader, exact fragment/standalone layout, both upstream roundtrip properties over that corpus |
| GHC floating-point display dependency | `formats/HaskellDouble.kt` | Decimal specialization of GHC 9.12.2 `floatToDigits`; 517 numeric boundary/random values at eight widths |
| `Readers.FB2` document semantics and effects | `formats/FB2Reader.kt` | All six original exact Native goldens, all 14 tracked FB2 files and 180 independent oracle cases; AST, diagnostic ordering and binary bytes |
| `Writers.FB2` | `formats/FB2Writer.kt` | All three original examples; byte-exact XML and ordered diagnostics for all 266 Native documents and 205 edge cases |
| Shared writer helpers | `ast/DocumentShared.kt` | Original legacy-table and section examples, 500 section-idempotence samples, 471 independent section and 471 block-flattening comparisons |
| XML.Light output | `xml/XmlOutput.kt` | Original FB2 goldens and explicit pretty-print, raw content, CDATA and escaping checks |
| MediaBag and MIME tables | `resources/MediaBag.kt`, `MimeTypes.kt` | 2,172 original-Pandoc comparisons, including every MIME extension and reverse MIME type |
| Resource fetching foundation | `resources/ResourceUri.kt`, `ResourceAccess.kt` | 18 original data URI cases, source SVG fetch, local search paths, bag precedence and local HTTP integration |
| XML.Light parsing foundation and xml-conduit tokenizer dependency | `xml/XmlLight.kt` | Namespace-aware names, duplicate attributes, entity expansion, content forests, direct text and malformed input; oracle cases and six focused tests |

Fixture import is reproducible with `tools/import-pandoc-json-fixtures.py` and
checked against the pinned original by CI. A fixture is not considered covered
merely because decode/encode returns the same input: the decoded typed model is
also compared with an independently translated upstream expectation.

## Remaining scope

1. Finish pandoc-types semantics and tests: full walker/list property generators,
   structural ordering and full generators/shrinkers. Raw formats now use full
   Unicode case folding via ICU4J 78.1; AST integer fields use signed Long to match
   64-bit Haskell Int. Unicode database version parity remains to be audited
   against the actual upstream text library used by the pinned release.
2. Port Pandoc common infrastructure: options and extensions, parsing, sources,
   URI/XML handling, media bag, templates, resource embedding, errors, logging,
   Unicode, syntax highlighting, bibliography/citations, filters and scripting.
3. Port every Pandoc reader and writer and its Haskell, command, golden,
   Lua/filter, and integration tests. Preserve options and default behavior.
4. Port the full CLI/application behavior and test it against the pinned upstream
   binary. The current CLI supports JSON/Native/FB2 input/output, with standalone/columns
   options, not the complete Pandoc CLI contract.
5. Port calibre ebook infrastructure and MOBI reader/writer paths (MOBI6/KF8,
   containers, compression, metadata, navigation, images), with original tests
   and actual old-Kindle compatibility checks.
6. Enumerate and port calibre's additional input/output formats absent from
   Pandoc from its conversion registry, including their supporting algorithms
   and test suites. Do not assume the formats are limited to MOBI/AZW3.
7. Complete the test inventory and map every upstream test to Kotlin evidence,
   including tests embedded in source modules. Files retained in submodules do
   not constitute test ports. Track behavioral deviations explicitly and resolve
   them before claiming parity.
8. Validate cross-format conversion, CLI error behavior, corrupted inputs,
   Unicode, large documents, determinism, resource use, and release packaging.

## Native evidence and remaining limits

`tools/import-native-fixtures.py` uses Pandoc 3.12.1 as an independent oracle
for every tracked `test/**/*.native` source file (266 files). Each fixture stores
its original path/hash, decoded JSON, and fragment/standalone output. CI verifies
the complete source inventory and hashes without requiring Pandoc at runtime.
`tools/import-native-edge-cases.py` adds 28 acceptance/rejection cases and 48
width/standalone cases, covering 517 floating-point values and all ASCII control
characters plus Unicode. Expected output is never generated by Kotlin.

Native rendering ports the upstream layout algorithm, including Float-based
ribbon rounding, constructor parentheses, records, Unicode string escapes and
GHC decimal digit generation. Native Read preserves signed zero; Aeson JSON
reading normalizes zero and supports null/`+inf`/`-inf` special Double values.
The decimal helper derives from GHC 9.12.2 and retains its separate BSD notice.

Passing this corpus does not prove all Haskell Read syntax or all floating-point
bit patterns. The original randomized writer properties are exercised on the
fixture corpus; translating the complete upstream generators/shrinkers remains
part of the objective. Full Haskell derived Ord and unusual NaN equality behavior
also remain to audit; no claim of complete pandoc-types parity is made.

## Next execution step

Continue common media operations (fill/extract and data URI creation), full
options/logging infrastructure and HTTP/URI dependency semantics, then HTML and
the remaining formats. Also continue Walk list properties and complete upstream
generator/shrinker coverage. FB2's writer-specific math option is implemented;
the shared WriterOptions record and full CLI option handling are still pending.

Current verification: 5,411 Kotlin tests pass locally. Every push runs source
fixture verification, Kotlin tests and installed CLI smoke checks in
`.github/workflows/ci.yml`. All source import scripts and independent oracle generators are committed
for reproducibility. CI also checks installed CLI stdout/stderr for the complete
14-file FB2 corpus, including embedded binary images.

## FB2 evidence and remaining infrastructure

`tools/import-fb2-fixtures.py` captures the complete tracked FB2 corpus and
180 additional cases using Pandoc 3.12.1 plus a Lua oracle writer that reads the
original media bag. Kotlin tests compare document AST, ordered reader reports,
MIME types and decoded binary bytes. The six `Tests.Readers.FB2` tests also compare
the original Native goldens byte-for-byte. No upstream executable is used by the
Kotlin library or CLI. CI checks the complete FB2 source inventory and hashes.

The reader deliberately matches upstream omissions: tables are dropped, most
element IDs are ignored, and `xml:lang` does not match its unbound QName lookup.
Notes are parsed first and resolved in declaration order, not recursively after
all definitions. The reader's lazy ByteString Base64 decoder skips non-alphabet bytes, including
padding. Pandoc's strict ByteString decoder used for data URI resources stops at
padding; these distinct upstream behaviors are preserved.

`ReaderResult` records `insertMedia` requests in order, before MediaBag's
canonicalization, MIME/path inference and extraction policy. MediaBag now applies
that inference, and the CLI transfers reader media to the FB2 writer. There is
still no media extraction option. Default warning output is implemented;
general logging options and exact XML error messages/positions remain pending.

The FB2 writer preserves upstream evaluation order and quirks, including notes
in metadata, nested-note state replacement, reverse image insertion order,
missing-image replacement scope, raw XML handling and case-sensitive data URI
image MIME policy. Expected output comes only from the original Pandoc binary.
`tools/import-fb2-writer-fixtures.py --check-source` verifies the entire source
inventory, hashes and independently captured input ASTs. Kotlin does not invoke
Pandoc at runtime or during ordinary tests.

Resource runtime tables contain 1,025 entries from mime-types 0.1.2.2 followed by
184 Pandoc overrides, plus 268 known URI schemes. Their full dependency source,
MIT license and import script are retained. Earlier mime-types tables differ for
JavaScript; the 0.1.2.2 mapping matches the pinned oracle across the entire table.
Resource tests cover percent-encoded octets, MIME parameters, padding behavior,
path canonicalization, hash-derived extraction names, redirects, relative URLs,
custom headers, gzip and missing MIME/HTTP status failures.

This does not establish complete PandocIO or network-uri parity. JVM URI parsing
still needs a complete upstream dependency test corpus. HTTP proxy environment
handling, certificate options, exact upstream IO/HTTP error strings and all
request behavior remain to port. Media fill/extract operations, shared logging
and options, further Shared helpers and full XML output/error tests also remain.
The local HTTP test verifies the implemented boundary behavior; it is not a
substitute for those upstream tests.

The XML layer derives from Pandoc's XML.Light and xml-conduit 1.10.1.0
[`Text.XML.Stream.Parse`](https://hackage-content.haskell.org/package/xml-conduit-1.10.1.0/src/src/Text/XML/Stream/Parse.hs)
and `Token.hs`, retaining the package MIT notice. It implements the parser path
needed here, not every XML.Light traversal/rendering helper or xml-conduit API.
It preserves custom entity maps, internal entities, the 8192-byte expansion
limit, namespace-sensitive QName matching and immediate-only `strContent`.
External DTDs/entities are not fetched, matching upstream. XML dependency test
coverage beyond the exercised cases and all error-location fidelity remain to
be completed; this is not a claim of complete XML infrastructure parity.

## Completion gate

Completion requires evidence for the complete objective above, all mapped tests,
and resolution of behavioral gaps. A green Kotlin CI run only proves the slices
it actually exercises. Neither all tests being present as Haskell/Python nor all
current Kotlin tests passing establishes completion.
