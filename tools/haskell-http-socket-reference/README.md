# Original socket orchestration observer

Development-only observer; never used by production conversion. GHC 9.12.2
(including patch level) is enforced, with the complete dependency freeze.

OriginalSocketConnection.hs is the entire pinned http-client 0.7.19 Connection
module. Only its module/export list and imports change. The fixture importer
verifies that all implementation bytes remain original. The two Manager raw
factory declarations/bodies are extracted verbatim into OriginalSocketFactories.
The observer uses the actual async 2.2.6 implementation; it does not reimplement
firstSuccessful, bracketOnError, withAsync or forConcurrently.

SocketEffects substitutes the Network.Socket, byte I/O and delay boundaries.
No native socket ABI or Internet endpoint is involved. Lookup verifies the
original defaultHints{addrSocketType=Stream}; real network AddrInfo/SockAddr/
PortNumber types, strictness, Enum and Show are retained. Effects are recorded
before injected failures, with JSON fields materialized at the effect boundary.

For concurrency, entered/permit gates let the driver choose completion order
without guessing wall-clock scheduling. Delay arguments stay 250000*n. Each
address keeps its full ordered trace, and traces are grouped by address instead
of imposing an unrelated global order on concurrent attempts. The driver waits
for explicitly requested boundaries, then releases attempts or cancels the
parent. The original structured scopes join cancellation before capture ends.
Non-IO child failures are observed during a bounded wait, followed by external
cancellation; the observer does not change the source into error propagation.
GHC's complete GC/deadlock-detection runtime is not claimed ported to the JVM.

Use an external build directory, then capture with the generated executable:

```
cabal build exe:http-socket-reference --offline --builddir=/tmp/kotdoc-socket-build
cabal list-bin exe:http-socket-reference --builddir=/tmp/kotdoc-socket-build
```

Pass the executable to `tools/import-http-socket-fixtures.py --oracle`.
`--check-source` verifies committed observations, original module extraction,
retained network/async release files, notices and preceding source manifests.
These additional observations do not replace the original upstream integration
suites. Native socket calls, exact syscall error mapping, TLS and complete live
request/history integration remain pending.
