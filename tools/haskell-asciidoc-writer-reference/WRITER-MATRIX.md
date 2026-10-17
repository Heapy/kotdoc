# AsciiDoc writer source and test matrix

Pinned Pandoc e51c9c6054c8f4ec5c3209d5abe10939dfe2963e. Entire 912-line module is in scope. GPL-2.0-or-later source notices retained.

This is a writer checkpoint. AsciiDoc reader, full registry closure, general App options and other readers remain open. `remaining-registry.json` enumerates both pinned registries; statuses deliberately do not infer implementation from registration.

## Source functions

| Function | Line | Current evidence / next obligation |
|---|---:|---|
| `unsnoc` | 61 | Inactive CPP compatibility branch: base >=4.19 uses Data.List.unsnoc. |
| `defaultWriterState` | 79 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `writeAsciiDoc` | 92 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `writeAsciiDoctor` | 98 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `writeAsciiDocLegacy` | 102 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `pandocToAsciiDoc` | 110 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `escapeString` | 138 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `olMarker` | 177 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `needsEscaping` | 187 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `blockToAsciiDoc` | 201 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `bulletListItemToAsciiDoc` | 460 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `taskListItemToAsciiDoc` | 477 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `addBlock` | 485 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `listBegin` | 505 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `orderedListItemToAsciiDoc` | 516 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `definitionListItemToAsciiDoc` | 529 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `blockListToAsciiDoc` | 549 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `inlineListToAsciiDoc` | 559 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `setIntraword` | 601 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `withIntraword` | 604 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `inlineToAsciiDoc` | 608 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `imageArguments` | 748 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `adjustHeaders` | 780 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `adjustFooters` | 805 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `adjustFooterCell` | 833 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `adjustEmptyRows` | 855 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `decrementRowSpanInCell` | 897 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |
| `applyEmptyCells` | 904 | Translated in owned Kotlin draft; independent original/branch/helper/deep proof executed. Final integrated JUnit/package/CLI gate pending. |

## Source contracts and evidence

- All Block/Inline constructors, section Div dispatch, figure caption conversion and body selection.
- Ordered intraword state evaluation, nested inline lists, link comma walk, ignored multiblock notes.
- Modern versus legacy escaping/quotes/task lists/math; paragraph olMarker and bracket guards.
- Header unique identifiers: exact text-2.1.2/GHC Unicode-16 profile, flags, collisions and nested inline handling.
- List continuation, nested marker level, definition marker restoration and inList math separator.
- Modern table headers/body/footers/span adjustment; empty-row consumption; cell diagnostics and source effect order; nested-table cutoff.
- Metadata/template demand, titleblock/toc/math fields, wrapping/offset and nonfinite/overflow width conversions.
- URI parsing, POSIX dropExtension, dimension parsing and pixel/percent formatting.
- Deep finite and lazy/poison demand cases, default/trace ordered logs.

## Original tests

12 original leaves copied by observation-only module/import transforms. Oracle passed upstream Tests.Helpers `lines` comparison. Both literal expected text and raw actual output retained in `fixtures/original12.jsonl.gz`; All12 raw original outputs pass Kotlin; source literal expectations remain separately retained.

Four Old goldens: basic and tables for asciidoc and asciidoc_legacy. All four outputs equal the verbatim original golden files and pass Kotlin.

31 canonical command blocks retained in `fixtures/commands-inventory.json`; four current native/Org input routes, 27 reader-dependent routes. All31 writer AST comparisons pass Kotlin; four complete current routes are independently captured and pass the first151 CLI replay. Other full commands require their readers/options. The --verbose block additionally depends on global App verbosity.

## Reuse audit

WriterTables spans/allRowsEmpty/tableBodiesToRows source closure reused with2460 original table adjustment probes and branch/deep consumer evidence. DokuWikiIdentifiers carries accepted text-2.1.2/GHC16 uniqueIdent profile; AsciiDoc cannot use generic Identifiers (text2.1.3). WriterMetadata and DocLayout rendering/offset are reused after source consumer audit; private section/chomp resource closures are documented below.

