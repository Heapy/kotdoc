# Complete pinned Muse reader reference

The development oracle compiles the complete1012-line original Muse reader at
Pandoc e51c9c6054c8f4ec5c3209d5abe10939dfe2963e with pandoc-types
b2cbcefc4547296f47b8120740997a9c021d756f. GPL copyright notices are retained.
prepare.py performs only explicitly checked module/import/test-observation wiring
changes. All250 original expected expressions and inputs remain unchanged;
ReaderReference executes those assertions before emitting independent AST values.
Production conversion uses Kotlin only.

Build with `python3 tools/haskell-muse-reader-reference/build-cached.py` using the
existing local GHC9.12.2/full Pandoc cache. Capture is explicit and refuses expected
payload overwrites. The immutable fixture manifest pins upstream implementation,
helpers, test and command sources, transformed modules, adapter/importer/capture
recipes, the original full CLI binary SHA256, all actual reused Kotlin helper sources and character resources, and all
fixture payload bytes. See SOURCE-MATRIX.md for complete function/branch mapping
and the distinction between library parsing and application normalization.

Read-only verification:

```
python3 tools/import-muse-reader-fixtures.py --check-source
python3 tools/import-muse-reader-fixtures.py --verify-oracle --oracle /tmp/kotdoc-muse-reader/oracle
python3 tools/check-muse-cli.py --cli /path/to/installed/cli
python3 tools/check-muse-cli.py --original-cli --cli /path/to/pinned/test-pandoc
```

The original application observations are captured using the cached full original
`test-pandoc --emulate` executable built from the pinned checkout, with
`pandoc_datadir` set to the pinned data directory. Capture scripts preserve exact
exit/stdout/stderr. CLI library comparisons decode JSON structurally; diagnostics
and all complete command/option/boundary observations compare exact bytes.
Command expectations come from the original Tests.Command Markdown extraction,
not raw fence regexes; App.ensureNl controls final application newlines.

This checkpoint covers Muse reader only. Muse writer164 original tests/two Old
goldens/4172 commands, RST writer22 original tests and the remaining text formats,
shared Unicode17 identifier drift local:612, general metadata CLI local:621 and
all unrepresented global Lua/filter/integration requirements remain open.
