# Public Curve25519 and modern X25519 checkpoint

Production ports actual Crypto.PubKey.Curve25519 from crypton2.0.1, using the
accepted specialized Kotlin Donna engine. It contains all three owned immutable
byte types (SecretKey/PublicKey/DhSecret) and all six exported functions. RAM
0.22.1 source and its existing notice remain the actual byte-wrapper dependency.
Secret/DH Show use <scrubbed-bytes>; public Show uses the exact Haskell byte
string representation, including constructor precedence. The original explicit
SecretKey DebugShow instance emits lowercase hexadecimal and is separately
exposed as debugShow(), never the default Show.

All three constructors accept any32bytes and copy them without normalization;
secretKey's source validation hook always returnsTrue. Size errors retain their
distinct CryptoError names. RNG requests exactly32bytes once then clamps an
owned copy (no retry/reduction). toPublic and dh clamp internally without
changing stored secret bytes. Raw DH accepts all-zero shared bytes. The modern
X25519 adapter encodes raw bytes, sizeBits255 and rejects zero shared secrets
only in ecdh; ecdhRaw preserves zero. No additional canonical-coordinate or
subgroup constructor validation is introduced.

CryptoEcc now has the four actual source capability classes: EllipticCurve,
EllipticCurveDH, EllipticCurveArith and EllipticCurveBasepointArith. PrimeCurve
is only a Kotlin intersection convenience for the three accepted prime curves.
X25519 implements base+DH only, with no arithmetic/basepoint stubs. Existing
prime algorithms/policies are unchanged; two prime test helper type constraints
are tightened to their real joined capability. The complete existing prime
oracle corpus remains a required regression alongside class-matrix checks.

The dev oracle imports actual original Crypto.PubKey.Curve25519 and Crypto.ECC,
plus pinned RAM. It executes the whole unchanged original Curve25519Spec body
(four KATs; only module declaration changes). The modern partition preserves
original assertion/property bodies: Curve Arbitrary selects X25519 only;
decode/weak vectors are filtered by their existential curve; the two properties
requiring base+DH remain. Four CurveArith properties and the two P256-specific
ECDH KATs are excluded explicitly. Result:4decode+7weak+2property examples,
seed1729, point-roundtrip100 trials and commutativity20 trials. This does not
claim the whole modern suite: X448 and Edwards remain pending.

The deterministic independent corpus additionally checks every export, all
256 byte values/Show precedence, equality, finite NFData, constructor sizes,
RNG/error/one-request semantics, raw/public key operations, noncanonical and
weak coordinates, all original four KATs and both applicable property families.
Its controlled payloads are separate from the original Hspec/QuickCheck random
stream; none of its expected values come from Kotlin. Both Kotlin Donna profiles
replay wrapper results; the modern adapter uses the accepted64profile.

Kotlin ownership tests mutate caller input/output/callback snapshots and verify
private storage isolation, preserved constructor bits and unclobbered RNG input.
Secret storage uses an owner-independent Cleaner action; local calculation/RNG
copies are wiped in finally. These checks do not prove JVM constant timing,
JIT/GC/hardware erasure, physical pinned addresses, allocation failures or GHC
bottom/laziness equivalence. MonadRandom's valid provider returns exactly the
requested byte count; malformed provider lengths are excluded from source
parity because original pointer access can exceed allocation. Kotlin rejects
such providers explicitly without claiming original error equivalence.

Build from this folder:

```
CABAL_DIR=/tmp/kotdoc-haskell/cabal-state /tmp/kotdoc-haskell/cabal build --offline --builddir=/tmp/kotdoc-curve25519-public-build --with-compiler=/tmp/kotdoc-haskell/ghc/bin/ghc --with-hc-pkg=/tmp/kotdoc-haskell/ghc/bin/ghc-pkg curve25519-public-reference
```

From root, ORACLE is the built executable:

```
python3 tools/extract-curve25519-public-reference.py --check
python3 tools/import-curve25519-public-fixtures.py --oracle "$ORACLE"
python3 tools/import-curve25519-public-fixtures.py --oracle "$ORACLE" --verify-oracle
python3 tools/import-curve25519-public-fixtures.py --check-source
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --no-build-cache -Dorg.gradle.vfs.watch=false -Pkotlin.compiler.execution.strategy=in-process --project-cache-dir /tmp/kotdoc-curve25519-public-gradle :core:test --tests io.heapy.kotdoc.crypto.CryptoCurve25519Test --tests io.heapy.kotdoc.crypto.CryptoEccModernPrimeTest
```

Source verification reconstructs the original suite/partition exactly, verifies
all pinned crypton/RAM bytes and notices, and checks tools/fixtures metadata.
OriginalSuite.txt retains actual native suite execution evidence. Native programs
are development oracles only; no Haskell/native cryptography production dependency
is introduced. This does not complete task613 or the full Pandoc/calibre goal.
