# Pinned Decaf448 point geometry, codecs, Niels and all tables

This completed first point checkpoint implements 28 definitions from pinned
`ed448goldilocks/decaf.c`, plus header point_copy: 29 functions. SourceMatrix.json
lists all **45** compiled source definitions, their source lines and checkpoint
status, plus conditional MSVC ctz and header copy. The remaining 17 definitions
are NOT implemented by this checkpoint: scalar/double/dual loops, batch inversion
and normalization, comb precompute/multiply/lookup, direct_scalarmul, X448 ladder
and key derivation, wNAF recoding/table/precompute/base-double multiply, and
precomputed destruction. No placeholder API claims their implementation.

X448/public/modern, Ed448/SHAKE signing/verification, remaining local:613 crypto,
Pandoc and later calibre remain open. Actual Haskell original suites here are
supporting native evidence, not a claim those public/signature suites are Kotlin.

## Exact source/profile matrix

The fixed guards are COFACTOR4, IMAGINE_TWIST0, EDDSA_USE_SIGMA_ISOGENY0,
EDWARDS_D=-39081, TWISTED_D=-39082, EFF_D39082. Comb configuration is5x5x18,
fixed window5, wNAF fixed5/variable3. Generic branch alternatives for other curves
are retained in the immutable source extracts but are not fabricated Kotlin APIs.

The implemented family is crypton_gf_invert, deisogenize, point_encode/decode,
point_sub/add/double_internal/double/negate, cond_neg_niels, pt_to_pniels,
pniels_to_pt, niels_to_pt, add/sub_niels_to/from_pt, add/sub_pniels_to/from_pt,
prepare_fixed_window, point_eq/valid/debugging_torque/debugging_pscale/cond_sel,
point_mul_by_ratio_and_encode_like_eddsa, point_decode_like_eddsa_and_mul_by_ratio,
ed448_convert_public_key_to_x448, point_mul_by_ratio_and_encode_like_x448,
point_destroy and header point_copy. Names and exact lines are in SourceMatrix.

The source tables contain340 field elements:4 base coordinates,240 comb entries,
and96 wNAF entries. Every element is eight56-bit literals:2720 original words,
or5440 split28-bit limbs. Kotlin retains all literal values verbatim. Every
compiled native field element is compared word-for-word, as64 raw memory bytes
and56 canonical wire bytes, including guards; this is more than a checksum.
RISTRETTO_FACTOR, both scalar adjustments, x448 base bytes and point identity
are independently dumped. Native C FIELD_LITERAL applies the profile split,
independently of Kotlin's split. The captured host is little endian; physical
memory byte observations do not claim JVM heap layout or big endian validation.

| Profile | Field limbs | Scalar/API words | Conditional-mask implementation |
|---|---|---|---|
| forced generic32 |16x28 stored uint32 |14x32 |32-bit generic register |
| forced generic64 |8x56 stored uint64 |7x64 |64-bit generic register |
| mixed generic64/API32 |8x56 |14x32 |64-bit generic register |
| actual AArch64 NEON/API32 |8x56 |14x32 |four repeated low32-bit lanes |

The actual pinned Haskell linkage selects ARCH64 via Cabal and API32 via
common.h UINT_FAST32_MAX. Its ARM NEON br_set_to_mask repeats the low32 bits.
For documented mask_t values0/allones this agrees with generic helpers. Raw
noncanonical masks differ, and are deliberately preserved as separate native
expectations. Point-local mask handling reproduces this fourth profile without
changing the accepted field engine. API bool parameters are different: any
nonzero API-word value normalizes to a full mask. The same0/1/2 bool cases run
across every profile. Forced32 selection is not a real32-bit OS ABI experiment;
no independent AVX/SSE compiler/runtime proof is claimed.

## Representation, alias, range and failure domains

Point coordinates are extended homogeneous(x,y,z,t); projective Niels stores
(a,b,c,z), and ordinary Niels uses only its first three fields. Guarded storage
checks untouched fourth fields, input regions and output guard words. Public
point add/sub/copy/negate/double/select/debugging operations replay exact out=a,
out=b,a=b,and all-equal aliases. Private Niels input/output regions remain
disjoint, while its intended point update and Niels negate are in place.
Deisogenize's three restrict outputs are disjoint from each other and input.
No arbitrary partial pointer overlap or cross-type byte/word alias proof is
claimed. Typed Kotlin byte spans and limb arrays cannot share physical storage.
Public immutable byte ownership is a subsequent wrapper checkpoint.

