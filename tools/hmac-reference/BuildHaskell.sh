#!/bin/sh
set -eu
export PATH=/tmp/kotdoc-haskell/ghc/bin:$PATH
export CABAL_DIR=/tmp/kotdoc-haskell/cabal-state
exec /tmp/kotdoc-haskell/cabal exec --project-file=/tmp/kotdoc-prime-haskell-oracle.project --builddir=/tmp/kotdoc-prime-haskell-build -- ghc -O2 -hide-package crypton-2.2.0 -package crypton-2.0.1 -package ram -package aeson -package hspec -package QuickCheck -itools/hmac-reference -ivendor/crypton-2.0.1/tests -outputdir /tmp/kotdoc-hmac-haskell-objects -o /tmp/kotdoc-hmac-haskell-oracle tools/hmac-reference/HmacReference.hs
