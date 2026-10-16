Development-only observer for pinned http-client-tls 0.4.0, crypton-connection
0.4.6 and tls 2.4.6, GHC 9.12.2. Full upstream snapshots/notices, the retained
freeze and selected revised Cabal bytes are included. crypton-connection's
revision 1 widens tls <2.4 to <2.5; its exact selected SHA256 is
`a3764a44de9f43b1e9f590009ebbea1401fc3911e3ba41ce4d9b93d9f2a8d22e`.
The actual Cabal plan selects that revision and original tls 2.4.6.

OriginalHttpTls retains the entire original module, including digest parsing,
MD5 inputs, SOCKS selection, factory/proxy/cleanup behavior, exception policies,
newTlsManagerWith field preservation and global CAFs. Only its module export
header and imports change. Network.Connection's context/socket/secure operations,
environment, default raw factory, manager-construction observation and digest's
httpNoBody probe are explicit effects. ConnectionEffects reuses actual original
ConnectionParams/ProxySettings/TLSSettings and exceptions from crypton-connection.
It supplies opaque connection/context tokens; it does not implement any expected
manager, digest, SOCKS or cleanup algorithm. The probe constructs a scripted
original Response (); Kotlin native HTTP tests separately exercise real probing.
HttpRedirectProjection is copied byte-for-byte from the preceding loop observer.

OriginalTlsError is the complete original tls Error.hs; only module/import names
change (the file needs Word8 from its broad Imports module). Original Eq/Show and
alert pattern matching execute unchanged. No expected names, byte rendering,
exception classification, digest parser/formula or SOCKS parser is rewritten in
this observer. The importer verifies both full module transformations and every
file in all four original source archives, plus Cabal revisions and the full
preceding fixture/source chain.

Each observation runs in a fresh original process, so genuine unsafePerformIO
CAF initialization is isolated between programs. Multiple operations inside a
program test actual sharing, setGlobalManager's initial manager creation and
memoized synchronous initialization failures. Parameter snapshots deliberately
force the context and SOCKS fields at the selected connection effect boundary;
a preceding connector failure leaves them unforced. The incompatible SOCKS/HTTP
CONNECT error is observed in the original connectionUseSocks thunk.

The real upstream test/Spec.hs remains intact: its live httpbin/badssl TLS tests
are not claimed complete by controlled adapter observations. Kotlin has not
substituted JSSE for the original TLS handshake, cipher, certificate validation,
session or SOCKS protocol implementations. HttpTlsManagers requires an explicit
Network.Connection backend; none is replaced with a missing-transport stub.
The current Pandoc ResourceContext JDK HTTP seam remains pending integration
when the native TLS/connection stack is complete. Full goal: docs/PORTING.md.

Build in this directory:

    cabal build --builddir=/tmp/kotdoc-http-tls-build

Capture/check from repository root:

    python3 tools/import-http-tls-fixtures.py --oracle /path/to/http-tls-reference
    python3 tools/import-http-tls-fixtures.py --check-source
