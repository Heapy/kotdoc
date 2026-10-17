# Pinned Org reader development reference

This development-only tool translates and observes the complete ten-file Org
reader from Pandoc e51c9c6054c8f4ec5c3209d5abe10939dfe2963e (3798 lines,
284 top-level signatures), with pandoc-types b2cbcefc4547296f47b8120740997a9c021d756f.
The original GPL-2.0-or-later notices remain in copied modules. Production
conversion is Kotlin; no original executable is a production dependency.

`prepare.py --check-source` verifies exact, finite module/import/observation
transformations of ten implementation and fourteen test modules. The only
additional implementation export is the private collectLines/collectAsList
closure; its body is unchanged. `source-pins.json` authenticates raw originals.
`runtime-profile.json` records the exact GHC9.12.2/text2.1.2 package registrations,
Cabal plan, private visibility-only package registration and oracle binaries.
The private registration changes no unit ID, ABI, compiled library or import path.
It exposes cached hidden original TeX types needed by the dev reference.

`build-cached.py` reuses the cached complete original library, without fetching or
rebuilding upstream packages. `verify.py --check-source` is read-only and checks
raw sources, exact transforms, runtime artifacts and the final manifest when
present. `verify.py --verify-oracle` replays the retained requests against original
modules, including original312, helper552, expanded973, profile1541, boundary49,
effects38, metadata60 observations and deep16 observations. Deep JSON/Show pairs
are retained separately: fourteen distinct deep reader inputs and two alternative
Show observations avoid conflating shared JSON-codec depth with reader behavior.
Metadata60 observations similarly cover58 distinct private-helper inputs.
No verification mode writes expectations.

Original test expectations and Old/org-select-tags fixtures remain independent
of Kotlin. Capturers refuse to overwrite existing payloads. The app corpus is
captured from the cached exact original test executable's --emulate route, not a
version-only external installation. Command extraction follows Tests.Command's
continuation, stdin, stderr and normative output rules; App.ensureNl is a separate
application boundary. Of58 Org-input commands,46 currently have available output
writers and12 require missing markdown/latex/asciidoc writers. All58 original
normative outputs were checked against original App; unavailable Kotlin routes
remain explicit pending work.

Initial bounded deep failure traces are durable in `evidence/`. The Org-local
metadata collector and finite headline-list bridge address reader consumer stack
requirements without modifying shared Builder/DocumentShared semantics. General
Builder bridge resource parity remains a separate open611/612 requirement.
This checkpoint does not claim full HTTP/IO, CSL/citeproc, global CLI, all618,
allPandoc or calibre completion. Final integration status belongs in the porting
ledger maintained by the coordinator after independent review.

Final author checkpoint:3538 new reader JUnit cases and5354 unchanged Org writer
regressions passed (8892 actual XML, zero failures/errors/skips). Independent
native replay passed3541 observations; bounded finite proof used512MiB/256KiB.
The installed immutable CLI passed446 reader comparisons (46 full original
commands,12 missing-writer routes pending) and65 accepted writer comparisons
(five full original commands). Thus14 of27 Org-writer commands now have available
reader routes; the other13 still depend on missing readers/citeproc. Historical
source manifests were preserved; `integration-snapshot.json` links the accepted
Main blob, current narrow Main diff and unchanged4299 prior core class bytes.

`evidence/final-proof.json` records terminal results and exact hashes. The package
snapshot has112 root notices plus the project GPL3 entry. An unrelated MD5 setup
added eight unselected test fixtures and one BSD2 notice during the build window;
the notice is included, MD5 code/test fixtures are absent from the runtime JAR.
The first external timing report omitted the notice; the corrected record states
actual package bytes and does not claim a whole-worktree freeze.
