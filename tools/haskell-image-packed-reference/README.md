# Original packed-pixel oracle

Compiles unchanged JuicyPixels 3.3.9 using GHC 9.12.2 and frozen Cabal dependencies.
Calls all ten PackeablePixel instances directly: scalar Word8/Word16/Word32/Float,
YA8/YA16, RGBA8/RGBA16 and CMYK8/CMYK16. Original pack/unpack, fillImageWith,
readPackedPixelAt, writePackedPixelAt and unsafeWritePixelBetweenAt supply every
expected result. The adapter contains no packing, index rounding, filling,
slice validation or memory-operation algorithm.

Requests are (operation,pixelType,parameters,storage,values). Parameters are
width,height,viewOffset,viewLength,indexOrStart,pixelCount. Pure pack requests use
values as interleaved components; unpack requests use packed primitive values.
Memory operations create an original mutable component slice of storage and
capture the complete parent buffer afterward, including unchanged outside data
and original errors. The adapter uses original unsafePixelAt/unsafeWritePixel
only to construct/serialize pixels, and explicit little-endian serialization of
Word16/Word32/Word64/raw Float bits for interchange.

The reference reports native memory order. These captures use little-endian
native memory; the Kotlin helpers respect their host's native byte order.
Numeric pack/unpack bit positions are independent of native memory byte order.
Float operations are identity/bit copies, including all NaN sign/payload bits;
no arithmetic NaN normalization or epsilon comparison is used.

Only complete packed reads/writes inside a view and range writes whose complete
packed span fits are captured. Invalid slice checks are observed before any
unsafe memory access. Undefined native overreads/overwrites, overflowing invalid
slice parameters and negative packed reads are excluded. Misaligned component
offsets that stay inside storage are observed on the pinned native build.
Images can have dimensions unrelated to their buffers: these methods use vector
length and ignore image dimensions. Fill preserves an incomplete component tail.
