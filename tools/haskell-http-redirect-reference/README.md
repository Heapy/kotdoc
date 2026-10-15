This development-only observer calls the actual getRedirectedRequest exported
by http-client 0.7.19, linked with the frozen GHC 9.12.2 dependency plan. It does
not copy or reimplement redirection, URI, authentication or cookie algorithms.
JSON projections expose original request fields, headers, Show and cookie jars.
The previous complete CookieJar corpus anchors its source dependency hashes.

Body builders, providers and IO actions deliberately throw if demanded. Redirect
policy must preserve them without executing them, or replace them with strict
empty bytes for 302/303. Header predicates exercise both case-insensitive
HeaderName equality and access to CI.original spelling. A failing predicate
also tests early returns for non-redirect statuses, missing Location and invalid
URIs. Only ErrorCall is caught; other native failures abort the capture.

Capture includes multi-hop chains, differing original/current hosts and header
policies, return-to-original-host restoration, CI first-value deduplication,
all byte values in Location/header names, relative URI resolution, credentials,
body constructors, cookie enablement and status boundaries. All expectations
come from the original linked implementation and are independent of Kotlin.

The retained original test-nonet ClientSpec includes local socket/manager tests
for redirect history and header stripping. They require the full manager and
transport lifecycle, and remain pending; this observer does not label helper
observations as executions of those original integration tests. Disabled live
httpbin tests in test/ likewise remain recorded as pending.

Build here:

    cabal build http-redirect-reference --project-file=cabal.project --builddir=/tmp/kotdoc-http-redirect-build

Capture/check from the root:

    python3 tools/import-http-redirect-fixtures.py --oracle /path/to/http-redirect-reference
    python3 tools/import-http-redirect-fixtures.py --check-source

Production uses Kotlin only. Redirect loops, limits, history and manager/network
integration remain tracked in docs/PORTING.md.
