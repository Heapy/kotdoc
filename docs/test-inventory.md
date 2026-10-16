# Upstream test inventory checkpoints

This inventory preserves the full Pandoc → calibre MOBI → additional calibre
formats goal. It is **not** a completed port, complete global leaf inventory,
or coverage percentage. Local:622 remains incomplete; `docs/PORTING.md` remains
the project scope ledger.

`test-inventory.json` records pinned revisions, source paths/SHA-256, conservative
registry candidates, source assertions, executable Kotlin mappings, independent
expectation origins and gaps. Default checks run no Gradle, Kotlin, Haskell,
Pandoc conversion tests or calibre programs:

```sh
python3 tools/build-test-inventory.py
python3 tools/check-test-inventory-pandoc-types.py
python3 tools/check-test-inventory-dependencies.py
python3 tools/check-test-inventory-runtime.py
python3 tools/check-test-inventory-census.py
python3 tools/check-test-inventory-extraction.py
python3 tools/check-test-inventory-verifier.py
python3 tools/import-pandoc-json-fixtures.py --check
```

Explicit snapshot refresh is `python3 tools/build-test-inventory.py --write`.
`--snapshot PATH` verifies or refreshes an alternative snapshot. Refreshing is a
review action; it never generates conversion expectations. Hash verification
detects drift; it does not certify behavior or a mapping's correctness. Submodules
and vendored source trees must be present. The snapshot includes its generator
and extraction provenance, so extractor changes require a reviewed refresh too.

## Counting and existing Kotlin evidence

The complete main pandoc-types registration has **81 source tests**: 12 Walk
properties, JSON roundtrip, 56 paired JSON examples, nine normalization properties,
two table literals and Figure. The pairs have **112 assertions**; the complete
suite has **137 assertion/property registrations**. Generated trials and oracle
observations never add source tests.

The 58 literal examples have exact executable mappings: original literal JSON
bytes plus independently translated typed values in `UpstreamJsonTest`, and two
independent literal tables in `TablesTest`. Every mapping records its Kotlin
method/dynamic IDs and production path/hash. The independent JSON importer checks
its 56 fixtures against the pinned original; Kotlin creates no expected bytes.

All **81 source registrations now have explicit finite executable mappings**.
The 23 property links use original generators and independent original expectations,
including complete normalization/Figure outputs and Aeson bytes/decoder trees.
Historical result provenance is immutable commit
`66722c107b7663ded9c8f510a41e05c3e131a970` (**32,056 actual JUnit tests** with
zero failures/errors/skips), not current typed Read work or a fresh run by inventory.
The exact mapped Kotlin, production and fixture files must still match their
accepted artifact hashes in the checkout; mismatches fail verification.

The concrete mapped behavior identities total **20,419**: twelve Walk predicates
on 500 inputs (6,000), nine table predicates on 1,500 inputs each (13,500),
305 Figure cases, 500 JSON roundtrips, 112 paired literal assertions and two table
literals. These identities are reconstructed from the accepted real factory bodies
and fixture iteration, retaining actual factory dynamic ordinals. In particular,
list Walk predicates display `p_walk`/`p_query`, and Figure witness strings retain
JSON quotes. This is more specific than a name/prefix match.

The **137 assertion/property registrations** remain source units, not 20,419
source tests. Eleven separate native QuickCheck reports (1,000 successful trials
each) add eleven report checks, not Kotlin conversion trials. Historical total
32,056 includes other generator/shrinker/helpers and JSON/table guards; it must
not be presented as the count of these 81 source registrations. Historical raw
per-case JUnit XML is not committed; the immutable ledger documents its totals.

The verifier checks all 81 manifest/source IDs, all accepted source/payload hashes,
original eleven property excerpts and six exposed forall bodies, original Walk
predicate/callback bodies, real assertion contracts and production entrypoints.
JSON expectations retain original literal bytes and independently translated
expected typed values. The mapping evidence is source/fixture/code/result provenance,
not an inferred association by similar names. No expected values are generated.

