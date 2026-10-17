# Org writer source coverage

Pinned Pandoc e51c9c6054c8f4ec5c3209d5abe10939dfe2963e; pandoc-types
b2cbcefc4547296f47b8120740997a9c021d756f. Delivery requires all gates below and independent review. This is not completion of task 618.

| Source function / branch | Kotlin draft | Independent evidence / remaining |
| --- | --- | --- |
| Org.hs 42–100 state/writeOrg/pandocToOrg | Session/document | Original8, metadata/options/template observations; default-template CLI captured independently |
| notesToOrg/noteToOrg | batch queue with source note numbering | nested notes and metadata/body ordering; deep4096 notes exact |
| replaceSpecialStrings/escapeString | special/escape, afterBreak ZWSP | all five substitutions, specials at line/wrap boundaries, all wrap modes |
| isRawFormat | exact text2.1.2 casefold | raw formats/case/ordered logs; full casefold scalar proof |
| blockToOrg every Block constructor | renderBlock | original8 + expanded755 + tables66 + both Old; deep11 bounded exact |
| ipynb cell/code/execute_result | Div special branches | dedicated cell/output cases |
| refs stripping | Div refs + citations | enabled/disabled dedicated cases |
| LineBlock stanza split | renderBlock | empty/leading/trailing/consecutive stanza cases |
| Header tag query/properties | tags/properties | recursive query order incl Span/Note/Cite prefix/suffix; display-width padding via accepted DocLayout realLength,10 Unicode/combining/ANSI cases exact |
| CodeBlock flags/language/escaping | language/CodeBlock | examples, jupyter, startFrom/continuedSourceBlock, comma/indent escapes |
| Table/toLegacyTable | legacy conversion + heap caption | 64 table cases, two figure cases, Old tables exact; caption shape/deep11 bounded exact |
| Bullet/Ordered/DefinitionList | item/task/tight/endsWithPlain | all delimiter/start/style/tight/task branches; maxBound zip truncation retained |
| Figure | heap caption + anchor/body | caption/no-caption/anchor; all14 caption constructors exact |
| propertiesDrawer/attrHtml | properties/attrHtml | classes/id/kvs/default/null cases |
| divBlockType/isAdmonition/divToOrg | div/greater/admonition | duplicate drawer, greater class partition, case-sensitive title suppression |
| inlineListToOrg/fixMarkers | fix/renderInlines | 288 notes/digits/bullet wrap boundaries exact |
| inlineToOrg every Inline constructor | renderInline | nested144, all delimiters, quote/smart/raw/math/note/cite/link paths |
| citations parseLocator/locmap | OrgCitationLocator | 1297 original helper observations incl 53 aliases, 1557 casefold mappings and full scalar proof |
| escapeLinkTarget/Description/orgPath | escapeTarget/Description/path | all path classes, bracket/backslash runs, autolinks; private helper/deep11 bounded exact |
| pandocLangToOrg | language | c/commonlisp/r/bash + identity tested |
| Locator.hs 1–334 used closure | OrgCitationLocator | complete parseLocator/CSL serialization/braces/roman/digits/split/comma; unused CSL Locale/toLocatorMap outside scope |
| Citeproc.Util.splitStrWhen | split | native helper corpus; all1114112 GHC predicate values exact |

## Original inventory and delivery boundary

* Tests.Writers.Org: all 8 original leaves captured from the exact transformed
  module and actually executed by Kotlin, with unchanged literal expectations.
* Tests.Old writerTests org: basic standalone testsuite.native → writer.org and
  tables.native → tables.org: both Kotlin byte-exact with original fixtures.
* 27 extracted Org-output command blocks. Five full commands have currently
  available readers (RST/native); 22 require missing Org/Markdown/HTML/IPYNB CLI
  readers and/or citeproc. AST writer observations are not full command execution.
* Locator test/oracle adapter exposes only writer-used parseLocator closure and
  private helpers. It does not claim the complete CSL Locale API is ported.
* All production conversion is Kotlin. Original Haskell is development evidence.
* Existing accepted RST/Muse/Doku and their historical manifests stay unchanged.

## Resource and Int domains

Eleven programmatic original deep cases include Emph/Span/Div/Note/caption depth4096,
quote/bullet/ordered/definition/table128 and mixed64. Kotlin test bodies run at
512MiB heap/256KiB stack. This is finite feasible input evidence, not arbitrary
Haskell bottom equivalence. Header negative Int64 levels include minBound and
values that become positive under an incorrect JVM Int32 narrowing. Source
replicate for all negative levels is empty; no narrowing occurs before the guard.

The exact text2.1.2 profile is independently pinned:1557 casefold mappings and
1433 accepted lower mappings (reused LaTeXReaderText). Both new casefold and four
GHC predicates are compared across1114112 code points; each domain check is one
JUnit test, not1114112 JUnit leaves. Existing CoreUnicodeText2.1.3 is untouched.
