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
| Skylighting style JSON, built-in themes and `Options.HighlightMethod` | `highlighting/HighlightStyle.kt`, `HighlightStyles.kt`, `options/HighlightMethod.kt` | All four original style JSON tests, 6,168 direct original-Haskell observations and 25 actual Pandoc theme lookups; eight complete original theme tables |
| KDE finite regex compiler/matcher and Unicode/UTF-8 dependencies | `highlighting/regex/KdeRegex.kt`, `KdeCompiler.kt`, `KdeMatcher.kt`, `parsing/RegexCharacters.kt`, `Utf8String.kt`, `Utf8Text.kt` | All 186 original regex results and 16 original compile-error assertions; 9,777 independent observations including all 3,960 XML RegExprs, every Haskell Char and all byte pairs; deep finite stack checks |
| Skylighting syntax records, keyword lists, map operations and RE metadata instances | `highlighting/Syntax.kt`, `Syntaxes.kt`, `regex/RE.kt`, `RERead.kt`, `REBinary.kt` | 15,132 independent observations over all 195 original syntax models, every matcher constructor, derived field/constructor order and Show, keyword resolution, lookup, RE Show/Read/JSON/Binary and cached matching; two ownership/deep-stack checks |
| Skylighting XML syntax parsing and file/directory loading; default Text.XML document parsing dependency | `highlighting/SyntaxParser.kt`, `SyntaxLoader.kt`, `SyntaxUtf8.kt`, `xml/XmlDocument.kt`, `XmlDocumentParser.kt`, `XmlDocumentLexer.kt` | 6,168 independent original observations, all 195 original XML files through document/text/file paths, all matchers/flags, namespaces/entities/diagnostics, chunk boundaries, UTF-8, POSIX/Windows filenames and both directory folds; three source-derived/deep/ownership checks |
| Skylighting syntax Binary instances and baked default map | `highlighting/SyntaxBinary.kt`, `BinaryAscCollections.kt`, `DefaultSyntaxes.kt` | 77,801 independent original Binary/default-map observations, exact full models/Show/re-encoded bytes for all 195 baked definitions, every two-byte Char input, malformed constructor/text/container errors and lookup/equality; deep rule/ownership/cache check |
| Skylighting tokenizer and context/capture state | `highlighting/Tokenizer.kt` | 27,881 independent original token/trace/error/config-Show observations: all 32 original goldens, 21 regressions, 6,240 mixed samples, 19,500 seeded Unicode fuzz inputs and 2,088 synthetic boundary cases; finite 10,000-child/include chains and immutable result/config checks |
| Skylighting HTML/HTML4, ANSI, LaTeX, ConTeXt and Typst formatters; FormatOptions and style Binary | `highlighting/format/`, `FormatOptions.kt`, `HighlightBinary.kt` | 27,030 original complete-output/model observations: all 32 original tokenized files with eight themes, every token, all public formatter functions, option/Unicode/line-boundary cases, ANSI palette/roundtrip/boolean-style cases, 14,008 Binary decodes and 359 encodes; immutable ownership check |
| Annotated table conversion and finite cell/row/head traversal | `writers/AnnotatedTable.kt` | All six original AnnotatedTable tests; 1,400 original full-model, inverse, builder and walk/query/effect-trace observations, including all 242 upstream Native fixture tables; a 10,000-column/ownership check |
| Blaze structural markup and Pandoc `Writers.Blaze.layoutMarkup` | `markup/BlazeMarkup.kt`, `BlazeRenderer.kt`, `BlazeLayout.kt` | 71,478 original observations; all 25 original HTML examples, all 65,536 byte pairs, full Markup/ChoiceString and Doc structure, lazy returns/chunks and 1,080 competing-error cases; ten original properties at 1,000 trials each, two additional deep/ownership checks and a composition check |
| Pandoc Highlighting wrapper and complete WriterOptions record / Native-FB2 integration | `highlighting/Highlighting.kt`, `ListingsLanguages.kt`, `options/WriterOptions.kt` | 7,637 original Highlighting/Shared observations, all 103 listings pairs and 195 syntax names/aliases; 915 actual Pandoc writer-option/output observations, all 39 source fields, 266 Native documents in two modes and Int64 widths; two callback/ownership/record-update checks |
| DocLayout finite documents, rendering, attributes and display widths | `layout/Doc.kt`, `DocLayout.kt`, `Attributed.kt`, `DisplayWidth.kt`, `DocValues.kt` | All 54 original render goldens and 15 other literal assertions, both original width properties, all emoji examples, every Haskell Char and 38,147 original-Haskell observations |
| Doctemplates Context/Val conversion and traversal foundation | `templates/Context.kt`, `TemplateValues.kt` | 6,880 original-Haskell observations, including the JSON contexts from all 22 original goldens, arbitrary-precision numeric conversions, Unicode map order and State traversal; a deep finite traversal check |
| Doctemplates finite Template AST, pipes and rendering | `templates/Template.kt`, `TemplateRenderer.kt` | 12,211 original-Haskell observations for Text/String, all 22 original golden outputs and nine successful unit cases through original-parser ASTs, full Doc structure, column state, loops and free variables; a deep finite template check |
| Doctemplates finite compilation, partial loading and public conveniences; Parsec character kernel | `templates/TemplateCompiler.kt`, `PartialPaths.kt`, `parsing/Parsec.kt`, `ParserCharacters.kt` | All 37 original doctemplates cases through the Kotlin compiler/application, 11,320 original observations, every Haskell Char, POSIX/Windows paths, loader effects and Parsec continuation/error states; native dependency suites and deep/large-input checks |
| Pandoc template resources, writer defaults/aliases and partial wrappers; `PandocMonad.toTextM` | `templates/PandocTemplates.kt`, `resources/PandocText.kt` | All 62 original template files packaged verbatim, 6,538 independent cases including 65,536 byte pairs, all 16 original Lua tests run natively, POSIX/Windows path comparisons and source-URL/fallback effect checks |
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
| JuicyPixels PNG pixel decoder and complete metadata | `images/PngDecoder.kt` | 27,711 direct original-Haskell observations; all 176 PNGSuite files, every valid-file pixel output, five filters, Adam7, standard bit depths, indexed/transparency expansion and deferred metadata errors |
| JuicyPixels PNG encoder and palette export | `images/PngEncoder.kt` | 4,814 original-Haskell observations; eight pixel types, both palette types, complete output bytes, original metadata/palette/plane examples and every valid PNGSuite re-encoding |
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

Continue the options layer from `Text.Pandoc.Options`. The extension tables,
format parsing, ReaderOptions fields/JSON/defaults and HighlightMethod/style JSON
are implemented; the complete WriterOptions record and defaults still need its
template/context and syntax-map dependencies, plus remaining highlighting
behavior. These dependencies must be ported rather than replacing the record
with a subset. Finite doclayout 0.6 rendering and document operations, and the
doctemplates 0.11.1 Context/Val conversion, Template/Variable/Pipe, finite
rendering/traversal and compilation are implemented, including exact parser
diagnostics, partial loading/recursion and public application/file conveniences.
All 22 original goldens and 15 unit cases now exercise the Kotlin compiler.
Next are Pandoc's template resource wrappers and the remaining syntax-map/
highlighting dependencies for the complete WriterOptions record/defaults.
Also continue the remaining DocLayout interfaces recorded below.
Context values and Template literals contain structured DocLayout documents. CLI `App.Opt` is
a separate layer. The upstream writer math default is MathML (also used by the
current FB2 writer options); FB2 renders non-WebTeX methods identically.

Current verification is recorded at the end of this ledger. Every push runs source
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


## Complete PNG pixel decoding

`PngDecoder` ports Png.hs decodePng, decodePngWithMetadata and
decodePngWithPaletteAndMetadata onto generic Kotlin rasters. Its parser retains
the original unsigned IHDR dimensions, ignored IHDR length/CRC, unvalidated
compression/filter method fields, per-chunk CRC checks and first-IEND stopping.
IDAT payloads are concatenated before zlib decompression. Five byte filters,
unknown-filter fallback, MSB-first 1/2/4-bit unpacking, 8-bit components,
big-endian 16-bit components and all seven Adam7 passes are implemented in
Kotlin without ImageIO or another platform PNG codec.

Indexed images expose the original indices and palette separately. Low-depth
grayscale uses the original generated RGB palette. Exactly one tRNS chunk adds
palette alpha, padding omitted alpha entries with 255; multiple tRNS chunks do
not add alpha. As upstream, tRNS is ignored for ordinary grayscale/true-color
images. Flattening uses the previously ported palettedToTrueColor operation.
True-color pixel work stays deferred inside TrueColorImage; indexed results
force the deinterlacer while constructing the decoder result. Empty passes can
avoid both zlib demand and an otherwise invalid bit-depth unpacker.

Metadata covers first-pHYs/first-gAMA selection, typed standard text keywords,
Latin-1 unknown keys/values, malformed textual headers and zTXt. Shared metadata
entries now retain lazy values through insertion/deletion/union, so key/DPI
observations and overridden text values do not force unrelated compressed tails.
The first zTXt output constructor remains strict, with right-to-left demand from
the original foldMap. Full text demand checks the entire stream. Zlib uses the
JDK decompression primitive, with the original exception messages and distinct
Either failures preserved. The older metadata-only consumer remains separately
tested against its existing immutable 6,329-capture corpus.

A frozen `tools/haskell-png-decoder-reference` compiles unchanged JuicyPixels
3.3.9 with GHC 9.12.2, vector 0.13.2.0 and zlib 0.7.1.1. The captured macOS
system zlib is 1.2.12. The adapter calls original decode/expansion/metadata APIs;
it contains no copied filter, unpacking, palette or metadata conversion logic.
Reproduce captures with the built png-decoder-reference executable via
`python3 tools/import-png-decoder-fixtures.py --oracle PATH`.

The retained corpus contains 27,711 independent observations: 19,461 image/tag/
result successes, 2,711 metadata successes, 5,190 Either failures and 349 original
exceptions. Successful outputs include 15,159,456 component/palette bytes,
checked byte-exactly or against original SHA-256 hashes for larger outputs.
The manifest hashes 248 source/harness/corpus files and is verified by CI.

- Every one of the 176 retained PNGSuite files is used verbatim, including all
 161 original validTests names, huge.png and 14 original invalidTests names.
 All 162 valid-file pixel outputs are compared. xhdn0g08 also decodes, matching
 upstream's ignored IHDR CRC rather than an assumed strict PNG validator.
- Synthetic inputs cover every standard color/depth combination, fifteen shapes,
 all filter predictors and unknown filters, interlaced/noninterlaced scans and
 empty passes. All 256 row-filter bytes and header method/color/interlace bytes
 are exercised; IDAT splits cross zlib header/body boundaries.
- Palette and transparency cases cover grayscale expansion, omitted/empty/short/
 full/oversized alpha tables, repeated tRNS, palette validation and separate
 indexed-versus-expanded observations. Defined nonstandard-depth and empty
 arbitrary-depth behavior is retained instead of silently validating the spec.
- Metadata cases exercise physical units, gamma boundaries, every standard text
 key, binary Latin-1 values, duplicate-key order, compressed-tail failures and
 key-only/title-only/DPI-only demand with conflicting text failures.
- File truncations, signature/chunk-CRC corruption, short/invalid zlib headers,
 missing checksums, incorrect Adler checksums and trailing bytes are compared
 with original parser/decompression results. Decode convenience wrappers are
 separately checked against all 174 corpus files with defined dynamic demand.

Native uninitialized output or unsafe scanline/storage reads have no portable
oracle. xd0n2c08 and xd9n2c08 are retained for safe result/palette-tag/metadata
observations only; synthetic unsafe shapes likewise avoid pixel demand.
Kotlin guards truncated inflated rows with TruncatedScanlineException, verified
separately without claiming it matches a native unsafe-read result. Primitive
JVM array-capacity limits remain as documented for generic rasters. Metadata
strings materialize on value demand in Kotlin; arbitrary Haskell lazy string
prefix consumption is not a Kotlin String operation.

PNG writing, APNG binary helpers, original PNG cross-format re-encoding tests,
older image API integration, remaining image codecs, remaining Pandoc formats/
application behavior and all Calibre MOBI/additional formats remain pending.
The full objective remains active.

All 298,929 Kotlin tests pass with no failures, errors or skips. All 48 source/
fixture manifests verify against the retained originals.
All 234 installed-CLI comparisons pass: 14 FB2, 66 media, 69 format modifiers
and 85 math-option cases.


## PNG encoding and original writer examples

`PngEncoder` ports all eight PngSavable instances, both RGB8/RGBA8
PngPaletteSaveable instances, metadata-bearing variants, encodeDynamicPng,
writePng and writeDynamicPng. Kotlin constructs noninterlaced IHDR, zero-filter
rows, big-endian 16-bit components, PLTE/tRNS, IDAT and IEND. JDK Deflater provides
only the zlib primitive; document/image conversion does not depend on an upstream
runtime. Complete compressed files, chunk lengths and CRCs match the original
outputs byte for byte, including the large-image and compression-buffer cases.

Palette validation uses the declared width/height before demanding indices, and
checks every component in the logical index vector, including unused raster
tails. RGB palette export retains the complete logical vector even when its
length disagrees with the declared width; the resulting unusual chunk length
and CRC are preserved. RGBA export drops alpha and extracts the alpha plane over
the declared palette raster, discarding unused vector tails. Typed/dynamic encoded
bytes remain lazy; unsupported dynamic variants fail without forcing their image.
Empty heights skip source data, while zero-width 8-bit rows still demand the
source vector; the 16-bit empty-row loop can avoid it.

Metadata writing includes pHYs when both DPI fields exist, gAMA and all nine
standard text keywords, plus Unknown/String values. DPI arithmetic preserves
Word64 overflow before narrowing to Word32. Char8 packing truncates Unicode
scalar values to their low byte, including supplementary code points. Unknown
non-string, Exif, Format, Width, Height and ColorSpace entries are ignored.
Metadata order follows the original storage order. The first DPI lookup short
circuits if DpiX is absent.

A Gamma diagnostic exposed two mismatches from a mathematical BigInteger ceiling
at extreme finite values. GHC's optimized Word32 ceiling instead specializes to
ceilingDoubleInt followed by narrowing. The retained GHC 9.12.2 RealFracMethods
source records its truncation, Double comparison and Int64 increment/wrap.
Kotlin now preserves that sequence with the captured ARM64 conversion behavior;
NaN, infinities and large finite Gamma values are not silently rejected or
normalized. The 81 Gamma observations include both signs near 2^31, 2^32, 2^52,
2^53, 2^62, 2^63, 2^64, 2^65, 2^80 and 2^100 after scaling. Out-of-Int64 floating
conversion is architecture dependent in native GHC; the retained macOS/ARM64
baseline is the compatibility oracle here. Identical native-GHC overflow results
on other architectures are not claimed. All ordinary PNG compression expectations
are checked unchanged on both the local JVM and Linux CI.

A separate frozen original-Haskell encoder harness calls unchanged JuicyPixels
3.3.9 with GHC 9.12.2/vector 0.13.2.0/zlib 0.7.1.1, using system zlib 1.2.12 for
capture. It contains input adapters and calls exported original encoding,
decoding, metadata and plane-extraction functions; no encoder implementation is
copied into the oracle. Reproduce with
`python3 tools/import-png-encoder-fixtures.py --oracle PATH`.

There are 4,814 original observations: 2,287 complete PNG successes, 286 deferred
Right results, 1,926 Either failures and 315 exceptions. The 7,463,671 output bytes
are compared directly for smaller files and by original length/SHA-256 otherwise.
The manifest covers 255 original/harness/corpus/source files and is verified by CI.

- Every original validTests PNG and huge.png is decoded and re-encoded through
  original encodeDynamicPng, compared with the full Kotlin decoder/encoder path.
  All 174 files with defined dynamic demand are included; all 162 valid files and
  the ignored-header-CRC xhdn0g08 case produce independently captured PNG bytes.
- Original metadataWriteTest and palettedPngCreation inputs are retained, including
  the exact metadata insertion order, white 16x16 RGB raster and 256x128 index
  raster with a 256-entry transparent RGBA palette.
- Original RGB/RGBA plane-separation images are retained verbatim in their own
  vendor tree, with original revision/provenance and license. All seven plane
  outputs run original decode/extract/encode and the corresponding Kotlin path.
  Original YA8 plane-separation luma, alpha and combined writer examples also run.
- Synthetic coverage includes eight savable types, zero/max/pattern/random
  components, row shapes, slices, tails, zero/negative/extreme header dimensions,
  deferred image/vector/metadata errors and all six unsupported dynamic types.
