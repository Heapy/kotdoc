# GHC 9.12.2 Semigroup helper source

`GHC/Internal/Data/Semigroup/Internal.hs` is the complete, unchanged file from
the `ghc-9.12.2-release` tag:

https://github.com/ghc/ghc/blob/ghc-9.12.2-release/libraries/ghc-internal/src/GHC/Internal/Data/Semigroup/Internal.hs

`Translations.stimes` is newtype-derived from `Map.stimes`, which uses
`stimesIdempotentMonoid`. The source distinguishes negative, zero and positive
arbitrary-size integral multipliers. The complete original ghc-internal license
is retained as `LICENSE`; the same notice is already shipped in the core JAR.

This source file does not claim a port of the remaining Semigroup runtime or
GHC's generic representation machinery.
