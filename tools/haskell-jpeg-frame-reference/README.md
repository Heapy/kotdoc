# Original JPEG frame oracle

This harness compiles the unchanged JuicyPixels 3.3.9 modules from the pinned
vendor tree. It exercises strict and legacy semi-lazy image parsing, individual
frames, frame locations, early header lookup, image/frame serialization, and
macroblock debug printers. It does not decode JPEG pixels.

Build with GHC 9.12.2 and the frozen cabal.project dependencies:

```
cabal build jpeg-frame-reference
```

Each input line is a Haskell tuple `(operation, numericArguments, hexInput,
frames)`. A frame is `(constructor, code, headerValues, payloadBytes)`.
The executable returns JSON with original Show output, serialized bytes, or
parser failure including consumed offset and a compact remainder fingerprint.
`--file OP INPUT PREFIX` writes full Show/serialized outputs to files so the
capture script can hash large original JPEGs without embedding them again.
`--reference-info` identifies the actual compiler and binary package.

Expected values are captured by tools/import-jpeg-frame-fixtures.py. No Kotlin
implementation is used to generate them. The original JPEG assets include all
29 jpegValidTests, huge.jpg, and the Samsung metadataReadTest photograph from
the pinned upstream test revision. Their pixel decode/re-encode tests remain
separate outstanding work.
