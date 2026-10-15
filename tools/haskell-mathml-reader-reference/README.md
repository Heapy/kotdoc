# Complete MathML reader and legacy XML input oracle

This executable links the complete unchanged pinned texmath 0.13.3.1 and xml
1.3.14 libraries. JSON is only a transport for the public MathML reader, XML input
and lexer APIs. Its MathWire serialization comes from the already protected
math-reader oracle. The hidden EntityMap module is copied byte-for-byte; it
supplies the complete production entity table without modifying the real reader.

The separate `original-texmath-tests` executable compiles the unchanged original
`test-texmath.hs`, with all its registrations intact. From the original texmath
directory, `--pattern '$2 == "reader" && $3 == "mml"'` executes all 576 original
MathML reader goldens. Regression 252 is the remaining original MathML input
and is selected additionally by the importer (577 original tests total).
Other texmath tests remain registered and in port scope.

Build using GHC 9.12.2 and an external build directory, freeze dependencies, and
repeat the build with that freeze file. The importer verifies unchanged source,
the full original fixture set, entity data, independent observations and hashes.
