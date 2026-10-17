# Full Org reader checkpoint (in progress)

Pinned Pandoc e51c9c6054c8f4ec5c3209d5abe10939dfe2963e and pandoc-types
b2cbcefc4547296f47b8120740997a9c021d756f. Production remains Kotlin only.

| Module | Lines | Scope | Current gate |
|---|---:|---|---|
| Org entry |45|sources/final-newlines/ReaderT/read errors/log report|original oracle copied; Kotlin pending|
| BlockStarts |160|block boundaries/list markers/GHC Int/LaTeX env guards|Kotlin helper corpus passes; full-reader pending|
| Blocks |1009|all block grammar/tables/grid/include/code/results/notes/lists|source read; pending|
| DocumentTree |423|Future headlines, tags/pruning/todo/planning/properties/IDs|full draft syntax compiled; behavior pending block grammar|
| ExportSettings |206|all options, bool/list/integer/unknown warnings|Kotlin helper corpus passes; integration pending|
| Inlines |1011|markup/math/entities/TeX/links/cites/notes/macros/quote-local|linked inline draft compiles; independent AST observations running|
| Meta |305|metadata collection, link formatters, export filtering/macros|full draft syntax compiled; behavior pending block grammar|
| ParserState |310|all state fields/Future/local quote/state capabilities|source read; pending|
| Parsing |238|Org stateful newline/anyLine/parseFromString/context wrappers|552 combined helper observations pass; indentWith pending|
| Shared |91|paths/images/languages/export flags|552 combined helper observations pass|

Total3798 lines; SOURCE-FUNCTIONS.json enumerates284 top-level signatures, not a
claim of284 covered branches. Full branch mapping will bind actual Kotlin functions
and source oracle cases before freeze. All modules must be implemented;312 tests
alone do not establish source completeness.

Original312 leaves: Inlines104, BasicBlocks128, Meta38, Directives42. All312 original
assertions pass the exact copied-module oracle, including8 file/include effects;
fixtures/original312.jsonl.gz preserves its independent expected documents. This is
now also executed on the complete linked Kotlin draft:312/312 documents match
at512MiB heap /256KiB stack (reader-proof2.log). This is original-suite parity,
not a source-completeness claim. One Old reader golden:
org-select-tags.org to org-select-tags.native, standalone native output.

Existing ReaderParsec/Future preserve parser-state rollback separately from ReaderT
quote-local context. Reuse requires source/demand audit. Exact text2.1.2 mappings,
GHC Unicode16 predicates/simple-char case, header identifiers, TeX/math/entity APIs,
URI and Posix/Windows filename contracts must be checked. Grid's accepted raw
tracer is reusable; RstTableAdapter is Identity-specialized, so Org needs a genuine
Future-valued adapter rather than forcing cell futures early. No existing shared
adapter/source is changed by current drafts.

Include uses Parsing.General.insertIncludedFile and Class.readFileFromDirs: strict
UTF8 conversion, search-dir retry, source-name/line positions, include loop stack,
line slicing, warning ordering, state restoration and minlevel walk all in scope.
It does not use RST's raw/CSV HTTP fetch route. General HTTP/IO/globalCLI gaps remain
separate; Org citations produce AST and do not claim citeproc/full CSL Locale.

Semantic search returned stale revision515ae343a907; known pinned paths were used.
Source/test notices remain in copied files. Private dev package registration exposes
unchanged cached original hidden TeX types (same package unit id/ABI/library paths);
its source/registration/binary/buildplan pins will be retained before final freeze.

Current isolated Kotlin evidence: 552/552 original helper observations pass at
512 MiB heap / 256 KiB stack (helper-proof2.log). The inline-only AST probe is
a development observation, not execution of the complete Org reader: its paragraph
wrapper does not implement block parsing or final document-tree processing.
Compile-only block placeholders live exclusively in /tmp and are never production.

