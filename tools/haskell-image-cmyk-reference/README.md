# Original CMYK and YCbCrK conversion oracle

Compiles the unchanged pinned JuicyPixels 3.3.9 sources with GHC 9.12.2 and
frozen Cabal dependencies. The adapter calls the original six ColorSpaceConvertible
instances: RGB8/CMYK8, RGB16/CMYK16, YCbCrK8/RGB8 and YCbCrK8/CMYK8. Each is
invoked through both convertImage and pixelMap convertPixel. No conversion,
coefficient, division, clipping or rounding algorithm is copied into the adapter.

Requests are (operation,kind,path,width,height,values). Kind sweep generates
65,536 input pixels; other kinds use the supplied interleaved values. Sweeps
cover the complete RGB8 domain, the complete Y/Cb/Cr domain with varying K,
every CMYK8 component/K pair, and full 16-bit component ranges at selected
parameters. The importer also supplies boundary products, K sweeps, small
images and seeded samples. It captures original output dimensions and all
samples as hexadecimal; 16-bit samples use explicit little-endian serialization.
Full large-output SHA-256 digests and lengths are stored, and small outputs are
retained verbatim. The source manifest includes the original test runner where
CMYK8/16 images are converted to RGB before downstream image encoders.

The entire four-channel 32-bit domain and entire 16-bit color domains are not
claimed exhaustive. Short vectors and images with two negative dimensions can
observe unchecked or uninitialized native memory in pixelMap; they are not
portable oracle cases. Zero-sized images, including zero with a negative other
dimension, and preserved input tails are included.
