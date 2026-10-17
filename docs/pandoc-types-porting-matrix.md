# local:611 finite acceptance matrix

Source authority: pinned upstream/pandoc-types/{src/Text/Pandoc/{Arbitrary,Definition,Walk,Builder,Generic}.hs,test/test-pandoc-types.hs}. Every row needs exact source-symbol IDs, source hashes, a Kotlin target, independent original observations and executed checks. A row without evidence remains open. This is a closure checklist, not a percentage.

## Accepted evidence and open gates

| Gate | Exact source | Accepted evidence | Status / remaining gap |
|---|---|---|---|
| Original twelve Walk predicates | `test/test-pandoc-types.hs:p_walk,p_walkList,p_query,p_queryList` and twelve registrations | `665c5daf970f0981b2851fc4d50fc35ad0dd004a`: 500 original generator inputs, 6,000 independent Data.Generics expectations | Covered original predicates; all public Walk instances/effects remain separate gateE |
| Actual QCGen/Gen and original Pandoc seed replay | `Arbitrary.hs:288-289`; QuickCheck2.16.0.0 `Gen.hs`, `Random.hs`; splitmix0.1.3.2 | `cdf2b84906e91b1baad740738564855677966acb`: 420 primitive seed/size cases and 500 complete source Pandoc trees | Covered initial seed replay; accepted standalone/helper domain below. Large/deep/partial generator runtime audit remains open |
| All nineteen default generators and helper domain | `Arbitrary.hs:all19 instances,realString,arbAttr,arbInline/Block/Inlines/Row/Table*/Cell` | `43db34fe4fd20707b4891e81378c059acdc09d22`: 5,700 exact standalone values and 4,538 private/domain observations; all18 Attr choices,20 Inline/14 Block/22 enum root witnesses, text endpoints | Covered named finite matrixA; broad large/deep/partial generator demand remains in the runtime audit |
| Ten explicit shrink families | `Arbitrary.hs:36-111,114-142,178-211,309-346` | `fde33c3346204ff8f2eee836ef445fd302a0bdc4`: 134 named lists, all1,114,112 Char, six demand probes; focused XML33,284; supplements665c5da | Covered named finite gateB, all constructors and represented known-spine demand; no general GHC evaluation runtime claimed |
| Nine inherited empty shrink families | `Arbitrary.hs:288-307,348-407`; QuickCheck `Arbitrary.shrink` default | `cdf2b84906e91b1baad740738564855677966acb`: 270 original inputs, nine explicit Kotlin APIs; compound Citation/Alignment hooks retained | Covered original default no-op semantics on the represented inputs; standalone generator domain remains gateA |
| All original registered tests | `test/test-pandoc-types.hs:tests` | Existing 56 paired JSON literals and two table literals; exact Walk evidence above | All 81 source registrations have concrete Kotlin mappings and executed finite evidence in remaining-property-manifest.json; whole semantic API and other gates remain open |
| Derived Read | Definition.hs23 data/newtypes; Builder.hs Many; native GHC dictionaries | Accepted06317ca31379e7fb09a1c4579d4167e50f55d62a:16,954 native requests,25 concrete dictionaries/79 constructors+5 aliases, four Read class methods plus Prelude.read | Finite typed matrix independently accepted. NativeReader integration is the next checkpoint below |
| Eq/Ord | Definition.hs derived/manual dictionaries; Builder.hs Many | Existing75,470 independent observations plus current5,855 original requests,225 explicit method slots | Finite source audit accepted; Many dictionaries added for a demonstrated gap. Gradle81,325 passes; independent native/source review passes |
| Whole public semantic API | exports/instances of Definition/Walk/Builder/Generic | Existing typed AST/builders/visitor/table helpers | Open: enumerate every export/instance and assign native evidence; missing APIs block611 closure |

Accepted verification for the second checkpoint: actual JUnit XML7,375 cases, zero failures/errors/skips; both fixture source verifiers and independent original byte-for-byte replay passed. This is the verification of those named slices, not completion611. Test inventory snapshot `5bbf0f27f96e0ef3c369d7684c5be898da2bdf4f` is a separate accepted audit snapshot. The next accepted generator-domain commit `43db34fe4fd20707b4891e81378c059acdc09d22` passed actual JUnit17,613 cases, zero failures/errors/skips, all three source verifiers and independent raw original replay. Neither count establishes completion611.

## A. All 19 Arbitrary instances and the actual source generator domain

Rows: Inlines, Blocks, Inline, Block, Pandoc, CitationMode, Citation, Row, TableHead, TableBody, TableFoot, Cell, Caption, MathType, QuoteType, Meta, Alignment, ListNumberStyle, ListNumberDelim.

Acceptance for EACH row: exact original mkQCGen replay, complete typed value equality at seeds 1..50 and requested sizes 0,1,2,3,8,30; wrappers retain resize3/resize8 where the source uses them. This is 5,700 named observations, not inferred coverage through Pandoc. Runtime primitives already have 420 source cases; original Pandoc already has 500 exact replays.

Additional helper rows: realString, arbAttr, arbInlines, arbInline, arbBlock, arbRow, arbTableHead, arbTableBody, arbTableFoot, arbCell. Probe unchanged source in its GHC module context (private bindings) rather than exporting/reconstructing a replacement module. Depth witnesses -1,0,1,2,3,4 cross both nesting guards and LineBlock mod/div transitions. Size witnesses 0,1,3 cross empty/nonempty and rejection growth. Record generator branch, output and replay seed. Explicit source observations verify text length0/8, codepoint32/127/128/9999, all18 Attr choices, all enum constructors, table row/cell/body limits, spans1/2 and three widths. The old injected-Random helper is not a substitute for this exact runtime.

## B. Ordered shrink closure

Ten explicit families: Inline, Block, Inlines, Blocks, Row, TableHead, TableBody, TableFoot, Cell, Caption. Nine inherited empty families: Pandoc, Meta, Citation, CitationMode, MathType, QuoteType, Alignment, ListNumberStyle, ListNumberDelim (270 native no-op cases now retained).

