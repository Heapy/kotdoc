# kotdoc

Native Kotlin document conversion, porting Pandoc and then calibre's MOBI and
additional formats. **Work in progress: this is not yet a replacement for either project.**

The complete goal is to port Pandoc, including its tests, and then port calibre's
MOBI support, additional formats missing from Pandoc, and their tests. No external
Pandoc or calibre process is used by the implemented Kotlin code.

## Current implementation

- Typed document model covering every Pandoc 1.23 AST constructor.
- Pandoc JSON reader and writer, including metadata, citations, tables, figures,
  API version validation, and ordered/duplicate attributes.
- All 56 literal JSON examples from `pandoc-types`, with independent typed Kotlin
  expectations and byte-for-byte encoder checks (112 individual checks).
- Bottom-up AST transforms and preorder queries, including metadata, citation
  affixes, captions, tables, notes, and list-level hooks.
- Table normalization and simple tables, with the original examples and nine
  upstream property checks plus an independent grid-occupancy oracle.
- Additional Unicode, malformed input, API compatibility, and recursive
  roundtrip tests. Current validation totals are recorded in the
  [porting ledger](docs/PORTING.md).
- Pandoc builders, metadata helpers and both legacy/modern figure representations.
- Full Unicode raw-format case folding and 64-bit AST integers.
- Original Haskell AST Show/showsPrec for every node and associated table,
  metadata and citation type. Checked by 36,970 independent native observations,
  all 266 original documents, IEEE values, lazy/infinite prefixes and 100,000-step
  structures. Full Read contracts remain in progress.
- Typed AST source Eq/Ord dictionaries, including Citation hash ordering, every
  relational operator, caller-preserving min/max and nested NaN behavior. Checked
  by 75,470 native observations, all 266 documents, 17 original generators,
  partial/infinite inputs and 100,000-step structures.
- Configurable JSON pretty output from aeson-pretty, including exact Aeson text
  escaping, Scientific numbers, key comparators and indentation. Checked by
  21,522 original-library observations.
- All 54 typed Logging messages: original human-readable text, JSON fields,
  severity, derived Show and source Eq/Ord, plus Verbosity codecs/Read/Enum.
  Checked by 25,373 independent observations and complete FB2 diagnostic JSON
  comparisons. Raw source names and file paths preserve Haskell Chars.
- Native reader/writer and JSON ↔ Native CLI conversion with standalone output
  and configurable column width. Verified against all 266 upstream Native fixtures.
- GHC-compatible number and string display, with additional lexical/layout cases.
- FB2 reader: metadata, sections, notes, poetry, images, diagnostics and binary
  insertion effects. All six original reader goldens, all 14 upstream FB2 files,
  and 180 independent oracle cases, including lenient Base64 decoding.
- XML parsing follows Pandoc/xml-conduit namespace, entity and token rules.
- FB2 writer: sections, lists, tables, figures, notes, metadata, embedded images,
  raw XML, and math handling. All three original writer examples plus exact output
  and diagnostic comparisons for 266 upstream documents and 205 edge cases.
- Shared section construction, block flattening, legacy tables and XML output.
- TeXMath expression models, shared transformations, Unicode/TeX/Pandoc output
  and the complete finite MathML expression writer, including all 561 original
  MathML writer goldens. The complete finite TeX formula reader, macro
  preprocessing, command tables and Unicode-to-TeX mapping are implemented,
  including 69 original native reader goldens. The Pandoc math bridge preserves
  original inline/fallback results, reporting and deferred generic writers.
- MediaBag, complete MIME/URI tables, data URI decoding, local resource paths,
  and HTTP resource fetching. Verified with 2,172 independent MediaBag/MIME cases,
  18 data URI cases and a local HTTP integration test.
- Media filling and extraction into directories or deterministic ZIP archives,
  with image-link updates, missing-image placeholders and typed resource failures.
  All original Haskell MediaBag and Lua media API examples are translated into
  Kotlin API tests; this does not yet provide the Lua scripting engine.
- Data URI creation preserves upstream UTF-8, BOM, CR and MIME behavior.
- Complete finite `Text.Pandoc.URI` exports: encoding/escaping, scheme recognition,
  POSIX/Windows URI paths and the incremental Base64 data-URI parser. Checked by
  40,705 original-library observations, all Haskell Chars and 100,000-step parser
  chains/chunk feeds. Parser results preserve independent deferred MIME/bytes.
