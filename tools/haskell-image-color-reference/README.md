# Original RGB8 / YCbCr8 conversion oracle

Compiles unchanged JuicyPixels 3.3.9 with GHC 9.12.2 and the frozen Cabal
dependencies. The adapter directly calls the two ColorSpaceConvertible instances
in Codec.Picture.Types, either convertImage or pixelMap convertPixel. It does not
implement color coefficients, rounding, clipping or conversion formulas.

Requests are (mode,direction,width,height,values). The exhaustive-image and
exhaustive-pixel modes generate all 65,536 second/third-channel combinations
for the first-channel value in values, in lexicographic order. Other requests
use values as the actual interleaved sample vector. Direction rgb means RGB8 to
YCbCr8; ycc means YCbCr8 to RGB8. Results contain dimensions and all output bytes
as hex. The importer stores SHA-256 and byte length for exhaustive batches and
verbatim hex for small images. Each complete 24-bit domain is checked separately
against the original image and pixel paths.

Mode jpeg calls original encodeJpegAtQuality 100 after original RGB conversion,
including the upstream testSuite white, black and test gradient sent to toJpg.
Expected JPEG files are captured in full. Unused input vector tails and zero
dimensions are covered. Short source vectors would invoke unchecked native
memory access and are not treated as portable oracle cases.
