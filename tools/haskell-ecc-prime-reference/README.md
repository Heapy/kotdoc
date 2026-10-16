# Prime-field fixed-width ECC reference

Build this development-only oracle with pinned crypton 2.0.1 and GHC 9.12.2.
Production uses Kotlin limb arrays; it does not invoke this oracle or a C
cryptography library. The BSD package notice is retained in the core JAR.

OriginalPrimeEcc.c preserves the complete pinned crypton_ecc.c implementation.
Only external symbol names and read-only operation/table-read counters change.
The exact transformation is checked by extract-ecc-prime-reference.py.
PrimeReference.c includes the original secure exponentiation source unchanged,
and redirects calloc/free to record sizes, failure boundaries, free order and
zeroing before release. It can fail one selected allocation without exhausting
process memory. 64-bit limbs are required explicitly by the native reference.

OriginalEccTests is the entire original PubKey/ECCSpec.hs with its module/export
declaration changed and source-extracted weight input helpers appended. The
original spec body runs with `--original --seed 1729 --qc-max-success 200`.
This does not mean that the whole public ECC API or its tests are ported.
`--curves` emits every prime curve's source parameters and a non-base point;
`--weights` emits the exact original P-384/P-521 scalar and pair expressions.
Finite requests can also compare the native result to the original test's
affine doubleAndAdd implementation, which does not use native pointMul.

Raw requests exercise all four C entry points: mul, table size, table build,
and table mul. Results retain status, guard bytes, aliased output behavior,
borrowed input contents, every table byte's hash, operation counts and ordered
table-read address hash. Tables use the captured 64-bit little-endian native
representation when hashed; Kotlin's internal LongArray remains portable.
The table is read at a nonzero aligned offset and byte buffers at unaligned
offsets. Allocator and scalar widths and zero/infinity short-circuits are
observed rather than replaced with new validation rules.

From this folder:

```
cabal build --builddir=/path/to/build ecc-prime-reference
cabal list-bin --builddir=/path/to/build ecc-prime-reference
```

From the repository root, with the resulting binary as ORACLE:

```
python3 tools/extract-ecc-prime-reference.py
python3 tools/import-ecc-prime-fixtures.py --oracle "$ORACLE"
python3 tools/import-ecc-prime-fixtures.py --oracle "$ORACLE" --verify-oracle
python3 tools/import-ecc-prime-fixtures.py --check-source
```

The raw C contract requires an odd prime and a valid finite point, with
parameters/coordinates below that prime. Explicitly labeled unchecked-buffer
observations additionally preserve deterministic C results for bounded
noncanonical inputs; they do not establish a mathematical curve contract for
invalid parameters. Real memory pressure, enormous UInt-wrapping allocation
sizes and JVM/JIT/GC/hardware timing remain outside this finite reference.
The scalar loop has uniform complete-formula/table-access counts at a fixed
width. Final conversion deliberately short-circuits infinity, as upstream;
its total allocation/conversion count is therefore not asserted constant.

The Haskell prime wrappers, permanent lazy named-base cache, binary-field
engine, specialized P-256, public ECC operations and their remaining original
tests are subsequent work. This reference does not substitute generic affine
arithmetic for any of those native algorithms.
