# Original pandoc-types AST Show oracle

This test-only executable links the unchanged local `upstream/pandoc-types`
package (1.23.1.2). GHC 9.12.2 derives the actual Show instances from its
Definition.hs; no shadow module, reconstructed instance or Kotlin implementation
is used to produce expected text. Text is pinned to 2.1.2.

OriginalExamples.hs retains the complete 56 JSON example value definitions
verbatim from test/test-pandoc-types.hs. The importer checks the excerpt against
that pinned file and hashes the whole original package, including its test suite
and Arbitrary module. The original Data.String.QQ module is compiled directly
from the upstream test directory. We observe Show of these original values;
this is not a new claim that all their JSON assertions or all pandoc-types tests
are ported by this change.

Reproduce from the repository root:

```sh
cabal build exe:ast-show-reference \
  --project-dir=tools/haskell-ast-show-reference \
  --builddir=/tmp/kotdoc-ast-show-build
python3 tools/import-ast-show-fixtures.py --oracle PATH_TO_EXECUTABLE
python3 tools/import-ast-show-fixtures.py --check-source
```

The adapter transports JSON into the original FromJSON instances. Bit-pattern
Double requests avoid loss of signed zero, infinity, subnormal values and NaN
payloads. Code-point Text requests use original Text.pack, including surrogate
replacement. Partial list elements/tails and infinite nodes are constructed in
Haskell without weakening Definition's StrictData fields. Deep observations use
up to 100,000 nesting steps. All 266 original accepted Native documents use their
independently retained Pandoc JSON inputs; rejected source fixtures remain in the
hashed input inventory. Source equality and ordering dictionaries, Read and
Logging integration are separate remaining work.
