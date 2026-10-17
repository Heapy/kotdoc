# Org table demand addendum

This corrects the initial72-file checkpoint before independent acceptance. Its
5345-body/11832-regression/394-CLI evidence and frozen source/class snapshots are
retained as pre-fix evidence, never relabelled as corrected production evidence.
The existing provenance.json, README/source matrix and all old expected payload
bytes remain unchanged. This addendum adds9 original demand cases, bringing Org
to5354 bodies (5352 finite/original/helper +2 full-domain tests).

Org.hs Table uses toLegacyTable's caption/headers/rows, but discards alignments and
widths. Writers.Shared.toLegacyTable needs only length specs to normalize geometry;
its lazy unzip results do not force column pairs or either tuple field. The shared
Kotlin DocumentShared.toLegacyTable eagerly maps both. Original succeeds for a
one-column list containing an undefined pair; initial Kotlin threw "column pair
forced". The local correction substitutes default values by column count before
calling the shared helper. No generic runtime or accepted writer is changed.

Order matters: original renders the caption before forcing a later bad list spine.
The local correction renders caption first, then obtains the required column count.
When head/body/foot sections are all absent, the source never needs that count.
Four additional original cases prove the distinction: empty sections ignore a bad
colspec tail, whereas an empty TableBody or a head/foot Row requires its spine.
Builder.normalizeTableBody clamps its strict RowHeadColumns even for empty rows;
normalizeTableHead/Foot need the width only when a row exists.
Definition.hs has StrictData: a completely undefined columns root fails while
constructing Table, but an existing list spine need not force its elements. The
Kotlin probe models strict root construction separately; a deferred list element
models an unforced pair/tuple-field constructor, and a deferred size models a bad
later spine. This proves these specific observations, not arbitrary Haskell bottom
or a new general lazy-list API. Native traces are independent ordered expectations.

First five original cases: bad root; ignored bad pair; ignored bad tuple fields; caption
before failing tail/spine; empty columns. Current Kotlin test is the additive
OrgWriterDemandTest; the old OrgWriterTest5345 remains unchanged.

```
python3 tools/haskell-org-writer-reference/check-demand.py --check-source
python3 tools/haskell-org-writer-reference/check-demand.py --verify-oracle
```

Corrected focused Org5354/package and installed Org65 are required. The initial
394 CLI comparisons bind unchanged Muse/RST/Doku/Main/options classes only after
an exact old/new class identity check. integration-demand.json records that check.
The new copied CLI lives at /tmp/kotdoc-org-writer/corrected-installed-cli; the first
installed-cli copy and its successful durable logs are preserved.

Remaining612 issue: the general DocumentShared.toLegacyTable API still eagerly
forces unused alignment/width projections; blocksToInlines remains a separate
known shared helper resource gap. This Org-only fix closes neither612 nor618.
Org reader/other dependency routes and22 Org-output commands remain pending as
listed in the original matrix. RST HTTP/general IO/global621 CLI gaps remain open.
