This development-only observer compiles the four unmodified PublicSuffixList
modules directly from the complete pinned http-client 0.7.19 release. It does
not reconstruct their lookup or serialization algorithms. The same frozen
GHC 9.12.2 dependency versions as the original Pandoc build are used.

The production binary resource is the exact original ASCII ByteString literal
with its named NUL escapes decoded, not a current public suffix list. Capture
also checks that the original putDataStructure emits exactly those bytes.
Every rule/exception node, including non-leaves and wildcards, is observed on
its own domain and with additional prefixes, uppercase and empty labels.
Custom trees exercise exception precedence, wildcard fallbacks, duplicate
keys, Unicode Text ordering and serialized-tree EOF/trailing-byte behavior.
Malformed UTF-8 is recorded as a strict decoding failure; exact Haskell
UnicodeException Show and general lazy forcing order are not claimed here.
Only UnicodeException is caught; unexpected observer failures abort capture.

The release includes no separate PublicSuffixList test module. The independent
observations supplement the pending original CookieSpec; they are not counted
as translated upstream unit tests.

Build from this directory:

    cabal build public-suffix-reference --project-file=cabal.project --builddir=/tmp/kotdoc-public-suffix-build

Capture/check from the repository root:

    python3 tools/import-public-suffix-fixtures.py --oracle /path/to/public-suffix-reference
    python3 tools/import-public-suffix-fixtures.py --check-source

No Haskell executable or source module is a production dependency. The Kotlin
implementation uses the shipped original tree and its own translated lookup
and serializer. Cookie date parsing/storage and full URL transport remain work
under the unchanged complete Pandoc/calibre objective in docs/PORTING.md.
