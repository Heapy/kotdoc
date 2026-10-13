# Original progressive JPEG oracle

This adapter compiles the original JuicyPixels 3.3.9 Progressive module and its
12 dependencies unchanged. It calls the exported progressiveUnpack function
with explicit frame, quantization, packed Huffman and scan parameters, and
captures every output byte. It contains no coefficient or MCU decoding logic.

Build with GHC 9.12.2 and the frozen Cabal dependencies. Input is one Haskell
`(dimensions, components, quantizationTables, scans)` tuple per line. Dimensions
are `[width,height,maximumHorizontal,maximumVertical]`; a component is
`[identifier,horizontal,vertical,quantizationIndex]`. Each scan is
`([(dcPackedTree,acPackedTree,parameterFields)],bytes)`; parameter fields follow
JpgUnpackerParameter after its two Huffman trees, with pairs flattened. Output
is JSON pixels or the first line of the original exception.

The fixtures test the complete progressive unpacker with supplied scan
parameters. JPEG frame-to-scan orchestration, colorspace conversion, metadata
extraction, baseline decoding and encoder tests are separate remaining work.
Synthetic inputs avoid original unchecked out-of-allocation writes; checked
vector failures are retained and classified separately from successful pixels.
