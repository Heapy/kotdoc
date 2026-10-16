# Binary-field fixed-width ECC reference

The complete pinned crypton_f2m.c is mechanically retained. Only its external
name, read-only operation/cswap-address hooks and the audit workspace root differ.
The observer controls calloc failures and checks allocation sizes, release order
and contents at free. The public polynomial temporary is not wiped upstream;
the secret working buffer is wiped. No native arithmetic formula is replaced.

Build the development-only shared observer from the repository root:

```
clang -O2 -shared -fPIC -I vendor/crypton-2.0.1/cbits tools/haskell-ecc-binary-reference/BinaryReference.c vendor/crypton-2.0.1/cbits/crypton_cpu.c -o /path/to/binary-reference.so
```

Build `ecc-binary-reference` with Cabal/GHC 9.12.2 from this folder, using the
checked-in freeze file. The original complete ECCSpec body is preserved, with
only its module declaration changed and mechanically extracted binary names
and scalar expressions appended. `--curves` emits all eighteen binary curves
and independent affine non-base/order-two points; `--edges` emits the original
four curves' exact scalar lists. The default JSON stream computes the original
test's affine doubleAndAdd, independently of the C Montgomery ladder.

From the repository root:

```
python3 tools/extract-ecc-binary-reference.py
python3 tools/import-ecc-binary-fixtures.py --library /path/to/binary-reference.so --math-oracle /path/to/ecc-binary-reference
python3 tools/import-ecc-binary-fixtures.py --library /path/to/binary-reference.so --math-oracle /path/to/ecc-binary-reference --verify-oracle
python3 tools/import-ecc-binary-fixtures.py --check-source
```

The native observer exercises both the generic four-interleaved-bit carry-less
product and the source's runtime hardware dispatch on the host, plus spreading,
squaring, destructive reduction, field operations, Fermat inversion and masked
exchange. Production Kotlin translates the portable software backend; PMULL and
PCLMULQDQ machine instructions are not JVM intrinsics in this port. Their direct
execution on other hardware and performance/timing parity remain unverified.

The full native ladder is checked on all named binary fields, non-base points,
wide/order scalars, every byte scalar on four curves, aliases and unaligned
guarded buffers. Point x=0 is deliberately unsupported here: its public Haskell
fallback is separate pending work. Raw sparse/reducible/noncanonical polynomial
cases preserve C outputs without claiming an irreducible field contract. In
particular, no alternative reducer is substituted at exact 64-bit degrees or
for dense sparse-term boundary cases. Core loop/cswap schedules are observed at
fixed widths; infinity/negation recovery short-circuits match the source and
are not asserted to take identical total work. Real memory pressure, enormous
array sizes, other ABIs and JVM/JIT/GC/hardware timing remain outside this corpus.

The Haskell wrappers/fallback, public ECC APIs, specialized P-256, signature and
trust verification and their remaining original tests are still pending.
