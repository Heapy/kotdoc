Build with the frozen Cabal project, GHC 9.12.2 and the complete original
prepared Pandoc 3.12.1 library. OriginalTable.hs is the entire Table module,
changing only its module name, private test exports and OriginalParsing import.
InlineObserver changes only the parent observer's module name. The importer
checks these transformations, all prepared upstream files, immutable dependency
archives and all protected parent observer/source hashes.

The component callback handles paragraphs, tables, captions, labels, parbox,
inline math and the previous inline callback. It is not the full readLaTeX
grammar. Ordinary observations preserve full AST/state/errors/raw capture,
positions, remaining tokens and common-state effects. Original full-reader
AST/CLI/report assertions remain pending.

Private parser probes cover alignments/widths/repetition, prefix/suffix token
position rewriting, all table environments, rule/line breaks, cell parsers,
nested spans and caption state. Original AST values independently exercise
fixTableRows/fixTableRow and compactifyTable with ten AST Walkable shapes
and Builder.Many Block, including notes, metadata and nested tables. Deep finite witnesses
compare complete original Show hashes. Run import-latex-table-fixtures.py
with --oracle, --prepared, --parsec-archive and --unicode-cache as for Inline;
revalidate with --check-source.

The Builder.Many Block dictionary is checked separately from [Block]: the
generic Traversable instance queries each block without querying the outer
container as a block list. Caption traversal also uses every original block
tree, including nested tables/notes, and explicit matching label/space removal.

Int64 boundary probes use a finite input overhang and observe ten output
overhang elements plus the entire returned cell list. This preserves extreme
dimensions and overflow behavior without forcing the original algorithm to
linearly drop Long.MAX_VALUE entries from repeat Nothing. All ordinary row
repair cases observe the complete result. Recapture preserves the entire
previous observation prefix verbatim in meaning.

Shared.trim trims only space, CR, LF and TAB. SharedText ports isWS, trim,
triml, trimr and stripTrailingNewlines with this exact distinction. Direct
Unicode-whitespace/error probes and 17 digest witnesses compare every Haskell
Char in three string shapes through the four text functions and isWS.
