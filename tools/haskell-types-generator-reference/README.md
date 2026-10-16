# Exact QuickCheck and pandoc-types generator replay

The oracle links unchanged pinned QuickCheck 2.16.0.0, splitmix 0.1.3.2,
random 1.3.1 and local pandoc-types 1.23.1.2 with GHC 9.12.2. No Kotlin
implementation produces an expected value.

`quickcheck-reference` observes actual QCGen initialization and both split
children, generic choose versus chooseInt, fmap, monadic bind, Applicative ap,
replicateM/vectorOf, listOf/listOf1, suchThat and arbitrary Int/inBounds. The
420 seed/size cases include native Int64 seed extrema and rejection at size0.
Kotlin ports the actual SMGen bit operations and Gen split tree.

`OriginalSeededTypesArbitraryTest` replays all 500 original Pandoc generator
inputs already retained in properties.jsonl.gz, using the same mkQCGen seed and
requested size and comparing complete typed trees. This checks actual seeded
outputs rather than statistical constructor frequencies. The pre-existing
injected-Random policy helper remains a separate exploratory implementation;
it does not claim seed compatibility.

`types-no-shrink-reference` executes the nine original implicit no-op shrink
instances on 270 original generated inputs (ten seeds, three sizes). Kotlin
exposes the corresponding empty sequences, including Citation/Alignment hooks
used in compound shrinkers.

```sh
cabal build exe:quickcheck-reference exe:types-no-shrink-reference --project-dir=tools/haskell-types-generator-reference --builddir=/tmp/kotdoc-types-generator-build
python3 tools/import-types-generator-fixtures.py --oracle PATH_TO_QUICKCHECK --no-shrink-oracle PATH_TO_NO_SHRINK
python3 tools/import-types-generator-fixtures.py --check-source
```

This checkpoint does not complete local:611. Independent standalone replay of
all nineteen default generator instances and explicit helper depth/size/Unicode
boundaries, complete shrinker lazy/deep/partial audits, remaining original
table/Figure/JSON property generator replays, and Read/ordering/inventory audits
remain required. Kotlin List-backed finite source values are covered here;
this is not a general GHC heap/undefined/infinite-value implementation.
