# Original complete JPEG decoding oracle

The harness compiles 16 original JuicyPixels 3.3.9 modules unchanged, including
Codec.Picture.Jpg. It calls decodeJpeg and records dynamic image kind, dimensions
and all raw sample bytes. No decoding/state-fold/color conversion is duplicated
in the adapter. Metadata extraction is not forced by decodeJpeg.

Build with the frozen Cabal dependencies and GHC 9.12.2. Each stdin line is a
hex-encoded JPEG. `--file INPUT OUTPUT` writes complete raw pixels to OUTPUT and
returns JSON dimensions/kind (or the original failure). The importer hashes these
bytes for retained photographs; small synthetic cases store them directly.
`--reference-info` identifies the actual compiler and vector dependency.

Tests preserve all 31 original JPEG assets. Full decode/re-encode upstream tests
still need the encoder. Undefined native uninitialized/out-of-allocation memory
is not a portable expected pixel value; synthetic successful baseline cases
supply all components and scans so their complete output is initialized.
