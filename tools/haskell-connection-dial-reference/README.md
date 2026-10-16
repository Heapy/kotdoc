# Connection acquisition and SOCKS reference

The observer compiles the complete pinned Network.Connection implementation and
Types from crypton-connection 0.4.6, plus the complete crypton-socks 0.6.2 main,
Types, Conf, Wire, Command and Lowlevel modules. The importer verifies every
original byte after surgical module/import changes; Command's export list is
opened to observe its private driver. No resolver retry, bracket, proxy parsing,
SOCKS wire, protocol or exception algorithm is rewritten in the observer.

The socket/resolver/environment/TLS effects record arguments and inject scripted
results or exceptions. Socket allocation uses actual descriptors solely as
opaque original Socket values; a separate observer cleanup closes all allocated
descriptors after the result/events are captured. It does not invent original
close calls in the event log. Original TLS effects are reused from the preceding
observer, with their log routed into the common timeline.

Wire decoding uses the pinned **actual cereal 0.5.8.3** implementation. Its full
source archive, license and test files are retained. Its incremental read
primitives used for SOCKS are translated in the Kotlin driver; general cereal
parser combinators, serialization instances and all its property suites remain
unported. The crypton-socks release itself has no test-suite stanza. Its legacy
Parse module is retained but not yet translated.

Environment values exercise the actual `Network.Socket.PortNumber` Read
instance in the original Connection module. This uses network 3.2.9.0 `safeInt`
bounds checks, not Word16 wrapping. Leading spaces, nested parentheses and
binary/octal/hex literals are accepted; trailing unconsumed spaces reject the
proxy setting. The environment lookup itself is a controlled getEnv effect;
the complete live GHC getEnv/encoding runtime remains separate work.

The corpus records protocol errors as the original ErrorCall message, without
machine-specific Haskell call stacks, and records structured original IO errors.
It checks strict current-chunk consumption, fragmented/empty input, malformed
versions/types, every method/command/reply byte, host-endian address words,
length/port truncation, proxy identity and socket cleanup/error replacement.

Build using GHC 9.12.2 from this directory:

```sh
cabal build --builddir=/tmp/kotdoc-connection-dial-build connection-dial-reference
```

From the repository root, regenerate with
`python3 tools/import-connection-dial-fixtures.py --oracle /absolute/path/to/connection-dial-reference`.
CI uses `--check-source` without a Haskell installation or external proxy.
