# Native network address and GHC C-string reference

Development-only observer; production conversion does not invoke it. Requires
GHC 9.12.2 including its patch version, network 3.2.9.0 and the retained freeze.

The full original network source release is copied without algorithm changes.
Only network.cabal's module visibility changes: Info and Types are exposed for
observing their actual implementations. The importer checks every source/test
file against vendor. SocketPatterns contains the original two pattern tables
verbatim. GhcErrnoPatterns enumerates the guards in the original pinned GHC
errnoToIOError in their original order, preserving aliases and unavailable -1
constants. Original constants, type instances, marshalling, getaddrinfo,
followAddrInfo, C-string codecs, error construction and error rendering execute
in the pinned native libraries; no expected conversion algorithm is rewritten.

Native header layouts and named constants are captured separately on
macOS/aarch64 and Linux/x86_64 glibc. The .hsc observer records actual struct
sizes/offsets, the source Storable alignment and CSize zeroing width, address
family/type aliases and AI flag mappings. It observes real numeric getaddrinfo
and GHC errnoToIOError calls. Those expectations belong to their native host;
Kotlin separately tests controlled address memory against both retained ABIs.

Foreign.C.String uses GHC's initial IgnoreCodingFailure (despite the older
API comment describing transliteration), independently of filesystem roundtrip
encoding. Codec expectations include every Haskell Char, surrogate slots,
stateful native iconv, empty input, C NUL and withCStringLen/Len0 buffer growth.
Large Char ranges retain independent original-output lengths and SHA256.
All two-byte strings are additionally decoded in 256-string groups per codec.

Error observations project raw IOError record fields, including ioe_description
and errno. ioeGetErrorString is not the same as the raw description for non-user
errors. The initial observer used that accessor; its correction changed only
the description field in 205 original observations, leaving all Show values and
other expectations unchanged. Expectations never come from Kotlin.

Build outside the checkout:

```
cabal build exe:network-address-reference --offline --builddir=/tmp/kotdoc-network-build
cabal list-bin exe:network-address-reference --builddir=/tmp/kotdoc-network-build
```

Capture with `tools/import-network-address-fixtures.py --platform macos-64`
(or linux-64) `--oracle /path/to/executable`. The generator batches JSONL input
so a native Linux observer can also run through a streaming SSH/container wrapper.
The Linux build uses the same freeze and exact Hackage Cabal revisions selected
by the macOS plan; only normal platform constants/dependencies differ.
`--check-source` checks observations, ABI assets, retained source and extraction.

Native Windows/Winsock/code-page initialization, full mutable GHC encoding and
raw out-of-Unicode String integration, other ABIs, network Read instances,
getNameInfo, socket syscalls/polling/IO/cancellation, TLS and the original complete
local-server/httpbin suites remain pending. These observations do not claim to
be the whole upstream suite or a complete network port.
