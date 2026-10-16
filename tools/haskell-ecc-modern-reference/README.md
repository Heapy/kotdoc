# Simple 33-curve API and modern prime adapter checkpoint

This checkpoint covers Crypto.ECC.Simple.Types/Prim for all 33 named curves and
Crypto.ECC classes/KeyPair/SharedSecret with P256/P384/P521 instances only. The
X25519/X448/Edwards instances remain pending. A passing prime partition is not a
claim that the entire modern ECC API or task 613 is complete.

Simple parameters are compared mechanically, in full, with the accepted legacy
33-curve table. The new typed tags and phantom Scalar/Point wrappers preserve
Simple policy: scalarFromInteger bounds by field modulus/polynomial, not group
order; pointFromIntegers validates coordinates before subgroup; negative point
multiplication negates the result. All valid points use the accepted Internal
prime/binary engines, including Simple P256, which has no dedicated P256 dispatch.
Shared affine formulas and positive off-curve fallbacks reuse accepted legacy
code, whose generic fallback formulas are the same. No native process is used
by production. No accepted engine is rewritten.

The public adapters preserve their distinct policies. P256 scalar generation is
one unrestricted 32-byte request. Zero is returned as a scalar, but constructing
a strict KeyPair or lifting zero to a point errors. Its tagged point decoder
checks empty input, tag, exact payload length and coordinates in source order.
P384/P521 point decoding splits arbitrary payload length at length div 2; no
independent exact-size guard is introduced. Their scalar decoding checks only
48/66 bytes, and integer construction checks numBits(abs) against 384/528, even
accepting negative values whose subsequent encoding fails. P256 ECDH rejects
identity and scalar zero/n while accepting valid all-zero x output. The Simple
adapters reject an infinity result and preserve raw-point source behavior.
SharedSecret owns bytes, supports equality, concat, empty and force. Best-effort
Cleaner reuse and defensive copies do not prove JVM timing, erasure, physical
memory/finalizer or GHC bottom/laziness equivalence.

The full original modern ECCSpec remains unchanged except its module name and
runs against actual public Crypto.ECC, including the as-yet-unported families.
The explicit Kotlin partition contains all 27 prime point vectors, both P256
ECDH KATs (including the valid zero-X secret), and all six property bodies for
each of three prime adapters using original TestDRG-generated scalar samples.
The eight X-only point vectors and nine X-only weak-point vectors are recorded
as pending. These deterministic per-curve samples are not described as the exact
mixed-curve stream of the separate full Hspec run.

Simple modules are hidden in the upstream package. The extractor therefore
retains complete dev-only copies with verified module/import substitutions.
The prime Crypto.ECC copy removes only the three out-of-scope instance blocks
and their private checkNonZeroDH helper, plus their imports. The original
arithmetic and prime policies are unmodified. Exact Internal/Compat/Serialize
copies expose the ordinary original C calls without arithmetic instrumentation;
CPP uses the pinned integer-gmp/deepseq build. The unchanged public Hspec suite
is an additional independent baseline. All transformations and source bytes are
verified, fixtures come exclusively from this original-source oracle, and no
expectations are derived from Kotlin. Negative-polynomial undefined domains,
arbitrary broken parameters, timing and physical erasure remain unproved.

Native build, from this folder (reuses the accepted legacy build directory):

```
CABAL_DIR=/tmp/kotdoc-haskell/cabal-state /tmp/kotdoc-haskell/cabal build --offline --builddir=/tmp/kotdoc-ecc-legacy-build --with-compiler=/tmp/kotdoc-haskell/ghc/bin/ghc --with-hc-pkg=/tmp/kotdoc-haskell/ghc/bin/ghc-pkg ecc-modern-reference
```

From the repository root, with ORACLE naming the produced executable:

```
python3 tools/extract-ecc-modern-reference.py --check
python3 tools/import-ecc-modern-fixtures.py --oracle "$ORACLE"
python3 tools/import-ecc-modern-fixtures.py --oracle "$ORACLE" --verify-oracle
python3 tools/import-ecc-modern-fixtures.py --check-source
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --no-build-cache -Dorg.gradle.vfs.watch=false -Pkotlin.compiler.execution.strategy=in-process --project-cache-dir /tmp/kotdoc-ecc-modern-gradle :core:test --tests io.heapy.kotdoc.crypto.CryptoEccModernPrimeTest
```