- LaTeX reader foundation: complete tokenizer/source-token conversion and token
  stream semantics, all 30 state fields/defaults, scoped macro tables and raw/
  verbatim/local-input helpers. Checked by 8,973 original observations, including
  all 90 reader unit inputs, every Haskell Char and 100,000-step structures.
- LaTeX token grammar, macro expansion, numeric evaluators, Sources bridges and
  the complete finite macro-definition module, including global scope updates.
  Checked by 34,080 kernel and 1,553 definition observations, both native numeric
  platforms, all 33 definition commands and 100,000-step structures.
- LaTeX citation commands and math/theorem/proof environments, with optional
  notes, raw capture, numbering, labels, styles and state/report semantics.
  Checked against the complete original Citation/Math modules, including all
  original document trees and 100,000-level finite witnesses. Full LaTeX reader
  grammar and original reader/CLI AST assertions remain in progress.
- LaTeX Babel/Polyglossia language mappings and commands, nested quote contexts
  and default-language metadata/translation-cache effects, checked against the
  complete original Lang module and original inline trees. Full document reader
  and original reader/CLI assertions remain in progress.
- LaTeX SIunitx commands, complete unit/modifier tables, uncertainty/exponent
  number parsing, lists, ranges, angles and unit powers. Original-module checks
  include exact errors/state, every Haskell Char, original inline trees and deep
  finite inputs. The complete LaTeX document reader remains in progress.
- LaTeX inline command modules: all eight registries, verbatim/listings/minted
  code, references and labels, acronyms, BibLaTeX formatting, translated names
  and accents. The NFC dependency uses pinned Unicode tables and a Kotlin
  normalization algorithm; 188,741 independent original observations include
  every Char/accent pair, all original NFC assertions and all 107 translations.
  Full LaTeX reader/CLI assertions remain pending.
- FB2 → FB2 CLI conversion preserves embedded media through the conversion;
  installed CLI stdout/stderr match Pandoc on all 14 tracked FB2 source files.
- All 90 built-in extension names, custom extensions, six presets and the complete
  default/supported extension registry. Format modifiers preserve original set
  composition, ordering, validation and CLI errors; path inference includes URI
  paths and the original filename rules. Registry entries do not imply that the
  corresponding reader/writer has been ported.
- ReaderOptions: all ten fields, library defaults, JSON codecs and syntax-extension
  queries. Shared citation, wrapping, caption, slide and math option types include
  their original JSON aliases and URL defaults. CLI math methods are connected to
  FB2 output, with original deprecated-option warnings and invalid-option errors.
- Skylighting's complete syntax records and matcher constructors, keyword-list
  resolution and syntax lookup, checked against all 195 original XML models.
  RE metadata has lazy compilation/capture caches and original Show/Read,
  JSON and Binary behavior. XML syntax parsing and file/directory loading are
  checked against 6,168 original parser/loader observations, including all 195
  XML files. Binary syntax codecs and the complete baked default map preserve
  all 195 original definitions and are checked against 77,801 independent native
  observations. The tokenizer now preserves all matcher kinds, context/capture
  state, lookahead, physical-line switches and original debugging traces; checked
  against 27,881 independent original token/trace/error observations, including
  all 32 original goldens, 21 regressions and mixed/fuzz inputs for every language.
  HTML/HTML4, ANSI, LaTeX, ConTeXt and Typst formatters and their CSS/macro output
  are checked against 27,030 complete original output/model observations.
  All eight FormatOptions fields and public color/style/options Binary instances
  preserve original field order, malformed-data errors and remaining bytes.
  Pandoc's shared highlight wrapper preserves class priority, numbering, language
  lookup and all 103 LaTeX listings mappings. The complete 39-field WriterOptions
  record retains compiled templates/contexts and powers Native/FB2 output.
  Its original constructor display includes every nested model. FilePath fields
  use immutable CharacterString values, preserving individual Haskell Chars;
  CharacterStringChars.fromString converts ordinary Kotlin paths. TemplateShow
  supplies original display for templates, contexts, values, documents and fonts,
  with an explicit target showsPrec callback and safe handling of deep trees.
  TemplateComparison exposes original Eq/Ord operations for templates, documents,
  attributes and fonts, retaining constructor/field order, NaN behavior, target
  short-circuiting and min/max selection. CharacterString has original Char-list
  ordering without converting to JVM UTF-16 strings.
