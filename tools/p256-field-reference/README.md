# P-256 field-header checkpoint

This checkpoint ports both complete pinned crypton 2.0.1 p256_gf.h headers.
It is part of task613, not completion of task613, the point engine, public
P256/ECC operations, or the full Pandoc/calibre project.

The 32-bit profile has nine limbs, starting with 29 bits and alternating
29/28 bits (positions 0,29,57,86,114,143,171,200,228). The 64-bit profile has
five limbs, alternating 51/52 bits (positions 0,51,103,154,206). Both use R=2^257.
The profiles are compiled separately on the current host; selecting include32
is not evidence for a real 32-bit OS ABI, stack layout or calling convention.

Production uses static Kotlin arithmetic, including explicit unsigned128
low/high pairs for the 64-bit profile. It does not call C, Haskell, BigInteger
or external cryptography. Constants/table limbs and product/reduction
expressions are mechanically transcribed by extract-p256-field-reference.py.
That tool has an explicit integer expression parser preserving C precedence,
casts, unsigned widths and overflow, rather than interpreting C in production.
The rest of the Kotlin methods preserve the source loops and operation order.
The original notice is retained by the existing crypton-p256 BSD notice.

FieldReference.c ordinarily includes unchanged pinned p256.c and p256_gf.h.
An OBSERVED build includes mechanically verified copies with read-only counters
and snapshots. All ordinary outputs, input mutations and guards from the
observed build must match the unchanged build on each digit profile. The
word observer from checkpoint610 is reused unchanged for nested word counters.
Native expected values never come from the Kotlin implementation or generator.

Snapshots retain product low/high words, the normalized reducer scratch array,
every even/odd elimination half, carry-reduced outputs, each variable-time
zero-normalization pass and cleared conversion temporaries. Guarded arrays
capture raw limbs at nonzero aligned offsets and exact whole-object aliases.
All 840 precomputed table limbs, the Montgomery constants, zero offsets and
zero/p/2p representations are observed independently. Arbitrary partial overlaps,
word/field type-punning and self-overlapping memcpy assignment are excluded.

From the repository root:

```
python3 tools/extract-p256-field-reference.py --check
python3 tools/import-p256-field-fixtures.py
python3 tools/import-p256-field-fixtures.py --verify-oracle
python3 tools/import-p256-field-fixtures.py --check-source
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --project-cache-dir /tmp/kotdoc-p256-field-gradle -Pkotlin.compiler.execution.strategy=in-process :core:test --tests io.heapy.kotdoc.crypto.CryptoP256FieldTest
```

A C11 compiler supporting unsigned128 is required by the original 64-bit header.
The captured native compiler is Apple clang21, arm64-apple-darwin25.6.0. No JVM,
compiler, GC, hardware timing or memory-erasure equivalence is claimed.

Range comments are not silently strengthened into mathematical guarantees.
The original32-bit scalar8 can exceed its stated output bound for some inputs
within the documented expanded30/29-bit bounds: an observed odd limb is
553958708, exceeding2^29. Its final carry can be15, while reduce_carry documents
carry<8. The unchanged result is preserved; independent congruence still holds.
Likewise64-bit sum permits no-overflow64 input sums in its comment, but those
can produce a carry greater than reduce_carry's documented64 bound and return
limbs wider than the field-range postcondition. These are separately labelled
raw observations; expected values are never rewritten to satisfy comments.
Direct arbitrary64/128 temporary arrays test the reduction algorithm's exact
unsigned behaviour, not a claim that every such array is reachable from field
multiplication or has a universally correct modular residue.

Inputs within the ordinary expanded field bounds additionally receive an
independent Python modular-congruence check. Full-width unchecked sum and
direct-degree observations deliberately receive only exact native raw parity.
Variable-time zero detection is compared by raw normalization trace, including
its original0/p/2p comparisons. No iteration cutoff or new input validation is
added to the production field operations.

Point inversion, point formulas, selection/table lookup, scalar multiplication,
public Haskell wrapper demand/ownership contracts, ECDH/subgroup suites and all
remaining cryptography in task613 remain separate checkpoints. The original
P256Spec has no field-helper units; this raw reference supplements it and does
not claim the full public suite has been ported or executed in Kotlin.
