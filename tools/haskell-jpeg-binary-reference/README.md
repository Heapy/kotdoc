# JPEG binary reference

This adapter compiles ten original JuicyPixels 3.3.9 modules without edits,
including Jpg.Internal.Types and its TIFF/Exif dependencies. It calls the actual
Binary instances, calculateSize, parseECS, parseECS_simple and marker helpers.
It does not reproduce their algorithms. Inputs and outputs use newline-delimited
Haskell Read tuples and JSON respectively. Parser results retain error messages,
byte offsets, remaining bytes, Show, re-encoded bytes and calculated sizes.
Explicit constructor cases separately exercise the writer, including inconsistent
counts, lengths, non-nibble Word8 values and checked-vector bounds errors.

Use the GHC 9.12.2/Cabal environment documented in ../haskell-reference/README.md.
The freeze file pins binary 0.8.9.3, bytestring 0.12.2.0, vector 0.13.2.0 and
primitive 0.9.1.0. Run from this directory:

```sh
cabal build jpeg-binary-reference --with-compiler=/path/to/ghc
cabal list-bin jpeg-binary-reference
```

Pass that executable to `tools/import-jpeg-binary-fixtures.py --oracle ...` from
the repository root. CI uses `--check-source` to verify source and result hashes.
No Haskell executable is needed in production or for ordinary Kotlin tests.

The ECS cases explicitly preserve lazy ByteString chunk boundaries. The original
optimized parser differs from its simple counterpart: nonempty input without a
terminator succeeds; a leading marker returns a single FF despite consuming zero
bytes; a marker crossing chunks can duplicate the previous chunk's prefix.
These behaviors were observed by executing the pinned original, not inferred
from the JPEG specification. They must not be silently normalized in the port.
Full image/scan assembly and Exif serialization are subsequent work.