For the ten explicit families: every Inline constructor20 and Block constructor14 plus all six table/caption record forms gets a small complete-list original oracle case; no source branch may be represented only by random luck. List lengths0,1,2,3,4,7,8,9 check chunk-removal boundaries and ordered duplicates. Every flattening branch and each no-flatten branch is named; source Span versus Emph, Citation no-op, Cartesian Attr and custom DefinitionList order remain distinct. Large Cartesian products use original ordered prefixes with limit/truncated metadata; small cases retain complete lists. Text shrink has an all-Haskell-Char sweep through Text.pack, batched source observations, including surrogate replacement, Unicode16 category/lowercase and ASCII digit/space ranking. Partial/demand and deep finite tests explicitly list represented-model domains; divergence/error evidence is not silently replaced by eager results.

## C. All 81 original registered tests

23 property rows: exact twelve Walk predicates (already6000), JSON round-trip1, table normalization9, Figure representation1. Source property names are the stable IDs. Remaining table cases use original Arbitrary TableHead/Body/Foot and every width2..16, preserving the exact original predicates and independent original normalized outputs; three properties per type. Figure and JSON rows use original generated inputs, original predicate truth and independent original builder/Aeson outputs, not Kotlin round-trip self-consistency. Empty and explicit constructor witnesses supplement generated inputs.

56 JSON literal rows remain paired original encoding/decoding checks (112 assertions), and t_tableSan/t_tableNormExample retain unchanged original expected values. Existing covered literals are audited, not rewritten. Closure requires all81 source IDs mapped to executed methods/dynamic IDs without generic catch-all assertions.

## D. Read and ordering dictionaries

Read is NOT closed by NativeReader.read: its public API parses a complete Pandoc/block/inline value and consumes all input; it does not expose each typed readsPrec/remainder contract. Enumerate all23 Definition data/newtype Read instances, aliases Attr/Target/ListAttributes/ColSpec/ShortCaption and dependency List/Maybe/Map/Text/Int64/Double. Add Builder Many Inlines/Blocks derived Read/Ord to the audit. For each concrete dictionary cover all constructors, source precedences -1/0/10/11, extra/missing parentheses, record field order/duplicates/missing fields, list/tuple/Map syntax, lexical whitespace/comments/escapes/gaps, Int64 wrap and IEEE lexemes, valid prefix plus exact remainder, rejected prefixes and demanded errors. Source expected results come from actual readsPrec/readPrec/readList, not Show/Read self-consistency. Deep finite paths use explicit continuations.

Ordering is an audit of the existing75000+ observations before implementation changes. Enumerate each typed dictionary and its nine methods eq/ne/compare/lt/le/gt/ge/min/max. Join constructor order/field order and primitive delegation to original derived dictionary dumps; require source witnesses for Citation hash ordering, all18 IEEE contexts, caller-preserving extrema, Unicode Format, Map/list/Maybe order and lazy/deep/partial short-circuits. Any uncovered source instance/method (including Builder Many) becomes a concrete row and blocks closure.

## E. Public semantic API audit before closing611

Enumerate every export and source instance in Definition, Walk, Builder and Generic into a symbol-to-Kotlin-to-native-evidence matrix. In particular, twelve Pandoc Walk properties do NOT certify all Walkable root/container instances, exported walk*M/query* helpers, monadic effect order, or Generic topDown/bottomUpM/queryWith. Current PandocVisitor is synchronous/bottom-up; these APIs must be checked explicitly. Definition Num/Enum numeric wrappers and Builder Many algebra/metadata/builders/table helpers likewise need explicit source mapping. Missing semantic APIs block closure rather than being removed from the matrix. This audit determines exact remaining implementation, without expanding to a general GHC runtime.

Final gate: zero unmapped/partial source rows in these matrices, all original81 tests mapped, independent review, source/hash checks and scoped/full relevant regression pass, then ledger/inventory reconciliation by integrator. Preserve the full Pandoc/calibre objective and distinguish completion611 from completionPandoc.


## Accepted finite standalone/helper checkpoint

Independent review and original byte-exact replay confirm 5,700 direct observations
for all nineteen default instances and 4,538 private-helper/domain observations,
including 56 named constructor witnesses. Fresh regression XML has 17,613 actual
cases with no failures/errors/skips. This supplies the finite standalone/helper
corpus specified in gate A; it does not close every semantic gate or establish
unbounded seed/size/demand fidelity. At that accepted checkpoint the remaining property gate was open; it is now
covered by66722c1 above. Shrink, Read/ordering and public API gates remain required
before closing local:611.

## Accepted original registered property checkpoint

The new `remaining-property-manifest.json` enumerates all 81 original source
registrations and their exact Kotlin method/dynamic-name prefixes. The source
verifier reconstructs the eleven original remaining property bodies and six
exposed forall bodies verbatim. The independent native payload contains 4,500
TableHead/Body/Foot inputs (every width2..16 and seeds1..100), full original
normalization results and three original predicate truths per input; 305 Figure
cases (including five empty/whitespace witnesses), and 500 original generated
Pandoc values with actual Aeson encoder bytes and decoded values. Eleven actual
original QuickCheck properties additionally report 1,000 successful trials each.
Fresh focused Gradle execution passed 14,316 new assertions plus the prior
17,613 cases, 113 unchanged JSON-literal checks and 14 table checks: actual
JUnit XML32,056 cases, zero failures/errors/skips. Source verifiers pass.
Independent source/predicate review and separate byte-exact original replay of all 5,305 records and eleven reports pass. Existing 56 paired JSON
literals and both original table literals remain unchanged and are included in
the focused regression. This checkpoint does not close the shrink/Read/ordering
or whole public semantic API gates.

## Accepted ordered shrinking closure checkpoint

