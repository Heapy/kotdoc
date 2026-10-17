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
| GHC 9.12.2 getEnvironment/divvy and selected filesystem ROUNDTRIP decoding | `resources/GhcEnvironment.kt`, `GhcEnvironmentEncoding.kt` | 4,986 independent fixture groups per macOS/Linux, all byte pairs for 13 encodings, every Char through UTF8, live OS/JVM cache tests; complete GHC IO runtime and other host ABIs remain pending |
| `Network.HTTP.Proxy` Windows branch and selected Win32 registry wrappers | `http/HttpWindowsProxy.kt`, `HttpNativeWindowsRegistry.kt` | 2,179 complete-original Windows-CPP observations with ordered registry effects; actual Windows API test has a dedicated CI job; Manager/environment backend remain pending |
| `Network.HTTP.Proxy` common environment/non-Windows selection branch | `http/HttpProxyEnvironment.kt` | 4,460 independent complete-original observations, exact/folded names, lazy no_proxy, auth/fallback/Show; Windows registry and Manager integration remain pending |
| pandoc-types `Text/Pandoc/Definition.hs` AST constructors | `core/.../ast/Pandoc.kt` | All node variants represented; JSON tests |
| Definition derived Show/showsPrec | `ast/AstShow.kt` | All node/associated types; 36,970 native observations, 56 original values and 266 documents; deep/lazy boundaries |
| Definition Eq/Ord dictionaries | `ast/AstComparison.kt` | Complete represented AST/associated types, distinct source operators, 75,470 original observations, all 266 documents and 17 original generators |
| `Text.Pandoc.Logging` represented values and Verbosity instances | `logging/`, `parsing/Parsec.kt`, `ast/AstJsonValues.kt` | All 54 constructors, pretty/JSON/verbosity/Show/source Eq/Ord; 25,373 original observations, 266 ToJSON documents, raw names and 100,000-level structures |
| `Class.CommonState` record and `PandocMonad` state/logging operations | `state/CommonState.kt`, `state/PandocState.kt` | All 13 fields/defaults, persistent reverse log, callbacks/typed errors/nested silence, headers, URL, media and cache identity; 3,615 native observations plus snapshot/identity checks |
| Complete `Text.Pandoc.Translations` loading/cache operations and typed `toTextM` | `translations/TranslationLoading.kt`, `Translations.kt`, `resources/PandocText.kt` | All four module functions, exact Value FromJSON, full Lang cache equality, ordered fallback, typed/host errors and re-entrant effects; 77,429 original observations, all 107 YAML files and all 65,536 two-byte UTF8 inputs |
| `Text.Pandoc.Data` algorithms and reproducible timestamp helpers | `resources/PandocData.kt`, `PandocTime.kt`, `PandocFilePaths.kt` | Both embedded/installed branches on POSIX/Windows, actual 248-file TH catalog, three reference archives, user routing, enumeration and default directory selection; 5,663 independent original observations |
| TeXMath finite expression model, shared transformations, Unicode and TeX/Pandoc output | `math/` | All 23 Exp constructors, Show/Eq/Ord, all Shared functions, complete Unicode and TeX renderer, complete writePandoc algorithm; 3,146 native observations, 3,721 original inputs and exhaustive Char mapping |
| Complete finite TeXMath MathML writer and original XML.Light output | `math/MathMLWriter.kt`, `MathMLOperators.kt`, `MathNumbers.kt`, `xml/LegacyXml*.kt` | All 561 original MathML writer goldens, full 1,107-row dictionary, exact XML trees/serialization, every Char and numeric boundaries; 9,700 original observations |
| Complete finite TeXMath MathML reader and xml-1.3.14 legacy input/lexer | `math/MathMLReader.kt`, `xml/LegacyXmlInput.kt`, `LegacyXmlLexer.kt` | All 576 original MathML reader goldens plus regression 252; 18,743 independent observations, every entity/Char, namespace/line/lexical boundaries and 1,500-level finite inputs |
| Complete finite TeXMath expression-to-TeX writer | `math/MathTeXWriter.kt` | All three exports; 561 original writer goldens and four regressions; 32,292 original observations, all 561 round-trip results/verdicts (344 already fail upstream), package/Unicode/scope/array/space and deep-input coverage |
| Complete finite TeXMath macro preprocessing and Unicode-to-TeX conversion | `math/MathMacros.kt`, `MathTeXUnicode.kt`, `MathTeXUnicodeData.kt` | All macro constructors, original Parsec replies/errors, bounded recursive expansion and all 2,755 ordered records/2,832 commands; 26,415 original observations, 149 original TeX inputs and exhaustive Char conversion |
| Complete finite TeXMath TeX formula reader and Commands | `math/MathTeXReader.kt`, `MathTeXCommands*.kt`, `MathTeXReaderData.kt` | All reader productions and six command tables, exact trees/Show/errors, all 69 original native goldens; 25,581 independent original observations and every Char through readTeX |
| Shared metadata/space/code helpers and finite AST traversal | `ast/InlineShared.kt`, `DocumentShared.kt`, `PandocVisitor.kt` | addMetaField, extractSpaces, formatCode and htmlSpanLikeElements; 19,643 native observations, all 266 documents/10,584 inline lists, original stringify property/generators, every Char and 100,000-level finite trees |
| Network.URI materialized finite runtime operations | `uri/NetworkURI.kt`, `URIParsing.kt`, `URI.kt`, `resources/MediaBagUri.kt` | Full parsing/validation, UTF8 escaping/decoding, normalization, relative resolution and deprecated accessors; 95,464 native observations, all 339 active original unit tests and both translated property predicates |
| Complete HTML table parser and simple-cell query | `html/HtmlTableParsing.kt`, `ast/TableSimplicity.kt` | Full pTable traversal/normalization and caller state/effects; 6,539 independent original observations, all 266 Native documents, 42 HTML/HTML4/HTML5 table spans and all Char numeric-prefix/remainder witnesses |
| Complete finite HTML document grammar and public Sources APIs | `html/HtmlReader.kt`, `HtmlSourceParsing.kt` | All 69 original units, original reader golden, all eight HTML files and 173 explicit command inputs; full AST/error/report comparisons, original roundTrip predicate and actual QuickCheck witnesses; CLI transport/application integration remains pending |
| General Smart punctuation, TagSoup rendering and strict Pandoc UTF8.toText | `parsing/SmartParsing.kt`, `html/HtmlRendering.kt`, `resources/PandocUtf8.kt` | Direct original parsers with exact quote/last-string state, errors and Sources positions; original render/canonicalization behavior; every UTF-8 byte pair, every encoded Haskell Char, host UnicodeException ordering and iframe integration |
| Complete HTML reader tag-parsing/category modules | `html/HtmlTagParsing.kt`, `HtmlTagCategories.kt`, `HtmlTagMatching.kt`, `parsing/TokenParsec.kt`, `ParserMachine.kt` | All 14 Parsing functions, TagOmission and eight category values; 66,067 independent native observations, all 69 original HTML inputs/legacy input at the token boundary, exact consumed/empty state/errors and every Char for whitespace/Tag Show |
| Complete `Text.Pandoc.Parsing.Math`, reader-option getter/guards and `Shared.trimMath` | `parsing/MathParsing.kt`, `ReaderOptionsAccess.kt`, `Parsec.kt` | Both math parsers, all delimiters/groups/comments, five original stream instances, exact state/errors and raw/Text error views; 10,304 independent observations including all 48 HTML and 138 Markdown math inputs |
| Complete `Text.Pandoc.Writers.Math` bridge | `math/PandocMath.kt` | Both conversion functions and URL re-exports; exact AST/fallback, reporting, re-entrant state, deferred/shared generic writer results; 98,180 native observations over all 335 original Pandoc math nodes and 149 TeX inputs |
| libyaml byte reader and yaml scalar schema/string-style helpers | `yaml/YamlReader.kt`, `yaml/YamlScalars.kt` | Complete defined reader transitions; 82,635 native cases, every UTF8 code point/UTF16 unit, all 65,536 byte pairs, actual buffers/errors, source numeric overflow and all scalar styles/tags |
| Complete libyaml scanner algorithm | `yaml/YamlScanner.kt`, `YamlTokens.kt` | All 22 token kinds including terminal empty token; 8,641 native token/error/per-call state observations over directives, keys, indentation, tags, anchors, all scalar forms, encodings and original source inputs |
| Complete libyaml syntax parser algorithm | `yaml/YamlParser.kt`, `YamlEvents.kt` | All 24 state dispatcher cases, directives/tag resolution, empty nodes and collection productions; 10,286 native event/error/per-call stack and peek-state observations |
| yaml event-to-Value decoder and parse-exception pretty printing | `yaml/YamlValues.kt` | Demand-driven documents/anchors/merges, raw scalar keys, duplicate paths/order, all 11 pretty-error cases and Warning Eq/Show; 23,105 independent Haskell observations plus ownership, identity and conversion-order checks |
| aeson-pretty defined-value library and numeric formatting dependency | `json/PrettyJson.kt`, `PrettySort.kt`, `ScientificNumber.kt` | All configuration modes, Aeson Text quoting, source natural/fused sorting and Scientific formatting; 21,522 original observations, 266 documents, 1,027 source hashes |
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
| LaTeX tokenizer, token stream, complete state record and independent parsing/scoping helpers | `latex/LaTeXTokens.kt`, `LaTeXState.kt`, `LaTeXParsing.kt` | 8,973 original observations; all 90 reader unit inputs and 14 LaTeX files, every Haskell Char, all 30 fields/defaults and 100,000-step token/scoping/macro-stack witnesses; full reader remains pending |
| LaTeX token grammar, macro expansion, evaluators and Sources bridges | `latex/LaTeXKernel.kt`, `LaTeXEvaluators.kt`, `LaTeXParsingBridge.kt` | 34,080 complete-original-module observations, both native numeric platforms, argument/default/xparse processors, conditionals, raw capture, scopes, global reports, exact errors and 100,000-token witnesses; full LaTeX reader/command-definition assertions remain pending |
| LaTeX macro-definition module | `latex/LaTeXMacros.kt`, `LaTeXState.kt` | Complete finite Macro.hs algorithm; 1,553 original-module observations, all 33 definition commands, every Haskell Char through letterize, all 127 matching command inputs/90 reader unit inputs through the token component, and 100,000-level global scope/definition streams; full reader/CLI AST assertions remain pending |
| LaTeX Citation and Math modules | `latex/LaTeXCitations.kt`, `LaTeXMathReader.kt` | Complete finite module algorithms; 22,557 original observations, all 52 citation commands/23 math environments, theorem/proof styles, labels, numbering, callback state/errors/reports, all 266 original AST documents and 100,000-level finite witnesses; complete reader/CLI assertions remain pending |
| LaTeX Lang module | `latex/LaTeXLanguages.kt` | Complete finite language/quote algorithms; 30,431 original observations, all 85 Polyglossia mappings, Babel aliases, six quote/86 language commands, parser/common-state/cache/backtracking semantics, all 10,341 original inline lists and 100,000-level composition witnesses; complete reader/CLI assertions remain pending |
| LaTeX SIunitx module | `latex/LaTeXSIunitx.kt` | Complete finite module algorithms; 34,007 original observations, all 12 commands/21 modifiers/152 unit keys, numeric values/errors, unit exponents/infix/options, all 10,341 original inline lists, every Haskell Char and deep finite inputs; full reader/CLI assertions remain pending |
| LaTeX Inline module and pinned NFC dependency | `latex/LaTeXInlines.kt`, `CanonicalNormalization.kt` | All eight command registries/210 entries, original state/effects/errors and complete finite NFC path; 188,741 independent observations, 94,980 original NFC predicates, all Char/accent pairs, pinned Unicode tables and all 107 translations; full reader/CLI assertions remain pending |
| LaTeX Table module and Shared.compactifyTable | `latex/LaTeXTables.kt`, `LaTeXTableRows.kt`, `ast/TableCompaction.kt` | All seven environments and finite module algorithms; 95,246 independent observations, ten AST Walkable shapes and Builder.Many Block, all original AST trees, row repair, parser state/errors and deep finite cases; full reader/CLI assertions remain pending |
| Complete LaTeX document grammar and public raw Sources APIs | `latex/LaTeXReader.kt`, `LaTeXReaderText.kt`, `cli/LaTeXCliReader.kt` | All 90 untouched original unit assertions, both fixed reader goldens, all 362 matching command inputs at the AST boundary, exact errors/common state; 1,454 independent original observations and installed CLI checks |
| LaTeX date, pinned text case and input-tab dependencies | `resources/PandocGregorian.kt`, `latex/LaTeXReaderText.kt`, `ast/SharedText.kt` | All Haskell Chars through text-2.1.2 upper/lower/title, four complete Gregorian cycles, unbounded dates/leap-second/timezone boundaries, SOURCE_DATE_EPOCH and 72 original tabFilter observations; full time-library port remains pending |
| Shared ASCII whitespace and text trimming | `ast/SharedText.kt` | isWS, trim, triml, trimr and stripTrailingNewlines; 119 independent observations including every Haskell Char, 13,369,344 text transformations and nine reproduced/fixed Table error regressions |
| Complete finite `Text.Pandoc.URI` and incremental Text parser dependency | `uri/PandocURI.kt`, `parsing/attoparsec/` | All six exports, prefix/EOF and chunked parsing, independent deferred MIME/bytes, pinned scheme set, POSIX/Windows paths; 40,705 original observations, every Haskell Char and 100,000-deep parser/chunk feeds |
| Resource fetching foundation | `resources/ResourceUri.kt`, `ResourceAccess.kt` | 18 original data URI cases, source SVG fetch, local search paths, bag precedence and local HTTP integration |
| `fillMediaBag`, `extractMedia`, `writeMedia` and Lua media API operations | `resources/MediaOperations.kt` | Every original Haskell MediaBag and Lua media API example translated to Kotlin; independent extraction/AST/report oracles and recoverable/fatal error checks |
| `SelfContained.makeDataURI` | `resources/ResourceUri.kt` | 56 independent MIME/UTF-8/BOM/CR/binary cases, including original rejection of invalid text UTF-8 |
| zip-archive media serialization dependency | `resources/MediaZip.kt` | Complete ZIP-byte comparison against Pandoc (headers, CRC, compression, order, collisions); independent JDK decompression |
| zip-archive general record/binary codec and pure entry operations | `archive/ZipArchive.kt`, `templates/PartialPaths.kt` | Complete local/central/signature/end productions, record metadata, DOS dates, filename encoding, descriptors and PKWARE; 76,920 native observations, all 309 original archives and every Haskell Char in POSIX/Windows paths |
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
   binary. The current CLI supports JSON/Native/FB2/LaTeX input and
   JSON/Native/FB2/HTML/slide output, with a subset of application options.
   The complete Pandoc CLI contract remains pending.
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

Continue the remaining reader/writer/application suites against the pinned
original tests. The complete LaTeX grammar and public raw Sources entry points
are now implemented; extend their original command assertions through complete
writers/application behavior. The finite HTML document grammar and original reader
assertions now reuse the HTML token/state/table and raw LaTeX foundations.
Connect its CLI URL transport with the original IO.HTTP/PandocMonad behavior
and continue the remaining command/application assertions. Remaining TeXMath reader/writer tests,
dependency partial-value contracts and original property generators also remain
part of the full port. Preserve exact options, state and error behavior as each
format is connected.

Continue the remaining PandocMonad and HTTP/URI/XML infrastructure, complete
CLI App.Opt/application behavior, scripting/filter/bibliography paths and the
remaining pandoc-types generators/shrinkers and original property suites. The
complete library WriterOptions record, syntax/highlighting/template dependencies,
translation loading and standard data/reference archive algorithms are now
implemented; this does not make the separate application/CLI layer complete.

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
newline and eight-column tab rules. The Sources section below adds chunked
streams, four-column tabs, source-file position jumps and both ByteString
instances to these same list algorithms. General Parsec lazy reply/state APIs
and the complete ParserState remain pending.

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


## Sources, character streams and parser positions

Sources translates the list of (SourcePos, Text) tuples without flattening the
input or discarding committed empty chunks. SourceChunk tuple fields and
DeferredChain elements/tails can be demanded independently. uncons skips leading
empty chunks only when it finds a character, preserves the current tuple's
stored position and retains its now-empty Text after consuming the last character.
Text slices share backing storage while tokens are consumed. Sources append and
mconcat preserve lazy right operands and support finite observations of infinite
chains; empty is the original empty list, distinct from a singleton empty Text.

ToSources dictionaries cover Text, raw [Char], file/text pairs and identity.
Raw character arrays distinguish surrogate Chars from supplementary scalars;
packing to Text applies the retained Text scalar rules. fromString/toSources
remove CR. File inputs append LF even if already newline-terminated. The actual
Sources constructor retains CR and all stored positions unchanged. initialSourceName
reads the first tuple, including an empty Text; sourcesToText ignores positions.
ensureFinalNewlines examines only the last Text, retains untouched tuple fields,
returns the original source when sufficiently terminated, and handles empty input
and nonpositive counts using the original rules. Derived Show/showsPrec, Semigroup
and Monoid observations are retained.

CharacterStream also supports strict Text, raw [Char], strict ByteString and lazy
ByteString. Bytes become unsigned Char values, not UTF-8 decoded text. The lazy
byte constructor removes empty chunks, and consumption normalizes exhausted
chunks as in ByteString.Lazy.Char8. UpdateSourcePos dictionaries for ordinary
streams ignore the remaining input and use Parsec's eight-column tabs. Sources
uses four-column tabs, except the original Sources [] special case. The remaining
stream's two-tuple pattern is inspected before Text.null; an empty current chunk
jumps to the next stored position, ignoring the old position/character when not
needed. Deferred dictionaries retain that demand behavior.

SourceParsers covers satisfy, oneOf, noneOf, anyChar, char, string, newline,
space, spaces, letter, alphaNum, digit and hexDigit. Failure displays a Char and
adds no expectation label, except spaces' original white space label. string
consumes characters incrementally, including source-position jumps and mismatch
errors. takeWhileP/takeWhile1P follow the original whole-chunk span algorithm,
return the corresponding consumed/empty status and retain the stop error, even
on success at EOF. addToSources strips CR, inserts a positioned chunk and stores
the current parser position on the old first chunk. addToInput retains CR and
positions; its list/tuple pattern is demanded immediately, while its Text and
position fields remain deferred. Both preserve user state and parser position.

ParseInput preserves its existing String/index API and additionally carries a
persistent CharacterStream. The existing PandocTextCharacters/list-marker
algorithms use the remaining stream's position rule. Original Parsec.Char
primitives continue to use their original position updater. This change adds no
new CLI format and does not substitute a reduced ParserState for the complete
31-field source record.

The frozen tools/haskell-sources-reference compiles the unchanged upstream
Sources.hs and Parsec 3.1.18.0. Its list integration compiles the unchanged
Lists.hs with the previously retained two-field test-only ParserState projection;
that projection is not a full-state implementation. No local Sources module or
reconstructed conversion function is used as an oracle.

The 154,295 native observations include all exports/instances and derived Show,
all five stream families, retained empty boundaries, four/eight-column tabs,
CR/LF, supplementary/surrogate characters, signed Int64 position overflow,
source names and positions moving backward, predicate failure, incremental
string mismatch, labels, many/many1, lookAhead, try/choice, EOF, input expansion
and preserved user state. All 1,114,112 GHC Chars are classified independently,
with 3,867 interval boundaries also exercised through character parsers. Every
byte value is exercised in both byte streams. Corpus inputs include 1,177 original
upstream text documents, observed through toSources, rather than claimed as full
reader-test coverage. Thirty-one controlled partial/infinite observations cover
ignored fields, required failures and a 10,000-character infinite prefix.
Four hundred native list observations cross positioned/empty chunk boundaries.
Seeded compositions and deep Sources/string/span/ensure operations reach 100,000
fragments. One host check verifies immutable snapshots and caller-state identity.

Case digest:
`1b8a7f97e8a8a7e1bbb39e2fade3777898d38dbddc03d58a718869637d8eaf12`.
The importer checks 1,252 retained source/input/adapter/license hashes, document
provenance, the complete corpus digest and operation inventory. Reproduce with
cabal build --project-dir=tools/haskell-sources-reference
--builddir=/tmp/kotdoc-sources-build, then
python3 tools/import-sources-fixtures.py --oracle PATH.
CI checks this corpus without requiring Haskell at runtime.

There is no dedicated Sources suite in the pinned upstream Tests tree. Complete
consumer suites for Markdown, RST, LaTeX and other readers remain pending, along
with the full ParserState, typed TeX and Logging dependencies and their capabilities.
General Parsec lazy reply/state boundaries, arbitrary raw source-name Char lists,
and the rest of the upstream runtime are not claimed complete by these finite
observations and controlled partial/infinite checks. Full Pandoc implementation
and all its tests, followed by calibre MOBI/additional formats and all their tests,
remain the unchanged objective. All calibre implementation remains unstarted.


Local validation: all 953,949 tests pass across 77 suites, with zero failures,
errors or skips. The full suite was repeated after changing finite chain storage
so consumed prefixes are not retained by list-snapshot closures. All 80 source/
fixture checks pass. All 234 installed-CLI comparisons pass (14 FB2, 66 media,
69 extensions and 85 math cases). The final JAR includes Sources/SourceParsers,
all stream classes and all 40 original license files verbatim.

All 1,252 new source/input hashes and the compressed corpus match staged Git bytes
and pinned submodule commits. Existing oracle folders and expectations remain
unchanged. The repository remains public; exact-SHA CI checks the full source,
Kotlin and installed-CLI suites after publication.


## TeX token and macro data

TeX.kt translates every constructor in the complete Text.Pandoc.TeX module:
TokType (CtrlSeq, Spaces, Newline, Symbol, Word, Comment, Esc1, Esc2, Arg and
DeferredArg), Tok, ExpansionPoint, MacroScope, Macro and all nine ArgSpec forms
(ArgNum, Pattern, BoolArg, DelimArg, VerbArg, EmbellishArg, ProcessedArg, BodyArg
and VerbBodyArg). SourcePos is reexported as a Kotlin typealias. Every original
field retains its type, order and signed Int64 number semantics. Defaults keep
Nothing distinct from Just []; processor order, embellishment pairs and
replacement token order are preserved. These are real typed values for the
pending full ParserState's Macro map, not Any/placeholders or a test-program AST.

Tok and TokType implement derived Eq/Ord in original constructor/field order,
including all seven Eq/Ord relations. Constructor differences and earlier token
fields short-circuit undefined later fields. Text order is Unicode scalar order,
not Java UTF-16 code-unit order. There is no identity shortcut that would allow
undefined equal fields to escape demand. Macro and ArgSpec derive Show only;
no structural Eq/Ord instance for them is introduced. Kotlin hash methods for
Tok/TokType maintain their ordinary host equality contract.

All constructor fields, token/argument/processor/embellishment list spines and
Maybe payloads can remain deferred independently. Convenience finite constructors
snapshot caller lists. TeXShow supplies an iterative, lazy showsPrec stream,
full Show output and scalar-counted prefixes. Constructor applications,
negative-number precedence, quoted Text, Bool, lists, tuples and Maybe syntax
match original derived Show. SourcePos retains its original unescaped source-name
Show behavior. Prefixes can stop before undefined fields/list elements, or read
finite projections of infinite ProcessedArg/token-list values. Fully rendering
infinite values is not represented as a finite operation.

The original ToSources [Tok] instance groups consecutive source names, retains
each group's first position and concatenates its token Text exactly. Repeated
names separated by another source remain separate groups; empty text groups,
CR and LF are retained. Token types are ignored. Group splitting, tuple fields
and tails are deferred, so reading the first name can ignore later tokens and
text, and reading singleton text can ignore its position. Shared split results
avoid repeated grouping. Deep groups and Show values use iterative evaluation.

Native partial-field checks exposed an existing sourcesToText demand-order bug:
Text.mconcat is Text.concat, whose []/[t] pattern inspects the list tail before
reading the first Text. With one TeX group this requires finding its boundary
before reading token text. The production Sources helper now retains that
singleton lookahead; the TeX group Text helper follows the same rule. Original
cases remain unchanged, and direct group-text, plain Sources and exception-priority
regressions were added to the new corpus. The complete Text 2.1.2 source release
(119 files, including its original tests) is retained verbatim for this exact
native dependency behavior. Its LICENSE matches the existing Text notice.
This is not a claim of completion for the entire Text library and test suite.

The frozen tools/haskell-tex-reference compiles unchanged TeX.hs and Sources.hs
from pinned Pandoc 3.12.1 with original Text 2.1.2/Parsec 3.1.18.0 and GHC 9.12.2.
No module is shadowed or reconstructed to generate expected behavior. The 21,326
native observations cover constructor ordinals, every Eq/Ord relation, all Show
constructors and precedence boundaries, every optional/list/processor form,
Unicode ordering, control/numeric escapes, surrogate packing, Int64 boundaries,
source grouping, retained empty groups and CR/LF, ignored/required fields,
scalar-counted Show prefixes, infinite projections, and deep Show/group operations
through 100,000 nodes/tokens. Five original TeX documents are transported as
Text fields and ToSources inputs; this is not their reader-tokenization coverage.
One host check verifies caller snapshots, typed field access and hash contracts.

Case digest:
`f2321797c8a99b7401df92ed1b6f62c760f6cf36c51e9e6a39695e80a895007e`.
The importer verifies 189 source/input/adapter/license hashes, all document
provenance, operation inventory and case digest. Reproduce using
cabal build --project-dir=tools/haskell-tex-reference --builddir=/tmp/kotdoc-tex-build,
then python3 tools/import-tex-fixtures.py --oracle PATH. CI checks the retained
corpus without requiring a Haskell runtime.

There is no dedicated TeX data-module unit suite in the pinned Tests tree. The
complete LaTeX/ConTeXt consumer suites, tokenization, macro expansion, xparse
argument grabbing/processing and HasMacros capabilities remain pending. Full
ParserState still needs Logging/LogMessage and its other concrete capabilities.
General raw/lazy SourcePos name and Parsec reply/state boundaries retain their
previous pending status. No CLI format is registered by this data-module change.
Complete Pandoc and all its tests, followed by calibre MOBI and additional formats
missing from Pandoc and all their tests, remain the unchanged goal. All calibre
implementation remains unstarted.


Local validation: all 975,276 tests pass across 78 suites, with zero failures,
errors or skips. All 21,326 native TeX/Sources observations and the host snapshot/
hash check pass, including both initially failing demand-order regressions. The
existing 154,295 Sources expectations remain unchanged and pass after the shared
sourcesToText correction. All 81 source/fixture checks and all 234 installed-CLI
comparisons pass (14 FB2, 66 media, 69 extensions, 85 math cases). The final JAR
contains all TeX classes and all 40 original license files verbatim.

All 189 new source/input hashes, the compressed corpus and all 119 complete Text
release files match staged Git bytes and the pinned submodule commit. The older
native oracle folders and expectations remain unchanged. Original release bytes
and whitespace are retained by scoped attributes. The preceding Sources head
7f09e63832c3b5ebb9cb82e2be3b23c617ad80f4 passed all 92 exact-SHA CI steps. This
TeX milestone 6eb6301d5f0d17df8c98acd25935d8d944ff907a passed all 93
exact-SHA CI steps: https://github.com/Heapy/kotdoc/actions/runs/38613393759.
The full port remains in progress.


## Definition AST Show/showsPrec

AstShow ports the actual derived Show instances of the pinned pandoc-types
1.23.1.2 Definition.hs, independently of Native writer layout. Every Inline,
Block, MetaValue and associated type is represented, including Meta's record,
Citation's exact record field order, Attr/Target/ListAttributes/ColSpec tuples,
Format's stored spelling, RowSpan/ColSpan/RowHeadColumns wrappers, optional short
captions and ascending Unicode Text map keys. Application precedence, signed
Int64/Double precedence, signed zero, infinities, NaN and original escaping are
preserved. Explicit tasks render deep nodes without consuming the JVM stack.
Lists are demanded incrementally through iterators; finite prefixes of infinite
nodes/lists and partial list elements/tails are checked against original Haskell.
This does not introduce a full lazy GHC runtime or change the AST's StrictData
contract, and does not claim general lazy Map-value or raw/lazy Text coverage.

The new frozen tools/haskell-ast-show-reference links unchanged local original
pandoc-types with GHC 9.12.2 and Text 2.1.2. Its OriginalExamples.hs preserves the
56 literal upstream example value definitions verbatim; the original QQ module
is compiled directly. The importer checks the excerpt and hashes the whole
pinned package, including Arbitrary and all its original tests. Expected Show
strings come from GHC's original instances, never Kotlin or the Native writer.
All 266 accepted original Native documents use their independently retained
JSON inputs; all rejected source files are also retained in the hash inventory.
These Show observations supplement the existing JSON/reader tests, rather than
standing in for all original pandoc-types tests.

There are 36,970 observations: 12,763 full Show cases, 7,590 IEEE bit-pattern
Double cases, 13,872 code-point Text.pack cases (including every surrogate),
2,000 finite prefixes, 721 partial/infinite projections and 24 deep observations.
Deep Inline, Block, MetaValue and mixed Note/Plain structures reach 100,000
nesting steps. All 56 original example values are observed at six precedences.

Case digest:
`527ea90d3106764270b0181b33bce0eb9ee6c36fa82cf900008ce15fb7ea2649`.
The importer checks 422 source/input/harness/license hashes, document provenance,
operation inventory and case digest. Reproduce with the commands in
tools/haskell-ast-show-reference/README.md; CI checks the retained corpus without
requiring Haskell. The existing 40 packaged license notices remain sufficient
and unchanged.

AST source Eq/Ord dictionaries (including Citation's hash-only ordering and
Double's non-total relations), full Read/property-generator contracts, Logging
and full ParserState remain pending. No CLI format is registered by this change.
Complete Pandoc and all its tests, then calibre MOBI and all additional formats
missing from Pandoc and all their tests, remain the full objective. All calibre
implementation remains unstarted.

The next Ord implementation has been checked against GHC 9.12.2's actual derived
instance dump from the unchanged source package. MetaValue, Inline and Block
provide compare only; Pandoc, Caption, table types and ColWidth provide compare
and a distinct less-than implementation. ColWidth therefore inherits negated
less-than defaults for <=/>=, rather than the raw Double operators. The dump
is a development observation, not a retained or passing Kotlin Ord corpus.
These differences must be tested through nested ASTs before Logging can rely on
source comparisons. Citation still has its handwritten hash-only compare.

A native smoke probe also confirms that a NaN ColWidth compares GT with itself,
while its < and > are false and <=/>= are true. A list of ColSpecs containing
that width, and a Table containing that list, instead have both < and > true
and <=/>= false. Two Citations with different content and the same hash have
Eq false and compare EQ. A Pandoc whose first metadata field compares GT can
short-circuit its separate < implementation before reaching its block list.
These observations define required cases for the forthcoming independent AST
comparison corpus; they are not counted as implemented Kotlin comparisons.

Local validation: all 1,012,246 tests pass in 79 suites, with zero failures,
errors or skips. All 36,970 new original AST Show observations pass. All 82
source/fixture checks and all 234 installed CLI comparisons pass (14 FB2, 66
media, 69 extensions, 85 math). The final JAR contains AstShow and every one of
the existing 40 license files verbatim. All 422 source/input hashes and the
compressed corpus were verified against staged Git bytes and pinned submodule
objects. The old oracle folders and corpora remain unchanged.


## Definition AST equality and ordering dictionaries

AstComparison provides separate typed source Eq/Ord dictionaries for every
represented Definition AST and associated type. This preserves existing Kotlin
data-class contracts while allowing ported consumers to use the actual source
instances. Inline/Block/MetaValue constructor order and every field order are
retained. Citation Eq includes all fields, but Ord compares only citationHash.
Format uses its existing complete Text case-fold dictionary. Numeric newtypes
use Int64, and tuple wrappers retain actual tuple instances.

Each relational operator follows its own GHC 9.12.2 dictionary, rather than
reducing all results to the sign of compare. MetaValue/Inline/Block, Map, list
and Citation have compare-only defaults. Stock small records, tuples, Maybe and
ColWidth derive a separate <, then <=/>/>= follow the generated defaults. Raw
Double retains IEEE comparisons. Min/max use the original <= selection and
return caller objects, retaining stored Format spelling, Citation content and
IEEE payloads. NaN, infinity, signed zero and subnormal behavior is checked
through 18 direct/nested contexts, including Caption's short and long forms,
every row/table type, metadata and Pandoc's distinct prefix/last-field paths.

Explicit continuations keep deep comparison off the JVM stack and preserve
short-circuit results across nested dictionaries. Lists are inspected through
iterators without asking their size or demanding an unreachable tail. Map Eq
checks size first, then ascending key/value pairs; Ord compares ascending pairs
without a size shortcut. Lazy value suppliers are forced only after their key
has been compared. There is no object-identity equality shortcut. Partial tests
honor actual StrictData constructors and use failing list heads/tails or lazy
Map values, including ignored and required fields and self-comparison. General
lazy/partial Text, tuple fields and arbitrary GHC runtime emulation remain
outside this represented-model evidence.

The new frozen tools/haskell-ast-order-reference links the unchanged local
pandoc-types 1.23.1.2 package with GHC 9.12.2, Text 2.1.2 and QuickCheck 2.16.0.0.
No source instance or module is shadowed or reconstructed. It calls the actual
Eq/Ord methods, min/max and source Show. Original Arbitrary is imported directly:
17 generators each supply 80 reproducible pairs. These native observations
supplement the property suite; they do not claim that all generator/shrinker or
all original property contracts have been translated to Kotlin. Every accepted
original Native document is checked against itself, a neighboring document and
an empty document, while all rejected source files remain in the hash inventory.

There are 75,470 observations: 36,821 complete relation/extrema sets, 112 list
sets, 23,832 IEEE context sets, 14,517 code-point Text sets, 133 partial operator
probes, 48 deep comparisons and seven finite unequal projections of infinite
lists. Deep Inline, Block, MetaValue and mixed Note/Plain structures reach
100,000 nesting steps. Original width bits from actual selected min/max results
prove which NaN or signed-zero payload survived even if Show strings coincide.

Case digest:
`abc0a609c77905f61c5299e128d634a39df75e9f21cf136dab4271ee45a27180`.
The importer checks 426 source/input/harness/license hashes, operation and
generator inventories, full document provenance and the case digest. CI uses
these retained expectations without needing a Haskell runtime. New original
GHC.Classes and Maybe sources are retained verbatim in vendor/ghc-ast-order-9.12.2
with both complete original notices. The ghc-internal notice matches the existing
GHC-base notice; ghc-prim's distinct complete notice adds the 41st JAR license.
Containers Map source and every old oracle folder/corpus remain unchanged.

The previous Show commit cbb75b93be690480f29467210bdaaef8a1cb5e91 passed all
94 exact-SHA CI steps: https://github.com/Heapy/kotdoc/actions/runs/38618274246.
Full Read and original property/generator contracts, consumer integration,
Logging and full ParserState remain pending. This does not register a new CLI
format or complete pandoc-types/Pandoc. The full objective remains all Pandoc
and its tests, then calibre MOBI and all additional formats missing from Pandoc
and all their tests. All calibre implementation remains unstarted.

Local validation: all 1,087,716 tests pass in 80 suites, with zero failures,
errors or skips. All 75,470 new native comparison observations pass, including
the extended 18-context IEEE matrix. All 83 source/fixture checks and all 234
installed CLI comparisons pass (14 FB2, 66 media, 69 extensions, 85 math). The
final JAR contains AstComparison and all 41 license files verbatim. Every one
of the 426 source/input hashes and the compressed corpus matches staged Git
bytes and pinned submodule objects. Old oracle folders and expectations remain
unchanged.

Next Logging dependency inspection: the pinned Pandoc constraint is
aeson-pretty >=0.8.9 && <0.9. The frozen Hackage index offers 0.8.11. Its complete
release archive was downloaded for inspection (SHA-256
`d25ce5aa8e447d64dce5b721dfc3f9563ab534cb4388e959645bcf03e23b156e`); it has
no dedicated test suite in the release manifest. The actual keyOrder comparator
considers every unlisted key equal and keeps its initial order, rather than
performing a fallback lexical comparison. With frozen Aeson ordered-keymap,
that initial order comes from the original KeyMap. This must be preserved in
Logging's byte-exact pretty output. The complete release and its source dependencies are now retained and the
defined-value formatting surface is translated below. Logging remains pending.


## aeson-pretty and Scientific formatting for Logging

The pinned Pandoc dependency accepts aeson-pretty 0.8.11. Its complete release
(all seven regular files), Scientific 0.3.9.0 (all eleven regular files, including
the original tests), and Aeson 2.2.3.0 (all 866 regular files, including its test
corpus) are retained unchanged in new vendor directories. Archive SHA-256 values
are recorded in their KOTDOC-PROVENANCE.md files. The original GHC 9.12.2
OldList.hs and its full notice are retained separately; the existing pinned
Text 2.1.2 and GHC numeric source trees are also included in the new manifest.
None of the previously hashed source directories or fixture corpora was changed.
Scientific and aeson-pretty add two original notices; the core JAR now contains
43 complete notices, verified byte for byte.

`PrettyValue` represents Aeson Values independently of kotlinx serialization's
number printing. Numbers retain arbitrary-precision coefficients and signed
64-bit decimal exponents, including the original stored scale delivered to
custom callbacks. `ScientificNumber.fromDouble` uses the already translated GHC
shortest-digit algorithm and Scientific's normalization; both signed zeros
become Scientific zero. NaN maps to null and infinities to the original strings.
This adapter is independently checked on 3,019 IEEE bit patterns, including
subnormals, extrema, signed zeros, infinities, NaNs and the GHC 1e23 boundary.

`PrettyJson` implements the defined-value pretty configuration: Spaces (including
negative values and the special whitespace-free Spaces 0), Tab, trailing newline,
arbitrary key comparison, Generic/Scientific/Decimal number modes and custom
numeric text. Custom output is preserved even when it is not valid JSON. The
input KeyMap enumeration uses scalar Text order as in the frozen ordered-keymap
backend; keyOrder gives every unknown key the same rank and uses the first
occurrence of duplicate requested keys. The GHC natural-run/four-way sort is
translated explicitly. It retains the trailing empty run from sequences [] and
the fused merge3/merge4 exhausted-list transitions; these matter for nontransitive
comparators and cannot be replaced by Java TimSort or nested pair merges.

Aeson.Text quoting uses lower-case four-digit escapes for backspace/formfeed,
short escapes only for tab/newline/carriage return, and leaves slash, U+2028,
U+2029 and non-ASCII scalars intact. Scientific formatting includes all three
FPFormat modes, optional decimal precision, half-even ties, carry propagation,
trailing-zero normalization, original Int64 arithmetic, and Generic's strict
1e19 cutoff. Full output and chunk APIs share virtual zero runs. The iterative
value renderer and iterative kotlinx JsonElement adapter support 100,000 levels
without JVM call-stack recursion; arrays and objects snapshot input containers.

`tools/haskell-pretty-json-reference` builds the unchanged release libraries
with GHC 9.12.2, Aeson 2.2.3.0 +ordered-keymap, Scientific 0.3.9.0 and Text 2.1.2.
Its verbatim projection of the original Scientific Arbitrary instance and both
integer generators is checked against the complete original test file. The
importer captures actual original output, never Kotlin-generated expectations:

- 10,079 full formatter results across every mode/precision branch, decimal
  boundaries, signs, normalization, half-even ties and original generator values;
- 1,030 decimal-digit observations, including the original million-digit case;
- 840 original Text-builder prefixes and 20 original String-formatter prefixes
  at extreme Int64 exponents/precision, retaining source wrapping;
- 5,889 pretty text and UTF-8 byte results across configuration, stored number
  scales, the strict cutoff, Unicode/control/surrogate characters and all 266
  document JSON values in three configurations;
- 3,019 original fromFloatDigits/Double-JSON observations;
- 639 sortBy outputs, including always-GT, always-LT and cyclic relations;
- six original deep-array outputs, through 100,000 levels.

Total: 21,522 observations. The native response digest is
`a2a897610b15201f2db4244ed61f2840fca5d8b7f138a850029e39d1bcfa230b`.
The durable corpus losslessly stores native UTF-8 byte vectors as base64,
with transport digest
`838554d1d62a16c7a0b1d6017047b6ff7cbe829a9ece2625d006e225d80813b5`.
The source checker reconstructs every native vector and checks both digests,
all 1,027 source hashes, original generator projection, operation totals and the
complete document inventory. Test factories retain raw transport lines and
parse each case during execution, avoiding retention of millions of numeric
JSON nodes. CI runs the new --check-source command without native dependencies.

Remaining scope is explicit: this does not port the aeson-pretty command-line
program, the full Scientific arithmetic/Eq/Ord/Read/Binary/conversion library or
its complete original test suite, the Aeson parser/general ToJSON/ToEncoding
contracts, or the general GHC Builder/Text runtime. In particular, original
strict Text.replicate allocation and error demand and opaque custom Builder
algebra/buffer boundaries are not equivalent to the chunk projection. A native
near-Int64 zero-precision allocation was observed to fail with Out of memory;
that is not treated as a successful output comparison. Prefix tests use original
branches that produce defined results, plus the separately identified original
String formatter. The three original formatter values are checked; their outer
Maybe Show wrappers remain part of the pending full Scientific instance port.
The document JSON observations prove this Value formatter, not a complete AST
ToJSON encoder: the existing PandocJson numeric and recursive encoding gaps
remain open. Full Logging, ParserState, the remaining Pandoc readers/writers,
application/CLI/Lua tests, and all calibre formats/tests remain pending.


Validation for this change: 1,109,239 tests in 81 suites, zero failures/errors/
skips; all 84 source/fixture commands from CI; all 234 installed CLI comparisons
(14 FB2, 66 media, 69 format modifiers, 85 math options). All 1,027 new manifest
source hashes and the compressed corpus were checked directly against staged
Git blobs, and all 43 core-JAR license notices against their original bytes.
The preceding AST Eq/Ord publication e5c7c9e passed CI run 38623855923 at that
exact SHA, with all 95 steps completed successfully.


## Logging diagnostics, raw source positions and AST ToJSON Values

`logging/LogMessage.kt` represents every one of the original 54 constructors,
including arbitrary-precision FilterCompleted elapsed milliseconds, raw
CharacterString FilePaths, Maybe SourcePos, complete Inline/Block payloads and
Int64 levels/codepages. `Logging` translates every human-readable message,
messageVerbosity branch and constructor-specific ToJSON field. The original
.sty suffix exception, omitted punctuation for empty auxiliary messages,
setext suggestion at levels below three, special source names, unknown-key
ordering and string-valued milliseconds are preserved. No constructor produces
ERROR. The complete original keyOrder configuration is used for log arrays.

Derived Show/showsPrec is a Char-based chunk stream with raw SourcePos Show
and exact constructor/field precedence, nullable position wrappers, Text/String
quoting and BigInteger signs. Source Eq/Ord dictionaries use all declaration
fields in order and the original AST dictionaries. GHC's actual derivation dump
was inspected: LogMessage supplies compare only. Therefore its default less and
greaterEqual compare reversed arguments, while lessEqual and greater compare
forward arguments. This distinction is observable for NaN in a nested table.
Min/max preserve the selected caller instance. Host data-class equality is not
substituted for these source dictionaries.

Verbosity has exact JSON aliases/error text, bounded values, successors,
predecessors, toEnum checks and all four enumeration forms, including infinite
zero-step sequences. Its derived readsPrec grammar is checked against GHC's
actual parser: identifiers use the already source-backed Unicode 16 categories,
whitespace is accepted, redundant parentheses and the exact leftover raw Chars
are preserved, and source comments are not skipped by the cut-down Read lexer.
Large precedence values and 10,000 nested parentheses are included. Full GHC
exception stacks and generic Data/Typeable/Generic runtime machinery are not
claimed as Kotlin equivalents.

`SourcePos` now retains a CharacterString source name. Existing String
construction/name/copy/components and facade hash codes are preserved, and raw
construction/copy/show APIs distinguish individual surrogate Chars from a
supplementary Char. Name/line/column equality and ordering follow the source
instance. Position updates and copies retain raw names; nameText performs the
original per-Char Text.pack replacement for JSON and human-readable log text.
The String facade cannot itself distinguish a surrogate pair from one scalar;
callers requiring the source name use nameRaw/showCharacters. General lazy raw
source-name/error-message capabilities still remain in the runtime backlog.

`ast/AstJsonValues.kt` adds iterative ToJSON Value conversion for the complete
represented AST and associated types, independently of the existing binary
JSON writer. Doubles use original Scientific.fromFloatDigits fields, normalize
both signed zeros, and preserve Aeson's NaN/null and infinity/string rules.
Citation records, metadata, all tuple/newtype/table variants and every node are
covered. This correct Value path is used by Logging. It does not silently
replace `PandocJson.encode` or claim the still-pending exact binary ToEncoding
writer and all decoder/Read contracts. Deep values are kept off the JVM stack.

Existing ReaderMessage, WriterMessage and ResourceEvent APIs have exhaustive
adapters into the common model; effect order is retained. FB2 reader tests now
compare all diagnostic JSON fields against original Pandoc for all 14 accepted
upstream inputs and 161 accepted edge cases. Writer tests compare complete
writer diagnostic JSON for all 266 documents and 204 accepted edge cases.
The capture harness's own RunningFilter/FilterCompleted events are application
Lua-filter effects, already excluded from the writer's observation inventory;
they are not presented as implemented writer effects. No original fixtures
were changed. Numeric JSON comparisons preserve numeric type and exact decimal
value because the existing CLI fixtures were parsed before being stored.

The native reference compiles unchanged `Text.Pandoc.Logging`, local unchanged
pandoc-types 1.23.1.2, Parsec 3.1.18.0, GHC 9.12.2, Aeson 2.2.3.0, aeson-pretty
0.8.11 and Scientific 0.3.9.0. Its Shared module contains only the byte-exact
tshow definition from the pinned full Shared source; that projection is checked
and does not stand in for the remaining Shared implementation. The complete
original pandoc.log Lua test file is hashed and retained without moving its
fragile line numbers; the Lua runtime/silence tests still need their actual
Kotlin runtime port, rather than being counted from stored source alone.

Independent observations:

- 5,825 complete message/verbosity/pretty/derived-Show/ToJSON/log-byte results;
- 8,232 constructor/field/self equality, ordering, extrema and relational results;
- six complete log-array encodings;
- all 266 original document ToJSON Values, retaining Scientific fields;
- 948 nested Inline/Block table IEEE comparisons and complete message outputs;
- 2,315 original SourcePos raw-name/order/update/copy results and 2,306 raw Show
  prefixes, including every surrogate Char and distinct pair/scalar cases;
- 13 Verbosity JSON results, 5,320 derived Read results and 130 Enum results;
- twelve full compact ToJSON and Show-prefix results for Inline/Block chains
  through 100,000 levels, using original Aeson.Text for compact rendering.

Total: 25,373 cases; SHA-256
`211a45adbf33a3aa9a5e58f2c2fd4401cb41196c433a5eb0c1914e1620a2627a`.
The verifier checks all 1,101 source hashes, complete constructor and document
inventories, the original tshow projection and operation totals. Actual native
UTF-8 byte vectors are stored losslessly as base64. Expected output never calls
Kotlin. This change adds no new license: all 43 complete notices remain in the
core JAR.

Full PandocMonad/report/verbosity-filter/silence/Lua application state and
ParserState capabilities remain pending, along with general lazy/partial
fields, opaque Builder behavior, original generic reflection machinery, the
remaining Pandoc formats/application/CLI/test suites and all calibre formats
and tests. These requirements are unchanged; the full objective remains active.


Validation for Logging: all 1,134,613 tests in 82 suites pass with zero failures,
errors or skips. All 85 source/fixture checks and all 234 installed CLI checks
pass (14 FB2, 66 media, 69 format modifiers, 85 math options). The built core JAR
still contains all 43 original notices byte for byte. The preceding 3dfa17c
publication passed exact-SHA CI run 38631352283 with all 96 steps successful.

## Translations.Types — defined-value module behavior

Added Kotlin `translations/Term.kt` and `translations/Translations.kt` as direct
prerequisites for the full CommonState translation-cache field. All 22 original
Term constructors retain source order, exact names, nullary Show, Eq/Ord,
Bounded/Enum, all four enumeration forms (including infinite zero-step ranges),
successor/predecessor/toEnum failures, derived readsPrec and readList grammar.
Raw Read inputs and leftovers use CharacterString, source-backed Unicode 16
identifier/whitespace categories and iterative parenthesis parsing, including
10,000 levels. Haskell comments are not skipped by this Read lexer. Exact-name
FromJSON remains distinct from Read: it accepts neither whitespace nor
parentheses around a name.

Translations is immutable and keeps the original left-biased Map.union,
Monoid identity/concatenation, Semigroup concatenation and stimes behavior.
Arbitrary-size positive multipliers retain the value, zero returns empty and
negative multipliers use the original error text. Derived Show retains both
constructor and Map parentheses at the original precedence, original Term
order, and Haskell Text quoting. JSON validation follows the original sorted
Text-key order; unknown keys and non-string values keep their source errors.
Each accepted value uses original Data.Text.strip/GHC isSpace, including NBSP
and distinctions from JVM trim/whitespace. Per-Char Text.pack replacement is
kept separate from the raw Read input representation.

The test oracle compiles the complete, unchanged pinned
`Text.Pandoc.Translations.Types` directly, without replacing its implementation.
GHC 9.12.2's actual generated instance dump was inspected for newtype Show,
Semigroup/Monoid coercions, and stock Term instances. Original containers Map
source is already retained; the complete unchanged GHC Semigroup.Internal file
and its license are added under vendor/ghc-translations-9.12.2. Existing complete
notices cover all translated code; no new license is introduced. The native
build uses a frozen dependency plan. Native YAML 0.11.11.2 is used only to
transport original data-file JSON Values into the FromJSON tests; it is not a
Kotlin production dependency or a claim of a ported YAML parser.

Independent observations:

- 3,965 Term JSON successes/errors, including raw surrogate replacement and
  numeric Haskell Text escapes;
- 2,904 exact Term relations/ordinal/Show observations;
- 42,671 complete bounded Enum/range observations;
- 8,608 derived Read results and 329 list-Read results with exact raw leftovers;
- 5,107 complete translation-map parse/error/lookup/Show observations;
- 3,577 append, mconcat, sconcat and arbitrary-size stimes observations;
- all 107 unchanged original translation YAML files, independently decoded to
  JSON by the original library, then checked against the original FromJSON.

Total: 67,268 observations; SHA-256
`3b04c0b4ec378dbe99320ce119ecdbe9e6c71dfc64e457c4007c41e453078966`.
The verifier checks 1,117 source/fixture hashes, all original language-file
requests, the complete constructor inventory, operation totals and corpus
integrity. Expectations never run Kotlin. All 107 original language maps parse
successfully. One additional Kotlin test checks complete-Read convenience and
that mutation of a caller's JSON object cannot mutate the stored map.

Remaining work is unchanged: full CommonState/PandocMonad state and logging,
Lang and translation-file search/cache, actual YAML/readTranslations, Lua tests
and runtime, general lazy/partial values, GHC generic machinery, remaining
Pandoc readers/writers/application/CLI/all tests, and every calibre format and
test. No whole-project completion is inferred from this dependency port.

The preceding 8d6bb6f publication passed exact-SHA CI run 38637848732 with all
97 steps completed successfully.

Validation: all 1,201,882 tests in 83 suites pass with zero failures, errors or
skips. All 86 source/fixture checks and all 234 installed CLI checks pass
(14 FB2, 66 media, 69 format modifiers, 85 math options). All 43 complete
original notices remain byte-for-byte in the built core JAR. All 1,117 new
manifest hashes were verified against staged Git blobs or the exact staged
Pandoc submodule commit. The original no.yaml alias points to nb.yaml: its
link spelling is hashed as the Git blob, while both the target file bytes and
the alias's loaded contents remain independently covered.

The first complete run exhausted Gradle's 1 GiB build-daemon heap while
retaining the larger result inventory. The daemon budget is now 2 GiB; the
2 GiB test-executor budget is unchanged. The repeated full run completed
successfully in six minutes. Tests were not reduced to accommodate memory.

The next full-CommonState dependency is Text.Collate.Lang from the original
unicode-collation package (Pandoc's bound is >=0.1.1 and <0.2), rather than a
package named text-collate. Its full Lang implementation and original BCP47
parse/round-trip tests have been read in preparation; no Lang implementation
or tests are counted as ported by this change.

## Lang — defined-value behavior and original language tests

Added `language/Lang.kt` and `language/LangBinary.kt`, based on unchanged
Text.Collate.Lang from unicode-collation 0.1.3.7. The original Pandoc dependency
bound is >=0.1.1 and <0.2. All 117 regular release files, including the complete
unit/conformance tests and collation data, are retained unchanged. Archive
SHA-256: `ec7f75d635486d760bf45d2093ff380b1dad4146ceee39107d66cd00af713c57`.
The complete original BSD-2-Clause notice is added to licenses and the JAR.
No other collation implementation or test group is claimed as ported.

The six-field Lang record keeps immutable snapshots of nested lists, per-field
Text scalar normalization, declaration-order Eq/Ord, Maybe/list/tuple ordering,
record Show with original precedence and Haskell Text escapes, and selected
caller instances for min/max. Direct construction permits arbitrary record
values, as upstream; parsing is a separate operation. renderLang retains empty
optional/keyword values, duplicate extensions, original field spelling and
private-use rendering. IsString uses the original und fallback on parse error;
raw CharacterString construction preserves per-Char Text.pack replacement.

parseLang uses the full original Parsec [Text] grammar through the shared
consumed/empty interpreter. Its token cursor updates columns by Text character
length, without counting separators, and keeps original error labels/text.
ASCII predicates, source GHC whitespace/digit categories, hyphen/underscore
splitting, extlang count/backtracking, optional script/region, variants,
extension attributes and case-preserving keyword/type spelling are retained.
The original grammar permits trailing garbage and stops at the first source
whitespace; it is not replaced with platform Locale validation. The original
private-use parser is retained even though the earlier extension parser
normally consumes an x singleton.

lookupLang follows language/script/region/collation matching and original
stable sortOn (Down . fst), using the already source-backed GHC natural-run
sort. Script outranks region, which outranks collation. Empty present values
remain distinct from Nothing. The first u extension and first co keyword are
used, variants/private-use do not participate, and ties retain the first
original association-list entry and caller identity.

The original Binary instance is the six-tuple of Text/Maybe/list/tuple fields.
The port composes the existing independently verified Data.Binary engine.
All nonzero Maybe tags decode as Just; decoded values re-encode using the
canonical tag. Signed length boundaries, strict UTF-8 errors, consumption
counts and complete remaining input follow decodeOrFail. There is no native
conversion subprocess in production. The test oracle compiles unchanged Lang
with Parsec 3.1.18.0, Binary 0.8.9.3, text 2.1.2 and GHC 9.12.2; its full frozen
dependency plan is retained. GHC's actual Eq/Ord/Show derivation dump was read.
The original langPairs and emptyLang definitions are copied verbatim into the
oracle, checked against the complete original test source and executed as
sixteen separate Kotlin parse/round-trip tests.

Independent observations:

- eight original example records, covering all 16 direct original Lang tests;
- 21,294 parse results/errors and 3,831 IsString fallback/normalization results;
- 18,225 declaration/field/list/tuple comparisons and selected extrema;
- 811 complete arbitrary record/render/Show/binary results;
- 20,358 competing language matches, absent/exact/different field combinations,
  duplicate/empty collation fields and stable caller-order ties;
- 8,655 complete binary decode results, every valid-record truncation,
  trailers, all 256 tags in both Maybe fields, invalid UTF-8 and length limits.

Total: 73,182 observations; SHA-256
`9b06f20c06e6d4ee19c9af3d62e84d905aa7eb9e558dff1b4a3293aa05ca8434`.
All 337 source hashes, original test definitions, operation inventories and
corpus bytes are checked. Expected results never execute Kotlin. One extra
Kotlin test verifies caller mutation cannot alter the nested record, stable
lookup identity, and defensive ownership of decoded remaining bytes.

Remaining work still includes general lazy/partial fields and lists, GHC
compile-time Lift/typed quotation, the rest of unicode-collation and all its
other original tests, full CommonState/PandocMonad/translation caching/YAML,
application/Lua runtime and tests, all remaining Pandoc formats and tests,
and all calibre formats/tests. Full CommonState needs immutable MediaBag
ownership and the real PandocError catch contract; the existing mutable
ResourceContext media bag is not treated as equivalent state merely because
logging fixtures pass.

The preceding 31c01df publication passed exact-SHA CI run 38641381919 with all
98 steps completed successfully.

Validation: the complete suite passes all 1,275,073 tests in 84 suites with
zero failures, errors or skips. All 87 source/fixture checks and all 234
installed CLI checks pass (14 FB2, 66 media, 69 format modifiers, 85 math
options). The built core JAR preserves all 44 complete notices byte for byte,
and all 117 regular package files were rechecked against the original release
archive. The final ownership test was strengthened to mutate a nonempty
returned byte suffix; the full Lang suite was rerun and passed after that
test-only change. Production code and independent corpus were unchanged.
All 337 manifest hashes and the complete corpus were also verified against
staged Git blobs or the exact staged Pandoc submodule commit before publication.

## Immutable MediaBag values, source map trees and payload demand

Added MediaBagValue as the original immutable state model. The existing
mutable MediaBag API now changes a held value and exposes an O(1) snapshot;
older snapshots retain their resources and unchanged items. insert/delete/
lookup, ascending mediaItems/mediaDirectory, left-biased union, concatenation,
stimes and custom Show use this shared model. Payload arrays are owned; safe
insertDeferred paths retain a memoized delayed payload, while paths requiring
SHA-1 demand it during insertion, matching MediaItem's strict path field.
MediaItem now keeps a raw CharacterString extraction path and original
Text/raw-path/unsigned-byte Eq/Ord and record Show. String/ByteArray accessors
remain available for existing callers.

PersistentTextMap ports the actual containers 0.7 insert/insertR/delete/union,
split/link/glue/min/max views and delta=3, ratio=2 weight balancing needed by
MediaBag. Updates preserve old tree roots and unchanged branches; snapshotting
does not scan or clone the collection. Independent tests inspect the complete
original containers tree, including cached subtree sizes, after each operation,
with prior roots retained. All 147 operation histories match the original tree
shapes, rather than merely yielding the same sorted values. The remaining
containers API/general runtime is not inferred complete from this support port.

The expanded original oracle exposed differences in the previous URI adapter:
Network.URI.parseURI rejects non-ASCII raw paths, even though Pandoc's separate
isURI test first escapes non-ASCII Text. MediaBag must therefore sometimes retain
a Unicode URI-looking name without forcing its payload. Absolute-URI validation
and path selection now use a source grammar projection, including scheme-only
and rootless URIs, empty authorities, unbounded numeric ports, percent escapes,
255-token registered names, IPv6/IPv4/zone identifiers and the pinned source's
one-hex-digit lowercase IPvFuture form. URI query text is excluded from the
extension fallback. Leading POSIX drive separators in makeCanonical are also
retained. Java URI acceptance is not used as the source parser for MediaBag.
The complete unchanged network-uri 2.6.4.2 release (ten regular files and all
original tests) and its full license are retained; other network-uri APIs and
test groups remain pending. Archive SHA-256:
`9c188973126e893250b881f20e8811dca06c223c23402b06f7a1f2e995797228`.

The new native reference compiles unchanged MediaBag/MIME/URI/UTF8, real
containers/crypton/network-uri/mime-types libraries and a frozen dependency
plan. Its Shared shim contains only the exact original makeCanonical definition;
the projection is checked against the complete pinned source. Original Haskell
MediaBag tests and the complete Lua mediabag test file remain retained and
hashed. The original pure path/alias/collision assertions are checked here;
existing integration fixtures/tests still cover fill/extract. The Lua runtime
and complete original Lua test execution remain pending.

Independent observations: 2,910 complete bags/MIME-registry results, 1,326
absolute-URI/known-scheme results, 856 MediaItem Show/value results, 1,500 item
comparisons, 300 delete and 300 union before/after results, 2,170 payload demand
or failure results, six arbitrary-size stimes results, and 147 full tree
histories. Total: 9,515 cases; SHA-256
`f0dbd7ee0bc71bd66a46afdd773d236025694edd27976d8bf02e91f5e68f8499`.
All 78 source hashes, original assertion inventory, operation totals and corpus
integrity are checked. Expectations never use Kotlin. One extra ownership test
checks saved snapshots, reused caller item identity, delayed evaluation exactly
once, and isolation from mutation of producer/returned byte arrays.

Remaining work includes general chunked/infinite/partial lazy ByteStrings and
all their original contracts, generic Data/Typeable machinery, remaining URI
APIs/tests and containers operations, full PandocError and CommonState/Monad
state/logging, translation cache/YAML, Lua/application/CLI, all other remaining
Pandoc formats/tests and all calibre formats/tests. The full objective remains
active. The preceding ac36698 publication passed exact-SHA CI run 38645363976
with all 99 steps completed successfully.

Validation: all 1,284,589 tests in 85 suites pass with zero failures, errors or
skips. All 88 source/fixture checks and 234 installed CLI checks pass (14 FB2,
66 media, 69 format modifiers, 85 math options). All 45 complete original
notices remain byte-for-byte in the built JAR. All 78 manifest hashes and the
complete corpus were checked against staged Git blobs or the exact staged
Pandoc submodule commit. Existing upstream expectations were not modified.

## PandocError, CiteprocError and GHC IOError values

Added the complete 33-constructor PandocError family, preserving source field
types/order, renderError/displayException, derived Show/raw Show characters,
and all original handleError exit statuses. UTF8 decoding diagnostics keep
signed 64-bit offsets and the original two-character space-padded hexadecimal
Word8 form. Empty filenames, doc/pdf special cases, embedded controls/Unicode,
per-field Text.pack replacement, negative numeric Show parentheses, nullary
constructors and nested CiteprocError precedence are covered independently.
The exception base is the specific type required by PandocMonad.catchError;
it is not equated with arbitrary host exceptions.

GhcIoError retains all six original IOException fields: optional Handle,
19-case IOErrorType, raw location/description, optional signed CInt errno and
raw filename. Its Eq uses the original fields and order; Show ignores errno,
uses filename before handle, emits unquoted raw String fields and keeps empty
present filename distinct from Nothing. Raw Show and Text display are separate
so individual surrogate Chars remain visible before Text.pack replacement.
ErrorHandle models File versus Duplex and read-side cell identity; differing
names or Duplex write-side cells do not change source Handle equality. Actual
IO device/buffer operations are not inferred implemented from this model.

All three original CiteprocError variants, strict normalized Text fields, Eq,
Show and prettyCiteprocError are included. The complete citeproc 0.14.1 release
is retained without modifying any of its 1,069 regular source/test/data files.
Archive SHA-256:
`1287ab8196b3d2b1dd635b78117cacdc3bddfd1125f15a9017f0720e9ca03feb`.
Its original full BSD-2-Clause notice is added to the JAR. The citation engine,
remaining Citeproc types/APIs and all other original tests remain pending.
Complete original GHC 9.12.2 IO.Exception and IO.Handle.Types sources and their
license are also retained for the value/identity/Show definitions.

handleError is translated with explicit IO effects at the boundary. Success
returns the original caller value without writes or exits. PandocIOError writes
the original raw IOException String; other variants write rendered UTF8 Text,
then exit with the original code. Writer failures propagate and do not trigger
an exit. The native oracle executes the complete unchanged original
Text.Pandoc.Error/UTF8 modules and captures actual stderr bytes and ExitCode
exceptions. Its Shared tshow and CiteprocError shims contain only the byte-exact
needed definitions, checked against complete original sources; StrictData is
retained for CiteprocError. The other Citeproc implementation is not replaced
by a surrogate. Actual GHC's generated PandocError Show instance was inspected.

Independent observations:

- 2,250 complete render/display/derived raw Show results for all 33 variants;
- 1,984 actual original handleError stderr/exit observations;
- 1,127 GHC IOException raw Show/display results;
- 3,097 IOException/Handle/CInt-wrap equality results;
- 255 CiteprocError Show/pretty results and 2,601 constructor/Text equalities.

Total: 11,314 cases; SHA-256
`ed035b7d9c894ca8a063376124a5184a59d92e416932028b45028c0ebc9c8858`.
The verifier checks all 1,094 source/fixture hashes, exact source projections,
complete constructor and operation inventories and corpus bytes. Expected
results never execute Kotlin. A separate effect test checks success identity,
no success side effects and propagation of writer failures before process exit.
The captured handleError IO inputs are valid Unicode under native UTF8; raw
surrogate IOException fields are separately covered by Show/display tests.
Full GHC native handle encoding/newline/buffering/failure behavior and a concrete
production process backend/complete CLI dispatch remain pending, along with
Typeable/Generic machinery and general lazy/partial values.

Both original PandocIO and PandocPure stacks were read: ExceptT PandocError
is outside StateT CommonState, so ordinary caught PandocError preserves other
state changes. This is required by the next CommonState/runSilently work.
Full CommonState/Monad/Lua/YAML/application and all remaining Pandoc formats
and tests, plus all calibre formats/tests, remain within the unchanged goal.

Validation: all 1,295,904 tests in 86 suites pass with zero failures, errors or
skips. All 89 source/fixture checks and 234 installed CLI checks pass (14 FB2,
66 media, 69 format modifiers, 85 math options). The core JAR preserves all
46 complete original notices. All 1,069 regular Citeproc release files were
rechecked byte-for-byte against the original archive. All 1,094 manifest hashes
and corpus bytes were verified against staged Git blobs or the exact staged
Pandoc submodule. The preceding cd81680 publication passed exact-SHA CI run
38649089610 with all 100 steps completed successfully.


## CommonState and PandocMonad state/logging operations

The complete CommonState record now has all 13 fields and original defaults,
including the HTTP-support manager field. FilePath fields use raw CharacterString
values, Text fields are normalized independently, mutable caller collections are
owned snapshots, and immutable MediaBag/Translations/Lang values preserve their
identity. The cached JVM HttpClient is an actual transport reference; this does
not implement or imply the complete original Network.HTTP.Client request, TLS,
resource lifecycle or concurrent manager contracts.

The reverse chronological log uses persistent cons nodes with O(1) report prepend
and shared old roots. getLog returns a separate immutable chronological snapshot.
report selects output by original message verbosity, calls logOutput before
append, and rereads current state after output. Reentrant reports, callback log
replacement, callback verbosity/other-state changes and exceptions before append
are independently compared. Suppressed messages still enter the log.

runSilently saves log/verbosity, installs ERROR and an empty log, and returns
(result, reverse-chronological captured log). It restores only those two fields
on success and caught PandocError; all other state changes persist. Arbitrary host
exceptions bypass that catch and leave the current state intact. The same typed
throwable and successful return object identities are retained. An action that
raises verbosity can produce output even within runSilently, as upstream does.
Nested silence and caught typed errors retain the source restoration order.

Request-header replacement is case sensitive, removes every exact duplicate,
prepends the new pair, and preserves other order. Changing certificate-check
policy discards the cached client, while assigning the same policy retains it.
setInputFiles inspects only the first raw filename, accepts the original ASCII
absolute URI grammar and exactly lowercase http/https schemes, and removes only
query/fragment. It preserves the complete URL path including the filename; the
source comment's reference to a directory does not override its implementation.
All field getters/setters, media insertion and toLang warning/report behavior
are included.

Tracing retains the source prefix and GHC traceIO NUL removal plus its additional
warning line. The complete original GHC 9.12.2 Debug.Trace source is preserved;
its bytes were checked against the ghc-9.12.2-release tag (SHA-256
`2c010ddb58214ff0bb1bf35faaddff98442af6b63365ecea1dd168b813b0ba42`).
The native oracle captures actual stderr bytes under UTF8. JVM effect callbacks
and the default stderr output are provided; complete GHC C-runtime buffering,
locale/terminal/Windows newline and asynchronous/lazy evaluation behavior remain
pending, as do general infinite/partial state field values and Generic machinery.

The oracle compiles unchanged original CommonState with PANDOC_HTTP_SUPPORT,
Logging, MediaBag/MIME/URI/UTF8, Error, Translations.Types and Lang. The minimal
PandocMonad projection contains byte-exact selected defaults and state/logging
functions, checked against the pinned full module; unported IO/parser/application
methods are not replaced by fake implementations. ExceptT PandocError is outside
StateT CommonState, matching both original instance stacks. A mirrored IORef lets
the harness observe writes before a host exception without changing catchError
semantics. HTTP cache tests compare real original Manager reference identities.
The separate harness input protocol constructs values and selects effects only.

There are 3,615 native observations across all 54 message constructors and all
three verbosity levels, five callback behaviors, every URI case from the previous
native media corpus, raw paths, duplicate/case-sensitive headers, cache changes,
translation/media state persistence, language warnings, actual trace output,
800 seeded mixed sequences and nested success/Pandoc-error/host-error actions.
Three original Lua silence assertions are checked at their underlying state/log
contract; the full Lua interface/runtime and its original test suite remain
pending. Two additional Kotlin checks cover immutable snapshots, 10,000 persistent
log inserts, shared roots and exact return/throwable identities.
Corpus SHA-256:
`63733112dafa3e9d8b58eebd9b28b946806185afb7bcbc752300deaed9ef8205`.
All 31 source/fixture hashes and the corpus bytes are independently checked.
Existing oracle expectations were not altered. The relevant 3,617 state tests
and 25,374 logging tests pass with zero failures/errors/skips.

Full PandocMonad IO/environment/time/translation loading, ParserState, readers,
writers, Lua and application work, remaining original/dependency tests and all
calibre formats/tests remain within the unchanged objective.

Validation: all 1,299,521 tests in 87 suites pass with zero failures, errors or
skips. All 90 source/fixture checks and 234 installed CLI checks pass (14 FB2,
66 media, 69 format modifiers, 85 math options). All 46 complete original notices
are byte-exact in the built core JAR. The 31 manifest source hashes and exact
corpus bytes were verified against staged Git blobs or the pinned Pandoc
submodule commit. The previous bdad2bb publication passed exact-SHA CI run
38651628767 with all 101 steps completed successfully.


## YAML reader and scalar schema dependencies for translation loading

Text.Pandoc.Translations requires the original YAML pipeline, not merely a parser
for the 107 packaged translation files. The first dependency stage now translates
libyaml 0.2.5 reader.c and the YAML 0.11.11.2 scalar schema/numeric/string-style
functions. The scanner, syntax parser, anchor/merge/document state, exception
rendering, generic FromJSON decoding, emitter and configuration/file/include APIs
remain pending; readTranslations/getTranslations/setTranslations/translateTerm
are not claimed implemented by these dependency helpers.

YamlReader retains the original fixed logical raw/decoded buffer capacities,
encoding detection/BOM skipping, explicit UTF8/UTF16LE/UTF16BE selection, batched
prefetch, unread-character counts, raw-byte offsets, buffer compaction, EOF NUL,
read-handler failures and error priority. Actual backing storage grows only to
populated capacity so a tiny scalar does not allocate two large empty arrays;
this preserves callback size requests and observed bytes/state. Scanner lookahead
can read a byte without copying the entire buffer. The read handler owns the IO
boundary; physical native allocation failures and invalid C callback contracts
are not claimed equivalent to JVM memory errors.

UTF8 failures distinguish invalid leading/trailing octets, incomplete sequences,
overlong width and invalid Unicode values before rejecting YAML-prohibited
control characters. UTF16 distinguishes incomplete code units, unexpected low
surrogates, incomplete pairs and wrong second units. Encoding detection waits
for three bytes or EOF even for a two-byte BOM. Explicit encoding does not strip
a BOM. Offsets use unsigned 64-bit source size_t arithmetic, including wrapping
and the original MAX_FILE_SIZE check after non-EOF fill. The EOF early return
bypasses this check; synthetic boundary offsets are compared to actual native code.

The scalar layer uses the original style/tag precedence: single/double quoted,
StrTag and Folded yield strings; Literal does not force string typing. Null and
boolean spellings are exactly the source alternatives. Other tags do not coerce
content. Numeric parsing accepts lowercase unsigned 0x/0o, signed decimal with
optional empty fractional digits and decimal exponent, preserves coefficient
precision and wraps exponent arithmetic like source Int64. Full-input failures
retain original Attoparsec messages. NaN/infinity, underscores, sexagesimal,
uppercase numeric prefixes and signed prefixed integers remain ordinary strings
according to this source schema. Scalar bytes use the existing source Text
lenient UTF8 decoder. String-style selection chooses Literal for newline, quotes
special/numeric text, and handles empty strings without invoking a custom style
callback. Scalar events own bytes and raw anchor character values.

Native evidence uses unchanged complete C api.c/reader.c for actual buffer state,
and the complete installed original yaml Internal/libyaml/Attoparsec packages
for numeric parsing, scalar-event decoding and stringScalar/defaultStringStyle.
The C bridge constructs inputs/consumes characters and observes state only; no
decoder is duplicated. Both frozen native programs are test oracles only.

There are 82,635 independent fixture cases:

- 1,088 grouped UTF8 cases execute all 1,114,112 code points;
- 128 grouped UTF16 cases execute all 131,072 code units in both byte orders;
- all 65,536 two-byte inputs, with actual buffer/error/EOF/callback state;
- 1,332 chunk/BOM/input-failure cases, 75 unsigned-offset and 24 capacity cases;
- all 107 original Pandoc YAML files under three input chunk plans (321 cases);
- 2,500 seeded reader sequences; 257 numeric boundaries and 3,500 numeric fuzz cases;
- 3,640 scalar style/tag cases, 3,856 malformed scalar byte cases and 378 string events.

Corpus SHA-256:
`981df8ac79badbdf48ed7e56e0bf803013c60c578d7448a0e58d78f594f2c6c3`.
All 185 source/fixture hashes, complete Unicode domains, byte-pair inventory and
all original translation paths are checked independently. The original no.yaml
symlink is hashed as its target spelling and its bytes are loaded through nb.yaml.
No pre-existing upstream expectations are changed. The 82,636 targeted tests
pass, including a separate ownership/empty-strategy effect check. These tests
validate the reader and schema stages; they do not substitute for porting the
full original YAML test suite or full translation/Pandoc runtime.

Complete original Hackage releases retained verbatim:

- yaml 0.11.11.2: 34 regular files, archive SHA-256
  `ee74923ee5b0fef74413c58aaa20ea59baf5d29d256a62fb47cc279570d9dc2f`;
- libyaml 0.1.4: 8 regular files, archive SHA-256
  `d6297ec9b1ebb4e4b580a4357d840d08a6f53e13a10c0c53ee74b973bba6fa13`;
- libyaml-clib 0.2.5: 14 regular files, archive SHA-256
  `04526bc54e5fcf471c2bd22854e6b519e162a6e3d654e866cd8862103c725149`.

All 56 original files were byte-compared against their archives. Their complete
BSD-2-Clause/BSD-2-Clause/MIT notices are preserved in the core JAR. The preceding
95b757b publication passed exact-SHA CI run 38654151387 with all 102 steps
completed successfully. Full Pandoc, all original tests and all calibre work
remain within the unchanged goal.

The first full-suite run exceeded the existing 2 GiB test worker heap because
JUnit retains dynamic-test lambdas and the new factory captured the parsed JSON
for every case, including all grouped Unicode arrays. The YAML factory now
retains transport text only until each test runs and releases it before parsing;
results remain the same independent native values and no cases are removed.
The targeted 82,636 tests pass after this retention fix. The complete suite also
passes at the unchanged 2 GiB worker limit in 6 minutes 6 seconds.

Validation: all 1,382,157 tests in 88 suites pass with zero failures, errors or
skips. All 91 source/fixture checks and 234 installed CLI checks pass (14 FB2,
66 media, 69 format modifiers, 85 math options). All 49 complete original notices
are byte-exact in the built core JAR. All 185 source hashes and the complete
corpus were verified against staged Git blobs or the exact pinned Pandoc
submodule. All 56 original YAML release files match their archives byte-for-byte.


## Complete libyaml scanner algorithm

Translated the complete libyaml 0.2.5 scanner.c algorithm to Kotlin on the
previously ported streaming byte reader. No native scanner, external converter,
precomputed translation table or alternate YAML grammar is used in production.
All token fetchers/scanners, delayed simple-key insertion, flow/indent stacks,
whitespace/comment/BOM handling, directives, tags/anchors/aliases, URI escapes,
quoted/plain/block scalar processing and scanner error branches are present.
The C scanner API's empty token after stream-end or prior failure is represented
by End, separate from the first Failure result.

The queue preserves prospective simple-key token numbers, required-key failures,
single-line/1024-character staleness, KEY/BLOCK-MAPPING-START insertion order and
original block/flow restrictions. Indentation guards retain the source INT_MAX
memory-error branch, while actual native allocation failures/invalid C memory
contracts are not claimed equivalent to JVM errors. Queued token bytes and marks
are immutable snapshots; terminal calls do not ask the reader for more input.

Marks count Unicode characters, with source CRLF index advance and line/column
reset. CR, LF and NEL normalize to LF inside scalars; LS/PS retain their original
UTF8 bytes. Single-quoted doubling, double-quoted simple/hex escapes, escaped
line breaks, folding/blank preservation, literal/folded chomping and explicit
indentation follow the original transitions. Error context/problem text and
both marks preserve the source spelling (including `hexdecimal`) and timing.
Reader errors can preempt token recognition because filling the source reader
prefetches a whole batch; native states verify this ordering.

Tag URI escapes validate UTF8 octet widths/continuations exactly as the scanner,
which intentionally differs from the reader's Unicode/overlong validation.
Percent-decoded NUL terminates C-string tag/anchor fields; scalar byte fields
retain embedded NUL because they have an explicit length. This distinction is
preserved instead of normalizing or dropping all NULs indiscriminately.

The new independent oracle compiles unchanged complete api.c/reader.c/scanner.c.
Its bridge only supplies input/chunk plans and observes actual tokens, pending
key records, queue, indentation/flow state, marks, reader buffers and error
fields after every yaml_parser_scan call. GHC transports values and uses its
original Read to decode quoted Haskell input literals; expected scanner output
never runs Kotlin. Prior protected oracle directories/corpora are unchanged.

There are 8,641 native observations:

- 54 runs of 18 note-based and composed input examples;
- all 117 original Pandoc/dependency YAML paths with three chunk plans (351 runs);
- 430 decoded quoted literals from the complete original Haskell YAML tests;
- 248 surrounding-context and 198 quoted/plain line-folding cases;
- 840 block scalar/chomping/indent cases, 1,536 byte-character contexts;
- 140 quoted-escape, 48 tag-URI and 48 directive cases;
- 24 key-length, 66 indentation/flow, 20 buffer and 108 encoding cases;
- 30 input-handler failure/EOF cases, 4,000 syntax fuzz and 500 byte fuzz cases.

All 22 token kinds and normal/reader/scanner error families are observed.
Corpus SHA-256:
`a6ad24027b0e586ed2850949ee4e5f2977a22cac76632883e9127f165e3cfc20`.
All 176 source/fixture hashes, original path inventory, groups and token-kind
inventory are checked. The 8,642 targeted tests pass with zero failures/errors/
skips, including token ownership and no reads after terminal calls. Transport
text is released before each dynamic test parses its expected JSON, retaining
the previous full-suite memory fix.

Scanning these source literals/files is a component comparison, not a claim
that the full original YAML parser/emitter/include/config/property suites are
ported. Syntax parsing into events, tag/directive resolution, higher-level
anchor/merge/document handling, translation loading/caching, the remaining
Pandoc application/readers/writers/Lua/tests and all calibre work remain within
the unchanged objective.

The preceding 57b817c publication passed exact-SHA CI run 38657511399 with all
103 steps completed successfully.

Validation: all 1,390,799 tests in 89 suites pass with zero failures, errors or
skips at the unchanged 2 GiB worker limit. All 92 source/fixture checks and all
234 installed CLI checks pass (14 FB2, 66 media, 69 format modifiers, 85 math
options). All 49 complete original notices remain byte-exact in the core JAR.
All 176 source/fixture hashes and exact corpus bytes were verified against
staged Git blobs or the exact pinned Pandoc submodule commit. No pre-existing
oracle expectations or protected oracle implementations were modified.


## Complete libyaml syntax parser algorithm

Translated the complete libyaml 0.2.5 parser.c state machine on the Kotlin reader
and scanner. All 24 state dispatcher cases and productions are represented,
including implicit/explicit document starts/ends, empty document/node values,
block/flow/indentless collections, ordered mapping entries inside sequences,
anchor/tag property ordering, alias events, directives and tag expansion.
Parser recursion is represented by the explicit state/mark stacks; a nested
collection does not create an equally deep JVM call stack.

The scanner now separates PEEK_TOKEN from SKIP_TOKEN, preserving token availability,
parsed-token counts, delayed-key bookkeeping and stream-end timing during parser
lookahead. Its public scan behavior remains independently checked against the
same unchanged 8,641 original scanner observations. Parser errors use both source
context/problem marks and exact text; reader/scanner failures retain their source
priority. The first failure and the later empty terminal event remain distinct.

YAML version handling accepts only source 1.1/1.2 and preserves duplicate/incompatible
errors. User tag directives take precedence over default !/!! handles, duplicates
fail at the source mark, and document end clears directive scope. Tag bytes are
expanded without applying a different Unicode schema or treating C-string fields
as length-bearing scalar bytes. Missing versus present empty tags/anchors remain
distinct. Plain/quoted implicit flags, collection styles, explicit directive lists,
version records, owned scalar bytes and all event marks follow original fields.
Physical native allocation failures, invalid direct manipulation of internal C
structures and generic partial values are not claimed equivalent to JVM behavior.

The new native oracle compiles unchanged complete api/reader/scanner/parser modules.
Its bridge only supplies bytes/chunk schedules and observes events, parser state,
return/mark stacks, active tag directives, token availability, scanner queue/keys,
reader state and errors after every yaml_parser_parse call. Previously published
oracles/corpora and their expectations are unchanged. The scanner corpus is used
only as an input inventory; every parser expected result is captured afresh from
actual original C execution.

There are 10,286 native observations: all 8,641 previous scanner inputs plus
57 document/directive cases, 840 node-property order/tag cases, 124 collection
production cases, 18 nested-stack cases through depth 300, 500 generated nested
nodes and 106 byte-by-byte input-failure schedules. All 11 event kinds including
the empty terminal event, normal/reader/scanner/parser results and all 22 normally
observed parser states are present. Legacy dispatcher entries 6/7 are not assigned
by normal original productions; their node routines are called directly and their
corresponding dispatcher handlers are translated as well.

Corpus SHA-256:
`0002d61ee657df3b5cdd0ba2eef8af775a54de5d9873fd0773f63eba6b7edb18`.
All 69 source/fixture hashes, case/group totals, event inventories and observed
state inventories are checked. All 10,287 targeted parser tests and 8,642 scanner
tests pass with zero failures/errors/skips. The additional parser check verifies
byte/directive ownership and absent versus empty tag distinction. Dynamic fixture
text is released before parsing so completed test lambdas retain no input trees.

This stage parses syntax into libyaml events. Higher-level yaml anchor lookup,
merge/warning/document conversion, FromJSON decoding, parse exception rendering,
configuration/includes/emission and Pandoc translation loading/caching remain
pending, together with remaining Pandoc formats/runtime/tests and all calibre
work within the unchanged objective.

The first combined full-suite run exceeded the unchanged 2 GiB worker heap.
The parser's input transport is 147,650,483 characters, with a 23,800,235-character
nested-stack trace; each case releases its transport text, but JUnit retains
completed test descriptors from preceding classes until the worker exits. The
complete test task now recycles its worker after every 20 classes, executing all
classes/cases sequentially with the same 2 GiB per-worker limit. No expectations,
tests or corpus entries are removed. Targeted parser/scanner validation passed
before this full-run isolation change; the complete task passes with all cases after worker isolation.

The preceding 7662684 publication passed exact-SHA CI run 38659627184 with all
104 steps completed successfully. All 93 source checks still pass after the
worker-isolation configuration change.

Validation: all 1,401,086 tests in 90 suites pass with zero failures, errors or
skips, recycling workers after 20 classes at the unchanged 2 GiB per-worker
limit. All 93 source/fixture checks and all 234 installed CLI checks pass (14
FB2, 66 media, 69 format modifiers, 85 math options). All 49 complete original
notices remain byte-exact in the core JAR. All 69 source/fixture hashes and exact
corpus bytes were verified against staged Git blobs or the exact pinned Pandoc
submodule commit. The parser native corpus still has the same 10,286 cases and
case hash after its source inventory verification update. No pre-existing
native expectations, tests or protected reference implementations were changed.


## yaml event-to-Value decoding and parse exception display

Translated the complete parse/parseAll/parseO/parseS/parseM value-building
algorithm from yaml 0.11.11.2 Data.Yaml.Internal. Nested collections use explicit
frames rather than a JVM call stack. Reader paths share persistent reverse-list
tails, avoiding a copy of every ancestor path at every level; an additional
10,000-level direct event-stream check verifies complete decoding and terminal
event demand without involving scanner complexity. Event demand is preserved: sequence ends
are peeked, mapping keys are consumed directly, and stream end leaves trailing
events untouched. Empty byte input follows Text.Libyaml.decodeMarked's parser
bypass; an empty event source yields Null for a single-document decode and an
empty list for all-document decode. Single-document mode parses the whole stream
before rejecting multiple documents, preserving later syntax/alias error priority.

Anchors live across all documents in the stream, scalar anchors save the typed
value even when the mapping key remains raw lenient-decoded text, and collection
anchors become available only after the collection is complete. Alias references
share completed immutable values. String aliases may serve as keys; other alias
types fail with the source error. Properties use Text.Libyaml's separate event
model, Char8 tag decoding, owned scalar bytes, exact styles and optional anchors.

Mapping merges retain existing keys; sequences of merge objects retain the first
object's value and ignore non-objects. The original merged-key tracking set is
replaced at each merge, not accumulated. Explicit assignments remove their key
from that set. Duplicate warnings retain root-first paths and reverse emission
order, while NonStringKey retains the source reverse Reader path. All 11 original
prettyPrintParseException branches are represented, including raw Haskell String
characters, nested settings errors, libyaml zero-based marks, Aeson Value Show
and pinned Unicode JSONPath classification/escaping. Warning equality and derived
Show are represented as well. Generic exception display is supplied at the IO
boundary; synthetic ProbeException tests verify the display branch without
claiming all host exception types have been ported.

The new independent oracle runs unchanged decodeHelper_/decodeAllHelper_, actual
Text.Libyaml decoding and prettyPrintParseException under the frozen Haskell
dependency plan. The previous syntax corpus supplies inputs only; no old expected
output is reused. The 23,105 observations compare complete values with exact
Scientific coefficient/exponent fields, warning paths and Show, raw error display
characters and the exact number of demanded events. They include original source
literals/files, malformed direct event streams, truncations, all scalar styles,
invalid scalar UTF8 supplied directly, cross-document anchors, alias key types,
repeated merge priority/tracking, trailing unused events, depth 300, every error
display branch, raw surrogate characters and negative/path/precedence boundaries.

Corpus SHA-256:
`0d5b4d5fd6e21bb92568fb69456bbdee88f6df0ea2947f38155e1e0c78842c4a`.
All 70 source hashes and exact case/group totals are checked. Kotlin production
uses only its existing reader/scanner/parser/schema; Haskell remains a test oracle.
No previously published oracle or expectation corpus was modified.

Typed conversion callbacks are exposed after complete value parsing, with exact
first-failure ordering and AesonException wrapping. The remaining FromJSON
instances, full generic/async/IO exception boundary, derived Show ParseException,
objToEvents/emission, includes/configuration/property/file suites, Pandoc
translation loading/cache and other Pandoc readers/writers/application/Lua/tests
remain pending, followed by all requested calibre MOBI/tests/additional formats.
The complete objective above is unchanged.

The preceding 5f0263b publication passed exact-SHA CI run 38661795109: all 105
steps completed successfully, with no failed or unfinished step.

Validation: the final implementation passes all 1,424,195 tests in 91 suites,
with zero failures, errors or skips, including 23,109 YAML value tests and the
10,000-level event-stream check. The full suite was rerun after replacing copied
ancestor paths with shared reverse-list tails. All 94 source/fixture checks and
234 installed CLI checks pass (14 FB2, 66 media, 69 format modifiers, 85 math).
The installed CLI uses the exact final core JAR, and all 49 complete notices
remain byte-exact inside it. All 70 native-source hashes and exact corpus bytes
were verified against staged Git blobs and the pinned Pandoc gitlink. Existing
protected oracles/corpora remain unchanged. The complete Pandoc/calibre objective
is still active; this validation proves this decoder stage and the current suite.


## Complete Pandoc translation selection, parsing and caching

Translated all four functions in the pinned Text.Pandoc.Translations module:
readTranslations, setTranslations, getTranslations and translateTerm. The YAML
decoder now feeds the exact Aeson Value model into the original Translations
FromJSON instance, avoiding a numeric/JSON serialization roundtrip. Validation
uses sorted Text keys, exact Term/type errors and the original Unicode T.strip.
The prior JSON entry point retains its existing behavior and independent tests.
All documents must parse and validate before the first translation table is
selected; an empty document stream reports the source empty-YAML error.

Language selection is lazy and retains a loaded table only when all six Lang
fields compare equal. Equal rendered tags with different underlying records
still invalidate the cache. Loading deduplicates full-tag, language/script and
base-language candidates in original order. Only Pandoc errors from file reads
and toTextM are swallowed during fallback; host errors propagate. Success caches
the language captured before file effects run, while retaining other state
changes made by callbacks. On final failure the original warning is reported
before clearing the cache. A reporting exception leaves that clearing undone;
re-entrant logging/state changes retain the existing report ordering. Missing
terms report NoTranslation and return an empty string; present empty terms do
not issue a warning.

Added the typed PandocMonad toTextM entry point over the existing strict UTF8
implementation. It strips one UTF8 BOM and CR bytes before decoding, then reports
the original unfiltered input offset/byte through PandocUTF8DecodingError. Raw
Haskell file-name characters become normalized Text only in error fields.

The independent oracle compiles the complete unchanged Translations module and
original Types/CommonState/Logging/Error/UTF8/Lang modules. Selected original
PandocMonad get/put/report/toTextM/findDecodingError definitions are byte-exact
projections checked against the pinned source. readDataFile is an observing
effect boundary supplied with bytes/typed/host failures; it does not replace
translation selection or cache behavior. Full default/user-directory routing,
reference document archives, other PandocMonad IO and the remaining application/
reader/writer/Lua/tests are not claimed implemented by this boundary. They,
remaining YAML emission/includes/configuration suites and all calibre MOBI/tests/
additional formats remain within the unchanged full objective.

There are 77,429 independently captured cases: all 107 original YAML files both
through readTranslations and runtime loading/all term lookups; the original
OOXML en-US language-selection setup; earlier syntax/source literals as inputs
only; all Term/value kinds; multiple-document and alias/merge failures; 100
unique direct/parsed Lang inputs, a six-field cache relation matrix, all fallback
ranks and duplicate candidate handling; log/file callback mutations, re-entry
and typed/host failure ordering; BOM/CR transformations; every two-byte UTF8
input, malformed offsets/raw paths and 500 generated operation sequences.
Every expected table, error, warning, state write and file/log effect trace is
actual original Haskell output. A separate identity check covers the loaded
cache/state ownership contract.

Corpus SHA-256:
`646410acf1965d22e0ae4577ba8bf10c339c78926fe82cc73c757630ac851746`.
All 196 source hashes, original file coverage and case/group totals are checked.
The no.yaml symlink hash preserves its Git target spelling while runtime inputs
use the actual resolved original file bytes. Cabal solver caches are excluded
from the source inventory; the frozen plan rebuild is independently confirmed
up-to-date. No existing protected oracle or expectation corpus was changed.

The preceding 172b304 publication passed exact-SHA CI run 38664072385 with all
106 steps completed successfully, no failures and no unfinished step.

Validation: all 1,501,625 tests in 92 suites pass with zero failures, errors or
skips, including 77,430 translation-loading tests and the unchanged 67,269
translation-type tests. The worker remains limited to 2 GiB and recycles after
20 classes. All 95 source/fixture checks and all 234 installed CLI checks pass
(14 FB2, 66 media, 69 format modifiers, 85 math). The installed CLI uses the exact
current core JAR; all 49 complete notices remain byte-exact. All 196 source hashes
and exact corpus bytes were verified against staged Git blobs and the pinned
Pandoc gitlink. The full objective remains active; standard user/default data
file routing and the other remaining Pandoc/calibre work are still pending.


## General ZIP record codec required by Pandoc data/reference archives

The Text.Pandoc.Data branches for reference.docx/reference.pptx/reference.odt
require general ZIP records and decoding, beyond the earlier media-only zero-
timestamp encoder. Added the original archive/entry models, owned bytes and
ordered membership, normalized add/delete/find, raw stored/deflated contents,
PKWARE password verification/decryption and POSIX symlink metadata/target access.
All represented Entry fields and equality are retained, including independent
declared sizes/CRC, extra fields, file comments, versions, internal/external
attributes, encryption verification bytes and arbitrary Integer timestamps.
No new production binary/library dependency was added.

Translated all local/central/digital-signature/end binary productions. Reading
uses local offsets and preserves original validation/error order, size descriptor
handling, the stored descriptor signature scanner's overlap limitations, deflate
stream consumption and ignored multi-disk/end-directory bookkeeping. Central
metadata is retained independently of local metadata and trailing input remains
unconsumed. Writing preserves source UTF8 flags (including its treatment of
encryption metadata), field truncation, full extra/comment/signature payloads,
entry order, DOS timestamp clipping and the entry-count ZIP64 guard. JVM array
capacity is lower than the source's 4 GiB archive threshold; physical oversized
lazy payloads/allocation failures are not claimed represented by ByteArray.

Paths use the pinned FilePath rules rather than filesystem normalization. Raw
Haskell surrogate Chars remain separate from supplementary Chars through a
reversible escape around the existing ASCII structural operators. Added the
original takeDirectory/splitDirectories/dropTrailingPathSeparator operations;
normalization preserves source rooted/trailing separator and Unicode drive-
letter behavior. Every Haskell Char is observed in ordinary/drive-looking paths
on both POSIX and the source's Windows CPP/import adapter.

The retained source has a compiler-dependent CP437 string constant: the pinned
GHC 9.12.2/macOS clang CPP splices its string-gap line continuations, introducing
literal continuation spaces/backslashes/digits. The first comparison caught
116 differences from a standard CP437 table. The Kotlin constant now translates
the actual preprocessed declaration, retained in cp437-clang-cpp.txt and checked
against the original line-splicing transformation. Native expectations were not
changed to hide these failures. This profile preserves the pinned behavior;
equivalence to other preprocessors/platform builds remains unverified. UTF8
filename decoding retains the original lenient replacement behavior.

The independent oracle compiles the complete retained original Zip.hs byte-for-
byte. Its Windows adapter changes only module name, FilePath import and _WINDOWS
selector; the original algorithm bodies stay unchanged. Original binary/zlib/
time modules execute under the frozen plan, whose rebuild is confirmed up-to-
date. The transport catches forced original exceptions and emits exact fields/
bytes, with SHA256 witnesses for full compressed contents, decompressed contents
and complete re-encoded bytes of every original archive to avoid duplicating
large binary inputs in JSON. These witnesses are computed by original execution.

There are 76,920 cases: every one of the 309 retained Pandoc DOCX/PPTX/ODT/EPUB/
ZIP files; all 1,114,112 Haskell Chars in four path contexts (4,456,448 paths);
every 16-bit DOS date with ordinary/extreme time fields; compression choices
and buffer boundaries; arbitrary metadata, field-length wrapping, every
truncation and header mutations, compression/version/encryption/offset failures,
all byte filename inputs, malformed UTF8, stored/deflated signed/unsigned data
descriptors, trailing compressed data, valid/invalid PKWARE, symlink masks,
normalized archive updates/finds and binary/path fuzz inputs.

Corpus SHA-256:
`326003153dbd9fe25fa183af8be98d4d1d92fadb921211aa8d14333cdec229e7`.
All 322 source hashes, case/group totals, full character ranges and original
archive coverage are checked. The earlier media encoder/oracles/expectations
remain unchanged. IO read/write/extraction, generic derived Show/Read instances,
other CPP profiles and physical/lazy capacity behavior remain pending within
the full goal, together with standard Pandoc data/reference archive orchestration
and the remaining Pandoc application/formats/Lua/tests and all calibre work.

The preceding 1cda9f3 publication passed exact-SHA CI run 38666899599: all 107
steps completed successfully, with no failure or unfinished step.

Validation: all 1,578,547 tests in 93 suites pass with zero failures, errors or
skips, including 76,922 ZIP checks. All 96 source/fixture checks and 234 installed
CLI checks pass (14 FB2, 66 media, 69 format modifiers, 85 math). The 2 GiB worker
limit and recycling after 20 classes are unchanged. All 49 complete notices are
byte-exact in the core JAR used by the installed CLI. All 322 source hashes and
exact corpus bytes were verified against staged Git blobs and the pinned Pandoc
gitlink. Original byte/expectation corpora remain unchanged; Git attributes keep
the copied original ZIP source's final empty lines intact. The complete objective
remains active, with Pandoc data/reference orchestration next.


## Pandoc data routing, embedded assets and reference archives

Translated the algorithms in Text.Pandoc.Data: readDataFile,
readDefaultDataFile, getDataFileNames, defaultUserDataDir and the three private
reference-archive builders. The runtime retains separate strict/lazy reads,
existence, installation lookup, environment and clock effects. Embedded and
installed branches execute the original routing rules on both POSIX and Windows.
The user overlay keeps the source relative/parent guard and callback-visible
state; a present override does not silently fall back after a failed read.
Installed MANUAL.txt bypasses the data/ prefix, and missing-file errors retain
the original raw or resolved filename as appropriate.

Packaged all 248 entries returned by the original BakedIn Template Haskell
catalog, preserving every byte and catalog order. This includes the existing
62 templates, the manual, translations, styles and all reference-document parts.
Raw Haskell filename characters stay distinct, including separate surrogate
Chars. Returned asset bytes are owned by the caller. Default DOCX/PPTX/ODT
assembly uses the original fixed 16/49/6 part lists, performs a fresh timestamp
and data lookup for each part, and preserves original ZIP bytes, order and
metadata. Existing user reference archives take the original lazy-read,
parse-and-reserialize branch. The original ZIP codec's documented capacity and
compiler-profile limits still apply.

Added PandocMonad getTimestamp/getPOSIXTime algorithms, the Integer
specialization of Shared.safeRead and raw UTCTime/POSIX picosecond conversion.
SOURCE_DATE_EPOCH uses the original GHC numeric grammar, including parentheses,
whitespace, negative signs and binary/octal/hexadecimal literals; invalid values
fall back to the clock. Arbitrary Integer days/epochs, negative fractions and
leap-second clipping retain source arithmetic. This does not claim a generic
Haskell Read interpreter or all other time-library APIs.

The independent oracle compiles full original Data bodies in four build/platform
variants, with only module/import/CPP selectors adapted. The original BakedIn TH
executes over the pinned data tree. Original timestamp, path-guard, safeRead and
makeCanonical projections are checked against the pinned source; the unchanged
ZIP modules and original Translations modules execute together. Only primitive
file/directory/environment/clock effects are controlled. The frozen Cabal plan
rebuild is confirmed up-to-date; no protected earlier oracle was changed.

There are 5,663 native observations: every embedded asset in four variants,
canonical/raw/user paths, installed lookup and enumeration, recursive directory
IO order, all default-directory existence/failure combinations, reference
assembly and real user archives, malformed archive and typed/host failures,
state-changing callbacks, per-part changing clocks and an error at every part
position. Timestamp lexical/fuzz and raw picosecond cases plus real translation
loading exercise the integrated runtime. All 4,515 earlier captured expectations
remain unchanged after adding the final per-part cases. A separate Kotlin check
verifies payload ownership. The complete byte catalog and all 292 source hashes
are checked in CI.

Corpus SHA-256:
`beca4597651087e9ce045b1662fa96d84c8d2dbab1f80e536e583f57031fde35`.

Directory operations and PandocMonad primitives remain injectable effect
boundaries; native filesystem/environment implementations and remaining monad,
application, reader, writer, Lua and original test work are still pending.
Source SomeException is observed as host failures at these boundaries; exact
platform exception text and a complete GHC asynchronous-exception runtime are
not claimed. Existing template-specific providers remain available separately.
All calibre MOBI/tests and additional formats remain within the full objective.

The preceding 6b7287b publication passed exact-SHA CI run 38670048638.

Validation: all 1,584,211 tests in 94 suites pass with zero failures, errors or
skips, including 5,664 Data checks. All 97 source/fixture checks and 234 installed
CLI checks pass (14 FB2, 66 media, 69 format modifiers, 85 math). The installed
core JAR matches the build output and contains all 248 byte-exact catalog assets,
both indexes and 49 complete notices. Staged Git blobs match all 292 source
hashes, the exact corpus and every embedded asset; upstream gitlinks match the
pinned revisions. The old template packager check now verifies its template
subtree/index, while the new Data check verifies the whole catalog. Only that
packager's hash changed in the old template manifest; every old case byte is
unchanged. All 108 steps in the preceding exact-SHA CI run succeeded. The full
Pandoc/calibre objective remains active.


## TeXMath expression foundation required by the complete HTML writer

The HTML writer's MathML and PlainMath branches require genuine TeXMath parsing
and writing. Retained the complete original texmath 0.13.3.1 release, compatible
with Pandoc's pinned constraint: all 3,762 original source/test files match the
Hackage archive byte for byte, with no Cabal revision edits. The release archive
SHA-256 is recorded in tools/haskell-math-reference/SOURCE.json. Its full license
is retained and packaged. No production Haskell/binary dependency was added.

Implemented all 23 finite Exp constructors with owned nested lists, every enum,
the Operator/Record fields, default environment, source constructor/field order,
iterative Exp equality/order and derived Show. Operator/Record Show and the TeX
intermediate model's Show/Eq are also compared with original execution. Complete
Haskell Read/Data/Typeable and partial/lazy-field behavior are still pending.

Translated all Shared algorithms: immediate-child mapping, bottom-up expression
and list-tail traversals, the two-pass fixTree, empty checks, convertible
under/over handling, Greek/prime/RL-sequence predicates, all style/command/operator/
diacritical/scaler tables and lookups, exact-rational spaces and readLength.
The list visitor applies rewrites to every tail, including the empty tail.
getSpaceChars retains source quirks: even zero/fractional-negative width can
produce a hair space, and the whole-number part follows signed 64-bit Int
conversion. Physical JVM string capacity remains a limit for enormous outputs.

readLength retains decimal-only parsing, exact unit suffixes, source Double
multiplication, ties-to-even Integer rounding and rational unit factors. Native
observations expose GHC's Infinity-to-Integer decoding: overflow yields a signed
2^1024 before division by 18. This behavior is translated rather than clamped or
rejected. The corresponding original GHC Real and float decoder sources are
retained/hashed, separately from protected earlier source inventories.

Translated the full 1,031-row Unicode table in original order, including the
source's last-row preference in both directions. All 14 styles and every
1,114,112 Haskell Char are checked with original sparse lookup outputs; actual
Text conversion separately checks surrogate replacement and supplementary text.
The complete TeX renderer preserves control-word spacing, literal lookahead,
space suppression, group collapsing/trimming and every escape branch. A rope
and explicit tasks avoid recursive or quadratic deep-group rendering.

Implemented all branches of writePandoc. Operator/symbol spacing, text styles,
Unicode variants, sub/superscripts, delimiters, scaling, convertible under/over
and the specialized combining-accent precedence match the original. Unsupported
roots/fractions/matrices/other constructs return Nothing exactly as the source
writer does. The DisplayType argument is unused by the original writer. This
is an expression writer, not a replacement for the pending TeX parser.

The independent oracle compiles the entire original package unchanged under a
frozen GHC 9.12.2 plan. Only JSON transport is supplied. Original TeX/MathML/OMML/
Read parsers process every original .test/.native input: 3,717 native successes
and four native MathML failures. The 722 distinct successful trees seed the
Kotlin expression/helper/output comparisons. This is provenance for defined
input trees, not a claim that all those reader/writer golden suites are ported.
Every original input remains inventoried, including the four native failures.

There are 3,146 independent observations covering all constructors and tables,
record/type instances, styles, symbol spacing, accent priority, seeded expression
and numeric cases, both display modes, all Char mappings/escape decisions and
TeX suffix/group spacing. The first comparison exposed a prime-symbol typo;
later TeX Eq tests exposed singleton recursion. Both Kotlin defects were fixed
without changing native expectations. An additional Kotlin check covers owned
lists and 10,000-level expression traversal/ordering/Show/Pandoc/TeX rendering.
All 3779 source hashes and full original-input coverage are verified in CI.

Corpus SHA-256:
`91e17bff1671e09a31d907dec41580792ac974200138e1490f9d97729245b9f8`.

TeXMath readers, MathML/OMML/TeX/other writers, the full original golden and
roundtrip suites, generic/partial/lazy interfaces, Pandoc's math bridge and the
full HTML writer remain pending. All remaining Pandoc and calibre work stays
within the original objective. The previous 798df7d publication passed exact-SHA
CI run 38672865932 with all 109 steps successful.

Validation: all 1,587,358 tests in 95 suites pass with zero failures, errors or
skips, including 3,147 Math checks. All 98 source/fixture checks and all 234
installed CLI checks pass (14 FB2, 66 media, 69 format modifiers, 85 math). The
2 GiB worker limit and recycling after 20 classes remain unchanged. The installed
CLI uses the exact current core JAR, with all 50 complete notices and all 248
original data assets byte-exact. Staged Git blobs match the exact math corpus
and every one of its 3,779 source hashes; pinned submodule revisions match.
All earlier protected corpora and original source files remain unchanged. The
full Pandoc/calibre objective remains active, with math parsing/writing and the
HTML writer next.


## Plugin classpath repository availability

CI run 38675344577 for f01d4d9 failed during root project configuration, before
compilation or tests: Gradle could not resolve five Kotlin 2.4.21 plugin
artifacts through plugins.gradle.org. The same artifacts currently return HTTP
200 through both Plugin Portal and Maven Central, and a completely fresh local
Gradle user/project cache resolves the original configuration successfully.
Thus the observed failure is consistent with transient repository availability;
it is not evidence of a Kotlin test regression.

Plugin resolution previously used only Gradle's default Plugin Portal. Added
explicit pluginManagement repositories with Maven Central first and Plugin
Portal second, keeping all versions unchanged. This routes Kotlin's published
artifacts directly to Maven Central instead of relying solely on the Portal
proxy. Gradle's official plugin repository documentation supports this setup:
https://docs.gradle.org/current/userguide/plugins_intermediate.html

A second isolated empty artifact/project cache successfully configures the
updated project. Its download log confirms that all five previously missing
Kotlin modules, including POM/module/JAR metadata, resolve directly from Maven
Central. This verifies the resolution path locally; the new exact-SHA remote
pipeline still needs to run.


## Complete finite TeXMath MathML writer and original XML.Light output

Translated every branch of Text.TeXMath.Writers.MathML, including the initial
recursive convertible-under/over transformation, grouped/function-application
handling, all number/identifier/operator/symbol styles, delimiters and fences,
accent classification and vector-arrow substitution, all fraction modes,
scripts, roots, phantom/boxed/cancelled expressions, scaling and arrays. The
writer returns the original XML tree with source attribute/content order; it
does not substitute HTML strings for the MathML model. Explicit work frames keep
finite deeply nested formulas off the JVM stack.

All 1,107 MathML dictionary rows are translated from the retained original
source, with descriptions, forms, priorities, left/right spaces and ordered
properties. Public table order and last-row map construction are retained.
getMathMLOperator tries the requested form, then infix, postfix and prefix,
including the source's repeated candidate when appropriate. Every row, every
lookup form and every Haskell Char are compared with original execution.

Added exact Rational-to-Double conversion and ties-to-even Integer/Int rounding
from the original GHC definitions. Normal/subnormal values, signed underflow,
overflow and signed 64-bit wrapping are retained. MathML widths use the existing
verified GHC floatToDigits/FFFixed specialization, matching printf %.3f, then the
source trailing-zero rule; scales use exact rational percentages. Native numeric
witnesses include raw Double bits, formatted decimals and wrapped percentages.

The writer imports xml 1.3.14, whose output differs from Pandoc's Text-based fork.
Retained the complete original XML release and its license. The original release
Cabal file is used; Hackage revision-2 metadata is retained separately. Added a
separate String-based model with raw CharacterString names, URIs, prefixes,
attribute/text/reference values and arbitrary Integer line fields. QName and
attribute Eq/Ord match source semantics, including prefix-insensitive qualified
names. Nested attribute/content lists are owned. Existing Pandoc XML code and
protected expectation corpora remain unchanged.

Translated all finite Text.XML.Light.Output productions and configurations:
compact/pretty/content/element/top-level output, QName/attribute/CData rendering,
short-empty callbacks, raw/verbatim CData splitting, numeric escapes, indentation,
processing-instruction endings and the original XML header/newline distinctions.
All Haskell Chars are compared for escaping, including unassigned/control/private
characters and surrogate Chars. Raw String fields keep two surrogate Chars
separate from a supplementary Char, including through output witnesses. This
does not claim the XML parser/cursor/proc modules, generic Data/Typeable/Show
interfaces or a full Haskell partial/lazy-string runtime.

The independent oracle compiles complete unchanged texmath and xml source
modules under its own frozen plan. The observing main supplies transport only;
MathWire is a module-name-only copy of the protected earlier adapter. No Kotlin
code runs while capturing expectations. Every one of the 561 original MathML
writer golden files is checked against original output before Kotlin execution,
and CI verifies the same exact original golden expectations. Additional inputs
come from the earlier independently captured expression corpus in both display
modes, all constructors/styles/accents, array alignments, function application,
nested scaling/duplicate attributes, generated exact-rational boundaries, raw
XML records and failing/conditional printer predicates.

Serialized MathML views use exact length/SHA256 witnesses over big-endian
32-bit Haskell Chars, alongside complete XML tree fields. They witness original
raw character output without UTF8 normalization, while avoiding repeating large
formatted strings in every JSON case. Generic XML observations retain complete
raw codepoint arrays. Original UTF8 golden files are verified against their exact
character sequence, including the header and final newline.

There are 9,700 original observations and 3,791 source hashes. An additional
Kotlin check verifies owned XML lists, nested duplicate-attribute order and exact
compact rendering of a 10,000-level formula. Full TeX parsing, the Pandoc math
bridge, remaining TeXMath formats/goldens, HTML writer and the rest of Pandoc and
calibre remain pending within the unchanged complete objective.

Corpus SHA-256:
`9cd664b8492fdad77302572b5a7519a2985209092152268fdef3ca38aa59e022`.

Validation: all 1,597,059 tests in 96 suites pass with zero failures, errors or
skips, including 9,701 MathML/XML checks. All 99 source/fixture commands in CI and
234 installed CLI checks pass (14 FB2, 66 media, 69 modifiers, 85 math). Worker
heap/recycling limits are unchanged. The installed CLI uses the exact current
core JAR; all 51 complete notices, 248 data assets and both indexes are byte-
exact. Staged Git blobs match the complete new corpus and all 3,791 source hashes;
all pinned submodule revisions match. No protected earlier corpus/oracle changed.

The preceding plugin-resolution repair d209043 was published separately after
fresh-cache verification. Its exact-SHA CI run 38677372152 was still running at
publication of this writer stage. The failure of the earlier f01d4d9 pipeline
was diagnosed before compilation, not hidden by claiming its tests had passed.
The full objective remains active.


### TeX macro preprocessing and Unicode-to-TeX mapping

Translated the complete finite algorithms of Readers.TeX.Macros and
Unicode.ToTeX from the retained texmath 0.13.3.1 release. Macro parsing uses the
existing continuation-based Parsec engine, preserving consumed/empty replies,
backtracking, remaining input, source names/line/column, unchanged arbitrary
Integer user state, error-message order and pretty errors. Generic parser input
retains raw surrogate Chars; Text entry points apply original Text normalization.

All newcommand/renewcommand/providecommand, DeclareMathOperator and
newenvironment/renewenvironment variants are supported, including optional
arguments, 0..9 argument counts, canonical Show, comment/delimiter rules,
parameter substitution, escaped placeholders, nested same-name environments and
later-definition shadowing. Expansion preserves the source 2*N+1 pass limit,
macro-fired flag and complete-original-input fallback for cycles. Nonmatching
macros and an empty macro list retain their distinct comment behavior. Truncated
comments/backslashes and malformed definitions retain original unconsumed input.

The complete 2,755-row Unicode mapping is generated in original order, with
all command/package pairs, categories and comments. Character lookup retains the
last duplicate record; command lookup retains first-record/first-command priority
and contains all 2,832 original commands in source Text order. Base commands take
precedence over package choices; package order, accent/radical empty groups,
variation-selector handling, Unicode font fallback and escaping match source.
The list renderer implements original foldr renderTeX with shared suffix lookahead
using the existing explicit work stack and rope. The single-token renderer and
protected earlier expectations are unchanged in behavior.

Original GPL notices and the mapping's additional LPPL clause-6 notice are
retained. The modified Kotlin mapping has distinct names and a change/support
notice; the complete original mapping and release remain vendored. The official
complete LPPL 1.3c text and mapping notice are included in distributed JARs.

The independent oracle compiles unchanged complete texmath with a separate frozen
Cabal plan. Its main transports original results only. There are 26,415 original
observations and 3,822 source hashes, including complete record/command tables,
all 1,114,112 Haskell Chars for classification and conversion in seven package
environments, every record's package choices, canonical macro constructors,
arguments/defaults, Unicode/raw names, Int64 source-position wrapping, errors,
comments, malformed prefixes, cycles, transitive chains and deterministic fuzz.
Serialized range witnesses hash exact output as big-endian 32-bit characters.
All 149 original TeX input sections are retained byte-exact and execute original
macro preprocessing; this is not a claim of full TeX reader/golden completion.

All 26,415 native comparisons and an additional ownership/depth test pass. The
latter checks immutable snapshots/maps, 1,500 nested groups and adjacent command
spacing. The earlier 3,147 TeXMath checks also pass after list rendering was added.
Corpus SHA-256:
`195bafae516e70601453b1ccd3888bee4b5f25be1e1e53ad21c965b2c075f2c2`.

The complete TeX reader and Commands tables, Pandoc math bridge, remaining
TeXMath readers/writers/goldens, complete HTML writer and all other remaining
Pandoc/calibre work remain pending under the original full objective.

The exact-SHA CI runs for d209043 and 7c55eaf are now verified successful, with
all 110 and 111 recorded steps successful respectively, including full tests,
source checks and installed CLI checks.


Validation: all 1,623,475 tests in 97 suites pass with zero failures, errors or
skips, including 26,416 new macro/Unicode checks. All 100 source/fixture commands
in CI and all 234 installed CLI checks pass (14 FB2, 66 media, 69 modifiers and
85 math). The existing 2 GiB heap and worker-recycling policy are unchanged.
Installed CLI/core JAR bytes match; all 53 complete notices, 248 data assets and
two indexes are byte-exact. The staged new corpus and all 3,822 recorded source
blobs match their independent hashes; all three pinned upstream revisions match.
No protected earlier corpus or oracle changed. The full objective remains active.


### Complete finite TeX formula reader and Commands

Translated the complete Text.TeXMath.Readers.TeX algorithm from texmath
0.13.3.1. This is the TeX formula reader, distinct from Pandoc's still-pending
full LaTeX document reader. It performs original macro preprocessing, expression
parsing and recursive binary-symbol reclassification, returning immutable
expression lists or complete original error text. Production uses Kotlin only.

All source productions are represented: command-boundary checks and longest
trie matching; ignorable comments/labels/tags/style indicators; decimal numbers,
primes and bare/combined scripts; convertible and fixed limits; explicit,
implicit and middle delimiters; fractions/binomial/genfrac; unary/colored/styled
expressions; math operators and negation; all array/AMS environments and column
specifications; nested text/inner-math, ligatures, accents and character commands;
spacing with arbitrary Integer rational arithmetic; complete siunitx quantities,
ranges, lists, angles, uncertainty/exponents and unit powers; extensible arrows.
The original alternative/try/label/position semantics are retained, including
malformed-input behavior. The array-column error labels found by differential
checks were corrected by restoring the original separate alternatives; no
original expectations were weakened or generated from Kotlin.

All six Commands tables are translated mechanically from all 443 original rows:
26 style operations, seven text operations, 30 enclosure entries, 20 operators,
185 symbol overrides and 175 SI unit entries. Original duplicate keys retain
last-entry priority; overrides take precedence over the full Unicode symbol map.
Maps use original Text ordering and immutable snapshots. The source's special
mathbf double-style wrapper is retained. Generated text data retains all 26
negation exceptions, 24 literal commands and 155 accent exceptions, including
multi-codepoint values. All remaining text/parser logic is translated directly.

The complete reader uses the existing continuation-based Parsec implementation;
recursive parser construction and tree processing use explicit work stacks.
Operator-name flattening also uses a work stack while preserving the original
rejection of nested grouped values. Exact Rational units, signed machine-Int
column repetition overflow, source input versus expanded-input snippets, caret
placement, message ordering, original quote/ligature distinctions and Text
surrogate normalization are retained.

A separate independent oracle compiles the complete unchanged retained texmath
package. Its hidden Commands module is compiled from a byte-identical local
copy; MathWire is a module-name-only copy of the protected earlier adapter.
The observing main calls original readTeX, Commands and Exp Read/Show only. All
149 original TeX input sections are included verbatim. For all 69 original native
goldens, the original Exp Read instance independently parses the golden and its
tree/Show is compared against original readTeX before Kotlin execution. Other
output formats provide reader inputs without claiming their writers are ported.

The 25,581 native observations include exact complete trees, Show and full error
text; every command and boundary, every SI unit, all environments, column and
text/spacing grammars, macro integration, truncation/generated failures and all
1,114,112 raw Char inputs passed through original Text/readTeX. Character-range
witnesses frame each original successful Show or complete error with its length,
then hash big-endian 32-bit characters and retain success/failure counts. This
preserves every character's output without storing a million repeated trees.
A native case verifies 1,500 nested styles inside operatorname; an additional
Kotlin check verifies immutable maps and 1,500 nested formula groups.

There are 3,826 source hashes. Corpus SHA-256:
`09a80d4a1b20665e678f0b9110190faecf8a3849bb844051ab418803e3169e94`.
All 25,582 reader/table/depth checks pass. The frozen native build and all source
imports remain separate from production. The exact-SHA CI for aa7946c passed
all 112 recorded steps, including complete tests/source checks/installed CLI.

Pandoc's math bridge, remaining TeXMath readers/writers/goldens, the full HTML
writer and remaining Pandoc/calibre scope still require their complete ports.
The original full objective remains active.


Validation: all 1,649,057 tests in 98 suites pass with zero failures, errors or
skips. This includes 25,582 new reader/table/depth checks and every-Char reader
witnesses. All 101 CI source/fixture commands and all 234 installed CLI checks
pass (14 FB2, 66 media, 69 modifiers, 85 math). Heap/recycling limits are unchanged.
The final core JAR's 1,961 production class files are byte-identical to the
full-tested/CLI-tested build after generator EOF normalization; every native
observation line is also byte-identical. Installed CLI/core JAR bytes match,
including all 53 complete notices, 248 data assets and two indexes. Staged corpus
bytes and all 3,826 source blobs match recorded independent hashes. All pinned
upstream revisions match, and no protected earlier oracle/corpus changed.


### Complete Text.Pandoc.Writers.Math bridge

Translated both exported conversion functions and the two re-exported default
URLs from the complete pinned original Writers.Math module. convertMath parses
TeX, maps InlineMath/DisplayMath to the corresponding TeXMath display type, and
returns either a fallback Inline or a generic writer result. A successful generic
result uses the existing shared Deferred cell: the writer is not called until
its value is demanded, runs once, retains object/null identity and memoizes host
exceptions. The parsed finite expression list remains immutable. A parse failure
reports CouldNotConvertTeXMath with the original error and preserves the exact
raw formula between single or double dollar delimiters.

texMathToInlines uses the complete previously ported TeX reader and writePandoc.
Supported formulas return original Pandoc Inlines; a successful parse whose
representation is unsupported reports the same warning with an empty reason
and returns a fallback Str. Empty successful output remains empty. Exceptions
from a writer or logging callback propagate instead of becoming fallback warnings.
Logging uses the original report algorithm already ported in PandocState, so
verbosity filters output without filtering the accumulated log, and re-entrant
callbacks preserve their state changes and the original prepend order. The original
host failure on the oversized-space case remains a host failure in Kotlin, with
its state and log effects compared across both runtimes.

The independent native oracle compiles complete unchanged Writers.Math and
texmath 0.13.3.1. It reuses byte-identical exact state/Shared/Citeproc projections
from the protected earlier state oracle; Options contains only the byte-exact
two URL definitions imported by this module. Full original modules and notices
remain retained. The observer supplies the state instance and pure writer demand
instrumentation only, not conversion/report algorithms. It forces each returned
generic value twice, including exceptions, and observes original sharing.

The corpus contains 98,180 independent original observations and 3,794 source
hashes: all 335 Math nodes from the complete preserved original Pandoc Native
corpus, all 149 original TeX inputs through both conversion functions in both
display modes, and all 23,332 distinct explicit inputs from the earlier complete
TeX-reader corpus through both public paths and both modes. Additional cases
cover all verbosity levels, seeded logs, all logging callbacks, nested reporting,
typed/host callback errors, supported/unsupported/failed/empty formulas, generic
Nothing/constant/throwing/Pandoc/probe writers, undemanded versus demanded
results, repeated forcing, Unicode Text normalization and exact fallback/error
strings. Every observation includes full state fields, chronological logs,
output/writer events, AST JSON/Show or generic result, and exception status.

All 98,180 native comparisons plus a Kotlin ownership/identity check pass. The
latter verifies deferred object/null identity, one execution, memoized exception
identity and immutable fallback lists. Corpus SHA-256:
`e4363fbedec716c209874f04da0bfead06ace03b9b94aedceed368e658ce66b7`.
No earlier protected oracle or corpus was changed.

The complete HTML writer, remaining TeXMath formats, other Pandoc formats and
application/property-test paths and all calibre work remain in the original scope.


Validation: all 1,747,238 tests in 99 suites pass with zero failures, errors or
skips, including 98,181 bridge/demand/identity checks. All 102 source/fixture
commands and all 234 installed CLI checks pass (14 FB2, 66 media, 69 modifiers,
85 math). The existing heap and worker-recycling settings are unchanged. Installed
CLI/core JAR bytes match; all new bridge classes, 53 complete notices, 248 data
assets and two indexes are verified. Staged new corpus bytes and all 3,794 source
blobs match, including exact original upstream Git blobs; all three upstream
worktrees are clean at pinned revisions. No earlier protected oracle/corpus
changed. Exact-SHA CI for 2103253 is verified successful: all 113 recorded steps,
including full tests, source verification and installed CLI checks.

The full objective remains active. The next required format work is the complete
HTML writer and its original tests, not a reduced HTML rendering API.

## Date normalization and metadata language setup for the HTML writer

The full HTML writer also needs `Shared.normalizeDate` and
`Writers.Shared.setupTranslations`. These are now implemented as
`DocumentShared.normalizeDate` and `WriterShared.setupTranslations`. This stage
provides those actual dependencies; the eleven HTML exports, original HTML
writer tests, EPUB/slides integration and the rest of the original objective
remain pending.

Date normalization preserves the original eleven format alternatives and their
order, GHC TimeLib 1.14 numeric padding, required/optional whitespace, ASCII digit
grammar, case-insensitive English month matching using GHC Unicode mappings,
1969–2068 two-digit-year pivot, Gregorian validity and 1601–9999 year restriction.
Year-only and year/month inputs default the missing fields to January/1. Day
numbers parsed with `%e` retain GHC Int64 wrapping before calendar validation.
The implementation specializes the actual `normalizeDate` grammar; it does not
claim a general port of `Data.Time` or its entire test suite.

Language setup uses the existing exact metadata stringification, `toLang` warning
and `setTranslations` state operations. It preserves lazy file loading, loaded
equal-language tables, callback state mutations, report order and typed/host
failures. The original default constructor places `US` in `langScript`, with
`langRegion = Nothing`; this is intentionally preserved, including the cache
equality consequence relative to a parsed `en-US` tag.

The frozen `tools/haskell-writer-environment-reference` compiles byte-exact
projections of those original bodies, their lookup/stringification dependencies
and `setTranslations`, with unchanged original state/report/Lang modules. Its
state instance and JSON/IORef transport only observe effects. All projection
bodies are checked against the pinned upstream source before corpus validation.
The native executable builds successfully again after freezing dependencies.
The complete original TimeLib 1.14 release, including its tests and license, is
retained byte-for-byte: 101 files match the original release archive, SHA-256
`dfa0fe7cc9728e20d123a067161fa23ee27a5b180d58fa09d7738cb8ad737f3e`.
Its full notice is also included in the distributed Kotlin JAR.

All 61,878 independent native comparisons and the Kotlin cache/state identity
check pass. They include metadata from every one of the 266 accepted original
Pandoc documents, 144 original metadata representations, all eight original Lang
unit inputs, 39 original date-metadata strings, 7,756 distinct original inline
nodes, 30,036 year-boundary cases, 8,568 Gregorian calendar cases, all 100 two-digit
years across 1,200 month/day combinations, 7,776 month-name/padding cases, 1,216
whitespace cases, 40 integer overflow cases, 29 malformed/Unicode cases, 3,000
mutations and 1,800 language-cache/verbosity/report-callback cases. State/effect
comparisons include all thirteen common-state fields and every put/output event.
Corpus SHA-256:
`99b1125c32b6d3ac165d8093f798d0b5432f84141f958fb6dfdb890f9f99b72b`.
The new source checker verifies 133 source hashes and is included in CI. No
previous protected oracle or expectation corpus has changed.

Validation: all 1,809,117 tests in 100 suites pass, with zero failures, errors or
skips, including all 61,879 new native/identity checks. All 103 CI source/fixture
commands and all 234 installed CLI comparisons pass (14 FB2, 66 media, 69 format
modifiers, 85 math). Existing heap limits and worker recycling are unchanged.
Installed/core JAR bytes match; all three affected production classes, 54 full
license notices, 248 original data assets and both resource indexes are verified.
All 133 new corpus source hashes match staged/original Git blobs, the staged
corpus is byte-identical, and all 101 original TimeLib files match both the
release archive and staged Git blobs. No earlier protected oracle/corpus changed;
all upstream worktrees remain clean and pinned. Exact-SHA CI for ce152421 is now
verified successful with all 114 recorded steps successful.

The full objective remains active. Next is the complete stateful HTML writer,
all eleven exports and every original unit/integration test. The existing
`tools/haskell-writer-html-reference` covers only shared helpers; it cannot serve
as the full HTML module oracle. The installed original Pandoc 3.12.1 can provide
CLI expectations, while API/state/structured-markup comparisons require compiling
the actual complete module with its original dependencies. No other remaining
Pandoc or calibre work is removed from scope.

## Stateful HTML writer and original HTML tests

`HtmlWriter` implements all eleven `Text.Pandoc.Writers.HTML` exports: HTML4/5
strings and structured markup, EPUB2/3 strings, S5, Slidy, Slideous, DZSlides,
Reveal.js and `tagWithAttributes`. The Kotlin session retains the original
block/inline/table/note traversal and state, sections/TOC/slides, math methods and
MathML annotations, template variables and defaults, raw-format gates, URI/media
classification, dimensions, email obfuscation and attribute warning order.
`HtmlWriterMarkup` retains Blaze tree structure, including external script text
and structural syntax highlighting. Converted MathML remains deferred until its
markup is demanded. `MediaBagUri.parseReference` implements the original relative
reference grammar used by media classification.

The new `tools/haskell-html-writer-reference` links the **complete unchanged**
pinned Pandoc library and pandoc-types, rather than the earlier extracted HTML
helpers. It invokes every exported writer API and observes output and the public
chronological log. Sequence requests share the actual PandocPure common state.
The existing separate foundation suites retain their full common-state checks;
this oracle does not replace hidden library modules to inspect their state.
The complete original HTML unit-test module runs unchanged. `OriginalHelpers.hs`
changes only the original helper module declaration, checked byte-for-byte; its
adapter additionally serializes original inputs and expectations. All seventeen
original assertions and their seventeen observation checks pass natively. All
sixteen original old-style HTML/literate/S5 goldens match the complete library.

The native library's initial `-O2` build was terminated only after a process
sample proved 39.8 GB physical memory and GC in BibTeX. Library-only `-O0` avoids
that compiler resource failure without changing algorithms, modules or tests.
The complete build succeeded and a repeat build with committed dependency freeze
returned `Up to date`. Other dependencies and the observer retain `-O2`.

The independent corpus contains 6,732 comparisons and verifies 798 source hashes:
17 original HTML units, 16 original fixed goldens, all 266 accepted original AST
documents through eleven writer variants (2,926 cases), 264 wrapping/prefix/quote
cases, 18 span/CSL cases, 462 raw-format/math gates, 108 email cases, 528 media
cases, 528 section/slide/note cases, 24 figures, 135 highlighting cases, 288 math
cases, 180 template contexts, 7 original default templates, 66 ASCII entry-point
cases, 66 nested-note/state cases, 1,088 attribute cases and 11 shared-runtime
sequences. Corpus SHA-256:
`226adb86760c29958cda9c83b7570a6fe219180f9c440d5d1aef76467b5df223`.
All 6,733 HTML tests (including thirteen original source examples in one method)
pass. The first independent run exposed two defects: pretty-printing MathML
instead of using the original compact XML configuration, and setting Reveal's
conditional `scrollProgressAuto` unconditionally. Both were fixed against the
original source without changing expected results. All 27,031 formatter tests
also check the new structural HTML outputs against the original native formatter
expectations where applicable.

The installed CLI exposes HTML/HTML4/HTML5 and all five slide targets, using the
Kotlin library and original embedded templates. Writer-facing options include
wrapping, TOC/depth, figure/table lists, numbering/offsets, sections, slide level,
quotes, identifiers, reference locations, caption positions, DPI, ASCII,
highlighting, custom templates and partials, repeated/JSON variables, CSS and
header/body includes. Output extensions are applied, warnings use the ordinary
Pandoc log, and application variables include source/output files, version,
current directory and DZSlides core. Sixteen fragment/standalone defaults match
both output and stderr of installed original Pandoc 3.12.1 across all eight CLI
target names, including HTML aliases and all slide variants.

Validation: all 1,815,850 tests in 101 suites pass with zero failures, errors or
skips (7m 4s). All 104 CI source/fixture commands pass. The installed CLI passes
all 234 prior comparisons and 141 new HTML comparisons (375 total), including
the 17 original units, all 16 fixed goldens and four literal original CLI
ordering/newline observations. The sixteen goldens also pass with the unchanged
original `.native` files as CLI inputs. Literal CR is preserved by the byte-based
CLI harness. Unsupported library extensions are checked against the original CLI
rejection (including exit 23), rather than relaxing the format registry. Number
offsets and standalone output retain the original CLI's flag ordering and final
newline behavior. Installed/core JAR bytes match; all four HTML production
classes, 54 full notices, 248 original assets and both indexes are verified.
All 798 new source hashes match staged/original Git blobs, including the original
`no.yaml` symlink and its target. The staged corpus is byte-identical, the helper
module substitution is exact, all three upstream checkouts remain clean/pinned,
and no earlier protected oracle/corpus changed. Exact-SHA CI for 9022b7c is now
verified successful with all 115 recorded steps successful.

The full objective remains active: original command tests include at least 158
explicit HTML-family target cases whose other readers, resource embedding and
application transformations still need porting; inferred targets are additional.
This stage does not establish full Pandoc CLI parity. Every other remaining
Pandoc reader/writer, application/Lua/property test, then calibre MOBI and its
original tests, and other calibre formats absent from Pandoc remain in scope.
Next is the HTML reader and its complete dependency boundary, starting with the
MathML semantic reader and all 576 untouched original reader goldens. Existing
XML.Light parsing can be reused, with the original permissive forest/first-root
semantics retained. The HTML reader has 69 ordinary original assertions, its
original QuickCheck round-trip property (default 25 successes), and the separate
`html-reader.html` -> `html-reader.native` legacy golden; none is waived.

## MathML semantic reader and legacy XML input

`MathMLReader.readMathML` implements the complete pinned
`Text.TeXMath.Readers.MathML.readMathML` path in Kotlin, with its original
expression constructors, inherited attributes/styles, operator dictionary,
stretchy fence/script reordering, multiscripts, tables, annotations, selection
and error behavior. The complete 2,125-entry entity table comes directly from
the unchanged native EntityMap, including the original epsilon/varepsilon
mapping. Reader scopes and expression continuations run on an explicit queue;
spacelike classification and delimiter matching use explicit stacks. Finite
nested input does not require increasing the JVM stack or starting a thread.

This dependency needs **xml-1.3.14's** permissive `Text.XML.Light.Input` and
`Lexer`, implemented separately as `LegacyXmlInput` and `LegacyXmlLexer`.
The existing `XmlLight` parser implements a different upstream XML module and
cannot substitute for it. Legacy input preserves forests and first-root
selection, truncated/unquoted tags, namespace/ancestor-close resolution, unknown
references, CDATA/DTD content, original text merging and arbitrary-precision
line numbers. Raw Char strings retain surrogate code points; Text input uses
the original scalar replacement, and ByteString input maps octets directly.
Polymorphic source adapters and the deferred custom scanner are also retained.
No earlier parser or format behavior was replaced.

`tools/haskell-mathml-reader-reference` links the complete unchanged texmath
0.13.3.1 and xml 1.3.14 libraries. The hidden EntityMap is copied byte-for-byte.
The separate test executable compiles the complete unchanged original
`test-texmath.hs` with every registration intact. Its selected MathML tests run
all 576 original reader goldens and regression 252: **577 native assertions
pass**. Each original input and literal expected golden is retained and checked
against the original reader and original `Read Exp`; no expected output is
computed by Kotlin. A successful native rebuild with the dependency freeze
returns `Up to date`.

All 18,743 independent MathML/XML observations pass, including all 577 original
inputs and their XML trees, every entity through all five token categories,
operator positions/flags, inherited styles, script reordering, table padding,
annotation effects, namespace/line/lexical boundaries, all 256 octet values,
raw versus Text surrogate behavior, custom-source scanning, 2,500 XML mutations,
2,500 MathML mutations and five finite inputs nested 1,500 levels deep.
Seventeen aggregate witnesses compare the complete reader result over all
1,114,112 Char values; these are 17 aggregate tests, not a claim of that many
individual JUnit cases. Corpus SHA-256:
`c6c4f96502e4c0cba5f93823bd35bef8f5f8562f05145300b8c95a2c0ad5af1c`.

The initial independent run exposed nine numeric-reference differences:
Haskell `reads` retains trailing whitespace while `readMaybe` permits it.
The lexer now requires the original empty remainder; 351 additional numeric
remainder comparisons pass. Deep-input checks then exposed two JVM stack
overflows. Queued continuations and iterative predicates/fence processing fix
them without altering any expected output or test input. All 105 CI
source/fixture commands pass. All 3,784 new source hashes match staged Git blobs;
the staged corpus and copied EntityMap are byte-exact, older protected corpora
and oracles are unchanged, and all three upstream checkouts remain clean and
pinned. The installed/core JAR bytes match and include all five new primary
classes, the full entity table, 54 complete notices and 250 Pandoc assets/index
files. All 375 installed CLI comparisons pass (14 FB2, 66 media, 69 extension,
85 math and 141 HTML cases), along with the workflow smoke checks. Exact-SHA CI
for d13abce is verified successful (117 successful steps).

Full validation: all 1,834,593 tests in 102 suites pass with zero failures,
errors or skips (8m 55s). No test classes or cases were disabled, and the
existing 2 GiB per-worker heap and sequential worker recycling are unchanged.

The full objective remains active. This is a dependency for the HTML reader,
not completion of that reader or a new standalone Pandoc input format. Next is
the remaining MathML-to-TeX bridge and HTML reader, including its 69 original
ordinary assertions, original round-trip property and legacy golden. Every
other remaining Pandoc reader/writer and application/Lua/property test, followed
by calibre MOBI with its original tests and other calibre formats absent from
Pandoc, remains in scope.

## Complete expression-to-TeX writer and original round-trip baseline

`MathTeXWriter` implements all three public
`Text.TeXMath.Writers.TeX` functions: `writeTeX`, `writeTeXWith` and
`addLaTeXEnvironment`. It retains the complete expression traversal, ordered
package selection, default environment, convertible operator scopes, grouped
WriterT output, prime/script handling, narrow/wide accents, extensible arrows,
fences/binomials/genfrac, matrix/cases/aligned/array selection, substacks, exact
row endings, text escaping, styled Unicode reversal and rational space commands.
Space conversion retains floor followed by original Int64 wrapping. An explicit
queue executes writer steps in source order and snapshots a group only after
its child steps; expression depth does not consume the JVM call stack. This
uses the existing Kotlin TeX-token renderer and Unicode mapping, with no upstream
program as a production dependency.

`tools/haskell-tex-writer-reference` compiles the complete unchanged texmath
library and the complete original `test-texmath.hs`, preserving all registrations.
All 561 original TeX writer golden assertions and all four original TeX-output
regressions pass natively (**565 tests**). The three OMML-input regressions are
also covered at the writer boundary through original-reader ASTs; their input
reader remains in scope. The fourth regression uses TeX input. Every original
input and literal expected output is preserved, and generated results are
checked against those expectations before capture. The native dependency freeze
is retained and a repeat build reports `Up to date`.

The optional original **TeX round-trip group already fails in unchanged
upstream**: 217 passes and **344 failures** among 561 tests. The unmodified
native runner is executed without accepting or editing its goldens. All 561
original goldens, actual original reader/writer results and individual original
assertion verdicts are retained. Kotlin checks the actual result, original tree
and verdict for every case, reproducing all 217 successful and 344 unsuccessful
round trips. The native aggregate failure count is checked independently. Green
Kotlin observation tests do **not** mean those upstream assertions were repaired
or that the original round-trip group passes; no original test is skipped.

All **32,292 independent observations pass**: the 565 original output tests;
all 561 original TeX round trips with their baseline verdicts; 3,156 successful
original `.test` inputs through the writer; all 577 original MathML inputs
through the reader-to-TeX pipeline; 7,293 original writer trees with thirteen
package lists; all 2,755 Unicode records in default, declared and reversed
package contexts (8,265 cases); 3,072 script/accent/arrow/scope cases; 6,480
delimited-array/row cases; 180 binomial/fallback cases; 350 text/style cases;
252 rational-floor/Int64 boundaries; both environment wrappers; 1,500 seeded
expression/scope cases; ten 1,500-level finite trees; and seventeen aggregate
witnesses over all 1,114,112 Char values in four leaf constructors. These are
17 aggregate tests, not 4,456,448 individual JUnit tests. All 3,160 original
`.test` inputs are accounted for: four reader-only MathML failures are preserved
in the manifest and exercised by the 577 reader-to-writer pipeline observations.
Corpus SHA-256:
`98c9f91e739ef2592f62619047f40b0fc783ecf8e1bbdde1d0028d38462fe47c`.

All 106 CI source/fixture commands pass. All 3,784 source hashes match staged
Git blobs, the staged corpus is byte-exact, earlier protected corpora/oracles
are unchanged and all upstream checkouts remain clean/pinned. Installed/core
JAR bytes match; the TeX writer, all 54 full notices, 250 Pandoc data/index files
and the unchanged MathML entity table are verified in the installed artifact.

Full validation passes all 1,866,885 tests in 103 suites, with zero failures,
errors or skips (9m 2s). The original round-trip failures described above remain
explicit native baselines; none is represented as an upstream assertion pass.
All 375 installed CLI comparisons and the workflow smoke checks pass. Worker
heap/recycling limits remain unchanged.

The full objective remains active. Next is the complete HTML reader and its
remaining parser/state/table/helper boundary, including all 69 ordinary original
assertions, its original round-trip property and its legacy golden. The
MathML-reader/TeX-writer dependency is now available. Remaining dependencies
include the tag-stream adapter and HTML state, omitted-tag/category/matching
helpers, table parsing, metadata/code/URI helpers, raw LaTeX inline parsing and
`Text.Pandoc.Parsing.Math` (HTML imports its delimiter parsers through Parsing).
Other Pandoc readers,
writers, application/Lua/property tests, then calibre MOBI and all its original
tests and other calibre formats absent from Pandoc remain in scope.


## Complete mathematical delimiter parsers and reader-option capability

`MathParsing` implements both public `Text.Pandoc.Parsing.Math` parsers,
`mathInline` and `mathDisplay`, with the complete dollar, single-backslash and
double-backslash alternatives. The port retains extension guards outside each
backtracking delimiter parser, nonempty content, leading/trailing space and
following-digit gates, whole nested TeX groups, escaped braces, preserved
comments and the blank-line restrictions. It implements `Shared.trimMath`
exactly, including the single retained whitespace character after a trailing
backslash. Deferred parser continuations retain finite deep/long inputs without
using recursive JVM calls.

`ReaderOptionsAccess` retains both `extractReaderOptions` and the overridable,
parser-valued `getOption` capability. `ReaderOptionGuards` implements both
`guardEnabled` and `guardDisabled`; callers can retain arbitrary reader state
and override getter effects. `ParseError` now exposes both raw Haskell-Char Show
and `Text.pack . show` views for source names, preserving individual surrogate
characters and replacing each invalid scalar when converted to Text. Its existing
UTF-16 facade remains available.

The native oracle links the complete unchanged pinned Pandoc/pandoc-types
libraries. It compiles the complete original HTML and Markdown reader test
modules without replacing their implementation. A helper adapter invokes the
original assertions and separately serializes original inputs and expectations;
the copied original helper differs only in its module name. The entire HTML
module passes **69 ordinary assertions plus the original QuickCheck property**
(25 generated cases), with 69 additional serialization checks: 139 native tests.
The selected original Markdown math-groups subtree passes **138 ordinary
assertions** and 138 serialization checks: 276 native tests. These counts correct
the earlier ledger's 70 ordinary HTML assertions: 70 included the property.
The full original Markdown module is compiled, but its other groups are not
claimed as run by this oracle.

All **10,304 independent parser observations pass**. They include all 48
original HTML math inputs, all 138 original Markdown math inputs at the shared
parser boundary and 12 later math spans; all eight extension configurations;
all five original UpdateSourcePos stream instances (Text, Char list, strict and
lazy ByteString, and Sources); consumed/empty replies, values, remaining chunks,
source positions and complete error messages; default and custom stateful,
consuming and failing option getters; guard/repetition/lookahead/backtracking
composition; source chunk jumps/empty spines, raw source names and Int64 bounds;
all byte values and raw surrogate characters; 3,000 seeded group/comment cases;
and four deep/long inputs, including 1,500 nested groups and 50,000 characters.
Seventeen aggregate witnesses cover every one of the 1,114,112 Haskell Char
values through both inline and display parsers (2,228,224 parser results, not
that many separate JUnit tests).

The first parity run exposed 72 Text-rendered error differences for raw source
names; parser values, positions and message lists already matched. The fix adds
the distinct raw-Char/Text error views. All original 10,304 requests and expected
results were checked unchanged after independent raw Show observations were
added; no expected result was weakened. The 307-source manifest and deterministic
corpus hash are checked in CI. Corpus SHA-256:
`2c8204315f196db2fb0c13031508e76295d44282d89688d5c9501b01ee62c609`.
The dependency freeze is retained and a repeated native build reports `Up to date`.
All 107 CI source/fixture commands pass. The 307 source hashes match staged Git
blobs, the corpus and copied helper are byte-exact, earlier protected corpora and
oracles are unchanged, and all three upstream checkouts/tags/gitlinks remain
clean and pinned. Installed/core JAR bytes match; the new parser classes, all 54
full notices, 250 Pandoc data/index files and the MathML entity table are verified.

Full validation passes **1,877,189 tests in 104 suites**, with zero failures,
errors or skips (7m 16s). Existing worker heap/recycling limits and all earlier
test registrations remain unchanged. All 375 installed CLI comparisons pass
(14 FB2, 66 media, 69 extension, 85 math and 141 HTML cases), along with the
installed workflow smoke checks.

This completes the shared math-parser module, not the HTML or Markdown reader.
Their full AST-level Kotlin tests and the HTML round-trip property/legacy golden
remain pending. The full objective remains active: complete the remaining HTML
state/tag/table/helper boundary and every other Pandoc reader/writer and
application/Lua/property test, then calibre MOBI and its original tests and the
other calibre formats absent from Pandoc. Exact-SHA CI for d1b8c70 and fb2f08f
is verified successful (118 and 119 successful steps respectively).


## Complete HTML tag parsing, categories and typed parser streams

`HtmlTagParsing` implements all fourteen functions exported by
`Text.Pandoc.Readers.HTML.Parsing`, plus the `TagOmission` type. The port retains
position-token skipping, nonconsuming failure with the skipped token's error
position, explicit versus implicit tag closing, td/th cross-closing, all original
optional-tag/category rules, all three opening/closing omission policies,
caller-supplied content/predicates and monoid concatenation, whitespace/comment
skipping and the original backtracking boundaries. Attribute handling preserves
missing versus empty values, first-match lookup, one-pass xml:lang/data-prefix
normalization, the full standard-attribute exception set, first-wins duplicate
removal, empty id precedence, Unicode class words and epub:type classes.

`HtmlTagCategories` retains all eight exported sets/lists, including the duplicate
`dt` in the original grouping-content list; callers cannot mutate the shared
catalogs. `HtmlTagMatching` implements the Tag/Text form of TagSoup's asymmetric
`(~==)` and exact derived Show for all six tag constructors. This includes the
original comment/warning empty-left behavior, empty-name/value attribute matches,
ordered duplicate attributes and parenthesized negative position arguments.
This does not claim all remaining TagSoup utility APIs are ported.

The character parser's consumed/empty interpreter is factored into
`ParserMachine` over an arbitrary positioned state. Both the existing `Parsec`
facade and the typed `TokenParsec<T,U,A>` use that single interpreter. The existing
character reply type is a source alias for the generic reply. Typed token streams
preserve arbitrary tokens rather than encoding tags as characters; tokenPrim,
tokenPrimEx, EOF and caller state updates retain original behavior. Explicit
work stacks/deferred nodes handle finite deep inputs. List-backed streams own
their input spine; a separate contract test proves that nullable accepted values
and nullable updated user state remain successful and that original input/state
are retained. The pre-existing 10,304 math-parser observations also pass after
the interpreter extraction.

The oracle builds the **complete original Pandoc library**. Since its helper
modules are hidden, `prepare.py` creates a separate copy and changes only Cabal
module exposure declarations for Parsing, TagCategories and Types. Every one of
**2,888 tracked original files** (implementation, tests, data and notices) is
checked byte-for-byte; only that exact Cabal exposure edit is permitted. The
original checkout is unchanged. The observer calls the original compiled
functions, without module extraction or replacement. The complete original HTML
test module and original helper assertions are compiled/run unchanged; a helper
adapter additionally serializes literal inputs/expectations. All **69 ordinary
assertions plus the original QuickCheck property** (25 cases) pass, along with
69 serialization checks: **139 native tests**. A dependency freeze is retained
and a repeated build reports `Up to date`.

All **66,067 independent observations pass**, plus the ownership/nullable-state
contract test. Coverage includes all eight category values; 40,328 opening and
closing pairs over all 142 category/boundary names; 5,041 asymmetric tag matches;
1,988 open/attribute matches; close/missing/empty/Show cases for all six tag
constructors; all standard/prefixed attributes and duplicate permutations;
7,242 predicate/position/backtracking observations; all omission policies and
content/state effects; implicit closing and custom opening predicates; all 69
original HTML unit inputs (276 parser compositions) and the complete original
HTML-reader legacy input at the token boundary; raw source names/Int64 positions;
2,000 seeded state/error/tag cases; and four deep/long streams (1,500 nested tags,
30,000 position tokens, 15,000 content tokens and 15,000 in-tag content tokens).
Thirty-four aggregate witnesses cover every Haskell Char for the HTML whitespace
predicate and Show in all five textual tag constructors: 6,684,672 native results,
not that many individual JUnit tests. Corpus SHA-256:
`02ccd4ff067fa76cd8484b8795b5ee20c78a32c578f5c24a8e9fde6663e1d02d`.
The manifest binds 332 source files; all 108 CI source/fixture commands pass.
All 332 source hashes match staged Git blobs; the corpus and copied helper are
byte-exact, previous protected corpora/oracles are unchanged, and every upstream
checkout, release tag and root gitlink remains clean/pinned. Installed/core JAR
bytes match and contain the new typed parser/tag classes, all 54 full notices,
250 Pandoc data/index files and the unchanged MathML entity table. Installed
workflow smoke checks pass. Exact-SHA CI for 6e59f89 is verified successful
(120 successful steps).

Full validation passes **1,943,257 tests in 105 suites**, with zero failures,
errors or skips (8m 1s). All 375 installed CLI comparisons pass (14 FB2,
66 media, 69 extension, 85 math and 141 HTML), alongside the workflow smoke
checks. No tests were disabled and the original worker heap/recycling limits
remain unchanged. The metadata checker was strengthened to verify the exact
original/exposed Cabal hashes and tracked-file count. Recapture with the same
original native program preserves all 66,067 request/expectation lines byte-for-
byte and the same case hash; only the source-check manifest changes.

The full objective remains active. These are the complete tag helper/category
modules, not the complete HTML reader. HTMLState/HTMLLocal and the remaining
ParserState/capability boundary, table parsing, shared metadata/code/URI helpers,
raw LaTeX inline parsing and the HTML reader traversal still need Kotlin ports.
The original HTML AST-level Kotlin assertions, original round-trip property and
legacy golden remain pending. All other remaining Pandoc readers/writers and
application/Lua/property tests, followed by calibre MOBI and its original tests
and other calibre formats absent from Pandoc, remain in scope.


## Complete HTML table parsing and simple-cell classification

`HtmlTableParsing.pTable` implements the complete
`Text.Pandoc.Readers.HTML.Table` module, including its private column/group,
cell/row/header/body/footer and normalization algorithms. It preserves the exact
caption/head/top-foot/body/bottom-foot parse order; optional/implicit closing;
header-only row detection and mixed th/td backtracking; raw attribute versus CSS
alignment precedence; rowspan/colspan Read behavior; CSS cleanup; row-heading
vectors across spans, including Int64 wrapping; empty-table rejection; width
padding; first-body-row alignments and simple/normal implicit widths. Relative
width sums use original Int64 arithmetic; percentage/relative calculations retain
NaN, infinities and signed zero. The existing Builder table-shape normalization
is applied after the HTML module's own normalization, as in the original.

The caller supplies the cell/caption `HtmlTagParser<U,Blocks>`, retaining arbitrary
user state and external effects. `TableSimplicity.onlySimpleTableCells` implements
the complete Shared helper. An iterative query visits nested inline/block/citation,
note/caption/table structures, retaining LineBreak detection without JVM recursion.
The existing Haskell numeric Read implementation is reused; no earlier production
implementation or expectation needed changes to match this module.

The reference builds the complete pinned Pandoc/pandoc-types libraries. As in the
previous tag-helper stage, an independently verified copy changes only Cabal
exposure metadata, now for Parsing, Types, TagCategories and Table. All 2,888
tracked original implementation/test/data/notice files are checked, and the
original checkout remains unchanged. The observer calls the real original pTable,
safeRead and simple-cell query; it never implements their algorithms. All 69
ordinary HTML assertions and the original QuickCheck property (25 successes)
pass, plus 69 serialization checks: **139 native tests**. The dependency freeze
is retained and a repeated native build reports `Up to date`.

All **6,539 independent observations pass**. They cover:

- the exact integer/Double Read grammar and 1,500 seeded numeric cases;
- 784 percentage/relative/nonfinite/overflow width combinations and style/colgroup
  precedence, omission and padding;
- 1,512 span/alignment/header-vector/CSS cases, and all caption/head/body/foot
  arrangements, including both top and bottom footers;
- all **266 original Native documents** as arbitrary cell content and as direct
  simple-cell queries;
- all **42 table spans** found in original `.html`, `.html4` and `.html5` fixtures
  through original tokenization/canonicalization, without newline rewriting;
- all 69 original HTML unit inputs at the table-parser boundary, all relevant
  block/simple-cell shapes and nested Note/Cite LineBreaks;
- raw source names, Int64 positions, 1,500 seeded malformed/backtracking cases,
  1,500 nested tables and a 1,500-row table; and
- 10,000-level finite inline queries plus seventeen aggregate witnesses over every
  Haskell Char as numeric prefix/remainder for both Int and Double: 4,456,448 Read
  results, not that many separate JUnit tests.

Normal table cases compare complete AST JSON and native Show, so nonfinite and
signed-zero distinctions are retained. Deep nested-table cases compare complete
AST Show independently of the general AST JSON encoder; this is not a claim of
stack safety for that separate codec. Callback tests use original PandocMonad
CommonState as an external effect trace. They prove that speculative headers,
lookahead and failed tables restore Parsec input/user state while retaining
external effects. All initial 6,441 request/expectation records were independently
checked unchanged when these effect observations and 98 additional fixture/effect
cases were added. Original literal expectations were not weakened.

Corpus SHA-256:
`9962829c32de6068efe4ae0b310cf1342bdc2adb1347c9eb2525c7af10adf510`.
The manifest binds 617 original/observer source files; all 109 CI source/fixture
commands pass. Exact-SHA CI for fe49e08 is verified successful (121 successful
steps). All 617 source hashes match staged Git blobs; corpus/copied-helper bytes
are exact, earlier protected corpora/oracles are unchanged, and upstream
checkouts/release tags/root gitlinks remain clean and pinned. Installed/core JAR
bytes match and include both new classes, all 54 full notices, 250 Pandoc assets/
index files and the unchanged MathML entity table. Workflow smoke checks pass.

Full validation passes **1,949,796 tests in 106 suites**, with zero failures,
errors or skips (9m 44s). All 375 installed CLI comparisons pass: 14 FB2,
66 media, 69 extension, 85 math and 141 HTML cases. No test registrations were
disabled and existing 2 GiB worker/recycling limits remain unchanged.

The full objective remains active. The HTML table module is complete at its
caller-supplied content-parser boundary; it does not implement the full HTML
reader's block parser. HTMLState/HTMLLocal and remaining ParserState/capabilities,
shared metadata/code/URI helpers, raw LaTeX inline parsing and HTML traversal
still need ports. The original full HTML AST assertions/property/legacy golden
remain pending in Kotlin. All other Pandoc readers/writers and application/Lua/
property tests, then calibre MOBI with all original tests and the other calibre
formats absent from Pandoc, remain in scope.


## Full parser-state record, capabilities and reference keys

`ParserState` now represents all 31 fields of the original
`Text.Pandoc.Parsing.State`, with the original default values and concrete
ReaderOptions, Blocks/Inlines/Meta, SourcePos, LogMessage and Pandoc TeX Macro
models. Parsed notes and deferred metadata reuse the existing typed
`Future<ParserState,...>` implementation and read the final state. Eager metadata
set/delete deliberately leaves future metadata unchanged. The record has no
invented derived Eq/Ord/Show instance. ParserContext, QuoteContext, both raw-Char
HeaderType constructors and the original table aliases are provided.

`ParserStateAccess` implements the original state instances for identifiers,
macros, reader options, quotation, last-string positions, logs and include files.
Character and typed-token capability helpers preserve consumed/empty replies,
errors, positions and state rollback. Quote scope restores only the previous
quotation on success, retaining other state changes; it does not introduce a
finally-based restoration on failure. Local logs are prepended and exposed in
chronological order. Reporting keeps local logs and delegates every message to
the existing PandocState effect boundary, including verbosity and external logs.
Include files form a stack and dropping an empty stack succeeds. The typed-token
reader-options dictionary supports a separately overridden parser-valued getter;
guards invoke that getter rather than bypassing it through pure extraction.

`Key` retains the original derived Eq/Ord, precedence-aware Show and Read/readList
syntax, including raw Haskell Char remainders, nested parentheses, character-list
Text input, string gaps, escapes and surrogate replacement at Text.pack. `toKey`
removes exactly one outer bracket pair before splitting whitespace, joining and
lowercasing. Exhaustive native observations found that the original Pandoc build
uses a different lowercase table from the earlier text-2.1.3 dependency port.
The exact **1,433 text-2.1.2 mappings** are therefore translated separately from
that original source and checked in CI. The earlier table and its expectations
are unchanged. All 27 lowercase differences between these dependency versions
have individual native observations; seventeen aggregate witnesses cover all
1,114,112 Haskell Chars through key normalization. These witnesses are aggregate
tests, not that many separate JUnit cases.

The observer builds the complete pinned original Pandoc/pandoc-types libraries.
A separately verified git-archive copy changes only Cabal exposure declarations
for Parsing.Capabilities and TeX; implementation, tests, data and notices remain
byte-identical across all **2,888 original tracked files**. The original checkout
is not edited. The library uses the previously documented `-O0` compiler resource
setting; the observer/dependencies retain `-O2`. Its dependency freeze is retained
and a repeated native build reports `Up to date`.

All **4,725 independent native observations pass**. They compare all record fields,
defaults and generated transitions; real nonempty macro maps; references and
normalized-key replacement; both metadata paths; final-state parsed notes;
include/log ordering; repeated reports and external effects after rollback;
character and typed-token quotation/error/guard behavior; overridden token option
getters which consume or fail; source names containing raw surrogate Chars and
Int64 positions/counters; 10,000 nested quote scopes; 10,000 parentheses in Key
Read; and all **266 original Native documents** stored as substitutions, parsed
notes and metadata. The two initial aggregate Unicode failures were fixed in
Kotlin, without changing the original expected values. All initial 4,698
request/expectation records are verified unchanged in the final corpus.

The full ParserState also implements the existing ordered-list field capability.
All **34,920 original list-parser run observations** are replayed against this
record with non-default sentinels in each of the other 29 fields; those fields
remain unchanged. The original list-parser corpus and its earlier projection
checks are retained. Together the new suites pass **39,645 comparisons**.

Corpus SHA-256:
`5e020def564c8b75237cebbbe05c1461dd0ef2e23065733e24d3a21e5d622433`.
All **695 source hashes** match staged root Git blobs or clean pinned upstream
blobs; staged corpus and lowercase-resource bytes match exactly. Earlier protected
corpora/oracles and all upstream release pins are unchanged. All **111 CI
source/fixture commands pass**. Exact-SHA CI for d089516 is verified successful,
with all 122 steps passing. Installed/core JAR bytes match and contain the new
state/capability/key classes and exact lowercase table, all 54 full notices,
250 Pandoc assets/indexes and the unchanged MathML entity table. The workflow's
installed-CLI smoke checks pass.

Full validation passes **1,989,441 tests in 108 suites**, with zero failures,
errors or skips (11m 31s). All **375 installed CLI comparisons** pass: 14 FB2,
66 media, 69 extension, 85 math and 141 HTML cases. Original tests and earlier
corpora remain enabled, with the existing 2 GiB worker/recycling limits.

The objective remains active. This supplies the full materialized parser-state
record and its capability boundary, not the complete HTML reader. HTMLState and
HTMLLocal still need their own instances and ReaderT environment semantics,
followed by shared metadata/code/URI helpers, raw LaTeX inline parsing and the
HTML traversal. General lazy/partial parser reply and record-field demand beyond
the existing Future/deferred models is not claimed by these finite state tests.
The original full HTML AST assertions/property/legacy golden remain pending in
Kotlin. All other remaining Pandoc readers/writers and application/Lua/property
tests, then calibre MOBI with all original tests and other calibre formats absent
from Pandoc, remain in scope.


## HTML reader state, local Reader environment and URI values

`HtmlState` implements all eleven global fields of the original
`Text.Pandoc.Readers.HTML.Types`, including the complete nested ParserState,
Blocks note map, source-position map and concrete five-component URI value.
`HtmlLocal` implements all four local fields and the original NoQuote/False
initialization. `HtmlState.initial` mirrors readHtmlWithDepth: both option records
start with the supplied options, and the supplied iframe depth retains Int64
semantics. HTMLState itself has no invented original Default instance.

HTML capability instances preserve the original ownership boundaries. Metadata
and last-string positions update nested ParserState. Reader options are extracted
from that nested record, even when readerOpts differs. Identifier/macro maps and
logs belong to HTMLState itself and leave the corresponding nested fields alone.
Local logs are accumulated in reverse order and exposed chronologically;
reporting delegates to PandocState and keeps the local list.

Character and typed-token Reader parser adapters reuse the existing Parsec
consumed/empty interpreter. Their environment is separate from user state:
ReaderT.local scopes quote/chapter/plain/list flags, while parser modifications
and external PandocState effects follow their original rollback rules. Neither
the interpreter nor earlier character/token parser behavior was replaced.
Deferred environment transformations preserve ignored-context behavior; explicit
lazy ask/quote adapters allow callbacks to ignore the selected result. Normal
value adapters serve materialized contexts. These adapters do not claim general
Haskell partial/infinite parser-result or record-field demand beyond the existing
Deferred/Future APIs; that broader runtime boundary remains in scope.

`URI` and `URIAuth` retain every original component as CharacterString, including
individual surrogate Chars. Their Eq/Ord follows original field and Char ordering.
URI Show keeps the original password mapping and ignores precedence; URIAuth
keeps derived record Show and precedence. uriToString/uriAuthToString retain
userinfo callbacks, skipped callbacks for empty/absent userinfo and raw suffixes.
Rectification and absolute/relative predicates follow the original constructor
values, including rectifying an empty scheme to `:`. Constructors do not introduce
parsing, validation, normalization or java.net.URI behavior. Full Network.URI
parsing, escaping, normalization and relative resolution remain pending.
The original module's full Graham Klyne BSD notice is now retained alongside the
existing network-uri package notice and is included in the JAR.

The observer builds the complete pinned original Pandoc/pandoc-types libraries.
A verified git-archive copy changes only three Cabal exposure declarations:
HTML.Types, Parsing.Capabilities and TeX. All **2,888 original tracked files**
retain their exact implementation/test/data/notice bytes, except that exposure
metadata; the original checkout is unchanged. This retains the documented library
`-O0` compiler resource setting and observer/dependency `-O2`, a dependency freeze,
and an `Up to date` repeated native build.

All **6,368 independent native observations pass**, including:

- complete global/nested/local state projections, initialization, independent
  updates and 1,500 seeded transitions;
- 756 quote-scope/state collision cases, all local flags, original quote failure
  text, nested option extraction and raw SourcePos/Int64 boundaries;
- chronological local/global logs and scoped Reader effects retained across
  failed alternatives, try and lookahead;
- ignored environments, lazy ask/quote result adapters, ordinary Parsec
  combinators, 10,000 nested scopes and 100,000-step map/bind/then/before/local
  compositions for both character and typed-token input;
- all **266 original Native documents** as HTML note content and nested metadata;
- all **four original Network.URI Show assertions**, with their literal expected
  strings preserved and checked separately from the native observations; and
- component/authority formatting, rectification, callback demand and suffixes,
  original URI comparison results and seventeen aggregate witnesses covering every
  Haskell Char through URI Show, full rendering and URIAuth Show: **3,342,336
  original rendered values**, not that many separate JUnit tests.

All initial **6,242 native request/expectation records** remain unchanged when
scoped effects and deep composition cases are added. Corpus SHA-256:
`8826baa2a26e297337ee00e1d7c94fc35dee80b047877012877c39934c371ecb`.
All **112 CI source/fixture checks pass**. Exact-SHA CI for 1763c99 is verified
successful, with all 124 steps passing.

All **776 source hashes** match staged root Git blobs or clean pinned upstream
blobs. Corpus and new notice bytes match exactly; earlier protected corpora,
oracles and release pins are unchanged. Installed/core JAR bytes match and
include the new state/environment/URI classes, all **55 full notices**, 250 Pandoc
assets/indexes and the unchanged MathML entity and text-2.1.2 lowercase data.
Workflow smoke checks and all **375 installed CLI comparisons** pass: 14 FB2,
66 media, 69 extension, 85 math and 141 HTML cases.

Full validation passes **1,995,809 tests in 109 suites**, with zero failures,
errors or skips (10m 27s). No original tests/corpora were disabled and the existing
2 GiB worker/recycling limits remain unchanged.

The full objective remains active. The global/local state boundary is available
for assembling the HTML reader; shared metadata/code/URI helpers, URI parsing
and relative resolution, raw LaTeX inline parsing and the reader traversal still
need ports. The original full HTML AST assertions/property/legacy golden remain
pending in Kotlin. General lazy/partial parser reply and record-field behavior,
all other remaining Pandoc readers/writers and application/Lua/property tests,
then calibre MOBI with all original tests and other calibre formats absent from
Pandoc, remain in scope.


## Network.URI grammar, escaping, normalization and relative resolution

The remaining materialized finite runtime operations of network-uri 2.6.4.2
`Network.URI` are implemented in Kotlin. The original Parsec grammar runs through
the existing character-stream parser machine, retaining alternative ordering,
backtracking, consumption and the 255-token registered-name bound. This includes
all four URI/reference parser entry points, IPv4/IPv6 validation, IPvFuture and
RFC6874 zone identifiers. MediaBag's previous separately implemented projection
is replaced by delegation to this full parser; existing projection consumers
retain their scheme/path interface.

The URI operations retain raw Haskell Char components, escaped-byte handling,
source UTF8 encoding and malformed-prefix replacement/consumption, character
predicates, source case mapping, path-segment normalization, strict/non-strict
relative resolution and relative-reference construction. Deprecated names and
component accessors are available. The original `normalizeCase` behavior is
preserved even for input without a scheme: `"g"` becomes `"gg"`, because its
recursive fallback processes the complete original string after emitting a
lowercase prefix. Eight initial Kotlin discrepancies were fixed without changing
any original expectations. Template Haskell/Lens generation and generic
Data/Typeable dictionaries are not claimed as Kotlin runtime exports. General
lazy/infinite/partial URI field demand remains pending alongside the wider
parser/value demand work.

`tools/haskell-uri-reference` builds the unchanged vendored Network.URI with a
metadata-only deepseq upper-bound relaxation for GHC 9.12.2. Its control suite
preserves the complete original test file except its module name. Its observed
copy additionally redirects the Network.URI import to test-only wrappers; every
test body and expectation is unchanged. Both native suites pass **341 tests**:
339 unit cases and two QuickCheck properties at seed 314159. Nested observations
are fully encoded before taking the output mutex, so source-lazy argument
execution cannot deadlock a nested observer call.

`tools/import-uri-fixtures.py` captures **95,464** independent original-library
observations and **79 source hashes**. These include:

- **1,495 actual API calls** from the complete original unit/property suite,
  with every recorded result independently rechecked through the direct observer;
- 232 parser boundary cases, 348 predicate cases and 5,416 parsing observations
  over the original test source's string literals, including inactive examples
  without counting them as active original assertions;
- 13,500 raw-constructor relative-resolution cases, 8,000 generated grammar cases,
  all **65,536 escaped byte pairs** and 608 malformed UTF8/prefix cases;
- seventeen aggregate witnesses covering every **1,114,112 Haskell Char** through
  source escaping, decoding, case normalization and all five character predicates;
- finite paths and dot-segment sequences with **10,000** segments.

Corpus SHA-256:
`a9eee09783fcd21bc217809a1a85da371f6e02e359154e0eca94150dc19b89f0`.
These API observations are supplemented by a direct Kotlin translation of the
original assertions, rather than being counted as the assertion port themselves.

`tools/import-uri-unit-tests.py` independently extracts every active registration,
source literal, expected record and normalization composition into
`uri/upstream-units.json`. It invokes neither Kotlin nor the Haskell observer and
fails on unhandled definitions or changed case/property counts. All **339 active
unit cases** run through translated Kotlin assertion helpers, including duplicate
source labels, three checks per URI classification, both directions of relative
round trips, exact source records, normalization compositions, password-masking
Show, alternative entry points, path reconstruction and rectification. Both
original property predicates are translated too: the original validity premise
and high-character OR mask are retained. Kotlin executes 10,000 deterministic
string samples and exhausts the scalar Char domain for both properties, a
superset of the pinned QuickCheck Char generator's assigned, non-surrogate
domain. This does not claim to port QuickCheck's random generator or shrinking.
Raw surrogate behavior is independently covered by the complete-Char observer
witnesses, rather than incorrectly requiring surrogate UTF8 round trips.

The complete original URI unit/property suite and the MediaBag/resource/state/FB2
integration suites pass. Exact-SHA CI for ab86f0a is verified successful.

All **114 CI source/fixture checks pass**. All **79 source hashes** match staged
root Git blobs; URI unit extraction matches the staged original source and JSON
bytes exactly. Earlier protected corpora/oracles and all three clean upstream
release pins are unchanged. Installed/core JAR bytes match and contain the new
URI parser/runtime classes, all **55 full notices**, **250** Pandoc assets/indexes
and unchanged MathML/text-2.1.2 tables. All workflow smoke checks and **375
installed CLI comparisons** pass: 14 FB2, 66 media, 69 extension, 85 math and
141 HTML cases.

Full validation passes **2,091,614 tests in 111 suites**, with zero failures,
errors or skips (11m 40s). The new suites contribute 95,464 native observations
and all 341 original unit/property cases. No original expectations, tests or
protected corpora were disabled or changed; the existing 2 GiB worker/recycling
limits remain unchanged.

The full objective remains active. This supplies URI parsing/resolution for the
HTML reader; shared metadata/code/URI helpers, raw LaTeX inline parsing and the
reader traversal still need ports. The original full HTML AST assertions,
property and legacy golden remain pending in Kotlin. All other remaining Pandoc
readers/writers and application/Lua/property tests, then calibre MOBI with all
original tests and other calibre formats absent from Pandoc, remain in scope.


## Shared metadata/inline helpers and deep finite AST traversal

`InlineShared` ports the complete `Text.Pandoc.Shared.addMetaField`,
`extractSpaces`, `formatCode` and `htmlSpanLikeElements` operations used by the
HTML reader. Metadata accumulation keeps the original distinction between an
existing MetaList (append and flatten a new list by one level) and every other
existing value (construct a two-element list, preserving a new nested list).
All existing unrelated metadata is retained. Text, raw Haskell String,
Bool, Inlines, Blocks and explicit recursive list/map converters are available;
raw Haskell surrogate Chars pass through the original Text.pack replacement
boundary. The span-like set retains all four elements and source ordering and
cannot be mutated through a host-language cast.

Space extraction inspects only the original first and last inline, distinguishes
Space from SoftBreak, trims only those two constructors, invokes the supplied
function exactly once even on an empty interior, and uses original Inlines
Semigroup melding on the result. LineBreak is not treated as trim whitespace.
Code formatting walks all nested inline lists bottom-up, including citation
prefixes/suffixes, notes, table captions/cells, definitions and figures. The
original groupBy predicate treats Str, Space, SoftBreak and Quoted as plaintext;
other formatting remains outside Code while its children are processed. Runs
become a single Code using the original stringifyInlines behavior and unchanged
attributes, including empty Code text.

The existing `PandocVisitor` now executes its complete materialized finite
traversal through per-call DeepRecursiveFunction continuations. Public methods,
named arguments, child/field order, preorder visits, postorder transformations,
metadata key ordering and replacement-without-revisiting semantics are retained.
Nested MetaMap entries still do not introduce an extra Meta hook. The
stringifyInlines fold uses explicit iterator/quote frames, retaining skipped
notes/citation metadata, quote punctuation, raw HTML br handling and all other
original inline cases. This removes JVM recursion from both paths and allows
code formatting on deep finite documents. It does not claim productive
infinite/partial AST values or a port of general Haskell evaluation demand.

`tools/haskell-shared-inline-reference` calls the complete original Pandoc Shared,
Walk, Builder, Definition, Native reader and Arbitrary instances. No behavioral
module is substituted. It reuses the already audited original-library build:
all **2,888 tracked Pandoc files** match byte-for-byte, with only the previously
documented three-module Cabal exposure adjustment retained to reuse that build.
The standalone project also supports the normal unchanged upstream package.
`OriginalSharedProperty.hs` contains the verbatim
`stringifyInlinesMatchesStringify` signature and body from Tests.Shared, with
source-extraction validation. Its original QuickCheck control passes **1,000
trials at seed 314159**. Production conversion has no Haskell dependency.

`tools/import-shared-inline-fixtures.py` captures **19,643** original observations
and **641 source hashes**:

- all **266 original Native documents** with source query/preorder and
  walk/postorder inline-list traces, node/list replacements and full AST outputs;
- all **10,584 original inline lists**, checked through code formatting, specialized
  stringifyInlines and generic stringify;
- **1,000 original-generator property inputs** at ten source size settings, plus
  their direct formatter/stringifier observations. Kotlin translates the original
  equality predicate; the QuickCheck generator/RNG/shrinker itself is not claimed
  as a Kotlin port;
- every Inline constructor, quote/raw-format/empty-text variants and three
  attribute records; 4,352 group-boundary combinations, 1,800 wrapper/boundary
  space cases and 540 metadata dictionary/merge/field-preservation cases;
- seventeen aggregate witnesses covering every **1,114,112 Haskell Char** through
  Builder.text, quoted stringification, formatCode, extractSpaces and raw-String
  metadata insertion;
- spans and alternating inline/note/block trees at **1,500**, **10,000** and
  **100,000** levels, and nested quote stringification/code formatting at 1,500
  and 5,000 levels.

The Unicode aggregate transport uses typed raw-codepoint arrays for strings and
sorted arrays for object fields, keeping witnesses independent of JSON key and
string-escape spelling. All **19,626 initial non-aggregate request/results** are
unchanged when those seventeen hashes switch to the explicit transport.
Corpus SHA-256:
`a81084e819aabc44d420402dd14a826a951eb79e967eab86c6a5be0cec45211c`.
The original observations and existing Builder/Visitor/Shared tests pass; an
additional ownership/callback check verifies immutable span-set behavior,
metadata persistence and single invocation for an all-space input.

All **115 CI source/fixture checks pass**. All **641 source hashes** match staged
root Git blobs or clean pinned upstream blobs. Corpus bytes, earlier protected
corpora/oracles and all three release pins are unchanged. Installed/core JAR
bytes match and include the new helper/traversal classes, all **55 full notices**,
**250** Pandoc assets/indexes and unchanged MathML/text-2.1.2 data. All installed
workflow smoke cases and all **375 installed CLI comparisons** pass: 14 FB2,
66 media, 69 extension, 85 math and 141 HTML cases. Exact-SHA CI for **8054e82**
is verified successful.

Full validation passes **2,111,258 tests in 112 suites**, with zero failures,
errors or skips (10m 46s). The new suite contributes 19,643 original observations
and one ownership/callback check. No original tests or protected expectations
were disabled or changed; the existing 2 GiB worker/recycling limits remain
unchanged.

The full objective remains active. These are complete finite shared helpers,
not the complete HTML reader. Remaining Text.Pandoc.URI helpers, raw LaTeX
inline parsing and the HTML traversal still need ports, including the original full HTML AST
assertions/property/legacy golden. All other remaining Pandoc readers/writers,
application/Lua/property tests and general lazy/partial-value contracts, followed
by calibre MOBI with all original tests and the other calibre formats absent
from Pandoc, remain in scope.

## Complete finite Pandoc URI exports and incremental parsing

`uri/PandocURI.kt` ports all six exports of the pinned `Text.Pandoc.URI`:
`urlEncode`, `escapeURI`, `isURI`, `schemes`, `uriPathToPath` and
`pBase64DataURI`. Text inputs pass through the original Text surrogate replacement
boundary. URL encoding uses the exact HTTP query-component safe set and uppercase
UTF-8 escapes. URI escaping preserves non-ASCII characters and percent signs,
escaping GHC isSpace and precisely the source punctuation set. Recognition first
checks the complete data-URI grammar without demanding decoded contents, then
uses the complete pinned Network.URI parser and immutable 268-scheme registry.
Windows paths drop exactly one leading slash; POSIX paths preserve it.

The data-URI parser preserves case-insensitive `data:`, case-sensitive `;base64,`,
restricted MIME names, GHC whitespace after parameter semicolons and original
parameter normalization. Percent octets become Unicode Chars before UTF-8/Base64
decoding, including non-ASCII octets and encoded padding. The prefix parser
preserves remaining input; ResourceUri's downloadOrRead facade requires EOF as
in the original PandocMonad caller. MIME and decoded bytes are independent
Deferred fields. Existing resource/FB2/HTML URL recognition and encoding adapters
now delegate to these exports; MediaBag URI recognition uses the same data-URI
fast path. JVM HTTP transport/resolution and the separate extractURIData adapter
remain existing IO boundaries, not a claim of complete PandocMonad parity.

The strict base64-bytestring decoder previously stopped at every padding byte.
The original skips padding while looking for the first two sextets of a group,
then terminates on padding in the third/fourth position. The Kotlin decoder now
follows that algorithm, including padding introduced by percent escapes.
The FB2 lazy decoder remains its separate original algorithm.

`parsing/attoparsec/` adds the independent incremental Text kernel required by
this source grammar. It preserves Attoparsec's always-backtracking choice,
atomic literal strings, character labels/context order, takeWhile demand,
prefix results and immutable branchable Partial continuations. Feeding a
terminal Done or Fail appends to its remaining input. Empty feed marks EOF for
a Partial result. An immutable balanced chunk buffer avoids input replay/copy
on every feed; an explicit parser/frame interpreter and Deferred result
trampoline handle deep finite parses without JVM recursion. LookAhead restores
position on success, retaining the original consumed position on failure.
This ports the primitives/combinators used here, not all Attoparsec exports,
ByteString/lazy APIs, QuickCheck machinery or infinite/diverging parser values.

`tools/haskell-pandoc-uri-reference` compiles the exact original URI source for
both CPP branches. The POSIX copy changes only the module name; the Windows copy
also defines `_WINDOWS` before imports. The source checker verifies these exact
transformations. This is needed because URI is hidden by Pandoc's Cabal package.
Both copies call the original Pandoc MIME/UTF8 and pinned Attoparsec, network-uri,
http-types, safe and base64-bytestring packages. The complete cached original
Pandoc build is again verified against all **2,888 tracked files**, retaining
only the prior three-module Cabal exposure adjustment. Production has no Haskell
or upstream-program conversion dependency.

The complete attoparsec **0.14.4** (74 regular archive files) and http-types
**0.12.6** (21 files) releases are retained byte-for-byte, including original
tests. Archive SHA-256 provenance is recorded. Both full license notices match
their original bytes; the new http-types notice is packaged with the existing
Attoparsec notice. Existing vendored Attoparsec/XML subsets remain unchanged.

`tools/import-pandoc-uri-fixtures.py` captures **40,705 observations** and **141
source hashes**, with Kotlin expectations supplied only by original programs:

- all 268 schemes with casing and malformed/relative/Unicode URI boundaries;
- seventeen aggregate witnesses over every **1,114,112 Haskell Char**, comparing
  URL encoding, URI escaping and both platform path branches;
- all 18 previous data-fetch examples, MIME/grammar boundaries, every split point
  in both prefix and EOF modes, single-character fragmentation and terminal feed;
- every percent octet in seven sextet/padding positions, plus ASCII/Unicode MIME
  name/whitespace/Base64 predicate boundaries;
- **19,531 exhaustive short Base64 byte inputs** and 1,000 seeded arbitrary byte
  sequences, observed directly through the strict original decoder;
- 6,260 synthetic kernel cases covering alternatives, labels, lookAhead, match,
  many/many1/skipMany, failures and chunking, plus 80 branched continuations;
- map/bind nesting and literal/many/backtracking feeds at 1,500, 10,000 and
  **100,000** steps; four checks discarding bottom-valued parser results.

Corpus SHA-256:
`236aa94825846ee31bd0363ab707a6dd3a29c85a046eae2cb994d00720d7a1d3`.
The original Attoparsec Text/Combinator tests were inspected for relevant contracts;
this corpus does not claim a port of its entire dependency test suite. Existing
protected original fixtures and upstream expected outputs are unchanged.

Full Gradle validation passes **2,151,964 tests in 113 suites**, with zero
failures, errors or skips (10m 43s). The new suite contributes 40,705 original
observations and one immutability/deferred-tuple check. All **116 source/fixture
checks** pass; all 141 source hashes match staged/root or pinned upstream Git
blobs, and all three release pins and protected earlier corpora/oracles remain
unchanged. Installed/core JAR bytes match, including the new URI/parser classes,
all **56 full notices**, **250** Pandoc assets/indexes and unchanged MathML/text
mapping data. All **375 installed CLI comparisons** pass: 14 FB2, 66 media,
69 extension, 85 math and 141 HTML cases. Installed workflow smoke cases pass.
Exact-SHA CI for **441068c**
is verified successful. No existing tests were disabled or changed, and worker
heap/recycling settings remain unchanged.

The full objective remains active. The full HTML reader, raw LaTeX inline
parsing, remaining original HTML assertions/property/goldens, other remaining
Pandoc readers/writers, application/Lua/property tests and general lazy/partial
value contracts still require work. Calibre MOBI and all original tests, followed
by calibre formats absent from Pandoc, remain in scope.

## LaTeX tokenization, state and parser scoping

The next dependency of HTML raw-TeX parsing is the original LaTeX reader, not a
balanced-brace approximation. Its `Readers.LaTeX.Parsing` layer is now being
ported directly. `latex/LaTeXTokens.kt` implements complete `tokenize`,
`tokenizeSources`, `getInputTokens`, `untokenize`, `untoken`, token relocation and
kind predicates, and the `TokStream` append/empty/uncons behavior. This extends
the previously ported original TeX token/macro/argument models and Sources;
it does not introduce a different intermediate token format.

The tokenizer retains GHC isAlphaNum/isLetter, ASCII space/tab grouping, exact
comments/newlines/control-sequence spelling, trailing command whitespace,
makeatletter/makeatother and independent ExplSyntaxOn/Off mode switches. Argument
indices include arbitrarily long decimal syntax followed by original 64-bit Int
wrapping. Double-hash failures emit both Symbol tokens before resuming, and caret
escapes recognize only lowercase hex. Source columns count Unicode Chars, not
UTF-16 units. The original escaped-newline column-only update and second-newline
fallback are retained, including their unusual positions. Each Sources chunk
restarts tokenizer modes; getInputTokens replaces only the first chunk position
with the current parser position. Empty chunks remain lazy about unused positions.

Tokenization and source flattening retain deferred positions, token fields and
list spines. Untokenize inserts a separating space after a letter-ending control
sequence when the next nonempty token starts with a letter; skipped empty tokens
need not expose their kind or position. The Text.concat singleton/list-spine
inspection order is preserved for competing deferred errors. TokStream retains
its expanded flag from the first nonempty operand and always resets it after a
successful uncons. `unconsLazy` exposes the original lazy token field; the generic
TokenStream adapter demands the token when projecting into its existing strict
host tuple. Both use the same persistent tail.

`latex/LaTeXState.kt` provides all **30 original fields and defaults**, the
nonempty macro-table stack, DottedNum and TheoremSpec/TheoremStyle models, typed
LaTeXEnv dispatch tables and the LP ReaderTokenParsec alias. It includes the
original reader-options, quote-context, identifiers, macro, include-file,
metadata and reverse-log instances. State retains identity rather than inventing
an Eq instance absent upstream. DottedNum increment/render/derived Show and
TheoremSpec's complete finite Eq are observed independently. Full derived Show
for LaTeXState, TheoremSpec and TokStream and general partial-state demand remain
pending; no complete port of the whole Parsing module is claimed.

The macro-table stack uses persistent links with O(1) push/pop/active-table
replacement and an immutable List view of outer scopes. Inspection caught an
initial materialized-list design that copied all outer scopes on every push;
that design was replaced before publication and checked against three original
1,500/10,000/100,000-level witnesses. Updating an inner scope preserves all outer
maps, and popping the last scope keeps the original nonempty table. All **8,970
prior observations** remain unchanged when those three witnesses are added.

`latex/LaTeXParsing.kt` ports the state-scoping helpers `withVerbatimMode`,
`disablingWithRaw`, `parseFromToks`, `withRaw`, `getNextNumber`, `resetCaption` and
MetaValue `addMeta`, with shared ReaderT environments. Success restores precisely
the source fields while preserving other modifications; consumed/empty failure,
try/alternative rollback and nested raw capture use the existing Parsec machine.
Temporary token parsing clears raw accumulation and restores input/position/raw
state on success. Raw output reverses only the counter delta and is independently
lazy; outermost completion clears the accumulator, while nested scopes retain it.
Chapter-aware numbering reads the selected counter without mutating state.

`tools/haskell-latex-parsing-reference` compiles the **complete original Parsing
module** with only its module name changed and the PandocMonad/report import
routed through `Text.Pandoc.Class`, which reexports those exact original entities.
The source checker verifies both edits; no behavior is replaced. It uses the
complete original library build with all **2,888 tracked files** checked and the
same three-module exposure-only adjustment as earlier phases.

All **90 original `Tests.Readers.LaTeX` input expressions** are evaluated by a
copy with unchanged test definitions. The module name and harness imports alone
change: a capture harness collects (group/name, Text input), retaining the
original constraints and helper operator without evaluating expected ASTs. This
captures literal, computed and list-generated inputs. It is evidence for
lexical coverage, **not** a claim that the 90 reader AST assertions have been
ported or run through a Kotlin LaTeX reader.

The new original-program corpus contains **8,973 observations** and **156 source
hashes**:

- all 90 original reader unit inputs and all **14 tracked `.latex`/`.tex`/`.sty`/
  `.cls` test files**, with complete tokens, positions, kinds, text and untokenize;
- seventeen aggregate witnesses over all **1,114,112 Haskell Char**, both alone
  and following a backslash, including original Text surrogate replacement;
- 4,681 exhaustive short tokenizer strings, 786 mode-transition cases, 132
  argument-index/overflow examples and 1,280 caret-escape boundaries;
- raw surrogate source names and Int64 position overflow, all split points for
  mode-sensitive inputs, empty source chunks and current-position overrides;
- synthetic control-word separators/empty tokens, complete stream flag/tail
  behavior, dotted counters and all TheoremSpec field-equality boundaries;
- all state defaults/updates/instances, macro-stack preservation, include and log
  order, 455 parser-scope/failure/restoration observations and 108 chapter-counter
  observations;
- nine deferred-demand/competing-error cases and 1,500/10,000/**100,000**-level
  token lists, mode sequences, empty Sources chunks, stream concatenations,
  untokenize lists, nested raw scopes and macro-stack push/update/pop.

Corpus SHA-256:
`61200bf6f8f3739b16a0fd88a6072e9006cb12f230ae95defe74344c1b173afa`.
Upstream expectations and all earlier protected corpora/oracles remain unchanged.

Final full Gradle validation passes **2,160,937 tests in 114 suites**, with zero
failures, errors or skips (7m 51s). The LaTeX suite contributes all 8,973 original
observations (6.79s in the full run). All **117 source/fixture checks** pass; all
156 source hashes match staged/root or pinned upstream Git blobs. All three
release pins and protected earlier corpora/oracles are unchanged. Installed/core
JAR bytes match, including all **27 compiled LaTeX class files**, **56 full
notices**, **250** Pandoc assets/indexes and unchanged MathML/text mapping data.
Installed CLI workflow smoke cases pass. No LaTeX CLI reader is registered by
this foundation phase. Exact-SHA CI for **91ef73e** is verified successful.
No original tests were disabled or changed; worker heap/recycling settings remain
unchanged. The early full run was explicitly cancelled before replacing the
quadratic stack; these totals are from the completed final implementation.

The full objective remains active. The remaining Parsing token primitives,
macro expansion (including all xparse/special macros), rawLaTeXParser/applyMacros,
full LaTeX grammar/reader and original AST assertions still require ports.
Original macro controls also live in command fixtures beyond the 90 unit inputs:
`macros.md`, `4007.md`, `4253.md`, `5682.md`, `5740.md`, `7494.md`, `10915.md`,
`4442.md`, `latex3-commands.md`, `latex-opt-arg-state.md` and
`latex-tokenize-hash-positions.md`. Their full command/AST/report assertions remain
pending. These
are necessary for HTML raw LaTeX and the full HTML reader. Other remaining Pandoc
readers/writers and application/Lua/property tests, then calibre MOBI with all
original tests and other calibre formats absent from Pandoc, remain in scope.

## LaTeX token grammar, expansion and Sources bridges

The next Parsing dependency cycle now runs in Kotlin: `satisfyTok` expands the
head of the stream, argument parsers consume through `satisfyTok`, and macro
expansion recursively calls those parsers. No expansion identity stub or upstream
runtime dependency is used. `LaTeXKernel` implements the token/group/delimiter,
verbatim/URL, whitespace, dimension, key-value, raw-command, environment, label,
caption and label-removal operations. It uses the existing consumed/empty
ParsecT/ReaderT interpreter and persistent token/macro stacks.

The macro engine implements positional/pattern/default arguments, recursive
argument-default references with cycle guards, all xparse ArgSpec constructors
and processors, body/verbatim-body arguments, optional embellishments, and both
expansion points. Special macros include expandafter, xspace, primitive and
xparse conditionals, ifstrequal, ProcessList, UseName, ExpandArgs and all four
expandable evaluators. Conditional scanning recognizes aliases from all macro
scopes. Nested parsers discard their local state while retaining global reporting
and fatal PandocMacroLoop effects. Deferred argument resolution/substitution
preserves unused body/default fields in the independently observed demand cases.

`LaTeXParsingBridge` implements `rawLaTeXParser` and `applyMacros` over the existing
Sources parser, including the extension guard, two token-parser passes, macro
map merge, source-position boundary, unchanged lookahead and final control-word
space handling. Its state dictionaries remain generic. The tests compare values,
remaining tokens/source chunks, positions, macro tables, raw counters, caption,
last label, parser failures and global logs directly against the original.

Integer and floating evaluators retain the original grammar and rounding rules.
The new GHC-formatting methods provide scientific output at explicit precision
and shortest fixed decimal output. GHC's default atan2 formula must be used
instead of C atan2: the original rejects atan(infinity,infinity). Integer-result
rounding must convert Integer zero to positive Double zero. Invalid caret
escapes reproduce GHC's chr bounds and error text. Transcendental Double primops
use the host libm through the already retained JNA dependency.

`tools/haskell-latex-kernel-reference` compiles the complete original
LaTeX.Parsing module, with only its module name, public PandocMonad reexport
import and additional private-helper exports changed. The generator verifies
that transformation and all **2,888** prepared Pandoc files (the same three
packaging-only module exposures documented in the previous phase). All earlier
corpora and observers are retained unchanged. The **34,080** observations carry
**17** source hashes and case SHA-256
`09e6271aa7ad45148036a6c8a0c49e63820dc6205cf5536b8cab5cad4e344a95`.
They include 24 evaluator expressions extracted from the original command
fixtures, 5,000 signed binary operations, all evaluator functions and arities,
1,600 atan2 operation-order cases, 14,576 scientific-format observations, 3,672
token-parser/raw/backtracking cases, all argument processors/specifications,
conditional and scope witnesses, 42 Sources-bridge cases, nine deferred-demand
cases and finite expression/token inputs up to 10,000/100,000 levels or tokens.

A second observer extracts the unchanged pure numeric declarations from Parsing
and Shared. All **29,492** macOS/aarch64 results agree with the complete original
module. The identical requests were compiled with GHC 9.12.2/text 2.1.2 on
Ubuntu 24.04/glibc 2.39 x86_64; **89** libm-dependent floating results differ from
macOS. The tests use exact native expectations, with no tolerance or skipped
numeric cases. Both extraction/source checks verify request parity, unchanged
non-floating results and every recorded platform difference. The Dockerfile pins
its compiler/base images. Original upstream and GHC licenses remain in the JAR.

This is not a completed LaTeX reader or a claim that all of Parsing's partial
Haskell-value behavior/derived Show instances have been ported. Macro-definition
commands in LaTeX/Macro.hs, the complete LaTeX reader and all original reader AST
and command/report assertions remain pending. Other Pandoc readers/writers,
application/Lua/property tests, then calibre MOBI and all its tests and the
additional calibre formats remain required by the unchanged full objective.

Final validation passes **2,195,018 tests in 115 suites**, with zero failures,
errors or skips (6m 50s). The new suite contributes **34,081 tests** (4.498s in the
full run), including the platform-corpus parity assertion. The same final 34,081
cases also pass under Linux x86_64/glibc 2.39/JDK 21 with the unchanged **2 GiB**
heap limit. All **120 source/fixture checks** pass. All **22** distinct source
hashes agree with staged/root or pinned upstream Git blobs; the release pins,
earlier corpora and earlier observers are unchanged. Installed/core JAR bytes
match, including all **63 compiled LaTeX classes**, **56 full notices**, **250**
Pandoc assets/indexes and unchanged MathML/text mappings. Installed CLI workflow
smoke cases pass. No LaTeX CLI reader is registered by this dependency phase;
full original reader and command assertions remain required. The previous
**a703f368** exact-SHA CI test job also completed successfully, including its
complete source, Gradle and CLI checks.

## LaTeX macro definitions and global scope transformations

`LaTeXMacros` now implements the complete finite Macro.hs algorithm: macroDef,
its quick command registry, all 33 definition commands, insertion/lookup,
let/edef/xdef/def/gdef/global/newif, classic and xparse command/environment
constructors, command/environment copies, letterize, bracketed numeric argument
counts, xparse specifiers, modifiers, processors and body restrictions. It uses
the preceding token/macro engine and the existing Pandoc state/reporting layer.
No identity expansion or upstream program is used for production behavior.

The implementation retains upstream behavior, including permissive Renew/Declare
creation, New/Provide duplicate policies, global scope copied by let/copy,
end-code helper definitions for document environments, unsupported argument
specifier errors, required-delimiter fallback, optional-number read failure and
Int64 wrapping. The original private helper/argument/default behavior is tested
through definitions and calls, rather than a separately invented representation.
The raw definition constructor is used only when latex_macros is disabled.

`MacroScopes.mapAll` translates NonEmpty.map without eagerly traversing or
materializing outer tables. The persistent stack stores deferred tables/tails;
push, local head replacement and pop retain their constant-time structure.
Global definitions affect inherited/outer groups, while local definitions and
verbatim-mode state retain the original scoping behavior. Repeated global
updates can leave unused outer tables unevaluated. Tests exercise both deeply
nested scopes and long definition streams with 100,000 steps.

The observer compiles the complete pinned Macro.hs and Parsing.hs. Macro changes
are limited to its module/import names, two private helper exports, and a LINE
pragma preserving the canonical source filename/line numbers in pattern-match
errors. Parsing and transport copies are checked against the protected preceding
observer byte-for-byte (only the transport module name changes). The prepared
Pandoc build again verifies all **2,888** tracked files, allowing only the same
three packaging-only module exposures. The original command/reader test files
and their expectations are unchanged.

The corpus contains **1,553** direct observations with case SHA-256
`bac821036a4aa0965149279f80b4e07251cb2b6681f90f8fd8c50ab5134f4b5c`.
All **127** matching original command inputs from **44** files and all **90**
reader unit inputs run through the definition/token component with macros both
enabled and disabled. Further cases cover every definition command and existing
name policy, raw capture/backtracking, all xparse specifiers/modifiers/processors,
malformed syntax, Int64 wrapping, snapshot/scoping semantics, nested definitions,
newif, environment copies/helpers, global logging and all **1,114,112 Haskell
Chars** through letterize (17 independent digest witnesses). Seven deep
witnesses compare complete output hashes and final state, not just successful
termination. All earlier corpora and observers remain protected.

This component driver is not readLaTeX: the full reader AST/command/report
assertions remain pending, including the complete CLI semantics of the 127/90
inputs. General partial Haskell-value behavior and the remaining Parsing derived
Show work also remain in scope. The next dependency is the full LaTeX reader
and its command/environment dispatch. Other Pandoc readers/writers, complete
application/Lua/property tests, then all requested calibre MOBI tests and the
additional calibre formats remain required by the unchanged objective.

Final full Gradle validation passes **2,196,571 tests in 116 suites**, with zero
failures, errors or skips (8m 12s). The new macro suite contributes all **1,553**
observations (11.340s in the full run). All 1,553 also pass under Linux
x86_64/glibc 2.39/JDK 21 with the unchanged **2 GiB** heap limit (7.136s).
All **121 source/fixture checks** pass; all **63** corpus source hashes match
staged/root or pinned upstream Git blobs. All three release pins, earlier
corpora and earlier observers remain unchanged. Installed/core JAR bytes match,
including all **67 compiled LaTeX classes**, **56 full notices**, **250** Pandoc
assets/indexes and unchanged MathML/text mappings. Installed CLI workflow smoke
cases pass. No reader or test was disabled, no worker memory/recycling setting
changed, and no partial LaTeX reader was registered. Exact-SHA CI for the
preceding **c66ce1bb** commit is verified successful.


## LaTeX citations, mathematical environments and theorems

`LaTeXCitations` ports the complete finite Citation.hs algorithm: all 52 command
names, simple/multiple citation arguments, prefix/suffix placement, citation
modes, footnote wrapping, citeauthor/citetext composition, nocite metadata and
raw LaTeX capture. Optional bracket arguments use parseFromToks and retain macro
and state changes. The implementation retains the source's citetext grouping:
mconcat flattens the semicolon parser's lists of callback results; it does not
merge each group into a single Inlines before extracting citations. Prefixes or
suffixes returned by a separate callback are therefore not silently attached.

`LaTeXMathReader` ports the complete finite Math.hs algorithm: math-mode scopes,
dollar delimiters/nesting, all 23 environment entries and substitutions,
theoremstyle, newtheorem, theoremEnvironment, proof and private block helpers.
It preserves the empty-dollar fallback, math environment's distinct trimming,
ASCII-only trimr whitespace, unmatched-brace failure, theorem-state snapshots,
shared/missing numbering series, chapter/Int64 numbering and label updates.
Only direct Para spans contribute theorem labels. Plain theorems walk nested
blocks with the original single-image exception, then remove labels. Title and
QED concatenation retain the original direct-list boundary behavior. Production
uses the existing Kotlin parsing/state/walk/AST implementation, with no upstream
runtime conversion dependency.

The observer compiles complete pinned Citation.hs and Math.hs, changing only
module/import names and private-helper exports. Parsing, Macro and the kernel
observer are byte-identical protected copies from the preceding macro oracle;
the complete state transport changes only its module name. The prepared library
again verifies all 2,888 tracked original files, allowing only the same three
packaging-only module exposures. All upstream tests and expectations are
unchanged. Ordinary results compare complete AST JSON, all LaTeX state fields,
remaining tokens, position, expanded flag, raw capture, parser/Pandoc errors and
global reports. JSON numeric equality uses exact decimal values, matching Aeson
Scientific equality for fixed/exponent spellings; no floating tolerance or
conversion to Double is introduced.

The corpus contains 22,557 independent observations with case SHA-256
`81457be37b7fbd00072b509ad3fde84f1742aa5cbb11e04c47131f6e159ba941`.
Every command is exercised with valid, malformed, Unicode, multi-key and
prefix/suffix inputs. Stateful/failing callback cases cover optional-argument,
math-mode and caption backtracking and global reports. All 266 original Native
AST documents pass through the block walks/title/QED helpers; all 10,341 original
inline lists and 30 citation lists exercise citation extraction/placement.
Twelve deep finite witnesses compare hashes of complete original AST Show
results plus final state for dollar nesting, citation streams, nested math modes
and theorem block walks at 1,500, 10,000 and 100,000 levels/items.

All 90 original LaTeX reader inputs and 36 matching command inputs also run
through explicitly documented test callbacks. Those callbacks implement only
literal tokens, groups, macro definitions, emph/textbf and citation commands;
they are not readLaTeX and do not substitute for the original full-reader AST,
CLI or report assertions. Those remain pending. The remaining Table, Lang,
SIunitx and Inline modules and complete reader dispatch/grammar are still
required, as are general partial Haskell-value behavior and remaining Parsing
Show work. Other Pandoc readers/writers, complete application/Lua/property
tests, then calibre MOBI and all its tests and additional calibre formats remain
required by the unchanged full objective.

Final full Gradle validation passes **2,219,128 tests in 117 suites**, with zero
failures, errors or skips (11m 9s). The new suite contributes all **22,557**
observations (18.850s in the full run). The identical new suite also passes on
Linux x86_64/glibc 2.39/JDK 21.0.12.1 with the unchanged **2 GiB** heap limit
(4.517s). All **122 source/fixture checks** pass. All **60** new corpus source
hashes match staged/root or pinned upstream Git blobs; all three release pins,
earlier corpora and earlier observers are unchanged. Installed/core JAR bytes
match, including all **80 compiled LaTeX classes**, **56 full notices**, **250**
Pandoc assets/indexes and unchanged MathML/text mappings. Installed CLI workflow
smoke cases pass. No reader/test was disabled, no worker memory/recycling
setting changed and no partial LaTeX CLI reader was registered. Exact-SHA CI for
the preceding **6fe16ccf** commit is verified successful.


## LaTeX language mappings, quote contexts and translation selection

`LaTeXLanguages` implements the complete finite Readers/LaTeX/Lang.hs algorithm:
setDefaultLanguage, all 85 Polyglossia mapping functions, all Babel aliases, all
six quote commands and all 86 inline language commands. Its public registries
retain original key ordering and defer parser construction until an entry runs.
They use the shared Kotlin token grammar, quote-context capability, InlineShared
space extraction, six-field Lang model and Pandoc translation-state operations.

The port retains the distinction between Babel and Polyglossia records, including
the Greek polyton region/variant difference and classic Latin variant spelling.
Option matching removes only ASCII spaces, preserves option order/case and
retains unknown-option fallbacks. Language commands remove all brackets from
raw options. Polyglossia inline commands extract boundary spaces/softbreaks;
foreignlanguage and foreign/hyphen quotes keep their different wrapping rules.
Quote choice observes the initial context and star, sets the callback context,
and restores it on success with the original parser failure/backtracking rules.

Recognized default languages replace the local lang metadata and call the real
Kotlin setTranslations operation. Equal-language loaded tables are retained;
other selections become lazy unloaded caches. The whole Lang record participates
in equality. Unknown language names leave metadata and common state alone.
Common-state changes survive parser try/lookAhead even when local metadata is
rolled back. No upstream executable or test callback is used for production.

The observer compiles the complete pinned Lang.hs, changing only its module
name and Parsing import. It compiles the protected Parsing/Macro/Citation/Math
and state/token helpers directly from the preceding observer folder; the copied
observer driver changes only its module name. All transformations and 90 source
hashes are checked. The complete prepared original library verifies all 2,888
tracked files. Its only source difference is pandoc.cabal exposing the preceding
three modules plus Class.CommonState, so the observer can inspect the actual
original cache without replacing PandocMonad. Earlier prepared trees, observer
sources, corpora, original tests and license notices are unchanged.

Deep language compositions exposed repeated copying of growing diagnostics in
the shared ParseError.merge path. The source retains a lazy list append, while
the old Kotlin List.plus eagerly copied both operands on each merge. ParseMessages
now keeps persistent concatenation nodes and traverses them with an explicit
stack. Every message and duplicate remains in its original order; message
formatting, comparison, position selection and public ParseError data fields are
unchanged. Collection.size saturates at Int.MAX_VALUE while traversal retains
larger shared lists. The generator verifies the immutable Parsec Hackage archive
SHA-256 and every vendored source/test/license byte before capturing its Error
observations. Existing upstream notices and fixtures remain unchanged.

The corpus contains 30,431 original observations with case SHA-256
`58d23df596f6d4635bbea0195b368361f8bfa82ebc94b30d3ad0fe8948aba07f`.
It checks complete Lang Show/render results, ASTs, all 30 parser state fields,
positions, remaining tokens, raw capture, errors, global reports and seeded
common-state/cache values. Cases cover every registry/mapping, Babel aliases,
malformed/unknown arguments, nested quotes, context-mutating/failing callbacks,
macro expansion, ASCII versus Unicode whitespace, option order/bracket removal
and full-record cache differences. All 10,341 inline lists from the 266 original
Native documents run through language-space extraction and quote wrapping.
Six finite witnesses compare hashes of complete original AST Show output and
final state for 1,500/10,000/100,000 nested quote/language parser compositions.
Eight direct original Parsec Error cases compare complete diagnostic-message
hashes, counts and Show after 0/1/1,000/100,000 left/right-associated merges.
Two additional cases observe unknown/position on shared exponentially large
message lists without forcing those lists. A Kotlin ownership/indexing check
ensures concatenation snapshots input leaves, retains order and supports shared
lists beyond Int-sized counts without discarding their iterator contents.
JSON numbers retain exact decimal equality, without floating tolerances.

All 90 original LaTeX reader inputs and 12 matching command inputs from the
tracked language/quote fixtures also run through the explicit component driver.
Its callbacks support literal tokens, groups, emph/textbf, macro definitions,
citations and language/quote commands; they are not the complete readLaTeX
parser. Full original reader AST, CLI and report assertions remain required.
Table, SIunitx, Inline and the complete LaTeX reader dispatch/grammar are still
pending, as are general partial Haskell-value behavior and remaining Parsing
Show work. Other Pandoc readers/writers, all application/Lua/property tests,
then calibre MOBI and all its tests and additional calibre formats remain in
the unchanged full objective.


Final full Gradle validation passes **2,249,560 tests in 118 suites**, with zero
failures, errors or skips (9m 29s). The new suite contributes **30,432 tests**:
all 30,431 original observations plus the ownership/indexing assertion (6.780s
in the full run). The identical final suite passes on Linux x86_64/glibc 2.39/
JDK 21 with the unchanged **2 GiB** heap limit (6.409s). All **123 source/fixture
checks** pass. All **90** new corpus source hashes match staged/root or pinned
upstream Git blobs. The three release pins, earlier corpora/observers, ParseError
public data fields, worker memory and recycling settings are unchanged.
Installed/core JAR bytes match, including all **82 compiled LaTeX classes**,
**204 parsing classes**, **56 full notices**, **250** Pandoc assets/indexes and
unchanged MathML/text mappings. Installed workflow smoke cases pass. Exact-SHA
CI for the preceding **bd3fa5c7** commit is verified successful. No reader/test
was disabled and no partial LaTeX CLI reader was registered.
All five installed CLI check scripts also pass: **375 cases** comprising 14 FB2,
66 media, 69 format-modifier, 85 math-option and 141 HTML conversions/errors.


## LaTeX SIunitx numbers, quantities and unit expressions

`LaTeXSIunitx` ports the complete finite Readers/LaTeX/SIunitx.hs algorithm:
all 12 commands and v3 aliases, number/list/range/angle handlers, optional value
prefixes, unit parts, recursive modifiers, prefix/suffix powers, subscripts,
per/slash infixes and hyphen/spacing helpers. All 21 modifier entries and 152
unique unit keys retain their original complete AST values (including the
original duplicate Pa entry). Production uses Kotlin parsers/builders/walks,
without an upstream converter dependency.

The numeric grammar retains leading-plus removal, Unicode minus, permissive
multiple-dot bases, comma replacement, uncertainty placement/trailing-zero
rules, plus-minus/imaginary/multiplication/exponent syntax, ASCII-space removal
and whole-input fallback to Builder.text on failure. It uses Pandoc's Sources
char/string/satisfy primitives, not Parsec.Char's differently labeled/atomic
primitives. Both complete formatted results and exact private parseNum errors,
message lists and positions are independently observed. The immutable numeric
parser graph is shared, matching the original top-level parser value.

Unit parsing preserves scope/raw capture, separators, recursive modifier lookup,
one suffix after ordinary bases, the distinct prefix branch, nested per/slash
associativity and first-match duplicate per-mode options. Per prepends a minus
to an existing superscript instead of performing algebraic negation; negative
exponents can therefore retain the source's double-minus result. Hyphen walking
changes only Str nodes throughout complete inline/block/citation trees. Lists
retain empty semicolon parts, unconditional unit spacing and the source's final
conjunction; angles retain only the first three parts and remove a leading plus
without rewriting their minus characters. Unsupported rounding options remain
ignored as upstream.

The observer compiles the complete original SIunitx module, changing only its
module/Parsing import, private exports and PandocMonad's public re-export import.
The preceding driver copy changes only its module name; other complete original
modules and state/token observers compile directly from protected preceding
folders. The prepared library again verifies all 2,888 original files and only
the four documented pandoc.cabal module exposures. All original test fixtures,
earlier observers/corpora, vendored Parsec sources/tests and licenses remain
unchanged. Capture verifies the exact immutable Parsec archive and every
vendored source/test/license byte.

The corpus contains 34,007 independent original observations with case SHA-256
`4b460cf70b936565596981264a306b51ee0415eafe74da8057635ea4712b7bef`.
All 3,182 numeric inputs have both formatting and full parser/error observations.
All 3,192 modifier/unit combinations, every unit with prefix/suffix powers, all
command aliases, malformed/grouped/ungrouped input, range/list/angle shapes,
duplicate options, macro/raw/verbatim paths and stateful/failing callbacks are
covered. Three intentional empty-successful callbacks preserve Parsec's original
many-contract exception. All 10,341 inline lists from 266 original Native
documents exercise the complete exponent hyphen walk and conditional spacing.
Seventeen digest witnesses cover all 1,114,112 Haskell Chars through tonum.
Eleven finite witnesses compare complete original AST Show hashes and final
state: long uncertainty numbers, modifier recursion and 100,000-level exponent
walks, plus per-infix chains up to 10,000 levels. Expected JSON is decoded per
case to retain the full corpus within the unchanged 2 GiB worker heap; no case
is omitted. JSON numeric equality remains exact decimal equality.

All 90 original reader unit inputs and all 24 matching command inputs from the
four original SIunitx command files also run through the documented component
callbacks. The callbacks handle literal tokens, groups, formatting, macros,
citations, language/quote and SIunitx commands; they are not readLaTeX. Original
full-reader AST, HTML/native CLI golden and report assertions remain required.
Inline, Table and the complete LaTeX reader dispatch/grammar remain pending,
as do general partial Haskell-value behavior and remaining Parsing Show work.
Other Pandoc readers/writers, all application/Lua/property tests, then calibre
MOBI and all its tests and additional calibre formats remain in the unchanged
full objective.


Final full Gradle validation passes **2,283,567 tests in 119 suites**, with zero
failures, errors or skips (10m 39s). The new SIunitx suite contributes all
**34,007** observations (10.273s in the full run). The identical final suite
passes on Linux x86_64/glibc 2.39/JDK 21 with the unchanged **2 GiB** heap limit
(6.718s). All **124 source/fixture checks** pass; the final SIunitx check verifies
**91** source hashes, including Sources and the Parsing re-export facade.
All 34,007 expectations remain unchanged after correcting the primitive family;
all source hashes agree with staged/root or pinned upstream Git blobs. The three
release pins, earlier corpora/observers and worker memory/recycling settings are
unchanged. Installed/core JAR bytes match, including all **93 compiled LaTeX
classes**, **204 parsing classes**, **56 full notices**, **250** Pandoc assets/
indexes and unchanged MathML/text mappings. Installed workflow smoke cases pass.
Exact-SHA CI for the preceding **8080d447** commit is verified successful. No
reader/test was disabled and no partial LaTeX CLI reader was registered.


## LaTeX inline commands and pinned canonical normalization

`LaTeXInlines` ports the complete finite Readers/LaTeX/Inline.hs algorithms:
all eight command registries (210 entries), rawInlineOr, labels/references,
verbatim/lstinline/mintinline, listings language selection, Roman numeral
arguments, accents, literal symbols, BibLaTeX formatting, translated names and
singular/plural acronyms. Parsing retains exact consumed/empty replies, token
remainders, source positions, raw captures, local-state restoration and
common-state/log/cache effects. Term translation is deferred until parsing and
uses the existing complete translation/Data implementation, including actual
embedded YAML files, user replacements, fallback and cache behavior.

The accent dependency exposed a real Unicode-version mismatch: original
unicode-data 0.9.0 assigns combining classes to marks such as U+1ADE that ICU
78.1 treats differently. This changes canonical ordering. The Kotlin
`CanonicalNormalization` ports unicode-transforms 0.4.0.1's finite NFC path,
with explicit recursive-decomposition work storage, stable canonical ordering,
composition blocking, starter composition and algorithmic Hangul handling.
It uses the pinned original tables rather than the host JDK/ICU normalizer.
The original 121,106 observations remain unchanged; no differing expectations
were replaced or tolerated.

`import-unicode-nfc.py` extracts all 1,002 nonzero combining classes, 2,081
immediate canonical decompositions, 961 combining compositions and 59 starter
compositions, and verifies them with the compiled original functions. Every
Haskell Char is queried for nonzero classes, combining/starter predicates and
canonical decomposability; all source composition pairs are independently
queried. The full original unicode-data and unicode-transforms source/test trees
are retained from immutable Hackage archives, with full Apache-2.0 and
BSD-3-Clause notices. Production conversion is Kotlin and does not invoke an
upstream executable.

The observer compiles the full original Inline module, with only its module
name, two private exports, the OriginalParsing import and the public
PandocMonad re-export import changed. All 2,888 prepared Pandoc files are
verified; only the previously documented four-module packaging exposure differs.
The observation/capture scripts preserve the original library and use explicit
component callbacks rather than substituting an implementation for readLaTeX.

The independent corpus contains **188,741 observations**. All 94,980 original
normalization-column predicates pass both upstream and Kotlin. Seventeen digest
witnesses compare all 1,114,112 Haskell Chars with all 22 distinct Inline accent
marks (24,510,464 pairs). Additional observations exercise every pinned
canonical decomposition, combining class and composition with ordering/blocking
probes, all 11,172 Hangul syllables, all 38,025 jamo-range pairs including gaps
and U+11A7, and 1,024 deterministic mixed sequences. All 10,341 inline lists
from the 266 original AST documents exercise accent and BibLaTeX callbacks.
Every name command loads each of the 107 original translation files; user
replacement/decode failures, regional fallback, language changes and cache
retention also run through the real translation layer. Twelve deep finite
witnesses cover verbatim/code output and 100,000-level inline structures.

All 90 original LaTeX reader inputs and 90 matching command inputs from 49
files run through the documented component callback. Source hashes additionally
cover 268 matching command files, including output occurrences and non-LaTeX
commands; that number is not an executed command-input count. These component
observations do not replace the original full-reader AST/CLI/report assertions.
Table.hs, complete reader dispatch/grammar, remaining Parsing Show/partial-value
semantics and all original full-reader assertions remain pending. The broader
Pandoc readers/writers/application/Lua/property suites, calibre MOBI and all its
tests, then calibre formats absent from Pandoc remain in the objective.

The complete new suite passes on Linux/x86_64 JDK 21 in the unchanged 2 GiB
worker budget: **188,741 tests, no failures/errors/skips** (9.099s). The preceding
SIunitx checkpoint `bfc705f5afb547adc96c8c3bf5415242fff549ed` has successful
exact-SHA CI: https://github.com/Heapy/kotdoc/actions/runs/38766769849.

All **126 source/fixture checks** pass. The final Inline corpus contains **560
source hashes** and case SHA256
`229cc61c1bad4b291db262e394cd395505e6516dcbaa894348875024f7c6f8fe`.
All source hashes match staged/root or pinned-submodule Git blobs; the original
`no.yaml -> nb.yaml` symlink is also checked as a link and its dereferenced
content is checked against the target Git blob. All earlier tracked conversion,
test, fixture and observer bytes remain unchanged. The worker heap/recycling
settings and all three release pins are unchanged. The 86-source-hash NFC table
resource and full Unicode license/NOTICE copies have independent provenance
checks. Original vendored source/benchmark whitespace is retained verbatim.

Final full Gradle validation passes **2,472,308 tests in 120 suites**, with no
failures, errors or skips (8m 35s). The Inline suite contributes all 188,741
observations (9.709s in the full run). After the final license/provenance-only
updates, test resources and the installed distribution are rebuilt; the entire
188,741-case semantic hash remains unchanged. Installed/core JAR bytes match,
including all **104 compiled LaTeX classes**, **204 parsing classes**, the pinned
NFC data resource, all **59 full license/NOTICE files** and **250** Pandoc assets.
Installed workflow smoke cases pass. No existing reader/test was disabled and
no partial LaTeX CLI reader was registered.


## LaTeX table environments, row repair and table compaction

`LaTeXTables` implements the complete finite Readers/LaTeX/Table.hs algorithms:
all seven environment entries, rule and line-break parsing, word splitting,
alignment/width/repetition parsing, column prefix/suffix insertion with source
position rewriting, raw-cell collection with protected ampersands, simple and
empty cells, full multirow prototypes, nested multicolumn/multirow, table
head/body assembly, caption attachment, label removal and table numbering.
State changes and common-state reports use the existing parser/kernel machinery;
no reader, parser or translation implementation is replaced by an upstream
runtime dependency.

`LaTeXTableRows` preserves the original padding-cell removal separately from the
Builder's malformed-table normalization. Column/row spans are clamped only for
recording overhang, while cell widths used to discard padding retain their raw
values. Signed Int64 arithmetic and overflow are preserved. Persistent runs
represent lazy replicate/repeat Nothing overhangs without allocating one entry
per column, including widths above Int.MAX_VALUE. Ordinary row observations
compare complete lists. Extra Int64 boundary probes use finite input overhangs
and compare ten output overhang elements plus the entire returned cell list.
They retain extreme widths/heights and overflow without requiring the original
linear dropToWidth to traverse Long.MAX_VALUE empty entries. These are added
probes; no original upstream test is removed or altered.

`TableCompaction` ports Shared.compactifyTable with an explicit Walkable
dictionary. It tests all block lists for zero or one Para/Plain, changes Para to
Plain only when every list qualifies, then restores single Plain notes to Para.
The Builder.Many Block dictionary traverses each block without treating the
outer builder container as [Block]. This distinction is tested independently;
treating both containers alike prevents LaTeX table cells from compacting.
Notes, citations, captions, metadata and nested tables retain the original
traversal scope through the existing complete finite AST visitor.

The native observer compiles the complete original Table module, changing only
its module name, private exports and OriginalParsing import. InlineObserver
changes only the preceding observer's module name. The original Shared function
and all other Pandoc implementations remain unchanged. The importer verifies
all 2,888 prepared Pandoc files, the same four packaging exposures, immutable
Parsec/Unicode archives, protected parent observers/corpora and full notices.
The corpus contains **95,246 independent original observations**, with case
SHA256 `aa1ddfb6e41f0233e10da9141a2a70895e5de1ab5c4fff8e5ccb9ea8b8ed8258`.
It preserves all earlier captured observations when adding the final caption
probes. All **574 source hashes** and source symlinks are checked.

Compaction probes cover all original values in ten explicit Walkable shapes:
266 documents, 10,341 inline lists, 54,642 inlines, 5,211 block lists, 10,272
blocks, 242 table heads, 247 bodies, 242 feet, 752 rows and 2,394 cells. All
5,211 block lists additionally use Builder.Many Block. Row repair covers every
original table head/body, 425 exhaustive short finite-overhang cases, 1,500
mixed malformed/spanning cases, 28 Int64 boundary probes and deep finite lists.
Caption traversal uses every original document block tree with both absent and
present labels, chapter numbering and explicit matching label/space removal.
Twelve deep finite witnesses check complete original Show hashes through
100,000 rows, overhang entries, inline nesting levels and alignment columns.

All 90 original LaTeX reader input expressions and 19 matching command inputs
from 17 files also run through the explicit table component callback. Twenty-
eight matching command files are hashed. The callback supports paragraphs,
tables, captions, labels, parbox, inline math and the earlier inline component;
it is not readLaTeX. Original full-reader AST/CLI/report assertions still need
the complete reader dispatch/grammar. Remaining Parsing derived Show/partial-
value work and the full Pandoc reader/writer/application/Lua/property suites
remain in scope, followed by calibre MOBI and all its tests and calibre formats
absent from Pandoc. No partial LaTeX CLI reader is registered here.

The final new suite passes on Linux/x86_64 JDK 21 with the unchanged 2 GiB
worker budget: **95,246 tests, no failures/errors/skips** (7.681s). All **127
source/fixture checks** pass. The original vendored files, previous corpora,
release pins, worker heap and recycling settings are unchanged.

The dependency audit also ports Shared.isWS, trim, triml, trimr and
stripTrailingNewlines in `ast/SharedText.kt`. Shared whitespace is exactly SP,
CR, LF and TAB, distinct from the broader character-class predicate. Nine new
regression cases first demonstrated incorrect Unicode whitespace removal in
alignment-repetition errors; all pass after the Table parser uses SharedText.
Seventeen digest witnesses check every Haskell Char in three string shapes
through all four text functions and isWS (13,369,344 text transformations).
Original expectations remain unchanged. The final suite adds 119 whitespace
observations to the preceding 95,127-case capture.

The new dynamic-test factory streams fixture lines and releases each completed
case's payload, bounding retention of duplicated AST JSON within the unchanged
2 GiB worker limit. All cases remain independently executed. Two complete
project attempts exhausted heap. The second run's stack trace localizes the
failure after all nine workers finished to Gradle 9.8.1's
`TestTreeModel.StoreLoader.finalizePath`, reached from
`GenericHtmlTestReportGenerator.generate`. This is build-daemon report
aggregation, not a Kotlin assertion failure or a worker heap failure.
The installed Gradle implementation also loads the same full tree for JUnit
XML (`TestTreeModelResultsProvider.useResultsFrom`), so disabling HTML alone
would not address this retention. `gradle.properties` gives the build daemon
3 GiB for both reports; workers remain at 2 GiB with sequential execution and
recycling after 20 classes. No report or test is disabled. The full repeat with
this report budget succeeds: **2,567,554 tests in 121 suites**, zero failures,
errors or skips, in **10m 58s**. The new Table suite contributes 95,246 cases
(13.034s). Every XML file is parsed completely and its actual testcase,
failure/error/skipped elements agree with its declared counts. Both HTML and
JUnit XML reports are generated successfully.

Installed CLI validation passes 14 original FB2 conversions, 66 media cases,
69 format modifier cases, 85 math-option cases and all 141 HTML cases, including
17 original HTML units, 16 original fixed goldens and four original CLI
ordering/newline regressions. All four workflow smoke cases also pass. The
installed and core JARs have identical bytes; all 114 compiled LaTeX classes,
204 parsing classes, TableCompaction/SharedText classes, 59 full license/NOTICE
files, 250 Pandoc data assets and pinned NFC/MathML/text tables match the current
compiled classes and source resources. README's CLI output-format list now
includes the existing eight HTML/slide format names verified by these checks.
The preceding Inline checkpoint `d98992dc4291a95d20b71b1f6453fe447b201b48`
has successful exact-SHA CI:
https://github.com/Heapy/kotdoc/actions/runs/38772705386.

The next reader work can reuse Identifiers.uniqueIdent, include-cycle tracking,
listings mappings and PandocDataRuntime file effects. Reader-level registerHeader,
TEXINPUTS/include/listing effects and the full block/inline dispatch and grammar
still require implementation. jbcontext has no index for this checkout; these
dependencies were checked directly against pinned source paths.


## Complete LaTeX reader grammar and CLI integration

`LaTeXReader` ports the complete original Readers/LaTeX.hs grammar and dispatch,
reusing the preceding tokenizer, token parser, macros, language, citation/math,
SIunitx, inline and table implementations. It implements preambles, every block
and inline command registry and environment, sections, metadata, lists, figures,
notes/footnote marks, references, captions, raw fallback, toggles, includes,
filecontents, listings/minted file inputs and resource-path updates. The public
rawLaTeXInline, rawLaTeXBlock and inlineCommand Sources parsers use this actual
grammar and preserve outer macros, options, remaining input and source positions.
The existing applyMacros bridge remains in use. No original runtime is a
production dependency.

Header registration preserves the source's explicit duplicate identifier and
stored warning rather than inventing a replacement identifier. Automatically
generated IDs use the full reader's pinned text-2.1.2 and GHC Unicode-16
character dictionaries. A regression expansion exposed 54 failures when the
earlier independent text-2.1.3/ICU identifier profile was reused; all now pass
with the correct source profile. The earlier independent dependency port is
unchanged. Full uppercase/lowercase/title mapping data is generated from the
vendored text-2.1.2 source, and one independent observer witness enumerates all
1,114,112 Haskell Chars through all three operations.

The LaTeX date path uses source-derived time-1.14 Gregorian/ordinal/month and
local-hour/minute calculations. Integer dates and years are unbounded; negative
floor division, leap seconds and Int64 timezone overflow retain original
behavior. Four complete 146,097-day cycles compare 584,388 original day strings,
with 294 additional calendar/timezone boundary observations and five complete
reader SOURCE_DATE_EPOCH probes. The already tracked complete time release and
full BSD notice remain unchanged; a new provenance record pins the original
archive. This implements the date path needed here, not every time API/test.

Shared.tabFilter retains the original Text.lines/Text.unlines behavior,
code-point column counts, zero/negative tab-stop behavior and per-tab resets.
All 72 independent original observations pass, including non-BMP text and
Int64 minimum. Included files use the existing strict PandocText.toTextM,
preserving UTF-8 errors, BOM removal and CR filtering. TEXINPUTS and cached
filecontents follow the original lookup/retry order.

The native observer links the actual unmodified full readLaTeX implementation
in the prepared pinned original library. Only the same four private-module
packaging exposures differ. Capture verifies all 2,888 tracked source files.
All 90 original unit inputs and expected expressions remain unchanged; the
small test adapter applies their original Helpers ToString/line assertion
before emitting observations. Both original fixed reader goldens are checked
with the original Native writer before capture. Neither original implementation
nor expectations are derived from Kotlin.

The corpus contains 1,454 independent original observations. All 362 matching
original command inputs from 145 files now exercise the full reader AST, typed
errors and common-state effects. Their final command output and unrelated
application/writer flags are not asserted here; those original command tests
remain pending. Additional probes cover every environment, extension choices,
Unicode transforms, exact note-number reads, include/listing UTF-8/BOM/CR,
TEXINPUTS/cache behavior and all public raw Sources entry points. Complete
Pandoc AST values are compared as typed values, preserving numeric semantics
such as 2 versus 2.0 for ColWidth; original fixture JSON remains untouched.
Reports, errors and other observations retain exact comparisons.

The installed CLI now accepts latex and extension modifiers, named files or
stdin, tab/default-image options and the original read/write aliases. Both
stdin and named file input use Sources.fromFiles, including its added final LF,
as the original application does. Reader effects supply resource paths for
media extraction. Typed Pandoc errors retain their original exit code and human
rendering. The general application, all remaining readers/writers, Lua,
properties and unresolved dependency partial-value/Show behavior remain in
scope, followed by calibre MOBI/all tests and formats absent from Pandoc.

The preceding Table checkpoint f443cc1003022f8bbb62c17bea1b94f38a092454 has
successful exact-SHA CI:
https://github.com/Heapy/kotdoc/actions/runs/38780511554.

The new corpus preserves every preceding captured observation, inherits all
Table dependency hashes, and records 770 source hashes. Its semantic case SHA256 is
`55ef6f973af3b3625c2929af3ff0a57b1322d48fd5baa418cc8280abd2bb0c95`.

All 129 source/fixture checks pass. The first aggregate source check correctly
noticed an added provenance record under the already tracked time tree: the
older writer-environment manifest hashes that directory in full. Moving the
new record into the new observer directory restores the exact earlier tree and
its unchanged 61,878-observation corpus; no old expectations or source checks
are weakened. The final Reader manifest has 770 source hashes, verified against
staged root/pinned upstream Git blobs and the original symlink target bytes.
All previous corpora/observers and all three upstream release pins are unchanged.

The new suite passes on Linux/x86_64 JDK 21 with the existing 2 GiB worker limit:
1,454 tests, no failures/errors/skips/aborts (2.938s). Installed LaTeX CLI checks
pass all 101 cases, including all 90 original units, both fixed reader goldens
through stdin and named files, include lookup, dates, image-extension defaults
and typed invalid-UTF8 errors. The added graphicspath integration check compares
the exact original PNG bytes after HTML conversion and ZIP extraction. A manually written image Native golden first
failed only because of line wrapping; actual original readLaTeX/writeNative
independently confirmed the wrapped expectation before the checker was corrected.
The production writer and original goldens remain unchanged.

Installed/core JAR bytes match. All 206 compiled LaTeX classes, 204 parsing
classes, SharedText/TableCompaction/Gregorian classes, the pinned full case/NFC
resources, 59 full license/NOTICE files and 250 Pandoc assets match the compiled
classes/source resources. Original MathML and lowercase-only tables are unchanged.
All four installed workflow smoke cases pass. Worker/report budgets, sequential
execution, recycling and both HTML/XML reports retain the previous settings.

The existing installed CLI suites also pass unchanged: 14 original FB2
conversions, 66 media cases, 69 format-modifier cases, 85 math-option cases and
141 HTML cases, including all 17 original HTML units, 16 fixed goldens and four
original command ordering/newline regressions.

Final full Gradle validation passes 2,569,008 tests in 122 suites, with no
failures, errors or skips (10m 37s). The full-reader suite contributes all 1,454
observations (3.376s). Every XML file is parsed completely and its testcase,
failure/error/skipped elements agree with the declared counts. Both HTML and
JUnit XML reports are retained. The daemon remains at the previously established
3 GiB report budget; test workers remain at 2 GiB, sequential with recycling
after 20 classes. No previous test or report is disabled.

The last provenance/checker additions change only the new fixture's source
manifest, preserving all 1,454 observations and their exact semantic hash.
Test resources and the installed distribution are rebuilt afterward without
replacing the complete full-run XML reports. The final corpus and copied test
resource bytes agree. The original PNG used by the new end-to-end CLI check
is included in the final source manifest.


## Complete finite HTML reader grammar, public Sources APIs and text dependencies

`HtmlReader` ports Readers/HTML.hs using the preceding HTML state, token and
table parsers and the actual LaTeX raw Sources bridge. All document/block/inline
branches are implemented, including metadata and base URI, headings and stored
identifier warnings, main extraction, malformed lists and definitions, pre/code,
figures, SVG data images, MathML and original TeX annotations, quoted text,
span/HTML/raw modes, EPUB notes and references, TOC/title-page suppression,
checkboxes, iframe MIME routing, nested frames and the depth limit. The public
Sources htmlTag/htmlInBalanced APIs preserve predicates, errors, raw text,
positions and remaining chunks. All eight original module exports are represented;
classification and toAttr reuse the same reader dependencies.

`SmartParsing` implements the complete finite Smart module as ReaderT-aware
character parsers. Quote context is separate from last-string position and
options. Direct calls preserve start/end guards, failed-quote fallback,
context restoration, apostrophes, ellipses and both dash conventions. The
standalone state dictionary and HTML's local Reader context are both exercised.
`HtmlRendering` implements canonicalizeTags and the complete finite TagSoup
renderer, all three option callbacks, raw tags, minimized pairs, processing
instructions, comment repair and duplicate/empty attributes. Simple Char case
mapping for canonicalization is distinct from Shared.renderTags' full Text
lowercase dictionary, including the dotted-I case.

`PandocUtf8.toText` implements the exact strict path needed by iframe HTML:
remove one leading UTF-8 BOM and every CR byte, then decode. A host
UnicodeDecodeException remains distinct from PandocMonad's typed UTF8 error.
The source's StrictTextBuilder forces the last buffered byte for incomplete
prefixes, while invalid sequences report their lead byte. All 65,536 byte pairs,
512 seeded malformed arrays, BOM/CR/code-point boundaries and every original
encoded Haskell Char are checked independently. No original invalid byte is
silently replaced. The complete UTF8 IO/lazy/general dependency suites remain
in scope beyond this path.

The observer links the actual original full HTML reader in the verified pinned
library, with the same four packaging exposures and no implementation changes.
All 2,888 prepared tracked files are checked. OriginalReaderTests keeps all 69
input/expected expressions and the original roundTrip normalization/predicate.
Only its module export and framework imports change. The adapter performs the
original Helpers ToString and Haskell lines assertion before capture. Reader
options for each unit group are independently rechecked against its complete
original result. The original fixed reader golden is also validated with the
original Native writer.

The roundTrip translation performs the source's normalization and all three
WrapPreserve HTML writer/readHtml rewrites, comparing every original HTML string
and intermediate AST before the final predicate. It covers all 266 original
documents, 25 additional original Arbitrary Blocks witnesses, and a separate
actual QuickCheck run with the original generator, shrinker, size schedule,
withMaxSuccess(25) and explicit replay seed. The original QuickCheck run succeeds
with 25 observations. This does not replace the remaining full Kotlin
QuickCheck/generator/shrinker work with a smaller fixture-only objective.

Iframe effects are observed using a ReaderT/StateT fixture that overrides only
openURL and delegates every other PandocMonad method to PandocPure. Success,
MIME selection, nested common-state report order and depth limits retain the
original reader implementation. A failed URL callback rolls back its StateT
append in this fixture; the Kotlin adapter records successful returns accordingly.
The production reader accepts an injected URL effect. CLI URL transport,
original full command outputs/options and remaining application/HTTP behavior
remain pending; no approximate HTML CLI reader is registered here.

All eight original HTML files and 173 explicit HTML command inputs now exercise
the full grammar. Their final command outputs and unrelated flags are separate
remaining assertions. The inherited LaTeX corpus and every earlier observer,
fixture, source release and license are unchanged. OriginalSmart.hs preserves
its complete original body, changing only module/import packaging and using a
verbatim General.isSpaceChar helper, checked by the importer.

The first 830-case comparison found two test-adapter newline mismatches for
standalone metadata Native output; implementing the original Haskell lines
comparison on both operands resolves them without changing expectations or
production output. Expanding direct Smart probes found 1,320 adapter failures
from decoding the fixture context by enum ordinal; explicitly mapping the
fixture's NoQuote/Single/Double tags resolves them. The original core enum/order
and every captured expectation remain unchanged. Successful iframe tests also
prove strict UTF-8/BOM/CR behavior instead of checking only missing URLs.

The final new corpus contains **7469 independent original observations**, with **876
source hashes** and semantic case SHA256
`f37ca67714e38a5699a93e8466a978272aa5bf11cf620e0129e9866f52a6dcd3`.
All previously captured observations are preserved on recapture. The targeted
macOS/ARM64 suite passes every case, with no failures/errors/skips (8.763s).
The encoded-Char witness compares a length-prefixed SHA256 over all 1,114,112
Haskell Chars through original UTF8.fromText/toText, including surrogate
replacement, CR removal and leading BOM removal. It does not inflate the
individual dynamic-test count. Full project and Linux checks follow below.

The Linux/x86_64 JDK 21 run passes all 7,469 new tests with no failures,
errors, skips or aborts (5.021s), at the unchanged 2 GiB worker budget.
All 130 source/fixture checks pass. All 876 manifest hashes match staged root
or pinned upstream Git blobs, including exact symlink and target contents.
The three upstream release pins and every previous corpus/observer remain
unchanged. No previous conversion implementation, test or expectation is edited.

The installed and core JAR bytes match. All current 93 HTML, 207 parsing,
206 LaTeX and 55 resource class files match the compiled output. The packaged
59 full license/NOTICE files and 250 Pandoc assets retain their original bytes.
Comparison with the preceding LaTeX checkpoint distribution proves that all
2,415 previously packaged class files and every previous JAR entry are exactly
unchanged; the existing CLI and dependency JARs are byte-identical as well.
All four installed workflow smoke cases pass. No CLI reader registration or
transport behavior is changed in this library phase.

The preceding LaTeX checkpoint cc67d32cc952cf22cd11f6bbddef0822a1256a99 has
successful exact-SHA CI:
https://github.com/Heapy/kotdoc/actions/runs/38787635645.

The new prepare.py creates a fresh complete original tree and frozen project,
changing only the four documented module exposures. This preparation was run
and all 2,888 tracked source/test/data files verified; the complete observation
corpus was then recaptured against that freshly prepared tree. The final source
manifest includes the preparer and its documentation (876 hashes), while every
observation line and the semantic case hash remain unchanged. These last
provenance-only changes are copied into test resources after the full run;
no test is rerun with altered expectations and the full XML reports are retained.

Final full Gradle validation passes **2,576,477 tests in 123 suites**, with no
failures, errors or skips (9m 39s). The new HTML reader suite contributes all
7,469 observations (5.833s). Every XML report is parsed completely and its
actual testcase/failure/error/skipped elements agree with the declared totals.
Both HTML and JUnit XML reports are retained. The existing 3 GiB build-daemon
report budget, 2 GiB sequential workers and recycling after 20 classes are
unchanged. No previous test, report or source verifier is disabled.


## HTTP request construction, wire effects and original request tests

The preceding HTML checkpoint is concrete progress at
85d905f19582a61ac7f12feb323c2495b14fdda1. This dependency phase follows the actual
Pandoc Class.IO.HTTP caller: openURL uses parseUrlThrow, prepends the captured
custom headers, and applies the lower-case http_proxy URL's parsed host/port.
parseUrlThrow installs throwErrorStatusCodes even though parseRequest's default
policy does nothing. The HTTP caller itself does not check statuses, but the
installed request policy does. The old ResourceContext is not an equivalent
implementation of this transport.

New Kotlin http/HttpRequest.kt translates the request preparation and writing
algorithms from pinned http-client 0.7.19. This includes raw Char/ByteString URL
handling, method prefixes, case-insensitive schemes, URI update/relative merge,
credential decoding, Basic/Bearer/proxy auth, proxy origin/CONNECT modes, query
and partial-query encoding, URL-encoded bodies, gzip selection, Request Show
with sensitive header redaction, and the two stock status policy selections.
ByteString fields preserve arbitrary bytes independently of Unicode strings.
The existing exact GHC numeric reader supplies readPositiveInt syntax and
64-bit wrapping before its nonnegative guard.

The writer translates all six body forms used by the source (strict, lazy
chunks, byte-producing builder, fixed stream, chunked stream and IO body),
Content-Length/Transfer-Encoding, ordered/duplicate/custom headers, implicit
Host and Accept-Encoding, exact Expect: 100-continue continuations, stream size
errors and observedStreamFile. Provider lifetimes use scoped callbacks; file
providers reopen per use. Progress includes the EOF observation and retains the
size captured before sending, even if a file subsequently changes. Empty
popper chunks terminate a stream. Popper exceptions bypass the body-exception
hook, whereas covered connection-write exceptions invoke that hook; streaming
header writes remain outside that hook, as upstream.

The translated Blaze toByteStringIO/bytestring threshold behavior retains
individual connection writes, not just concatenated bytes: a 32,752-byte output
buffer, the 8,160-byte copy threshold, direct large inserts, flushes and final
empty buffers. The relevant original complete releases and notices are retained.
New http-client (35 files), case-insensitive (12 files) and bytestring (67 files)
releases are byte-checked against their actual source archives; only separate
provenance.json files are added. Cabal's actual linked http-client and
case-insensitive source SHA256s agree with those archives. Existing http-types,
Network.URI and Blaze releases remain unchanged.

The independent observer links the actual original http-client package from the
Pandoc frozen dependency set. OriginalRequestSpec and OriginalRequestBodySpec
change only module names. A minimal assertion runner executes every unchanged
IO action, Eq/Show assertion, predicate and shouldReturn assertion. All **26
original tests** pass before capture; the Kotlin suite translates all 26 with
their literal expectations, including five original body variants in both
Expect modes and the original 75,000-byte file verifier.

The additional corpus contains **3,150 original observations** across URL/URI
parsing, all byte and representative raw Unicode/surrogate URL characters,
all percent-decoded auth bytes, all Latin-1 header folds/redaction, all query
bytes, status policy switching, exact gzip membership/first-MIME semantics,
proxy/header/body combinations, 270 exception-hook schedules, buffer boundaries,
repeated file sends and post-capture file changes. The observer actually invokes
the original checkResponse callback with a synthetic 404; this phase records
policy selection without implementing response/status execution in Kotlin.
The corpus has **204 independent source hashes** and semantic case SHA256
`f8803c93de33c96e2a303dffc535b685947d7cef7e9852e2794cdc8ce6ea4086`.
A provenance-only final recapture preserves every observation and this hash.

Initial comparison found 18 differences: the GHC reader also accepts binary
integer syntax, empty non-streaming Expect bodies still emit a final buffer,
and large builder inserts preserve separate writes. Reusing the pinned numeric
reader and translating the actual Blaze/bytestring write rules resolves these
without changing original expectations. Later native fault-scope and file
reopening probes pass as well. Targeted macOS/ARM64 validation passes all
**3,176 tests**. Linux/x86_64 JDK 21 also passes all 3,176, with no failures,
errors, skips or aborts (1.400s), at the unchanged 2 GiB worker budget.

All **131** source/fixture verifiers pass (51.5s). Installed/core JAR bytes
agree. All 37 new HTTP classes and the existing HTML/parsing/LaTeX/resource
compiled classes agree with the JAR. All 61 full notices and 250 original Pandoc
assets retain exact bytes. Every entry of the preceding HTML core JAR,
including all 2,461 old class files, is unchanged; the CLI and dependency JARs
are also byte-identical. Full project verification follows below.

Remaining HTTP work includes response/header/body parsing, status-response and
HttpException wrapping, redirects, cookies, manager reuse/configuration,
proxy/network/TLS execution, and all remaining original http-client suites.
The request model still needs the non-wire fields/arbitrary response callbacks,
generic Blaze builders, RequestBody Monoid and lazy/partial/infinite contracts;
this checkpoint does not claim complete http-client or URL transport parity.
The existing CommonState manager and all old conversion code remain unchanged.
HTML CLI is still pending that transport. The full objective remains unchanged:
all Pandoc code and original tests, then calibre MOBI/all tests, then calibre
formats absent from Pandoc and their tests. No Haskell oracle is a production
conversion dependency.


The preceding exact-SHA HTML CI run has now completed successfully:
https://github.com/Heapy/kotdoc/actions/runs/38794239397.
Git attributes explicitly preserve the complete retained HTTP release bytes and
original RequestSpec whitespace. A staged-blob audit caught Git's default CRLF
normalization in case-insensitive's original pg2189.txt; after renormalizing with
-text attributes, all 204 source hashes agree with actual staged/pinned Git
blobs. No upstream source or test bytes were edited to satisfy Git whitespace
checks. Every older Kotlin source, test, corpus and observer remains unchanged.


Final full Gradle validation passes **2,579,653 tests in 124 suites**, with no
failures, errors or skips (12m 13s). Every XML report is fully parsed; actual
case/failure/error/skipped elements agree with declared totals. The new HTTP
request suite contributes all 3,176 tests (2.052s in this full run). Both HTML
and JUnit XML reports remain enabled and retained. The existing 3 GiB build
daemon and 2 GiB sequential/recycled test-worker budgets are unchanged.

All six installed CLI checkers pass: 14 FB2, 66 media, 69 format modifiers,
85 math options, 141 HTML writer and 101 LaTeX reader cases (**476 total**).
The first checker invocation lacked JAVA_HOME and failed to locate a JVM;
repeating with the same explicit JDK 21 used for Gradle passes every checker
(395.0s). There was no source/expectation change for this environment correction.
The Linux run, source checks, staged source archive audit and JAR preservation
checks above also pass. This checkpoint completes request preparation/writing
work recorded here, not the remaining complete transport or the full goal.


## HTTP response parsing, incremental bodies and streaming zlib

The preceding goal turn is concrete progress: public commit
882027d0876ea5766bdbc24ac53c37dda4c2d657 and its synchronized clean mirror.
This phase reads the pinned http-client 0.7.19 Connection, Headers, Body,
Response and Types implementations and all four original related test suites.
The checkout still has no jbcontext index; known exact source paths are used.
The full Pandoc/calibre objective remains unchanged.

New http/HttpResponse.kt translates the connection read/unread stack and
idempotent close, raw-Char strippedHostName, connectionReadLine/With,
connectionUnreadLine and dropTillBlankLine. The original chunk-sensitive length
limit and unread-before-return effects are preserved. It parses status/version
with the actual bounded bytestring-0.12.2.0 readInt contract, distinct from the
wrapping GHC Read Int used for Content-Length and request ports. Header parsing
retains ordered/duplicate fields, space-only stripping, ignored malformed final
headers, early-hint unread behavior, count limits, up to three blank status
lines, 100 Continue and repeated 103 Early Hints. Header validation preserves
the original rule (LF in values) and complete diagnostic bytes.

The timeout boundary preserves zero/negative timeout behavior and the separate
Expect continuation scope. The default JVM effect uses an interruptible virtual
thread/FutureTask deadline. Independent actual System.Timeout witnesses check
zero, negative and successful deadlines, timeout during a delayed read, and a
continuation lasting longer than the header timeout while remaining outside it.
Full GHC asynchronous exception/runtime contracts are not inferred from these
finite witnesses or claimed complete.

Body readers translate constBodyReader, brRead, brReadSome, brConsume,
makeUnlimitedReader, makeLengthReader, makeChunkedReader and makeGzipReader.
They retain individual chunks, overshooting readSome, leftovers, trailers,
raw framing, hexadecimal machine-Int overflow, EOF and repeated cleanup effects,
short-body errors and exact error forcing. getResponse, lbsResponse,
getOriginalRequest and hasNoBody preserve first-header Content-Length,
case-insensitive exact Transfer-Encoding membership, no-body statuses/method,
case-sensitive Connection close value, HTTP version reuse policy, release and
keepAlive ordering, early hints and request-body erasure. The early-hint callback
now belongs to HttpRequest, so getOriginalRequest retains it for another response.
These managed effects are a real injectable resource boundary, not a claim that
the actual connection pool or network transport is implemented.

Parser/body failures have typed HttpFailureContent values and original
HttpExceptionContentWrapper Show text. The remaining full HttpException,
status/redirect responses and host/socket/TLS exceptions still require work.
HttpHeader now has the original CI-name/byte-value Eq and tuple Ord, with a
consistent Kotlin hash; HttpBytes has unsigned lexicographic Ord. This corrects
the initial model's generated case-sensitive Header equality. The corpus has
1,536 independent all-byte name/value/fold/prefix comparison witnesses.
The old Request observation corpus and every previous expectation remain intact.

New http/StreamingInflate.kt translates streaming-commons 0.2.3.1 init/feed,
popper drain, dictionary retry, finish/flush, unused-input and completion APIs.
It uses the existing JNA dependency to call the system C zlib primitives, with
C-width-aware z_stream fields and scoped/GC-cleaned native allocations. The
32,752-byte buffer, full-buffer pop, error-before-output rule, ignored init
return, non-error Z_BUF_ERROR/Z_NEED_DICT, repeated stream-end behavior and
flush semantics follow the original. HTTP gzip does not introduce validation
at EOF or automatically reset for another member. No Haskell executable or
whole-buffer gzip converter is a production dependency.

The complete unchanged streaming-commons release (36 files) is verified against
its Hackage archive SHA256
`33d16c0c6080795800d9f0e490203ea3e50bd5034e5d077a0ddc04d33ea4dc6e`.
Its original MIT notice is copied byte-for-byte to licenses and the core JAR.
The actual linked Cabal source SHA agrees with that archive. Original LICENSE
and test/LICENSE.gz are preserved verbatim in test resources.

The independent observer links the actual frozen original packages. All **26
original HeadersSpec, BodySpec, ResponseSpec and ConnectionSpec tests** run
unchanged before capture, with only module-name packaging differences. Every
original action, literal assertion and predicate is also translated to Kotlin.
Five further original streaming-commons inflater units are translated: single
and byte-at-a-time license inflation, different dictionary, large raw
inflation (four default buffers plus one byte), and getUnusedInflate. The
remaining full streaming-commons deflate/property and other suites stay pending.

For release/keepAlive observations, OriginalResponse.hs preserves the entire
original implementation; only its module/import packaging and pool capability
boundary differ. The actual original ResponseSpec separately runs against the
unmodified release and its original managed connection implementation. Additional
observations record exact reads/unreads/writes, body chunks, errors, continuation,
early-hint callbacks, cleanup/release/keepAlive and unread input. The observer
also verifies getOriginalRequest retaining its early-hint callback on a second
response. Direct inflater observations retain every feed/pop/flush/finish,
unused byte and completion transition across valid, invalid, truncated,
concatenated, dictionary and raw/gzip/zlib input.

The final corpus contains **2,662 independent observations** and **257 source
hashes**, with semantic case SHA256
`4c890044c1e4c9dbfb0b60ad1be16d71f8fa015787a61362f2cf4e5c7837939f`.
Together with 26 original HTTP tests and five inflater units it contributes
**2,693 new tests**. Initial 1,146-case validation passed, followed by the callback,
timeout, original inflater unit and CI-header equality/order extensions.
Linux/x86_64 JDK 21 passes all final 2,693 tests (3.114s), with no failures,
errors, skips or aborts, including actual native zlib calls and blocked-read
interruption. The existing 2 GiB worker budget is unchanged.

All **132 source/fixture verifiers** pass (58.4s). Installed and core JAR bytes
agree; all 77 current HTTP compiled classes match the packaged bytes. All 62
full notices and 250 Pandoc assets retain exact bytes. Comparison with the
preceding request distribution preserves every old non-HTTP JAR entry and all
2,461 old non-HTTP class files. Only the expected HTTP model/debug-table class
bytes and Kotlin module metadata change; every previous class still exists.
CLI and dependency JARs remain byte-identical. Full project validation follows.

Remaining HTTP work includes real pool/socket/TLS/proxy/manager behavior,
redirects/cookies, full exception/status response behavior, outstanding Request
fields and general builder/Monoid/lazy contracts, complete dependency suites,
and Windows native zlib distribution/loading. HTML CLI integration still waits
for genuine URL transport parity. All remaining Pandoc readers/writers/App/Lua
and original tests, then calibre MOBI/all tests and calibre formats missing from
Pandoc/all their tests, remain in the full active objective.


Final full Gradle validation passes **2,582,346 tests in 125 suites**, with no
failures, errors or skips (9m 8s). All XML documents are fully parsed; actual
case/failure/error/skipped elements agree with declared counts. The new response
suite contributes all 2,693 tests (3.649s in the full run); the preceding request
suite and every earlier suite also pass. Both HTML/XML reports, the 3 GiB build
daemon and sequential/recycled 2 GiB test-worker budgets remain unchanged.

The preceding request checkpoint's exact-SHA CI completed successfully:
https://github.com/Heapy/kotdoc/actions/runs/38798913843.
The staged source audit verifies all 257 hashes against actual staged/pinned
Git blobs, with the three source release pins and submodules clean. Every older
test, corpus and observer is unchanged. The only previous implementation file
edited is the HTTP request model for CI equality/Ord and early-hint ownership;
all other implementation files are new. The installed distribution and CLI
byte-preservation checks above remain valid. This is a response/body foundation
checkpoint; complete Pandoc and calibre conversion/test coverage remains active.

### HTTP cookie dependency: pinned Public Suffix List

The complete objective remains all Pandoc behavior and original tests, followed
by calibre MOBI/all tests and calibre formats absent from Pandoc/all their tests.
Cookie/redirect discovery found no jbcontext index for this checkout. A read-only
dependency exploration identified existing lenient UTF-8 and UTC representation,
but missing PSL lookup, cookie dates and Data.IP parsing. The preceding response
checkpoint's exact-SHA CI was observed still running its full Gradle suite.

`http/PublicSuffixList.kt` translates the bundled upstream Types, Lookup and
Serialize modules. It supplies immutable Text-keyed trees, the original rules
and exceptions pair, effectiveTLDPlusOne/isSuffix for the built-in and custom
trees, and both binary serializer directions. Map keys use Unicode scalar
ordering and Text's surrogate replacement; duplicate keys keep their last value.
Lookup retains explicit-before-wildcard selection, leaf/non-leaf distinctions,
exception precedence and the original unknown-domain fallback. As upstream,
it does not lowercase, apply IDNA, or validate domain syntax.

The production resource contains exactly the original embedded ByteString
literal's **60,137 bytes**, generated at 2015-04-29 04:59:31.367899 UTC. No current
host/online suffix list is substituted. Native putDataStructure independently
re-emits those same bytes. There are **7,330 rule nodes and 19 exception nodes**;
every node, including internal nodes, is observed with exact/deeper domains,
wildcard substitution, uppercase and leading/trailing empty labels.

The binary codec preserves sorted Map output, repeated-key replacement,
implicit tree closure at EOF, ignored bytes after the two trees and strict
UTF-8 rejection. The corpus also checks custom wildcard/exception conflicts,
empty exceptions, NUL/dotted/Unicode keys, supplementary versus BMP order,
surrogate normalization, deterministic generated trees, every single-byte
value and truncated multibyte UTF-8. Exact Haskell UnicodeException Show and
general lazy forcing order remain pending dependency/runtime work; the malformed
UTF-8 witnesses compare strict failure, not host-specific error strings.

The new observer compiles the **four unmodified vendored modules directly**
with the existing frozen original GHC 9.12.2 dependency set. It calls original
functions and only projects JSON inputs/results. This release supplies no
separate PSL test module; observations are not claimed as translated original
unit tests or a substitute for the remaining CookieSpec. No Haskell code or
executable is used in production.

The independent corpus contains **51,670 observations**, with semantic SHA256
`dfeedfab0daad58b6901055cb67c6f4610298d80a3e081d0a59f5f10c3971db2`.
The new Kotlin suite adds these plus two ownership checks for tree snapshots
and resource byte arrays, for **51,672 passing tests**. Source and distribution
validation follows. Cookie date parsing, storage/generation, Data.IP loopback
semantics, redirects and the full pool/socket/TLS transport remain pending,
along with the unchanged full Pandoc/calibre objective.

Validation: all **51,672** new tests pass locally (1.106s suite time) and on
Linux/x86_64 JDK 21 (0.826s). There are no failures, errors, skips or aborts.
The entire local XML report is parsed and its declared count matches the actual
testcase elements. All **133 source/fixture verifiers** pass (37.2s); the new
corpus retains **265** independent source hashes. The installed distribution
and core JAR agree; all 86 HTTP class files, all 62 complete notices, all 250
Pandoc assets and the new binary snapshot match source/build bytes exactly.
Compared with the preceding response checkpoint, all **2,538 existing classes**
and all other existing JAR entries remain byte-identical, except the expected
Kotlin module metadata that exposes the added declarations. CLI and dependency
JARs are byte-identical too. Existing tests, corpora and observers are unchanged.
This is an additive cookie dependency checkpoint; it does not assert a new full
project suite run or completion of Pandoc/HTTP/calibre.

### Web.Cookie header codec, record behavior and cookie dates

The unchanged objective remains all Pandoc behavior and tests, then calibre
MOBI/all tests and other calibre formats absent from Pandoc/all their tests.
The preceding PSL checkpoint is published at 0d3cec0; its exact-SHA CI and the
preceding response CI were re-polled and observed running, not assumed green.

`http/WebCookies.kt` translates cookie 0.5.1 parseCookies/renderCookies and BS
wrappers, parseCookiesText/renderCookiesText, parseSetCookie/renderSetCookie,
the default/def record, all ten fields, SameSite values and SetCookie Eq/Show.
Raw headers retain arbitrary bytes; textual cookies use the original lenient
UTF-8 behavior and Text surrogate replacement. Parsing preserves first duplicate
attributes, ASCII-space-only trimming, exact quote removal, raw attribute values
and case-sensitive SameSite values. Rendering preserves the original field
order, unquoted values and semicolon separators.

Max-Age uses unbounded integer picoseconds, rejects non-digits, and retains the
original empty-value `Prelude.read: no parse` error. That field is lazy: reading
the name, copying the record, changing a differing name before Eq reaches
Max-Age, and replacing Max-Age do not spuriously force the error. Eq against
itself still forces the original record fields. Rational formatting floors
negative fractions; Eq and Show retain the full picosecond value. This is a
specific translated demand boundary, not a claim of complete Haskell runtime
or generic Builder/lazy behavior.

`http/CookieDates.kt` specializes the pinned time 1.14 parser/formatter paths for
`%a, %d-%b-%Y %X GMT`. A read-only source exploration (jbcontext again had no
index) established locale substitution, whitespace, name/literal case, digit
widths, year parsing, calendar validation and UTC/display conversions. Gregorian
dates and signed years remain unbounded; invalid month/day combinations and
clock ranges are rejected. Weekdays are parsed but need not agree with the date.
Two-digit years 0..69 gain 2000, 70..99 gain 1900. Leap seconds, negative raw
DiffTime, picosecond Show and default zero-zone day adjustment follow the source.
Existing PandocGregorian and PandocUtcTime are reused unchanged.

Independent capture found nine initial mismatches on huge raw positive
DiffTime. The actual FormatTime `%S` implementation explicitly floors to a
machine Int, while Show Pico remains unbounded. Kotlin now preserves that
Int64 wrap and negative padding; 21 further original boundary observations
cover Int64 minimum/maximum, negative one-digit results, zero and fractions.
No expected result was changed to accommodate Kotlin.

The complete cookie release is retained unchanged (six original files plus
provenance), with archive SHA256
`1adde62726e3a3bbe147c02514fe0c4f5f2c5a2ff63842deac10c451a2a6f7a0`.
Every archived file is compared byte-for-byte; the actual linked Cabal package
source SHA agrees. Its full original MIT notice is copied to licenses and the
core JAR. Existing Time/Text notices and sources remain unchanged.

The native observer links actual cookie 0.5.1, time 1.14 and text 2.1.2 with the
frozen GHC 9.12.2 dependencies. The entire original Spec.hs is retained with
only its module name changed. Its actual Tasty/QuickCheck/HUnit main executes
all **nine original test families**, including **1,000 cases per property**
(replay seed 938), before capture. The observer also calls the original
Arbitrary instances and all three original property functions to collect
**3,000 reproducible inputs**. Kotlin translates the six literal assertions
and the same three properties for those inputs. This finite run is not a
claim to exhaust property domains or to port the whole QuickCheck runtime.

The final corpus contains **8,786 independent observations**, including those
3,000 property inputs, with semantic SHA256
`2cc9d327995384e12bccf9830682041731790ef05c29322ce2ce08ed8e9dfbe0` and
**508 source hashes**. Arbitrary bytes, invalid UTF-8, quotes/whitespace,
duplicates, all renderer flag combinations, lazy record updates, Eq/Show,
calendar boundaries and unbounded dates/intervals are covered. With the six
literal original units, all **8,792 new tests pass locally**. Platform,
source-integrity and installed-distribution validation follows.

Remaining HTTP work includes cookie storage/generation and original CookieSpec,
Data.IP loopback parsing, redirects, real pool/socket/TLS/proxy/manager effects,
full exceptions and remaining Request/builder/lazy contracts. Generic Data.Time
format/parser APIs and dependency/runtime suites are not claimed complete.
All remaining Pandoc readers/writers/App/Lua and original tests, then calibre
MOBI and additional formats/all their tests, remain active.

Final validation passes all **8,792 tests** locally (1.146s suite time) and
Linux/x86_64 JDK 21 (0.900s), with no failures, errors, skips or aborts. The
local XML is fully parsed and its actual testcase count equals its declarations.
All **134 source/fixture verifiers** pass (50.9s). The installed/core JAR bytes
agree; all 95 compiled HTTP classes, 63 full notices and 250 Pandoc assets
match source/build bytes. The existing original 60,137-byte suffix snapshot is
unchanged. Relative to the published PSL distribution, all **2,547 existing
classes and every previous core JAR entry**, including Kotlin module metadata,
remain byte-identical. CLI and dependency JARs are byte-identical. All older
implementations, tests, corpora and observers are unchanged. This additive
codec/date checkpoint does not assert a new full project suite run or complete
Pandoc/HTTP/calibre parity.

### HTTP CookieJar storage, matching and request/response fields

The full objective remains all Pandoc behavior and original tests, followed by
calibre MOBI/all tests and calibre formats absent from Pandoc/all their tests.
The preceding PSL checkpoint 0d3cec0 now has successful exact-SHA CI:
https://github.com/Heapy/kotdoc/actions/runs/38805155580. The earlier response
checkpoint 9be46ec also completed successfully. The Web.Cookie checkpoint's
exact-SHA CI also completed successfully before publication:
https://github.com/Heapy/kotdoc/actions/runs/38807642822.

`http/HttpCookies.kt` translates every exported Cookies.hs algorithm: domain
and path matching, defaultPath, cookie-specific isIpAddress, trustworthy-origin
checks, create/destroy/remove, generation, checked insertion, receipt, eviction,
header computation/request insertion and response Set-Cookie processing.
Cookie/CookieJar models retain original Show and named equal/equiv/compare
functions, plus CookieJar Semigroup/Monoid operations. They deliberately do not
invent a structural Eq instance. Immutable jar snapshots do not expose mutable
list storage. Derived Read for Cookie/CookieJar remains pending.

The original quirks are retained: jar equality zips without a length check;
compareCookies never returns EQ; recent-first jar merging returns GT for ties;
only the first equivalent cookie or Cookie header is removed; creation time
survives replacement; matching-cookie updates follow original matching order,
including duplicates. GHC sorting reuses the unchanged faithful PrettySort
implementation and pinned GHC source, rather than Java TimSort. Expiry exactly
equal to now is retained; header computation does not evict expired cookies.
defaultPath and pathMatches preserve arbitrary bytes, prepended slash behavior,
the original unnormalized remainder and its empty ByteString head/last errors.

Generation preserves single-leading-dot removal, trailing-dot rejection, the
original pinned PSL and raw-host comparison for public suffixes, host-only
flags, untouched explicit paths and HTTP-only policy. Max-Age takes precedence
over Expires; nominal time addition uses the original POSIX clamp/normalization,
including raw leap seconds and negative/fractional DiffTime. Session-cookie
expiry is the original day plus 365000 at midnight, not a calendar-year rewrite.

Storage integration exposed a missing distinction in the preceding SetCookie
model: empty Max-Age is Just an undefined number, so testing the Maybe constructor
must not force that number. HttpSetCookie now retains separate lazy presence
and value; public hasMaxAge observes presence without forcing the number, and
differing-Maybe Eq returns False without forcing it. Generated
Cookie expiry is also lazy. Persistence, equivalence, name access, record updates
and header generation can succeed while expiry, full equality, Show and eviction
still produce the original read error. All prior Web.Cookie expectations remain
unchanged and pass; new original observations demonstrate these demand paths.
This is not a claim of complete generic Haskell lazy/runtime behavior.

`http/CookieIp.kt` translates the used iproute 1.7.15 IPv4/IPv6 Read paths and
appar 0.1.8 String parser combinators. Initial comparisons found 16 mismatches:
the original accepts inputs such as `::1:`. Reading the underlying parser showed
that alternatives start from the consumed failure position unless try resets
it; failed branches inside many/option/sepBy1 also retain consumed input. The
final implementation translates those combinators and branches directly instead
of adding a trailing-colon exception. Exhaustive `0`, `1`, `:` inputs through
length seven and further overlong-hex/separator cases extend the independent
corpus. Pure and IPv4-embedded IPv6 retain different trailing-whitespace rules.
Cookie isIpAddress is a separate signed-decimal, bounded-Int algorithm that can
accept a trailing dot; trustworthy IPv4 Read rejects leading-zero octets.

HttpRequest now has the original optional cookieJar, defaulting to Just empty.
HttpResponse has its cookieJar; getResponse starts it empty, getOriginalRequest
preserves request cookie policy while erasing the body, and lbsResponse retains
the response jar. insertCookiesIntoRequest changes headers while preserving the
request's cookieJar field. updateCookieJar returns a separate updated jar and
strips all Set-Cookie headers without overwriting the response jar. These are
the original helper boundaries; automatic manager/transport cookie effects
remain to be integrated, not silently added to these helpers.

All **15 original CookieSpec tests** execute unchanged before capture (only the
module name differs) and are separately translated with all original literals,
field mutations and predicates. The observer links actual frozen original
http-client/cookie/iproute/appar/time code; no algorithm is rewritten there.
The new corpus contains **12,995 independent observations**, with semantic SHA256
`c8f5f8d27008f35f579740621ce83c4b0eb30cef90baf805885277763af21030`
and **550 source hashes**. With 15 original units and one snapshot-ownership
check, the new suite contributes **13,011 tests**. All **79,344 HTTP tests in
five suites** pass locally (final 20s Gradle run), including every preceding request,
response, PSL and Web.Cookie test. Every XML is parsed fully and actual test
counts agree with declared counts; there are no failures, errors or skips.

Complete unchanged iproute (16 release files) and appar (nine release files)
archives are retained with provenance, together with their complete BSD notices.
Archive SHA256 values are respectively
`18a331a7e0e6f9dc89a2da95577b0d76bd2690b8f832b72b46d6cc9b667b4ba5`
and `c4ceeddc26525b58d82c41b6d3e32141371a200a6794aae185b6266ccc81631f`.
Every archived file, notice and actual linked Cabal source SHA is verified.
Generic Data.IP routing/ranges/builders and Appar input/API/test coverage are
not claimed complete. No Haskell executable is a production dependency.

All **135 source/fixture verifiers** pass (61.7s). The installed/core JARs agree;
all 104 compiled HTTP classes, 65 full notices, 250 Pandoc assets and the
original 60,137-byte PSL resource match source/build bytes. Relative to the
published Web.Cookie distribution, every previous non-HTTP core JAR entry and
all 2,461 non-HTTP classes remain byte-identical; all previous classes remain
present. Expected HTTP model/ABI and debug-table changes account for changed
HTTP classes. CLI and dependency JARs remain byte-identical. Linux validation
and final staged source audit follow.

Remaining HTTP work includes redirects, real pool/socket/TLS/proxy/manager
effects and manager cookie handling, full exception/status behavior, derived
Read and outstanding Request/builder/lazy contracts. HTML CLI URL transport
integration, all remaining Pandoc readers/writers/App/Lua and original tests,
then calibre MOBI and additional formats/all their tests, remain active.

Final Linux/x86_64 JDK 21 validation passes the same **79,344 HTTP tests**
(final 5.659s), with no failures, skips, aborts or failed containers. Local suite
counts are 13,011 CookieJar, 8,792 Web.Cookie, 51,672 PSL, 3,176 request and
2,693 response tests. This checkpoint does not assert a new full project suite
run or complete Pandoc/calibre parity. Final staged hashes and release pins
are audited before publication.

### http-client redirected-request policy

`HttpResponses.getRedirectedRequest` now translates the complete pure request
policy from the pinned http-client 0.7.19 Response.hs. Both Request fields retain
the original defaults: shouldStripHeaderOnRedirect is const False and
shouldStripHeaderOnRedirectIfOnDifferentHostOnly is False. A typed HeaderName
retains original bytes and Latin-1 folded equality/order, so predicates can
observe either case-insensitive identity or original spelling.

Only 300 through 399 are considered; the first case-insensitive Location is
used, escaped from Char8 bytes and resolved against the current request using
the existing Network.URI port. Invalid references, schemes and ports return
Nothing. Credentials pass through the original setUriEither/basic-auth rules.
302 and 303 always select GET, replace every body constructor with strict empty
bytes and remove all Content-Type headers. Content-Length is deliberately not
removed by this helper. Other 3xx responses preserve method/body, including
HEAD and effectful body constructors. Cookie enablement comes from the current
request; when enabled, the supplied response jar replaces it. Redirect counts
are preserved here: loop limits belong to the not-yet-ported Core loop.

Host comparison uses raw bytes against the original request in the chain,
not a case-folded comparison against only the preceding hop. Stripping follows
the current request predicate. Returning to the exact original host with the
host-only option restores selected original headers after current headers,
then applies first-value CI nubBy to the entire result, including unrelated
duplicate names. Scheme/port changes on the same host do not count as host
changes. Multi-hop observations preserve this restoration and auth precedence.

The development observer links the actual frozen original getRedirectedRequest;
no redirect algorithm is copied into it. The complete prior CookieJar source
chain plus observer/importer inputs yields **557 source hashes**. Native Cabal
plan source hashes are checked against actual cached release archives for
http-client/network-uri/cookie/case-insensitive/iproute/appar; bytestring 0.12.2.0
and time 1.14 are the original GHC boot packages. The independently captured
corpus contains **6,172 observations** with semantic SHA256
`191fd55ecb0e01d60de2a27d49d87dfdcd236dafce11705dc10e6308b9f6a20d`.
It includes every status from -1 through 700, Int boundaries, every byte in
Location and HeaderName, relative/raw bases, duplicate Location, body forms,
invalid targets, header policies/original spelling, cookie enablement, auth,
unrelated-field preservation and multi-hop chains. Failing predicates test
validation demand order; builder/provider/IO bodies fail if executed. Only
ErrorCall is caught by the observer; unexpected native exceptions abort capture.

The new Kotlin suite runs all 6,172 expectations unchanged plus an ownership/
effects check for preserved hooks/body objects and supplied cookie jars.
All **85,517 HTTP tests in six suites** pass in the local 35s Gradle test/build
run, with complete XML parsing confirming actual counts, no failures/errors/
skips. The same installed JARs/classes/resources pass all **85,517 tests on
Linux/x86_64 JDK 21** (5.986s), with no skips, aborts or failed containers.

The installed/core JAR bytes agree. All 106 compiled HTTP classes, 65 complete
notices, 250 Pandoc assets and the unchanged 60,137-byte PSL snapshot match
build/source bytes. Relative to the published CookieJar distribution, every
previous non-HTTP core entry and all 2,461 non-HTTP classes remain byte-identical;
no old class is removed. Changes to old classes are confined to the HTTP
package (including expected model ABI and shifted source debug tables).
CLI and dependency JAR bytes remain identical. This does not assert a new full
project test run or complete Pandoc/calibre compatibility.

The retained upstream test-nonet ClientSpec's local-server header stripping,
redirect history/limits and connection-close tests still require actual manager/
transport effects; none is relabeled as a helper-only test or claimed passed
here. Disabled live httpbin redirect tests likewise remain pending. Remaining
work includes Core redirect loops/history/limits, real pool/socket/TLS/proxy/
manager and automatic cookie effects, remaining Request/lazy/exception contracts,
HTML CLI URL integration and the full remaining Pandoc/App/Lua/original suites,
then calibre MOBI and formats absent from Pandoc with all their tests.

All **136 source/fixture verifiers** pass (63.9s), including the new redirect
source-chain check. CookieJar commit `0a915aa64a17f03daf71ef824e14fd27a34eeafb`
is published and its full CI run `38812129359` has passed all 135 source checks
and is executing the full Gradle suite at the time of this checkpoint. Its
pending conclusion is not reported as success. Final staged source/pin auditing
precedes publication of this request-policy checkpoint.

### http-client redirect loops, history payloads and closed reads

`http/HttpCore.kt` translates the actual Core.hs httpRedirect and httpRedirect'
loops as `httpRedirect` and `httpRedirectWithRequest`. Both callback contracts
are preserved, including the extended callback's potentially modified final
request. A negative starting count throws before invoking the callback; zero
still permits one callback. If that callback reports a redirect, its body is
flushed/closed and its response appears in TooManyRedirects before another
callback can run. The loop itself does not inspect request.redirectCount or
status: those belong to the higher-level request/manager policy.

Flushing uses the unchanged brReadSome port with limit 1024, retaining whole
chunks and permitted overshoot. EOF stops consumption. The response is then
closed, its byte-body snapshot is prepended to history, and the remaining count
is decremented. History is reverse chronological and includes the last response
that exhausted the limit. Iteration avoids JVM stack growth on long chains.
A non-redirect response is returned unchanged and remains open/unread. Callback,
read and close failures retain the original ordering; no unrequested finally
cleanup is inserted where the original does not perform it.

`handleClosedRead` suppresses only ConnectionClosed in the original content
wrapper, ConnectionClosed in HttpExceptionRequest, or the exact IO
ResourceVanished condition. A failure after partial body consumption discards
all bytes from that brReadSome action, as the original catch does. Errors after
1024 bytes need not be read at all. Ordinary IO errors, other HTTP failures,
callback/close errors and asynchronous cancellation are rethrown. A typed
ResourceVanished marker represents that specific condition; mapping actual
platform socket errors remains transport work, not a catch-all IOException
substitute. Generic GHC IO exception formatting is not claimed ported.

TooManyRedirects now retains an immutable snapshot of byte-body Responses,
including original headers, cookie jar, early hints, original request and close
action. HttpExceptionRequest and concrete Response derived Show/precedence are
available; body Show is supplied explicitly for the polymorphic response type.
The old content-wrapper message is now evaluated when requested, preserving its
nullable public message contract. Merely throwing or inspecting an exception
must not force response/cookie Show and undefined cookie expiry. Original Show
still forces that expiry if explicitly demanded. All preceding error-message
expectations remain unchanged. Generic Haskell lazy/runtime behavior and the
remaining HttpExceptionContent constructors are not declared complete.

The observer links the actual frozen original Core functions; callbacks only
supply scripted original requests, responses and read/close effects. An event
log observes every callback argument, read and close. The request JSON prefix
is copied byte-for-byte from the preceding observer except the module name and
is checked as such. The **1,827 independently captured observations** have
semantic SHA256
`5a7bd88e56da43ddaf5bac4de1898098c7b527b0e0e4d6a44e8cf64f3676a8d2`
and **565 source hashes**. They cover both APIs, negative/zero/Int-boundary
limits, final and modified requests, whole-chunk flush thresholds, EOF,
overshoot, reverse history, all header/status/body byte values, preclosed
responses, failure locations, explicit final read/close effects, undefined
cookie expiry and two original 10,000-hop chains. ErrorCall during result
projection is recorded separately from errors raised by the original loop.
Actual native http-client/network-uri/cookie/case-insensitive/iproute/appar
archive hashes match the Cabal plan; bytestring 0.12.2.0 and time 1.14 remain
original GHC boot packages.

The new Kotlin suite translates all 1,827 expectations unchanged and adds three
checks for final-response identity/ownership, unchanged exception identity and
immutable history/field preservation without forcing cookie expiry. All
**87,347 HTTP tests in seven suites** pass locally (final 29s Gradle test/build),
with fully parsed XML counts and no failures/errors/skips. Linux/x86_64 JDK 21
passes the same **87,347 tests** (6.473s), with no skips, aborts or failed
containers. All **137 source/fixture verifiers** pass (60.6s).

The installed/core JAR bytes agree; all 112 compiled HTTP classes, 65 complete
notices, 250 Pandoc assets and the original 60,137-byte PSL snapshot match
build/source bytes. Relative to the published request-policy distribution,
every previous non-HTTP core entry and all 2,461 non-HTTP classes remain
byte-identical; no old class is removed. Changed old entries are confined to
HTTP model/behavior classes and their source debug tables. CLI/dependency JARs
remain byte-identical. This checkpoint does not assert a new full project run
or full Pandoc/calibre compatibility. Exact release pins and staged fixture
source blobs are audited before publication.

Actual responseOpen/responseOpenHistory integration remains pending. In
particular, normal history bookkeeping performs its own first body read before
the Core loop reads again; it must not be replaced with the exception history
implemented here. Full manager modification/status/exception wrapping, real
pool/socket/TLS/proxy and cookie effects, bracketed resource APIs and the
original local-server ClientSpec redirect/history tests still remain. The
original disabled live httpbin tests are not relabeled as executed. All other
outstanding Request/builder/lazy contracts, HTML CLI URL integration and the
full remaining Pandoc readers/writers/App/Lua/tests, then calibre MOBI and
formats absent from Pandoc/all their tests, retain their full scope.

### http-client keyed resource pool and managed lifetime

`http/KeyedPool.kt` ports Data.KeyedPool from the pinned complete http-client
0.7.19 source as a real reusable resource pool. KeyedManaged exposes the original
resource/reused properties, release and keepAlive; dummyManaged preserves its
no-op release behavior. The existing HttpReuse constructors are shared. The
pool will be wired into the actual HTTP manager next; existing HTTP helpers
are not silently changed to use an invented manager or alternate transport.

Only idle resources count toward per-key/global limits. Active acquisitions do
not wait for a slot: a missing key creates another resource outside the state
transaction. Returns keep newest entries first; a full pool discards the newly
returned resource without evicting existing entries. An absent key still accepts
its first entry when maxPerKey is nonpositive, provided the global limit allows
it. A closed pool can still create fresh resources; subsequent reuse returns
are destroyed. Nullable resources retain their presence independently of the
absence of an idle entry.

Release claims its exactly-once flag before any return/destroy effect. DontReuse
suppresses destructor exceptions. Reuse overflow/closed-pool destruction is
outside the state transaction and can throw; its flag remains claimed, so a
second release does not repeat it. A clock failure also occurs after that flag
is claimed. These are original behaviors, including their cleanup consequences,
not corrections to fit the comments. Atomic claims and locked state updates
preserve the tested concurrent schedules; create/destroy callbacks run outside
the state lock, permitting another take or duplicate release to proceed.

The real default runtime uses JVM Cleaner weak-owner cleanup, real UTC time
and a lightweight JDK 21 virtual reaper thread. The thread does not inherit
user InheritableThreadLocals, which could otherwise keep an owner alive through
a captured request context. It checks every five seconds;
empty maps wait until state changes. Reaper timestamps are read after this
wait, with a 30-second inclusive deadline. The original prefix/span rule is
retained even when wall-clock timestamps move backwards. Stale resources are
removed atomically, then destroyed in ascending key/newest-first list order,
with destructor errors suppressed. Reaper errors call the supplied handler
and restart; a handler exception ends the reaper. Pool cleanup marks state
closed before destroying all idle resources, suppressing destructor errors.
Checked-out resource release closures retain pool lifetime; reaper/finalizer
closures must not retain the pool's alive owner or its create callback.

Clock, finalizer registration and reaper startup/delay are explicit effect
boundaries. Production defaults perform actual effects; the deterministic
observer/runtime can drive finalizer actions and reaper iterations without
waiting 30 seconds. This is not a replacement for real JVM cleanup, which is
also tested. It does not claim complete generic Haskell STM/GC/async masking,
all possible Ord dictionaries, or other platform runtime APIs.

Data.KeyedPool is private in the upstream package. The native observer therefore
compiles the **complete original private module**, with just its module name
changed, using original clocks, STM, weak IORefs and threads. A second complete
copy changes only its module name and three effect-import lines for controlled
clock/finalizer/reaper effects. Every remaining byte, including the full
algorithm body and comments, is checked against the pinned source. Its MIT
notice remains in the original archive, licenses and distributable JAR. No
algorithm or expected result is rewritten in the observer. The plan uses the
verified http-client 0.7.19 archive and original GHC boot base 4.21.0.0,
bytestring 0.12.2.0, time 1.14, containers 0.7 and stm 2.5.3.1.

The **1,478 independent observations** have semantic SHA256
`82bf1e27bb5cac222107f8787c49404cef2f4207fb22e7dc81faeca7a0813426`
and **575 source hashes**. Live and controlled cases cover nonpositive/large
limits, arbitrary active acquisition, LIFO/key order, duplicate release,
nullable resources, create/destructor/clock errors, closed pools, inclusive
reaping/rollback, empty-map wakeup and handler restart/exit. Gated workers prove
creation and overflow destruction do not hold the pool transaction, a second
release sees the claimed flag before destruction completes, and interruption
of blocked create/overflow destruction retains original state/cleanup behavior.

All three actual native GC scenarios must complete before capture can succeed.
Initial capture exposed retention in the observation harness: a lazy JSON
result could still hold its Managed wrapper during later GC. Results are now
serialized/detached per command, with stable roots keeping only the explicitly
owned handle map/pool reference alive until forget operations. No upstream
algorithm or expected GC outcome was changed. The final original GC observations
and matching JVM Cleaner observations all complete successfully.

The Kotlin suite executes all 1,478 expectations unchanged plus two checks:
actual Cleaner collection of a pool cycle reachable through its create callback
and a would-be inherited thread context,
and propagation of the same overflow exception object only once. No original
KeyedPool test module exists in the retained release; these observations are
not labeled as invented original units. All **88,827 HTTP tests in eight suites**
pass locally (final 15s Gradle run) and on **Linux/x86_64 JDK 21** (final 7.213s).
Every XML is parsed fully; actual/declared counts match, with no failures,
errors or skips. Linux likewise reports no failed/aborted/skipped containers.
All **138 source/fixture verifiers** pass (44.3s).

The installed/core JARs agree; all 120 compiled HTTP classes, 65 complete notices,
250 Pandoc assets and the unchanged 60,137-byte PSL snapshot match source/build
bytes. Every old JAR entry, including all **2,573 old classes**, remains
byte-identical to the published redirect-loop distribution; all 2,461 non-HTTP
classes, CLI and dependency JARs are unchanged. Only new pool classes are added.
This checkpoint does not assert a new full project run or Pandoc/calibre parity.
CookieJar commit 0a915aa's full CI run `38812129359` is now verified successful;
request-policy run `38812985721` is also verified successful. The redirect-loop
full CI run is still active at this point.

Next work remains actual Manager construction/proxy/connection acquisition,
request modification/override and exception/status wrapping, pool adapter and
socket/TLS transport, automatic cookie handling and responseOpenHistory's
separate first-read/history behavior, including all original local-server
integration tests. All remaining Request/lazy contracts, HTML CLI URL integration,
Pandoc readers/writers/App/Lua/original suites, then calibre MOBI and additional
formats absent from Pandoc/all their tests remain in scope. Final staged source
blobs and exact release pins are audited before publication.

### http-client common environment proxy selection

`http/HttpProxyEnvironment.kt` ports `httpProtocol`, `envHelper`, proxy settings
Show and the **non-Windows** `systemProxyHelper` selection branch from the pinned
http-client 0.7.19 `Network/HTTP/Proxy.hs`. The corresponding Kotlin helper is
explicitly named `environmentProxyHelper`; no manager is silently wired to an
incomplete cross-platform helper. The production environment effect snapshots
`System.getenv()` once. Its injected ordered-list boundary retains duplicate
names for exact original observations. Raw OS environment enumeration (including
physical duplicate entries that the JVM map cannot represent) remains a runtime
integration gap, as does the entire Windows registry branch.

Exact names use the first original entry; only a missing exact name consults a
map with the last duplicate **folded** key. The lookup name itself is not folded.
An empty exact entry takes precedence over nonempty uppercase alternatives.
Data.Text.pack surrogate replacement and the original text **2.1.2** complete
lowercase table are retained, including multi-character expansions. This reuses
`Text212Lowercase` by changing only its visibility from private to internal;
text 2.1.3 and the host JDK tables are not substituted. All 4,725 ParserState
observations still pass, including its existing exhaustive Char key witnesses.

Proxy parsing uses the already ported original Network.URI implementation.
It accepts only the original http scheme, empty/root path, no query/fragment,
original userinfo decoding and decimal machine-Int overflow. Invalid selection
throws the original `InvalidProxyEnvironmentVariable` content wrapper during
helper construction, even if no_proxy would bypass that request. Settings/auth
and derived Show preserve original precedence and quoting.

no_proxy lowercases String Chars with the pinned simple Data.Char mapping before
Char8 truncation; host bytes are lowercased as Char8. Empty comma entries are
filtered before leading ASCII spaces are removed. Trailing spaces/tabs and
wildcards are literal. Dot prefixes preserve exact/subdomain boundaries. Each
suffix stays deferred: an earlier match can avoid a later all-space entry's
original partial ByteString.head error. Empty hosts are likewise demanded only
when suffix matching needs them. Bypassing an environment proxy permits the
supplied request/none/explicit fallback. Existing Proxy-Authorization headers
remain, and new Basic credentials prepend another header in original order.
Request bodies and callbacks stay deferred and retain identity.

The development observer compiles the **entire original module** on its actual
non-Windows CPP branch. Its only changes are the module name, two private imports
to the exposed Internal re-export and the environment effect import. Every other
byte is checked. The native GHC 9.12.2 plan uses original http-client 0.7.19,
network-uri 2.6.4.2, text 2.1.2, bytestring 0.12.2.0 and containers 0.7. No proxy
algorithm is duplicated in the observer. Forced JSON projections observe each
host's value/error separately, including original settings/request/exception
Show. Input code-point lists retain raw surrogate Chars before Text conversion.
There is no standalone upstream ProxySpec in this release; these observations
are not described as upstream unit tests. Existing RequestSpec proxy-auth units
remain translated in the request suite.

The **4,460 observations** have semantic SHA256
`82f94e1728b79992d69569960bf8dd9a25a9c0f22bae41373ae665d61693350a`
and **576 source hashes**. The Kotlin suite adds two identity/effect checks.
All **93,289 HTTP tests in nine suites** and **4,725 ParserState tests** pass in
one local Gradle run (98,014 tests, 41s). Every XML is fully parsed: actual and
declared counts agree, with zero failures, errors or skips. All 93,289 HTTP tests
also pass on Linux/x86_64 JDK 21 (7.501s), without failed/aborted/skipped tests or
containers. All **139 source/fixture verifiers** pass (81.3s). This is scoped
verification, not a new full-project parity claim.

The installed/core JARs agree. All **130 compiled HTTP classes**, **66 full
notices**, **250 Pandoc assets** and the original 60,137-byte PSL snapshot match
build/source bytes. `Proxy.hs` carries its own many-author BSD-3-Clause notice,
which is preserved verbatim as `licenses/http-proxy-BSD-3-Clause.txt` and packaged
in the JAR. Compared with the preceding pool distribution, the only old
non-HTTP class changed is Text212Lowercase's reviewed visibility; the other
2,460 old non-HTTP classes and all CLI/dependency JAR bytes remain identical.
HTTP changes are confined to the new proxy classes and the existing response
compilation unit containing the extended sealed exception type.

The preceding pool commit a300ada's exact full CI run `38817631586` is still
observed running. The redirect-loop commit ab18917's full run `38814432738`
is verified successful. No CI result is inferred from elapsed time.

Next work includes the Windows registry/selection branch and physical environment
backend, actual Manager construction and proxy/connection acquisition, pooled
socket/TLS transport, modification/override/status/exception contracts, automatic
cookie handling and responseOpenHistory, including the original local-server
integration tests. HTML CLI integration, remaining Pandoc readers/writers/App/Lua
and all original suites, then calibre MOBI and additional formats absent from
Pandoc/all their tests remain in the original scope.

### Windows HTTP proxy selection and selected native registry calls

`http/HttpWindowsProxy.kt` now ports the Windows CPP branch of the pinned
`Network.HTTP.Proxy`: registryProxyString, windowsProxyString selection,
parseWindowsProxy, parseProxy/fixUserInfo/uri2proxy and systemProxy. The new
`HttpProxyEnvironment.systemProxyHelper` combines the real platform branches.
The prior `environmentProxyHelper` retains its explicit non-Windows behavior.
All environment lookup rules from the preceding checkpoint remain unchanged.

Registry state is read eagerly after the environment helper is constructed,
even if the environment proxy will win. A bad environment variable fails before
opening the registry; bad selected registry settings can fail construction even
with a valid environment proxy. no_proxy bypass permits subsequent registry
selection; registry bypass permits the supplied fallback. Neither fallback nor
unauthenticated proxy selection removes existing Proxy-Authorization headers.
The real manager has not yet been wired to these helpers.

The original registry algorithm brackets HKCU Internet Settings open/close,
queries a four-byte ProxyEnable, accepts only REG_DWORD, and treats any nonzero
unsigned value as enabled. Only IOException is hidden; ErrorCall/async failures
propagate, subject to the original close-exception precedence. ProxyOverride IO
failure becomes an empty exception string; server/open/DWORD/close IO failure
hides the entire registry result. Each callback's order is independently logged.
Selected server entries use case-sensitive protocol prefixes and first-match
semicolon/equal splitting. Plain host:port entries do not match the selection
stage. URI parsing preserves its original scheme/authority fallback, accepted
paths/queries, default/overflow port behavior and trailing-@/userinfo rules.
Invalid selected settings throw the original content and exact Show text.
Bypass matches raw Char8 host substrings, plus the exact localhost/127.0.0.1
and literal <local> rule. Wildcards are not expanded; an empty host matches any
exception string. No environment or registry reread occurs on application.

`HttpNativeWindowsRegistry.kt` implements the selected Windows API calls through
the existing JNA dependency, loading Advapi32 only on use. Its explicit binding
reference is retained **Win32 2.14.2.1**, with its full original archive, source
and BSD notice; this is not a claim to have reconstructed a Windows Pandoc build
plan. The source calls **RegQueryValueW** for ProxyServer/ProxyOverride, reading
a subkey's default value, rather than RegQueryValueExW named-string lookup. The
adapter preserves that distinction, as well as RegOpenKeyW, a four-byte unsigned
DWORD query via RegQueryValueExW, the original two-call string size/read pattern
and RegCloseKey. Windows WCHAR decoding retains UTF-16 supplementary characters.
Generic Win32 FFI/ForeignHANDLE runtime and localized OS exception Show are not
claimed as ported. Defined registry string/DWORD calls are the scope here.

The observer compiles the **complete original Proxy.hs with its Windows CPP
branch enabled** on the observer host. Only module/export names, CPP selection,
two imports to the exposed Internal re-export, and environment/Win32 effect
imports change. Algorithm bytes, bracket/catch, alloca/peek, Network.URI and
original Show remain unchanged. This controlled registry boundary does not
pretend to run Windows DLLs on macOS. The original **safe 0.3.21 readDef** is
compiled from its retained complete source/archive; its full Neil Mitchell
notice is packaged too. Both archives' hashes and every extracted byte are
checked. No standalone upstream ProxySpec exists; these observations are not
mislabeled as original units. No Haskell executable is a production dependency.

There are **2,179 independent Windows-branch observations**, semantic SHA256
`dceeff9451af5f80ff4427cec7e0ffaab36334e463b0e6f2e6d5f542f3ed0443`,
with **727 source hashes**. They include all preceding environment-helper
inputs, direct parsers, every host byte at the bypass boundary, unsigned DWORD
values/types, IO/non-IO/async failures and overriding close errors. Kotlin adds
a non-Windows branch/identity/deferred-body check and two native-call boundary
checks for HKCU handle representation, exact API sequence/sizes, unsigned
DWORD, UTF-16 decoding and native error-code conversion.

The full local HTTP run passes **95,471 tests** (20s final Gradle run), with
exactly **one intentional platform skip**: the real Windows-registry test on
macOS. Every suite XML is parsed fully; counts agree and no failure/error exists.
The same **95,471 tests** pass on Linux/x86_64 JDK 21 (7.603s), with that same
single Windows-only skip and no failed/aborted tests or containers.
The dedicated `windows-proxy` CI job runs the common/Windows proxy and binding
suites, including the real registry test. That test creates a unique temporary
HKCU key, checks that a named ProxyServer value is not a subkey default, then
checks real DWORD/Unicode subkey reads and combined selection. It deletes only
its own temporary key and never edits actual Internet Settings. CI parses all
four XML suites and requires exactly **6,645 tests with no skips**. All 6,645
pass on actual Windows/JDK 21 for code commit **c556108**: run `38820697555`,
job `116716306297` (`windows-proxy`) is verified successful. Its downloaded
job log confirms the 4m 9s successful Gradle build and the XML coverage guard's
explicit real-Advapi32/HKCU success message. This verifies the live registry test,
not merely the Windows-branch observer on a POSIX host.

All **140 source/fixture verifiers** pass (67.3s). Installed/core JARs agree;
all **138 HTTP classes**, **68 complete notices**, **250 Pandoc assets** and the
original 60,137-byte PSL snapshot match source/build bytes. All **2,461 old
non-HTTP classes**, every other old non-HTTP JAR entry and all CLI/dependency JAR
bytes remain unchanged from the environment-proxy distribution. Only HTTP
classes in the extended exception/proxy compilation units change or are added.
This checkpoint does not claim a new full-project or complete Pandoc/calibre run.

The preceding pool commit a300ada's exact full CI run `38817631586` is now
verified successful. Environment-proxy run `38819218765` (dd6043d) and c556108's
separate full Linux job remain observed running; the successful focused Windows
job is not presented as full-project parity. This verification-only ledger update
does not change tested source or restart the already active full code CI.
Next work remains physical OS environment enumeration, actual Manager/pool/
proxy/socket/TLS integration, manager mutation/override/status/error contracts,
automatic cookies and responseOpenHistory, with all original local-server tests.
The complete Pandoc port/all original tests, then calibre MOBI and its tests
and additional formats absent from Pandoc/all their tests remain unchanged.

### Live GHC environment blocks and filesystem byte decoding

`resources/GhcEnvironment.kt` replaces the HTTP proxy backend's JVM environment
map with live OS reads. On Darwin it reads `_NSGetEnviron`; on Linux/glibc it
reads `environ`. Both keep original pointer order, duplicate names, nameless
entries, entries without `=`, empty values and first-`=` splitting. Null blocks
return before demanding the filesystem encoding. Pointers are enumerated before
C strings are decoded, matching the original function's order. Each new HTTP
helper captures a fresh snapshot once; later native changes affect a newly
constructed helper without changing an already constructed one.

Windows uses actual Kernel32 GetEnvironmentStringsW/FreeEnvironmentStringsW,
with cleanup even for a null block and an ignored Bool result. Acquisition
failure does not release; cleanup failure overrides the result as in bracket.
The original seekNull advances by two-byte CWchar while peeking **Word8**. A
Unicode character with a zero low byte can therefore split a record or end
iteration early. This behavior is preserved, not replaced by a conventional
UTF-16 environment parser. The original cWcharsToChars pair/surrogate behavior
and divvy splitting are retained, including leading-`=` drive entries.

`GhcEnvironmentEncoding.kt` ports the selected Unicode/ASCII/Latin1 decoder
algorithms, BOM/default-BE state, per-string decoder reset and RoundtripFailure.
A bad/truncated sequence consumes one byte, preserving ASCII or mapping bytes
128..255 to U+DC80..U+DCFF. Native system iconv remains the original system effect
for other codecs; it is not replaced by JVM Charset tables. The original 'a'
encode/C-string/decode acceptance probe starts with two output bytes, doubles
when output or NUL space is exhausted, and preserves iconv state on retries.
Input/output positions, stateful recovery, error/close ordering and four-byte
Char output are retained. The initial native filesystem codec is captured from
the process C codeset; arbitrary replacement of GHC TextEncoding objects and
the complete GHC IO/locale runtime are not claimed as implemented here.

Some native iconv aliases yield **raw 32-bit Char slots beyond Unicode**, such
as 0x110000, even though normal Haskell Char constructors reject them. Decoding
and `getEnvironmentCodePoints` retain those raw slots in IntArrays without
replacement or UTF-16 pair folding. Values can be interpreted as UInt for the
full 32-bit range. `getEnvironment` is the UTF-16/String facade for representable
values; callers needing the raw native slots use the code-point API. The HTTP
backend uses the ordinary String facade. Other POSIX ABI constants and full
GHC runtime state remain explicit integration gaps; native bindings at this
checkpoint target Darwin, Linux/glibc and Windows.

Pinned implementation sources are retained from GHC **9.12.2**, release commit
`383be28ffdddf65b57b7b111bfc89808b4229ebc`. Every selected file was compared
against the official source archive, whose SHA256 is
`0e49cd5dde43f348c5716e5de9a5d7a0f8d68d945dc41cf75dfdefe65084f933`;
the official test archive SHA256 is
`cf142eadb4ff9b84ba49c01f52813acbeb742d57b21e64c46424563e0bf410aa`.
Both match the release SHA256SUMS. SOURCE.json records the release and selected
file hashes, including the complete base notice already packaged in the JAR.
The whole original getEnvironment/divvy bodies are extracted without algorithm
edits for both branches. POSIX block/encoding effects are injected; the Windows
CWchar effect type is Word16 with its real Windows size/alignment. The original
wide-character conversion body is extracted unchanged. GHC's actual codec
implementation is used by the development-only observer, not the production
converter. No original program is a production dependency.

Separate native **macOS/aarch64** and **Linux/x86_64 GHC 9.12.2** observers check
the full compiler patch version. They supply **4,986 fixture groups each**,
with **751 source hashes** per corpus. These include all **851,968 byte-pair
string decodes** across 13 codecs, all **1,114,112 Haskell Char inputs** encoded
as raw UTF-8 (including surrogate inputs), BOM/endian/EOF cases, aliases,
stateful streams and original environment pointer/UTF-16 layouts. Large Char
ranges use independent original-output count/SHA256 checks. These are grouped
observations, not 4,986 invented upstream unit tests.

Semantic corpus SHA256:

- macOS: `e9be5c6b225eea2426ad8c031349b639af1fb1620621a0eac8c1a59231481a75`
- Linux: `703567e7fc72b86b0110d0d3bb9709b979cfdd2764291583ab1e87018d3c00a1`

The original complete **T12494** source, stdout and registration are retained.
Its full native executable must pass before capture on each platform. Kotlin's
live tests cover its getEnvironment empty-value assertion; the separate Blank
getEnv/getEnvDefault/setEnv APIs and the rest of that test are not mislabeled as
ported. Controlled POSIX expectations are additionally checked against actual
System.Environment.getEnvironment after temporarily replacing environ inside
the disposable Haskell observer. Kotlin's isolated child JVM independently
checks actual physical ordered/duplicate/malformed/null environ blocks. A
native setenv test proves that the HTTP backend sees new values after the JVM
cache was initialized, while an existing helper keeps its snapshot. Test
variables are unique and removed; user environment contents are never printed.

Initial Linux verification found a **test context mismatch**, not different
expected decoding: the GHC observer used C locale `C`, while the JVM used UTF-8.
The empty iconv name means the current C locale. The observer now records its
actual locale; Kotlin applies/restores that exact locale for the observation.
All preceding **4,980 expected values on each platform remain byte-for-byte
semantically unchanged**; no expectation was derived from Kotlin. Six additional
codec alias/probe cases were then captured from the original. The raw Char slot
API fixes the initially exposed JVM String range limitation without dropping
that original out-of-range observation.

The final local run passes **100,460 tests** (38s Gradle run): all 95,471 HTTP
checks and 4,989 environment checks. Every XML is parsed fully; declared and
actual counts agree. Exactly one Windows-registry-only test is intentionally
skipped on macOS. The same **100,460 tests** pass on Linux/x86_64 JDK 21 (11.108s),
with that same single platform skip and no failures, errors or aborted tests or
containers. All **142 source/fixture verifiers** pass (60.7s).

The Windows CI job now selects the existing proxy/registry tests plus all 3,826
applicable environment fixture groups, cleanup checks and actual Kernel32
snapshot/mutation/empty-value/Unicode scan tests. Its XML guard requires
**10,473 tests without skips**. The exact code commit **ce98df6** passes this
job on actual Windows/JDK 21: run `38825978868`, job `116733421138` is verified
successful. Its downloaded log confirms a 4m 8s successful Gradle run and the
XML guard's explicit 10,473-test success with actual Advapi32/HKCU and Kernel32
calls. This includes the new live mutation, empty-value and original Unicode
scan checks, not only controlled block observations. The separate full Linux
job remains running. This documentation-only verification update leaves tested
code and the already active full CI unchanged.

Installed/core JARs agree; all existing HTTP classes, 68 complete notices,
250 Pandoc assets and the 60,137-byte PSL snapshot match source/build bytes.
Every old JAR entry is unchanged except HttpProxyEnvironment.class's backend
call. All **2,461 old non-HTTP classes**, CLI and dependency JARs remain identical
to the preceding Windows-proxy distribution. New environment classes are added.
The environment-proxy full CI run `38819218765` and Windows-proxy full CI run
`38820697555` are now both verified successful. These results are scoped evidence,
not a claim of full Pandoc/calibre parity.

Next work remains actual Manager construction and request override/mutation,
proxy/pool/connection acquisition, socket/TLS transport, status/error wrapping,
automatic cookies and responseOpenHistory, with all original local-server tests.
All remaining Pandoc readers/writers/App/Lua and original suites, then calibre
MOBI and its tests and additional formats absent from Pandoc/all their tests
remain in the original objective.

### HTTP Manager construction, keying, CONNECT and actual pool integration

The next checkpoint ports the complete construction/control logic of
http-client **0.7.19 Network.HTTP.Client.Manager** with explicit transport
factories. It includes ManagerSettings defaults other than the not-yet-ported
native raw socket factory, Manager construction and prepared HTTP/HTTPS proxy
policies, raw/secure/CONNECT key selection, derived key Eq/Ord/Show,
getConn's empty ultimate-host guard, pool acquisition, dropProxyAuthSecure,
all direct proxy overrides, default retry/wrap rules and connection failure
wrapping. The existing live environment/registry helper is connected to the
Manager proxy override constructors. Request now retains the original optional
Word32 hostAddress; Request Show intentionally omits that field, as upstream does.

Factory initialization order is raw, TLS, CONNECT; proxy preparation occurs
before those factories and socket initialization occurs first. All factories
are initialized even if a request only uses one. Acquisition uses the actual
ported KeyedPool with the source idle limits, not an independent manager cache.
The default TLS factories retain TlsNotSupported. closeManager is the original
no-op, and withManager deliberately does not bracket cleanup, including when
its callback throws. This behavior is retained even though its historical API
comments could suggest otherwise.

CONNECT keeps the exact bytes, signed ports, optional first case-insensitive
Proxy-Authorization and Latin1 host unpacking. The connector owns the supplied
status-parser callback: it can invoke it zero or multiple times. That parser
uses the configured header limits, accepts status code 200 independently of
its reason phrase, processes interim responses through the original header
logic, and throws ProxyConnectException for other status codes. The constructor
does not add invented connection cleanup. Only IOException is wrapped as
ConnectionFailure; default request wrapping similarly wraps only IOException
as InternalException. Throwable identity and deferred Show dictionaries are
retained. Native GHC IO errors use the existing exact IO error representation;
foreign JVM exceptions keep their own display rather than being mislabeled as
arbitrary Haskell exception values. HTTP exception wrappers now also retain
original derived Show precedence when carried by SomeException-like values.

A development-only observer builds the complete original pinned http-client
package, including its real Manager and Data.KeyedPool. The copied source and
all tests are verified byte-for-byte against vendor; the only changes expose
private symbols in export lists and move Data.KeyedPool into exposed-modules.
No implementation algorithm is edited. GHC **9.12.2**, including the patch
version, is enforced at compile time; the full dependency freeze is retained.
Strong stable roots preserve observation lifetime through JSON materialization.
Real GC/reaper/cancellation behavior remains covered by the separate existing
KeyedPool corpus; these manager observations use explicit transport effects.

The original executable supplies **2,641 independent observation groups**:
960 request-key/auth cases, 810 derived key comparisons, 240 direct factory
cases, 160 CONNECT wire cases, 120 construction/error-order cases, 36 connect
errors, 50 status/chunk/EOF cases, 70 header-limit cases, 50 actual pool limit/
partition/LIFO cases, plus defaults, retry/wrap predicates, proxy overrides,
parser callback multiplicity, unsupported TLS, once-only release and failure
paths. Hosts/auth include every byte, addresses cross signed/unsigned boundaries,
and ports include both signed Int64 extremes. Expected values come exclusively
from the original executable. They are observations, not 2,641 claimed original
upstream unit tests. The fixture manifest binds **805 source hashes** and has
semantic corpus SHA256
`8025cf0a5d0244e5985d340aff96ef224c2dd3c40678b751dee72e8e56ed82d2`.

Five additional Kotlin integration checks verify callback identity/deferred
execution, first socket-initialization effects, original exception identity and
lazy error display, a fully consumed response returning its actual connection
to the pool, a partially consumed response closing it once, and withManager's
success/error lifetime behavior. The adapter retains the Managed owner and its
keepAlive hook through response cleanup. All **2,646 manager tests** pass.

The complete selected HTTP/environment run passes **103,106 tests** on
macOS/aarch64 JDK 21 (22s Gradle build), and the same **103,106 tests** pass on
Linux/x86_64 JDK 21 (11.792s). All 14 local XML suites are fully parsed: declared
and actual testcase/failure/error/skip counts agree. Exactly one actual Windows
registry test is skipped on each POSIX host; no test fails, errors or aborts.
All **143 source/fixture verifiers** pass (38.4s). The preceding exact code
commit ce98df6 now has a verified successful **full CI** run `38825978868`,
including both the full Linux job and the actual Windows 10,473-test job.

Installed/core JARs agree, all **167 HTTP classes**, **68 complete notices**,
**250 Pandoc assets** and the original 60,137-byte suffix list are packaged
exactly. Every one of the preceding **2,465 non-HTTP classes** is byte-identical;
all CLI/dependency JARs are unchanged. Reviewed existing entry changes are
confined to HTTP classes (request field/constructor call sites, exception Show
interfaces, and corresponding source line/metadata changes).

This does **not** claim a complete native network stack. Constructing a Kotlin
manager still requires an explicit raw connection factory and socket-initializer
effect. The original defaultManagerSettings raw socket backend,
rawConnectionModifySocket/Size, actual socket address lookup/racing/cancellation,
TLS transport, request manager override/mutation, responseOpenHistory, automatic
cookies and original local-server/httpbin integration tests remain pending.
The original complete Pandoc readers/writers/App/Lua and all tests, then calibre
MOBI/all its tests and other formats absent from Pandoc/all their tests remain
required. This checkpoint does not change that full objective.

### HTTP socket orchestration and original address-race ownership

This checkpoint translates http-client **0.7.19 Connection** socket control
functions: withSocket, openSocket, socketConnection, openSocketConnection/Size
and firstSuccessful. Manager's rawConnectionModifySocket and Size factories
now connect to this layer with an explicit Network.Socket backend. Native
syscall bindings are still required; no Java HTTP client or missing-transport
stub is substituted for the original network implementation.

Address resolution retains the stripped host and signed decimal service port
and requires defaultHints with Stream. An explicit HostAddress bypasses lookup
and creates an AF_INET/Stream/protocol-6 address. openSocket acquires the socket,
sets NoDelay to 1, runs the user tweak and connects, in that order. Failure
after acquisition invokes close; acquisition failure does not. withSocket
closes its selected socket if the final action throws and otherwise transfers
ownership. socketConnection uses the previously ported makeConnection with
receive/sendAll/close effects, including its once-only close and closed guards.
The original default receive size is 8192; Size passes the signed Int unchanged
to the backend, whose actual native allocation/recv validation is still pending.

The original approximate happy-eyeballs race starts every candidate through
forConcurrently, with delay **n * 250,000 microseconds** for index n > 0. The
first success fills a separate result MVar. If every attempt fails with an
IOException, the error from the last address in input order is returned,
regardless of completion order. A non-IO child failure cancels siblings but
does not fill that separate MVar: the unlinked withAsync parent keeps waiting
until externally cancelled (or the GHC runtime intervenes). Kotlin preserves
that control path instead of silently treating it as another IO failure.

The required async **2.2.6** concurrently' behavior is translated using a
nested two-child tree and a capacity-one completion channel, not a flat
executor. Cancellation sends to right then left, drains exactly the outstanding
completion writes, and joins children before leaving the scope. Completion
writes and cleanup joins survive interruption. JVM virtual threads do not
inherit caller thread-local state. This is the subset of async required by
Connection; the entire async library and GHC GC/deadlock-detection/runtime
semantics are not claimed implemented.

Native comparison caught and corrected an initial port assumption: network
**3.2.9.0 PortNumber derives Enum from Word16**, whose toEnum checks the range
rather than truncating. That conversion sits inside a lazy list element, while
network's Cabal **Strict/StrictData** settings make its AddrInfo/SockAddr fields
strict. Out-of-range explicit ports therefore fail when the child first uses
the address, before opening a socket, rather than throwing in the resolver
parent. The Kotlin implementation retains that forcing boundary. Both signed
Int64 extremes, negative ports and 65536 are observed independently. The
resolver path still passes the original signed service text to its backend.

The development observer compiles the **entire original Connection module**
with unchanged implementation bytes, replacing only its module/export/import
boundary. Both original Manager raw-factory declarations/bodies are retained
verbatim in a separate module. Original async is used directly by GHC 9.12.2;
the observer does not rewrite the race, forConcurrently, withAsync or bracket.
Network's original AddrInfo/SockAddr/PortNumber types and Show/Enum/strictness
are retained. Controlled socket effects record materialized arguments, and
gates choose attempt completion/cancellation order without assuming scheduler
speed. Every address retains its full ordered effect trace; unrelated traces
are grouped instead of inventing a total order between concurrent callbacks.
The entire pinned network and async source releases/tests and complete BSD
notices are retained with archive SHA256 and per-file hashes.

There are **342 independent original-code observation groups** with **894
source hashes**. They cover lookup/explicit address selection and lazy checked
ports, IPv4/IPv6 candidate metadata, all socket setup/final-action failure
phases, cleanup overriding a connect error, receive/send/unread/close behavior,
default/custom chunk sizes and both Manager raw factories, all-IO failure
completion permutations, first success at every candidate, waiting after a
non-IO child failure, and external cancellation while callbacks or staggered
delays are blocked. Three separate full native captures give the same semantic
corpus SHA256:
`ed1d23d1ad42c0c5765c141e7c9ddd7dc47c27a894597e5b24afcfabd8e84e3f`.
These are additional observations, not relabeled original upstream unit tests.
Four JVM checks additionally prove last-error object identity, null success and
absence of inherited thread state, waiting for cancelled sibling cleanup, and
the actual quarter-second system delay. All **346 new tests** pass.

All selected HTTP/environment checks pass: **103,452 successful tests** on
macOS/aarch64 JDK 21 (40s Gradle build), and the same **103,452 tests** on
Linux/x86_64 JDK 21 (14.207s). All **15 local XML suites** are parsed completely;
actual testcase/failure/error/skip counts equal their declarations. The one
actual Windows-registry-only test is intentionally skipped on each POSIX host.
There are no failed, erroneous or aborted tests/containers. All **144 source/
fixture checks** pass (37.8s). Installed/core JARs agree: **179 HTTP classes**,
**70 complete notices**, **250 Pandoc assets** and the unchanged 60,137-byte PSL
snapshot match source/build bytes. All **2,465 preceding non-HTTP classes** and
all CLI/dependency JARs remain byte-identical. Existing changed HTTP entries are
confined to Manager's new factories and corresponding source/debug metadata;
HttpConnectionKey bytecode was inspected and only its inline line table shifts.

The preceding Manager commit 4aadfc8 has a verified successful actual Windows
job in run `38829085149`; its separate full Linux CI job remains in progress.
The native Network.Socket ABI backend, address resolution/error mapping,
nonblocking sockets and polling/cancellation, syscall IO error representation,
Windows initialization, default raw factory, TLS, request override/mutation,
responseOpenHistory, automatic cookies and original local-server/httpbin
integration tests remain pending. The full original Pandoc port and all tests,
then calibre MOBI/all tests and other formats missing from Pandoc/all their
tests remain required without narrowing the objective.

### Native POSIX network addresses, foreign C strings and errno

This checkpoint translates network **3.2.9.0** address types, their native
marshalling and getAddrInfo/followAddrInfo for **Darwin 64-bit little endian**
and **Linux/glibc 64-bit little endian**. The native adapter calls libc's actual
getaddrinfo/freeaddrinfo/gai_strerror through JNA; it does not replace lookup
with Java InetAddress, its DNS cache or its address parser. Native socket IO
and the default HTTP raw backend are still separate unfinished work.

Family/socket-type values retain signed CInt ordering, named-pattern aliases,
first-alias Show selection, unsupported -1 values and General constructors.
AddrInfo flags retain original ordered bit packing/unpacking, unknown-bit
handling and duplicate input behavior. IPv4 host words retain native byte
order while ports and IPv6 address words use network order. IPv6 Show keeps
original first-longest-zero compression and mapped/compatible IPv4 forms.
SockAddr Eq/Ord follows original constructor and field order, including unsigned
fields and individual Haskell Char slots in Unix paths. Unix marshalling uses
Char-to-CChar truncation, original capacity checks, Darwin sa_len and Linux
abstract-path sizing; original C-string peek truncates at the first NUL.

Both ABI assets come from original native headers and Storable instances,
including platform-dependent family widths and AddrInfo pointer offsets.
The original AddrInfo alignment is CInt alignment, and poke zeros ai_addrlen
using CSize width, even where the actual field is narrower. These details are
preserved rather than replaced with a guessed JVM struct. Successful lookup
copies the full linked result in order before freeing once, retaining duplicate
nodes and null versus empty canonical names. Failure while copying also frees
the result. A successful native call returning a null root produces the original
NoSuchThing record. Socket initialization precedes host and service encoding;
Darwin removes the first AI_NUMERICSERV flag when service is Nothing, while
error context retains the original hints. gai_strerror executes before fetching
the foreign codec used to decode its returned C string.

GHC **9.12.2** foreign C-string encoding initially uses IgnoreCodingFailure,
independently of filesystem RoundtripFailure. The shared locale encoding name
is captured once. Built-in UTF-8/16/32, ASCII and Latin1 encoders/decoders and
native stateful iconv retain malformed-input recovery, surrogate handling,
embedded C NUL, empty input and encoder buffer growth. Original withCStringLen
and Len0 restart the input after growth without resetting BOM/iconv state;
the Kotlin encoder preserves that behavior. The charIsRepresentable validation
catches any IOException and reports the original structured unknown-encoding
error. A reusable GhcIOException now carries the original IOError record and
defers rendering; HTTP exception display recognizes this native IO carrier.

POSIX errno conversion retains every original errnoToIOError guard in order,
including aliases and unavailable constants, raw errno/location/filename/handle
fields and the original 512-byte strerror buffer behavior. Linux calls the
POSIX int-returning __xpg_strerror_r, rather than the incompatible GNU variant.
The GHC wrapper's ERANGE ellipsis and other-failure fallback are retained.
The new original GHC source snapshot includes Foreign.C.Error, configurable
Foreign.C.String.Encoding and cbits/strerror.c with revision and file hashes;
the existing complete GHC license is packaged unchanged.

The development observer compiles the **whole pinned network release** with
only Info/Types module visibility changed in network.cabal. Every other original
source/test byte is checked. Original pattern-table bodies are retained verbatim;
errno guards are enumerated in their original order. Original native code
performs marshalling, lookup, linked traversal, C-string conversions, error
construction and Show. The observer checks the complete GHC version 9.12.2.
The Linux build uses the same freeze and exact selected Hackage Cabal revisions.
No expected conversion algorithm is reimplemented in the observer.

There are **5,411 independent observation groups per native platform**, with
**972 source hashes** each. They include all 256 IPv6 zero-run patterns,
unsigned address ordering, Unix path/length/NUL edges, named integer aliases,
every AI flag combination with unknown bits, original AddrInfo zeroing, 165
native errno cases, 61 actual lookup cases, and controlled linked lists with
duplicates, malformed UTF-8 and empty/NUL canonical names. Two original lookup
examples have matching input observations; this is not a claim that the entire
original network test suite has been ported. The original IPv6 AI_ADDRCONFIG
unit, remaining network tests and full local-server suites remain pending.

The codec observations cover **28,966,912 Char inputs** in independently hashed
encoding groups (all 0..0x10ffff slots for 13 codecs and two terminator modes)
and **851,968 two-byte decodes**, plus empty, malformed, alias and shift-state
edges. Error projections use the original raw ioe_description field: replacing
the initial ioeGetErrorString accessor changed only 205 description fields,
leaving Show and all other observed fields unchanged. Kotlin never supplies
expected values. Final semantic corpus SHA256 values are:

- macOS: `09b8894991030f59ac3afb84cd4643699e76a8e3302c2d698ce9b9922b1557c6`
- Linux: `1f101c45281950c1664c15a7ae5c1443698b9258bf895f198708db4fe8a81844`

Each POSIX host runs its native corpus and the other platform's **911 controlled
ABI cases**, plus two ownership/effect-order checks: **6,324 new tests**. Native
codec, errno and actual resolver results are not compared against another OS's
expectations. Both controlled ABIs are tested independently of system lookup.

All selected HTTP/environment checks pass on both hosts: **109,776 successful
tests** on macOS/aarch64 JDK 21 (62s Gradle build), and the same **109,776 tests**
on Linux/x86_64 JDK 21 (76.859s). All **16 local XML suites** are fully parsed;
actual testcase/failure/error/skip counts agree with their declarations. The one
Windows-registry-only test is intentionally skipped on POSIX; no test fails,
errors or aborts. All **146 source/fixture checks** pass (38.2s).

Installed/core JARs agree, including **202 HTTP classes**, **70 complete notices**,
**250 Pandoc assets**, both native ABI assets and the unchanged **60,137-byte**
PSL. All CLI/dependency JARs remain byte-identical. Of **2,465 preceding non-HTTP
classes**, only the three intended environment/encoding entries change.
Normalized javap instructions/signatures/constants for all other changed old
class entries are identical except the two intended HTTP IO-exception wrappers;
remaining HTTP differences are metadata/debug information. The preceding socket
commit **8eb8021** has a verified successful complete CI run **38831296360**,
including both full Linux checks and the actual Windows registry job.

This is still an incomplete port. Windows/Winsock/native code-page support,
other ABIs, mutable GHC encoding globals and raw out-of-Unicode String integration,
network Read instances/getNameInfo, socket syscalls/nonblocking polling and
cancellation, default manager raw transport, TLS, request override/mutation,
responseOpenHistory, automatic cookies and the complete original local-server/
httpbin suites remain pending. The complete original Pandoc readers/writers/App/
Lua and all tests, followed by calibre MOBI/all tests and other formats absent
from Pandoc/all their tests, remain required without narrowing the objective.

### Native POSIX socket IO and default raw HTTP transport

This checkpoint supplies an actual native POSIX HttpSocketBackend for the
preceding address-resolution and connection-race ports. Network.Socket 3.2.9.0
socket creation, connect, integer option calls, send/sendAll/recv, bind/listen,
socket/peer names and shutdown execute through libc, not Java sockets or an
external upstream executable. Java TCP peers are used only by tests.
HttpManagers.defaultPosixManagerSettings/newPosixManager connect the original
no-op-tweak default raw factory to this backend. General construction with
explicit factories remains available; Windows/Winsock and TLS are not supplied
by these POSIX-specific helpers.

Native sockets retain original nonblocking flags, the absence of CLOEXEC on
socket creation, IPv6Only=0 for IPv6 Stream/Datagram and DontFragment=1 for
IPv4 where supported. Darwin follows original Posix.Internals: F_GETFL retries
EINTR, and a failed F_SETFL write is deliberately ignored. Native comparison
found and fixed an actual ABI error in the first binding: on Darwin/arm64,
fcntl's variadic flags argument must use the variadic ABI. Calling it as fixed
arity returned success but left O_NONBLOCK unset. The JNA varargs binding now
passes the flags correctly; actual native flag checks and blocked-IO interruption
prove the resulting sockets are nonblocking.

The original errno guards distinguish a non-retrying option setter from EINTR
retrying operations. MayBlock retries EINTR immediately, waits only for EAGAIN/
EWOULDBLOCK and retries after readiness. Connect retries EINTR, waits for
EINPROGRESS and then reads SoError; other failures retain original location,
errno, description and IO-error type. sendAll preserves empty-input short
circuiting, short writes, zero-write waiting and ByteString.drop bounds. It
keeps one native buffer and advances views instead of repeatedly copying the
remaining payload. recv rejects negative lengths at ByteString.recv and zero
at recvBuf, retaining the original distinct structured errors and EOF result.
The current HttpBytes/JVM-array representation still limits very large positive
allocations; the full Haskell ByteString size domain remains unfinished.

Socket objects preserve identity equality and their original descriptor Show
following atomic invalidation. Explicit close ignores the C close result and
runs once; a Cleaner provides the JVM ownership/finalizer boundary, and withFd
uses a reachability fence. Native readiness uses poll with immediate readiness
wakeups and a 20ms upper observation interval for Java interruption and owner
invalidation. HUP/ERR wake the original syscall path for its actual result.
This is a JVM boundary, not a claim to have ported the full GHC event manager,
masking, signals, finalization or every close/reuse race.

In particular, **two original event-manager observations remain unported and
are explicitly skipped**: direct read/write registration of descriptor -1.
Darwin's original kevent and Linux's modifyFdOnce produce backend-specific
records; the second Darwin registration can also see the failed preceding
registration's state. The current JVM poll boundary rejects invalid descriptors
using threadWait/EBADF to avoid poll's silently ignored negative entries.
These behaviors are not asserted equivalent. Both original expectations and
selected original GHC Event.Thread/Manager sources are retained for completing
the runtime port; the skips must be resolved before claiming completion.

The observer compiles the entire original network release, changing only
Options module visibility. Original send/waitWhen0/sendAll declarations and
bodies are retained verbatim with controlled sendBuf/readiness effects, recording
all remaining-byte views and zero-write waits. Actual GHC errno guards execute
scripted native errno/results. Other observations call actual original socket
creation/options, invalid-descriptor operations and TCP/half-close. Native ABI
assets contain original header constants, poll layout and compiled option pairs.
The same GHC 9.12.2/freeze and exact selected Hackage revisions run on both hosts.
Every original source/test byte, extraction and retained source hash is checked.

There are **366 independent observation groups per OS**, **364 implemented and
two explicitly pending**, with **1,050 source hashes** per manifest. The groups
cover 180 guard cases, 31 original sendAll traces, 17 actual invalid-socket/length
cases, 120 actual creation/flag/default-option cases and 16 actual TCP exchanges,
plus the two pending runtime observations. Semantic corpus SHA256 values are:

- macOS: `9bcd43c5713d72c07e76d7c1935afe98c9bc3ee1bd5f269b033e9f79a18bb364`
- Linux: `5b85b50c8179224b59ce7e8e982ff50ba31140bf0e724b57b1084d4c773f64c2`

The Kotlin suite additionally translates **10 original ByteStringSpec TCP units**
with the original literal message, checking send/sendAll/recv, closed/unconnected
errors, receive overflow and EOF against independent Java peers. Five JVM/native
integration checks cover read cancellation and concurrent close, interruption
of a partly written 8MiB sendAll against a non-reading peer, bound socket identity/
Show and repeated close, invalid-descriptor poll termination, and an actual
HTTP manager connection reused after chunked and fixed responses whose headers
arrive incrementally. There are **379 successful new tests** and the **two
explicit pending GHC-runtime skips** on each supported POSIX host.

All selected HTTP/environment checks pass their implemented assertions on both
hosts: **110,155 successful tests** on macOS/aarch64 JDK 21 (69s Gradle build)
and **110,155 successful tests** on Linux/x86_64 JDK 21 (76.987s). All **17 local
XML suites** are fully parsed: **110,158 cases**, zero failures/errors and three
skip records. One is the Windows-registry-only test; two are the explicitly
pending GHC event-manager assumptions. The Linux console reports these as one
platform skip and two assumption aborts, with no failed/aborted containers.
All **148 source/fixture checks** pass (38.2s).

Installed/core JARs agree: **216 HTTP classes**, **70 complete notices**, **250
Pandoc assets**, all **four ABI assets** and the original **60,137-byte PSL**
match source bytes. All **2,471 preceding non-HTTP classes** and every CLI/
dependency JAR remain byte-identical. Nine changed old HTTP class entries have
identical normalized javap instructions/signatures/constants; their changes
are debug/metadata only. HttpManagers adds the two intended POSIX helpers.
The preceding **e4e8555** address/C-string commit has a verified successful
complete CI run **38836697051**, including full Linux and actual Windows jobs.

Remaining required work includes the two retained event-manager observations,
complete GHC runtime ownership/masking/signal/GC fidelity, Windows/Winsock, other
ABIs, accept, datagram/vectored/message IO, all socket option value types,
gracefulClose, Read/name-info/interface APIs, full HTTP request override/mutation,
responseOpenHistory, automatic cookies, TLS and every original local-server/
httpbin test. The complete original Pandoc readers/writers/App/Lua and all tests,
then calibre MOBI/all tests and other formats absent from Pandoc/all their tests,
remain required. This checkpoint does not redefine or complete the full goal.

### GHC event registration, native queues and thread-wait ownership

This checkpoint replaces the preceding temporary timed poll boundary with
GHC **9.12.2 Event.Manager** registration/dispatch logic and actual **EPoll on
Linux/glibc** or **KQueue on Darwin**. Socket waits now park on their original
registration's completion channel. The shared native loop performs the original
two nonblocking probes, with a yield between them, then a blocking native wait.
It does not periodically wake IO callers to check Java interruption.

The registry retains 32 fd-hashed locks, unique registration cookies, prepended
callback order, separate event/lifetime bits, and absent versus empty entries.
Only an explicit unsupported-False response restores the preceding table state;
a backend exception leaves the inserted entry. Dispatch partitions triggered
and untriggered callbacks, preserves the original saved ordering, retains
multishot callbacks and performs the original one-shot/multishot rearming.
Callbacks execute outside the callback-table lock. Unregister and close preserve
their different backend checks and callback behavior. Manager step/loop,
release/resume, shutdown and cleanup retain the original state transitions.

Native EPoll uses MOD-then-ADD on ENOENT for one-shot registration and the
original epollControl/modifyFdOnce error locations. Native KQueue deletes old
filters before adding new ones, retains Darwin's disabled one-shot policy,
returns unsupported on EINVAL/ENOTSUP, and treats EINTR as the source does.
Returned ERR/HUP and filter values map through the original event rules.
Both backends start with 64 native event slots and double capacity after a
full batch. Their layouts, timespec offsets, flags and constants come from
native headers in separately checked ABI assets. KQueue deletion retains the
original pid/fork check. Native control uses the original nonblocking/cloexec
pipe setup, wakeup pipe on Darwin and eventfd on Linux, wakeup/death messages,
drain sizes and shutdown error rules for newControl(False).

Socket invalidation still happens atomically before close. Native close now
removes registrations under the event-manager locks, performs the raw close
once, then unlocks and notifies each waiting registration with evtClose.
The stable manager-array close path supports one or several managers; callbacks
still run if the raw-close action throws. Cancelling a blocked JVM wait invokes
original unregisterFd_ behavior before leaving the wait. The JVM uses Java
interruption at this boundary; this does not claim the complete GHC mask/throwTo
runtime. Capability-array replacement/retry remains pending. Exceptional backend
modification failures inside closeFdWith also remain a fidelity gap: the current
JVM path releases acquired locks instead of reproducing the original stranded
MVar behavior. Standalone-manager GC ownership is also unfinished; the actual
shared system manager remains process-owned.

The two previously pending direct threadWait(-1) observations now **execute and
pass without assumptions**. Their original requests and expected values are
unchanged: Darwin naturally produces kevent/EBADF and then kevent/ENOENT after
the failed prior registration; Linux naturally produces modifyFdOnce/EBADF.
No fd=-1 special case or canned error string supplies these results. A dedicated
native manager isolates their retained failed-registration state in the JVM
suite. Only their old pending group labels were renamed; all **366 preceding
network-IO requests and expected values** were compared and remain identical.

The new vendor snapshot retains **22 complete original Event files** with
revision/file hashes and the full existing GHC notice. The development observer
compiles **12 unchanged original Event modules**, changing only Manager exports
for record/state/dispatch observation. OriginalThreadWait contains the complete
original threadWait and closeFdWith declarations/bodies verbatim; only manager/
array lookup effects are supplied by its header. The fixture importer verifies
all copied bytes and both exact excerpts. No expected registry, backend, close
or wait algorithm is reimplemented in the observer. Both native builds use
GHC 9.12.2 (patch version enforced), the retained freeze and exact selected
Hackage revisions.

Each platform has **3,524 independent original-code observation groups** and
**1,094 source hashes**. There are **3,424 controlled state-machine programs**
covering event/lifetime combinations, two/three-callback ordering and saved-list
partitioning, fd hash collisions, unknown/replaced cookies, false rollback versus
throwing effects, callback failures, all step probe paths, release/resume and
cleanup. They include 1,000 seeded action sequences and complete per-action
callback-table/effect/state snapshots. The remaining **100 native groups**
include 32 failed-registration programs, 60 gated real-pipe waiter scenarios,
one native release/resume/shutdown scenario and seven queued-readiness bursts.

Real waiter scenarios execute original threadWait/closeFdWith with up to four
waiters and one/two managers. They wait for actual registrations before readiness,
partial/full cancellation, replacement or close, retain each result by input id,
and prove one raw close plus callback notification even when that action fails.
Bursts make up to 90 descriptors ready before stepping, crossing the original
64-event initial capacity. Ephemeral descriptor/unique values and unrelated
scheduler completion order are not projected. Async ThreadKilled is projected
against JVM interruption only at the deliberately blocked wait boundary.
These are observations, not relabeled original GHC testsuite units.

Final semantic corpus SHA256 values are:

- macOS: `81e5fbbbb0a3753c2f2cfc4373eb0112b371bee81f77ddca46a93a840c1c5895`
- Linux: `cf8f11bae29a8725a1cb9ddaeff94ddd754dbe1a881009bd376b18ffcc271417`

Each supported POSIX host executes its entire 3,524-group corpus and the other
platform's 3,424 controlled programs: **6,948 new assertions**. Native results
are compared only to the matching OS's original expectations. Existing socket
integration tests now wait for real registrations before cancellation/concurrent
close, rather than observing an obsolete poll call.

All selected HTTP/environment checks pass on both hosts: **117,105 successful
tests** on macOS/aarch64 JDK 21 (63s Gradle build) and the same **117,105 tests**
on Linux/x86_64 JDK 21 (74.415s). All **18 local XML suites** are fully parsed:
declared and actual testcase/failure/error/skip counts agree. Of **117,106 cases**,
only the actual Windows-registry test is skipped on POSIX. There are no failures,
errors, assumption aborts or aborted containers. The former two GHC assumptions
are gone. All **150 source/fixture verifiers** pass (38.1s).

Installed/core JARs agree. All **239 HTTP classes**, **70 complete notices**,
**250 Pandoc assets**, all **six native ABI assets** and the unchanged
**60,137-byte PSL** match source/build bytes. Every one of the preceding **2,471
non-HTTP classes** and every CLI/dependency JAR remains byte-identical. Existing
changed classes are confined to the intended socket IO/ownership/readiness
layer and its source/debug metadata; the Kotlin module map adds the new native
helper facade. The preceding native IO commit **c86a245** has a verified
successful complete CI run **38839234431**, including full Linux and actual
Windows jobs.

The complete goal remains unfinished. Required runtime work still includes
capability-array replacement/retry, full GHC masking/throwTo/signals/timers/weak
references, all exceptional backend-close paths, standalone-manager GC and
other OS/ABI support. Network accept/datagram/vectored/message IO, all option
value types, gracefulClose, Read/name-info/interface APIs, Windows/Winsock,
complete HTTP request override/mutation/history/cookies, TLS and all original
local-server/httpbin suites remain pending. The full original Pandoc readers/
writers/App/Lua and all tests, followed by calibre MOBI/all tests and other
formats absent from Pandoc/all their tests, remain required without narrowing
that objective.

### HTTP request execution, manager overrides and response history

This checkpoint translates http-client **0.7.19 Core** request execution and
Client.hs **responseOpenHistory/withResponseHistory** into Kotlin. HttpExecution
uses the existing manager, keyed pool, wire builder, response/body readers,
CookieJar, redirect policy and native POSIX raw connector. Haskell or another
external converter is not a production dependency.

The execution layer includes httpRaw/httpRaw', ordinary streaming responseOpen,
withResponse, strict httpLbs, httpNoBody, responseOpenHistory/withResponseHistory
and interactive withConnection. Request manager overrides select the manager
before its modifyRequest hook. Ordinary responseOpen retains the original
initial modification and per-hop modification, initial-manager exception/
response hooks and final-request status hook. History retains its distinct
path: no ordinary header validation/status/modifyResponse wrapper, per-hop
manager selection and the source's separate final-request reference. The
replaceable requestAction effect is a volatile function on each execution
instance, including the shared system instance.

Raw requests apply prepared proxy policy, evict expired cookies before selection,
insert the Cookie header and update response jars at the original two clock
boundaries. Set-Cookie headers remain in the response: the original discards
the header-stripped second result from updateCookieJar. Redirects carry jars,
strip/restore selected headers using the existing original policy, and retain
302/303 method/body replacement. Ordinary redirectCount=0 disables following;
history still follows its first redirect and can then raise TooManyRedirects.
Chronological public history retains the first whole chunks reaching 1024 bytes;
the existing loop then performs its second body flush before close. Exception
history remains reverse chronological. The original request provenance of body
exceptions is retained separately for ordinary and history APIs.

Request/checkResponse and requestManagerOverride fields now carry the original
effects. Status helpers replace a custom checker and preserve the earlier
checkStatus compatibility flag. Non-2xx checks consume the original whole-chunk
prefix and throw StatusCodeException with a unit-body response and strict bytes.
ConnectionTimeout, InvalidRequestHeader and StatusCodeException include original
Show/precedence; HistoriedResponse record Show is independently observed too.
Structured GhcIOException/ResourceVanished now follows handleClosedRead's original
condition instead of requiring the earlier special marker subclass.

Connection timeout selection uses request, manager and original 30-second defaults.
The connection acquisition budget subtracts exact picosecond elapsed time, uses
Haskell's ties-to-even round and signed 64-bit Int conversion, and passes the
remaining budget to the response header parser. Failed sends/header acquisition
release DontReuse; only a reused connection and a retryable direct exception
restart the action. Body failures do not retry. The system boundary uses JVM
interruption and explicitly discards a connector result arriving after timeout
or cancellation, including connectors that ignore interruption. This proves
resource ownership at this boundary, not the complete GHC asynchronous runtime.

The original withConnection expression binds finally to keepAlive more tightly
than <*. A failing user action therefore relies on Managed's finalizer; the
port retains that observed behavior. withResponse/withResponseHistory bracket
both normal and throwing callbacks and close their final response.

The development observer compiles **the complete original Core function bodies**
and the original history declarations with GHC **9.12.2**, the retained freeze
and preceding visibility-only http-client package. Changes are restricted to
module/import visibility, controlled getCurrentTime/acquisition/header timer
effects and unused datatype deriving dictionaries. The importer reconstructs
and checks every copied body. Original Manager/KeyedPool/Request/Response/Cookies
and makeConnection still execute. Expected retry, clock arithmetic, status,
redirect/history, cookie and release algorithms are never copied into a second
observer implementation or generated from Kotlin.

There are **1,771 independent original-code observation groups**, with **815
source hashes**, covering nine APIs, actual pool reuse, two-manager override and
hook ordering, limits, two body flushes, same/different-host header stripping,
cookies and retained Set-Cookie, pre/post-body exception boundaries, bracket
ownership, wire bytes, header validation, timeout selection and picosecond rounding.
The final semantic corpus SHA256 is
`9059bf3b64797aa6af43ac1677dab5a321325c18bf56ae0d0b27327e3d54a300`.
They include ClientSpec-derived header/redirect scenarios; they are not labeled
as the complete original local-server suite.

Six integration units exercise the actual native POSIX manager transport:
keep-alive history with cookies/header stripping, stale pooled connection retry,
ordinary/history redirectCount=0 differences, closing an unconsumed response on
callback failure, header timeout/discard, and timed-out/interrupted late connector
ownership. The last unit tests both timeout and cancellation against a connector
that deliberately ignores interruption.

All selected HTTP/environment checks pass: **118,882 successful tests** on
macOS/aarch64 JDK 21 (84s Gradle build) and the same **118,882 tests** on
Linux/x86_64 JDK 21 (77.981s). Of **118,883 cases**, only the actual Windows
registry unit is skipped on POSIX. There are no failures/errors, assumption
aborts or aborted containers. All **20 local XML suites** are fully parsed;
declared counts agree with actual testcase/failure/error/skip elements. The
processed resource is byte-identical to the final corpus, including original
history record Show/precedence observations. All **151 source/fixture checks**
pass (38.1s).

Installed/core JARs agree on all **252 HTTP classes**, **70 complete notices**,
**250 Pandoc assets**, all **six native ABI assets** and the unchanged
**60,137-byte PSL**. Every preceding **2,471 non-HTTP class** and every CLI/
dependency JAR is byte-identical. Existing changes are confined to HTTP request/
exception/core APIs, their constructor callers and source/debug metadata; the
Kotlin module map includes the added helpers. The preceding d7ae6de Windows CI
job has succeeded; its full Linux workflow is still running and is not claimed
as a completed CI result here.

The full objective remains unfinished. Required work still includes all original
local-server/httpbin HTTP tests, TLS, Windows/Winsock and other native ABIs,
full GHC mask/throwTo/signals/timers/capability/weak-reference behavior and the
previously recorded network APIs. Pandoc resource loading still needs integration
with the complete HTTP/TLS implementation. The full original Pandoc readers,
writers, App/Lua and all tests, then calibre MOBI/all tests and other formats
missing from Pandoc/all their tests, remain required without narrowing scope.

### http-client-tls manager adapter, SOCKS policy and digest authentication

This checkpoint translates the **http-client-tls 0.4.0** manager adapter and
its digest authentication implementation. HttpTlsManagers takes an explicit
Network.Connection backend for the original context/connect/chunk/put/close/
setSecure effects. It installs neither a fake default TLS connection nor a
JSSE replacement for the original handshake/cipher/certificate stack. Existing
Pandoc ResourceContext HTTP fetching still uses its earlier JDK client seam;
its replacement remains required after the connection/TLS runtime is ported.

mkManagerSettings/mkManagerSettingsContext preserve the original supplied
manager fields while replacing TLS/proxy/raw factories and retry/wrap policies.
The no-SOCKS raw factory comes from defaultManagerSettings even when supplied
settings have a custom raw factory. Raw and secure SOCKS policies are independent.
Factories initialize a context at the original boundary when none is supplied;
an existing context is retained. They strip host brackets, ignore the HostAddress
argument and truncate ports to the original PortNumber. Converted connections
retain original makeConnection unread/close guards. Only IOException is suppressed
while closing the converted connection; bracket-on-error cleanup directly closes
the original connection and can replace the action's failure. Its finalizer
captures only the backend and original connection, avoiding an extra reference
to the adapter/global manager that could own the same pooled connection.

The CONNECT adapter writes the tunnel request, converts the connection, invokes
the status-check callback, then calls connectionSetSecure on the original
connection/context. Its incompatible SOCKS/CONNECT error stays deferred in the
connectionUseSocks field: a prior connector failure leaves that field unforced.
Retry extends the default policy only for **PostHandshake Error_EOF**. The TLS
wrapper produces HttpExceptionRequest/InternalException for IO, TLSException,
LineTooLong, HostNotResolved and HostCannotConnect, preserving unrelated exceptions.
The added TLS error model retains original Eq/Show/precedence for all ten TLSError
constructors, six TLSException constructors and all byte-valued alert descriptions/
levels, including unknown values and their custom Show behavior.

newTlsManager snapshots the environment, uses first exact environment lookup
before the last Unicode-lowercased key, and accepts only socks5:/socks5h: URIs
with explicit decimal ports, empty/root paths and no query/fragment. Port parsing
uses Word16 arithmetic. Only the corresponding HTTP/HTTPS proxy policy becomes
proxyFromRequest when a SOCKS value is present. newTlsManagerWith preserves its
original TLS and TLS-proxy factories. The global manager is a replaceable reference;
its initial manager creation and shared global context retain source laziness.
Synchronous failed initialization is cached and rethrown, rather than retried by
Kotlin's default lazy. This does not claim the complete GHC asynchronous thunk/
black-hole/masking runtime.

HttpDigestAuth first performs the real httpNoBody probe with response checking
disabled, then returns the deferred validation effect. It preserves the source
five failure reasons, original derived Show and friendly display (including its
literal wording). It uses the first WWW-Authenticate header, case-insensitive
Digest prefix, original ASCII-space/quoted/unquoted/duplicate parser rules,
raw-byte MD5 inputs, path-only digest URI, fixed nonce count/cnonce and qop-presence
behavior. Authorization replaces all case variants; response cookies enable the
returned request's jar even if the original jar was disabled. The source method
and body remain in both probe and returned request. JCA supplies only the MD5
primitive; the original digest composition and parsing are Kotlin code.

Four complete pinned source archives are now retained: http-client-tls 0.4.0,
crypton-connection 0.4.6, crypton-x509-system 1.9.0 and tls 2.4.6, with all **133
original files**, source/archive hashes and four full notices. The original
http-client-tls test/Spec.hs and all tls test files remain intact. The source
crypton-connection Cabal file initially required tls <2.4; selected Hackage
revision **1**, SHA256
`a3764a44de9f43b1e9f590009ebbea1401fc3911e3ba41ce4d9b93d9f2a8d22e`,
widens this to <2.5. The actual retained GHC 9.12.2 Cabal plan selects that revision
and tls 2.4.6; revised metadata is retained separately from immutable archive bytes.
No dependency-version mismatch is papered over.

The development observer compiles the **complete original http-client-tls module**,
changing only its module/export header and imports for connection/environment/
manager/probe effects. Actual crypton-connection parameter/settings/exception types,
TLS exception classes, HTTP Manager/KeyedPool and MD5 implementation remain original.
A second local module compiles complete original tls Error.hs with only module/
Word8-import changes. No expected digest, SOCKS, manager, retry, cleanup or error
algorithm is rewritten in the observer. Each program starts a fresh original
process to isolate its genuine global CAFs; multiple operations inside programs
observe sharing, replacement, lazy parameters and cached initialization failures.

There are **3,845 independent original-code programs**, retaining **970 source
hashes**. Their semantic corpus SHA256 is
`903e447779b8c9fb129281a2ac87799ffb3e3d86d76fc3a9f622c2ffb7a0d656`.
They include 1,500 SOCKS URI/port/suffix cases, 348 environment/Unicode cases,
556 error/Eq/Show/all-alert-byte programs, every direct exception-policy branch,
factory/context/cleanup/proxy-check failures, two-context/global initialization
and replacement sequences, every credential/challenge byte, duplicate/malformed
headers, disabled-cookie enablement and 200 seeded malformed digest challenges.
The complete source module and error definitions supply all expectations.

Four native HTTP integration units exercise a successful challenge/authenticated
request with original-source Authorization bytes and response cookies, the
original incorrect-authentication status failure, deferred missing-nonce failure
with the custom checker disabled, and sending the original POST body both during
the probe and the authenticated request. These local digest tests do not claim
execution of the original live httpbin/badssl TLS suite.

All selected HTTP/environment checks pass: **122,731 successful tests** on
macOS/aarch64 JDK 21 (71s Gradle build) and the same **122,731 tests** on
Linux/x86_64 JDK 21 (72.166s). Of **122,732 cases**, only the actual Windows
registry test is skipped on POSIX. No failures/errors, assumption aborts or
aborted containers occur. All **22 local XML suites** are fully parsed and
declared counts agree with actual testcase/failure/error/skip elements. The
processed resource exactly matches the final fixture bytes. All **970 source
hashes** also match staged root blobs or pinned staged submodule commits;
Git attributes preserve the revised Cabal CRLF bytes. All **152 source/
fixture verifiers** pass (37.9s).

Installed/core JARs agree on **274 HTTP classes**, **22 new TLS error classes**,
**74 complete notices**, **250 Pandoc assets**, all **six native ABI assets**
and the unchanged **60,137-byte PSL**. Every preceding **2,723 class**, including
all **2,471 non-HTTP classes**, is byte-identical. The only changed preexisting
JAR entry is Kotlin's module metadata; all CLI/dependency JARs are unchanged.
The base **9cd5eb4** has a verified successful complete CI run **38847084808**,
including full Linux and actual Windows jobs; the earlier **d7ae6de** also has
verified successful complete CI **38844725892**.

The full goal remains unfinished. Required next work includes crypton-connection's
native stream/socket/buffer/session/SOCKS runtime; the original TLS handshake,
ciphers, certificate stores/validation, sessions and all TLS/live-server tests;
Pandoc openURL/cache/certificate-policy integration replacing the JDK seam;
Windows/Winsock and other ABIs; the prior full GHC/network/runtime gaps. All
original Pandoc readers/writers/App/Lua and all tests, then calibre MOBI/all tests
and other formats absent from Pandoc/all their tests, remain required without
narrowing that objective.

### crypton-connection buffered reads, backend lifecycle and sessions

`NetworkConnection` translates the pinned **crypton-connection 0.4.6** buffer
state machine: get/getExact/getChunk/getChunk', line reading, and the
waitForInput wrapper's signed-64-bit millisecond-to-microsecond multiplication.
The timeout mechanism is an explicit runtime effect; full GHC asynchronous
cancellation and System.Timeout are **not** claimed implemented here.

The port preserves separate non-reentrant buffer/backend cells, backend
snapshots released before IO, stream reads of 16,384 bytes, socket receives of
1,500 bytes and TLS receive dispatch. Callback pair/remainder failures restore
the previous buffer cell without rolling back underlying reads. Callback values
are evaluated after committing/releasing the cell; EOF never evaluates the
callback remainder. A first empty backend read returns its callback result and
sets EOF; later reads throw the original structured EOF with host/port and
operation location. Zero-size reads bypass the EOF cell; negative sizes retain
the original userError.

Line reading trims LF only, retains CR, checks the limit only after consuming a
chunk without LF, and permits the final chunk containing LF to exceed the limit.
**The original implementation returns an empty line on its first empty backend
read**, despite its API comment promising an exception. The next line read
throws EOF. Independent original-code observations establish this behavior.

Backend lifecycle includes stream put/flush order, native socket sendAll, TLS
send, close and STARTTLS state changes. TLS close suppresses only bye's IO
exceptions and always closes the context; a close failure replaces an earlier
bye failure. Upgrades hold buffer then backend, discard buffered bytes/EOF on
success, restore both cells on failure and do nothing on an already-secure
connection. connectFromHandle/connectFromSocket ignore SOCKS, leave plain
contexts unforced, and call contextNew then handshake without inventing cleanup
on handshake failure. TLS parameter construction/context creation remains an
explicit factory supplied by the eventual original TLS engine.

`PosixConnectionSocket` connects this layer to the already ported native socket
IO, rather than a Java Socket transport. `ConnectionSessionManager` ports lookup,
insert/overwrite, invalidate and atomic lookup/delete for resumeOnlyOnce, with
no tickets. It is not installed into TLS parameters: the upstream installation
line is itself commented out.

The development observer compiles the **complete original Connection module and
Types**, changing only module visibility/names and the TLS effect import. Its
buffer/EOF/line/rollback/session algorithms are unchanged. Stream reads use real
binary files; TLS parameter/context/handshake/read/write/close effects log order
and inject IO/non-IO failures. The pinned release has **no test-suite stanza**;
all original source and notices remain retained.

The corpus contains **1,399 independent stateful original-code programs** with
**19 source hashes**, semantic SHA256
`7bf767f58f357540e6a0915115a83c100b96fd76d0634b46419b14288a7e7b02`.
It covers Int64 extremes, all byte values and raw host characters, first/later
EOF, initial empty/data/EOF cells, callback pair/result/remainder failures,
16,384-byte line boundaries, timer-wrapper overflow, successful/failed/repeated
upgrades, lazy parameters/context/SOCKS, close failure replacement, 250 seeded
reading programs and original session insert/lookup/overwrite/delete sequences.
Five additional units verify concurrent single-use session consumption, backend
snapshot release and put/flush ordering, reentrant reading from the *result*
continuation after buffer release, reader/handshake/isSecure lock ordering, and
real POSIX binary/line/EOF exchange against an independent Java TCP peer.

Validation: **124,135 successful selected HTTP/environment tests** on both
macOS/aarch64 JDK 21 (71s Gradle build) and Linux/x86_64 JDK 21 (74.888s JUnit).
Of **124,136 cases**, only the actual Windows Registry test is skipped on POSIX.
All **23 macOS XML suites** are fully parsed, including agreement between
declared counts and actual testcase/failure/error/skip elements. Linux reports
no aborted tests/containers, no failed containers and no failures. The new class
contributes **1,404 passing tests**, including the five integration/concurrency
units. Processed fixtures match final source bytes. All **153 source/fixture
verifiers** pass (38.6s), and all 19 new provenance hashes also match staged Git
blob bytes. Independent regeneration retains the same semantic corpus hash.

Installed/core JAR identity checks verify **293 HTTP classes**, **22 TLS error
classes**, all **74 notices**, **250 Pandoc assets**, **six ABI assets** and the
unchanged **60,137-byte PSL**. All **2,767 preexisting classes** (including
**2,493 non-HTTP classes**) and every other preceding JAR entry are byte-identical
to the 0e9a136d checkpoint. CLI/dependency JAR bytes are also unchanged.

This checkpoint does not complete crypton-connection: sequential address
resolution/connectTo and its bracket/exception behavior, SOCKS negotiation and
environment parsing, TLS parameter/certificate construction and the actual TLS
protocol still need translation and verification. The existing resource HTTPS
seam remains unchanged until a real translated TLS backend can replace it.
The complete original goal remains required: all Pandoc functionality and tests,
then calibre MOBI and all its tests, then formats absent from Pandoc and all
their tests, along with the previously documented runtime/platform gaps.

### crypton-connection sequential acquisition and SOCKS5 protocol

`NetworkConnectionDialer` translates the complete original **connectTo**
acquisition path, with an explicit GHC getEnv effect and the TLS factory from
the preceding checkpoint. Native `PosixConnectionDialBackend` supplies original
`AI_ADDRCONFIG`/Stream resolver hints, retains resolver order/duplicates and
attempts create/connect strictly in sequence. Create/connect IO failures are
collected in reverse encounter order; resolver failures are outside that
aggregation. Empty results raise HostNotResolved; exhausted IO failures raise
HostCannotConnect for the host actually being resolved. Only translated GHC
IOExceptions are caught. Non-IO errors and cleanup-error replacement preserve
the original bracket behavior.

OtherProxy connects to the proxy while retaining the destination connection ID.
Simple SOCKS resolves/connects to the proxy, sends a Char8-packed destination
domain/port, and rejects domain-name bind replies just like the original.
Negotiation is still inside acquisition: the original performs no explicit
close when negotiation itself fails. Once acquisition returns, TLS setup failures
close the acquired socket; a failing close replaces the original error.
Environment settings use the first colon, default port 1080 without a colon,
and the exact whole-remainder PortNumber Read contract. The actual network
3.2.9.0 instance uses **safeInt bounds 0..65535**, not wrapping Word16 Read.
Nested parentheses, leading Unicode whitespace and binary/octal/hex integers
are accepted; plus signs, overflow/underflow and trailing unconsumed whitespace
reject the proxy. Missing/IO-failing/invalid environment settings fall back to
direct connection; non-IO environment errors propagate.

`Socks5` translates crypton-socks 0.6.2 Wire serialization and the Command
incremental receive driver, establish, rpc/rpc_, connectWithSocket and low-level
listen. It preserves every byte-valued command/method/reply, explicit Other
constructors, errors/host Show behavior, native-endian IPv4/IPv6 words, BE ports,
ignored reserved bytes, modulo-256 length fields followed by the complete
payload, and the source's strict rejection of extra bytes in the current read.
Separate later chunks remain unread. Initial empty input receives a continuation
feed before cereal reports insufficient input; fragmented reads request 4096
bytes. Only method 255 is rejected at authentication selection; other methods
continue as in the original, without inventing authentication support.

`NetworkConnectionClient` implements the existing http-client-tls backend
interface using this dialer and the translated connection buffer/lifecycle.
It requires a TLS engine/context initializer and GHC environment effect supplied
by its caller; no fake default TLS or Java transport is installed. A real
integration test sends an HTTP request for an unresolved destination through a
local SOCKS proxy using the native resolver/socket, original manager adapter,
new connection client/buffer and translated HTTP execution/response parser.
A second integration test inspects actual native getaddrinfo hints and verifies
that the returned native list is freed.

Both source archives are retained byte-for-byte: **crypton-socks 0.6.2** (13 files,
archive SHA256 `a836087e5c277413c79e6d94a7bf346bfb61eaffb2f55555875c76dfeca69f3b`)
and **cereal 0.5.8.3** (11 files, including all three original test files,
archive SHA256 `99905220661b26e5bd91130bd9772554938608a5b1d717240a6eb331121e0f6a`).
Both complete notices are distributed with the core JAR. crypton-socks has no
test-suite stanza. The observer compiles six complete original SOCKS modules and
the complete original Connection/Types, with only visibility/module/import
changes and controlled socket/resolver/environment/TLS effects. Actual cereal
0.5.8.3 supplies wire decoding and actual network 3.2.9.0 supplies PortNumber Read.
No expected wire, retry, bracket, proxy parsing or cleanup algorithm is rewritten.

There are **3,839 independent original-code programs**, **57 provenance hashes**,
semantic corpus SHA256
`70380955dac335755e93499595dad30a9c2c8a2cf9c1ac6522156e1bf71e66c8`.
Coverage includes all method/command/reply/version/address-type byte values,
negative/oversized Enum inputs, native word and BE port order, 255/256/257-byte
domains, method-count overflow, every split/truncation of representative frames,
coalescing/extra chunks, protocol and IO/fatal errors, all sequential-acquisition
branches, inner/outer cleanup-error replacement, post-SOCKS TLS failures,
raw-character destinations and environment values, numeric bounds/parentheses/
bases/whitespace, and 300 seeded malformed frames. Regeneration is deterministic.

Validation: **127,976 successful selected HTTP/environment tests** on both
macOS/aarch64 JDK 21 (70s Gradle build) and Linux/x86_64 JDK 21 (74.167s JUnit).
Of **127,977 cases**, only the actual Windows Registry test is skipped on POSIX;
there are no failed/aborted tests or containers. All **24 macOS XML suites** are
fully parsed and declared counts match actual testcase/failure/error/skip
elements. The new class contributes **3,841 passing tests**, including the two
native integration units. Processed fixtures match final source bytes. All
**154 source/fixture verifiers** pass (38.7s); all **57 new provenance hashes**
also match staged Git blob bytes, including original CRLF files. The upstream
cereal changelog's Setext headings are preserved verbatim with a file-specific
Git conflict-marker-size attribute, rather than altering the archived source.

Core/installed JAR identity checks verify **341 HTTP classes**, **22 TLS error
classes**, all **76 complete notices**, **250 Pandoc assets**, all **six native
ABI assets** and the **60,137-byte PSL**. All **2,786 preceding classes**, including
**2,493 non-HTTP classes**, and every other preceding JAR entry are byte-identical
to b1140aeb. CLI/dependency JAR bytes are unchanged. The earlier 0e9a136d full
CI run **38850392211** completed successfully, including Linux and actual Windows.

Remaining work is explicit: live GHC getEnv/name/value encoding and global
encoding configuration; TLS parameter/certificate construction, the real TLS
engine and all its original tests; full GHC async masking/timeouts; Windows and
other ABI implementations; remaining standalone SOCKS APIs/legacy Parse and
general cereal combinators/instances/property suites. This checkpoint does not
claim these libraries complete. Pandoc resource HTTPS/cache integration remains
unchanged pending the real TLS backend. All original Pandoc readers/writers,
App/Lua and all tests, followed by calibre MOBI/all tests and additional formats
absent from Pandoc/all their tests, remain required under the unchanged goal.

### Native GHC getEnv/lookupEnv and Windows system-error conversion

`GhcEnvironmentLookup` translates the pinned **GHC 9.12.2** getEnv/lookupEnv
platform branches. POSIX calls live libc getenv, encoding names through the
foreign C-string IGNORE codec and decoding found values through the separate
filesystem ROUNDTRIP codec. Missing values never force the filesystem decoder;
empty found values do. Embedded NUL truncation and invalid byte/surrogate
behavior are preserved. A missing getEnv raises the original NoSuchThing record
with location `getEnv`, description `no environment variable`, the full original
name as filename and no errno. lookupEnv returns null for absence.

Windows uses explicit 16-bit names and GetEnvironmentVariableW with an initial
256-unit buffer. Required sizes greater than the current size cause another
allocation/query; the original strict greater-than comparison is retained.
Valid surrogate pairs are combined, lone surrogates retained, and the returned
count does not replace NUL-terminated decoding. A zero result returns null from
lookupEnv. getEnv checks error 203 for a missing variable, otherwise reads
GetLastError again through throwGetLastError. An empty Windows value follows
this same source path, rather than inventing a successful empty getEnv result.
The native binding captures JNA's last-error value immediately after the query
and retains it per Java thread across decoding/cleanup.

`GhcWindowsErrors` translates original errCodeToIOError/getErrorMessage and the
complete C maperrno table/ranges, preserving first-match order (including the
duplicate ERROR_INVALID_HANDLE entry). FormatMessageW uses the original system/
allocated-buffer flags and neutral/default language; successful messages are
decoded as UTF-16 and LocalFree results ignored. Missing messages use lowercase
`Error 0x...`; trailing GHC whitespace is removed before constructing the original
IOError fields. The data table is derived from **46 original C entries**, pinned
mingw-w64 v12.0.0 headers, and the original errnoToIOError guard ordering.

`NetworkConnectionDialer` and `NetworkConnectionClient` now default to this live
getEnv implementation. Their explicit environment effect remains injectable.
The native SOCKS/HTTP integration test now sets an isolated OS variable after
JVM-cache initialization and obtains its proxy through the default implementation.
The former source-counterpart-only environment boundary is thus removed from
the default connection path.

The observer compiles exact platform-selected getEnv/lookupEnv/missing-error
fragments, original Windows wide conversion, Windows error conversion, the
complete original errnoToIOError function, and unchanged C errtable/maperrno_func.
The C build includes actual retained Windows headers with a minimal declaration
shim; no retry, classification, mapping, trimming or lookup algorithm is
rewritten. POSIX conversion uses actual GHC builtin codecs. OS calls, message
ownership and encoding getters are controlled effects. errnoToString supplies
an unused placeholder, since the original Windows function overwrites that
description. Source/header bytes, BSD and mingw header notices are retained.

There are **8,090 independent original-code observations**, **31 source hashes**,
semantic SHA256
`110b78951c0808adcc37d61fa9bddac8b31808d800609e4a3deca92e4f693a46`.
They cover 3,456 name/value codec/NUL/missing cases, lazy encoding/effect failures,
raw UTF-16 names/results, repeated required-size growth, count==size behavior,
zero/empty/missing results, separate first/second last-error calls, message
allocation/free/fallback, every error code 0..4096 plus DWORD wrapping/extremes,
and original IOError records with malformed UTF-16 and Unicode whitespace.
One additional native unit executes real libc in an isolated child JVM on POSIX
or real Kernel32 on Windows, checking live changes, bypass of the JVM cache,
missing/empty/long Unicode values and Windows message formatting/fallback.
Windows CI explicitly selects all new cases and requires **18,564 total
proxy/environment tests**, with no skipped cases and actual registry plus
environment APIs executed. Actual Windows validation is pending the new CI run.

Validation before publication: **136,067 successful selected HTTP/environment
tests** on macOS/aarch64 JDK 21 (67s Gradle build) and Linux/x86_64 JDK 21
(74.459s JUnit). Of **136,068 cases**, only the actual Windows Registry case is
skipped on POSIX. All **26 macOS XML suites** are fully parsed with declared and
actual case/failure/error/skip counts in agreement; Linux reports no failures,
aborted tests or aborted/failed containers. New lookup cases and the native
lookup unit contribute **8,091 passing tests**. Processed fixture bytes match
the final corpus. All **155 source/fixture verifiers** pass (38.4s).

Installed/core JAR checks verify **343 HTTP classes**, the new environment/
Windows-error classes and mapping asset, all **77 complete notices**, **250
Pandoc assets**, **22 TLS error classes**, all **six native ABI assets** and the
unchanged **60,137-byte PSL**. Of **2,834 preexisting classes**, **2,830** remain
byte-identical, including every **2,493 non-HTTP class**. The four changed HTTP
classes share NetworkConnectionDialer.kt: two gain the live default getter;
the neighboring ConnectionSocksEnvironment/PosixConnectionDialBackend retain
identical javap bytecode/descriptors with shifted source/debug locations.
Every other preceding JAR entry and all CLI/dependency JAR bytes are unchanged.
The preceding b1140aeb full CI run **38851887009** completed successfully on both
Linux and actual Windows; the 0e7d3aa6 full run is still in progress.

This does not complete GHC's encoding/runtime layer: configurable global locale,
foreign and filesystem TextEncoding state (including integration with the older
getEnvironment/resolver consumers), all codec failure policies/stream state,
full async masking/RTS thread semantics, remaining Windows APIs and other ABIs
still require translation and verification. System lookup currently uses the
initial locale codecs; controlled entry points prove separate/dynamic encoding
getter ordering without claiming global setters. TLS parameters, certificate
stores/validation and the actual TLS engine remain unfinished. The full Pandoc
port/all tests, calibre MOBI/all tests and additional formats absent from Pandoc/
all their tests remain the unchanged objective.

### GHC TextEncoding recovery and complete encoding005 checkpoint

The next prerequisite for shared encoding globals is the full recovery-mode
selection used by those refs, rather than a façade restricted to the previous
foreign IGNORE/filesystem ROUNDTRIP defaults. `GhcTextEncoding` now translates
mkTextEncoding's exact suffix grammar, builtin dispatch/canonical names, all four
CodingFailureMode values, C-string encoding/decoding and charIsRepresentable.
The builtin codecs and existing Darwin/Linux iconv path now implement strict
InvalidArgument IOError records, IGNORE, TRANSLIT and ROUNDTRIP. Error records
retain the original location, description, absent errno/filename and GHC Char
Show. The exact original `UTF16-LE` textEncodingName spelling is preserved.

Recovery consumes one offending byte on decode; TRANSLIT inserts U+FFFD.
Encode TRANSLIT rewrites the input Char slot to '?' and retries it through the
actual encoder, retaining that mutation across output-buffer growth. The
caller's Kotlin array is not mutated. ROUNDTRIP encode only unescapes failed
U+DC80..U+DCFF slots, preserving original rejection of other surrogates and
unrepresentable characters. Source buffer-space checks precede recovery;
BOM and native iconv shift state survive retries. `char8` is the separate
unchecked Latin1 encoder and reduces every Char modulo 256 without failure.
Representability uses encode/C-NUL-truncate/decode equality and catches only
IOExceptions, including the source's nonrepresentable NUL behavior.

The unmodified GHC **encoding005.hs**, expected stdout and complete IO/all.T
registration are retained at the already pinned GHC commit
`383be28ffdddf65b57b7b111bfc89808b4229ebc`. All **60 actual assertions** in that
program are translated, including direct Latin1 constructors, mkTextEncoding
paths, every byte, unchecked char8 and ASCII roundtripping. The original test
compiles and reproduces its complete expected stdout on both Darwin and Linux
before each platform's fixture generation.

A separate observer calls the actual GHC 9.12.2 mkTextEncoding, codecs,
Foreign.C.String.Encoding functions and recovery implementations; it does not
rewrite expected algorithms. Both compiler version and pinned source bytes are
checked. Each platform corpus contains **1,196 groups**, backed by **27 source
hashes**. Cases cover every UTF-8 byte pair in all four modes; every lead byte
against 16 recovery/width boundaries for other builtin codecs; all single bytes;
malformed/truncated/BOM input; all surrogate slots and representative full-range
Char boundaries; C-string growth with/without terminator reservation; names,
aliases, exact suffix rejection; unchecked char8; representability; native
CP1251/CP1252/Shift-JIS/ISO-2022-JP/UCS-2LE/UCS-4BE and deterministic failures.
The Darwin semantic SHA256 is
`a28f56104b777829efcc15ee31cd90a7a93207293aae7531e8aa9c3bff0fa65d`;
Linux is
`3f5d5c72a382f4a17fcd295b870267827cd8a60ab0aa1dd171ac608300ce15ef`.
The **89 differing groups** are confined to native iconv cases; every portable
builtin observation agrees. Both compressed corpora and upstream notices are
retained; CI checks their provenance. Windows CI selects the **968 portable
new cases/assertions**, requires no skipped tests, and increases the explicit
proxy/environment/encoding total to **19,532**.

On macOS/aarch64 JDK 21, **137,323 selected HTTP/environment/encoding tests**
pass (72s Gradle build); **137,324 cases** include only the actual Windows
Registry skip. All **27 XML suites** are parsed with declared and actual
case/failure/error/skip counts in agreement. The new TextEncoding class contains
**1,256 passing cases**, including all 60 upstream encoding005 assertions.
Linux/x86_64 JDK 21 independently passes the same **137,323 tests**
(75.312s JUnit), with no failed/aborted tests or containers.
All **157 source/fixture verifiers** pass (38.4s). Installed/core JARs match;
all **343 HTTP classes**, **22 TLS error classes**, **77 complete notices**,
**250 Pandoc assets**, **six native ABI assets** and the **60,137-byte PSL** are
verified. **2,841 preexisting classes** are byte-identical, including every HTTP
class. The five changed old classes are the codec/companion and its three
resource callers updated for the expanded constructor. All other preceding JAR
entries and CLI/dependency JAR bytes remain unchanged.

The preceding 0e7d3aa full CI run **38853460201** completed successfully on both
Linux and actual Windows. In 58d0871 run **38855745348**, the Windows job passed
all 18,564 cases including real Kernel32 lookup, growth and error formatting;
the full Linux job is still running at this checkpoint.

This does not complete GHC.IO.Encoding: the three shared mutable global refs
and initial CAF forcing/failure semantics still need integration with
getEnvironment/getEnv/resolver/errno consumers. Windows code-page codecs,
complete streaming BufferCodec APIs, file-handle encoding and async/RTS semantics
remain unported. The new C-string API supports builtin codecs on Windows; it
does not substitute POSIX iconv for its missing code-page backend. The prior
live system getters still use initial locale codecs. TLS parameters,
certificate stores/validation and the actual TLS engine remain unfinished.
The full Pandoc port/all tests, calibre MOBI/all tests and additional formats
absent from Pandoc/all their tests remain the unchanged objective.

### Shared GHC encoding refs, lazy initial CAFs and native consumers checkpoint

`GhcEncodings` now translates the three independent process-wide locale,
filesystem and foreign encoding refs. Getters and setters exchange an encoding
value without demanding its definition. Initial encoding CAFs remain shared
and accessible independently of later ref writes; a saved value is unaffected
by subsequent settings. `GhcTextEncoding` supports deferred definitions and
memoizes both their values and synchronous evaluation failures. POSIX initial
codecs use the shared native locale-name snapshot with the source ERROR,
ROUNDTRIP and IGNORE policies, respectively. The internal mkTextEncoding'
entry point accepts the native charset independently of recovery-mode suffix
parsing, matching the source's initial-encoding construction. The native locale
snapshot also retains a synchronous initialization failure. argvEncoding follows
the current filesystem ref on POSIX and selects UTF-8 independently on Windows.

The existing native getEnvironment/getEnv, Foreign.C.String binding, resolver,
canonical-name memory reader and POSIX errno-description defaults now consult
these refs. Explicit source-effect callbacks remain injectable. Legacy explicit
foreign codec construction retains IGNORE behavior; construction from a
TextEncoding follows all of its configured policies. The initialPosix helper
still refers to the initial foreign encoding, while production defaults use
currentPosix. No JVM environment or DNS cache substitutes for native operations.

The new delayed definition is significant for source ordering: non-null empty
environment blocks retrieve a filesystem setting but do not demand its codec;
getEnvironment enumerates all native pointers before decoding any record;
lookupEnv scans the returned C string before demanding its filesystem codec,
and missing values never retrieve it. An encoding retrieved before a write
remains stable through the whole operation. charIsRepresentable forces the
encoding itself outside its IOException handler, exactly as the source's bang
pattern requires; a deferred encoding initialization error is not converted
into False.

The observer compiles unchanged mkGlobal and its three bindings with only
initial values parameterized per case, plus the unchanged POSIX mkTextEncoding'
and unknownEncodingErr bodies. Original source bodies/platform selection are
verified. Real Haskell thunks/IORefs perform all evaluation/caching; a lazy Map
holds saved snapshots without accidentally forcing them. The native branch
calls actual GHC global settings, System.Environment, Foreign.C.String and
Foreign.C.Error against a scoped native environ block. Both original GHC
encoding/errno implementations, notices and the environment-address bridge are
retained and hashed. An independent Linux GHC 9.12.2 build reproduces **all
3,308 observations** exactly, including **812 actual native programs** and
**2,496 controlled original-code programs**. There are **31 source hashes**;
semantic SHA256 is
`887bac1e07ac3967dc6caaa158998efe9a2d16dba90992c01c6e8d6abd864ec3`.

Cases cover all three refs, initial-CAF aliases, both argv branches, setter/read
laziness, every ordering of initial demands, shared charset capture, cached
initial/installed failures, replacement and restoration, saved snapshots and
all four coding policies. Native cases cover every pair of 20 builtin
foreign/filesystem settings, independence of locale encoding, missing/empty/NUL
lookups, raw environment bytes, configured C-string conversion and errno
messages. One original native program mutates the second environ pointer while
the codec is first forced: its current result must retain the previously read
pointer list, while the next call sees the mutation. Another changes the ref
while the first codec is forced, proving all current records use its snapshot.
Null/empty blocks and poisoned foreign/filesystem encodings prove demand order.

Native JUnit observations run in a child JVM that restores its own environ and
settings, with file-based protocol I/O, a bounded deadline and cleanup. A
separate isolated POSIX binding test constructs resolver/memory consumers before
changing settings, checks host bytes and canonical names, and changes the foreign
setting during getaddrinfo to prove the later canonical-name conversion fetches
it again. On Windows that platform test instead executes actual Kernel32 wide
environment calls while all three byte-encoding refs are poisoned, and verifies
independent UTF-8 argv. A controlled eight-thread JVM check demands the same
failing CAF concurrently, verifies a single evaluation/shared exception, and
replaces its ref without waiting for the old evaluation. These two extra tests
bring the new POSIX class to **3,310 actual cases**. Windows selects **2,498**
controlled/platform/concurrency cases, with an explicit **22,030 total Windows
proxy/environment/encoding cases** and no skips required by CI.

On macOS/aarch64 JDK 21, all **140,633 selected HTTP/environment/encoding tests**
pass (75s final Gradle build). **140,634 cases** include only the actual Windows
Registry skip; all **28 XML suites** are fully parsed with matching declared
and actual case/failure/error/skip counts. Linux/x86_64 JDK 21 independently
passes the same **140,633 tests** (77.707s JUnit), without aborted/failed tests
or containers. The final platform-test factory structure is separately verified
on Linux with all **3,310 new cases passing** (862ms JUnit). All **158 source/
fixture verifiers** pass (38.2s).

Installed/core JAR identity and exact packaging are verified for the new
encoding state/definitions, all **343 HTTP classes**, **22 TLS error classes**,
**77 complete notices**, **250 Pandoc assets**, **six native ABI assets** and
the **60,137-byte PSL**. Of **2,850 preexisting classes**, **2,834** are
byte-identical. Three HTTP callback classes and the errno callback now fetch
the current foreign encoding. Other changed classes are the encoding/resource
implementation and direct callers; the two neighboring lookup-result data
classes have identical javap bytecode/descriptors with shifted source locations.
Every other preceding JAR entry and all CLI/dependency JAR bytes remain unchanged.

The preceding 58d0871 full CI run **38855745348** completed successfully on both
Linux and actual Windows. In 4d06372 run **38857107790**, Windows passed all
19,532 cases; its full Linux job is still running at this checkpoint.

This does not complete GHC's encoding/runtime layer. Windows initial code-page
codec construction remains explicitly unimplemented; the new refs/setters can
carry builtin encodings on Windows, and its wide environment/argv paths bypass
those initial byte codecs. Full CodePage tables/API codecs, streaming BufferCodec
interfaces, file-handle encoding, RTS recursive-black-hole/async exception
semantics, exact weak-memory IORef behavior and remaining ABIs still require
translation and verification. No POSIX iconv or invented UTF-8 default is used
to stand in for Windows code pages. TLS parameters, certificate stores/validation,
the actual TLS engine and integration with the remaining resource-I/O seams are
still incomplete. The full Pandoc port/all tests, calibre MOBI/all tests and
additional formats absent from Pandoc/all their tests remain the unchanged
objective.

### Windows CodePage tables, API buffers and initial encodings

The preceding goal turn made concrete progress with 38b52be's shared encoding
settings. Its full Linux CI run **38859450799** passed; the Windows job exposed
one test-protocol encoding failure after 22,030 cases. The child JVM's stdout
encoding could replace the Russian JSON string even though Kernel32 wide
lookup itself was correct. The protocol now writes UTF-8 bytes explicitly.
Its isolated regression test forces US-ASCII stdout and verifies a Russian
string before the native binding observation. This fixes the observer without
changing wide environment behavior. The preceding 4d06372 run **38857107790**
has now completed successfully on both Linux and Windows.

Pinned GHC **383be28ffdddf65b57b7b111bfc89808b4229ebc** CodePage.hs, API.hs,
Table.hs, IO.Buffer and the table generator/Makefile are retained in
`vendor/ghc-codepage-9.12.2`. Kotlin now implements console-page selection with
ACP fallback, the shared lazy currentCodePage snapshot, all 29 compact single-
byte tables, special UTF page dispatch, Windows CP-number Read grammar and
case-sensitive builtin aliases. Initial locale/filesystem/foreign encodings
use their original Error/Roundtrip/Ignore modes and the same page snapshot;
the former explicit Windows initializer failure is removed. POSIX still uses
its native iconv path. No Java Charset is substituted for CodePage behavior.

The API path implements CPINFO's 18-byte Storable layout (with 20-byte safe
native backing), per-encoder/decoder GetCPInfo validation, stateless Win32
calls, default-character rejection, shrinking prefix search, binary search,
UTF16/Char buffer conversion and original progress/reset ordering. Recovery
and CString retry/reservation loops retain input mutations across retries
without modifying caller-owned arrays. Native Kernel32 calls capture their
Win32 error before further effects. CodePage descriptors remain lazy: an
invalid page's name is available, but opening even an empty CString fails.

The **11,414-byte** production asset is derived from the original compact
arrays, preserving their indices, duplicate/undefined mappings and signed
Int16-to-Char slots. **301,357** decoder/encoder entries are independently
observed through the original Haskell table functions, including raw Char
values outside Unicode. They are not expectations produced by the Kotlin
loader or a reversed decoder map.

`tools/haskell-codepage-reference` compiles unchanged CodePage/API algorithms,
the complete original generated Table, the Windows mkTextEncoding' branch and
scoped instances of the original locale CAF. Only module/import/effect seams
are adapted; host CWchar is explicitly Word16. Original GHC UTF16 codecs,
CString loops and failure recovery remain the oracle. Controlled Win32 effects
provide DBCS/BMP/astral/malformed output, errors, default flags and writes before
failure. Tests compare complete effect traces, buffer sizes/cursors, fatal
branches and outputs. Low-level source bSearch can read past the logical input
end: both observers use an identical initialized 64-element backing canary to
make these observations physically in-bounds and deterministic, preserving
logical metadata. This does not assert defined behavior for native accesses
outside their backing allocation.

The fixture contains **4,861 observations**, **22 source hashes**, and semantic
SHA-256 **3edac3b355f38e01c7d1f6c10dd7c9dea7a315c49b16b4252dccac71d56c4a29**.
An independent Linux/x86_64 GHC 9.12.2 build agrees with every macOS/aarch64
observation. Additional Windows-only tests execute actual Kernel32 CP932
Japanese conversion, truncated sequences, recovery, lazy invalid GetCPInfo
and initial page selection. CI explicitly requires **4,864 CodePage cases**
and **26,894 total Windows cases**, with no skips. Actual Windows execution
of this checkpoint is pending its pushed CI run; POSIX effect tests alone do
not establish Windows runtime validation.

macOS/aarch64 JDK 21 passes **145,494 selected HTTP/environment/encoding tests**
in a 73s Gradle build. Its **145,495 cases** contain only the real Windows
Registry skip; all **29 XML suites** have matching declared/actual counts and
zero failures/errors. Linux/x86_64 independently passes the same **145,494
cases** (76.080s JUnit), with no failed/aborted tests or containers. All **159
source/fixture checks** pass (38.8s); the final CodePage source manifest is
separately rechecked after documentation updates.

Core/installed JAR identity, all **343 unchanged HTTP classes**, **22 TLS error
classes**, **77 complete notices**, **250 Pandoc assets**, **six native ABI
assets**, the **60,137-byte PSL** and the new table asset are verified. Of
**2,857 preexisting classes**, **2,849** are byte-identical. Changed entries
are limited to the encoding interfaces/implementation and Kotlin module
metadata. GhcCodingFailureMode has identical javap bytecode/descriptors with
shifted debug source locations. Every other preceding JAR entry and all
CLI/dependency JAR bytes remain unchanged.

This checkpoint does not complete GHC's runtime or document conversion. General
streaming BufferCodec/Handle APIs, complete machine-width and RTS semantics,
remaining native ABIs, TLS parameters/certificate stores/validation/engine and
remaining resource-I/O integration still require translation and verification.
The full Pandoc port/all tests, calibre MOBI/all tests, and additional formats
absent from Pandoc/all their tests remain the unchanged objective.

### PEM and the raw ASN.1 substrate for X.509

The preceding goal turn made concrete progress with 68b970f. Its full CI run
**38862524725** has now completed successfully on both Linux and Windows,
including **26,894 actual Windows tests** with native CP932/initial CodePage
execution. The next TLS dependency audit found that the system-store adapter
was retained but its certificate parsing dependencies and lower binary layers
were missing. Semantic search remains unavailable in this sandbox; the audit
followed the pinned dependency freeze and concrete upstream modules.

Retained complete source archives, tests, original notices and per-file SHA-256
manifests for **crypton-x509 1.9.2**, **crypton-x509-store 1.9.0**,
**crypton-x509-validation 1.9.1**, **crypton-pem 0.3.0**,
**crypton-asn1-types 0.4.1**, **crypton-asn1-encoding 0.10.0**,
**crypton-asn1-parse 0.10.0** and **base64 1.0**. Archive identifiers/hashes are
pinned explicitly in the importer. The original Get module's additional
Lennart Kolmodin/Galois 2009 notice is preserved in binary distribution notices,
as well as the original Vincent Hanquez package license.

Kotlin implements PEM sections, structural equality/Show, strict and chunked
reading/writing, exact delimiters and one-CR line handling, line-local Base64,
Char8 truncation of names/header keys, 48-byte output splitting and the original
header writer/parser mismatch. It uses a port of base64 1.0's standard padded
ByteString encode/decode and validity paths, including size checks, canonical
padding bits and exact error priority/offsets. It does not substitute a lenient
Base64 decoder. Lazy Sequence input stops at the original parser's error and
does not force a failing later chunk.

The raw ASN.1 layer implements classes, lengths, headers, events and errors;
Int/Integer conversion helpers; Get result continuations and choice buffering;
header reading/writing; incremental ParseState; strict/lazy event parsing; and
raw event writing. Machine Int is modeled with the pinned hosts' 64-bit Long,
while parser positions/construction ends preserve Word64 wrapping. Incomplete
headers/primitives retain their continuations across feeds. Empty raw-parser
feeds do not flush Get, whereas explicitly feeding an empty Get continuation
signals insufficient input. The source's choice position behavior is preserved:
backtracking restores buffered bytes but keeps the failed branch's position.
The raw writer also preserves the original empty ConstructionEnd serialization,
including indefinite constructions; it does not silently insert an EOC.

`tools/haskell-certificate-binary-reference` compiles **22 unchanged original
modules directly from vendor**, with only the actual base64 version CPP branch
selected for PEM. The observer contains projections/input programs, not a second
implementation of parsing or encoding. The original complete PEM test/Main.hs
is compiled unchanged as a second executable: all **four unit cases** and its
marshalling property pass **1,000 generated cases** with seed 9122.

The independent fixture has **12,331 observations**, **136 source hashes**, and
semantic SHA-256 **f656219acc37d78aa89ff8ad16877da1254f7c3c49dfecde1d5b5eca678c4833**.
It includes the verbatim PEM unit inputs, **1,000 PEM marshalling-domain
observations**, **1,000 original ASN.1 Header properties**, **1,000 original
ASN.1 Event properties**, strict/lazy chunk boundaries, malformed frames,
Base64 table bytes/error conflicts, nested EOC/length boundaries, integer
wrap/truncation, Get choice/EOF behavior, exact raw/PEM Show, and exceptions in
unneeded lazy tails. A separately compiled Linux/x86_64 GHC 9.12.2 observer agrees
with every macOS/aarch64 observation. Kotlin compares complete outputs and every
incremental event/done/result trace, not only successful round trips.

macOS/aarch64 JDK 21 passes **157,825 selected HTTP/environment/encoding/binary
cases** in a 72s Gradle build. All **30 XML suites** are parsed with matching
declared/actual counts: **157,826 cases**, zero failures/errors, and only the
actual Windows Registry skip. Linux/x86_64 independently passes the same
**157,825 cases** (76.363s JUnit), with no failed/aborted tests or containers.
All **160 source/fixture verifiers** pass (38.8s), with the final binary-source
manifest separately rechecked after adding explicit archive pins. CI requires
**12,331 binary cases** and **39,225 total Windows cases**, without skips;
Windows execution of this checkpoint awaits its pushed CI run.

The core and installed JARs match. All **2,887 preexisting classes** remain
byte-identical; **53 new PEM/ASN.1 classes** bring the core to **2,940**.
Only Kotlin module metadata changes among old JAR entries. All **343 HTTP
classes**, **22 TLS error classes**, **250 Pandoc assets**, **six native ABI
assets**, the CodePage table asset and **60,137-byte PSL** remain exact.
All **86 full notices** are packaged, including the nine new source/license
notices. CLI and dependency JAR bytes remain unchanged.

This is a real binary prerequisite, not a completed certificate store or TLS
implementation. High-level ASN.1 values/strings/bit arrays/OIDs, typed BER/DER
primitive conversion and its remaining DER/Real properties, hourglass-compatible
dates, ParseASN1 combinators, X.509 signed objects/certificates/keys/extensions,
store lookup/merging/platform loading, validation/signatures/fingerprints/cache,
TLS parameters/handshake/ciphers/sessions and resource-I/O integration remain.
Base64 URL/typed/lenient/text APIs and their tests are retained but not claimed
as implemented. The full Pandoc port/all tests, calibre MOBI/all tests and
additional formats absent from Pandoc/all their tests remain the unchanged goal.

### ASN.1 bit arrays, character strings and OID contracts

Published the preceding PEM/raw-ASN.1 checkpoint as **b4e6ef5**. Its CI run
**38866281441** has completed the Windows job successfully, including all
**39,225 actual cases** with no skips; the full Linux job is still running.
This goal turn makes further implementation and independent verification
progress. The complete original objective remains unchanged.

Translated the complete `Data.ASN1.BitArray`, `Data.ASN1.Types.String` and
`Data.ASN1.OID` APIs from retained **crypton-asn1-types 0.4.1**. Bit arrays retain
their independent Word64 length/padded bytes, exported constructor semantics,
unchecked padding subtraction/wrapping, MSB-first indexing, persistent set/clear
operations, structural equality/Show and distinct typed/underlying bounds
errors. OIDs remain lists of arbitrary-size integers with object-ID and lookup
contracts, rather than narrowing arcs to machine integers.

All twelve string encoding constructors retain their original order and
Eq/Ord/Show. Character strings preserve raw bytes, UTF8 IsString construction,
Char8 truncation, BMP's low-16-bit truncation and independent surrogate Chars,
big-endian UTF32, unsupported VideoTex/Character conversion, and original UTF8
truncation/continuation/range errors. The original accepts overlong UTF8 and
does not validate ASCII-like character repertoires; these behaviors are
preserved rather than replaced by a platform charset decoder.

Haskell makes both the String spine and its individual Char values lazy.
Independent observations exposed **33 initial error-priority mismatches** when
scalar evaluation was eager. Kotlin now provides separately suspended elements
(`decodeLazy`), element-by-element demand (`decodePoints`) and full
materialization (`decode`). Expected outputs remain unchanged by this fix.
Both demand orders and partial prefixes are compared directly with the source.

The new development observer compiles **three unchanged original modules**
directly from vendor under GHC 9.12.2. Its bridge only interprets requests,
projects results and selects evaluation demand. The fixture contains **12,250
observations**, **21 source hashes**, and semantic SHA-256
**5be0edf672866c1d723886be2eea1c300ad1199cef1ee637bd867d551f914292**.
Coverage includes every byte across all encodings, all byte/bit set/clear
combinations, malformed/unrepresentable strings, raw/padded constructor
boundaries, comparison/equality, and **1,000 cases in the original upstream
BitArray generator domain**. The type package defines no original test suite;
the encoding package's generator definition is retained and hash-checked.
A separately compiled Linux/x86_64 original observer agrees with all cases.
Kotlin executes **12,251 tests**, including an additional immutability,
typed-exception and arbitrary-size OID contract check.

macOS/aarch64 JDK 21 passes **170,076 selected HTTP/environment/encoding/ASN.1
tests** in a 76s build. All **31 XML suites** have matching declared/actual
counts: **170,077 cases**, zero failures/errors, and only the actual Windows
Registry skip. All **161 source/fixture verifiers** pass (39.5s). CI adds this
fixture check and requires **51,476 Windows cases**, with no skips. Linux/x86_64
JDK 21 independently passes the same **170,076 cases** (77.458s JUnit), with
zero failed/aborted tests or containers and the one Registry skip. Windows
execution of this checkpoint awaits its push.

Core/installed JAR identity is verified. All **2,940 preexisting classes** remain
byte-identical; **12 new classes** bring the total to **2,952**. Only Kotlin
module metadata changes among preceding entries. All **343 HTTP classes**, **22
TLS error classes**, **86 full notices**, **250 Pandoc assets**, **six native
ABI assets**, the CodePage table and **60,137-byte PSL** remain exact, as do all
CLI/dependency JAR bytes.

This completes the named three prerequisite modules, not the full ASN.1
package, certificate handling, or TLS. High-level ASN.1 values, Stream/Pretty,
typed BER/DER and its DER/Real properties, hourglass-compatible dates,
ParseASN1, X.509 objects/stores/validation and TLS/resource-I/O integration
remain. The full Pandoc port/all tests, calibre MOBI/all tests and additional
formats absent from Pandoc/all their tests remain required.

### High-level ASN.1 values, stream extraction and ParseASN1

The preceding goal turn made concrete progress with **b4e6ef5** and **387b84e**.
CI run **38866281441** has now completed successfully on Linux and Windows,
including **39,225** actual Windows cases with no skips. Run **38867372736**
has a successful Windows job with **51,476** actual cases, including native
registry/encoding execution and no skips; its full Linux job is still running.
Neither an observation timeout nor an unfinished full job was treated as a
failure or restarted.

Retained the complete **time-hourglass 0.3.0** source archive (**27 original
files**, including its original tests/platform modules/C source), full license
and per-file hashes. The archive's fixed SHA-256 is
**9c7c7c74dda5033160328606cb3e8724201abadf9a140271c73c89a2612b9d22**.
Its original line endings are retained in Git. Kotlin adds the exact date,
time-of-day, date-time, Month and four Int64 unit models needed by ASN1Time,
their Eq/Ord/Show, and timezone-offset comparison/Show/seconds conversion.
Constructors preserve unchecked values and signed machine-width wrapping,
including the original minBound offset Show failure and pad2 truncation.
This does not claim the whole hourglass library's numeric/Read, calendar,
formatting, epoch conversion, current-clock or local-time APIs.

Translated finite materialized `Data.ASN1.Types` values and exported contracts:
every ASN1 constructor, construction/time types, tag and stream-builder aliases,
arbitrary Integer/OID payloads, immutable byte payloads, Eq/Show and ASN1Object.
Real Eq preserves NaN non-reflexivity and equality of signed zeros rather than
Kotlin data-class Double equality. Original GHC floating-point Show is reused.
Both Stream extraction functions preserve event associations, nesting, signed
depth wrapping, mismatched marker kinds and missing closing markers without
adding validation absent from the source.

The complete finite `ParseASN1` API now includes state/full runs, throw/fail,
Functor/Applicative/Monad/Alternative composition, next/optional/hasNext,
container access/optional access, nested parsers, object delegation and getMany.
Backtracking restores the original input. Nested parsers and full runs reject
leftovers with the original message. Parser result values, Some payloads,
function arguments and ignored bind values can remain suspended independently
of state/errors. Thus an unused error does not replace a later parse failure,
and a full-run leftover error does not force an unused mapped value. getMany
uses an iterative implementation and shared immutable input views. Failure
messages retain raw Haskell String Chars, including a surrogate pair distinct
from one supplementary Char, with both point and Aeson-text observations.

The new development observer compiles **seven unchanged ASN.1 modules** from
vendor and the **13-module original hourglass library**, including its real
platform/C support. No replacement Data.Hourglass module or parser algorithm
is substituted. A small Witness ASN1Object is only a test dictionary observing
getObject delegation and tail-building contracts, not an X.509 implementation.
The two original ASN.1 packages have no standalone test suites. The complete
hourglass suite is retained, not claimed ported or executed here.

The independent fixture has **9,755 observations**, **57 source hashes** and
semantic SHA-256
**697500a100626470199699c7a4d1f69c48a6ba3590b2e99a243daeb9683e94b0**.
It checks all constructors, complete payloads/Show/Eq matrices, 500 random IEEE
bit patterns, unchecked date/unit/offset boundaries, both stream splitters,
all parser combinators, composed programs, deferred-error precedence, raw
error Chars and **10,000-level finite structures/streams**. A separately
compiled Linux/x86_64 GHC 9.12.2 observer agrees with every observation. Kotlin
executes **9,756 cases**, including an extra ownership/optional-null/NaN check.

macOS/aarch64 JDK 21 passes **179,832 selected HTTP/environment/encoding/ASN.1
tests** in a 72s build. All **32 XML suites** have matching declared/actual
counts: **179,833 cases**, no failures/errors and only the actual Windows
Registry skip. Linux/x86_64 independently passes the same **179,832 tests**
(79.061s JUnit), with no failed/aborted tests or containers. All **162
source/fixture verifiers** pass (40.7s); the final new manifest is separately
rechecked after adding raw-error-Char observations. CI requires **61,232
Windows tests** with no skips; execution awaits this checkpoint's push.

The installed/core JARs match. All **2,952 preexisting classes** remain
byte-identical; **54 new classes** bring the total to **3,006**. Only Kotlin
module metadata changes among preceding entries. All **343 HTTP classes**, **22
TLS error classes**, **87 full notices**, **250 Pandoc assets**, **six native
ABI assets**, the CodePage table and **60,137-byte PSL** are exact. All
CLI/dependency JAR bytes remain unchanged.

Typed BER/DER primitive conversion, ASN.1 time parse/print and the remaining
original DER/Real/IntegralReal properties, Pretty, the rest of hourglass,
X.509 objects/stores/validation/signatures, TLS and resource-I/O integration
remain unfinished. The full Pandoc port/all tests, calibre MOBI/all tests and
additional formats absent from Pandoc/all their tests remain the objective.


### Hourglass calendar, arithmetic, epochs and native time boundaries

The preceding published checkpoint **3da909a** made concrete progress on ASN1
values/ParseASN1. Its full CI run **38870266497** is now independently verified
**successful** on Linux and Windows, including all **61,232 Windows cases**
with no skips. The public repository remains **Heapy/kotdoc**. The current
checkpoint continues the full objective; it does not replace the remaining
Pandoc/MOBI/other-format scope with these prerequisite modules.

Translated `Time.Calendar`, finite `Time.Diff`, timezone naming and padding,
Unix/Windows/MJD epoch dictionaries, Elapsed/ElapsedP and ElapsedSince/P
arithmetic, period/duration monoids, TimeInterval conversions, Time/Timeable
conversion, Time/LocalTime operations, and all six `Time.System` functions.
Int64/machine-Int operations retain wrapping and negative floor division;
precise addition normalizes, subtraction makes the original single borrow,
component multiplication does not normalize, and negate/abs/signum retain the
nanosecond component. Rational conversion uses exact arbitrary integers.
Unchecked dates normalize through the original month loop; no Gregorian date
validation is added. LocalTime Eq and UTC-based Ord remain distinct.

Production native backends translate the original POSIX and Windows modules
using JNA, with independently injectable pointer APIs. POSIX retains CInt year
addition before widening, localtime_r at **epoch zero**, gmtoff floor division,
gmtime/localtime failure text, and the original clock backend (Mach calendar
clock on macOS; clock_gettime on Linux). Windows retains unsigned FILETIME
wrapping, 100ns resolution, SYSTEMTIME Word16 fields, ignored milliseconds,
Int32 bias addition/negation, Standard/Daylight/unknown modes and actual GHC
Windows IOException mapping. Existing Win32 and hourglass full notices apply.
Native layouts are verified for the current 64-bit macOS/Linux/Windows targets;
this is not a claim of support for every possible architecture.

Separate APIs preserve generic and specialized conversion paths. The original
GHC 9.12.2 `-O2` build applies the unconstrained `timeGetTimeOfDay/Date` const
RULE to polymorphic calls, yielding zero without a native conversion; direct
DateTime calls use their specific field rewrite. Both paths are observed.
Generic date/time conversion can normalize unchecked input even when the
specialized DateTime identity/field access does not. Future polymorphic
formatting code must use the generic path rather than silently selecting a
specialized identity.

The new development observer compiles **12 unchanged hourglass modules**, plus
its small request bridge. Its POSIX executable links original C/native calls;
two controlled-boundary executables compile the original POSIX and Windows
modules without rewriting their date/time algorithms. Fake FFI/Win32 IO
supplies raw fields, failures and argument/call-order traces. The native error
model uses a fixed exception; actual Win32 error translation is separately
exercised by Kotlin native tests and the existing GHC Windows-error corpus.
A new manifest also verifies the complete, already retained **Win32 2.14.2.1**
archive (**124 files**, no changes to their bytes), SHA-256
**69d15a9fb4ef718353aaf8700a64c5885743d4f34a94f7da273fa12584df0315**.

The corpus contains **10,676 independent observations**, **165 source hashes**,
and semantic SHA-256
**12ce7cf8fe36b7cbaf762f0b778a2c0ed60d84306aabea39c72d7f187a505217**.
It comprises **4,553 pure**, **3,184 controlled POSIX**, **2,894 controlled
Windows**, and **45 actual native-calendar** observations in the common
1601..9999 range. It extracts all **seven original arithmetic literals** with
original expected results and checks the **four original Real regression
examples** against their explicit fractions. It covers every month, negative
and overflowing years/units, all eleven built-in source/target Time types,
three epoch offsets, timezone zero/minBound/overflow, native invalid months,
raw nanoseconds, clock fields, errors and System call ordering. Separately
compiled Linux/x86_64 GHC executables agree on all **10,676 observations**.
Two additional Kotlin native tests exercise live clocks, System wrappers and
real native failure mapping; no host clock/timezone snapshot is frozen.

macOS/aarch64 JDK 21 passes **190,510 selected HTTP/environment/encoding/ASN1/
hourglass tests** in a 67s build. All **34 XML suites** have matching declared
and actual counts: **190,511 cases**, zero failures/errors and only the actual
Windows Registry skip. All **163 source/fixture verifiers** pass (41.5s).
Linux/x86_64 JDK 21 independently passes the same **190,510 tests** (79.667s
JUnit), with no failed/aborted tests or containers and the one Registry skip.
CI requires **71,910 Windows cases** with no skips, including both hourglass
classes and actual Kernel32 execution; execution awaits this checkpoint's push.

Core and installed JAR bytes match. All **3,006 preexisting classes** remain
byte-identical; **67 new classes** bring the total to **3,073**. Only Kotlin
module metadata changes among preceding entries. All **343 HTTP classes**,
**22 TLS error classes**, **87 full notices**, **250 Pandoc assets**, **six
native ABI assets**, the CodePage table and **60,137-byte PSL** remain exact,
as do all CLI/dependency JAR bytes.

The complete hourglass suite is still **not** ported: its missing upstream
`test-time-db` contributes no claimed known cases; formatting/parsing and custom
callback properties, Read instances, remaining derived unit numeric/Enum/
Integral operations, Time.Compat and fuller property-generator coverage remain.
Typed BER/DER, ASN1 time parse/print, remaining DER/Real/IntegralReal tests,
Pretty, X509/store/validation/signatures, TLS and resource integration also
remain. Full Pandoc/all tests, calibre MOBI/all tests and additional formats
absent from Pandoc/all tests remain the original required end state.


### Hourglass format strings, printing, parsing and callbacks

The preceding goal turn made concrete progress and published **c8c86f1**,
covering calendar/epoch/native time prerequisites. Its exact-SHA CI run
**38874522194** has a successful Windows job with all **71,910 cases** and no
skips. At this checkpoint its full Linux job is still executing the Gradle
suite; no observation timeout was interpreted as termination or grounds to
restart it. The original complete Pandoc/MOBI/other-format scope remains active.

Translated `Time.Format` format-element/value models, String tokenization,
ISO date/date-time formats, print/localPrint, both Either-style parsers and
both Maybe-style parsers. Format lists are immutable. Callback equality uses
only the raw Haskell String name. Format/element Show and errors have explicit
CharacterString paths retaining surrogate Chars separately from supplementary
Chars. Text input/output is represented in Haskell Char points throughout.

Every standard element and programmatic precision/text/function element is
represented. Printing uses the original generic Timeable path, not a
specialized DateTime identity. Year2 subtraction, pad truncation/errors,
subsecond components, precision truncation and all timezone sign/zero forms
are preserved. Parsing retains the original initial year/month/day/time,
sequential overwrites, two-digit year pivot, Month2 modulo behavior, unchecked
fixed-width day/time fields, day-of-year dependence on the current year,
nanosecond mask merges, Int64 numeric folding, raw (unscaled) precision digits,
ASCII digits, pinned GHC whitespace, timezone-name consumption without updating
the offset, offset bounds and exact error text. A literal Z leaves the previous
offset intact. Global parsing normalizes through LocalTime only for nonzero
offsets; Maybe parsers discard the remaining input as in the original.

Custom callbacks can preserve/replace state, reject, fail on demand, or replace
the remaining input with an unrelated string. The parser keeps the accumulated
pair, DateTime constructor, date and time fields independently suspended.
This is required to preserve later-error priority, discarded EPOCH values,
timezone-only observations and callbacks producing an undefined date with a
valid time, or vice versa. The original platform modules differ: POSIX builds
the DateTime constructor before demanding rawGmTime, while Windows first
demands SYSTEMTIME. Native adapters now preserve this distinction and keep date
validation independent from time-field access.

`HourglassDateTime` therefore now supports independently deferred fields while
retaining its public eager constructor, getters, component/copy methods,
comparison, equality, hash and Show for ordinary finite values. An actual Java
client compiled against the preceding JAR runs unchanged against the new JAR
and checks all those existing operations. LocalTime Show explicitly evaluates
the value before the timezone: JVM string interpolation otherwise evaluated a
failing inline-class offset before demanding the lazy date/time value. All
preceding hourglass/ASN1 observations continue to pass unchanged.

The new GHC 9.12.2 development observer compiles **12 unchanged hourglass
modules** plus the **unchanged original CustomTimeFormatFct test module**.
It reuses the previous native-boundary stubs verbatim; no format/parser
algorithm is replaced in the reference. The independent fixture contains
**27,638 observations**, **170 source hashes**, and semantic SHA-256
**6fb9ae68a5928e613c3358cfbd78f46f3dc4dff053768be614159abb4944ca2b**:
**5,006 pure**, **8,277 controlled POSIX**, **8,299 controlled Windows**, and
**6,056 actual native/reference-property** observations. Separately compiled
Linux/x86_64 GHC executables agree on every observation.

The three original custom-format roundtrip properties each use **1,000 valid
dates** in the original 1800..2202 generator range with 0..100,000,000ns.
ISO date/date-time roundtrips contribute another **2,000** cases. The two
original printing properties are checked against the independent **Data.Time
1.14** implementation for **1,008** seconds in the common POSIX/Windows range;
the observer also asserts original hourglass agrees. All **1,321 timezone
offsets** in the original -660..660 generator range are parsed. Additional
observations cover all eleven Time types, raw Char tokenization/Show/errors,
very large digit runs, invalid precision, field overwrites, callback state/input
replacement, independently undefined date/time fields and native/error demand.
A complementary Kotlin ownership/deferred-field check brings the new class to
**27,639 cases**. This is stronger evidence for the named formatting behavior,
not proof that every hourglass property/generator or arbitrary deep demand
chain has been ported and verified.

macOS/aarch64 JDK 21 passes **218,149 selected HTTP/environment/encoding/ASN1/
hourglass tests** in a 68s build. All **35 XML suites** have matching declared
and actual counts: **218,150 cases**, zero failures/errors and only the actual
Windows Registry skip. Linux/x86_64 JDK 21 independently passes the same
**218,149 tests** (79.244s JUnit), with no failures or aborted tests/containers.
All **164 source/fixture verifiers** pass (43.9s). CI now requires **99,549
Windows cases** with no skips; execution awaits this checkpoint's push.

Core and installed JAR bytes match. Of **3,073 preexisting classes**, **3,061**
are byte-identical. The **12 changed class files** are confined to hourglass:
four semantic classes (DateTime, LocalTime, POSIX/Windows adapters) and eight
whose complete javap instruction listings are identical (debug metadata only).
There are **28 new classes**, bringing the total to **3,101**. All other JAR
entries and all CLI/dependency JAR bytes remain exact, including **343 HTTP
classes**, **22 TLS error classes**, **87 full notices**, **250 Pandoc assets**,
**six native ABI assets**, CodePage and the **60,137-byte PSL**.

Read instances, remaining derived numeric/Enum/Integral operations, Time.Compat,
the rest of the original hourglass property suite and deeper transformation/
demand stress coverage remain. No known cases from the missing upstream
`test-time-db` are invented or claimed executed. ASN1 time parse/print still
needs integration (its manual timezone suffix must not be normalized twice),
as do typed BER/DER and remaining DER/Real/IntegralReal tests, Pretty,
X509/store/validation/signatures, TLS and resource integration. Full Pandoc/all
tests, calibre MOBI/all tests and additional formats absent from Pandoc/all
tests remain the required end state.


### ASN1 primitives, typed BER/DER and all five original encoding properties

The previous goal turn made concrete progress and published **2c348cd**.
Both exact-SHA runs **38874522194** (c8c86f1) and **38877005202** (2c348cd)
are now verified successful on Linux and Windows, including **71,910** and
**99,549** actual Windows cases respectively, with no skips. No running job
was restarted because it was slow or an observation timed out.

Translated `Data.ASN1.Prim` materialized primitive values, headers, integer,
boolean, bit/octet/string, NULL, OID, Real and ASN1 Time conversion, smallest
length, primitive/one/list/constructed encoding; and the typed BER/DER
Encoding/BinaryEncoding paths. Existing raw incremental parsing and writing
remain the byte boundary. The typed decoder preserves an important distinction:
raw parse failure is an eager Left, while a successful raw parse returns a
lazy value/representation stream whose typed/policy errors throw ASN1Error
when demanded. Prefix zero or success-tag observation does not force the
stream. Primitive headers retain their original ignored fields; constructed
values map universal Sequence/Set and arbitrary Containers, with raw event
associations preserved. The DER policy remains exactly the original length
policy, including machine-Int exponent behavior. It does not silently add a
canonical boolean check absent from the original typed DER decoder.

ASN1 Time now integrates the real hourglass parser/formatter. UTC/generalized
formats, missing-seconds fallback, the **2050** UTC year boundary, at most three
fraction digits, scaled milliseconds and the permissive manual timezone suffix
are retained. That suffix is attached to the parsed date/time without a second
UTC conversion. Invalid sign-prefixed suffix lengths become Nothing; exactly
four suffix characters use the leading digit prefix, as upstream. Output uses
generic hourglass date normalization and intentionally retains the original
omission of fractional seconds for both time types.

Real conversion preserves the original base/scale/header handling, including
the extended exponent-length byte also participating in exponent decoding.
The required GHC 9.12.2 integerEncodeDouble paths are translated independently
of Java BigInteger.toDouble: small signed Int intermediate rounding and its
minBound abs anomaly, GMP truncation and wrapped C-int exponent, and Native
backend limb accumulation. The complete original Integer/BigNat/GMP/Native/C
wrapper source files and ghc-bignum **1.3** full notice are retained in a new
source subset, with fixed hashes; no claim is made to port all ghc-bignum.
Both backends are checked against actual compiled GHC functions. There is no
production GMP/Haskell conversion dependency.

The original AArch64 and x86_64 builds produce **positive** and **negative**
quiet NaN respectively for 0/0. All **37 affected observations** retain both
raw bit patterns, with explicit source profiles; no sign normalization hides
the difference. Kotlin exercises both profiles and offers an explicit profile
parameter; its default selects the matching current architecture profile.
Every other observation and all generated inputs agree across the two builds.

Encoded lengths and event streams can be demanded independently. Length
computation retains suffix-before-current error priority. DER output supports
lazy byte chunks/prefixes. Finite construction handling uses explicit stacks
for nesting, child lengths and event traversal; top-level End differs between
Prim.encodeList (skipped) and DER.encodeToRaw (primitive error), and mismatched
or absent closing marker kinds retain the source behavior. Corpus cases include
**1,000-level constructions**, incomplete structures, failing later values and
zero/prefix demand. Finer Event/header-subfield demand and larger composition/
resource stress coverage remain work; these checks are not an exhaustive
proof of every possible Haskell thunk observation.

The reference directly compiles **16 unchanged ASN1 modules**, **12 unchanged
hourglass modules**, a small request bridge, and an original-test module
adapter. The unmodified **Tests.hs** is retained and separately compiled as its
own executable. The adapter only normalizes line endings and inserts an
explicit module header exposing the existing generators/properties; the
importer verifies that exact transformation. No test body, generator,
expectation or conversion algorithm is rewritten in the reference.

All **five original crypton-asn1-encoding tests** (Header, Event, DER, Real,
Integral Real) pass **1,000 QuickCheck cases each** on macOS/AArch64 and
Linux/x86_64, with replay seed **1095978545**. The corpus also imports **1,000
actual original generator inputs per property** and invokes each corresponding
original property for its expected output. Linux independently regenerates
and exactly matches all **5,000 inputs**, then verifies their outputs. The
original DER property takes [ASN1]; the unused ASN1s generator is not presented
as proof that the original property covers nesting. Additional independent
construction cases provide that evidence separately.

The final corpus has **38,651 observations**, **214 source hashes**, and
semantic SHA-256
**df83f342ef1ddddb151049d7f3022d3b1e2f2b4c8d9b2c5f4216a336b8c65d0d**:
**33,417 pure/profile**, **5,010 actual native/property**, **112 controlled
POSIX** and **112 controlled Windows** cases. Each source build checks all
**38,614 observations applicable to its profile**; the extra 37 preserve the
other architecture's NaN bits. A complementary ownership/lazy-error check
brings the Kotlin class to **38,652 cases**. It includes eager raw Left versus
lazy typed failure, independent encoded components, input mutation and
returned-byte ownership.

macOS/AArch64 JDK 21 passes **256,801 selected HTTP/environment/encoding/ASN1/
hourglass cases** in an 85s build. All **36 XML suites** have matching declared
and actual counts: **256,802 cases**, zero failures/errors and only the actual
Windows Registry skip. All **165 source/fixture verifiers** pass (55.6s).
Linux/x86_64 JDK 21 independently passes the same **256,801 tests** (79.306s
JUnit), with no failed or aborted tests/containers and the one Registry skip.
CI requires **138,201 Windows cases** with no skips; execution awaits this
checkpoint's push.

Core/installed JAR identity is verified. All **3,101 preexisting classes** and
every preceding JAR entry remain byte-identical. **20 new classes** bring the
total to **3,121**; the new complete ghc-bignum notice brings the total to **88**.
All **343 HTTP classes**, **22 TLS error classes**, **250 Pandoc assets**, **six
native ABI assets**, CodePage, Windows error mapping and **60,137-byte PSL**
remain exact, as do all CLI/dependency JAR bytes.

Pretty, remaining hourglass Read/numeric/Enum/Integral/Compat APIs and property
coverage, finer stream/subfield demand and broader stress cases, X509 objects,
stores/validation/signatures, TLS and resource-I/O integration remain. Full
Pandoc/all tests, calibre MOBI/all tests and additional formats absent from
Pandoc/all tests remain the unchanged required end state. This checkpoint
completes the five named encoding properties at the recorded replay counts,
not that whole end state.

### ASN1 Pretty and independent event/header demand

`Asn1Pretty` ports the pinned `Data.ASN1.Pretty` implementation, including both
`PrettyType` constructors and their Eq/Show behavior. Multiline changes only
indentation and emits **no newlines**; SingleLine includes a comma after the
last item. The implementation preserves each constructor's exact prefix and
spacing, hexadecimal integers, quoted lowercase base16 output, raw ByteString
escapes for the appropriate string encodings, Double display and raw DateTime/
Maybe Timezone display. Negative integers fail with GHC 9.12.2's actual
`GHC.Internal.Numeric.showIntAtBase` message. Negative indentation and Int64
wrap are preserved. A code-point stream allows bounded output before a failing
later field or input tail, without allocating enormous initial indentation.

The event encoding correction distinguishes **list spine**, **event
constructor** and **Header payload** demand. `Asn1Encoded.lazyEvents` exposes
independent lazy event values; `events` continues to expose ordinary events.
A Header event can be inspected before its payload, whereas Primitive's bytes
are strict when the event is evaluated. The original ASN1Header's four fields
remain strict. Length calculation still evaluates later siblings before the
current one, as in the original; event traversal preserves prefix demand.

The new development-only reference compiles **17 unchanged ASN1 modules**,
**12 unchanged hourglass modules** and **seven unchanged base16 modules**.
Pretty and Prim execute directly from the pinned original sources. The small
bridge supplies JSON adapters and observes code points, errors, modes and
encoded component demand; it contains no substitute formatting/encoding
algorithm. Base16 1.0's entire **21-file** release is retained byte-for-byte,
including its tests and complete notice, with archive SHA-256
**86366364910b78609677817cf3f987bf1690e1f75bba04bc91a5ed993d619cde**.
Its historical Cabal bounds are not edited: the observer compiles its modules
directly with the frozen modern dependencies and selects Pretty's original
>=1.0 CPP branch. The unchanged GHC Numeric source and full GHC notice are also
retained and hash-checked. Separate base16 decoding APIs and original base16
tests are **not** claimed ported by this checkpoint. The crypton-asn1-types
release has no original test suite; its encoding sibling's five tests remain
covered by the preceding checkpoint.

The new corpus has **20,160 observations**, **94 source hashes**, and semantic
SHA-256 **e14f903d4f00f01190ada20e8856fbf3dc8cbe13de627cdb73707953d0740cd4**. The exact
same expected outputs were independently verified with GHC 9.12.2 on
macOS/AArch64 and Linux/x86_64. Cases cover all constructors, all 256 byte
values for all 12 character string encodings, varied byte lengths through
513, floating point boundaries/random bit patterns, raw time fields and
extreme timezone offsets, arbitrary unbalanced marker streams, mode Eq/Show,
negative/overflowing indentation, bounded output before failing DateTime
fields/input tails, and encoded list/event/header demand around invalid OIDs
and structural values. Zero-demand bridge expressions are themselves delayed,
matching Haskell's unevaluated pair rather than forcing a Kotlin function call
before `take(0)`. One ownership/cache check brings Asn1PrettyTest to **20,161**.

macOS/AArch64 JDK 21 passes **276,962 selected HTTP/environment/encoding/ASN1/
hourglass tests** in an 88s build. All **37 XML suites** match their actual
case counts: **276,963 tests**, zero failures/errors and only the actual Windows
Registry skip. Linux/x86_64 JDK 21 independently passes the same **276,962**
tests in **84.131s**, with no failed/aborted tests or containers and the same
one Registry skip. All **166 source/fixture verifiers** pass (74.5s). Windows
CI now requires
**158,362 cases**, including the new Pretty class, with no skips. The preceding
97cf362 Windows job already passed its **138,201** cases; its broader Linux
CI run remains live rather than being restarted.

Core/installed JAR identity and **89 complete notices** are verified. Against
97cf362, **3,099 classes are byte-identical**; 18 additional class changes are
only debug/source-position differences, verified with identical `javap -p -c`
output. Only Asn1Encoded and Event.Header change executable behavior. Two
private old event coroutine classes are replaced and 12 classes are added,
for **3,131 packaged classes**. All **343 HTTP classes**, **22 TLS error
classes**, **250 Pandoc assets**, **six native ABI assets**, CodePage, Windows
error mapping, **60,137-byte PSL** and CLI/dependency JAR bytes remain exact.
A Java client compiled against 97cf362 runs unchanged against the new JAR,
covering Header construction, getter, component1, copy, copy$default, equality,
hash and display. Header is now a manually implemented class with lazy storage;
its former Kotlin-reflection `isData` property is not preserved. Ordinary JVM
method descriptors are preserved.

Finer nested field/list-thunk observations and broader stress/resource checks,
remaining hourglass Read/numeric/Enum/Integral/Compat APIs and property coverage,
X509 objects/stores/validation/signatures, TLS and resource-I/O integration
remain work. Full Pandoc/all tests, calibre MOBI/all tests and other formats
absent from Pandoc/all tests remain the unchanged required end state.

### X509 algorithm identifiers and original signature test

`X509AlgorithmIdentifier.kt` ports the pinned
`Data.X509.AlgorithmIdentifier` module: all **seven HashALG values**, all **nine
named PubKeyALG values plus Unknown**, all **three SignatureALG constructors**,
the derived Eq/Show behavior, public-key OIDable instance, and SignatureALG's
ASN1Object instance. The original **21-entry** signature OID table is preserved,
including the four PSS hash OIDs, SHA1/224/256 DSA and EC mappings, and EdDSA.
Unknown identifiers preserve arbitrary Integer arcs. Unsupported signature/key/
hash combinations fail with the source message instead of inventing an OID.

Both original PSS input patterns are retained: absent inner NULLs or NULLs in
both hash AlgorithmIdentifiers. Outer wrapper kinds/tags and their End kinds
are independent wildcards; the second hash OID and integer salt payload are
ignored. The MGF1 OID and sequence markers remain required. The first hash OID
is resolved by the original signature table, including unknown/intrinsic cases;
this is parsing behavior, not additional cryptographic validation. Ed25519 and
Ed448 reject an explicit NULL parameter but accept an absent one. The parser
preserves the unconsumed input, including lazy payloads in ignored fields.

Output preserves PSS's nested wrappers, MGF1 identifier and supported hash salt
lengths (28/32/48/64). Intrinsic algorithms omit NULL, while other/unknown
algorithms include it. `toAsn1Stream` supports bounded output and a lazy tail;
ordinary `Asn1Object` builder composition remains available. ASN1 Oid and IntVal
now offer cached deferred payloads: output constructors/list length can be
observed before an unsupported OID or salt value is demanded. Existing
constructors/getters, IntVal component1/copy/copy$default, equality, hash and
display remain callable. IntVal is now manually implemented, so its former
Kotlin-reflection isData flag is not preserved; ordinary JVM method descriptors
are preserved, as was previously recorded for Event.Header.

The development-only reference directly compiles the **unchanged X509 module**,
**16 unchanged ASN1 modules** and **12 unchanged hourglass modules**. Its test
adapter contains the exact original HashALG/PubKeyALG/SignatureALG Arbitrary
instances and generic `property_unmarshall_marshall_id` body from the retained
crypton-x509 Tests.hs; the importer verifies that extraction. No codec,
generator, test body or expectation algorithm is rewritten in the observer.
The original named **signature alg** property passes **1,000 QuickCheck cases**
on both macOS/AArch64 and Linux/x86_64 (replay seed **1095978545**). The corpus
also records **1,000 actual original generator inputs** and invokes that
original property for their expected output. Linux independently reproduces
all 1,000 inputs and their outputs. This is the signature test, not the
upstream public/private key, extension, certificate, CRL or redaction tests;
those remain required work.

There are **44,471 independent observations**, **89 source hashes**, and
semantic SHA-256
**59e769ac76bf6ab0202b57c5e5fd711b68076fa08a79b7b18c20ba55574da549**.
Both original GHC builds verify all observations. Coverage includes every
algorithm constructor and Eq pairing, all table mappings, unsupported
combinations and unknown OIDs, output prefix/list-constructor/individual-field
demand, failing difference-list tails, DER byte prefixes, simple absent/NULL/
invalid parameters, every PSS token position mutated and every truncation,
arbitrary wrapper combinations and unmatched ends, ignored versus demanded
failing fields, remaining input, and malformed token streams. An ownership/
cache check brings X509AlgorithmTest to **44,472 tests**.

macOS/AArch64 JDK 21 passes **321,434 selected HTTP/environment/encoding/ASN1/
X509/hourglass tests** in a 153s build. All **38 XML suites** match their actual
counts: **321,435 tests**, zero failures/errors and only the actual Windows
Registry skip. Linux/x86_64 JDK 21 independently passes the same **321,434**
tests in **80.865s**, with no failed/aborted tests or containers and the same
one Registry skip. All **167 source/fixture verifiers** pass (114.6s).
Windows CI now requires **202,834 tests** with no skips. The preceding
97cf362 CI completed successfully on both Linux and Windows; 6d8c63e's Windows
job passed **158,362 tests**, while its full Linux CI remains live.

Core/installed JAR identity is verified, with **89 complete notices** and
**3,153 classes**. Against 6d8c63e, **3,117 classes are byte-identical**, 12
additional class changes have identical `javap -p -c` output, IntVal/Oid change
storage behavior and the Kotlin module metadata gains the new package. No old
class is removed; 22 classes are added. A Java client compiled against 6d8c63e
runs unchanged against the new JAR, including IntVal copy$default and Oid input/
returned-list ownership. All **343 HTTP classes**, **22 TLS error classes**,
**250 Pandoc assets**, **six native ABI assets**, CodePage, Windows error
mapping, **60,137-byte PSL**, and CLI/dependency JAR bytes remain exact.

This checkpoint supplies algorithm identifiers, not key material or signature
verification. Distinguished names, public/private key objects, extensions,
certificates/CRLs/signed objects, stores and validation, remaining original
X509 tests, finer thunk/resource/streaming coverage, hourglass remaining APIs,
and TLS/resource-I/O integration remain work. Full Pandoc/all tests, calibre
MOBI/all tests and additional formats absent from Pandoc/all tests remain the
unchanged required end state.

### X509 distinguished names, inner codec and deferred parser errors

`X509DistinguishedName.kt` ports the pinned
`Data.X509.DistinguishedName` module's DN and Inner values, five DnElement
constructors and their OIDable mappings, first-match getDnElement lookup,
derived DN Eq/Ord/Show and Inner Eq/Show, append/empty Monoid behavior, and
both ASN1Object instances. Attributes retain input order and duplicate OIDs.
Ordering compares OID Integer lists, then character encoding and unsigned raw
bytes; lookup does not decode/normalize the string. As in the source, a
multi-attribute SET is flattened and writing emits one SET per attribute.

The parser composes the already ported original ParseASN1 combinators. It
retains getConstructedEnd's permissive End handling, including accepted missing
closing markers and unmatched closing kinds. The original Inner FIXME is
preserved: without an outer Sequence, getMany consumes all input and a trailing
non-SET is an error rather than preserved remainder. The ordinary DN codec
preserves the input following its outer container. The original private
Internal.asn1Container helper is available with finite-list and streaming
forms; this does not claim the entire Internal ExceptT alias layer is ported.

Parser errors now retain an independently deferred message. Returning a Left
constructor, including through nested onNextContainer/onNextContainerMaybe,
does not prematurely format a malformed value in its error text. The remaining
state error from run also defers its text. Existing Failure constructors,
getter, component1, copy/copy$default, equality, hash and ordinary display
remain callable. Failure is now manually implemented for lazy storage; its
former Kotlin-reflection isData flag is not preserved, as previously recorded
for Header and IntVal. Nested string-prefix/infinite-list and other finer thunk
observations remain separate work.

The development-only reference directly compiles **two unchanged X509
modules**, **17 unchanged ASN1 modules** (including Parse) and **12 unchanged
hourglass modules**. OriginalDnTests is an exact checked extraction of the
original arbitraryBS/string/DN generators and the generic
property_unmarshall_marshall_id body from crypton-x509 Tests.hs, with only its
module header/imports adapted. Upstream has no separately named DN test; this
checkpoint instantiates that original generic property for DN, whose generator
is used by upstream certificate/CRL generation. It does not claim those
certificate/CRL tests are complete. The original generator/property passes
**1,000 QuickCheck cases** on both macOS/AArch64 and Linux/x86_64 with replay
seed **1145978545**. The corpus also invokes that original property on **1,000
actual original generated inputs**, independently regenerated exactly on
Linux. Arbitrary OIDs include empty/invalid DER encodings: the original DN
property is about typed ASN1 and does not require DER validity. Those property
results are preserved separately from binary encoding observations.

There are **28,425 independent observations**, **97 source hashes**, and
semantic SHA-256
**7122cbbb48148793362d7a9cf1434e07f53c15fb8cbc8493899935ce56d7c268**.
Both GHC builds verify all observations. Coverage includes all 12 character
encodings and every byte, DER output, all DnElement mappings, derived comparisons
and display, append/empty, first duplicate lookup, deferred later OIDs, flat
multi-valued SETs, ordinary versus Inner tail consumption, every truncation,
every token-position mutation across ASN1 constructors, malformed streams,
missing/mismatched ends, Left tag versus message/payload demand, bounded output,
failing input tails and the container helper. Ownership/cache/error checks
bring X509DistinguishedNameTest to **28,426 tests**.

macOS/AArch64 JDK 21 passes **349,860 selected HTTP/environment/encoding/ASN1/
X509/hourglass tests** in a 123s build. All **39 XML suites** match actual case
counts: **349,861 tests**, zero failures/errors and only the actual Windows
Registry skip. Linux/x86_64 JDK 21 independently passes the same **349,860**
tests in **80.730s**, with no failed/aborted tests or containers and the same
one Registry skip. All **168 source/fixture verifiers** pass (63.4s). Windows
CI now requires **231,260 cases**, including the new DN class, without skips.
The preceding 60991c5 Windows job passed its **202,834** cases; that checkpoint's
full Linux CI remains live rather than being restarted.

Core/installed JAR identity and **89 complete notices** are verified. Against
60991c5, **3,143 classes are byte-identical**; seven other changed classes have
identical `javap -p -c` output. Only Failure, Parser and its companion change
executable behavior, and Kotlin module metadata gains the new file. No old
class is removed; **16 classes** are added for a total of **3,169**. A Java
client compiled against 60991c5 runs unchanged against the new JAR, covering
both Failure constructors, getter/component1/copy/copy$default/equality/hash/
display. All **343 HTTP classes**, **22 TLS error classes**, **250 Pandoc
assets**, **six native ABI assets**, CodePage, Windows error mapping,
**60,137-byte PSL**, and CLI/dependency JAR bytes remain exact.

Key objects and cryptographic operations, extensions, certificates/CRLs/signed
objects, stores/validation, remaining original X509 tests, broader stress and
finer thunk/resource/streaming coverage, remaining hourglass APIs, and TLS/
resource-I/O integration remain work. Full Pandoc/all tests, calibre MOBI/all
tests and additional formats absent from Pandoc/all tests remain the unchanged
required end state.

### Raw X509 extensions and original Extensions test

`X509ExtensionRaw.kt` ports the pinned `Data.X509.ExtensionRaw` record/container,
derived Eq/Show, both ASN1Object instances, tryExtRawASN1 and the deprecated
exception-raising extRawASN1 behavior. OID, critical and content fields are
owned and independently deferred. The writer omits False and emits Boolean
True for critical extensions; the reader accepts omitted, explicit False and
explicit True. Its scalar End must be Sequence, and its format error displays
only the suffix after an already matched Sequence/OID prefix. Returned input
remainders and deferred error messages retain the original behavior.

Extensions preserves **Nothing versus Just []**, order and duplicates. Nothing
writes no container; Just [] writes an empty Sequence. The optional-container
parser leaves non-Sequence input unconsumed, retains getConstructedEnd's outer
End permissiveness, and rejects malformed scalar records inside the container.
Its Maybe constructor and list payload are separately demandable. The general
onNextContainerMaybe success branch now preserves a deferred Some payload
instead of forcing the nested parser's value while wrapping it.

Nested content is decoded with **BER**. A raw framing error yields Left with
the source OID/error prefix; a successful raw parse yields Right with a lazy
list that may fail later on primitive decoding. Critical is ignored by this
operation; OID is demanded only when formatting a raw error. The deprecated
helper throws on raw Left and preserves zero/prefix demand of its lazy output.
OctetString gains owned cached deferred bytes, allowing constructor/prefix
observations before content is demanded without changing its ordinary public
constructor/getter/equality/hash/display contracts.

The development-only reference compiles **four unchanged X509 modules**,
**17 unchanged ASN1 modules** and **12 unchanged hourglass modules**. The full
original Ext module is used by the original Extensions generator to encode
key-usage data; it is not replaced by a test stub. OriginalExtensionsTests is
an exact checked extraction of the original Extensions/ExtKeyUsageFlag/
ExtKeyUsage generators and generic round-trip property body, with only module
header/imports adapted. It invokes the same `Extensions -> Bool` property as
the original named **extensions** test. It passes **1,000 QuickCheck cases** on
macOS/AArch64 and Linux/x86_64 (replay seed **1245978545**). The corpus also
records **1,000 actual original generated inputs**, runs the original property
on them, and independently regenerates/matches all inputs on Linux. This does
not claim that Kotlin's typed extension codecs or their separate original
properties have already been ported.

The corpus contains **27,419 independent observations**, **97 source hashes**,
and semantic SHA-256
**3f31c920a2da3b0acb5b6da6031278f25d8102c7b7369a76925e0efc4162784c**.
Both original GHC builds verify every observation. Cases include all byte
values, raw Eq/Show and field order, absent/empty/present containers, omitted
False/explicit True, DER output, every scalar/container position mutation and
truncation, malformed streams, suffix errors, preserved remainder, outer versus
scalar End rules, Maybe/list/field/constructor/prefix demand, failing tails,
BER framing Left versus typed exceptions, and the deprecated helper. BER values
are observed through the original ASN1 Show; previous typed-ASN1 fixtures retain
separate IEEE bit-profile observations. The observer also captures failures
that arise while forcing an exception's own lazy message (e.g. an undefined
OID), without rewriting upstream error construction. One ownership/cache/Maybe
payload check brings X509ExtensionRawTest to **27,420 tests**.

macOS/AArch64 JDK 21 passes **377,280 selected HTTP/environment/encoding/ASN1/
X509/hourglass tests** in a 125s build. All **40 XML suites** match their actual
counts: **377,281 tests**, zero failures/errors and only the actual Windows
Registry skip. Linux/x86_64 JDK 21 independently passes the same **377,280**
tests in **80.550s**, with no failed/aborted tests or containers and the same
one Registry skip. All **169 source/fixture verifiers** pass (85.8s). Windows CI now
requires **258,680 cases** with no skips. The preceding 055d995 Windows job
passed **231,260 tests**; its full Linux CI remains live.

Core/installed JAR identity and **89 complete notices** are verified. Against
055d995, **3,155 classes are byte-identical**, 12 other class changes have
identical `javap -p -c` output, and only OctetString and Parser.Companion change
executable behavior. No old class is removed; **10 classes** are added for
**3,179 total classes**. Every old public JVM signature/descriptor on the two
changed API classes is retained. A Java OctetString client compiled against
055d995 runs unchanged, including defensive input/output ownership, equality,
hash and display. All **343 HTTP classes**, **22 TLS error classes**, **250
Pandoc assets**, **six native ABI assets**, CodePage, Windows error mapping,
**60,137-byte PSL**, and CLI/dependency JAR bytes remain exact.

Typed extension codecs/accessors and their remaining original tests, key
objects/cryptographic operations, certificates/CRLs/signed objects, stores and
validation, broader stress and finer thunk/resource/streaming coverage,
remaining hourglass APIs, and TLS/resource-I/O integration remain work. Full
Pandoc/all tests, calibre MOBI/all tests and additional formats absent from
Pandoc/all tests remain the unchanged required end state.

### Typed X509 extensions, accessors and original key-usage properties

`X509ExtensionModels.kt`, `X509ExtensionCodecs.kt`,
`X509ExtensionSupport.kt` and `X509GeneralNames.kt` port the pinned
`Data.X509.Ext` models, all nine extension instances, recognized OIDs and
extensionEncode/Decode/Get/GetE. Models retain source Eq/Show and the declared
Ord/Enum behavior, including descending, empty and zero-step enumeration
prefixes. Owned input/output arrays and lists prevent caller mutation.

BasicConstraints retains its exact accepted shapes and negative-path rejection;
KeyUsage retains fixed nine-bit encoding, duplicate/order behavior and deferred
out-of-enum flags; ExtendedKeyUsage retains known and unknown OIDs and its
exception for non-OID content. Subject/Authority key identifiers keep their
different wrappers. GeneralNames supports exactly the original name variants,
byte-truncating Char8 names, nested DER distinguished names and composed
XMPP/DNSSRV character strings. Unsupported names/encodings and malformed
containers retain source errors, including deferred error-message payloads.

The source's DNSSRV encoder emits the **XMPP OID ending in 5**, whereas its
DNSSRV decoder recognizes the OID ending in 7; the port preserves the resulting
round-trip change. NameConstraints preserves the source's extra encoder
Sequence wrapper, its asymmetric decoder, implicit subtree groups, optional
DER-encoded signed/unbounded distances, permissive Context End tags, last-group
replacement and ignored trailing input. It does not normalize these behaviors
into a different implementation. The source explicitly leaves CRL Distribution
Points encode/decode **unimplemented**; its models/metadata and the same errors
are retained. Netscape Comment bypasses ASN1 for bytes and throws for the ASN1
methods, as upstream does.

Raw decoding checks the OID before demanding content, ignores critical, and
retains the separate Maybe/Either/payload layers. GetE returns the first
matching OID even when decoding fails; Get skips Left results and returns the
first success. The new memoized sequence parser preserves constructor, list
spine, element and prefix demands without materializing an unused tail. IP
payloads, Other bytes, BitString values and CharacterString bytes are cached and
owned independently. Existing BitString constructor/getter/component1/copy/
copy$default/equality/hash/display JVM contracts are retained. Its Kotlin
reflection metadata now reports a regular class rather than a data class;
this is an explicit API metadata change, not a claim of reflection identity.
The former synthetic CharacterString private-field accessor is replaced by the
compiler's private-getter accessor; ordinary public constructors/getters remain.

The development observer compiles the unchanged pinned X509/ASN1/hourglass
modules. Its adapter is an exact checked extraction of the original
ExtKeyUsageFlag/ExtKeyUsage/ExtKeyUsagePurpose/ExtExtendedKeyUsage generators and
the generic property_extension_id body. Both original named key-usage
properties pass **1,000 QuickCheck cases each**, with replay seeds
**1345978545** and **1445978545**. The fixture corpus retains and independently
regenerates those **2,000 actual original generated inputs**. Kotlin evaluates
the same extDecode(extEncode(value)) property, in addition to recording original
DER bytes and encoded ASN1 values. All **512 key-usage subsets** are also tested.

There are **44,136 original observations**, **97 source hashes**, and semantic
SHA-256
**6772a04721fc9a83796986f7d3414e3c9f56a6ebd46e92e26d520b158b2bb2d7**.
Generation and full independent oracle replay pass on macOS/AArch64. Coverage
includes all byte values, pairwise models, every enum range, encoder prefixes,
every-position constructor mutations/truncations, malformed BER/DER, nested DN,
composed-name encodings, malformed bit storage, out-of-enum set bits, raw-field
and accessor error priority, and constructor/spine/payload demands. The JSON
adapter preserves Aeson's list-spine-before-element forcing order, separately
from the production sequence API. Extreme Word64 lengths are observed only
where bounded demands avoid unbounded zero-bit scans; this is not resource or
termination stress evidence. Independent Linux oracle replay remains work.
One ownership/cache check brings **X509TypedExtensionTest to 44,137 tests**.

The macOS/AArch64 JDK 21 regression run passes **237,606 tests** across all nine
ASN1/X509 suites, with actual XML testcase counts checked and zero failures,
errors or skips. This includes the eight preceding suites as well as the new
suite. The installed core JAR is byte-identical to the standalone core JAR;
all **89 notices** are included. Against eec419a, no old class is removed,
**3,161 classes** are byte-identical and **82 classes** are added for **3,261**
total classes. All **560 existing non-class/non-module-metadata assets** remain
exact. A Java client compiled against eec419a executes unchanged against both
JARs, checking the changed classes' constructors/getters, BitString destructuring
and copy/default methods, equality/hash/display and defensive ownership.
Windows CI now requires **302,817 tests** with no skips, including the new typed
suite; Linux CI also runs the new source verifier and suite. The preceding
eec419a and 055d995 Linux/Windows CI runs are confirmed successful.

Keys/cryptographic operations, certificates/CRLs/signed objects, stores and
validation, remaining original X509 tests, independent other-platform typed
oracle replay, broader stress and finer thunk/resource/streaming coverage,
remaining hourglass APIs and TLS/resource-I/O integration remain work. Full
Pandoc/all tests, calibre MOBI/all tests and additional formats absent from
Pandoc/all tests remain the unchanged required end state.

### X509 public keys, curve OID tables and original pubkey property

`X509PublicKeys.kt` and `X509PublicKeyCodec.kt` port the pinned
`Data.X509.PublicKey` models, Eq/Show, pubkeyToAlg and ASN1Object instance.
RSA/DSA public records retain the crypton 2.0.1 field order, including DSA's
p/g/q record order versus p/q/g wire order. All nine PubKey constructors are
represented, including DH, named/explicit-prime EC, all four fixed byte-key
families and unknown OID/payload records. SerializedPoint and all byte fields
own their input/output arrays; deferred numeric/point/payload fields are cached.
All 33 original curve names and OID pairs retain order. Generic OID table
lookups preserve first-match duplicates, independent fields and a separately
observable Maybe payload, including unused search targets for an empty table.

The writer retains the original RSA NULL parameter, absent curve/Ed parameters,
named-EC curve OID, nested RSA/DSA DER and byte-key BIT STRING wrappers, with
independently demanded output nodes/fields and caller tails. DH and explicit
EC Prime encoding retain the source's **unimplemented** errors. Unknown keys
can be encoded but are rejected by the source reader; the port preserves that
asymmetry. No key validity or point checks are invented where the source only
checks byte length. X25519/Ed25519 require 32 bytes, X448 56 and Ed448 57,
independent of the declared BitArray length.

RSA parsing retains optional NULL, BER nested decoding, the alternate nested
RSA production, its exact version/OID errors and discarded nested remainder.
Ordinary RSA inner remainder is prepended to the outer remainder. The source's
negative-modulus repair reinterprets the minimum signed two's-complement bytes
as unsigned; public_size is recomputed, including **zero bytes for modulus 0**
on the pinned integer-gmp backend. DSA accepts exactly one nested IntVal.
Named EC accepts all original curve OIDs without validating the point bytes.
Explicit-prime EC retains the literal version/field OID shape, unsigned a/b/seed
conversion, signed integer parameters and ignored bit lengths. Unknown curves,
unknown key algorithms, structural errors, raw BER Left and deferred primitive
exceptions preserve the original message and demand order.

The parsed key and remainder have independent cached views. Both the sequence
API and the existing ASN1Object List API retain a lazy nested BER remainder:
reading the key, an empty prefix or a known prefix does not decode later invalid
primitives. `Asn1BinaryEncoding.encodeDerSequenceChunks`/`encodeDerSequence`
encode one top-level element before traversing its suffix. A constructed root
still needs its contained structure to compute DER length; a later failing tail
cannot replace an earlier root's payload/OID error. Existing finite List APIs
are retained. This extends the binary encoder rather than changing recorded
original error expectations to accommodate eager list materialization.

The development observer compiles unchanged X509/ASN1/hourglass modules and
the complete unchanged **crypton 2.0.1** package. Its original pubkey generator
uses actual crypton toPublic for X25519/X448/Ed25519/Ed448, with the exact
original secret-key/ByteString generators. OriginalPublicKeyTests is checked
against the original source extraction, including the unchanged generic
property_unmarshall_marshall_id body. The original named **pubkey** property
passes **1,000 QuickCheck cases** at replay seed **1545978545**. The corpus
retains **1,000 actual original generated inputs**, independently regenerated
on replay; Kotlin evaluates that same ASN1 round-trip property and matches
original encoded values and DER bytes.

There are **40,433 independent observations**, **537 source hashes** and
semantic SHA-256
**76659c4fa894bf2c7c88fce0e52d2f670bd43aa5526f122fa73c4c7b41da318b**.
Both generation and full replay pass on macOS/AArch64. Cases cover every byte
value in all four fixed key families, all curves, RSA signed/size boundaries,
DSA parameters, pairwise key Eq/Show, explicit EC, unsupported writers,
constructor/field/tail prefixes, every-position mutations/truncations, nested
BER/error/remainder ordering, raw payload thunks, fixed-key lengths versus bit
lengths and generic OID lookup demand. An ownership/cache/lazy-remainder check
brings **X509PublicKeyTest to 40,434 tests**. This is not independent Linux
oracle, unbounded recursion, resource stress or cryptographic-operation evidence.

The macOS/AArch64 JDK 21 run passes **278,040 tests** across all ten ASN1/X509
suites, with actual XML testcase counts checked and zero failures/errors/skips.
All **171 source/fixture verifiers** pass (143.8s). The standalone and installed
core JARs are byte-identical and contain **90 full notices**. All **560 pre-existing non-class/non-module-metadata assets** from
the eec419a baseline remain byte-exact; the new crypton notice is added.
The core now contains **3,296 classes**. Windows CI requires **343,251 tests**
without skips, including the public-key suite; Linux CI runs the new verifier
and suite. The preceding f105a4f Windows job has passed; its full Linux job was
confirmed still executing the complete Gradle test/install step.

Private keys and their original tests, cryptographic operations, certificates/
CRLs/signed objects, stores and validation, independent other-platform oracle
replay, broader stress and finer thunk/resource/streaming coverage, remaining
hourglass APIs and TLS/resource-I/O integration remain work. Full Pandoc/all
tests, calibre MOBI/all tests and additional formats absent from Pandoc/all tests
remain the unchanged required end state.

### X509 private-key codecs and both original private-key properties

`X509PrivateKeys.kt` and `X509PrivateKeyCodec.kt` add all seven private-key
variants from pinned `Data.X509.PrivateKey`: RSA, DSA, named/explicit-prime EC,
X25519/X448 and Ed25519/Ed448. They retain Eq, privkeyToAlg, ordinary redacted
Show and separately invoked debugShow, including the exact RSA/DSA record
fields, EC metadata and hexadecimal byte-secret debug rendering. Ordinary Show
and algorithm lookup do not demand hidden secret fields. Arrays are owned and
copied on output; numeric/byte payloads are cached independently. Byte equality
scans all equal-length bytes; a Cleaner overwrites the owned backing array when
its owner is collected. This does not establish equivalence to native
ScrubbedBytes pinning, zeroization or all copies made by a moving JVM GC/JIT.

The ASN1 writer emits traditional RSA and DSA, SEC1 named/prime EC and nested
OctetString PKCS8 for fixed byte keys. RSA size is not serialized. DSA writes
p/q/g and the calculated public value before the secret x; q is ignored by that
calculation. EC scalar/a/b/seed use the original defined nonnegative minimal
unsigned byte representation, including one zero byte for zero. Encoded nodes,
values, DER bytes and caller-tail prefixes remain independently demandable.

The reader retains ordered RSA/DSA/EC/new-curve alternatives: **Left tries the
next parser**, while a thrown primitive exception propagates. Consequently an
earlier detailed error can be replaced by the final newcurve error, as in the
source. Traditional RSA keeps signed n and computes at least one size byte,
including for zero/negative n, unlike the public-key reader's unsigned repair
and zero size. RSA/DSA PKCS8 accept their original precise wrappers, ignore
nested remainders and return the outer remainder. The DSA traditional public
IntVal is matched but its payload is ignored.

EC retains local-before-inherited curve parameters, skipped Nulls, explicit
prime fields and ignored optional public-key containers. Tag matching ignores
container class and matching End kind as upstream does. Outer EC PKCS8 returns
the suffix **after the OctetString, including its outer End Sequence**, instead
of normalizing that source remainder quirk. Fixed-byte PKCS8 accepts versions
0 and 1, skips optional attribute container/public primitive by tag regardless
of class and does not demand their payloads. It preserves size-versus-structure
secret-key errors and nested BER/inner-shape errors. Parsed key/remainder views
retain separate prefix demands in both sequence and ASN1Object List APIs.

The DSA writer currently uses JDK BigInteger modular arithmetic specialized to
the observed pinned GMP results, including signed moduli/exponents, zero
modulus and failed inverse returning zero. Its **timing does not reproduce
crypton's fixed-width constant-time C backend**; that backend port/verification
remains work. The source `i2osp` negative-input branch allocates bytes and returns
without initializing them. The Kotlin helper explicitly reports this as an
unsupported undefined-buffer path; no fabricated exact expectations or claim of
negative EC integer-byte parity is made. General cryptographic operations and
constant-time/memory guarantees remain outside this completed functional slice.

The observer compiles unchanged X509/ASN1/hourglass modules and local crypton
2.0.1, with exact checked extraction of the original private-key/supporting
generators, generic property_unmarshall_marshall_id and ECSecret/redaction
property. The original named **privkey** and **ec privkey is redacted** tests
both pass **1,000 QuickCheck cases**, at replay seeds **1645978545** and
**1745978545**. The corpus separately retains **1,000 actual original inputs
for each property** and regenerates all 2,000 on independent replay. Kotlin
executes both original predicates and matches original serialization/results.
The separate original certificate and CRL tests still remain pending.

There are **35,577 independent observations**, **537 source hashes** and
semantic SHA-256
**97c1f6ee1813ded36ba507c4db597d87ac2955d7e124e3eb4581c30a00aa0a34**.
Generation and full oracle replay pass on macOS/AArch64. Cases cover all byte
values in four secret families, all named curves, signed RSA/DSA records,
Show/debug demand, modular-power boundaries, nonnegative i2osp boundaries,
writer prefixes/failing tails, every-position mutations/truncations of all
traditional and PKCS8 forms, local/inherited EC parameters, skipped public
payloads, fixed-secret versions/lengths and optional-field class/End rules.
Nested wrapper bytes are produced by the unchanged original DER writer. The
fixture builder rejects failed DER-helper output before constructing an octet
payload, so JSON-adapter type errors are not substituted for key-codec tests.
An ownership/cache/redaction/lazy-remainder check brings **X509PrivateKeyTest
to 35,578 tests**. Independent other-platform oracle replay, unbounded recursion,
resource stress and full timing/memory equivalence remain unverified.

The macOS/AArch64 JDK 21 run passes **313,618 tests** across all eleven ASN1/X509
suites; actual XML testcase counts match, with no failures/errors/skips. Both
standalone and installed core JARs are byte-identical, with **3,320 classes**
and **90 full notices**. All **172 source/fixture verifiers** pass (161.9s). Windows CI now requires **378,829 tests** without
skips, including the new private-key suite. Linux CI includes the source
verifier and suite. The preceding e872dc5 Windows job has passed; its Linux job
was confirmed still executing the complete Gradle test/install step. A missing
Gradle classpath snapshot caused an initial build failure; a subsequent fresh
build regenerated it and the final regression run succeeded without deleting
the shared cache or changing dependency/toolchain versions.

Private-key timing/scrubbing and undefined-buffer boundaries above, broader
cryptographic operations, certificates/CRLs/signed objects, stores/validation,
remaining original X509 tests, independent other-platform oracle replay,
broader stress and finer thunk/resource/streaming coverage, remaining hourglass
APIs and TLS/resource-I/O integration remain work. Full Pandoc/all tests,
calibre MOBI/all tests and additional formats absent from Pandoc/all tests
remain the unchanged required end state.

### Certificate field stream and original certificate property

`X509Certificate.kt` ports the pinned public `Data.X509.Cert` record, Eq/Show,
ASN1Object reader/writer and validity/version/extension helpers. All eight
fields are independently cached/deferred. Validity preserves an ordered pair
of raw DateTime values. The source represents the unsigned certificate header:
**its ASN1Object writes the field stream without an outer Sequence**. No signed
certificate or signature validation is substituted for that source contract.

Encoding always emits explicit Context 0 version, even zero; decoding accepts
its absence as zero and wraps an arbitrary Integer to the pinned signed Int64.
Serial remains unbounded. Signature, issuer, subject, public key and raw
extension codecs are composed from the existing Kotlin translations. Validity
writes UTC before **2050-01-01 00:00:00** and Generalized at/after that exact
DateTime ordering boundary, including nanoseconds. It always writes a zero
timezone. Decoding requires exactly two ASN1Time constructors but ignores their
time kinds and timezone fields. Optional Context 3 extensions preserve
Nothing/Just-empty/nonempty, are not gated by version, and keep source permissive
container End matching. Issuer/subject unique identifiers are **not skipped** by
the original implementation: they and extensions following them remain in the
returned suffix. That behavior is preserved rather than inferred from the
source comment describing the RFC structure.

The memoized X509 input now exposes independent sequence/List remainder views.
Signature and raw-extension decoding share their existing algorithms through
input-based adapters, and certificate parsing reuses the general-name DN parser
and public-key decoder. A malformed DN/extension can yield Left without reading
a later failing structural tail. Formatting the Left now consumes its shown
values in order, rather than materializing the complete tail first. Two new
source-derived cases exposed/fixed that old shared formatter error priority;
all earlier ASN1/X509 fixture suites still pass.

ASN1 Time gains independently cached kind/DateTime/timezone payloads so emitting
its constructor or a writer prefix does not prematurely demand validity fields.
All old JVM signatures/descriptors for its constructor, getters, components,
copy/default, equals/hash and inherited display are retained, including the
nullable inline-timezone mangling. A Java client compiled against the eec419a
JAR runs unchanged against both old and new JARs and exercises those contracts.
**Kotlin reflection now reports Time as a regular class instead of a data
class**; reflection identity is not claimed. The new ownership/cache/Time/
remainder test separately verifies deferred fields and bounded suffix demand.

The development reference compiles unchanged `Data.X509.Cert`, its X509/ASN1/
hourglass dependencies and the complete unchanged crypton 2.0.1 package.
OriginalCertificateTests retains every original generator/property body before
main, with only checked module/import adaptation; retaining a body is not
counted as executing it. The original named **certificate** property passes
**1,000 QuickCheck cases** at replay seed **1845978545**. The corpus separately
retains **1,000 actual original generated Certificate inputs**, regenerates them
on replay and executes the same generic ASN1 round-trip predicate in Kotlin.
That original property works on ASN1 values. Arbitrary DN OIDs can pass it while
failing DER encoding; byte-encoding observations are separate requests, so a
DER failure does not hide whether the original property ran successfully.

There are **24,068 independent observations**, **537 source hashes** and
semantic SHA-256
**3151539918fe533656b209e3f18ae411f6fcad9d9877c9580231f632323ceda3**.
Generation and full independent replay pass on macOS/AArch64. Coverage includes
record/field Eq/Show and prefix demand, signed/wrapped/default versions,
2050/nanosecond/extreme-DateTime boundaries, every-position ASN1 mutations and
truncations, all represented public-key families and signature forms, optional
extensions, ignored time metadata, preserved unique-ID/outer suffixes and
lazy error-message versus failing-tail priority. One cache/Time/remainder check
brings **X509CertificateTest to 24,069 tests**. Independent other-platform oracle
replay and broader stress remain unverified.

The macOS/AArch64 JDK 21 regression run passes **337,687 tests** across all twelve
ASN1/X509 suites, with exact XML testcase counts and no failures/errors/skips.
Standalone and installed core JARs are byte-identical, contain **3,332 classes**
and **90 full notices**. All **173 source/fixture verifiers** pass (122.6s).
All **560 pre-existing non-class/non-module-metadata
assets** from eec419a remain byte-exact and no eec419a class is missing. Windows
CI now requires **402,898 tests** without skips and Linux CI includes the new
source verifier/suite. Both f105a4f and e872dc5 Linux/Windows CI runs are confirmed
successful; 115504a remains live at this checkpoint.

CRL and its original property, Signed/SignedExact/raw signed-byte preservation,
certificate/CRL signature and trust validation, stores, private-key timing/
scrubbing/undefined-buffer boundaries, general cryptographic operations,
independent other-platform oracle replay, broader stress and finer thunk/
resource/streaming coverage, remaining hourglass APIs and TLS/resource-I/O
integration remain work. Full Pandoc/all tests, calibre MOBI/all tests and
additional formats absent from Pandoc/all tests remain the unchanged required
end state.

## X509 CRL records, field streams and original suite (2026-10-16)

Ported unchanged `Data.X509.CRL` from crypton-x509 1.9.2: CRL and
RevokedCertificate records, source-order Eq/Show, ASN1 readers/writers and the
original named **crl** round-trip property. Production is Kotlin, using the
existing X509/ASN1/hourglass implementations; Haskell remains a development
oracle. This implements unsigned CRL fields, not a signed CRL or trust policy.

The CRL writer preserves the upstream bare field stream without an outer
Sequence. Version is an unbounded Integer, all dates use GeneralizedTime with
zero offset, next-update is optional, empty revoked lists omit their container,
and extensions use Context 0. An explicit empty revoked Sequence decodes to an
empty list and re-encodes without that container. Revoked records retain their
own Sequence and optional extension Sequence. Readers ignore input time
kind/zone, preserve suffixes, and retain the upstream permissive End handling
and exact structural/payload error order. Independent cached fields, Maybe
constructor/payload demand, list spine/element demand, immutable input-list
ownership and bounded remainder views are covered separately.

The reference compiles unchanged local X509/ASN1/hourglass modules and the full
crypton 2.0.1 package. OriginalCrlTests retains exact original generator/property
bodies before main with checked module/import adaptation. Its original CRL
property passes **1,000 cases** at replay seed **1945978545**. The corpus also
retains **1,000 actual original CRL generator inputs**, regenerates them during
independent replay, and executes the original round-trip predicate in Kotlin.
That predicate operates on ASN1 values; DER observations remain separate because
arbitrary generated DN OIDs may be invalid DER.

A separate `x509-original-tests` executable now compiles the **unchanged vendored
Tests/Tests.hs**, including its original main, directly. All **nine named
upstream properties pass 1,000 cases each** with `--quickcheck-tests 1000
--quickcheck-replay 1945978545` (9,000 cases total): pubkey, privkey, signature
alg, key-usage, extended-key-usage, extensions, certificate, crl and EC private
key redaction. This proves the upstream baseline runs together; Kotlin parity
is checked by the independent observation/property corpora, not inferred from
that upstream run. Other upstream packages' complete suites are not implied.

The CRL corpus contains **19,664 independent observations**, **537 source
hashes**, and semantic SHA-256
**01711b50db8830d0af3033c525045b9b18bafe55a5a11927dbb7313553690e5a**.
Generation and full independent replay pass on macOS/AArch64. Coverage includes
all writer prefixes, record/field Eq/Show, optional containers, extreme integer
and DateTime values, every-position mutations/truncations, generalized writer
dates, ignored input time metadata, malformed entries, parser remainders and
ordered deferred errors. A separate ownership/cache/spine check brings
**X509CrlTest to 19,665 tests**. Other-platform oracle replay remains pending.

All **357,353 tests across fourteen ASN1/X509 suites** pass on macOS/AArch64
JDK 21 with exact XML testcase counts and no failures/errors/skips. The standalone
and installed core JARs are byte-identical, contain **3,344 classes** and **90
full notices** (89 upstream notices plus kotdoc's license). All **560 baseline
non-class/non-module-metadata assets** remain byte-exact and no eec419a class is
missing. The Windows gate now requires **422,564 tests** with the CRL/native-time suites;
Linux includes its source verifier. The earlier 115504a CI run is now confirmed
successful on both platforms. All **175 source/fixture verifiers** pass
(125.2s), including the POSIX environment fixture. The cb91954 Linux job remains live; its Windows job reports 135
certificate DER observation failures with GhcIOException. These exposed a
reference-environment mismatch, described below; the corrected Windows run
must still pass before cross-platform verification can be claimed.

Certificate/CRL DER fixtures had implicitly used the macOS POSIX calendar on all
hosts. The pinned original actually normalizes dates through native conversion:
2050-02-29 25:-1:61 becomes 2050-03-02 01:00:01. Its concrete DateTime rewrite
rules cannot be assumed to remove that observed native conversion. Windows
FILETIME rejects pre-1601 dates that POSIX accepts; production must preserve
that distinction. The regression fix supplies a captured **POSIX reference
environment** to fixture replay, without changing production time conversion,
catching away native failures, changing expected DER, or dropping any cases.
An additional X509NativeTimeTest checks actual native normalization, the actual
Windows early-date failure, and POSIX reference replay independently.

The POSIX environment fixture captures **28,471 date inputs** found in the
certificate/CRL requests and **28,469 distinct elapsed-to-native conversions**
from unchanged upstream code. It has **547 source hashes**, including both
input corpora, and model SHA-256
**4ebafa35e15b09b90d828cc9754ed6a0dc3e9aad42b455df22a683a0bb1811de**.
Both DateTime-to-elapsed and native results regenerate exactly on independent
replay. The complete table is environment data, not an additional claim of
28,469 executed JUnit tests. Controlled POSIX replay on Windows is not an
upstream Windows-oracle run; that independent oracle replay remains pending.

Signed/SignedExact and original signed-byte preservation, certificate/CRL
signature and trust validation, stores, private-key timing/scrubbing/undefined
buffer boundaries, general cryptographic operations, independent other-platform
oracle replay and broader stress, remaining hourglass APIs and TLS/resource-I/O
integration remain work. The full Pandoc/all tests, calibre MOBI/all tests and
additional formats absent from Pandoc/all tests remain the required end state.

## X509 Signed / SignedExact and concrete signed records (2026-10-16)

Ported the unchanged crypton-x509 1.9.2 `Data.X509.Signed` implementation:
Signed and SignedExact records, field-order Eq/Show, signedToExact,
objectToSigned, objectToSignedExact, arbitrary-functor objectToSignedExactF and
decodeSignedObject. Kotlin uses an open higher-kinded witness/functor interface;
list, Maybe and writer-pair effects are compared with the original fmap behavior.
Explicit deferred entry points retain independent object/signature/algorithm/
result demand and avoid serializing unused object data. Byte arrays are owned,
copy on access, and cached independently. Signing callbacks supply signatures;
this module does not perform cryptographic signature or trust validation.

The decoder preserves the whole input byte string, accepts upstream BER
container/length behavior, reconstructs signed-object bytes through the same raw
event writer, and retains error messages and suffix demand. In particular, the
raw writer emits no EOC bytes for indefinite constructions; this upstream
behavior is preserved, not silently repaired. Invalid signature shapes can
return `signed object error: remaining stream in object: []` even when the
object has no remaining state. Object parser Left strings are shown with quotes.
Constructed splitting has one-cell lookahead: an invalid first trailing primitive
can throw while a later invalid primitive after a valid trailing value remains
undemanded. The independent corpus caught these details and Show error ordering.

The observer compiles unchanged local Signed/X509/ASN1/hourglass code and the
full unchanged crypton 2.0.1 package with GHC 9.12.2 and frozen dependencies.
Signed.hs has no named property in the original Tests.hs; no original Signed
property is claimed. Instead, **21,574 independent observations** exercise all
these APIs, including **1,000 actual original generated Certificate inputs** and
**1,000 actual original generated CRL inputs** at seed 2045978545 plus index and
size index modulo 100. Generator inputs regenerate exactly on replay. A probe
ASN1Object exposes object remainders/error paths, and deterministic concrete
certificates/CRLs guarantee valid DER coverage in addition to arbitrary DN OIDs.
Certificate cases cover all represented public-key families and time thresholds;
CRL cases cover next-update, revoked entries and extension presence. All-byte
truncations and position mutations, nonminimal/indefinite BER, raw/whole-byte Eq,
failing fields/signers and empty/single/multiple effect results are retained.

The corpus has **542 source hashes** and semantic SHA-256
**53b9129c3e04b6b745dbb725d810fb1880623d5548d50304ae478fb5f9eb9ec9**.
Full independent replay passes on macOS/AArch64. A separate ownership/cache/
signer-demand check brings **X509SignedTest to 21,575 tests**. Compiler locations
are removed from non-exhaustive-case diagnostics by the observer; other fatal
first lines and returned errors remain independently recorded. This does not
prove arbitrary consumer codecs/effects, every thunk/resource boundary or other
platform oracle behavior; finer coverage remains work.

The captured POSIX environment now also includes Signed fixture date inputs:
**56,698 inputs**, **56,696 distinct native conversions**, **554 source hashes**
and model SHA-256
**7742855af3405a8e63a09b4127e8fc9bb33b718692bfbc2d36ee489d37ad7e7c**.
Original DateTime-to-elapsed and native conversion observations regenerate
exactly. Production native date conversion and Windows range failures are
unchanged. This model is environment data, not an additional JUnit test count.

The macOS/AArch64 JDK 21 regression passes **378,928 tests across fifteen
ASN1/X509 suites** with exact XML testcase counts and no failures/errors/skips.
Standalone and installed core JARs are byte-identical, contain **3,355 classes**
and **90 notices**, retain all **560 baseline non-class/non-module-metadata
assets** byte-exact, and omit no eec419a class. The Windows gate now requires
**444,139 tests**. f5a9783's Windows job passes all **422,564 tests**, confirming
the preceding POSIX-fixture environment correction; its Linux job remains live
at this checkpoint. All **176 source/fixture verifiers** pass (158.5s).

Certificate/CRL signature and trust validation, stores, private-key timing/
scrubbing/undefined-buffer boundaries, general cryptographic operations,
independent other-platform oracle replay, broader stress and finer thunk/
resource/streaming coverage, remaining hourglass APIs and TLS/resource-I/O
integration remain work. The full Pandoc/all tests, calibre MOBI/all tests and
additional formats absent from Pandoc/all tests remain the required end state.


## X509 EC utilities, certificate chains and DN hashes (2026-10-16)

Ported unchanged crypton-x509 1.9.2 Data.X509.EC, CertificateChain and the
remaining Data.X509 facade operations: signed certificate/CRL aliases and
accessors/decoders, hashDN and hashDN_old. Existing classes/codecs remain shared.
Production uses Kotlin and JDK primitives; no upstream process is required.

EC support includes all **33 named curve definitions** (15 prime, 18 binary),
Curve/Common/Point records and source Eq/Show, curve size, on-curve validation,
uncompressed point deserialization, named/explicit prime public/private curve
construction and matching names/OIDs. Named constants are translated directly
from pinned Crypto.PubKey.ECC.Types and independently checked through the
original getCurveByName results. Prime range/equation and binary-polynomial
reduction/multiplication follow the required isPointValid semantics, including
infinity, negative/zero modulus errors and ordered field demand. Explicit name
matching compares a, b, order and prime only; it ignores generator, cofactor,
seed and public/private point/scalar. Curve construction validates the explicit
generator but does not validate a domain or subgroup. This is **not** a port of
all ECC arithmetic, scalar multiplication, DH or subgroup validation.

The observer retains exact bodies from crypton tests/PubKey/ECCSpec.hs: the
original Arbitrary Curve instance, twelve point-validation vectors, and the
outOfSubgroup table/helper. Extraction is checked byte-for-byte after the
explicit module/import adaptation. The **12 original point-vector results**
and **20 original "the point is on the curve" assertions** pass against the
original implementation and Kotlin. The twenty points deliberately remain
valid according to the on-curve predicate; subgroup rejection is a separate
operation that is still pending. **1,000 actual original Curve generator inputs**
use seed 2145978545 plus index and size index modulo 100 and regenerate exactly.
Other original ECCSpec assertions and the full crypton suite are not claimed.

CertificateChain and CertificateChainRaw retain source Eq/Show, lazy separate
list spine/head/tail access, immutable List/byte-array ownership, and whole
signed bytes. Encoding maps signed objects without demanding unused heads;
decoding checks every raw certificate and stops at the first returned Left,
preserving its signed 64-bit index and lazy message. Exceptions remain exceptions.
The pinned derived newtype Eq observes the right list spine before the left and
left element before right; independent failing-spine/head cases enforce that
order. Aeson list observations first collect the selected spine before rendering
its elements; the Kotlin observer preserves that boundary separately from the
production sequence API. Chain decoding does not check signatures or trust.

hashDN retains ASCII-only byte lowercasing, forces the ASN1 string tag to UTF8,
DER-encodes DistinguishedNameInner and hashes with SHA1. hashDN_old preserves
original string encodings, DER-encodes the full DN wrapper and hashes with MD5.
Both return digest bytes [3,2,1,0]. **158 independent hash observations** include
all twelve string encodings, the full byte range, non-ASCII text, ordering,
duplicates and invalid OIDs. They do not imply broader OpenSSL policy equivalence.
Facade cases also decode concrete signed certificates/CRLs retained from the
preceding independent Signed observer; that input corpus and sources are hashed.

The combined corpus has **66,416 independent observations**, **550 source
hashes** and semantic SHA-256
**12b53f482954bdee3d4e14551af55a47c75291eb36b2be263221a076a498c308**.
Generation and full independent replay pass on macOS/AArch64, including the
1,000 regenerated curve inputs and 32 original assertions. Coverage also spans
all serialized point format octets, every generator-byte mutation/truncation,
custom field moduli, all curve OIDs/unknowns, explicit deferred fields, chain
first-failure indices, signed-byte truncations and Eq/Show/error order. An
additional ownership/spine/ignored-secret check brings **X509UtilitiesTest to
66,417 tests**. Independent other-platform oracle replay remains pending.

The macOS/AArch64 JDK 21 regression passes **445,345 tests across sixteen
ASN1/X509 suites**, with exact XML testcase counts and no failures/errors/skips.
All **177 source/fixture verifiers** pass (155.8s). Standalone and installed core
JARs are byte-identical and contain **3,380 classes** and **90 byte-exact notices**.
All **560 baseline non-class/non-module-metadata assets** remain byte-exact and
no eec419a class is missing. Windows CI now requires **510,556 tests**. The
preceding 0f8de08 Windows job passes all **444,139 tests**; its Linux job remains
live at this checkpoint.

Signature/trust validation, stores, full ECC/scalar/DH and remaining cryptographic
operations/tests, private-key timing/scrubbing/undefined-buffer boundaries,
independent other-platform oracle replay, broader stress and finer thunk/
resource/streaming coverage, remaining hourglass APIs and TLS/resource-I/O
integration remain work. The full Pandoc/all tests, calibre MOBI/all tests and
additional formats absent from Pandoc/all tests remain the required end state.

## X509 PEM memory/file readers and certificate stores (2026-10-16)

The Data.X509.Memory, File and CertificateStore APIs from pinned
crypton-x509-store 1.9.0 now have native Kotlin implementations in X509Memory,
X509File and X509CertificateStore. Production uses the existing Kotlin PEM,
BER/DER, signed-object and key codecs; it does not invoke Haskell, OpenSSL or
an upstream converter. This covers store APIs, not certificate trust validation
or native operating-system CA discovery.

Memory key loading preserves all eight original key families and both their
outer and inner PEM encodings. PRIVATE KEY tries RSA, modern curves, EC and DSA
in source order; candidates are lazy, invalid returned results are skipped, and
later PEM blocks prepend their keys. Named labels restrict the attempted parser
as in the source. The Memory RSA wrapper deliberately accepts a remainder without
requiring the outer End, independently of the stricter PrivateKey codec. The
shared EC/DSA/native RSA parsers retain their preceding behavior. Signed-object
readers ignore PEM labels. A whole PEM parse failure returns an empty memory
result, while File raises PEMError with the original display text/Show spelling;
file I/O errors propagate there. Store.readCertificates instead swallows only
I/O errors and skips returned signed-object decode failures. Thrown ASN1 errors
remain exceptions, including errors discovered when a result is demanded.

Single stores use source-ordered distinguished-name keys and last insertion
wins for equal subjects. Weight-balanced insertion retains containers 0.7's
delta=3/ratio=2 and strict key lookup, including DN demand before an undefined
map. The relevant original Map source, archive pin and notice are retained.
Composite stores preserve first-match priority, child order and duplicates
between maps. Their emptiness is structural: appending two empty maps is not
empty. Map enumeration sorts subjects; composites concatenate their maps.
Inputs and returned encoded bytes retain immutable ownership and result spines
are memoized. Prefix enumeration and lookup preserve the independently observed
head/spine/field error order without reading unrelated certificate fields.

Directory reads preserve native enumeration order, skip dot-prefixed names and
exactly eight ASCII hex digits followed by a dot and one ASCII digit, and read
only existing non-directory entries, including devices/FIFOs rather than
restricting the source predicate to regular files. The counterintuitive hashed-file exclusion is upstream
behavior. Memory/file tests include non-ASCII digit suffixes, unrelated labels,
invalid blocks, directories and missing paths. File PEM parsing consumes chunks
on demand rather than draining an invalid prefix's suffix. Kotlin closes its
stream on parse failure; the upstream lazy handle's eventual finalization is
not reproduced. Exact handle lifetime, interrupted/mid-read I/O, filesystem
races, symbolic-link/error variations and independent platform oracle captures
still need broader coverage; native directory ordering is not replaced by a
portable sort.

The unchanged original Tests/Tests.hs executable passes all **32 Tasty tests**.
All 32 assertions are also replayed in Kotlin, with independently retained
original PEM fixtures. The new corpus has **36,223 original-code observations**,
**630 source hashes** and semantic SHA-256
**2a7ca86fe594ce1c6ac4db267baf8342623466cd78bf80b2c529f1cf1814e86e**.
Generation and a complete independent oracle replay pass on macOS/AArch64.
Coverage includes every original DER truncation and byte mutation, all labels
crossed with all key/certificate families, multi-block ordering and malformed
PEMs, map/composite policies, balanced maps up to 128 insertions, deferred DN
components and certificate payloads, and real memory/file/directory boundaries.
An ownership/memoization/file-type check brings X509CertificateStoreTest to
**36,224**, including a real POSIX device assertion on macOS/Linux.

The JDK 21 regression passes **519,886 tests across twenty ASN1/X509/hourglass
suites**, with exact XML testcase counts and no failures/errors/skips. All
**178 CI source/fixture verifiers** pass, including a final recheck of the
expanded store corpus. Standalone and installed core JARs are byte-identical,
with **3,395 classes** and **90 byte-exact notices**. All **560 baseline assets**
from the retained ASN1 Pretty checkpoint remain byte-exact and no baseline
class is missing. The Windows gate now requires **546,780 tests**, including
the store suite. The preceding 0fb4a20 Windows job succeeded; its Linux test job
was still running at this checkpoint.

System.X509 platform CA loaders, signature/trust and chain validation, remaining
cryptographic operations/tests, finer thunk/streaming/resource fidelity and
native TLS integration remain pending. The full Pandoc/all tests, calibre
MOBI/all tests and additional formats absent from Pandoc/all tests remain the
required end state; this dependency slice does not complete that objective.

## System CA stores: Unix, macOS and Windows (2026-10-16)

The complete System.X509 loader APIs from pinned crypton-x509-system 1.9.0
are now implemented in Kotlin: Common override selection, Unix paths, macOS
keychain extraction and Windows ROOT enumeration. The package's existing
archive/file pins in http-tls-sources.json and its byte-exact BSD notice are
verified; the package defines no original test suite. No original system tests
are claimed. This supplies CA storage access, not signature/trust validation or
the remaining TLS integration.

Both SSL_CERT_FILE and SSL_CERT_DIR are looked up before selecting the first
present value. An empty present value is still an override; the DIR value is a
single literal path, not a path-list parser. Overrides that yield no certificates
produce mempty without falling back. Native environment lookup reuses the live
GHC-compatible implementation, including its previously tested Windows empty
variable behavior. Unix catches only IOException from SYSTEM_CERTIFICATE_PATH
getEnv; otherwise it reads all five original default paths in order. Store I/O
completes before the lazy Maybe/store results are demanded. CertificateStore now
offers deferred read/root views for this boundary; the existing store corpus
continues to pass. An empty native FilePath is handled as an empty source path,
not Java's current-directory Path("") alias.

macOS starts the original `security find-certificate -pa` command with the two
original system keychains, inherited stdin/stderr and piped stdout. PEM parsing
is incremental; only exact CERTIFICATE labels are decoded. The first resulting
list spine cell is demanded before waitForProcess. Later certificate field
errors may remain deferred until after waiting. Process exit codes are ignored
as upstream; returned decode failures are skipped and thrown errors retain their
original boundaries. Production uses the OS security utility, not a Haskell,
Pandoc, calibre or OpenSSL conversion process.

Windows calls CertOpenSystemStoreW with null provider and ROOT, enumerates with
the preceding context pointer, and closes with flags zero after normal traversal.
The decoder peeks the encoding DWORD, aligned pointer and length DWORD and copies
the bytes before the next enumeration frees the preceding context. Only encoding
type exactly 1 is accepted; combined flags such as 65537 remain unsupported as
in the source. Null opening raises "no store", null enumeration ends the list,
and the close result is ignored. An explicit frame stack preserves the original
nested ASN1Error catch scopes without JVM recursion, including errors injected
in a descendant enumeration. Other exception types propagate and no new finally
close is inserted. Lazy certificate fields are demanded after normal close when
the subject map is evaluated.

The controlled reference preserves all original policy/decoder bodies, verified
by mechanical source comparisons. Only store I/O and the three Windows FFI
declarations are redirected; environment/process modules supply traceable native
effects, and the Windows type shim supplies actual host-aligned contexts. The
original pointer peeks, memcpy, ASN1 decoder, catch placement and store queries
still execute. It is a POSIX controlled reference, not an independent Windows ABI
capture. The new corpus has **29,238 independent observations**, **659 source
hashes** and semantic SHA-256
**feccf3240a2602575279d92aa4f59c96d429f6afed3cc201a77380f5b7e81a9e**.
Generation and complete independent replay pass. Coverage includes override and
empty/missing precedence, both environment effects, all default paths, deferred
Maybe/store/certificate failures, exact process arguments/labels/wait ordering,
ignored exits, Windows encoding tags, typed errors at each native effect, 1,024
contexts and every original certificate DER truncation/byte mutation before and
after a valid certificate. Fixtures use upstream public test certificates;
machine CA bytes are not published as fixtures.

X509SystemTest runs **29,239 tests**, adding a real JNA-memory layout/copy/close
check. Its dynamic corpus is read as source lines and parsed per test, retaining
every case while avoiding the initial all-JSON-tree heap exhaustion under the
existing 2 GiB worker budget. **Two X509SystemNativeTest cases** run isolated JVMs
with real OS CA loading and a real native environment override at a Unicode path.
They remove overrides only in child environments and do not change parent state
or OS certificate stores. On macOS/AArch64, the original native executable and
Kotlin both read **162 certificates**, with concatenated, subject-ordered signed
bytes SHA-256
**47a5a6318693e6a52b03fd446aaa41890827b676ba67967d736f29d64ae1f567**.
The Windows CI gate includes actual Crypt32 ROOT loading and the Unicode override;
Linux CI executes its actual Unix store loader. Independent native Linux/Windows
oracle comparisons remain pending.

The JDK 21 regression passes **549,127 tests across twenty-two
ASN1/X509/hourglass suites**, with exact XML counts and no failures/errors/skips.
All **179 source/fixture verifiers** pass. The installed CLI native/JSON/native
round-trip passes. Standalone and installed core JARs are identical, with
**3,415 classes** and **90 byte-exact notices**; all **560 baseline assets** remain
byte-exact and no retained baseline class is lost. Windows now requires
**576,021 tests**. The preceding 85a64989 Windows gate passes all 546,780 tests;
its Linux test job was still live at the last observation.

Signature/trust and chain validation, remaining cryptographic operations/tests,
native TLS integration, finer thunk/file-encoding/streaming/resource fidelity,
independent platform oracle captures, huge context buffers beyond JVM byte-array
capacity and broader stress remain work. Full Pandoc/all tests, calibre MOBI/all
tests and all additional formats absent from Pandoc/all tests remain the required
end state; CA loading does not complete that objective.

## Binary polynomial arithmetic and all F2m properties (2026-10-16)

Ported every public operation of crypton 2.0.1 `Crypto.Number.F2m` to
`io.heapy.kotdoc.crypto.F2m`: XOR addition, polynomial reduction and
multiplication, raw/reduced squaring, exponentiation, square roots, extended-GCD
inversion, division and quadratic solving by trace/half-trace. The binary
polynomial representation is BigInteger. The original BSD notice remains in
the standalone and installed core artifacts. X509 binary-curve point validation
now uses this complete shared module instead of its two private partial helpers.

The implementation retains unreduced input behavior, zero/one modulus edge
cases, signed XOR, nonunit results, exact error texts and deferred Maybe payloads.
Strict entry order is independently observed against the pinned optimized GHC
9.12.2/AArch64 reference, including different error boundaries in standalone
multiplication and multiplication mapped under Maybe. These are observations of
that build, not a promise that every Haskell compiler chooses identical ordering
among bottoms. Exponentiation uses an explicit stack, preserving the observed
guard order without adding the JVM recursive-call limit. Sparse and dense
exponents through 16,385 bits are independently replayed.

The original `tests/Number/F2mSpec.hs`, `Imports.hs` and `Utils.hs` run unchanged
against the full pinned crypton package and Hspec 2.11.18. All **29 examples**
pass with seed 1729 and QuickCheck max-success 200. The fixed GF(2^16) property
checks every element from 0 through 65,535. The general square-root property
commented out upstream stays disabled; it is not counted as an active test.

The reference additionally compiles mechanically extracted, unchanged active
property expressions, including their Positive/NonNegative wrappers. Extraction
is verified byte for byte. Kotlin ports all 29 expressions and replays concrete
inputs against their independent Haskell outcomes. The corpus also records each
of the **65,536 actual GF(2^16) square roots**, not only a shared round-trip
invariant. It covers sparse/dense polynomials through degree 2048, unreduced
values, reducible moduli, inverse failures, quadratic trace, signed/zero/one
boundaries, ignored arguments, error precedence and Maybe constructor versus
payload demand. Original degree-zero square-root and negative-polynomial GCD
inputs that can fail to terminate are excluded from finite observation batches;
no invented finite fallback is assigned. Resource/timing behavior for these
undefined inputs and other GHC/platform builds remains unverified.

The independent corpus contains **76,971 observations**, **448 source hashes**
and semantic SHA-256
**bdc7e9c76efa68dde0ed09137ed9531804cafb0bf34b9f18ae56f37a3bbbad0a**.
Generation and complete independent replay pass. The final JDK 21 regression
passes **626,098 tests across twenty-three ASN1/X509/hourglass/F2m suites**, with
exact XML counts and no failures/errors/skips. All **180 source/fixture
verifiers** pass. Windows CI now requires **652,992 tests**, including every
F2m observation. The preceding c00cb01d Windows job passes its 576,021-test gate;
its full Linux job is still live at this observation.

The installed CLI native/JSON/native round-trip passes. Standalone and installed
core JARs are identical and contain **3,420 classes** and **90 byte-exact
notices**. All **560 baseline assets** remain byte-exact and no class from that
retained baseline is missing. No upstream program is a production arithmetic
or conversion dependency. As in upstream F2m, no constant-time guarantee is
provided by this module.

Prime modular arithmetic (including the upstream secure exponent/inverse
contracts), general ECC point/scalar arithmetic, signature/trust/chain
validation and the remaining cryptographic operations/tests are still pending.
The existing native TLS, encoding/resource, platform oracle and stress gaps
remain open. Full Pandoc/all tests, calibre MOBI/all tests and all additional
formats absent from Pandoc/all tests remain the required end state.

## Source test inventory foundation (2026-10-16)

`docs/test-inventory.json` and its accompanying explanation preserve a
reproducible registry snapshot: **8,646 source/assets**, **1,125 registry
candidates**, and the complete **81 registered pandoc-types source tests**.
The latter comprise 56 paired JSON examples, two table literals and 23
properties, or **137 assertion/property registrations**. Trials and independent
oracle observations are counted separately. The 58 literal examples have
identified executable Kotlin mappings; 19 properties have partial mappings
and four list walk/query properties remain unmapped. Original generator and
shrinker semantics, Read and the separately reviewed Eq/Ord mapping remain
required.

The snapshot retains pinned revisions, source SHA-256 values, Kotlin test and
production paths/hashes, expectation provenance and reproduction commands.
Pandoc Haskell imports, Cabal suites and calibre Python factories/definitions
are conservative discovery candidates, not proof of reachable test execution.
Separate Lua-engine roots and embedded MOBI/PalmDOC test sources are retained.
Computed golden matrices, command AST blocks, conditional/runtime registration,
dependency embedded/non-Cabal tests and complete executable leaf mappings still
require expansion. This checkpoint does not complete the test inventory or
establish full Pandoc or calibre coverage.

Independent review confirmed the source registration counts and existing
literal mappings. `python3 tools/build-test-inventory.py`, all five negative
snapshot-drift checks in `tools/check-test-inventory-verifier.py`, and
`tools/import-pandoc-json-fixtures.py --check` pass. The importer verifies all
56 verbatim JSON fixtures. No fresh Kotlin suite execution is claimed by this
inventory-only checkpoint. Hash drift detection does not certify behavioral
correctness of future mappings. Full Pandoc/all tests, then calibre MOBI/all
tests and all additional absent formats/all tests remain required.


## Modular arithmetic and fixed-width Montgomery core (2026-10-16)

Added all eight public operations of crypton 2.0.1
`Crypto.Number.ModArithmetic`: expSafe/expFast, inverse/inverseSafe,
inverseCoprimes/inverseFermat, jacobi and squareRoot, with the two original
CoprimesAssertionError/ModulusAssertionError types and deferred Maybe payloads.
The pinned GHC 9.12.2/GMP behavior is observed independently, including signed
moduli, negative exponents, noninvertible bases, zero/one moduli and the zero
inverse's ignored modulus. Square roots retain the source's selected result,
all residue-class dispatches and Tonelli-Shanks constructor/payload boundary.
The variable-time expFast path uses BigInteger arithmetic; it is not used as a
replacement for the secure positive-exponent/odd-modulus path.

Translated `cbits/crypton_bignum.h` and `crypton_powm.c` into a Kotlin core of
fixed-width unsigned 64-bit limbs. It includes carry/borrow arithmetic, shift,
masked selection/equality, Newton inversion of the low modulus word,
add-multiply, Montgomery reduction/multiplication/specialized squaring, R-squared
construction and exact-width big-endian import/export. Exponentiation retains
four squarings and one multiply per four-bit window, visits every table entry,
and uses masks instead of exponent-derived table indices. Loop bounds depend
on public widths. Compiled backend bytecode has no BigInteger reference.

The public wrapper reduces the base, rounds the exponent buffer to whole
64-bit words (zero needs no words in the pinned GMP configuration), wipes
serialization temporaries and the exponent buffer, and the core wipes its
workspace. Failure of that specific workspace allocation maps to the original
C failure status and ordinary exponentiation fallback. The reference redirects
only calloc for controlled failure observations; neither process is actually
forced out of memory. Kotlin tests additionally prove fallback and exponent
wiping after the controlled failure. Other allocation errors are not broadly
caught. Real host memory-pressure behavior and sizes beyond JVM array capacity
remain unverified.

The original vendor NumberSpec/Imports/Utils run unchanged under Hspec 2.11.18:
all **nine modular arithmetic examples** pass (four exponentiation, four
inverse, one squareRoot; seed 1729, QuickCheck max-success 200). The broader
NumberSpec basic-number, serialization, generator and primality examples are
not counted as ported. A mechanically checked copy changes only the module
export and appends the exact original inverse-corner literal, allowing reuse
of every corner and the actual QAInteger/TestDRG/Int0_2901 generators. There
are **810 concrete original-example observations**, plus **1,338 individual
results** on the same generated large values/corners, so equality and
round-trip properties cannot hide an incorrect selected root or numeric result.

The C observer exercises all thirteen fixed-width helpers, records the return
carry/borrow and complete output/destroyed scratch buffers, and checks supported
output aliasing. It covers one through thirty-three limbs, unsigned overflow,
leading zero widths and partial import failure. A mechanically instrumented copy
of the original exponentiation records multiplication/square/window counts,
every table read's ordered relative-address hash and workspace zeroing. All
256 one-byte exponents are captured at three moduli; the access schedule remains
independent of their bit values. The source arithmetic and masking bodies are
preserved. These are algorithm/access-schedule observations, not proof of
machine-code timing under every JVM/JIT/GC/hardware configuration. That runtime
security audit remains pending. Composite Tonelli searches that can fail to
terminate retain finite constructor/error observations without invented finite
payload answers.

The corpus has **16,530 independent observations**, **450 source hashes** and
semantic SHA-256
**09f43629695d1dbe898fe7a533ff51e8a7357ee8f65be84d5c471035f3d7cf67**.
Generation, complete independent C/GMP replay and all **181 source/fixture
verifiers** pass; after the allocation additions, the affected verifier is
rechecked. The initial combined regression passes **641,285 tests across
twenty-four suites**. After adding exact generated values and allocation cases,
the affected suite passes **16,532 tests**, including two direct wrapper checks.
Together with the **626,098 unchanged regression cases** this verifies
**642,630 cases across twenty-four suites**, with exact XML counts and no
failures/errors/skips. Windows now requires **669,524 tests**. The preceding
3764c589 Windows gate passes its 652,992 tests; its Linux job is still live.
The earlier c00cb01d Linux and Windows jobs now both pass.

The installed CLI native/JSON/native smoke passes. Standalone and installed core
JARs are byte-identical, with **3,429 classes** and **90 byte-exact notices**.
All **560 baseline assets** are byte-exact and no retained baseline class is
missing. Production does not invoke an upstream executable or C cryptography
library for these operations.

General ECC point/scalar arithmetic, signature/trust/chain validation, remaining
number/generator/primality/serialization and cryptographic operations/tests,
native TLS integration and the previously recorded fidelity/platform/stress
gaps remain work. Full Pandoc/all tests, calibre MOBI/all tests and every
additional format absent from Pandoc/all tests remain the required end state.


## Fixed-width prime-field ECC engine (2026-10-16)

Translated the complete pinned crypton 2.0.1 `cbits/crypton_ecc.c` engine and
all four entry points: multiplication, precomputed-table sizing, table building
and table multiplication. Production uses unsigned 64-bit Kotlin limb arrays,
Montgomery field operations and the original complete homogeneous projective
addition/doubling formulas, including the public a=0/a=p-3/general branches.
The direct four-bit loop retains four doublings and one masked table selection
and addition per window. Precomputed multiplication visits every one of the
sixteen entries in every window. The implementation retains actual upstream
representation conversions rather than substituting alternative field formulas.

Borrowed byte-buffer spans extend the existing Montgomery core so final
inversion can use the same aliased scratch regions as the source. Context and
secure-inversion allocations, failure statuses, free order and wiping match the
controlled native observer. Infinity leaves output buffers untouched and skips
final inversion as upstream. The engine relies on the caller's prime/point
validation; explicitly labeled bounded unchecked-buffer cases observe C behavior
without promising mathematical results for invalid parameters. UInt32 table-size
guards and wraparound are observed independently of JVM allocation capacity.

The reference preserves all native arithmetic bodies, mechanically checks its
symbol/counter transformation, and instruments allocation failure without
exhausting memory. It records coordinates/status, exact table-byte SHA-256,
borrowed table mutations/read-only multiplication, unaligned byte regions,
aliasing/guards, operation counts, ordered table-address hashes and memory
clearing. Captures cover all fifteen named prime curves, non-base points,
order/wide-scalar boundaries, original P-384/P-521 weight inputs and every byte
scalar on three different curves. Original affine doubleAndAdd supplies separate
mathematical expectations. At fixed widths the scalar loop's formula and table
access schedule is identical across all 256 byte values; final infinity
conversion deliberately has different total work. JVM/JIT/GC/hardware timing,
real memory pressure, huge wrapped allocation sizes and other native ABIs remain
unverified; exact table bytes use the captured 64-bit little-endian ABI.

The entire original ECCSpec body also runs under the pinned Hspec reference:
**124 examples pass**, seed 1729, max-success 200 (source-local property limits
remain intact). This is evidence for the oracle, not a claim that all public
ECC operations or original tests are already ported to Kotlin. Only its module
export and mechanically extracted weight helpers differ from the pinned file.

The new independent corpus contains **2,819 observations**, **450 source hashes**
and semantic SHA-256
**6aa3672d3ce2f9220767134bda8de1f9f393a9ee34e4e0a94636f4d573836c68**.
Generation, complete native replay, the new Kotlin suite and all **182
source/fixture verifiers** pass. The final JDK 21 regression passes **645,449
tests across twenty-five suites**, with exact XML/case counts and no
failures/errors/skips, covering crypto, ASN1, X509 and hourglass. Windows CI now
requires **672,343 tests**. The preceding b2050201 Windows gate passes its
669,524 cases; its full Linux job remains live at this observation. Both jobs
for the earlier 3764c589 commit now pass.

The installed CLI native/JSON/native round-trip passes. Standalone and installed
core JARs are byte-identical and contain **3,436 classes** and **90 byte-exact
notices**. No retained baseline class is lost. All **559 nonmetadata baseline
resource assets** are byte-exact; the Kotlin module metadata is regenerated.
Compiled prime-ECC and Montgomery backend classes contain no BigInteger
reference. Neither an upstream executable nor a C cryptography library is a
production dependency for this engine.

The Haskell prime wrappers/permanent lazy base-table cache, binary-field engine,
specialized P-256 and public ECC point/scalar operations with their remaining
original tests are pending. Signature/trust/chain validation, remaining number,
generator, primality, serialization and other cryptographic operations/tests,
native TLS integration and previously recorded fidelity/platform/stress gaps
also remain. Full Pandoc/all tests, calibre MOBI/all tests and every additional
format absent from Pandoc/all tests remain the required end state.


## Fixed-width binary-field ECC engine (2026-10-16)

Translated the portable software backend and complete scalar-multiplication
algorithm of pinned crypton 2.0.1 `cbits/crypton_f2m.c`. It retains the original
four interleaved bit groups for 32-bit carry-less products, the 64-bit
composition, multiword product, bit-spreading square, destructive sparse-term
reduction, XOR field operations, Fermat inversion and masked coordinate swaps.
The x-only Montgomery ladder performs one differential addition and doubling
per scalar bit, then retains all three source recovery paths: infinity,
negation of the input and one-inversion affine coordinate recovery.

The polynomial parser retains its eight-lower-term limit, degree/width guards,
original limb layout, coordinate overflow/zero-x rejection and return statuses.
A valid point with x=0 is deliberately unsupported by this layer, including
all eighteen named binary curves' order-two points; the public affine fallback
is still pending. The public polynomial temporary is freed without wiping as
upstream, while the 14*n-word secret workspace is cleared before release.
Controlled failures of each calloc and free order/contents are independently
observed. Borrowed unaligned byte spans, output aliases and buffer guards
retain the native results. Bounded reducible/noncanonical polynomial captures
observe the C contract without inventing a field validity guarantee or replacing
its reducer at exact 64-bit degree boundaries and unusually close lower terms.

The mechanically checked observer keeps the entire native source and all
arithmetic bodies, adding only external renaming and read-only counters,
cswap-address recording and a snapshot immediately after the scalar loop.
Primitive observations cover every single-bit operand pair at 32 and 64 bits,
dense/alternating and lane boundaries, all 65,536 sixteen-bit spread inputs,
full-word spread inputs, one through thirty-three limbs, generic and host
hardware-dispatched products, destructive scratch, field aliases, reduction
and fixed Fermat inversion. Kotlin uses the source's portable backend. PMULL
and PCLMULQDQ instructions are not exposed as JVM intrinsics here; execution
on other CPUs and hardware acceleration/performance parity remain unverified.

All eighteen named binary curves have base/non-base, order and wide-scalar
observations. The four original binary-tests names and all eleven scalar
expressions are mechanically extracted without editing test bodies. There are
**1,652 independent affine doubleAndAdd coordinate expectations**, computed by
the original Haskell test independently of the native ladder. All 256 byte
scalars on each of four curves have identical captured ladder-operation counts
and ordered cswap-address schedules. Final recovery intentionally short-circuits
infinity/negation and is not claimed to take identical total work. These finite
schedule checks are not a JVM/JIT/GC/hardware timing proof. Real memory pressure,
huge array sizes, other native ABIs and runtime timing remain unverified.

The development reference also runs the entire original ECCSpec body unchanged:
**124 examples pass**, seed 1729, max-success 200 with the original per-property
20-case limit. This is oracle validation; public ECC/P-256/subgroup tests are
not counted as already ported to Kotlin. Only the module declaration and
appended mechanically extracted binary-input helpers differ from upstream.

The independent corpus has **75,544 observations**, **451 source hashes** and
semantic SHA-256
**d2d53a7e5d6ed8f24d436b78031042bee271a820fdfc6e9f5a8bc6d8ebeb7a7f**.
Generation, complete native C/independent affine Haskell replay, the new Kotlin
suite and all **183 source/fixture verifiers** pass. The final JDK 21 regression passes
**720,993 tests across twenty-six crypto/ASN1/X509/hourglass suites**, with exact
XML/case counts and no failures/errors/skips. Windows CI now requires
**747,887 tests**. The preceding aa427142 Windows gate passes its 672,343 cases;
its full Linux job remains live at this observation. Both jobs for b2050201
now pass.

The installed CLI native/JSON/native round-trip passes. Standalone and installed
core JARs are byte-identical, with **3,440 classes** and **90 byte-exact notices**.
All **559 nonmetadata baseline resource assets** remain byte-exact, and none of
the **3,131 retained baseline classes** is missing. New binary backend bytecode
has no BigInteger reference; no upstream executable or native crypto library
is a production dependency for these operations.

The Haskell prime/binary wrappers, permanent lazy named-base cache, binary
Integer/affine fallbacks, specialized P-256, public point/scalar operations and
their remaining original tests are pending. Signature/trust/chain verification,
remaining number/generator/primality/serialization and cryptographic tests,
native TLS integration and the previously recorded fidelity/platform/stress
gaps also remain. Full Pandoc/all tests, calibre MOBI/all tests and every
additional format absent from Pandoc/all tests remain the required end state.


## Basic numbers and BE/LE integer serialization (2026-10-16)

Added all seven `Crypto.Number.Basic` operations and all public/raw operations
from `Crypto.Number.Serialize`, `.LE`, `.Internal` and `.Internal.LE` for the
pinned crypton 2.0.1/GHC 9.12.2/integer-gmp configuration. This provides the
shared numeric widths and pointer serialization needed by the pending ECC
wrappers. Zero has zero bits/bytes in this backend; signed sizes use the
magnitude and nonpositive logarithms are zero. The source's sqrti algorithm is
translated with its actual selected pair of bounds, which is not replaced by
a generic floor/ceiling square-root pair. Power-of-two/odd decomposition retains
signed results, and areEven preserves element/spine short-circuiting.

Extended GCD preserves the exact observed GMP coefficient selection, including
signs, equal magnitudes and half-period ties, rather than accepting any Bezout
identity. Its result constructor and three components have separate demand
boundaries. The old integer-gmp wrapper computes the second coefficient by a
division by b: at b=0, x and gcd are available but demanding y throws the
original divide-by-zero error. Deferred arguments preserve entry/error order.
The retained GHC/bignum sources document the native normalization and zero/sign
handling; the actual legacy wrapper call site and pinned GHC/GMP oracle supply
independent observations. Retained GMP source delegates ties to mpz_gcdext,
so no unsupported source-level claim about GMP's internal tie algorithm is made.

Both endian serializers retain minimal output, the special one-byte public
unpadded zero, fixed-width padding, Maybe constructor versus payload and the
unsafe wrapper's exact failure text. Their internal API records return counts,
partial/no writes, nonpositive sizes, prefix import, unaligned offsets and
complete prefilled guard buffers. The strict size binding demands the integer
even if the requested output size is invalid. Zero export writes no magnitude
bytes after padding, matching pinned GHC; the ordinary raw i2osp zero case
instead writes one byte and returns one.

The public unpadded i2osp contract specifies a nonnegative integer. For negative
input its native callback writes no bytes into the allocated result; the corpus
therefore captures its deterministic allocation length without inventing byte
contents from uninitialized storage. Kotlin arrays start zeroed. Equality of
unspecified negative-result bytes, allocator/native memory behavior and memory
disclosure parity is not claimed. Controlled negative raw-pointer requests do
preserve every prefilled byte. Invalid pointers, enormous sizes, actual resource
pressure, other GHC/integer backends and runtime timing remain unverified.

The complete original NumberSpec is retained with only its module/export
changed. All **21 original examples** in this slice pass in the native Hspec
run: five basic-number properties, six marshalling properties and ten known
answers. Every property runs **200 cases**, seed 1729. Kotlin ports their
expressions, reuses actual QAInteger/Int1_2901/Positive samples and all five
original 128-byte/decimal vectors, and additionally checks exact generated
widths, factors and output bytes. Generator/primality and other NumberSpec
examples are not counted as part of this slice.

The independent corpus contains **57,574 observations**, **454 source hashes**
and semantic SHA-256
**f5fe66194ba741559c7048eedf6936de81f59b47b49e52d0c8679660ac57d3bc**.
Dense signed values, exact GMP-selected coefficients through 4096 bits,
perfect-square neighbours, endian/padding/overflow cases, strict error order,
lazy components and list tails are captured. Generation, complete independent
GHC replay, all new Kotlin cases and all **184 source/fixture verifiers** pass.
The final JDK 21 regression passes
**778,567 tests across twenty-seven crypto/ASN1/X509/hourglass suites**, with
exact XML/case counts and no failures/errors/skips. Windows CI now requires
**805,461 tests**. The preceding a21d6c2a Windows gate passes its 747,887 cases;
its Linux job remains live at this observation. The aa427142 Windows gate also
passes, while its Linux job remains live.

The installed CLI native/JSON/native round-trip passes. Installed and standalone
core JARs are identical, contain **3,445 classes** and **90 byte-exact notices**,
retain all **559 nonmetadata baseline resource assets** byte for byte and lose
none of the **3,131 retained baseline classes**. Production does not invoke
Haskell or GMP for these operations. Basic-number operations are variable-time;
this module is not a replacement for the separately ported secure limb core.

ECC prime/binary wrappers, the permanent lazy named-base cache, binary
Integer/affine fallback, specialized P-256, public point/scalar operations and
remaining original ECC tests are pending. Number generation/primality,
signature/trust/chain verification, remaining cryptographic operations/tests,
native TLS integration and previously recorded fidelity/platform/stress gaps
remain. Full Pandoc/all tests, calibre MOBI/all tests and every additional
format absent from Pandoc/all tests remain the required end state.


## Internal ECC wrappers, dispatch and permanent base tables (2026-10-16)

Translated the complete pinned crypton 2.0.1 `Crypto.Internal.ECC` module:
MulResult/CurveField, primeCurveMul, primeCurveTable/primeCurveTableMul,
baseTable, curveMul, binaryCurveC and binaryCurveMul. It now connects the
already ported fixed-width prime/binary engines to shared numeric serialization.
All original widths, shared-buffer layouts, guards, C status mappings and the
post-call clearing of the exact scalar region are preserved. Coordinate buffers
are decoded only for success, and all finite return statuses clear the scalar.
Deferred inputs preserve ignored arguments, strict size bindings and guard/error
precedence; an exception before the native call does not invent a later wipe.

The prime path uses the permanent named-base table only when the caller says
base and the scalar width equals the order width. All six numerical key
components participate in lookup, with short-circuit tuple comparison. Each
of the fifteen named prime tables is built on first demand and retained; a
failed build's Nothing is retained too. The Kotlin cache publishes one complete
shared table under synchronized first use. Borrowed table identity/mutations
remain observable, and a scalar wider than the order takes the original direct
path. Unused tables are not built. The binary path rejects x=0 for the public
caller's affine fallback and otherwise tries the fixed-width engine before the
original Integer ladder when native multiplication is unsupported.

The complete binary Integer ladder is retained, including exact requested bit
width (high bits outside that width are not walked), one differential addition
and doubling per bit, infinity/negation recovery, Maybe inverse failures and
three affine divisions. Its projective pair fields remain shared/deferred.
Kotlin evaluates a memoized expression graph with an explicit stack, preserving
finite upstream wide inputs without relying on JVM recursive-thunk depth.
Native observations include 512/2048/8192-bit ladders. The pinned emitted GHC
Core and independent observations distinguish this module's library-call demand
order from the standalone F2m worker specialization, including left-first
integerXor, shared field entry and recovery errors. No expected errors are
rewritten to accommodate the Kotlin implementation. This variable-time Integer
path is used only where upstream selects it; neither fixed-width algorithm is
replaced by it.

The reference mechanically retains the entire source of ECC, its raw serializer
and the numeric/IO compatibility modules. Only module/import/FFI boundaries
change. Prefilled guarded alloca/foreign buffers expose complete layouts,
release contents, exact scalar clearing and table SHA-256/guards. The C shim
normally calls the actual pinned engines; separately labeled return-status
injection checks wrapper policy without simulating host memory exhaustion.
A pure table-size FFI function is not assigned an effect counter. Each cache
scenario executes in a fresh native process, preserving the original lazy CAF;
ordinary independent cases use a deliberately warmed reference cache. Kotlin
uses a fresh instance of the same production cache per scenario.

All thirty-three named curves have base/non-base and order/wide-scalar cases,
with **5,060 independent affine doubleAndAdd coordinate expectations** from the
original ECCSpec algorithm. Every byte scalar on four curves is exercised.
There are **136 fresh native cache scenarios**, including every named prime
curve, all six key components, ignored mismatch tails, failure memoization,
shared mutation and concurrent first use with two/thirty-two threads. The
9,216-case raw Integer guard matrix covers negative/zero fields, deferred
coordinates/coefficient errors and six requested widths. The original complete
ECCSpec still passes **124 native examples**, seed 1729, max-success 200 with
its original local 20-case limit. This validates the reference; the remaining
public ECC/P-256/ECDH/subgroup tests are not counted as already ported.

The independent corpus contains **15,360 observations**, **455 source hashes**
and semantic SHA-256
**a88ff868acf45e4ad8ca2405330acb3c286cb895bc6034b2180728e6d5585fb6**.
Generation, complete independent native replay, the expanded Kotlin suite and
all **185 source/fixture verifiers** pass. The final JDK 21 regression passes **793,927
tests across twenty-eight crypto/ASN1/X509/hourglass suites**, with exact XML
and actual-case counts and no failures/errors/skips. Windows CI now requires
**820,821 tests**. Both Linux and Windows jobs for aa427142 and a21d6c2a now
pass. The preceding b43f5094 Windows gate passes its 805,461 cases; its full
Linux job remains live at this observation.

The installed CLI native/JSON/native round-trip passes. Installed and standalone
core JARs are identical, with **3,465 classes** and **90 byte-exact notices**.
All **559 nonmetadata baseline resource assets** remain byte-exact, and none of
the **3,131 retained baseline classes** is missing. All twelve compiled classes
of the fixed prime/binary/Montgomery backends still have no BigInteger
reference. The newly ported wrapper/Integer path deliberately uses BigInteger.
Production does not invoke Haskell, GMP or native cryptography for this layer.

Upstream ignores serialization failure into uninitialized memory. Bounded
negative/oversized cases use an explicit poison byte in the observer rather
than inventing native garbage contents. JVM arrays are zero-initialized in
production, so unspecified native-memory/disclosure parity is not claimed.
Table bytes capture a 64-bit little-endian ABI. Invalid pointers, enormous or
wrapping allocation sizes, real resource pressure, other ABIs, complete
resource/lifetime equivalence and JVM/JIT/GC/hardware timing remain unverified.

Specialized P-256, public point/scalar arithmetic, the public affine fallback
and remaining original ECC tests are pending. Number generation/primality,
signature/trust/chain verification, other cryptographic operations/tests,
native TLS integration and the previously recorded fidelity/platform/stress
gaps remain. Full Pandoc/all tests, calibre MOBI/all tests and every additional
format absent from Pandoc/all tests remain the required end state.


## P-256 fixed-width word arithmetic (2026-10-16)

Translated the complete pinned crypton 2.0.1 `cbits/p256/p256.c` word slice
to Kotlin, including both 8-by-32 and 4-by-64 digit profiles, all five constants,
word/helper arithmetic, masked modular addition/subtraction, reduction and
nonzero top-digit multiplication, binary variable-time inverse, fixed-chain
Montgomery scalar inversion, point validation and 32-byte big-endian codecs.
The quotient-overestimate borrow correction is preserved. Production does not
replace these algorithms with BigInteger or invoke native cryptography.

The independently compiled unchanged C supplies ordinary expected results for
both original headers. A mechanically verified observer adds only function/loop
counters, correction counts and bounded pre-iteration R/S/U/V checkpoints.
Its ordinary results match unchanged C before becoming fixtures. The corpus
contains **79,934 observations**, **12 source hashes**, semantic SHA-256
**a9bcf3663ba24e89c1ebaaa98e13ed7b65f201f688b4e3562182957070a12163**.
There are 39,711 32-bit and 40,223 64-bit observations, 480 bounded inverse
cases (276 stopped), and 60 observed quotient corrections. Guarded whole-object
aliases include modulus output; nullable destinations, helper masks, full-width
operands, all ten original validation points and their invalid neighbours are
retained. Safe inversion retains the exact 294-Montgomery-call schedule.

Independent review checked function completeness, the original source/headers,
observer transformation, fixture provenance, aliases and fixed inversion chain.
Exact extraction, source verification and full native replay pass. The focused
JDK 21 Kotlin suite passes **79,946 actual tests**, including all source hashes,
with no failures/errors/skips. All four compiled production classes contain no
BigInteger reference. The final six-suite ECC/modular/serialization/P-256
regression passes **247,775 actual tests**, with no failures/errors/skips.
`installDist` and `core:jar` pass; installed and standalone core JARs are
byte-identical and contain all four P-256 classes and the byte-exact notice.
The distribution notice and installed Native/JSON/Native CLI smoke also pass.
This does not claim the complete public P256Spec suite.

Both digit profiles run on the current 64-bit host; an actual 32-bit OS ABI is
not established. Zero-residue/negative C shifts, invalid pointers/buffers and
arbitrary partial overlaps remain outside this evidence. Signed C shift/cast
idioms are compiler observations, not portable ISO C or JVM timing guarantees.
Inverse checkpoints establish finite prefixes only; inverse(0) is not assigned
a finite result and the production algorithm has no iteration cutoff. The
original notice is retained. Field headers, point engine, public P256/ECC APIs,
remaining crypto/trust/TLS work, complete Pandoc/tests and subsequent calibre
MOBI/additional formats/tests remain pending.


## Original pandoc-types Walk properties and ordered shrink checkpoint (2026-10-16)

The twelve original Walk properties now replay on 500 documents generated by
the unchanged pinned `Text.Pandoc.Arbitrary` with QuickCheck 2.16.0.0. Expected
transforms and queries execute independent `Data.Generics.everywhere/everything`,
not either Walk implementation. All twelve original Haskell predicates are
evaluated during capture. Kotlin preserves the original foldr/tails adapters
for list transforms and queries, including suffix-list query contributions.
The callbacks use the oracle's pinned text-2.1.2 upper-case profile; the separate
text-2.1.3 API has different mappings and is not substituted for that callback.

There are **12 source properties and 6,000 generated assertion trials**, not
6,000 distinct upstream source tests. A separate corpus retains **184 original
ordered shrink observations**: 104 complete small candidate lists and 80 raw
source-generated values with the first 32 candidates and an explicit truncation
bit. Kotlin uses lazy sequences to retain order and duplicate candidates without
materializing the attribute Cartesian product. An additional check preserves
the unforced head of a removed 100,000-element partial list. Original notices
and the relevant QuickCheck source files are retained.

Independent review checked predicates, generic expectation independence, list
adapters and shrink ordering. Source/hash verification passes, and a separate
execution of the frozen original binary reproduces both decompressed fixtures
byte for byte: 500 property rows and 184 shrink rows. The focused JDK 21 suite
passes **6,185 actual tests** (6,000 property trials and 185 shrink checks),
with no failures/errors/skips. No production converter was changed.

The Kotlin generator translates source size, weights and rejection policies
using an injected RNG; its full independent decision/distribution audit and
QCGen parity are not established. Full generator/shrinker acceptance, nine
implicit no-op shrink APIs/proofs, broader Unicode/deep/partial shrink coverage,
original-generator replays of the nine table properties, JSON roundtrip and
Figure property, and remaining Read/ordering/inventory audit remain pending.
This checkpoint does not complete local:611, all pandoc-types semantics, full
Pandoc/tests or the subsequent calibre MOBI/additional-format stages.


## P-256 redundant Montgomery field headers (2026-10-16)

Ported both complete pinned crypton 2.0.1 `p256_gf.h` headers: nine alternating
29/28-bit limbs for include32 and five alternating 51/52-bit limbs for include64,
with R=2^257. Kotlin preserves carry/degree reduction, sum/difference, square/mul,
assignment, scalar3/4/8, variable-time zero normalization and both Montgomery
conversions. All constants and **840 precomputed table limbs** are transcribed
from pinned sources. The 64-bit profile uses unsigned128 low/high pairs; neither
profile replaces field arithmetic with BigInteger or native cryptography.

A mechanical extractor verifies constants, product expressions, reducer control
flow and instrumented header copies. Independent native observations compare
ordinary outputs, mutations and guards against unchanged C on each profile.
Read-only traces retain products, normalized scratch arrays, each elimination
half, carry reduction, zero-normalization passes and cleared conversion buffers.
The corpus has **8,440 observations**, **17 source hashes**, semantic SHA-256
**8b0410e671ed68f1bee317a405199b37c7aa27ff2f644ac1b82803252b250da4**.
Exact whole-object aliases and nonzero aligned offsets are checked. Ordinary
field-range operations additionally have independent Python modular-congruence
checks; arbitrary raw degree arrays and labelled wider sums claim raw parity.

Independent source review, exact extraction and full original/observed native
replay pass. The final JDK 21 build disables build cache and filesystem watching
and uses in-process compilation: all ten requested tasks execute. Fresh XML
contains **8,457 field tests** plus **79,946 word tests**, **88,403 actual tests**
total with no failures/errors/skips. `core:jar` and `installDist` pass; standalone
and installed core JARs are byte-identical, contain all five field classes with
no BigInteger references, and retain the byte-exact original P-256 notice.

Native expectations preserve source range counterexamples: 46 include32 scalar8
observations exceed the stated expanded output bound, and 150 labelled include64
no-word-overflow sums exceed ordinary field limb bounds. They are recorded in
manifest.rangeExcess, never corrected to fit comments. Both profiles run on the
current 64-bit host; actual 32-bit OS ABI, invalid pointers/buffers, arbitrary
partial overlaps/type-punning, self-overlapping memcpy, timing and secure-erasure
parity remain outside the evidence. This checkpoint does not port the point
engine/public P256 API or complete local:613; public ECC/ECDH/subgroup tests,
other crypto/trust/TLS work, full Pandoc/all tests and subsequent calibre stages
remain required.


## Exact seeded generator and implicit shrink checkpoint (2026-10-16)

Added the actual pinned QuickCheck 2.16.0.0 QCGen/Gen split tree, splitmix
0.1.3.2 initialization/mixing and random 1.3.1 bounded-choice path as Kotlin
test support. Literal pandoc-types generator expressions retain applicative
and monadic splitting, size/depth policies and rejection resizing. Independent
observations cover **420 seed/size operation rows**, including native Int64
seed extrema, choose versus chooseInt, fmap/bind/ap, vectors/lists, suchThat
and arbitrary Int/inBounds. Full typed trees for the existing **500 original
Pandoc generated inputs** now match the same QCGen seeds and requested sizes,
rather than an unrelated Kotlin random stream.

Nine original instances inherit QuickCheck's empty shrink; explicit Kotlin
APIs and compound Citation/Alignment hooks now preserve that behavior. A
separate unchanged-source oracle supplies **270 generated observations** for
those nine instances. All original sources and notices are retained. Independent
review traced seed operations, combinator evaluation and generator expressions;
separate original binaries reproduce all 420 and 270 decompressed rows byte for
byte. Both source/payload verifiers pass.

The fresh JDK 21 no-build-cache/in-process run passes **7,375 actual tests**:
6,000 trials of the twelve existing Walk properties, 185 ordered shrink checks,
420 QuickCheck operation checks, 500 seeded Pandoc comparisons and 270 implicit
empty-shrink checks. There are no failures/errors/skips. This extends test
support and evidence; no production converter changed. Trials and observation
rows are not additional registered upstream source properties.

Exact replay of this finite seed corpus does not prove every seed or complete
generator fidelity. Independent standalone replay of all nineteen default
generator instances, explicit helper depth/size/Unicode boundaries, complete
shrinker lazy/deep/partial acceptance, original-generator replays for nine table
properties, Figure and JSON roundtrip, and Read/ordering/inventory audits remain
required. The earlier injected-Random helper is separate exploratory support.
Local:611, full Pandoc/all tests and subsequent calibre stages remain open.


## Command, computed Old and Lua registration inventory (2026-10-16)

Enumerated **1,870 Command source goldens from 1,116 Markdown files**,
**174 computed Old registrations** and **48 Lua registrations** (34 Haskell
assertions and 14 script suites). All remain **unmapped** to complete Kotlin
application/runtime assertions. Independent pinned Pandoc Markdown AST capture
and replay select top-level CodeBlocks; bounded source extraction preserves
tabs, indentation, fence lengths and HTML-comment exclusions without executing
contained shell commands. Combined command goldens remain authoritative where
stdout can collide with conventional stderr/status prefixes.

The fail-closed Old registration parser expands original helper matrices and
retains duplicate MediaWiki registrations, exact argv, input/expected hashes
and normalizers. Independent review corrected explicit processing order:
expected text is normalized after strict UTF8 reading; actual stdout is decoded,
normalized, then stripped of CR. Command continuation-only blocks retain the
original unwords/take/drop behavior. The eight extraction regressions pass.
Independent AST replay of all 1,116 files, canonical snapshot verification and
eleven negative drift scenarios pass. Existing literal JSON expectations remain
unchanged. Local:610 observations and local:611 property trials are separately
counted from upstream source leaves. Historical recorded test results remain
distinct from current artifact hashes reviewed at commit
`cdf2b84906e91b1baad740738564855677966acb`; the later 7,375-check generator result
above is not retroactively attributed to the earlier 6,185-check evidence record.

Nested Lua script leaves, remaining reader/writer/common/CPP trees, executable
Command/Old/Lua mappings, embedded/non-Cabal dependency suites, calibre runtime
registry reconciliation and directional additional-format matrices remain
required. New field/generator checkpoints still need separate machine evidence
records. This source inventory checkpoint does not complete local:622 or the
full Pandoc/calibre objective.


## Complete raw P-256 point engine (2026-10-16)

Translated all eleven static helpers and five actual exports of pinned crypton
2.0.1 `p256_ec.c`, using the accepted specialized Kotlin word/field engines.
The fixed field inversion chain retains 287 squares, 13 multiplies and six
assignments. Jacobian doubling, mixed/general/equality-aware addition, masked
copy, both table selectors, runtime precomputation, base/nonbase scalar windows,
affine conversion and raw public C exports preserve source operation/write order.
There is no production BigInteger, generic affine replacement or native process.

Independent unchanged C and mechanically instrumented copies agree on every raw
output, input mutation and guard for **7,040 observations** (3,520 per profile),
with **20 source hashes** and semantic SHA-256
**bf670b6e5e3ae8d94a58edea2b41fcd32f96ad7058cbb278ea461f053e568321**.
Read-only events retain field temporaries, table reads, complete precomputation,
branches and scalar windows through counts, bounded prefixes and a full ordered
FNV1a64 replay accumulator. This is not a cryptographic or timing proof. Labelled
valid domains additionally have independent affine arithmetic checks.

Independent source/observer review, exact extraction and full native replay pass.
A fresh no-build-cache/in-process JDK 21 build executes all ten requested tasks;
XML confirms **7,060 point + 8,457 field + 79,946 word tests = 95,463 actual tests**,
with no failures/errors/skips. `core:jar`/`installDist` pass, installed and
standalone core JARs are byte-identical, all three point classes contain no
BigInteger/foreign/ProcessBuilder references, and the original notice is exact
in the JAR and installed distribution.

Raw source boundaries are preserved: incomplete add/mixed formulas do not gain
universal infinity identities; negate(0,0) returns (0,p). Above-order
points_mul_vartime(n,1,G) returns (0,0), not G, outside its scalar<n precondition.
Whole/cross-coordinate aliases capture exact write schedules, not universal
group-law guarantees. Partial/table-output overlaps, exact-self negate memcpy,
invalid buffers/pointers, actual 32-bit OS ABI and timing/erasure remain outside
the evidence. Public Haskell P256 wrapper policies, complete P256/ECC/ECDH/
subgroup suites and remaining crypto/trust/TLS work still require porting.
Local:613 and the full Pandoc/calibre objective remain open.


## Standalone generator domain and source constructor witnesses (2026-10-16)

Every one of the nineteen original pandoc-types Arbitrary instances now has
direct unchanged-source seeded replay: 50 seeds and requested sizes 0/1/2/3/8/30
give **5,700 complete typed observations**. Seven default Kotlin wrappers retain
the source resize3 and builder-list policies instead of inferring them through
Pandoc generation. Private realString/arbAttr/arbInlines/arbInline/arbBlock and
row/head/body/foot/cell helpers are observed by loading unchanged Arbitrary.hs
in its GHC module context; no export patch or substitute implementation is used.

The **4,538 helper/domain observations** include depth -1/0/1/2/3/4, sizes0/1/3,
all eighteen Attr choices, source-generated text boundary witnesses (codepoints
32/127/128/9999 and lengths0/8), and 56 named constructor witnesses (twenty Inline,
fourteen Block and twenty-two enum constructors). Expectations remain original
outputs, never Kotlin-generated or rewritten. Existing 420/270/500 payloads are
unchanged; the QuickCheck manifest refreshes changed reference README/cabal hashes.

Independent review checked wrappers, source expressions, helper context and
fixture provenance. Separate original execution reproduces all 5,700 standalone
and 4,538 private-helper rows byte for byte. Source/payload verification passes.
Fresh JDK 21 no-cache XML confirms **17,613 actual tests**, comprising 10,238 new
generator-domain checks plus the previous 7,375 tests, with no failures/errors/
skips. No production converter changed.

The new finite acceptance matrix in `docs/pandoc-types-porting-matrix.md` retains
explicit open gates. This seed/depth/size corpus is not proof for every possible
seed, arbitrary size or undefined/infinite value. Complete lazy/deep/partial
shrinker and Unicode acceptance, original-generator table/Figure/JSON property
replays, derived Read/ordering dictionaries and every public semantic export/
instance still require evidence. Local:611, full Pandoc/tests and subsequent
calibre stages remain open.


## Haskell registration census and nested Lua trees (2026-10-16)

The next inventory checkpoint preserves **60 reachable Haskell module root
tests definitions, 62 module references and 2,391 registration sites/templates**.
Sites are not expanded runtime leaves: typed helpers, named-list reuse, map/
comprehension multiplicities and conditional behavior retain explicit semantic
expansion gaps. Root definitions, source spans/hashes, aliases and shared helper
comparison semantics are retained. Lexical handling excludes nested comments,
strings, characters and quasiquotes; CPP detection records branches without
selecting them. No CPP branch occurs in these reachable sources at this pin.

A separate original Pandoc Lua registration observer replaces only tasty
constructors and never invokes assertion callbacks. It captures **309 nested
registrations plus one empty group across fourteen original script suites**,
with callback source-line provenance. All remain unmapped. The finite captured
environment does not establish every dynamic/platform registration variant or
execute/translate the underlying test assertions.

Independent review checked source-census limits, comparison contracts, the
Lua observer, source spans and historical evidence provenance. Eight census
regressions, eight extraction regressions, independent replay of all fourteen
Lua trees, canonical verification, sixteen negative drift scenarios and the
56 original JSON fixture checks pass. Historical local:611 **7,375-check**
evidence reads exact blobs from commit
`cdf2b84906e91b1baad740738564855677966acb` and its pinned submodules, independently
of current checkout work; it does not rewrite older 6,185-check history.

Full helper/list/comprehension leaf expansion, original expected-value contracts,
all executable mappings, dependency embedded/non-Cabal suites, Lua/platform
variants, calibre runtime registry/factory reconciliation and directional format
matrices remain required. Newer generator/field/point evidence still needs its
own machine inventory records. This checkpoint leaves local:622 and the full
Pandoc/calibre objective open.


## Original registered pandoc-types property evidence (2026-10-16)

The remaining eleven original registered properties now have independent Kotlin
evidence: nine table normalization properties, Figure representation and JSON
roundtrip. An exact source verifier reconstructs all original property bodies
and six exposed forall bodies verbatim from the pinned test module. The native
reference emits **5,305 records**: 4,500 original TableHead/Body/Foot inputs over
all widths2..16 and seeds1..100, full normalization outputs and original predicate
truths; 305 Figure inputs/results including five empty/whitespace witnesses; and
500 original Pandoc inputs with actual Aeson bytes and decoder trees.

Kotlin compares independent full table/Figure results and JSON bytes/trees before
evaluating the translated source predicates. This is not self-roundtrip-only
evidence. Eleven separate original QuickCheck runs each retain 1,000 successful
trials; their eleven report checks supplement 14,305 Kotlin behavior checks.
The manifest maps all **81 original source registrations** to concrete methods/
dynamic prefixes, including existing twelve Walk properties, 56 unchanged paired
JSON literals and two table literals. Trials are not new registered source tests.

Independent review checked the exact predicates, source mappings and complete
expected values. Separate unchanged native replay reproduces every record and
all eleven reports byte for byte; source/payload verification passes. Fresh
JDK21 XML confirms **32,056 actual tests**: 14,316 new checks, 17,613 earlier
generator/Walk/shrink checks, 113 existing JSON checks and fourteen table checks,
with no failures/errors/skips. No production converter changed.

This discharges the named registered-suite evidence checkpoint, not every
pandoc-types semantic requirement. Complete shrinker Char/lazy/deep/partial
acceptance, typed Read and Eq/Ord dictionaries, all public Walk/Builder/Generic
exports and effects, and authoritative inventory reconciliation remain open.
Local:611, full Pandoc/all tests and all subsequent calibre stages remain required.


## Public specialized P256 values and original P256Spec (2026-10-16)

Ported the two public value types and all thirty exported terms of pinned
`Crypto.PubKey.ECC.P256`. Scalar/Point use owned native-representation bytes;
RAM 0.22.1 supplies the independently observed Eq/Show/NFData contracts. Scalar
Show remains `Scalar <scrubbed-bytes>`; Point Show quotes raw storage bytes.
Public constructors preserve distinct size/coordinate errors and integer bounds,
scalarReduce is explicit, RNG makes exactly one 32-byte request without zero/order
retry, and infinity policies preserve original operand identity and raw quirks.
BigInteger is used only for the source Integer conversion boundary; arithmetic
calls the specialized Kotlin word/field/point engines.

The original public module and pinned RAM provide **9,877 independent
observations**, **56 source hashes**, semantic SHA-256
**92fbafe1cf2899aa35bcd21430d6e96f9f67a8624d2ad527cb9cf432bc1e07e9**.
Both Kotlin digit profiles replay them. The complete unchanged original P256Spec
passes **31 examples**, including twenty property families at 200 successes each
and eleven literals. Kotlin translates every family/literal with original-module
inputs/results; the 400 captured generator rows are separate from the native
Hspec trial stream. The complete original test source is mechanically retained.

Independent review found and corrected an omitted edge/documentation claim:
original scalarInv(n) terminates with zero and is now an explicit independent
regression. ScalarInv(0) is not assigned a finite result; its unbounded source
loop and bounded-prefix evidence remain. Production did not change for this fix.
Separate original-module replay, extraction/source checks and all original-suite
checks pass. Fresh focused XML after the fix has **19,813 actual tests** with
no failures/errors/skips. The preceding unchanged-production combined run passes
**115,274 actual public/word/field/point tests**, also without failures/errors/
skips; its count predates the two added edge assertions. JAR/installDist checks
pass with byte-identical standalone/installed core JARs and exact RAM/crypton/
P256 notices. Bytecode BigInteger calls are only constructors, signum, bitLength
and toByteArray; there is no native/ProcessBuilder conversion dependency.

The public oracle observes a 64-bit little-endian native host; big-endian and
actual 32-bit OS ABI are not established. JVM timing/GC/finalizer/pinned-address
and secure-erasure parity, retained copies, allocation failure and general GHC
bottom/lazy behavior remain unproved. Cleaner ownership and local pointDh cleanup
have explicit Kotlin tests, not claims of lexical native-finalizer equivalence.
Modern Crypto.ECC policies, generic ECC/ECDH/subgroup suites, other cryptography/
trust/TLS work, full Pandoc/all tests and subsequent calibre stages remain open.
This checkpoint does not complete local:613.


### Original pandoc-types ordered shrink and finite demand checkpoint

Independent original-module replay reproduces 134 ordered cases byte-for-byte
(127 complete, seven explicitly truncated prefixes; 21,654 retained candidates),
six finite demand observations, and all 1,114,112 Haskell Char inputs across
1,088 complete digest batches. The raw native Char stream hash also matches.
Original constructor/wrapper/Attr/table/deep/list candidate ordering and duplicates
are retained. Kotlin list views avoid forcing retained partial elements;
DefinitionList enumeration and Citation no-op shrinking preserve these observed
finite demands. Views assume immutable input lists, as do the source AST values.

Source/payload verification passes. Fresh focused XML records 33,284 actual
tests with zero failures/errors/skips (32,056 preceding checks plus 1,228 new);
independent XML inspection confirms all 1,228 new cases. This is evidence for
finite lists and finite prefixes, not infinite/partial spine or general GHC
bottom/exception semantics. Typed Read, Eq/Ord, public API/vector coverage,
remaining dependency work, full Pandoc/tests and later calibre stages remain
open; local:611 is not completed.


### Original Pandoc native runtime registration inventory

The untouched pinned test-pandoc binary and an export-only original-tree observer
agree on 4,465 runtime registrations for GHC 9.12.2/macOS/aarch64 with HTTP and
embedded data enabled. These comprise 1,870 Command, 174 Old and 2,421 remaining
reader/writer/common leaves. JSON component paths preserve 29 duplicate paths
and truncated/dotted/newline labels; IDs include duplicate occurrence. Command
and Old multiplicities reconcile with independently extracted source records.
All 2,888 Pandoc and 19 pandoc-types tracked source/assets match pinned Git blobs.

Independent existing-binary replay passes without rebuilding or running original
assertions. Seven runtime, eight extraction and eight census regressions pass;
the snapshot verifier rejects all eighteen independent corruption cases. The
17,613-check generator evidence is explicitly historical and reads immutable
43db34fe4fd20707b4891e81378c059acdc09d22 blobs, not current shrinker sources.
All 4,465 runtime records remain unmapped. Exact generated-leaf expectation
attribution, executable Kotlin mappings, other OS/feature profiles, dependency
embedded/non-Cabal suites, dynamic Lua variants and calibre directional format/
test inventories remain open. Listing registrations executes no test assertions
and does not complete local:622, full Pandoc or subsequent calibre stages.


### Original QuickCheck finite vector runtime checkpoint

The Kotlin test-helper vector now constructs a finite read-only memoized list
without recursive generator construction or eager element callbacks. The original
Gen bind/ap split tree determines each element seed and tail seed. Per-list
synchronized cells retain successful, nullable and synchronous failure results.
Independent native replay matches all 180 seed/size/count value records and ten
demand stages byte-for-byte; the retained replicateM/liftA2 compiler unfolding
also matches the actual installed GHC 9.12.2 interface byte-for-byte.

Source/payload verification passes. Fresh focused XML has 33,466 actual tests,
zero failures/errors/skips; independent inspection confirms 182 new cases
(180 native value rows, one ten-stage native demand comparison and one adapter
bounds/run-isolation test). Accepted generator/shrinker/property regressions
remain green. This establishes the represented finite deterministic callback
contract, not general asynchronous/cyclic GHC thunk or lazy Gen semantics.
Typed Read, Eq/Ord and complete public API/effect coverage remain open; local:611,
full Pandoc/tests and later calibre stages are not completed.


### Legacy public ECC Types / Prim / DH checkpoint

Kotlin now ports all fifteen original Prim exports, P256 dispatch, generic
Jacobian/affine fallback, composition/decomposition and subgroup policy, plus
the four DH functions. It reuses accepted prime/binary/Internal/P256 engines
and the existing X509 value/parameter tables. Mechanical comparison verifies
all 33 source curve tuples and SEC OIDs. DH preserves coordinate, subgroup,
then infinity error order; field-byte-width shared x; generatePrivate [0,n)
versus scalarGenerate [1,n), including masking/rejection/RNG effects.

Independent unchanged-source replay passes 6,772 observations and 61 source pins
(SHA256 4c70f518fb6bcd8ff12f79c4f9b5c5b2a3a2fb3afa982080e39b6520652c26eb).
The complete original ECCSpec passes 124 examples, including seven property
families at twenty trials each; separate finite generated corpus samples are
not claimed to be that native Hspec trial stream. All twenty subgroup rows
(eighteen binary plus p112r2/p128r2), six private values and valid 7G controls
are retained. Source/extractor checks pass. Fresh independent combined XML has
122,143 actual tests, zero failures/errors/skips: 6,867 legacy plus 115,276
public P256/word/field/point checks. JAR/installDist passes; installed core JAR
is byte-identical and crypton/RAM/P256 notices are exact.

Negative-polynomial F2m inverse-domain inputs are explicitly excluded, including
a source pointDouble(-1,0) diagnostic that exceeded three seconds; this bounded
observation does not prove nontermination or all-Integer parity. Broken custom
parameters, GHC Data/Read reflection, bottom/laziness, physical scrubbing, JVM
timing/GC and actual 32-bit/big-endian hosts remain unproved. Generic source
Integer fallback remains Kotlin BigInteger; accepted fixed engines are not
replaced. Modern Simple/Crypto.ECC/other curves, remaining crypto/trust/TLS,
full Pandoc/tests and later calibre stages remain open. Local:613 is not closed.


### Retained dependency declaration and runner inventory

An independent original Cabal 3.14.1.0 parser replay reconciles all 76 retained
vendor manifests and 69 declared test-suite roots, retaining common stanza
imports and every conditional branch without selecting a build profile. There
are 67 suites with potential retained entry sources and two without; unioning
source directories is explicitly not proof of buildability. All 118 retained
vendor roots are catalogued, including 42 without manifests. Of 226 exact frozen
dependency identities, 73 have matching retained manifests and 153 do not;
installed compiler packages/source fragments do not establish absent test trees.

The independent synthetic common/flag/OS fixture replay and eleven focused
regressions pass. The canonical snapshot passes and all twenty-two corruption
cases are rejected. Source/catalog hashes retain 9,056 files; 310 files have
lexical main/doctest/preprocessor signals and fourteen script/build candidates
remain discovery units. Four inspected non-Cabal routes retain alias/historical
compiler/argument-dispatch distinctions and three original text rule-firing
integers. No package tests or assertions run in this checkpoint.

Every suite remains unmapped; listed/runtime/assertion counts remain unknown.
Effective build profiles, missing exact releases, factory/import and embedded
expectation closure, runnable dependency leaves and Kotlin assertion mappings
remain open. Calibre runtime/format-direction inventory follows separately;
calibre implementation still follows full Pandoc acceptance. Local:622 and
the full Pandoc/calibre goal are not completed.


### Simple named curves and modern prime ECC adapters

Kotlin ports Simple.Types/Prim for all 33 named curves and the modern P256,
P384 and P521 adapters. Simple retains field-modulus/polynomial scalar bounds,
coordinate-before-subgroup validation and negative multiplication by negating
the positive result. Valid Simple P256 points use accepted Internal engines,
not the distinct legacy P256 dispatch. Existing generic fallback formulas are
reused without changing accepted engines. Modern policies preserve one-shot
P256 scalar generation and strict zero-KeyPair failure, variable-length P384/
P521 point payload splitting, scalar byte-width versus signed integer bounds,
and P256 ECDH acceptance of a valid all-zero-x secret. KeyPair is a plain eager
class without added structural equality or secret-printing data-class methods.

Independent native replay passes 4,458 observations/37 source pins, SHA256
f49e5c9daacd02273ceaede7eaafb5ca86b0304f156e3b5819a74018fd86b37d.
The unchanged full modern native suite passes 52 examples/six families/660
checks, while Kotlin's explicitly partial prime partition retains 27 vectors,
two P256 KATs and six property bodies for three curves at twenty samples each.
It does not represent the separate mixed-curve Hspec trial stream. Hidden
Simple source copies and prime-only module extraction are mechanically checked;
parameter tables match the accepted 33-curve legacy source exactly. Fresh
independent combined XML passes 126,640 actual tests with zero failures/errors/
skips, including 4,497 new Simple/prime checks. JAR/installDist, byte-identical
installed core JAR and exact crypton/RAM/P256 notices pass; compiled KeyPair has
no generated equality/toString/components.

X25519/X448/Edwards instances and their vectors/weak-point suites remain open,
as do other cryptography/trust/TLS and full Pandoc/tests. Negative-polynomial/
broken-parameter domains, GHC reflection/bottom, general timing/physical erasure
and other native host profiles remain unproved. Owned SharedSecret bytes and
best-effort cleanup do not establish hardware scrubbing. Local:613 and later
calibre stages are not completed by this checkpoint.


### Calibre source registrations and directional format inventory

Pinned calibre 9.16.0 source inventory reconciles eighteen setup groups with
76 primary factory append routes, retaining original imports, OS/group
conditions, kwargs and exact factory/call spans. Four package-loader routes
retain immediate modules including __init__ and excluding main, without
executing imports/loadTestsFromModule. The complete 1,633 tracked Python/
RapydScript code-file catalog matches pinned Git blobs. Its 811 test_-named
function/method declarations and 143 named factories are source units, not
runtime/inherited/generated tests. RapydScript's 134 files and Qt/webengine
runner remain a separate dialect and unresolved runtime registration scope.

Forty-two builtin conversion classes (22 input/20 output) yield 49 UI input
tokens; Plumber's additional OEBZIP route yields fifty supported source input
tokens and twenty output tokens. The seventy directional rows preserve twelve
exact Pandoc registry names, six filename-inference targets, a separate PDF
engine output route and fifty-one unmatched-direction rows. Names/inference
do not establish semantic equivalence; archive inputs, HTML-in-ZIP output, OEB
directories and optional KEPUB viewing normalization remain distinct routes.

Independent source audit, ten new focused regressions and canonical snapshot
verification pass; the implementation worker's full twenty-six negative cases
pass. A redundant reviewer negative run was stopped after worker completion;
it is not claimed as another full run. No calibre imports, setup/plugin
initialization, assertions or conversions executed. All new records remain
unmapped; runtime availability/counts, factory/import/inheritance/generated
closure, fixture expectations and Kotlin mappings stay open. Full Pandoc
acceptance still precedes MOBI6/KF8/old-Kindle and additional calibre format
implementation. This inventory does not complete local:622 or the full port.


### Donna X25519 redundant field prefixes

Kotlin ports all fifteen Donna32 and nine Donna64 field helpers before fmonty,
retaining signed32 casts/signed64 limbs, unsigned64/128 terms, carry/reduction
stages, contract packing and source buffer mutations. The generic accepted
unsigned128 primitive is reused; there is no BigInteger/native/subprocess field
arithmetic. Independent source comparison also checks all nineteen product and
nineteen square-inner coefficient polynomials against the exact 32-bit source.

Unmodified original C-prefix replay passes 6,734 raw observations/eleven source
pins, SHA256 fabebb4db080364f7626971f1f400bff17798da12c2bdfb2c42fa89b54c56c17.
Full padded buffers, aliases, guards/tails, range labels, traces and byte
serialization remain original expectations. Exact prefix extraction and both
Google BSD/public-domain notices pass. Fresh independent Donna plus reused
P256-field/wide-helper regression XML has 15,203 actual tests (6,746 Donna and
8,457 existing field), zero failures/errors/skips. JAR/installDist passes; the
installed core JAR is identical, both Donna notices match, and Donna bytecode
contains no BigInteger/foreign/ProcessBuilder references or native methods.

Signed negative/overflowing C left shifts are selected pinned-clang observations,
not universal ISO-C results. Outside-precondition Donna64 difference cases are
raw words only. fsquare_times(0) means 2^64 unsigned do-while iterations and is
explicitly excluded from finite replay; no identity answer is invented. Real
32-bit ABI, timing, physical erasure and other host profiles remain unproved.
The ladder, inversion and public X25519 API, X448/Edwards, other crypto/trust/TLS,
full Pandoc/tests and later calibre stages remain open; local:613 is not closed.


### Executable bridge for all original pandoc-types registrations

The inventory now joins every original source registration ID to accepted
Kotlin factory/assertion, exact fixture and production artifacts: 81 source
registrations, comprising 23 properties, 56 paired JSON examples and two
literals, yield 137 assertion/property registrations. Independently rebuilt
factory names and dynamic ordinals identify 20,419 finite behavior cases;
trials and eleven native 1,000-trial report checks are separate units. Original
predicate/forall/callback exposures, generator instances, source literal bytes,
typed expected values and complete oracle output links remain explicit.

The evidence reads immutable 66722c107b7663ded9c8f510a41e05c3e131a970 artifacts
and its documented 32,056-test result, plus separate accepted 33,284 shrink and
33,466 vector support checkpoints. Current mapped Kotlin/production/fixtures
must match those accepted hashes. Raw historical per-case XML is not committed;
identities are reconstructed from exact accepted factory/fixture iteration,
not asserted to be archived XML replay or fresh execution. Review corrected
two literal labels to source-extracted table sanitisation and table normalization
example, keeping IDs and expression symbols unchanged. Ten focused checks and
canonical plus four targeted drift rejections pass; older unrelated scenarios
were not redundantly rerun.

All 81 finite source-execution links are mapped in this precise sense. Universal
generator/shrinker/runtime semantics, typed Read work in progress, Eq/Ord,
complete Walk/Builder/Generic/public API/effects and global Pandoc/calibre
coverage remain separate open requirements. Local:611 and local:622 are not
closed by this mapping bridge. FB2 mapping is a separate next finite checkpoint.


### Typed original Read dictionaries and finite ambiguity demand

The Kotlin typed Read API covers 25 concrete Definition/Many dictionaries and
five aliases, all 79 source constructors, the four class methods readsPrec/
readPrec/readList/readListPrec and Prelude.read. Native Data arity/field census,
original examples, precedence/remainder/record/lexical/container/numeric cases
and deep finite inputs through 8,192 retain independent expectations. Text
packing/remainders distinguish actual Haskell Chars from JVM String adapters;
Double/ColWidth retain native raw IEEE words as well as canonical Show.

Independent replay matches all 16,954 original response lines byte-for-byte,
SHA256 ec36cf4e7454f7ae621f95c9b6988fb4ee02d194390c61a41c6f9d3666a828a6;
the preceding 14,308 response bytes remain unchanged. Review found and fixed
the omitted readListPrec method and original Char/Text duplicate alternatives.
Independent field parses multiply; exact counts/order remain without dedup.
A further review caught whole-read Int overflow/eager duplicate allocation:
31 ambiguous fields have 2^31 original parses, but Prelude.read needs only two
to report ambiguity. Whole read now saturates its ambiguity count at two.
Independent compiled Kotlin probes under 128 MiB reproduce original ambiguity
for 31/1,024 fields and no-parse for a rejected tail; a 2^30-result ReadS view
retains exact size/shared endpoints without allocating every result.

Source/payload verification and diff checks pass. Fresh selected XML has 51,853
actual tests, zero failures/errors/skips: 16,954 Read, preceding types/Native
checks and nine separately selected FB2 tests. These FB2 executions are separate
from this Read API checkpoint. The ReadS List adapter supports result cardinality
through Int.MAX_VALUE and explicitly rejects larger cardinalities; whole read
does not inherit that adapter limit. Infinite/partial input spines, arbitrary
user Read instances, asynchronous exceptions and general lazy ReadS/GHC runtime
equivalence are not claimed. Existing NativeReader remains unchanged; its
uppercase O/X escapes and Char-list Text integration are the next separate fix.
225 Eq/Ord, 174 Walk and full public export/effect rows remain open. Local:611,
full Pandoc/tests and later calibre stages are not completed.


### Complete raw Donna ladder, inversion and export

Kotlin ports both full Donna suffixes: fmonty input destruction/output buffers,
signed32/unsigned64 conditional swaps, the double-buffer 256-bit ladder, both
254-square/eleven-multiply inversion schedules and the clamped Donna export.
The accepted two field profiles remain unchanged. Secret bytes are copied
before clamping; point high-bit masking and byte/limb whole-object aliases
retain source order. The original Haskell FFI ignores C's constant-zero return
and this raw Kotlin output API likewise has no new error return policy.

Independent ordinary-C and mechanically observed-C replay passes 1,782 cases/
twelve source pins, SHA256
da761f6f1f2c1291397a37c8195fc218e1f19728eb269e3d88394e6215f703a8.
Each observed case first agrees with unchanged ordinary C on all output/input
mutations. Fifty-eight complete trace cases retain every ladder coordinate
round and inversion operation; all four original KATs run in both profiles.
Fresh independent combined XML passes 8,541 actual tests (1,795 raw plus 6,746
field), zero failures/errors/skips. JAR/installDist passes with identical
installed core JAR, two exact Donna notices and no BigInteger/foreign/process
references or native methods in the Donna classes.

Aliases are whole-object aliases, not arbitrary overlapping memory regions;
fmonty slots remain source-disjoint and bounded, swaps use source 0/1 masks.
Signed-C shift UB is selected pinned-clang evidence, not universal ISO-C or
real32-bit ABI proof. JVM timing/constant-time execution, physical erasure and
address-layout parity remain unproved. Public Haskell/modern X25519 adapters,
X448/Edwards, remaining cryptography/trust/TLS, full Pandoc/tests and subsequent
calibre stages remain open. This does not close local:613.


### Native reader integration with typed Read and Text normalization

NativeReader now uses the accepted typed Read dictionaries in the original
Pandoc, [Block], Block, [Inline], Inline fallback order. Internal first-result
adapters cap ambiguity demand at one, matching Shared.safeStrRead rather than
Prelude.read uniqueness; the public typed Read API and its duplicate results
remain unchanged. The ToSources Text boundary filters every raw CR before
parsing, including constructor names, quoted strings and numeric escape digits;
escaped backslash-r remains content. Whitespace remainder checking and the final
"couldn't read native" error retain source behavior.

Independent original Pandoc 3.12.1 CLI replay matches all 268 captured responses
byte-for-byte, including the unchanged earlier 207 cases and 61 CR cases; 198
inputs succeed. Saved fresh Gradle60984 XML independently counts 52,121 actual
tests with zero failures/errors/skips: 268 integration cases, 16,954 typed Read,
preceding types/Native checks and nine separately captured FB2 executions.
Native and typed Read source/payload checks and scoped diff checks pass.

This String entry corresponds to upstream ToSources Text; the Sources identity
instance and labeled multi-source chunk/newline API are not claimed. General
lazy ReadS/runtime equivalence remains outside the finite adapter contract.
225 Eq/Ord, 174 Walk and full public export/effect audit remain open, as do
local:611, full Pandoc/tests and subsequent calibre stages.


### Finite FB2 original unit-leaf execution bridge

The inventory now maps exactly six original Readers/FB2 golden leaves and
three Writers/FB2 unit leaves to concrete Kotlin factory ordinals, inputs,
assertions and original expectations. Reader checks compare the complete Native
golden; writer checks preserve the independently decoded original literal/XML
template and explicit para-to-paragraph label alias. The writer Kotlin check is
stronger about the terminal newline than the original lines comparator.

Fresh post-CR Gradle60984 execution supplies exactly nine selected FB2 cases
with zero failures/errors/skips; captured XML and all eight relevant test,
production and Gradle source hashes agree before/after. Native source state is
accepted in a4479abd39f15c094994637bae5c3105b88fe941; the capture truthfully retains
its earlier basis commit and worktree-execution provenance. Independent review
checks original helpers/registrations, the eleven focused regressions, canonical
snapshot and targeted coverage-drift rejection. Other selected Read/Native
counts are not FB2 coverage.

Eight Old CLI goldens, one figures-fb2 command and one ImageSize fixture remain
separate unmapped obligations. Global FB2 options/resources/effects and all
other Pandoc/dependency/calibre mapping remain open; this does not close FB2,
local:622, full Pandoc/tests or later calibre stages.


### Public Curve25519 and modern X25519 adapter

Kotlin now ports all six Crypto.PubKey.Curve25519 functions and three owned
byte wrappers using the accepted specialized Donna engine. Constructors retain
all32 input bytes; RNG requests exactly32 bytes once and clamps an owned copy.
Secret/DH default Show stays scrubbed, public Show and explicit SecretKey
DebugShow match RAM/source formatting. Raw DH permits zero; the modern X25519
ECDH policy rejects zero while ecdhRaw preserves it. The source four-capability
ECC hierarchy now represents X25519 as base+DH without arithmetic/basepoint
stubs, and existing prime behavior remains unchanged.

Independent original replay passes11,433 observations/59 source pins, corpus
SHA25648ae4fcdeaa650d9eaf186f9b421a15d9ea6bdb9dacb65972f2130fcd1de1c84,
plus all four original raw examples and the explicit13-example modern X25519
partition (four decode/seven weak/two property families). Fresh independent
combined Gradle passes35,966 actual tests with zero failures/errors/skips:
22,928 public,4,497 prime,1,795 raw Donna and6,746 Donna field. JAR/installDist
pass; installed core JAR is identical, crypton/RAM notices match exactly, and
eleven public-wrapper classes have no BigInteger/process/foreign references or
native methods. Source/extraction and scoped diff checks pass.

Both Donna profiles replay public results; the modern adapter selects64 bits.
Malformed RNG provider lengths are an explicit Kotlin boundary guard outside
the original pointer-access precondition. Finite ownership/NFData checks do not
prove GHC bottom/laziness, physical erasure, pinned addresses, real32-bit ABI or
JVM constant timing. X448/Edwards and remaining cryptography/trust/TLS, full
Pandoc/tests and subsequent calibre stages remain open; local:613 stays open.


### Finite complete Eq/Ord dictionary and method audit

The source matrix now covers25 concrete dictionaries and225 Eq/Ord method
slots, joining unchanged75,470 original observations with5,855 new original
requests. It retains79 constructors,92 independent immediate-field changes,
73 competing field pairs,19 original generator families, Many IEEE contexts
and126 individual demand/method probes. Expected responses are original
Haskell bytes; no existing expectation was rewritten.

The demonstrated implementation gap was the two Builder.Many dictionaries.
AstComparison.Inlines/Blocks now preserve containers Seq's known-length-first
equality and list-based ordering/default methods. Min/max preserve caller
wrappers using the original <= selection policy, including nontrivial NaN
direction. Independent review checked actual GHC derived/Seq interface/source
bodies and replayed all5,855 new responses byte-exact. Both source verifiers
pass, including unchanged75,470 observations/426pins. Saved fresh Gradle1879
XML independently counts81,325 actual tests, zero failures/errors/skips.

This finite source-backed gate does not claim arbitrary StrictData bottoms,
invalid/infinite Seq spines or general GHC runtime equivalence. Walk's58
dictionaries/174 methods and140 module-owned export entries before member
expansion remain open. Local:611, full Pandoc/tests and subsequent calibre
stages remain open.

Cached diff check reports 46 whitespace/EOF diagnostics only in the verbatim
GhcDerivedEqOrd compiler dump; original dump bytes are intentionally preserved.
Production/test/tool/doc edits have no whitespace diagnostics.


### DokuWiki writer library checkpoint

Kotlin ports the complete496-line pinned Pandoc DokuWiki writer, including
block/inline branches, list/quote/table environments, metadata/templates,
wrapping, image dimensions, language aliases, URI handling and ordered reports.
The development oracle compiles the original full module and original helpers;
production conversion has no Haskell/CLI dependency. All five original Old
goldens are retained verbatim. Command3824 and5107 writer observations preserve
original AST-level output;5107 still depends on the original Muse reader.

Independent review replays all383 observations: unchanged372 (266 original
Native documents plus options/branch/golden cases) and11 new bounded deep
cases. The original372 payload hash remains
4f91d5116e093fed549b943c516da420940949381fff4f8a3f333aafbc523c98.
Source/tool/helper/request/payload/binary provenance and original golden bytes
verify read-only. Saved fresh Gradle77229 XML has383 actual tests and zero
failures/errors/skips. Independent standalone execution also passes all383
under512MiB heap/256KiB stack. A review-found Emph4096 StackOverflow was fixed
with heap continuations, shared output rope and iterative flattening; bounded
probes include Emph8192, Div/quote/note4096, lists/definitions/tables1024.
This is finite evidence, not an arbitrary-depth/resource-budget guarantee.

CLI registration, complete command behavior (including Muse input), the115
original DokuWiki reader tests and remaining textual formats remain open. This
starts local:618 implementation without closing that task, full Pandoc/tests
or later calibre stages.

Cached diff check retains two whitespace/EOF diagnostics in verbatim original
5107.md and writer.dokuwiki fixtures; production/test/tool/doc edits are clean.


### Complete Edwards Donna field and constant tables checkpoint

Kotlin ports the distinct Edwards Donna unsigned32x10 and unsigned64x5 field
algorithms (15/16 helpers), all three common power schedules and every table.
Only the accepted generic uint128 primitive is reused; no X25519 field formulas
or BigInteger/native arithmetic dependency is introduced. Source wrap widths,
operand direction before widening, profile-specific carries, aliases, guarded
byte serialization and mutation order remain visible in raw observations.

Independent original replay passes11,177 observations/19pins, corpus SHA256
1033a89a06117f7a6d1e93f882a1932d752aa1215fa37ac0ec14b868050f0dad.
Ordinary C and mechanically observed C agree before retaining158 complete
power traces (245/7,254/11,251/11 weighted square/multiply schedules). Source
preparation verifies nine original headers,15 mask/offset constants and every
1030/515 table word plus24,576 packed bytes per profile. Kotlin replays all
entries and returns defensive table snapshots.

Fresh independent Gradle39023 passes19,655 actual tests with zero failures,
errors or skips:11,198 Edwards and8,457 reused P256 field/uint128 checks. The
earlier focused Edwards XML also independently confirms11,198/0. JAR/installDist
passes with identical installed core JAR, exact original public-domain notice
and two production classes without BigInteger/process/foreign references or
native methods. Source/extractor/oracle/table verifiers pass.

Raw-word parity outside source-produced redundant bounds is not a mathematical
reduction guarantee. Nonpositive square_times counts retain documented source
UB/unbounded schedules and are not tested as invented identity operations.
Swap masks0/1 avoid signed-negation UB; original aligned byte casts retain C
effective-type assumptions. Real32-bit OS ABI, JVM timing, physical erasure and
address layout remain unproved. Complete modm, points, public wrappers and the
original25-property/six-example Edwards suite remain pending, alongside X448/
Decaf and remaining613/Pandoc/tests/calibre work. Local:613 stays open.

Cached diff check reports17 cosmetic whitespace diagnostics: nine in verbatim
original headers and eight in translated Kotlin power-helper lines. They are
retained without changing arithmetic/source evidence; no other paths are flagged.


### Complete Edwards Donna scalar/MODM checkpoint

Kotlin ports all16 helpers in both original MODM profiles: nine30-bit uint32
words or five56-bit uint64 words, single conditional reduction, fixed-width
Barrett/convolution, expand/raw/contract/window encodings and batch helpers.
Source32 final q1 truncation differs from64 retained carry; q3 tail packing is
separate. MSB subtraction tests, profile-specific batch comparison/default
branches, and shared byte/word aliases preserve raw source behavior rather
than replacing it with generic modulo arithmetic. Existing field/tables and
public engines are unchanged.

Independent ordinary/observed C replay passes11,808 observations/28pins, with
q1/r1/q3/r2/difference/reducer stage traces and complete guarded buffers. Case
SHA25659598f3250c2ace94c7fcc8173e91c2f32770d18fab580898df1d8babbb6e388
remained unchanged during provenance hardening. All actual crypton/RAM source
map bytes/archive identities and exact RAM notice authenticate. Original
Haskell scalar partition passes nine property families100trials each plus one
order literal;200 original Arbitrary samples and all three payloads are
independently replayed, supporting3,600 Kotlin family checks across profiles.
Saved fresh Gradle80569 XML independently confirms15,437 actual tests with zero
failures/errors/skips. Own source/extraction/native/Haskell checks pass.
Own packaging Gradle passes; installed core JAR is identical, notices match
exactly, Scalar/Region classes contain no BigInteger/process/foreign references
or native methods.

Mathematical reduction claims apply only to original bounded inputs. Raw full
words/unrelated Barrett factors and batch underflow preserve observations
without normalization. Expand length0..64 and sliding widths1..8 are the finite
defined matrix; invalid shifts or larger expand stack overwrites are excluded.
32 scalar value storage is36 bytes, not the entire40-byte Haskell allocation.
Native alignment/effective-type assumptions, real32-bit OS ABI, timing, physical
erasure and general GHC laziness remain unproved. Point/selector engines, public
and modern Edwards plus the remaining16 properties/five literal examples of
the original31-example suite, Decaf/X448 and full613/Pandoc/tests/calibre remain
open. This does not close local:613.

Cached diff check retains50 whitespace diagnostics only in verbatim original
and mechanically observed C headers; Kotlin/test/tool/doc edits are clean.


### Complete portable Edwards Donna point engine checkpoint

Kotlin ports all18 portable base helpers,12 Crypton point exports and two
private selectors in both field/scalar profiles, reusing accepted field/MODM
and immutable tables. Source formulas, partial coordinate writes, negative-root
unpack/failure state, exact-object aliases, wrapped uint32 selector comparison,
nonboolean move flags and custom packed tables retain raw behavior. Variable
scalarmul intentionally preserves the source destination==input identity
overwrite before precomputation; actual252 doubles are not changed to match
the source comment's256. Public byte-wrapper/modern policies are separate.

Independent ordinary/observed C replay passes25,184 cases/40pins, corpus SHA256
b4073a80817e73f821e15c0c69b75d783ccdd77e0b6b2ae63d3f0c6e9b6d830a.
Every result includes guarded input/output state before222 trace rows/14,906
checkpoints are retained. All32positions×256 signed-byte selectors/profile,
all eight torsion multiples, raw/off-curve tuples, aliases and sourcefailure
writes replay without normalization. Source/extraction checks pass. Fresh
original Haskell100 generator tuples and the complete31-example suite
(25properties×100trials+six literals) pass; Kotlin translates16point families
across both profiles and five point literal examples, with prior scalar9+1
already accepted. Saved Gradle72678 XML independently confirms28,426 actual
tests, zero failures/errors/skips.

All32 frozen path hashes remain exact. Independent artifact checks reuse the
fresh completed DokuWiki74539 corejar/installDist without another build: core
JAR equals installed JAR, both Donna/Olivier notices match byte-exact, and two
point classes have no BigInteger/process/foreign references or native methods.
No DokuWiki test counts are attributed to point coverage.

Portable forced profiles on one64-bit host do not prove assembly/SSE or real32
ABI equivalence. Source bounded arithmetic applies; raw out-of-range scalar
windows are parity records, not arbitrary mathematical multiplication. Invalid
selector indices/signbit>1, partial struct/byte-word overlaps, pointer lifetime,
layout, constant timing and physical erasure are not claimed. Public immutable
wrappers/modern Edwards, Decaf/X448 and remaining613/full Pandoc/tests/calibre
stages remain open. This does not close local:613.

Cached diff check retains63 cosmetic whitespace diagnostics:48 in original/
observed C headers and15 in translated Kotlin formula lines. No arithmetic or
original bytes were changed for formatting; other staged paths are clean.


### Finite complete Walk dictionary/helper source matrix

The new Kotlin Walkable interface ports56 concrete dictionaries plus generic
Traversable and overlapping pair:174 walk/walkM/query slots and all24 exported
helpers. Caller-supplied child dictionaries, pure/query overrides, generic pair/
nested containers and Caption ShortCaption=[Inline] resolution retain source
contracts. Effects preserve bottom-up transformation, replacement children not
revisited, State/failure/list sequencing; lazy query folds preserve preorder,
multiplicity and First/Last demand direction. Persistent continuation frames
avoid JVM stack recursion for the built-in deep traversal path.

Independent verification reconstructs all174/24 mappings and31source hashes,
replays5,717 original response lines byte-exact and verifies the actual installed
GHC Monoid interface. Two undefined whole-list StrictData constructor failures
remain explicit source-only observations;5,715 are represented in Kotlin.
Saved fresh Gradle44054 XML independently confirms11,725 actual tests without
failures/errors/skips:5,715 new,6,000 old source-property and10 Visitor cases.
The corpus uses49 Walk-relevant source examples, including all20 Inline,14
Block and six MetaValue constructors; non-Walk enum/scalar roots are not
standalone Walk inputs. This corrects the earlier too-broad79 wording without
changing fixtures. Source tests include custom dictionaries, partial demand,
ordered branching prefixes and depth10,000 probes.

Fresh completed PublicEd7376 jar/installDist also packages the unchanged Walk
interfaces with identical installed core JAR and exact pandoc-types/GHC/
Transformers notices. PublicEd test counts are not Walk coverage. Caller effects
need a stack-safe tailRecM override for deep execution; arbitrary strict-field
bottoms, infinite/undefined spines, impure query callbacks and general GHC runtime
equivalence are not claimed. Existing Visitor/production and old expectations
are unchanged. The140 module-owned export entries remain a separate audit;
local:611, full Pandoc/tests and later calibre work remain open.

Cached diff check retains two whitespace/EOF diagnostics only in the verbatim
GhcMonoid interface dump; source byte comparison passes and other paths are clean.

### Public Edwards25519 and modern arithmetic adapter checkpoint

The abstract owned Scalar/Point API now ports all15 original functions and
Eq/Show/NFData observations; the modern Edwards instance supplies18 methods
without inventing a DH capability. Source contracts preserve LE0..64 scalar
decode/reduction, BE32 modern serialization, Integer bounds, point error order,
noncanonical/torsion acceptance, one64-byte RNG request and fresh result ownership.
No accepted field/scalar/point engine was changed.

Independent source review and original replay pass6,194 observations/26pins,
the complete31-example public suite (25properties×100 plus six literals),
and100 original modern DRG tuples. The source-forced Edwards modern native
suite is supporting evidence; its other-curve DH tests do not establish X448.
Saved fresh7376 Gradle XML independently confirms12,418 actual tests with
zero failures/errors/skips, including both portable profiles and ownership/
capability checks. The frozen15-file hashes match. Completed jar/installDist
packages the eight public wrapper classes and byte-exact crypton/RAM notices;
wrapper bytecode has no BigInteger dependency. Modern Integer serialization
uses the existing explicit conversion boundary only.

Scalar Show's payload non-demand and Point Show/NFData demand have finite
original probes and explicit Lazy adapters; arbitrary Haskell heap equivalence
is not claimed. Cleaner storage is best effort: no constant-time JIT, secure GC
erasure, locked memory, temporary-copy lifetime, FFI scheduling or real32-bit
OS parity claim. Public/modern Edwards is accepted within these source contracts;
X448/Decaf448, remaining crypton dependencies and the full local:613/Pandoc
and later calibre scope remain open.

### DokuWiki reader and narrow CLI registration checkpoint

The complete573-line pinned reader is translated in Kotlin, with all115
original reader leaves and1,340 additional independent observations plus
deep/trace/Unicode cases:1,479 observations and one exhaustive classifier
check form1,480 fresh JUnit cases. Saved74539 XML confirms1,480 reader and383
accepted writer cases with zero failures/errors/skips. Independent review
replayed source observations, all1,114,112 identifier predicate inputs and
source/helper/provenance checks; bounded standalone replay passed512MiB/256KiB.

Narrow CLI registration adds explicit Doku input/output, listings, connected
filename inference, options and standalone/templates. Installed proof preserves
10 complete original commands,83 CLI-applicable original reader ASTs and five
verbatim Old writer goldens. The32 remaining library math cases request two
extension combinations that the original CLI rejects with exit23; both errors
are independently retained. Sixteen application observations cover supported
options/inference and exact exit/stdout/stderr. RST8182 and Muse5107#1/#2 complete
commands remain pending dependencies. This is not full application parity.

Review fixed two real App boundary gaps: default UTF8 failures now detect
ZIP/PDF/CFBF/DjVu signatures only after decoding fails, otherwise warn and decode
original raw bytes as Latin1, then remove CR and convert tabs. Thirty-five
original boundary records preserve BOM/signature/malformed/file behavior.
Canonical warning output now indents each embedded LF by two spaces, preserving
CR/tab and keeping binary errors/trace formatting separate. Twenty-two original
filename/error/trace controls plus the former35 and16 cases pass installed proof.
Independent replay authenticated all22 new original responses; the three prior
multiline-warning failures and binary-error control now match byte-exact. The
49 frozen file hashes match and all prior21+35 fixture payloads are unchanged.
Completed85360 installDist packages the warning fix; earlier55224 packaged UTF8
correction. No production upstream subprocess dependency is introduced.

The local Doku identifier helper pins GHC Unicode16 predicates. Shared
CoreIdentifiers still uses ICU78.1 Unicode17: U+1E6C0 yields source `section`
versus the shared Kotlin character identifier; all4,803 Unicode17 additions
remain a shared local:612 gap. General App `-M` metadata is unsupported by the
current CLI and remains local:621 scope. Neither local correction closes
shared identifiers or full CLI. local:618, full Pandoc/tests and later calibre
remain open; source matrix retains branch evidence and dependency boundaries.

Staged diff checking reports one EOF blank-line diagnostic in the verbatim
original5107.md command fixture. Its upstream-byte hash passes; the original
fixture is intentionally preserved and all other checkpoint paths are clean.

### Complete portable Decaf p448 field checkpoint

The first Decaf checkpoint ports all25 field functions, add_nr macro alias and
ZERO/ONE/MODULUS for16×28-bit and8×56-bit representations. Source review
checks profile-specific raw add/sub/bias/reduction, unsigned truncation and
uint64-to-uint128 multiplication operand order, serializers, masks, aliases
and the complete27-stage ISR chain (446 squares,13 multiplies,one copy).
Accepted P256/X25519/Edwards engines and public APIs are unchanged.

Independent extraction/source verification authenticates original source-map
bytes and observer-only ISR snapshots. Own native replay passes16,622 original/
observed rows with27 pins, including80 traces/2,160 states. Guarded raw buffers
and failure outputs are retained, not replaced by mathematical expectations;
169 canonical-input mathematical checks are secondary original-result checks.
All24 frozen file hashes match. Saved fresh50730 XML independently confirms
16,650 actual tests with zero failures/errors/skips. Completed jar/installDist
contains the specialized Kotlin field class without BigInteger and the exact
packaged Decaf notice; installed/core JAR bytes match. No extra Gradle rerun
was needed after these frozen source/test/package checks.

The forced32 portable profile runs on a64-bit host with generic conditional
word branches and observed32-bit big_register_t; it is not a real32-bit OS ABI
or SIMD proof. Destination overlaps forbidden by C restrict are excluded;
ISR output==input is covered. Positive square counts and32-bit multiplier
bounds are enforced. Signed mulw INT_MIN and subx_nr INT_MAX are excluded
original C UB inputs; negative signed carries follow observed Clang arithmetic
shift, not universal ISO-C behavior. Wider raw tuples establish source raw
parity only, not mathematical guarantees beyond source range contracts.

Original headers state MIT and reference LICENSE.txt absent from the pinned
archive. Copyright headers remain verbatim; decaf-MIT.txt explicitly identifies
the separately supplied standard MIT text/URL and does not claim an absent
upstream license file was copied byte-exact.

RemainingSourceInventory records the full remaining Decaf source, not completed
coverage: scalar, points/precomputed tables, X448 public/modern wrappers,
Ed448 signatures/SHAKE, number/primality/signatures/trust/chain and other
local:613 operations remain open. This field slice does not close local:613,
full Pandoc/tests or later calibre delivery.

Cached diff checking reports original trailing whitespace/EOF blank lines only
in the byte-authenticated original/ C source copies. Those files are preserved
verbatim; all translated code, wrappers, tools, notice and ledger paths are clean.

### Corrective pure Walk demand checkpoint

The accepted Walk source matrix exposed a real pure-Identity demand defect:
walk(const Space)(Emph[undefined child]) must return Space, but the previous
Kotlin implementation forced the child. Pure reconstruction now supplies
memoized descendants, immutable deferred lists/maps, an explicit walkLazy root
thunk callback and independently memoized WalkLazyPair fields. Strict walkM
and query traversal machines are unchanged.

Independent review then rejected eager DefinitionList outer mapping and eager
pair-field reconstruction: source take1 of a list with an undefined second
pair succeeds, as does requesting terms while definitions are undefined.
Both exact original/Kotlin repros now agree after common list extent deferral,
lazy outer projection and independent per-pair List field views. Fourteen new
original observations cover these failures, reverse field demand, prefixes,
forced errors, empty take and adjacent Line/Bullet/Ordered/nested definition
spines. Original initial38 request/response/paired streams retain exact prefix
hashes; the5,717 prior original observations remain unchanged.

Own original native replay passes52 exact responses:47 represented Walk cases
and five source-only Generic contrasts. Both source verifiers pass23+31 hashes
and actual GHC Identity/First/Last interface bytes. All20 frozen paths match.
Saved fresh35230 Gradle XML independently confirms11,777 actual tests without
failures/errors/skips:5,715 old closure+6,000 properties+10 Visitor+47 new
original Walk+five adapter checks. Author isolated47+five+5,715 replay passes
512MiB/256KiB; independent exact repro execution also passes. Adapter checks
cover immutable collection equality/hashing, successful/failed memoization
and independent pair fields. No Kotlin-derived original expectation is used.

This corrects the earlier pure-demand acceptance boundary; the174-slot/24-helper
matrix alone did not prove these partial-demand cases. Kotlin convenience
callbacks/Pair remain strict at their documented boundaries; custom dictionary
laziness needs the explicit adapter. Finite Int-sized represented containers,
explicit thunks and bounded source demand are supported, not arbitrary infinite
spines, GHC black holes or opaque callback laziness. Generic4, the140 exports,
expanded350-instance/JSON audit and full local:611/Pandoc/tests remain open.

Staged diff checking preserves eight whitespace/EOF diagnostics only in the
verbatim GHC Identity interface, original syb Basics source and syb license.
Their exact source bytes pass verification; other checkpoint paths are clean.

### Complete Decaf448 scalar source checkpoint

The specialized Kotlin scalar engine ports all16 scalar.c functions plus header
copy and called bzero utility, with all five constants and14×32/7×64 word
profiles. Independent source review checks Montgomery N+1 accumulators/high
carry, signed borrow correction, conditional masks, halve carry, raw equality,
uint64 construction, decode/decode_long chunking and failure output, exact
object/byte aliases, full encode and requested zeroed storage. Accepted field
and other crypto APIs are unchanged.

Own extraction/source and native replay pass6,900 rows/30 pins, with65,852 trace
states and130 defined inverse prefixes. Every API32 row additionally replays
unchanged native code with ARCH64:3,450 mixed-profile observations match raw
outputs/traces (profile layout metadata remains separately observed). Actual
pinned AArch64 Haskell linkage uses API32/scalar words and ARCH64; independent
FFI replay passes389 public scalar observations. Its unchanged29 Ed448/Curve448
examples remain native supporting evidence, not implemented Kotlin signatures
or point/public completeness. Independent canonical integer checks pass376
results without generating expected fixtures. All31 frozen hashes match.

Saved fresh8910 Gradle XML independently confirms10,511 actual tests with zero
failures/errors/skips:6,900 raw rows+3,450 mixed-profile cases+30 pins+130 prefix
checks+one integrity check. Completed jar/installDist contains exactly three
scalar classes without BigInteger and byte-exact packaged Decaf notice; the
installed/core JARs match. No redundant Gradle rerun was needed.

Original scalar_invert has undefined negative C shift counts for its last three
rounds. Original Clang O0/O2/O3 observations are preserved separately; a bounded
observer proves every defined prefix before the first negative count. Kotlin
implements the explicitly normalized zero-tail extension, independently checked
against canonical modular inversion. This is not ISO-C equivalence for undefined
inputs, and raw unreduced observations do not extend canonical guarantees.
Pointers/sizes/alignment, finite alias cases, signed-shift convention and
forced-profile versus real OS ABI limits remain explicit. Functional destroy/
bzero parity does not prove JVM/JIT secure erasure or constant-time execution.

The existing transparent Decaf notice still distinguishes preserved copyright
headers from separately supplied standard MIT text because referenced LICENSE.txt
is absent from the pinned archive. Points/precomputed tables, X448 public/modern,
Ed448/SHAKE, number/primality/signatures/trust/chain and full local:613/Pandoc/
tests/later calibre remain open.

Cached diff checking retains93 whitespace/EOF diagnostics only in exact original
C/header copies and the mechanically derived bounded/normalized observers.
Their source transformations/bytes verify; all other staged paths are clean.

### Complete Muse reader and narrow application registration checkpoint

The complete1,012-line pinned Muse reader is translated in Kotlin with original
ordered grammar, immutable ReaderParsec state/environment, final-state Future
metadata/notes, deletion before note recursion and report-before-demand logging.
Lists/tags/tables/grid chunk reparsing, Long source positions/indentation and
continuation execution preserve the source contracts within represented finite
inputs. Private persistent concatenation avoids quadratic retained list tails.
The existing local Unicode16 identifier helper is reused without modifying Doku.

Independent full source review and original replay pass3,006 observations:
all250 literal reader assertions+2,464 derived+277 expanded+11 deep+four exact
trace cases. A separate full1,114,112-point predicate assertion verifies GHC
isLetter against the reused Kotlin predicate and native isAlpha equivalence;
this is one JUnit case, not a million extra tests. Own bounded replay passes
all3,006 and the predicate at512MiB/256KiB. Source/payload/tool/helper/original-byte
checks and all38 frozen path hashes pass; original expectations come only from
the complete copied upstream reader/tests and retained native responses.

Saved fresh99381 Gradle XML independently confirms4,870 actual tests, zero
failures/errors/skips:3,007 Muse+1,480 accepted Doku reader+383 Doku writer.
Completed installDist and copied installed JAR hashes are authenticated. Final
installed22581 proof passes323 complete application observations (35 input,
22 warning/error/trace,16 options and250 original reader cases) plus two complete
5107 Muse-to-Doku commands and input registration. Own selected five boundary
controls and the original Tabs distinction pass:249 App ASTs match library
expectations, while the Tabs case has a separate original App-normalized result.
The UTF8-only fallback catch, conditional binary signature rejection, raw Latin1,
BOM/CR/tab order and multiline warning versus typed-error formatting remain
source-backed. Main changes only narrow Muse input/extension/inference/default
HTML routing; accepted Doku implementation/payloads and HtmlCliOptions are unchanged.

This supplies the previously pending5107#1/#2 command evidence; historical Doku
checkpoint manifests remain immutable. Muse writer164 original leaves/two Old
goldens and4172 commands, RST writer997lines/22 tests and8182, other formats and
full integration/Lua/filter/options closure remain open. Shared Unicode17/16
CoreIdentifiers drift stays local:612; general App-M metadata stays local:621.
No full Muse writer, local:618, Pandoc/tests or later calibre completion is claimed.

Cached diff checking retains one EOF blank-line diagnostic in the verbatim
original5107.md fixture; its upstream-byte pin passes. Other staged paths are clean.

### Decaf448 geometry, codecs, Niels and full table checkpoint

This coherent first point slice ports28 decaf.c definitions plus header copy
(29 functions), all fixed cofactor4/no-twist/no-sigma guards and complete table
data. SourceMatrix lists45 compiled source definitions:17 remain unimplemented
plus the separately conditional MSVC helper. Independent review checks exact
field call order, aliases/disjoint restrict domains, decode failure buffers,
assertion preconditions, ignored rotation, zero-factor rescaling, torsion equality
and before_double stale-T behavior. No validity/subgroup safety check is invented.
Accepted field/scalar production and public APIs are unchanged.

Own extraction/source/native replay passes21,340 observations/47 pins across
four profiles (5,335 each), with1,764 trace rows/31,590 field stages. Independent
actual Haskell NEON ABI replay passes926 observations and unchanged29-example
Ed448/Curve448 native supporting baseline. Own integer proof passes6,441 checks.
All49 frozen path hashes match; saved fresh59227 XML independently confirms
21,396 actual tests, zero failures/errors/skips. Completed jar/installDist packs
four point/table classes without BigInteger and exact existing Decaf notice;
installed/core JARs match. No additional Gradle run was necessary.

All340 field table elements are independently compared as words,64 raw memory
bytes and56 wire bytes in every profile. Own direct C-versus-Kotlin comparison
checks all2,720 56-bit literals; forced32 independently splits to5,440 limbs.
Constants, identity/base and full comb/wNAF data are present without claiming
their pending multiplication loops. Host raw-memory byte observations are little
endian observations, not JVM layout or big-endian proof.

Matched32/32 and64/64 generic profiles are separated from mixed field64/API32
generic and actual ARM NEON/API32 profiles. NEON repeats low32 mask lanes;
point-local handling preserves raw noncanonical mask differences without changing
field arithmetic. Documented zero/all-ones masks and normalized API booleans agree.
These are portable/host-profile checks, not real32-bit OS or all SIMD ABI proof.

Point_valid does not prove tight limb bounds. All120 independent non-tight
comparisons and28 original32-bit group-arithmetic discrepancies are retained.
The explicit base-plus-p witness first differs at field stage5 (multiply at
decaf.c346 after raw sums343/345); tight32 and all64 controls agree. This proves
a concrete raw-source behavior and a sufficient tested tight mathematical
domain, not universal caller-range reachability or a new upstream precondition.
Raw parity is preserved instead of rewriting expectations to ideal arithmetic.

Remaining17 definitions include scalar/double/dual loops, batch inversion/
normalization, comb/wNAF lookup/precompute/multiply, direct_scalarmul, X448 ladder/
key derivation and precomputed destruction. Later recode_wnaf signed-shift UB
and zero-variable-scalar base-double quirk are explicit pending source concerns.
Public/modern X448, Ed448/SHAKE, other crypto operations and full local:613/
Pandoc/tests/later calibre remain open. Existing transparent MIT provenance and
JVM timing/erasure/foreign-memory limits continue to apply.

Cached diff checking retains304 original whitespace/EOF diagnostics only in
byte-authenticated original C/header copies and the mechanical point observer.
Source transformation verification passes; all other staged paths are clean.


### Generic traversal checkpoint: independent review and attribution correction

The four original Generic schemes (bottomUp, topDown, bottomUpM, queryWith)
and their finite Data/Typeable bridge are ported with 23 AST Data schemas,
77 constructors, aliases and Many specializations. Independent native replay
matched all807 original response bytes and actual GHC interface evidence;
Gradle91143 passed12585 actual cases (808 Generic plus11777 Walk regressions).
The attribution review found and corrected missing Glasgow/CWI2001--2003
credit. Full upstream syb LICENSE and the original17-line Schemes notice are
retained and packaged byte-exact. Narrow Gradle51615 passed the affected
integrity case and jar/installDist;68 source pins and69 frozen paths match.
This is finite protocol/demand evidence, not arbitrary GHC heap equivalence.
The140 module-owned exports,350 instance members and JSON supplement audit
remain open; local:611 and the full Pandoc/calibre goal are not complete.


### Decaf remaining multiplication and X448 raw engine checkpoint

The remaining17 point definitions now join the accepted28 geometry definitions
(and header copy); conditional MSVC ctz remains a separate non-host branch.
Independent replay passed2852 rows/50 pins/four profiles,12 actual assertion
probes,14656843 ordered field writes and eight full table comparisons.
Independent integer models passed1096 checks plus228 defined negative-shift
prefixes. Saved Gradle97490 XML has2915 actual cases with no failures/errors/
skips; jar/installDist contain three new classes and the exact MIT notice.
Actual Haskell ABI239 observations and the unchanged29-example upstream suite
are supporting evidence, not public Ed448 completeness. Original wNAF signed
shift UB is separated from normalized observers, compiler observations and
defined-prefix mathematics. Raw aliases, failure outputs, zero-variable
nonsecret identity and profile-specific masks are preserved. Forced32 profiles
are host execution, not real32-bit ABI proof or universal caller-range proof.
Public/modern X448, Ed448/SHAKE/signatures and the remaining crypto/Pandoc/
calibre scope of local:613 remain OPEN.


### Muse writer and narrow CLI checkpoint

The complete pinned732-line Muse writer is translated, with all164 original
unit inputs and both Old goldens retained independently. Own source/oracle
review and bounded512MiB/256KiB replay passed3378 observations plus the full
Unicode classifier assertion. Gradle20716 passed8249 actual tests:3379 writer,
3007 Muse reader,1480 Doku reader and383 Doku writer; installDist passed.
The local caption helper covers all14 Block branches, preserves simple-table
demand and avoids forcing grid captions. Original double-render side effects,
state/layout, numeric overflow and ordered diagnostics remain explicit.

Initial copied-CLI session results were unrecoverable and are not counted as
passes. Authorized durable recapture now records both child exit codes0, empty
stderr and exact commands/PIDs/log hashes:204 writer App observations,six Doku
controls,two complete4172 commands;323 accepted reader App observations and
two complete5107 commands. All45 frozen source hashes remain unchanged.
Historical reader Main source verification intentionally reports the old pin
mismatch; integration.json links the accepted reader Git blob,current narrow
Main diff,XML and jars. The original reader manifest was not rewritten.

Common DocumentShared recursion/eager caption behavior remains outside this
local helper; generic template errors outside Muse and general App metadata
remain CLI gaps. The --output= alias is resolved for this narrow registration.
RST writer22 original tests/8182 commands and the rest of local:618,612,621
remain OPEN; this checkpoint does not complete Pandoc or later calibre.


### Public Curve448 and modern X448 DH checkpoint

The pinned Curve448 module's three immutable byte types and six functions are
ported with raw56-byte constructors, RAM Show/Eq/NFData observations, owned
input/output snapshots and one56-byte unclamped RNG request. Public raw DH
ignores the native status and permits zero exactly as the source; modern X448
adds the source all-zero rejection and only EllipticCurve/DH capabilities.
No arithmetic/basepoint capability or additional range policy is introduced.

Independent replay passed11189 actual Haskell/RAM observations and59 source
pins, four original Curve448 KATs, the eight-example X448 modern partition and
the complete52-example modern native supporting suite. The corpus is replayed
against all four accepted raw profiles (44756 comparisons). Saved Gradle59616
XML contains84661 actual zero-failure/error/skip cases:44818 new plus39843
X25519/Ed25519/prime regression cases. Own15 frozen hashes,13 packaged classes,
absence of BigInteger references and exact crypton/RAM notices were verified;
jar/installDist passed. No accepted raw engine source changed.

Valid random providers must return the requested size; malformed providers are
outside the source contract. Cleaner erasure is best effort; JVM constant timing,
physical erasure, general GHC demand equivalence and real32-bit ABI are not
claimed. Public Ed448/SHAKE/signatures and remaining number/primality/trust/
chain work keep local:613 and the complete Pandoc/calibre goal OPEN.


### Portable SHA3/Keccak/SHAKE dependency checkpoint

All11 portable crypton_sha3.c definitions and six Decaf SHAKE wrappers are
translated, including all24 round/rotation/permutation constants. Ten selected
algorithms (four SHA3,four Keccak,two SHAKE) retain independent original KATs,
chunking, bit truncation and cSHAKE finalization observations. Own full replay
passed13256 rows/36 pins, including1036850 ordered absorb/round events over
41474 permutations; actual Haskell replay passed5160 bit/metadata observations
and the original48-example partition (30 KAT,ten chunk,eight truncation).
The dev Haskell overlay verifies438 unchanged source/test files and changes
only two hidden-module visibility declarations; optimized Haskell and portable
C are separate reference backends, not an ARM assembly implementation claim.

Saved Gradle17317 XML has13293 actual cases with zero failures/errors/skips.
Own37 frozen hashes,four packaged classes and all three exact notices pass;
jar/installDist passed. State/index, empty calls, stale buffers, repeated
finalization, alias domains, reset/destroy and low-bit final-byte truncation
retain source behavior without adding phase validation.

Nonstandard rates not divisible by eight have explicit C alignment-UB rows,
retained only as compiler observations. Standard rates and unaligned trampoline
paths avoid that boundary. Twenty virtual header dispatch observations above
4GiB prove chunk planning, not resident huge-buffer hashing; JVM ByteArray
spans remain Int-sized. Little-endian arm64 context ABI, real32/big-endian,
physical erasure,timing and general Haskell bottom/heap behavior are not proven.
This is an internal dependency, not the full generic Crypto.Hash API. Ed448,
signatures,other hashes and remaining local:613/Pandoc/calibre scope stay OPEN.


### Many dictionaries and specialized algebra checkpoint

The42 source slots cover two Functor,seventeen Foldable,four Traversable,
thirteen specialized Inlines/Blocks algebra/IsString methods and six helpers/
representation slots. No Applicative,Alternative,Monad or NFData instance for
Many is invented. Independent replay matched all5127 original responses
(5080 finite plus47 demand), actual GHC/Builder interfaces and64 source hashes.
The source defaults preserve Seq tree/append/repetition shapes, strict middle
versus lazy cycle fields, fold grouping, Max/Min >=/<= rather than Ord.max/min,
NaN/signed-zero behavior, actual Monad Applicative superclass effects, signed64
count wrapping and Text-packed IsString behavior.

Saved Gradle65560 XML contains17713 actual zero-failure/error/skip cases:
5128 Many including integrity plus12585 unchanged Generic/Walk/property/Visitor
regressions. The5128-body isolated factory passed512MiB/2MiB stack. Own54 frozen
hashes,thirteen major packaged classes and four exact notices pass; jar and
installDist passed with identical core jars. Existing production files remain
unchanged; five new Kotlin files implement this finite source surface.

Explicit thunk/source-list adapters preserve represented demands; finite JVM
List sizes remain Int-bounded and callback-driven thunk recursion is not an
unbounded GHC stack-equivalence claim. Other Many Data/Eq/Ord/Read/Show/Typeable/
Generic members,NumEnum/metadata/Builder/Data.Version,140 module exports,350
expanded instance members and JSON supplement remain OPEN within local:611.
The complete Pandoc/tests and later calibre goal is not complete.


### Complete RST writer and narrow CLI checkpoint

The pinned997-line RST writer and public flatten helper are translated with
all22 original leaves (19writer/three flatten),four verbatim Old goldens and
43 original reader-produced command ASTs. Own full-source/helper review and
native replay passed2723 observations,all1114112 classifier inputs and43
command extractions. Two exhaustive classifier/lowercase assertions make2725
JUnit cases. Saved final Gradle99759 XML has2725 actual zero-failure/error/skip
cases and installDist passed; the complete factory passed512MiB/256KiB.
The first display-math occurrence stops the source space-removal recursion even
when it has no following Space;three appended originals prove the correction.
All28 prior fixture payloads remain unchanged.

Durable final copied CLI proof records96 exact App observations and21 complete
original commands with exit0/empty stderr. Own eight targeted final CLI checks
passed. All common CLI classes are byte-identical to the initial10971-test
regression build;only common RstWriter.class changed. This binds the earlier
Muse204/six Doku/two4172 durable regression without claiming it was rerun after
the writer-only fix. All56 owned hashes and final class bindings were verified.
The22 remaining commands need missing readers;their AST writer probes are not
full command completion. RST reader and full local:618 remain OPEN.

RST uses the actual Pandoc text-2.1.2/GHC Unicode16 profile:all1433 lowercase
mappings match the vendored source,independently enumerated over every Char.
CoreUnicodeText intentionally implements a distinct text-2.1.3 profile. Existing
Doku auto_identifier U+1C89 mismatch is a consumer-profile defect,not a corrupt
core table;the concrete original/Kotlin repro is retained for task612 followup.
No Doku/Muse source was changed here. Local caption handling is stack-safe,
while common DocumentShared gaps remain. RST flags --list-tables/reference-links
optional booleans and template errors are covered;global verbosity,-M and
generic template behavior remain621. Full Pandoc/tests/later calibre stay OPEN.


### Complete raw Ed448 core checkpoint

All16 pinned eddsa.c definitions and the two prehash update/destroy header
aliases are translated on the accepted Kotlin Decaf/SHAKE engines. Independent
source review and replay passed9884 guarded ordinary/normalized observations
over four source profiles,59 pins and four original SIGABRT subprocess probes.
Own actual Haskell ABI2061 observations plus unchanged original25 examples and
an independent affine Edwards/hashlib model114 checks passed. Author finite
O0/O3 optimizer observations824 also pass;these do not remove the original
negative-shift wNAF ISO-C UB boundary reached by verification.

Saved Gradle15421 XML has9945 actual zero-failure/error/skip cases;jar and
installDist passed. Own56 frozen hashes,one packaged class,no BigInteger/JNA
references and three exact notices were verified. Eight original literal
public/signature/verify vectors run through all four Kotlin profiles.
Prehash copies exactly344 bytes,source keypairs occupy114 bytes,domain flags/
context lengths,error order,canonical response byte113 check,ratio constants,
raw aliases and destruction match the finite source domains. No accepted
engine source changed.

Raw sign intentionally consumes supplied public bytes without rederivation;
sign_prehash rederives and aborts on mismatch. Kotlin represents that fatal
process boundary with a checked IllegalStateException,not invented successful
signing or a CryptoError. Pointer/memcpy UB,unbounded buffers,real32/big-endian
ABI,JVM physical erasure/timing and universal limb reachability are not claimed.
The archive's absent LICENSE.txt remains transparently documented under the
accepted MIT notice provenance. Public Ed448 three types/three sizes/eight
functions and full original public suite are the next gate. No source modern
Curve_Ed448 instance is invented;remaining local:613/Pandoc/calibre stay OPEN.


### Identifier consumer text-profile correction

The confirmed U+1C89 identifier mismatch is fixed for the three current
DokuWikiIdentifiers consumers: DokuWiki reader,Muse reader and Muse writer.
Only the helper's lowercase dependency changes to accepted LaTeXReaderText
text-2.1.2;the complete GHC Unicode16 flags and identifier pipeline are retained.
CoreUnicodeText's distinct text-2.1.3/1460-mapping implementation is valid and
unchanged. Original runtime text-2.1.2 has1433 mappings;all27 profile deltas,
inline/emoji/flags and duplicate/collision cases have independent expectations.

Own original replay passed648 observations and exhaustive lowercase enumeration;
650 new JUnit cases comprise648 observations plus two complete1114112-input
domain assertions. Saved Gradle58407 XML has11241 actual zero-failure/error/skip
cases including unchanged Doku/Muse/RST regressions. Isolated650 passed512MiB/
256KiB. Own full copied CLI replay passed33 exact original observations with
controls and five full4172/5107/8182 commands;durable exit0/empty stderr and
all copied-JAR pins were checked. All20 frozen hashes,packaged helper,both
case profiles,flags/notices and historical accepted helper Git blob match.

Old format fixtures/manifests remain unchanged;their old live-helper pins are
historical snapshot checks,not falsely regenerated current green evidence.
Integration.json binds the verbatim old helper snapshot to the narrow current
change. General identifier consumers/Unicode profiles,DocumentShared and global
CLI gaps remain separate;local:612/618/621 and full Pandoc/calibre stay OPEN.


### Count Num/Enum dictionaries checkpoint

RowHeadColumns,RowSpan and ColSpan now have their actual derived Num7/Enum8
dictionaries:45 source slots. Independent replay matched9930 finite original
responses and48 demand observations,actual GHC/Definition interfaces and38
pins. Int64 arithmetic/fromInteger wrap,succ/pred error strings,newtype pattern
non-strictness,enum endpoint/zero-step/overflow behavior and source-list thunks
are preserved.351 original Cell/TableBody bridges retain represented values
without positivity checks or replacing the existing AST Long field ABI.
Nine complete ranges include ascending/descending10000-element results;other
ranges retain ordered prefixes with explicit truncation,not false finite claims
for zero-step streams.

Saved Gradle96735 seven XMLs contain27693 actual zero-failure/error/skip cases:
9980 Count (9978 originals,integrity,one sharing adapter) plus17713 accepted
Many/Generic/Walk/property/Visitor regressions. The9980-body isolated factory
passed512MiB/256KiB. Own36 frozen hashes,eight major packaged classes and two
exact notices pass;jar/installDist passed with identical core jars. Three new
main files supply distinct value wrappers,dictionaries and explicit AST bridges;
no accepted production implementation or expected fixture was rewritten.

No Real,Integral or Bounded wrapper instance is invented. Strict JVM value
conveniences and explicit construct/match thunk adapters are distinct;the source
model is pinned64-bit GHC Int,not actual32-bit or arbitrary heap equivalence.
Other count/Many generic classes,remainingBuilder/metadata/Data.Version,140
exports,350 instance members and JSON supplement keep local:611 and the full
Pandoc/calibre goal OPEN.


### Complete public Ed448 wrapper checkpoint

The pinned Crypto.PubKey.Ed448 module's three immutable byte types,three
size constants57/57/114 and eight exports are translated on accepted raw
engines. Independent original Haskell/RAM replay passed9056 observations/27
pins and the unchanged25-example suite. Four raw profiles contribute36224
comparisons;ownership,RNG,shared-error identity and original partition tests
complete saved Gradle7980 XML36254 actual zero-failure/error/skip cases.
Own25 frozen hashes,eleven packaged classes,no BigInteger/JNA references and
four exact notices pass;jar/installDist passed. Raw engines remain unchanged.

Safe sign ignores supplied public completely and rederives it;unsafeSign uses
it. Wrong secret/signature lengths return source SecretKeyStructureInvalid,
public length returns PublicKeySizeInvalid. The only existing API edit appends
the required shared error case,retaining previous identities/ordinals. Generated
secrets store one57-byte RNG request without clamp/retry. Constructors and public
access own copies;RAM Show/Eq/DebugShow/NFData finite observations are retained.
Explicit Lazy adapters reproduce observed demand,including ignored public and
verify public-before-signature-before-message under the pinned optimized GHC.
Ordinary Kotlin argument evaluation remains eager;no universal bottom/exception
order equivalence is claimed. No nonexistent Crypto.ECC Curve_Ed448 is invented.

Malformed RNG providers are outside source preconditions;JVM Cleaner scheduling,
physical erasure,constant timing,huge pointer buffers,real32/big-endian ABI and
raw engine UB/range limits remain explicit. System entropy is separate scope.
Other signature families,number generation/primality,trust/chain and the rest
of local:613 remain OPEN,as do full Pandoc623 and later calibre624/625/626.


### Metadata dictionaries, helpers and legacy demand correction

Nine ToMetaValue,two HasMeta and Meta/Pandoc algebra dictionaries cover15
instances/25 method slots,with ten public helpers. Independent replay matched
5084 finite original responses,55 demand probes,actual GHC/Definition/Builder
interfaces and51 pins. Source String/Text overlap,mapKeys packing collisions,
right-biased Meta union,ordered key/value demand,large counts through2^200 and
deep4096 structures retain finite original expectations. Only docTitle/docDate/
docAuthors bodies change in existing Metadata.kt: head-before-tail and grouped
author-prefix behavior now follow source;all other legacy helpers are unchanged.

The initial34870 run passed32859 cases but a separate JVM Map contract probe
found structural Entry equality/hashCode missing. The corrected immutable-entry
adapter leaves all5084/55 original payloads unchanged. Final76981 nine XMLs
contain32860 actual zero-failure/error/skip cases:5145 metadata plus27715
Count/Many/Generic/Walk/property/Visitor/Builder regressions. Metadata comprises
5139 originals,three legacy cases,integrity,sharing and one Map contract case.
The isolated5145-body factory passed512MiB/256KiB. Own Java21 execution against
the retained immutable final JAR confirms symmetric Map/entry equality,equal
hashes and real HashSet lookup. Initial XML/source/reproducer evidence remains
separate rather than overwritten.

Own43 frozen hashes,seven major classes and four exact notices pass;jar and
installDist passed with identical core jars. Lazy finite ordered maps and explicit
source dictionary/list/root adapters do not claim arbitrary infinite maps,GHC
heaps or universal multi-bottom exception ordering. Remaining generic Many/count
classes,Builder/Data.Version/JSONfilter,140 exports and350 instance members keep
local:611 and the complete Pandoc/calibre goal OPEN.


### Complete Number.Generate checkpoint

All four exports and two GenTopPolicy constructors/Show/Eq are translated.
Independent actual Haskell replay passed37127 rows/26 pins and all four original
NumberSpec property families at200 trials each.800 recorded actual TestDRG/
Arbitrary samples supply externally observed bytes and translated source
property checks,not a Kotlin ChaChaDRG or entropy implementation. RNG request
length/order,retained provider buffers,top/mask/odd ordering,prefix versus suffix,
simple biased modulo,normal/overgeneration acceptance and exact100-retry errors
match source. Inclusive unsorted bounds remain accepted without new policy.

Saved Gradle50208 XML contains37955 actual zero-failure/error/skip cases.
Own25 frozen hashes,three packaged classes and two exact crypton/RAM notices
pass;jar/installDist passed. Integer uses the existing Basic/Serialize BigInteger
boundary as appropriate to source arbitrary-precision Integer;specialized word
engines are not replaced. No accepted production implementation changed.

Original bits1/SetTwoHighest performs a logical out-of-bounds store. Ordinary
numeric rows retain result1 unchanged;Kotlin returns that prefix safely without
claiming heap-memory parity. Own separate transformed guarded-oracle replay
passed120 probes:eight trailing sentinels change34→162,112 remain intact.
This controlled allocation proves the store,not ordinary GHC allocator/GC layout.
Malformed RNG lengths are outside provider preconditions;explicit Lazy demand
models are finite compiler observations. Kotlin Int-sized bit counts,host GHC
Int64,virtual request boundaries,unbounded memory,timing and physical scrubbing
limits remain explicit. Prime/Probabilistic/SHA512-ChaChaDRG,other signatures,
trust/chain and full local:613/Pandoc623/calibre624–626 remain OPEN.


### RST reader CSV/grid prerequisites checkpoint

Kotlin RstCsv and RstGridTable provide the reader-used low-level parsing
closure. CSV91 source lines retain all delimiter/quote/escape/keep-space,empty
first-cell,newline/error behavior;250 independent originals supply direct
evidence where no dedicated upstream CSV unit module was found. The cached
gridtables0.1.1.0 source/provenance is retained;all27 original assertions execute
in Kotlin,including the rows accessor and two exact post-grid parser compositions.
Twenty-two appended full ArrayTable frames compare bounds,ContentCell and
ContinuationCell pointers,spans and column specs instead of lossy row-only data.
Earlier309 payloads remain unchanged.

Own source review and native replay passed331 observations and all27 original
assertions,with55 source/tool/runtime/dependency pins. Saved Gradle72954 XML
has331 actual zero-failure/error/skip cases;the same factory passed512MiB/256KiB.
Own32 frozen hashes,eighteen packaged helper classes and verbatim gridtables
MIT notice pass;jar/installDist passed. Four deep originals cover CSV10000 rows/
65536 quoted characters and grid4096 rows/columns. Unicode widths use accepted
doclayout behavior;the source CSV versus standard Parsec primitive distinction
and partial raw array bounds remain explicit. No CLI format registration or
existing production implementation changed.

Unused polymorphic mapCells/general Array API are not claimed ported. Higher
Pandoc Parsing.GridTable302 adapter/cell-block callbacks,full1962-line RST
reader,37 original tests,three Old goldens,directives/runtime/CLI remain OPEN.
Incomplete reader drafts stay outsidecore and outside this commit. Full618,
Pandoc623 and calibre624–626 are not completed by this prerequisite slice.


### Portable SHA512-family bounded checkpoint

All23 portable definitions (eleven exports,five chunk/prefix adapters,seven
masked helpers),eighty constants and selected public SHA384/SHA512/SHA512t224/
256 Hash/Digest contracts are translated. Only SHA384/SHA512 expose source
prefix capability. Independent generic C replay passed9974 rows/33 pins with
separate mechanical schedule/eighty-round observers matched to ordinary C.
Own actual Haskell replay passed2892 rows including1600 original generator
samples and the26-example HashSpec partition with eight properties×200.
Author full-corpus generic O0/O2/O3 and actual ARM dispatcher O2 agree;the host
reports the SHA512 extension available. Conditional instruction backends are
reference profiles,not translated assembly claims.

Saved Gradle95294 XML contains11608 actual zero-failure/error/skip cases.
Own35 frozen hashes,seven packaged classes,absence of JDK digest/JNA calls and
three exact notices pass;jar/installDist passed with identical core jars.
Counter carry/bit-length wrap,masked comparisons,prefix truncation/negative
uint32 conversion,input-output alias write order,derived IVs,immutable context
copy and Digest Read/Show/Ord/length retain source behavior. Raw SHA512t>=512
init retains the zero branch;finalize is bounded0..512 to exclude source local
temporary over-read;public t is only224/256.

Raw logical context is208 bytes;Haskell reserves256 with uninitialized48-byte
tail excluded from public comparisons but retained as raw guards. Runtime
algorithm dictionaries do not enforce Haskell phantom typing or arbitrary lazy
list demands. Prefix loops are finite tested buffers. An actionable representation gap remains:
Kotlin len+144 can overflow signed Int for otherwise valid ByteArray lengths
above Int.MAX_VALUE-144,while the original uint32 bound still fits. Followup
must use overflow-safe loop/index comparisons and virtual boundary probes;
this does not require claiming a real huge allocation test. Other huge-buffer
execution remains an unverified resource limit. This accepted bounded
checkpoint is not full-module equivalence. Host endian/alignment/strict-alias casts,
JVM timing/physical scrubbing and other ABIs remain explicit limits. ChaCha
StateSimple/DRG/Probabilistic/Prime,other signatures/trust/chain and remaining
local:613/full Pandoc623/calibre624–626 stay OPEN.


### Builder table family and lazy compatibility bridges

Twelve public table exports and three internal algorithms now use source lazy
placement triples/remnants,Long widths,clipping and independent head/body
overhangs. Independent original replay matched17559 finite responses plus52
demand/deep observations and44 pins. Malformed/nonpositive spans,Long MIN/MAX,
boundary-predicate demand,infinite padding prefixes and50000-row spines remain
source observations;truncated prefixes are explicit. The unchanged iterator
Placement convenience remains separate from the source three-field contract.

Only six Tables bodies and three Builder table-entry bodies delegate to the
new source adapters. Nine retained before/after legacy repros prove unwanted
second-row/cell/header forcing is removed. Original Builder/Tables Git bytes
and historical Many manifest are retained;own comparison confirms the current
Many manifests change only Builder's source hash. Fresh5127 Many original
responses pass without rewriting inputs or expectations.

Final Gradle62661 five XMLs contain37101 actual zero-failure/error/skip cases:
17621 new (17559finite,52demand,ninelegacy,integrity),14316 original property
assertions including13500 table predicates,5128 Many,14 Tables and22 Builder.
Actual eleven original property reports×1000 remain supporting native evidence.
The earlier51890 run's37100 behavior passes and lone integrity-path failure
remain separate;only the test's kotdoc.root lookup was corrected. Bounded
512MiB/256KiB proof passed. Own39 frozen hashes,three new major classes and104
exact notices pass on the retained immutable final JAR;installDist matched.

Strict legacy List arguments remain JVM conveniences;explicit source root/
list/triple adapters provide the represented lazy contracts,not arbitrary GHC
heap equivalence through every old signature. Generic Many/count classes,
remaining Builder/Data.Version/JSONfilter,140 exports and350 instance members
keep local:611 and full Pandoc/calibre scope OPEN.
