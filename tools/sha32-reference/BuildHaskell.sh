#!/bin/sh
set -eu
export PATH=/tmp/kotdoc-haskell/ghc/bin:$PATH
export CABAL_DIR=/tmp/kotdoc-haskell/cabal-state
exec /tmp/kotdoc-haskell/cabal exec --project-file=/tmp/kotdoc-prime-haskell-oracle.project --builddir=/tmp/kotdoc-prime-haskell-build -- ghc -O2 -hide-package crypton-2.2.0 -package crypton-2.0.1 -package ram -package aeson -package hspec -package QuickCheck -itools/sha32-reference -ivendor/crypton-2.0.1/tests -outputdir /tmp/kotdoc-sha32-haskell-objects -o /tmp/kotdoc-sha32-haskell-oracle tools/sha32-reference/Sha32Reference.hs
