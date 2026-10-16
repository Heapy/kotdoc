# Public Curve448 and modern X448 partition

Source: pinned crypton2.0.1 `Crypto/PubKey/Curve448.hs`, `Crypto/ECC.hs`, Curve448Spec/ECCSpec and RAM0.22.1. Notices remain `licenses/crypton-BSD-3-Clause.txt`, `licenses/ram-BSD-3-Clause.txt`, and accepted raw Decaf MIT provenance. Original Haskell is development oracle only; production calls the specialized Kotlin Decaf engine accepted in3d79fde18b8612e18b56090ac410e6adc45c776b.

Ownership: new CryptoCurve448.kt, CryptoEccX448.kt, CryptoCurve448Test.kt, crypto-curve448-public resources, these source/oracle tools and the eventual narrow CryptoEcc.X448 adapter registration. Existing raw field/scalar/geometry/multiplication sources remain unchanged. Drafts stay outside core until raw-engine acceptance and isolated compilation/source-slot handoff.

## Exact source matrix

Three immutable byte types SecretKey(ScrubbedBytes), PublicKey(Bytes), DhSecret(ScrubbedBytes), each ByteArrayAccess/Show/Eq/NFData; SecretKey additionally DebugShow. All store exactly56 bytes after checked constructors. Public/show includes Haskell byte-string quoting; scrubbed Show preserves the RAM marker and constructor precedence; debugShow deliberately prints lowercase secret hex. Input and exported snapshots are owned copies. Kotlin Cleaner cleanup is best effort; JVM constant timing, prompt physical erasure and GHC bottom/lazy-demand equivalence are not proven.

Six exports: publicKey, secretKey, dhSecret, dh, toPublic, generateSecretKey. Constructors reject length other than56 with PublicKeySizeInvalid, SecretKeySizeInvalid, SharedSecretSizeInvalid respectively. secretKey's private validPtr returnsTrue unconditionally; its structure-error branch is unreachable in this pinned source. All raw56-byte bit patterns pass, including zero, unclamped secrets and noncanonical public coordinates. No extra range/subgroup/safety checks are introduced.

Unlike Curve25519, generateSecretKey requests exactly56 random bytes once and does NOT clamp the stored secret. The valid MonadRandom provider must return the requested byte count. Kotlin rejects malformed provider lengths at this boundary; no parity is claimed for source providers violating that contract. Provider exceptions propagate without retry. Calculation uses the accepted raw engine clamp and never mutates the stored secret. toPublic allocates fresh56-byte output via derive_public_key; dh allocates fresh56-byte scrubbed output, deliberately ignores the C status and permits all-zero shared values. Original FFI declarations return IO(), so C status is unobservable here.

Modern marker Curve_X448 derives Show/Data in Haskell. Its singleton name is preserved as Curve_X448; source constructor metadata is evidence, not a claim that JVM reflection implements Haskell Data. Exactly EllipticCurve and EllipticCurveDH capabilities are supplied, with no arithmetic/basepoint interfaces or fake unsupported methods. Ten source methods: curveSizeBits448, curveGenerateScalar, curveGenerateKeyPair, encode/decodePoint, encode/decodeScalar, scalarToPoint, ecdhRaw, ecdh. Generation retains the same raw secret bytes. Codecs are raw owned56-byte snapshots. ecdhRaw preserves zero; ecdh applies source constAllZero and returns ScalarMultiplicationInvalid only for an all-zero shared result.

## Independent coverage and remaining gates

The oracle also executes the complete unchanged modern ECCSpec52-example native suite as a supporting baseline across all six source families. This does not turn the X448 partition into a claim about every upstream module.

The oracle runs unchanged Curve448Spec4 literal examples and a mechanically selected ECCSpec X448 partition:4decode vectors,2weak points,2applicable property families (8examples). X448 is excluded from all CurveArith properties in the actual source; no arithmetic stubs are added. The full original ECC suite has separately accepted prime/X25519/Edwards25519 partitions; this partition does not claim Ed448 signatures/public adapters.

Corpus covers all256 repeated byte values plus100 independent seeded random patterns; boundary constructor lengths; equality/inequality; Show precedence; DebugShow/NFData; exact RNG effects, provider failure, generated-public roundtrips and commutative DH; all original literal vectors; low-order/noncanonical coordinates; raw-zero vs modern checked-DH results. Kotlin additionally checks mutable input/output/provider ownership and capability reflection. Original expected values come solely from pinned Haskell/RAM, never Kotlin. Native profile selection and real32-bit ABI limits remain documented in the accepted raw engine.

Ed448 requires separate SHAKE/signature/public/modern work. Other local613 number/primality/trust/chain and whole Pandoc/calibre scope remain open. This checkpoint is public Curve448 plus modern X448 only.