- Structural Blaze markup, attributes, lazy return values and chunked Text/bytes,
  String/Text/UTF-8/Pretty output, contents/external transforms and Pandoc's
  markup-to-DocLayout wrapping. Checked against 71,478 original observations,
  including every byte pair, all 25 original HTML examples, constructor trees,
  Doc structure and partial values; all ten upstream markup properties are ported.
  The HTML writer is implemented; remaining builder/HTML vocabulary APIs are pending.
- Annotated tables for writer output: clipped spans, exact covered column specs,
  continuous row/column numbers, separated row headers/body cells and inverse
  conversion. All six original tests are ported, with 1,400 independent native
  observations covering all 242 upstream fixture tables and cell/row/head walks.
- Shared grid-table output: source width redistribution, merged cells, header
  and footer borders, alignment markers and exact renderer callback order.
  Checked against 12,865 original Haskell observations, including all 242
  upstream tables, all DocLayout constructors, renderer failures and Int64/IEEE
  boundaries. Shared row/span bookkeeping helpers are also ported; complete
  Markdown/RST/Muse writers and their consumer suites remain pending.
- Pandoc XML utilities and all three CSS helpers, plus shared writer HTML tags,
  attributes, style updates and alignment names. Checked against 18,789 original
  Haskell observations, including every Unicode code point, every entity name,
  all HTML4/HTML5/RDFa attribute registries and 785 upstream attribute sets.
  The full XML reader/writer remains pending.
- Writer metadata/template contexts: source callback ordering, template gating,
  variable precedence, exact rendered `meta-json`, field updates, strict language
  selection and metadata lookups. Checked against 11,241 original observations,
  including all 266 upstream metadata maps, three independent text dictionaries,
  every Unicode code point and nesting through 5,000 levels. The complete HTML
  writer is implemented; remaining Shared helpers are still in progress.
- Pure writer helpers: display-math grouping, Unicode sub/superscripts, smart
  punctuation reversal, sentence splitting, delimiter whitespace, link removal,
  task-list extraction and XML identifier validation. Checked against 50,729
  original Haskell observations, all 1,114,112 Char positions, all 266 upstream
  documents and all 13,475 distinct upstream AST nodes. XML ids now use pinned
  GHC Unicode 16 classification. Parsing/translation helpers and complete format
  writers remain in progress.
- Ordered-list marker parsing: all eight source algorithms, Roman/letter
  ambiguity, style/delimiter combinations, example labels, counter updates and
  parser backtracking. The writer marker predicate now uses the parser plus EOF.
  Verified by 47,175 original Haskell observations and 398 upstream fixture tokens.
  Complete format readers remain in progress.
- Sources: positioned fragments, retained empty chunks, source-file transitions,
  four-column tabs, all character primitives, bulk spans and input expansion.
  Text, raw characters and both byte streams have independent native comparisons;
  deferred fields and list tails preserve demand behavior. Verified against 154,295
  original observations, every GHC Char, 1,177 upstream text inputs and 400 list
  parser integrations. Complete consumer reader suites remain pending.
- Future computations for the final parser state: all source exports and
  Functor/Applicative/Monad/Semigroup/Monoid behavior, with lazy callbacks,
  shared results and explicit-stack evaluation. Checked against 13,463 original
  Haskell observations, nullable values, cycles, finite infinite-list projections,
  100,000-step chains and all 266 original documents/metadata maps. General
  format-reader integration remains in progress.
- All 31 fields and defaults of Pandoc's parser state, typed reference/note/macro
  tables, final-state Future values and state capabilities for both character and
  token parsers. Key normalization and original Eq/Ord/Show/Read retain the exact
  text-2.1.2 lowercase table. Verified by 4,725 original-library observations,
  every Haskell Char, nested quote scopes, external log effects and all 266 Native
  documents. Another 34,920 original list-parser runs use the full state and check
  preservation of every unrelated field. General lazy/partial record-field demand
  remains in progress.