Later finite support evidence remains separate: shrink commit
`fde33c3346204ff8f2eee836ef445fd302a0bdc4` records **33,284 tests** and vector commit
`a5c8fb8f8366b825db351057153b2f3160cfcdef` records **33,466 tests**. Their source
and payload hashes come from their own immutable commits. These observations do
not add source registrations or discharge typed Read/public API/universal demand
requirements. Earlier 6,185/7,375/17,613 checkpoints and the local:610 observation
corpus retain their distinct historical boundaries. Full local:611/622/Pandoc and
subsequent calibre stages remain open.

`mapped` means an identified source assertion has an explicit executable link,
not that its production module is complete. `partial` records missing evidence;
`unmapped` has no accepted executable mapping; `blocked` is reserved for external
prerequisites. All newly enumerated Command/Old/Lua records remain **unmapped**.
Their upstream commands are reproduction instructions, not Kotlin mappings.

## Pandoc Command: 1,870 source goldens

The original `Tests.Command` reads 1,116 immediate `command/*.md` files and selects
**top-level CodeBlocks** using `readMarkdown` with `pandocExtensions`. A textual
`% pandoc` search would be wrong. Stable IDs retain the original filename and
`#N` block registration ordinal; adding/reordering blocks changes subsequent IDs
in exactly the same way as the original registration.

`test-inventory-command-blocks.json` independently captures those selected blocks
using pinned Pandoc 3.12.1's Markdown AST. `--preserve-tabs` avoids the CLI input
layer expanding tabs (74 source files contain raw tabs). No raw CR exists in this
corpus; new CR inputs fail pending direct-library capture because CLI Input strips
CR. A bounded root-fence extractor also reproduces **every captured block** from
source bytes: leading indentation, fence length and comments matter. In particular,
1390's commented-out fence is not a test; 10984/4715/5899 use indented fences.
The extractor is not a general Markdown implementation: unsupported new syntax
fails comparison with the independently captured AST rather than silently
changing the leaf inventory.

Development-only independent capture/replay commands are:

```sh
python3 tools/extract-test-inventory.py --capture-command-ast
python3 tools/extract-test-inventory.py --verify-command-ast
```

These require pinned `pandoc 3.12.1` and parse source Markdown **only**; they never
execute the shell commands inside it. Ordinary verification requires no Pandoc.

Each record retains the original code text/hash, exact continuation-joined
command, input, combined golden, conventional stdout/stderr/status decomposition,
source hash and execution semantics. `Tests.Command` compares the **combined**
golden: prefixed stderr, stdout and an appended nonzero status marker, with CR
removed from output. Leading `2> ` or final `=> integer` can also occur in literal
stdout, so decomposition explicitly retains this ambiguity; the combined golden
is authoritative. Missing `^D` and terminating `.` follow original break/drop
semantics. Continuation-only blocks (for example `% cmd` followed immediately
by a terminal backslash) retain the command with empty input and golden: the
original `take 1 r1` may be empty. Haskell `lines` splits LF only, preserving Unicode separators.

## Computed Old: 174 source registrations

A bounded Haskell expression parser consumes the entire pinned `tests` expression:
application, lists, parentheses, `$`, `++`, `map` lambdas and `mconcat`. It evaluates
reviewed source helper formulas for writer/basic/tables, extended table matrices,
literate readers/writers, S5 and FB2. Unknown syntax/helpers fail closed. The helper
source hash is fixed in the extractor; a changed helper requires a formula audit
before refresh. The translated formulas retain the original GPL notice.

Records preserve group/name, input and expected file/hash, exact argv, exit-zero
requirement and distinct ordered `expected_processing`/`actual_processing`.
Expected text is read with strict `UTF8.readFile` and then normalized; actual
stdout uses `UTF8.toStringLazy`, then the same normalizer, then removes CR.
Expected text does **not** receive that final CR removal. FB2 retains `ignoreBinary . formatXML`; a raw expected
file is not incorrectly labelled a byte-exact normalized expectation. Old's stderr
is not compared by its golden assertion. MediaWiki basic/tables are registered
twice upstream; both registrations remain, distinguished by occurrence suffix.
No asset reuse or Native/FB2 component check is inferred to cover these application
assertions. All 174 have null Kotlin mappings.

## Lua Haskell roots: 48 registrations

