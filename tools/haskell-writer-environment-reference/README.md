# Native writer environment/date oracle

This executable observes the pinned Pandoc `normalizeDate`, `setupTranslations`,
`lookupMetaString`, and `setTranslations` definitions. All their bodies and their
stringification dependencies are byte-exact projections of the original sources.
`Text.Pandoc.Class.PandocMonad` and the narrow Citeproc type boundary come from the
previous protected common-state oracle unchanged. Other listed Pandoc modules are
compiled directly from their pinned upstream paths. The IORef/JSON/state instance
is transport and an observable effect boundary, not a replacement implementation.

`time ==1.14` is the original package supplied with the frozen GHC 9.12.2 compiler.
The complete release source and tests are retained under `vendor/time-1.14`, with
its license also distributed in the Kotlin JAR. Kotlin only specializes the date
formats actually used by `normalizeDate`; this does not claim a port of all TimeLib.

Original release archive: `https://hackage.haskell.org/package/time-1.14/time-1.14.tar.gz`.
SHA-256: `dfa0fe7cc9728e20d123a067161fa23ee27a5b180d58fa09d7738cb8ad737f3e`.
Every retained file is byte-identical to that archive. No earlier protected oracle
or fixture corpus is modified by this stage.

Build/freeze from this directory with the same GHC/Cabal setup as the other native
oracles. Use an external `--builddir`, then pass the built executable to
`python3 tools/import-writer-environment-fixtures.py --oracle ...` from the repo root.
Run `--check-source` to verify source provenance and the independent corpus.