LaTeX runtime profile audit: pinned Class/PandocPure.hs default stTime is
posixSecondsToUTCTime 0 and stTimeZone is utc. The draft default bridge uses
1970-01-01 and epoch/UTC, with an explicit zoned-day callback for an effectful
runtime. General runtime completeness, include and LaTeX callback demand remain
required before acceptance; no HTTP/citeproc closure is implied.

Current full grammar is linked without compile-only placeholders. Independent
original312 request/expected bytes are unchanged. The first full Kotlin replay
matched240/312; source fixes added the missing try on print_bibliography, the
per-alternative rollback in orgParamValue, and avoided an empty Str in verse.
Second replay96371 terminated0 with312/312. Expanded branch/runtime/demand,
Old golden, error/trace/resource proof and CLI integration remain required.

The grid adapter uses Parsing.GridTable's general parseFromString', not Org's
lastPre-resetting parseFromString. It resets/restores lastStr, keeps other cell
state changes, and sequences cell parser effects before final-state Future values.
The case-insensitive string helper now uses exactly char(toUpper c) <|>
char(toLower c), without a global try; Unicode equivalence by lowercasing would
accept a different domain and is deliberately not used.

Finite reader gates (2026-10-17, isolated accepted writer runtime): original
312/312, expanded 973 plus Old/org-select-tags = 974/974, and primitive-effect
38/38 observations passed. These do not by themselves close the full 3798-line
reader scope. Original deep14 expectations were captured independently; initial
bounded Kotlin replay passed10/14, failing author accumulation1024/4096 and
nested headline256/1024. The original fixture bytes are retained unchanged.

Org-local metadata accumulation now retains lazy old metadata fragments and
flattens them with an explicit work stack. Independent private collectLines /
collectAsList observations cover58 distinct finite inputs (all MetaValue forms,
all Block forms, append4096 and deep old values) at512MiB/256KiB. Two carried deep
old values use separate original Show observations, because the shared JSON codec
has its own depth limitation; the original JSON observations remain preserved.
Export-disabled author metadata remains undemanded. This is a finite internal
reader proof, not a public claim about arbitrary Haskell bottoms.

Nested headline output exposed a finite consumer resource defect in the accepted
Builder.orderedList AST bridge: repeated GenericAstData encode/decode wrappers
accumulate per nested level. The Org-local finite constructor is exactly pinned
Builder.hs473–478 (singleton OrderedList/default attributes/map toList), retaining
child list carriers and fields. No shared Builder or Generic implementation was
changed. The shared consumer resource requirement remains open for611/612;
initial stack trace is retained in /tmp/kotdoc-org-reader/deep-proof1.log.
Corrected deep replay and final full CLI replay remain in progress.

Consolidated corrected-full.jar compiled all reader and narrow CLI drafts without
stubs. Bounded replays passed original312, expanded973 plus Old golden, helpers552,
primitive effects38, and metadata collector58. A new immutable profile corpus1541
observations covers all1433 exact text2.1.2 lowercase mapping header/collision
inputs plus27 delta points across four auto/ascii/gfm/no-auto profiles. All1541
passed Kotlin at512MiB/256KiB. Source profile input mappings are inherited from
accepted identifier-profile fixtures, while every document/log expectation comes
from the copied pinned Org reader oracle. This is a finite affected-domain proof;
exhaustive shared classifier proofs remain separately bound upstream artifacts.

## Source closure and final audit map

The function inventory retains all284 source definitions, including definitions
inlined into Kotlin alternatives/records. `implementationFiles` lists the full
Kotlin closure for each source module; it is not a one-symbol-name equivalence.

