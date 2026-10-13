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
| `fillMediaBag`, `extractMedia`, `writeMedia` and Lua media API operations | `resources/MediaOperations.kt` | Every original Haskell MediaBag and Lua media API example translated to Kotlin; independent extraction/AST/report oracles and recoverable/fatal error checks |
| `SelfContained.makeDataURI` | `resources/ResourceUri.kt` | 56 independent MIME/UTF-8/BOM/CR/binary cases, including original rejection of invalid text UTF-8 |
| zip-archive media serialization dependency | `resources/MediaZip.kt` | Complete ZIP-byte comparison against Pandoc (headers, CRC, compression, order, collisions); independent JDK decompression |
| XML.Light parsing foundation and xml-conduit tokenizer dependency | `xml/XmlLight.kt` | Namespace-aware names, duplicate attributes, entity expansion, content forests, direct text and malformed input; oracle cases and six focused tests |
| `Extensions` and format specifications | `options/Extensions.kt`, `ExtensionName.kt`, `FlavoredFormat.kt` | All 90 built-ins, six presets, all default/supported tables, set/diff operations and JSON; 96 registry, 140 extension-read, 317 path, 17 native-spec and 14 actual custom-reader oracle cases plus original Lua examples and algebra checks |
| `Options.ReaderOptions` and independent option types | `options/ReaderOptions.kt`, `OptionTypes.kt`, `MathMethod.kt` | All ten reader fields/defaults and JSON; original construction/copy examples; 297 forced original option-codec cases and 13 genuine JSON-filter environment captures, plus field/type/integer/tuple checks |
| `Chunks` TOC functions, SecInfo and PathTemplate; `Writers.Shared.toTableOfContents` | `structure/TableOfContents.kt`, `PathTemplate.kt` | All seven original Haskell TOC examples; 532 comparisons over all 266 Native documents, 232 edge cases, 16 original chunk TOC trees and 35 actual original chunk paths |
| Shared identifiers, Asciify, Pandoc Emoji and emojis-0.1.5 public operations | `ast/Identifiers.kt`, `Asciify.kt`, `Emoji.kt` | All seven dependency assertions and four original Lua examples; 1,913 alias ASTs, 3,953 emoji sequences, 40 edge/collision cases and every Unicode scalar in four identifier modes |
| Haskell text full lower/upper/casefold mapping | `ast/UnicodeText.kt`, generated source tables | Independent lower/upper comparisons for every scalar; 1,585 original Format equality comparisons; original exception baselines, Cherokee, ASCII, length and idempotence properties |
| TagSoup finite HTML parsing and entity dependency | `html/TagSoup.kt`, `HtmlTag.kt`, `HtmlEntities.kt` | All 53 original parser/position and 12 entity assertions, original warning example, 1,000 seeded option-property inputs across eight modes, all 2,231 HTML5 names, 3,670 independent Pandoc ID-discovery captures |
| `Chunks` splitting, reference rewriting, TOC paths and navigation; Chunk/ChunkedDoc walkers and queries | `structure/Chunks.kt` | 4,598 independent complete-output cases (all 266 Native documents twice, 3,670 raw HTML cases and section/metadata/boundary cases), complete finite navigation snapshots and explicit traversal/scope checks |
| `Slides.getSlideLevel` and `prepSlides` | `structure/Slides.kt` | 549 original-Pandoc level results and 2,745 prepared-section results over every Native fixture and generated edges; direct branch, signed-boundary and deep/long traversal tests |
| `ImageSize` type detection, PNG/JPEG/EMF/WebP/AVIF header scanners and intrinsic size arithmetic | `images/ImageHeaders.kt` | All 19 original type examples and original cases for these five scanners; 3,189 independent type results, 779 sizes and 1,780 failures, plus integer/DPI/deep-container checks |
| `ImageSize` dimensions, units, desired sizes, SVG/EPS/PDF parsers | `images/Dimensions.kt`, `DocumentImageSize.kt`, GHC numeric helpers | All remaining original SVG/EPS/PDF/numUnit examples; 962 original LaTeX dimension renderings, 752 sizes, 975 errors, one process-level UTF-8 exception and five detection-only cases; arithmetic, equality and attribute checks |
| JuicyPixels GIF metadata consumer path | `images/GifMetadata.kt` | 4,980 independent Pandoc results: 2,274 sizes and 2,706 exact GIF-component failures, all 12 dependency GIF files plus Pandoc's GIF; direct signature and long-input tests |
| JuicyPixels TIFF metadata consumer path | `images/TiffMetadata.kt` | 8,390 original outcomes: 1,263 sizes, 7,123 exact TIFF-component errors and four arithmetic exceptions; all 68 retained TIFFs, format matrices, nested Exif, duplicate/overlap/endian cases and direct type-gate/cancellation tests |
| JuicyPixels PNG fallback metadata consumer | `images/PngMetadata.kt` | 6,329 independent captures: 4,425 sizes, 1,858 exact PNG-component errors, 45 uncaught exceptions and one explicitly non-equivalent unsafe-memory case; all 176 retained PNG inputs probed through fallback |
| JuicyPixels shared bit readers/writers | `images/ImageBitStream.kt` | 12,853 direct original-Haskell captures: 10,245 reader/state operations and 2,608 writer sequences, including widths through 256, JPEG markers, alignment, stuffing and 256 KiB output boundaries |
| JuicyPixels JPEG Huffman and quantisation helpers | `images/JpegHuffman.kt`, `JpegDefaultTables.kt` | 5,450 direct Haskell captures: trees, packed arrays, inverse codes, sequential symbols, all default tables, complete Int16 coefficient domain at 13 qualities; three JVM bounds/cancellation tests |
| JuicyPixels JPEG block/IDCT helpers | `images/JpegBlocks.kt` | 14,233 direct original-Haskell captures of numerical results, mutable source/destination states, coefficient reader continuations, pixel placement and parameter diagnostics; four explicit bounds/domain/cancellation tests |
| JuicyPixels JPEG binary structures and ECS | `images/JpegBinary.kt` | 20,043 original Haskell captures: reads, writes, Show/size, parser errors/offsets/remainders, malformed headers/tables and chunk-sensitive ECS behavior |
| JuicyPixels typed TIFF/Exif and JPEG APP1 | `images/TiffTypes.kt`, `TiffBinary.kt`, `JpegExif.kt` | 16,177 original Haskell captures: tags, full nested fields, binary get/put, exact errors/offsets, writer allocation/order and APP1; reused by the existing TIFF metadata consumer |

Fixture import is reproducible with `tools/import-pandoc-json-fixtures.py` and
checked against the pinned original by CI. A fixture is not considered covered
merely because decode/encode returns the same input: the decoded typed model is
also compared with an independently translated upstream expectation.

## Remaining scope

1. Finish pandoc-types semantics and tests: full walker/list property generators,
   structural ordering and full generators/shrinkers. Raw formats now use full
   Unicode case folding via Haskell text's Unicode 16 mapping; AST integer fields
   use signed Long to match 64-bit Haskell Int. Remaining Unicode dependencies
   still require their complete original suites, beyond the exhaustive scalar
   case-conversion and identifier comparisons now present.
2. Port Pandoc common infrastructure: options, parsing, sources,
   URI/XML handling, media bag, templates, resource embedding, errors, logging,
   Unicode, syntax highlighting, bibliography/citations, filters and scripting.
3. Port every Pandoc reader and writer and its Haskell, command, golden,
   Lua/filter, and integration tests. Preserve options and default behavior.
4. Port the full CLI/application behavior and test it against the pinned upstream
   binary. The current CLI supports JSON/Native/FB2 input/output, with standalone/columns
   and media extraction/resource-path options, not the complete Pandoc CLI contract.
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
`tools/import-native-edge-cases.py` adds 42 acceptance/rejection cases and 48
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

Continue full options/logging infrastructure and HTTP/URI dependency semantics,
then HTML and the remaining formats. Also continue Walk list properties and complete upstream
generator/shrinker coverage. FB2's writer-specific math option is implemented;
the shared WriterOptions record and full CLI option handling are still pending.

Start the options layer from `Text.Pandoc.Extensions`, `Text.Pandoc.Options`
and `Text.Pandoc.Format`: preserve custom extensions, set ordering, complete
format defaults/support tables, extension-diff parsing and validation, all
ReaderOptions fields/JSON/defaults and WriterOptions fields/defaults. WriterOptions
depends on template/context, highlighting and syntax-map types; these dependencies
must be ported rather than replacing the record with a subset. CLI `App.Opt` is
a separate layer. The upstream writer math default is MathML (also used by the
current FB2 writer options); FB2 renders non-WebTeX methods identically.

Current verification: 127,916 Kotlin tests pass locally. Every push runs source
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
that inference, and the CLI transfers reader media to the FB2 writer. The CLI
now also supports media extraction and resource paths. Default warning output is implemented;
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
request behavior remain to port. Shared logging and options, further Shared helpers and full XML output/error
tests also remain. The original platform-specific IO exception text is not yet
replicated; typed IO/resource/HTTP failures now preserve fill/extract control flow.
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


## Media operations and CLI evidence

`MediaOperations.fill` follows the original bottom-up walk, including metadata,
captions, notes and image descriptions. Existing keys are skipped. Recoverable
IO, HTTP and missing-resource failures produce the original placeholder Span
attributes and warning messages; unrelated failures propagate.

Extraction writes every bag item, rewrites only matching Image targets, and
preserves the distinction between directory creation failure (fatal) and file
write failure (reported and ignored). Empty bags create no output. A lowercase
`.zip` suffix creates an archive and image links refer to its containing
directory. Directory output and ZIP deliberately differ when multiple keys
have the same extraction path: directory writes leave the last item, while
zip-archive's right fold keeps the first. Both behaviors have independent tests.

`tools/import-media-operations.py` captures 56 data URI creation cases and 11
fill/extract scenarios with full expected ASTs, ordered reports and output file
bytes. ZIP serialization retains zip-archive 0.5's BSD attribution and source.
The media writer preserves zero-time DOS clamping, UTF-8 flags, CRC, deterministic
entry order, and compression only when it reduces size. The full zip-archive
API, all its own tests, large/streamed binary representations and complete
compression dependency parity remain part of the remaining work.

`tools/check-media-cli.py` checks 66 independent original CLI runs: all 14 FB2
source files converted to JSON and FB2 with both directory and ZIP extraction,
plus ten resource-path/repeated-option cases. It compares stdout, stderr and
every output file byte. `--resource-path` retains the default current directory,
prepends each later flag's components, and preserves empty-component behavior.
This is coverage of these options for the implemented formats, not complete
Pandoc application/CLI or Lua runtime parity.

## Extension and format evidence

The full extension registry is generated from pinned `Extensions.hs`, and its
default/supported sets are independently compared to the original binary's Lua
API for 96 names. Set and JSON order follow Haskell constructor order, including
the non-alphabetical positions of table attributes and raw Markdown. Custom
constructors remain distinct from built-ins even when their displayed names match.
Extension reading retains the exact `lhs` alias and Haskell Read trailing-space
behavior. Tables are source-verified in CI; runtime code does not invoke Pandoc.

Format parsing preserves case when a filename extension is present, performs
context-independent Unicode lowercasing otherwise, and accepts empty custom
extension names. Parsing repeated `+`/`-` modifiers keeps both sets; applying
them enables an extension present in both. Composing two diffs instead gives
later changes precedence. Unsupported extensions are reported in constructor
order. Real upstream custom-reader executions supply independent expectations.

Path inference covers all 66 original mappings, first-recognized selection,
`.lhs` modifiers, manual-page suffixes and URI paths. The original unreachable
`.Rmd` entry is deliberately preserved. This path uses the current JVM URI
adapter: complete network-uri dependency semantics and cross-platform FilePath
behavior still require their upstream corpora, as described above.

All original `pandoc-format.lua` examples are translated into Kotlin API tests.
This does not implement the Lua runtime. The installed CLI accepts modifiers
for the three implemented formats, including `--from=`/`--to=`, and matches
69 original CLI executions in stdout, stderr and exit status. Complete CLI
format inference/defaults, reader/writer dispatch, options and scripting remain.
Full WriterOptions and its dependencies remain common infrastructure work; no
reduced WriterOptions placeholder is counted as a port.

## Reader and common option evidence

ReaderOptions includes extensions, standalone, columns, tab stop, indented-code
classes, abbreviations, default image extension, track changes, comment stripping
and ordered Typst input pairs. Its library defaults are independently captured
through `pandoc.utils.run_json_filter`, whose default filter environment exposes
the original Aeson encoding as `PANDOC_READER_OPTIONS`. Thirteen captures also
exercise application configurations. Library defaults (80 columns, 28
abbreviations) deliberately differ from application defaults (72 columns and the
external abbreviation data file). These captures do not mean that the complete
application option pipeline has been ported.

The reader record JSON requires all fields, ignores unknown fields, validates
types and pairs, and uses signed 64-bit integers. Explicit Kotlin values are
compared with independently captured JSON, rather than only testing round trips.
Record decoding follows the upstream `deriveJSON` declaration; complete Aeson
dependency coverage and exact nested JSON error paths/messages remain unfinished.

The common citation, email-obfuscation, track-changes, wrapping, top-level-division,
reference-location and caption types preserve JSON aliases and canonical output.
MathMethod retains all six variants, optional/default URLs and its intentionally
noninvertible empty-URL encoding. HTMLSlideVariant expectations are translated
from its JSON deriving declaration; they are not presented as binary-oracle cases.
EPUBVersion has no JSON instance upstream and none is invented here.

`tools/import-option-fixtures.py` forces Lua option userdata back through the
original constructors before checking results: merely assigning and reading a
Lua property bypasses the deferred Haskell decoder. The 297 codec cases cover
acceptance, canonical values and available decoder error messages. This remains
a Kotlin API port, not a Lua runtime implementation.

`--math-method` and the original deprecated math flags are connected to FB2Writer.
Eighty-five original CLI captures compare full output, warnings, errors and exit
status, including repeated flags, empty URLs and real inline/display math. The
new option's Haskell-Show-to-JSON path intentionally rejects some Unicode/control
URLs that are valid through the JSON API or deprecated URL flags, matching the
pinned implementation. No network calls are needed to run these comparisons.

Full WriterOptions still requires Template, Context, HighlightMethod
and Skylighting Style/SyntaxMap dependencies. ReaderOptions integration into all
readers, complete CLI option validation, full generic Haskell Read/Show behavior
and scripting tests also remain part of the overall objective.

## Table of contents and path template evidence

