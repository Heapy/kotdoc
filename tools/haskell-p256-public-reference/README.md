# Public P256 module and original P256Spec checkpoint

This checkpoint ports the entire crypton2.0.1 Crypto.PubKey.ECC.P256 public
module: two immutable value types and all30 exported terms. Production uses
the accepted specialized Kotlin word/field/point engines. BigInteger appears
only at the original Integer conversion boundary, not as an arithmetic backend.
No native cryptography, Haskell or C subprocess is a production dependency.
Modern Crypto.ECC policies, generic ECC, ECDH/subgroup and remaining613 work
remain separate. This does not complete613 or the full Pandoc/calibre goal.

The dependency is pinned ram0.22.1, not an assumed memory version. Its full
source archive and BSD notice are retained. Scalar storage is private owned
32-byte little-endian native-representation storage with defensive snapshots;
Point uses64bytes,x theny. The public oracle captures the64-bit little-endian
native host. Both Kotlin digit profiles are replayed against these public
results; their underlying C digit algorithms were independently checked in
previous checkpoints. No actual32-bit OS ABI or big-endian host parity is claimed.

RAM's ScrubbedBytes Show is exactly<scrubbed-bytes>; derived Scalar Show is
Scalar <scrubbed-bytes>, with constructor parentheses at precedence>10.
Point Show is derived Point followed by a Haskell string of raw native storage
bytes, not hexadecimal or network-order pointToBinary. Scalar equality scans
all32bytes in Kotlin; Point equality retains byte equality. These algorithms
and source NFData forcing of finite constructed values do not establish JVM
constant timing or general GHC bottom/laziness equivalence.

The Kotlin Scalar Cleaner uses an owner-independent wipe action and pointDh
clears its locally owned64-byte temporary in finally. Ownership and deterministic
local cleanup are tested directly. RAM uses a GHC weak finalizer; timing,
JIT/GC/hardware erasure, physical pinned addresses, retained copies, allocation
failure and finalizer equivalence are not proved. Audit temporary snapshots are
explicit development-only observations and intentionally reveal bytes when
requested; default Scalar Show never reveals content. The Kotlin pointDh
release event denotes local cleanup, not a claim that native RAM finalizes at
that lexical boundary.

OriginalP256Spec.hs preserves the entire original suite, changing only its
module declaration. The native oracle imports the actual original P256 public
module and the pinned original RAM implementation. It executes all31 Hspec
examples, including20 property families, with seed1729/max-success200:4000
property checks plus11 literal examples. --generators invokes the original
P256Scalar and QAInteger Arbitrary instances to capture400 independent sample
rows at explicit seeds/sizes. Kotlin translates every family and literal unit
using independent original-module expected results; sample rows are not
mislabelled as the exact internal sample stream of the separate Hspec run.

The independent corpus additionally covers every exported value operation,
byte lengths, integer boundaries, Eq/Show/NFData, unsafe-versus-valid decoding,
pointX, source infinity quirks, RNG one-request semantics and callback failure.
RNG probes instantiate the original MonadRandom interface with controlled
payloads or an explicit exception. There is exactly one32-byte request and no
retry/reduction for zero,n or all-ones data. Scalar inverse(n) returns zero in the original module and is captured as an
independent regression. Scalar inverse(0) is not assigned a finite result:
production retains the original unbounded variable-time loop, whose raw bounded
prefixes/non-return observations are retained in checkpoint610. This is an
input-specific observation, not a general claim for arbitrary moduli or inputs.

Source policies are not repaired: toPoint0 errors, toPointn yields infinity,
pointAdd returns an original operand on infinity, pointNegate infinity gives
(0,p), pointX is Nothing only for all-zero storage and otherwise reducesx byn,
and pointsMulVarTime preserves the existing out-of-precondition raw quirks.
Malformed-size constructors retain their distinct CryptoError names.

Commands from this folder:

```
CABAL_DIR=/tmp/kotdoc-haskell/cabal-state /tmp/kotdoc-haskell/cabal build --offline --builddir=/tmp/kotdoc-p256-public-build --with-compiler=/tmp/kotdoc-haskell/ghc/bin/ghc --with-hc-pkg=/tmp/kotdoc-haskell/ghc/bin/ghc-pkg p256-public-reference
```

From the repository root, with ORACLE naming the resulting executable:

```
python3 tools/extract-p256-public-reference.py --check
python3 tools/import-p256-public-fixtures.py --oracle "$ORACLE"
python3 tools/import-p256-public-fixtures.py --oracle "$ORACLE" --verify-oracle
python3 tools/import-p256-public-fixtures.py --check-source
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --no-build-cache -Dorg.gradle.vfs.watch=false --project-cache-dir /tmp/kotdoc-p256-public-gradle -Pkotlin.compiler.execution.strategy=in-process :core:test --tests io.heapy.kotdoc.crypto.CryptoP256Test --tests io.heapy.kotdoc.crypto.CryptoP256OriginalSpecTest
```
