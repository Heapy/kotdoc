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
  roundtrip tests. Current suite: 548,858 tests, no skipped tests.
- Pandoc builders, metadata helpers and both legacy/modern figure representations.
- Full Unicode raw-format case folding and 64-bit AST integers.
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
- MediaBag, complete MIME/URI tables, data URI decoding, local resource paths,
  and HTTP resource fetching. Verified with 2,172 independent MediaBag/MIME cases,
  18 data URI cases and a local HTTP integration test.
- Media filling and extraction into directories or deterministic ZIP archives,
  with image-link updates, missing-image placeholders and typed resource failures.
  All original Haskell MediaBag and Lua media API examples are translated into
  Kotlin API tests; this does not yet provide the Lua scripting engine.
- Data URI creation preserves upstream UTF-8, BOM, CR and MIME behavior.
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
  captures; this does not yet implement the HTML document reader or writer.

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
