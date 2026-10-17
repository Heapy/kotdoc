# RST reader source matrix (in progress, no completion claim)

Pinned Pandoc e51c9c6054c8f4ec5c3209d5abe10939dfe2963e, types
b2cbcefc4547296f47b8120740997a9c021d756f. Source1962lines, original unit
module253lines,37 original leaves verified by the original full test inventory
and all37 literal assertions evaluated by the copied full reader oracle.
Three Old reader goldens: rst-reader.rst→rst-reader.native (smart,standalone,
columns80); tables.rst→tables-rstsubset.native; lhs reader fixture→native.
This is a reader checkpoint, not the previously accepted22 writer leaves.

| Source range | Function/branch contract | Current evidence/status |
|---|---|---|
|55–108|ensureFinalNewlines2/readWithM/typed errors/constants|full native module compiled; Kotlin draft pending|
|113–181|title/subtitle promotion, metadata foldr, authors splitting|native37 includes title/metadata; full branches pending|
|182–315|citation sorting, notes first-wins/consumed order, two ordered walkM phases, references/substitutions/cycles|native37 includes circular targets/substitutions; expanded/state probes pending|
|319–445|block precedence, fields/options/line blocks|native37 copied expected literals; Kotlin pending|
|446–637|para/code boundary, header table/autoID/key registration, hrule, indentation/lhs/quotes|native37+3Old planned|
|641–677|include guard/body/line slicing/file loop/source positions/restore state|callback oracle/runtime bridge pending|
|681–778|ordered marker styles/delimiters/compactification/list context/endline|full source read; corpus pending|
|780–958|directive parse/options and all label branches; image scaling/figure/unknown ordered logs|full source read; corpus pending|
|960–1110|table/list-table/CSV options/files/URL/errors/widths/column counts|no existing CSV parser; narrow full source port required|
|1113–1241|custom roles inheritance/warnings, unicode, chunks, code attrs|native37 role cases plus expanded warning corpus pending|
|1246–1425|notes/citations/keys/substitutions/anonymous order/anchors|full source read; state corpus pending|
|1432–1565|simple tables/spans, gridtables dependency, callbacks/widths/compactify|grid tracing draft outsidecore, source dependencies below|
|1570–1928|inline precedence/boundaries, escaped/code/strong/emph/roles/cites/smart/links/notes/anchors|full source read; expanded boundary corpus pending|
|1930–1962|pure column-span alignment/count helpers|independent public-helper observation adapter planned|

Required dependencies, not silently deferred:
- Pandoc CSV.hs91lines, exact copied dev oracle and narrow Kotlin draft planned.
- Parsing.GridTable302lines and gridtables0.1.1.0 source693lines across public,
  Parse/Trace/ArrayTable; absent Kotlin parser. Cached tarball matches actual
  pinned Cabal selected package; retain MIT license. Heap tracing draft underway.
- Existing DisplayWidth/ParserCharacters/RegexCharacters/SmartParsing/source
  parser/LaTeXReaderText/PandocData and resources need relevant source/profile and
  callback/demand audits before reuse; a matching name is not proof of parity.
- include/readFileFromDirs/fetch/date require explicit effect boundary and
  original controlled runtime fixtures, not production upstream subprocesses.

Semantic search was attempted first; jbcontext keychain unavailable in sandbox.
Direct known source paths and exact searches verified this inventory. All drafts
remain outsidecore until source-complete isolated compilation. No RST CLI reader
is registered yet. All37 tests/Old/commands must run Kotlin before acceptance;
RST writer, other formats,full618/Pandoc/calibre remain separate/open.

Low-level dependency gate (reader still unfinished): 303 independent CSV/grid
observations plus two exact post-grid original parser compositions pass Kotlin
at512MiB/256KiB. The actual gridtables suite has27 leaves; all27 native literals
pass and all27 corresponding Kotlin contracts execute (24 direct parser cases,
one rows-array case and two post-table parser compositions). The two initial
GT/rest probes for those compositions remain independent extra observations;
no expected payload was rewritten. Four appended original deep cases cover
CSV10000rows/65536quotedchars and grid4096rows/4096columns. Persistent factory
has331 cases; its final result is recorded separately, not assumed from303.
No RST reader unit/Old/CLI parity is claimed by this dependency checkpoint.

The22 original grid parser cases additionally retain full ArrayTable equality
observations: bounds, every ContentCell/ContinuationCell pointer, spans and
column specs, not just the reader's rows view. These appended22 fixtures leave
all earlier309 payload bytes intact. Raw array access preserves source partial
bounds behavior; no claim about arbitrary bottom/undefined array values is made.
