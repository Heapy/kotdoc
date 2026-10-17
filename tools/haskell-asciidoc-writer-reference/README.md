# Pinned AsciiDoc writer development oracle

This directory is development/test tooling. Production conversion is Kotlin and
never invokes the programs here. The full pinned Pandoc writer is retained with
its original GPL-2.0-or-later copyright/license notice. Only module visibility and
imports needed to observe it are transformed; `prepare.py --check-source`
checks that exact transformation and the original12-test module transformation.

Pandoc commit e51c9c6054c8f4ec5c3209d5abe10939dfe2963e; pandoc-types commit
b2cbcefc4547296f47b8120740997a9c021d756f. The cached development oracle uses
GHC9.12.2, text2.1.2-b3a1, filepath1.5.4.0 and the original library package DB.
`build-cached.py` rejects a different text/Pandoc/gridtables profile. The manifest
pins the plan, package registration, linked library archives, original executable,
source helpers, adapters, capture/checker tools and immutable expected payloads.
A matching version string alone is not treated as source provenance.

Run read-only verification from any directory:

```
python3 /absolute/repo/tools/haskell-asciidoc-writer-reference/prepare.py --check-source
python3 /absolute/repo/tools/haskell-asciidoc-writer-reference/prepare.py --verify-oracle
```

The latter replays existing requests and compares original outputs/ordered logs;
it never writes or regenerates expected answers. Capture programs refuse to
replace payloads. Original fixtures retain raw module output separately from the
literal test expectation because Tests.Helpers compares Haskell `lines`.
Four Old outputs first equal their verbatim source goldens before capture.

`WRITER-MATRIX.md` maps the complete912-line writer and helper closures. Finite
synthetic observations, direct upstream tests, exhaustive scalar-domain proof,
writer-only command ASTs and complete App commands have different accounting.
`DEPENDENCY-GAPS.md` records source-proved shared helper/resource and general App
limitations; local closures do not close those global tasks.

The31 command inventory entries are extracted by the original Tests.Command
Markdown parser. `command-ast` uses original readers to isolate the writer; it is
not evidence of a Kotlin reader. Complete App captures compare exit, stdout,
stderr and created-file bytes. Four currently executable command blocks and27
reader-dependent commands are distinguished. Four exact Old command lines also
remain blocked by global --quiet despite library golden parity.

`work-in-progress` and `test-drafts` are explicitly owned development snapshots,
not production fallback dependencies. They preserve source translation and proof
before atomic integration. Final integrated source/class/JAR/XML and durable CLI
bindings are a separate manifest; historical accepted snapshots are not rewritten.
The durable runner records argv, PID/start, pinned inputs, log hashes and terminal
exit code atomically. Existing live proofs are polled rather than restarted.