The four main groups register 34 Haskell assertion leaves and 14 `testPandocLua`
script suites. IDs retain group/name, including the nested global-module group;
records retain registration source hashes and independent expectation origins.
The 14 script suites retain original Lua file/hash, not an invented leaf count.
Their nested tasty-Lua trees are now recorded by the finite source-registration
checkpoint below; dynamic/platform variants and executable assertions remain pending. Kotlin API examples do not discharge Lua engine
assertions, so all 48 registrations remain unmapped.

## Remaining global inventory work

The original foundation still preserves 8,646 upstream/dependency source/assets
and 1,125 discovery candidates: Cabal suites, Haskell imports, qualified Python
`test_*` definitions and suite factories. These are **registry candidates**, not
registered source-test counts. Source discovery is intentionally finite: exact
test directories, Cabal declarations and calibre framework/test markers.

Before local:622 can complete:

- Map each Command/Old assertion to independently reviewed executable Kotlin CLI
  behavior, preserving options, stderr/status and normalizers.
- Attribute captured reader/writer/common runtime leaves to precise source
  expectations, and audit other Lua/platform/CPP/feature-dependent variants.
- Resolve dependency flags, embedded doctests, tests outside exact test directories
  and non-Cabal runners; current discovery does not claim every dependency test.
- Reconcile calibre's runtime registry with static candidates, exclusions,
  platforms, dynamic factories, embedded tests and standalone/RapydScript runners.
- Resolve semantic equivalence/runtime availability for the source directional
  format matrix below, retaining aliases/containers; MOBI/AZW3 are not the full scope.
- Finish reviewed existing-slice mappings and full generator/shrinker/Read/ordering
  acceptance; preserve all original fixtures, notices and explicit deviations.

Verifier regressions reject lost sources/registrations/leaves, changed hashes or
expectations, false mapping promotion and miscounted observation evidence. Skips,
partial evidence, source tests, trials and observations must remain separate.

## Haskell source-registration census and nested Lua checkpoint

The next finite census traverses every `Tests.*.tests` reference from the pinned
main registration, resolving Org's qualified import aliases transitively. Besides
the separately enumerated Command/Old matrices, it preserves **60 module root
`tests` definitions and 62 module registration references** (the latter includes
main Command/Old references). Comments, string/character contents and nested block
comments are excluded lexically. Every complete root definition and registration
expression retains original source offsets (UTF8-decoded Unicode code points,
end-exclusive), lines and SHA-256; original files
and their notices remain untouched.

The **2,391 source registration sites** comprise 1,473 infix-helper uses, 573
constructor/helper sites and 345 groups. These are deliberately **not runtime
leaf counts**. Typed local `TestTree` helpers, `test`/`testCase`/`testProperty` and
infix `=:` form a documented finite recognition rule; other helper applications
remain covered by complete root-definition source spans and an explicit semantic
expansion gap. Source names, declaration scopes and directly enclosing literal
groups are navigation data. Unknown name expressions remain dynamic. Source names are not runtime Tasty
IDs: `Tests.Helpers.test` truncates source names longer than 54 characters to
52 plus an ellipsis. The shared helper source and comparison semantics are
recorded: `test` compares Haskell lines of `ToString` values; `testGolden` uses
Text equality, with CR filtering only for its failure diff display. A helper
body, a call of that helper, a reused named list and a comprehension template must
not be added together as distinct executed source tests.

Examples whose runtime multiplicities are now captured below are Markdown's nested math/list
comprehensions, Shared's named helper lists and RTF's `map rtfTest` matrix. The
lexical census records computed-syntax markers without assigning multiplicity; the runtime capture supplies multiplicity for its explicit profile. None of
the 60 reachable module sources contains a CPP branch directive at this pin;
the detector is nevertheless tested against both branches and never selects a
branch. Library/build flags and future conditional registration remain explicit
acceptance work. All source-site records are unmapped; existing reader/writer
Kotlin tests are not inferred to cover them.

The 14 original Lua module scripts now also have an independently observed
nested registration tree: **309 finite registered tests plus one empty group**.
The development-only observer replaces only `tasty.test_case`/`test_group`, reads
the scripts using the pinned Pandoc 3.12.1 Lua environment, records callback source
line ranges and **never invokes a test callback or assertion**. Unsupported
constructors/assertion execution fail instead of being ignored. The observer
also verifies that callbacks originate in their registered script. Original
callback bodies and referenced original helpers supply expectation provenance;
no expected conversion result is generated and no Kotlin coverage is claimed.

