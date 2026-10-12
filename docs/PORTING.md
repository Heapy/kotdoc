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
   binary. The current CLI supports JSON and Native with standalone/columns
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

Use the Native reader to load original AST expectations while porting subsequent
Pandoc readers/writers (starting with FB2 and HTML), alongside remaining Walk list
properties and complete upstream generator/shrinker coverage. Common options,
media handling and XML parsing must follow pinned Pandoc semantics.

Current verification: 1,582 Kotlin tests pass locally. Every push runs source
fixture verification, Kotlin tests and installed CLI smoke checks in
`.github/workflows/ci.yml`. Both source import scripts and the Native edge-case
oracle generator are committed for reproducibility.

## Completion gate

Completion requires evidence for the complete objective above, all mapped tests,
and resolution of behavioral gaps. A green Kotlin CI run only proves the slices
it actually exercises. Neither all tests being present as Haskell/Python nor all
current Kotlin tests passing establishes completion.
