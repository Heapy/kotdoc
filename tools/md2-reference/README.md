# MD2 prerequisite for the complete PKCS15 family

This checkpoint translates all four pinned portable C definitions: init, update,
finalize and private md2_do_chunk. All 256 S-box bytes and 17 logical padding
payloads are source-checked. The source checksum input alias in finalize is
preserved, including raw defined aliases with state/checksum/buffer. Public
Hash/Hash.IO/Digest exposes MD2 block16/digest16/context96, immutable context
updates/finalization, mutable init/update/finalize/reset, ignored algorithm
witnesses, digest conversion/Eq/Ord/hex Show/Read and synchronous deferred demand.
MD2 has NO HashAlgorithmPrefix instance; no Prefix API or fake stub is added.
Shared Digest Data reflection remains a separate gate.

The C struct occupies 56 bytes on the actual arm64/little-endian profile; the
Haskell allocation is 96. Native init/reset writes only the first56. Raw tests
start with known caller bytes and check the untouched40-byte public tail and
outer guards. Ordinary public dump comparisons only compare the first56 because
the original fresh tail may be uninitialized. Borrow tests explicitly write the
40 tail bytes before comparing them; they do not infer zero initialization.

Context, Digest and MutableContext offer ByteArrayAccess borrowed storage,
including default pointer-copy behavior. bytes()/debugBytes() are owned snapshots.
Explicit in-scope writes through borrowed views alias the same stored object;
nonempty pure updates copy storage while empty updates retain the context.
Generic input access tests distinguish zero-length pure-update skipping from
mutable-update callbacks, returned IO Unit roots, repeated callbacks and errors.
Digest conversion first checks length, with its Just payload copy deferred.

## Independent evidence

Current C corpus:800 generated raw observations,33 full round/checksum traces
from a mechanically instrumented development copy,80 virtual unsigned64+uint32
counter controls. Ordinary source is byte-identical and compiled at O0/O2/O3;
all three profiles replay the same expectations. Trace transformations only
rename symbols and insert post-round/checksum observers, verified in the extractor.
The 80 controls execute only the exact counter arithmetic, not giant input reads.

Current Haskell corpus:484 observations over Hash/IO/Digest plus3 original literal
vectors,8 synchronous demand probes,10 controlled borrow/copy cases and180
ByteArrayAccess observations. The exact original HashSpec MD2 partition has
4 examples:3 literal vectors and1 chunk property×100. There are no MD2 long-vector,
prefix or hybrid entries. All100 original generator rows execute the translated
property equation too. These counts are distinct from generated oracle cases.

History is intentionally retained. The first484-row Haskell capture omitted the
algorithm field, so the development mutable dispatcher selected its SHA256
default for192 rows. The current requests explicitly say MD2 and were recaptured
from the original executable; Kotlin output never supplies expectations.
history/first-mixed-dispatch.json.gz retains the original bytes/hash. Its native
replay still verifies those old inputs, but it is not counted as current MD2 proof.

A first borrow observer wrote through `hashInitWith MD2`'s debug pointer. GHC-O2
floated this pure constant into a shared CAF; its h[0] changed0..9 across10 requests.
The ten exact observations and original observer are retained under history/,
with an independent executable. This intentional mutation of immutable/debug
storage invalidates ordinary pure referential reasoning. The current observer
uses a guaranteed nonempty hashUpdate copy before the same in-scope writes,
checking defined copy versus alias behavior inside that object. It does not
silently reinterpret the CAF observations or claim universal fresh-init/heap
identity. Kotlin does not emulate GHC's global full-laziness allocation choices;
the unsafe CAF profile remains an explicit physical-identity boundary for review.

## Reproduction

- `sh tools/md2-reference/BuildHaskell.sh ordinary|borrow|access|caf` builds the
  original Haskell programs with GHC9.12.2-O2 and the pinned package overlay.
- `cc -O2 -shared -fPIC -Itools/md2-reference/original/cbits
  tools/md2-reference/Md2Reference.c -o /tmp/kotdoc-md2-oracle.dylib`
  and the same command with Md2Trace.c build the ordinary/trace development libs.
- `python3 tools/md2-reference/capture-native.py --verify` replays original C.
- `capture-haskell.py --oracle /tmp/kotdoc-md2-haskell-oracle --verify`,
  `capture-borrow.py --oracle /tmp/kotdoc-md2-borrow-oracle --verify` and
  `capture-access.py --oracle /tmp/kotdoc-md2-access-oracle --verify` replay current
  Haskell cases. `check-history.py` independently checks both historical profiles.
- `python3 tools/extract-md2-reference.py --check` checks source/table/trace/test
  bindings; `python3 tools/import-md2-fixtures.py --check` checks current hashes.

## Boundaries and remaining work

Byte buffers and pointer lifetimes must be valid, initialized and non-retained.
Partial memcpy overlap and identical source/output ranges are excluded; update
aliases only use full blocks with a zero initial buffer index. Finalize output
aliases avoid its source h region. Arbitrary out-of-bounds lengths, other heaps,
actual32-bit/big-endian ABI, physical erasure, asynchronous/general bottom or JVM
constant-time claims are not proved. Virtual counter controls do not allocate or
hash actual2GiB buffers. No JDK MD2/native/upstream runtime is a production fallback.
Original C/header notices and Haskell BSD notices are preserved.

RIPEMD160 is the next prerequisite, then all14 concrete PKCS15 ASN1 adapters and
all PKCS15 functions, then PSS. Prefix bytes over supplied MD2/RIPEMD160 digests
alone do not complete those hash adapters. Full RSA.Types/SystemDRG/signatures,
trust/chain and remaining613, full Pandoc623 and calibre624–626 remain open.

## Focused Kotlin gate

The isolated JVM gate passed 1 699 behavioral assertions with a 256 KiB stack:
1 598 current independent corpus rows, 100 translated original chunk-property
equations and one host ownership/failure-sharing check. Integrity assertions are
counted separately from those behavioral checks. The final installed JUnit count,
immutable JAR and source hashes are recorded in the handoff evidence.
