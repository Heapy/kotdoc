# X509 distinguished name reference

Development-only GHC 9.12.2 observer. It compiles unchanged upstream
`Data.X509.DistinguishedName`, `Data.X509.Internal`, `Data.ASN1.Parse`, the 16
existing ASN1 modules and 12 hourglass modules directly from pinned releases.
The bridge supplies JSON adapters and observations; it does not replace the
DN parser, encoder, derived comparisons, lookup, monoid or container helper.

`OriginalDnTests.hs` contains the unchanged arbitraryBS function, ASN1 string
and DistinguishedName Arbitrary instances, and the unchanged generic
`property_unmarshall_marshall_id` from crypton-x509 Tests.hs. Only the module
header/imports are adapted, and the importer verifies this exact extraction.
The upstream main has no separately named DN test: the observer instantiates
that original generic property for DN, whose original generator is used by
upstream certificate/CRL generation. It does not claim that the certificate or
CRL tests have been ported.

```
cabal build exe:x509-dn-reference
cabal run exe:x509-dn-reference -- --original-test
```

This runs 1000 original-generator/property cases with replay seed 1145978545.
`--generate-tests 1000` exports original generator inputs with seed 1145978545+i
and size i mod 100. The importer invokes the actual original generic property
on every exported input. Arbitrary OIDs include empty/invalid binary encodings:
the original DN property works on typed ASN1 and does not require DER encoding;
the observer preserves that distinction. DER is tested separately.

```
python3 tools/import-x509-dn-fixtures.py --oracle /path/to/x509-dn-reference
python3 tools/import-x509-dn-fixtures.py --verify-oracle --oracle /path/to/x509-dn-reference
python3 tools/import-x509-dn-fixtures.py --check-source
```

Additional observations cover all 12 string encodings/all byte values, derived
DN/Inner Eq/Show and DN Ord, all five DnElement OIDs, first-duplicate lookup,
append/empty, flat attributes from multi-valued SETs, Inner consuming all input,
missing/mismatched End markers, every token-position mutation/truncation,
malformed streams, bounded output/failing tails, ignored/deferred OID payloads,
and the distinction between an error's Left constructor and its lazy message.
Full nested bottom/infinite-list semantics and broader resource stress remain
separate work; these finite observations are not an exhaustive proof of them.