- Both palette types exercise every byte index at five palette widths, invalid
  dimensions, index tails, palette/vector slices, validation/demand ordering and
  deferred palette data failures. Metadata cases include all text keywords,
  embedded NUL, Latin-1/Unicode truncation, DPI boundaries/missing fields, duplicate
  insertion order, ignored keys and Gamma conversion boundaries.
- Twenty random 8/16-bit inputs cross 16/32/64-KiB zlib buffer boundaries and a
  100,003-component row. A separate filesystem test checks both file helpers
  against original PNG bytes and verifies an unsupported type preserves an
  existing file without forcing its image. Two additional original IO probes
  verify that typed/dynamic write failures open and truncate a destination before
  demanding an erroneous image.

Native unsafe input overreads/uninitialized output are excluded as in the decoder.
Primitive JVM array limits remain. The APIs materialize a full ByteArray when its
lazy result is demanded; arbitrary Haskell lazy-ByteString prefix consumption is
not exposed as a ByteArray operation. Incremental file prefixes before a late
encoding failure likewise require the remaining lazy-stream integration.
APNG/binary helper coverage, original
cross-format branches requiring other image codecs, older image API integration,
remaining image codecs, remaining Pandoc formats/application behavior and all
Calibre MOBI/additional formats remain pending. The full objective remains active.

All 303,744 Kotlin tests pass with no failures, errors or skips. All 49 source/
fixture manifests verify; all 234 installed-CLI comparisons pass.


## Highlighting style and HighlightMethod layer

The Kotlin style layer translates the pinned skylighting-core 0.15 Color,
TokenType, TokenStyle and Style JSON behavior, structural equality/order, native
Show rendering and the string/Int/byte-channel/Double-channel color conversions.
All 31 token constructors retain their original ordering. TokenType deliberately
preserves the original distinction between generic toJSON ("KeywordTok") and its
custom toEncoding ("Keyword"); style map keys use the latter spelling.

All eight complete built-in themes are generated from the unchanged literal
Skylighting.Styles source, independently of expected outputs: pygments, tango,
espresso, zenburn, kate, monochrome, breezedark and haddock. The default is pygments.
The Pandoc lookup layer preserves Unicode lowercasing of built-in names,
case-sensitive .theme suffix selection and the original unknown-style and invalid
file-theme option messages, including malformed UTF-8 files. Filesystem IO failures currently retain JVM exception
behavior; full Pandoc error-framework integration remains pending.

Theme decoding preserves first-member-wins duplicate JSON semantics, including
escaped duplicate keys and nested maps. Every member is validated even if it is
unknown or subsequently discarded. Lone Unicode surrogate escapes and malformed
number literals are rejected. Style decoding retains field demand order, optional
null/default behavior, editor-color fallbacks, exact raw Normal fallback, unknown
style names mapping to OtherTok and ascending raw-key collision precedence.
Schema errors retain original Aeson JSONPath spelling, including quoted
punctuation/underscore keys and Unicode letter/number categories. The shared ICU
category helper also replaces the identical private TagSoup predicates.

Color strings count Unicode scalar values, ignore the two ARGB alpha characters,
and preserve original reads partial-consumption behavior rather than enforcing a
stricter CSS-color parser. In particular, a single hex digit followed by Haskell
whitespace is accepted, and the final channel can terminate with a closing
parenthesis. Double conversions preserve original range guards, floor-based
quantization, NaN/infinity rejection and exact returned IEEE-754 channel bits.
HighlightMethod includes all four constructors, their JSON aliases and original
Show representation; booleans and null retain the original decoding defaults.

The complete immutable skylighting-core release (284 original files) is retained
with provenance and BSD license. Original tokenizer, regex, syntax parser and
formatter tests are retained but are not counted as Kotlin test ports. Aeson
2.2.3.0's original error-formatting module and license are retained separately;
this is not an Aeson port. All 303 source/harness/provenance files are hashed.

A frozen GHC 9.12.2/Aeson 2.2.3.0 test-only oracle calls unchanged Skylighting
modules and the exact original Pandoc HighlightMethod excerpt. Reproduce captures
with `python3 tools/import-highlight-style-fixtures.py --oracle PATH`; build
instructions are in `tools/haskell-highlight-style-reference/README.md`.
The independent corpus has 6,168 observations: 2,531 accepted results, 2,320
rejections and 1,317 ordering/equality comparisons. It covers every token,
eight themes, color/channel boundaries, map collisions, duplicate JSON members,
field/type errors, Unicode paths and HighlightMethod aliases. All four original
Skylighting JSON assertions are translated directly. Actual Pandoc 3.12.1 CLI
output independently verifies all eight themes and 25 name/file lookups.
Together these provide 6,197 Kotlin tests. CI checks both the literal table
generation and the captured source/fixture manifests.

JSON object member order is compared semantically. Invalid raw JSON is checked
for rejection, but exact Aeson syntax-error wording is not yet implemented;
the original wording remains in the independent fixtures. Schema and Pandoc
option errors are compared exactly. Colour-package conversions, derived Binary/
Read interfaces, SyntaxMap/parser/tokenizer/formatters, template/context engines,
the full 39-field WriterOptions record/defaults, Lua integration and full CLI
highlighting behavior remain pending. No reduced WriterOptions placeholder is
introduced. Remaining Pandoc infrastructure, readers/writers and all Calibre
MOBI/additional-format work remain in the original objective.

All 309,941 Kotlin tests pass locally with no failures, errors or skips. All 51
source/fixture checks verify; all 234 installed-CLI comparisons pass.

## DocLayout finite rendering and document dependency

The pinned doclayout 0.6 dependency is retained unchanged (all 28 original files),
with archive provenance and BSD license. Kotlin models all 16 Doc constructors,
the full Font/StyleReq state, Attr and Attributed values and the HasChars
abstraction. Its production renderer is Kotlin; no Haskell or Pandoc executable
is used for production layout.

The finite public operations include literal/text/character construction,
horizontal/vertical composition, breaking spaces, carriage returns, explicit
newlines/blank lines, prefixes, flush/nest/hang, conditional text, nowrap,
nestle/chomp, left/center/right blocks, expandable borders, delimiters, styles,
links, plain/ANSI rendering, offset/minOffset/updateColumn and height. Native
signed-Int arithmetic uses Long, including overflow and negative cached widths.
Explicit Text/Block/CookedText/VFill widths retain original behavior even when
they disagree with their contents.

Rendering preserves normalization order, trimmed prefixes on empty lines,
prefix restoration, block padding, combining marks during chopping, cooked
attribute segments, ANSI resets, OSC8 links and original nested-link handling.
BeforeNonBlank uses the original distinct measurement and rendering rules.
Two independent random cases exposed nested BeforeNonBlank measurement and
lookahead after ignored nested links; the fixes are checked against the original
corpus and reduced regression inputs.

The original 1,176 Unicode range boundaries and both optimized shortcut guard
sets are translated directly from retained source. Width calculation preserves
first-character combining behavior, narrow/wide ambiguous characters, emoji
presentation selectors, skin tones, ZWJ state and the original emojis 0.1.5 map.
It does not substitute platform Unicode widths. The corpus probes all 1,114,112
Haskell Char values, including surrogate code points, through charWidth, four
string-width variants and all three character predicates.

HasChars supports scalar Text, explicit CharacterString values and nested Attr/
Attributed representations. CharacterString distinguishes separate surrogate
Chars from a supplementary character; its plain output is compared as code-point
lists, while ANSI builder output observes original Text replacement behavior.
Attribute concatenation retains the first part's font/link; line splitting and
folds preserve original segment order and empty segments. Monoid concatenation
retains the original right fold; starting an Attr fold from mempty on the left
would discard the first font/link. Standalone Attr rendering is independently
checked as well as Attributed rendering. Font rendering covers
all finite style-field combinations, optional font links and request sequences.
Plain output concatenates buffers in one pass to avoid quadratic string copying.

Value mapping and folds preserve original derived field order, font/link/prefix
fields and cached widths. Effectful mapping is checked against the original
State traversal, including values inside cooked text and block attribute lists.
Each occurrence is visited; shared subtrees are not memoized. Finite work stacks
also cover 50,000 concatenations and 10,000 nested style/prefix pairs on the JVM.

A frozen original-GHC oracle compiles unchanged DocLayout/ANSIFont/Attributed/
HasChars sources, with emojis 0.1.5, QuickCheck and GHC 9.12.2. Its separate
unchanged original test suite passed all 4,024 tests, including two properties
with 10,000 examples each. Build/capture instructions are in
`tools/haskell-doclayout-reference/README.md`.

All 54 original renderTest expressions and literal goldens are read from the
original test file by a restricted expression reader. Before accepting captures,
the original executable must reproduce every literal golden. The 15 remaining
literal assertions are translated directly (three offset assertions share one
Kotlin test). All 3,953 base/ZWJ emoji inputs and both shortcut properties are
covered; 20,000 retained inputs come from the actual QuickCheck Arbitrary String
generator with fixed seeds/sizes. QuickCheck's runtime/shrinking infrastructure
is not itself ported.

The corpus has 38,147 original observations: 23,953 string-width cases, 272
all-Char batches, 5,489 font results, 4,266 documents, 900 attribute concatenations,
786 derived value walks, 650 rich documents, 196 Int64 measurements, 140 Haskell
String documents, 1,430 standalone Attr documents and 65 attributed fold/split/build cases. Together with the
direct original assertions and deep-document check, this adds 38,161 Kotlin
tests. The manifest verifies 40 original/harness/importer/provenance files;
CI also verifies both generated source tables/classifiers.

This is the finite rendering/document layer. Exact derived Haskell Show/Read/Ord
interfaces, arbitrary Applicative traversal interfaces, fully lazy/infinite
strings/documents and incremental lazy-Text rendering remain pending. JVM
string/array capacity guards remain explicit platform limits. Doctemplates'
Template parsing/partial loading, remaining syntax highlighting,
the complete WriterOptions record/defaults and Lua/application integration remain
pending. Remaining Pandoc formats/infrastructure and all calibre MOBI/additional
formats stay in the original objective; the full port is not complete.

At this milestone all 348,102 Kotlin tests passed locally with no failures,
errors or skips. All 53 source/fixture checks verified; all 234 installed-CLI
comparisons passed.

## Doctemplates Context/Val foundation

The complete original doctemplates 0.11.1 Hackage tree is retained unchanged,
including all 22 original file goldens and 15 unit tests. Its immutable archive
SHA256, provenance and BSD license are retained. Pandoc's pinned stack.yaml
selects this version. A frozen GHC 9.12.2 oracle compiles the unchanged original
modules together with the retained DocLayout 0.6 sources. The unchanged original
test executable passed all 37 tests when run from the package root, where it
can actually find every golden and partial fixture. This is an upstream baseline;
it does not claim Kotlin template parser/rendering coverage.

Kotlin Context and all five Val constructors retain left-biased Data.Map union,
Unicode-scalar key order, missing versus present NullVal, document structure and
cached widths. Explicit ToContext/FromContext converters cover values, contexts,
target strings, documents, lists, maps, booleans and JSON. Non-map ToContext
inputs become an empty context; FromJSON Context instead rejects them with
`Expecting MapVal`. List extraction is all-or-none and accepts a successfully
converted non-list value as a singleton. A Haskell String target is a single
SimpleVal, while a list of strings is a ListVal; Kotlin exposes the original
type-class choices explicitly rather than guessing from runtime collections.

JSON numbers become literal documents and serialize back as JSON strings.
Integral values use arbitrary-precision decimal integers, including values
beyond Int64 and Double's range. Non-integral values use Scientific's Double
conversion followed by the existing GHC display port, preserving negative
underflow, infinity and rounding boundaries. JSON is therefore intentionally
not an identity roundtrip. Source-derived mapping/folding visits raw document
values in the original order; it does not flatten a SimpleVal before mapping,
change its cached width, visit prefix/link metadata or memoize shared subtrees.
Finite value mapping and folding use work stacks and handle 20,000 alternating
list/map levels on the JVM.

The independent corpus has 6,880 observations: 5,203 raw-JSON contexts/numbers,
25 other JSON values, 32 typed conversions, 1,319 nested values/documents and
301 unions/lookups. It includes the actual JSON context of every original
golden and 5,000 seeded decimal strings without an intermediate Python float.
Functor and State traversal comparisons include raw values, effects, complete
JSON and plain/ANSI renders at three widths, including attributed blocks and
deliberately inconsistent cached widths. The manifest checks 80 source,
license, harness, freeze and importer files. The corpus plus the deep traversal
check adds 6,881 Kotlin tests.

At this milestone, remaining doctemplates work included Template/Variable/Pipe constructors,
parsing and exact diagnostics, rendering and truthiness, pipes, nested templates,
partial loading/recursion, Pandoc's resource wrappers, original golden output
and original unit cases in Kotlin, exact Haskell Show and arbitrary Applicative
interfaces. Complete WriterOptions/defaults, remaining Pandoc functionality,
Lua/application/CLI integration and all calibre work remain in the objective.

At this milestone all 354,983 Kotlin tests passed locally with no failures,
errors or skips across 54 suites. All 54 source/fixture checks verified;
all 234 installed-CLI comparisons passed.

## Doctemplates finite templates, pipes and rendering

The eight original Template constructors, Variable Semigroup/Monoid and all
14 Pipe constructors are translated from doctemplates 0.11.1 Internal.hs.
Variable/Pipe/Border order retains original constructor and field order,
signed 64-bit widths and Unicode Text comparison. Finite template mapping,
folding and effectful mapping visit literal Doc values in source-derived order,
including unselected conditional branches and loop separators. Free-variable
extraction preserves first occurrence and removes loop-bound paths and `it`
only inside the loop body; the separator remains outside that binding scope.

The renderer retains original State Int evaluation order. Interpolation
recursively flattens lists, emits `false` for BoolVal False but treats it as
false in a condition, treats maps as true and tests SimpleVal's structural
Empty constructor rather than its rendered text. Only final NewLine or
CarriageReturn nodes on the right Concat spine are removed. Loop binding sets
`it` and adjusts existing variable paths without creating missing keys.
All loop bodies run before the separator is evaluated once, even for an empty
loop. Nesting captures the column before the child runs; partial pipes do not
recompute column state after transforming the child result.

All pipes preserve their distinct original behavior: map/list pairs, scalar
length, raw-value full case mapping, list selection/reversal, rendered scalar
reversal, recursive chomp/nowrap/alpha/Roman transforms and alignment blocks
with vertical borders. Decimal parsing retains Int64 wrap, alpha cycles every
26 letters, and Roman zero becomes an empty document while values outside
0..3999 remain unchanged. Alignment turns NullVal into an empty SimpleVal,
but leaves other non-simple values unchanged.

The expanded frozen oracle still compiles unchanged original modules. It
captures genuine Text and String execution, complete rendered Doc constructor
trees (including cached widths, prefixes, fonts and links), six plain/ANSI widths,
three starting-column measurements, offsets/heights, Template mapping/folds and
State traversal. Before capturing any original example, the unchanged original
compiler/renderer must reproduce its literal golden/assertion. Kotlin consumes
the captured AST and renders it independently. All 22 original file goldens and
nine successful original unit cases are covered this way; the six original
parser diagnostic cases are still pending in Kotlin. The same 31 successful
inputs are also executed with the original String target.

Structural comparisons exposed right-associative Doc concatenation that earlier
text-only checks could not distinguish. DocLayout's spacing, line separators
and delimiters, and template block borders now retain the original right
grouping. Int64 cases also cover state wrap and nesting an Empty at a huge
column: the original guard returns Empty before evaluating the prefix, so Kotlin
now avoids that unnecessary allocation too. The existing DocLayout and Context
corpora continue to pass. The 6,880 Context observations remain unchanged;
their manifest only refreshes provenance for the expanded shared oracle.

There are 12,211 new native observations: 11,016 Text runtime cases, 894 String
runtime cases, 300 Variable union/order cases and a complete pairwise Pipe order
matrix. Coverage includes all Roman inputs 0..4000, chained pipes, attributed
blocks, Unicode expansions, null/missing paths, nested binding, column-sensitive
separators/partials, Int64 boundaries and 2,500 seeded templates. The source
manifest checks 81 files. A separate finite-depth test renders, maps and folds
20,000 concatenations and renders 20,000 nested templates, adding 12,212 Kotlin
tests in total.

