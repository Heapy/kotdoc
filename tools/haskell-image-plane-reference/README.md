# Original color-plane, transparency and luma oracle

Compiles unchanged JuicyPixels 3.3.9 with GHC 9.12.2 and frozen Cabal dependencies.
The adapter calls original unsafeExtractComponent for all 15 pixel types, all 32
named ColorPlane instances, four TransparentPixel instances (dropTransparency,
getTransparency, pixelOpacity, dropAlphaLayer), and ten LumaPlaneExtractable
instances (computeLuma and extractLumaPlane). It implements no channel extraction,
alpha removal or luma formula. Per-pixel methods are observed using original
pixelMap; whole-image methods are also called independently.

Requests are (operation,pixelType,selector,kind,width,height,values). Kind values
uses the supplied interleaved vector; other kinds generate deterministic inputs.
Float inputs use raw Word32 bits. The ya8-upstream generator reproduces the
original planeSeparationYA8Test input. Word16/Word32/Float results use explicit
little-endian serialization; original image sharing is observed using stable names.

Expected complete bytes, byte lengths and SHA-256 digests come from the original.
Large finite RGB-domain/range results are stored as full-output hashes. For
computed RGBF luma only, the importer additionally hashes NaNs canonically:
NaN payload/sign after floating arithmetic depend on native platform/compiler.
Tests compare every finite/infinite/zero result bit exactly and require every
computed NaN to remain NaN. Raw Float copies, scalar identities and plane
extraction preserve all original bits, including NaN sign/payload. No epsilon
comparison is used.

Error fixtures retain original invalid-component messages and error-before-data
ordering. Negative indices are captured only for empty output (no native read).
Short vectors and nonempty negative indices enter unchecked native reads and
are not portable oracle cases. Stride extraction with two negative dimensions
has a defined positive linear count and is captured; pixelMap operations on
two negative dimensions expose uninitialized output and are excluded. Scalar
extractLumaPlane is id and preserves the complete vector and malformed dimensions.
