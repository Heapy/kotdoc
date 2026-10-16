# Original Pandoc registration observer

This development-only executable folds the actual original Tasty test tree into
JSON arrays of group/name components. It does not run assertions. `trivialFold`
passes an unevaluated failing IO action for resources, just as tasty 1.5.4's
`--list-tests`; neither observer invokes that resource action or test values.

Preparation preserves the complete original `test-pandoc.hs`, replacing only
`module Main (main)` with `InventoryOriginalMain (main, tests)` in an isolated
copy. All imported original test modules remain byte-identical. The unchanged
original binary is separately built using its real Cabal test component. Its
`--list-tests` output must equal the observer's `testsNames` output byte for byte,
while a second fold retains unambiguous JSON path components (dots/newlines in
labels cannot be parsed reliably from plain listing output).

Example with the existing local development toolchain/cache:

```sh
python3 tools/prepare-test-inventory-runtime.py --directory /tmp/kotdoc-test-inventory-runtime
CABAL_DIR=/tmp/kotdoc-haskell/cabal-state /tmp/kotdoc-haskell/cabal v2-build --project-dir=/tmp/kotdoc-test-inventory-runtime --builddir=/tmp/kotdoc-test-inventory-runtime/build --offline -j2 pandoc:test:test-pandoc
python3 tools/prepare-test-inventory-runtime.py --directory /tmp/kotdoc-test-inventory-runtime --compile-observer
```

The project reuses the existing frozen dependency set and cached source archives
for texmath 0.13.3.1, citeproc 0.14.1 and commonmark-pandoc 0.3.0.2. It builds the
unchanged pinned Pandoc 3.12.1 and pandoc-types 1.23.1.2. Pandoc optimization is zero
as in the established reference setup. The structured observer compiles directly
against the same plan's package IDs, including Pandoc's internal xml-light unit,
without changing the original package or production code. Paths/toolchain can be
supplied explicitly; missing caches fail rather than selecting new versions.

Use `cabal list-bin ... pandoc:test:test-pandoc` to locate the original binary,
then pass it to `tools/runtime-test-inventory.py --capture` or `--verify` with the
same `--directory`. These commands execute registration only. All `TASTY_*`
environment variables are removed from both discovery subprocesses, so an ambient
pattern cannot silently filter the inventory. The original program runs from
`upstream/pandoc` and enters its own `test` directory; the observer enters that
same test directory directly.

Capture verifies every tracked Pandoc/pandoc-types source/asset against its pinned
Git blob, includes SHA-256 and build/compiler/OS/feature/dependency conditions,
and records both binary hashes. Replay compares registration data and conditions;
linker-dependent binary hashes may differ after clean rebuilds. A finite native
profile is not evidence for every OS/CPP/feature combination or Kotlin coverage.
