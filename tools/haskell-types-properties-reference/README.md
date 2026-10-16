# pandoc-types original property oracle

Links unchanged local pandoc-types 1.23.1.2 with pinned GHC 9.12.2,
QuickCheck 2.16.0.0, text 2.1.2 and aeson 2.2.3.0. The twelve original
Walk predicates and their functions are copied verbatim from
`upstream/pandoc-types/test/test-pandoc-types.hs`, with the original
BSD-3-Clause notice retained. Expected transformations and queries execute
`Data.Generics.everywhere/everything`, independently of both Kotlin traversal
and Haskell Walk. The capture also evaluates all twelve original predicates.

The original Arbitrary Pandoc instance runs with `mkQCGen` seeds 1..500 and
requested sizes cycling 0,1,3,8,30. Its own resize 8 is preserved; these requested sizes
do not expand the source generator's depth policy. Replay inputs are original
generator outputs, not Kotlin-generated expectations. Generic list queries
include every cons suffix; Kotlin mirrors the explicit foldr/tails adapters in
the original predicates.

```sh
cabal build exe:types-properties-reference --project-dir=tools/haskell-types-properties-reference --builddir=/tmp/kotdoc-types-properties-build
python3 tools/import-types-properties-fixtures.py --oracle PATH_TO_EXECUTABLE
python3 tools/import-types-properties-fixtures.py --check-source
```

This checkpoint does not establish a complete independent Arbitrary/shrink audit,
complete Read/ordering audit, arbitrary partial/infinite values, or completion
of local:611 or the upstream port. Those remain required work.

The shrink corpus has 104 complete candidate lists: 100 source-generated
values projected through generic take-one list/text transforms and nullAttr,
plus four explicit small constructor/attribute literals. It also has 80 raw
original generated values with the first 32 ordered candidates and an explicit
`truncated` bit determined by observing candidate 33. No expected candidate is
rewritten. Kotlin's shrink functions return lazy Sequences, preserving candidate
order and duplicates without materializing the Cartesian attribute product.
The original QuickCheck Arbitrary/Gen sources and license are retained under
`vendor/quickcheck-2.16.0.0` and `licenses/QuickCheck-BSD-3-Clause.txt`.

`OriginalTypesArbitrary` translates size, constructor-weight, text-range,
nonempty-list and rejection-resizing policies using an injected Kotlin RNG.
It intentionally does not claim bit-for-bit QCGen seed compatibility. Independent
validation of its full decision/distribution contract is still required.
