# Original doctemplates oracle

This harness compiles unchanged doctemplates 0.11.1 and DocLayout 0.6 modules.
Its JSON protocol constructs Context/Val/Doc inputs and calls the original
conversion, JSON, Semigroup, Functor, Foldable and State traversal operations.
It contains no substitute implementation of those operations. The full Cabal
freeze pins the dependency closure; capture uses GHC 9.12.2 and Aeson 2.2.3.0.

```sh
cabal build doctemplates-reference test:original-doctemplates-tests \
  --project-dir=tools/haskell-doctemplates-reference
cabal list-bin doctemplates-reference --project-dir=tools/haskell-doctemplates-reference
python3 tools/import-template-value-fixtures.py --oracle PATH_TO_REFERENCE
```

The unchanged original test executable must run with working directory
`vendor/doctemplates-0.11.1`: it globs `test/*.test` relative to its working
directory. Running it elsewhere can silently omit all 22 original file goldens.
Use `cabal list-bin test:original-doctemplates-tests` to locate it, then change
directory before executing it. The 15 original unit cases also run. These are
baseline upstream tests; Kotlin template parsing/rendering is still pending.

The committed corpus covers every original golden's actual JSON context,
arbitrary-precision integers, decimal rounding/underflow/overflow, finite
Text/String conversion instances, nested all-or-none list extraction, left-biased
map union, Unicode key order, styled and attributed documents and effect order.
CI checks all source/corpus hashes and compares Kotlin with these observations.
Upstream Haskell remains a test-only dependency.