- HTML reader state: all eleven global fields and four local fields, original
  capability instances and separate ReaderT environment scope. Character and
  token parsers share the existing consumed/empty interpreter. Verified by 6,368
  independent native observations, 100,000-step Reader compositions, quote/state
  rollback and retained external effects. Full URI/authority records preserve raw
  Haskell Chars, source Eq/Ord/Show, formatting and rectification, including all
  four original URI Show assertions and every Haskell Char. The full HTML reader
  traversal remains in progress.
- Shared HTML reader helpers for metadata accumulation, boundary spaces, code
  formatting and span-like elements. AST walk and inline stringification handle
  deep finite documents without JVM recursion. Verified by 19,643 original-library
  observations, all 10,584 inline lists from 266 Native documents, original
  generator/property inputs, every Char and 100,000-level span/note structures.
- Network.URI parsing, validation, escaping, normalization, relative resolution
  and deprecated accessors, sharing the existing Parsec interpreter. MediaBag
  delegates to this parser. Verified by 95,464 original-library observations,
  all 339 active upstream unit tests and both translated property predicates,
  plus every Char and all 65,536 escaped byte pairs. QuickCheck RNG/shrinking
  is not ported; Kotlin properties use seeded samples and exhaustive scalars.
- TeX token/macro data: all token kinds, scopes, expansion points, Macro fields
  and nine argument-specification forms. Original Eq/Ord/Show and token-to-Sources
  grouping are checked by 21,326 native observations, lazy/infinite prefixes and
  100,000-node structures. The full LaTeX document reader remains in progress.
- Table-of-contents trees and lists, with original numbering, depth, section IDs,
  hidden sections and title cleanup. Chunk path templates preserve substitution
  order and filename sanitization.
- Document splitting into chunks, preambles, cross-chunk links and navigation
  snapshots. All 266 original Native documents are checked in two modes, alongside
  section/metadata/HTML edge cases: 4,598 independent complete-output comparisons.
  Chunk walkers and queries preserve their distinct traversal scopes and ordering.
- Slide-level inference and preparation: heading adjacency, horizontal-rule breaks,
  initial empty slides and bibliography headers. Verified by 549 original-Pandoc
  level results and 2,745 complete prepared-section results; presentation writers
  themselves remain pending.
- Image type detection and PNG/JPEG/EMF/WebP/AVIF header dimensions, preserving
  DPI metadata, Exif precedence, signed bounds and malformed-container behavior.
  Verified by 3,189 original type results and 2,559 size/failure results.
- SVG/EPS/PDF dimensions, including SVG units/viewBox and compressed PDF object
  streams. Unit conversion preserves GHC rounding, display and non-finite behavior.
  Verified by 2,695 additional original captures and direct arithmetic/attribute
  tests. Full raster decoding and writer/Lua integration remain pending.
- GIF metadata follows JuicyPixels' complete block parsing, including extensions,
  later frames, palette lengths and exact parser errors. Verified by 4,980 original
  Pandoc results, including all 12 retained upstream GIF files and Pandoc's GIF.
  Pixel decompression, animation and GIF writing remain pending.
- TIFF metadata follows JuicyPixels' IFD/Exif parsing and strict color-format
  checks, preserving byte order, cursor overlap, duplicate tags and DPI errors.
  Verified by 8,390 original outcomes, including all 68 retained TIFF images.
  TIFF pixel decompression/encoding and full imageSize dispatch remain pending.
- PNG fallback metadata preserves CRC checks, ignored IHDR fields, palette and
  zlib forcing, first pHYs resolution and compressed-text behavior. Verified by
  6,329 independent captures, including fallback probes of all 176 retained PNGs.
  One original unsafe memory read gets a bounded exception in Kotlin; pixel
  decoding/encoding and the complete imageSize dispatcher are still pending.
- Shared image bit readers/writers: JPEG marker handling, alignment and byte
  stuffing, LSB/MSB reads, GIF output and reader-state continuations. Verified by
  12,853 direct captures from the unmodified JuicyPixels Haskell module, including
  32-bit overflow, wide malformed fields and output buffer boundaries. This is a
  dependency for JPEG/GIF pixel codecs, which remain in progress.
- JPEG Huffman trees, packed decoding tables, inverse encoder codes, standard
  DC/AC and quantisation tables, quality scaling and macro-block construction.
  Verified against 5,450 direct original-Haskell outcomes, including every Int16
  coefficient at 13 quality settings and malformed table errors.
