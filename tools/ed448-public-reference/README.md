# Complete public Ed448 wrapper

This checkpoint ports the pinned Crypto.PubKey.Ed448 module: three immutable
byte wrappers (SecretKey, PublicKey, Signature), three size constants57/57/114,
and all eight functions. SourceMatrix.json lists every export and derived
instance. The accepted raw Ed448/Decaf/SHAKE engines remain unchanged. No
Crypto.ECC Curve_Ed448 instance exists in the original, and none is invented.
Raw context/prehash/keypair support is not added to the narrower Haskell API.

All expectations come from actual original crypton2.0.1 and ram0.22.1 Haskell,
never Kotlin. Extractor verifies both pinned archive manifests and copies the
public source, errors, byte implementation and original tests verbatim.
OriginalEd448Spec changes only its module name. The unchanged original suite
executes all25 examples: eight literal vectors times derive/sign/verify plus
secret generation. Kotlin replays those24 literals on all four accepted engine
profiles; additional unsafeSign literals and deterministic MonadRandom probes
cover the generation contract. The system entropy provider itself remains a
separate Crypto.Random scope, not a Kotlin/native production dependency.

The9056-row corpus includes all256 uniform byte patterns plus seeded random
buffers, exact/wrong lengths for all constructors, RAM Show precedence,
DebugShow, Eq, NFData, source-copy ownership, RNG effects, derivation, safe vs
unsafe signing, source literal vectors, message/public/signature tampering and
explicit finite demand probes. Every row is replayed on all four raw profiles.
Original Haskell source-owned copies are checked using a dev ByteArrayAccess
view over mutable foreign storage: values are forced before the caller mutates
the source buffer. This does not mutate an immutable Bytes contract.

## Exact source contracts

- publicKey wrong length returns CryptoError_PublicKeySizeInvalid.
- secretKey AND signature wrong length return CryptoError_SecretKeyStructureInvalid.
  Secret pointer validation is unconditionally True; arbitrary correctly sized
  raw bytes are accepted. No point/signature validation is added at construction.
- sign ignores the supplied public key completely and rederives it from secret.
  unsafeSign uses the supplied public key, including inconsistent raw bytes.
- generateSecretKey makes exactly one57-byte RNG request and stores raw bytes:
  no clamp, range rejection or retry. Valid providers return the requested size;
  malformed provider lengths are outside MonadRandom's source precondition and
  rejected at the Kotlin bridge. Provider exceptions propagate after one call.
- Constructors own copies. Public byte access in Kotlin returns independent
  snapshots. Scrubbed Show is '<scrubbed-bytes>', not hex; DebugShow is explicit.
  Secret Eq scans all57 bytes; physical JVM erasure/timing is not proven.

Only one shared API change is needed: append the source-required
CryptoError_SecretKeyStructureInvalid case to CryptoEcc.Error, preserving shared
CryptoFailable/error identity. Existing values and adapter behavior are unchanged;
new tests check typed identity and prior key-size error behavior.

Explicit Lazy adapters model the observed demand boundaries: scrubbed secret
Show does not demand its payload; NFData and public/signature Show do. Safe sign
never demands supplied public. On the pinned optimized GHC build, sign/unsafeSign
force message before secret/public; verify's observed multi-bottom precedence is
public, then signature, then message. The actual oracle records every combination,
not a guessed evaluation order from source formatting. These are finite
compiler-profile observations, not universal Haskell/GHC exception-order or
lazy equivalence proofs. Ordinary Kotlin calls remain eager at call boundaries.

## Limits and reproduction

Raw engine domain/negative-shift UB/ABI/range limits remain those recorded by
accepted checkpoints. Four forced engine profiles are not four real OS ABIs.
ByteArray spans are Int-sized; no huge size_t or pointer-identity equivalence,
constant-time execution, Cleaner scheduling or physical wipe guarantee is made.
Readonly withByteArray actions receive snapshots, not a native pointer into the
stored object. Public errors and demand results are preserved without extra
security hardening. Full local:613 number/primality/signature/trust/chain and
other crypto work, and the complete Pandoc/calibre goal, remain OPEN.

- python3 tools/extract-ed448-public-reference.py --check
- python3 tools/import-ed448-public-fixtures.py --check-source
- python3 tools/import-ed448-public-fixtures.py --oracle <executable> --verify-oracle
- :core:test --tests io.heapy.kotdoc.crypto.CryptoEd448Test :core:jar :cli:installDist

Build the dev Haskell project with its pinned cabal.project/freeze. Existing
executable: /tmp/kotdoc-ed448-haskell-build/build/aarch64-osx/ghc-9.12.2/kotdoc-ed448-public-reference-0.1.0.0/x/ed448-public-reference/opt/build/ed448-public-reference/ed448-public-reference.
Original crypton BSD3, RAM BSD3, Decaf MIT and SHA3 BSD2 notices remain packaged.
