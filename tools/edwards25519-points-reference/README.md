# Pinned Edwards25519 Donna point engine

Dev-only original C/Haskell oracles; production is specialized Kotlin and reuses the accepted Edwards field and modm engines. Public Haskell byte-wrapper ownership/error semantics, modern ECC adapter, X448/Decaf448 and complete task613 remain separate gates.

## Exact source matrix

Both profiles use the identical portable `ed25519-donna-impl-base.h` and point portion of `ed25519-crypton-exts.h`. Profile32 uses10 alternating26/25-bit limbs and9 radix30 scalar limbs; profile64 uses5 radix51 field limbs and5 radix56 scalar limbs. All operations preserve source order and source field helper variants (`add_after_basic`/`sub_after_basic` differ by profile). Constants `ecd`, `ec2d`, `sqrtneg1`, basepoint,32 sliding Niels multiples and256×96 packed basepoint table reuse accepted immutable tables. No new numerical constants or alternative Edwards formulas are used.

| Original base function (18) | Contract and observations |
|---|---|
| p1p1_to_partial |3 multiplications, leaves destination T untouched; overlapping source/destination observes writes in source order |
| p1p1_to_full |4 ordered multiplications, including aliased conversion effects |
| full_to_pniels |Y−X,Y+X,Z,2dT, exact source sub/add variants |
| add_p1p1 |General extended addition, raw four-coordinate result |
| double_p1p1 |Square/add/sub formula; no exceptional-point normalization |
| nielsadd2_p1p1 |Niels3-coordinate input, sign0/1 swaps input factors and destination Z/T target |
| pnielsadd_p1p1 |Projective Niels4-coordinate input, sign0/1 |
| double_partial |P1P1 conversion leaves T unchanged |
| double |Full doubling |
| add |Full addition |
| nielsadd2 |In-place full addition with3-coordinate Niels |
| pnielsadd |Four-coordinate projective Niels output, exact intermediate write order |
| pack |Reciprocal Z, canonical Y bytes and XOR of X parity; zero Z is preserved as source behavior |
| unpack_negative_vartime |Negative-root convention, noncanonical Y accepted as original; returns0 with partially written X/Y/Z and untouched T on failure |
| double_scalarmult_vartime |Sliding windows5/7,8 dynamic/32 static precomputed entries; actual last iteration full conversion includes pinned issue31 fix |
| windowb_equal |Wrapped uint32 expression, including high-bit raw quirks; not ordinary integer equality |
| scalarmult_base_choose_niels |Scan8 packed entries, sign/absolute signed-byte expression, neutral fallback; all32positions × all256 signed-byte values plus custom tables |
| scalarmult_base_niels |Signed window4, odd pass, four doubles, even pass; original ECD multiplication only before even0 addition |

| Crypton point function (12 exports +2 private) | Contract |
|---|---|
| point_encode |Delegates pack |
| point_decode_vartime |Copies32bytes, flips parity before negative unpack |
| point_eq |Two cross-product equalities, ignores T, no on-curve checks |
| point_is_identity (static export-named) |X==0 and Y−Z==0, ignores T, no on-curve checks |
| point_negate |Ordered negate X/copyY/copyZ/negateT |
| point_add, point_double |Delegate full addition/doubling |
| point_mul_by_cofactor |Two partial doubles then full double |
| point_base_scalarmul |Accepted packed table |
| move_cond_pniels (private) |Word mask flag−1, including nonboolean flag behavior |
| point_scalarmul_w_choose_pniels (private) |Scan15, neutral if unmatched; wrapped comparison high-bit cases preserved |
| point_scalarmul |Scalar contract before destination identity initialization, then15 precomputed multiples;64 window additions and actual252 doubles (source comment says256); destination==input intentionally precomputes from overwritten identity |
| base_double_scalarmul_vartime |Reorders scalars into variable-point/basepoint helper |
| point_has_prime_order |Exact source order multiplication plus identity predicate |

