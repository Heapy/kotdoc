# Public source-spine traversal audit (additional to 74 extent sites)

The 74-site audit omitted protocol bypass: avoiding size preallocation is not
sufficient if an API invokes a source-only List's ordinary iterator. The rejected
185-path bundle is retained at /tmp/kotdoc-asciidoc-audit-final, with source copies
under /tmp/kotdoc-asciidoc-audit-185-baseline. No old expectations are changed.

| Reachable traversal | Source contract / current route | Proof |
|---|---|---|
| Writer Note blocks | Note [Para]/[Plain] constructor before required tail; common cursor, wrong constructor rejects without tail | protocol-only note-wrong-head/37 and note-plain; old Note38 controls |
| Writer Span classes null | null demands spine only; asciiDocNull checks explicit cell before ordinary fallback | protocol-only span-empty/mark/roles |
| Writer CodeBlock classes sequence/delete/join | source consumes required class spine, via cursor sequence; no raw SourceOnly iteration | protocol-only code-classes |
| Writer LineBlock lines and caption lines | outer lists use cursor before flatMapIndexed; each inner list wrapped before concat | protocol-only line-outer; old nullLine/source tests |
| Writer BlockQuote second any | source predicate after completed block actions; use cursor for public original list on second pass too | protocol-only quote-recheck |
| Caption Blocks/items/definitions/table rows/cells | source Builder concatenation still requires its extent. asciiDocWholeSpine retains the ordinary size representation of required extent, and uses cursor source cells for explicit protocol values | protocol-only caption-blocks/list-merge/merge; old caption/deep72 |
| Caption Inline endpoint monoid merges | local pure shape-map wraps mergeable child lists without inspecting child heads or tails; Builder endpoint concatenation receives cursor Lists | caption-list-merge; shape-map sharing/independent head-tail test |
| Writer body/inlines/list/task/Figure/makeCell/table rows | already common cursor or asciiDocMap; task/makeCell iterators are over cursor values, not raw input | all source-mode fixtures now forbid ordinary iterator/size/get |
| Writer width/align/percentages/options/docs | computed internal closed lists; public columns consumed by asciiDocMap first | old width/order and table family proofs |
| Tables placements sort and footer length | source-required extent remains; whole-spine adapter makes source-only List legal without relaxing sort/length | old helper2460 and Table19 |
| Tables active/pending/change chains | internally constructed finite state; raw map/any/reverse/iteration cannot reach supplied List interface | Table19 and deep72 |
| Sections input/classes/KV | input cursor, source predicate helpers, generated filter/nub/concat; merge-tail Sequence is already cursor | Attr8, root/query, productive infinite classes |
| MinLevel/Stringify | supplied List branch wraps cursor; Iterable fallback only receives internal child vectors; frame iterators only iterate constructed internal vectors | List15 +Stringify34 +protocol-mode source families |
| CommaWalk | every supplied AST child collection uses local lazy map over cursor; character split iteration is strict Text output | Walk34 +Comma5 |
| Metadata | MetaList uses cursor; Map keys are separately sorted scalar keys with values fetched per callback; no supplied List bypass | Metadata6 +Map1 |
| Patterns | only raw input.iterator is the explicitly guarded ordinary-List fallback; source protocol checked first. Generated sequence iterator is owned, cached output | source-only tests; sharing |
| Attrs / Chomp | attrs inputs use cursor; Chomp operates on typed finite Doc constructors, not supplied List | Attr8; prior chomp/deep proofs |

Borrowed helpers: Builder Inlines endpoint merging is explicitly adapted above.
Header identifier helper receives a local lazy identity AST/list bridge after
content rendering. Its full Inline/Block/citation/caption/table/Attr field mapping
provides ordinary List views backed by the common source protocol; it does not
change identifiers or call the external oracle. IdentityListOracle uses original
Walk id over the retained34 constructor requests, and a nested header source-only
control exercises the actual borrowed helper entry. Strict Map/Text/Doc-field domains and existing shared611/612/621 gaps
are unchanged. This audit does not claim arbitrary lazy String/Doc bottom parity.

The SourcePrefix test adapter now throws from ordinary iterator, get and size in
all source-mode fixtures; the same expected bytes remain unchanged. New original
ProtocolOnlyOracle observations are additive, not generated from Kotlin. Ordinary
List mode remains separately executed, retaining the original iterator bridge.

The first corrective draft revealed an ordinary caption regression: unconditionally
wrapping a List before pure map changed its required-extent failure to a head
failure. That rejected draft is not the installed artifact. asciiDocWholeSpine
keeps required ordinary size demand, while adapting explicit source cells before
the same pure map/sort/length. Action-order traversal still uses the common cursor.
This is a public interface distinction, never a fixture-class/type-name test.

The ordinary cursor now delays iterator.next until that head is requested, or
until advancing past it requires consumption. Merely observing a second cons
does not extract its head. Existing note/multi-ignored-inline exposed this demand
regression before installation and now passes unchanged. The explicit protocol
still separates heads/tails where an ordinary iterator cannot do so.

Caption adaptation is memoized by input identity within one Session. Adapted
results map to themselves, preventing repeated pure wrapper layers through nested
Div captions. No global state or cached rendered action is used; different writes
create different caches. A pre-fix bounded caption1024 stack failure is retained
in /tmp/kotdoc-asciidoc-protocol-bounded4; final bounded5 passes all1425/deep72
at512MiB/256KiB. The latest grouped241 passes at128MiB/256KiB.
