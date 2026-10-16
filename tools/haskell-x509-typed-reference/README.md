# Pinned typed X509 extension observer

Development-only observer of `Data.X509.Ext` from the checked-in,
SHA-256-verified crypton-x509 1.9.2 source. Production conversion uses Kotlin.
The Cabal file compiles unchanged vendor modules directly; it does not use a
substitute implementation of X509 or ASN1. The freeze file pins dependencies,
and the main module requires GHC 9.12.2.

From this directory, with that GHC and Cabal on PATH:

```sh
cabal build --offline --builddir=/tmp/kotdoc-x509-typed-build x509-typed-reference
cabal list-bin --builddir=/tmp/kotdoc-x509-typed-build x509-typed-reference
```

Use the printed executable as `ORACLE` below (run from the repository root):

```sh
"$ORACLE" --original-tests
python3 tools/import-x509-typed-fixtures.py --oracle "$ORACLE"
python3 tools/import-x509-typed-fixtures.py --check-source
python3 tools/import-x509-typed-fixtures.py --oracle "$ORACLE" --verify-oracle
```

`OriginalTypedExtensionTests.hs` preserves the exact original key-usage and
extended-key-usage generators and generic round-trip property body. The
importer verifies that extraction against the pinned original Tests/Tests.hs.
The executable runs both original properties with 1,000 cases, using replay
seeds 1345978545 and 1445978545. `--generate-tests 1000` emits their 2,000 actual
inputs, with independent seeds and sizes; the verifier regenerates them.

The JSON adapter observes metadata, derived models, enum ranges, encode/decode,
BER/DER bytes, raw wrappers, accessors and separately demanded constructors,
list spines, elements and prefixes. It records exceptions without rewriting
source behavior, including unimplemented CRL methods, Netscape non-nested
bytes, DNSSRV's XMPP encoder OID and NameConstraints' asymmetric wrappers.
The Kotlin adapter materializes JSON list spines before serializing elements,
as the original Aeson observer does. Extreme Word64 bit lengths are observed
without forcing unbounded zero-bit scans; this is not a termination or resource
stress test. The checked-in corpus was generated and independently replayed on
macOS/AArch64. Other-platform oracle replay remains separate work.
