# X509 algorithm identifier reference

Development-only observer, using GHC 9.12.2. The actual
`Data.X509.AlgorithmIdentifier` module is compiled directly and unchanged from
crypton-x509 1.9.2, alongside 16 original ASN1 and 12 original hourglass modules.
The observer contains JSON adapters and observations, not a rewritten codec.

`OriginalSignatureTests.hs` consists of the unchanged HashALG, PubKeyALG and
SignatureALG Arbitrary instances, plus the unchanged polymorphic
`property_unmarshall_marshall_id` body from the retained original Tests.hs.
Only the module header/imports are adapted. The fixture importer verifies that
exact extraction. This allows the original signature test to execute without
claiming that public/private keys, extensions, certificates, CRLs or the
private-key redaction tests have been ported. The entire original test file and
its release remain retained verbatim.

```
cabal build exe:x509-algorithm-reference
cabal run exe:x509-algorithm-reference -- --original-test
```

The latter runs the original signature property for 1000 QuickCheck cases with
replay seed 1095978545. `--generate-tests 1000` exports 1000 actual original
SignatureALG generator inputs (seed 1095978545+i, size i mod 100); the fixture
importer calls the original property for each expected result. Both generation
and outputs can be checked independently with another GHC build:

```
python3 tools/import-x509-algorithm-fixtures.py --oracle /path/to/x509-algorithm-reference
python3 tools/import-x509-algorithm-fixtures.py --verify-oracle --oracle /path/to/x509-algorithm-reference
python3 tools/import-x509-algorithm-fixtures.py --check-source
```

Additional cases cover every algorithm constructor and OID mapping, unsupported
combinations, unknown OIDs, both PSS parameter patterns, every position mutation
and truncation, ignored PSS hash/salt/wrapper fields, explicit-NULL EdDSA
rejection, remaining input, output-prefix/constructor/field demand, failing
input tails and DER prefixes. These observations preserve the source parser's
permissiveness and do not assert cryptographic validation.
