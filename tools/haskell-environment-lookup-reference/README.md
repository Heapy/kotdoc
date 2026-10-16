# GHC environment lookup reference

The observer compiles the exact platform-selected getEnv/lookupEnv and
ioe_missingEnvVar fragments of pinned GHC 9.12.2, Windows UTF-16 conversion,
Windows throwGetLastError/errCodeToIOError/getErrorMessage, the complete original
errnoToIOError function, and the unchanged C errtable/maperrno_func. The importer
verifies these bodies against retained sources. Only imports, FFI/encoding/OS
effects, and module visibility are adapted. Windows errno constants come from
the actual retained mingw-w64 v12.0.0 errno.h; other unavailable constants have
the source unsupported value -1. The C function includes the real retained
winerror.h and errno.h, with a minimal crtdefs declaration shim for host builds.

POSIX name conversion uses the actual GHC foreign encoder in IGNORE mode;
value decoding uses the actual GHC filesystem decoder in ROUNDTRIP mode. These
are separate effects, and missing values never request a filesystem decoder.
The controlled Windows API receives original 16-bit names, returns scripted
buffer sizes/units/error codes and supplies a scoped message buffer. The
observer zeroes simulated output buffers before the fake API writes them to
keep partial scripted writes defined; production buffers follow the native API.
Its simulated LocalFree logs ownership release but does not free its parent's
scoped allocation. errnoToString is an effect returning an unused placeholder:
errCodeToIOError replaces that description with the Windows message. No error
classification, mapping, retry, trimming or getEnv algorithm is rewritten.

The fixture is portable: selected codecs are the GHC builtin UTF/ASCII/Latin1
implementations, not platform-dependent iconv aliases or localized Windows
system text. Controlled messages include malformed UTF-16 and Unicode whitespace.
The native JUnit test separately checks the real POSIX libc or Windows Kernel32
implementation, including bypassing the JVM environment cache. Windows CI
explicitly selects this class and requires no skipped test cases.

Build from this directory with GHC 9.12.2:

```sh
cabal build --builddir=/tmp/kotdoc-environment-lookup-build environment-lookup-reference
```

From the repository root, regenerate with
`python3 tools/import-environment-lookup-fixtures.py --oracle /absolute/path/to/environment-lookup-reference`.
CI uses `--check-source` without a Haskell toolchain. This observer does not
claim all GHC encoding globals, Windows CRT/RTS APIs or asynchronous masking
implemented; those remain tracked separately in the porting ledger.
