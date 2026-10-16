# Original Time.Format observer

Development-only GHC 9.12.2 (`-O2`) observer. Builds the unchanged 12 hourglass
modules, the unchanged original `tests/CustomTimeFormatFct.hs`, and a request
bridge. The real POSIX/C executable and controlled POSIX/Windows executables
use the same original modules. Native-boundary stubs are reused verbatim from
`../haskell-hourglass-reference`; no formatting/parsing algorithm is substituted.

```
cabal build all
python3 tools/import-hourglass-format-fixtures.py --oracle-dir /path/to/three/executables
python3 tools/import-hourglass-format-fixtures.py --check-source
python3 tools/import-hourglass-format-fixtures.py --oracle-dir /path/to/independent/executables --verify-oracle
```

Run the Python commands from the repository root. Executables are named
`hourglass-format-reference`, `hourglass-format-posix-model` and
`hourglass-format-windows-model`. All fixtures bind complete upstream source
archives, full notices, observer and boundary sources and the importer.

Observations cover every format element, String tokenization, ISO builtins,
Eq/Show (raw Char points), all eleven Time types, all six print/parse entry
points, validation/error text, sequential overwrites, nanosecond masks,
precision semantics, timezone forms, custom callbacks and independent demand
of result tag/remainder/timezone/date/time. Controlled native failures and
replacement callbacks observe lazy error precedence. POSIX constructs a
DateTime before demanding rawGmTime; Windows first demands SYSTEMTIME.

The three original custom-format roundtrip properties each use 1,000 valid
dates in the original 1800..2202 generator range and 0..100,000,000 nanoseconds.
ISO date/date-time roundtrips use 1,000 dates each. The two original printing
properties compare ISO dates and EPOCH against the independent `Data.Time`
implementation for 1,008 seconds in the common Windows/POSIX range; the
observer first asserts that original hourglass also agrees. Every timezone
offset in the original -660..660 generator range is parsed. Extra overflow,
Unicode, unchecked input and callback-demand cases are independent observations
of the original code, not expectations produced by Kotlin.

This does not claim the complete hourglass suite is ported: no missing
`test-time-db` cases are invented, and Read/remaining numeric instances,
Time.Compat and the rest of the library's property suite remain outstanding.
