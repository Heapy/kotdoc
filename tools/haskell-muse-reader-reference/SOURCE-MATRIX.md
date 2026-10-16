# Complete Muse reader scope and source matrix

Pinned Pandoc e51c9c6054c8f4ec5c3209d5abe10939dfe2963e; pandoc-types
b2cbcefc4547296f47b8120740997a9c021d756f. Readers/Muse.hs is1012 lines,
SHA256 e3f2d6050ed460386cc0e2b2dde7b30af6670fe4cba5f0bf82d5c232b2c02241.
Tests/Readers/Muse.hs has250 runtime leaves,1580 source lines,
SHA256 e378c2fee2c6c05e4331c74830cd0c0fcd5d7ba6c9e5f01852d2504640d64a38.

There was no partial Kotlin Muse reader to preserve or patch. This checkpoint
translates the complete module. It does not implement Muse writer (164 original
unit leaves and two Old goldens), RST writer (997 source lines/22 unit leaves),
or the remaining mechanically registered text formats under local:618.

| Original functions/state | Kotlin translation / source branches | Independent evidence |
| --- | --- | --- |
| readMuse, MuseState, MuseEnv, HasReaderOptions/IdentifierList/LastStrPosition/LogMessages, updateLastSpacePos, parseMuse | ReaderParsec environment outside immutable parser state; complete options/meta/identifier/last positions/messages/notes; report before final Future demand | all250 literal tests,2464 branch observations, source chunks, duplicate IDs/notes and cycles |
| F/Future, allowPara | Existing source-backed Future/Deferred; final-state metadata/notes; local inLink/inPara; deletion before recursive note evaluation | note-cycle1..17/both modes; forward refs/meta; deep notes1024/4096 and directive-inline2048 |
| lchop, rchop, unindent, dropSpacePrefix | At most one boundary LF; ragged Text.transpose common space/tab prefix | original Example/Example tag tests, branch raw tags and tabs |
| atStart, noSpaceBefore, firstColumn, eol, getIndent | Position equality and exact last-space/last-string updates; column vs consumed-space count | original inline/list/header cases; modes; tabs/options/chunks |
| openTag, closeTag, htmlAttrToPandoc, parseHtmlContent | Case-sensitive tags, duplicate first lookup, id/class extraction, remaining attrs; indent-matched close, Reader allowPara | original tags; malformed/mismatched tags; branch attributes; quotes/lists |
| parseDirectiveKey, parseEmacsDirective, parseAmuseDirective, directive | Letter/hyphen key; one-space/single-line Emacs vs required-space/multiline Amuse; cover translation; deferred metadata overwrite | original Directives; branch directives/forward notes; deep2048 unique directives |
| parseBlocks, parseSection, parseBlocksTill, listItemContentsUntil | Exact ordered continuations and end consumption; paragraph permission across blank lines; list/block/paragraph precedence | all250; six extension variants;500 mixed cases; nested malformed quote/list/tag cases |
| parseBlock | Original trace only on Emacs-note block path; Future evaluated against default state for trace | separate exact stderr trace requests; normal success/log tests |
| blockElements, comment, separator, pagebreak | Every ordered block alternative; column-one comment and optional single-space text; four-plus dashes; six spaces pagebreak | original Blocks and boundary/mixed corpus |
| headingStart, emacsHeading, amuseHeadingUntil | Optional preceding anchor; single/multiline mode; registerHeader using default-state content then deferred final content | original Headers; all six extension variants; duplicate IDs; source chunks |
| example, exampleTag, literalTag | Raw delimiters, unindent, attrs, style default; exact closing/eol requirements | original Example/Literal/Example tag; raw/attribute/malformed branches |
| centerTag, rightTag, quoteTag, divTag, biblioTag, playTag | Center/right ignored; blockquote/div attrs; Amuse-only biblio/play | original Blocks; nested tags; quote1024/4096 |
| verseLine, verseTag, commentTag, lineBlock | Optional nonempty NBSP prefix; empty lines; indentation; backtracked separator; discarded comments | original Verse/Comments;250 literal tests; verse/malformed variants |
| paraContentsUntil, paraUntil | Trim lifted Future; end parser under inPara=true; no nested paragraph; return actual terminator result | original paragraphs, mode variants, mixed cases, blocks10000 |
| noteMarker', noteMarker, addNote, amuseNoteBlockUntil, emacsNoteBlock | Nonzero numeric bracket/brace IDs; original update order and duplicate positions; Amuse indentation vs Emacs remainder blocks | all original Footnotes, cycles, malformed IDs, exact ordered logs; notes1024/4096 |
| bulletListItemsUntil, bulletListUntil, museOrderedListMarker, orderedListItemsUntil, orderedListUntil | Leading indent; style detection/order; same-style continuation; original marker column/blank lines | original Lists/Nested lists/Blank lines; branch types; lists256 |
| descriptionsUntil, definitionListItemsUntil, definitionListUntil, anyListUntil | Mode-dependent leading spaces; multiple descriptions; nested terms; paragraph-vs-list precedence | original definition-list cases; six mode/identifier variants; nested tag/list branches |
| MuseTable/MuseTableElement, museToPandocTable, museAppendElement, tableElements, elementsToTable | First header row, extra headers/body/footers reordered; first caption wins; max ragged width; compactifyTable | original Tables; headers/footer/captions and malformed rows |
| museGridPart, museGridTableHeader/Row/RawLine, museGridTable | Exact column counts; trimr/unlines/transposition; nested parseFromString resets/restores lastStr only; body-only tables | original grid tables/nested grid/examples; chunk/field branches |
| table, tableParseElement/Row/Header/Body/Footer/Caption | Raw current-fragment separator heuristic with cross-chunk allowance; inline separator precedence; caption terminator | original Tables; raw-line/mixed/chunk tests; table2000 |
| inline', inline, endline, whitespace, br, parseAnchor, anchor, footnote | Ordered dispatch; no implicit URI; soft break guard; first-column anchors; no notes in links; final-state resolution | original Inlines/Anchors/Footnotes; six variants; cycles; chunks |
| emphasisBetween, inlineTag, strongEmph, strong, emph, underlined, strongTag, emphTag, superscriptTag, subscriptTag, strikeoutTag | Opening/closing word/space guards; ordered asterisk lengths; Emacs-only underline; nested tags | original formatting tests;500 mixed; inline4096 |
| verbatimTag, classTag, inlineRtl/Ltr, nbsp, code, codeTag, mathTag, inlineLiteralTag | Raw text vs Builder.text; class-name attr; direction wrappers; NBSP; non-detached code terminator; raw styles | original Code/RTL/LTR/Literal; malformed and attr branches |
| str, asterisks, symbol | GHC alphaNum fast path and lastStr update; unsplit literal asterisk runs; nonspace scalar | original Inlines; mixed/Unicode corpus; symbols20000 |
| linkOrImage, linkContent, link, image | URL prefix priority; scoped nested-link prohibition; all nine image suffixes, width and r/l/f attrs; nested descriptions | original Links;72 suffix/width/alignment branches; malformed nested links |

