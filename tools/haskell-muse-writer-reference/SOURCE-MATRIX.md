# Complete Muse writer source and original-test matrix

Pinned Pandoc e51c9c6054c8f4ec5c3209d5abe10939dfe2963e;
pandoc-types b2cbcefc4547296f47b8120740997a9c021d756f.
Writers/Muse.hs732lines SHA256c4e39ce829af38c8ace2f6c562e3934eae6c4310f12fd0c4058dbcefe431062f;
Tests/Writers/Muse.hs658lines SHA25642804bfe9fa28fe61e5e641f0ad936a550b1c700b53bd4a1c26f8c1957c642ed.
All164 original runtime leaves are retained. The original Helpers.test compares
Prelude.lines; literal expected bytes and separately observed original raw outputs
are both retained, preventing accidental weakening of final-newline behavior.

| Source functions / branches | Kotlin implementation | Independent evidence |
| --- | --- | --- |
| WriterEnv, WriterState, evalMuse, writeMuse | Immutable per-call environment, per-session notes/number/IDs/useTags; heap continuations for blocks/inlines | all164;3378 observations; nine deep ASTs at512MiB/256KiB |
| pandocToMuse | metadata before body, state shared with metadata callbacks, notes after body; WrapAuto-only width; defField body and optional template | default/custom/meta-json templates, metadata notes/IDs/body collision, variables, two Old goldens |
| catWithBlankLines, flatBlockListToMuse, blockListToMuse | Every final block retains BlankLines0; consecutive same-type/style lists two blanklines; DefaultStyle normalizes Decimal; topLevel=not insideBlock | original list tests,49 style pairs,84 marker/boundary/delimiter cases,372 original native documents |
| simpleTable | caption/head/rows render in source order for widths, then body rows render again; ragged transpose/max/offset, noHeaders separators, optional top nest/caption | all original table tests; repeated note side effects, both wraps and all reference locations |
| Plain, Para, LineBlock, CodeBlock, RawBlock | literal vs breaking spaces, one-line mode, unconditional format literal tags, precise blankline/cr operators | original block tests;372 native ASTs and mixed cases |
| BlockQuote, Div, Figure | quote uses flat list and nest0 to chomp trailing blanks; Div flat; Figure source figureDiv body/caption order | original quotes/divs; native figures; quote4096/div4096 |
| OrderedList, BulletList, DefinitionList | Period forced; original marker enumeration; original reset points; hang/nest/topLevel semantics; label width uses codepoint length of rendered lines | original lists; style pairs/bounds; nested lists512/definition256 |
| Header, HorizontalRule | oneLine inline render, unique ID inserted even when explicit anchor rendered; default identifier extensions; top-only stars | original headings; duplicates; Unicode17 affected ranges/four extension variants; empty/negative levels |
| Table | toLegacyTable simplicity/width tests; simple only when>1 column, otherwise grid with head rows moved to body, caption discarded on grid path | two Old goldens; original one-column grid test;64 grid/span/foot/width/wrap variants |
| currentNotesToMuse, notesToMuse, noteToMuse, blockToMuseWithNotes | drain queue before rendering; preincrement number for full batch; source EndOfBlock/Section behavior and exact env updates | all original note locations, simple-table effects, metadata notes, nested-note4096 |
| escapeText, replaceNewlines, startsWithMarker, containsNotes, shouldEscapeText, conditionalEscapeText | complete codepoint predicate guards, nonzero decimal-note syntax, nested bracket scan, exact closing tag splitting | source-derived escape/context corpus and all original string tests |
| preprocessInlineList, replaceSmallCaps, removeKeyValues | math conversion before normalization, top-level Cite expansion, Code key-values only; retains original unreachable-node errors after unusual Cite expansion | original math/normalization; native corpus; malformed TeX logs; SmallCaps/Cite failures |
| normalizeInlineList, fixNotes | removes empty Str; merges exactly specified adjacent constructors; Code nullAttr; RawInline same-format only; Space/SoftBreak before Note becomes literal space | original normalization/4172-sensitive shapes;200 mixed cases |
| startsWithSpace, endsWithSpace, urlEscapeBrackets, isHorizontalRule, stringStarts/EndsWithSpace, fixOrEscape, inlineListStartsWithAlnum | source guards and lazy lookahead order | original inline tests; marker/semicolon/newline contexts; all1,114,112 points for all five used Data.Char predicates |
| renderInlineList, inlineListToMuse, inlineListToMuse', emphasis | original state mutation and environment order; reset useTags per inline-list; one-line and newline position; nested asterisk lookahead | all original emphasis/spacing tests,49 nested format pairs across six contents, mixed cases, emph4096 |
| Str, Emph, Strong, Underline | full nested special cases/tags/space decisions; Underline intentionally renders contents twice when amuse is off | originals; all format pairs; options variants |
| Strikeout, Superscript, Subscript, Quoted, Code, RawInline, LineBreak, Space, SoftBreak | exact resets/quotes/raw formats/code terminator splitting/oneLine/WrapPreserve | originals; all wraps; raw/native corpus |
| Link | escapeURI equality test, nine image extensions with POSIX takeExtension semantics, bracket escaping and link-description env | original links plus suffix/path/bracket corpus |
| Image | recursively strips fig: prefixes; alt rendered even with title; conditional title escaping; percent round only amuse; both alignment flags in source order | width/title/classes/options product, nested fig:, both Old goldens |
| Note, Span | append deferred note body; exact numbering/state reset; dir first lookup; class first only; anchor-only and empty Span cases | originals; nested notes/metadata/table order; native spans |

