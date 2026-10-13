# Original HDR helper oracle

Builds unchanged JuicyPixels 3.3.9 with GHC 9.12.2 and frozen dependencies. Calls
original gammaCorrection/toneMapping directly. tone-pixels batches construct
independent 1x1 images, call original toneMapping on each, and concatenate outputs;
no tone/gamma or brightness algorithm is copied into the adapter.

Float inputs/outputs use raw Word32 bits. Capture platforms are macOS/ARM64 and
Linux/x86-64: original gammaCorrection uses the host system Float powf primitive,
whose finite rounding can differ from double pow and between system libraries.
Original component bytes/hashes are retained. Tone comparison permits only
arithmetic NaN payload/sign differences; finite numbers, signed zeros and
infinities remain bit exact. Gamma expectations match the native capture platform.

Generated batches cover every sign/exponent at six mantissa boundaries, a full
Cartesian boundary cube and a deterministic positive-finite component sequence.
Small cases cover deferred image/data/parameter failures, output metadata, empty,
negative and Int64 overflow dimensions, vector slices/tails and max brightness
ordering. Only defined original reads and initialized outputs are observed.
Empty tone output can avoid brightness evaluation even with nonempty dimensions.
No original HDR helper assertions occur in the retained JuicyPixels test runner;
these are independent original-function expectations.
