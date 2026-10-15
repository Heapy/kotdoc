# Independent original MathML/XML.Light oracle

Compiles the complete retained texmath 0.13.3.1 and xml 1.3.14 packages unchanged.
All original xml release files are retained at their original paths; Hackage's
revision-2 Cabal metadata is an additional file, not an algorithm modification.
MathWire.hs is a module-name-only copy of the earlier protected math transport.
The new main only transports original writeMathML, dictionary, XML output and
numeric results. It does not supply conversion algorithms.

Original native test inputs and original TeX/MathML/OMML readers provide defined
expression trees. The complete MathML writer goldens are independently checked
against original output before Kotlin is run. Input parsing from other formats
is provenance, not a Kotlin reader implementation. The adapter forces output
inside its exception boundary. Codepoint arrays preserve raw Haskell Strings,
including separate surrogate Chars; UTF8/JSON does not replace those values.

Numeric witnesses execute original Rational-to-Double conversion, printf %.3f
and exact Rational rounding to Int, including signed 64-bit wrapping. The frozen
Cabal plan fixes compiler/dependency versions. Source/fixture hashes preserve
all original inputs and the new transport independently of Kotlin code.
