# Original dynamic image and palette oracle

Compiles unchanged JuicyPixels 3.3.9 with GHC 9.12.2 and frozen dependencies.
Calls original dynamicMap, dynamicPixelMap, DynamicImage equality,
palettedAsImage and palettedToTrueColor. The adapter supplies polymorphic test
callbacks, creates original vector slices, serializes original results and
observes sharing using ForeignPtr/offset and StableName. It contains no dynamic
dispatch, equality, palette expansion or image mapping algorithm in place of
those upstream functions. Pattern matching only serializes resulting variants.

All 14 DynamicImage variants are supported; YCbCrK8 is deliberately absent,
matching the original type. Palette' is exercised for all 15 pixel types and
paletted expansion for exactly the four original variants. Palette metadata may
disagree with actual storage. Captures use only valid in-buffer palette indexes,
including all 256 unsigned byte indexes. Invalid unsafe native palette reads and
uninitialized row-map output are excluded. Empty/negative/overflow dimensions,
whole-image/data/palette exceptions, constant callbacks and tag-only observations
expose evaluation demand without undefined native accesses. Float data uses raw
Word32 serialization and numeric original equality. Palette sharing observations
force the palette vector even for metadata results.

Source modules remain optimized with -O2; only the dispatch adapter disables
automatic specialization/SpecConstr. The original GIF/PNG palette codec tests
remain distinct pending integration work, not claimed completed by helper tests.

Original NFData DynamicImage observations force the image/vector record without
reading component bytes, including malformed shape metadata and deferred errors.
