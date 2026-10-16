# Original native network IO reference

Development-only observer. Production uses Kotlin and native OS calls, never
this executable. Requires GHC 9.12.2 (including patch version), network 3.2.9.0
and the retained exact freeze. Build in an external directory:

```
cabal build exe:network-io-reference --offline --builddir=/tmp/kotdoc-network-io-build
cabal list-bin exe:network-io-reference --builddir=/tmp/kotdoc-network-io-build
```

The entire original network release is copied byte for byte; only Options is
exposed in network.cabal. Its algorithms and tests are unchanged. The importer
checks both that change and the exact file set. OriginalSend retains the complete
original send, waitWhen0 and sendAll declarations/bodies verbatim, replacing only
sendBuf and readiness effects. Its input-byte trace records every remaining
ByteString view and zero-write wait; no expected send loop is rewritten.
GHC's actual errno guards execute with scripted C errno/results. The observer
also invokes actual network socket creation, options, invalid-descriptor errors
and TCP send/receive/half-close. Native constants/layouts and option pairs are
captured from actual system headers and original compiled SocketOption values.

The two native platforms have separate observations: Darwin/aarch64 and
Linux/x86_64 glibc. Family values, errno values/descriptions, O_NONBLOCK and
socket options are host dependent. Actual TCP observations omit ephemeral
ports and packet boundaries, retaining both complete payload directions.
Kotlin tests use a Java TCP peer to independently exercise its native transport.
The ten translated ByteStringSpec TCP units keep the original literal message
and assertions, separately from the generated observation groups.

Capture with `tools/import-network-io-fixtures.py --platform macos-64`
(or linux-64) `--oracle /path/to/executable`. Streaming wrappers may invoke the
same original binary on a Linux container. `--check-source` checks source,
extraction, all observations and ABI bytes. Expectations never come from Kotlin.
The selected GHC Posix.Internals source and full notice are retained separately.

This is not the whole network suite or GHC runtime. Windows/Winsock, other ABIs,
accept, datagram/vectored/message IO, all socket option value types, gracefulClose,
name-info/interface APIs, exact GHC event-manager/masking/signal/GC semantics and
the entire original local-server/httpbin suites remain pending. The JVM uses
interruptible bounded poll waits as its readiness boundary, not GHC's event
manager. The HTTP ByteString representation still has JVM array-size limits.
TLS and the full Pandoc/calibre port remain required.

Two retained direct threadWait(-1) observations are explicitly pending in the
Kotlin suite. On the pinned original runtime, Darwin reports kevent errors
(including a different errno after the preceding failed registration), while
Linux reports modifyFdOnce. The current JVM poll boundary rejects an invalid
descriptor with threadWait/EBADF. These are not asserted equivalent or removed
from the required port. The selected original Event.Thread/Manager sources are
retained for completing this runtime behavior.
