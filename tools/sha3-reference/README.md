# Portable SHA3, Keccak and SHAKE checkpoint

This ports all 11 portable definitions in crypton_sha3.c and all six inline
Decaf SHAKE256 wrappers. SourceMatrix.json records exact names/lines, three
24-word permutation constant groups and the ten HashSpec algorithms. The
ARM runtime probe and assembly backends are retained as pinned source, not
translated or claimed covered by the portable implementation.

The ordinary oracle compiles the unchanged C source. ObservedSha3.c adds only
an absorb callback and one callback after each round; extractor --check verifies
these mechanical transformations. Each raw row compares ordinary and observed
full context/output frames, including guard bytes. The initial 8046 rows contain
1,036,850 ordered events (41,474 permutations), with complete selected states
and hashes of every event. No Kotlin output creates an expectation.

The final 13256 rows comprise 8046 raw/state/stream/reset/destroy/alias rows,
30 verbatim HashSpec literals, 5160 actual Haskell SHAKE/HashSHAKE observations,
and 20 virtual header length-dispatch observations. The original selected
HashSpec suite independently passes 48 examples: 30 literals, ten chunk
properties and eight bit-truncation examples. The Haskell matrix covers both
security levels, pads 31/4, output bits 0..17 and byte/rate boundaries up to
4096, three chunk patterns, actual size metadata and Show. The final partial
byte retains its LOW bits exactly as the original does.

The development Haskell package needs two normally hidden modules. The helper
copies and verifies all 438 pinned files, changing ONLY crypton.cabal module
visibility for Crypto.Hash.SHAKE and Crypto.Hash.Types. Provenance records both
Cabal hashes and the full overlay. prepare-haskell-oracle.py --check checks the
existing copy without modifying it. No vendor source is edited. Actual Haskell
uses its optimized platform backend; this is independent of the portable C
oracle, not evidence that the Kotlin port implements ARM assembly.

HeaderChunksReference.c includes the unchanged inline wrapper but redirects
its SHA3 update/output dependencies to offset/count observers. A PROT_NONE mmap
region permits valid in-region pointer arithmetic up to 10 GiB without reading
or allocating resident data. This proves the >UINT32_MAX dispatch plan (2 GiB
chunks plus final call, including zero), not hashing of a real huge buffer.
Kotlin ByteArray spans are Int-sized; this dispatcher boundary is virtual.

## Defined domains and limits

Raw contexts have a valid initialized rate/index and sufficient allocated
storage. Nonstandard security parameters 128,129,136,144,160,224,248,255,256,
257,384,511,512 exercise the original floor divisions; they are raw source
observations, not claims of standard cryptographic algorithms. For non-multiple-of-eight rates, initially aligned multi-block input can make later uint64_t loads misaligned in C: those rows are finite compiler observations with an ISO-C alignment-UB boundary, not defined portable parity. The unaligned-input trampoline path and all standard rates avoid this boundary. Invalid rates,
undersized pointers, overlapping memcpy regions and destroyed-context reuse
without reinitialization are outside the defined source domain. Opaque hash
input/output aliasing is tested because absorption finishes before output.

Empty output at index==rate still permutes; empty update there absorbs the old
buffer. Whole-block input preserves stale buffer contents. Repeated finalize,
output before finalize and update after output preserve the original raw
state behavior on valid contexts. No extra phase validation is introduced.

Native full context dumps are little-endian arm64 ABI observations. The
portable algorithm serializes words explicitly little-endian, but no actual
32-bit or big-endian execution is claimed. JVM timing, physical secret erasure,
Haskell laziness/bottom and unbounded allocations are not proven. Contexts are
mutable internal buffers; this is not a complete generic Crypto.Hash public
API port. Ed448, other hash algorithms and the remaining local:613 scope are
separate gates.

## Reproduction

- python3 tools/extract-sha3-reference.py --check
- python3 tools/sha3-reference/prepare-haskell-oracle.py --check
- python3 tools/sha3-reference/capture-haskell.py --oracle <built sha3-reference> --check
- python3 tools/import-sha3-fixtures.py --verify-oracle
- :core:test --tests io.heapy.kotdoc.crypto.CryptoSha3Test :core:jar :cli:installDist

The development executable is under
/tmp/kotdoc-sha3-haskell-overlay-build/build/aarch64-osx/ghc-9.12.2/kotdoc-sha3-reference-0.1.0.0/x/sha3-reference/opt/build/sha3-reference/sha3-reference.
The combined BSD-2-Clause notice preserves both source copyright/license
headers. Original utils retains the already packaged Decaf MIT notice.
