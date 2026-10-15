This development-only observer invokes actual httpRedirect/httpRedirect' and
handleClosedRead from http-client 0.7.19 with the frozen GHC 9.12.2 plan. No
loop, body reader, exception handler or history algorithm is reimplemented.
The callback supplies scripted original Response/Request objects and body/close
effects; an event log observes callback inputs, reads and closes in order.

HttpRedirectProjection is the unchanged JSON/request projection prefix from
the preceding redirect observer, except its module name. Its exact bytes are
checked. The corpus source hashes retain all previous HTTP/URI/cookie inputs.

Cases exercise negative/zero/large limits, both callback APIs, modified/final
requests, final response ownership, partial/overshooting 1024-byte reads,
reverse-chronological exception history, EOF, exceptions before/after the flush
threshold, preclosed bodies, repeated close effects and failures at callback,
read and close boundaries. ConnectionClosed content/request wrappers and the
specific IO ResourceVanished condition are tested separately from other HTTP,
IO, ErrorCall and asynchronous exceptions. Generic platform IO exception Show
is not claimed: IO errors are projected by condition, location and error string.
Original Response derived Show, precedence and exception Show are observed for
concrete byte bodies. A latent cookie expiry checks that merely constructing
TooManyRedirects does not force its Show. Original errors during projection are
recorded separately. Long finite chains exercise callback stack/memory behavior.

The original manager/socket-based ClientSpec history/redirect tests are retained
and remain pending full manager/transport integration. This callback observer
does not label its synthetic responses as executions of those tests. Normal
responseOpenHistory bookkeeping (which reads bodies once before the loop reads
them again), bracketed manager APIs and status checks remain pending too.

Build here:

    cabal build http-loop-reference --project-file=cabal.project --builddir=/tmp/kotdoc-http-loop-build

Capture/check from the root:

    python3 tools/import-http-loop-fixtures.py --oracle /path/to/http-loop-reference
    python3 tools/import-http-loop-fixtures.py --check-source

No Haskell executable is a production dependency. Full remaining scope is in
docs/PORTING.md.
