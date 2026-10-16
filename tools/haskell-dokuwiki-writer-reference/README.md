# DokuWiki writer reference

The production implementation is Kotlin. This development oracle compiles the
complete 496-line pinned `Text.Pandoc.Writers.DokuWiki` module, with its original
copyright notice and behavior intact. `prepare.py` changes only the module name,
uses the public reexport of `PandocMonad/report`, and wires the existing exact
original `SourceUriPosix` module for the otherwise hidden URI import. All other
imports refer to the full pinned original Pandoc library.

Pins: Pandoc `e51c9c6054c8f4ec5c3209d5abe10939dfe2963e`, pandoc-types
`b2cbcefc4547296f47b8120740997a9c021d756f`. Writer source SHA-256:
`e0601efac714a840467d1f10a1bcf961457e3c1e61c21fa88a9fa40bfdb4ef06`.
No installed Pandoc executable supplies expected output or ASTs.

With the existing complete original-library build/package databases:

```sh
python3 tools/haskell-dokuwiki-writer-reference/build-cached.py
python3 tools/import-dokuwiki-writer-fixtures.py --check-source
python3 tools/import-dokuwiki-writer-fixtures.py --verify-oracle --oracle /tmp/kotdoc-dokuwiki/oracle
```

`build-cached.py` accepts explicit compiler, store/package database and output
paths. It reuses an existing complete build and does not fetch dependencies.
The Cabal package is also provided for an independently configured environment.

The 372 observations contain all 266 original Native documents, source-branch
witnesses for every block/inline constructor, raw block consolidation, nested
list/quote environment rules, list wrapping, all 12 language aliases, image
units/duplicate dimensions/DPI, wrapping options, header formatting removal,
metadata/template defaults and exact ordered diagnostic JSON. The five original
Old.hs goldens are copied verbatim, and the importer requires the independently
computed original writer outputs to match their bytes exactly.

Command 3824 and both 5107 cases retain their original source files and original
AST-level writer observations. Original Tests.Command extracts Markdown code
blocks with lines/unlines; raw fence regex extraction retains an extra blank
line. App.ensureNl adds a newline only when needed for nonstandalone output;
the 3824 writer body already ends in one. Library expectations remain unchanged. These do **not** certify the Kotlin full CLI commands:
DokuWiki CLI registration and the Muse reader remain pending. The 115 original
DokuWiki reader tests and the complete local:618 task remain pending. There is
no upstream `Tests.Writers.DokuWiki` unit module.

An additional **11 independently evaluated deep AST recipes** cover Emph at
4096/8192 levels, Div/quote/note/diagnostic at 4096, bullet/ordered/definition/table
at 1024 and a mixed quote/div/list path at 256. The original oracle constructs
these finite trees directly, avoiding unrelated JSON/native reader stack
limits. Their expectations live separately in `deep.jsonl.gz`; the accepted
372-observation payload remains byte-for-byte unchanged. Kotlin constructs the
same explicit recipes iteratively and checks exact output/logs both on the
normal test worker and a thread requesting only a 256 KiB stack.

The renderer uses heap continuations (`DeepRecursiveFunction`) for every block,
inline and table/list path, an iterative simple-list predicate, and immutable
shared output pieces flattened iteratively. This also avoids retaining copies
of each descendant string across suspended parent frames. The complete 383
observations pass with a 512 MiB heap and 256 KiB stack. No stack/heap size
increase or production external conversion process is involved.

A persistent bounded-resource entry point is included in the test class:

```sh
java -Xmx512m -Xss256k -cp "$KOTDOC_TEST_CLASSPATH" io.heapy.kotdoc.writers.DokuWikiWriterTest
```

Use the core main/test classes, resources and test runtime dependencies in that
classpath. It executes the same independent assertions as JUnit and exits with
an exception on failure.

`--check-source` is read-only: it verifies finite source/tool/helper inventories,
SHA-256 pins, request recipes, both fixture payloads, verbatim original goldens,
and exact copied writer/URI module transformations. The source inventory
includes original Shared, Writers.Shared, ImageSize, URI, option/template/logging
and Native/Muse reader sources and pandoc-types Definition/Builder/Walk.
`--verify-oracle` first performs those checks, verifies the recorded oracle
binary hash, then replays **all 383** original Haskell observations and compares
the serialized records exactly, without writing expectations or manifests.
`--refresh-manifest` is an explicit maintenance operation after reviewed source
changes. `--generate-deep` refuses to overwrite an existing deep payload.
