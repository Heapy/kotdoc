# Pinned Decaf448 scalar engine development reference

This is a finite scalar checkpoint of local:613, not the point, X448, Ed448,
SHAKE, complete crypton, or Pandoc/calibre port. Production is Kotlin only.
The accepted field engine is unchanged. Original C and Haskell are dev oracles.

## Complete source matrix

Pinned crypton 2.0.1 `cbits/decaf/ed448goldilocks/scalar.c` defines sixteen
functions. The header's scalar_copy is seventeenth; its called bzero utility is
included as eighteenth. There are no scalar precomputed point tables.

| Original function | Original scalar.c line | Kotlin operation | Domain/evidence |
|---|---:|---|---|
| sc_subx | 40 | subx | explicit modulus, extra 0/1, raw borrow mask, diff/sum trace |
| sc_montmul | 64 | montmul | exact N+1 accumulators, high carry, each outer iteration |
| scalar_mul | 102 | multiply | both Montgomery passes, exact aliases |
| sc_montsqr | 112 | montsqr | exact Montgomery square, not ordinary square |
| scalar_invert | 116 | invert | eight-entry table, all 446 defined-prefix rounds, three zero tail rounds, final conversion |
| scalar_sub | 176 | sub | one conditional correction, raw unreduced observations |
| scalar_add | 184 | add | full carry plus one subtraction, raw unreduced observations |
| scalar_set_unsigned | 200 | setUnsigned | complete uint64 input, including high bit |
| scalar_eq | 215 | equal | API-word all-ones/zero, no reduction |
| scalar_decode_short | 227 | decodeShort | truncate at 56 bytes; zero-fill shorter inputs |
| scalar_decode | 242 | decode | 56 bytes; failure still reduces output; enum success -1/failure 0 |
| scalar_destroy | 259 | destroy | zero complete scalar storage |
| scalar_decode_long | 265 | decodeLong | zero length, 56 special case, partial top chunk, every lower 56-byte chunk trace |
| scalar_encode | 303 | encode | all 56 little endian bytes, raw no reduction |
| scalar_cond_sel | 315 | condSelect | any nonzero API-word bool chooses b |
| scalar_halve | 324 | halve | conditional p addition, high carry before right shift |
| scalar_copy (point_448.h:241) | header | copy | exact struct copy / equal-pointer alias |
| crypton_decaf_bzero (utils.c) | utility | bzero | requested byte region zero, guards preserved |

Constants independently dumped are MONTGOMERY_FACTOR, sc_p, sc_r2, scalar_one,
and scalar_zero. Scalar size is 56 bytes, order bits 446; profiles are 14x32 and
7x64 words. Forced generic profile selection is not a real 32-bit OS ABI test.
The actual pinned crypton Haskell library on this AArch64 macOS build selects
32-bit API/scalar words because common.h uses UINT_FAST32_MAX, while Cabal selects ARCH_WORD_BITS64. All 32-bit-word corpus rows additionally replay this mixed profile; profile layout values are retained separately. FFI bool/eq are
Word32, set_unsigned remains Word64. This extra linkage check is not an
independent compiler implementation of the original arithmetic.

## Independent evidence and boundaries

`original/` is byte-identical to the pinned vendor files, verified by archive
identity and all vendor SOURCE.json hashes. `extract-... --check` also verifies
both mechanical observer transformations. Native ordinary expectations retain
all output words, input words, guard words, bytes, masks and return values.
No expectations are synthesized from Kotlin. Exact aliases include out=a,
out=b, a=b, all equal, subx out=p / p=a / p=b, plus byte input/output overlap.
These are finite observations, not a claim that every arbitrary partial
scalar-pointer overlap is defined. Pointers are aligned and nonnull, buffers
are sufficiently large, lengths are nonnegative. Invalid pointers/negative
lengths/out-of-range address arithmetic are not portable domains.

Canonical arithmetic has operands in [0,p). Add/sub have one correction, not a
generic mod implementation. Separate raw full-448-bit observations (0,p,p+1,
2p-1,2p,2^447,max448, alternating high words, deterministic randoms) preserve
source behavior without extending its mathematical canonical contract.
`verify-mathematics.py` uses Python integer arithmetic as an independent
specification for canonical add/sub/multiply/halve/inverse, decoding, and
uint64 construction. It never writes native expectations. Zero inverse yields
failure and zero. Raw noncanonical inverse observations are not a canonical
arithmetic guarantee.

Original scalar_invert shifts by `i % WBITS` for i=-1,-2,-3. Negative C shift
counts are undefined even when w is zero. The unmodified original is retained
and observed separately under available Clang -O0/-O2/-O3. Agreement among
these finite compiler observations is not ISO-C parity. NormalizedObservedScalar.c
makes exactly those three bit inputs zero and adds stage snapshots.
BoundedObservedScalar.c keeps the original expression but stops before the
first negative-count iteration; its snapshots prove the defined prefix. Kotlin
implements the source-intended zero tail and is independently checked against
modular inversion on canonical scalars. Signed negative right-shift behavior
is the observed arithmetic shift convention; no general C portability claim.

C volatile/memset_s destruction and Haskell memory lifetime/forcing timing are
not proven equivalent to JVM/JIT secure erasure or constant-time execution.
No such security proof is claimed by these functional fixtures.

The Haskell reference FFI links the actual unchanged pinned crypton library,
replays all nonalias public scalar rows in the host profile, and runs unchanged
original Ed448 and Curve448 tests (only module declarations renamed). Its 29
passing original examples are dev baseline evidence; this scalar checkpoint
does not claim their point/signature/public behavior is implemented in Kotlin.

## Reproduction

From repo root:

```
python3 tools/extract-decaf448-scalar-reference.py --check
python3 tools/import-decaf448-scalar-fixtures.py --verify-oracle
python3 tools/import-decaf448-scalar-fixtures.py --check-source
python3 tools/decaf448-scalar-reference/verify-mathematics.py
python3 tools/decaf448-scalar-reference/verify-haskell.py --oracle /path/to/decaf448-scalar-reference --check
./gradlew :core:test --tests '*CryptoDecaf448ScalarTest' --no-build-cache -Dorg.gradle.vfs.watch=false
```

Build Haskell from this folder using its cabal.project/freeze and the same
pinned GHC/Cabal setup as adjacent accepted crypto reference tools. Run its
`--suite --qc-max-success=100` separately. OriginalPublicSuites.txt records the
baseline with elapsed timing removed. Sources, transformations, tools, notices,
Haskell ABI observations and original-suite baseline are pinned in the corpus.
The archive lacks the referenced LICENSE.txt; `licenses/decaf-MIT.txt` retains
original copyright and transparently identifies the standard MIT body source.
