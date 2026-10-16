# Original hourglass observer

Development only. GHC 9.12.2 compiles the unchanged `time-hourglass-0.3.0`
sources from `vendor`, with optimization 2, and observes their public values,
calendar, intervals, epoch, Time/LocalTime and System functions. Production
uses only Kotlin/JNA; no Haskell executable is a runtime dependency.

Build with `cabal build all`, then put the three executable paths in a directory
under their executable names and run:

```
python3 tools/import-hourglass-fixtures.py --oracle-dir /path/to/executables
python3 tools/import-hourglass-fixtures.py --check-source
python3 tools/import-hourglass-fixtures.py --oracle-dir /path/to/independent/executables --verify-oracle
```

`hourglass-reference` links the actual POSIX module and original C clock code.
The common native-calendar cases stay in the 1601..9999 range supported by
both POSIX and Windows. Live current-clock/timezone calls are tested separately
in Kotlin, without freezing wall-clock or host-timezone snapshots.

`hourglass-posix-model` links the unchanged POSIX module against a controlled C
FFI boundary, supplying `struct tm`, `timespec`, failure results and call traces.
`hourglass-windows-model` compiles the unchanged Windows module against a
controlled `System.Win32.Time` IO boundary. These boundaries do not implement
hourglass date, arithmetic, epoch, timezone or conversion algorithms. They
observe exact arguments, pointer fields, wrapping, failure propagation and
call order. The Windows boundary uses a fixed exception for native failure;
actual Win32 IOException translation is tested in the native Kotlin test and
the existing independent GHC Windows-error corpus.

The Win32 source manifest binds the complete preexisting upstream archive
(124 files), not a rewritten Win32 implementation. Both full upstream notices
are retained. The importer verifies all source hashes and fixture contents.

All seven arithmetic literal tests are extracted from the original Tests.hs,
with their explicit upstream expected values. Four rational regression inputs
are additionally checked against their original expected fractions. Remaining
cases are independent observations of original code, including unchecked Int64
inputs, compiler rewrite specialization and both native boundaries. This is
not the complete hourglass test-suite port: `test-time-db` is absent from the
upstream archive, and formatting/parsing properties, custom callbacks, Read,
remaining derived numeric operations and Time.Compat remain outstanding.

Notable pinned behavior: at `-O2`, the `timeGetTimeOfDay/Date` RULE has an
unconstrained input type through its `const` RHS. Polymorphic calls yield zero
without a native conversion; direct DateTime calls use the more specific
DateTime rewrite. Separate requests retain both observations.