At this milestone, remaining work included Kotlin template parsing, exact parser diagnostics,
partial file/resource loading and recursion semantics, Pandoc's template
wrappers, full lazy/infinite interfaces, exact Haskell Show/Read and Template
Ord, arbitrary Applicative interfaces, complete WriterOptions/defaults and
highlighting dependencies. Remaining Pandoc formats/application/Lua/CLI and all
calibre MOBI/additional formats remain in the original objective.

At this milestone all 367,195 Kotlin tests passed locally with no failures,
errors or skips across 55 suites. All 55 source/fixture checks verified;
all 234 installed-CLI comparisons passed.

## Doctemplates finite parser and partial loading

The complete original Parser.hs grammar is translated to Kotlin: dollar/braced
directives and escapes, variables/reserved words, conditionals/elseif, loops and
separators, comments, reflow, indentation/nesting, every pipe and border parameter,
partials and the original recursion guard. Compilation returns a typed syntax
failure or the complete Template AST. Public applyTemplate composition and the
compileTemplateFile IO convenience are also implemented. The default resolver
models Identity's empty partials; an injected resolver models TemplateMonad,
and the file convenience reads both root and partials from UTF-8 files.

Partial loading removes exactly one final LF, preserves the original template
path as the resolution base through nested partials, restores parser state after
each partial and retains loader effects across backtracking. The original loader
is called before checking nesting >50, so recursive templates record the extra
request before producing `(loop)`. POSIX and Windows FilePath operations retain
drive/UNC, separator, extension and absolute-name behavior without normalizing
paths. Native comparisons call unchanged original FilePath 1.5.4.0 modules for
both styles; end-to-end IO is checked on macOS and in the Linux CI matrix.

The reusable finite character-stream Parsec kernel translates consumed/empty
success/failure, bind/map, alternatives, try/lookAhead, labels, primitives,
repetition, state/input/position operations and error merging/formatting.
An explicit node/frame interpreter avoids JVM recursion; unknown-error empty
binds retain the original tail behavior. Source positions count Unicode Chars,
use eight-column tabs and original signed-Int arithmetic. SourcePos formatting,
message equality/order (which ignores message contents) and ParseError equality
are checked independently. Error merging compares line/column without source
name and prefers meaningful errors, including at restored partial positions.

Parsec.many and Control.Applicative's default many are distinct. The latter
retains successful-iteration errors; the former drops them. A partial-at-EOF
corpus exposed missing `$--` expectations when they were conflated. Kotlin now
preserves both operations, with a reduced independent regression. Applicative
repetition accumulates a persistent chain and converts once, avoiding quadratic
list copies for long flat templates. Functor mapping retains the original reply
and error rather than implementing a different state transition.

GHC 9.12.2's Unicode predicates use Unicode 16.0.0. The original GHC predicate
and generated-category sources, the versioned UnicodeData.txt and its license
are retained. Production's 1,606 flag ranges are generated from those source
categories, not from oracle expectations or host JVM/ICU data. Every one of the
1,114,112 Haskell Chars is checked against original isAlpha/isAlphaNum/isSpace/
isDigit, including surrogate Chars and ASCII-only decimal digits.

The expanded frozen oracle compiles unchanged retained doctemplates, DocLayout,
Parsec 3.1.18.0 and FilePath 1.5.4.0 modules. All 37 original doctemplates tests
pass natively and in Kotlin, including the six exact diagnostic strings read
directly from original test/test.hs. Every original golden and successful unit
assertion must match the native compiler/renderer before capture is accepted.
The native unchanged Parsec main suite passes all six cases; issue175 and
issue171 each pass, and issue127 returns Right () for one million empty actions.
These are native dependency baselines, not claims that Token/Expr or the full
Parsec package have been ported to Kotlin.

The new independent corpus has 11,320 observations: 4,380 Text and 280 String
compilations, eight actual file-compilation cases, 272 all-Char batches, 3,250
POSIX/Windows path results, 2,830 Parsec continuation/state results and 300 error/
message comparisons. Compilation observations include full ASTs, free variables,
exact errors and ordered loader requests, with reflow, recursion, malformed input,
Unicode identifiers and Int64 widths. File cases cover recursive partials,
UTF-8/BOM, invalid UTF-8 and missing roots, distinguishing syntax failures from
propagated IOExceptions. Fixture-root paths are normalized for portable checks;
exact platform IOException wording is not represented by that classification.

Two further Kotlin tests cover 2,000 nested conditions, 20,000 escaped dollars,
real temporary-file loading, one million empty parser actions and a 200,000-Char
source name. Together this adds 11,322 Kotlin tests. The new manifest checks 171
source/input/harness/license/importer files. The existing 6,880 Context and 12,211
runtime observations remain unchanged; their provenance manifests now include
the additional original modules compiled by the shared oracle (159/160 files).

At this milestone remaining work included Pandoc's default-template/resource wrappers, complete
WriterOptions/defaults and syntax-map/highlighting dependencies, exact Haskell
Show/Read/Template Ord, full laziness/infinite interfaces, generic Parsec streams/
transformers and remaining combinator/Token/Expr/Perm interfaces and tests,
remaining FilePath APIs/tests, and GHC IO exception/locale/newline policies.
Remaining Pandoc formats/application/Lua/CLI and all calibre MOBI/additional
formats remain in the original objective; the full port is not complete.

At this milestone all 378,517 Kotlin tests passed locally with no failures, errors or skips across
56 suites. All 57 source/fixture checks verify; all 234 installed-CLI comparisons
pass. The full Pandoc/calibre port remains incomplete.

## Pandoc template resources and defaults

Text.Pandoc.Templates is translated to Kotlin: getTemplate, getDefaultTemplate,
compileDefaultTemplate and both default-only/source-aware partial resolvers.
Template retrieval suppresses the input document source URL while keeping media,
resource paths, headers and the HTTP client. The immutable resource context leaves
caller state intact on success and failure; fallback data effects see the original
context. Resource-not-found and absolute missing-file IO errors fall back to
`templates/takeFileName(path)`; HTTP and other IO errors propagate. Default-only
partials always read data files, including the user overlay, rather than resources.

All seven empty writer defaults and all original aliases are implemented.
compileDefaultTemplate resolves the alias for the default data file but retains
the requested writer name in the compiler path and raises a typed template error
on syntax failure. FilePath combine/addExtension are used rather than string
concatenation: empty extensions add no dot, leading-dot extensions add no second
dot, and an empty filename adds no trailing slash. Both platform implementations
of takeFileName/isRelative/combine/addExtension and Shared.makeCanonical are
checked against unchanged FilePath modules and the unchanged Shared definition.
Canonicalization deliberately retains the source's root/parent behavior instead
of substituting Path.normalize().

All 62 pinned template/partial files are packaged byte for byte. The template
provider implements readDataFile's user-directory precedence and relative/parent
path guard for this asset set. It is explicitly a template data provider; general
Pandoc data enumeration, reference DOCX/ODT/PPTX archive construction and default
user-data directory discovery are still pending, not replaced by empty stubs.
The provider is injectable for pure/effectful data environments.

PandocMonad.toTextM removes one leading BOM and all CR bytes, decodes strict
RFC 3629 UTF-8 and reports the original input's error offset/value. Malformed
continuations report the continuation byte; truncated sequences report the lead
byte. The original unstripped-input rescan is retained even when removing CR has
joined bytes into a valid sequence: a later decoding failure can therefore report
that earlier CR. Successful normalization does not rescan the original input.

The independent corpus contains 6,538 cases: 90 default text results, 170 default
compilations/renderings, 42 template retrievals, 56 partial compilations, 3,684
individual decoding cases, 256 batches covering all 65,536 two-byte combinations,
and 2,240 POSIX/Windows path operations. It captures exact text, rendering and
syntax/data/UTF-8 errors, user overrides, media precedence, nested partial root
paths, recursion, BOM/CR handling, malformed Unicode, aliases, absolute missing
files, HTTP redirects/failures and remote partials. A 119-file manifest verifies
original sources, inputs, runner/adapters and native provenance. HTTP exception
text is classified by error kind; exact platform-specific wording remains pending.

All 16 unchanged original Lua template tests pass natively via a minimal tasty
assertion/runner adapter. It supplies no conversion algorithms. Module/function/
userdata assertions are native baselines, not a claim that Kotlin implements the
Lua API. Kotlin tests exercise the equivalent typed behaviors through independent
captures. Four further Kotlin tests check alias compiler-path/default-only loader
effects, source URL preservation on success/failure, user-data path guards and
non-missing absolute IO errors that must not trigger fallback.

The installed Homebrew Pandoc 3.12.1 was compiled without HTTP. Capture instead
uses the official same-version macOS ARM64 release; its archive and executable
SHA-256 hashes are retained. Every batch reads its input document over loopback
HTTP, and the server verifies that relative template lookup never accidentally
fetches at the document source URL. Conversion algorithms remain Kotlin in
production; the original binaries are test oracles only.

Complete WriterOptions/defaults and syntax-map/highlighting dependencies, the
remaining general data API, exact Haskell Show/Read/Template Ord, lazy/infinite
interfaces, remaining Parsec/FilePath APIs/tests and GHC IO policies are still
pending. Remaining Pandoc formats/application/Lua/CLI and all calibre MOBI and
additional formats remain part of the full original objective.

At this milestone all 385,059 Kotlin tests passed locally with no failures, errors or skips across
57 suites. All 59 source/fixture checks verify; all 234 installed-CLI comparisons
pass. The complete Pandoc/calibre port remains unfinished.

## KDE regex compilation and matching

The finite Regex.KDE core is translated directly rather than delegating to Java
regexes. All original constructors are represented: characters/predicates,
dynamic references, alternation, concatenation, repetition, captures/backreferences,
anchors and word boundaries, positive/negative forward/backward assertions,
possessive and lazy nodes and recursive subroutines. Exact Regex Show spelling,
including the original negative-assertion label and unparenthesized dynamic
constructor, is retained by an explicit-stack formatter.

Compilation implements the complete original grammar: scoped and inline i/U
flags, accepted no-op m/s flags and flag removal, branch-reset numbering and its
highest subsequent group number, atomic groups, lazy/possessive/ungreedy suffixes,
bounded/open/empty quantifiers and their 65,535 limit, dynamic numbers, both
backreference syntaxes, octal/hex escapes, Unicode/POSIX classes and their negation,
quoted class contents, literal initial ]/ranges and malformed-input backtracking.
Numbers use original signed-Int64 Read wrapping. The parser uses an explicit-stack
Parsec substrate with backtracking at the original Attoparsec choice/repetition
boundaries; it does not claim the whole Attoparsec API has been ported. Error
positions count original UTF-8 bytes minus encoded remaining Text, including
negative positions possible after lenient decoding expands malformed bytes.

Bounded optional repetition uses lazy right children of the exact original
MatchConcat/MatchAlt constructors. It unfolds on demand, preserving the publicly
observable constructor shape, choice priority and capture extraction while
avoiding eager duplication of both greedy/lazy trees. No extra repeat constructor
substitutes for the original AST. Signed repeat arithmetic is retained, including wrapped
negative counts; no smaller artificial repeat limit is substituted.

The matcher uses an explicit work stack, byte offsets/capture lengths and the
original lexicographic binary choice paths (arbitrary-precision Integer). Ordered
state sets preserve branch/repetition priority; deduplication retains the best
path for identical offset/capture states. Assertion/atomic commits, backwards
UTF-8 movement, case-insensitive capture byte lengths, recursion-at-offset guards,
zero-progress repetition and left-biased capture-group extraction are retained.
Both matchRegex and matchRegexWithGroups are observed, including arbitrary ASTs
and supplied group maps. Match bytes and returned capture maps are isolated from
caller mutation. The original 2,000-state pruning is retained exactly: a*a{1999}$
on 1,999 a's succeeds, while lengths 2,000/2,001/3,000 produce the original known
false negative. This is recorded behavior, not a claim of unrestricted PCRE parity.

GHC 9.12.2's Unicode 16 category/simple-case mapping source tables are retained.
Production data is generated independently from original UnicodeData.txt:
4,099 category ranges, 1,460 simple lowercase and 1,477 simple uppercase mappings.
All 1,114,112 Haskell Chars, including surrogates, are checked against native GHC
category/lower/upper and fourteen predicate flags. Eighty-nine compiled predicate
configurations additionally check every Char via full native-result fingerprints.
No host JVM/ICU Unicode classification substitutes for the pinned GHC predicates.

Strict ByteString UTF-8 (utf8-string 1.0.2) and Text lenient decoding (text 2.1.3)
are separate implementations. ByteString decoding retains original malformed
sequence lengths and noncharacter rejection; Text emits one replacement per bad
byte and accepts valid noncharacters. Both are checked over all 65,536 byte pairs,
all individual bytes, malformed/truncated triples and random byte strings.
Original utf8-string release files and the relevant unchanged Text decoder sources
are retained with licenses/provenance; the rest of those packages' APIs/tests
remain explicit dependency work.

The frozen native oracle compiles unchanged original KDE/UTF-8 modules. All 186
literal regex results and 16 compile-error assertions from the unchanged original
tables must pass before fixture capture. All 3,960 RegExpr elements in all 195
original XML files are inventoried, with source Parser.hs's leading-^ removal;
every one compiles natively. Additional captures exercise the XML rule flags,
2,500 generated/malformed patterns, explicit matcher ASTs/custom capture maps,
byte-level malformed inputs and deep/long/boundary cases. Ordinary ASTs and group
maps compare complete exact Show trees. For the 65,535-repeat boundary and the
largest flat/nested trees, a linear full-preorder structural fingerprint avoids
quadratic overhead in original String-based Show. It retains every original node
and field; character predicates are checked separately over all Chars.

The corpus contains 9,777 observations: 7,264 normal and four large-structure
compilations, 89 all-Char predicate configurations, 272 GHC character batches,
264 ByteString decoding and 264 Text decoding batches, and 1,620 explicit AST/group
map cases. One additional Kotlin test exercises 20,000 capture/concatenation
levels, maximal accepted repeat compilation and byte-result ownership. Together
this adds 9,778 Kotlin tests. The source manifest checks 321 retained source,
input, adapter, license, provenance and importer files. No Kotlin output is used
to generate expected results.

A separate Cabal target compiles and executes the complete unchanged original
Skylighting test file and core modules. All 4,610 native tests pass, including
original tokenizer goldens, XML loading, style assertions, no-drop/fuzz properties,
regex compilation and regressions. This is a native dependency baseline; the
corresponding XML/tokenizer Kotlin ports are still required. Adding the full
native target changes provenance only: all 9,777 independent expectation rows
retain their previous case hash.

Skylighting.Regex.RE's Show/Read, JSON/Binary/Data instances, full syntax types,
XML parsing/loading/default maps, keyword resolution, tokenizer, formatters and
their remaining original tests are still pending. Complete WriterOptions/defaults,
remaining Pandoc formats/application/Lua/CLI and all calibre MOBI/additional formats
remain part of the original objective; this milestone does not finish the port.

All 394,837 Kotlin tests pass locally with no failures, errors or skips across
58 suites. All 61 source/fixture checks verify; all 234 installed-CLI comparisons
pass. The final regex test class also passes all 9,778 cases after its test-only
warning cleanup. The complete Pandoc/calibre port remains unfinished.

## Skylighting syntax model, keyword resolution, map lookup and RE metadata

`highlighting/Syntax.kt` represents the complete original syntax-description
records: KeywordAttr, generic WordSet, all 18 Matcher constructors, all eleven
Rule fields, ContextSwitch, ListItem, all ten Syntax fields and all nine Context
fields. ContextName preserves tuple order. Collections are immutable snapshots;
map keys use Unicode scalar order. Eq/Ord retain the original constructor and
field order, including Left-before-Right unresolved/resolved keywords and
case-sensitive-before-case-insensitive word sets. Column values retain the
original 64-bit Int range. Structural comparison, hashing and Show use explicit
stacks; 10,000 nested rules remain usable.

WordSet retains a generic Ord/FoldCase dictionary rather than narrowing the
original generic type to Text. Its Text dictionary uses the existing pinned full
Unicode case folding. The raw CaseInsensitiveWords constructor does not fold
stored words; makeWordSet folds inserted words and membership folds the query.
Show retains the original specialized Char-list behavior: delimiter sets render
as `fromList "..."`, not lists of character literals. All records, constructors,
parentheses, negative Ints and escaped byte/character strings compare exactly
against original Show.

