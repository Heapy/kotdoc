This development-only observer links the actual frozen http-client 0.7.19,
cookie 0.5.1, iproute 1.7.15 and time 1.14 implementations with GHC 9.12.2.
No cookie, IP, sorting, time, request or response algorithm is rewritten here.
It projects JSON inputs/results around original public Internal APIs.

OriginalCookieSpec is byte-identical to the complete release test except for
its module name. The small assertion runner executes all 15 original actions,
literal expectations and predicates before recording their names. The Kotlin
suite separately translates all 15 units. Finite extra observations cover the
actual Cookie/CookieJar functions, original Show text, unlawful comparators,
prefix equality, first-only removal, header replacement/stripping and response
body/original-request/cookie-jar preservation.

The IP projection calls actual readMaybe for IPv4/IPv6, separately from the
cookie-specific isIpAddress algorithm and trustworthy-origin check. In
particular, pure IPv6 and IPv4-embedded IPv6 have different trailing-whitespace
behavior. Sorting observations include dense ties, duplicates, natural runs
and merge arities; production reuses the existing faithful GHC sort port.
The actual appar parser advances alternatives from the consumed failure
position unless try resets it. Used combinators and IP branches are ported
directly, with exhaustive short numeric/colon inputs and long-hex failures.

Generated cookies are also observed field-by-field. Empty Max-Age has a Just
constructor whose number is undefined: presence, differing-Maybe equality,
persistence, equivalence and header generation must not spuriously demand it.
Expiry, full equality, Show and eviction must still produce the original
ErrorCall. The observer catches only ErrorCall, retaining its message; other
unexpected failures abort capture.

Build from this directory:

    cabal build http-cookie-reference --project-file=cabal.project --builddir=/tmp/kotdoc-http-cookie-build

Capture/check from the root:

    python3 tools/import-http-cookie-fixtures.py --oracle /path/to/http-cookie-reference
    python3 tools/import-http-cookie-fixtures.py --check-source

The whole original iproute/appar releases and notices are retained. Only the IPv4/IPv6
Read paths used by cookies are specialized here; full Data.IP route/range/
builder APIs and remaining tests, derived Read for Cookie/CookieJar and generic
lazy runtime behavior are not claimed complete. Real connection transport,
manager cookie effects and redirects remain work under docs/PORTING.md.
Neither an oracle nor Haskell is a production dependency.
