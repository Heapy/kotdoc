The complete original Network.HTTP.Proxy 0.7.19 is compiled on its **Windows CPP
branch** with GHC 9.12.2 on the observer host. Only the module/export list, CPP
selection, two import paths to the exposed Internal re-export, and environment/
Win32 effect imports change. Every algorithm byte is retained and checked.

Registry effects log open, four-byte DWORD query, subkey/default string query
and close. They inject typed values, IO errors, ErrorCall and asynchronous errors.
The original alloca/peek DWORD, bracket/catch, URI parsing, protocol selection,
auth decoding, literal bypass rules and helper priority run unchanged. This is
an effect-boundary observation, not a claim that Win32 DLLs ran on macOS.
safe 0.3.21 readDef is compiled from its original retained archive. Win32 2.14.2.1
is an explicit binding reference for the production adapter; this does not invent
a Windows Pandoc dependency plan. Its complete source/archive and full notice
are retained. Both archive hashes and all extracted bytes are verified.

Importantly, original regQueryValue uses **RegQueryValueW**, which reads a
subkey's default string. It is not RegQueryValueExW named-string lookup. The
Kotlin adapter preserves this distinction. Separate native-Windows tests use
temporary HKCU test keys to validate the real API without changing Internet
Settings. Other-platform tests validate the native-call boundary directly.

The JSON projection prefix is checked against the preceding common proxy
observer, with additional IO/async error classification. There is no original
standalone ProxySpec in the pinned package; synthetic effect observations are
not mislabeled as original units. Manager/network integration and physical OS
environment enumeration remain pending. No Haskell program is a production
conversion dependency. See docs/PORTING.md for the unchanged full scope.

Build here:

    cabal build http-windows-proxy-reference --project-file=cabal.project --builddir=/tmp/kotdoc-http-windows-proxy-build

Capture/check at the repository root:

    python3 tools/import-http-windows-proxy-fixtures.py --oracle /path/to/http-windows-proxy-reference
    python3 tools/import-http-windows-proxy-fixtures.py --check-source
