# Complete portable ChaCha core checkpoint

All 16 portable definitions of crypton_chacha.c are translated: 13 C entry points
and three private helpers, including ChaCha/HChaCha key/state setup, core rounds,
XChaCha, generate/combine, both counter widths/setters, simple-block generation
and the distinct random generator. Both 16-byte constant strings and the QR
expressions are retained. The get_state macro is represented by the context's
state view. SourceMatrix lists the conditional assembly-state helper and backend
families separately; this checkpoint does not claim translated NEON/SSE/assembly
instruction implementations.

The random function is not ordinary ChaCha streaming. It returns bytes 40..55
of a block and reinitializes the key/nonce from bytes 0..39 after every at-most
16 requested bytes. Partial requests discard unused output. A zero request does
nothing. Counter changes do not clear pending stream bytes. For 12-byte nonces,
the source counter's carry into word 13 and 64-bit seeking also affect the first
nonce word; no new rejection or security policy is added.

## Independent evidence

Original files and tests are copied verbatim from the pinned crypton archive.
The ordinary oracle includes the C unchanged. A separately named observer only
inserts input/double-round/output callbacks; the extractor verifies its exact
transformations, and final state/bytes are checked against the ordinary oracle.
Raw observations retain complete context, input/key/nonce/output buffers and
guards. They cover leftovers and zeroing, word/counter wrap, raw odd/zero rounds,
unaligned output, forward overlapping combine, input/state and output/state
aliases, initialization aliases and random rekey/split-call behavior. Every source-reachable pending-byte phase
1..63 is consumed at its exact boundaries; valid manually supplied full-cache
endpoints are observed separately.

The unchanged original ChaChaSpec (module rename only) runs all 79 examples,
including 10 short/XChaCha/RFC KATs, 63 long-keystream literal SHA256 checks,
three counter-carry examples and three properties at 200 trials each. A separate
mechanical vectors view exposes the original local literal bindings, without
changing any expected bytes. Another 600 samples use the actual Vector and
Utils Arbitrary instances. These 676 original observations are cross-checked
with ordinary portable C and replayed by Kotlin raw-core adapters. The three
property expressions are translated too. SHA256 used by the test harness only
checks the retained original long literals; it is not a production dependency.

## Domains and ABI boundaries

The source uses uint32 words and uint64 counter values. Raw private-core signed
round counts preserve the source positive-loop rule (odd rounds perform the
last double round). Public C initialization/simple-block round arguments are
uint8; random's uint32-to-int high-bit conversion is recorded for this compiler,
not claimed universally across ISO-C implementations. The later Haskell wrapper
validates 8/12/20; it is not ported in this checkpoint. Raw invalid key/nonce length
observations retain existing source behavior (all non-32 keys use tau; unsupported
nonce sizes leave the final state words), but do not add public API guarantees.

Valid state/context pointers and sufficient readable/writable spans are required.
Direct core/hcore outputs require their C alignment; byte-output wrappers cover
aligned and unaligned paths. The logical Kotlin byte-buffer base represents an
8-byte-aligned native allocation, with offsets selecting alignment. The aligned
generate path writes its block before advancing the counter; the unaligned path
uses the source temporary/write order. This distinction matters for state aliases
and is preserved. Partial-overlap behavior is the portable forward byte-loop
profile, not an unproven guarantee for optimized backends. The finite stream corpus covers source-produced cache states (whose offset plus
length is 64 while pending) and selected valid manually supplied endpoints.
Arbitrarily forged metadata is not classified uniformly: some cases remain
defined, while out-of-object cache accesses violate pointer bounds. Invalid spans,
actual giant allocations and arbitrary foreign pointers are not claimed.

On this actual little-endian AArch64 ABI, sizeof(C context) is 136, while the
upstream Haskell initialize/initializeX allocate 132 logical bytes. Controlled
logical-132 buffers retain four trailing sentinels changed by the original
136-byte initialization. This proves that source store under controlled physical
allocation, not GHC heap/allocator corruption or memory parity. The raw Kotlin
object deliberately models the full 136-byte C object. The later public wrapper
must retain this distinction. Actual 32-bit ABI, big-endian layout, universal
strict-alias portability, physical scrubbing and constant-time JVM execution are
not proven. Logical hchacha stack clears are retained without a physical-erasure
claim. Production is Kotlin word arithmetic and calls no native/process/JDK cipher.

## Reproduce and remaining work

- python3 tools/extract-chacha-core-reference.py --check
- python3 tools/import-chacha-core-fixtures.py --verify-oracle
- python3 tools/import-chacha-core-fixtures.py --verify-oracle --optimization=-O0
- python3 tools/import-chacha-core-fixtures.py --verify-oracle --optimization=-O3
- python3 tools/chacha-core-reference/capture-haskell.py --oracle <executable> --verify
- :core:test --tests io.heapy.kotdoc.crypto.CryptoChaChaCoreTest :core:jar :cli:installDist

The new BSD-3-Clause notice preserves the source/header copyright and terms.
Public ChaCha has seven module functions, two state types, four class methods
with two instances and four private counter wrappers. ChaChaDRG, process-secret
Random.Probabilistic, optimized backend profiles, Prime's nine exports/420-prime
table, remaining signatures/trust/chain and the rest of 613 stay open. Full
Pandoc 623 and calibre 624/625/626 gates remain required.
