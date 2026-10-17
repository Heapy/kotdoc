#!/bin/sh
set -eu
export PATH=/tmp/kotdoc-haskell/ghc/bin:$PATH
export CABAL_DIR=/tmp/kotdoc-haskell/cabal-state
build() {
 /tmp/kotdoc-haskell/cabal exec --project-file=/tmp/kotdoc-prime-haskell-oracle.project --builddir=/tmp/kotdoc-prime-haskell-build -- ghc -O2 -hide-package crypton-2.2.0 -package crypton-2.0.1 -package aeson -package hspec -itools/rsa-types-reference -ivendor/crypton-2.0.1/tests -outputdir "/tmp/kotdoc-rsa-types-$1-objects" -o "/tmp/kotdoc-rsa-types-$1" "tools/rsa-types-reference/$2"
}
build oracle TypesReference.hs
build data-oracle DataReference.hs
build secrecy SecrecyMain.hs
build export ExportSecrecy.hs
