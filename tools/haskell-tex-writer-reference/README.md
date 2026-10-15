# Complete TeX writer oracle

The observer links complete unchanged texmath 0.13.3.1. MathWire supplies only
JSON transport from the already protected math-reader reference. The separate
original test executable compiles unchanged test-texmath.hs with all registrations.
It runs the complete TeX writer group and the original TeX round-trip group.
Other readers/writers and their tests remain in the full port scope.

Build with GHC 9.12.2 and an external Cabal build directory. Freeze dependencies
and repeat the build. Import using tools/import-tex-writer-fixtures.py --oracle
/path/to/tex-writer-reference; --check-source verifies the captured source and
original fixtures without invoking Kotlin or requiring Haskell.

All 565 selected original TeX-output tests pass. The unchanged upstream TeX
round-trip group has 217 passes and 344 failures. Capture preserves every original
round-trip golden, the actual original result, and whether the original assertion
passes. Kotlin compares all those observations, including each failing baseline;
this is not a claim that upstream round-trip failures were fixed or skipped.
