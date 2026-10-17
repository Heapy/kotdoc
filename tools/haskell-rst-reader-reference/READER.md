# Pinned RST reader checkpoint

This ports the complete 1,962-line Pandoc RST reader grammar and its reader-used
GridTable adapter to Kotlin. `READER-FUNCTIONS.json` maps all 133 source type
signatures, including functions combined into one Kotlin parser. `READER-MATRIX.md`
records the source ranges, state/effects, executed evidence and remaining routes.
It does not claim the complete Pandoc application or local:618 is finished.

The original source is Pandoc e51c9c6054c8f4ec5c3209d5abe10939dfe2963e; the
cached original oracle uses GHC 9.12.2, text 2.1.2, time 1.14 and gridtables
0.1.1.0. `build-cached.py` verifies the selected cached package unit IDs. Full
original source, dependency licenses and original expectations remain independent
of the Kotlin implementation. The accepted CSV/grid helper checkpoint and its
historical manifest were not rewritten.

The first packaged focused run executed 5,886 tests: 2,830 new reader/source
observations, 331 accepted helper observations and 2,725 RST writer regressions.
The 2,830 reader cases comprise 37 original unit assertions, 1,887 reader AST/error
observations, 9 deep original Show observations, 748 time formatting observations,
56 span observations, 35 widths, 42 block flattening, 4 deep flattening, 7 math
chunks and 5 Unicode transformations. They include all three Old fixtures after
independently checked original App tab conversion and 85 original command-input
AST bindings. Those 85 AST bindings are not complete application commands.

A separate additive corpus observes 35 original callback order/demand cases.
`CallbackReaderOracle.hs` forwards all PandocMonad primitives to PandocPure and
records file and URL calls in PureState, which survives typed catchError. For the
public timestamp callback granularity, the original getTimestamp entry is marked
when it looks up SOURCE_DATE_EPOCH; its subsequent clock read is part of the same
callback. No parsing or expected outputs are implemented by this observer.
Unused/reused notes, citations and substitutions, includes, literal/code includes,
failed and recursive includes, raw-file directives and CSV/data URI routes are
covered. The unchanged production classes pass these cases at 512 MiB/256 KiB,
and the additive JUnit run passed 35/35. Its compiled RST class bytes equal the
immutable packaged classes. The total new reader/source case count is 2,865.

The CLI checker reads tooling fixtures from `reader-fixtures/cli`; these are
external verification data, not inputs packaged in the production jar. The
complete original 85-command capture matches Tests.Command goldens. Of these,
74 have an available output writer and application route. Eleven depend on absent
writers (including LaTeX, whose reader is available) or citeproc. The CLI proof
also covers 128 newly captured App cases, 60 shared tab-stop cases, all three Old
complete commands, 12 accepted Muse/Doku controls and both 4172 Muse commands.
The original Old stdout is byte-identical to its verbatim source golden.

Run `reader-checker.py --check-source` for finite read-only provenance checks, or
`--verify-oracle` for the retained original observations. Run installed CLI
verification through `run-cli-durable.py --dist DIST --evidence NEW_DIRECTORY`.
The wrapper retains command, PID/start time, checker/input/jar hashes, combined
logs and an atomically written terminal exit code. It refuses to overwrite an
older run. Exact exit/stdout/stderr are compared, not just combined text.

## Explicit remaining integration boundaries

* The RST library accepts source-equivalent file/fetch/timestamp callbacks and its
  default Pure resource routing supports MediaBag, data URI, source URL and
  resource paths. The default CLI HTTP backend is not connected. Class.IO.HTTP
  requires the original TLS manager/transport and exception profile; the existing
  legacy Java HTTP route is not substituted. `http-pending-repro.json` retains an
  actual original exit 61 versus the explicit unsupported Kotlin exit 1 for an
  invalid URL scheme without making a network request. Existing tasks 612/613
  must account for this route before full application/runtime completion.
* The finite POSIX CLI file boundary maps observed missing file, directory,
  ENOTDIR and NUL-path conditions, and contains a permission-error mapping. The
  permission-error host branch was not exercised by this finite corpus. It is not
  a claim that arbitrary
  JVM IOException values reproduce every GHC IOException/OS profile. Remaining
  generic IO boundary work belongs to the shared runtime closure.
* Negative library tab-stop values can make the original parser nonproductive on
  some raw tab input; the original exploratory process was terminated after this
  was established. The App accepts only positive tab-stop values and reports
  PandocOptionError (exit 6) otherwise. No artificial library result was invented.
* Existing whole-application verbosity, metadata, template and absent-format
  dependencies remain open. No historical reader/writer manifest is rewritten
  to claim its old Main snapshot equals the new integrated Main.

The standalone bounded proof and original oracle are development checks only.
Production conversion has no Haskell, native Pandoc CLI or subprocess dependency.

The packaged Builder/PandocMany source baseline is retained as exact committed
blobs in `reader-fixtures/dependency-snapshots` and `historicalDependencyPins`.
Later authorized Builder work can provide a separate current integration binding
and fresh consumer XML; it must not rewrite this earlier immutable proof.
