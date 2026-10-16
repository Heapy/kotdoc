# Pinned X509 Signed / SignedExact observer

Development-only observer of unchanged `Data.X509.Signed` from crypton-x509
1.9.2, its unchanged ASN1/hourglass modules, and crypton 2.0.1. GHC 9.12.2 and
the frozen dependency graph are required. Haskell is not a production dependency.

Build in this directory with the pinned GHC/Cabal on PATH:

```sh
cabal build --offline --builddir=/tmp/kotdoc-x509-certificate-build x509-signed-reference
cabal list-bin --builddir=/tmp/kotdoc-x509-certificate-build x509-signed-reference
```

Set ORACLE to that executable. From the repository root:

```sh
python3 tools/import-x509-signed-fixtures.py --oracle "$ORACLE"
python3 tools/import-x509-signed-fixtures.py --check-source
python3 tools/import-x509-signed-fixtures.py --oracle "$ORACLE" --verify-oracle
```

This observer exercises source Eq/Show, signedToExact, objectToSigned,
objectToSignedExact, arbitrary-functor objectToSignedExactF (list, Maybe and
writer pair), and decodeSignedObject. A small ASN1Object probe exposes remaining
object state, structural errors and partial pattern matches; concrete Certificate
and CRL codecs are also exercised. A thousand inputs of each concrete type come
from the retained original generators at seed 2045978545 plus input index and
size index modulo 100. Signed.hs has no named property in the original Tests.hs;
these are observations of its unchanged implementation, not an invented claim
that an original Signed property was executed.

Non-exhaustive-case exceptions have compiler source locations removed from their
diagnostic. Other first-line exception text, returned Left messages, whole-input
bytes, reconstructed object bytes and Show output remain independent expectations.

BER input may use nonminimal lengths and arbitrary container kinds. The original
preserves the whole input byte string, but reconstructs signed-object bytes through
Raw.toByteString. That writer emits no bytes for ConstructionEnd, even for an
indefinite container: EOC omission is retained as upstream behavior. Invalid
primitive decoding has a one-cell lookahead after the first outer container;
invalid first trailing primitives are demanded while a second trailing primitive
can remain unevaluated. Structurally invalid trailing raw bytes can still fail
the earlier raw parsing stage.

Current observation/replay runs on macOS/AArch64. DER time formatting retains
the original native conversion and normalization. The POSIX time environment
fixture also captures inputs from this corpus so replay on other hosts does not
silently substitute Windows FILETIME behavior. Independent upstream replay on
other platforms, cryptographic verification/trust and broader stress remain work.