- JPEG block decoding helpers: integer IDCT, dequantisation, zig-zag ordering,
  coefficient decoding and pixel placement. Verified by 14,233 direct Haskell
  captures, including mutations and aliasing, all signed Int16 level shifts and
  malformed coefficient widths.
- JPEG binary structures: marker/header/table readers and writers, JFIF/Adobe
  records, restart intervals and both entropy-segment parsers. Verified against
  20,043 original Haskell outcomes, including malformed lengths, exact parser
  offsets and lazy-input chunk boundaries.
- Typed TIFF/Exif directories and binary serialization, including nested fields
  and JPEG APP1 parsing/writing. The existing TIFF metadata path now uses this
  shared parser. Verified by 16,177 original Haskell captures, including all
  65,536 tag codes, 68 original TIFF files and the Samsung JPEG metadata fixture.
- JPEG frame/image assembly: strict and legacy semi-lazy parsing, early header
  lookup, frame locations, all frame writers and macroblock debug printers.
  Verified by 9,819 original Haskell captures, including all 31 retained original
  JPEG photographs, truncations and deferred Huffman failures.
- Progressive JPEG unpacking: first/refined DC and AC coefficients, EOB runs,
  scan continuations, restart counters, subsampling and clipped pixel placement.
  All 8,754 independent original-Haskell cases match, including complete pixel
  arrays.
- Complete JPEG-to-pixel decoding for baseline, extended sequential and progressive
  DCT Huffman images, including frame state, table updates, restarts, component
  mapping and grayscale/YA/RGB/YCbCr/CMYK/YCCK finalization. Verified by 2,401
  independent original decodeJpeg captures; all 31 upstream JPEGs match the full
  127 MB of decoded samples. Another 195 original observations verify missing
  and late DHT tables: the original zero-table traversal remains interruptible,
  while explicit-table and empty-image controls match normally. Public image
  format dispatch remains outstanding.
- JPEG encoding for grayscale, YCbCr 4:2:0, RGB and CMYK, including fixed-point
  DCT, quantization, DC prediction, Huffman coding and complete frame output.
  Verified by 5,153 independent original captures: all Word8 quality values,
  edges, metadata frame integration and all 31 original JPEGs re-encoded at
  qualities 50, 90 and 100. Remaining cross-format test branches are still pending.
- The Float diagnostic referenceDct, preserving the original summation order,
  Float rounding and truncation. All 4,382,720 coefficients across 68,480 blocks
  match the original exactly in 3,200 captures, including every Int16 constant
  level, full-domain ramps, impulses, random blocks and preserved vector tails.
  Both exported functions of the original FastDct module are now implemented.
- RGB8 ↔ YCbCr8 pixel and image conversion, preserving original fixed-point
  coefficients, rounding, clipping, dimensions and unused source tails. All
  16,777,216 channel combinations in each direction match both original paths
  independently. The 1,467 captures also include 35 complete RGB → YCbCr → JPEG
  files, including the original white/black/gradient toJpg tests. Other color
  conversions and the remainder of Codec.Picture.Types are still pending.
- RGB8 ↔ CMYK8, RGB16 ↔ CMYK16 and YCbCrK8 → RGB8/CMYK8 pixel and image
  conversions. Original integer division, Word32-sized products, Float operation
  order, clipping and K-channel behavior are preserved. JPEG YCCK decoding now
  shares the same conversion. The independent 3,294 captures cover the complete
  RGB8 and Y/Cb/Cr domains, every CMYK8 component/K pair, full 16-bit component
  ranges at selected parameters, boundary products, all K values at boundary
  colors, image dimensions and unused tails. Other pixel operations and image
  codec integrations remain pending.
- All 21 original non-identity ColorConvertible pixel/image promotions, including
  8-to-16-bit scaling, grayscale/RGB replication, alpha preservation/addition,
  original YA → RGB alpha removal and exact Float normalization. Identity
  promotions/conversions preserve the original image and buffer. The 4,934
  independent captures cover complete scalar/YA8/RGB8 domains, 16-bit channel
  ranges, raw Float bits, dimensions, tails and original identity sharing. RGBF
  equality follows original IEEE behavior. Other pixel operations, image
  transformations and remaining codec test branches are still pending.
