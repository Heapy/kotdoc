# Original registered property closure oracle

OriginalRemainingProperties.hs retains eleven actual property bodies verbatim
from the pinned pandoc-types/test/test-pandoc-types.hs: JSON roundtrip, nine
table properties and Figure. Six forall bodies are additionally exposed by
mechanically replacing only their binders; predicate expressions and local
helpers remain verbatim. The importer verifies the entire resulting module
against exact original excerpts, as well as the pinned source hashes. No source
expectation or original fixture is rewritten.

The native executable records4,500 original Arbitrary TableHead/Body/Foot inputs
(seeds1..100, every original width2..16), complete actual Builder normalization
results and all three exact original predicate truths per input. Kotlin checks
those native results and translates the exact idempotence/subset/width predicates,
for13,500 tests. The oracle also records305 generated/empty/whitespace Figure
inputs and original Builder results, and500 original Pandoc generator values,
actual Aeson encoder bytes, decoder trees and original roundtrip truths.

Mode `suite` runs each of the eleven actual original QuickCheck properties with
fixed replay and1,000 successful trials, retaining the original name/result/count.
The eleven native-report assertions supplement14,305 actual Kotlin assertions;
they do not replace them. JSON checks compare independent native bytes/trees
before evaluating the original roundtrip predicate; table idempotence alone is
insufficient. Production conversion never invokes Haskell.

```sh
cabal build exe:types-property-closure-reference --project-dir=tools/haskell-types-property-closure-reference --builddir=/tmp/kotdoc-types-property-closure-build
python3 tools/import-types-property-closure-fixtures.py --oracle PATH_TO_EXECUTABLE
python3 tools/import-types-property-closure-fixtures.py --check-source
```

The manifest joins all81 original registered source cases to concrete Kotlin
factories/methods:23 properties,56 paired original JSON literals and two table
literals. Existing literal expectations remain unchanged. This closes the named
registered suite only after independent review and execution; complete shrinker,
typed Read, Eq/Ord and monadic Walk/Generic/public API audits remain separate open
gates in docs/pandoc-types-porting-matrix.md.
