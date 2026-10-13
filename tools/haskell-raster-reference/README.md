# Original raster-operation oracle

Builds unchanged JuicyPixels 3.3.9 with GHC 9.12.2 and frozen dependencies.
Calls original generateImage, withImage, generateFoldImage, pixelMap, pixelMapXY,
pixelFold, pixelFoldM, pixelFoldMap, zipPixelComponent3, Image equality and
freezeImage/thawImage, pixelBaseIndex and checked pixelAt/readPixel/writePixel. Slices use original vector V.slice/M.slice. There are no
image traversal, allocation, mapping, folding, zip, equality or slice-validation
algorithms in the adapter. Supplied callbacks compute fingerprints and generated
pixel components; original Pixel methods construct/serialize those pixels.
The unexported generateMutableImage is exercised by original generateImage.

Request fields are operation, source type, destination type, callback mode,
observation mode, parameters, parent source components and two extra component
vectors. Parameters are width, height, offset, length, initial state/seed and
second-image width/height. Float components use raw Word32 bits throughout.
Metadata observations demand only dimensions. A bad-data observation replaces
the source vector with an exception. IO callback traces expose observable
ordering and deferred errors; pure callback arithmetic uses Integer state.
Read/write captures use original vector slices and include the complete parent
after successful or partially failed writes. Int64 address arithmetic is tested
with dimensions and coordinates outside signed 32-bit range.
The bracket foldMap monoid intentionally violates associativity to distinguish
right association from a left fold. The Any monoid tests short-circuiting.

Only defined native reads/writes are captured. Buffers may contain unused tails,
and dimensions may be unrelated to data for metadata, equality, slicing and
copying. Both-negative dimensions in linear foldMap are valid when their positive
product fits the vector. Row generators with positive allocations and no pixel
writes would expose uninitialized native memory and are excluded. Int64-minimum
height with even component counts wraps allocation length to zero: row generators
skip it while generateFoldImage's original enumeration reaches a checked write
error. The analogous unsafe withImage write is excluded. Arbitrary native
out-of-bounds unsafe accesses and impractically large allocations are not portable
oracle cases. Applicative traversals and dynamic/paletted images remain pending.
