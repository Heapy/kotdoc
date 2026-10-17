# Complete Crypto.MAC.HMAC source checkpoint

Production Kotlin translates crypton 2.0.1's six HMAC functions (`hmac`,
`hmacLazy`, `initialize`, `update`, `updates`, `finalize`), strict two-field
Context, HMAC constructor/digest selector/ByteArrayAccess and Eq, including
initialize's private doHashAlg key branches and mutable reset. No native program,
JDK MAC, external hashing service or random generator is a production dependency.

`CryptoHmac.HashAlgorithm` models the raw hash dictionary with declared block,
digest and context allocation sizes and initialize/update/finalize callbacks.
Hash copies are explicit; reset invokes initialize on the same mutable allocation.
The immutable initial hash context is shared between outer and inner setup.
Zero block-size dictionaries retain source empty-update behavior. Long-key
prehash must finalize and reset before building key pads, including failure order.
Sixteen named adapters reuse accepted MD5/SHA32/SHA512/SHA3/Keccak engines without
changing them. SHAKE128/256 output-bit adapters reuse the accepted SHAKE engine.
This does not supply the absent MD2/MD4/RIPEMD160/Tiger/Whirlpool/Blake2/Skein
families or claim all original HashAlgorithm instances are ported.

Mac wraps an owned Digest, retains the constructor's Digest identity, and
returns it through hmacGetDigest. bytes() returns defensive copies; explicit withBytes borrows the underlying storage,
as the original pointer callback does. In-scope writes through Mac and its getter
are visible to each other; 30 original observations also check default-copy guards. Digest
Eq/Ord/Show/Read and size-checked byte conversion model the shared source digest
surface; shared Data reflection remains separate. HMAC itself has no source Show
instance. Kotlin's unavoidable host display is an opaque `<HMAC>` marker, never
the digest or a content-derived default object hash string.

Eq follows RAM constEq/memConstEqual: compare lengths first, then accumulate all
Word8 XOR differences without a data-dependent early result. This is source
algorithm/functional evidence, not a JVM constant-time guarantee. The original
GHC9.12.2-O2 deferred equality probe forces the right length/bottom first; explicit
deferred equality records that selected compiler profile, not universal imprecise
exception ordering. Deferred result carriers memoize success and synchronous
failure. Strict Context/newtype/selector/list-tail/error/sharing probes execute
real original HashAlgorithm instances, not a Kotlin-derived callback oracle.

## Original evidence

* `python3 tools/extract-hmac-reference.py --check` verifies verbatim HMAC, hash,
  RAM byte/ptr-method sources and the complete original HMACSpec body. Only its
  module export header is expanded for observation; no original test changes.
* `capture-haskell.py --oracle /tmp/kotdoc-hmac-haskell-oracle --verify` captures
  ordinary/stream/lazy/incremental, SHAKE zero/non-byte/long-output, public digest,
  equality and deep 10000-chunk cases. It also executes all original HMACSpec
  42 examples: 28 KAT assertions (14 vectors, one-shot and incremental) and
  14 properties with 100 trials each. All 1400 actual generator rows execute
  both original expectations and translated property equations in Kotlin.
* `capture-dictionary.py --oracle /tmp/kotdoc-hmac-dictionary-oracle --verify`
  preserves the initial 218 exact dictionary observations. KnownNat profiles
  include block/digest (0,4),(1,1),(8,4),(8,8),(8,12), testing short/equal/long keys,
  digest shorter/equal/longer than block, init/update/finalize and reset faults,
  metadata bottoms, ignored values, strict Context roots and Eq error order.
* `capture-dictionary-extra.py --oracle /tmp/kotdoc-hmac-dictionary-extra-oracle
  --verify` adds 216 list/newtype/selector/empty-message and repeated shared-root
  observations. Success/failure computations are evaluated twice; callback events
  remain once-only. Original expectation files are independent of Kotlin.
* `python3 tools/import-hmac-fixtures.py --check` binds packaged fixture bytes,
  source copies, accepted dependency hashes and the current Kotlin/test sources.

The Haskell programs use GHC9.12.2-O2 and the pinned crypton2.0.1 development
package with seven visibility-only Cabal exports; no implementation source bytes
change. Original crypto native backend selection is recorded separately from
Kotlin's portable algorithms. These ordinary observations do not implement or
close the remaining optimized backend gates.

Raw HashAlgorithm IO methods and ByteArrayAccess callbacks execute effects before
returning lazy results. Returned Unit roots are ignored where the original ignores
them; the equal-key branch needs the returned pad-pair root. Independent generic
access (305) and IO Unit-root (96) probes cover these distinctions, callback
repetition, ordering, faults and once-only shared synchronous outcomes.

## Boundaries

Raw dictionaries must provide valid nonnegative allocation dimensions fitting
JVM arrays, sufficient context/output storage, and write only within the supplied
callback buffers. Pointer retention, invalid native allocations and unsafe foreign
mutation after freezing are not parity domains. Real provider context sizes are
preserved; uninitialized native tails remain subject to accepted engine profile
limits. Giant allocations, arbitrary Integer/Nat widths beyond JVM metadata,
physical ScrubbedBytes erasure, heap layout, asynchronous exceptions, universal
bottom semantics and timing are not proved. Owned snapshots versus explicitly borrowed pointer callback storage, and context/digest copy
semantics and bounded synchronous callback/error traces are checked separately.

RSA padding/full Types/signatures, OS/SystemDRG, missing hash families/backends,
shared Digest Data, X509 trust/chain and the rest of task613 remain open. The full
Pandoc tests/formats and later calibre/MOBI goals are not closed by this module.
