# Original ASN.1 values, streams and ParseASN1

This observer compiles seven unchanged crypton-asn1-types/crypton-asn1-parse
modules from their retained package trees. The original time-hourglass 0.3.0
library is built as a local package, including its unmodified platform source
and C support. No replacement Data.Hourglass module is used.

```sh
cabal build asn1-values-reference
python3 ../import-asn1-values-fixtures.py --oracle /absolute/path/to/asn1-values-reference
python3 ../import-asn1-values-fixtures.py --check-source
python3 ../import-asn1-values-fixtures.py --verify-oracle --oracle /independent/build/asn1-values-reference
```

GHC 9.12.2 and the pinned freeze are required. The bridge interprets typed JSON
values and parser programs, projects original results, and observes errors.
Its Witness ASN1Object instance is a test dictionary used to observe the
original getObject/delegation and ASN1S contracts; it is not an X.509 codec.
Expected parsing/container algorithms come only from original modules.

The fixture covers finite exported ASN1 constructors/Eq/Show, hourglass model
Eq/Ord/Show and offset conversion, both container extraction functions, every
ParseASN1 combinator, backtracking, state/full runs, unmatched/mismatched ends,
and deferred result/function/argument errors. Parse failures retain raw Haskell
String Chars, including surrogate pairs distinct from supplementary Chars;
both raw points and the original Aeson text view are observed.
The two original ASN.1 packages
have no standalone test suites. Full hourglass tests are retained but not
claimed ported: calendar, native clock/epoch, formatting, numeric/Read APIs and
other hourglass modules remain outside this model checkpoint. BER/DER, date
parsing and X.509 are subsequent layers.
