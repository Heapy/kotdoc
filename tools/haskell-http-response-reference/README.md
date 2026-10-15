The observer links actual http-client 0.7.19, streaming-commons 0.2.3.1 and the
same frozen GHC 9.12.2 dependency set as the complete original Pandoc build.
Production conversion never invokes this observer or Haskell.

OriginalHeadersSpec, OriginalBodySpec, OriginalResponseSpec and
OriginalConnectionSpec preserve every original action, literal assertion and
predicate. Only their module names change. The assertion runner executes all
26 tests before capture. The Kotlin suite separately translates every test,
including their original body chunks, response fields, unread input, Expect
writes, early hint callbacks and EOF assertions. Original gzip inputs come
from exactly the original GZip.compress expressions, not Kotlin compression.

Additional observations call the actual original connection, header, body and
streaming inflater APIs. OriginalResponse.hs retains the full release module,
changing only module/import packaging to use the public Internal re-exports
and an injected pool boundary (ObserverManaged). It records release Reuse /
DontReuse and keepAlive calls; no response algorithm is rewritten. The original
ResponseSpec still links the actual unmodified release implementation and pool.
The importer checks these transformations and hashes all retained inputs.

Connection traces include reads, unreads, writes, close, continuation, early
hints, release and keepAlive, with remaining raw input and ordered outputs.
Body operations preserve individual chunks, partial reads, repeated EOF/errors
and callbacks. Timeout witnesses cover zero, negative, successful deadlines,
an interrupted blocked read and the Expect callback outside the timeout scope.
No duration or scheduler timing is compared; the delayed actions have explicit
one-second deadlines and longer delays.

Direct inflater observations retain feed/pop/flush/finish/unused/completion
state, dictionaries, malformed/truncated input and 32,752-byte output buffers.
Kotlin translates five original streaming-commons inflater unit assertions:
single/multi license inflation, different dictionary, large raw decompression
and unused input. LICENSE and test/LICENSE.gz retain exact original bytes in
test resources. The full deflater/property and other streaming-commons tests
remain in scope and are not claimed ported here.

Build from this directory with the pinned compiler:

    cabal build http-response-reference --project-file=cabal.project --builddir=/tmp/kotdoc-http-response-build

From the repository root:

    python3 tools/import-http-response-fixtures.py --oracle /path/to/http-response-reference
    python3 tools/import-http-response-fixtures.py --check-source

The JVM inflater uses native zlib through JNA, preserving the original C return
codes and stream behavior. Linux/macOS are checked independently. Complete
Windows zlib distribution/loading, generic deflate, full connection pooling,
socket/TLS/proxy effects, redirects, cookies and the remaining HTTP exception
model are subsequent work; this does not claim complete HTTP transport parity.
