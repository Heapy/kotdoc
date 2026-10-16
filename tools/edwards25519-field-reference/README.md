# Complete Edwards Donna field and tables checkpoint

Production Kotlin ports the complete, distinct Edwards Donna field algorithms
from crypton2.0.1:15functions in the unsigned32x10 profile,16functions in the
unsigned64x5 profile, plus all three common power helpers. The32 profile uses
alternating26/25-bit limbs, while64 uses51-bit limbs and unsigned128 products.
Only the verified generic unsigned128 primitive is reused. X25519/P256 field
formulas are not reused, and no BigInteger/native program is a production
arithmetic dependency. The public-domain attribution blocks remain verbatim.

The inventory is copy/add/add_after_basic/add_reduce/sub/sub_after_basic/
sub_reduce/neg/mul/square/square_times/expand/contract/move_conditional_bytes/
swap_conditional, plus the real64 mul_noinline wrapper and explicit32 macro
alias. Kotlin preserves arithmetic semantics without claiming JVM inlining or
constant timing. Common helpers are pow_two5mtwo0_two250mtwo0 (245squares/7muls),
recip (254/11) and pow_two252m3 (251/11), with their exact original schedules.

Raw differences are retained: sub32 carries only through the early limbs;
add_after_basic32 reduces while64 does not. Profile64 mul/square carry r0 into
r1 without X25519's extra r1 carry; square_times64 has its own high-half carry
schedule. All unsigned32 assignments and pre-widening argument expressions
retain their source wrap widths. square_times32 repeats the identical square
arithmetic on locally owned limb state; native observations check every finite
count/input/alias combination in the corpus.

All15profile mask/offset constants, including unused40/56-bit masks, are
checked against native constants. All tables are translated: basepoint coordinates, d,2d,sqrt(-1),32sliding Niels
triples per profile (1030/515words total), and256x96packed basepoint bytes.
Every word and byte is compared against an independent native table dump, not
just an aggregate hash. Returned Kotlin arrays are defensive snapshots.

The independent deterministic corpus contains11177observations across both
profiles: every helper and table, raw guarded word/byte buffers and defined
aliases (none,out=a,out=b,a=b,all; whole-object byte aliases), reduced inputs,
source-produced redundant operation traces, full unsigned-word observations,
p-adjacent/noncanonical bytes and high-bit masking. Output/input mutations and
all guards/tails are compared. Raw-word observations outside source-produced
bounds remain ordinary parity records without a mathematical-reduction claim.
Expected values are never normalized or produced by Kotlin.

The nine original headers are copied byte-for-byte and their pinned archive/
file hashes verified. Ordinary and observed C libraries are built separately.
ObservedHelpers.h is a mechanically verified copy with callbacks only after
all30 syntactic power-helper field operations; arithmetic/branches are untouched.
Every request must agree on final raw buffers in BOTH libraries before traces
are retained. Trace records check each operation label/raw limbs and weighted
square/multiply counts independently in native and Kotlin tests. SourceProbe
also checks every original native constant against literal text; that source
preparation proof is distinct from the translated Kotlin table replay.

Limits: square_times32 uses signed int count; count<=0 eventually reaches signed
decrement UB. square_times64 count0 requires2^64iterations. Finite replay uses
positive counts only and invents no identity result. Swap masks0/1 avoid the
source signed-negation INT_MIN UB; arbitrary move masks are unsigned bitvectors.
Native byte buffers use aligned8 offsets; source word-pointer casts retain C
alignment/effective-type assumptions, so no blanket ISO-C portability guarantee
is claimed. Both forced algorithm profiles run on the native64 host, not an
actual32-bit OS ABI. JVM timing, physical erasure and address-layout equivalence
are not proven.

Commands from root:

```
python3 tools/extract-edwards25519-field-reference.py --check
python3 tools/edwards25519-field-reference/verify-source.py
python3 tools/import-edwards25519-field-fixtures.py
python3 tools/import-edwards25519-field-fixtures.py --verify-oracle
python3 tools/import-edwards25519-field-fixtures.py --check-source
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --no-build-cache -Dorg.gradle.vfs.watch=false -Pkotlin.compiler.execution.strategy=in-process --project-cache-dir /tmp/kotdoc-edwards25519-field-gradle :core:test --tests io.heapy.kotdoc.crypto.CryptoEdwards25519FieldTest
```

Native compiler flags are-O2 -std=c99; the fixture manifest pins clang version,
original/transformed headers, tools/notices and deterministic input/case hashes.
The32force and NO_INLINE_ASM selections are explicit in SourceProbe.c.

Complete modm32/64 remains next (all16functions/profile, nine30-bit vs five56-bit
scalar words, including batch helpers), then point formulas/selectors/scalar
loops/crypton extensions, then public wrappers and modern Edwards arithmetic/
basepoint integration. Haskell Scalars allocate40bytes but32profile writes36;
padding is not value data. Point equality uses projective cross-products, not
raw160-byte equality. Scalar reduction/torsion semantics must not be repaired.
The later public acceptance target is the complete original Edwards25519Spec:
25properties+6literal examples and its actual Scalar/PrimeOrder/Point Arbitrary
instances. No field subset proves that suite. Decaf448 and remaining613/full
Pandoc/calibre work remain separate.
