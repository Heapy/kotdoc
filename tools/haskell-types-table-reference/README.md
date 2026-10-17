# Original Builder table family

This checkpoint implements twelve public exports and three private algorithms
from pinned Text.Pandoc.Builder, enumerated in acceptance-matrix.json. The new
Kotlin API consists of PandocTables, TableBuilders and TableStreams. Haskell is
an independent development oracle, never a production dependency. Translated
code retains John MacFarlane's attribution and the packaged pandoc-types notice.

The original executable imports the actual pinned Builder/Definition modules.
Its 17,559 finite observations include 9,617 complete placement triples, 5,936
body normalizations, 848 head and 848 foot normalizations, 106 clipping cases,
64 simple tables, 40 table/tableWith cases and 100 cell constructors/defaults.
Malformed/nonpositive spans, signed Int endpoints, invalid overhangs, empty
sections, independent row-head/body spans and remnant order are retained.
Padded cell streams use the original infinite repeat; observations retain a
bounded prefix and explicit truncation flag, without changing source results.

A separate 52-observation executable observes original demand: lazy triple
fields, ignored inputs/list heads/tails, boundary predicate demand, StrictData
Cell field roots, pinned optimized cellWith evaluation order, maximum/minimum
signed widths, and 50,000-row spines. Kotlin uses explicit root callbacks,
GenericList step/head/tail and GenericTuple3 fields for these contracts. Ordinary
strict Kotlin arguments cannot represent a failing argument before the call;
use the callback overload. Collection size/materialization remains Int-bounded;
source list observations use step/head/tail and Long widths instead. These are
finite constructor/demand proofs, not arbitrary Haskell heap equivalence.

Original nine table properties are additionally executed for all 4,500 original
generated inputs / widths, including independent native normalized trees:
13,500 unchanged assertions. Both original table literals and existing TablesTest
remain unchanged. Nine preserved legacy before/after observations demonstrate
why Tables normalizers/clipRows/normalize/simpleTable and the three Builder table
entry points now delegate through lazy bridges. The old iterator Placement API
is retained unchanged for compatibility; it does not claim the original lazy
three-field/remnant contract, which is exposed by PandocTables.placeRowSection.

## Provenance and reproduction

requests.jsonl is generated from inputs only; responses.jsonl and
demand-native.jsonl are raw original responses. Never regenerate expected values
from Kotlin. The verifier checks resources, source pins and optional native byte
replay. Use cached GHC9.12.2/cabal and the existing build directory:

```
cabal build exe:types-table-reference exe:types-table-demand \
  --project-dir=tools/haskell-types-table-reference \
  --builddir=/tmp/kotdoc-types-order-closure-build
python3 tools/haskell-types-table-reference/verify.py --check-source \
  --oracle /path/to/types-table-reference --demand-oracle /path/to/types-table-demand
```

historical/ retains the exact pre-edit Builder/Tables sources, legacy reproducer
and original Many manifest. The table integration manifest separately binds that
historical Builder artifact to its current three-method delegation diff. The
Many current source manifest is refreshed only for this Builder binding; its
original inputs/expected responses and historical execution attribution remain
unchanged. Fresh Many native replay and Kotlin regression are required.

This family does not close all 140 exports / 350 instance members, generic Many
and count derived classes, remaining Builder helpers, Data.Version or JSONfilter.

## Author final shared gate (independent review pending)

Gradle62661 completed BUILD SUCCESSFUL in2m27s. Saved XML contains37,101 actual
cases with zero failures/errors/skips:17,621 new table assertions (17,559 finite,
52 native demand/deep,9 legacy regressions,1 integrity),14,316 unchanged original
property assertions,5,128 Many,14 Tables and22 Builder. The earlier51890 run is
preserved separately: all37,100 behavioral checks passed; only the new integrity
test used the wrong working-directory-relative path. The correction uses the
standard kotdoc.root property and changes no production or original expected
value. Core JAR/installed JAR identity, all three new main classes and104 exact
packaged upstream notices pass. Final source44pins and native17,559+52 byte
replay pass. Fresh Many5,127 native responses and actual eleven original
properties×1,000 reports are byte-exact. Final commands/log/XML/JAR/package and
39owned-path hashes are saved at /tmp/kotdoc-tables-final-results.

The old strict-argument/List signatures are JVM convenience bridges; the new
source API exposes root callbacks, Many and lazy list/triple observations. This
does not claim arbitrary lazy Seq internals through every legacy List signature.
Full public API/instance/JSON closure remains a separate required gate.
