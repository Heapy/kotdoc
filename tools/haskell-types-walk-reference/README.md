# Original pandoc-types Walk closure

This development-only oracle invokes the pinned, unchanged Text.Pandoc.Walk
implementation. Production traversal is Kotlin; neither GHC nor upstream Pandoc
is a production dependency. OriginalWalk.hs is a byte-exact copy of Walk.hs with
its original copyright/license/EOF bytes. The source census retains all58 clauses
verbatim:56 concrete dictionaries, generic Traversable and overlapping pair.
Their walk/walkM/query methods give174 explicit slots. All24 exported helpers
have separate method mappings; twelve old Pandoc properties are not used as a
substitute for these contracts.

The corpus exercises a49-example Walk-relevant subset: all20 Inline,14 Block
and six MetaValue constructors, plus supported Meta/Pandoc/citation/table/caption
roots; non-Walk enum/scalar roots are not standalone Walk inputs. The corpus
also exercises empty/nonempty lists and metadata, distinct rich table/caption/citation/Figure positions, Map Unicode
ordering, replacement children which must not be revisited, Identity/State,
ExceptT+State failure and list-monad branching. Branching retains the first four
results plus original lookahead/truncation, without attempting exponential complete
lists. Complete query lists preserve order and multiplicity. First, Last, Sum,
Any, All, unit and lazy list-prefix probes distinguish callback order from demand.
Three depth10000 probes cover walk, state walkM and query under a256KiB JVM stack.

Caller-defined Probe dictionaries independently exercise the polymorphic helper
constraints, distinct pure/query method overrides and both pair fields. A mixed
built-in Meta/custom Block case checks composition of supplied dictionaries.
ShortCaption is the alias [Inline], not Maybe [Inline]. The original Caption
helper resolves Maybe traversal internally from that child dictionary; even a
separate overlapping Probe/Maybe instance at the call site does not replace it.
The Kotlin helper preserves that source dictionary selection.

The finite matrix is5717 original response lines,5715 represented Kotlin
observations and two explicitly source-only StrictData constructor failures.
Those two construct Pandoc with an undefined entire Block-list spine; the source
throws before entering Walk. They are retained and byte-replayed, not advertised
as Kotlin AST behavior. Known finite spines containing undemanded bad elements
have separate executable Kotlin/native cases. Infinite/undefined container spines,
arbitrary strict-field bottoms, impurity in query callbacks and a general GHC
runtime are not claimed. Kotlin AST/container inputs follow the existing immutable
input convention. Generic container adapters describe a finite ordered view;
select named concrete dictionaries for source overlapping list instances.

WalkMonad has the ordinary Monad recursion default for tailRecM. A caller supplies
a stack-safe override for deep effects; the library Identity and test State/list
adapters do so. WalkMonoids.sequence is the lazy list-monoid adapter; list is its
strict finite materialization. WalkQuery exposes forward/reverse lazy folds so
Last need not evaluate an erroneous left branch. queryLazy additionally represents
callbacks which do not demand their argument. A custom dictionary may override its
lazy query adapter. Source methods remain walk, walkM and query; these extra types
represent Kotlin's lack of higher-kinded types and implicit typeclass dictionaries.

Actual installed GHC9.12.2 GhcMonoid.iface.txt retains the complete original
First/Last dictionary and append strictness: First demands the left argument,
Last the right. The original transformers0.6.1.2 State/Except sources are pinned
for effect sequencing. Copyright/licenses remain in the original source and
licenses/pandoc-types-BSD-3-Clause.txt, GHC-base-BSD-3-Clause.txt and
Transformers-BSD-3-Clause.txt.

## Reproduction

The session reused the existing unchanged-library build directory
/tmp/kotdoc-types-order-closure-build; a fresh dedicated build directory also works.

```sh
PATH=/tmp/kotdoc-haskell/ghc/bin:$PATH CABAL_DIR=/tmp/kotdoc-haskell/cabal-state \
 /tmp/kotdoc-haskell/cabal build exe:types-walk-reference \
 --project-dir=tools/haskell-types-walk-reference \
 --builddir=/tmp/kotdoc-types-order-closure-build

python3 tools/haskell-types-walk-reference/verify.py --check-source \
 --ghc /tmp/kotdoc-haskell/ghc/bin/ghc \
 --binary /tmp/kotdoc-types-order-closure-build/build/aarch64-osx/ghc-9.12.2/kotdoc-types-walk-reference-0.1.0.0/x/types-walk-reference/opt/build/types-walk-reference/types-walk-reference
```

verify.py validates every source/fixture SHA256, reconstructs the exact request
stream, all174 method-slot mappings and24 helper mappings, then optionally
byte-compares the complete native response stream and actual installed GHC
interface. --record is for recording an intentional new source manifest, not
updating original expected values. capture.py --binary PATH --output DIR writes
an original-only capture to a separate directory. Gzip fixture contents are the
verbatim original request and response streams; no Kotlin-generated expected
values or deduplication participate.

Focused Kotlin validation uses OriginalTypesWalkClosureTest,
OriginalTypesPropertiesTest and PandocVisitorTest. Whole task611 remains open
until the separate140 module-owned export entries and remaining source gates in
docs/pandoc-types-porting-matrix.md are resolved and independently accepted.
