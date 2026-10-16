# Upstream test inventory foundation

This inventory preserves the full Pandoc → calibre MOBI → additional calibre
formats goal. It is **not** a completed port, a complete global leaf inventory,
or a coverage percentage. `docs/PORTING.md` remains the project scope ledger.

`test-inventory.json` records pinned submodule revisions, source paths/SHA-256,
entrypoint provenance, conservative discovery candidates, executable Kotlin
mappings and outstanding gaps. Generate with:

```sh
python3 tools/build-test-inventory.py --write
python3 tools/build-test-inventory.py
python3 tools/check-test-inventory-verifier.py
python3 tools/import-pandoc-json-fixtures.py --check
```

The default command is read-only and fails on additions/removals, changed source
bytes, registrations, pins or mapped Kotlin test files. Refreshing is an explicit
review action, never an operation that generates conversion expectations. No
Pandoc, Haskell, calibre or Kotlin executable runs during discovery. Python and
Git are required; all submodules and vendor sources must already be present.
`--snapshot PATH` allows validation of an alternative snapshot without editing
the canonical artifact. SHA verification detects drift; it does not certify that
a test's behavior or mapping is correct.

## Counting and mapping

The complete pinned `pandoc-types/test/test-pandoc-types.hs` registration has
**81 source tests**: 12 Walk properties, JSON roundtrip, 56 paired JSON examples,
9 normalization properties, two table literals and the Figure property.
The paired JSON examples contain **112 assertions**; including the other source
tests yields **137 assertion/property registrations**. A QuickCheck trial or
oracle observation is never an additional source test.

The 58 literal examples have executable mappings: 56 JSON pairs in
`UpstreamJsonTest` and two tables in `TablesTest`. JSON encoding compares exact
original literal bytes; decoding compares independently translated typed values.
Original expectations remain in the pinned Haskell source and imported fixture;
Kotlin never creates those expected bytes. The importer independently verifies
the fixture against the original, and its source and fixture hashes are retained.

Nineteen properties have **partial** mappings: eight Walk transformations/queries,
JSON roundtrip, nine normalization predicates and Figure. The existing Kotlin
methods and source hashes are recorded explicitly. Their fixtures or Kotlin
seeded samples do not reproduce the complete original QuickCheck generators or
shrinkers. Walk's generic JSON traversal is related evidence, not execution of
the original Haskell generic predicate; those tests also use JVM case conversion.
The four WalkList/QueryList properties are **unmapped**: ordinary list visitor
checks do not establish the upstream fold/tails contracts. Generator/shrinker,
Read and Eq/Ord acceptance are separate requirements, not additional source
registrations in this suite. Existing Eq/Ord observations still need a reviewed
inventory mapping.

`mapped` means an identified source assertion has a reviewed executable link,
not that every behavior of its production module is complete. `partial` names
material missing evidence; `unmapped` provides no accepted executable mapping;
`blocked` is reserved for an identified external prerequisite. Registry
candidates use `unmapped`, null executable/command and an explicit discovery gap.
This prevents discovery from being mistaken for execution. Stored commands are
reproduction instructions; this inventory update does not assert a fresh Kotlin
suite run.

## Global registry and remaining work

The snapshot includes all pinned Pandoc/pandoc-types test assets and Cabal files,
vendored dependency Cabal declarations and exact `test`/`tests` directory assets,
and calibre Python/RapydScript candidates found through test definitions or test
framework references. Calibre conversion plugin and built-in format registries
are retained even when they contain no test definitions. Haskell `Tests.*`
imports, Cabal suite declarations, Python suite factories and qualified nested
`test_*` definitions are **discovery candidates**, deliberately distinct from
registered source tests. Qualification keeps nested Python names distinct;
line numbers are navigation data and do not form registry identity.

Required next inventory work, before local:622 can be considered complete:

- Expand the complete Pandoc Tasty tree, feature/CPP conditions and every leaf;
  an import does not prove that its tests are registered or reachable.
- Parse command files through the pinned Markdown semantics: upstream selects
  top-level CodeBlocks, not a textual `% pandoc` match. Preserve command input,
  stdout, stderr and exit status separately. Reader-AST checks do not discharge
  application assertions.
- Expand computed `Tests.Old` golden helper matrices and pair every input with
  its expected file/options. Asset presence alone gives no test mapping.
- Enumerate the separate Lua-engine filters/modules/custom-reader/custom-writer
  trees and their assertions. Kotlin API examples do not port the Lua engine.
- Resolve dependency Cabal flags, embedded doctests, suites outside exact test
  directories and non-Cabal runners. The finite source-discovery rule is not a
  claim of discovering every dependency test.
- Reconcile calibre runtime `find_tests` with the static candidates, exclusions,
  platforms, dynamically built suites, embedded tests and standalone/RapydScript
  runners. In particular retain MOBI writer JPEG/EXIF and PalmDOC compression
  tests found inside production modules.
- Derive the directional input/output format matrix from calibre's built-in
  plugins and Pandoc's registries, preserving aliases and container semantics;
  enumerate tests for every additional format, not only MOBI/AZW3.
- Add reviewed mappings for the other existing Kotlin slices and every remaining
  upstream assertion, with independent expectation origins and exact execution
  commands. Resolve all semantic deviations and missing generators.

Acceptance must reject an unknown/lost upstream ID and a `mapped` ID without an
executable assertion. Preserve original fixtures and notices, report skips and
partial status separately, and keep registry/source/trial/observation totals
separate. The current verifier guarantees reproducible source discovery and
hash/mapping drift detection; it intentionally does not claim the unfinished
leaf extraction or independent behavioral acceptance.