Library Kotlin parity is independently exercised; integrated tests/package and complete183 CLI replay remain final gates. Neither the AsciiDoc reader nor all format/global App routes are claimed complete.

## Source nonproductive domain

A generated table with zero colspecs and an empty Row reaches `makeRow (Row attr []) = makeRow (Row attr (replicate (length widths′) emptyCell))`; when `length widths′ == 0` the recursive argument is unchanged. Original oracle child PID92237 was observed at100% CPU and explicitly stopped (SIGTERM15), not restarted on timeout. The finite branch corpus excludes this nonproductive input domain. This does not alter any captured expected value. Kotlin behavior outside the original productive domain is not claimed by that finite corpus.

## Dependency closure and resource boundaries

`AsciiDocSections` translates only `Shared.makeSections False Nothing` with empty offsets. It preserves the lazy minLevel query (first needed at a Header), Header/Div merge, attributes and traversal order. The unobserved numeric components of lastnum are abstracted out: materializing their range eagerly would break source-productive Header maxBound cases. Four independent Int64 boundary observations retain this distinction. This is not a general makeSections/numbering/base-level port.

`AsciiDocChomp` applies the source trailing Concat/Prefixed rules modulo Concat associativity, preserving untouched prefix sharing. It does not promise Doc Show/Eq tree representation. Finite probes compare canonical structure, render, offset, minOffset and isEmpty. Original width/offset/render implementations remain shared dependencies. The native Haskell Doc type can contain arbitrary bottoms that the eager Kotlin Doc fields cannot represent; no unrestricted bottom equivalence is claimed.

Shared makeSections div1024 overflow and shared chomp bullet4096 heap failure are retained in `shared-make-sections-before.log` and `shared-chomp-before.log`, with immutable deep72 input/answers. These are concrete remaining local:612 issues. Intermediate strict-reassociation drafts also failed heap512MiB and are not accepted evidence. The final sharing implementation must retain all72 source successes at512MiB/256KiB.

`sections-second.jsonl.gz` preserves an independent second original invocation.140 synthetic duplicate-attribute cases are non-idempotent in the source itself. An exploratory generic idempotence assertion failed; it is not a Kotlin/source mismatch. The exact two original passes are compared separately, and the explicit Tests.Shared idempotence example remains checked. The original first-pass payload is unchanged.

Haskell Text domain: T.singleton replaces surrogate Char positions with U+FFFD before case conversion. The exhaustive case-mapping harness normalizes that boundary before invoking helpers that accept Text. GHC Char classifiers still run over all1114112 positions. Production Native-reader Text.pack normalization is independently captured through CLI. No invalid UTF16 String equivalence is inferred from the Text-domain proof.

Demand corpus:9 original observations.8 have Kotlin finite/list/StrictData construction counterparts; code/ignored-attrid is native-only because there is no deferred String field carrier. `meta/ignored` is a constructor StrictData control, not a claim that the writer forces otherwise ignored metadata. `note/head2` caught and corrected source head-constructor demand before rejecting a multi-block footnote. No arbitrary-bottom closure is claimed.

## Coverage accounting

Direct upstream:12 Tests.Writers.AsciiDoc leaves, four Tests.Old goldens.
Independent synthetic source observations:1204 writer branches,158 additional
figure/path/state/options cases, four Int64 headers, four nonfinite widths,
2460 table adjustments,12644 escape/paragraph guards,5804 identifiers,
72 deep recipes,281 first +281 second section normalizations,11154 chomp
structures/render/metrics, nine demand controls (eight Kotlin counterparts).
The31 command-AST observations exercise only the writer. The151 original App
observations include four complete command blocks;32 additive phase observations
exercise warning/template/input ordering. These are not183 upstream test leaves.
The classifier/lower/upper/casefold proof spans1114112 Char positions separately
from the finite observation and JUnit registration totals.

The current JUnit draft registers each original/writer/command-AST observation;
large dependency corpora are grouped executions with their exact inner count
in the test name. Saved XML registration counts must not be presented as the
number of upstream tests or exhaustive-domain positions.
