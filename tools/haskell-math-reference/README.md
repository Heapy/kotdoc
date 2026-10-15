# Original texmath expression/helper oracle

Cabal compiles the complete retained texmath 0.13.3.1 package without replacing
any implementation module. SOURCE.json records the Hackage release archive;
all 3,762 source/test files match it byte for byte, with no Cabal revision edits.
The frozen plan uses GHC 9.12.2, pandoc-types 1.23.1.2 and text 2.1.2.

MathReference.hs only transports inputs/outputs. Original TeX/MathML/OMML/Read
parsers supply expression inputs from every original .test/.native file. These
native parser results are input provenance, not a Kotlin reader implementation
or evidence that those reader/writer golden suites have all been ported.
Defined expression trees run through original Shared transformations, derived
Show/Eq/Ord, writePandoc, Unicode mapping and TeX rendering. No expected result
is obtained by invoking Kotlin. Public tables retain their source order.

The adapter forces each complete JSON result inside its exception boundary.
Exact platform allocation failures, partial/lazy bottoms and generic SYB runtime
behavior are not represented by these finite-value observations. The remaining
TeXMath readers/writers and complete original golden suites remain in scope.
