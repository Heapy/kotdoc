# Pinned X509 CRL observer and original suite

Development-only reference for unchanged `Data.X509.CRL` from crypton-x509
1.9.2, with unchanged local X509/ASN1/hourglass modules and crypton 2.0.1.
GHC 9.12.2 and frozen dependencies are required. These programs are test
oracles, never production runtime dependencies.

Build here with the pinned GHC and Cabal on PATH:

```sh
cabal build --offline --builddir=/tmp/kotdoc-x509-certificate-build x509-crl-reference x509-original-tests
cabal list-bin --builddir=/tmp/kotdoc-x509-certificate-build x509-crl-reference
cabal list-bin --builddir=/tmp/kotdoc-x509-certificate-build x509-original-tests
```

Set ORACLE to the first executable and ORIGINAL to the second. From the root:

```sh
"$ORACLE" --original-tests
"$ORIGINAL" --quickcheck-tests 1000 --quickcheck-replay 1945978545 --color never --hide-progress
python3 tools/import-x509-crl-fixtures.py --oracle "$ORACLE"
python3 tools/import-x509-crl-fixtures.py --check-source
python3 tools/import-x509-crl-fixtures.py --oracle "$ORACLE" --verify-oracle
python3 tools/import-x509-posix-time-fixtures.py --oracle "$ORACLE"
python3 tools/import-x509-posix-time-fixtures.py --check-source
python3 tools/import-x509-posix-time-fixtures.py --oracle "$ORACLE" --verify-oracle
```

OriginalCrlTests retains all original generator/property bodies before main;
only module/imports change, and the importer verifies that exact extraction.
The observer's original CRL round-trip property uses 1,000 cases and replay
seed 1945978545. Another 1,000 actual original CRL generator inputs are retained
in the corpus and regenerated during replay. The independent original-suite
executable compiles the unchanged vendored `Tests/Tests.hs`, including its main
and all nine named properties. Merely compiling retained bodies is not counted
as executing them; running upstream tests does not prove Kotlin parity by itself.

The original property uses ASN1 values, not DER bytes: arbitrary DN OIDs may
pass that property but be invalid DER. Byte observations remain separate.
CRL encoding is a bare field stream. Dates are GeneralizedTime with offset
zero, version is an unbounded Integer, next-update/revoked/extensions are
optional, and explicit empty revoked containers normalize to omission.
The observer also covers individual revoked entries, permissive End markers,
remainders, deferred fields, separate Maybe/list spine/element demand and
ordered structural/payload/tail errors.

Current oracle generation/replay is macOS/AArch64. Signed/SignedExact, original
signed-byte preservation, signature/trust validation and stores remain pending,
as do independent other-platform oracle replay and broader stress coverage.

DER formatting uses a generic native calendar conversion in this pinned build.
For example, 2050-02-29 25:-1:61 normalizes to 2050-03-02 01:00:01. The original
DateTime rewrite rules must not be inferred to remove a native call when actual
oracle output demonstrates otherwise. Windows FILETIME rejects some pre-1601
values accepted by POSIX. The separate POSIX time fixture captures original
DateTime-to-elapsed and native elapsed-to-DateTime observations for the date
inputs found in both certificate and CRL requests. It supplies their reference
environment on every test platform; it does not change production native time
conversion or establish a Windows upstream-oracle replay. Real native formatting
and Windows range errors are checked separately in X509NativeTimeTest.
