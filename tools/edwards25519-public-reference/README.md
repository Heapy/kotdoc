# Public Edwards25519 and modern ECC adapter

Production uses accepted portable Edwards point/modm engines; no Haskell/C production dependency or generic BigInteger curve arithmetic. `BigInteger` appears only at the modern adapter's explicit Integer serialization boundary through the accepted LE serialization module. Full613, X448/Decaf448 and other upstream ports remain open.

## Complete source matrix

`Crypto.ECC.Edwards25519` exports abstract Scalar/Point and15functions:

| Functions | Exact behavior |
|---|---|
| scalarGenerate |One getRandomBytes64 request, no retry/clamping/nonzero check. Decode little-endian moduloL. Defined mock providers returning0..64bytes are accepted by source; >64 throws original scalar error. RNG exceptions propagate. |
| scalarDecodeLong |0..64bytes accepted, >64 CryptoError_EcScalarOutOfBounds, zero/empty/L map to zero. Defensive ownership. |
| scalarEncode |Fresh32byte LE canonical encoding. |
| scalarAdd, scalarMul |Fresh owned scrubbed scalar; accepted specialized modm engine. |
| toPoint |Fresh owned point, zero maps to identity. |
| pointDecode |Exact32byte guard FIRST (CryptoError_PointSizeInvalid), then original coordinate decoder (CryptoError_PointCoordinatesInvalid). Noncanonical encodings and low/torsion points accepted whenever original decoder accepts; no subgroup/small-order safety additions. |
| pointEncode |Fresh32byte encoded point, original canonicalization and sign behavior. |
| pointHasPrimeOrder |Original predicate; true for identity. |
| pointNegate, pointAdd, pointDouble |Fresh owned results, source point equality across projective representations. |
| pointMul, pointMulByCofactor, pointsMulVarTime |Exact source formulas; scalars already reduced moduloL, including source torsion consequences. Fresh destination prevents raw output/input alias quirk from leaking into public calls. |

Scalar Eq compares represented scalar values; all abstract constructors produce canonical modm values. Scalar stock-derived Show is `Scalar <scrubbed-bytes>` with parentheses at precedence>10. Original RAM Show ignores its payload: even `show (undefined::Scalar)` produces this string. Point custom Show is `Point "<64lowercasehex>"` with precedence>10 parentheses and demands encoding. NFData demands the Scalar/Point value; constructed values are already materialized. Explicit Lazy adapters preserve the four independently observed Show/NFData bottom probes; this is not a general Haskell lazy heap implementation.

The modern `Curve_Edwards25519` implements18methods across EllipticCurve(8), Arith(3), BasepointArith(7), and **no DH instance**. sizeBits255/orderBits253. Point encoding/decoding and arithmetic delegate public API. Scalar encode/decode is big-endian EXACTLY32bytes; invalid length CryptoError_SecretKeySizeInvalid. scalarFromInteger rejects negative or >=2^256, then reduces moduloL; it does not restrict to the253-bit order. scalarToInteger uses LE scalar encoding. KeyPair generation consumes one64byte RNG request and pairs the scalar with its base multiplication. Prime and X25519 capability behavior stays unchanged.

## Independent evidence

`EdwardsPublicReference.hs` calls actual pinned modules, not Kotlin arithmetic. `OriginalEdwards25519Spec.hs` is the full original31-example suite with module declaration only changed:25properties×100 +6literal examples, seed1729. Kotlin replays all25property families with100source-generated tuples and all6literal examples through public types for both engine profiles. Point/scalar sample inputs come from independently accepted original Arbitrary generators, pinned as dependencies.

`OriginalEdwardsModernSpec.hs` preserves original ECCSpec except module declaration and replacing the CurveArith generator list by the single Edwards entry. All original52examples execute; its4arithmetic property families therefore necessarily exercise Edwards (100,100,20,20 source trials). Its DH generators/vectors still concern other curves and are native supporting evidence, not a claim of Kotlin X448 completion. The public corpus additionally replays these4families over100 exact original TestDRG tuples (seed9001+i,size i%101). Edwards has no VectorPoint entries because that original existential list requires DH; no fake Ed DH tests are added.

The corpus covers every public/modern operation, all length0..69 boundaries, empty/zero/order/max and negative Integer bounds, malformed/noncanonical encodings, Show precedences, equality, RNG requests/exceptions, low-order/cofactor semantics, fresh copies and independently observed demand probes. Kotlin ownership tests mutate caller input/output/provider buffers and verify stored values; source allocations are explicit fresh B.alloc/B.allocAndFreeze.

Secret storage is privately owned, defensive copies and a best-effort Cleaner are used. JVM layout, locked memory, constant-time JIT execution, GC secure erasure, temporary-copy lifetime and Haskell FFI scheduling are NOT proven. Physical source allocation padding (40byte scalar,160byte point), raw pointers and arbitrary Haskell bottoms are not public Kotlin memory-layout promises. Both portable arithmetic profiles are tested, not two actual OS ABIs. No accepted field/scalar/point source is changed.

## Reproduce

From this folder:

```sh
CABAL_DIR=/tmp/kotdoc-haskell/cabal-state /tmp/kotdoc-haskell/cabal build --offline --builddir=/tmp/kotdoc-curve25519-public-build --with-compiler=/tmp/kotdoc-haskell/ghc/bin/ghc --with-hc-pkg=/tmp/kotdoc-haskell/ghc/bin/ghc-pkg edwards25519-public-reference
```

From repository root (oracle path is the executable produced above):

```sh
python3 tools/import-edwards25519-public-fixtures.py --check-source
python3 tools/import-edwards25519-public-fixtures.py --verify-oracle --oracle /tmp/kotdoc-curve25519-public-build/build/aarch64-osx/ghc-9.12.2/kotdoc-edwards25519-public-reference-0.1.0.0/x/edwards25519-public-reference/opt/build/edwards25519-public-reference/edwards25519-public-reference
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --no-build-cache -Dorg.gradle.vfs.watch=false -Pkotlin.compiler.execution.strategy=in-process --project-cache-dir /tmp/kotdoc-edwards25519-public-gradle :core:test --tests io.heapy.kotdoc.crypto.CryptoEdwards25519Test
```

Existing crypton BSD and RAM BSD notices remain packaged. Original expected JSON is generated only by Haskell; verifier checks unchanged vendor source-map bytes, mechanical original-test transformations, source/fixture hashes, samples, original suites and every expected observation. No expected changes are derived from Kotlin results.
