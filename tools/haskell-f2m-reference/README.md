# crypton binary polynomial reference

This development-only executable links the complete pinned crypton 2.0.1
package. Production uses only Kotlin/JVM arithmetic. The upstream package notice
is retained in `licenses/crypton-BSD-3-Clause.txt` and distributed in the core JAR.

`--original --seed 1729 --qc-max-success 200` executes the original
`tests/Number/F2mSpec.hs`, `Imports.hs` and `Utils.hs` directly from vendor,
without changing their bodies or substituting their test framework. Hspec is
pinned in cabal.project.freeze. The original suite contains 29 active properties;
its general-modulus square-root property is commented out upstream and remains
disabled. Its fixed GF(2^16) property checks all 65,536 elements.

`tools/extract-f2m-properties.py` mechanically extracts every active expression
into `OriginalF2mProperties.hs`, preserving each expression and its generators'
Positive/NonNegative wrappers. The fixture verifier checks the generated module
byte for byte. JSON requests can evaluate these exact expressions on concrete
inputs, or observe individual public arithmetic functions, including partial
argument demand and the constructor/payload boundary of Maybe Integer.

With GHC 9.12.2 and cabal available, from this folder:

```
cabal build --builddir=/path/to/reference-build f2m-reference
cabal list-bin --builddir=/path/to/reference-build f2m-reference
```

Use the resulting binary as `ORACLE` from the repository root:

```
"$ORACLE" --original --seed 1729 --qc-max-success 200
python3 tools/import-f2m-fixtures.py --oracle "$ORACLE"
python3 tools/import-f2m-fixtures.py --oracle "$ORACLE" --verify-oracle
python3 tools/import-f2m-fixtures.py --check-source
```

The corpus independently records every GF(2^16) square root, all 29 property
expressions, signed XOR, zero/one moduli, unreduced inputs, dense and sparse
polynomials through 2048 degrees, nonunits, quadratic trace, exception ordering,
ignored arguments and deferred Maybe values. Upstream sqrt at degree-zero
moduli and negative-argument polynomial GCD can fail to terminate; these are
excluded from finite observation batches, not assigned invented finite results.
The source does not provide timing protection; no constant-time guarantee or
complete crypton/X509 validation port is claimed.

The corpus also includes sparse and dense exponents through 16,385 bits. Kotlin
uses an explicit exponent stack to avoid imposing the JVM call-stack limit.
Strict argument entry in the pinned GHC build is observed independently: the
standalone multiplication worker and multiplication mapped under Maybe do not
have the same exception ordering. Both boundaries are preserved and exercised.
