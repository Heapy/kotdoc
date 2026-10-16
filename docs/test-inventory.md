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

All 23 properties remain **partial**. The twelve Walk properties now reference
`OriginalTypesPropertiesTest.originalWalkPredicates` and the separate local:611
provenance record: 500 unchanged-source generated documents, 12 predicates,
6,000 trials with independent `Data.Generics` expectations and original foldr/tails
adapters. The dynamic factory uses `p_walk`/`p_query` even for the source
`p_walkList`/`p_queryList`; the source IDs retain original names. Original
text-2.1.2 callback casing is used. Earlier related fixture-based checks remain
recorded separately. The checkpoint also has 184 ordered shrink observations
and 6,185 recorded Kotlin checks. Full generator/shrinker acceptance and QCGen
parity are not established, and other properties still use Kotlin samples.

Local:610 is a separate **observation corpus**, not new source-test coverage:
79,934 original C P-256 word observations and 79,946 recorded Kotlin checks.
Its provenance does not claim all public P256Spec tests. Both evidence records
verify committed source hashes and retain artifact hashes; their test counts
are earlier recorded results, **not fresh executions by inventory tools**.
Artifact hashes describe the current checkout snapshot, not a claim of byte
identity with the historical recorded commit or validation by its recorded count.
The word-checkpoint gap is its historical evidence boundary, not current global
status of the separately implemented field checkpoint.
The current artifact hashes were independently reviewed after commit
`cdf2b84906e91b1baad740738564855677966acb`. Its separate seeded-generator
checkpoint passed 7,375 checks, as recorded in `PORTING.md`; the machine evidence
record above intentionally retains the earlier 6,185-check historical result.
The newer no-op/generator evidence will receive a separate machine record in a
subsequent inventory checkpoint, rather than being attributed to the older commit.
Read/ordering and full generator/shrinker acceptance remain separate requirements.

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
- Expand remaining reader/writer/common helper/list/comprehension templates into
  runtime leaves, and audit Lua dynamic/platform and CPP/feature-dependent variants.
- Resolve dependency flags, embedded doctests, tests outside exact test directories
  and non-Cabal runners; current discovery does not claim every dependency test.
- Reconcile calibre's runtime registry with static candidates, exclusions,
  platforms, dynamic factories, embedded tests and standalone/RapydScript runners.
- Derive directional additional-format input/output matrices from calibre and
  Pandoc registries, retaining aliases/containers; MOBI/AZW3 are not the full scope.
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

Examples still requiring semantic expansion are Markdown's nested math/list
comprehensions, Shared's named helper lists and RTF's `map rtfTest` matrix. The
census records computed-syntax markers without assigning multiplicity. None of
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

This checkpoint leaves the global leaf inventory open: Haskell helper/list/
comprehension expansion and independent leaf expectations; all executable
mappings; dynamic/platform Lua variants; dependency embedded/non-Cabal suites;
calibre runtime/factory reconciliation; and directional additional-format
matrices. `local:622` and the full port remain incomplete.