Finite acceptance rows: all20 Inline and14 Block root constructors and their
34 wrapper flatten/no-flatten cases; seven table/caption cases;32 list-boundary
cases; six deep32/128/256 bounded prefixes; one large Cartesian prefix; eight
Text codepoint-length cases; eight Attr empty/nonempty component masks; two empty
Caption forms; and two alternative Quote/Math enum witnesses. This is134 named
cases. Complete cases retain every candidate in original order, including
duplicates; seven bounded cases retain explicit limit/truncated metadata.

The independent original oracle additionally evaluates every Haskell Char
codepoint0..0x10ffff through Text.singleton and the actual Str shrink instance:
1,114,112 complete candidate lists, checked by1,088 canonical numeric-JSON batch
SHA256 values. This includes all2,048 surrogate Char inputs mapping to U+FFFD by
Text.pack, all Unicode16 lower/upper mappings and ASCII digit/space ranking.

Six original demand observations distinguish candidate-spine demand from element
demand on finite known spines. They prove two concrete defects in the initial
Kotlin helper: copied candidates forced retained elements; DefinitionList eagerly
constructed recursive tails before the first candidate. Citation's inherited
empty shrink also requires removing list chunks without reading any elements.
Read-only AbstractList candidate views now retain original element references,
expose no mutation operations and defer indexed access. Source-order complete
list equality tests exercise their iterators; view size reads only known finite
spine length. Backing Lists follow the AST's existing immutable-input convention.
Infinite/partial spines and bottom-valued strict AST fields are outside the
represented Kotlin value model; no general GHC evaluation engine is claimed.

Remaining gates after this named matrix: the vector construction defect was subsequently closed by accepted a5c8fb8f8366b825db351057153b2f3160cfcdef below; typed Read was then implemented in the current review checkpoint. Remaining gates include NativeReader integration;
Eq/Ord dictionary evidence audit; and the exported semantic API symbol census and
missing-method implementation. The19 generator and19 shrink instance source
families stay explicit; finite observations are not proof of unbounded runtime
behavior. local:611 remains open.

Focused verification: Gradle session48118 completed successfully in2m05s.
Actual JUnit XML has33,284 cases (prior32,056 plus1,228 new), zero failures,
errors or skips. The1,228 new cases are134 ordered lists,1,088 full Char batches
and six finite demand observations. All five source verifiers and diff checks passed; independent original replay
and review accepted `fde33c3346204ff8f2eee836ef445fd302a0bdc4`.

Initial exact-source census for the subsequent API audit: Definition has37 export
entries,23 data/newtypes and five aliases; Builder has74 own export entries plus
the Definition re-export, one parametric Many type and two aliases; Walk has25
export entries (the two apostrophe helpers are distinct),58 source instance
clauses and three class methods; Generic has four exported functions. These are
140 module-owned export entries before expanding constructor/record/class-member
children. Walk's58 dictionaries have174 walk/walkM/query method slots. The typed
ordering audit has25 concrete Definition/Many Inline/Many Block dictionaries and
225 nine-method slots; Read has the same25 concrete dictionaries plus five
Definition aliases and dependency grammars. These finite source rows require a
separate explicit symbol/evidence census; this count does not mark them covered.

## Vector runtime checkpoint and finite implementation sequence

The next runtime correction replaces recursively constructed/eager vectorOf
results with finite lazy memoized Lists. Exact authority is QuickCheck2.16.0.0
Gen.vectorOf=Control.Monad.replicateM, Gen Applicative(<*>)=ap and Monad(>>=), plus
the retained GHC9.12.2 interface unfolding of replicateM via liftA2(:). Original
native evidence has180 seed/size/count records, complete small vectors and named
100000-element indices; ten actual native demand stages cover length without
elements, selected-element demand, repeated successful/nullable values and shared
synchronous exceptions. Two Kotlin test methods supplement180 native value
cases: one executes all ten native stages, one checks finite adapter bounds and
per-run cache isolation. Fresh Gradle64342 passed actual XML33,466 cases
(prior33,284 plus182), zero failures/errors/skips; six source verifiers pass.
Independent original replay and review accepted a5c8fb8f8366b825db351057153b2f3160cfcdef; no source
expectation is rewritten. This closes the identified vector construction gap
for the represented finite regression; it does not turn Kotlin into a general lazy Gen interpreter.

The following implementation sequence has explicit source rows and acceptance:

1. Finish/review this vector correction and rerun all19 accepted generator
   instance matrices, all19 shrink source families and the original81 registered
   test mapping. Keep callbacks deterministic and finite; concurrent/cyclic or
   asynchronous GHC thunk semantics are outside this represented-value contract.
2. Add actual native readsPrec/readPrec/readList observations and a Kotlin typed
   ReadDictionary API for25 concrete dictionaries: Pandoc, Meta, MetaValue,
   ListNumberStyle, ListNumberDelim, Format, RowHeadColumns, Alignment, ColWidth,
   Row, TableHead, TableBody, TableFoot, Caption, Cell, RowSpan, ColSpan, Block,
   QuoteType, MathType, Inline, Citation, CitationMode, Many Inline and Many Block.
   Include five aliases Attr/Target/ListAttributes/ColSpec/ShortCaption and the
   primitive/List/Maybe/Map/Seq grammar dependencies in the same source census.
   Every constructor, precedence -1/0/10/11, exact remainder, rejection/lexical
   boundary and deep finite path needs original expected results. NativeReader's
   private generic expression parser greedily consumes applications and demands
   EOF; its current Pandoc-only adaptation cannot serve these contracts. Reuse
   validated lexical rules where possible; preserve its existing expectations.
3. Audit all225 Eq/Ord method slots against the existing AstComparison original
   dictionaries and75470+ observations. Fill concrete missing source rows (e.g.
   the18 Many Inline/Block method slots) without replacing already-proven source
   behavior. Join aliases/dependency IEEE and finite demand rows explicitly.
4. Expand140 module-owned export entries to exact constructor/record/class-member
   IDs, reconcile58 Walk dictionaries/174 walk/walkM/query slots, four Generic
   functions, Definition algebra/numeric wrappers and Builder methods/instances.
   Implement missing public behavior with native state/effect traces and typed
   root/container inputs, then execute each exact source row. An unmapped member
   prevents closure; the original12 Pandoc properties cannot substitute for it.
