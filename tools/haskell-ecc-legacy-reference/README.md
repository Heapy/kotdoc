# Legacy public ECC Types / Prim / DH checkpoint

This finite checkpoint ports the pinned crypton 2.0.1 legacy public ECC policy,
all 15 Prim exports, private P-256 dispatch, complete Jacobian fallback, point
composition/decomposition and subgroup checks, and all four DH functions.
It reuses accepted Kotlin X509 curve/value types, all 33 source-derived curve
parameter tuples and SEC OIDs, CryptoEccInternal prime/binary engines, F2m,
modular arithmetic, serialization, and specialized P-256. These engines are not
rewritten or replaced by native production calls. Integer arithmetic in the
original generic fallback remains BigInteger arithmetic in Kotlin.

The extractor retains the complete original ECCSpec, changing only its module
name. Native execution runs all 124 examples, including seven original property
families with 20 trials each. The corpus uses the original Curve/QAInteger and
arbitraryPoint generators at explicit seeds/sizes, plus independent native
results. These corpus samples are not represented as the exact internal sample
stream of the separate full Hspec execution. Every original family is translated:
12 valid-point vectors; P-256 scalar and two-multiplication edges; P-384/P-521
weight and non-base-point cases; four binary-curve edge/order-two families; all
20 subgroup rows (18 binary, SEC_p112r2 and SEC_p128r2), each with all six source
private values and valid 7G controls; and all seven property families.

The manifest records exact subgroup names/points, all curve parameters, SEC OIDs,
original generator inputs, original suite summary and source hashes. The verifier
mechanically compares all 33 original parameter tuples and OIDs with the reused
Kotlin tables. Expected results come only from the original modules. Extra
coverage includes all Prim/DH exports, invalid/off-curve/out-of-range/infinity
points, negative/zero/wide scalars, compressed-point parity and RNG effects.
DH validates coordinates first, subgroup second, then rejects an infinity
multiplication; x is serialized at the field's byte width. DH generatePrivate
samples [0,n), while Prim scalarGenerate samples [1,n). The exact generateMax
masking, normal/over-generation, retry count and errors are retained.

Negative polynomials are outside the original F2m domain. In particular a direct
original binary pointDouble(-1,0) probe did not finish within a 3-second diagnostic;
that is a bounded observation, not a proof of nontermination. Binary double,
decompose and addition with a non-infinity operand at that negative x are excluded explicitly in manifest metadata,
not silently assigned a finite expected result. Other finite exceptions remain
ordinary oracle observations. No all-Integer, arbitrary broken-parameter,
constant-time, allocation-failure, GHC bottom/laziness or JVM/GC/hardware erasure
parity is claimed. Typed Kotlin construction maps Haskell data constructors;
Haskell Data reflection and generic Read typeclass infrastructure are not JVM
reflection APIs. SharedKey owns its bytes, copies snapshots, scans equality and
uses a best-effort Cleaner; physical scrubbing and finalizer timing are not proved.

Modern Simple / Crypto.ECC / X25519/X448/Edwards and the rest of task 613 remain
separate. This checkpoint does not complete 613 or the full Pandoc/calibre goal.

Build from this directory:

```
CABAL_DIR=/tmp/kotdoc-haskell/cabal-state /tmp/kotdoc-haskell/cabal build --offline --builddir=/tmp/kotdoc-ecc-legacy-build --with-compiler=/tmp/kotdoc-haskell/ghc/bin/ghc --with-hc-pkg=/tmp/kotdoc-haskell/ghc/bin/ghc-pkg ecc-legacy-reference
```

From the repository root, set ORACLE to the resulting executable:

```
python3 tools/extract-ecc-legacy-reference.py --check
python3 tools/import-ecc-legacy-fixtures.py --oracle "$ORACLE"
python3 tools/import-ecc-legacy-fixtures.py --oracle "$ORACLE" --verify-oracle
python3 tools/import-ecc-legacy-fixtures.py --check-source
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --no-build-cache -Dorg.gradle.vfs.watch=false -Pkotlin.compiler.execution.strategy=in-process --project-cache-dir /tmp/kotdoc-ecc-legacy-gradle :core:test --tests io.heapy.kotdoc.crypto.CryptoEccLegacyTest
```
