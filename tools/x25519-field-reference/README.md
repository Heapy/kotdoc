# Donna X25519 field checkpoint

This checkpoint ports the complete field prefixes from both pinned Donna
implementations, before fmonty. The separate ladder/inversion/public X25519
checkpoint remains pending; this does not complete Curve25519, modern ECC,
task 613 or the full Pandoc/calibre goal.

The 32 profile uses ten signed int64 limbs with alternating 26/25-bit weights.
All 15 helpers are covered: fsum, fdifference, fscalar_product, fproduct,
freduce_degree, div_by_2_26, div_by_2_25, freduce_coefficients, fmul,
fsquare_inner, fsquare, fexpand, s32_eq, s32_gte and fcontract. Product/square
formulas retain explicit signed-int32 casts, signed64 operations and the source
reduction/contract stages; Kotlin represents the source bit-vector behavior.

The 64 profile uses five unsigned 51-bit limbs and uint128 intermediates.
All nine helpers are covered: fsum, fdifference_backwards, fscalar_product,
fmul, fsquare_times, load_limb, store_limb, fexpand and fcontract. The existing
verified generic unsigned128 primitive is reused; P256 field formulas are not.
Multiplication preserves each source term's unsigned64 pre-multiplication by19
and unsigned128 product/addition. No generic BigInteger arithmetic, native
library or subprocess is a production dependency.

The deterministic independent corpus captures 6,734 observations directly from
unmodified original C prefixes with thin wrappers. It compares entire padded
word/byte buffers, including input mutation and untouched guards/tails; aliases
are none, out=a, out=b, a=b and all shared where defined. Product/square_inner
remain disjoint under their source precondition. Multiplication's temporaries
permit aliases, including ones used by the original inversion code. The corpus
also records operation-state traces, canonical/noncanonical p boundaries,
little-endian load/store, high-bit masking, negative limbs, exact signed division
boundaries, equality masks and wide scalar factors. Expected buffers are never
normalized or derived from Kotlin.

The 32 source explicitly requires two's complement and arithmetic right shift.
It also contains ISO-C undefined negative/overflowing signed-left-shifts,
including contract packing and s32_eq;
those observations are labelled selected-compiler behavior, not general ISO-C
portability proofs. The manifest pins the clang version and flags and native
replay requires that compiler. Multiplication/reduction finite magnitude bounds
are retained. In the 64 corpus, difference cases outside its documented
out[i]<2^52 precondition are explicitly marked and still replayed as ordinary
unsigned-word observations. No outside-precondition mathematical guarantee is
claimed. Both algorithms are compiled on the same native host; no actual32-bit
OS ABI, timing, address-layout or hardware erasure equivalence is claimed.

fsquare_times is do-while with unsigned64 count: count0 requires2^64 iterations.
Finite tests use positive counts and explicitly exclude count0; no identity
result is invented. The Kotlin loop retains unsigned wrap behavior. The next
ladder checkpoint will call only the original positive-count schedules.

Source and fixture verification checks the pinned crypton archive identity,
exact C bytes, complete prefix extraction, exact Google BSD32/public-domain64
notices, deterministic inputs and semantic corpus hash. Native replay rebuilds
ordinary original C; it does not modify arithmetic or suppress output differences.
The distinct Edwards25519 Donna implementation remains a separate future port.

Commands from the repository root:

```
python3 tools/extract-x25519-field-reference.py --check
python3 tools/import-x25519-field-fixtures.py
python3 tools/import-x25519-field-fixtures.py --verify-oracle
python3 tools/import-x25519-field-fixtures.py --check-source
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --no-build-cache -Dorg.gradle.vfs.watch=false -Pkotlin.compiler.execution.strategy=in-process --project-cache-dir /tmp/kotdoc-x25519-field-gradle :core:test --tests io.heapy.kotdoc.crypto.CryptoX25519FieldTest
```

Native shims may also be built directly:

```
clang -O2 -std=c99 -DFIELD_BITS=32 -dynamiclib tools/x25519-field-reference/FieldReference.c -o /tmp/kotdoc-x25519-field32.dylib
clang -O2 -std=c99 -DFIELD_BITS=64 -dynamiclib tools/x25519-field-reference/FieldReference.c -o /tmp/kotdoc-x25519-field64.dylib
```
