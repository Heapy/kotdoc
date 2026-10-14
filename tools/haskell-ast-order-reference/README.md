# Original AST equality and ordering oracle

The executable links unchanged local pandoc-types 1.23.1.2, including its
original Arbitrary instances. GHC 9.12.2, Text 2.1.2 and QuickCheck 2.16.0.0 are
frozen. All comparisons, relational operators and min/max call the original
instances; no source instance is reconstructed or shadowed.

Input values come from original examples, the independent Native document/Show
corpora, explicit constructor/field boundaries and reproducible original
QuickCheck generators. Bit transport preserves NaN payloads and signed zero.
Width contexts exercise the original Double, ColWidth, tuples, lists, Table,
Inline, MetaValue, Meta and Pandoc instances. Selected min/max width bits prove
which original payload survived, even when their Show strings are identical.
Partial inputs keep actual StrictData fields defined; list heads/tails and lazy
Map values can fail. Finite unequal positions in infinite lists are observed.
Deep comparisons use up to 100,000 nesting steps and a 256 MiB GHC stack limit.
This corpus concerns AST instances, not the whole original property suite or a
complete emulation of GHC's runtime and strictness.

From the repository root:

```sh
cabal build exe:ast-order-reference \
  --project-dir=tools/haskell-ast-order-reference \
  --builddir=/tmp/kotdoc-ast-order-build
python3 tools/import-ast-order-fixtures.py --oracle PATH_TO_EXECUTABLE
python3 tools/import-ast-order-fixtures.py --check-source
```
