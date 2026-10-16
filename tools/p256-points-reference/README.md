# P-256 raw point-engine checkpoint

This checkpoint ports all of pinned crypton2.0.1 cbits/p256/p256_ec.c: eleven
static helpers and five actual exported functions. It is a checkpoint within
613, not completion of public Haskell P256/ECC APIs, their complete test suites,
remaining cryptography, or the complete Pandoc/calibre goal.

Production Kotlin uses the accepted specialized field/word implementations.
There is no BigInteger, C/Haskell subprocess, generic affine fallback or native
cryptography dependency in production. The source notice remains in the existing
crypton-p256 BSD notice. The declared crypton_p256_point_mul symbol has no
implementation in the pinned sources; the actual export is
crypton_p256e_point_mul. No implementation is invented for the absent symbol.

The exact field inverse chain has287 squares,13 multiplies and6 assignments.
Point formulas preserve the original operation/write order, temporary values,
exceptional raw outputs, conditional copies, selector table scans, precomputed
Jacobian table, scalar windows and affine conversion. The two digit profiles
retain their different redundant limb representations throughout.

PointsReference.c normally includes unchanged pinned p256.c and p256_ec.c,
which includes the selected unchanged field header. OBSERVED builds instead
include mechanically verified observational copies. Existing accepted field/word
observation copies are reused without alteration; ObservedPoints.c adds only
function counters and read-only snapshots/table-read/branch events. All ordinary
observational outputs, input mutations and guards must match unchanged C on
both profiles. Expected raw values and trace hashes never come from Kotlin.

Trace encoding is independent of native byte layout: UTF8 event name,zero byte,
four little-endian bytes for length,then eight little-endian bytes per unsigned
value. An FNV1a64 accumulator consumes EVERY raw field/table/point event in order;
event counts and bounded full prefixes are also retained. This avoids storing
millions of redundant intermediate arrays in each scalar fixture. It is a
replay check, not a cryptographic commitment or constant-time proof. It covers
full field products, reduction halves, zero passes, every selector table read,
complete runtime precomputation tables, branches and each scalar-window result.

From the repository root:

```
python3 tools/extract-p256-points-reference.py --check
python3 tools/import-p256-points-fixtures.py
python3 tools/import-p256-points-fixtures.py --verify-oracle
python3 tools/import-p256-points-fixtures.py --check-source
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --no-build-cache -Dorg.gradle.vfs.watch=false --project-cache-dir /tmp/kotdoc-p256-points-gradle -Pkotlin.compiler.execution.strategy=in-process :core:test --tests io.heapy.kotdoc.crypto.CryptoP256PointsTest
```

Captured C builds use Apple clang21 on arm64. include32/include64 exercise both
digit algorithms on that host, not a real32-bit OS ABI. The original selectors
walk flat across nested precomp arrays; observations retain their native layout
behaviour. Invalid pointers, undersized buffers, partial overlaps, selector
output/table overlaps and exact-self memcpy in pointNegate are excluded.
Defined whole-coordinate and cross-coordinate aliases, including coincident
outputs, are captured as raw operations; group-law expectations do not apply
to outputs that overwrite still-needed inputs.

Domain boundaries are explicit per observation in mathDomain/math metadata.
EVERY observation receives unchanged-C raw parity. Only explicitly supplemented
cases additionally use independent Python affine arithmetic. Scalar constructors
and original P256Spec properties cover all256bits; all six original generator
intervals are represented. Raw C scalar loops/exports nevertheless document
scalar<n. Inputs beyond that condition are captured observations, not universal
portability/correctness guarantees. In particular the unchanged double-scalar
export points_mul_vartime(n,1,G) returns(0,0),notG: the nonzero-byte scalar check
does not recognize nG as infinity before the incomplete addition formula.
This exact regression remains recorded; no automatic scalar reduction repairs it.

The raw add/mixed-add formulas deliberately do not handle all infinity/equality
cases. add_or_double handles equality, not every infinity identity. Raw negate
returns p-y without reduction, so negating encoded(0,0) gives(0,p). Haskell
pointAdd separately short-circuits infinity and returns an original operand.
Those public wrapper policies are a subsequent checkpoint, not silently inserted
into this C port. The accepted field source-range exceptions remain applicable;
point arithmetic does not impose invented tighter limb bounds between steps.

The corpus includes original S/T/R and validation literals, every scalar bit,
every byte scalar on a nonbase point, all selector indices and reads, scalar
order/window boundaries, projective rescalings, redundant/raw field inputs,
conditional-copy masks, alias schedules and double-scalar zero/equality/inverse
branches. Public P256Spec is read and its relevant inputs retained; its full
Haskell wrapper assertions and modern ECC/ECDH/subgroup suites are not claimed
as already ported. JVM/JIT/GC/hardware timing and memory-erasure parity remain
unverified.
