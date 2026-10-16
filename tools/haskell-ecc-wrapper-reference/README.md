# Internal ECC wrappers and permanent base-table reference

The complete pinned Crypto.Internal.ECC is retained as ObservedECC. Its formulas,
guards, bindings, lazy table comprehension and loop/recovery bodies are unchanged.
Only module/import names and the FFI names change mechanically. ObservedSerialize,
ObservedNumberCompat and ObservedCompat likewise preserve their complete pinned
bodies. This exposes internal functions for a development-only GHC 9.12.2 oracle
without replacing the package's public curve types or native arithmetic.

The observer supplies prefilled guarded alloca/foreign buffers and records their
contents and native entry-point calls. Its C shim calls the actual pinned native
engine, with optional explicitly labeled return-status injection to test the
wrapper boundary. Table-size is a pure FFI function and is not given an effect
counter. No production operation invokes this reference or C cryptography.

Build `ecc-wrapper-reference` with Cabal from this folder using the freeze file.
`--warm` prepares all named base tables before the ordinary request stream so
independent cases do not depend on the order in which previous cases warmed a
global cache. Cache scenarios and concurrent lookups run in separate fresh
processes, preserving the original permanent lazy CAF and failure memoization.
The corresponding Kotlin tests use a fresh instance of the same production cache
per scenario and a separate warmed cache for ordinary independent observations.

`--curves` emits all 33 named curve parameters and independent affine 5G points.
Mathematical requests additionally use the original ECCSpec doubleAndAdd, which
does not call native pointMul. The entire original ECCSpec body is still runnable
with `--original`; that is oracle validation, not a claim that public ECC/P-256,
ECDH/subgroup or their remaining original tests are already ported.

From the repository root:

```
python3 tools/extract-ecc-wrapper-reference.py
python3 tools/import-ecc-wrapper-fixtures.py --oracle /path/to/ecc-wrapper-reference
python3 tools/import-ecc-wrapper-fixtures.py --oracle /path/to/ecc-wrapper-reference --verify-oracle
python3 tools/import-ecc-wrapper-fixtures.py --check-source
```

The source serializes into uninitialized buffers and ignores a serialization
failure. Bounded negative/oversized inputs are therefore observed with a defined
poison byte and labeled controlled-memory observations. Kotlin arrays start zeroed
in production; equality of native garbage bytes or memory-disclosure behavior is
not claimed. Valid inputs fully write their source fields. The exact native scalar
region is cleared after every finite C status, and output coordinates are only
read for success. Table bytes/guard hashes capture a 64-bit little-endian ABI;
Kotlin's arithmetic storage remains LongArray-based.

The binary Integer fallback retains demand order and ignored values, including
constructor-only projective pairs. Kotlin evaluates its shared expression graph
iteratively to avoid replacing upstream finite wide scalars with JVM stack
overflow. This graph is variable-time, like the original Integer path. It is
not used in place of either already ported fixed-width native algorithm.

The corpus observes native/direct/table selection, the width of the scalar and
order, all named base/non-base points, exact-width truncation in the raw Integer
ladder, C rejection followed by Integer fallback, sparse-term rejection, failed
inverses, early guards/deferred errors, table identity and mutation, failed-table
memoization and concurrent first use. Real allocation pressure, invalid pointers,
huge/wrapping allocation sizes, other native ABIs, JVM timing and complete source
resource/lifetime parity remain unverified. Public point/scalar operations,
specialized P-256, signatures/trust and native TLS remain subsequent work.
