#!/bin/sh
set -eu
export PATH=/tmp/kotdoc-haskell/ghc/bin:$PATH
export CABAL_DIR=/tmp/kotdoc-haskell/cabal-state
case "${1:-ordinary}" in
 ordinary) source=OaepReference; output=/tmp/kotdoc-oaep-haskell-oracle; objects=/tmp/kotdoc-oaep-haskell-objects;;
 demand) source=OaepDemandReference; output=/tmp/kotdoc-oaep-demand-oracle; objects=/tmp/kotdoc-oaep-demand-objects;;
 random) source=OaepRandomReference; output=/tmp/kotdoc-oaep-random-oracle; objects=/tmp/kotdoc-oaep-random-objects;;
 callback) source=OaepCallbackReference; output=/tmp/kotdoc-oaep-callback-oracle; objects=/tmp/kotdoc-oaep-callback-objects;;
 access) source=MgfAccessReference; output=/tmp/kotdoc-mgf-access-oracle; objects=/tmp/kotdoc-mgf-access-objects;;
 *) exit 2;;
esac
exec /tmp/kotdoc-haskell/cabal exec --project-file=/tmp/kotdoc-prime-haskell-oracle.project --builddir=/tmp/kotdoc-prime-haskell-build -- ghc -O2 -hide-package crypton-2.2.0 -package crypton-2.0.1 -package ram -package aeson -package hspec -package QuickCheck -itools/rsa-oaep-reference -ivendor/crypton-2.0.1/tests -outputdir "$objects" -o "$output" "tools/rsa-oaep-reference/$source.hs"