`Syntaxes.kt` ports addSyntaxDefinition, resolveKeywords, missingIncludes and
all five public Core lookup functions. Keyword resolution processes only each
context's top-level rules, leaves unknown local lists unresolved, follows global
cross-syntax list includes and drops missing included lists. An empty included
syntax name is not implicitly replaced with the current syntax. Existing resolved
word sets and child rules remain unchanged. Recursive list includes are not
silently truncated or discarded. missingIncludes preserves first occurrence order
and checks syntax names only, without descending into child rules or checking
context existence.

Lookup retains exact-key precedence, key-ordered case-insensitive fallback using
Text.toLower (not case folding), short-name/extension precedence and the exact
lowercase csharp/fortran aliases. Filename globs are case sensitive and understand
only `*`; question marks, brackets and slashes retain original behavior. A dynamic
program computes the same finite Boolean result without the original exponential
backtracking over repeated stars. SyntaxMap remains a map of full Syntax values;
a production default map is not substituted with test snapshots or placeholders.

`highlighting/regex/RE.kt` ports the metadata smart constructor and its Eq, Ord,
Show and JSON instances. Both regex compilation and capturing-group extraction
are cached lazily and excluded from metadata equality/order/serialization. Input
and returned byte arrays are isolated from mutation. JSON retains required
reString/reCaseSensitive fields, default-false missing/null reMinimal, strict
standard Base64 and Aeson's error paths. The strict decoder preserves the original
padding-before-invalid-character error priority, UTF-8 byte offsets and canonical
padding-bit checks; the host decoder's differing error behavior is not used.

`RERead.kt` ports the original hand-written Read record order together with the
needed GHC lexical paths: nested parentheses, Bool identifiers, quoted Strings or
Char lists, numeric/octal/hex/control/named escapes, string gaps and Char8 byte
truncation. Trailing input and malformed records are rejected. `REBinary.kt` ports
Binary encoding and decodeOrFail results, including remaining bytes, failure
positions, Bool validation and the original empty result for nonpositive
ByteString lengths. Decoding reconstructs the lazy RE cache. The general Data
traversal instance remains pending, as do syntax-model Read/Binary/Data instances.

The separate `tools/haskell-syntax-reference` project compiles unchanged original
Skylighting modules and a frozen dependency closure. It does not alter the earlier
regex oracle or expectations. All 195 original XML files are parsed natively into
complete typed models; Kotlin reconstructs those independent models for this
milestone, compares every Show field and resolves their keyword lists against the
full map. These checks are not evidence of a Kotlin XML parser or tokenizer.
The full native map additionally supplies 2,004 name/short-name/extension/filename
queries. Synthetic maps exercise alias keys, duplicate names, Unicode key order,
missing and cross-syntax lists, unresolved/previously-resolved keywords, child-rule
scope and missing syntax includes. Each matcher constructor and record field is
observed through independent comparisons and source-derived mutations.

The corpus contains 15,132 native observations: 195 complete original XML models,
380 KeywordAttrs, 311 switches, 340 list items, 380 word sets, 420 RE comparisons,
2,046 matchers, 740 rules, 660 contexts, 700 syntaxes, 800 keyword membership cases,
six generic integer word sets, 64 complete map operations, 2,633 each of fromJSON
and JSON decoding, 1,051 Read cases, 300 Binary encodings, 1,236 Binary decodings
and 237 cached compilation/matching cases. Two additional Kotlin tests verify
ownership, immutable maps/lists/sets and deep rule behavior. No expectations are
generated by Kotlin. All 335 retained source/input/adapter/license/provenance
hashes and the complete XML inventory are checked in CI. Case hash:
`4deab00de7f94ce77c09e4431f2a9de9e24754459887913795de6ef85a142c1b`.

The complete original base64-bytestring 1.2.1.0 release is retained unchanged,
with archive provenance and license. Only strict standard Base64 decoding is
ported here; its other APIs and original tests remain dependency work. The relevant
unchanged Binary 0.8.9.3 and ByteString 0.12.2.0 sources and licenses are retained
separately for RE's instances. These are reference sources, never runtime
conversion dependencies. GHC's existing retained Read/Lex sources are included in
the new fixture manifest without changing previous manifests.

Reproduce native captures with `cabal build syntax-reference
--project-dir=tools/haskell-syntax-reference --builddir=/tmp/kotdoc-syntax-build`,
then pass the resulting syntax-reference executable to
`python3 tools/import-syntax-fixtures.py --oracle PATH`. CI needs only
`python3 tools/import-syntax-fixtures.py --check-source` and the Kotlin tests.

All 409,971 Kotlin tests pass locally with no failures, errors or skips across
59 suites. All 62 source/fixture checks and all 234 installed-CLI comparisons
pass. The repository remains public. XML parsing/loading/defaultSyntaxMap,
tokenization, remaining formatters and corresponding original Kotlin test ports
are unfinished; full WriterOptions, remaining Pandoc infrastructure/formats,
application/Lua/CLI and all calibre MOBI/additional formats/tests remain part of
the unchanged objective. This milestone does not complete the full port.

## Skylighting XML parser/loader and default Text.XML document parsing

`SyntaxParser.kt` ports Skylighting.Parser's text and file parsing into the full
Syntax/Context/Rule model. It retains all 18 matchers, raw parent-attribute
inheritance, source-order child validation, first-context starting state,
last-wins map construction, keyword defaults/overrides and delimiter changes.
Decimal columns consume their digit prefix and wrap as the original 64-bit Int;
leading regex `^` sets column zero and removes exactly one caret. LineContinue
uses the first literal character or backslash, separately from the other matchers'
GHC Char Read behavior. Unknown item styles fall back to NormalTok, including
`dsOther`; the original spelling for OtherTok is `dsOthers`.

IncludeRules preserves the original deferred failure in its context pair. An
invalid pair raises the source runtime error when its fields are observed, so a
later parent-rule validation failure can still win without forcing that pair.
The existing syntax model's Eq/Ord/Show tests remain unchanged and pass. Parsing
does not eagerly compile regexes or silently resolve keyword lists.

`SyntaxLoader.kt` ports the single-file wrapper and strict/valid directory folds.
It filters the exact lowercase `.xml` extension and follows the original
non-directory existence check. Enumeration order is retained; duplicate syntax
names are overwritten in that order. Keyword resolution happens only after the
complete map is assembled. Strict loading stops at the first typed parse failure;
valid loading collects errors and continues. IO, invalid UTF-8 and deferred source
runtime errors are distinct from typed parse failures. Tests compare directory
membership with real filesystem enumeration, then replay the native order for
order-dependent folds, rather than assuming macOS and Linux enumerate alike.

The new XML document API is separate from XML.Light; FB2's existing parser is
unchanged. It ports default Text.XML.parseText document construction and the
required Stream.Parse tokenizer/entity behavior. Documents retain prologue and
epilogue comments/instructions, external DOCTYPE identifiers, qualified names,
attributes, child elements and merged text/CDATA. Name equality/order ignores
prefixes and compares namespace/local name; duplicate equal attributes keep the
last value and last spelling of the key. Unknown entities are collected only
after structural validation succeeds. Internal entity expansion preserves the
original first-declaration precedence, cycles, namespace scope and 8,192-byte
serialized-token limit, including escaping and CDATA markup. Explicit stacks
handle deep documents, rules and finite entity chains; adjacent text accumulates
linearly. Returned XML collections own immutable snapshots.

Lexer alternatives reproduce Attoparsec backtracking and labelled failures,
including malformed attributes, DTD directives, incomplete comments, namespace
prefixes and character references. Error ranges count Unicode characters, not
UTF-16 code units. Original Text chunk boundaries matter after backtracking:
conduit-extra's last-fed-block position behavior is preserved. String input
models Lazy.Text.pack's UTF-8 chunk allocation; explicit chunk input is also
available. The original U+FEEF BOM typo is retained, including its empty-input
failure. Ordinary XML text normalizes CR/CRLF; CDATA and instruction text retain
raw characters. Syntax file input separately drops the real UTF-8 BOM and all CR
bytes before strict UTF-8 decoding. ByteString's 32,752-byte input boundaries,
carried codepoints, invalid-chunk forcing and deferred incomplete-tail exception
preserve the observed error precedence.

The isolated `tools/haskell-syntax-parser-reference` oracle compiles unchanged
Skylighting core modules against a frozen original dependency closure. Native
observations include 870 syntax parses, 3,893 document parses, 1,016 byte-input
parses, 17 Windows filename cases, 275 document chunk cases, 72 syntax chunk cases,
20 single-file loads and five directory loads: 6,168 cases total. All 195 original
syntax XML files are tested through document, syntax-text and syntax-file paths.
The 232 XML-bearing literals extracted from the original xml-conduit unit source
are additional document probes; this does not claim to port its cursor, renderer
or streaming-combinator assertions. The original Skylighting loop syntax input
is retained independently. Synthetic cases cover every matcher, flags, token
styles, namespaces, entities, truncations, malformed DTDs, lazy failures and
UTF-8/chunk boundaries. Kotlin checks complete document observations and exact
full Syntax Show values; expectations are generated only by the original oracle.

Three further Kotlin tests translate default resolved-XML examples and verify
10,000 nested rules, 10,000 finite entity includes, large adjacent content,
filesystem APIs, ownership and immutability. Complete unchanged xml-conduit
1.10.1.0 and xml-types 0.3.8 releases are retained with original tests/licenses.
Selected unchanged attoparsec 0.14.4, conduit-extra 1.3.8, text 2.1.3 and bytestring
0.12.2.0 sources retain archive provenance and licenses. The native GHC dependency
closure uses its bundled text 2.1.2; the relevant pack/chunk boundaries are also
verified against that release and original native observations. These sources
are test/reference dependencies, never production conversion executables.
All 375 source/input/adapter/license/provenance hashes are checked in CI. Case
hash: `874cc640e4d7eeb5d8aa61aabcf2dddcf4c8187a0d384d483797b58a9a068491`.

Reproduce with `cabal build syntax-parser-reference
--project-dir=tools/haskell-syntax-parser-reference
--builddir=/tmp/kotdoc-syntax-parser-build`, then
`python3 tools/import-syntax-parser-fixtures.py --oracle PATH`. CI runs
`python3 tools/import-syntax-parser-fixtures.py --check-source` without Haskell.

The generated defaultSyntaxMap is still pending: upstream's baked Binary syntax
modules must be compared directly, not replaced with an assumed equivalent
reparse of the XML inventory. Tokenization, remaining formatters, syntax-model
Read/Binary/Data and the remaining XML model instances/custom settings,
rendering/cursors/streaming combinators and corresponding original tests remain
unfinished. Full WriterOptions, remaining Pandoc infrastructure/formats,
application/Lua/CLI and all calibre MOBI/additional formats/tests remain in the
unchanged objective. This milestone does not complete the full port.

Local validation: all 416,142 Kotlin tests pass across 60 suites, with zero
failures, errors or skips. All 63 source/fixture checks pass. The retained text
Read, Internal.Encoding, Lazy.Encoding and Lazy.Fusion sources are byte-identical
to the native closure's text 2.1.2 files; Internal.Lazy differs only in removal
of its redundant Typeable deriving/import/extension.

## Skylighting syntax Binary instances and original baked default map

`SyntaxBinary.kt` ports Binary encoding and decodeOrFail for ContextName,
KeywordAttr, generic WordSet, all 18 Matcher constructors, ContextSwitch,
ListItem, Rule, Context, Syntax, SyntaxMap and TokenType. The common interpreter
uses explicit work stacks for recursive products/lists; it preserves constructor
order, single-byte sum tags, record field order, 64-bit signed big-endian Ints,
list/map/set lengths and unconsumed input/error offsets. Either and Maybe treat
any nonzero tag as their second branch, while derived sums validate the tag.
Bool retains its separate validation message. Nonpositive ByteString/Text lengths
produce empty content; a negative ordinary list length continues reading and
fails at the same primitive boundary rather than silently producing an empty list.
RE values reconstruct their existing lazy compilation/capture caches.

Binary Char retains the original unchecked UTF-8 bit assembly and its sole final
upper-codepoint check. It does not use a host UTF-8 decoder that would reject
additional sequences. Binary Text instead checks strict UTF-8 and reproduces
Data.Text.Encoding's complete message, including the last carried byte for an
incomplete tail. Haskell String is represented in the codec by a list of Haskell
Char codepoints. Syntax retains exact filename/extension character sequences
internally, so separate surrogate characters survive Show, comparison, repeated
encoding and filename-glob matching. They are not conflated with a single astral
character by Java's UTF-16 representation. The shared Haskell quote renderer now
also accepts exact codepoint arrays; existing String output is unchanged.

`BinaryAscCollections.kt` retains Binary's unchecked fromDistinctAscList boundary
instead of normalizing corrupted sets/maps. The original containers 0.7 stack
builder, link and weight-balancing behavior determine member/key lookup. Duplicate
or out-of-order elements retain their original size, iteration order, Show and
re-encoded bytes. Equality compares the original element/entry lists, including
otherwise unreachable duplicate-key values. Generic WordSet decoding preserves
its raw case-sensitive/case-insensitive constructor without folding stored words.
Its element Binary codec and Ord/FoldCase dictionary remain generic; integer word
sets are tested separately from Text. This ports the needed construction,
iteration, lookup/member and equality boundary, not the full containers library
or all operations on invalid internal trees.

The complete original skylighting 0.15 release is retained in
`vendor/skylighting-syntax-0.15`, including all 195 generated Syntax modules,
aggregate Syntax.hs, CLI source, changelog, license and archive provenance.
`vendor/skylighting-core-0.15/bin/extract.hs` is the authoritative generator: it
loads/resolves XML definitions, sorts modules by short name, bakes each complete
Syntax using Binary, then constructs the map with full syntax-name keys. The
module data is already keyword-resolved. No placeholder map or assumed XML
substitute is used in production.

The native oracle reads each generated Haskell ByteString literal using the
original Read/Char8 boundary, decodes it with original Binary, and verifies that
original re-encoding reproduces its exact source bytes. Its independently captured
whole-map Binary stream is 7,468,305 bytes, SHA-256:
`4f3c50e18dca60b5b5b48b581dcc48e335418a64e3677d97be32e25e518ab012`.
All 195 full typed models also agree with the earlier original XML loader's
post-resolveKeywords models; this is now verified evidence rather than an assumed
generation invariant.

`DefaultSyntaxes.kt` packages the exact original per-module bytes and full-name
index under `highlighting/default-syntax-map/`. Each compressed definition is
independently decoded by Kotlin only on first access and cached immutably.
`LazySyntaxMap.kt` preserves ordered key access without decoding unrelated values;
case-insensitive name lookup and replacing a definition retain this laziness.
The original lazy map is exposed as `Syntaxes.defaultSyntaxMap`. Encoding the
complete production map exactly reproduces the independently captured original
whole-map stream. Production needs no Haskell/Pandoc/calibre executable or XML
parser to initialize these bundled definitions.

The isolated frozen `tools/haskell-syntax-binary-reference` project compiles
unchanged core/regex modules and observes original Binary instances. Its corpus
contains 77,801 independent observations: 1,387 encodings, 75,396 decodings,
538 set/map equalities, 135 each of malformed-set membership and map lookup,
ten word memberships, four exact-character filename matches, all 195 baked
models and the complete default map. Every two-byte Char input is covered, along
with individual constructor tags, truncations, tails, negative/large lengths,
invalid UTF-8, raw duplicate/unsorted collections, recursive records and all token
constructors. Complete values, Show, re-encoded bytes, remaining bytes and failure
positions are compared directly; expectations never come from Kotlin.

One additional Kotlin test verifies 10,000 nested rules, input/result ownership,
immutable maps, lazy initialization counts and cached RE compilation. A fresh map
has 195 keys and zero decoded definitions; exact and case-insensitive ABC lookup
and replacing that definition leave the count at one. Native fixtures check all
195 original definitions independently of these ownership/laziness assertions.

The complete unchanged binary 0.8.9.3 release, including its original tests, is
retained separately from the earlier RE-only source snapshot. Selected unchanged
containers 0.7 and text 2.1.2 sources retain original licenses/archive hashes.
CI checks all 739 source/input/adapter/license/provenance/production-resource
hashes, every per-module resource against its original captured bytes and the
complete 195-module inventory. Case hash:
`a28b1d2ed908e452e7fa367e60355694a0cd36c30189f1cdf81e64515cb9ecdd`.

Reproduce with `cabal build syntax-binary-reference
--project-dir=tools/haskell-syntax-binary-reference
--builddir=/tmp/kotdoc-syntax-binary-build`, then
`python3 tools/import-syntax-binary-fixtures.py --oracle PATH`. CI needs only
`python3 tools/import-syntax-binary-fixtures.py --check-source` and Kotlin tests.