Source helper dependencies: Parsing.General, Future, Capabilities, Sources,
Shared.compactifyTable/registerHeader/trim/identifier helpers; pandoc-types Builder,
Definition and Walk; Data.Char GHC9.12.2 Unicode16 predicates. Muse uses the already
reviewed narrow DokuWikiIdentifiers Unicode16 helper without modifying it. This
avoids the known global CoreIdentifiers Unicode17 drift; local:612 remains open
for other consumers. Persistent private block/list concatenation preserves
Sequence append behavior without retaining quadratic materialized tails.

Original test wiring changes only module imports/test-tree capture. Tagged
ReaderFunction wrappers record the exact amuse/emacs/explicit-auto-identifiers
options from each original function call. Inputs and expected expressions remain
unchanged. All250 expected literals are executed against the copied original
reader and checked before capture; Kotlin never supplies expected values.

Command inventory: actual Muse-input commands are4172#1/#2 (Muse output still
requires the separate writer) and5107#1/#2 (DokuWiki output applicable here).
There are no Old reader goldens. Original Muse writer goldens and all other
text-format/alias/options/CLI/Lua/filter/integration requirements remain open.

Final library proof accounting: 250 literal original tests +2464 deterministic
source-derived observations +277 expanded observations +11 deep observations
+4 exact trace observations =3006 observations. One separate exhaustive assertion
compares all1,114,112 original GHC isLetter bytes with the reused Kotlin predicate;
original isLetter/isAlpha equivalence is independently observed. Thus JUnit has
3007 cases, not1,114,112 additional tests. Combined persistent proof passes with
512MiB heap and256KiB stack. Expanded corpus includes24 Long SourcePos/indentation
boundaries; derived counts stay Long and count parsing allocates lazily.

CLI original250 all accept supported Muse options. 249 match literal library AST;
Blocks/Example/Tabs differs because App.Input expands tabs before invoking the
reader. Both independent expectations are retained. Additional complete CLI
observations cover35 input boundaries,22 warning/error/trace cases,16 application
option cases (including unsupported-extension rejection), filename inference and
default HTML. Warning continuation indentation is canonical Class.IO behavior;
binary typed errors retain their distinct unindented rendering. Commands5107#1/#2
use exact original Tests.Command Markdown extraction and App.ensureNl output.
