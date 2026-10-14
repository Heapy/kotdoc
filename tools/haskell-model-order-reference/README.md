# Native Eq/Ord reference

This test-only executable compiles unchanged doctemplates 0.11.1, doclayout 0.6
and their pinned dependency closure with GHC 9.12.2. TemplateAdapter is copied
byte-for-byte from the existing independently validated native model transport.
Expected equality, inequality, compare, four relations, min and max all come
from the original type instances, not a parallel comparison implementation.

The targets include original Text, Int64, Haskell String Char sequences, and
Double transported as raw Word64 bits. A custom native Probe Eq/Ord instance
throws for selected target arguments, proving the original short-circuit and
argument-order behavior rather than relying only on total targets.

GHC emits compare-only instances for Template/Doc, while Attr derives compare
and (<); the other methods use the original class defaults. This distinction
is observable for NaN and partial targets. Attributed derives its instances via
Seq; containers 0.7 checks lengths before element equality. Compiler-derived
instances can be inspected by rebuilding the reference in a separate builddir
with --ghc-options='-ddump-deriv -dsuppress-all'.

Build with `cabal build model-order-reference
--project-dir=tools/haskell-model-order-reference
--builddir=/tmp/kotdoc-model-order-build`, then pass the resulting executable to
`python3 tools/import-model-order-fixtures.py --oracle PATH` from the repo root.
CI checks all source/input hashes and runs every Kotlin comparison. These native
programs are not production conversion dependencies. Remaining Read/Data/Generic
and other Pandoc/calibre obligations stay separate in docs/PORTING.md.
