# Number.Generate actual provider storage correction

This dependency fix precedes complete PKCS15 acceptance. It changes no public API.
The previous exact-request-length guard rejected defined original behavior.
The source copies the actual returned ScrubbedBytes length. generateParams then
accesses p0 at index0 for its mask; SetHighest also accesses0, SetTwoHighest at
bit0 accesses0 and1, and odd forcing alone accesses requestedByteCount-1. Top
selection occurs before mask, then odd demand/store. No unused address is read.
Kotlin checks the actual index immediately before each store, with original input
ownership retained. An exact count is not a blanket requirement. generatePrefix
performs no tweak/pointer writes and uses os2ip/shift on any returned byte length.

The established bits=1/SetTwoHighest/actual-one-byte numeric-prefix behavior remains
an explicit historical source out-of-bounds boundary. It is not a memory-parity
claim. When actual storage has at least two bytes the second store is defined and
must execute even when the requested byte count is1. Other invalid executed
indices are safely rejected by the host bridge, without claiming an original
returned value or executing unsafe original code.

## Independent current evidence

Domain.py classifies only the actually executed source accesses, including top
and odd bottoms. capture.py refuses to execute excluded requests. It captures
1139 defined original observations across all four exports;278 out-of-bounds
requests are recorded as excluded BEFORE original execution. These are not native
pointer experiments: no new out-of-bounds load/store is executed. The existing
original executable calls pinned Crypto.Number.Generate unchanged. Cases include
valid short/long buffers, empty Prefix buffers, source ordering/errors/copy,
Int.MAX_VALUE bit counts with small returned storage, all range branches and the
source100-attempt retry bound. Virtual request counts allocate no giant buffer.

Eight existing PKCS15 safer/state repros are source-defined: the first parameter
request has topNothing/oddFalse and actual storage is9/63 or11/65 bytes, so only
p0 at0 is touched. Both the isolated PKCS15 source draft and the accepted blinder
consumer are checked against their original recorded expectations. The four
empty-storage safer/state observations remain historical only: Generate.hs67
would read/write p0 of a zero-byte copy. They are not re-executed for parity.
All456 original RNG observations and all12 pre-fix differences remain unchanged.
Pure padding Right-root/IO effect and long retry evidence belongs to the isolated
PKCS15 checkpoint and is not silently converted into dependency acceptance.

## Historical versus current bindings

The old37127-row data.jsonl.gz remains byte-for-byte unchanged, including its old
manifest/header. history/ stores the exact old gzip, production source, README
and importer; Baseline.json binds their hashes and the immutable baseline JAR.
Old source pins now explicitly resolve ONLY two historical documentation/tool
paths through HistoricalBindings.json: the old README and importer. All other
old pins still check their current files. There is no fallback from unexpected
current mismatches to an archive. CurrentPins.json separately binds every changed
production/tool/test/docs file and all new evidence. The old importer --check-source
and old test loader label and verify this historical binding; old expected rows
are not reassigned to the changed Kotlin source.

## Commands and limits

- `python3 tools/number-generate-length-reference/capture.py --verify` executes
  only the pre-classified defined original requests.
- `python3 tools/import-number-generate-length-fixtures.py --check` verifies
  current files, historical bindings, byte-exact gzip and executable access matrix.
- The final focused run selects Number.Generate old/new, Prime and rawRSA tests,
  plus jar/installDist; exact command/XML/JAR/hash/package evidence is in handoff.

The original NumberSpec four families and37127 old observations are Kotlin
regressions here, not new original runs of their historical unsafe subset.
Input storage, source Integer versus host Int/ByteArray widths, physical erasure,
other ABIs/general bottom/timing and actual huge allocation limits remain explicit.
PKCS15/PSS/RSA.Types/SystemDRG/signatures/trust/chain/rest613 and full Pandoc/calibre
remain OPEN. No native/upstream runtime is a production dependency.