Syntax-model Read/Data, tokenization, remaining formatting/style Binary instances,
formatters and corresponding original test ports remain unfinished. General
Binary streaming/file APIs, unrelated instances and remaining dependency tests
are not claimed complete by this finite syntax-codec boundary. Full WriterOptions,
remaining Pandoc infrastructure/readers/writers/application/Lua/CLI and all calibre
MOBI/additional formats/tests remain in the unchanged objective. This milestone
does not complete the full port.

Local validation: all 493,944 Kotlin tests pass across 61 suites, with zero
failures, errors or skips. All 64 source/fixture checks pass. The existing XML
parser/model and all original Native output assertions also pass after the
exact-character and lazy-map integration changes.

## Skylighting tokenizer, physical lines and context captures

`Tokenizer.kt` translates the complete observable `Skylighting.Tokenizer.tokenize`
state machine. All 18 matcher constructors use the already ported KDE compiler,
matcher, pinned GHC Unicode tables and full case folding. The supplied starting
Syntax remains independent of the configuration map; later pushes and includes
use exact map/context lookup. Keyword sets must already be resolved, as upstream
requires. Production code does not invoke Haskell or another converter.

Alternative rule failure restores the whole prior state. Lookahead restores only
input/endline/previous-character/column/loop-counter fields, while retaining child
context switches, captures and continuation effects. Child text retains its
parent's attribute; successful children can clear parent regex captures. Included
rules execute in the actual current context, clear their current captures and
optionally replace only NormalTok. An included lookahead returning Nothing can
still consume a child's input; the source's dropped-text quirk is explicitly
observed rather than repaired.

Stored context captures persist across physical lines and are restored when a
context is popped. Dynamic characters, strings and regexes read those stored
captures, including empty and Unicode captures. Regex substitution preserves the
source traversal boundary: Lazy and Possessive wrappers remain opaque. Dynamic
regex groups are recomputed, while static regexes reuse their cached groups.
A work stack handles finite nested children/includes and substitution without
recursive JVM calls.

Physical lines split only at LF, omit the trailing empty segment and preserve CR.
Column starts again at zero on each line. First-nonspace position uses Char8 byte
classification, while ordinary column advancement follows requested character
counts. Empty lines apply only successive lineEmptyContext switches; nonempty
lines apply successive lineEndContext switches unless continued. Context-list
comparison stops unchanged switches, ignoring captures. The original 1024 limits
and exact trace messages guard line transitions and zero-progress lookahead or
fallthrough cycles. Recursive self-IncludeRules may diverge in the original and
are not given an invented error/termination rule.

String input follows Data.Text packing's replacement of lone surrogate chars;
LineContinue uses utf8-string's distinct verbatim Char encoding. UTF-8 decoding
retains noncharacters in token text while the permissive matching decoder may
observe them as replacement characters. ASCII numeric/C-escape/identifier rules,
custom delimiters, incomplete exponent backtracking, case-folded word boundaries,
zero-length match failure and token normalization preserve source behavior.
Trace output is lazy, so it does not force malformed IncludeRules metadata when
tracing is disabled. TokenizerConfig's derived Show is also independently checked.

The frozen `tools/haskell-tokenizer-reference` project compiles unchanged original
Skylighting modules. It loads the original 195 XML definitions and captures full
tokens, typed errors/runtime exceptions and stderr debugging traces. Native
JSON/Text cannot accept lone surrogate escapes, so these input cases instead pass
integer Char values through original Text.pack. This observes native replacement
semantics without relying on the JVM's encoding defaults. The adapter has no role
in production and the corpus never obtains expected outputs from Kotlin.

`core/src/test/resources/highlighting/tokenizer.jsonl.gz` contains 27,881 original
observations: all 32 original expected token files, all 21 explicit tokenizer
regressions, 195 x 32 original mixed-prefix samples, 195 x 100 seeded fuzz inputs,
and 2,088 synthetic state/matcher/trace/error/Show cases. The exact source property
that input lines equal concatenated token text is asserted for every mixed/fuzz
case in both the import verifier and Kotlin tests. Complete classifications are
also compared to native output. Original golden files remain byte-for-byte in
vendor; their Haskell token values are read independently, while pretty-show's
text layout is not claimed ported by these tokenizer assertions. QuickCheck's
framework/generator/shrinker implementation is likewise not claimed ported by the
seeded finite property corpus.

Two additional tests verify 10,000 nested child and include chains, immutable
configuration/result ownership and normalization's empty-different-attribute
boundary. All 27,883 focused Kotlin tests pass. CI validates all 304 source,
adapter, frozen dependency, license and upstream fixture hashes and the complete
case digest: `3aef5767663a9d66d0a1c1a6a4dd77232b2c38d85d5da5444662b368ca63beab`.

Reproduce with `cabal build tokenizer-reference
--project-dir=tools/haskell-tokenizer-reference
--builddir=/tmp/kotdoc-tokenizer-build`, then
`python3 tools/import-tokenizer-fixtures.py --oracle PATH`. CI uses
`python3 tools/import-tokenizer-fixtures.py --check-source` and Kotlin tests.

Syntax-model Read/Data, remaining style/formatting Binary instances, highlighting
formatters, full WriterOptions and remaining Pandoc readers/writers/application/
Lua/CLI tests remain unfinished. Calibre MOBI and additional formats/tests remain
in the unchanged full objective. This tokenizer milestone does not complete the
full port.

Local validation: all 521,827 Kotlin tests pass across 62 suites, with zero
failures, errors or skips. All 65 source/fixture checks pass.
All 234 installed CLI comparisons pass: 14 FB2, 66 media, 69 extension and 85
math cases. Rebuilding the portable native adapter reproduces the identical
complete case digest.

## Skylighting formatters, FormatOptions and color/style Binary

The complete public formatter functions from all five upstream packages are now
implemented in Kotlin. These versions follow the pinned Pandoc configuration:
`skylighting-format-ansi-0.1`, `skylighting-format-latex-0.1`,
`skylighting-format-context-0.1.0.2`, `skylighting-format-typst-0.1` and
`skylighting-format-blaze-html-0.1.2.2`. Pandoc's stack.yaml uses LTS 24.51 with
an explicit HTML override, and cabal.project requires HTML >=0.1.2.2 and
ConTeXt >=0.1.0.2. The exact nine formatter/rendering dependency archives and
all 88 original files, including licenses and published dependency tests, are
retained verbatim with archive hashes. The five formatter releases contain no
standalone test suites; output expectations come from their unchanged functions,
not newly invented Kotlin snapshots.

`TeXFormats.kt` ports all inline/block functions and style macro generators for
LaTeX, ConTeXt and Typst. It preserves token naming, whitespace-only NormalTok,
escaping, line joins and trailing newlines, sorted macros, wrapper composition,
color fractions and default-color fallback. Source quirks remain observable:
LaTeX numbering keeps its repeated commas; ConTeXt shade hex channels are unpadded
and repeated spaces are grouped before adjacent escape regions are joined;
Typst's per-token background setting currently has no effect. Ignored options
remain ignored by the same formatter boundaries.

`HtmlFormat.kt` reproduces HTML/HTML4 rendered fragments and CSS. Token classes,
titles, duplicate/empty class strings, inline class deduplication, conditional
counter-reset styles, prefix/line IDs, anchor attribute order and HTML4 versus
HTML5 accessibility attributes match the original. Its text/attribute escaping
uses the exact five blaze-markup entities; controls and Unicode otherwise remain
in their original text. This ports the rendered fragment boundary, not the whole
Blaze Markup AST, its combinators, every renderer or its entire property suite.
Those broader dependency APIs are not claimed complete by the formatter corpus.

`AnsiFormat.kt` ports complete SGR output for 16-color, 256-color and true-color
modes. The original Manhattan nearest-color search and stable first-minimum tie
choice are preserved. In 256-color mode grayscale entries precede the cube and
system colors; this ordering changes tie results and cannot be replaced with
numeric palette order. Foreground/background/default/line-number inheritance,
reset placement, separate palette SGRs and bold/italic/underline resets match
ansi-terminal's unchanged Codes module. All 256 values of each individual RGB
axis check its colour Float -> sRGB24 roundtrip. The formatter's unused private
Xterm256ColorCode Data/Read/Enum/Binary instances and unrelated terminal controls
are not claimed ported by its public formatANSI boundary.

HTML and ANSI use the source's derived LineNo Enum behavior: enumeration stops at
Int64 maxBound, so later input lines are omitted at that boundary. Counter-reset
subtraction still wraps at minBound. LaTeX, ConTeXt and Typst retain their own
independent line behavior; no shared wrapping enumeration is substituted.

`FormatOptions.kt` supplies all eight original fields and defaults with immutable
lists, derived field-order equality/ordering and exact Show. `HighlightBinary.kt`
ports public Binary instances for Color, TokenStyle, Style, ANSIColorLevel and
FormatOptions through the previously verified explicit-stack Binary engine.
Complete values/Show/re-encoded bytes, errors, consumed positions and remaining
input agree with original decodeOrFail. Maybe accepts any nonzero Just tag;
Bool and enum tags retain their own distinct validation. Invalid unordered or
duplicate token-style maps preserve original fromDistinctAscList entries and
lookup trees, rather than being normalized by HighlightStyle's public constructor.
Canonical JSON construction and the earlier style corpus remain unchanged.

The isolated frozen `tools/haskell-formatter-reference` project compiles all five
unchanged formatter modules against original core 0.15, blaze-html 0.9.2.0,
blaze-markup 0.8.3.0 and ansi-terminal 1.1.5 / types 1.1.3. Production Kotlin uses
none of these programs/libraries. The corpus has 27,030 observations: eight
built-in complete style models/macros, 1,602 comparisons containing ten rendered
outputs each (including all 32 original source files with eight themes), 1,176
four-output style conversions, 9,680 focused ANSI outputs, 99 FormatOptions
observations, 98 option comparisons, 359 Binary encodings and 14,008 decodings.
These include all token/option constructors, escaping and Unicode, empty source
lines/tokens, all boolean style combinations, a complete 16x16x16 RGB grid for
both approximate palettes, exact palette entries, seeded mixed styles, Int64
limits, all individual constructor-byte values, truncations, tails, malformed
lists/text/Maybe/Bool/enum tags and invalid token-style maps.

One additional Kotlin check verifies immutable option lists and ownership of
encoded input/decoded map values. All 27,031 focused tests pass. CI checks all
397 source/input/adapter/license hashes and the complete case digest:
`e66353e5ac33518718a8fb2dbdd08aa792131f9f69f59af33d9c4174f71487dc`.
The core JAR now carries every retained upstream notice under META-INF/LICENSES;
CLI distributions already include the license directory, which now also contains
verbatim formatter, ConTeXt, Blaze and ANSI notices.

Reproduce using `cabal build formatter-reference
--project-dir=tools/haskell-formatter-reference
--builddir=/tmp/kotdoc-formatter-build`, then
`python3 tools/import-formatter-fixtures.py --oracle PATH`. CI runs
`python3 tools/import-formatter-fixtures.py --check-source` and Kotlin tests.

The Pandoc Highlighting wrapper and writer integration, full WriterOptions,
remaining derived model Read/Data behavior, Pandoc readers/writers/application/
Lua/CLI and all calibre MOBI/additional formats/tests remain in the unchanged
objective. Passing these formatter checks does not establish completion of the
full port.

Local validation: all 548,858 Kotlin tests pass across 63 suites, with zero
failures, errors or skips. All 66 source/fixture checks pass. Initial full runs
exhausted the long-lived Gradle daemon's default 512 MiB heap. Gradle now has
1 GiB, the test worker has 2 GiB headroom, and the new formatter factory parses
large expected JSON trees during execution rather than retaining duplicate trees
in every callback. The complete original failing command now passes with every
observation retained; no test was removed or skipped.
All 234 installed CLI comparisons pass (14 FB2, 66 media, 69 extension,
85 math). All 397 source/input/adapter/license hashes match staged Git blobs or
the pinned Pandoc submodule. Original CRLF is preserved explicitly by attributes.
The core JAR contains all seven new formatter/options/Binary classes and all 35
verbatim retained notices plus the project GPL license.

## Pandoc highlighting wrapper and complete writer-option record

`Highlighting.kt` translates the shared pure layer from Text.Pandoc.Highlighting.
The first class recognized by the syntax lookup wins; numbering and line-anchor
classes use the original exact aliases. Only the first startFrom attribute is
read, using original Read Int behavior, including parentheses, base prefixes,
Unicode spaces and Int64 overflow. A missing/invalid value becomes one. Unnumbered
code with no matching syntax returns Left empty text without calling the
formatter; numbered code with no syntax passes physical lines as single NormalTok
tokens, including empty tokens on empty lines. Matched syntax uses the already
ported tokenizer, propagates its errors and passes lowercased shortname code
classes, unchanged container classes and identifier-derived line prefix.

The formatter is generic: success invokes it once, refusal/error invokes it zero
times, and its own exceptions propagate unchanged. Config/result classes and
physical lines remain immutable. The wrapper exposes original ordered language
names, extension queries, built-in style order/defaults and the already validated
style lookup. It does not implement missing writers merely by supplying this
shared layer.

`ListingsLanguages.kt` retains all 103 literal source pairs. Forward lookup uses
Unicode lowercasing; reverse lookup is case-sensitive. Duplicate reverse keys
retain the original last input pair, such as C -> objectivec, C++ -> c++,
Assembler -> gnuassembler, Lisp -> commonlisp and TeX -> latex. The import verifier
checks the full production table against pinned original source.

The frozen tools/haskell-highlighting-reference compiles exact unchanged pure
function bodies and the original Shared.safeRead/safeStrRead definitions with
unchanged Skylighting core. Its corpus has 7,637 observations: all 103 listings
pairs in case/space variants, all 195 bundled syntax names and aliases, all 32
original tokenized code samples through the wrapper, callback options/full token
streams, class/attribute ordering, numbering fallbacks, Unicode/CR/LF handling,
synthetic map ordering/name differences and typed tokenizer errors. The 4,969
native safe-int observations include Int64 limits/overflow, hexadecimal/octal/
binary forms, parentheses, lexical failures and original GHC spaces. Expected
values never come from Kotlin. Case digest:
`ed30e7086ca70150b2ea1c80778301d015227e620ac78af6c6a85393f50383ce`.
All 307 source/adapter/input/license/table hashes and unchanged extracted bodies
are checked in CI.

`WriterOptions.kt` supplies all 39 fields of the original library record,
including complete compiled Template<String>, Context<String>, extension sets,
HighlightMethod and SyntaxMap values. Numeric options retain Int64 width, list
fields/maps snapshot caller ownership, and copy updates retain every unspecified
field. The original lazy per-definition default syntax map remains shared. These
are library defaults, including MathML, empty extensions, 72 columns, six zero
number offsets and EPUB title page true; application defaults remain a separate
boundary.

Native and FB2 have new overloads accepting this record. Native decides complete
Pandoc versus block-fragment output from template presence, ignores template
content as upstream does, and preserves Float ribbon calculation with Int64
columns. FB2 consumes the record's math method through its existing effectful
writer. Existing smaller APIs remain available. The installed CLI now constructs
one writer-option record for its supported Native/FB2 paths; unsupported CLI
flags/formats are not implied implemented by the larger record.

`tools/writer-options-reference.lua` observes the actual Pandoc 3.12.1 library
through original Lua marshaling and writer calls. There is no upstream ToJSON
WriterOptions instance, so its structured output is explicitly a test protocol,
not a new production codec. The 915 cases cover all 38 exposed option properties,
all 39 original source declarations (the syntax-map Lua property is disabled),
immutable Kotlin/baked-map assertions, record updates, enum/boolean/string/list/
nullable values, templates and contexts. Full outputs include all 266 accepted
original Native documents in two template-presence modes, 194 signed Int64 width
cases and all six math modes through FB2. Integer inputs travel as decimal
strings into Lua tonumber, preserving integer precision. Context documents and
template-application results are observed through original Doc tostring/render,
not JSON-coerced opaque userdata. Lua extension values use their original list
interface; map-shaped inputs remain ignored as upstream does. Case digest:
`6f75493b29f4f694bf3b7dd586644b7f136347ada390f4612d71b4d2a3b4d29b`.

One additional check in each new suite covers generic formatter invocation,
exception propagation, immutable fallback token streams and complete typed
writer-option payload/record-update ownership. All 8,554 focused Kotlin tests
pass. The existing Native, FB2 and earlier option/style tests remain in the
full regression gate.

