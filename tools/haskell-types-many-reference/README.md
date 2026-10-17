# Builder.Many dictionaries and specialized algebra

This checkpoint ports the actual pinned `Builder.hs:199–251` surface: two
Functor methods, 17 Foldable methods, four Traversable methods, six specialized
Inlines and six specialized Blocks Semigroup/Monoid methods, IsString Inlines,
four Builder helpers and the Many constructor/selector: **42 slots**.
`acceptance-matrix.json` maps every slot to source, Kotlin and original cases.
There are no upstream Applicative, Alternative, Monad or NFData instances for
Many; none are invented here. The remaining Many Data/Eq/Ord/Typeable/Show/Read/
Generic class members, other Builder exports and JSON supplement remain open.

All expected streams are raw responses from original Haskell. `ManyReference.hs`
imports the pinned Builder, Seq and actual derived/default dictionaries.
`ManyDemand.hs` additionally constructs original internal Seq constructors to
observe valid partial fields; it does not substitute a Kotlin model. Actual GHC
and compiled Builder `.hi` dumps retain the dictionary implementations and
source-default selections. No original pandoc-types test expected value changes.

The finite corpus consists of:

- `core`: 1,407 constructor/helper/Functor/Foldable and State/List/Maybe
  Traversable observations, lengths 0–34 and 40/50/64/80/100/130/256, Int64
  overflow cases, branch order, duplicates, empty errors and fold association.
- `construction`: 1,596 Seq append/repetition values and foldMap structure
  observations, including lengths through 512 and counts −1/0/1/2/3/4/7/10/31.
- `algebra`: 1,288 Inlines/Blocks cases from all original constructor witnesses,
  all touching-endpoint pairs, mappend/mempty/mconcat/sconcat/stimes and IsString.
- `numeric`: 364 Double sum/product/maximum/minimum observations, NaN,
  infinities, signed zero, empty errors and nonassociative rounding.
- `traversal`: 425 original call-structure observations for all four methods and
  `<$` replication shape, including every digit/node/tree shape through 512.
- `demand`: 47 original responses for ignored heads/seeds, partial middle fields,
  short circuit, callback direction, actual Ord superclass methods, custom Num,
  StrictData Inline roots, selected-payload demand, BigInteger count wrapping, and shared repetition to 2^40 elements / normalized Inlines to 2^100.

The Trace/Shape/Eq/Ord/Num witnesses intentionally distinguish implementation
calls that lawful instances could identify. These are source call-structure and
operand-direction witnesses, not assertions that those diagnostic instances
satisfy the class laws. The ordinary State/List/Maybe and numeric cases separately
prove observable values, effects and errors.

Important source details preserved:

- `foldDigit`/`foldNode` use a locally defined operator with left association;
  the outer tree's `foldMap` groups differently. Strict defaults `sum`/`product`
  use `foldMap'`, not that tree grouping.
- Foldable maximum/minimum use Functor.Utils Max/Min with `>=`/`<=` (left wins
  ties), not Ord.max/min. This matters for NaN and signed zero.
- `<$` is the Seq override `replicate (length s) x`; it does not preserve the
  original tree shape. Blocks.stimes uses cycleNTimes/rigidify/thin and accepts
  nonpositive counts. Inlines.stimes uses the Semigroup default power algorithm.
- Traversable Many is stock-derived with an outer fmap of Many. Seq leaf/digit/
  node constructors are built in their original Applicative grouping, without
  extra fmap calls per leaf. Monad-constrained mapM/sequence use their *actual*
  Applicative superclass. `ManyMonadEffects` describes that effect constraint;
  it is not a Monad instance for Many. `ManyMonadApplicative` is an explicitly
  selected bind-based convenience dictionary, not an assumption about all Monads.
- Seq append deliberately forces its new middle; replacement, fmap and cached
  lengths preserve the different source field demands. Views have independent
  result fields. Source StrictData means `Emph undefined` is a failing Inline
  root, while `Emph [undefined]` has a valid list constructor and an ignored head.