Inputs include identity, base, torque, doubles, negation, rescaling, two-torsion,
invalid zero-z and arbitrary bounded field coordinates, p boundary patterns,
and deterministic randoms. Lazy representatives redistribute carries and add p
to each coordinate of the valid base, exercising carry/headroom branches while
preserving its mathematical point. Point_valid does not establish tight limb bounds: original point_add can disagree with group arithmetic on the same valid base represented by adding p to each coordinate. RangeObservations.json retains every such independent mathematical comparison, while all raw C expectations remain unchanged. The mathematical group proof uses the explicit sufficient tested domain of limb values at most 2^radix; it is not a proof of a universal upstream precondition. Tight base literals, deserialize masking, normalized precomputed tables and arithmetic carry comments are caller evidence for ordinary representations; complete caller-range reachability is not proven here. The explicit base+p witness first differs at field stage5, the multiply at decaf.c:346: its inputs came from raw sums at343/345 (annotated2+e for ordinary representations). arch_32/f_impl.c:20 uses uint64 accumulators and26/27 forms paired limb sums. The 32-bit original disagrees with the independent modular result at this stage; all64-bit controls and the tight-base32 control agree. RangeWitnesses.json records the exact first stage for each profile. Source base literals (decaf_tables.c:7), deserialize masking (f_generic.c:67), and precomputed normalization (decaf.c:903/907/911) provide concrete ordinary-caller evidence; no universal reachability exclusion is asserted. Arbitrary full-machine-word point coordinates
are outside the field multiplication range contract; the accepted field-only
raw-width observations do not extend that contract to point formulas.

before_double!=0 deliberately leaves t stale. Tests preserve it exactly and do
not silently demand a full valid extended point. Equality compares x/y modulo
torsion, without adding a validity check. Pscale ignores deserialize failure and
replaces a factor equal to zero modulo p with one. Decode always records full
failure outputs, not just the success mask: Decaf enforces canonical/even s and
allow_identity; Ed448 enforces57th-byte/sign/canonical/isr conditions exactly,
including small-order outcomes. No extra subgroup or safety check is added.
All eight original Ed448 public-key literals are decoded as independent inputs.

Field inversion assert_nonzero requires nonzero input. Ed448 encoding requires
its affine denominator nonzero. Isolated original-C subprocesses record assertion
aborts for zero inversion and all-zero Ed448 point in each profile; Kotlin checks
these internal source preconditions. These are not portable C return values or
an exception-type equivalence claim. prepare_fixed_window receives sufficient
output capacity; finite count0 observations still record its element0 write.
Negative counts, invalid pointers, insufficient capacity, misalignment and
arbitrary lengths are not portable supported domains.

## Independent evidence

original/ is unchanged pinned C/header data, validated against SOURCE.json and
archive identity. ObservedPoints.c is a separately generated copy with snapshots
after field writes. Conditional field calls retain the same condition, and both
conditional-swap outputs are recorded. Every observed result must equal the
unchanged original result before it can enter the corpus. Kotlin's optional
point-local field audit reproduces the same intermediate limb sequence. Accepted
field and scalar production files remain unchanged. No expected result is taken
from Kotlin and no original program is a production dependency.

verify-mathematics.py separately checks projective validity, torsion equality,
affine add/sub/double/negation/torque/rescale, inversion and decoding success,
canonical even Decaf encodings, and bool selection using Python integers. It
never creates fixture expectations. verify-haskell.py links actual pinned crypton
C through Haskell FFI and replays every nonalias public geometry row for the
actual NEON profile, including raw mask boundary observations. The unchanged
original Ed448/Curve448 suites run separately (29 examples; only module
declarations renamed). Full signature/public behavior is still pending Kotlin.

The accepted scalar inverse's known negative-shift UB is not invoked by these
geometry operations. Pending recode_wnaf has signed negative delta<<pos UB and
must be investigated in its later checkpoint. Pending nonsecret base-double
multiply also retains its source zero-variable-scalar early return quirk; neither
is silently normalized in this geometry slice. Functional zeroing, alias and
byte output parity do not prove JVM/JIT constant time or secure memory erasure.

## Reproduction

From repo root:

```
python3 tools/extract-decaf448-points-reference.py --check
python3 tools/import-decaf448-points-fixtures.py --verify-oracle
python3 tools/import-decaf448-points-fixtures.py --check-source
python3 tools/decaf448-points-reference/verify-mathematics.py
python3 tools/decaf448-points-reference/verify-haskell.py --oracle /path/to/decaf448-points-reference --check
./gradlew :core:test --tests '*CryptoDecaf448PointsTest' :core:jar :cli:installDist --no-build-cache -Dorg.gradle.vfs.watch=false
```

Build the Haskell executable from this directory with its cabal.project/freeze,
pinned GHC/Cabal and `cabal build decaf448-points-reference`. Sources, observer,
reference programs, original-suite baseline, actual Haskell ABI observations,
source matrix, compiler profile and licenses are pinned by the corpus.

Copyright notices are preserved in all original extracts and translated Kotlin.
The existing licenses/decaf-MIT.txt transparently distinguishes original
copyright headers from the standard MIT body because the pinned archive lacks
the referenced LICENSE.txt. The dev-only crypton_sha3.h dependency retains its
own BSD-style header verbatim; SHAKE production is not translated here.