5. Reconcile accepted source evidence with the integrator's ledger/inventory and
   run relevant complete regression. Close611 only when all named gates have no
   unresolved implementation/evidence rows; closing611 still does not mean the
   entire Pandoc/calibre port is complete.


## Typed Read checkpoint awaiting independent review

`PandocRead` now supplies all25 concrete Read dictionaries and five Definition
aliases, plus the primitive/dependency grammar. The native census retains all79
constructors with actual Data.gunfold arities and record fields. The manifest
maps all five operations (readsPrec/readPrec/readList/readListPrec/Prelude.read) for every dictionary and
alias to exact fixture indices. Its16,936 requests retain complete original
response lines, full parse-result lists, exact Char remainders and whole-read
errors. The source matrix covers precedence and Int64 bounds, constructors and
records, missing/wrong fields and delimiters, whitespace/comment rejection,
escapes/string gaps/Char lists, surrogate replacement, greedy numeric boundaries,
IEEE rounding and signed NaN/zero words, and deep32/256/2048/8192 finite inputs.
Original GHC derived Meta/Seq Read interface evidence is retained. Expected
responses and the original56 imported examples are never rewritten by Kotlin.

Final Gradle session62176 passed actual XML49,198 cases, zero failures/errors/skips,
including14,308 new Read cases and the prior types, JSON, table and unchanged
Native suites. All parser/oracle/source hashes were finalized before that run.
This is finite represented-value evidence, not a general GHC evaluation engine.
Independent source and native byte-exact replay review is still required.

Next coherent production checkpoint is NativeReader integration with the ordered
fallback in `upstream/pandoc/src/Text/Pandoc/Readers/Native.hs`: Pandoc, [Block],
Block, [Inline], Inline. Two concrete old-reader gaps are now proved by actual
Read: uppercase numeric escapes `Str "\\X41"` / `Str "\\O101"`, and Text's
Char-list syntax `Str ['a','b']`. Existing NativeReader still rejects these;
its existing fixtures and expectations remain untouched in this checkpoint.
Empty [] must retain the original [Block] fallback precedence. This integration
must follow independent acceptance of the typed Read API.

After integration, remaining finite closure gates are225 Eq/Ord method slots,
58 Walk dictionaries/174 methods and140 module-owned export entries before
constructor/record/class-member expansion, including all four Generic functions
and Definition/Builder algebra. Existing original ordering evidence must be
audited and retained; only demonstrated gaps justify production changes.
local:611 remains open until each named source row has evidence and review.


Read review identified the omitted GHC Read.readListPrec method; this is now a
precedence-aware CharacterString/String API and independently observed via actual
readPrec_to_S readListPrec. The expanded16,936-request matrix retains the prior
14,308 unchanged requests/responses plus2,628 method/reset/ambiguity observations.
All25 dictionaries, five aliases and five primitive dependencies have five
operation mappings. Int64-min/-1/0/10/11/Int64-max enclosing precedences verify
list elements reset to zero; Char's quoted-string override remains distinct.

The additional source cases disproved an earlier broad unambiguity assumption:
Char.readListPrec applies parens outside readListPrecDefault's own parens, so a
bracket list with n groups has n+1 equal parses. Char.readList and Text's String
Read delegate to this grammar. The Kotlin parser retains this multiplicity,
including independent-field Cartesian products, and whole read reports the
original ambiguity error. Native depths0/1/2/3/8, quoted-string contrast and
Text/Inline/Target/Attr dependencies retain complete ordered result lists.
These are identical alternatives in this source branch, not deduplicated values;
no general arbitrary user-defined nondeterministic Read runtime is claimed.
Isolated compilation and all16,936 native assertions pass. Final Gradle session93615
completed successfully in2m03s: actual XML51,826 cases, zero failures/errors/skips.
All seven source verifiers and diff checks pass. Renewed independent review remains
required before this checkpoint is accepted.


Renewed review identified whole-read demand as a separate defect:31 independent
ambiguous Text fields have2^31 equal alternatives, while Prelude.read only needs
two to return its ambiguity error. Whole read now parses with ambiguity saturated
at two and never builds the ReadS result list. The result-list adapter instead
uses a shared read-only view for equal alternatives up to Int.MAX_VALUE; larger
cardinalities are explicitly outside JVM List representation. No universal finite
ReadS parity is claimed. Whole read has no such result-cardinality restriction.

Eighteen new original Prelude.read requests use2/16/30/31/32/64/256/1024 fields
and empty/whitespace tails;2/16-field cases also retain nonspace tails. Native
large rejected tails require exhausting failed alternatives and are not used as
fast demand probes. The16,954-request corpus and isolated Kotlin replay pass;
complete prior16,936 observations remain included. Source/input/response hashes
were finalized before final expanded Gradle session22091. It passed in1m58s:
actual51,844 Read/types/Native regression cases plus nine separately selected FB2
goldens, all zero failures/errors/skips. All seven source verifiers and diff checks
pass. Renewed independent review remains pending; local:611 is still open.


## NativeReader integration checkpoint

Typed Read is accepted in06317ca31379e7fb09a1c4579d4167e50f55d62a: independent
16,954 native byte-exact replay, unchanged prior14,308 requests/responses and
128MiB high-ambiguity stress passed. The final Gradle51,853 count included nine
separately selected FB2 golden assertions, not additional types source rows.

The Native reader now follows exact Text.Pandoc.Readers.Native fallback order:
Pandoc, [Block], Block, [Inline], Inline. Shared.safeStrRead accepts the first
reads result only when its remainder is entirely isSpace; it does not require
uniqueness. Thus empty [] chooses blocks, while ambiguous Text character-list
syntax remains accepted. The accepted parser receives only an internal first-
result adapter (ambiguity cap1); its public Read/ReadS behavior and retained
expected payloads are unchanged. The Read manifest refresh changes only this
production source pin, not any original response or payload hash.

