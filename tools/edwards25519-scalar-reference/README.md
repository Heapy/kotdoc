# Complete Edwards Donna modm scalar checkpoint

Production Kotlin ports all16helpers in BOTH pinned modm headers. The32 profile
uses nine unsigned32 words with30-bit radix and64-bit accumulators;64 uses five
unsigned64 words with56-bit radix and128-bit accumulators. Generic unsigned128
word operations are reused; no BigInteger/native cryptography or subprocess is
a production arithmetic dependency. Field/table/X25519 engines remain unchanged.
The existing public-domain notice retains Andrew M.'s original attribution.

| Original helper | Contract, range and alias policy |
|---|---|
| lt_modm | Wrapped subtraction MSB, not arbitrary unsigned comparison. |
| reduce256_modm | Exactly one conditional subtraction; valid modular reduction for the bounded source range, raw parity elsewhere. In-place. |
| barrett_reduce256_modm | Original high product approximation, q3/r2/difference and exactly two reducers; matched q1=x>>248/r1=x mod2^264 supports source inputs. Unrelated factors remain raw observations. All out/input whole-object aliases. |
| add256_modm | Word-width carry schedule, one reducer; canonical scalar inputs normally belowL. All out/a/b aliases. |
| mul256_modm | Fixed-width convolution and exact q1/r1 packing, then source Barrett. Canonical source scalars belowL; full raw words are not a generic modulo guarantee. All out/a/b aliases. |
| expand256_modm | Copies0..64bytes to local64-byte work, no reduction below32bytes, Barrett otherwise. Byte-input/output aliases captured. len>64 is source stack overwrite and excluded. |
| expand_raw256_modm | Exactly32little-endian bytes, no reduction. Byte-input/output aliases. |
| contract256_modm | Exactly32bytes and original overlapping-word packing; shared byte output/scalar aliases. |
| contract256_window4_modm | Original64signed digits and recoding order; shared output/scalar aliases retain source load/write order. |
| contract256_slidingwindow_modm | Original256bits/sliding loops; widths1..8 include actual source callers5/7 and keep signed digits representable. Shared output/scalar aliases. Invalid signed shift widths excluded; larger widths may invoke implementation-defined signed-char conversion and are not generalized. |
| sub256_modm_batch | Prefix fallthrough, lower masks, unmasked last word. Source assumesa>b for arithmetic meaning; raw underflows still replay. All aliases, every valid limbsize and explicit default branches. |
| lt256_modm_batch |32 uses high-to-low unsigned lexicographic comparison;64 uses low-to-high borrow. Raw out-of-radix differences are retained. |
| lte256_modm_batch | Same profile-specific strategies, including default0/1 branches. |
| iszero256_modm_batch | Scans actual9/5words, no Haskell allocation padding. |
| isone256_modm_batch | Exact limb pattern1,0,... |
| isatmost128bits256_modm_batch | Exact original masks; high bits outside normal radix can be ignored by source masks and are explicitly witnessed. |

Both modm_m and modm_mu arrays are copied from pinned source and checked against
native constants per word. Kotlin masks every unsigned32 assignment and retains
64/128-bit accumulator overflow. It preserves source truncation differences:
32 mul's final q1 limb keeps22bits and discards later carry;64 retains its final
carry. Barrett q3's final packing is separate and preserves its own tail carry.
Neither reducer is replaced with generic remainder or arbitrary-precision math.

The independent corpus executes every request against ordinary original C and a
separate mechanically observed copy. Final raw buffers must match in both before
stage traces are retained. Observed headers add callbacks only at q1/r1/q3/r2/
difference/reducer boundaries; extractor --check reconstructs all exact bytes.
Word buffers include prefix/suffix guards and untouched tails. Byte buffers have
aligned8offsets and320payload bytes, permitting source-defined byte/word aliases
for serialization/window output without writing beyond allocation. Kotlin's
Region can represent either owned word arrays or a shared byte view, preserving
those aliases rather than replacing them with snapshots.

Coverage includes all out/a/b alias combinations, every valid batch limbsize
and default branches, full unsigned words, all expand lengths0..64, raw32-byte
expansion, sliding widths1..8, preciseL/2L/256/512-bit boundaries, matched and
unrelated Barrett factors, MSB-borrow boundaries and128-bit ignored-high-mask
witnesses. Mathematical statements apply only within source bounds; every raw
outside-range record is replayed without normalization, skipping or repaired
expectations.

The dev Haskell oracle imports the actual pinned public Edwards module and the
whole original Edwards25519Spec body (only module declaration changes). It runs
the scalar arithmetic partition unchanged: nine property families100trials each
and one curve-order literal, seed1729.200original Arbitrary Scalar samples are
captured with explicit seeds/sizes and all three payloads; their actual original
instance result is asserted identical to scalarDecodeLong(arbitraryBS64) before
capturing canonical bytes. Kotlin translates all nine families over both
profiles, including both clauses of zero/one/distributivity properties. This
sample stream is distinct from Hspec's internal property stream. Native C and
Kotlin decode/encode results additionally match actual Haskell sample bytes.

Scalar storage is36bytes in32 and40bytes in64. Haskell allocates40bytes for both;
unused32padding is not value data. Native profile builds run on a64-bit host,
not a real32OS ABI. Byte views retain native aligned/effective-type assumptions;
no address-layout, JVM constant timing, physical erasure or GHC bottom/laziness
proof is claimed. Original programs are dev oracles only.

Build Haskell from this folder (the existing temporary builddir reuses already
built pinned dependencies):

```
CABAL_DIR=/tmp/kotdoc-haskell/cabal-state /tmp/kotdoc-haskell/cabal build --offline --builddir=/tmp/kotdoc-curve25519-public-build --with-compiler=/tmp/kotdoc-haskell/ghc/bin/ghc --with-hc-pkg=/tmp/kotdoc-haskell/ghc/bin/ghc-pkg edwards25519-scalar-reference
```

From root, with ORACLE naming the executable:

```
python3 tools/extract-edwards25519-scalar-reference.py --check
python3 tools/import-edwards25519-scalar-fixtures.py --oracle "$ORACLE"
python3 tools/import-edwards25519-scalar-fixtures.py --oracle "$ORACLE" --verify-oracle
python3 tools/import-edwards25519-scalar-fixtures.py --check-source
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --no-build-cache -Dorg.gradle.vfs.watch=false -Pkotlin.compiler.execution.strategy=in-process --project-cache-dir /tmp/kotdoc-edwards25519-scalar-gradle :core:test --tests io.heapy.kotdoc.crypto.CryptoEdwards25519ScalarTest
```

Source verification authenticates every actual crypton2.0.1/RAM0.22.1 file
against their immutable source-map/archive hashes, plus the exact RAM notice.
Direct Haskell dependencies (Imports/Utils/ByteArray/Cabal and RAM identity/
license) are separate JUnit pins. Final focused rerun follows provenance-only
resource metadata hardening; arithmetic inputs/expected buffers are unchanged.

C flags are-O2 -std=c99 with explicit force32/NO_INLINE_ASM; manifest pins clang,
source/archive hashes, ordinary/observed headers, generators and suite evidence.
Full point/selector engines, public/modern Edwards integration and the remaining
16properties+5literal examples of the full original31-example suite remain
pending. This scalar checkpoint does not complete Edwards, Decaf448, task613 or
the full Pandoc/calibre goal.
