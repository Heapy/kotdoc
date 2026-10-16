# P-256 word arithmetic reference

This development-only observer ports the finite word-arithmetic slice in pinned
crypton 2.0.1 `cbits/p256/p256.c`. Production is Kotlin and does not execute C,
Haskell, BigInteger or an external cryptography implementation for this slice.
Field headers, `p256_ec.c`, the public P256 wrappers, point multiplication and
remaining public ECC tests are separate pending work.

`WordsReference.c` normally includes the complete pinned `p256.c` unchanged.
The generator compiles it twice, selecting the original include32/include64
headers. `-DOBSERVED` instead includes `ObservedP256.c`, a mechanically verified
copy retaining all upstream text and notices with only observational insertions:
27 function-entry counters, the inverse-loop counter and pre-iteration bounded
R/S/U/V snapshot, and a reducer-correction counter. The inverse hook longjmps
before iteration limit+1; it does not assign a return value to inverse(0).
All ordinary results, input/output bytes and memory guards from the observed
copy must equal results from the unchanged C build. Only the bounded inverse
checkpoints use the instrumented build without an unbounded original replay.

From the repository root:

```
python3 tools/extract-p256-words-reference.py --check
python3 tools/import-p256-words-fixtures.py
python3 tools/import-p256-words-fixtures.py --verify-oracle
python3 tools/import-p256-words-fixtures.py --check-source
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew :core:test --tests io.heapy.kotdoc.crypto.CryptoP256WordsTest
```

Generation requires a C11 compiler with 128-bit integer support for the 64-bit
profile. `cc -std=c11 -O2 -Ivendor/crypton-2.0.1/cbits/include64` selects that
profile; include32 selects 32-bit digits even on a 64-bit host. This checks both
digit algorithms, not an actual 32-bit OS ABI, stack layout or calling convention.
The captured compiler was Apple clang 21.0.0, arm64-apple-darwin25.6.0.

The deterministic corpus retains all five word constants, every operation and
helper, every bit and carry/borrow boundary, masks, top-digit wrap, all defined
nonzero shift residues, disjoint/in-place outputs and nullable output arithmetic.
All four guarded word objects and byte buffers at nonzero offsets are retained.
Aliases share exact whole objects; arbitrary partially overlapping typed objects
and byte/word type-punning are excluded. The original P256Spec scalar generator's
six full-width intervals are represented, including operands above the order.
All ten original point-validation literals and their invalid neighbours replay,
including both quotient-correction regressions. The original source/tests and
both headers are pinned by SOURCE.json hashes. Native expectations and operation
counts are independent of the Kotlin observer.

Boundaries are explicit. Zero-residue shl/shr invokes a shift by digit width in C
and is excluded; negative shifts, out-of-bounds bit indices, invalid pointers,
undersized buffers and arbitrary partial overlaps are excluded. Upstream shr1's
signed shift into the sign bit, signed right shifts and signed/unsigned casts
retain compiler-specific/undefined C idioms; observations capture this compiler's
ordinary behaviour and do not establish portable ISO C behaviour. No compiler
or hardware timing/memory-erasure equivalence is claimed. The original inverse(0)
is independently observed not to return within a finite timeout on both profiles;
bounded inverse states additionally cover zero/even/composite moduli and zero or
non-coprime inputs. These checkpoints establish finite prefixes, never termination.
The Kotlin inverse has no production iteration cutoff; its audit-only bound is
used solely for these observations. Invalid modulus arithmetic outside original
n/p assumptions is not redefined as general-purpose modular arithmetic.

The safe inversion schedule remains the original Montgomery addition chain.
Operation counters establish its exact finite call schedule, not constant-time
execution of JVM/JIT/GC/hardware or security certification. No entire public
P256Spec run, public API port or complete Pandoc/calibre port is claimed here.
