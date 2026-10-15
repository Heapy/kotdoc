# Independent original TeX macro and Unicode mapping oracle

Builds the complete unchanged retained texmath 0.13.3.1 package with GHC 9.12.2.
The main transports original Readers.TeX.Macros and Unicode.ToTeX results only.
It supplies no parsing, expansion or conversion algorithm. Raw codepoint arrays
preserve Haskell String surrogate Chars separately from Text normalization.
Parser observations include consumed/empty replies, unchanged user state,
remaining raw input, source positions, sorted error messages and pretty errors.

Every ordered mapping row and the complete original command map are captured.
Range witnesses execute original conversion and foldr renderTeX, hashing output
as big-endian 32-bit characters; they do not depend on Kotlin output. Original
TeX test inputs exercise macro preprocessing here, not the pending full reader.
The frozen Cabal plan and source/fixture hashes are retained with the corpus.