```sh
python3 tools/census-test-inventory.py --capture-lua-trees
python3 tools/census-test-inventory.py --verify-lua-trees
python3 tools/check-test-inventory-census.py
```

The first two commands require pinned Pandoc only as a development registration
oracle. Ordinary inventory/census checks consume the committed source-hashed
capture, without requiring Pandoc or running the original tests. The observed
finite environment does not prove all dynamic/platform registration variants.
All 309 nested registrations remain unmapped to Kotlin Lua-engine assertions.

A separate provenance record preserves the later **7,375-check** local:611
result at `cdf2b84906e91b1baad740738564855677966acb`: 6,000 Walk trials, 185 ordered
shrink checks, 420 QuickCheck operation checks, 500 seeded Pandoc comparisons and
270 implicit empty-shrink checks. Its sources and artifacts are read from that
**Git commit and its pinned submodule objects**, not concurrent checkout work.
It neither overwrites the earlier 6,185-check historical record nor attributes
new work to old evidence. The earlier nine-no-op-shrink gap is an earlier
checkpoint boundary; the separate later record supplies finite evidence for it.
Exact finite seed replay still does not establish all seeds, all generator
instances/boundaries, complete shrinker acceptance or the other original
properties/Read/ordering contracts. No new upstream source properties are counted.

## Native runtime registration census

`test-inventory-runtime.json` preserves **4,465 original Tasty leaves** for
GHC 9.12.2, macOS/aarch64, Pandoc 3.12.1, pandoc-types 1.23.1.2 and Tasty 1.5.4,
with HTTP and embedded data enabled. Counts are Command 1,870, Old 174,
Readers 1,686, Writers 636, Shared 32, MediaBag 4, XML 1 and ImageSize 62.
The remaining reader/writer/common trees therefore contribute 2,421 leaves.
This establishes concrete multiplicities of named lists, maps and comprehensions
for this profile, replacing lexical site counts for that purpose.

An isolated development build compiles the untouched original test binary. A
second observer imports the same original test tree with only its Main module
header renamed/exported. It uses Tasty's tree fold to retain component arrays;
its joined names must match the original `--list-tests` stdout byte for byte.
Neither process invokes test callbacks or assertions. Source catalog verification
checks all 2,888 Pandoc and 19 pandoc-types tracked files against pinned Git blobs.
See `tools/test-inventory-runtime-reference/README.md` for build/replay commands.

Stable IDs hash exact JSON component arrays and append duplicate occurrence
ordinals. This preserves 29 duplicate paths, dotted labels and 48 truncated
labels without guessing components by splitting display names. Command and Old
leaves reconcile exactly to their source records, including duplicate occurrence
multiplicities. Other leaves retain their original module root and source hashes;
precise generated-leaf-to-expression/expectation attribution remains open.
The source census remains separate from executable mappings. The FB2 bridge below
maps exactly nine unit leaves; the other 4,456 runtime leaves remain **unmapped**. Runtime registration is not Kotlin coverage.
The verifier rejects profile, source, registration and multiplicity drift;
negative tests also cover duplicate/dotted/newline identities and malformed captures.
Clean replay canonicalizes order because original directory enumeration can vary;
each replay still compares its original CLI and observer byte for byte.

A separate immutable record at `43db34fe4fd20707b4891e81378c059acdc09d22`
preserves **17,613 recorded Kotlin checks**: the preceding 7,375 plus 5,700
standalone generator observations and 4,538 helper observations over 19 instances.
These finite generator observations add no upstream source leaves. All artifacts
and independent sources in this record come from that commit, not current WIP.

This checkpoint leaves the global leaf inventory open: precise Haskell generated
leaf expectations and executable mappings; other build/platform profiles;
dynamic/platform Lua variants; dependency embedded/non-Cabal suites;
calibre runtime/factory reconciliation; and directional additional-format
matrices. `local:622` and the full port remain incomplete.

## Dependency root-to-suite reconciliation

