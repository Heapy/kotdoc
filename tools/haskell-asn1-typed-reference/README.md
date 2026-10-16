# Original ASN1 primitive and BER/DER observer

Development-only GHC 9.12.2 / ghc-bignum 1.3 GMP observer. Production conversion
is Kotlin/JVM; no upstream executable, Python, GMP or Haskell library is a
runtime conversion dependency.

Build `cabal build all`. The four executables are `asn1-typed-reference`,
`asn1-typed-posix-model`, `asn1-typed-windows-model`, and `original-asn1-tests`.
The last compiles and runs the original Tests.hs directly from vendor:

```
original-asn1-tests --quickcheck-tests=1000 --quickcheck-replay=1095978545
asn1-typed-reference --generate-tests 1000
python3 tools/import-asn1-typed-fixtures.py --oracle-dir /path/to/AArch64/executables --secondary-oracle-dir /path/to/x86_64/executables
python3 tools/import-asn1-typed-fixtures.py --check-source
python3 tools/import-asn1-typed-fixtures.py --oracle-dir /path/to/independent/executables --verify-oracle
```

Python commands run from the repository root. The reference directly compiles
the retained ASN1 and hourglass implementations; the two model executables
reuse the previous original-platform/native-boundary setup unchanged.

`UpstreamTests.hs` is a mechanically checked module adapter: original Tests.hs
is preserved in vendor, and the adapter only normalizes line endings and adds
an explicit module header exposing its original generators and properties.
The importer verifies that exact transformation. It changes no test function,
generator, expected result or arithmetic. Deterministic seeds and sizes produce
1,000 actual original cases for each of Header, Event, DER, Real and Integral
Real. Each expected output invokes the corresponding original property.
Independent verification regenerates and compares all 5,000 input cases too.

Extra observations cover exported primitives, all tags/encodings, exact bytes,
DER lengths, malformed raw streams, Right/lazy typed exceptions versus eager
Left raw errors, independent encoded lengths/events, incomplete construction
markers, streaming prefixes and 1,000-level nesting. ASN1 Time cases preserve
the original fallback format, 2050 UTC adjustment, three decimal digits,
permissive manual timezone suffix and ignored fractions on output. They do not
mistake the parsed offset for an instruction to normalize the ASN1 date.

`encodeFloat` probes invoke actual GHC integerEncodeDouble and the actual
compiled Native backend. Retained ghc-bignum source explains both algorithms:
small signed Int conversion, the minBound abs anomaly, GMP truncation and
wrapped C exponent, and Native limb accumulation. This is only the needed
conversion path, not a port of the whole ghc-bignum package.

The pinned AArch64 and x86_64 GHC builds return positive and negative quiet NaN
respectively for 0/0. All 37 affected observations retain both raw bit patterns
as independently observed source profiles. Verification selects the running
oracle's profile; Kotlin runs both profiles. The production default selects
the corresponding architecture profile, and callers can choose it explicitly.
No NaN bit normalization is used to make cross-platform comparisons pass.

The importer binds complete ASN1/hourglass/Win32 archives, the retained
ghc-bignum source subset and RTS/Float files, full notices, all observer/model
sources and itself. These checks do not prove X509/TLS or the whole Pandoc/MOBI
objective complete. Pretty and remaining hourglass APIs/properties and deeper
composition/stream-demand coverage remain separate work.
