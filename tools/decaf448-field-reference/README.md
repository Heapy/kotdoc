# Decaf p448 portable field checkpoint

Complete first field checkpoint only. Production is specialized Kotlin limb arithmetic (no BigInteger/native/external fallback), reusing only accepted generic Wide128 add/shift/unsigned-product machinery. Nothing in accepted P256/X25519/Edwards engines is changed.

## Full remaining Decaf/X448 inventory and checkpoint gates

`RemainingSourceInventory.json` records every pinned cbits/decaf file/hash/line count/API macro identifier, plus public modules/tests. It is an inventory of source identifiers, not a claim each identifier is already implemented.

1. **This checkpoint:** p448/f_field.h, arch_32/ref64 f_impl.h/.c, f_generic.c, f_arithmetic.c and the field-specific inlines in include/field.h:25 actual functions +add_nr macroalias +ZERO/ONE/MODULUS. No point precomputation table is called a field table.
2. **Scalar:** ed448goldilocks/scalar.c (341lines), API scalar Montgomery arithmetic/inverse/decode/encode/conditional/halving/destroy, all constants and32/64 API word profiles. API word size is independently defined by decaf/common.h, while field ARCH_WORD_BITS is from arch_intrinsics.h.
3. **Point and X448:** decaf.c (1621lines), decaf_tables.c (354lines), combined decaf_all.c, point_448.h:extended/Niels formulas, all private selectors/recode/precompute/batch/encode/decode/scalarmults/ladder/direct/invert operations, cofactor/isogeny/error/identity policies and complete precomputed constants. Tables are a separate point-engine checkpoint.
4. **Curve448 public/modern:** Crypto/PubKey/Curve448.hs3 immutable byte types/6functions; tests/Curve448Spec.hs4KATs; Crypto/ECC.hs Curve_X448 base+DH instance only, remaining original ECCSpec X448 vector/weak-point/property partition. No fake Arith/Basepoint instance.
5. **Ed448 signatures are real remaining scope:** Crypto/PubKey/Ed448.hs, tests/Ed448Spec.hs public/secret/signature constructors+sizes, toPublic/sign/unsafeSign/verify/RNG; eddsa.c420lines, SHAKE256 callbacks/contexts/prehash/domain separation and scalar/point dependencies. Public signatures are57/114byte forms; they are not supplied by a representation codec. No modern Ed448 curve instance exists in pinned Crypto/ECC.hs.
6. **Beyond Decaf:** actual local:613 also includes remaining number generation/primality, signature/trust/chain validation and ledger crypto operations. Full Pandoc/calibre scope remains unchanged. This field checkpoint does not close613 or any later gate.

Original Curve448 and Ed448 KAT expectations remain pinned/unmodified; they are not claimed covered by field-only tests.

## Exact field matrix

| Source | Terms and distinctions |
|---|---|
| f_field.h |64byte/32byte-aligned field structure; copy; ZERO,ONE,MODULUS; GF_BITS448,SER_BYTES56,GF_LIT_LIMB_BITS56 |
| arch_32/f_impl.h |16 unsigned32 limbs, radix28, GF_HEADROOM2; add_RAW/sub_RAW no reduction; bias adds amount×P; weak_reduce carries/folds at limb8 |
| arch_ref64/f_impl.h |8 unsigned64 limbs, radix56, GF_HEADROOM9999; add_RAW/sub_RAW already weak-reduce, sub_RAW includes2P; bias is a no-op; weak_reduce carries/folds at limb4 |
| arch_32/f_impl.c |mul exact aa/bb partial convolutions and64bit accumulators; sqr calls mul; mulw_unsigned asserts b<2^28 |
| arch_ref64/f_impl.c |Exact default unrolled mul with aa/bb/bbb, dedicated reordered sqr and128bit accumulators; mulw_unsigned accepts full uint32 |
| f_generic.c |serialize,lobit,deserialize(hi_nmask),strong_reduce,sub,add,eq. Single conditional modulus subtraction after weak_reduce with source assertions; returned masks are all-word ones/zero. Failure deserialize still writes raw output. |
| f_arithmetic.c |isr exact27stages:446squares/13multiplies/1copy, followed by eq check. Zero/QNR failure preserves computed output. |
| field.h |sqrn; sub_nr/subx_nr; signed mulw; cond_sel/cond_neg/cond_swap; mul_qnr/div_qnr both negate sinceP_MOD_8=7. add_nr macroaliases add_RAW. |

