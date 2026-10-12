# kotdoc

Native Kotlin document conversion, porting Pandoc and then calibre's MOBI and
additional formats. **Work in progress: this is not yet a replacement for either project.**

The complete goal is to port Pandoc, including its tests, and then port calibre's
MOBI support, additional formats missing from Pandoc, and their tests. No external
Pandoc or calibre process is used by the implemented Kotlin code.

## Current implementation

- Typed document model covering every Pandoc 1.23 AST constructor.
- Pandoc JSON reader and writer, including metadata, citations, tables, figures,
  API version validation, and ordered/duplicate attributes.
- All 56 literal JSON examples from `pandoc-types`, with independent typed Kotlin
  expectations and byte-for-byte encoder checks (112 individual checks).
- Additional Unicode, malformed input, API compatibility, and recursive
  roundtrip tests. Current suite: 118 tests, no skipped tests.
- Initial JSON-to-JSON CLI. Other input/output formats are not implemented yet.

See [the porting ledger](docs/PORTING.md) for the full remaining scope and caveats.
Pinned upstream sources and original tests live in Git submodules; preserving
them is **not** counted as implementing or passing them.

## Build

Requires JDK 21 and Python 3 for source-fixture verification.

```sh
git clone --recurse-submodules https://github.com/Heapy/kotdoc.git
cd kotdoc
python3 tools/import-pandoc-json-fixtures.py --check
./gradlew test :cli:installDist
cli/build/install/cli/bin/cli --from json --to json input.json --output output.json
```

The installed launcher is currently called `cli`. Use `-` or omit the input
filename to read standard input. Errors are written to standard error and return
a nonzero exit status. Only `json` is supported at this stage.

## Upstream baselines and licensing

| Source | Baseline | Pinned commit |
|---|---|---|
| Pandoc | 3.12.1 | `e51c9c6054c8f4ec5c3209d5abe10939dfe2963e` |
| pandoc-types | 1.23.1.2 | `b2cbcefc4547296f47b8120740997a9c021d756f` |
| calibre | 9.16.0 | `61c4027d2bfe737e0177e09952ee4942042a5892` |

Project license: GPL-3.0-only; see [LICENSE](LICENSE). The existing ported
`pandoc-types` model and JSON tests retain their BSD-3-Clause notices. Original
Pandoc sources are GPL-2.0-or-later; calibre has GPLv3 and per-file notices.
Upstream notices are retained in the source submodules and [licenses](licenses).
Every subsequent port must preserve the applicable source attribution.