The dependency layer reconciles **all 76 retained vendor Cabal manifests** with
Cabal 3.14.1.0's original `GenericPackageDescription` parser: **69 declared test
suite roots**, each with a stable manifest-path/component-name ID. An independent
source declaration check requires identical names/multiplicities for every test
suite and executable. Missing or extra manifests, stanzas and hashes fail checks.
`test-inventory-dependency-cabal.json` preserves the original parsed conditional
trees and a selected node projection: common imports, entry interfaces, source
dirs, modules, dependencies, modern build-tool-depends, CPP options and buildable
flags. Legacy build-tools and other compiler/build fields remain in original
source and need effective-profile resolution; an empty modern tools list does not
mean no generator dependency. No branch is selected and no test
is built, listed or executed by this declaration observer.

Declared, source-available, listed, runtime and assertion units remain distinct.
67 suite roots have at least one retained potential entry source; the text2.1.2
and text2.1.3 package fragments lack their declared test entry. Potential paths
use the union of conditional source dirs for availability discovery only; a path
is not proof of an effective configuration, buildability or factory expansion.
Every root is unmapped. All listed/runtime/assertion counts remain unknown.

All **118 retained vendor directories** have a root record and complete retained
file catalog/digest. **42 have no retained Cabal manifest**, including compiler
and library fragments and separately fetched image fixtures. That is unknown
upstream suite availability, not a declaration of zero tests. The chosen frozen
reference baseline contains **226 exact package IDs**: 73 have a retained matching
manifest, **153 do not**. Installed compiler packages and source fragments do not
fill those source/registration gaps. Retained directory names or manifest presence
do not prove that the full upstream release or test tree is present.

The source-signal census separately retains **310 files** containing top-level
main declarations, embedded doctest prompts or Hspec preprocessing factories.
These are 67 Cabal entries, 50 package Setup files, 18 declared non-test executable
entries and 175 unreconciled source candidates; they are not additive test counts.
The **1,296 prompt candidates** are original source strings, not grouped doctest
assertions or newly generated expectations. Nine Hspec preprocessor entrypoints
retain original pragmas; effective generator versions/factory expansion remain
required. Fourteen retained scripts/build files remain explicit runner candidates;
file names alone never establish test classification.

Four additional source-inspected execution routes are recorded separately:
blaze-builder Makefile `test` (an alias of its Cabal entry), historical LLVM
regression target, text's literal-rule compiler script, and JuicyPixels' argument
or program-name dispatch into `testSuite`. The shell script preserves its three
active source assertions and original expected counts 8/7/4; the commented fourth
is excluded. The LLVM entry only prints output and is not recast as an assertion.
JuicyPixels defaults can select benchmarks, and its fixture source v3.3.8 differs
from library3.3.9. These route/condition distinctions prevent false suite coverage.

Replay/build instructions are in
`tools/test-inventory-dependency-reference/README.md`. The dependency snapshot
establishes no omitted **retained Cabal declared roots**, not no omitted global
dependency tests. Exact missing releases, source/import registration closure,
embedded prompt grouping, standalone targets, generated factories, all effective
OS/toolchain profiles, runtime leaves, independent expectation attribution and
Kotlin mappings remain open. The calibre source checkpoint below starts the
separate factory/embedded and directional-format reconciliation; runtime closure
remains pending. Full local:622 is incomplete.

## Calibre source registries and directional formats

This checkpoint reads pinned calibre **9.16.0** at
`61c4027d2bfe737e0177e09952ee4942042a5892` using Python AST/source only.
It imports no calibre modules, runs no setup/plugin initialization, and executes
no assertions or conversions. All new records remain **unmapped**. This is
inventory work; calibre implementation remains sequenced after full Pandoc.

The complete primary declarations reconcile **18 setup groups** with **76 factory
append routes** in `calibre.utils.run_tests.find_tests`. Every original append
must be recognized exactly once; imports, group predicates, OS conditions and
kwargs such as `only_build=True`/`return_tests=True` are preserved. Each route
retains its exact original factory definition and call sites. Embedded MOBI
writer resources, PalmDOC, TXT and archive input tests are included even though
their modules are not test-named files. The Windows-only route and `TestImports`
class are explicit. Availability and runtime leaf/assertion counts stay unknown.