`toTOCTree` preserves section order, uses Div identifiers and Header number
attributes, unwraps a Div containing a single Div (#8402), and excludes unlisted
headers only when no number attribute exists. An empty number remains present.
`tocToList` preserves absolute heading-depth filtering (including dropping the
whole subtree of a filtered parent), explicit chunk paths and empty identifiers.
Title cleanup removes nested Links and Notes through the original inline-list
walk semantics, including citation affixes and image descriptions. SecInfo's walk
visits only its title. The root is a structural container, not a TOC item.

`tools/import-toc-fixtures.py` captures two TOCs for every accepted original
Native fixture, all seven Haskell TOC examples and the Lua block-list example,
plus numbered/unlisted/depth/attribute/inline cases and seeded section trees.
Sixteen independently captured chunk TOC trees exercise `tocToList` against the
original Lua chunk examples. These isolate TOC behavior using original Pandoc for chunk creation; full Kotlin
splitting is separately verified by the Chunks corpus described below. A direct typed tree
test separately checks the complete SecInfo values and wrapper behavior.

PathTemplate replaces `%i`, `%h`, `%s`, then `%n`, so substituted values may
introduce placeholders consumed by later replacements. Slash and backslash
removal happens last. Thirty-five original split operations verify actual paths,
including substitutions containing placeholders, Unicode and chunk numbers
crossing the three-digit width. Signed integer boundary formatting and the
string JSON shape are tested from the source contract; exact Aeson error wording
and generic Haskell Show remain part of the existing dependency gaps.

Chunks splitting, raw-HTML identifier discovery, reference rewriting and
navigation snapshots are now ported and tested below. Full WriterOptions
integration and the Lua runtime remain; no smaller WriterOptions record is
substituted.

## Identifiers, emoji and Unicode case evidence

Identifier generation preserves the original default/GFM punctuation and
whitespace rules, ASCII filtering after NFD decomposition, Turkish undotted-i
handling, leading-nonletter removal, and the exact Span shape used for emoji
aliases. Raw emoji replacement occurs in Str nodes only when GFM or ASCII
identifiers are enabled; Code and Math are stringified without that replacement.
Unique identifiers retain the `section` fallback, choose the first free numeric
suffix, and intentionally allow repeats after suffix 60000 is exhausted.

The complete emojis-0.1.5 release is retained with its tests and attribution.
All 1,913 aliases are checked against original Markdown emoji parsing, including
the exact Span AST. All 3,953 distinct sequences from the alias, base and ZWJ
tables are checked through the original identifier API in four modes. Lookup is
case-sensitive, alias lists preserve source order, and replacement consumes the
longest complete code-point match without rescanning callback output. The
public base/ZWJ inventories include sequences without aliases, but the original
replacement trie only contains aliased sequences. Tables exposed to callers are
immutable. The source importer is verified in CI without network access.

`tools/import-identifier-fixtures.py` captures every Unicode scalar in 272
batches, placing each between ASCII anchors inside Code to bypass emoji
replacement. These independently verify filtering and casing in all four
identifier modes; separate inline and collision cases cover the transformations
and stateful boundaries. Fixtures are losslessly gzip-compressed with zero mtime.
They are test data only; no production behavior delegates to Pandoc.

The exhaustive corpus found ICU 78 lowercasing Unicode-17 letters that the pinned
Pandoc leaves unchanged. Lower/upper/casefold now use generated text-2.1.3 tables
from Unicode 16, including multi-character expansions and context-independent
sigma handling. This also fixes Format equality/hashing/literal construction,
format-specification lowercasing and shared capitalization. Original Lua lower
and upper operations independently check every scalar. All 1,557 folding entries
plus 28 new-letter pairs are checked with the original RawInline Format equality.
Original Haskell exception baselines, Cherokee behavior and scalar-based length,
idempotence and ASCII properties supplement these comparisons.

Full Haskell text, title-case behavior, unicode-transforms dependency tests,
generic Trie operations and all parser/writer use sites remain in the full
remaining scope. The three `4742.md` identifier expectations are represented by
the identifier oracle cases; the actual CommonMark reader and Markdown emoji
reader/writer command suite still await those format ports. These helpers do not
provide reader-side identifier registration; document splitting now uses them
through the Chunks port below.

## TagSoup HTML parsing evidence and remaining dependency scope

The complete tagsoup-0.14.8 Hackage release is vendored with BSD-3-Clause notices.
It satisfies Pandoc's dependency constraint. Generated.hs delegates to Manual.hs,
which composes Specification.parse with Implementation.output; the Kotlin parser
translates those state transitions and output assembly directly. It preserves
case, original attribute order and duplicates, unquoted slashes, permissive tag
and declaration syntax, malformed comment recovery, script end-tag boundaries,
CDATA literals and the exact whitespace classes. It is separate from the
XML.Light/xml-conduit parser used by FB2.

Parse options include entity data/attribute callbacks, optional warnings and
positions, and the original fast (unmerged text) mode. Text merging retains the
first text position and delays intervening warnings until after the merged run.
Close-tag attribute/self-closing warnings and attribute callback output preserve
the original ordering and warning-option filtering. Position accounting uses
Unicode code points, LF line breaks and eight-column tabs; CR remains ordinary.
Iterative transitions and attribute loops handle long finite inputs without
character-by-character JVM recursion.

All 2,231 named entities are imported from the original source without changing
semicolon distinctions. An independent Python standard-library HTML5 table
checks every name through lookup, text parsing and attribute parsing. Numeric
references accept ASCII decimal/hex digits, preserve the original NUL/C1 values,
and produce '?' outside the Unicode range. The standalone lookup instead
returns null outside that range. Numeric strings with arbitrarily many leading
zeros remain valid. Haskell String-compatible parsing retains surrogate values;
`parseTextTags` follows Pandoc's Data.Text replacement semantics, including two
adjacent surrogate references becoming two replacement characters.

`import-tagsoup-fixtures.py` strictly transcribes all original parser, position
and entity examples without running Kotlin. The original warning example and
option invariants are translated as tests, with 1,000 reproducible generated
inputs over all eight option combinations. Additional typed cases cover
callbacks, warning order, Unicode positions, declaration attributes, recovery
states and long inputs. `import-html-id-fixtures.py` captures 3,670 results from
original Pandoc's raw-HTML reference rewriting: all entities, malformed/duplicate
attributes, declarations, script/CDATA/comment hiding, numeric boundaries and
seeded damaged HTML. These are independent identifier-discovery comparisons;
they are not independent comparisons of the entire token or warning stream.

The finite parser API is used by the Chunks reference-rewriting port. TagSoup's
renderer, matching/combinator/tree APIs, Haskell generic instances, alternate
ByteString representations and lazy/infinite-input tests remain unported.
The current API materializes finite tokens and strings; its long-input checks
do not substitute for the original infinite-input laziness guarantees. Pandoc's
HTML reader/writer and remaining parser use sites remain in the full objective;
this dependency port does not claim those features.


## Chunks splitting, references and navigation evidence

`Chunks.splitIntoChunks` preserves the original pipeline: makeSections, postorder
section ID allocation, section/preamble splitting, TOC path resolution, internal
reference rewriting, then the three navigation passes. Chunk fields retain the
heading copy, identifier, signed level/number, optional section number, path,
unlisted state, full contents and up/prev/next snapshots. A deque implements the
original subsection-before-remainder order without repeatedly copying suffixes.
Defaults match the original Lua structure API; the core also accepts explicit
path templates, section numbering, base heading levels and chunk levels.

Missing IDs are allocated only for Divs with first class `section` and a first
Header. Children reserve IDs before parents. Explicit duplicate IDs remain
possible; reference and TOC maps choose the earliest chunk. Preamble headings
come from docTitle, with the original identifier suffix and path section number
`0`. Empty documents produce no chunks, and the root TOC path is still `#`.

Internal reference collection uses precisely the original node kinds. Div,
Header, Table, Span, Link and Image IDs participate; Code, CodeBlock, Figure and
table row/cell attributes do not. Raw HTML participates only for exact stored
format spellings `html`, `html4`, `html5`, despite general Format equality being
case-insensitive. TagSoup finds the first lowercase `id` attribute and preserves
its decoding/malformed-markup rules. Metadata IDs do not populate the map, but
links in metadata are rewritten. Only Link targets beginning with `#` change;
image targets and other URLs remain intact. The separate Chunk heading and TOC
title copies intentionally retain their original links.

Navigation follows adjacent levels exactly, including leaving `up` empty after
a level decrease instead of searching for an earlier ancestor. `prev` points to
the preceding result of the up pass; `next` points to the following result of the
prev pass. These are finite immutable snapshots, not cyclic links to the final
chunks. Tests recursively check every navigation edge and every snapshot's base
fields/content against independent original captures. Chunk walks visit contents
only. ChunkedDoc walks metadata before chunks; queries visit chunks before
metadata. Neither walks nor queries traverse the TOC or navigation copies.
SecInfo's inline query visits only its title, matching its walk scope.

`tools/import-chunk-fixtures.py` captures 4,598 full outcomes from Pandoc 3.12.1:
all 266 Native documents in two modes, all 3,670 raw HTML identifier cases through
actual chunk rewriting, original Lua examples, generated hierarchies, number and
class combinations, preamble metadata representations, duplicate IDs, every
relevant/excluded attribute location, exact format spelling and signed integer
boundaries. Original document JSON is transported as text, and extreme options
as decimal strings, to avoid Lua JSON floating-point coercion. Results include
metadata, every chunk field and contents, the whole TOC tree/list and all finite
navigation snapshots. Tests compare typed AST values so equivalent decimal and
exponent JSON spellings of table widths do not create false failures; raw Format
spellings are checked separately because their general equality folds case.
Fixtures are gzip-compressed with zero mtime and source/input hashes checked in
CI. Direct tests also check 1,001 sibling chunks and traversal side-effect order.

This ports the finite Kotlin structure API. It does not yet provide the Lua
runtime/userdata API, generic Haskell Show/Data/Generic instances, the chunked
HTML writer, EPUB packaging, templates or their full CLI/application integration.
Those remain in the complete original objective and require their own tests.


## Slide structure evidence

`Slides.getSlideLevel` starts at six, accepts only a Header immediately followed
by a non-Header/non-HorizontalRule block, and consumes that following block when
the level lowers the current minimum. This includes consuming a following Div
without inspecting it. A Div reached by the normal traversal forks two searches
using the same incoming minimum: its contents and the remaining siblings.
The Kotlin implementation uses an explicit branch stack to preserve this detail;
feeding the first result into the sibling search changes the original behavior.
Other containers, such as BlockQuote and lists, are not recursively searched.

`prepSlides` extracts a heading only from a final top-level Div whose identifier
is exactly `refs`, preserving the Div's other attributes and contents. It then
replaces top-level horizontal rules with NUL-title Headers, or drops a rule
immediately before a Header at exactly the requested slide level. A leading
Header or direct Div child Header at or below that level suppresses the initial
NUL-title Header. Nested horizontal rules remain untouched. Empty input produces
one placeholder header. The low-level API accepts the supplied signed level
literally; automatic inference for nonpositive `slide_level` is a separate policy
in the Lua structure wrapper.

`tools/import-slide-fixtures.py` captures the original `slide_level` and
`make_sections` APIs for all 266 Native fixtures, explicit adjacency, bibliography
and separator cases, and seeded nested block lists: 549 inference results and
five preparation/numbering/base-level combinations per input. The composition
checks compare complete typed block trees after `prepSlides` and `makeSections`.
They are not represented as direct binary-oracle captures of the intermediate
`prepSlides` list; direct source-contract tests cover that list and each recovery
branch separately. Signed boundaries and 20,000 sibling/10,000 nested Divs test
the finite Kotlin implementation. The gzip JSON-lines fixture is consumed
incrementally to keep the test corpus from accumulating in memory.

The slideshow writers (including Beamer, HTML slide formats and PowerPoint),
their templates, original format goldens/CLI tests and Lua runtime integration
remain pending. In particular, testing the original PowerPoint Native inputs
through these shared helpers does not constitute porting the PowerPoint writer.


## Image header evidence and remaining integration

`ImageHeaders` ports the complete image-type detector and the original PNG,
JPEG, EMF, WebP and AVIF header scanners from `Text.Pandoc.ImageSize`. These are
low-level scanners, not a replacement for the complete `imageSize` dispatcher.
The `ImageSize` value preserves arbitrary-precision Integer fields, the original
300×200 at 72 DPI default, pixel pairs, ordered floating-point point conversion,
and the dispatcher’s separate replacement of nonpositive DPI with 72.
WebP/AVIF take the DPI value directly (default 96); this does not introduce a
reduced WriterOptions record or count the full WriterOptions port as complete.

Detection checks byte signatures in source order, including all JPEG APP markers,
TIFF byte orders, AVIF/AVIS major brands, the restricted SVG BOM case and permissive
`<svg`/`<SVG` prefix search after an XML declaration. PNG scans IHDR and pHYs
without decoding pixels, checking CRCs, or consuming data after IDAT/IEND. Dots per
meter use integer flooring. JPEG handles padding, payload-free markers, all SOF
variants, repeated JFIF/Exif segments, both TIFF byte orders and first matching
IFD tags. Exif overrides JFIF, and rational resolution is floored before conversion
from centimeters. A malformed later IFD tag invalidates that Exif record even if
earlier tags contain resolution. EMF uses signed frame bounds, unsigned reference
device values and arbitrary-precision products with truncation toward zero.

WebP covers VP8, VP8L and VP8X with the exact magic, flag and bit-field rules.
AVIF preserves the upstream container whitelist, ordinary/zero/64-bit box sizes,
ispe, both tkhd time layouts, stsd sample entries and direct av01 entries. Source
permissiveness is intentional: dimension payload parsers can read beyond a box’s
declared length, and exhausting an inner search range resumes unbounded scanning
without restoring the parent range. The implementation translates tail calls to
an iterative loop; a separate 20,000-container case verifies stack independence.
Header parse failures catch only the private bounds/structure failure, not general
runtime errors or resource failures.

`tools/import-image-header-fixtures.py` captures `pandoc.image.format` and
`pandoc.image.size` from the pinned original binary. All 19 Haskell type examples
are present, alongside every original PNG/JPEG/EMF/WebP/AVIF scanner example and
the original JPEG/PNG files. The original GIF file is present for type detection
only; its size test remains pending with the decoder dependency. Additional cases
cover every truncation of the synthetic original headers, all 256 JPEG marker
signatures, duplicate/invalid resolution records, signed/unsigned bounds, extreme
DPI, AVIF range/length failures and 1,200 seeded payload mutations. The captures
contain 3,189 type results, 779 successful size results and 1,780 errors. For the
five ported formats, matching detected types are checked against complete original
size results after DPI normalization. Synthetic PNG/JPEG failures contain no pixel
stream, so these cases do not substitute for testing a successful full-decoder
fallback. Lua dimensions and DPI travel as decimal strings to avoid JSON double
rounding. Original literal size expectations additionally validate the oracle.
CI checks the fixture importer/upstream source hashes and original image bytes.

The subsequent dimension/vector milestone below ports the unit API and SVG,
EPS and PDF parsers. The later GIF/TIFF sections record their metadata ports.
Still pending in this module: JuicyPixels full image decoding and PNG/JPEG fallback, complete original tests
for those decoder branches, full WriterOptions integration, and the Lua runtime
and writer/application callers. No production code invokes the oracle executable.


## Dimensions and document-image evidence

`Dimensions` ports all eight Dimension constructors, Direction spelling,
`numUnit`, `lengthToDim`, first-attribute lookup, scaling, inch/point/em/pixel
conversion, display helpers and `desiredSizeInPoints`. Pixel values stay
arbitrary-precision. Numeric prefixes are restricted to ASCII digits and a dot;
signs, exponents, leading whitespace and trailing unit whitespace are rejected
by length parsing even though the general safeRead numeric helper accepts a
broader grammar. Percent dimensions produce empty display conversions and are
ignored when choosing desired sizes. A supplied single dimension uses the pixel
aspect ratio, not the ratio of physical dimensions at independently encoded DPI.

GHC numeric reference sources are retained verbatim under `vendor/ghc-9.12.2`.
`HaskellNumber` follows numeric Read literal syntax (including decimal, binary,
octal, hexadecimal, parentheses, negative signs, NaN and Infinity). Its Integer
flooring decomposes IEEE bits rather than narrowing to Long or saturating at JVM
integer bounds. Scaling pixels rounds to even through rint, then converts to
Integer. This also preserves the original enormous non-finite Integer results;
they are not silently replaced by zero or an exception. Dimension equality uses
IEEE equality: signed zeros are equal, NaN is unequal even to itself. Kotlin's
default data-class floating-point equality would differ.

GHC fixed formatting rounds the existing GHC shortest decimal digits with
half-even ties, rather than rounding the exact binary fraction as BigDecimal
constructed directly from a Double would do. It retains negative zero, finite
large/subnormal values and the original non-finite spellings. `showFl` then
removes trailing zeros and a trailing decimal point. The seven floating unit
constructors use that display except Percent, which uses ordinary GHC Double
Show. Direct tests cover conversions, rounding, signed zero, constructor equality,
huge integers, invalid-first duplicate attributes and desired-size fallback.

`DocumentImageSize.svgSize` reuses the XML.Light port and unqualified, first
attribute lookup. Width/height fall back independently to viewBox after comma
replacement and Haskell text word splitting. Parsing requires all four numeric
viewBox fields but only floors the dimensions actually needed. The byte path
removes up to two initial BOMs (the two upstream functions each remove one), then
removes all CR bytes before strict UTF-8 decoding. XML errors return no size;
invalid UTF-8 propagates an exception, as the original does. The XML tokenizer's
existing U+FEEF handling is unchanged; it is distinct from the byte-level BOM path.

EPS scans only initial percent-prefixed lines and uses the first BoundingBox
prefix, without trying a later box after malformed coordinates. Its coordinates
are arbitrary-precision signed integers. ByteString.Char8.words treats byte 0xa0
as whitespace in addition to ASCII whitespace. PDF's Attoparsec whitespace does
not; captures for all 256 single-byte separators exposed and verify this
otherwise easy-to-miss difference. PDF reads four unsigned decimal coordinates,
floors each before subtracting origins, and returns the first successful result.
Compressed object streams use zlib and the exact stream-line/endstream rules.
An explicit work stack preserves parser alternative ordering: decompressed data,
following input, then backtracking to scan raw input after the original slash.
Malformed/truncated zlib input fails without leaking a decompressor exception.

`tools/import-image-dimension-fixtures.py` records 2,695 independent original
outcomes: 962 LaTeX dimension renderings, 752 successful sizes, 975 size errors,
one process-level UTF-8 exception and five detection-only cases. LaTeX capture
checks just the image width option that exposes dimension Show/showInInch/showFl;
it does not claim to port or test the complete LaTeX writer. Size results use
`tostring` on the original fields, which preserves integers beyond 64 bits; tests
compare the complete BigInteger values. The invalid UTF-8 case runs in its own
original process because Lua pcall does not intercept that Haskell exception.
Original literal expectations also validate the oracle. Tests include every
original SVG/EPS/PDF/numUnit example, decimal/unit/whitespace boundaries, partial
and duplicate attributes, namespaces, extreme DPI, BOMs, byte separators,
compressed/raw/nested/corrupt streams and 350 seeded dimension pairs. Source
hashes cover the importer, Pandoc implementation/tests, Lua marshaling, LaTeX
consumer and retained GHC reference files.

The same numeric audit exposed missing binary literals in NativeReader. Both
Int and Double positions now accept 0b/0B forms, including negative values and
machine-width wrapping. Fourteen additional original acceptance/rejection
captures cover these paths. Native reader assertions compare typed AST values
so equivalent exponent spellings (e+21 versus E21) are not mistaken for parser
failures. Existing byte-exact Native writer goldens remain unchanged.

The complete imageSize dispatcher still needs the JuicyPixels decoder dependency
and its errors/metadata tests. Full WriterOptions, original Lua scripting,
writer/application callers, generic Haskell Read/Show for ImageSize and wider
dependency test suites also remain pending. Passing these helpers does not
constitute porting the SVG, EPS or PDF document reader/writer formats.

## GIF metadata dependency evidence

`GifMetadata.read` ports the forced path of JuicyPixels `decodeGifWithMetadata`
when the consumer only reads metadata, as Pandoc's `getSize` does. It validates
the six-byte signature, logical screen, global/local color table lengths, image
descriptors, LZW root byte, data sub-block framing, graphic controls, comment,
plain-text, application and unknown extensions. All later blocks must parse and
a trailer must exist; bytes after the trailer are ignored. A file with no image
fails. Width/height come from the logical screen rather than the first frame;
resolution defaults to 72 independently of the requested writer DPI.

The Haskell image tuple component is lazy. Metadata does not force LZW decoding,
color substitution, palette-index checks or animation composition. Consequently
zero-sized/out-of-screen frames, invalid LZW roots, empty or corrupt compressed
pixel streams and missing color maps can still yield metadata. Kotlin preserves
this behavior instead of eagerly rejecting those inputs through a different
image library. Graphic-control size/terminator bytes are consumed but ignored,
unknown extensions use ordinary sub-block parsing, and unknown block errors
include the original one-based consumed position. Truncation produces the
original `not enough bytes` error. Iterative loops handle long frame/sub-block
sequences without using the JVM call stack or allocating pixel buffers.

`tools/import-gif-metadata-fixtures.py` captures 4,980 original Pandoc outcomes:
2,274 sizes and 2,706 failures. The complete generic decoder error aggregates
remain in the fixtures, but this stage compares only their exact GIF component;
it does not pretend to implement the remaining generic decoder branches. Cases
cover every global/local flag byte, LZW root, graphic-control flag/size/terminator,
block marker and extension label, both accepted versions, invalid signatures,
every truncation of six structural seeds, dimensions/offsets at 0 and 65535,
sub-block boundaries, later-frame corruption, trailer suffixes, DPI independence
and 1,000 seeded mutations. All 12 original dependency GIF files and Pandoc's
`bodybg.gif` are tested for metadata. Two additional direct tests cover decoder
signatures hidden by Pandoc's type gate and 20,000-frame/sub-block traversal.

The complete JuicyPixels 3.3.9 source archive and BSD license are retained.
Pandoc accepts this version, but the oracle's exact linked dependency version is
not known. The package advertises the older v3.3.8 source tag and omits tests.
Original GIF sources match that tag after line-ending normalization; the separate
test directory records the tag's exact commit and retains `test-src/main.hs`
and all GIF fixtures. Its 26 encoder rejection assertions, generated animation
and palette tests, and full multi-frame pixel decode/conversion checks remain
pending. Passing metadata on their files is not counted as passing those tests.

The following TIFF section records the next metadata port. Further dependency
work is PNG/JPEG fallback, the other generic decoder
branches and complete error aggregation, followed by connecting the original
imageSize dispatcher. GIF pixel decoding/encoding and the broader dependency
tests remain explicit work. WriterOptions, Lua and writer consumers and the
overall all-Pandoc-then-Calibre scope are unchanged.

## TIFF metadata dependency evidence

`TiffMetadata.read` ports the forced metadata consumer path of JuicyPixels
`decodeTiffWithPaletteAndMetadata`, including `Tiff.Internal.Types` parsing,
`TiffInfo` field validation, the Either-producing `unpack` guards, and the
width/height/DPI part of `Tiff.Internal.Metadata`. It returns raw resolution;
Pandoc's existing `ImageSize.checkDpi` subsequently normalizes nonpositive DPI.
It is not a TIFF pixel decoder, encoder, or a general Exif metadata API.

The parser reads one IFD, consumes but ignores the next-IFD pointer, stably sorts
entries by their raw unsigned offsets, and only then normalizes big-endian
single SHORT values. Extended data shares one forward-only cursor. Backward or
out-of-file references become ExifNone, whereas a valid starting offset with a
truncated payload fails. Nested Exif directories are parsed with the same cursor
and their extra post-cleanup sort; an explicit stack preserves this order without
using the JVM call stack. Inline SHORT pairs, strings and undefined bytes,
external SHORT/LONG vectors, signed/unsigned rational values, ignored types and
Exif-directory redirects follow the upstream cases. Rational payloads explicitly
use little endian in the source, even for a big-endian TIFF; this is preserved.

Structural tags are checked in the original applicative order before returning
metadata. Defaults for planar configuration, sample formats, subsampling and
predictors remain distinct from mandatory tags. The strict color/depth/sample
format guards include palette, RGB/RGBA, mono/alpha, CMYK and YCbCr branches and
the original 64-bit and white-is-zero failure messages. Pixel stripping,
decompression, predictor execution and color substitution remain lazy in the
original pure metadata path and are not run here. In particular, absent pixel
strips can yield metadata; unsupported color/depth combinations still fail.
The original file-based JuicyPixels reader deepseqs the image and has a different
forcing boundary; its pixel tests are not claimed by this port.

Validation uses first matching tags in the offset-sorted list. Width/height
metadata uses the last match because its metadata union is right-biased. DPI
uses the first resolution/unit tags, requires the original SHORT unit values,
and integer-divides the rational before converting centimeters to inches.
Missing, backward, overlapping or non-rational resolutions default independently.
Zero denominators propagate arithmetic exceptions when the selected unit requires
DPI; the original exception escapes Lua pcall and is captured in a separate
process. The original zero-row/zero-height unpack recursion does not terminate;
the Kotlin loop preserves that behavior but supports thread interruption with a
CancellationException instead of making that pathological image succeed.

`tools/import-tiff-metadata-fixtures.py` captures 8,390 independent outcomes:
1,263 sizes, 7,123 parser/format errors and four arithmetic exceptions. Complete
generic decoder error aggregates are retained, while tests compare their TIFF
component. Cases include all 68 images in the retained upstream TIFF tree (the
original suite selects 32 of them), both byte orders, missing and malformed tags,
color/depth/component/sample-format matrices, palette shapes, planar YCbCr,
resolution types/units/duplicates/shared offsets, scalar dimensions with unusual
types, every truncation of eight structural seeds, next-pointer/header-offset
behavior, all 256 bytes in optional-tag error rendering, deep/nested/truncated
Exif, and 1,000 seeded field mutations. Source hashes cover the original library,
original test source and entire TIFF fixture tree. Two direct tests check decoder
errors hidden by Pandoc's type gate and cancellation of the nonterminating case.

The following PNG section records the next metadata port. Further dependency
work includes JPEG fallback and all other generic
decoder branches/error aggregation, complete imageSize dispatch, actual pixel
codecs and their original tests, writer integration, full WriterOptions and Lua.
The original TIFF full-image decode/re-encode tests, TIFF encoding, broad Exif
metadata operations are not counted as implemented. The retained raw G3 fixture
is not counted as a TIFF test. The full
Pandoc and Calibre objective remains unchanged.

## PNG fallback dependency evidence

`PngMetadata.read` ports JuicyPixels' `decodePngWithPaletteAndMetadata` as forced
by Pandoc's metadata consumer. It parses the signature, IHDR and all chunks
through the first IEND; bytes after IEND are ignored. The source deliberately
ignores IHDR's declared length and CRC, and reads compression/filter methods as
unvalidated bytes. Color and interlace codes are checked in their original order.
All subsequent chunk CRCs are validated, including unknown chunks and IEND.
CRC diagnostics preserve the unsigned computed/given values. IDAT chunks are
concatenated, and compressed length <= 6 fails before palette or metadata work.
The first PLTE determines whether indexed input has a valid palette; zero-length
palettes are accepted, and a later valid PLTE cannot repair an invalid first one.

The original `toImage` wraps its pixel thunk inside Right, so ordinary grayscale,
RGB, grayscale-alpha and RGBA return metadata without decompressing pixels.
Grayscale depths 1/2/4 route through the palette path. Indexed/palette metadata
forces the deinterlacer and therefore the complete zlib stream, except when all
scanline passes are empty (zero width/height or depth zero). Indexed depths above
8 fail only after the demanded decompression; invalid nonzero depths below 8
throw the original `Impossible bit depth` exception when the unpacker is forced.
The Kotlin metadata consumer computes the lengths of ordinary/Adam7 passes and
checks their input bounds. It does not materialize discarded pixel vectors or
execute filter arithmetic whose values cannot affect metadata. Unknown scanline
filter bytes select filterNone in the original, and are accepted here as well.
This is not an implementation of the public PNG pixel decoder or encoder.

Resolution uses the first pHYs only: truncated fields are ignored, unit 1 converts
unsigned pixels-per-meter with integer arithmetic, other units use 72 DPI.
`read` returns raw DPI; Pandoc's separate `checkDpi` normalizes zero values.
Textual metadata keys/values are not exposed by this API, but their forcing
behavior matters. Valid zTXt headers force the first lazy decompressed output
constructor. Invalid text headers are ignored. zlib emits produced bytes before
reporting a trailing error, so truncated/checksum-invalid textual streams can
still yield dimensions; streams failing before any output propagate exceptions.
The retained zlib 0.7.1.1 source documents this behavior explicitly in `finish`.
Full IDAT forcing checks the tail, checksum and missing dictionaries. Trailing
bytes after a completed zlib stream are ignored. The implementation uses the
JDK inflater, with independently checked original error text, and no external
converter at runtime.

The independent importer records 6,329 cases: 4,425 sizes, 1,858 decoder errors,
45 uncaught zlib/bit-depth exceptions and one unsafe-memory case. The complete
original decoder error aggregates are retained; tests compare the PNG component.
The one unsafe-memory input has a valid compressed empty stream and nonempty
indexed dimensions. JuicyPixels performs unchecked scanline reads; the captured
original process exits with SIGSEGV. Kotlin instead throws a dedicated
`TruncatedScanlineException`. This is an explicit bounded behavior difference,
not a matching upstream exception or a claim to reproduce undefined memory reads.
The same guard applies to other insufficient decompressed scanlines. The source
fixture remains executable and its observed original signal is retained.

All 176 original PNG files are preserved verbatim in a separate vendor tree.
The original suite lists 161 valid and 14 invalid files; the tree also includes
huge.png. Oracle probes change the ignored IHDR length to zero to force Pandoc's
fallback instead of its permissive header scanner. For signature-invalid files,
the first four bytes are additionally set to PNG's type prefix; the remaining
signature bytes remain unchanged. These exact transformations are recorded and
reapplied by the tests, and original file hashes are independently verified.
These are fallback-consumer probes, not translations of the full-image original
PNG tests, which force pixels and still require the complete pixel codec.

Additional captures cover all byte-sized color/interlace/compression/filter/unit
codes, lazy bit-depth combinations, palette sizes/duplicates, empty passes, all
256 row filters in both interlace modes for low-depth grayscale/indexed inputs,
unsigned dimension/resolution boundaries, CRC order, split IDAT, duplicate and
malformed ancillary chunks, textual forcing, dictionary/checksum/truncation
failures, every truncation of four seeds, and 1,000 seeded structural mutations.
A direct test exercises signature errors hidden by Pandoc's type gate. Source
hashes cover the full retained JuicyPixels and zlib trees, original test source,
PNG fixture tree, Pandoc consumer and importer. Total PNG Kotlin tests: 6,330.

Remaining work includes JPEG and the other generic decoder branches, their exact
aggregate diagnostics and full imageSize dispatch; public PNG pixels, encoding,
broad textual metadata APIs and original full-image tests; writer consumers,
full WriterOptions and Lua. The all-Pandoc-then-Calibre objective is unchanged.

## Image bit-stream dependency evidence

`ImageBitReader` and `ImageBitWriter` translate `Codec.Picture.BitWriter` from
JuicyPixels 3.3.9. This is shared infrastructure for the actual JPEG, GIF and
other pixel codecs. It does not replace JPEG's forced entropy decode with a
header-only implementation. The original module has no isolated assertions in
the retained `test-src/main.hs`; its callers are exercised by full-image tests,
which still need the corresponding codecs.

Readers preserve generic LSB and MSB state, JPEG MSB bits/signed Int32 fields,
reset operations, JPEG byte alignment, and EOF zero extension. JPEG skips every
FF/nonzero pair, not only restart markers; FF/00 yields one literal FF byte.
Marker chains are handled iteratively. The original initial empty JPEG state
has bit index zero, unlike a later empty reset's index seven; a trailing FF has
its own index-seven behavior. Independent copied continuations provide the
observable immutable-state reuse of `runBoolReaderWith` and `execBoolReader`.
Input byte arrays are owned copies, corresponding to immutable ByteString input.

Readers accept nonnegative bit counts, including corrupt-field widths exceeding
32. The original 64-bit GHC Word32 shift path performs a machine-word shift
before narrowing. For wide LSB fields this differs from Kotlin UInt's modulo-32
shift count. The port uses 64-bit shifts/narrowing, tested for every width 33–256.
The JPEG Int32 accumulator retains its signed overflow behavior. Negative read
counts are rejected as outside the supported domain; the original unchecked
negative-count paths can diverge or invoke undefined shifts. Writers accept the
original documented widths 1–32, plus the original effective no-op width zero;
other widths are explicitly rejected instead of promising undefined shifts.

The writer preserves MSB JPEG output, FF/00 stuffing, LSB GIF output, low-bit
masking, partial-byte padding, and operations interleaved on the shared state.
Original finalization flushes a partial byte without clearing the accumulator;
repeated finalization and later writes retain that behavior. Kotlin returns the
same flattened bytes without exposing Haskell's internal lazy ByteString chunk
representation. Large oracle cases cross both sides of the original 256 KiB
buffer boundary, including an FF byte/stuffing pair crossing the boundary.

The test-only `tools/haskell-reference/BitStream.hs` adapter imports and executes
the original retained module unchanged. Its Cabal executable compiles that file,
`VectorByteConversion` and `Types` directly from the vendor tree, using GHC 9.12.2,
vector 0.13.2.0 and primitive 0.9.1.0. A checked-in Cabal freeze file pins its
reference dependencies and Hackage index state. This enables direct tests of
internal codec helpers that Pandoc's Lua API does not expose. The temporary
compiler/tool/cache installation is outside the repository; no Haskell binary
or external converter is needed by Kotlin at runtime or by ordinary CI tests.
The reference README records commands and verified official archive checksums.

`tools/import-image-bitstream-fixtures.py` captures 12,853 independent command
sequences: 10,245 reader cases and 2,608 writer cases. Coverage includes every
single input byte with boundary widths, offsets 0–7 and widths 0–32, widths
33–256, every FF marker follower at six alignment positions, state reset and
save/restore, EOF, 10,000-marker chains, 1,000 seeded reader programs, all writer
widths with extreme Word32 values and partial prefixes, repeated finalization,
eight large buffer-boundary cases and 1,000 seeded mixed writer programs.
Small output arrays are retained verbatim; large outputs use independent length
and SHA-256 comparisons. Source hashes cover the importer, reference adapter,
configuration/freeze file and full retained JuicyPixels tree. CI verifies both
source and result hashes without executing an oracle or generating expectations
from Kotlin. All 12,853 comparisons pass. Dynamic test closures retain source lines and parse
JSON on execution, rather than retaining every JSON tree. The complete 72,006
case suite exceeds Gradle's default 512 MiB worker heap, so the test task now
explicitly allows 1 GiB; no cases are excluded or skipped.

Remaining JPEG work includes frame/state parsing,
baseline and progressive scan decoding, integration of block decoding, color-space
and Exif metadata, complete fallback/dispatcher integration and original pixel
tests. Original GIF LZW/animation/encoder tests and the remaining Pandoc/Calibre
scope are still open. A full transfer is not inferred from this dependency port.


## JPEG Huffman and quantisation evidence

`JpegHuffman.kt` and `JpegDefaultTables.kt` translate the operations and constants
of JuicyPixels 3.3.9 `Codec.Picture.Jpg.Internal.DefaultTable`: DCT component
selection, tree construction, all four default DC/AC trees and source tables,
512-entry packed decoding arrays, inverse writer codes, bit-driven symbol
lookup, both default quantisation matrices, quality scaling and macro-block
construction. These are internal helpers for the remaining JPEG codec, not a
claim of complete image decoding or encoding.

The original left-first insertion order, partially empty trees, duplicate
symbols and oversubscribed-tree failure are preserved. Packed decoding always
consumes a bit, including a root leaf or empty tree. Its empty flag takes
precedence over its leaf flag; leaf payloads use the low eight bits. Continuation
checks compare 32 bits after decoding each symbol sequence, rather than only
checking decoded symbols. Inverse writer codes retain wrapping Word8 depths and
Word16 codes. The upstream inverse array really has 255 entries: symbol 255
raises an index error, and repeated symbols are overwritten in traversal order.

Quality calculations use signed 64-bit wrapping multiplication followed by
Haskell floor division and clamping to 1–255. Negative quality is treated as
zero. Input coefficients retain the full signed Int16 domain, including zero
and negative values. `makeMacroBlock` truncates to 64 elements without padding a
short list; this was verified directly against vector 0.13.2.0.

`tools/haskell-jpeg-reference` compiles the unmodified vendored DefaultTable,
BitWriter, Types and VectorByteConversion modules using the frozen GHC 9.12.2,
vector 0.13.2.0 and primitive 0.9.1.0 environment. The adapter only selects source
operations, serializes outputs and forces exceptions. It does not compute
expectations itself. `tools/import-jpeg-table-fixtures.py` retains 5,450 captures:
1,679 sequential decode cases, 1,066 trees, 1,066 inverse tables, 1,054 packed
tables, 567 quality cases, 12 macro-block lengths, four default Huffman tables
and two default quantisation tables. The 51 original failures comprise nine
oversubscribed-tree failures and 42 inverse-table bounds errors; their original
diagnostic strings are retained. Kotlin compares exact tree-construction
messages and the bounds-error category (the JVM's diagnostic text differs).

Coverage includes all 256 byte prefixes with each default code table, EOF and
JPEG stuffing/restart handling through the shared bit reader, all 256 root leaf
symbols, singleton trees at depths 1–32, explicit branch shapes and missing
children, depth/code overflow through depth 300, 300 seeded canonical tables,
300 seeded arbitrary trees, and packed flag precedence. Quantisation covers
every Int16 value at 13 quality settings, quality boundaries for both default
matrices, and 300 seeded arbitrary matrices and signed 64-bit qualities. Source
and fixture checksums are verified in CI; expected results never come from Kotlin.

Original packed-array reads/writes are unsafe outside allocated storage. Kotlin
uses checked array access and raises bounds exceptions; these cases are explicitly
not asserted equivalent to arbitrary original memory reads or corruption.
Two direct tests cover those bounds and a third covers cancellation of malformed
cyclic decoding. Cycles otherwise retain the original nonterminating traversal;
there is no invented EOF success or arbitrary symbol-depth cutoff. Finite tree
operations are recursive, as upstream; pathological depth can exhaust the JVM
stack and is not promised to reproduce GHC resource limits.

The retained JuicyPixels test runner has full-image JPEG decode/re-encode and
metadata exercises, not standalone DefaultTable assertions. Those original
image tests still require frame parsing, baseline/progressive scans, block integration,
color conversion, metadata and encoding. They have not been counted as ported.
The complete Pandoc and subsequent Calibre scope remains open.


## JPEG block and IDCT evidence

`JpegBlocks.kt` translates the numerical and stateful operations of
`Codec.Picture.Jpg.Internal.Common` and `FastIdct`, plus the unpacker parameter
record, zero-block construction and its original diagnostic representation.
This includes Chen–Wang integer IDCT, the level shift, dequantisation, forward
and inverse zig-zag ordering, their composed macro-block decode, signed/DC/AC
coefficient decoding, bit unpacking, restart-interval behavior, block dimension
arithmetic, raster traversal and subsampled pixel placement.

The implementation preserves 64-bit Haskell Int intermediates, Int16 row writes,
arithmetic right shifts and clipping to -256..255 in the column pass. Level
shift then adds 128 using Int16 wrapping; clamping to bytes is a separate pixel
placement operation. Dequantisation multiplies Int16 values with wraparound.
Both transforms run in place and return the original array. Reordering and the
composed macro-block path preserve sequential reads/writes when source and
destination are the same array, including the destructive alias behavior.
Trailing elements beyond the first 64 remain unchanged. Forward ordering also
supports the original Int32/Word8 specializations and generic list values.

Signed entropy fields consume their sign bit before the remaining magnitude,
retain Int32 arithmetic and then narrow DC coefficients to Int16. Widths through
255 include malformed categories produced by the full Word8 Huffman domain.
Restart decoding returns -1 without consuming any bits, exactly as the current
upstream implementation does. Dimension rounding preserves signed 64-bit
addition overflow and floor division. Raster traversal is row-major with empty
results for nonpositive dimensions; Kotlin additionally checks interruption
between rows. Pixel placement preserves duplication factors, component stride,
upper-edge clipping, signed sample clamp and the source block-coordinate formula.

The new `tools/haskell-jpeg-block-reference` package compiles all 12 original
source modules unchanged, including Jpg.Internal.Types and its TIFF/Exif
dependencies. No replacement type module or numerical shim is used. It shares
the frozen GHC 9.12.2, vector 0.13.2.0 and primitive 0.9.1.0 versions, with the
compiler's binary 0.8.9.3 package. `tools/import-jpeg-block-fixtures.py` captures
14,233 independent cases. All values come from original calls; the adapter only
serializes their results, mutated buffers and remaining input bits.

Coverage includes 2,255 raw IDCT and 2,255 level-shifted IDCT cases (all 64 impulse
positions, signed boundaries, uniform blocks and seeded dense blocks), 1,024
level-shift cases spanning every Int16 value, 500 dequantisations, 527 composed
macro-block decodes, and 106 cases for each of five permutation entry paths.
Buffer snapshots cover distinct/identical source and destination arrays and
lengths beyond 64. Reference identity checks also verify returned storage.
The generic mutable-list path is checked against the same Int32 oracle.

Entropy coverage contains 2,295 signed-field cases across widths 1–255, offsets
and byte patterns, every possible DC category (768 cases), all 256 AC nibble
pairs, 512 cases for each default DC/AC decoder, and 39 cases each for raw bits
and the restart helper. A trailing 32-bit read verifies bit consumption.
Pixel placement has 2,520 cases covering 1–4 components, first/last components,
zero and odd image dimensions, boundary clipping, block offsets and positive,
zero or negative duplication factors. There are also 169 raster cases, signed
64-bit dimension boundaries, 30 exact parameter diagnostics and zero allocation.

Four direct Kotlin tests record deliberate platform/domain differences: bounds
exceptions for truncated blocks and negative pixel indices (the original uses
unsafe vector accesses), rejection of nonpositive signed-field widths (the
original unchecked negative remaining count is outside the supported domain),
and interruption of a huge raster traversal. These are not claimed equivalent
to undefined original memory access or nontermination. Array-offset view APIs
are not exposed; future codec consumers requiring overlapping vector slices
must preserve those views explicitly rather than silently copying them.

All 14,233 oracle cases and the four direct tests pass; the full local suite has
91,696 passing tests. CI verifies source and result hashes without running
Haskell. Original full-image JPEG tests remain open: frame parsing, scan state,
baseline/progressive entropy orchestration, metadata/color-space handling,
pixel-buffer integration and encoding are still required. Completing these
helpers does not complete JPEG, the image dispatcher, Pandoc or Calibre.

## JPEG binary structures and entropy-segment evidence

`images/JpegBinary.kt` translates the primitive Binary instances in JuicyPixels
3.3.9 `Jpg.Internal.Types`: all frame markers, JFIF units and APP0 records,
Adobe transforms and APP14 records, components, frame and scan headers,
quantisation and Huffman specifications, table lists and restart intervals.
Records retain source field order and exact Show diagnostics. Their Int fields
represent upstream Word8/Word16 domains; signed quantisation values use Short.
Calculated sizes and serialized field counts deliberately remain independent.

Reader failures preserve the original message, byte offset and unconsumed
suffix. Word16 reads are atomic, frame-header padding uses the declared length,
negative padding skips are no-ops, and scan length is retained without driving
a skip. JFIF uses the original conjunction in its version rejection and reads
thumbnail dimensions without consuming thumbnail pixels. Adobe requires version
100 on input while the writer retains its provided version. Nonzero quantisation
precision always reads/writes 16-bit values, while calculateSize still uses the
precision-plus-one formula. All nibble writers preserve Word8 shift/OR behavior
without first masking the low argument. DHT writing preserves the independent
size/code vectors, including checked bounds errors. Table-list readers continue
until their signed remaining length equals zero, even after an overrun.

Both original ECS entry points are ported. The simple lookahead parser requires
a terminator and preserves its EOF failure offset. The optimized parser accepts
nonempty input ending without a marker, and retains the chunk-sensitive behavior
of the pinned binary 0.8.9.3 implementation. For example, input `[FF,D9]` returns
`[FF]` while consuming zero bytes. Chunks `[01,02,03,FF]`, `[D9,AD]` return ECS
`[01,02,03,FF,02,03]` with offset 3 and remainder `[FF,D9,AD]`. The independent
oracle verifies these surprising results; the Kotlin parser does not replace
them with the simpler algorithm. Single-buffer parsing and explicit chunk-list
parsing are both available to future consumers.

`tools/haskell-jpeg-binary-reference` compiles ten unchanged original modules,
including the full upstream TIFF/Exif dependency closure. Its adapter constructs
values and calls actual get/put/calculateSize/Show implementations. It captures
both parsing and separately constructed writer inputs, so a matching round trip
alone cannot hide a writer error. Compiler and binary versions are reported by
the built executable. The freeze file records the dependency versions. Source
and canonical result hashes are checked by CI.

The 20,043 retained cases cover every marker/unit/transform byte, all packed
nibble bytes, JFIF versions, Adobe transforms, precise truncations, maximum
component counts, declared-length mismatches, variable table/vector lengths,
Word16/Int16 boundaries, malformed table lists and 250 seeded record sets.
Every FF successor is exercised at several offsets and every split point, with
empty chunks, missing terminators and seeded multi-chunk streams. Kotlin checks
all successful values, encoded bytes, sizes, diagnostics, parser offsets and
remainders. Checked upstream vector bounds exceptions are matched by category,
not by a Haskell source-location string.

All 20,043 new cases pass: 18,322 successful outcomes, 1,685 parser failures
and 36 checked bounds exceptions. The full local suite has 111,739 passing tests and the
four CLI check scripts retain 234 passing cases. Full frame/image assembly,
Exif IFD parsing/serialization, scan orchestration, color handling and JPEG
encoding remain open. The Types module's macro-block diagnostic printers also
remain unported. Existing `TiffMetadata` is a metadata consumer, not the typed
TIFF/Exif codec required by `JpgExif`; it must not substitute for that model.
This milestone does not complete JPEG, Pandoc, or Calibre.

## Typed TIFF/Exif and JPEG APP1 evidence

`TiffTypes.kt` and `TiffBinary.kt` port the typed Exif model and TIFF binary
operations from JuicyPixels 3.3.9 `Metadata.Exif` and `Tiff.Internal.Types`.
They preserve all 52 known tag constructors, unknown tags, constructor ordering,
IFD0 classification, every ExifData constructor, byte order, field types,
header/IFD/list serialization, extended values and TIFF document serialization.
Compression, planar, sample-format, photometric, predictor and extra-sample
code mappings are also implemented. Word32 values use nonnegative Long values;
Int32 values retain signed wrapping. TagUnknown with a known numeric code stays
a distinct constructor. Derived tag ordering follows declaration order, while
TIFF output separately sorts numeric tag codes, matching the original.

The shared forward cursor preserves atomic primitive reads, negative header
skips, offset bounds, stable raw-offset sorting and big-endian inline-short
cleanup. Nested Exif directories additionally sort again after that cleanup.
ASCII/undefined data can be immediate or extended; two shorts share the inline
word. Rational readers explicitly use little endian regardless of TIFF byte
order. The next-directory pointer is consumed without being followed. Nested
Exif-offset fields retain their full values; GPS pointers retain the original
scalar behavior. Backward/overlapping offsets become ExifNone or fail at the
same read as the original, rather than being repaired.

Writing retains the source's independent declared type/count/offset fields,
raw-data placement, numeric tag sorting and Word32 offset wrapping. String and
undefined payloads are padded to even lengths. Only the original constructors
advance the offset allocator: notably rational payloads are written without
being allocated there. ExifIFD values do not emit nested payloads from the
extended-value writer. These are observed upstream behaviors, not omissions
silently replaced by a standards-oriented writer. Explicit constructor inputs
exercise the writer independently of reader round trips.

`JpegExif.kt` implements the original Exif signature check and parse-failure
swallowing, then returns the first typed directory. Its writer partitions IFD0
and Exif fields, inserts the redirect field, uses big-endian TIFF at offset 8,
and writes the APP1 marker/length/payload. The original Samsung
`tests/jpeg/10x8-samsung-s8.jpg` used by metadataReadTest is retained verbatim
with its pinned commit and SHA256. Its APP1 fields and every truncation are
checked against original frame parsing. The full metadataReadTest's image
codec and metadata extraction/re-encoding remain outstanding.

`TiffMetadata` now uses the shared typed parser. Its prior private field model,
byte reader and nested-directory traversal were removed. Existing strict TIFF
format guards, dimension/DPI selection, diagnostics, division-by-zero behavior
and cancellable zero-row recursion retain their earlier oracle coverage.
This connects the new implementation to the image-size path without claiming
TIFF pixel strip decoding is finished.

The new reference package compiles ten original modules unchanged, invoking
Binary/BinaryParam directly. Compiler/package versions, original source hashes,
fixture hashes and canonical result hashes are retained. Remainder evidence
includes exact offsets/lengths and the first/last 16 bytes of the unconsumed
suffix, avoiding duplication of large pixel payloads. The adapter's JPEG writer
calls the original JpgImage encoder and removes only the outer SOI/EOI markers.
No TIFF/Exif algorithm is reimplemented in the oracle.

The 16,177 captures include 14,246 successful outcomes and 1,931 parser failures.
There are 257 grouped tag cases spanning every Word16 code, known/unknown
constructor distinctions and cross-constructor ordering; 5,568 extended-field
reads; 8,403 typed document reads (all 8,390 previous independent TIFF inputs,
including 68 original files, plus redirects and Samsung Exif); and 485 APP1
parses. Writer coverage includes 528 documents, 340 APP1 segments, standalone
headers/fields/directories and every ExifData constructor. There are also
primitive/truncation and enum-code cases in both byte orders.

Nested parsing and Show use explicit stacks, preserving original results at
5,000 levels without JVM call-stack limits. Six original Eq captures cover
independently constructed equal/unequal trees through 20,000 levels; Kotlin's
structural equality is iterative, and equal values also satisfy the hashCode
contract. This changes evaluation mechanics without dropping nested data.

All 127,916 Kotlin tests pass locally with no skips, including all existing TIFF
metadata tests. The installed CLI checks remain 234 cases. Remaining work
includes the complete metadata extraction/encoding layer, full JPEG frame/scan
assembly and pixel codecs, TIFF pixel codecs and their original image tests.
Pandoc's full format/application surface and the Calibre port remain open.


## JPEG frame assembly and serialization — 2026-10-13

`JpegFrames.kt` ports JpgFrame/JpgImage and their strict and legacy semi-lazy
parsers from the pinned JuicyPixels 3.3.9 Types module. It includes every frame
constructor, individual-frame parsing, strict frame lists, frame kinds with
byte offsets/lengths, early frame-header lookup, complete image writing, and
both original macroblock debug printers. APP0/APP1/APP14 payload parsing retains
optional failure swallowing. Other APP/extension payloads retain byte-for-byte
contents. Existing typed JFIF, Adobe, Exif, quantization and Huffman structures
are shared rather than duplicated.

Semi-lazy parsing advances the outer cursor to EOF at the first SOS and defers
entropy data traversal until the scan list cell is requested. Failures after
that scan discard the subsequent unsuccessful parser result, while failures
before it propagate. A memoized iterable list spine supports prefix consumers
without forcing or copying compressed data. Decoder Huffman trees are separately
lazy: header/kind inspection and writing table specifications do not build a
tree. Full Show or equality can force it and reproduce original malformed-tree
errors. Direct tests prove tail memoization/prefix behavior and writing without
evaluating an unused tree.

Writing preserves the original generic APP/extension behavior: the segment
length contains the payload size without adding two, followed by Binary's
Int64 ByteString length prefix and payload. This is an observed source quirk,
not a repaired JPEG writer. Explicit writer cases include every Word8 APP and
extension code, every frame constructor, and payload sizes spanning the Word16
length boundary. The two debug printers preserve their distinct spacing and
signed Int64 formatting.

The new original-Haskell oracle compiles ten unchanged vendored modules. Its
9,819 captures comprise 4,159 successful results, 5,618 parser failures and
42 deferred Huffman exceptions. Cases cover every marker byte, all truncations
of representative frames, negative/undersized APP lengths, optional metadata
corruption, multi-scan continuations, early lookup versus full-prefix parsing,
seeded frame compositions and writer-only values. Parser outcomes compare exact
consumption and remainder length/edge bytes, full constructor Show output and
serialized bytes. For large original images, full Show and encoded outputs are
compared by SHA256 and byte length instead of duplicating large files in JSON.

All 31 original JPEGs from the pinned upstream test revision are retained
verbatim with provenance and hashes: 29 jpegValidTests, huge.jpg, and Samsung's
metadataReadTest input. Each is checked in six original parser/consumer modes.
This exercises frame assembly on those inputs; their original full pixel
decode/re-encode assertions remain outstanding. The oracle wrapper and capture
script are hash-checked alongside all original source and image assets in CI.

All 137,737 Kotlin tests pass with zero failures, errors or skips. Full metadata
extraction, baseline/progressive JPEG scan decoding, pixel conversion/encoding,
and original pixel tests remain open, as do other image codecs, the remaining
Pandoc formats/application surface, and the complete Calibre phase.


## Progressive JPEG scan unpacking — 2026-10-13

`JpegProgressive.kt` ports the complete exported progressiveUnpack path from
JuicyPixels 3.3.9 `Jpg.Internal.Progressive`, including its internal first DC,
refined DC, first AC and refined AC decoders. It reuses the existing JPEG bit
reader, packed Huffman trees, Int16 coefficient/IDCT helpers and pixel writer.
`JpgScanData` carries explicit unpacker parameters and entropy bytes;
`JpgDecodedRaster` carries the raw interleaved component samples for subsequent
colorspace finalization. This is not yet a public JPEG-file decoder.

The implementation preserves per-scan bit-reader continuations and Int32 EOB
runs, Int16 DC prediction/overflow, successive approximation, coefficient sign
refinement, zero-run skipping, correction-bit consumption and scan-wide strategy
selection from the first parameter. Reader indices can be shared or reordered.
Each scan's restart counter initially comes from the first scan's first
parameter; reset of DC predictors occurs only for scan index zero. Marker
skipping/byte alignment retain the original shared bit-reader behavior.

Solo and interleaved MCU index vectors preserve partial right-edge visibility,
per-component sampling factors, per-row working-block reset/reuse and the
original vertical/horizontal replication formulas. Quantization selectors clamp
to slot three. Component block storage keeps only the original row workspace,
not the entire image's coefficient matrix. Zero width/height and zero sampling
factors exposed two eager-evaluation differences; memoized derived MCU sizes
now preserve the original successful empty results and division failures.
JVM arrays retain their natural allocation/index limits. Unchecked native
out-of-allocation writes are not manufactured as portable reference behavior.

The new reference harness invokes progressiveUnpack from 13 unchanged upstream
modules with explicit dimensions, components, tables and scan parameters. It
contains no decoding algorithm and returns every resulting sample byte.
Source/harness hashes, actual GHC/vector versions and canonical fixture hashes
are verified in CI. Captures include 8,712 complete pixel results, 34 division
failures, seven checked vector-bound failures and one empty-scan exception.
Bounds failures compare exception categories; successful images compare exact
width, height, component count and every byte, not only a checksum.

The 8,754 cases exercise all 256 AC run/size symbols, DC widths through 255,
approximation shifts through 63, empty/truncated/marker-containing bitstreams,
dense and split spectral ranges, positive and negative corrections, all four
coefficient modes, 11 sampling layouts with one to four components, nine image
size boundaries, seven restart intervals, separate/interleaved scans, signed
quantization extremes, clamped table selectors, shared readers, differing later
scan parameters, malformed checked indices, and 400 seeded scan sequences.
Cases using arbitrary nonzero AC runs bound writes within the original 64-entry
allocation while retaining writes beyond the declared spectral range.

All 146,491 Kotlin tests pass locally with zero failures, errors or skips.
Remaining JPEG work includes the frame-fold decoder state and scan-parameter
construction, baseline decoding, complete colorspace/metadata integration,
encoding, and original image decode/re-encode tests. Full Pandoc and Calibre
scope remains unchanged and incomplete.


## Complete JPEG file-to-pixel decoding — 2026-10-13

`JpegDecoder.kt` connects the original JPEG frame parser, Huffman/quantization
helpers, IDCT/pixel placement and progressive unpacker into the decodeJpeg path.
It accepts JPEG bytes and returns the decoded image kind, dimensions and raw
samples, or the original parser/kind/colorspace failure. Exceptions from invalid
internal state remain exceptions, as in the source. Metadata extraction and
JPEG encoding are not part of this milestone; the decoder is currently internal.

The frame-state fold preserves four default DC/AC table slots, four default
quantization matrices, table replacement/destination bounds, component-selector
lookup, restart state, latest frame/APP metadata markers and per-scan snapshots.
Baseline, extended sequential and progressive DCT Huffman frame kinds retain
the original single-recognized-frame rule. Scan parameters retain sampling,
approximation/range, reader and block indices. Parameter list elements are
memoized lazily, but both Huffman tree fields are forced when an element is
observed, matching the original strict record. Discarded/replaced tables remain
unforced. This was verified against malformed selected and unused tables.

Baseline decoding adds AC zero-run handling, DC prediction and complete MCU
traversal, with restart prediction reset and the original counter spanning
scans. Interior grayscale/YCbCr/horizontal-duplication writers retain the source
strides and branch selection; edge MCUs use the bounded generic pixel writer.
The shared scratch IDCT block is reused. Color selection preserves Adobe marker
precedence and component-name/count fallbacks. YCCK-to-CMYK preserves initial
byte inversion and the original Float arithmetic/clamping. Unsupported color
errors occur after scan decoding, matching runST's evaluation of the state/result
pair, rather than being short-circuited before malformed selected tables.

Noncanonical baseline AC symbols `(run,0)` with run 1 through 14 reach
`decodeInt(0)` in the original. They request -1 remaining bits, returning a bit
cursor position one bit earlier. The shared JPEG reader now supports that
reachable count, and coefficient decoding accepts zero. Eighty-four independent
complete-image captures exercise this behavior with an EOB leaf before any
unchecked out-of-allocation write. The old direct test's unsupported-width
boundary now covers negative widths; zero is proven by original decoder output.

The oracle compiles 16 unchanged original modules and calls the exported
`decodeJpeg` directly. Its adapter only describes dynamic images and writes raw
samples; it does not duplicate frame folding or decoding algorithms. There are
2,401 captures: 2,020 successful images, 343 returned errors and 38 original
exceptions (including 24 checked table-vector bounds, eight malformed Huffman
trees, four invalid selectors and two empty-component maxima). Bounds failures
compare exception categories; other deterministic failures retain exact text.
Small outputs compare every byte, dimensions and kind. Large original outputs
compare SHA256 and length over all raw samples.

All 31 retained upstream JPEGs decode identically, totaling 127,028,638 sample
bytes. This includes every jpegValidTests input, the 4928x3264 huge photograph,
the 4614x3076 broken progressive photograph, CMYK/RGB/grayscale files, multiple
MCU restart variants and the Samsung Exif sample. Other cases span interleaved
and separate scans, seven sampling layouts, clipped edges, three supported
frame kinds, all 16 selector indices, APP14/name colorspace choices, complete
file truncations, table destination bounds and 200 seeded complete images.

Native uninitialized output or unchecked out-of-allocation reads/writes on
malformed images are not portable reference values. Successful synthetic
baseline inputs initialize all output components; JVM allocations are zeroed
and out-of-bounds accesses throw. No claim of equivalence is made for native
undefined memory. The original decode/re-encode image tests remain incomplete
until the JPEG encoder is ported.

All 148,892 Kotlin tests pass locally with no failures, errors or skips.
Remaining work includes full metadata extraction, the imageSize/general-image
API integration, JPEG encoding and original encoder assertions, other image
codecs, the remaining Pandoc formats/application surface, and Calibre's MOBI
and additional formats. The complete goal remains active.

## JPEG pixel-to-file encoding — 2026-10-13

`JpegEncoder.kt` translates the fixed-point fastDctLibJpeg transform and the
complete direct JPEG encoding path in Codec.Picture.Jpg. Grayscale, YCbCr 4:2:0,
RGB and CMYK preserve component identifiers, sampling, quantization destinations,
default DC/AC tables, MCU/component/block traversal, edge replication, chroma
averaging, DC prediction and zero-run/category Huffman serialization. Quantization
uses signed quot after adding half the positive divisor. DCT arithmetic retains
Int32 wrapping, the two pass biases and the original level shift. DC predictors
retain their Int16 narrowing. Final bytes use the shared original-compatible bit
writer, byte stuffing, zero padding and frame serializer.

RGB includes the original Adobe APP14 marker; CMYK does not add one, matching
the source's unused local Adobe value. Quality defaults to 50 and accepts the
complete original Word8 domain, including 0 and 101..255. Zero-sized traversal
still writes image headers, whose dimensions narrow to Word16. The encoder is
internal and accepts already-encoded metadata frames. JFIF/Exif placement is
verified, but the complete typed Metadatas-to-frame API has not been ported.

The independent oracle builds 16 unchanged original JuicyPixels modules and
calls exported encodeDirectJpegAtQualityWithMetadata and fastDctLibJpeg. Its
wrapper only constructs original pixel/metadata types and serializes results.
The 5,153 captures comprise:

- 1,920 DCT cases: all 256 constant byte levels, all 65,536 Int16 values across
  consecutive 64-sample blocks, impulses at every position and seeded full-range
  blocks. All coefficients and unchanged input samples are compared; Kotlin's
  return value must alias the supplied workspace, which starts nonzero.
- 3,140 complete synthetic JPEG files: all 256 quality values for all four color
  models, constant/checker/random patterns, 1/7/8/9/15/16/17/31/33 edge sizes,
  empty traversal/header narrowing and 168 JFIF/Exif integration cases. These
  compare every output byte, including all frame headers and entropy bytes.
  Quality-50 cases also exercise the default argument. Metadata frame inputs
  are constructed explicitly in the Kotlin test, without claiming a typed
  metadata encoder already exists.
- 93 original JPEG decode/re-encode results: all 31 retained upstream JPEGs at
  qualities 50, 90 and 100. SHA256 and lengths cover complete encoded files,
  including large photographs, progressive inputs, restart variants and
  grayscale/RGB/YCbCr/CMYK samples. Quality 100 follows the original
  validationJpegEncode/liberalJpegEncode functions; 90 covers imgToImg's CMYK
  branch. The source runner also converts RGB to YCbCr and writes other image
  formats; those branches are not claimed complete here.

All 154,045 Kotlin tests pass locally without failures, errors or skips. Source
hashes pin the harness, fixture importer, complete vendored source and original
JPEG assets/test runner. CI now verifies this additional fixture manifest.
Unchecked invalid native image buffers are not portable reference values;
nonempty test images provide all samples. The Float diagnostic referenceDct,
full typed metadata encoding/extraction, color-conversion and cross-format
upstream test branches, general image API integration, other codecs, remaining
Pandoc formats/application behavior and Calibre MOBI/additional formats remain
outstanding. The full goal remains active.

## Typed image metadata and JPEG metadata integration — 2026-10-13

`ImageMetadata.kt` ports Codec.Picture.Metadata's typed key/value store, all
SourceFormat/ColorSpace/Value constructors, insert/delete/lookup/singleton,
ordered folds, Exif extraction, right-biased union, size/format/DPI helpers and
Word64 unit conversions. Key types constrain stored values, including Word64
rather than signed Int DPI values. Text and byte-string Show use the existing
Haskell string renderer; Double rendering uses the existing GHC-compatible
formatter. Value equality preserves numeric Double equality (NaN unequal to
itself and signed zeros equal). There is no invented equality for Metadatas,
which has no original Eq instance.

Union is a strict left fold over the right-hand entries, deleting the previous
key then prepending each replacement. Its observable entry order means that
parenthesization matters: the JPEG expression is jfif <> (exif <> size), as in
Haskell's right-associative operator. List foldMap in TIFF extraction is also
right-associated. Neither path sorts or normalizes the metadata container.

`ImageExifMetadata.kt` ports the complete JFIF metadata module and the TIFF
metadata transformations, including Exif offset construction, all makeIfd data
branches, recognized text keys, Char8 packing, stable numeric tag sorting,
unknown-vs-known tag constructor distinctions, nested Exif insertion, orientation
narrowing, duplicate tags, resolution units and rational division failures.
Short ExifUndefined values preserve the source's surprising Word8 shift before
widening, including negative shift counts on the pinned 64-bit GHC build.
Existing inline TIFF serialization quirks remain visible (for example, short
strings can decode as zero bytes); the port does not repair upstream output.

The encoder now accepts typed metadata and constructs JFIF/Exif frames. Decoded
JPEG images expose lazy metadata: plain pixel decoding does not force invalid
DPI rational denominators. The lazy computation retains only the metadata
records and header, not the entropy streams or complete decoder state. Exif
resolution overrides JFIF; the actual frame size and SourceJpeg override Exif
image-size/format entries. Later valid APP records replace earlier records;
malformed optional APP1 records are ignored as in the frame parser. The existing
TIFF size/DPI consumer now uses this shared extractor after its original strict
image-format guards.

The independent oracle compiles 16 unchanged upstream modules. Its 4,569 cases
include 575 typed storage/fold/order checks, 819 helper/Word64 conversion cases,
771 JFIF unit/range checks, 1,480 TIFF extraction cases, 625 complete TIFF/JFIF
metadata-frame encodings, 203 complete JPEG encodings, equality/Show checks,
and the Exif offset record. The original metadataWriteTest JPEG branch is
executed with its RGB-white-to-YCbCr conversion and exact metadata input; its
complete output bytes match. Four metadata roundtrips and three pixel-only
cases cover encoded data. Another 54 raw-JPEG cases prove Exif/JFIF precedence,
frame dimension replacement, ignored malformed APP1 records, and lazy metadata
errors. There are 18 original exceptions: ten divisions by zero, four ExifNone
and four ExifIFD type-selection failures. All other results compare exactly.

All 31 retained original JPEGs are decoded with complete metadata and re-encoded
at quality 90 with those metadata, including the Samsung metadataReadTest path.
Metadata Show compares complete field values and order; SHA256 and lengths cover
all encoded bytes. The raw-image metadata cases reuse independently captured
JPEG encoder bytes, and that fixture is included in the source hash manifest.
The harness, pinned sources, test runner, retained images and importer are also
hashed. CI now verifies this manifest alongside the existing independent corpora.

All 158,614 Kotlin tests pass without failures, errors or skips. Full metadata
integration with other codecs, general image format dispatch, Float referenceDct,
remaining color conversion/cross-format upstream tests, other pixel codecs,
remaining Pandoc readers/writers/application behavior and all Calibre MOBI and
additional-format work remain outstanding. The original full goal remains active.

## JPEG missing-table fidelity correction — 2026-10-13

The original emptyDecoderState calls prepareHuffmanTable for its four DC and AC
slots. That helper returns a singleton zero packed-table placeholder, not a
packed standard Huffman tree. The previous Kotlin decoder incorrectly populated
those slots with usable standard trees. Consequently, a JPEG without DHT could
return pixels where the original traverses its zero table indefinitely.

`JpegDecoder.State` now initializes the original one-element zero arrays. Normal
DHT updates and per-scan snapshots remain unchanged. With all-zero entropy,
huffmanPackedDecode repeatedly reads index zero, obtains zero, and loops;
setDecodedStringJpg supplies zero bits after EOF as well. This is a defined
in-bounds loop, not a claim about unchecked native out-of-bounds behavior. A one
bit with the singleton table can cause an unchecked native read; no portable
reference output is asserted for that case. Kotlin retains its existing bounds
checks and interruptible Huffman traversal.

The independent original decodeJpeg executable was first observed not returning
for three seconds on a minimal 8x8 input. The new importer then records 195 cases:
135 returned results and 60 bounded did-not-return observations (500 ms each,
with child cleanup). Timeouts alone do not prove divergence; the pinned source
trace above provides the reason for the zero-stream cases. The manifest stores
that trace and hashes the complete original source, decoder harness and importer.
Cases span baseline, extended sequential and progressive frames; selectors
0/1/2/3/15; absent DC, AC or both tables; and DHT appearing only after scans.
Explicitly provided tables and zero width/height images are paired controls.

Before the correction, all 60 zero-traversal tests failed and all 135 controls
passed. After it, all 195 pass. The Kotlin test observes a live decoder worker
inside Huffman traversal, checks that it has not returned, interrupts it, and
verifies CancellationException and thread termination. Finite controls compare
all output dimensions, color kinds and sample bytes with the original oracle.
CI verifies the additional observation manifest.

All 158,809 Kotlin tests pass without failures, errors or skips. This correction
does not complete the image dependency port or the overall objective. Remaining
work includes Float referenceDct, color conversions and cross-format image tests,
other codecs and image API integration, the remaining Pandoc formats/application
surface, and Calibre MOBI and additional formats. The full goal stays active.

## Float reference DCT — 2026-10-13

`JpegEncoder.referenceDct` ports the remaining exported transform from
Codec.Picture.Jpg.Internal.FastDct. It implements the original Float formula:
frequency loops u then v, sample summation x then y, Float cosine angles and
products, zero-frequency normalization, and truncation to Int32. The diagnostic
function does not apply the encoder's level shift. It returns the supplied
workspace, updates exactly the first 64 coefficients in v*8+u order, and leaves
all input samples and any workspace tail untouched.

The 64 fixed position/frequency cosine values are computed with Float angles
and cached lazily. This preserves the finite domain and arithmetic of the
original formula while avoiding repeated trigonometric calls; the summation and
normalization remain Float operations in the original order. There is no
Double accumulation, reassociation or approximate-result tolerance.

A separate oracle compiles unchanged original source and calls referenceDct
directly. It freezes the returned workspace and input; it contains no transform,
trigonometry or rounding implementation. Its 3,200 captures cover 68,480 blocks
and 4,382,720 transformed coefficients, all compared exactly:

- Every one of the 65,536 signed Int16 constant levels, grouped into 256 fixtures.
- Every signed Int16 value across 1,024 consecutive ramp blocks.
- 576 impulses spanning all 64 positions and nine signed/byte boundary values.
- 32 alternating extreme-value blocks.
- 1,024 seeded full-range Int16 blocks and 256 seeded byte blocks.
- 32 larger vectors with nonzero initial workspaces and preserved input/output
  tails. Tests also require the returned workspace to be the same Kotlin array.

The importer hashes the original source, upstream test runner, frozen harness
and its own implementation. CI verifies this manifest. Coefficients match the
pinned GHC 9.12.2/JuicyPixels 3.3.9 build; arbitrary other native libm/compiler
implementations are not claimed identical. Vectors shorter than 64 would enter
unchecked native memory access and are not used as portable reference outputs.
Both exported functions of FastDct are now implemented; the fixed-point encoder
continues to use its existing fastDct path.

All 162,009 Kotlin tests pass without failures, errors or skips. Remaining work
includes color conversion and cross-format upstream image tests, other pixel
codecs, general image API integration, remaining Pandoc formats/application
behavior, and Calibre MOBI/additional formats. The full objective remains active.

## RGB8 and YCbCr8 color conversions — 2026-10-13

`ImageColorConversion` ports both `ColorSpaceConvertible PixelRGB8 PixelYCbCr8`
and `ColorSpaceConvertible PixelYCbCr8 PixelRGB8` from the pinned
Codec.Picture.Types. Typed unsigned-byte pixels and interleaved byte images
provide both `convertPixel` and `convertImage` operations. RGB conversion retains
the original Float-to-fixed coefficient calculation, 16-bit fixed-point shifts,
luma bias and chroma bias minus one. Reverse conversion retains the four original
tables, arithmetic right shifts and clipping of each final component to 0–255.
Image conversion allocates a fresh vector, preserves dimensions, reads exactly
width*height pixels, leaves the source unchanged and ignores trailing samples.

A new independent oracle compiles unchanged original JuicyPixels sources and
calls either convertImage or pixelMap convertPixel directly. It implements no
conversion formulas. Its 1,467 captures comprise:

- 1,024 exhaustive batches: each of the 16,777,216 possible three-byte inputs
  in each direction, independently for the original pixel and image paths.
  All 67,108,864 conversion results (201,326,592 output bytes) are compared using
  SHA-256 and exact length per 65,536-pixel batch. The original outputs are
  hashed by the importer; expected hashes never come from Kotlin.
- 400 small image/pixel captures across empty dimensions, non-square shapes,
  seeded samples, edge sizes and unused input tails. Full outputs are retained
  and compared byte for byte as well as hashed.
- Eight original optimized-image cases with signed dimensions: two negatives
  yielding a positive pixel count, and zero combined with a negative dimension.
  The pixelMap path on two negative dimensions would expose uninitialized native
  memory and is deliberately not used as a portable reference.
- 35 complete RGB → YCbCr → JPEG captures at quality 100. These include the
  original testSuite/toJpg white and black 16×16 images and 128×128 RGB gradient,
  plus 32 seeded images with varied odd sizes. Every byte of each resulting JPEG
  matches the original conversion and encoder composition.

The importer records hashes of itself, the frozen harness, the original test
runner and all retained JuicyPixels source files; CI verifies them. Short source
vectors invoke unchecked native memory access and have no portable expected
results. JVM array size limits remain a platform boundary; the Kotlin image
allocation checks its computed Long length before converting to an Int.

This completes these two color-conversion instances, not Codec.Picture.Types.
Other pixel types/conversions, remaining cross-format tests and codecs, general
image dispatch, remaining Pandoc formats/application behavior, and all Calibre
MOBI/additional-format work remain outstanding. The full objective stays active.

All 163,476 Kotlin tests pass with no failures, errors or skips.

## CMYK and YCbCrK color conversions — 2026-10-13

`ImageCmykConversion` ports six further ColorSpaceConvertible instances from
Codec.Picture.Types: RGB8 → CMYK8, CMYK8 → RGB8, RGB16 → CMYK16,
CMYK16 → RGB16, YCbCrK8 → RGB8 and YCbCrK8 → CMYK8. Each has typed
pixel and image operations. RGB-to-CMYK preserves the all-black branch, maximum
channel selection and truncating integer division. The 16-bit path retains the
original Word32-sized products using Long, avoiding signed Int32 overflow.
CMYK8-to-RGB divides by 255; CMYK16-to-RGB intentionally shifts by 16 instead
of dividing by 65535, so full white maps to 65534, exactly as upstream.

YCbCrK conversion retains the original Float operation order and clamp/floor
semantics. Its RGB result ignores K. Its CMYK result complements each unrounded
RGB expression before clamping and preserves K. It is not composed from the
already rounded RGB result. `JpegDecoder` now calls this shared conversion after
inverting all four original YCCK channels, matching dynamicOfColorSpace in
Codec.Picture.Jpg. The existing 2,401 complete JPEG decoding captures pass
unchanged, including full output samples of the retained original photographs.

All six original convertImage implementations use pixelMap convertPixel. The
Kotlin image operations copy into a new component vector, traverse rows and
columns, preserve dimensions and input samples, and ignore unused source tails.

A separate frozen oracle compiles unchanged upstream code and directly invokes
both original convertImage and pixelMap convertPixel. It contains only request
handling, deterministic input generation and output serialization; it has no
conversion formulas. Its 3,294 captures cover 107,698,128 pixel conversions and
411,262,457 serialized output bytes:

- Every one of the 16,777,216 RGB8 inputs for RGB8 → CMYK8, independently via
  the pixel and image paths.
- Every Y/Cb/Cr triple for both YCbCrK conversions, independently via both paths,
  with a varying K channel. Another 864 captures sweep all 256 K values at each
  of 216 boundary colors for both operations and paths.
- Every CMYK8 component/K pair for each output component via both paths.
- Every 16-bit component value in RGB16 at four input permutations and in
  CMYK16 at 46 K values around powers of two, independently via both paths.
- Cartesian boundary products for all six operations, seeded images, odd and
  non-square shapes, zero-sized dimensions and unused source-vector tails.

The 1,638 large sweeps compare complete-output SHA-256 and length; the 1,656
remaining cases also retain and compare their original output bytes verbatim.
Word16 output is serialized explicitly little-endian only for test interchange.
The importer hashes itself, the complete pinned upstream source, upstream test
runner and frozen harness. CI verifies the manifest. The four-channel 32-bit
domain and complete 16-bit color domains are not claimed exhaustive.

Short input vectors and two negative image dimensions can expose unchecked or
uninitialized native memory in pixelMap; they are not portable oracle cases.
JVM bounds checking, zero-initialized allocations and array size limits remain
platform boundaries. Zero combined with a negative other dimension is covered.

These six conversions are implemented; the rest of the pixel API, lossless
promotions, identity instances, image transformations, codecs and cross-format
test branches still need work. Remaining Pandoc formats and application behavior,
Calibre MOBI and additional formats remain in the full active objective.

All 166,770 Kotlin tests pass with no failures, errors or skips.

## Pixel and image promotions — 2026-10-13

`ImagePromotion` implements all 21 non-identity ColorConvertible instances in
the pinned Codec.Picture.Types, with typed pixel and image entry points:

- Pixel8 → YA8, Float, Pixel16, RGB8, RGB16, RGBA8.
- Pixel16 → YA16, RGB16, RGBA16.
- Float → RGBF.
- YA8 → RGB8, RGB16, RGBA8; YA16 → RGB16, RGBA16.
- RGB16 → RGBA16; RGB8 → RGBA8, RGBF, RGB16, RGBA16.
- RGBA8 → RGBA16.

The implementation preserves scaling by 257, Float division by 255, replicated
grayscale channels, inserted maximum alpha and preserved/scaled alpha. Although
the upstream class describes its operations as lossless, original YA-to-RGB
instances discard alpha; the Kotlin behavior preserves that original choice.
The image adapters follow original pixelMap promotePixel: fresh destination
vectors, row/column traversal, preserved dimensions, unchanged input data and
unused source tails ignored. Same-type promotePixel/promoteImage and
convertPixel/convertImage are implemented as identity, preserving the original
object, complete underlying vector and aliasing without inspecting dimensions.

A separate frozen oracle calls unchanged original promoteImage or pixelMap
promotePixel. It implements no promotion, scaling or alpha formulas. Original
identity calls are evaluated and compared using GHC stable names. All 4,934
captures match; the 4,204 non-identity cases contain 137,320,458 converted pixels,
and all cases total 1,027,957,113 serialized output bytes. Coverage includes:

- Every Word8 scalar for all six scalar promotions, every Word16 scalar for
  all three scalar promotions, and every YA8 pair for all three YA8 promotions,
  independently through pixel and image paths.
- Every RGB8 triple for all four RGB8 promotions via both paths.
- Complete 16-bit component ranges at four permutations for YA16/RGB16,
  RGBA8 channel permutations, Cartesian boundary products and seeded images.
- All Float signs and exponents at six mantissa boundaries plus 8,192 seeded
  raw Float32 patterns. Outputs are compared by raw bits, including signed
  zero, subnormals, infinities, quiet/signaling NaNs and payloads. No epsilon
  or canonicalizing Float equality is used for sample comparisons.
- Empty dimensions, zero with a negative other dimension, odd/non-square
  images, untouched source data, fresh output buffers and unused input tails.
- 730 original identity observations, including RGB8 pixel sharing and
  RGB8/RGB16/RGBF image sharing, full vector tails and raw Float preservation.
  Identity images deliberately include short/empty vectors, negative dimensions
  and width Int.MAX_VALUE, because original id performs no raster allocation
  or validation. The returned Kotlin image and storage must be identical objects.

There are 2,104 large ramp captures, compared using full-output SHA-256 and exact
length, and 2,830 other captures with complete verbatim output bytes as well.
Word16 and raw Float32 bits use explicit little-endian serialization only for
fixture interchange. The importer hashes itself, the upstream test runner,
complete pinned source and frozen harness; CI verifies this manifest. Complete
multi-channel Word16/RGBA and Float32 domains are not claimed exhaustive.

The new PixelRGBF type also preserves the original derived Float Eq semantics:
NaN is unequal even to the same object and either sign of zero compares equal.
A direct GHC 9.12.2 evaluation of unchanged Types.hs returned
`(False,True,True,False,False,False)` for NaN-red self-comparison, signed-zero
red comparison, equal finite pixels, unequal finite pixels, NaN-green comparison
and NaN-blue comparison. A separate Kotlin test retains those exact results and
checks the equal-zero hash contract. Float sample equality and ordering are
distinct from raw-bit preservation; the rest of the pixel ordering/show API
remains pending.

For non-identity image operations, short vectors or two negative dimensions
can expose unchecked/uninitialized native memory in pixelMap and are not
portable oracle cases. JVM initialization, bounds checking and array size limits
remain platform boundaries. Existing upstream YA8 promotion branches are
covered at the promotion boundary; their PNG/BMP/GIF/TGA/TIFF encoding branches
still need the corresponding codec and image API ports.

Remaining work includes the rest of the pixel/image API and transformations,
other codecs and cross-format tests, general image dispatch, remaining Pandoc
formats/application behavior, and Calibre MOBI/additional formats. The full
objective remains active.

All 171,705 Kotlin tests pass with no failures, errors or skips.

## Color planes, transparency and luma — 2026-10-13

`ImagePlanes` implements unsafeExtractComponent for all 15 original pixel types,
including the new Word32 grayscale storage, and all 32 named ColorPlane
instances across YA8/YA16, RGB8/RGB16/RGBF, RGBA8/RGBA16, YCbCr8 and
CMYK8/CMYK16. Sealed plane families retain which planes are valid for each
image type. Alpha is component 1 for YA and component 3 for RGBA. Named
extraction delegates to the same original linear stride behavior as unsafe
extraction, creates a new grayscale vector and preserves image dimensions.

The original upper-bound-only guard and exact invalid-component error text are
retained. The guard runs before output size/allocation or source-data access.
Negative indices on empty output perform no read and return empty images.
Two negative dimensions give a positive linear pixel count and are processed
by stride extraction, matching the source instead of a row-loop replacement.

`ImageTransparency` implements all four TransparentPixel instances: YA8/YA16
to grayscale and RGBA8/RGBA16 to RGB. It includes dropTransparency,
dropAlphaLayer, the original deprecated getTransparency name, and pixelOpacity
for these four transparent types. Whole-image alpha removal follows original
pixelMap row/column traversal, produces fresh storage, leaves source data
unchanged and ignores unused vector tails.

`ImageLuma` implements all ten LumaPlaneExtractable instances. Word8, Word16,
Word32 and Float scalar luma is identity; extractLumaPlane on their images returns
the same image and complete vector, including unused data and malformed
dimensions. YA8 and YCbCr8 luma uses linear channel extraction. RGB8/RGBA8/RGB16
retain the original Double 0.3/0.59/0.11 expression and floor; alpha is ignored.
RGBF retains Float products and additions in source order. Computed-image paths
use the original row/column traversal.

A separate frozen oracle compiles unchanged JuicyPixels 3.3.9 and calls these
methods directly, with pixelMap batches for the per-pixel methods. It implements
no extraction, transparency or luma formula. Stable names independently observe
original image sharing. All 5,947 captures pass: 5,722 successful results,
225 original invalid-component exceptions, and 75,048,509 serialized output bytes.
The captured operation counts are:

- 1,346 named-plane and 2,015 unsafe component requests.
- 939 computeLuma batches and 955 extractLumaPlane requests.
- 173 each for dropAlphaLayer, dropTransparency, getTransparency and pixelOpacity.

Coverage includes every RGB triple for RGB8 and RGBA8 luma through both pixel
and image paths (67,108,864 pixel results), all 65,536 YA8 pairs for both luma
paths and all four transparency paths, full byte/Word16 component ranges,
Word32 bit boundaries, Cartesian boundary products, seeded odd/non-square
images, zero dimensions, vector tails and defined negative-dimension stride
cases. Float copies include both signs, all exponents at six mantissa boundaries,
subnormals, infinities, NaN signs/payloads and 4,096 seeded raw patterns.

All finite, infinite and zero Float results compare bit exactly. For computed
RGBF luma only, NaN sign/payload is a native platform/compiler boundary; tests
canonicalize computed NaNs for comparison while requiring NaN at every expected
position. The original raw outputs and their hashes are also retained. Raw Float
plane copies and scalar identity results preserve every bit, including NaN
payloads. There is no epsilon comparison or reduction of the tested input domain.

The original planeSeparationYA8Test 256×256 generator is captured for both luma
and alpha extraction. Its PNG writing, and original RGB/RGBA PNG plane-separation
branches, still need the corresponding raster codecs and image dispatch.
Another 180 original observations verify scalar luma image identity, including
short/empty vectors, complete tails, negative dimensions and Int.MAX_VALUE width
without allocation. Other extraction and alpha-removal outputs use fresh storage.

The 225 errors cover every pixel type, five invalid indices (including Int64
maximum) and three shapes, including Int.MAX_VALUE squared with empty source.
They verify error priority before huge allocation or source access. Empty-output
negative indices include Int64 minimum. Nonempty negative indices and short
source vectors enter unchecked native reads and have no portable oracle output.
PixelMap operations with two negative dimensions can expose uninitialized native
output and are excluded; the defined stride and identity cases are covered.
JVM bounds checking, zero initialization and array/dimension representation
limits remain platform boundaries.

Full-output SHA-256/length checks cover large domain/range batches; other results
also retain and compare original bytes. The importer hashes itself, the complete
upstream source, original test runner and frozen harness, and CI verifies the
manifest. Complete multi-channel Word16/RGBA/Float32 domains are not claimed
exhaustive. Remaining work includes the rest of the pixel/image API, mutable
operations, transformations, other codecs and cross-format tests, remaining
Pandoc formats/application behavior, and Calibre MOBI/additional formats. The
full objective remains active.

All 177,652 Kotlin tests pass with no failures, errors or skips.

## Pixel operations, access and mutable images — 2026-10-13

`ImagePixelOps` implements componentCount, pixelOpacity, mixWith, mixWithAlpha
and colorMap for all 15 original Pixel types. Component indices and field routing
match upstream. The default mixWithAlpha ignores its separate alpha function;
only YA16, RGBA8 and RGBA16 override it. YA8 intentionally keeps the default
and applies its color callback to alpha, unlike YA16. CMYK/YCCK K remains an
ordinary component and their pixelOpacity is maximum opacity. Existing
ImageTransparency opacity helpers now delegate to this shared implementation.

`ImagePixelAccess` implements both base-index methods, pixelAt/readPixel/writePixel
and unsafe pixel access for all 15 types. Checked access validates only the flat
component-buffer index, preserving out-of-shape/negative coordinates when their
flattened address is valid. Index arithmetic uses original wrapping Int64
addition/multiplication. The shared raw-width helper also verifies the full Int64
width domain; current typed image records retain their existing JVM Int dimensions.
Unsafe vector readers have concrete type suffixes because Kotlin cannot select
their pixel type from the return type alone. Raw unsafe indices count components,
not pixels, and valid unaligned offsets are preserved.

The exact original bounds error is `index out of bounds (index,length)`. A
checked multi-component write updates earlier components before reporting the
first failing index; there is no whole-pixel prevalidation or rollback. Unsafe
methods retain JVM memory safety rather than emulate unchecked native memory.

`ImageMutable` provides typed mutable records, safe thaw/freeze copies, unsafe
thaw/freeze sharing, background initialization and concrete newMutableY8/.../CMYK16
specializations of original newMutableImage. Safe operations copy the entire
component vector, including tails unrelated to dimensions; unsafe operations
share the same storage. Background creation follows the original row/column
initializer. Negative allocation counts produce original `negative length n`.
New JVM arrays are zero-initialized, whereas original M.new storage is garbage;
reference tests fully initialize every component before observing new storage.

A separate frozen oracle builds unchanged original JuicyPixels sources and calls
all these methods directly. The supplied arithmetic callbacks are test inputs;
the adapter implements no Pixel routing, address formula, bounds check, access
or lifecycle algorithm. Foreign pointer addresses observe original nonempty
buffer sharing. Safe freeze snapshots are observed after later mutable writes.
Unsafe freeze consumes its mutable view, while unsafe thaw captures immutable
contents before consumption and observes the mutable result afterward.

All 15,022 captures pass, including 10,498 successes and 4,524 original errors.
The complete observed outputs total 46,120,078 serialized bytes. Captures include:

- 75 component-count/opacity, 525 mixWith, 525 mixWithAlpha and 375 colorMap cases.
- 1,800 each for checked immutable read, mutable read and mutable write.
- 215 each for unsafe immutable read, mutable read and mutable write.
- 2,316 each for immutable and mutable base indices, including Int64 boundaries,
  wrapping products and seeded full-width arithmetic.
- 640 each for safe freeze/thaw and unsafe freeze/thaw, with tails, empty/short
  vectors and dimensions independent of vector length.
- 135 background-creation and 150 allocation cases, including negative allocation
  errors and two negative dimensions whose positive allocation is fully initialized.

The component tests cover every ordered byte-component pair for binary callbacks,
every byte/Word16 component value, Word32 bit boundaries, raw Float signs,
exponents and mantissa boundaries, plus seeded values. Distinct color/alpha
callbacks and throwing callbacks verify exactly when each callback is used or
ignored. There are 44 original mixWith channel-1 failures, 64 mixWithAlpha
callback failures and 60 colorMap failures. Full multi-channel domains and
complete Word16-pair/Word32/Float32 domains are not claimed exhaustive.

There are 1,432 bounds errors for each of immutable reads, mutable reads and
writes, plus 30 each for negative background/allocation lengths. All errors
retain their original messages and observable buffers. In 138 failed writes,
the original buffer changes before the error; the Kotlin buffer matches those
partial updates exactly. Only in-bounds unsafe accesses are captured: invalid
unsafe accesses invoke native unchecked memory and have no portable oracle values.

Raw copies, reads/writes, Float identity/negation and lifecycle operations compare
every bit, including NaN payloads. Arithmetic Float callbacks compare every
finite/infinite/zero bit and computed NaNs by category, with the independent
canonical comparison hash used for native platform/compiler NaN differences.
Original raw outputs or complete-output hashes remain recorded as well.

Nonempty buffer sharing is checked against original foreign pointer equality.
Native zero-length allocations can reuse the same address, so their pointer
equality does not establish writable sharing. Kotlin still requires fresh arrays
for safe copies and identical arrays for unsafe sharing in those empty cases.
No uninitialized native bytes or invalid unsafe memory reads are asserted as
portable expectations. Huge positive allocations are not attempted; JVM array
limits, dimension representation and uninitialized-storage differences remain
platform boundaries. Generic vector views/offsets and the broader image API
still require further work.

The importer hashes itself, frozen harness/dependencies, complete pinned source
and original test runner; CI verifies the manifest and every operation's pixel
type coverage. Remaining work includes generic image representations and views,
generators/folds/traversals, packed pixel operations, dynamic/paletted images,
other codecs/cross-format tests, remaining Pandoc formats/application behavior,
and Calibre MOBI/additional formats. The full objective remains active.

All 192,674 Kotlin tests pass with no failures, errors or skips.

## Packed pixels and mutable component slices — 2026-10-13

`ImagePacked` ports all ten PackeablePixel instances: Word8, Word16, Word32 and
Float scalar identities; YA8/YA16; RGBA8/RGBA16; and CMYK8/CMYK16. Packed
representations are Word16 for YA8, Word32 for YA16/RGBA8/CMYK8, and Word64
for RGBA16/CMYK16. Components occupy their original least-significant-first bit
positions. Word64 high bits remain unsigned. Float pack/unpack is raw identity,
including both zeros, infinities and every NaN sign/payload bit.

All four original memory helpers are implemented for these types: fillImageWith,
readPackedPixelAt, writePackedPixelAt and unsafeWritePixelBetweenAt. Numeric
packing is independent of host byte order; memory helpers use native byte order,
matching original ForeignPtr casts. The pinned oracle reports little-endian
memory, as used by the current macOS/Linux checks. Other host memory orders are
not claimed independently verified by these captured fixtures.

`ImageBufferView` represents an offset/length component slice of the existing
mutable backing array without copying. Packed helpers accept these views as
well as complete buffers. Offsets count base components; reads/writes round
the supplied index down to its packed-pixel boundary, as original Int div does.
For example, RGBA8 component index 1 reads/writes the pixel at component 0,
which differs from ordinary unsafeReadPixel. Fill uses vector length divided
by component count, so incomplete component tails remain untouched. Image
dimensions do not control any of these memory operations.

Range writes retain the original asymmetry: M.slice checks start/count in base
components, then its cast vector writes count complete packed pixels beginning
at start. The Kotlin implementation preserves both that slice check and that
packed write count. Captures use ranges whose full packed span fits inside the
view, while invalid scalar slices are observed before any unsafe memory access.
Slice failures retain exact `invalid slice (start,count,length)` messages and
leave the complete parent buffer unchanged.

A separate frozen oracle builds unchanged JuicyPixels 3.3.9 and calls original
pack/unpack and memory helpers directly. It contains no packing, rounding,
slice-checking, filling or memory-access algorithm. Original component/pixel
methods are used only to construct and serialize input/output pixels. The
adapter creates real original mutable-vector slices and captures the entire
parent vector after each operation, so mutations outside a copied view could
not pass as shared-buffer behavior.

All 17,149 captures pass: 10,039 successes and 7,110 original errors, with
4,397,346 serialized output bytes:

- 199 pack and 180 unpack batches compare both packed primitives and resulting
  component vectors, preventing mutually wrong inverse functions from passing.
- 1,102 fill, 11,116 range-write, and 2,276 each packed read/write observations.
- 7,546 successful operations on nonzero-offset views.
- 1,828 successful unaligned component-index reads/writes verify rounding.
- 5,490 inner-range slice errors and 540 each fill/read/write outer-view errors.

Input coverage includes every byte/Word16 component value in every position,
every packed Word8/Word16 value, Word32/Word64 bit boundaries and seeded values,
Cartesian component boundaries, and every individual bit position. Float inputs
cover both signs, all exponents at six mantissa boundaries, 4,096 seeded raw
patterns and dedicated memory-operation cases for zeros, subnormals, infinities
and signaling/quiet NaN payloads. All Float outputs compare bit exactly; no
NaN canonicalization or epsilon comparison is used for packed operations.
Full multi-component Word32/Word64/Float32 domains are not claimed exhaustive.

Memory tests cover complete buffers, empty views, component-aligned and
misaligned view offsets, counts of zero, untouched outside storage and tails,
dimensions unrelated to storage, and defined Int64 extreme slice failures.
Only complete in-view packed reads/writes and fully contained packed range
writes are observed. Invalid native overreads/overwrites, negative packed reads
and overflowing invalid slice parameters have no portable oracle expectation.
Kotlin retains JVM memory safety. Current generic image dimensions/array limits
and broader vector-view representations remain separate outstanding work.

The importer records complete-output hashes/lengths and verbatim small outputs,
hashes itself, frozen harness/dependencies, complete original source and test
runner, and CI verifies every operation/type combination. Remaining work includes
generic image representations/views for other operators, generators/folds,
traversals, dynamic/paletted images, other codecs and cross-format tests, remaining
Pandoc formats/application behavior, and Calibre MOBI/additional formats. The
full objective remains active.

All 209,823 Kotlin tests pass with no failures, errors or skips.

## Generic raster images, generators, maps and folds

`RasterImage`, `MutableRasterImage`, `RasterVector` and `RasterCodec` port the
common image representation and the following Types.hs operations for all 15
pixel types: generateMutableImage/generateImage, withImage, generateFoldImage,
pixelMap/pixelMapXY, pixelFold/pixelFoldM/pixelFoldMap, zipPixelComponent3,
pixelBaseIndex, checked pixelAt/readPixel/writePixel and mutable allocation,
background creation and safe/unsafe freeze/thaw. Typed primitive codecs retain
ByteArray/ShortArray/IntArray/FloatArray storage and every original component
order. `RasterBridge` lets each existing immutable/mutable image buffer use the
generic API without copying. Existing format-specific APIs have not all been
migrated to this representation.

Dimensions, slice lengths and address arithmetic use Long, including original
signed Int64 wrapping. A component slice shares its parent storage. Checked
reads/writes validate each flattened component against the logical slice length,
so an out-of-range later component leaves earlier writes visible without touching
the parent's neighboring components. Safe freeze/thaw copies exactly the logical
slice, including unused tails, and resets its offset. Unsafe conversions share
the view. Dimension equality precedes data evaluation; Float comparison follows
original IEEE equality, including equal signed zeros and non-reflexive NaNs.

Data can be deferred independently of dimensions. Original generateImage runs
generation before its Image record becomes observable; this differs from
pixelMap and generateFoldImage. Captures of metadata-only results distinguish
these cases. Callback APIs with explicit pixel thunks retain original non-strict
arguments, so a constant transform can ignore an erroneous source vector.
Accumulators can themselves be Lazy values; the original foldM IO trace records
all actions before a deferred accumulator/pixel failure is demanded. pixelFold
forces the source vector even for an empty raster, while empty pixelFoldM and
pixelFoldMap can avoid it.

Row generators/maps/folds use top-to-bottom, left-to-right order. withImage and
generateFoldImage use the original [0..count-1] enumeration, including Int64-min
subtraction overflow. generateFoldImage uses checked writes. pixelFoldMap instead
visits the linear width*height component product and associates results to the
right. Its monoid interface supplies a lazy tail for short-circuit instances;
pure instances that always demand their tails can opt into a stack-safe right
fold. Component zip first checks dimensions, then combines complete vectors up
to their shortest logical length, irrespective of pixel dimensions or tails.

A separate frozen Haskell harness compiles unchanged JuicyPixels 3.3.9 and calls
these original public operations. The unexported generateMutableImage is covered
through original generateImage. The adapter only supplies test callbacks,
constructs/serializes pixels through original Pixel methods and captures results,
errors and IO traces; it contains no replacement raster algorithm. Kotlin
expectations are independent captures of the original implementation.

All 14,889 captured observations pass: 12,692 successes and 2,197 original errors,
with 211,172 serialized component bytes:

- 1,575 each pixelMap/pixelMapXY observations cover every one of the 225
  source/destination type pairs, multiple raster shapes, ignored source data and
  metadata-only results that do not demand a failing callback.
- 344 generateImage, 330 withImage and 314 generateFoldImage results cover empty,
  non-square, negative and Int64 boundary shapes, callback errors, IO ordering,
  deferred metadata, state accumulation and checked overflow failures.
- 585 pixelFold, 645 pixelFoldM and 780 pixelFoldMap observations cover coordinates,
  raster versus linear order, source-data demand, deferred errors, monoid identity,
  short-circuit Any and a deliberately non-associative bracket monoid that exposes
  right association.
- 375 each slice/freeze/thaw observations include nonzero and misaligned offsets,
  short/empty/tail vectors, independent dimension metadata, safe copies, invalid
  slices and exact native error messages.
- 345 component-zip observations cover unequal vector lengths, unused tails and
  dimension errors before data demand. 206 equality observations cover differing
  dimensions, source errors, signed zeros, infinities and same-image NaNs.
- 5,145 base-address observations cover all products of seven Int64/32-bit boundary
  widths and x/y coordinates for every pixel type, without forcing source data.
  640 each checked pixel/read/write observations exercise sliced buffers, wrapped
  addresses and complete parent-buffer snapshots; 109 failed writes modify their
  valid component prefix.

Additional tests verify alias-preserving bridges and background creation for all
15 existing image types, unsafe lifecycle aliasing, partial writes against logical
slice bounds, dimension preservation, a 100,000-pixel stack-safe monoid fold and
single-callback short-circuiting.

Primitive JVM arrays remain bounded by their Int-sized physical capacity and
available memory; Long metadata does not claim addressable multi-billion-element
native buffers. New buffers are JVM-zero-initialized, whereas native newMutableImage
storage is unspecified. Captures never compare uninitialized native contents or
unsafe accesses outside their allocated storage. General lazy monoids retain a
recursive tail; the stack-safe path requires the documented strict-tail contract.
Applicative imagePixels/imageIPixels traversals, dynamic/paletted image operations,
remaining image codecs and cross-format tests remain pending, as do remaining
Pandoc readers/writers/application behavior and all Calibre MOBI/additional-format
work. The full objective remains active.

All 224,715 Kotlin tests pass with no failures, errors or skips.

## Applicative imagePixels/imageIPixels traversals and lazy foldMap operands

`RasterTraversal` ports Types.hs imagePixels/imageIPixels through a generic
`RasterApplicative` interface, with a higher-kinded effect carrier and deferred
operands/values. Callers can collect effects, fail, short-circuit or branch without
requiring source pixels or output storage. The implementation does not replace
Applicative traversal with pixelMap. A native Identity instance is available;
tests supply separate IO, Maybe, list and Const Applicative instances.

imagePixels visits linear component indices below the wrapped Int64 product
width*height*sourceComponents. imageIPixels instead follows row/column guards.
Both accumulate the original left-associated Applicative expression, with deferred
callback application as well as deferred pixel values. The final map can return
an image's dimensions without even constructing the effect tree for Identity.
Other effects can need the tree while still ignoring its image result. Const Any
and Const Last demand different operands and can skip callback failures.

Original GenST allocation/writes are represented by immutable write plans. Their
execution is deferred in imageData, with allocation before pixel-value demand
and writes in original raster order. IO effects can therefore finish before an
output allocation or pixel-value error occurs. An effect failure stops the IO
prefix; a deferred value failure retains all preceding/completed IO actions.
A list Applicative yields every Cartesian branch in original order, with separate
output buffers. Replaying an IO action runs effects again and allocates a fresh
result. Plan execution is iterative, so a large Identity traversal does not need
one JVM call frame per write.

The Last cases also exposed a prior pixelFoldMap fidelity bug: RasterMonoid had
a strict left operand, forcing a callback value even when Last selected a later
value. Thirty independent original cases returned the final pixel while Kotlin
incorrectly raised `prefix forced`. RasterMonoid now receives thunks for both
operands, and the default fold delays transform itself. The original Any behavior
and right association remain intact; the stack-safe opt-in is documented for pure
append operations that demand both operands.

A new frozen harness compiles unchanged JuicyPixels 3.3.9 with GHC 9.12.2 and
original base Applicative instances. Only the adapter disables automatic
specialization/SpecConstr to avoid code duplication across dispatch combinations;
source modules remain compiled with -O2. The adapter supplies callbacks, serializes
results and captures IO traces; it has no traversal, allocation, write or
Applicative algorithm. The retained original test runner has no assertions for
imagePixels/imageIPixels; these are independent original-call observations,
not claimed translations of nonexistent literal tests.

All 35,670 observations pass: 29,265 successes and 6,405 original errors, with
379,086 serialized image bytes:

- 17,670 imagePixels and 17,460 imageIPixels observations cover all 225
  source/destination pairs under each of eight effects: Identity, IO, Maybe,
  lists, Const list, Const Any, Const Last and Const bracket. The intentionally
  non-associative bracket monoid verifies left association, contrasting with
  pixelFoldMap's right association.
- Metadata-only observations distinguish effect construction, pixel-value demand
  and deferred output allocation. Tests include ignored erroneous source vectors,
  callback-construction failures, IO-action failures, deferred pixel failures,
  empty/signed/Int64-overflow dimensions, component views and unused tails.
- 7,200 cross-type Int64-minimum-height captures demand metadata or data. No
  source pixel is read: source products wrap to zero or a negative value.
  Odd destination component counts still request a negative allocation, exposing
  exactly when each effect demands the output buffer.
- IO observations preserve complete action order and 270 partial failure prefixes.
  Maybe includes successful, absent and short-circuiting results. Lists compare all
  branch images, preserving ordering and all component bytes. Float components
  compare raw bits without NaN normalization or epsilon tolerances.
- 540 original pixelFoldMap/Last observations cover all 15 source types, empty,
  positive and both-negative linear shapes, ignored source errors and invalid
  initial callback values. All thirty previously failing cases now match the
  original final value; legitimate last-value/source failures remain errors.

Additional tests execute a 100,000-pixel Identity traversal into RGBA16, verify
branch-buffer independence after mutation and verify IO replay with fresh buffers.
All earlier generic raster and monoid observations also pass after the lazy-left
correction. The importer retains full outputs, hashes itself, frozen harness and
dependencies, original source and the test runner. CI verifies every
operation/effect/source/destination combination against the retained manifest.

Native uninitialized output data and unsafe accesses outside storage are excluded.
Impractically large indexed effects that actually traverse all coordinates are
excluded, while Identity metadata and wrapped empty linear products remain
observable. Physical storage still has JVM array limits. Stack behavior of a
caller's arbitrary Applicative implementation is its responsibility; iterative
write-plan execution and the provided Identity path are independently checked.
Dynamic/paletted image operations, remaining image codecs and integrations,
remaining Pandoc formats/application behavior and all Calibre MOBI/additional
formats remain pending. The full objective remains active.

All 260,387 Kotlin tests pass with no failures, errors or skips.

## Dynamic images, palette records and true-color expansion

`DynamicRaster` ports all fourteen original DynamicImage alternatives, derived
variant/image equality, dynamicMap, dynamicPixelMap and NFData forcing. YCbCrK8
is not a DynamicImage alternative in the original, though its pixel type remains
supported elsewhere. Typed generic visitors retain the image's codec witness and
receive an image thunk, so constant callbacks can ignore an erroneous whole image.
Transforms retain their variant and defer both callback application and the
resulting image. Variant mismatch rejects equality without demanding either
payload; equal variants delegate to original-style raster equality, including
non-reflexive Float NaNs. Forcing evaluates the image/vector record without
reading component bytes, matching original NFData.

`RasterPalette` ports Palette' for all fifteen pixel types, with strict Int64 size
and component vector. palettedAsImage returns width=size, height=1 and shares the
complete logical vector, including unused tails; it does not validate size
against storage. `RasterRgbPalette` represents the original RGB8 Palette alias.
`PalettedRaster` contains exactly the original TrueColorImage fallback and Y8,
RGB8, RGBA8 and RGB16 indexed variants. Fallback memoizes its lazy payload and
returns that exact dynamic image. Other alternatives preserve a lazy source and
palette and expand unsigned byte indices via the original row pixelMap semantics.
The declared palette size is ignored; complete palette pixels in the actual vector
determine valid native reads. Source indices are demanded before palette data.
Four original cases with both buffers erroneous exposed the opposite initial
Kotlin demand order, which is now corrected for every indexed alternative.

A new frozen original-code harness calls unchanged JuicyPixels 3.3.9 helpers,
derived equality and NFData. Polymorphic test callbacks inspect dimensions/length,
return a constant, preserve images, swap dimensions, construct an empty image,
invoke original pixelMap or raise errors. ForeignPtr/offset and StableName
observations supply independent sharing/identity expectations. Pattern matching
only serializes resulting variants. The harness contains no replacement dispatch,
equality, palette-expansion or image-mapping implementation. Source modules use
-O2; only adapter specialization/SpecConstr is disabled to limit code duplication.

All 4,902 original captures pass: 4,439 successes and 463 original errors, with
181,493 serialized component bytes:

- 196 dynamicMap and 434 dynamicPixelMap observations cover all fourteen variants,
  constant/non-strict callbacks, metadata/tag/data observations, callback failures,
  dimensions, full vector tails, lazy source errors and typed transformations.
- 206 image-equality and 56 cross-tag comparisons include same-image NaNs, signed
  zeros, infinities and errors that variant checks must avoid or demand.
- 168 NFData observations cover every variant with normal, erroneous image and
  erroneous vector payloads under empty, negative and extreme Int64 metadata.
- 3,276 palettedAsImage observations cover all fifteen pixel types, zero/negative/
  extreme sizes, offset/length slices, unused tails, exact slice errors and strict
  palette failures. All 3,156 successful results share the original palette vector.
  Six raw Float batches cover both signs and every exponent at six mantissa
  boundaries, preserving every bit and NaN payload without normalization.
- 300 indexed expansion captures include all 256 byte indices for each of the four
  alternatives at eight declared sizes and three vector offsets, plus deferred
  source/palette errors, empty/negative/overflow dimensions and error order.
- 266 fallback captures preserve the original dynamic value; all 224 successful
  observations have original StableName identity. A separate Kotlin regression
  ensures repeated conversion forces its lazy payload only once and returns the
  same object.

Native unsafe out-of-storage palette reads and uninitialized row-map outputs are
excluded. Metadata, tag-only and empty results still exercise deferred errors
without unsafe native access. Palette indexes used for byte comparison always
fit complete pixels in actual vector storage, even when declared size disagrees.
Primitive JVM storage limits remain unchanged. Original GIF/PNG palette codec
validation and encoder/decoder tests remain separate pending work, as do older
image API integration, gamma/tone operations, remaining image codecs, remaining
Pandoc formats/application behavior and all Calibre MOBI/additional formats.
The full objective remains active.

All 265,290 Kotlin tests pass with no failures, errors or skips.

## HDR gamma correction and tone mapping

`ImageHdr` ports Types.hs gammaCorrection/toneMapping for generic RGBF rasters,
including Int64 dimensions, logical vector views and lazy image data/parameters.
Gamma computes a Float exponent 1/gamma and applies the original float32 power
primitive to each raster component. Tone preserves the exact Float operation
order in exposure*(exposure/maxBrightness+1)/(exposure+1). Brightness is obtained
by original-style raster pixelFold and RGBF luma. Float max uses <= selection,
ignoring a NaN candidate and selecting the right signed zero; Kotlin math.max
would change these cases. Scaling maps the complete logical source vector,
including nonpixel tails, while the brightness fold only scans raster pixels.
An empty output vector does not demand exposure or brightness, even when image
dimensions claim missing pixels.

Float power is a platform runtime primitive, as in GHC. A diagnostic over 900,018
positive finite components at nine gamma values found 4,266 one-ULP differences
between original macOS gamma and JVM double Math.pow rounded to Float.
The retained tools/diagnose-hdr-double-power.py reproduces that experiment. A simple
double-power substitution is therefore not used. `SystemFloatMath` forwards only
the scalar C powf primitive through JNA 5.19.1 to the host system math library;
no upstream document/image conversion code is a runtime dependency. All pixel
traversal, conversion and tone arithmetic remain Kotlin. The native binding is
loaded lazily, so metadata and empty gamma results do not load or call it.
JNA is used under Apache-2.0, with its license choice and bundled libffi license
retained. No Apple math source or experimental approximation is incorporated.

A separate frozen harness compiles unchanged JuicyPixels 3.3.9 with GHC 9.12.2
and calls both original helpers. The tone-pixels batch adapter creates independent
1x1 inputs, calls original toneMapping for every sample, and concatenates results;
it contains no copied gamma, brightness or tone algorithm. There are no literal
HDR helper assertions in the retained original test runner, so these captures
are independent original-function expectations.

Both macOS/ARM64 and Linux/x86-64 fixtures retain 3,793 requests each: 3,268 successes
and 525 original errors per platform, with 16,207,144 serialized output bytes each.
CI is pinned to Ubuntu 24.04 to match the retained Linux math baseline.
The Linux harness was built and executed independently in GitHub Actions run
38397538265 on Ubuntu 24.04/glibc 2.39; the capture workflow and artifact provenance
are retained. The macOS capture records Darwin 25.6.0. All request fields are
identical between platforms; 96 gamma captures have different original output
hashes, while every tone capture has the same canonical arithmetic-NaN output.

- 1,834 gamma observations per platform cover scalar boundary powers, every Float
  sign/exponent at six mantissa boundaries, all triples of nineteen component
  boundaries and nine 100,002-component positive-finite sequences. Native powf
  output is checked bit exactly, including its NaN results, on the matching host.
- 1,900 tone-image and 59 independent-pixel batch observations per platform cover
  luma/max ordering, opposite NaN/zero/infinity pixel orders, negative and special
  exposures, signed zeros, underflow/overflow, slices and unused vector tails.
  Both platforms' tone expectations run on each checked host. Only arithmetic
  NaN sign/payload differences are normalized; every finite, zero and infinity
  output bit remains exact. Original raw bytes/hashes are retained unchanged.
- Metadata/data observations distinguish deferred image, vector and parameter
  failures. Empty, negative, both-negative and Int64-overflow dimensions expose
  row traversal versus full-vector mapping and deferred coefficient demand.
- The two manifests hash complete original source, test runner, harness/frozen
  dependencies and importer. CI verifies both. A separate test requires identical
  source requests across captures, preventing different host corpora from masking
  platform behavior.

Native unsafe input overreads and uninitialized row-map outputs are excluded.
Empty tone vectors can safely avoid an otherwise invalid brightness scan; those
original demand cases remain covered. Captured platform/library versions define
the numerical baselines. Windows and other math-library/architecture combinations
are not claimed independently verified. Gamma requires the host powf symbol and
the JNA native bridge; there is no silent lower-precision fallback. Physical JVM
array limits remain unchanged. HDR codec integration, older image API migration,
remaining image codecs, remaining Pandoc formats/application behavior and all
Calibre MOBI/additional formats remain pending. The full objective remains active.

All 271,043 Kotlin tests pass with no failures, errors or skips.
