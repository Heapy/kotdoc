This development-only observer compiles the complete pinned Network.HTTP.Proxy
0.7.19 module on the non-Windows CPP branch. Only its module name, two import
paths (to the package's exposed Internal re-export) and the getEnvironment import
are changed. The complete algorithm is unchanged and checked byte for byte.
The environment effect returns an ordered list with duplicate keys, allowing
observations impossible to express through many host environment APIs.

The GHC 9.12.2 plan is frozen, including text 2.1.2, network-uri 2.6.4.2 and the
original http-client archive. No parsing, lowercase, suffix or selection logic
is duplicated in the observer. Expected settings/request/error Show comes from
the original types. Each host is observed separately so partial ByteString.head
failures do not prevent subsequent calls. JSON is forced inside the exception
boundary. Input Strings use code-point lists to retain surrogate Chars before
the original Data.Text.pack replacement.

Coverage includes exact-key-first/folded-key-last lookup, full text lowercase
expansion, custom names, empty variables, machine-Int decimal overflow, URI and
credential parsing, Char8 truncation, simple character lowercase for no_proxy,
domain boundaries, literal wildcards, leading-only ASCII spaces, short-circuit
partial failures, fallback policy, repeated application and duplicate auth
headers. Changing the injected environment after helper creation checks that
configuration is captured once. No standalone original ProxySpec exists in the
pinned release; these are independent observations, not upstream unit tests.

Windows registry lookup/selection, actual Manager construction and socket
integration remain pending. This checkpoint does not claim them or complete
Pandoc/calibre parity. No Haskell executable is a production dependency.

Build in this directory:

    cabal build http-proxy-reference --project-file=cabal.project --builddir=/tmp/kotdoc-http-proxy-build

Capture/check from the repository root:

    python3 tools/import-http-proxy-fixtures.py --oracle /path/to/http-proxy-reference
    python3 tools/import-http-proxy-fixtures.py --check-source
