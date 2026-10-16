# GHC encoding globals reference

OriginalEncodingGlobals compiles the unchanged three GHC 9.12.2 mkGlobal
bindings and mkGlobal implementation, parameterizing only their initial values
so each observation has isolated IORefs. OriginalInitialEncoding compiles the
unchanged POSIX mkTextEncoding' dispatch/validation and unknownEncodingErr.
The importer verifies exact source bodies, including platform selection and
argv branches. The observer uses ordinary Haskell thunks/unsafePerformIO for
controlled initial CAF effects; it does not implement thunk evaluation, caching,
IORef state, codecs or exception recovery itself. A lazy Map stores snapshots
without forcing them. Values are forced inside each operation's exception
boundary, including charIsRepresentable's outer encoding bang pattern.

The native branch uses actual GHC process-wide encoding getters/setters,
System.Environment, Foreign.C.String and Foreign.C.Error functions and a retained C environ
address bridge. It replaces/restores only its process's native environment,
and restores encoding refs after each observation. Native JUnit observations
run in an isolated child JVM, never altering the parent worker's state.

The corpus uses portable GHC builtin codecs. Native POSIX effects are excluded
from the Windows selection; the separate Windows native test verifies that
wide environment APIs and argv encoding bypass poisoned encoding globals.
Windows initial code-page codec construction is not covered by this POSIX
initializer observer and remains an explicit backend gap. CAF tests cover
synchronous exceptions and controlled thread handoff, not complete RTS async
masking, black-hole recursion or weak-memory IORef semantics.

Build with GHC 9.12.2 in this directory:

```sh
cabal build --builddir=/tmp/kotdoc-encoding-globals-build encoding-globals-reference
```

Regenerate from the repository root:

```sh
python3 tools/import-encoding-globals-fixtures.py --oracle /absolute/path/to/encoding-globals-reference
```

CI runs `--check-source` without a Haskell toolchain. The native observations
must independently agree with a Linux build of this same observer.