WriterOptions' derived Haskell Show/Data/Generic behavior, remaining syntax/model
Read/Data, complete writer/application/Lua integration and missing Pandoc formats
are still unfinished. The typed record and observed getters/writers do not claim
those remaining contracts complete. All calibre MOBI and additional formats/tests
remain in the original objective; this stage does not complete the full port.

Local validation: all 557,412 Kotlin tests pass across 65 suites with zero
failures, errors or skips. All 68 source/fixture checks pass.
All 234 installed CLI comparisons pass (14 FB2, 66 media, 69 extension and
85 math). All 316 source/input/adapter/table hashes in the two new corpora match
staged Git blobs or the pinned Pandoc submodule. The identical highlighting case
digest is reproduced after removing an extra extraction EOF separator.

## Writer and nested model Show contracts

WriterOptions.haskellShow now renders all 39 original record selectors
in declaration order, with derived-record precedence and its original syntax-map
representation. toString delegates to this source display. MathMethod,
HighlightMethod, Extension and Extensions expose source constructor display with
application precedence, original constructor ordering and exact set notation.
These methods do not add a WriterOptions JSON or Read instance absent upstream.

TemplateShow<A> accepts an explicit target showsPrec callback, preserving the
target's own constructor precedence. It covers all eight Template constructors,
Variable, all 14 Pipe constructors, Alignment/Border, Context and all five Val
constructors, all 16 public Doc constructors, Attr/Attributed, Font and all six
StyleReq constructors. It retains cached widths, empty nodes, nested maps,
attribute/link/font distinctions and constructor syntax instead of flattening
documents to rendered text. Text and CharacterString targets use the original
GHC string escaping and distinct Text/Char handling. Iterative tasks avoid JVM
stack exhaustion in nested templates, documents and values.

Source FilePath is a Haskell Char sequence, not Text. The writer record's
referenceDoc and epubFonts therefore now retain immutable CharacterString values;
ordinary JVM paths enter through CharacterStringChars.fromString. This fixes an
existing representation loss: adjacent surrogate Chars remain two Chars rather
than becoming one supplementary scalar. copy and defensive list/array ownership
preserve those sequences unchanged. Text-valued options separately keep original
Data.Text scalar replacement behavior.

The frozen tools/haskell-model-show-reference project compiles unchanged pinned
doctemplates, doclayout, Skylighting and dependencies. Its Pandoc record/enum
models retain verbatim source declarations, deriving clauses and the original
WriterOptions Default body; the importer checks every extraction and all 39
production output selectors against pinned Pandoc. The test adapter supplies an
explicit empty syntax map in ordinary field cases and the original XML-loaded
195-model map in its complete default-map observation. It does not substitute
that adapter map for production defaults, which remain independently covered by
the actual Pandoc writer-options corpus. All expected display comes from native
GHC deriving, never Kotlin or a second hand-written display implementation.

The corpus contains 3,440 observations: 879 complete writer records, 243 math,
294 extension, 45 extension-set, 99 highlighting, 248 font, 252 style, 452 Doc,
63 generic integer Doc, 63 CharacterString Doc, 63 Text option code-point and
63 FilePath code-point displays, 336 pipe, 72 border, 27 variable, 134 template,
98 value and nine context displays. It includes every writer field, all 90
built-in extensions, all eight themes, signed Int64 limits, original constructor
precedence boundaries, escaping/Unicode and every public document/template
constructor. All 22 original template goldens and nine original successful unit
cases contribute native displays of their compiled trees. Every one of the 195
original syntax definitions is also observed inside a complete writer record,
followed by the full default syntax map. The case digest is
`51ecd6483121da65a925441ba1783b2e77ac98aa256b84942b32e1f6d85c28db`.
CI verifies all 467 source/input/adapter/license hashes and the complete corpus.

Two further Kotlin tests prove target callback precedence, 10,000-level nested
models, exact FilePath Char distinction and immutable record-copy ownership.
All 3,442 new tests and all 916 prior writer-option tests pass.

Reproduce with `cabal build model-show-reference
--project-dir=tools/haskell-model-show-reference
--builddir=/tmp/kotdoc-model-show-build`, then
`python3 tools/import-model-show-fixtures.py --oracle PATH`.
CI runs `python3 tools/import-model-show-fixtures.py --check-source` and all
Kotlin comparisons.

WriterOptions Data/Typeable/Generic behavior, other remaining model Read/Data/
Generic/ordering contracts, all remaining Pandoc readers/writers/application/
Lua/CLI and all calibre MOBI/additional formats/tests remain in the full original
objective. Implementing these display contracts does not complete that port.

Local validation: all 560,854 Kotlin tests pass across 66 suites, with zero
failures, errors or skips. All 69 source/fixture checks pass.
All 234 installed CLI comparisons pass (14 FB2, 66 media, 69 extension and
85 math). The core JAR contains the new display classes and all 36 retained
license files. All 467 native source/input hashes match staged Git blobs or
the pinned Pandoc submodule.

## Structural model Eq/Ord contracts

TemplateComparison<A> exposes the original equality/inequality, compare, four
relations and min/max for Template, Doc, Attr/Attributed, Font, StyleReq,
Variable, Pipe, Border and Alignment. Constructor tags and fields retain source
order; cached widths, empty nodes, fonts/links and list order remain structural.
These explicit source contracts are separate from JVM data-class equals and
Java's total-order assumptions. Context and Val have no upstream Eq/Ord instance;
this layer does not invent one.

TargetComparison supplies independent target Eq/Ord operations. Built-in targets
cover Text, Haskell Char sequences, signed Int64 and original IEEE Double
comparisons. GHC's compare-only instances for Template/Doc have different class
defaults from Attr's generated compare/< implementation. With a NaN target,
Doc's compare may be GT in both directions, equality false, both strict relations
true and both inclusive relations false; Attr's relation outcomes differ. The
implementation preserves those outcomes and original min/max input selection,
including NaN payloads and signed zero, instead of deriving every operation from
one forward compare or substituting JVM Double.compareTo.

Iterative work preserves lexicographic field order and stops at the first decisive
comparison. Lists schedule one pair at a time, so an unreachable suffix is never
read merely to allocate comparison work. Seq-backed Attributed equality checks
length first, as containers 0.7 does; list equality retains its distinct element
then tail behavior. Scalar Text comparison and CharacterString.compareTo avoid
full suffix normalization/copying while preserving Unicode scalar versus Char
semantics. A 10,000-level tree comparison does not consume the JVM call stack.

The frozen tools/haskell-model-order-reference builds unchanged original
modules and uses their Eq/Ord methods for all nine observed outputs. Its transport
is a byte-for-byte copy of the independently validated prior native adapter.
Expected results never come from Kotlin. GHC-generated instance definitions were
inspected with -ddump-deriv in a separate build directory; the dependency closure
pins GHC 9.12.2 and containers 0.7. The corpus has 11,637 comparisons: 5,167 Doc,
2,770 Template, 552 Font, 392 StyleReq, 315 Variable, 637 Pipe, 380 Border, nine
Alignment, 369 Attr and 1,046 Attributed observations. Targets comprise 3,539
Text, 4,876 Double, 150 Int64, 1,200 Char-sequence and 1,872 partial-probe cases.

Inputs cover every public constructor, all original successful template golden/
unit compiled trees, signed bounds, links/fonts/empty sequences, Unicode and
adjacent surrogate Chars. Floating inputs/results use raw Word64 bits, including
both zero signs, infinities, subnormals, signed quiet/signaling NaNs and distinct
NaN payloads. Native partial-probe Eq/Ord instances throw for selected left/right
arguments; their outcomes prove short-circuiting and argument order, not merely
agreement for total values. All nine methods, including min/max complete models,
are independently observed for every case. Case digest:
`f47e9d886840c82327e7074732cb83accad2c49889b4593984b33054625354fc`.
CI verifies all 162 source/input/adapter/license hashes and the complete corpus.

The additional Kotlin test proves 10,000-level trees, propagated target errors,
Seq length short-circuiting, Text scalar replacement and an unreachable tail in
a one-million-row list. All 11,638 focused tests pass.

Reproduce with `cabal build model-order-reference
--project-dir=tools/haskell-model-order-reference
--builddir=/tmp/kotdoc-model-order-build`, then
`python3 tools/import-model-order-fixtures.py --oracle PATH`.
CI runs `python3 tools/import-model-order-fixtures.py --check-source` and Kotlin.

Remaining Read/Data/Typeable/Generic model behavior, remaining Pandoc readers/
writers/application/Lua/CLI and all calibre MOBI/additional formats/tests remain
in the full unchanged objective. Passing model comparisons does not complete
that port.

Local validation: all 572,492 Kotlin tests pass across 67 suites, with zero
failures, errors or skips. All 70 source/fixture checks pass. The core JAR
contains both new comparison classes and all 36 retained license files.
All 234 installed CLI comparisons pass (14 FB2, 66 media, 69 extension and
85 math). All 162 native source/input hashes match staged Git blobs.


## Structural Blaze markup and Pandoc wrapping

The ten original MarkupM constructors and eight ChoiceString constructors now
have Kotlin counterparts. Transparent memoized thunks represent source delayed
constructor production. StaticString retains three independent backends: a
String suffix function, UTF-8 bytes and Text. It is not reconstructed from a
single textual field. Raw bytes are defensively owned; String preserves Haskell
Chars, while Text replaces surrogate Chars according to the pinned text library.

Functor/Applicative/Monad operations retain the source Append/Empty structure,
rightmost return values and callbacks that can ignore bottom values. Canonical
mapLazy/applyLazy/flatMapLazy callbacks receive a Lazy argument; ordinary strict
Kotlin conveniences are separate. Attribute composition retains source g.f
application order with a persistent operation tree and iterative traversal.
Composing 10,000 attributes no longer copies a growing operation list.

String, Text, UTF-8 and Pretty renderers preserve distinct escaping/raw-byte
behavior, static suffix callbacks, comments, root attribute propagation, ignored
content attributes and pretty indentation. Only direct External String/Text/
bytes reject the closing-tag sequence; nested wrappers use the source fallback.
String interprets raw bytes as Latin-1 characters; Text uses strict UTF-8 or an
injected/lenient decoder; UTF-8 preserves raw bytes. The blaze-builder escaped
String/Text primitives discard ASCII controls except TAB/LF/CR and also DEL;
raw/static/pre-escaped fragments retain those bytes. Strict text decoding pins
both the message and the byte selected for truncated UTF-8 errors.

Lazy Text/bytes content, comments and attribute values consume sequences on
demand and memoize each chunk, including failures. Kotlin's ordinary lazy
initializer retries after an exception, which would consume past a failing
iterator element; the chunk boundary explicitly retains that failure. Null
checks can stop at a nonempty prefix, and attributes attached to plain content
remain unevaluated. contents/external transformations preserve leaf values.

Pandoc Writers.Blaze is translated into structural DocLayout output. Wrapping
is suppressed under the exact built-in code/pre/style/script/textarea opening
strings; flush applies to the source subset. Custom parents retain their
separate rules. Whitespace groups, pre-escaped values, raw attribute names,
quote structure and right-associated Concat trees match the original. Delayed
layout plans preserve the pinned optimized native evaluation order, including
competing invalid key/value/child bytes, without recursion in the markup walker.

The frozen tools/haskell-blaze-reference builds the untouched original Blaze
Markup/HTML/DocLayout modules and the entire Pandoc Blaze module with only its
module name changed. It calls original renderers, combinators and layoutMarkup;
expected results never come from Kotlin. GHC 9.12.2, text 2.1.2, bytestring
0.12.2.0 and blaze-builder 0.4.4.1 are pinned. The new builder release sources and
BSD notice are preserved byte-for-byte; its native builder is an oracle-only
dependency, never a production conversion dependency.

The 71,478-row corpus observes String, Text, bytes, Pretty, lenient Text, return
values, null, the complete source markup graph, the complete produced Doc graph,
and output at five widths. It includes all 25 original HTML example constructor
trees, five original markup assertions, every byte pair, all 256 low Chars,
surrogate/scalar cases, independent StaticString fields/functions, lazy chunk
failures, 2,000 seeded nested trees and 1,080 competing-error cases. Case digest:
`e27194a0ef79e02e7a4e111f7e14778489c9aa20fed0c3dcdf9b6fdf44369c79`.
The importer verifies all 136 source/input/adapter/license hashes and the corpus.

All ten properties from the original Text.Blaze.Tests are translated, retaining
its cross-backend equality checks and deliberately permissive UTF-8 predicate;
each receives 1,000 deterministic generated trials. The unchanged original
Haskell suite also passes all 15 tests with 1,000 trials per property and replay
seed 727598. Additional Kotlin checks cover defensive byte ownership, discarded
return values/attributes, repeat chunk observations, source composition order,
10,000-level parent trees and 10,000 composed attributes. All 71,491 focused
Kotlin tests pass.

Reproduce: cabal build all --project-dir=tools/haskell-blaze-reference
--builddir=/tmp/kotdoc-blaze-build; run original-markup-tests with
--quickcheck-tests 1000 --quickcheck-replay 727598, then run
python3 tools/import-blaze-fixtures.py --oracle PATH. CI verifies the corpus with
--check-source and runs the Kotlin suite.

Remaining Blaze work includes actual Text Builder and ByteString Builder/IO
buffer interfaces, ToMarkup/ToValue numeric/general conversions, all generated
HTML dialect vocabularies and their remaining tests, and consumer integration.
The complete Pandoc HTML writer is not implemented by this dependency layer.
Remaining Pandoc readers/writers/application/Lua/CLI, model Read/Data/Typeable/
Generic contracts and all calibre MOBI/additional formats/tests remain within
the unchanged full objective. These passing dependency tests do not complete it.


Local validation: all 643,983 Kotlin tests pass across 69 suites, with zero
failures, errors or skips. All 71 source/fixture checks pass. The standalone
core JAR contains the new markup/layout classes and all 37 retained license
files. All 136 original source/input hashes match staged Git blobs, including
the pinned Pandoc submodule; the repository remains public.
All 234 installed CLI comparisons pass (14 FB2, 66 media, 69 extension and
85 math).


## Annotated tables for writer output

Writers.AnnotatedTable's structural model and conversion algorithms now have
Kotlin implementations. toTable clips row spans at each section boundary,
clamps row-header width to the table width, fixes column spans and overlap,
pads missing cells and separates each body row's stub from its remaining cells.
Each placed cell retains a nonempty sequence of the exact column specifications
it covers and its starting column number. Occupied columns advance the cursor
without consuming a cell; stub and body placement share the remaining stream.
Rows are numbered continuously across the head, every body's intermediate head
and body rows, and the foot, including rows with no newly placed cells.

The converter uses iterative placement and buffered lookahead. Source pattern
guards examine a cell even when the remaining column section is empty; that
cell must remain available for body placement. Row-span clipping is deferred
until a cell is inspected. Native partial probes prove that an unused first
cell constructor is forced, while the following cell-list tail stays untouched,
including tables with zero columns. This preserves source behavior instead of
eagerly mapping every excess cell in an input row.

fromTable strips annotation data and returns the core Table without performing
normalization. Arbitrary public annotations, inconsistent column specs, signed
row/column numbers and invalid core spans can therefore be preserved exactly.
ColumnSpecs expresses the source NonEmpty requirement and defensively retains
its tail. Walker adapters preserve the original three instance scopes: Cell,
HeaderRow and TableHead. They delegate traversal to the wrapped core Cell, never
visit column specs/numbers, and preserve annotation objects across changes.

The frozen tools/haskell-annotated-table-reference compiles the unchanged
Pandoc 3.12.1 AnnotatedTable module and pinned pandoc-types 1.23.1.2 sources. It
also builds the unchanged Tests.Writers.AnnotatedTable suite, which passes the
original golden and all five properties with the source's 50 trials per property
(replay seed 727599). The Kotlin test reproduces the full typed golden, ports
all five properties with 50 trials each, and checks a 10,000-column table,
column-spec ownership and annotation preservation.

The 1,400-case native corpus contains every one of the 242 Table occurrences
in accepted upstream Native fixtures, the original sample, 136 explicit boundary
cases, 1,000 seeded tables, twelve arbitrary annotation models and nine partial
cell-list probes. It observes complete annotated structures, fromTable output,
independent Builder.tableWith normalization, and each source walker scope with
Inline, Block, [Inline] and [Block] targets. Walk outputs, postorder effect traces
and preorder query results are compared separately, including nested cell notes,
citations, lists and tables. Captions outside the selected scope stay unchanged.
Expected results are never generated by Kotlin. Digest:
`5b339b1cf65b3e72102dc89872066f43a20f1247a60ad7c37a4649b1cf68ebba`.
The importer verifies all 80 source/input/adapter/license hashes and inventories
every original fixture table. The adapter JSON is an explicit observation
protocol, not an invented source JSON instance. Scientific numeric spellings in
the adapter (such as 0 versus 0.0) compare as exact decimal values; the comparison
does not replace the separately tested Pandoc JSON byte contracts.