`Many.toSourceList` and Foldable.toList return the accepted GenericList constructor
model, allowing independent step/head/tail observation even for 2^40 elements.
`Many.toList` is a finite JVM List convenience; its total size is Int-bounded.
Kotlin Collection extensions may demand that size: use `step()`/source thunks for
source demand observations. Ordinary Kotlin value callbacks are separately named
`fmapValue`/`foldMapValue`; primary source callbacks receive explicit thunks.
ManySequence is the constructor/selector carrier needed here, not a claim to port
all of containers' public API. Arbitrary GHC heaps, infinite structures, compiler
exception nondeterminism and every class-member instance remain outside this
finite checkpoint. Non-strict callbacks can use JVM stack proportionally when
they themselves demand nested accumulator thunks; the corpus is bounded, not a
claim of unbounded stack equivalence.

## Reproduction

Use the existing GHC9.12.2/cabal dev toolchain and frozen dependency project:

```
cabal build exe:types-many-reference exe:types-many-demand \
  --project-dir=tools/haskell-types-many-reference \
  --builddir=/tmp/kotdoc-types-order-closure-build
python3 tools/haskell-types-many-reference/verify.py --check-source \
  --oracle /path/to/types-many-reference --demand-oracle /path/to/types-many-demand \
  --ghc /path/to/ghc --builder-interface /path/to/Text/Pandoc/Builder.hi
```

No Haskell program is a production dependency. Production is Kotlin. The
containers source and license are preserved verbatim; source attribution and the
packaged Containers/GHC-base/pandoc-types notices are retained.

## Author final verification (independent review pending)

Gradle65560 completed `BUILD SUCCESSFUL in 1m 20s`: **17,713 actual JUnit cases,
zero failures/errors/skips**. Many contributes5,128 (5,080 finite originals,
47 demand originals, one integrity test); unchanged regression contributes808
Generic,5,715 Walk closure,52 pure Walk demand,6,000 original properties and10
Visitor cases. Actual XMLs/command/log/package report are preserved under
`/tmp/kotdoc-many-final-results`. Many XML SHA256 is
`e709a499b41caac1e4f194070a38a76c671213db2c44897565ea0ebef736a2f0`.
The final source verifier checks64 hashes and replays all5,127 original responses
byte-exact, plus the actual installed GHC/Builder interfaces. The complete
5,128-case factory had also executed in isolation with512MiB heap/2MiB stack.

The same Gradle run built core JAR and CLI installDist. Thirteen major new class
entries exist, all four Containers/GHC-base/pandoc-types/text notices match exact
retained source bytes, and the installed core JAR is byte-identical.
Core JAR SHA256:
`d2d7a9c4ba5cd52e38f5c6486a1bb172ba0d7f0c5bb3e4f9ac1b2e388e87a819`.

```
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew \
  --no-daemon --no-build-cache --project-cache-dir /tmp/kotdoc-types-gradle \
  -Dorg.gradle.vfs.watch=false -Pkotlin.compiler.execution.strategy=in-process \
  :core:test --tests io.heapy.kotdoc.ast.OriginalTypesManyTest \
  --tests io.heapy.kotdoc.ast.OriginalTypesGenericTest \
  --tests io.heapy.kotdoc.ast.OriginalTypesWalkClosureTest \
  --tests io.heapy.kotdoc.ast.OriginalTypesWalkPureDemandTest \
  --tests io.heapy.kotdoc.ast.OriginalTypesPropertiesTest \
  --tests io.heapy.kotdoc.ast.PandocVisitorTest :core:jar :cli:installDist
```

These accepted-source method selections and author results do not close task611
or replace independent review of this checkpoint. All140 exports,350 expanded
instance members and the JSON supplement remain the enclosing acceptance scope.
