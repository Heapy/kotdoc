# Dependency declaration observer

This development-only program uses GHC9.12.2's Cabal3.14.1.0 parser to read every
retained vendor `.cabal` file. `condTestSuites` supplies the authoritative declared
suite roots. Common stanza imports are resolved by Cabal; condition trees remain
unselected. The selected data projection retains test entry interfaces, source dirs, modules, dependencies, modern build-tool-depends,
CPP options and buildability declarations are retained for every branch.
Executables are separate components and are not silently counted as test suites.
No package or test executable is built, listed or executed by the observer.

```sh
/tmp/kotdoc-haskell/ghc/bin/ghc -package Cabal -O0 -outputdir /tmp/kotdoc-dependency-inventory-build -o /tmp/kotdoc-dependency-inventory tools/test-inventory-dependency-reference/CabalInventory.hs
python3 tools/dependency-test-inventory.py --capture /tmp/kotdoc-dependency-inventory
python3 tools/dependency-test-inventory.py --verify-capture /tmp/kotdoc-dependency-inventory
python3 tools/check-test-inventory-dependencies.py
```

The synthetic `fixtures/conditional.cabal` independently exercises common import,
nested flag/OS conditions and an executable. `conditional.json` is this parser's
output; regenerate/check it with the same binary and explicit fixture path.
These fixtures test inventory parsing, not document conversion expectations.

The Python reconciler checks that every current manifest appears exactly once,
all hashes match and every syntactic test-suite/executable declaration has the
same component name/multiplicity as the original Cabal AST. It never treats a
retained source fragment as a complete upstream package. The frozen package list
is a separate baseline; an exact missing manifest remains explicit even when a
source fragment or installed compiler library exists.

Potential entry/module paths use all declared source directories across the tree.
They report source availability only, never solve mutually exclusive branches or
establish an effective build configuration. Runtime registrations/assertions stay
unknown. Main/embedded-doctest/preprocessor signals and scripts/build files are
separate discovery units, with source-inspected additional routes recorded apart
from Cabal roots. In particular, alias execution routes are not extra source tests.

The projection is not a complete effective BuildInfo: legacy build-tools, compiler
options/extensions and other build fields remain in the original source catalog
and require profile resolution before listing/execution. No build-tool absence
is inferred from an empty modern build-tool-depends list.
