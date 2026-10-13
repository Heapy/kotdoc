# Original DocLayout oracle

The executable compiles the unchanged retained doclayout 0.6 modules and calls
their exported operations. It adapts a JSON document-expression protocol; it does
not implement layout, width calculations, ANSI rendering or attribute operations.
The committed Cabal freeze pins its dependencies, including emojis 0.1.5 and
QuickCheck. Capture uses GHC 9.12.2. The original test/test.hs is also compiled
unchanged as a separate Cabal test suite.

```sh
cabal build doclayout-reference --project-dir=tools/haskell-doclayout-reference
cabal list-bin doclayout-reference --project-dir=tools/haskell-doclayout-reference
cabal test original-doclayout-tests --project-dir=tools/haskell-doclayout-reference
python3 tools/import-doclayout-fixtures.py --oracle PATH
```

The importer reads all 54 original renderTest expressions and their literal
goldens with a restricted Haskell-expression reader. Every translation must also
reproduce that literal golden using the original executable before capture is
accepted. The remaining 15 original literal assertions are translated directly
in Kotlin (three offset assertions share one test). All base/ZWJ emoji inputs
are retained. Both original shortcut properties use 20,000 captured inputs from
the actual QuickCheck Arbitrary String generator, with fixed seeds and sizes.
This does not claim a port of QuickCheck's shrinking/runtime infrastructure.

The corpus also probes every Haskell Char (including surrogate code points),
every font field combination, font changes, attributed folds/line splitting,
attribute concatenation, nested styles/links, alignment, signed-Int boundaries,
standalone Attr document rendering (including the original right-fold mconcat),
derived value mapping/folding/State traversal order and seeded documents.
Haskell String outputs are retained as lists of code
points so lone/paired surrogate Chars cannot silently become supplementary
characters. ANSI builder output is observed separately.

CI verifies source and fixture hashes and runs Kotlin against these observations.
Haskell is a test oracle only, never a production rendering dependency.
