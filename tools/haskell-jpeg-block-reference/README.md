# Original JPEG block reference

This adapter compiles the unchanged JuicyPixels 3.3.9 Common, FastIdct and Types
modules, including their TIFF/Exif dependencies, directly from vendor/. It calls
original operations and captures numerical results, input/destination mutation,
and bit-reader continuations. No numerical algorithm is reproduced here.

Use GHC 9.12.2 and cabal-install 3.14.2.0 from the environment described in
[the bit-stream reference](../haskell-reference/README.md). The freeze file pins
the same dependency versions and index state.

```sh
cabal build jpeg-block-reference
cabal list-bin jpeg-block-reference
```

From the repository root:

```sh
python3 tools/import-jpeg-block-fixtures.py --oracle /path/to/jpeg-block-reference
python3 tools/import-jpeg-block-fixtures.py --check-source
```

Keep build output in dist-newstyle (ignored) or an external directory. Kotlin
and regular CI use the retained results and do not depend on Haskell binaries.