- Color-plane extraction for all 15 pixel types and all 32 named plane
  instances, four transparency instances, and ten luma instances. Original
  linear/row traversal, invalid-index errors, scalar image sharing, Double/Float
  luma precision and alpha handling are preserved. All 5,947 independent
  captures pass, including complete RGB luma domains, YA8 pairs, raw Float
  channel copies and the original YA8 plane-separation input. Other pixel/image
  operations and remaining codec integrations are still pending.
- Pixel component mixing/mapping/opacity and checked/unsafe pixel access for all
  15 original types, plus mutable image allocation, initialization and safe/unsafe
  freeze/thaw. The 15,022 independent captures verify callback routing (including
  YA8/YA16 alpha differences), Int64 address wrapping, exact bounds errors,
  partial writes, complete vector copies and buffer sharing. Remaining generic
  image APIs, vector views, generators and codec integrations are still pending.
- All ten PackeablePixel instances and packed fill/read/write/range operations,
  including shared component slices, rounded indices, native memory byte order,
  untouched tails and exact slice errors. All 17,149 original captures pass,
  comparing packed words and unpacked pixels separately, whole backing buffers
  and raw Float/NaN bits. Broader image representations/views, generators and
  remaining codec integrations are still pending.
- Generic raster images for all 15 pixel types, with Int64 dimensions and shared
  component views, generators, pixel maps, raster folds and component zip.
  Independent original captures cover every source/destination map pair, lazy
  data and callbacks, IO order, right association, logical bounds, partial writes,
  slice copies and Float equality. Integration of older image APIs with this
  representation remains pending.
- Applicative imagePixels/imageIPixels traversals for all source/destination
  pixel pairs, with deferred buffers, independent branched results and caller
  effects. The 35,670 original observations include eight Applicatives, IO error
  prefixes, left-associated Const effects, overflow and lazy Last folding.
  Remaining codec integrations are still pending.
- All 14 DynamicImage variants, generic dispatch/transforms/equality/forcing,
  Palette' for all 15 pixel types and all four paletted expansions. Original
  captures verify lazy payloads, shared palette buffers, fallback identity, ignored
  palette size, every unsigned byte index, raw Float bits and error ordering.
  GIF/PNG codec integration and older API migration remain pending.
- HDR gamma correction and tone mapping, preserving Float arithmetic, lazy
  brightness/parameters, row scans, complete vector tails and native powf rounding.
  Original macOS/ARM64 and Linux/x86-64 captures verify every finite output bit.
  Gamma uses system powf through JNA; document/image conversion remains Kotlin.
  Other native-math platforms and HDR codec integration remain unverified.
- Typed image metadata storage, ordered right-biased union, lookup/delete/folds,
  Word64 DPI conversions and complete JFIF/TIFF metadata transformations. JPEG
  encoding and lazy metadata decoding use these shared types; TIFF size/DPI
  extraction also uses the shared metadata implementation. Verified by 4,569
  original captures, including all 31 JPEGs re-encoded with metadata, the original
  JPEG metadataWriteTest, duplicate tags, ordering, byte packing and lazy errors.
  Full metadata integration in other codecs remains pending.
- Identifier generation, ASCII conversion and complete emoji lookup/replacement
  tables. Every Unicode scalar is checked against Pandoc in four identifier modes.
  Case conversion uses Haskell text's Unicode 16 tables, including full expansions,
  with independent lowercase/uppercase and format case-folding comparisons.
- TagSoup HTML tokenization, entities, positions, warnings and custom entity hooks.
  Preserves malformed-markup recovery, ordered attributes, script content and CDATA.
  Verified by original examples/properties and 3,670 independent Pandoc identifier
  captures; the complete HTML document reader remains pending.

See [the porting ledger](docs/PORTING.md) for the full remaining scope and caveats.
Pinned upstream sources and original tests live in Git submodules; preserving
them is **not** counted as implementing or passing them.

## Build

Requires JDK 21 and Python 3 for source-fixture verification.

