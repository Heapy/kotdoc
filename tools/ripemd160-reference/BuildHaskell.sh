#!/bin/sh
set -eu
export PATH=/tmp/kotdoc-haskell/ghc/bin:$PATH
export CABAL_DIR=/tmp/kotdoc-haskell/cabal-state
case "${1:-ordinary}" in
 ordinary) source=Ripemd160Reference.hs;output=/tmp/kotdoc-ripemd160-haskell-oracle;objects=/tmp/kotdoc-ripemd160-haskell-objects;;
 borrow) source=Ripemd160BorrowReference.hs;output=/tmp/kotdoc-ripemd160-borrow-oracle;objects=/tmp/kotdoc-ripemd160-borrow-objects;;
 access) source=Ripemd160AccessReference.hs;output=/tmp/kotdoc-ripemd160-access-oracle;objects=/tmp/kotdoc-ripemd160-access-objects;;
 
 *) exit 2;;
esac
exec /tmp/kotdoc-haskell/cabal exec --project-file=/tmp/kotdoc-prime-haskell-oracle.project --builddir=/tmp/kotdoc-prime-haskell-build -- ghc -O2 -hide-package crypton-2.2.0 -package crypton-2.0.1 -package ram -package aeson -package hspec -package QuickCheck -itools/ripemd160-reference -ivendor/crypton-2.0.1/tests -outputdir "$objects" -o "$output" "tools/ripemd160-reference/$source"
