# Native model Show reference

This test-only executable compiles the unchanged pinned doctemplates 0.11.1,
doclayout 0.6, Skylighting 0.15 and supporting modules. PandocOptionsModels retains
verbatim original declarations, their deriving clauses and the complete original
WriterOptions Default body. The importer checks those extractions against pinned
Pandoc 3.12.1 source. It does not hand-implement any expected Show output.

The observation adapter defaults its explicit writer syntax-map argument to the
empty map to keep ordinary field cases small. A separate case supplies all 195
models from the original Skylighting XML loader; the port already independently
checks these against the original baked map. This adapter's defaultSyntaxMap
binding is deliberately not a replacement for the production default syntax map.
Actual Pandoc library defaults/getters/writers are independently covered by the
prior writer-options corpus.

Build with `cabal build model-show-reference --project-dir=tools/haskell-model-show-reference
--builddir=/tmp/kotdoc-model-show-build` and pass the resulting binary to
`python3 tools/import-model-show-fixtures.py --oracle PATH` from the repository
root. The frozen dependency closure targets GHC 9.12.2. CI checks all source/input
hashes and runs every captured comparison against Kotlin. No native program is
part of production conversion.

This stage observes Show only. Read, Data, Typeable and Generic instances in the
native declarations remain distinct obligations in the full porting ledger.
