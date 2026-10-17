# Independent review fix: generic dictionary pair-root demand

The first3311 native observations and22518 green Kotlin tests used ChaCha's lazy
operations dictionary. They missed a valid custom DRG whose randomBytesGenerate
throws at the pair root. The independent review demonstrated that the original
withRandomBytes Bad7(const42) and fmap(const42)(getRandomBytes7) return42, whereas
the first Kotlin implementation called the dictionary too early. That checkpoint
was not accepted. PreviousReview.json binds the unchanged original fixture and
saved previous XML/JAR/source hashes; /tmp/kotdoc-drg-final-results is retained.

CryptoPseudoRandom now delays and shares evaluation of each run's pair root as
well as its two projections. withRandomBytes independently delays its dictionary
pair root before passing a lazy byte value to the callback. The same memoized
pure thunk retains thrown failures and rethrows them; it does not swallow errors.
map/apply/bind compose these lazy projections, including ignored preceding
computations, callback failures and state-dependent forcing. This is not a retry
facility, a coroutine primitive or a proof of asynchronous-exception equivalence.

The ordinary original Crypto.Random/Types modules are unchanged in the Haskell
oracle. A custom public DRG instance and callbacks use NOINLINE unsafePerformIO
marks solely to observe evaluation/sharing. Four dictionaries throw at the pair,
value or state, or succeed.25 operation shapes cover get/map/apply/bind and
withRandomBytes, throwing computations, two successive draws, ignored/strict/lazy
callbacks and pure. Four repeated value/state forcing orders retain complete
results/errors and event prefixes:400 actual GHC9.12.2 -O2 observations. Kotlin
never supplies expected values. The original3311 expected rows remain byte-exact.

The JVM eager Pair container represents source component projections explicitly
as Lazy values; these tests do not claim universal language-level seq, all Haskell
bottoms, async exceptions or concurrent evaluation equivalence. The broader OS,
ABI, entropy and full613/Pandoc/calibre gates recorded by the main README remain.

Reproduce with the existing original Haskell Cabal environment:

    cabal exec --project-file=/tmp/kotdoc-drg-haskell-oracle.project --builddir=/tmp/kotdoc-drg-haskell-build -- ghc -package crypton-2.0.1 -O2 -outputdir /tmp/kotdoc-drg-demand-review-fix -o /tmp/kotdoc-drg-demand-review-fix/oracle tools/chacha-drg-reference/demand-fix/DrgDemandReference.hs
    python3 tools/import-drg-demand-fixtures.py --verify-oracle --oracle /tmp/kotdoc-drg-demand-review-fix/oracle

Original source and custom oracle remain under the existing crypton BSD-3-Clause
notice. No new production runtime dependency is introduced.
