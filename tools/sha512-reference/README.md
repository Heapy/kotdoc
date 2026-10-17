# Complete portable SHA512 family checkpoint

This checkpoint translates all 23 portable function definitions: 11 exported
SHA384/SHA512/SHA512t functions, two chunk functions, three prefix adapters and
seven masked header helpers. The shared prefix source is instantiated for
SHA512; SHA384 uses its own 64-byte temporary and truncates the digest. All 80
round constants and source sigma/round expressions are retained. The public
algorithms are SHA384, SHA512, SHA512t_224 and SHA512t_256. Only SHA384 and SHA512
have the upstream HashAlgorithmPrefix instance; no fabricated SHA512t prefix
capability is exposed.

## Independent evidence

Original C/Haskell implementation and test files are extracted byte-for-byte
from the pinned crypton 2.0.1 archive. The ordinary C oracle includes the original
C unchanged. A separately named observer inserts only schedule/round callbacks;
the extractor checks each transformation. Observed callbacks are compared to
ordinary final state/output before being used as expectations. The original
HashSpec partition retains all 12 short KATs, six long KATs (including the million
'a' cases), four chunking properties, two prefix properties and two hybrid
properties. Its 26 examples execute with 200 trials per property. Another 1,600 samples use the actual original generators and are held to the
translated eight original property families in Kotlin. Additional
actual Haskell rows cover stream splitting, prefix conversion/clamping,
Digest Read/Show/length and logical context snapshots. No expectation is derived
from Kotlin. C is only a development oracle; production uses Kotlin word
arithmetic and does not call a JDK digest, process or native crypto library.

## Source behavior and limits

The low-level context layout is two little-endian host counter words, 128 buffer
bytes and eight state words (208 bytes on this host). Haskell reserves 256 bytes;
its final 48 bytes may be uninitialized and are expressly excluded from public
context comparisons, as documented by upstream Context. Raw guard tests retain
those tail bytes unchanged. Kotlin exposes detached debug bytes and immutable
public contexts/digests; empty updates return the original context, while
nonempty updates and finalization copy it. Digest Read consumes exactly the
required hexadecimal characters and retains the suffix without adding trimming.
The runtime algorithm enum represents the selected source dictionary; Haskell's
compile-time phantom-type separation and arbitrary lazy/infinite lists are not
claimed as Kotlin type-system or demand equivalence.

Raw SHA512t init includes the source derived-IV path for values other than
224/256, and the zero-initialization return for unsigned t >= 512. Its header
specifies multiples of eight; observations of other counts retain source floor
truncation and are labeled raw behavior. Finalize is bounded to 0..512 to avoid
the original local 64-byte temporary over-read for larger counts. Public t is
only 224 or 256. Prefix n is converted to uint32 and clamped as in source,
including negative public Int observations. Prefix output may alias the input:
source zeroing/truncation order is retained rather than replaced with hashing a
copied prefix. Input and context/output overlapping memcpy regions, invalid
pointers and insufficient spans are outside the defined input domain. Prefix loop positions and input predicates use Long across the entire valid
JVM span-length domain. The separate sha512-prefix-boundary-reference observer
and arithmetic argument cover the signed Int boundary; no actual huge
allocation or 2GB/4GB input hashing is claimed. Counter carry and bit-length wrap are
checked using injected valid-sized contexts, independently of allocating that
many bytes. Mask helpers are tested across signed/unsigned high-bit boundaries.

The native profile is portable generic C on this actual little-endian AArch64
host; alignment offsets exercise both original copy/direct paths. ARMv8 and
x86 assembly are conditional backend implementations, not ported instruction
sets. Actual Haskell package backend/compiler selection is recorded separately.
Full-corpus generic -O0/-O2/-O3 and actual ARM dispatcher -O2 replay agree;
the actual runtime reports SHA512 extension availability=1. Generic schedule
traces remain a separately observed profile. These finite observations do not establish all ABIs, big-endian state layout,
strict-alias portability, physical scrubbing or constant-time JVM behavior.
The source's stack byte-array to uint64 casts in derived-IV processing retain
their native compiler provenance; no universal ISO-C alias proof is asserted.

## Reproduce

- python3 tools/extract-sha512-reference.py --check
- python3 tools/sha512-reference/capture-haskell.py --oracle <executable> --verify
- python3 tools/import-sha512-fixtures.py --verify-oracle
- :core:test --tests io.heapy.kotdoc.crypto.CryptoSha512Test :core:jar :cli:installDist

All notices are retained in licenses/crypton-sha512-BSD-2-Clause.txt and the
existing crypton BSD-3-Clause notice. ChaChaDRG/Probabilistic/Prime, signatures,
trust/chain validation and the rest of local:613 remain open. Full Pandoc 623
and calibre 624/625/626 gates remain required.
