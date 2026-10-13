# TIFF/Exif binary reference

This adapter calls the unmodified JuicyPixels 3.3.9 TIFF Binary/BinaryParam
instances, Exif tag functions, JPEG APP1 frame parser and JPEG image writer.
The vendored TIFF and JPEG modules are compiled unchanged with their complete
module dependencies. No parser, offset allocator or writer algorithm is copied
into the adapter. Tuple inputs construct typed fields independently of Kotlin.

Use the GHC 9.12.2/Cabal environment described in ../haskell-reference/README.md.
The freeze file pins binary 0.8.9.3, bytestring 0.12.2.0, vector 0.13.2.0 and
primitive 0.9.1.0. From this directory:

```sh
cabal build tiff-binary-reference --with-compiler=/path/to/ghc
cabal list-bin tiff-binary-reference
```

Pass the executable to `tools/import-tiff-binary-fixtures.py --oracle ...`.
Ordinary tests/CI use its retained results and verify source/result hashes.
The newline request tuple contains operation, byte order, numeric arguments,
hexadecimal input and lists of explicit IFD field constructor tuples.
Results retain Show, encoded bytes and parser error/offset/remainder information.
Remainders are represented by length and first/last 16 bytes, avoiding repeating
large pixel payloads; parser offsets identify the complete input suffix.

The cases include all inputs from the earlier independent TIFF metadata corpus,
but the expected values are captured afresh from the full typed TIFF parser.
This verifies actual nested Exif fields, not just the former size/DPI projection.
The original TIFF decode/re-encode pixel tests remain separate outstanding work.
