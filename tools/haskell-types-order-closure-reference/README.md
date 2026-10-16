# Original 25 Eq/Ord dictionaries / 225 method slots

Development only. Production remains Kotlin. The executable invokes unchanged
pandoc-types1.23.1.2 instances, original Arbitrary, QuickCheck2.16.0.0, Text2.1.2,
containers0.7 and GHC9.12.2. It does not reconstruct comparison instances.

The finite matrix joins the existing75,470 native observations (unchanged) with
5,855 additional original requests. Every one of25 concrete dictionaries has all
nine methods mapped: equality, inequality, compare, four relations and min/max.
The23 Definition dictionaries and Many Inline/Many Block include all79 actual
constructors, cross-products, all92 immediate Data fields changed independently,73 competing field pairs,
19 original generator families at40seeds×5sizes, integer bounds, Format folds,
Many IEEE contexts and126 individual method/demand probes. Extrema are compared
by complete original Show and caller selection using original <= plus the source
method bodies. This preserves equal-ordered Format/Citation values and NaN.

Many was missing from the Kotlin dictionary API. Its derived dictionaries coerce
all methods to Seq; Seq equality compares finite lengths before any element,
while ordering compares list elements and inherits original defaults. The new
Kotlin path preserves that distinction and returns caller wrappers for extrema.
Known-spine partial values expose source element demand, ordering direction,
short-circuits and error messages. Infinite/undefined Seq spines are not a valid
source Seq representation. Strict AST fields cannot model arbitrary Haskell
bottoms; this is a finite source matrix, not a general GHC evaluator.

GhcDerivedEqOrd.txt retains45 complete original compiler-generated blocks from
the existing GHC9.12.2 unchanged-library inspection build
(/tmp/kotdoc-ast-ord-inspect-build; -ddump-deriv). Manual Format Eq/Ord and Citation
Ord remain in Definition.hs. The original75,470 corpus source hashes pin the same
Definition/Builder source. GhcSequenceEqOrd.iface.txt is a verbatim relevant
boundary from the installed GHC9.12.2 containers0.7 Data.Sequence.Internal.hi,
read by ghc --show-iface. It retains actual dictionary and default direction.
OriginalSequenceInternal.hs and containers-LICENSE are verbatim upstream v0.7:
https://github.com/haskell/containers/tree/v0.7/containers
Original copyright/license notices and EOF bytes are retained. Reproduction:

```sh
PATH=/tmp/kotdoc-haskell/ghc/bin:$PATH CABAL_DIR=/tmp/kotdoc-haskell/cabal-state \
 /tmp/kotdoc-haskell/cabal build exe:types-order-closure-reference \
 --project-dir=tools/haskell-types-order-closure-reference \
 --builddir=/tmp/kotdoc-types-order-closure-build
python3 tools/import-types-order-closure-fixtures.py --oracle PATH
python3 tools/import-types-order-closure-fixtures.py --check-source
```

The manifest is a finite slot/source/evidence join. Every original response line
is retained verbatim. Constructor/field indices and untouched old corpus are
verified independently of Kotlin. Passing this gate does not close Walk's174
methods or the140 module-owned export entries, nor all Pandoc/calibre scope.
