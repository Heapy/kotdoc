# Pinned X509 EC, chain and facade observer

Development-only oracle for unchanged crypton-x509 1.9.2 Data.X509.EC,
Data.X509.CertificateChain and Data.X509. GHC 9.12.2, unchanged local ASN1/
hourglass modules and the full crypton 2.0.1 package are pinned by source and
the frozen dependency graph. Haskell is never a production dependency.

Build here with the pinned GHC and Cabal on PATH:

```sh
cabal build --offline --builddir=/tmp/kotdoc-x509-certificate-build x509-utilities-reference
cabal list-bin --builddir=/tmp/kotdoc-x509-certificate-build x509-utilities-reference
```

Set ORACLE to that executable. From the repository root:

```sh
"$ORACLE" --vectors
python3 tools/import-x509-utilities-fixtures.py --oracle "$ORACLE"
python3 tools/import-x509-utilities-fixtures.py --check-source
python3 tools/import-x509-utilities-fixtures.py --oracle "$ORACLE" --verify-oracle
```

OriginalEcPointVectors preserves exact bodies from crypton tests/PubKey/ECCSpec:
the Arbitrary Curve instance, twelve point-validation vectors, and twenty points
outside the prime-order subgroup. The importer checks the exact extraction.
The observer executes the twelve original expected validation results and the
twenty original "the point is on the curve" assertions. It retains another
1,000 actual original Curve generator inputs at seed 2145978545 plus index and
size index modulo 100. These do not imply the remaining ECC scalar arithmetic,
DH, subgroup rejection, timing or full crypton tests have been ported.

Coverage includes all 33 named curves, simple/binary-field point validation,
serialized point format/length, explicit generator validation, matching a prime
curve name using only a/b/order/prime, lazy ignored key fields, and curve OIDs.
The original on-curve predicate accepts infinity and is not a subgroup check.

Chain cases preserve the first failing index and message, all-or-failure decode,
raw byte ownership, Eq/Show, and separate list spine/head/tail demand. Signed
certificate/CRL facade cases use concrete raw bytes retained from the preceding
independent Signed observer; those input fixtures and sources are hashed too.
DN hashes retain ASCII-only byte lowercasing and forced UTF8/inner DER for SHA1,
and original encoding/full DN DER for MD5, followed by digest bytes [3,2,1,0].

Current independent oracle replay is macOS/AArch64. Chain decoding does not
verify signatures or trust. Broader stress, finer thunk/resource boundaries,
independent other-platform oracle replay, and remaining cryptographic operations
and their complete original suites remain pending.