Source helpers reused without edits: DocLayout/TemplateCompiler/Renderer/Values,
WriterMetadata, DocumentShared.toLegacyTable/orderedListMarkers, TableSimplicity,
AnnotatedTable/GridTable, PandocMath and its Kotlin math pipeline, URI.escapeURI,
Dimensions.dimension, and accepted narrow DokuWikiIdentifiers Unicode16 behavior.
Grid callback docs are computed through heap continuations in original head/foot/
body callback order before using existing GridTable, avoiding nested JVM recursion.
Private Doc trees preserve sharing until final iterative layout rendering.

Counts:164 literal originals+2962 deterministic source-derived/options/native AST
observations+219 expanded table/metadata/marker/Unicode observations+9 deep+2 Old
byte goldens+4 decimal-overflow percentage widths+18 caption helper probes=3378 observations. One exhaustive five-predicate assertion is one
JUnit case, yielding3379 cases. All bounded persistent checks pass512MiB/256KiB.
The372 native AST inputs come from the accepted original Doku oracle payload;
only input documents are reused, Muse outputs are produced by this complete
original module. Exact source and payload provenance is pinned by the importer.

Native marker resource limit: original alpha markers perform linear drop(start-1)
over an infinite26-letter cycle. A probe of wrapped LongMIN alpha would drop
about9e18 elements and was intentionally stopped, not treated as a passing result.
Finite alpha cycles(-1,0,27,52) and all numeric/Roman Int64 bounds are independently
captured. This does not change expected values or production helper semantics.

Full command4172#1/#2 requires both reader and writer and is checked through the
actual CLI. The original Markdown/Tests.Command lines/unlines extraction defines
expected bytes; raw fence regex newline interpretations are not used. Both Old
writerTests use columns80 and pandoc-version empty; basic activates default.muse,
tables does not activate a template. Readerca6e019 remains accepted and unchanged.

Remaining global scope: RST writer997lines/22 original units/8182, other registered
text formats and aliases, broader integration/Lua/filter/options closure under618,
shared identifier Unicode drift612 and general CLI-M metadata621 remain open.

Source audit adds four independent decimal percentage widths at finite Double
limit and numeric overflow, with amuse on/off. Rounding reuses the original-backed
HaskellNumber.round helper, including GHC non-finite Integer bit decomposition.
Previously captured3356 observations remain byte-identical.

Application failure probes retain original192 observations unchanged and add12
separate template/ordered-warning cases. Muse custom template compilation failures
throw PandocTemplateError (App.OutputSettings110), preserving exit5 and exact error
text, including LF/Unicode filenames; successful templates preserve metadata-before-
body TeX warning order. The unchanged generic exception path for other formats
remains a shared621 gap. Main now accepts standard --output= alias, independently
needed by Muse file inference; this resolves that alias only, not general CLI621.

Caption helper audit: existing DocumentShared.blocksToInlines is recursive, and
toLegacyTable eagerly flattens even an ignored grid caption. Original module
succeeds at4096 nested Div captions for simple and grid tables; old Kotlin threw
StackOverflowError under512MiB/256KiB. Muse locally translates the complete
Shared.blockToInlines/blocksToInlines functions (877–923), all14 Block constructors,
using heap continuations and original Builder concatenation/separators. Legacy
shape conversion receives an empty caption; only the simple path flattens the
real caption. Two original4096 results,15 complete helper branch cases and one
original bottom/poison-caption demand result are retained independently. The
grid poison caption element must not be forced. Shared DocumentShared remains
unchanged and its recursion/eager-demand gap remains for other consumers.
