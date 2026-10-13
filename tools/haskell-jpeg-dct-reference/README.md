# Original Float reference DCT oracle

Builds the unchanged JuicyPixels 3.3.9 source with GHC 9.12.2 and the frozen Cabal
closure. The adapter directly calls Codec.Picture.Jpg.Internal.FastDct.referenceDct,
then freezes its returned Int32 workspace and the Int16 input. It implements no
transform, trigonometry, summation or rounding algorithm.

Each input line is (operation,initialWorkspace,inputSamples). Operation constants
uses each input value as the level of a complete 64-sample block and returns all
64 output coefficients per level. Other operations transform the supplied sample
vector and return the entire workspace plus the unchanged entire input. Vectors
may exceed 64 elements so that untouched tails can be checked independently.
Short vectors would invoke unchecked native memory accesses and are not used.

The fixture importer covers every signed Int16 constant level, every signed
Int16 value in ramp blocks, impulses at every position, alternating/extreme and
seeded full-range blocks. Float behavior is pinned to the actual original
compiler/library build. Native Float libm implementations can differ across
platforms; no claim of equality to every possible native compiler is implied.
