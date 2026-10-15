Build latex-siunitx-reference with this Cabal project/freeze, GHC 9.12.2 and
the complete prepared Pandoc 3.12.1 library. OriginalSIunitx.hs retains the full
pinned SIunitx module. Changes are limited to the module/Parsing import names,
private-helper exports and importing PandocMonad through its public Class
re-export. No parsing or formatting implementation is extracted or replaced.

LanguageObserver.hs changes only the module name of the protected preceding
driver. Other original modules and transport dependencies compile directly from
the protected language/citation-math folders. The generator checks all packaging
transformations, hashes the complete observer dependency tree, and validates all
2,888 original prepared files. Only pandoc.cabal differs, exposing HTML.Types,
Parsing.Capabilities, TeX and Class.CommonState. The immutable Parsec archive
and all its vendored source/test/license bytes are verified before capture.

The main driver supplies explicit literal/group/formatting/macro/citation/
language/SIunitx callbacks. It is test scaffolding, not the complete readLaTeX
document grammar. All original reader/command inputs and expectations remain
unchanged; full-reader AST, CLI and report assertions are still required.
Ordinary observations include full ASTs, all parser state fields, remaining
tokens, position, raw capture, errors and common-state/log/cache values.

Fixtures exercise every command, unit and modifier, exponent prefix/suffix/infix
and duplicate options, numeric uncertainty/exponent grammar and fallback, lists,
ranges, angles, malformed input and stateful/failing callbacks. All original
Native inline lists exercise the AST hyphen walk and conditional spacing.
Intentional empty-successful callbacks retain Parsec's original many-contract
exception rather than hiding it. Unicode digest witnesses cover every Haskell
Char through number formatting; deep finite witnesses compare hashes of full
original AST Show results and final parser/common state. JSON numeric equality
uses exact decimal values without a floating tolerance.

Capture: tools/import-latex-siunitx-fixtures.py --oracle BINARY --prepared DIR
  --parsec-archive PARSEC_TAR_GZ
where DIR contains pandoc/. Revalidate with --check-source. Original sources and
full license notices remain in pinned submodules/vendor trees and JAR notices.
