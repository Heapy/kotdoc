# Complete RSA.PSS source checkpoint

The full pinned crypton 2.0.1 module is translated: ten exported functions,
four PSSParams fields and both private helpers. SourceMatrix.json maps every
source definition and contract to the Kotlin implementation and independent
observations. It is not a claim that all crypton, Pandoc or calibre is complete.
Production uses accepted Kotlin RSA, MGF1, hash and persistent DRG engines only.
No original program or JVM signature provider is a production dependency.
The crypton and ram BSD notices are retained in the repository/package.

## Source and independent expectations

original/ contains verbatim upstream PSS.hs and PSSSpec.hs. OriginalPssSpec.hs
changes only the module name/export list to expose the source literals. The full
original suite actually passed 53 examples: 25 sign, 25 verify and three signature
range checks. All 53 are independently exported and replayed in Kotlin; they are
not added again to synthetic test counts. The 25 literal vectors include the
upstream internal example and four groups of six at 1024/1025/1026/1031 bits.

Ten separate corpora total 10,033 rows: original source 53, ordinary 2,564,
paired demand 1,452, generic dictionary/MGF 732, pure RNG 868, retry controls 24,
IO callbacks 540, malformed encodings 2,226, digest API controls 600 and helper/
virtual bounds 974. Inputs and expected values come from original execution,
never Kotlin. Digest verification positives use signatures produced by the
original digest API, not a Kotlin round trip. Ordinary requests explicitly select
all eighteen currently available real hash engines; helper/mutation requests use
the declared SHA1 source default. Other hash families remain separate work.

ObservedPss.hs changes only the upstream module name and export list for the two
private helpers; the source bodies remain identical. Generic hash observations
use bounded context-eight/digest-four callbacks, valid input lengths and safe
stores. They test source dictionary effects/errors and unit-result demand, not a
replacement hash engine. Each configured observer runs in its own process to
avoid conflating configuration-dependent unsafePerformIO CAFs. The earlier
observer's lazy trace-label serialization failure and all failing Kotlin draft
repros remain under history/; none were rewritten as upstream failures.

## Contracts retained

The HashAlgorithm dictionary is passed separately from the lazy Params record.
The source method can ignore or demand the pssHash witness; a standard SHA1
signing Right-root can therefore ignore a bottom Params value, whereas a custom
strict witness method need not. The MGF interface preserves lazy seed, length
and result. An empty left zip ignores the MGF. Source guard/effect order, salt
before metadata/key demand, and safer key/modulus range demand before dictionary
pair-root are verified separately, with every related sign/digest variant.

Explicit-salt signing ignores the configured salt length. The source comment's
salt<=hash-size recommendation is not added as validation. Verification retains
its exact length, representative, pre-octet, trailer, PS, marker, salt-length and
hash-comparison order. Trailer bytes other than 0xbc are allowed by the source.
Unused-bit normalization observations are retained in SourceQuirks.json; no extra
canonicality rule is introduced. zipWith truncates to the shorter mask/buffer.

Pure actions can ignore the whole RNG pair and parameters; IO executes callback
effects even when its returned result is ignored. Lazy Int64 source dictionary/
callback adapters preserve methods which ignore the requested length. Standard
Int callback/accepted DRG adapters are separate conveniences. The negative/huge
length observations execute only custom providers returning a fixed small salt;
there is no huge native allocation or UInt32 conversion. State/value projections,
repeated failures, CRT/non-CRT branches, exact 100-retry failure and success after
99 rejected buffers (102 total draws) are independently observed. These are
bounded execution results, not general termination proofs.

Twelve empty-provider safer requests are excluded BEFORE native execution:
advancing to the salt requires Generate's p0 access on zero-byte blinder storage.
The precise request set and source path are recorded; no native result is claimed
for them. Ignored pure results do not touch the RNG and remain defined controls.
No blanket exclusion is made for nonlawful dictionaries or wrong returned lengths.

## Ownership, arithmetic and limits

Public ByteArray convenience calls snapshot immutable-source message/digest/salt
and provider bytes, and export copied signature bytes. A deferred signing guard
also snapshots its materialized salt before retaining a Right payload. Controlled
Lazy/Source dictionary adapters model immutable buffers; mutation through unsafe
original pointers and concurrent host mutation are not source ownership promises.
Host synchronous payload failures share their memoized result; asynchronous/heap/
universal Haskell-bottom identity is not claimed. PSSParams has no invented Eq or
Show instance. Typed source Digest bytes must come from a hash or valid-width
source parser; unsafe forged Digest storage is outside these adapter observations.

The 974 helper/root-only bounds rows include signed 64-bit wrap and empty-list
helper demand; 594 guards also match independent modular signed64 arithmetic.
They materialize only small salt/input buffers and never hash/allocate giant
encoded messages. Actual materialized ByteArray dimensions remain Int-sized.
The source's full scan/comparison structure is retained, but no JVM constant-time,
physical erasure, all-ABI/backend, StableName or universal memory proof follows.

## Reproduction and integration

BuildHaskell.sh builds the ordinary oracle and original suite. BuildExtraOracles.sh
builds source-case, demand, dictionary, RNG, callback and private-helper observers.
The capture-*.py tools accept --verify; capture-random.py pre-classifies the twelve
unsafe requests and never sends them to the original process. No expected corpus
is regenerated from Kotlin. import-pss-fixtures.py --check verifies verbatim
fixtures, source copies, twelve definitions, export-only transforms, exclusions
and all current source hashes. CompilerProfile.json records exact native binary
identities and the selected compiler profile. The final handoff saves command,
terminal XML, immutable JAR, package notices and unrelated-class identity.

Independent review is required for this checkpoint. Full RSA.Types/SystemDRG,
other signatures/trust/chain, missing hash families/optimized backends/rest613,
all Pandoc 623 and calibre 624–626 remain OPEN.
