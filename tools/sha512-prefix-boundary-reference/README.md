# SHA512 prefix signed-boundary fix

The accepted bounded implementation used an Int loop bound `len + 144` and an
Int position. For a valid span length above Int.MAX_VALUE - 144, that bound
became negative and the loop processed no bytes. This is reachable incorrect
arithmetic, not an acceptable finite-test exclusion. The corrected production
PrefixControl retains a Long bound and position, narrows a position to an input
index only while it is below the real input length, and explicitly casts the
position to the source uint32 bit pattern for the original masked helpers.
SHA384 delegates to this corrected common prefix implementation.

## Arithmetic argument for every valid JVM span length

For 0 <= len <= Int.MAX_VALUE and any input n bit pattern, the original unsigned
clamp gives 0 <= n <= len. The updated low counter modulo 128 gives a pad index
in 0..127, hence the exact source pad length lies in 1..128. The loop bound is
len + 144 <= 2^31 + 143 < 2^32. Source `len + BLOCK_SIZE` fits uint32 in this
domain; adding sizeof(bits) is evaluated in size_t on this host and produces
the same bound. The source uint32 position therefore cannot wrap before exit.
A Long holds the whole interval exactly, including padding above Int.MAX_VALUE.

The length-byte start n + padlen and digest-selection position n + padlen + 16
also fit uint32 and are at most the loop bound. Converting their bit patterns
and the position to Int only at the original masked comparisons preserves the
source unsigned comparisons/equality. The input branch is tested using Long:
when it is taken, 0 <= pos < len <= Int.MAX_VALUE, so the narrowed index is safe.
For a valid span, offset + pos <= offset + len - 1 < array length, so its index
addition also fits Int. Context-counter uint64 wrap, carry and bit encoding
remain the existing source translation and are covered independently.

## Independent segmented control oracle

Pinned C files are copied byte-for-byte and checked against SOURCE.json.
ObservedPrefix.c is a separate mechanically checked transformation of the
original crypton_hash_prefix.c. It retains the exact clamp, counter update,
padding, uint32 masks, original loop predicate, position increment and selection
expressions. It starts a selected finite window with index
(initialIndex + start) modulo 128, caps that window, replaces input bytes by
(pos * 17 + 3) modulo 256, and omits compression while recording its boundary.

The jump is valid for control arithmetic because the original chunk function
changes chaining words only; it changes neither the byte counter nor the local
index/position. The index advances once per iteration and resets at 128. The
observer records byte masks, generated length/padding bytes, selection masks,
block counts and final counter/index/position. It does not compute or claim the
huge message's chaining state or digest. Source transformations and their limits
are recorded verbatim in Transformations.json.

The corpus has 14,349 source windows. It includes both sides of the old signed
bound failure, positions around the end of actual input, padding and selected
digest, maximum length and JVM-like maximum-minus-eight, unsigned n values,
low/high counter wrap and every initial index 0..127. Generic O0/O2/O3 ordinary
compilations of the transformed control observer agree. The existing 9,974-row
ordinary SHA512 family corpus and its expected values remain unchanged; only
source/provenance hashes are refreshed after documentation changes.

These checks and the argument establish the prefix control arithmetic for valid
JVM-sized spans. They do not claim an actual 2 GiB allocation, reading, hashing,
compression trace or output digest test. Invalid spans/pointers, uint32 buffer
lengths beyond the JVM representation, arbitrary overlapping memcpy regions,
other ABIs, timing and scrubbing retain the existing limits. The transformed
observer is never described as an unmodified full-hash oracle.

## Reproduce

- python3 tools/extract-sha512-prefix-boundary-reference.py --check
- python3 tools/import-sha512-prefix-boundary-fixtures.py --verify-oracle
- python3 tools/import-sha512-prefix-boundary-fixtures.py --verify-oracle --optimization=-O0
- python3 tools/import-sha512-prefix-boundary-fixtures.py --verify-oracle --optimization=-O3
- :core:test --tests io.heapy.kotdoc.crypto.CryptoSha512Test --tests io.heapy.kotdoc.crypto.CryptoSha512PrefixBoundaryTest :core:jar :cli:installDist

Original Vincent Hanquez / Olivier Chéron BSD-2-Clause notices remain in the
sources and licenses/crypton-sha512-BSD-2-Clause.txt. ChaCha/DRG/Probabilistic,
Prime, signatures/trust/chain and the rest of 613/Pandoc/calibre remain open.