A new207-case original Pandoc3.12.1 CLI oracle retains raw stdout/stderr/status
for all five roots, uppercase octal/hex escapes, Text character lists, nullary
and empty-list fallback, EOF/Unicode whitespace/comment rejection, exact failure
message, nested duplicate parses and31..1024-field first-result demand. Deep
32/128/256 inputs exercise all five entry shapes. Existing Native fixtures and
expectations remain untouched. Isolated execution passed all207 new cases plus
1,424 existing Native assertions and16,954 accepted Read assertions:18,585 with
zero failures. Final Gradle92971 passed in2m12s: actual52,060 cases, zero
failures/errors/skips (previous51,844 plus207 integration cases and nine FB2
goldens). All nine source verifiers and diff checks pass; independent review
remains required. Remaining225 Eq/Ord,174 Walk and140 module-owned export
rows still require closure; this integration does not complete611.


Native review found the ToSources Text boundary step missing: Sources.hs77–78
filters every raw CR before Read, including CR embedded in constructor names,
quoted strings or numeric escape digits. NativeReader now performs that filter
once before fallback; public typed Read still preserves its own raw CR semantics.
The61 new original CLI cases distinguish raw CR from escaped backslash-r and
exercise constructor/record/list/numeric/string boundaries under all five roots.
The earlier207 native responses remain byte-for-byte unchanged; total268 cases
have198 accepted inputs. Isolated execution passed18,646 assertions (268 new,
1,424 existing Native and16,954 Read). Final Gradle60984 passed in1m57s:
actual52,121 cases, zero failures/errors/skips, including268 integration cases and
nine separately selected FB2 goldens. All nine source verifiers and diff checks
pass. The authorized FB2 freeze/capture tool verified its eight relevant sources
before/after and retained fresh6+3 XML assertions with exact command/log/JDK;
no inventory mapping logic was changed. Renewed independent review is required.

This Kotlin entry accepts String corresponding to upstream ToSources Text.
Upstream ToSources Sources identity and [(FilePath,Text)] per-chunk newline/source
position handling are separate unrepresented multi-source APIs; this checkpoint
must not be described as implementing those overloads or completing all Pandoc.


## Eq/Ord 25-dictionary / 225-method checkpoint

Accepted Native integration is a4479abd39f15c094994637bae5c3105b88fe941, with268
independent CLI responses and52,121 passing regression assertions. The FB2 proof
is accepted separately in e279d0e6fbf2bbecef8c2ed5be7c393670583adf. This ordering
checkpoint changes no Native/Read/FB2 test or production file.

`order-closure-manifest.json` enumerates all25 concrete Eq/Ord dictionaries and
all225 slots: eq/ne/compare/lt/le/gt/ge/minimum/maximum. Its source census contains
all79 constructors in actual original Data order. The5,855 new original requests
include all constructor cross-products;92 immediate-field changes;73 competing
field pairs (distinct changed fields on opposite operands);19 original Arbitrary
families at40 seeds and sizes0/1/3/8/30; integer bounds and Format folds; Many
constructor/sequence/IEEE contexts; and126 individual Many demand/method probes.
All native response lines are retained verbatim. Complete original min/max Show
and source <= plus actual class/Seq selection bodies prove caller preservation.

The old75,470 observations, all266 document inputs,17 generator families,
18 IEEE contexts, lazy/deep/partial and100,000-step structures remain unchanged.
Their source/hash verifier and entire existing Kotlin suite pass in isolation.
New and old methods execute together:81,325 assertions, zero failures. Isolation
compiles both tests into an immutable jar, avoiding dependencies on live Gradle
class outputs. No original expectation is changed.

The demonstrated production gap was Builder.Many: neither Inlines nor Blocks
had an AstComparison dictionary. GHC's actual derived methods coerce to Seq.
Seq Eq compares known finite lengths before elements; Ord compares list elements
and uses the source defaults. The two new dictionaries preserve that distinction
and caller wrappers for min/max. Known-spine partial elements verify demand and
method direction; arbitrary bottom-valued StrictData fields, invalid/infinite Seq
spines and a general GHC runtime are not claimed.

Source evidence retains45 complete actual GHC9.12.2 derived Eq/Ord blocks, actual
installed containers0.7 Seq dictionary interface boundaries and the unchanged
original Sequence source/license. Handwritten Format Eq/Ord and Citation Ord are
pinned directly in Definition.hs; class/list/tuple/Maybe/Map/primitive delegation
retains the old authority and observations. Final focused Gradle1879 passed in
1m44s: actual JUnit XML81,325 cases (75,470 existing plus5,855 new), zero failures,
errors or skips. Both ordering source verifiers and diff checks pass. Independent
review replayed all5,855 original responses byte-exact and parsed saved81,325
actual XML cases with no failures/errors/skips; source/default/demand review passes. Walk's58 dictionaries/174 methods and140 module-owned
export entries before member expansion remain open; this is not completion611.

## Walk closure checkpoint pending independent acceptance

The source census in `tools/haskell-types-walk-reference/source-dictionaries.json`
retains all58 original instance clauses verbatim:56 concrete dictionaries plus
Traversable and overlapping pair. `source-manifest.json` maps every one of their
174 walk/walkM/query slots to actual original request IDs, and independently maps
all24 helper exports. The source verifier reconstructs those matrices and the
exact request stream. OriginalWalk.hs retains unchanged source/license/EOF bytes.

The5717 original response lines include5715 represented Kotlin observations and
two source-only StrictData constructor failures, explicitly distinguished in the
manifest. The49 Walk-relevant examples include all20 Inline,14 Block and six
MetaValue constructors plus supported container/citation/table/caption roots;
non-Walk enum/scalar roots are not standalone Walk inputs. Rich ordered table/caption/citation/Figure
positions, empty containers, Unicode Map keys, replacement-not-revisited,
Identity/State/failure/list effects and complete trace/First/Last/Sum queries are
covered. List branching uses ordered four-result prefixes with original explicit
truncation, not a falsely complete exponential list. Separate native probes check
lazy list prefixes, ignored root/callback thunks, First versus Last direction,
Any/All/unit, skipped unrelated bad elements, failure before later elements and
walk/state/query depth10000. The5715 observations pass isolated Kotlin replay
under512MiB heap and256KiB stack.

