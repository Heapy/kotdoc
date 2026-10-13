# Original pixel/image promotion oracle

Compiles unchanged JuicyPixels 3.3.9 using GHC 9.12.2 and the frozen Cabal
closure. All 21 non-identity ColorConvertible instances in Codec.Picture.Types
are called directly, through both promoteImage and pixelMap promotePixel.
The adapter has no promotion, channel scaling or alpha-handling formulas.

Requests are (operation,kind,path,width,height,values). Operation names describe
the source and target, for example y8-rgb16 or ya8-rgba8. Kind ramp generates
deterministic packed input values; other kinds use values directly. Float input
values are raw Word32 IEEE-754 bit patterns. Output is captured as hex, with
explicit little-endian Word16 and raw Float-bit serialization. Large results
are stored as SHA-256 and exact byte length; other results are retained verbatim.

Identity operations call the original same-type promoteImage/convertImage or
promotePixel/convertPixel. The original and returned objects are evaluated and
compared using GHC stable names; shared=true is captured alongside all bytes
and dimensions. Identity images include unused data, empty and short vectors,
negative dimensions and large dimensions, since original id does not inspect
dimensions or allocate another image. Non-identity malformed vectors and two
negative dimensions can access unchecked/uninitialized native memory and are
not used as portable reference cases.

The importer covers all scalar Word8 and Word16 values, every YA8 pair, every
RGB8 triple for all four promotions, channel ranges/permutations for multi-channel
Word16/RGBA inputs, boundary Cartesian products, seeded images, tails, and
Float signs/exponents/mantissa boundaries, subnormals, infinities and NaN payloads.
It does not claim exhaustive coverage of complete multi-channel Word16/RGBA or
Float32 domains. The original upstream test runner is hashed as source evidence;
remaining image encoder/decoder branches in that runner still need their own ports.
