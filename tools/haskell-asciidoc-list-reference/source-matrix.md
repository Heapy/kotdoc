# Additive source-demand matrix

| Source closure | Kotlin | Independent observations |
|---|---|---|
| Writers.AsciiDoc `blockListToAsciiDoc`, mapM | Work.Blocks iterator cursor; asciiDocMap; listItem cursor | block-root, quote-prefix, div-prefix, old bullet/ordered/figure controls |
| Writers.AsciiDoc `inlineListToAsciiDoc` go two-cell/guard/fallback | common renderInlines prefix cursor for every List | inline-prefix, plain-prefix, second-head/space-second, explicit tail-before-head, all old Emph/alt controls |
| Shared.makeSectionsWithOffsets [] False Nothing go | indexed prefix views, bounded break, source-shaped merge guard | block-root, div-prefix, old section guard, all 281+281 finite section cases |
| Shared minLevel `query getLevel` + pandoc-types Walk Block | AsciiDocMinLevel explicit continuation and source traversal order | header root, Note, citation, Figure caption, table cell; retained exhaustive finite/deep writer corpus |
| Writers.AsciiDoc table `map fst colspecs`, width map | asciiDocMap over input columns | table-column-prefix ordinary and source protocol |
| List matching vs actual full-spine operation | unchanged caption flattening flow | figure-caption-prefix original caption-tail is preserved, not changed to head |
| List API bridge ownership/sharing | AsciiDocListSpine/Cell and shared State/offset views | 19 original source observations, 18 ordinary representable counterparts; separate JVM sharing contract |

The old 38 native/Kotlin family controls are unchanged. The 19 new native
observations are 15 root/query and four adjacent cases. Thus 75 additive writer
comparisons run in Kotlin (38 + 19 + 18), plus one JVM sharing test. Original
12, four Old library goldens and all retained synthetic finite/deep/profile
observations remain distinct evidence. Native-only deferred String and eager Doc
bottom controls remain native-only. No arbitrary Kotlin List equivalence to all
Haskell partial values is claimed: the explicit protocol supplies independent
heads/tails where Iterator.next cannot. All ordinary representable failures are
executed through the common algorithm, not a renderer branch selected by type.


| Additional exact closure | Kotlin | Original proof |
|---|---|---|
| Writers.AsciiDoc Link `walk (concatMap fixCommas)` | AsciiDocCommaWalk lazy child rebuild, source concatMap split/intersperse | 5 writer cases in both modes, 34 constructor-complete JSON AST expectations |

No PandocVisitor calls remain in the own Writer/Sections/CommaWalk/MinLevel
closure. The general Visitor extent defect is retained in comma-pre-fix-repro.json.
All remaining used generic helpers are unchanged; this correction does not claim
arbitrary Haskell partial String/AST-field representation in strict Kotlin fields.
