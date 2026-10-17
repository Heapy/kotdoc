# Public Crypto.Cipher.ChaCha

This checkpoint translates all seven module functions, State/StateSimple, and
all four ChaChaState methods for each state. Four private counter wrappers and
twelve FFI mappings use the accepted Kotlin portable core. Neither state has a
source Eq/Ord/Show/ByteArrayAccess instance; Kotlin exposes no invented value
comparison or byte serialization. NFData forces the opaque state; normal Kotlin
values are eager and separate deferred adapters preserve recorded demand probes.

Initialization checks key, nonce and rounds with exact source errors. Simple
seeds require at least40 bytes and ignore the suffix. Ordinary generate<=0 and
empty combine retain the original state object; simple generation at zero and
counter setters copy. Pending bytes survive counter changes. Word32/Word64
counter values use UInt/ULong; block-round input represents conversion to Word8
before validation. Output and input ownership is independent of state ownership.

The ordinary public Haskell module is unchanged. The line oracle calls its public
functions, observes both counters and probes the previous immutable state after
each operation. The original600 generated samples and76 literal/carry observations
are retained from the previous mechanically verified original-suite capture;
concatenated public outputs are checked against those independent expected bytes.
The unchanged original79-example suite was independently run by that checkpoint.
New requests exercise every reachable pending-byte offset1..63, chunk boundaries,
seeking/wrap, StateSimple's distinct rekey generator, XChaCha and ordered errors.
No expected output is computed by Kotlin.

## Explicit boundaries

This is the actual AArch64 little-endian crypton Cabal backend; optimized raw
backend implementation is still unported. Public-byte agreement with that backend
and portable Kotlin does not prove all raw optimized aliases or instruction paths.
The observed C context is136 bytes; Haskell allocates132 logical bytes. The
accepted raw checkpoint preserves a controlled trailing-store witness. Kotlin
uses a safe full136-byte object and makes no GHC heap/allocator-memory parity claim.

RAM alloc maps a negative allocation size to zero, but generateSimple passes the
negative Int converted to CUInt to the writer. That invokes out-of-object writes;
negative simple counts are outside the safe source buffer domain. Kotlin rejects
negative array sizes; this is a safe boundary, not matching source memory effects.
Ordinary generate negative remains defined empty behavior. Actual giant allocation,
64-bit Haskell Int beyond JVM Int, physical erasure and constant-time JVM execution
are not proven. A Cleaner retains logical best-effort state cleanup only.

Bottom/exception-choice probes record the actual optimized Haskell compiler. In
particular initialize with bottom rounds and invalid key, and block generation
with invalid rounds and bottom state, expose strictness before a source guard.
Deferred adapters retain those finite observations; they are not a proof of all
Haskell evaluation order or compiler-independent exception selection. State
constructors are eager in ordinary Kotlin; lazy adapters make that boundary explicit.

Existing committed raw-core work-in-progress Kotlin copies remain historical
artifacts of their accepted checkpoint; this checkpoint neither edits nor imports
them. Live production uses CryptoChaChaCore.kt from core's source set. New public
drafts are removed on installation so no duplicate public implementation is kept.

Crypton and RAM notices remain unchanged. ChaChaDRG, Random.Probabilistic, Prime,
optimized raw backends, other613 signatures/trust/chain and full Pandoc623/calibre
624–626 remain open.
