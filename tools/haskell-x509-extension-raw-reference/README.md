# X509 raw extension reference

Development-only GHC 9.12.2 observer. It compiles the unchanged ExtensionRaw,
Ext, DistinguishedName and Internal X509 modules, 17 ASN1 modules (including
Parse), and 12 hourglass modules from the pinned releases. JSON adapters and
observation code do not substitute an extension codec or ASN1 implementation.

OriginalExtensionsTests is an exact checked extraction of the original
Extensions, ExtKeyUsageFlag and ExtKeyUsage generators plus the generic
property_unmarshall_marshall_id body from crypton-x509 Tests.hs, with only
module header/imports adapted. It invokes the same `Extensions -> Bool`
property as the original named `extensions` test. The original Ext module
supplies typed key-usage encoding inside the original generator. This does not
claim that the Kotlin typed-extension codecs or their separate tests are
already ported; this checkpoint tests the raw records/container.

```
cabal build exe:x509-extension-raw-reference
cabal run exe:x509-extension-raw-reference -- --original-test
```

The latter runs 1000 cases with replay seed 1245978545. `--generate-tests 1000`
exports actual original generator inputs (seed 1245978545+i, size i mod 100).
The importer invokes the original property for each expected result and can
independently regenerate/verify the inputs with another GHC build.

```
python3 tools/import-x509-extension-raw-fixtures.py --oracle /path/to/x509-extension-raw-reference
python3 tools/import-x509-extension-raw-fixtures.py --verify-oracle --oracle /path/to/x509-extension-raw-reference
python3 tools/import-x509-extension-raw-fixtures.py --check-source
```

Additional observations cover raw Eq/Show and field order, all byte values,
absent versus present-empty Extensions, omitted False/explicit True, full DER,
optional container parsing, suffix-specific errors and remaining input, every
position mutation/truncation, constructor/field/list/prefix demand, failing
tails, BER Left versus lazy typed errors, and deprecated extRawASN1 exceptions.
Decoded BER values are observed through upstream ASN1 Show; IEEE bit-profile
observations remain in the preceding typed-ASN1 corpus.

The bridge forces serialized observations inside its exception handler. An
exception's message can itself throw when it embeds an unevaluated OID; the
handler also evaluates that message under capture, observing the eventual
message failure instead of crashing the whole batch. This affects observation
only; no upstream conversion/error-construction code is changed.
