# RIPEMD160 prerequisite for complete PKCS15

This checkpoint ports the complete pinned portable crypton_ripemd.c core:
crypton_ripemd160_init/update/finalize and private ripemd160_do_chunk. The nine
K constants, five Boolean macros, rotate macro and all 160 ordered R invocations
are preserved. The extractor checks every instruction/constant and the unchanged
source/test copies. Kotlin uses wrapping Int arithmetic and explicit little-endian
loads/stores; alignment-copy and direct-block branches have the same observable
bytes on valid buffers. No native/JDK hash is a production dependency.

The source C struct is 96 bytes on the observed arm64 ABI: uint64 counter at0,
buffer at8, five words at72, four ABI padding bytes at92. Public allocation is128.
Init/reset zeros96 bytes, leaving32 unspecified public tail bytes. Raw contexts
start with initialized caller bytes; guards and untouched tails are compared.
Ordinary public snapshots compare96 bytes. Controlled borrowed-storage probes
first make a nonempty pure copy, explicitly initialize the32 tail bytes and then
compare within-object alias/copy behavior. No GHC global CAF allocation identity
is inferred from the MD2 observations, or claimed by this checkpoint.

## Public source matrix

Crypto.Hash.RIPEMD160 has one HashAlgorithm instance: block64, digest20,
context128, three FFI methods. It has NO HashAlgorithmPrefix instance; no Prefix
adapter or invented stub is added. Hash methods implemented here are hashInit,
hashUpdate/hashUpdates, hashFinalize, hash/hashlazy, hashInitWith/hashWith and
size accessors, plus digestFromByteString. Hash.IO supplies mutable init/initWith,
update/finalize/reset. Context and MutableContext expose borrowed debug storage;
Digest supplies byte access, Eq/Ord, hex Show/Read and NFData demand. Owned snapshot
helpers are separate from borrowed views. Generic access observations exercise
length errors, pointer callback errors/repetition, ignored Unit result roots,
empty-update skipping, deferred digest payload copy and shared synchronous errors.
Shared Digest Data reflection remains a separate gate.

The raw ordinary corpus contains860 observations: init/update/partial/full blocks,
repeated finalize, arbitrary initialized compression state, unsigned64 wrap and
all padding branches. Aligned context aliases exercise the direct-block branch;
unaligned guarded inputs exercise the trampoline branch. Full-block input aliases
use offsets0,8,64 with initial counter index0; these avoid memcpy overlap. Finalize
output aliases include offsets0,8,72,76,80,84,88,96,108. Source stores each word in
order, so overlaps with later state words preserve its sequential overwrite effects.
No restrict qualifier applies. Invalid pointers/lengths, retained pointers and
partial memcpy overlap are outside the asserted domain.

Thirty-three mechanically instrumented observations contain every round state
(all ten lanes, 160 steps per compressed block). The unchanged ordinary source
remains a separate oracle. Eighty virtual controls execute the exact uint64+uint32
counter expression; high counters are also finalized in ordinary raw cases,
exercising wrapped bit length. Virtual controls do not perform giant buffer reads.
The C source runs at O0/O2/O3 with WITH_ASSERT_ALIGNMENT on actual arm64/little
endian. The actual 32-bit/big-endian ABI is not executed. Kotlin byte loads model
the portable little-endian message words, without claiming cross-ABI heap identity.

The exact original HashSpec RIPEMD160 partition has four examples: three literal
vectors and one chunk property with100 trials. No long-vector/prefix/hybrid entries
exist for this algorithm. Current original Haskell observations comprise484
Hash/IO/Digest cases (including100 original generator rows), three literals,
eight demand cases, ten controlled borrow/copy cases and180 generic access cases.
All inputs explicitly select RIPEMD160; the mutable development dispatcher rejects
an omitted algorithm rather than silently selecting another hash.

## Reproduction

- `python3 tools/extract-ripemd160-reference.py --check`
- `cc -O2 -shared -fPIC -DWITH_ASSERT_ALIGNMENT -Itools/ripemd160-reference/original/cbits tools/ripemd160-reference/Ripemd160Reference.c -o /tmp/kotdoc-ripemd160-oracle.dylib`
- Same flags with Ripemd160Trace.c and output /tmp/kotdoc-ripemd160-trace.dylib.
- `sh tools/ripemd160-reference/BuildHaskell.sh ordinary|borrow|access`
- `python3 tools/ripemd160-reference/capture-native.py --verify`
- `python3 tools/ripemd160-reference/capture-haskell.py --oracle /tmp/kotdoc-ripemd160-haskell-oracle --verify`
- `python3 tools/ripemd160-reference/capture-borrow.py --oracle /tmp/kotdoc-ripemd160-borrow-oracle --verify`
- `python3 tools/ripemd160-reference/capture-access.py --oracle /tmp/kotdoc-ripemd160-access-oracle --verify`
- `python3 tools/import-ripemd160-fixtures.py --check`

Current independent rows total1658;100 translated original chunk-property
equations and one host ownership/error-sharing assertion bring the behavioral
JUnit count to1759. Source integrity pins are counted separately. Final focused
XML/JAR/source hashes and packaging are retained in the handoff evidence.

No general bottom/asynchronous exception, timing/constant-time, physical erasure,
universal allocator identity or giant-allocation parity is claimed. Original
BSD2 C/header and BSD3 Haskell notices are retained. All14 full PKCS15 adapters
and functions, PSS, RSA.Types, Digest Data, SystemDRG, other signatures/trust/chain,
remaining613 and full Pandoc/calibre remain open.