All 1,407 focused Kotlin tests pass. Reproduce with cabal build all
--project-dir=tools/haskell-annotated-table-reference
--builddir=/tmp/kotdoc-annotated-table-build, run original-annotated-table-tests
--quickcheck-replay 727599, then run python3 tools/import-annotated-table-fixtures.py
--oracle PATH. CI uses --check-source and runs the Kotlin tests.

Annotation model Eq/Ord/Read/Show/Data/Typeable/Generic contracts and explicit
RowNumber/ColNumber derived Num/Enum contracts remain within the full model-port
work. Arbitrary lazy monoidal query/typeclass interfaces remain pending beyond
the finite core-cell adapters and four tested target types. No new Walkable
instance is claimed for Table/BodyRow/TableBody/TableFoot, which the source
module does not declare. The complete HTML writer still requires the remaining
shared/math/XML/URI/translation and stateful pipeline work. Remaining Pandoc
readers/writers/application/Lua/CLI and all calibre MOBI/additional formats/tests
remain part of the unchanged objective; this table dependency does not complete
that port.


Local validation: all 645,390 Kotlin tests pass across 70 suites, with zero
failures, errors or skips. All 72 source/fixture checks pass. The core JAR
contains all public annotated-table classes and all 37 retained license files.
All 80 source/input hashes match staged Git blobs and both submodule pins.
The preceding Blaze commit's exact-SHA CI passed, including all 234 CLI
comparisons; this dependency adds no CLI reader/writer registration. The new
commit's CI will run the complete installed-CLI checks again.

## Pandoc XML/CSS and shared writer HTML utilities

PandocXml ports Text.Pandoc.XML's fourteen exported utilities/registries.
It preserves XML's legal-character filtering and four predefined escapes;
apostrophes remain literal. Individual Haskell Chars, including surrogates,
use the original Text singleton replacement. NCName escaping uses GHC 9.12.2's
Unicode 16 letter predicate, ASCII digits and the exact source combining ranges,
with uppercase U%04X escapes. Non-ASCII numeric output preserves uppercase
hexadecimal, while HTML5 output selects the shortest TagSoup entity name and
the earliest source entry when lengths tie. Missing names use decimal entities.

Commonmark 0.3.1's original lookupEntity is distinct from TagSoup's lookup.
Numeric lookup accepts optional semicolons, arbitrary Integer lengths and the
additional 0x/0X prefix accepted by Data.Text.Read.hexadecimal. Zero, out-of-range
and surrogate values become U+FFFD. Named lookup retains all 2,231 exact names,
including semicolon variants. fromEntities retains upstream missing-semicolon,
whitespace and unknown-reference behavior. Its iterative traversal avoids JVM
recursion without rescanning unknown references that the source keeps intact.
XML tag helpers escape names/values of attributes and encode newlines in values;
tag names themselves stay literal. They retain generic Doc targets, exact nested
Concat/Prefixed structure, raw text widths and the original layout operators.

PandocCss ports all three CSS exports. It preserves the source parser's unusual
accepted prefixes and distinction between consumed failures (discard every rule)
and unconsumed failures (retain parsed rules); there is no invented eof check.
Property/value trimming removes only Shared.isWS's four ASCII characters,
whereas inter-rule spaces use GHC's Unicode isSpace. Duplicate rules, property
order, first-match lookup and empty trimmed values are retained. This is the
upstream style-attribute parser, not an expanded standards-compliant CSS parser.

WriterHtml ports Shared.tagWithAttrs/htmlAttrs/htmlAddStyle/
htmlAlignmentToString. It retains all HTML5/RDFa allowed names, the source label
exception, markdown and colon/data-/aria- rules, case sensitivity, duplicate
attributes and independently escaped id/classes/values. Keys themselves are
literal. Its nested hsep structure preserves break opportunities. Style updates
modify only the first style attribute and first matching rule; new properties
are prepended, malformed styles collapse to the newly supplied property, and
later duplicate styles/rules remain in their source order.

The frozen tools/haskell-writer-html-reference compiles unchanged Pandoc 3.12.1
Text.Pandoc.XML, Commonmark 0.3.1, TagSoup 0.14.8 and DocLayout 0.6 sources.
prepare-writer-html-reference.py verifies that CSS, Shared.trim/isWS and writer
HTML function bodies were copied verbatim, changing module/import wiring only.
The CSS parser imports the same Parsec primitives through Text.Parsec rather
than the large Pandoc.Parsing reexport. Expected results never call Kotlin.
All original Commonmark package sources, fixtures and license are retained;
the numerical dependency's original text-2.1.2 Read source is also retained.
The 22 Commonmark release files match the original Hackage archive verbatim,
SHA-256 `72ea2f2f678a5ed0cc7ab20e7eebf161f4a03d4a5e73eda3255c82ebc64dbc53`.
Generated HIE build files are excluded from tracking and source fingerprints.

The 18,789-case native corpus checks every Haskell Char (all 1,114,112 code
points, including surrogates) for XML escaping, start/body NCNames, numeric
lookup and entity formatting. It covers every Commonmark entity name, all
three attribute registries, 785 distinct attributes from accepted original
Native documents, all six-symbol CSS strings through length five, seeded
parser/style/attribute cases, signed cached document widths and generic Text/
String tag documents. Complete Doc trees and outputs at five wrapping widths
are checked independently. Case digest:
`f4826166f5700b78843b86034c220fbcaf58af7349c8ef9f801e2b2b2b57ff30`.
The importer verifies source/input hashes and complete upstream-attribute and
Unicode inventories; the production data importer separately verifies all
2,231 entities and 342 attribute-list entries against original source bytes.

Reproduce with cabal build all --project-dir=tools/haskell-writer-html-reference
--builddir=/tmp/kotdoc-writer-html-build, then run
python3 tools/import-writer-html-fixtures.py --oracle PATH.
CI checks the source/data/corpus and runs WriterHtmlTest.

Text.Pandoc.XML utilities are distinct from the XML AST serialization format.
Tests.XML's original p_xml_roundtrip remains pending with that reader/writer;
it is not claimed by the utility corpus. No dedicated CSS or shared writer
HTML unit suite exists in the pinned Pandoc tests. Original HTML reader/writer
consumer suites remain pending with their complete stateful implementation.
Commonmark parsing/token entity APIs (charEntity/numEntity/pEntity/unEntity)
and the full Commonmark parser/test suite remain pending beyond lookupEntity.
The remaining Shared context/math/grid/translation/parsing helpers, complete
Pandoc readers/writers/application/Lua/CLI and all calibre MOBI/additional formats
and tests remain within the unchanged full objective. This dependency milestone
does not complete that port.

Local validation: all 664,179 Kotlin tests pass across 71 suites, with zero
failures, errors or skips; WriterHtmlTest's complete 18,789 observations pass.
All 74 source/fixture checks pass. All 280 new fixture source/input hashes match
staged Git bytes and the pinned submodules. The JAR contains all three new
public utility classes, their data resource and all 38 retained license files.
The repository remains public. This utility layer adds no CLI reader/writer
registration; the new commit's CI runs the complete installed-CLI checks.

## Writer metadata and template context bridge

WriterMetadata ports Shared.metaToContext/metaToContext'/addVariablesToContext,
getField/setField/resetField/defField/getLang and the four metadata lookups.
The primed entry point is exposed as metaToContextPrime. Metadata maps visit
leaves in original Unicode Text key order, including nested maps; lists retain
their order. MetaString uses the unchanged Builder.text semantics before invoking
the inline writer. Blocks/inlines invoke their supplied writer once as a whole,
booleans need no rendering and nested maps/lists retain their source Val shape.
An absent template returns an empty Context without reading metadata or calling
either renderer or FromText. The prime function always converts metadata.

Variable documents are structurally lifted with FromText without changing cached
widths, fonts, links or fixed prefixes. Variables override metadata fields, and
generated meta-json overrides both. Its contents serialize the rendered original
metadata Context, before any variable overrides. The context JSON encoder walks
iteratively, preserving original keys, null/bool/list/map values and unwrapped
rendered target strings. It emits Aeson 2.2.3.0 Value-encoding string escapes:
backspace/form feed are numeric U+0008/U+000C escapes, rather than the host JSON
encoder's shorter spellings. Literal backslash-b/backslash-f text is preserved.
Unicode and escape spellings compare as exact strings, not normalized JSON.

setField appends one value to an existing list; a newly supplied list remains
nested. Other existing values become a two-element list. resetField replaces
the value, and defField retains an existing value without evaluating its
converter. getField delegates to the selected FromContext dictionary. getLang
accepts a rendered simple variable (including an empty string), otherwise only
the exact source MetaString, singleton Str inlines or singleton Plain/Para with
one Str. Maps, lists and multi-inline languages do not silently stringify.
The four metadata lookups preserve the original constructor rules, including
Plain/Str output for empty MetaString and True/False text for MetaBool.

WriterHtml now accepts a separate FromText callback for attributes. Fixed tag
syntax still uses IsString, while id/classes/keys/values use FromText as in the
source. Existing Text/String defaults remain unchanged. Native independent
dictionaries also exposed doctemplates' distinct JSON Number conversion:
it uses IsString, whereas the String branch uses FromText. TemplateValues now
preserves that distinction; ordinary built-in targets keep their prior results.

The frozen tools/haskell-writer-metadata-reference compiles verbatim extracted
Shared metadata/context/stringify bodies, unchanged UTF8 and dependency sources,
and the previously verified full 39-field WriterOptions declaration. Sparse
native options deliberately leave the other 37 fields undefined, proving the
helpers do not read them. prepare-writer-metadata-reference.py verifies the
original function bodies and writer record before capture/check. No reduced
metadata algorithm or Kotlin implementation generates native expectations.
The SourceWriterHtml body comes from the preceding independently verified port.

The 11,241 native observations cover all 266 accepted upstream metadata maps
with and without a template on Text, Haskell String and a Wrapped target whose
FromText/IsString/ToText dictionaries deliberately differ. They compare entire
Context/Val/Doc structures, callback inputs/order, original rendered JSON values,
exact encoded JSON strings, language lookup and four FromContext choices.
Seven ToContext input kinds, all five Val constructors, all sixteen Doc
constructors, duplicate keys, existing empty lists, nested appended lists,
language boundaries, seeded nested metadata and arbitrary Integer/scientific
JSON inputs are included. Partial native probes cover missing fields, ignored
new default values and an absent template with undefined metadata/variables.

Every Haskell Char (all 1,114,112 code points, including surrogates) is checked
on all three targets for meta-json rendering and key escaping. Original String
points remain distinct until the source ToText conversion, which replaces each
surrogate with U+FFFD. Depths through 5,000 maps check the exact encoded result
without converting through a recursively allocated host JSON tree. Kotlin
dynamic tests parse captures when executed, retaining raw fixture lines rather
than millions of Unicode JsonPrimitive descriptors. No observations are dropped.
Case digest: `39b7340d1c9adec9b6dafb79813cb60cf2fe8adba9f8af3af3f9cc73fc240a83`.
The importer verifies all 419 original source/input/adapter/license hashes,
complete upstream metadata inventory and every code point for every target.

Reproduce with cabal build all --project-dir=tools/haskell-writer-metadata-reference
--builddir=/tmp/kotdoc-writer-metadata-build, then run
python3 tools/import-writer-metadata-fixtures.py --oracle PATH.
CI verifies the source/corpus and runs WriterMetadataTest.

No dedicated Shared metadata unit suite exists in the pinned upstream tests;
writer consumer tests remain pending with their complete implementations.
Arbitrary lazy/partial value interfaces and general monadic typeclass adapters
remain beyond the finite synchronous writer callbacks and dictionaries tested
here. The remaining Shared math/grid/translation/parsing helpers, complete
HTML pipeline, other Pandoc readers/writers/application/Lua/CLI and all calibre
MOBI/additional formats and tests remain within the unchanged full objective.

Local validation: all 675,420 Kotlin tests pass across 72 suites, with zero
failures, errors or skips. All 11,241 metadata observations and all 75
source/fixture checks pass. All 419 new source/input hashes match staged Git
bytes and the pinned submodules. The core JAR contains WriterMetadata and all
38 retained license files; the public repository and clean mirror checkout are
preserved. This dependency adds no reader/writer CLI registration. Exact-SHA
CI runs all source, Kotlin and installed-CLI checks after publication.


## Shared grid-table writer and span bookkeeping

GridTable.gridTable ports the complete finite grid algorithm from
Text.Pandoc.Writers.Shared. It accepts the source column/head/body/foot inputs
and a writer callback; the convenience Block.Table overload ignores table
attributes/caption as the original shared function does. AnnotatedTable clips
and places spans before callbacks run. The callback receives the exact original
WriterOptions instance and normalized cell blocks, first for the head, then the
foot, then each body's intermediate headers and body cells. Renderer failures
stop at the same cell with the same observed callback prefix.

The port retains specified/full/minimum/current widths, ragged transpose maxima,
per-column division of spanning widths, source div versus rem semantics,
WrapNone minimum widths and the original bounded redistribution recurrence.
resetWidths preserves short width lists and nonpositive span branches rather
than adding validation. Spanning widths include three columns per internal
separator. Dummy cells are threaded from the previous rendered row separately
for head, foot and each body; the algorithm does not invent a rowSpan filter.
Intermediate body headers and body rows share that body's dummy pass.

Borders retain all five LineStyle variants, exact precedence when joining rows,
shorter-length Text.zipWith behavior and markers from the first covered column's
alignment. Cell alignment is not substituted for column alignment. Header lines
merge boundaries across all rows; head/body/foot top and bottom styles preserve
the source's empty-section and empty-next-row branches. Cell contents use lblock
regardless of alignment, as in the original writer. The complete resulting
DocLayout structure, including the source right-associated concatenations,
compares directly against native output before rendered strings are compared.

The numeric path retains signed Int64 overflow in available-width arithmetic,
width sums, spans and reset widths. toCharWidth uses GHC's optimized
floorDoubleInt sequence: float-to-Int truncation, comparison and decrement,
followed by subtraction of three and max zero. A mathematical BigInteger floor
would disagree at nonfinite/out-of-range values. The captured GHC 9.12.2 -O2
macOS ARM64 baseline agrees with JVM Double.toLong conversion for these cases.
Other native-GHC architecture overflow behavior is not claimed. Huge widths are
observed through the original numeric/private-width helpers without attempting
to allocate astronomical border strings. Ordinary table results are checked
unchanged on the JVM and Linux CI.

WriterTables ports allRowsEmpty/tableBodiesToRows and all three exported span
map helpers. Empty means absent blocks, not visually blank rendered content.
Body flattening keeps intermediate headers before body rows and preserves row
attributes. Insertion skips row spans <= 1; taking decrements/deletes only a
positive tracked span; trailing decrement touches only keys at/after the given
column and preserves existing nonpositive spans and arbitrary column spans.
Returned maps are immutable snapshots in ascending numeric key order, retaining
the Data.Map contract even when callers provide maps in another iteration order.
Seventy-two native shuffled-map observations compare the returned key sequence
directly; the observation adapter performs no output sorting.

The frozen tools/haskell-grid-table-reference compiles unchanged extracted grid
and table helper bodies, original AnnotatedTable, DocLayout 0.6 and the full
previously verified WriterOptions declaration. prepare-grid-table-reference.py
checks exact source bodies and the record. The local redistribution recurrence
is exposed through a wrapper with its body retained verbatim. Sparse native
options leave all fields other than columns/wrap and two callback markers
undefined. The production code has no Haskell runtime dependency.

The 12,865 native observations include all 242 tables in accepted upstream
Native fixtures under nine columns/wrap combinations; empty and ragged rows,
row/column spans, stub clipping, intermediate headers, multiple bodies and feet;
all sixteen Doc constructors with font/link data and mismatched cached widths;
raw annotations with all alignments and signed span limits; all border styles
and marker combinations; 800 seeded rendered-cell pipelines, signed reset and
600 arbitrary redistribution states; IEEE bit patterns, NaN payloads, infinities,
subnormals and both signs around powers of two; span map operations at signed
limits; and renderer failure prefixes. All expectations come from original
Haskell. No callback adapter is claimed as an implementation of a document
format writer. The source manifest contains 185 original/input/oracle/license
files. Case digest:
`d6b72905f1c1c703a9f4cba4f151976fd80c66194b1ba68a00680970ffe96d2a`.

