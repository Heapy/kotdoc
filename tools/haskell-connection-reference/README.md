# crypton-connection reference

This development-only observer builds the complete pinned Network.Connection
and Network.Connection.Types from crypton-connection 0.4.6. The importer verifies
both original files byte-for-byte after the following visibility/import edits:

* Rename the modules and expose the original private functions.
* Import the renamed Types module.
* Route Network.TLS to ConnectionTlsEffects. Its parameter, session and other
  types are re-exported from the pinned real tls 2.4.6 package. Only context
  allocation, handshake, receive, send and close are controlled effects.

The buffer, line-reading, EOF, modification rollback, backend lifecycle and
session algorithms are unchanged. Real binary temporary files supply the Stream
backend; the observer reads the original buffer MVar after each operation.
The TLS effects record parameters/order, return scripted chunks and inject IO
and non-IO failures. These are not real TLS protocol tests. Original session
values are lazy exception sentinels: forcing a resumed value identifies it while
the observer leaves session lookup/insert/delete entirely to the original code.

`connectionGetLine` on the first empty backend read returns an empty line,
despite the source API comment saying it throws EOF. The next call throws the
structured EOF. The fixtures preserve the implementation's actual behavior.

Build with GHC 9.12.2 and the retained freeze:

```sh
cd tools/haskell-connection-reference
cabal build --builddir=/tmp/kotdoc-connection-build connection-reference
```

From the repository root, regenerate with
`python3 tools/import-connection-fixtures.py --oracle /absolute/path/to/connection-reference`.
CI uses `--check-source` without needing Haskell or a TLS server.

There is no test-suite stanza in the original crypton-connection release.
These independent observations supplement the retained source; they do not
prove the unported TLS protocol, certificate policy, SOCKS, resolver or full GHC
asynchronous exception/timeout runtime.
