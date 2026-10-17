#!/bin/sh
set -eu
export PATH=/tmp/kotdoc-haskell/ghc/bin:$PATH
export CABAL_DIR=/tmp/kotdoc-haskell/cabal-state
for family in Demand Dictionary Random Callback Boundary; do
 lower=$(printf '%s' "$family" | tr '[:upper:]' '[:lower:]')
 /tmp/kotdoc-haskell/cabal exec --project-file=/tmp/kotdoc-prime-haskell-oracle.project --builddir=/tmp/kotdoc-prime-haskell-build -- ghc -O2 -hide-package crypton-2.2.0 -package crypton-2.0.1 -package ram -package aeson -itools/pss-reference -outputdir "/tmp/kotdoc-pss-$lower-objects" -o "/tmp/kotdoc-pss-$lower-oracle" "tools/pss-reference/Pss${family}Reference.hs"
done
/tmp/kotdoc-haskell/cabal exec --project-file=/tmp/kotdoc-prime-haskell-oracle.project --builddir=/tmp/kotdoc-prime-haskell-build -- ghc -O2 -hide-package crypton-2.2.0 -package crypton-2.0.1 -package aeson -package hspec -package QuickCheck -itools/pss-reference -ivendor/crypton-2.0.1/tests -outputdir /tmp/kotdoc-pss-source-objects -o /tmp/kotdoc-pss-sourcecase-oracle tools/pss-reference/ExportSourceCases.hs
