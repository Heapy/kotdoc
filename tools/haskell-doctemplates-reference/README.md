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
directory before executing it. The 15 original unit cases also run. Kotlin
template parsing and all original doctemplates cases now have a separate Kotlin
parser corpus described below.

The committed corpus covers every original golden's actual JSON context,
arbitrary-precision integers, decimal rounding/underflow/overflow, finite
Text/String conversion instances, nested all-or-none list extraction, left-biased
map union, Unicode key order, styled and attributed documents and effect order.
CI checks all source/corpus hashes and compares Kotlin with these observations.
Upstream Haskell remains a test-only dependency.

`tools/import-template-runtime-fixtures.py` captures rendering of all eight
Template constructors, every Pipe, loop binding, truthiness, column state and
free-variable extraction. Compiled AST inputs from all 22 original goldens and
nine successful original unit cases are captured with the unchanged original
parser. Their literal golden outputs/assertions must match before capture is
accepted. That corpus tests the Kotlin renderer through captured ASTs; the
separate parser corpus below tests compilation itself. Both original Text and String
targets are executed. Observations include complete Doc structures, cached
widths, attributes, plain/ANSI output at six widths, mapping/folds/State traversal,
all Roman inputs 0..4000 and Variable/Pipe union/order. Reproduce with:

```sh
python3 tools/import-template-runtime-fixtures.py --oracle PATH_TO_REFERENCE
```

The oracle now compiles the retained unchanged Parsec 3.1.18.0 core and
FilePath 1.5.4.0 modules too, instead of relying on package binaries for those
algorithms. Its logged TemplateMonad records every partial request while
returning the supplied immutable file-map contents; missing keys return empty
text. Parser backtracking restores Parsec state and does not undo loader effects.
`compile-only-map` and `compile-only-string-map` observe the complete AST, free
variables, exact diagnostics and loader requests using the original compiler.
`parsec-run` exposes consumed/empty success/failure, values, remaining input,
user state, source positions and retained errors. Other operations compare
POSIX/Windows partial-path resolution, error/message equality/order/formatting
and all four GHC character predicates for every Haskell Char.
`compile-file` calls the original public compileTemplateFile against retained
files, including recursive partials, UTF-8/BOM data, malformed UTF-8 and a missing
root. It distinguishes parser errors from propagated IOExceptions; fixture-root
paths in syntax diagnostics are normalized for portable comparisons. Exact
platform IOException wording is not claimed by this classification.

```sh
python3 tools/import-template-parser-fixtures.py --oracle PATH_TO_REFERENCE
python3 tools/import-parser-char-data.py --check
```

The parser importer extracts the six literal diagnostic assertions from original
test/test.hs and checks them against the native compiler before capture. It
checks all 22 literal goldens and the successful unit assertions too. GHC 9.12.2
uses Unicode 16.0.0; production predicate tables are translated independently
from that original UnicodeData.txt and the retained GHC predicate source, not
from oracle outputs. The native all-Char observations independently check them.

Separate Cabal targets compile all unchanged original Parsec test executables:
`original-parsec-tests`, `original-parsec-issue175`, `original-parsec-issue171` and
`original-parsec-issue127`. Their native success is a dependency baseline; Token,
Expr, Perm, generic stream/transformer interfaces and other unused Parsec APIs
are not thereby claimed to be ported to Kotlin.
