Build latex-citation-math-reference with the checked-in Cabal project/freeze and
GHC 9.12.2 against the complete prepared Pandoc 3.12.1 library. The prepared
library changes only the exposure of HTML.Types, Parsing.Capabilities and TeX;
the generator verifies all 2,888 tracked original files.

OriginalCitation.hs and OriginalMath.hs retain the complete original modules.
Only their module names, Parsing import and private helper exports change.
OriginalParsing.hs, OriginalMacro.hs and KernelObserver.hs are byte-identical
to the protected macro observer. StateObserver.hs changes only the module name
of the protected parsing observer; both input extractors are unchanged copies.
The generator checks every transformation and records hashes of the observers,
original implementation, original reader/command tests and license notice.

LaTeXCitationMathReference.hs supplies explicit inline/block callbacks and
observes the original module functions: complete returned ASTs, all LaTeX state
fields, token remainder, position, raw capture, errors and global reports. Its
rich callback supports macro definitions, citation commands, emph/textbf, groups
and literal tokens. It is test scaffolding, not the full readLaTeX grammar.
Original reader AST and command expectations remain independent and pending.

Deep finite witnesses compare SHA-256 of the complete original AST Show result
and final state for dollar nesting, citation-key streams, nested math modes and
theorem block walks, up to 100,000 levels/items. Ordinary AST cases compare full
JSON values. The Kotlin test compares JSON numbers as exact decimals, matching
Aeson Scientific equality across fixed/exponent spellings without a tolerance.

Capture: tools/import-latex-citation-math-fixtures.py --oracle BINARY --prepared DIR
where DIR contains pandoc/. Revalidate with --check-source. Full upstream source
and license notices remain in the pinned submodules and packaged JAR notices.
