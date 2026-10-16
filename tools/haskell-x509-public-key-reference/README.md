# Pinned X509 public-key observer

Development-only reference for `Data.X509.PublicKey` and `Data.X509.OID` from
crypton-x509 1.9.2. The observer compiles unchanged checked-in X509, ASN1 and
hourglass modules, with unchanged crypton 2.0.1 as a local Cabal package.
Production code is Kotlin and has no Haskell or crypton runtime dependency.
GHC 9.12.2 is required by a compile-time check; dependencies are frozen.

Build from this directory with the pinned GHC and Cabal on PATH:

```sh
cabal build --offline --builddir=/tmp/kotdoc-x509-public-key-build x509-public-key-reference
cabal list-bin --builddir=/tmp/kotdoc-x509-public-key-build x509-public-key-reference
```

Set ORACLE to the printed executable and run from the repository root:

```sh
"$ORACLE" --original-tests
python3 tools/import-x509-public-key-fixtures.py --oracle "$ORACLE"
python3 tools/import-x509-public-key-fixtures.py --check-source
python3 tools/import-x509-public-key-fixtures.py --oracle "$ORACLE" --verify-oracle
```

OriginalPublicKeyTests preserves exact original public-key generators, their
secret-key/ByteString generator dependencies and the generic ASN1 round-trip
property. Only module header/imports are adapted; the importer checks every
extracted body. The original `pubkey` property runs 1,000 QuickCheck cases with
replay seed 1545978545. The corpus retains another 1,000 actual original
inputs generated with explicit seeds and sizes; verification regenerates them.
X25519/X448/Ed25519/Ed448 public inputs are derived by the real crypton
`toPublic` functions, not synthetic replacements.

JSON observations cover key records/Eq/Show, all 33 named curve OIDs, algorithm
selection, ASN1/DER output, BER readers, remainders, partial output and separate
constructor/field/tail demands. Unsupported DH/EC-Prime encoders and unknown-OID
reader rejection are retained exactly. The corpus was generated and replayed
on macOS/AArch64; independent other-platform oracle replay and cryptographic
operations are not claimed by these tests.
