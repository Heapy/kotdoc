# Final AsciiDoc writer coverage

This supplements the progress matrix without rewriting its historical source pins.
The entire pinned912-line writer is translated; inactive pre-base4.19 `unsnoc`
CPP is separately identified. This does not claim a full AsciiDoc reader, global
CLI, Shared or DocLayout port.

| Source closure | Independent executed evidence |
|---|---|
| default state, modern/legacy/deprecated library entry points, metadata before body, template activation/context defaults |12 original leaves,4 verbatim Old goldens,32 metadata branch observations,8 extra metadata/header state cases; CLI aliases/default templates/inference |
| escapeString, olMarker, needsEscaping |12644 original scalar observations;216 text branch cases;14 extra paragraph literal guards; exact GHC16 predicates |
| all Block constructors, section Div, Plain/Para/LineBlock, raw format equality, code, rules, headers |1204 branch corpus and158 extras;8 LineBlock,20 raw,40 code,160 Div branches; Format casefold controls and Int64 headers |
| Figure caption/body selection, blocksToInlines closure, dimensions/URI/POSIX filename handling |150 media +40 dimension branch observations;10 extra Figure,26 filepath and18 paired-dimension cases; deep caption recipes |
| list item/continuation/definition markers, task items, nested levels and state restoration |36 list +56 ordered branch cases; deep bullet/ordered/definition/mixed recipes |
| inline list intraword set/restore, every Inline constructor, nesting, links/citations/math/notes |288 inline branches,10 note branches, nested-original tests,8 extra inline-code cases; deep emph/span/note; ordered diagnostics |
| modern tables, spans, headers/footers, empty rows, nested cutoff and source effect order |144 table branches,2460 original table-helper observations,64 extra table-order cases,2 nested-cutoff cases,4 nonfinite-width source recipes,4 Old goldens |
| unique identifiers/collisions/flags and exact case profile |5804 identifier observations; all1114112 GHC Char positions for classifier/lower/upper/casefold on the declared Haskell Text domain |
| makeSections False/Nothing/empty-offset private closure |281 first +281 independent second original calls;140 source non-idempotent duplicate-attribute cases explicitly retained;4 Int64 headers |
| private chomp/render/measure closure |11154 original concrete structures/render/offset/minOffset/isEmpty comparisons, reused session cache;72 deep recipes at512MiB/256KiB |
| partial state/ignored payloads |9 native AST controls:8 Kotlin counterparts (7 writer calls +1 StrictData constructor control),1 deferred-String native-only;8 additional Doc Concat/Prefixed WHNF/render controls native-only |

These categories overlap: e.g. one branch request can exercise several helpers.
They are not sums of upstream test leaves. The finite Kotlin observation count is
34121, executed through1425 behavioral JUnit registrations; large helper corpora
are grouped tests with their inner counts retained.208 repository source/tool/
fixture pins add integrity registrations, making1633 AsciiDoc XML registrations.
The exhaustive domain is separately accounted, never counted as JUnit leaves.

The integrated five-writer regression has13465 actual XML registrations:
AsciiDoc1633,Org5345,Muse3379,RST2725,DokuWiki383,all zero issues.
It does not relabel the omitted separately-owned Org demand-test class as run.
The copied final core JAR retains all4578 baseline class bytes; added classes and
CLI class deltas are enumerated by the integration manifest.

Original command extraction uses Tests.Command's actual Markdown parser. All31
writer-only AST observations run Kotlin. Four complete command routes run the
real Kotlin CLI;27 unavailable reader/option routes remain separate. Four exact
Old command lines additionally require global --quiet and remain pending despite
four library goldens and six App-without-quiet controls.

App coverage is183 independent complete observations (151 original capture +32
phase additions), including aliases/deprecation before templates/input, source
exit5/64/97 typed boundaries, options/templates/inference, text packing and four
complete commands. Final installed-copy evidence is separate from the earlier
isolated183 proof. Accepted Org65,Muse204+6Doku+2commands and RST96+21commands are
integration regressions, not additional AsciiDoc original tests.

See DEPENDENCY-GAPS.md for source-backed shared612 resource defects, exact
Native malformedUTF8/JSON general621 controls, native-only demand boundaries and
nonproductive source input. Local closures and typed AsciiDoc-only application
corrections do not silently repair those global contracts. Historical manifests
and all old expected payloads remain byte-identical; final integration evidence
adds current bindings rather than rewriting history.