The new public Walkable interface allows caller-defined child dictionaries.
Independent Probe dictionaries exercise11 polymorphic helper contexts, custom
pure/query overrides and generic/pair composition; mixed built-in Meta/custom
Block witnesses prevent requiring all children to use built-in dictionaries.
ShortCaption is [Inline]; Caption resolves Maybe internally from that dictionary,
as proved by the original helper even with an overlapping Maybe instance present.
The old PandocVisitor, twelve source predicates and their expected data are
unchanged. Production uses persistent continuation frames and lazy query folds;
no Haskell program is a production dependency.

This is a finite source matrix. Undefined/infinite spines, arbitrary StrictData
bottoms and a general GHC runtime are not claimed. Known finite spines with bad
undemanded elements have executable native/Kotlin proofs. Caller-provided effect
adapters override ordinary Monad-recursive tailRecM for deep stack-safe execution.
The actual installed GHC First/Last dictionary interface is retained and
byte-replayed. The whole140 module-owned export matrix remains a subsequent gate;
this checkpoint does not close task611. Independent source/native/XML review
passes as recorded below.

Final focused Gradle44054 passed in1m46s: actual XML11,725 cases, comprising5,715
new closure observations,6,000 unchanged original Walk-property cases and10
unchanged Visitor cases, with zero failures/errors/skips. XML/log snapshots are
saved at /tmp/kotdoc-types-walk-final-results for independent review. Source
verification passes31 hashes and reconstructs174 method slots/24 helpers;
all5,717 original responses and the actual installed GHC Monoid interface replay
byte-exact. No existing production or expected fixture was changed. Independent
acceptance passes; the140-export audit remains pending.

## Pure Walk demand correction awaiting independent review

The accepted Walk finite matrix exposed a further source demand defect during the
Generic audit: original `walk (const Space) (Emph [error "child"])` returns Space,
but the strict Kotlin Identity machine threw child. Pure AstWalkable reconstruction
now uses memoized deferred child lists and Map entries; monadic traversal and query
are unchanged. The original174 slots/24 helpers and their expected fixtures remain
unchanged, with the current production hash recorded by their source verifier.

`tools/haskell-types-walk-pure-reference` retains38 actual original responses:
33 represented Walk observations and five explicitly source-only Generic contrasts.
The33 include constant/constructor/as-pattern/forcing callbacks, bad roots and
list tails, all seven target families, List/Many/Map, custom dictionaries, pair
field/query demand, failure before the second field and a two-element prefix from
a2^30-element list. Five separate Kotlin adapter tests check immutable collection
behavior and memoized values/failures. They are not original observations.

The source-equivalent thunk callback/input adapter is `walkLazy`. Strict Kotlin
callbacks still demand their A argument; custom dictionaries supply an appropriate
walkLazy override for a lazy pure implementation. `WalkLazyPair`/`Walk.lazyPair`
represent independently memoized source fields where Kotlin Pair's strict fields
cannot. Explicit adapters execute the concrete bottom/field demands above; no AST
placeholder substitutes for an unevaluated value. Finite Int-sized containers and
explicit thunks are the represented model, not a general GHC heap/infinite-spine
implementation. Default caller dictionary/convenience methods retain their stated
strict boundaries; source lazy query folds use queryLazy with a lazy monoid.

Focused Gradle69073 passed in1m15s:5,715 unchanged closure cases +6,000 unchanged
original property cases +10 Visitor cases +33 new original Walk observations +five
adapter checks =11,763 actual XML cases, zero failures/errors/skips. XML/log/hashes
are saved in /tmp/kotdoc-types-walk-pure-final-results. Isolated final replay passes
at512MiB heap/256KiB stack. Original5,717 closure responses and38 correction responses,
actual GHC First/Last and Identity interfaces replay byte-exact. Source verifiers
pass31 existing and23 new hashes. Independent review is still required.

The new durable public API census classifies140 module-owned exports and expands
350 source-owned instance rows; it also retains JSON's extra ToJSONFilter class/six
instances and Arbitrary's zero exported names/19 orphan instances. These are audit
rows, not350 accepted implementations. Generic's four functions remain missing;
Many generic APIs, Num/Enum, Data/Typeable/Generic/NFData, HasMeta/ToMetaValue and
remaining per-export source proofs stay explicit gates. This correction does not
close Generic or task611.


### DefinitionList independent-review follow-up, accepted9f9c6cb

The11,763 author run above was not accepted: independent original probes found
an eager DefinitionList outer map and eager accesses to both inner Pair fields.
The complete correction defers the common List node extent, its result extent,
the outer DefinitionList projection and each pair's two List fields. It also
memoizes failed extents. This repairs the represented constructor demand family,
rather than adding exceptions for the two reported fixtures.

Fourteen new original observations cover both exact reproducers, reverse field
selection, terms/definitions/blocks prefixes, deliberate forced-field errors,
empty prefixes and equivalent nested LineBlock/BulletList/OrderedList/definitions
paths. Both direct review reproducers now pass. The initial38 request, response
and paired-fixture streams remain byte-identical prefixes, checked by their
original SHA-256 values; no prior expected value was rewritten. The accepted
5,717 source closure responses and their fixtures are unchanged.

The new native stream has52 rows:47 executable Walk observations +five source-only
Generic contrasts. The new Kotlin class has52 tests:47 original observations +five
adapter checks. These are different partitions; Generic parity is not claimed.
Final Gradle35230 passed in1m29s with11,777 actual XML cases/zero failures/errors/
skips:5,715 closure +6,000 original properties +10 Visitor +47 original demand
observations +five adapter checks. Isolated47+five+5,715 replay passes at512MiB/
256KiB. Saved XML/log/frozen paths are in /tmp/kotdoc-types-walk-definition-final-results.
Source verifiers pass31 existing and23 correction hashes, preserve all174 slots/
24 helpers and replay52 original correction responses byte-exact. Independent
review reran both reported reproducers against the final isolated jar and accepted
the correction in9f9c6cb2962cfb4d28503bfb5588507df0478aad. Generic4/all140 exports/
350-instance members/JSON supplement remain separate open gates.