Four `find_tests_in_package` routes expand their retained immediate modules:
server12, database14, polish7 and tinycss8, including `__init__.py`, excluding
`main.py`. The original loader accepts `.pyc` too and deduplicates basenames;
the source profile contains `.py` files. This is a module-registration expansion,
not runtime `loadTestsFromModule` execution or a claim about inherited/dynamic
cases. Other factory call expressions remain source evidence, with closure gaps.

The code catalog checks **1,633 original tracked Python/RapydScript files** against
pinned Git blobs. It retains **811 `test_`-named source function/method declarations**,
their decorators and original assertion expressions/source hashes, plus **143
named factory declarations** throughout the source tree. These are source units,
not executed tests; loop/subTest, inheritance, generated methods and helper/fixture
expectations are not multiplied into runtime counts. RapydScript's **134 sources**
are a separate dialect, never fed to Python AST. `TestRS`, its original Qt/webengine
runner body, `src/pyj/test.pyj` and decorator registration helper are linked, while
compiled decorator/import/runtime expansion remains pending.

The builtin conversion registry has **42 registered classes:22 input/20 output**.
Every imported conversion class must occur exactly once in builtin registration;
class ancestry and literal `file_types`/`file_type` declarations determine direction.
Calibre UI declares **49 input tokens** including its ZIP/RAR extras and **20 output
tokens**. Plumber's additional OEBZIP archive route raises supported source input
tokens to **50**. ZIP/RAR/OEBZIP archive registrations are reconciled against
`ARCHIVE_FMTS`; existing output directories select OEB. These containers, plugin
aliases and optional `kepub` viewing normalization remain distinct source routes.

The **70-row directional matrix** compares those tokens with the exact pinned
Pandoc **52-reader/76-writer** registries and filename inference rules. It records
12 exact directional names, six extension-inference targets, one separate PDF
engine output route and 51 rows without a corresponding pinned direction route.
Neither name nor extension matching establishes semantic conversion equivalence.
In particular `.txt` infers Markdown; `.djvu`/`.zip` inference deliberately does
not supply readers; PDF input differs from PDF engine output; calibre ZIP output
is HTML-in-ZIP, and OEB is a directory route. Pandoc's named registry aliases retain
individual records and their shared implementations rather than being deduplicated.

Remaining no-matching-direction tokens are:

| Direction | Calibre tokens |
|---|---|
| Input | azw, azw3, azw4, cb7, cbc, cbr, cbz, chm, djv, djvu, docm, downloaded_recipe, fbz, htmlz, kepub, lit, lrf, mobi, oebzip, opf, pdb, pdf, pml, pmlz, pobi, prc, rar, rb, recipe, shtm, shtml, snb, tcr, txtz, updb, xhtm, zip |
| Output | azw3, htmlz, kepub, lit, lrf, mobi, oeb, pdb, pmlz, rb, snb, tcr, txtz, zip |

These rows preserve requirements; they do not assert that every token is a separate
serialization or that related containers are equivalent. Runtime plugin availability
can change with disabled/custom/system plugins, initialization failures, platform,
Qt/native dependencies and resources. No runtime profile was initialized here.

```sh
python3 tools/calibre-test-inventory.py
python3 tools/check-test-inventory-calibre.py
python3 tools/build-test-inventory.py
```

Global local:622 remains incomplete: runtime factory/inheritance/generated leaf
closure, independently attributed fixture/helper expectations, other embedded and
RapydScript runners, all dependency/platform profiles and executable Kotlin mappings
still require acceptance. Neither retained source nor source assertions complete
the upstream port or start calibre implementation.

## First finite executable-mapping closure

`tools/pandoc-types-test-inventory.py` is the source of the 81 historical mappings
and `pandoc_types_mapping_evidence`. All mapped execution/expectation links are
explicit; historical blobs keep typed Read WIP out of the accepted evidence.
Reproduction commands select actual JUnit factory classes/methods, while concrete
display-name/ordinal identities identify the retained fixture assertions. Commands
are reproduction selectors, not claims that an exact historical shell invocation
was archived. Inventory performs no Gradle or native oracle execution.

```sh
python3 tools/pandoc-types-test-inventory.py
python3 tools/check-test-inventory-pandoc-types.py
```