| Source module | Branches/state contracts | Independent exercised families |
|---|---|---|
| Org45 | two final newlines; initial source name; parse error conversion; final document |312 original;49 positions/options;46 available full commands |
| BlockStarts160 | line/column guards; heading/list/table/drawer/meta starts; note cookies |552 helper observations;312 original blocks |
| Blocks1009 | attrs and rollback; raw/verse/code/results/export/admonitions/drawers; block ordering; lists/tasks; grid and Org tables; includes; delayed document output |973 expanded;38 effects;positive tab stops;14 deep;original blocks/directives |
| DocumentTree423 | delayed title/body/children; select ancestors then exclusion; archive/comment/depth ordering; root removal/reserved identifiers; properties/planning/TODO/tags |original headers;expanded tree/property cross products;1541 profile/collision cases;deep breadth/depth |
| ExportSettings206 | case-sensitive source keys; booleans; signed/wrapped integer settings; complementable lists; TeX/subsup/archive settings; unknown warnings |552 helpers;expanded before/after options;49 mixed warning/trace cases |
| Inlines1011 | emphasis border/last-position/newline state; balanced groups/math; citations/ref variants; notes/substitutions; links/images/custom formats; smart quotes; raw LaTeX secondary parsing and effects |104 original inline leaves;expanded citation/link/markup cases;38 primitive effects;deep balanced/footnotes |
| Meta305 | keyword order/rollback; final export filtering; lazy previous metadata accumulation; snippets/lists; macros15-depth; TODO sequences; link formatters |38 original metadata leaves;expanded metadata/macros;58 private collector inputs;deep author/ignored author |
| ParserState310 | complete state fields; final-state Future lookup; default export flags; macros/TODO/reference/note stores; parser options |312 original,expanded option order and deferred resolution,552 helpers |
| Parsing238 | ReaderT quote context; lastPre/lastForbidden/lastStr; general versus Org parseFromString; case-alternative consumption; include/source positions |552 exact helper outcomes/errors/remainders;49 boundary/trace/source-position cases |
| Shared91 | image extension and host path validity; Windows absolute detection; language maps; source text trimming |552 helper observations;expanded media/link inputs |

Grid uses the raw gridtables tracing API with Future-valued cells, general
parseFromString' lastStr reset/restore and source effect ordering. It does not
reuse the Identity-only RST adapter as if it had the same Future contract. Include
uses file-only readFileFromDirs, exact UTF8 conversion/catch/retry and raw include
stack identifiers; its line slicing/minlevel walk and source positions are covered
by originals, expanded cases and primitive-event observations. Org itself does
not introduce a default HTTP fetch route. Nested LaTeX receives the same explicit
PandocDataRuntime and zoned-day callback; default pure time is1970-01-01 UTC.

The reader's finite internal constructor adapters are not public generic lazy
protocol replacements. Nonproductive/arbitrary-bottom domains are not inferred
from successful finite fixtures. Shared codec depth and generic Builder bridge
resource limitations are recorded independently rather than hidden by expected
normalization or larger test stacks.

## Final author integration gate

Gradle69831 terminated0 after10m30s:3538 reader cases plus5354 accepted Org writer
regressions =8892 XML, all zero failures/errors/skips. The new reader dynamic
factories account for312 originals,973 expanded,1541 profile,49 boundary,14 deep,
552 helper,38 effect,58 metadata-collector cases and one Old golden. Fresh
original-module replay3541 passed; its four duplicate alternative observations
(two metadata and two deep Show) are retained without replacing old expectations.
The native primary observation count3537 (excluding four alternatives) plus the
Old golden equals3538 reader cases; this is case accounting, not a global
deduplication of equal input strings.

Durable installed reader39285 terminated0:446/446 exact exit/stdout/stderr,
12 missing writers pending. Durable accepted writer1918 terminated0:65/65,
five original complete commands. The new reader includes46 complete original
commands and closes nine Org-to-Org command dependencies. Org writer inventory
is therefore14 available /13 pending out of27; no old inventory was rewritten.
All42 owned installed file hashes remained stable through the final build.
All4299 prior core class bytes are unchanged; only Main and its dispatch lambdas
changed among old CLI classes. Package and historical/current bindings are in
`evidence/`, with unrelated MD5 license/resource timing explicitly distinguished.
