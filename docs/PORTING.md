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
