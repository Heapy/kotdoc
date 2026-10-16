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

## Lua: 48 registrations, nested script leaves pending

The four main groups register 34 Haskell assertion leaves and 14 `testPandocLua`
script suites. IDs retain group/name, including the nested global-module group;
records retain registration source hashes and independent expectation origins.
The 14 script suites retain original Lua file/hash, not an invented leaf count.
Their nested tasty-Lua trees, dynamic/conditional tests and assertions require a
later source-leaf inventory. Kotlin API examples do not discharge Lua engine
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
- Enumerate nested Lua script leaves, all remaining reader/writer/common suites,
  and CPP/feature-dependent registration trees.
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