### Generic4 checkpoint: awaiting independent acceptance

The four source functions now have explicit Kotlin Data/Monad dictionaries and
source thunk adapters. Source extraction records23 Definition Data types,77
constructors/five aliases; all77 constructors occur in the independent native
corpus. Dependency instances expose list head/tail, tuple fields, Map's ascending
entry list, Text's String field, Seq's viewl head/tail and generic Many. Existing
AST adapters are separate from canonical lazy GenericNode/list/tuple values;
strict Kotlin scalar records are not silently substituted for partial fields.

The807 original requests comprise26 core cases,92 constructor/alias/Many inputs,
644 target variations,33 custom/partial/monad/default-foldl/DefinitionList cases
and12 deep cases. They produce7,360 result/effect/query comparisons through
canonical and existing AST adapters,4,463 Data/Typeable/field-order nodes and72
deep observations through list20000/Inline10000. Native TyCon module/name/package
and recursively every type argument are checked, including List/Tuple2/Tuple3 and
GHC.Internal.Maybe. Surrogate Char versus Text.pack, Unicode key collisions,
NaN/Infinity/negative zero and source aliases are covered independently. All six
initial core/constructor/target streams remain byte-identical guarded prefixes.

The source demand cases exposed a draft monadic bug: Identity must retain lazy
descendant results, not always use the strict State/List effect machine. The
public GenericMonad protocol supplies action/result thunks; Identity, Reader and
Maybe execute original partial-field/failure cases. Source everything foldl
still demands the immediate gmapQ spine before the monoidal result; Walk query
shortcuts are not reused. The generic DefinitionList adapters execute both prior
Walk-style independent-field reproducers plus definitions-only selection.

Author Gradle84220 passed12,585 actual XML cases/zero failures/errors/skips and
jar/installDist. Required recursive Typeable argument validation was then added
(test-only); final Gradle91143 passed the same12,585 cases in1m51s, with packaging
green:808 Generic +5,715 Walk closure +52 pure demand +6,000 original properties
+10 Visitor cases. XML/logs/source hashes are saved in /tmp/kotdoc-generic-final-results.
Original807 responses and actual installed GHC Data/Typeable interfaces replay
byte-exact;66 source/artifact hashes pass. The isolated808-case JUnit run passed
at512MiB heap/256KiB stack before the final recursive-argument test strengthening.
Final Gradle executes that strengthening against the same independent native data.

These are finite explicit representation/adaptor bounds, not a general GHC heap,
arbitrary callback stack or infinite-spine equivalence. Generic traversal's minimal
Data protocol does not certify every Data/Typeable/GHC.Generic/NFData class member.
Independent acceptance is still required. Full140 exports/350 expanded instance
members/JSON supplement remain open; generic Many/Functor/Foldable/Traversable,
Num/Enum, metadata and remaining per-export proof gates remain in the durable audit.


### Generic independent-review attribution correction

Independent review accepted the807 native/interface/schema/protocol/AST/effect
checks and saved12,585-case regression, but blocked missing exact syb attribution.
The correction preserves `(c) The University of Glasgow, CWI 2001--2003` in the
translated Schemes source. Full OriginalSybLICENSE is copied byte-for-byte to
licenses/syb-0.7.4-LICENSE.txt; the original17-line Schemes header is retained
verbatim as licenses/syb-0.7.4-NOTICE.txt. No behavior or fixture changed.

Gradle51615 passed the affected source-integrity case (one actual case/zero
failures/errors/skips), jar and installDist in1m40s. Verifier passes68 hashes and
both original-copy relationships. Both notices match their exact source bytes in
META-INF/LICENSES, including the CWI credit; installed core JAR is byte-identical.
Core JAR SHA256:81090515b4bd1229c8e2d3d763e90b9bb9df12f525267664b69efe050715ef47.
Saved license-fix XML/log/package proof are under /tmp/kotdoc-generic-final-results/license-fix.
The earlier12,585 semantic regression is retained; this attribution-only change
requires no expectation or semantic-suite rewrite. Repeat review/integration is
pending and full611 remains open.

### Generic acceptance and next Many dictionary checkpoint

Generic's four-function checkpoint and the exact syb attribution/package
correction were independently accepted and pushed as
`1171e93b2c7666043e6bbfac387fd81f76f1b22c` (611 remains open). This supersedes the
pending-review wording in the historical author report above; it does not
certify all Data/Typeable/GHC.Generic/NFData instance members.

The next source-closed Many slice is the 42-slot matrix at
`tools/haskell-types-many-reference/acceptance-matrix.json`: Functor2,
Foldable17, Traversable4, specialized Inlines/Blocks algebra13, four helpers and
Many/unMany2. Original Builder and GHC interfaces resolve every derived/default
method. There is no source Applicative/Alternative/Monad/NFData Many instance.
The Kotlin effect protocol's Monad constraint is not a Monad instance for Many.

Author isolated JUnit bodies currently pass5,128 cases:5,080 raw original
responses,47 demand responses and one source-integrity check. The native verifier
independently replays all5,127 responses byte-for-byte and validates the actual
GHC/Builder interfaces. Core1,407; Seq structure1,596; algebra1,288; numeric364;
traversal grouping425; demand47. Source numeric observations cover NaN, signed
zero, Float64 association and Int64 wrap; partial-field probes distinguish strict
append middles from lazy fmap/repetition and cached length. Shared2^40 repetition
executes without expanding its values; source-list step/head/tail adapters avoid
requiring an Int-sized JVM List. These are author results pending installation,
focused Gradle and independent review, not a completion claim.

