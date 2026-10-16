Development-only original http-client 0.7.19 execution observer, GHC 9.12.2.

OriginalExecution retains the entire pinned Core.hs, changing only its module
header/imports to use the package's exported Internal facade and explicit clock,
connection-acquisition timer and response-header timer effects. It appends the
original HistoriedResponse/responseOpenHistory/withResponseHistory declarations
from Client.hs; only the unused deriving list is reduced to Show. Every function
body is byte-for-byte original. The importer reconstructs and checks these bytes.
The actual original Manager, KeyedPool, Request, Response, Cookies, headers and
body readers come from the preceding visibility-only package copy. Its full
source/test snapshot, notices, freeze and preceding fixture chain stay hashed.
HttpRedirectProjection is copied unchanged from the original loop observer.

ExecutionEffects supplies deterministic getCurrentTime, timeout and header-timer
boundaries. It does not implement timeout selection, elapsed-time subtraction,
rounding, retry, redirect/history, cookie or status-check behavior. The header
timer observes the exact remaining budget and calls the original getResponse
with an unlimited timer so synthetic small budgets do not depend on scheduling.
Scripted raw/TLS connectors use original makeConnection. Their read/write/close
logs, manager hooks, request/response snapshots and original exception Show form
independent expectations. Actual native timeout/cancellation behavior is tested
separately by the Kotlin local-server/late-connector integration suite.

The corpus covers all nine execution APIs, actual pool reuse, retry only for
reused/direct exception types, two managers and override/mutation ordering,
negative/zero redirect counts, ordinary versus history behavior, chronological
history versus reverse exception history, double redirect-body reads, status
checks and brackets, cookies/Set-Cookie retention, header validation and exact
picosecond/bankers-rounding timeout budgets. Added ClientSpec-derived scenarios
exercise header stripping and cookie propagation across same/different hosts.
These synthetic observations are not labeled as the complete original test suite.

The source withConnection expression parses as action <* (keepAlive `finally`
release); an action failure relies on Managed's finalizer. The observer exposes
this and Kotlin retains that behavior. It does not claim a stronger bracket.
The original requestAction mutation hook is retained on each HttpExecution
instance, including the shared system instance; no global Haskell IORef is used
in production. Full GHC mask/throwTo semantics and all original local-server/
httpbin tests, TLS and Windows transports remain unfinished.

Build in this directory:

    cabal build --builddir=/tmp/kotdoc-http-execution-build

Capture/check from repository root:

    python3 tools/import-http-execution-fixtures.py --oracle /path/to/http-execution-reference
    python3 tools/import-http-execution-fixtures.py --check-source

No upstream executable is a production dependency. Full scope: docs/PORTING.md.
