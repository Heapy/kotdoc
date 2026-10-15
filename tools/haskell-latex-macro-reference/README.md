Build latex-macro-reference using the checked-in Cabal project/freeze, GHC 9.12.2
and the same complete prepared Pandoc 3.12.1 library as the preceding LaTeX
observer. The only prepared-library changes expose HTML.Types,
Parsing.Capabilities and TeX; implementation files are unchanged.

OriginalMacro.hs retains the complete pinned Macro.hs, changes its module and
Parsing import names, adds exports for letterize/xparseArgSpecs, and uses a LINE
pragma for canonical pattern-error source locations. OriginalParsing.hs is the
unchanged complete-module observer from haskell-latex-kernel-reference.
KernelObserver.hs is that observer's transport with only its module name changed.
The fixture generator checks every transformation byte-for-byte.

LaTeXMacroReference.hs drives original macroDef and the token/group parser, and
observes return values, state, remaining input, positions and global reports.
It introduces no definition or expansion implementation. The program driver is
test scaffolding, not the full document grammar. Original source AST/command
assertions stay independent and are still required by the porting objective.

Capture with tools/import-latex-macro-fixtures.py --oracle BINARY --prepared DIR.
The prepared directory contains pandoc/. All original tests and licenses remain
in the pinned submodules and packaged notices. Revalidate with --check-source.
