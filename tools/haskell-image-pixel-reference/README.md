# Original Pixel class and mutable-image oracle

Compiles unchanged JuicyPixels 3.3.9 using GHC 9.12.2 and frozen Cabal
dependencies. The adapter calls the original Pixel methods for all 15 pixel
types, plus thawImage/freezeImage, unsafeThawImage/unsafeFreezeImage,
createMutableImage and newMutableImage. No Pixel component routing, index
formula, bounds logic, pixel reads/writes or lifecycle implementation is copied
into this adapter. Supplied callback functions and deterministic input generators
belong to the test inputs, as they would for any caller of these higher-order APIs.

Requests are (operation,pixelType,mode,parameters,leftOrStorage,rightOrReplacement).
Parameters for access/lifecycle are width,height,x,y,componentIndex. All Int
arithmetic is the original 64-bit GHC build. Float inputs and outputs use raw
Word32 bits; Word16/Word32/Float serialization is explicitly little-endian.
Per-pixel pure operations are batched with original unsafePixelAt/unsafeWritePixel.

Checked reads and writes retain original error messages and the complete mutable
buffer after a failure, including partial writes. Invalid unsafe accesses enter
unchecked native memory and are never used as reference cases. Unaligned but
in-bounds component offsets are valid for unsafe methods. Coordinates are tested
through the original flattened index, including negative/out-of-shape coordinates
that still produce valid indices and Int64 wrapping.

Foreign pointer addresses independently observe buffer sharing for freeze/thaw.
Safe freeze snapshots are compared after subsequent mutable writes. Unsafe
freeze consumes its mutable view; unsafe thaw captures immutable contents before
consumption and observes only the resulting mutable image afterward. Empty
buffers can share native zero-length allocation addresses, so physical pointer
comparison is meaningful only for nonempty buffers. Mode view skips writes,
allowing short/empty vectors to test lifecycle copying without an unsafe access.

New mutable storage is completely initialized with supplied data before any
sample observation. Its original garbage contents are not portable expectations.
Create uses the original constant-background initializer. Negative allocation
lengths are captured; huge positive allocations are not attempted.

Arithmetic Float callbacks compare all finite/infinite/zero bits exactly and
computed NaNs by category (canonical comparison hash); raw copies/identity,
negation, reads/writes and lifecycle tests preserve every bit, including NaN
payloads. Native output bytes/hashes are retained independently.
