# GHC TextEncoding reference

This observer calls the actual GHC 9.12.2 `mkTextEncoding`, builtin and iconv
codecs, `char8`, `peekCStringLen`, `withCStringLen`, `withCStringLen0` and
`charIsRepresentable`. It does not reimplement codec/recovery algorithms.
Each batch item gets fresh codec state; exceptions retain the original IOError
record. Every result is forced inside its exception boundary. The build checks
the exact compiler version. The unmodified upstream encoding005 test is also
compiled and must reproduce its retained stdout before generating fixtures.

The original encoding implementation and notices remain pinned under
vendor/ghc-environment-9.12.2; encoding005 and its test registration are retained
under vendor/ghc-text-encoding-9.12.2 at the same GHC commit. Native iconv cases
have separate Darwin and Linux observations. Windows runs only the portable
builtin cases. Windows code-page encoding is a separate, still unported backend.
This suite exercises C-string state/retry behavior, not the complete streaming
BufferCodec API, global encoding refs, file handles or RTS asynchronous effects.

Build from this directory with GHC 9.12.2:

```sh
cabal build --builddir=/tmp/kotdoc-text-encoding-build all
```

Generate from the repository root with:

```sh
python3 tools/import-text-encoding-fixtures.py --platform macos-aarch64 \
  --oracle /absolute/path/to/text-encoding-reference \
  --original-test /absolute/path/to/original-encoding005
```

Use `--platform linux-x86_64` with binaries built on Linux for its native iconv
results. CI verifies both retained corpora with `--check-source` without GHC.