The remaining gate is still the complete140-export/350-expanded-instance-member
matrix and JSON.ToJSONFilter supplement, including other generic Many class
members, Num/Enum count types, metadata conversions/HasMeta, remaining Builder
exports, Data.Version and complete Data/Typeable/GHC.Generic/NFData surfaces.
Existing concrete Read/EqOrd/Walk/JSON witnesses do not automatically close those
families. Source-language thunk/list/effect adapters execute the represented
partial cases; arbitrary GHC heaps, infinite spines and unbounded JVM callback
stack behavior are not claimed.

Many author final gate: Gradle65560 `BUILD SUCCESSFUL in1m20s`,17,713 actual
cases/zero failures/errors/skips:5,128 Many +808 Generic +5,715 Walk closure
+52 pure demand +6,000 original properties +10 Visitor. Final verifier64 hashes
and all5,127 original raw responses/GHC+Builder interfaces pass byte-exact.
JAR/installDist pass;13 major new class entries and four retained notices are
verified; installed core JAR is identical. Saved XML/command/log/frozen hashes are
at /tmp/kotdoc-many-final-results. The 42-slot checkpoint is frozen for independent
review; no source expectation or existing production file changed. The enclosing
140/350/JSON and generic Many instance gates above remain open.


Many42-slot checkpoint independently accepted at
5adcfdb5972f62bb81bdfbc4531610b790e0f94e (17,713 actual regression cases).
The earlier author/pending wording records its historical preparation stage.

Count Num/Enum author checkpoint adds three distinct typed dictionaries:
RowHeadColumns, RowSpan and ColSpan; seven Num and eight Enum methods each,
45 slots total. Exact source/default/runtime mapping and original case IDs are
in tools/haskell-types-count-reference/acceptance-matrix.json. Original native
responses:9,930 finite (including351 Cell/TableBody bridge cases and complete
10,000-element ranges) plus48 newtype/demand observations. Isolated Kotlin
JUnit bodies:9,980 including source integrity and one explicit sharing adapter.
Source construct/match thunks preserve represented ignored-root/field behavior;
strict value-class convenience boundaries are explicit. Ranges expose lazy
GenericList views, including zero-step infinite streams through bounded source
observations. Long arithmetic wraps exactly; fromInteger accepts BigInteger;
legacy AST Long fields remain unchanged. No Real/Integral/Bounded instances are
invented. This checkpoint awaits final shared Gradle and independent review.
Other generic Many/count derived classes, metadata/Builder/Data.Version, all
140exports/350expanded instance members and JSON supplement remain open.


Count author final shared gate: Gradle96735 `BUILD SUCCESSFUL in1m47s`,
27,693 actual cases/zero failures/errors/skips:9,980 Count +5,128 Many +808
Generic +5,715 Walk +52 pure demand +6,000 original properties +10 Visitor.
Final38 source hashes and9,978 native responses/interfaces pass; package
eight major classes/two exact notices/identical installed core JAR pass.
Saved XML/command/log/package proof: /tmp/kotdoc-count-final-results.
The45-slot checkpoint is frozen for independent review; all remaining gates
listed above remain open. No accepted production or original expectation changed.


Count45-slot checkpoint independently accepted at
db0341b4533491b61eb9998a6e5fb26919a5d7c9:27,693 actual regression cases,
9,978 raw original responses/interfaces/package and38source pins.

Metadata author checkpoint covers15 dictionaries/25 method slots and10 public
helpers. The nine ToMetaValue instances retain distinct Text/String and
Map Text/Map String dictionaries, recursive typed conversions and custom
source dictionary demand. HasMeta Meta and Pandoc each have setMeta/deleteMeta.
Meta/Pandoc each include actual Semigroup3 and Monoid3 defaults. The complete
source-to-member/helper/original-case matrix is
tools/haskell-types-metadata-reference/acceptance-matrix.json.

Original5,084 finite responses and55 partial/demand observations match Kotlin;
actual GHC Base/Definition/Builder interfaces match. Isolated actual JUnit bodies
pass5,144 cases at512MiB/256KiB:5,139 originals,3 legacy helper regressions,
integrity and one sharing adapter. Before/after legacy repros preserve independent
source expectations. Only docTitle/docDate/docAuthors bodies in Metadata.kt
delegate to the new API; other functions and SimpleFigure stay byte-identical.
Source optimized defaults' strictness and imprecise-exception order are recorded
for the pinned build, rather than falsely promoted to universal Haskell semantics.
Explicit source root/list adapters and finite ordered keys with lazy Map values
are separate from strict scalar/JVM Collection convenience boundaries.
This metadata slice awaits shared Gradle and independent review. Other generic
Many/count derived classes, remaining Builder exports, Data.Version, JSONfilter
and full140exports/350expanded instances remain open.

Metadata final adapter gate adds a separate JVM Map/Entry contract test after
a source-preserved before/after repro found unequal hashes/entry sets and failed
HashSet lookup for equal maps. Entry equality/hashCode are now structural.
The actual isolated factory now passes5,145 cases, including that extra Kotlin
contract test; all5,084 finite and55 original demand expectations stay unchanged.
The earlier5,144 author gate and shared34870/32,859 results remain historical
pre-correction evidence; the corrected shared run is recorded separately below.


Metadata author final shared gate: corrective Gradle76981 BUILD SUCCESSFUL
in1m24s,32,860 actual cases/zero failures/errors/skips. Metadata5,145 plus
accepted Count/Many/Generic/Walk/properties/Visitor27,693 and Builder22. Final
5,084 finite +55 native demand responses and actual interfaces match;51pins
pass. The extra case after the earlier32,859 gate is a Kotlin Map/Entry/HashSet
contract test, not a fabricated original response. Seven major packaged classes,
four byte-exact notices and identical installed core JAR pass. Nine XMLs,
commands/logs/package/immutable JAR/frozen hashes: /tmp/kotdoc-metadata-final-results.
The complete25-method/10-helper slice is frozen pending independent review.
All other140/350/JSON and derived-class/Builder/Data.Version gates stay open.
