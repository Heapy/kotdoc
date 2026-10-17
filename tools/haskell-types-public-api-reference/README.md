# Public API audit in progress

The source census contains140 module-owned export entries before expanding
constructors, fields and typeclass methods: Definition37, Walk25, Builder74,
Generic4. Builder's Definition module re-export and imported(<>) operator are
explicitly recorded separately. Operator behavior and all source instances still
require the independent member/instance audit; they are not dropped from scope.
All140 rows now have conservative classifications in export-matrix.json.
The current export matrix has103 accepted finite source-function rows, three
accepted Num/Enum dictionary rows and five accepted finite dictionary rows.
The other27 representation rows and two partial rows are not closed by names.
Generic4 is independently accepted; the new24 derived Generic instances are a
distinct source/member gate. expanded-instances.json contains350 source-owned instance rows, including derived
and Template Haskell instances; their member audits remain explicit work.

Read-only task snapshot: exact `kotgent task show local:611` in this project's cwd;
project4079779d-7715-4f0e-b255-45a14648c022, taskstate todo, no dependencies,
dependent local:623. No task/pane state was mutated. Parent authorization covers
this independent workstream; shared pane613 was not changed.

Initial direct source reads identify the following concrete audit priorities:

- Generic.bottomUp/topDown/bottomUpM/queryWith delegate to Data.Generics Data
  traversal, which includes list tails and every Data field. PandocVisitor's
  restricted AST traversal is not evidence for these four contracts. The new
  Generic checkpoint supplies canonical Data/Monad/thunk dictionaries and existing
  AST adapters with807 original requests/808 isolated JUnit cases; final91143
  Gradle12585zero/package passed and independent acceptance remains a gate.
- Builder.Many is generic over arbitrary element types and derives Data/Ord/Eq/
  Typeable/Foldable/Traversable/Functor/Show/Read/Generic. Kotlin currently has
  separate Inlines/Blocks wrappers. Named map/fold/traverse/Many APIs and their
  semantics need explicit audit despite accepted concrete Read/EqOrd/Walk proofs.
- Definition.RowHeadColumns/RowSpan/ColSpan derive Num and Enum; Kotlin AST stores
  Long fields, so representation alone does not prove the complete typeclass APIs.
- HasMeta has both Meta and Pandoc instances; current Metadata.kt directly shows
  only Pandoc set/delete functions. ToMetaValue includes overlapping String and
  recursive list/Map Text/Map String instances. Exact APIs, Unicode collision
  behavior, ordering and independent proof remain to be audited.
- Remaining Builder constructors, metadata helpers and table functions have
  implementations/tests, but their complete source-to-proof mapping is pending.

`jbcontext search` was attempted first. It failed because the sandbox cannot
access the OS-keychain-backed stored session. No authentication state was changed;
the audit used the already-known pinned source and Kotlin AST paths.

The initial census was read-only outside this folder; later owned Generic
implementation/proof paths are documented separately in its reference README.
No Git/tracker/pane mutation is performed by these scripts. Next: enrich all140 rows with concrete Kotlin,
original-test and independent finite-proof references; expand all members and
instances; implement coherent missing modules after agreed ownership/review.

Current Generic implementation: ../haskell-types-generic-reference documents all
four functions,23 Definition Data types/77 constructors/five aliases plus generic
Many and dependency fields. The807 original requests and808 isolated JUnit cases
include structural canonical casts, existing AST adapters, Unicode/Text surrogate
packing, Typeable field order, lazy Identity/Reader/Maybe, partial DefinitionList
fields and list20000/Inline10000 deep tests. These author results are not yet an
accepted Generic row or proof of every Data/Typeable/Generic/NFData class member.
Full140 exports/350 expanded instances and JSON's ToJSONFilter supplement remain
open, with Generic acceptance followed by generic Many/Num/Enum/metadata/API gates.

Accepted Generic4 is1171e93b2c7666043e6bbfac387fd81f76f1b22c; NFData23 is7e9500f27942769cca9280fbde790859346f8093. The new derived-Generic instance checkpoint is distinct from Generic4 Data traversal. It adds author evidence for24 associated Rep types/48from-to members, with complete original metadata/shape, direct partial products and custom dictionaries. expanded-instances marks these24 as author evidence, not independent acceptance. NFData23 rows now reference accepted exact rnf evidence. Generic Many Eq/Ord/Read/Show/Data/Typeable and remaining Definition/count members, Data.Version, six ToJSONFilter and JSON supplement stay open;140-name classification is not closure.