## Independent fixtures and defined domains

`extract-edwards25519-points-reference.py` validates archive identity and exact source bytes, then copies original headers unchanged. Separate mechanically checked observer headers only insert callbacks at completed base/variable/double-scalar loop boundaries. Every native case runs ordinary and observed variants and asserts identical return and full guarded buffers before recording stage traces. Observer output is labeled separately; it is not the unchanged oracle. Profile switches force portable C arithmetic on one64-bit host, not two actual operating-system ABIs or assembly/SSE paths.

Raw tests include neutral/allzero tuples, all8 torsion multiples, ordinary subgroup points, full-word/high-bit tuples and arbitrary off-curve coordinate tuples at1×/2×/4× nominal limb bounds, all destination/input exact-object aliases, input equality, all sign branches, carry propagation through actual source formulas, arbitrary packed tables, malformed/noncanonical/sign-flipped encodings and partial failure writes. Every observation always compares raw results; mathematical validity is never used to skip parity assertions. Whole point arrays include guard words and untouched fourth coordinates for3-coordinate outputs; byte outputs include guards.

The field layer's documented unsigned raw arithmetic limits remain applicable. Native pointer parameters are aligned, allocated, valid and non-null; no claims for dangling/short pointers, out-of-bounds selector positions or signbit>1 (indices), strict-aliasing reinterpretations of unrelated objects, arbitrary partially overlapping structs or byte/word shared storage. Kotlin's Point uses field-word storage and byte spans separately, so physical C byte/point aliasing is not exposed as a portability promise. No timing, secure erasure, physical layout or assembly equivalence proof. Integer window4 recoding beyond the reduced scalar domain can select neutral entries; raw output parity is captured but not promised to equal mathematical scalar multiplication for arbitrary full-width limbs.

`OriginalEdwards25519Spec.hs` is the entire original31-example suite with only its module declaration changed. `OriginalSuite.txt` records execution of25properties×100trials and6literals, seed1729. `OriginalSamples.json` captures100 tuples using the *original* Arbitrary Point, PrimeOrder and Scalar instances with explicit QCGen seeds/sizes. Kotlin translates16point property families and5point literal examples against accepted raw engine APIs; scalar9+1 is already independently accepted. This does not finish public Haskell wrappers or modern ECC integration.

## Verification

```sh
python3 tools/extract-edwards25519-points-reference.py --check
python3 tools/import-edwards25519-points-fixtures.py --check-source
python3 tools/import-edwards25519-points-fixtures.py --verify-oracle --oracle /tmp/kotdoc-curve25519-public-build/build/aarch64-osx/ghc-9.12.2/kotdoc-edwards25519-points-reference-0.1.0.0/x/edwards25519-points-reference/opt/build/edwards25519-points-reference/edwards25519-points-reference
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --no-build-cache -Dorg.gradle.vfs.watch=false -Pkotlin.compiler.execution.strategy=in-process --project-cache-dir /tmp/kotdoc-edwards25519-points-gradle :core:test --tests io.heapy.kotdoc.crypto.CryptoEdwards25519PointsTest
```

Original Haskell oracle build (from this directory):

```sh
CABAL_DIR=/tmp/kotdoc-haskell/cabal-state /tmp/kotdoc-haskell/cabal build --offline --builddir=/tmp/kotdoc-curve25519-public-build --with-compiler=/tmp/kotdoc-haskell/ghc/bin/ghc --with-hc-pkg=/tmp/kotdoc-haskell/ghc/bin/ghc-pkg edwards25519-points-reference
```

Run the executable without arguments for immutable samples; `--original --seed1729 --qc-max-success100` (arguments separated as below) for suite:
`--original --seed 1729 --qc-max-success 100`.
Public-domain attribution Andrew M./Bernstein et al. and Olivier Chéron is retained in original headers and the packaged `licenses/ed25519-donna-public-domain.txt` and `licenses/ed25519-crypton-exts-public-domain.txt`.
