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
| Derived Read | Definition.hs23 data/newtypes; Builder.hs Many; native GHC dictionaries | Current review checkpoint:16,954 native requests,25 concrete dictionaries/79 constructors+5 aliases, all four methods | Finite typed matrix implemented and tested; independent review pending. NativeReader integration remains separate |
| Eq/Ord | Definition.hs derived/manual dictionaries; Builder.hs Many | Existing `AstComparison`, 75,470 independent observations, source dictionary dumps | Audit open; retain existing implementation unless a concrete gap/defect is demonstrated |
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
