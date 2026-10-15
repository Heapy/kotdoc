# Original complete TeX reader and Commands oracle

Builds the complete retained texmath 0.13.3.1 package unchanged. The hidden
Commands module is a byte-identical local copy, compiled against original
exposed Types and Unicode modules. MathWire is a module-name-only copy of the
protected earlier math adapter. No Kotlin code participates in observations.

The main calls original readTeX and table functions, serializes complete Exp
constructor/field trees, original Show and complete error text. Original native
goldens are read by the original Exp Read instance and compared to reader output
before Kotlin execution. Source and transport hashes plus the frozen GHC/Cabal
plan make expectations independent of Kotlin implementation changes.
