# RST reader prerequisite: CSV and grid tracing

This checkpoint supplies the reader-used low-level parsing closure, not an RST
reader. The full1962-line reader/37 originals/3Old goldens/runtime/CLI remain in
SOURCE-MATRIX.md. Incomplete grammar and runtime drafts stay outsidecore.
No partial format is advertised in the CLI registry.

Production contains Kotlin-only RstCsv and RstGridTable; upstream is a development
oracle only. CSV.hs has91 lines and no dedicated upstream unit module. All CSV
options, empty first-cell behavior, quote/escape/keep-space/delimiter and newline
errors follow the original parser combinators. Native observations independently
supply250 boundary/option cases; expected values never come from Kotlin.

The actual cached Pandoc dependency is gridtables0.1.1.0 (693 source lines across
GridTable/Parse/Trace/ArrayTable). All27 original package assertions pass natively.
The Kotlin factory executes24 original parser/line cases, the original rows-array
accessor case, and the two exact post-grid parser compositions. The initial two
GT/rest probes remain extra observations; their expectations were not replaced.
Additional cases cover alignment/body separators, wide/zero-width characters,
malformed parsers, missing/short last columns and raw tracing. Four deep originals
cover CSV10000 rows/65536 quoted characters and grid4096 rows/4096 columns.
Total331 dynamic assertions pass at512MiB/256KiB; no Unicode-domain test counts
are folded into that number.

The internal representation exposes the reader-used rows/spans/header/footer/
column-width data. It does not claim the general gridtables public Array API or
unused polymorphic mapCells contract. The higher Pandoc Parsing.GridTable302-line
adapter, normalization/cell-block callbacks and simple-table helpers remain part
of the full reader implementation. Their absence is explicit, not hidden by the
low-level package test count. Standalone CSV reader/CLI is also not registered.

Source predicates/positions and tab behavior in the full reader must use its
pinned text2.1.2/GHC Unicode16 profile. GridTable.Parse uses the original standard
Parsec character primitives, while CSV uses Sources primitives on a Text stream;
this distinction is retained. DisplayWidth is the existing independently ported
doclayout charWidth/realLength dependency, not a host terminal width estimate.

Preparation makes only the declared module/import transformations. The original
reader and all37 literal tests are copied for the next checkpoint; their native
capture is not Kotlin reader parity. SourceCSV is an exact module-name change.
OriginalGridTests preserves all assertions and input expressions: an observation
print in parse' exposes their text, and OverloadedStrings mirrors Cabal defaults.
The complete unmodified package source/test/Cabal/license and tar checksum are
retained in original-gridtables and gridtables-provenance.json.

Read-only proof commands:

```
python3 tools/haskell-rst-reader-reference/helper-checker.py --check-source
python3 tools/haskell-rst-reader-reference/helper-checker.py --verify-oracle
```

Build only when reproducing the development oracle:

```
python3 tools/haskell-rst-reader-reference/prepare.py
python3 tools/haskell-rst-reader-reference/build-cached.py --main HelperReference.hs --output /tmp/kotdoc-rst-reader/helper-oracle
python3 tools/haskell-rst-reader-reference/build-cached.py --main OriginalGridTests.hs --output /tmp/kotdoc-rst-reader/original-grid-tests
```

Only cached pinned packages are linked; no production subprocess is introduced.
The MIT notice is packaged alongside the existing Pandoc GPL/Parsec notices.
Full618/Pandoc and later calibre goals remain open.

The22 original grid parser cases additionally retain full ArrayTable equality
observations: bounds, every ContentCell/ContinuationCell pointer, spans and
column specs, not just the reader's rows view. These appended22 fixtures leave
all earlier309 payload bytes intact. Raw array access preserves source partial
bounds behavior; no claim about arbitrary bottom/undefined array values is made.
