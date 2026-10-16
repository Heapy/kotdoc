# ASN1 Pretty and event-demand reference

Development-only observer. It compiles the unchanged `Data.ASN1.Pretty` and
`Data.ASN1.Prim` from the pinned releases, with seven unchanged base16 1.0
modules and the existing hourglass sources. No formatting or encoding algorithm
is reimplemented in the bridge. The JSON value adapters reuse the preceding
typed reference's request schema. Exceptions are observed at the first message
line; results are code points rather than locale-dependent encoded text.

The original base16 release's old Cabal bounds are not modified. Its modules
are compiled directly by this observer with the frozen dependency versions.
`MIN_VERSION_base16(a,b,c)=1` selects the upstream >=1.0 branch in Pretty.
Numeric.hs from GHC tag `ghc-9.12.2-release`, under
`libraries/ghc-internal/src/GHC/Internal/Numeric.hs`, is retained verbatim in `vendor/ghc-asn1-pretty-9.12.2` for
showHex's negative-integer error. Its source and full license are checked by
the importer, while the observer uses the pinned GHC's actual Numeric module.

Build with GHC 9.12.2:

```
cabal build exe:asn1-pretty-reference
```

Use the resulting executable with:

```
python3 tools/import-asn1-pretty-fixtures.py --oracle /path/to/asn1-pretty-reference
python3 tools/import-asn1-pretty-fixtures.py --verify-oracle --oracle /path/to/asn1-pretty-reference
python3 tools/import-asn1-pretty-fixtures.py --check-source
```

The corpus covers both modes, all ASN1 constructors, all byte values in all
12 string encodings, raw/hex ByteString display, Double display, time values,
negative/overflowing indentation, unbalanced markers, failing DateTime fields
and input tails, and independent encoded length/list-spine/event/header demand.
It does not claim to port the separate base16 decoding APIs or original base16
tests, nor every possible nested bottom in a Haskell value.