```sh
git clone --recurse-submodules https://github.com/Heapy/kotdoc.git
cd kotdoc
python3 tools/import-pandoc-json-fixtures.py --check
python3 tools/import-native-fixtures.py --check-source
python3 tools/import-fb2-fixtures.py --check-source
python3 tools/import-fb2-writer-fixtures.py --check-source
python3 tools/import-resource-tables.py --check
python3 tools/import-media-fixtures.py --check-source
python3 tools/import-media-operations.py --check-source
python3 tools/import-extension-tables.py --check
python3 tools/import-extension-fixtures.py --check-source
python3 tools/import-option-fixtures.py --check-source
python3 tools/import-toc-fixtures.py --check-source
python3 tools/import-emoji-tables.py --check
python3 tools/import-case-mappings.py --check
python3 tools/import-text-212-lowercase.py --check
python3 tools/import-parser-state-fixtures.py --check-source
python3 tools/import-html-state-fixtures.py --check-source
python3 tools/import-uri-fixtures.py --check-source
python3 tools/import-uri-unit-tests.py --check
python3 tools/import-shared-inline-fixtures.py --check-source
python3 tools/import-identifier-fixtures.py --check-source
python3 tools/import-html-entities.py --check
python3 tools/import-tagsoup-fixtures.py --check
python3 tools/import-html-id-fixtures.py --check-source
python3 tools/import-chunk-fixtures.py --check-source
python3 tools/import-slide-fixtures.py --check-source
python3 tools/import-image-header-fixtures.py --check-source
python3 tools/import-image-dimension-fixtures.py --check-source
python3 tools/import-gif-metadata-fixtures.py --check-source
python3 tools/import-tiff-metadata-fixtures.py --check-source
python3 tools/import-png-metadata-fixtures.py --check-source
python3 tools/import-image-bitstream-fixtures.py --check-source
python3 tools/import-jpeg-table-fixtures.py --check-source
python3 tools/import-jpeg-block-fixtures.py --check-source
python3 tools/import-jpeg-binary-fixtures.py --check-source
python3 tools/import-tiff-binary-fixtures.py --check-source
./gradlew test :cli:installDist
python3 tools/check-fb2-cli.py
python3 tools/check-media-cli.py
cli/build/install/cli/bin/cli --from native --to json input.native --output output.json
cli/build/install/cli/bin/cli --from json --to native --standalone --columns 80 output.json
cli/build/install/cli/bin/cli --from fb2 --to native --standalone book.fb2
cli/build/install/cli/bin/cli --from fb2 --to fb2 book.fb2 --output converted.fb2
cli/build/install/cli/bin/cli --from fb2 --to json --extract-media media.zip book.fb2
```

The installed launcher is currently called `cli`. Use `-` or omit the input
filename to read standard input. Errors are written to standard error and return
a nonzero exit status. Input formats are `fb2`, `json` and `native`; output formats
are also `fb2`, `json` and `native`. `--extract-media` fills and extracts media,
then rewrites image links; a `.zip` destination creates an archive.
`--resource-path` accepts platform-separated directories and can be repeated;
later flags are searched first. General CLI options, logging, and complete
HTTP/URI dependency parity remain pending.

## Upstream baselines and licensing

| Source | Baseline | Pinned commit |
|---|---|---|
| Pandoc | 3.12.1 | `e51c9c6054c8f4ec5c3209d5abe10939dfe2963e` |
| pandoc-types | 1.23.1.2 | `b2cbcefc4547296f47b8120740997a9c021d756f` |
| calibre | 9.16.0 | `61c4027d2bfe737e0177e09952ee4942042a5892` |

Project license: GPL-3.0-only; see [LICENSE](LICENSE). The existing ported
`pandoc-types` model and JSON tests retain their BSD-3-Clause notices. Original
Pandoc sources are GPL-2.0-or-later; calibre has GPLv3 and per-file notices.
Upstream notices are retained in the source submodules and [licenses](licenses).
GHC decimal conversion retains the BSD notice in `licenses/GHC-base-BSD-3-Clause.txt`.
XML tokenization retains `licenses/xml-conduit-MIT.txt`. The full MIME table
retains `licenses/mime-types-MIT.txt`; source provenance is in `vendor/README.md`.
Media ZIP serialization retains `licenses/zip-archive-BSD-3-Clause.txt`.
Emoji support retains the complete dependency release and its BSD/GitHub MIT
notices. Haskell text case tables retain `licenses/text-BSD-2-Clause.txt`.
TagSoup retains its complete source release and `licenses/tagsoup-BSD-3-Clause.txt`.
Every subsequent port must preserve the applicable source attribution.
