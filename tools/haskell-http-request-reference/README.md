This observer links the actual frozen http-client 0.7.19 and http-types 0.12.6
used by the complete original Pandoc 3.12.1 build (GHC 9.12.2, 64-bit).
Production Kotlin does not invoke this observer or any Haskell executable.

OriginalRequestSpec.hs and OriginalRequestBodySpec.hs retain every original
test body, literal expectation and predicate. Only their module names change.
Test/Hspec.hs executes the original IO actions, Eq assertions, predicates and
shouldReturn assertions, and records names only after success. All 26 original
tests must pass before capture. The Kotlin suite translates all 26 tests with
their original literal values, including the five request body forms, both
Expect modes, and the 75,000-byte file verifier. No production result supplies
an expected value.

The JSON protocol calls actual original parseRequest, parseUrlThrow,
requestFromURI, setUri/setUriRelative, getUri, authentication/proxy/query helpers,
needsGunzip, Request Show and requestBuilder. checkResponse is invoked on a
synthetic 404 response to observe its policy. This dependency observer opens no
network connections. Byte fields and Haskell Char code points use separate
arrays, preserving arbitrary bytes and surrogate Chars.

Wire observations preserve individual writes (including empty Blaze buffers),
the Expect continuation, and bytes written before/after it. Fault probes
distinguish IO/runtime popper exceptions from connection-write failures; the
actual upstream onRequestBodyException callback records its invocations.
The file observer records progress including EOF, reuses the same body three
times, and observes files changed after size capture. Temporary paths are kept
outside results; temporary observer files are closed and removed.

Build from this directory with the pinned compiler and frozen dependencies:

    cabal build http-request-reference --project-file=cabal.project --builddir=/tmp/kotdoc-http-request-build

From the repository root, capture using the executable printed by Cabal:

    python3 tools/import-http-request-fixtures.py --oracle /path/to/http-request-reference
    python3 tools/import-http-request-fixtures.py --check-source

The importer verifies unchanged original test bodies and hashes the retained
releases, tests, observer, frozen project and pinned Pandoc HTTP caller. Response
parsing, redirects, cookies, connections, TLS/proxy execution, exception/status
response wrapping and the other original http-client suites remain subsequent
transport work. checkStatus currently records the two stock policies; it does
not implement arbitrary response callbacks or claim network parity.
RequestBody.Builder currently exposes byte-producing builders. Complete generic
Blaze/bytestring builder APIs, lazy/partial/infinite values, RequestBody Monoid
and remaining non-wire Request fields are not claimed complete.
