# Pinned X509 certificate observer

Development-only reference for unchanged `Data.X509.Cert` from crypton-x509
1.9.2, compiled with unchanged X509/ASN1/hourglass modules and local crypton
2.0.1. GHC 9.12.2 is required by CPP; dependencies are frozen. None of this
Haskell code is a production runtime dependency.

Build here with the pinned GHC and Cabal on PATH:

```sh
cabal build --offline --builddir=/tmp/kotdoc-x509-certificate-build x509-certificate-reference
cabal list-bin --builddir=/tmp/kotdoc-x509-certificate-build x509-certificate-reference
```

Set ORACLE to the printed executable and run from the repository root:

```sh
"$ORACLE" --original-tests
python3 tools/import-x509-certificate-fixtures.py --oracle "$ORACLE"
python3 tools/import-x509-certificate-fixtures.py --check-source
python3 tools/import-x509-certificate-fixtures.py --oracle "$ORACLE" --verify-oracle
```

OriginalCertificateTests retains every original generator/property body before
`main`, including all dependencies of Arbitrary Certificate. Only module/imports
are adapted, and the importer verifies the exact extraction. The original
named certificate round-trip property runs 1,000 QuickCheck cases with replay
seed 1845978545. The corpus independently retains and regenerates another
1,000 actual Certificate generator inputs. Other original property bodies are
not counted as executed merely because they are retained in this module.

The original property operates on ASN1 values, not DER bytes. Arbitrary DN OIDs
can be empty or negative and may fail DER encoding while passing that original
property; DER observations are therefore separate requests. Observations retain
the raw field stream without a surrounding Sequence, optional/wrapped version,
2050 time-type threshold, ignored input time kind/zone, version-independent
extensions, parser remainders and ordered lazy payload/tail errors. This slice
does not implement Signed/SignedExact, certificate signature validation, CRL or
trust-store policies. Current oracle generation/replay is macOS/AArch64;
independent other-platform oracle replay and broader stress remain pending.
