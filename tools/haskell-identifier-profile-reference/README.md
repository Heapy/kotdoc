# Pinned Pandoc identifier consumer profile

This narrow checkpoint repairs DokuWikiIdentifiers' lowercase dependency selection.
Pandoc e51c9c6054c8f4ec5c3209d5abe10939dfe2963e and pandoc-types
b2cbcefc4547296f47b8120740997a9c021d756f select text-2.1.2-b3a1 in the retained
Cabal plan/package database. CoreUnicodeText intentionally ports text-2.1.3;
its 1460 mappings are not defective. The consumer needs the original 1433-map
profile, already supplied by unchanged LaTeXReaderText.toLower. No shared table
or accepted format fixture is rewritten.

The exact pre-fix Doku repro is retained by the RST checkpoint. New independent
original/current probes in consumer-repros-before.json confirm BOTH Muse reader
(auto ID changes) and Muse writer (spurious explicit anchor). These are the
only three current DokuWikiIdentifiers callers. All are corrected by changing
one helper's lowercase call while retaining its original Unicode16 classifiers.
Semantic discovery via jbcontext was attempted, but sandbox keychain access
failed; actual known source files and exact caller searches were read instead.

## Source/function matrix

- Shared.hs531–574: inlineListToIdentifier unEmojify exact Span attr predicate,
  emoji first alias/fallback, stringify, textToIdentifier, ordinary/GFM filtering,
  spaces, ASCII conversion, initial nonletter removal. Complete original
  predicates retained; no host Java/ICU classification replaces them.
- Shared.hs671–683: empty => section, used-ID membership, first free suffix
  1..60000 and exhausted-range base fallback. Native fixtures include exhaustion.
- DokuWiki.hs heading/registerHeader, Muse.hs both reader heading forms and
  parser registerHeader: generation only without explicit ID and with
  auto_identifiers; ASCII alternative and identifier-state insertion remain.
- Writers/Muse.hs Header258–266: compute auto even when auto extension disabled,
  record generated auto, suppress explicit anchor only under the source condition.
  New duplicate-header cases exercise state and auto enabled/disabled.
- All 27 differing text2.1.2/text2.1.3 lowercase points are observed separately
  under ordinary, ASCII, GFM, combined flags, and in duplicate consumer headers.
- Generic fixture shapes cover empty, spaces/punctuation/marks, full 1433 mapping
  string, all inline constructors and emoji Span attribute positive/negative
  boundaries; collisions include first-gap and exhausted60000. Native original
  Shared.uniqueIdent is the expectation source, never the Kotlin helper.

## Evidence accounting

200 native helper +116 Doku reader +116 Muse reader +216 Muse writer =648
independent observations. Two exhaustive assertions separately cover all
1,114,112 lowercase inputs (1433 changed mappings) and all identifier predicate
inputs; these are650 JUnit cases, not millions of JUnit tests.
The existing original Doku1480/MuseReader3007/MuseWriter3379/RST2725 suites are
regressions, with unchanged expected bytes. CLI fixtures retain exact original
exit/stdout/stderr, including registry rejection of unsupported extensions;
library acceptance and CLI extension acceptance are distinct contracts.

Reference.hs imports cached original Shared and is a development oracle only.
The prior source provenance of complete reader/writer copied modules is linked
by manifest hashes; runtime-profile.json pins the selected package profile,
Cabal plan, vendored text source and package DB. build.py refuses a different
text/Pandoc unit. checker.py provides read-only --check-source/--verify-oracle,
--check-cli and durable installed proof; capture scripts refuse overwrites.
Historical format manifests keep their old helper snapshot pins: integration.json
links the accepted old helper blob, retained verbatim snapshot and current source.
A historical checker that pins old live helper content will reject the new live
helper; use historical snapshot plus current integration checks, not changed old
hashes or regenerated old expectations.

Remaining: general Shared Identifiers may still select the independent ICU/text
profile, DocumentShared stack/demand gaps and global CLI621 gaps remain separate.
This does not close612,618 or all Pandoc/calibre. No claim of new format support.
