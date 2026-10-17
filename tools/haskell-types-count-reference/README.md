# Definition count Num/Enum dictionaries

This checkpoint covers the actual `RowHeadColumns`, `RowSpan` and `ColSpan`
newtypes in pinned Definition.hs219–220/265–270: each derives **Num7 and Enum8**,
for45 source method slots. The source has no Real, Integral or Bounded instances
for these wrappers, and none are invented. The original GHC Enum Int internally
uses its Int bounds; that does not create a Bounded instance for the wrapper.
`acceptance-matrix.json` maps each method to the source dictionary, Kotlin target
and independent original request IDs. Constructor/pattern and AST bridges are
additional representation witnesses, not invented source methods.

The original oracle imports the unmodified pinned Definition module and invokes
its actual derived dictionaries. Preserved compiler interfaces capture Definition,
GHC.Internal.Num and GHC.Internal.Enum. The installed GHC9.12.2 Haddock source
HTML is copied verbatim; extracted `.hs` files are the visible original source
with annotation tooltips omitted, not a claim to copy a raw source tarball.
`extract-ghc-source.py` reproduces that extraction byte-for-byte. Original GHC
and pandoc-types license notices are retained and already packaged by core.

There are9,930 finite raw responses:

-9,570 constructor/pattern/Num/Enum observations across all three types. Thirteen
named Int64 values cover min/max, near endpoints, ±2^62, negative/zero/positive
small values; binary operations and stepped ranges use their complete Cartesian
products. BigInteger conversion includes ±2^200 and values crossing2^64.
-351 original Cell/TableBody constructor/extraction observations prove the
explicit CountBridge preserves all represented Long fields without introducing
positivity validation or changing the existing AST field ABI.
-Nine additional complete finite range responses include ascending and descending
10,000-element lists and complete small ranges. Other ranges preserve an original
ordered prefix plus an explicit `truncated` flag. Zero-step infinite streams are
never called complete lists.

There are48 original demand observations: non-strict newtype patterns with an
undefined whole value or underlying field; strict numeric operands; zero-demand
range consumers; root/end/step demand; zero-step and short endpoint behavior.
The separate Kotlin sharing test checks the explicit construct/match thunk
adapters share their field/root. It is not counted as another native response.
The complete isolated JUnit factory executes9,980 cases:9,978 originals +integrity
+one adapter test, at512MiB heap/256KiB stack.

Production uses three distinct Kotlin value classes and explicit source
Num/Enum dictionaries. Strict finite value constructors are convenient JVM
representations; `construct` and `match` provide the non-strict source newtype
constructor/pattern behavior without fabricated placeholder values. Enum methods
return the accepted GenericList constructor model with independent head/tail
thunks. Kotlin Collection.size remains Int-bounded; consumers requiring source
laziness must use `step()`/thunks rather than size-demanding Collection helpers.
The GHC eftInt/efdtIntUp/efdtIntDn algorithms are translated directly, including
wrapped signed64 deltas and their endpoint guards. Error messages retain the
underlying `Enum.succ{Int}`/`Enum.pred{Int}` wording. This is the pinned64-bit Int
model, not a proof of a32-bit GHC target or arbitrary GHC heap/evaluation behavior.

The original test module has JSON literals for the three newtypes at lines377–385
and paired registrations765–767; these and the table properties retain their
original expectations. No independent Num/Enum test suite exists there, so this
native45-slot corpus supplies the missing direct contract evidence. Existing
Read/Show/EqOrd concrete dictionaries and the AST's Long fields are unchanged.
No generic Many class, count Data/Typeable/GHC.Generic/NFData/JSON or remaining
metadata/Builder/Data.Version/JSON-filter gate is silently closed by this slice.
The full140-export/350-expanded-instance audit and task611 remain open.

## Reproduction

Using the cached GHC9.12.2/cabal toolchain:

```
cabal build exe:types-count-reference exe:types-count-demand \
 --project-dir=tools/haskell-types-count-reference \
 --builddir=/tmp/kotdoc-types-order-closure-build
python3 tools/haskell-types-count-reference/verify.py --check-source \
 --oracle /path/to/types-count-reference --demand-oracle /path/to/types-count-demand \
 --ghc /path/to/ghc --definition-interface /path/to/Text/Pandoc/Definition.hi
```

No upstream executable or Haskell library is a production dependency. All
conversion/contract behavior is Kotlin. Raw original response streams remain
independent, verbatim and reproducible; no expected value is derived from Kotlin.


## Author final shared gate

Gradle96735 completed `BUILD SUCCESSFUL in 1m47s`:27,693 actual cases, zero
failures/errors/skips. Count9,980; accepted Many5,128; Generic808; Walk5,715;
pure-demand52; original property replays6,000; Visitor10. All seven XML files,
exact command, log and package hashes are saved in
`/tmp/kotdoc-count-final-results`. Final native replay9,930+48 and actual
GHC/Definition interface comparison pass; final manifest records38 hashes.
The core JAR contains all eight major Count classes; GHC/pandoc-types notices
match original files byte-for-byte, and the installed core JAR is identical.
This checkpoint is frozen for independent review; task611 remains open.
Canonical Kotlin files are under core; work-in-progress Kotlin copies are
development drafts excluded from the review commit.
