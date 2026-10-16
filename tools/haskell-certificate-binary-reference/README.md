# Certificate binary reference

Development-only GHC 9.12.2 observer. Production conversion is implemented in
Kotlin; it does not invoke GHC, OpenSSL, Java X509 parsing or JSSE.

The executable compiles 22 original modules directly from retained package
source trees, without rewriting their algorithms: crypton-pem 0.3.0, the
standard ByteString path of base64 1.0, raw crypton-asn1-encoding 0.10.0 and
crypton-asn1-types 0.4.1 Lowlevel. The base64 version CPP macro selects its
actual 1.0 branch in crypton-pem; it does not modify the original sources.
The observer projects values/results to JSON and supplies Get programs and
chunks. Header parsing, integer conversions, raw-event writing, continuation
state, choice/backtracking, PEM framing and Base64 errors all run upstream.

Original PEM test/Main.hs is also compiled unchanged as original-pem-tests.
Its four unit cases and marshalling property are retained; the Kotlin corpus
includes the exact unit inputs, 1,000 observations from the property domain,
additional writer/reader boundaries and both strict/lazy chunks. Raw ASN.1
Header/Event marshalling properties are separately observed. The remaining
three typed ASN.1 DER/Real properties are retained but not claimed as ported.
All base64 source/tests are retained; only the standard strict encode/decode
and standard validity APIs used by PEM are implemented in this checkpoint.
URL alphabets, typed witness APIs, lenient and text wrappers remain outside it.

Raw-event writing intentionally retains the original empty ConstructionEnd
serialization even for indefinite lengths. Long header tag/length arithmetic
uses the original 64-bit machine Int/Word64 behavior. Get choice retains the
failed branch's position rather than resetting it. Lazy chunk-demand cases
put an exception after an input chunk and verify whether the original parser
needs that tail. Only genuine upstream results supply fixture expectations.

The additional retained crypton-x509 1.9.2, crypton-x509-store 1.9.0,
crypton-x509-validation 1.9.1 and crypton-asn1-parse 0.10.0 sources/tests prepare
the next dependency layer. Their availability does not mean their algorithms,
certificate parsing, trust-store loading, validation or TLS are implemented.

Build the observer in this directory:

```sh
cabal build --builddir=/tmp/kotdoc-certificate-binary-build certificate-binary-reference
cabal build -fupstream-tests --builddir=/tmp/kotdoc-certificate-binary-build original-pem-tests
```

Run original-pem-tests with `--maximum-generated-tests=1000 --test-seed=9122 --plain`.
Regenerate/compare from the repository root:

```sh
python3 tools/import-certificate-binary-fixtures.py --oracle /absolute/path/to/certificate-binary-reference
python3 tools/import-certificate-binary-fixtures.py --verify-oracle --oracle /independent/path/to/certificate-binary-reference
python3 tools/import-certificate-binary-fixtures.py --check-source
```