Remaining semantic gates include unbounded generator/shrinker/runtime acceptance,
Read/ordering and complete public API/effects. Other upstream runtime leaves still
need executable evidence. The FB2 bridge below is a separate checkpoint; none of its obligations is
implicitly closed by this 81-registration bridge. Full local:622 remains incomplete.

## Nine FB2 unit-leaf executable mappings

The FB2 bridge joins exactly **six reader and three writer** original native
runtime IDs to selected Kotlin assertions. Reader source labels Emphasis, Titles,
Epigraph, Poem, Meta and Notes map to the lowercase dynamic names in
`FB2ReaderTest.all original FB2 reader golden tests`. Writer paths
`block elements/para`, `inlines/Emphasis` and `bullet list` map to
`FB2WriterTest.original FB2 writer examples`: `paragraph`, `emphasis`, `bullet list`.
The `para`→`paragraph` and `Emphasis`→`emphasis` aliases are explicit, never inferred
as coverage by matching names.

Each reader mapping preserves its original `.fb2` and `.native` bytes/hashes,
`fb2Test` helper, strict UTF8/drop-BOM/filter-CR input semantics, `readFB2 def`
and `writeNative def{writerTemplate=Just mempty}` formatter. Original
`goldenVsString` compares exact bytes. Kotlin asserts both the independently read
Native AST and the **complete Native serialization**, with CR filtering. All six
original Native goldens contain no CR/BOM, so Kotlin's expected bytes are unchanged.
This is finite golden evidence, not broad Native/FB2 option or formatter parity.

Each writer mapping preserves the exact original input constructors, formatter
prefix/suffix and expected inner XML. Original `Tests.Helpers.test` compares
`lines(toString actual)` and `lines(toString expected)`; String ToString is identity,
and Blocks ToPandoc is `doc`. Kotlin checks the whole independently translated XML
plus a final LF, a stronger terminal-newline constraint than the original helper.
Source/Kotlin constructor sequences are explicitly checked for the three finite
inputs. Neither normalized text comparisons nor other corpus/oracle tests replace
these original unit assertions.

Execution provenance lives in `test-inventory-fb2-execution.json`: exactly six
reader and three writer XML cases, class/display identities, original XML hashes,
complete shared command, selected reproduction selectors and eight relevant source
hashes checked before/after execution. XML copies remove only hostname metadata;
case/result data is retained. Unrelated Read/native counts are not FB2 coverage.
The prior post-Native integration shared run (92971, BUILD SUCCESSFUL in 2m12s)
executed these nine cases with zero failures/errors/skips; only those nine are
counted here. All eight before/after source hashes match. A `refresh_pending`
record cannot promote mappings, and current source/hash mismatches fail
verification. The fresh worktree result is not misattributed to its basis commit
or unrelated Read WIP; result commit remains unset until integration.

Reproducible capture uses an explicit coordinated Gradle window:

```sh
python3 tools/fb2-test-inventory.py --freeze-execution-sources /tmp/fb2-before.json
# Execute the two recorded factory selectors in the coordinated Gradle run.
python3 tools/fb2-test-inventory.py --capture-execution /tmp/fb2-before.json --command-file /tmp/fb2-command.txt --log /tmp/fb2-gradle.log --jdk 21.0.12-librca
python3 tools/check-test-inventory-fb2.py
```

**Ten other known FB2-related obligations remain unmapped**: eight Old CLI/goldens
(basic, titles, images, images-embedded, math, meta, tables, testsuite), Command
`figures-fb2.md/#1`, and ImageSize `fb2/test-small.png`. Each retains its own
source/runtime ID and expectation origin. Their CLI options/resources/normalizers,
stdout/stderr/status or image-size predicate are not discharged by the nine unit
mappings. This is not all FB2, full Pandoc, global622 or subsequent calibre completion.

FB2 execution refresh is currently **pending**: subsequent Native review requires
a ToSources raw-CR correction. Retained run92971 XML/hashes describe only its
executed NativeReader artifact. The prior generated snapshot is a non-final
checkpoint and intentionally fails current execution validation while pending.
After final source freeze, recapture eight before/after hashes and the same nine
selected assertions, then regenerate and verify once. Proof logic/eleven focused
checks and canonical/four targeted negative checks passed before marking pending;
no future corrected artifact is attributed to that run.
