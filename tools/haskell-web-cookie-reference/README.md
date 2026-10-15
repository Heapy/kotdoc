This development-only observer links the actual original cookie 0.5.1,
time 1.14 and text 2.1.2 with the frozen GHC 9.12.2 Pandoc dependency set.
No cookie/date algorithm is copied into the observer. OriginalCookieSpec is
the complete unchanged release test file with only its module name changed;
the importer verifies that exact transformation.

The original-cookie-tests executable calls its original main, using actual
Tasty/QuickCheck/HUnit. Capture first requires all nine original tests to pass,
including 1,000 cases per property with replay seed 938. The observer separately
uses the original Arbitrary instances and calls all three original property
functions to capture 3,000 reproducible passing inputs. Kotlin translates the
six literal units and the same three property assertions for those inputs.
This is a finite property run, not a claim to exhaust all generated values or
to port the entire QuickCheck runtime.

Additional observations retain arbitrary header bytes, Unicode text builders,
record fields, Show, rendering, reparsing and Eq. Date observations call actual
parseCookieExpires/formatCookieExpires and UTCTime Show, including two-digit
year fuzzing, leap seconds, negative/unbounded years and raw DiffTime values.
The observer catches only ErrorCall; unexpected exceptions abort capture.
Empty Max-Age records are also inspected without demanding that field, after
record updates, and with short-circuit/self equality. Kotlin retains that
specific field lazily rather than hiding its original read error.

Build from this directory:

    cabal build all --project-file=cabal.project --builddir=/tmp/kotdoc-web-cookie-build

Capture/check from the repository root:

    python3 tools/import-web-cookie-fixtures.py --oracle /path/to/web-cookie-reference --original-tests /path/to/original-cookie-tests
    python3 tools/import-web-cookie-fixtures.py --check-source

The complete cookie source release, copyright notice and archive provenance
are retained. Neither Haskell nor an external converter is used in production.
The Kotlin date code specializes the exact original fixed cookie format; the
whole generic Data.Time formatter/parser, generic Builder chunk/lazy contracts,
HTTP cookie storage, redirects and transport remain under the unchanged full
Pandoc/calibre objective in docs/PORTING.md.
