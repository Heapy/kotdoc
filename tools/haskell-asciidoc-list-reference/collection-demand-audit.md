# Owned AsciiDoc collection demand audit

The accompanying JSON classifies every one of the 74 recorded collection/extent
callsites. Its line numbers refer to the retained pre-correction inventory, rather
than pretending that new source line numbers are historical identities. The old
115, 132, 1709 and 1753 bundles remain immutable failed/intermediate baselines.
This is an audit record, not an acceptance or a final current-artifact binding.

## Corrected raw-input boundaries

* Writer, Sections, MinLevel: a common memoized cursor handles ordinary iterable
  prefixes and explicit independent head/tail cells. No renderer path selects a
  special test-list marker. Singleton constructor checks demand the constructor
  before precisely the following spine required by the original pattern.
* Tables: lazy cell/row maps, header/body/footer concatenation and placements
  preserve cell actions before subsequent rows and pure column formatting.
  `sortOn` placements, `maximum` row spans and positive-span footer `length` are
  source requirements, not accidental Kotlin preallocation; they remain.
* Sections/Attrs: elem/notElem guards, filtered header KV, prepended section class,
  nubOrd classes and combineAttr's foldr/lookup preserve their separate demands.
* Stringify: local walkfix/query closure ignores Note bodies and Cite metadata;
  line/title null checks inspect the spine without demanding a head.
* Metadata: local mapM evaluates callbacks in key/list order. Sorting keys does
  not fetch later Map values before the first callback. Variables merge happens
  only after every raw metadata callback has completed.

## Borrowed helper boundaries

| Call | Source and demand classification | Evidence / limit |
|---|---|---|
| Shared.stringify | Replaced locally; walk/query constructor closure | NullTitle4 and Stringify34 original observations |
| Shared.combineAttr/makeSections | Replaced within False/Nothing sections closure; unused attrs remain deferred | Attr8 and existing section/query controls |
| Writers.Shared.metaToContext/metaValueToVal | Replaced locally; mapM callback order, no Collection.size or copied lazy Map values | Metadata6 plus separate map-value-order probe |
| Writers.Shared.addVariablesToContext | Receives completed defined Context; no raw MetaList survives | Metadata gate; general arbitrary deferred Val fields outside this finite AST bridge |
| Shared.tableBodiesToRows / allRowsEmpty | Local lazy concat and null predicate | Table19, ordinary and explicit spine modes |
| PandocVisitor walk | No remaining call in owned Writer/Sections/MinLevel/CommaWalk/Stringify closure | CommaList5 and full constructor Walk34; shared Visitor gap retained |
| Builder caption mconcat | Source complete concatenation spine is required; do not lazify merely to avoid an error | Original caption/deep and adjacent caption controls |
| Builder.text metadata strings | Strict Text tokenization; MetaString fields are already defined strict Text | Original metadata/string profile cases |
| Dimensions.dimension | Ordered lookup predicates; rendering width/height necessarily demands selected strict values | Original image/options corpus; arbitrary deferred String fields remain native-only |
| Text casefold / identifiers | Exact text2.1.2 profile; no general Unicode library replacement | Retained full mapping and classifier domains |
| DocLayout / private chomp | Finite constructed Doc closure, trailing-spine demand and writer-session identity cache | Chomp11154, deep72; deferred Doc native-only controls remain distinct |
| Templates / title accessors | All metadata callbacks complete before template/title result observation | Metadata ordering controls; global App/template gaps remain 621 |

## Domain and proof accounting

Ordinary List iterators cannot separately expose an undefined head and a defined
next spine: iterator.next necessarily returns that head. The explicit public
AsciiDocListSpine protocol supplies independent lazy head and tail observations;
representable ordinary prefix/error cases still run through the same algorithm.
No equivalence to arbitrary Haskell bottom values is asserted for strict Kotlin
AST fields, Strings or finite Doc constructors. Infinite source lists requiring
maximum/sum/length do not produce a finite observation; productive ignored-class
spines are covered by two original controls executed in both List modes.

Fresh draft proof: Table19 x two modes =38; NullTitle4 +Attr8 +Metadata6 x two
modes =36, all source observations matched at 128 MiB heap /256 KiB stack.
Metadata Map source/current both produce map-a. The grouped isolated test gate
executes 195 registrations at 128 MiB/256 KiB, including all new writer families,
both productive infinite controls in both modes, Stringify34, CommaWalk34 and
cursor sharing. Latest bounded1425/deep72 and current package/CLI bindings remain
separate required gates; this document is not a current-artifact acceptance.
