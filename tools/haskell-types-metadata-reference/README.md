# Original metadata dictionaries and helpers

This coherent slice ports nine Builder ToMetaValue dictionaries, two HasMeta
instances (two members each), and Meta/Pandoc Semigroup/Monoid dictionaries
(three members each):15 dictionaries/25 method slots. Ten public helpers are
covered separately: nullMeta, isNullMeta, lookupMeta, docTitle, docAuthors,
docDate, doc, setTitle, setAuthors and setDate. acceptance-matrix.json records
exact pinned source/member/default locations, Kotlin targets and original case IDs.

The oracle imports unmodified pinned Definition/Builder. Its recursive typed
Decoder constructs actual Haskell values and invokes actual class dictionaries;
expected output never comes from Kotlin. Original GHC Base, Definition and Builder
interfaces preserve the real defaults and specialized strictness. Upstream's
JSON metadata examples remain unchanged; there is no standalone metadata class
property suite in test-pandoc-types.hs. These original-native observations supply
independent direct contract evidence rather than claiming JSON is enough.

There are5,084 finite responses: all constructors and helper branches, empty and
nonempty maps/lists, recursive list/map conversions, source String/Text overlap,
surrogate packing/mapKeys collisions, Unicode/zero keys, overwrite/delete and
right-biased metadata algebra, all six algebra methods per carrier, negative/zero
stimes errors and large Integer counts through2^200. Twelve additional depth/size
cases include4096 nested MetaList constructors,4096 authors/skip elements and
4096-element concat/sconcat inputs. All inputs are finite; a complete result is
not an assertion about arbitrary infinite map keys or GHC heaps.

There are55 original demand probes, including caller-defined ToMetaValue values
that ignore their input, lazy mapped values/list elements, StrictData field WHNF,
newtype Meta map behavior, non-strict unused HasMeta values, grouped author scans,
head-versus-tail pattern order, source class defaults and controlled error order.
Pinned optimized GHC dictionaries force the sconcat head before its tail and the
stimes value even on zero/negative multipliers. These are observations of this
compiler build's imprecise-exception order, not a claim that Haskell specifies
all orders between multiple bottoms. The actual interfaces are also verified.

MetadataMap holds finite ordered source Text keys and shared lazy values;
MetadataDocument forces the Pandoc field WHNF while retaining lazy list elements.
ToMetaValue/HasMeta are explicit dictionary protocols, not runtime Any dispatch
or a Kotlin compiler typeclass resolver. Source Text uses the existing AST's
normalized scalar String representation. Source String uses GenericList<Int>
Char code points, including surrogate Char values that Text.pack replaces.
The overlapping source String dictionary is explicitly `string`, not a generic
list of a user-invented Char conversion. `mapString` accepts the finite source
Map String keys/value-thunk adapter (input pairs are normalized as Map.fromList),
then applies ordered mapKeys Text.pack before converting values. Map Text and
Map String remain separate dictionaries. Native tests prove their different
collision behavior. Source-list observations use step/head/tail; JVM Collection
size/take helpers may demand the entire spine and are not the source consumer.

The old public docTitle/docDate/docAuthors helpers delegate to the new source API.
Only those three bodies change in Metadata.kt; all other functions/SimpleFigure
remain byte-identical. OriginalMetadataBefore.kt.txt and legacy-before.txt retain
reproducible pre-fix evidence: title/date raised tail before head and authors
forced an unrequested later value. LegacyMetadataMismatch.kt reproduces the same
three observations; legacy-after.txt and three direct JUnit regressions match
unchanged original demand expectations (head/head/[[Str "i"]]). No fake AST
placeholder or expected-value rewrite is used. Sharing has one separate Kotlin
adapter test, not an extra original response.

The isolated actual JUnit factory passes5,145 cases at512MiB heap/256KiB stack:
5,084 finite +55 demand +3 legacy helpers +source integrity +one sharing test +one JVM Map contract test.
Production conversion is Kotlin; Haskell is dev-only. Source copyrights and full
licenses are retained, including pandoc-types, GHC-base, containers and text.

Other generic Many/count derived classes, remaining Builder exports, Data.Version,
JSONfilter and the complete140-export/350-expanded-instance audit remain open.
This metadata checkpoint alone does not complete local611 or Pandoc/calibre.

## Reproduction

Use cached GHC9.12.2/cabal and the frozen project:

```
cabal build exe:types-metadata-reference exe:types-metadata-demand \
 --project-dir=tools/haskell-types-metadata-reference \
 --builddir=/tmp/kotdoc-types-order-closure-build
python3 tools/haskell-types-metadata-reference/verify.py --check-source \
 --oracle /path/to/types-metadata-reference \
 --demand-oracle /path/to/types-metadata-demand --ghc /path/to/ghc \
 --definition-interface /path/to/Definition.hi --builder-interface /path/to/Builder.hi
```

For the legacy before proof, compile the preserved OriginalMetadataBefore.kt.txt
as a temporary Metadata.kt alongside LegacyMetadataMismatch.kt against a copied
core jar, then run that small main. Compile the same main with the new Metadata.kt
for the after proof. The source verifier constrains the production diff to the
three helper bodies. Canonical main/test sources go under core; work-in-progress
Kotlin copies are development drafts excluded from the review commit.

The final adapter contract gate found and corrected structural Map.Entry
equality/hashCode. A before/after MapContractReproducer proves equal maps
previously had unequal hashes/entry sets and failed a real HashSet lookup. The
new separate Kotlin test proves map/entry equality, hashes and HashSet behavior;
it is not counted as another native response or as a Haskell Hashable instance.
MetadataSourceBefore.kt.txt preserves the exact earlier Kotlin adapter source.
The initial shared run34870 passed32,859 cases; the corrective final run adds one
Kotlin contract case. All5,084/55 original expected responses remain unchanged.


## Final frozen shared gate

Corrective Gradle76981 completed BUILD SUCCESSFUL in1m24s. Nine saved XMLs
contain32,860 actual cases with zero failures/errors/skips:5,145 metadata +9,980
Count +5,128 Many +808 Generic +5,715 Walk +52 pure demand +6,000 original
property replays +10 Visitor +22 Builder. The initial34870/32,859 run remains
separate pre-correction evidence. Final native5,084+55, actual Base/Definition/
Builder interfaces and51 pins pass. No additional original rows or expectations
were added after those5,084/55; only the separate JVM contract case was added.

Core JAR/installDist pass:seven major new classes, four byte-exact original
notices, and identical core/installed JAR. Saved XML, exact command/log, native
log, package JSON, immutable final core JAR and frozen owned hashes are under
/tmp/kotdoc-metadata-final-results. This checkpoint is frozen for independent
review; local611 and all remaining gates stay open.
