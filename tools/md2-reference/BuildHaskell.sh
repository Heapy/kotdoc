#!/bin/sh
set -eu
export PATH=/tmp/kotdoc-haskell/ghc/bin:$PATH
export CABAL_DIR=/tmp/kotdoc-haskell/cabal-state
case "${1:-ordinary}" in
 ordinary) source=Md2Reference.hs;output=/tmp/kotdoc-md2-haskell-oracle;objects=/tmp/kotdoc-md2-haskell-objects;;
 borrow) source=Md2BorrowReference.hs;output=/tmp/kotdoc-md2-borrow-oracle;objects=/tmp/kotdoc-md2-borrow-objects;;
 access) source=Md2AccessReference.hs;output=/tmp/kotdoc-md2-access-oracle;objects=/tmp/kotdoc-md2-access-objects;;
 caf) source=history/Md2BorrowSharedCafReference.hs;output=/tmp/kotdoc-md2-caf-oracle;objects=/tmp/kotdoc-md2-caf-objects;;
 *) exit 2;;
esac
exec /tmp/kotdoc-haskell/cabal exec --project-file=/tmp/kotdoc-prime-haskell-oracle.project --builddir=/tmp/kotdoc-prime-haskell-build -- ghc -O2 -hide-package crypton-2.2.0 -package crypton-2.0.1 -package ram -package aeson -package hspec -package QuickCheck -itools/md2-reference -ivendor/crypton-2.0.1/tests -outputdir "$objects" -o "$output" "tools/md2-reference/$source"
