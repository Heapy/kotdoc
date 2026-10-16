# Complete original Muse writer reference

This development-only oracle compiles the complete pinned732-line
Text.Pandoc.Writers.Muse module (GPL-2.0-or-later, Alexander Krotov2017–2020).
prepare.py checks exact original source and test SHAs and changes only module /
hidden-import / test-observation wiring. The reused complete URI module differs
from the pinned original only in module name; the read-only verifier asserts this.
All164 original literal expectations remain unchanged and are executed by the
original module before capture. Original Tests.Helpers.test compares lines;
separate original raw outputs additionally preserve exact terminal newlines.

Build with `python3 tools/haskell-muse-writer-reference/build-cached.py`, reusing
local GHC9.12.2 and the pinned complete Pandoc library cache. No network rebuild
or native production conversion dependency is required. Expected payloads come
only from this original module or the complete pinned original test executable.
The372 document inputs reused from the accepted Doku oracle are input ASTs only;
Muse expectations are newly produced by this complete original writer. Both old
goldens, their native inputs, original test modules, default.muse and4172 source
are copied verbatim and independently pinned.

Source/function/state/options/test coverage is in SOURCE-MATRIX.md. The complete
3378-observation persistent proof passes512MiB heap/256KiB stack, plus one separate
exhaustive assertion for the five actual Data.Char predicates over all1,114,112
Haskell Char values. JUnit totals3379, not a million extra tests. Native alpha
marker LongMIN/MAX probes would require a source linear drop of~9e18 values;
finite cycles are tested, numeric/Roman Int64 bounds are captured separately.

Read-only checks from the repository root:

```
python3 tools/import-muse-writer-fixtures.py --check-source
python3 tools/import-muse-writer-fixtures.py --verify-oracle --oracle /tmp/kotdoc-muse-writer/oracle
python3 tools/check-muse-writer-cli.py --cli /path/to/installed/cli
python3 tools/check-muse-writer-cli.py --original-cli --cli /path/to/pinned/test-pandoc
```

The importer verifies exact copied modules, actual helpers/resources and tool
sources, deterministic request recipes, retained payload bytes, source Old golden
bytes and original command extraction. Verification never rewrites expected values.
The original full application executable SHA is retained separately from the
copied writer-module oracle SHA. Explicit capture scripts refuse overwrites.
The frozen manifest can be refreshed only explicitly after authorized source/tool
changes; it does not regenerate any expected payload.

CLI observations preserve exact exit/stdout/stderr and output-file bytes. Complete
4172 commands use original Markdown parsing and Tests.Command lines/unlines
extraction, with App.ensureNl application output behavior. The malformed custom
template contract is App.OutputSettings.templateOrThrow: PandocTemplateError exit5;
Muse handles it narrowly. Other formats' generic template-error handling, general
-M metadata621 and shared identifier drift612 remain separate open issues.

This checkpoint completes the Muse writer module and its scoped tests/application
registration, alongside the previously accepted reader. It does not close full618,
RST writer/8182, other formats or global integration/Lua/filter/options closure.

Caption demand proof uses a list with a failing Block element, not a bottom list:
pandoc-types Definition enables StrictData, so Caption already forces the list
constructor. The original grid writer leaves that element unevaluated; Kotlin
uses a known-size list whose element accessor fails. The shared recursive/eager
DocumentShared helper remains unchanged for other consumers and is a separate
remaining issue. The Muse-only helper covers every Shared block constructor and
is additionally checked against15 original complete caption-shape outputs.
