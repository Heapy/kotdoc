# Complete raw Ed448 core checkpoint

All 16 definitions of pinned ed448goldilocks/eddsa.c are translated: clamp,
hash_init_with_dom, sign_internal, prehash_init, private-key conversion,
derive_public_key, sign, sign_prehash, derive_keypair, both key extractors,
keypair_destroy, both keypair signing variants, verify and verify_prehash.
The two header aliases prehash_update/destroy reuse accepted SHAKE256. Constants,
source lines, four profiles and remaining gates are in Ed448SourceMatrix.json.
Accepted Decaf field/scalar/points/tables and SHAKE APIs are unchanged.

Production uses specialized Kotlin engines only. Native C and Haskell are dev
oracles. Extractor checks the accepted exact Decaf source extracts and copies
eddsa.c, ed448.h, hash dependencies, public module and Ed448Spec verbatim.
OriginalEd448Spec changes only its module name; vectors/expectations are intact.
The archive lacks the referenced LICENSE.txt; the already accepted Decaf MIT
notice/provenance applies, with original source copyright headers retained.

## Independent observations

The ordinary C oracle includes unchanged scalar, point, table, SHA3 and Ed448
sources. A second oracle changes ONLY the already accepted negative signed shift
in point wNAF to signed multiplication. Every full guarded input/output/context
frame and return code is compared between these oracles, then replayed on
Kotlin. Ordinary results are retained; normalized C is separately identified,
not substituted as an unmodified oracle. Source negative-shift UB remains an
ISO-C proof limit, including callers reached by signature verification.

Profiles are field32/API32, field64/API64, field64/API32 generic masks and
field64/API32 native NEON masks. These are forced word/field compiler selections
on arm64, not actual execution of a 32-bit ABI. Actual pinned crypton linked
through Haskell separately checks the fourth profile and all 13 exported C
functions plus the two header aliases (private helpers are not exported).

All eight original Ed448Spec literal public keys/signatures/verify results are
replayed on all four Kotlin profiles. The unchanged original Haskell suite has
25 examples: eight vectors times three plus secret generation. Its RNG example
is a supporting public baseline, not proof of a Kotlin public wrapper. The full
public module (three immutable byte types, three sizes, eight functions, length
errors, ownership, RNG and safe/unsafe signing) is the next explicit gate.
There is no original Crypto.ECC Curve_Ed448 instance to invent.

Corpus families include empty and block-boundary messages through 4096 bytes,
context lengths 0/1/2/3/254/255, raw prehashed/for_prehash byte branches,
all 256 uniform clamp byte values, prehash streaming/copy/nonmutation, keypair
layout/extraction/destruction, output aliasing with fully consumed inputs,
every signature/public byte-position tamper for all eight vectors, small-order
and noncanonical point encodings, response scalar order boundaries, and
inconsistent raw public/keypair inputs. Positive prehash verification uses
independently generated original sign_prehash signatures and chunk variants.

A separate integer affine Edwards model derives the generator from pinned
literal Decaf coordinates using an independently evaluated isogeny and inverse
cofactor, then uses hashlib SHAKE256 and affine group arithmetic to check
public derivation, nonce/challenge signing and private conversion. It neither
calls Kotlin nor consumes Kotlin-derived expectations. Literal source vectors
anchor the generator/signature model.

Final corpus: 9884 guarded ordinary/normalized C observations. Actual Haskell
ABI replay: 2061 rows plus original25 examples. Independent math:114 cases.
A further824 original-C O0/O3 observations match captured O2 results; this is
finite optimizer evidence, not an ISO-C proof across the negative-shift UB.
Four original subprocess abort observations are retained separately.

## Exact quirks and domains

Raw sign does NOT rederive the public key: the check in the header prose is
commented out in this pinned implementation. sign_prehash DOES rederive and
abort on mismatch. Four subprocess observations retain the original SIGABRT;
Kotlin represents that unsupported fatal-process boundary with a checked
IllegalStateException, not a successful signature or an invented CryptoError.
Keypair signing consumes its stored public bytes, including inconsistent ones.
No extra key validation or small-order rejection is introduced.

Verification decodes public then R, computes challenge, negates it, decodes the
56-byte canonical response, checks signature byte113==0, then invokes the
original nonsecret double multiplication and source point equality. Decode
ratio is1, encode ratio4, cofactor4. Failure returns0; success returns-1. Invalid
inputs remain unmodified. Prehash finalization uses a copy of exactly344 bytes;
prehash initialization is plain SHAKE256 (no early context prefix). Source
context remains unchanged unless caller deliberately aliases output to
it. Destroy clears exactly114 keypair bytes or344 prehash bytes. Physical JVM
erasure and timing/constant-time behavior are not proven.

Raw fixed-size buffers must provide57/114/56/344 bytes as specified; context
length is a uint8 domain (0..255), raw flags0..255, message lengths fit the
Kotlin ByteArray Int domain. Null-pointer identity is irrelevant because
NO_CONTEXT=0; no contextless sentinel branch is compiled. Overlapping memcpy
keypair derivation/extraction inputs are not claimed defined C alias domains.
Signing/derive output aliases are tested where all input reads finish before
output writes. Pointer UB, huge size_t buffers, invalid contexts, 32-bit ABI,
big-endian execution and universal caller limb bounds are not proven. Existing
field range and point wNAF limitations remain explicit; corpus parity is finite.

## Reproduce

- python3 tools/extract-decaf-ed448-reference.py --check
- python3 tools/import-decaf-ed448-fixtures.py --verify-oracle
- python3 tools/decaf-ed448-reference/verify-haskell.py --oracle <built executable> --corpus core/src/test/resources/crypto-decaf-ed448/data.jsonl.gz --check
- python3 tools/decaf-ed448-reference/verify-math.py core/src/test/resources/crypto-decaf-ed448/data.jsonl.gz
- :core:test --tests io.heapy.kotdoc.crypto.CryptoDecafEd448Test :core:jar :cli:installDist

The Haskell executable is under /tmp/kotdoc-ed448-haskell-build/build/aarch64-osx/ghc-9.12.2/kotdoc-decaf-ed448-reference-0.1.0.0/x/decaf-ed448-reference/opt/build/decaf-ed448-reference/decaf-ed448-reference.
Ed448 public wrappers and the remaining number/primality/signature/trust/chain
and other local:613 scope remain OPEN; this is not the full Pandoc/calibre port.
