# Complete raw Donna X25519 checkpoint

Both complete pinned Donna sources are retained byte-for-byte. Production Kotlin
ports the full suffix: fmonty, swap_conditional, cmult, crecip and the exported
crypton_curve25519_donna. The accepted distinct signed10-limb and unsigned5-limb
field implementations are reused unchanged. No native program or BigInteger is
a production dependency. Public Haskell wrappers, modern X25519 adapter, X448,
Edwards and the remainder of task613/Pandoc/calibre remain pending.

The scalar is copied before clamping (low3 bits cleared, top bit cleared, bit254
set); basepoint expansion masks bit255. All256 scalar bits are processed by the
original double-buffer ladder and conditional swaps. fmonty destroys its four
input coordinates as the source does. The two distinct inversion schedules
perform254 squarings and11 multiplications, including zero inputs without special
cases. Output serialization and byte input/output aliases retain source order.

The independent corpus covers1782 observations across both algorithm profiles:
all five suffix helpers, raw negative32/redundant64 fields, guarded whole-buffer
mutations, reciprocal aliases, swap identity aliases, ladder output/q and x/z
aliases, Donna output/secret/point/all aliases, noncanonical/highbit/loworder
coordinates and all four original Curve25519Spec KATs. The literal KAT values
are verified against the unchanged original test source, not Kotlin output.

Original32.h and Original64.h contain the full unmodified pinned sources.
Observed32.h and Observed64.h are dev-only mechanical transformations: two
observer declarations, one callback after each completed ladder round and one
after every inversion field operation. Extractor --check reconstructs and checks
all exact transformed bytes. No arithmetic or branches are changed. Each corpus
case is run against BOTH ordinary and observed native C and must agree on every
raw output/mutation before trace data are retained. Trace cases compare every
one of256 rounds' four coordinate arrays, and each inversion operation's raw
limbs/label; tests independently check254-square/11-multiply counts. Unclamped
cmult input coverage is separate from clamped Donna export coverage.

Aliases are whole-object aliases, not arbitrary overlapping offsets. fmonty
uses disjoint input/output slots and bounded source-domain inputs. Swap tests
use only0/1 and reduced32 limbs; no interpretation for arbitrary masks is
invented. All observations, including zero/noncanonical inputs, replay normally.
The signed32 C implementation contains ISO-C undefined signed left shifts;
these are selected pinned-clang bitvector observations under original two's
complement/arithmetic-shift assumptions, not an ISO-C portability guarantee.
Both algorithm profiles run on the native64 host; a real32-bit OS ABI, timing,
constant-time JVM behavior, physical erasure and address layout are not proven.

Commands:

```
python3 tools/extract-x25519-reference.py --check
python3 tools/import-x25519-fixtures.py
python3 tools/import-x25519-fixtures.py --verify-oracle
python3 tools/import-x25519-fixtures.py --check-source
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --no-build-cache -Dorg.gradle.vfs.watch=false -Pkotlin.compiler.execution.strategy=in-process --project-cache-dir /tmp/kotdoc-x25519-gradle :core:test --tests io.heapy.kotdoc.crypto.CryptoX25519Test
```

The manifest pins the archive identity, original source hashes, transformed
sources, original tests, generators, shim and this document, plus deterministic
input/case hashes and clang version. Replay recompiles ordinary/observed C with
-O2 -std=c99 and verifies independent expected values; expectations are never
normalized or regenerated from Kotlin.
