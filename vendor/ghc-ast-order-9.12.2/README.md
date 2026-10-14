# GHC 9.12.2 AST comparison dependencies

Original files retained verbatim from the official ghc-9.12.2-release tag:

- libraries/ghc-prim/GHC/Classes.hs → GHC/Classes.hs
- libraries/ghc-internal/src/GHC/Internal/Maybe.hs → GHC/Internal/Maybe.hs
- libraries/ghc-prim/LICENSE → ghc-prim-LICENSE
- libraries/ghc-internal/LICENSE → ghc-internal-LICENSE

Source: https://github.com/ghc/ghc/tree/ghc-9.12.2-release
Release tree: 383be28ffdddf65b57b7b111bfc89808b4229ebc.
Classes contains the actual Eq/Ord list, tuple and primitive instances and class
defaults. Maybe retains its actual data declaration and stock derivations.
Classes SHA-256: 5b69807c1974e1538eb70ac0535fdbb53005fa228814128327b689cbe97c0894.
Maybe SHA-256: bd165d3b83ffb2631f8e2ef6f1af8cf05750d224499ebc719201e7b6519e7ad3.
The ghc-internal license matches licenses/GHC-base-BSD-3-Clause.txt; ghc-prim's
complete original notice is also packaged as licenses/ghc-prim-BSD-3-Clause.txt.
These are comparison dependencies, not a claim to have ported all ghc-prim or
its tests. Containers' Map instances are retained separately in the unchanged
vendor/containers-syntax-0.7 tree.
