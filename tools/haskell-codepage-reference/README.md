# GHC Windows CodePage reference

GHC 9.12.2 is a development oracle only. Production conversion stays in Kotlin.
The importer verifies the unchanged CodePage algorithms and entire generated
Table against revision 383be28ffdddf65b57b7b111bfc89808b4229ebc. The API module
changes only its module/export name, imports and native effects. The host
CWchar alias is explicitly Word16: Windows wchar_t is two bytes even when the
observer is compiled on a host whose wchar_t is four bytes. Original UTF16
buffer functions, Foreign.C.String loops and recovery policies run inside GHC.
OriginalWindowsEncoding selects the original Windows mkTextEncoding' branch.
OriginalLocaleCodePage scopes the unchanged currentCodePage CAF and locale
bindings to each independent effect program; the importer compares their bodies.

CodePageEffects is a controlled Win32 boundary. It supplies ASCII, DBCS, BMP,
astral and deliberately malformed UTF16 mappings, default-character flags,
errors and writes before failure. It does not implement GHC's shrinking search,
binary search, UTF16 bridge, CString growth or recovery. Every native call and
GetLastError/prefix query is recorded. The JVM supplies the same controlled
boundary and compares full traces as well as results, errors and buffer metadata.

The table observations enumerate every byte and every compact encoder entry
through its original maximum. The signed Int16-to-Char conversion is retained,
including raw 32-bit values outside Unicode. Expectations must not be rebuilt
by reversing the decoder or substituting a host charset implementation.

For low-level buffer observations, backing allocations include 64 initialized
canary elements beyond their logical size. This makes the original bSearch's
possible reads past the logical end deterministic and physically in-bounds;
logical size and cursors are preserved in both observers. CString observations
use the original GHC CString allocation/loop functions. Insufficient-buffer responses are tested through bounded buffer-capacity
cases; an infinite CString retry is not requested. Only addresses in bSearch error summaries are
normalized. The real Windows tests separately execute Kernel32 CP932 mapping,
truncated input, recovery, invalid GetCPInfo and initial encoding selection.
Controlled API agreement on POSIX is not evidence of actual Windows execution.

Build in this directory with GHC 9.12.2:

```sh
cabal build --builddir=/tmp/kotdoc-codepage-build codepage-reference
```

Regenerate or independently compare observations from the repository root:

```sh
python3 tools/import-codepage-fixtures.py --oracle /absolute/path/to/codepage-reference
python3 tools/import-codepage-fixtures.py --verify-oracle --oracle /independent/path/to/codepage-reference
python3 tools/import-codepage-fixtures.py --check-source
```

Full general Handle/BufferCodec interfaces, all machine-width/RTS semantics and
native ABIs beyond those exercised remain outside this observer's coverage.
