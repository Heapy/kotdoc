Build latex-language-reference using this Cabal project/freeze, GHC 9.12.2 and
the complete prepared Pandoc 3.12.1 library. OriginalLang.hs is the complete
pinned Readers/LaTeX/Lang.hs with only its module and Parsing import renamed.
CitationMathObserver.hs changes only the module name of the protected preceding
observer. Other source modules are compiled directly from that protected folder.
The generator checks these transformations and hashes all observer dependencies.

The prepared library preserves all 2,888 tracked files, with one packaging-only
exception: pandoc.cabal exposes HTML.Types, Parsing.Capabilities, TeX and
Class.CommonState. The fourth exposure allows observing the actual original
common state and translation cache without substituting a PandocMonad class.
No implementation is extracted, rewritten or replaced. Earlier prepared source
trees and corpora are unchanged.

The main driver supplies explicit component callbacks and runs original language
and quote commands. Its rich callback handles literal tokens, groups, emph/
textbf, macro definitions, citations and language/quote commands; it is not the
full readLaTeX grammar. Original reader/CLI AST and report assertions remain
independent and required. The driver records complete ASTs, all parser state,
position, token remainder, raw capture, errors, common state and translation
cache. Nondefault common fields are seeded to detect accidental replacement.

Fixtures cover every mapping, Babel aliases, option spelling/order/bracket and
whitespace behavior, contexts, cache equality on all Lang fields, backtracking,
callback changes/failures/reports, all original Native inline lists and the
original LaTeX reader/command inputs through the explicit callbacks. Deep finite
composition witnesses compare hashes of complete original AST Show output and
final state up to 100,000 levels. Additional original Parsec Error observations
check all diagnostic messages/order/Show after 100,000 left/right-associated
merges, and the unknown/position observers on shared exponentially large lists.
The generator verifies the exact Parsec Hackage archive/provenance and all its
vendored source/test/license bytes when capturing. JSON numbers compare as exact decimal values,
matching Aeson Scientific equality without a floating tolerance.

Capture: tools/import-latex-language-fixtures.py --oracle BINARY --prepared DIR
  --parsec-archive PARSEC_TAR_GZ
where DIR contains pandoc/. Revalidate with --check-source. Original upstream
sources and full license notices remain in pinned submodules and JAR notices.