Kotlin arithmetic preserves unsigned word truncation and actual source sequence, not generic modular reduction. The unreachable I_HATE_UNROLLED_LOOPS=1 branch is not selected by the pinned ref64 implementation (local variable is0); its selected unrolled formulas are translated. No SIMD or assembly equivalence claim.

## Oracle, aliases and boundaries

Original C files are copied byte-exact after verifying pinned archive identity and every source-map byte. Observer variant ONLY inserts27 ISR snapshots line-by-line; ordinary and observed returns/guarded raw buffers must match before capture. All scalar operations run against both portable profiles. Field buffers are actually32byte-aligned,64byte-sized with32byte prefix/suffix guards; byte outputs have guards.

The wrapper disables SIMD macros to select generic word conditional helpers and, for forced32 on64host, disables64host branch macros so big_register_t is32bits. This selects existing unchanged portable branches; actual sizeof(word/dword/big_register),alignment,headroom are asserted by native profile observations. It is not an actual32bit OS ABI proof or a claim about SIMD nonboolean masks.

Defined exact-object word aliases are exercised for nonrestrict functions (including ISR output==input, which writes destination only after reading x). C restrict forbids overlapping destinations for mul/sqr/mulw/sqrn/QNR and conditional swap; those aliases are excluded, not given fabricated portable expectations. Input a==b is allowed. Arbitrary partial overlaps, byte/field shared physical storage, invalid pointer/size/alignment and memory-access timing are outside exposed Kotlin Region/ByteSpan contracts.

sqrn requires positive count;32mulw_unsigned requires b<2^28. signed mulw INT_MIN is C unary-negation UB; subx_nr INT_MAX overflows amt+1; these cases are excluded and recorded as precondition gaps. bias itself accepts all signed Int amounts, including negative/extreme raw unsigned-wrap behavior. Negative signed carries in generic reduction/deserialization rely on implementation-defined arithmetic right shift; Clang behavior is reproduced, not universal ISO-C portability. Original assertions remain enabled.

Corpus includes canonical0/1/2/P−1, nominal unreducedP/P+1/max448,1×..4× limb bounds,full-word/high-bit tuples, random expanded limbs, all conditional mask patterns, headroom branch amounts9998/9999 and signed extremes, hi_nmask0/1/128/255, both squaring-count parities and all27 ISR states. Every row compares raw parity even outside mathematical ranges. Secondary Python integer assertions check ORIGINAL native results only for explicitly labeled canonical inputs; these assertions do not generate/replace expected values. Raw wider inputs do not promise mathematical field arithmetic beyond source range contracts.

## License and reproducibility

The pinned tarball headers say MIT and refer to LICENSE.txt, which is absent from the archive. Original copyright headers are retained verbatim; packaged licenses/decaf-MIT.txt transparently records this and includes the standard MIT text (https://opensource.org/license/mit), rather than claiming a byte-exact absent license file.

```sh
python3 tools/extract-decaf448-field-reference.py --check
python3 tools/import-decaf448-field-fixtures.py --check-source
python3 tools/import-decaf448-field-fixtures.py --verify-oracle
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --no-build-cache -Dorg.gradle.vfs.watch=false -Pkotlin.compiler.execution.strategy=in-process --project-cache-dir /tmp/kotdoc-decaf448-field-gradle :core:test --tests io.heapy.kotdoc.crypto.CryptoDecaf448FieldTest
```

Expected fixtures are from original C only. JVM constant-time behavior, erasure, actual native32 ABI and unrelated Haskell suites are not proven by this checkpoint.
