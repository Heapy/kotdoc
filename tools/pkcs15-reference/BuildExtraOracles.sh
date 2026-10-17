#!/bin/sh
set -eu
export PATH=/tmp/kotdoc-haskell/ghc/bin:$PATH
export CABAL_DIR=/tmp/kotdoc-haskell/cabal-state
for family in Callback Boundary; do
 lower=$(printf '%s' "$family" | tr '[:upper:]' '[:lower:]')
 /tmp/kotdoc-haskell/cabal exec --project-file=/tmp/kotdoc-prime-haskell-oracle.project --builddir=/tmp/kotdoc-prime-haskell-build -- ghc -O2 -hide-package crypton-2.2.0 -package crypton-2.0.1 -package ram -package aeson -outputdir "/tmp/kotdoc-pkcs15-$lower-objects" -o "/tmp/kotdoc-pkcs15-$lower-oracle" "tools/pkcs15-reference/Pkcs15${family}Reference.hs"
done
