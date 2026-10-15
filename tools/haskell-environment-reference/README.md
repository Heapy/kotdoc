This base-only GHC 9.12.2 observer compiles exact extracted getEnvironment/divvy
function bodies from GHC.Internal.System.Environment on both platform branches.
The POSIX block/encoding effects are injected; decoding calls the installed
original GHC encoding implementation. The Windows branch retains its original
Word8 peek while advancing by sizeof(CWchar), including low-byte-zero splitting.
CWchar is represented by Word16 on the observer host, matching Windows size and
alignment; the original cWcharsToChars function is extracted unchanged.

The production API uses live libc environ/_NSGetEnviron or Kernel32's Unicode
environment block, not System.getenv's cached map. Native system iconv remains
a system effect for nonbuiltin encodings; GHC builtin Unicode/ASCII/Latin1 and
RoundtripFailure recovery are ported to Kotlin. The explicit source/codecs here
do not claim a complete GHC IO/locale runtime, every platform ABI or arbitrary
user replacement of GHC TextEncoding objects.

Decode observations invoke original mkTextEncoding //ROUNDTRIP and peekCStringLen.
Posix observations run the complete original function on controlled raw C arrays.
Live observations temporarily replace environ **inside the disposable observer
process**, then invoke actual System.Environment.getEnvironment and restore the
pointer. No parent/user environment is changed or printed. Input/expected values
are code-point lists, retaining malformed-byte surrogate escapes and Windows
unpaired surrogates without JSON replacement. The original compiler patch
version is checked through System.Info.fullCompilerVersion.

Corpus generation uses independent macOS/aarch64 and Linux/x86_64 GHC 9.12.2
executables because system iconv can differ. Native Windows Kotlin tests verify
actual Kernel32 snapshots separately. No Haskell executable is a production
dependency. All extracted bodies, retained sources and full base notice are
hashed/checked. Scope and remaining integration are in docs/PORTING.md.

The empty encoding name uses the current C locale; the observer records that
locale and Kotlin reproduces/restores it for that observation. This is distinct
from the JVM's startup locale. Raw 32-bit Char slots from iconv are retained,
including out-of-Unicode values; they are not replaced to fit a JVM String.
The full original T12494 native test is run before capture. Kotlin covers its
getEnvironment empty-value assertion through live API tests; the separate Blank
getEnv/getEnvDefault/setEnv API is not claimed as ported by this checkpoint.

Build with GHC 9.12.2 in this directory, keeping generated files outside it:

    ghc --make -O2 -XScopedTypeVariables -package ghc-internal EnvironmentReference.hs NativeEnvironment.c -outputdir /tmp/kotdoc-environment-build -o /tmp/environment-reference
    ghc --make -O2 ../../vendor/ghc-environment-9.12.2/tests/T12494.hs -outputdir /tmp/kotdoc-original-env-test -o /tmp/T12494

Capture/check at the root:

    python3 tools/import-environment-fixtures.py --platform macos-aarch64 --oracle /path/to/environment-reference --original-test /path/to/T12494
    python3 tools/import-environment-fixtures.py --platform linux-x86_64 --oracle /path/to/linux-wrapper --original-test /path/to/linux-T12494-wrapper
    python3 tools/import-environment-fixtures.py --platform macos-aarch64 --check-source
    python3 tools/import-environment-fixtures.py --platform linux-x86_64 --check-source
