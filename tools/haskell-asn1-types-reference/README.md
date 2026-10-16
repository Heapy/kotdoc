# Original ASN.1 bit arrays and character strings

This development observer compiles Data.ASN1.BitArray, Data.ASN1.OID and
Data.ASN1.Types.String directly from the unchanged, SHA-pinned
`vendor/crypton-asn1-types-0.4.1` tree. It only interprets JSON requests,
projects values, and forces/catches original results. It implements no codec.
GHC 9.12.2 is required; the project freeze selects the same package set as the
certificate binary observer.

```sh
cabal build asn1-types-reference
python3 ../import-asn1-types-fixtures.py --oracle /absolute/path/to/asn1-types-reference
python3 ../import-asn1-types-fixtures.py --check-source
python3 ../import-asn1-types-fixtures.py --verify-oracle --oracle /another/build/asn1-types-reference
```

The fixture observes every string encoding, exact raw bytes, Eq/Ord/Show,
IsString, malformed and noncanonical Unicode, partial String demand, exported
BitArray constructors, Word64 length/padding wrapping, bit reads/updates and
exceptions. The BitArray generator domain is taken from the retained original
`crypton-asn1-encoding-0.10.0/tests/Tests.hs` (0..529 byte data, 0..7 padding).
The type package itself defines no test suite. No claim is made that the full
encoding package's DER/Real properties are ported here.