Reproduce with cabal build all --project-dir=tools/haskell-grid-table-reference
--builddir=/tmp/kotdoc-grid-table-build, then run
python3 tools/import-grid-table-fixtures.py --oracle PATH.
CI checks the source/corpus and runs GridTableTest. The pinned Tests.Shared has
no dedicated grid/span helper unit suite. Original Markdown/RST/Muse consumer
suites remain pending with those complete writers. General monadic/partial-value
interfaces remain beyond the synchronous finite callbacks tested here. Private
model Show/Ord/typeclass contracts are not claimed by JSON observations.
The remaining Shared math/translation/parsing helpers, complete Pandoc
readers/writers/application/Lua/CLI, and all calibre MOBI/additional formats and
tests remain within the unchanged full objective. This is a dependency milestone,
not completion of that port.


Local validation: all 688,285 Kotlin tests pass across 73 suites, with zero
failures, errors or skips. All 12,865 grid/table observations, all 76
source/fixture checks and all 234 installed-CLI comparisons pass. All 185 new
source/input hashes match staged Git bytes and pinned submodule commits; the
staged corpus bytes match the checked worktree. The core JAR contains GridTable,
WriterTables and all 38 retained license files. The repository remains public.
This dependency adds no CLI format registration. Exact-SHA CI runs the complete
source, Kotlin and installed-CLI checks after publication.


## Pure shared writer operations

WriterShared ports fourteen exports from Text.Pandoc.Writers.Shared:
isDisplayMath, stripLeadingTrailingSpace, fixDisplayMath, unsmartify,
toSubscript/toSuperscript, both Inline script adapters, endsWithPlain,
splitSentences, delimited, removeLinks, toTaskListItem and
ensureValidXmlIdentifiers. XML identifier validation delegates to the existing
DocumentShared implementation, whose letter test is now pinned to GHC Unicode
16 through ParserCharacters.isAlpha. No Haskell process is a production
conversion dependency.

Display-math recognition accepts only a direct DisplayMath or a Span containing
exactly one direct DisplayMath. It does not search arbitrary descendants.
fixDisplayMath changes only mixed Plain/Para inline lists, retaining the source
block kind inside Div with class math. Equal recognition runs are grouped,
Space/SoftBreak are stripped from their ends, and empty groups disappear.
LineBreak and text containing whitespace remain content. A block consisting
entirely of recognized display math remains unchanged.

The script character APIs use Int code points for the full Haskell Char range,
including surrogate positions. The exact source mappings for digits, operators,
parentheses and Unicode minus are retained; whitespace follows GHC isSpace.
Other characters return null. Inline conversion accepts Str, Span and the three
whitespace constructors only. Unsupported descendants abort without walking
subsequent siblings. Span attributes and nested structure remain unchanged.
The traversal is iterative, with native depth observations through 5,000 spans.
Text inputs use scalar Text semantics; surrogate Char inputs and Text conversion
are observed separately rather than conflating UTF-16 units with Haskell Char.

unsmartify reverses the seven original smart punctuation characters. Only the
two dash cases read OldDashes; enabling it changes en/em dashes to one/two ASCII
dashes, while the ordinary mode uses two/three. All other characters retain their
original scalar values. Native sparse WriterOptions leave the other 38 fields
undefined. The original HasSyntaxExtensions instance, isEnabled and
extensionEnabled accessors are retained verbatim in the reference harness.

endsWithPlain follows the last item of BulletList/OrderedList and the last
definition of the last DefinitionList entry, iteratively. Other containers are
not searched. Plain contents and earlier block values are not demanded.
removeLinks uses the existing source-compatible bottom-up AST visitor, replacing
Link by Span with the same attributes/content and walking citations, notes,
tables, figures and all other nested inline locations. Original Link targets
are discarded and replacement Spans are not revisited.

Doc operations flatten only Concat nodes. Prefixes, styles, links, cooked text
and blocks remain opaque. delimited moves only the four source whitespace
constructors outside the opener/closer, preserving right-associated output and
all-whitespace/explicit Empty branches. splitSentences changes BreakingSpace
(and an immediately preceding AfterBreak) after sentence-ending Text into
NewLine; it retains the cached Text width. Periods after a single GHC-uppercase
character are initials, including supplementary letters and titlecase. One
closing parenthesis/bracket/straight quote/right smart quote may follow the
punctuation. Arbitrary whitespace payload text and nested Styled/Linked content
are not recursively rewritten. Deep left/right Concat spines are observed
through 10,000 nodes; signed Int64 cached widths are compared structurally
without allocating astronomical padding.

toTaskListItem accepts exactly the source eight Plain/Para forms for unchecked
U+2610 and checked U+2612. A single marker needs no following Space; a marker
with remaining content requires a literal Space. Other markers and SoftBreak
separators fail. The nullable convenience entry point represents Maybe; a
pure/zero callback adapter preserves the two operations used from MonadPlus.
Native Maybe and list dictionaries, plus callback selection counts, compare
directly. Read-only suffix/head views preserve ignored inline contents and block
tails when a consumer requests only the checkbox flag. A native partial-success
probe exposed eager drop/copy in the initial Kotlin translation; the retained
regression now verifies that those values are not read. Inputs follow the
existing Kotlin AST List contract.

All Haskell Char positions are tested directly for both script functions,
XML identifier classification and sentence-initial handling. unsmartify is
checked over every position in both dash modes, after original Text packing.
The XML probes exposed ICU's newer Unicode 17 letters, including U+088F and
U+A7CE, which the pinned GHC/Pandoc correctly prefixes as non-letter identifiers.
The fixed DocumentShared predicate retains that pinned behavior. No global ICU
classification behavior or unrelated identifier generation is changed here.

The frozen tools/haskell-writer-shared-reference compiles verbatim function
bodies from pinned Shared, original extension accessors, the previously verified
full WriterOptions declaration, pandoc-types 1.23.1.2 and DocLayout 0.6. The source
preparer checks original bodies/record and the importer retains every original
expectation independently of Kotlin. The 50,729 observations include all 266
accepted Native documents; all 13,475 distinct inline/block values representing
64,914 upstream occurrences; exhaustive display-math/whitespace sequences of
length zero through four; source marker boundaries; all sixteen Doc constructors,
fonts/links, left/right associations and seeded trees; signed cached widths;
deep spans, lists and Doc spines; and controlled partial inputs. StrictData AST
constructor fields remain defined in partial native probes: only valid ignored
list elements/tails are left undefined.
Case digest:
`bdfe89a7e54aa8e0e4b8c8cc4d5cd7aa8303de43b229bd69748d58efe0d57d07`.
The manifest verifies 399 original/input/adapter/license/data files, the complete
upstream node/document inventory and every character in every applicable mode.

Reproduce with cabal build all --project-dir=tools/haskell-writer-shared-reference
--builddir=/tmp/kotdoc-writer-shared-build, then run
python3 tools/import-writer-shared-fixtures.py --oracle PATH.
CI checks the source/corpus and runs WriterSharedTest. There is no dedicated
upstream unit suite for these exports in the pinned Tests.Shared; complete
writer consumer suites remain pending with their implementations. Arbitrary
lazy/infinite value interfaces, general MonadPlus typeclass instances and
resource behavior of the general recursive AST visitor remain beyond the finite
callbacks/list adapters covered here. setupTranslations and isOrderedListMarker
still require their full language/translation and parser dependencies. Complete
Pandoc readers/writers/application/Lua/CLI and all calibre MOBI/additional
formats and tests remain within the unchanged full objective.


Local validation: all 739,014 Kotlin tests pass across 74 suites, with zero
failures, errors or skips. All 50,729 pure-helper observations, all 77
source/fixture checks and all 234 installed-CLI comparisons pass. All 399 new
source/input hashes match staged Git bytes and pinned submodule commits, and
the staged corpus bytes match the checked worktree. The core JAR contains
WriterShared, the pinned character data and all 38 retained license files.
Original extracted whitespace is retained with a narrowly scoped Git attribute
for the source module's final blank line. The repository remains public. This
milestone adds no CLI format registration. Exact-SHA CI runs the complete source,
Kotlin and installed-CLI checks after publication.


## Ordered-list parsing and writer marker detection

ListParsing translates all eight exports of Text.Pandoc.Parsing.Lists:
romanNumeral, upperRoman, lowerRoman, decimal, lowerAlpha, upperAlpha,
anyOrderedListMarker and orderedListMarker, including every private helper.
It uses the existing consumed/empty Parsec continuations rather than a regular
expression or a Boolean approximation. Roman parsing preserves the original
permissive sequence of additive/subtractive stages and prefix consumption.
The single i/I ambiguity, ASCII-only alphabetic markers, # continuation of every
style, parenthesis/period rules and backtracking order match the source.
Decimal Read Int wraps modulo 2^64, including literals beyond Int64 and 10,001
digits; it does not reject overflow or silently default those values to one.

ExampleNumberState provides access to the two fields used by this module,
stateNextExample and stateExamples, preserving the caller's other fields on
updates. ExampleNumbers supplies their default values for stateless writer
marker detection. This is explicitly a field capability, not a replacement or
claim of completion for the full 31-field ParserState, Future values, TeX macros,
reader options or parser-context instances. Empty labels, reused labels, numeric
prefix resets, signed counter overflow and failed-parse rollback are preserved.
The default writer isOrderedListMarker now requires the source last-character
precheck, anyOrderedListMarker and complete-input eof success.

PandocTextCharacters retains the original Sources primitives for Text/String
streams: token failures show a Haskell Char and have no Parsec.Char expectation
label. Source positions advance by code point, with the original Text/String
newline and eight-column tab rules. The chunked Sources stream (including its
four-column tabs and source-file position jumps), ByteString stream instances
and the rest of Sources remain pending; their eventual integration must use the
same list algorithms. General lazy/infinite input stream APIs also remain pending.

The frozen tools/haskell-list-parsing-reference compiles Lists.hs and Sources.hs
directly from pinned Pandoc, plus unchanged safeRead/safeStrRead and the writer
marker body. Its only state wiring is a documented test-only projection of the
two source fields with an untouched sentinel. The source preparer verifies the
exact field access inventory, declarations/defaults and extracted bodies. It
also rejects local shadow copies of Lists or Sources. Original expectations
are captured independently of Kotlin.

The 47,175 observations compare success/failure, consumed/empty status, complete
ParseError messages and display, source positions, consumed prefixes, remaining
input, example counters and ordered label maps. Failure observations do not
invent an exposed user state, which native Parsec does not return. The corpus
covers exhaustive upper/lower Roman sequences through length four; all 28 style
and delimiter pairs; malformed delimiter and label boundaries; all 3,867 pinned
Unicode classification interval boundaries; huge decimal literals; Roman and
example labels through 10,000 repetitions; seeded mixed input; sequence/eof/many
composition; rollback; reused labels and Int64 wrap. Three native partial-field
probes retain defined state records and leave only unused fields undefined,
verifying that those fields are not read. 398 distinct candidate tokens are
retained from original reader/command fixture text with file/line provenance.
These token-level observations do not count as complete Markdown/RST reader
suite coverage, which remains pending with those readers. No dedicated Lists
unit suite exists in the pinned upstream test tree.

Case digest:
`396ea156228df9c1eba8fdf19d08be46b07975e161e010aa14a2553b40ede60d`.
The importer verifies 452 source/input/adapter/license files, the corpus digest
and complete retained upstream-token inventory. Reproduce with cabal build all
--project-dir=tools/haskell-list-parsing-reference
--builddir=/tmp/kotdoc-list-parsing-build, then run
python3 tools/import-list-parsing-fixtures.py --oracle PATH.
CI checks the new corpus and runs ListParsingTest. This change registers no new
CLI format. Full Pandoc readers/writers/state/application/Lua/CLI, translations
and all calibre MOBI/additional formats and their tests remain part of the
unchanged full objective.


Local validation: all 786,189 Kotlin tests pass across 75 suites, with zero
failures, errors or skips; this includes all 47,175 new source observations.
All 78 source/fixture checks pass. The core JAR retains all 38 license files,
ListParsing, PandocTextCharacters, ExampleNumbers and the writer marker predicate.

All 234 installed-CLI comparisons also pass (14 FB2, 66 media, 69 extension and
85 math cases). All 452 new source/input hashes and the retained corpus match
staged Git bytes and pinned submodule commits. Original extracted whitespace is
retained. The repository remains public; exact-SHA CI checks the full suite
after publication.


## Future and deferred parser computations

Future translates the complete Text.Pandoc.Parsing.Future module: the constructor
and runDelayed accessor, runF, askF, asksF and returnF, plus derived Functor,
Applicative and Monad operations and the source Semigroup/Monoid instances.
Both sides of bind and every applicative operand receive the same final state.
Reusing an action with different final states does not cache its environment.
ReturnF's outer Monad uses only return, represented by a pure dictionary callback.
Maybe, list, Identity, IO and Proxy dictionaries are compared natively, including
construction without demanding the future or its value.

Deferred arguments/results carry Haskell's demand semantics across the Kotlin
callback boundary. map, flatMap, apply and liftA2 callbacks can ignore their
inputs, including failed actions or undefined fields. Value adapters deliberately
demand their argument before invoking an ordinary Kotlin callback. then, before,
replace and left/right biased semigroups preserve their ignored operands.
Monoid dictionaries include lists, Sum, Product and Maybe Sum. The public type
remains generic in state and result; it is not a closed evaluator for the test
program grammar. Consumers supply their actual state/datatype functions.

Deferred evaluates compositional nodes through an explicit work stack. Cells
share successful values, nullable results and exceptions, and release evaluated
closures. Per-cell locks coordinate shared demand; a focused test has 32 consumers
on eight threads and verifies a single evaluation. A self-demanding shared thunk
produces the native <<loop>> failure rather than growing the work stack forever.
This is not a port of the complete GHC runtime, its scheduler or asynchronous
exception machinery. Callbacks should compose Deferred values rather than
recursively forcing them on the host stack.

The independent tools/haskell-future-reference compiles the unchanged Pandoc
Future module and the complete local mtl 2.3.1/transformers 0.6.1.2 releases.
No Future/Reader implementation is reconstructed in the oracle. Original release
bytes and notices are retained, with a frozen Cabal project. Its test datatype
and function dictionaries retain lazy fields and list spines. Initial comparison
failures exposed prematurely strict numeric payloads and list indexing in the
Kotlin transport; the corrected adapter retains every original regression.

The 13,463 native observations cover constructor/accessor and all exports,
all operator families, partial inputs, ignored failures, Monad return shapes,
shared success/failure demand, nullable results, shared cycles, finite projections
of an infinite list, signed Int64 overflow, seeded nested compositions and chains
through 100,000 operations. Every one of the 266 accepted upstream Native
documents and metadata maps is transported through the generic reader; an action
is also reused with changing final metadata states. These are Future observations,
not claims of completion for the full format reader suites.
Case digest:
`736de33769fd5180f292cc09a4e2e15a2a45482ec5d4267601542cc57391a11b`.
The importer verifies all 344 source/input/adapter/license hashes, complete
upstream document provenance and the independent corpus digest.

Reproduce with cabal build all --project-dir=tools/haskell-future-reference
--builddir=/tmp/kotdoc-future-build, then run
python3 tools/import-future-fixtures.py --oracle PATH.
CI checks the corpus and runs FutureTest. There is no dedicated Future unit suite
in the pinned upstream Tests tree; the complete Markdown/RST and other consumer
suites remain pending. The full 31-field ParserState still requires typed TeX
Macro/Tok/ArgSpec and LogMessage dependencies and its capability implementations.
No placeholders for those types are introduced here. Complete Pandoc and all
calibre MOBI/additional-format implementation and tests remain within the
unchanged full objective. This milestone registers no new CLI format.


Local validation: all 799,653 Kotlin tests pass across 76 suites, with zero
failures, errors or skips. All 13,463 new native observations and the shared-demand
host test pass. All 79 source/fixture checks pass. The JAR contains Future,
Deferred and all 40 retained licenses, including both new original Reader
license copies. The two complete dependency releases retain original bytes and
whitespace through scoped Git attributes.

All 234 installed-CLI comparisons pass (14 FB2, 66 media, 69 extension and
85 math cases). All 344 new source/input hashes and the corpus match staged Git
bytes and pinned submodule commits. The repository remains public. Exact-SHA CI
checks the complete source, Kotlin and installed-CLI suites after publication.
