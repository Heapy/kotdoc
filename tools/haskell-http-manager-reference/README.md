# Original http-client Manager observer

Development-only JSONL observer of http-client 0.7.19, built with GHC 9.12.2.
Production conversion never invokes this executable or any upstream program.

The complete pinned source release and tests are copied from
`vendor/http-client-0.7.19`. The fixture importer verifies every copied byte:
only package visibility for Data.KeyedPool and export-list visibility for
connKey, mkCreateConnection and HttpExceptionContentWrapper are changed.
No implementation algorithm is extracted or rewritten in the observer.
Building the whole library preserves the actual Manager/KeyedPool type identity.

The observer provides logging raw/TLS/CONNECT factories and proxy preparation
callbacks. It invokes the original constructors, pool, parser, key comparison,
Show instances, proxy overrides, wrapping and retry predicates. Strong stable
roots keep resources alive through JSON materialization; GC is tested separately
by the existing original KeyedPool corpus. No real network endpoint is used.

From this directory, with the pinned compiler/Cabal environment available:

```
cabal build exe:http-manager-reference --offline --builddir=/tmp/kotdoc-manager-build
cabal list-bin exe:http-manager-reference --builddir=/tmp/kotdoc-manager-build
```

Pass the resulting executable to `tools/import-http-manager-fixtures.py --oracle`.
CI uses `--check-source` and consumes the committed independent expected values.
The source manifest also binds the preceding HTTP, proxy, pool and native
filesystem/environment observations to their pinned dependency sources.

These are additional original-code observations, not invented upstream unit
tests. The original local-server/httpbin integration suites, default native
socket factory and full request/history integration are still pending in Kotlin.
TLS support from http-client-tls is separate; base http-client's default TLS
connectors throw TlsNotSupported, which is tested here.
