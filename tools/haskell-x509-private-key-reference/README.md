# Pinned X509 private-key observer

Development-only reference for unchanged `Data.X509.PrivateKey` from
crypton-x509 1.9.2, with unchanged X509/ASN1/hourglass modules and the complete
unchanged local crypton 2.0.1 package. Production behavior is Kotlin; this
executable is never a runtime dependency. GHC 9.12.2 is required by CPP and
Cabal dependencies are frozen.

Build here with the pinned GHC and Cabal on PATH:

```sh
cabal build --offline --builddir=/tmp/kotdoc-x509-private-key-build x509-private-key-reference
cabal list-bin --builddir=/tmp/kotdoc-x509-private-key-build x509-private-key-reference
```

Set ORACLE to the printed executable and run from the repository root:

```sh
"$ORACLE" --original-tests
python3 tools/import-x509-private-key-fixtures.py --oracle "$ORACLE"
python3 tools/import-x509-private-key-fixtures.py --check-source
python3 tools/import-x509-private-key-fixtures.py --oracle "$ORACLE" --verify-oracle
```

OriginalPrivateKeyTests preserves the exact original RSA/DSA/curve private-key
and supporting generators, generic ASN1 round-trip property, ECSecret generator
and EC show-redaction property. Only imports/module header are adapted; the
importer checks every extracted body. Both original named tests run 1,000
QuickCheck cases with replay seeds 1645978545 and 1745978545. The corpus retains
another 1,000 actual inputs for each original generator with explicit seeds and
sizes; verification regenerates all 2,000 inputs independently.

The observer separates ordinary redacted Show from explicit debugShow, Eq,
algorithm selection, ASN1/DER encoding, fallback parsers, decoded key/remainder,
metadata, list prefixes and field/tail demand. DER helper requests build nested
wrapper inputs using the unchanged upstream writer. Signed modular-power
observations characterize the used crypton/GMP numeric results, not timing.
The negative-i2osp native path returns allocation bytes without initializing
them; no exact byte expectations are fabricated for that undefined-memory path.
The Kotlin encoder reports that path as unsupported. Byte scrubbing, JIT/GC and
constant-time equivalence are not established by these functional observations.
All current observations were generated/replayed on macOS/AArch64; independent
other-platform oracle replay remains pending.
