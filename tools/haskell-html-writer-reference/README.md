# Complete HTML writer oracle

This project depends on the entire unchanged pinned Pandoc library and
pandoc-types. Unlike `haskell-writer-html-reference`, it does not extract or
replace the HTML writer, its common-state implementation or its conversion
dependencies. JSON is only a transport for invoking all eleven exported APIs
and observing output and the public chronological log. Repeated requests in a
`sequence` share the real PandocPure common state.

Build from this directory with GHC 9.12.2, the existing Cabal state and an external
`--builddir=/private/tmp/kotdoc-html-writer-build`. The full library build and
repeat build with `cabal.project.freeze` succeeded. A sampled earlier GHC process showed a 39.8 GB physical footprint
and garbage collection while optimizing BibTeX at `-O2`. That failed build was
terminated; `package pandoc: optimization: 0` now avoids this compiler resource
problem without changing the original library or its behavior. Other packages
and the observation executable retain `-O2`. Do not restart a live build because a polling timeout expires.
The freeze file pins the resolved dependencies for reproduction.

`OriginalHelpers.hs` changes only the module declaration of the complete original
`Tests.Helpers`; the importer checks this byte-exact substitution. The observing
`Tests.Helpers` adapter additionally requires `ToPandoc`, executes the original
assertion and serializes its input, original expected value and native result.
The original `Tests.Writers.HTML` source must remain unchanged.

The executable registers the original HTML test module, OriginalHelpers and the
observation adapter with their Tasty/Diff dependencies. Its `--upstream-tests`
branch executes the original assertions and captures their input/expectations.

`tools/import-html-writer-fixtures.py --oracle PATH` runs those original units,
all fixed old-style HTML/literate/S5 goldens, every original accepted Native AST
through all writer variants, and broad option/state/markup cases. The captured
HTML corpus contains 6,732 observations: all 17 original units (their assertions
and 17 serialization checks pass natively), 16 original fixed goldens, 266
original documents through all eleven writer variants, and option/state cases.
Its case hash is `226adb86760c29958cda9c83b7570a6fe219180f9c440d5d1aef76467b5df223`.
Run `--check-source` without an oracle to verify source hashes and coverage.
Command-level tests involving other readers,
resource embedding and application transformations remain in the full port scope.
