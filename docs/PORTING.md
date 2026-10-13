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

Current verification: 5,484 Kotlin tests pass locally. Every push runs source
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
