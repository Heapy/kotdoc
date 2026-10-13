# Original JPEG table reference

This test-only adapter compiles the unmodified JuicyPixels 3.3.9
`Codec.Picture.Jpg.Internal.DefaultTable` and its dependencies from vendor/.
It captures trees, packed arrays, inverse tables, sequential entropy symbols,
quantisation and macro-block construction. No algorithm is reimplemented here.
Exceptions are forced and their first diagnostic line retained in the fixtures.

Use GHC 9.12.2 and cabal-install 3.14.2.0, as described in
[the bit-stream reference](../haskell-reference/README.md). The copied freeze
file pins the same package versions and Hackage index state.

```sh
cabal build jpeg-tables-reference
cabal list-bin jpeg-tables-reference
```

From the repository root:

```sh
python3 tools/import-jpeg-table-fixtures.py --oracle /path/to/jpeg-tables-reference
python3 tools/import-jpeg-table-fixtures.py --check-source
```

Kotlin and ordinary CI need neither this executable nor GHC. Keep build output
in dist-newstyle (ignored) or use a build directory outside the source tree.
