# Pure Walk demand correction

This checkpoint corrects an independently reproduced defect in the accepted Walk
implementation: `walk (const Space) (Emph [error "child"])` returns `Space` in the
pinned original, while the strict Kotlin Identity machine forced the child.
The pure implementation now supplies memoized reconstructed child fields. The
monadic machine and query traversal are unchanged.

The original oracle calls the pinned modules directly. Its 52 complete response
lines contain 47 executable Walk observations and five source-only Generic
contrasts. Generic's `everything` uses foldl and has different demand from Walk's
query; these five rows do not claim a Kotlin Generic implementation. Both original
request/response streams are retained verbatim, and the paired test fixture is
mechanically reconstructed from them. Expected values are never computed in Kotlin.

The Walk observations cover constant, constructor, as-pattern and forcing
callbacks; bad root/child/list-tail demands; Inline, Block, MetaValue, Meta,
Pandoc, list targets; generic List/Many/Map and custom child dictionaries; pair
field demand, query short circuit and monadic failure before the second field.
A prefix of a source list with 2^30 elements checks sparse demand without expanding
that list. Existing 174 method slots, 24 helpers and 5,715 observations are retained
as a separate unchanged regression, alongside the 6,000 original property cases
and ten Visitor cases. Five additional Kotlin tests check immutable collection
contracts and memoized successful/failed results; they are adapter tests, not
additional original-source observations.

Kotlin's `(A) -> A` convenience callback evaluates its argument. `walkLazy` supplies
an explicit `() -> A` argument and `() -> B` input for source callbacks that ignore
an undefined root. Custom dictionaries override this adapter when their own pure
implementation can avoid its input; the default preserves existing strict walk
overrides. No fabricated AST value stands in for an unevaluated input.

Kotlin Pair has strict fields. `WalkLazyPair` / `Walk.lazyPair` provide separately
memoized fields so a source fst/query/failing monadic traversal need not read the
other field. Nested source pairs can compose these adapters. Ordinary Pair remains
a convenience for already represented fields. Deferred lists allocate cache cells
only for requested indices, and deferred Map entries preserve keys, equality,
hashing and immutable collection behavior without forcing unrelated values.

The represented model uses finite JVM containers with Int sizes and explicit
thunks. It does not implement a general GHC heap, black holes, arbitrary infinite
spines or automatic laziness of opaque Kotlin callbacks. These bounds do not hide
the concrete root, descendant, pair and large-prefix cases above: each has an
executable source-equivalent adapter and independent original result.

Build/replay (existing native toolchain; no production Haskell dependency):

```sh
PATH=/tmp/kotdoc-haskell/ghc/bin:$PATH CABAL_DIR=/tmp/kotdoc-haskell/cabal-state \
 /tmp/kotdoc-haskell/cabal build exe:types-walk-pure-reference \
 --project-dir=tools/haskell-types-walk-pure-reference \
 --builddir=/tmp/kotdoc-types-order-closure-build
python3 tools/haskell-types-walk-pure-reference/capture.py --binary <built-binary>
python3 tools/haskell-types-walk-pure-reference/verify.py --check-source \
 --ghc /tmp/kotdoc-haskell/ghc/bin/ghc
```

The binary is under the reused build directory at
`build/aarch64-osx/ghc-9.12.2/kotdoc-types-walk-pure-reference-0.1.0.0/x/types-walk-pure-reference/opt/build/types-walk-pure-reference/types-walk-pure-reference`.
The actual installed GHC Identity interface is retained and byte-replayed.
Original syb 0.7.4 source files and license are verbatim archive members, including
original copyright and EOF bytes. Source manifests pin all mapped dependencies.

Status: final author verification; independent acceptance remains required. The
initial author run below was rejected for two DefinitionList demand blockers; the
follow-up below records their correction. Whole
local:611, Generic4, the 140 exports and expanded 350-instance audit remain open.

Initial author verification (not independently accepted): Gradle69073 terminal
SUCCESS in1m15s, actual11,763 cases/zero failures/errors/skips, with38 new Kotlin
cases =33 original Walk observations +five adapter checks. The five Generic source
contrasts are not Kotlin parity cases. Saved XML/log/results:
`/tmp/kotdoc-types-walk-pure-final-results`. Isolated final replay at512MiB/256KiB
passes33+five+5,715; both native streams and actual Identity/First/Last interfaces
replay byte-exact. Source checks pass31 existing+23 new hashes.

Exact focused command:

```sh
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --no-build-cache --project-cache-dir /tmp/kotdoc-types-gradle -Dorg.gradle.vfs.watch=false -Pkotlin.compiler.execution.strategy=in-process :core:test --tests io.heapy.kotdoc.ast.OriginalTypesWalkClosureTest --tests io.heapy.kotdoc.ast.OriginalTypesWalkPureDemandTest --tests io.heapy.kotdoc.ast.OriginalTypesPropertiesTest --tests io.heapy.kotdoc.ast.PandocVisitorTest > /tmp/kotdoc-types-walk-pure-final-gradle.log 2>&1
```


## DefinitionList review correction

Independent review found that the first author run still forced DefinitionList's
unrequested outer tail and the definitions field while requesting only terms.
The correction uses the common deferred list node/extent mechanism, a lazy outer
list projection and two independent List field views inside each Kotlin Pair.
Those are actual list views of the computed source fields, not fabricated AST
values. Failed extent demands are memoized as well as successful/failed elements.

Fourteen new actual-original cases cover both exact review reproducers, the
reverse field selection, terms/definitions/block prefixes, deliberate errors when
those fields are demanded, empty prefixes and adjacent LineBlock/BulletList/
OrderedList/nested-definition inner spines. Both /tmp/KotdocWalkReview.kt and
/tmp/KotdocWalkReview2.kt pass isolated, producing [([Space], [])] and [Space].
The original first38 request bytes, response bytes and paired fixture bytes are
preserved as prefixes; verify.py checks their original SHA-256 values explicitly.
The prior5,717 closure responses and expected fixtures remain unchanged.

The final native stream has52 rows =47 represented Walk observations +five
source-only Generic contrasts. The new JUnit class also has52 cases, but this is a
coincidence:47 original Walk observations +five Kotlin adapter checks. It does not
claim Kotlin parity for the five Generic contrasts.

Final focused Gradle35230 passed in1m29s:11,777 actual cases, zero failures/errors/
skips. Saved XML/log/results/frozen hashes:
`/tmp/kotdoc-types-walk-definition-final-results`. The isolated final run at512MiB/
256KiB passes47+five+5,715. Source checks pass31 existing+23 correction hashes,
52 original responses and the installed Identity interface replay byte-exact.
The command is identical to69073 above, with log redirection changed to
`/tmp/kotdoc-types-walk-definition-final-gradle.log`.
