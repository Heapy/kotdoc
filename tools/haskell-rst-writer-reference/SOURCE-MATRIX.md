# RST writer source coverage (full library and narrow CLI checkpoint)

Pinned Pandoc e51c9c6054c8f4ec5c3209d5abe10939dfe2963e; pandoc-types b2cbcefc4547296f47b8120740997a9c021d756f. Writer997 lines, original Tests.Writers.RST178 lines /22 leaves (19 writer and3 exported flatten). Original module oracle executes all22; literal expected expressions retained. Development oracle only, production Kotlin.

| Source | Contract | Independent evidence |
|---|---|---|
|45–116| state/init; title before metadata before body; standalone heading normalization; notes then refs then pics; template defaults/render/wrap | metadata side effects/options/default/custom templates |
|118–180| references use normalized labels without repeated transforms; notes snapshot; image classes/dimensions/targets | collision/state/order/notes/images |
|182–243| codepoint escaping predicates/smart punctuation | all1114112GHC predicates +all1114112text2.1.2 lower points +83boundaries |
|245–485| every14 Block constructors; top vs nested rubric; Div admonitions; code/literate; Figure; table/simple-grid fallback/list table | original19 +Old4 +2215expanded +318extra +12deep |
|487–547| ligature fixBlocks comment separators, top-level restoration, directive layout | original ligature + command10236 + empty/header/list cases |
|549–586| list tables, widths roundInt, header presence | finite/nonfinite widths, spans/state |
|588–696| flatten/style priority/export whitespace/empty filter/display math/backslash insertion | original3flatten +all144containerpairs/contextneighbors +83boundaries |
|698–766| flatten/setInlineChildren/dropInlineParent | every Inline constructor + deep containers |
|768–911| normalize once bottom-up then render; all Inline paths; notes/refs/images collision | original19 +2215branches +318extra +deep/state/imagecollisions |
|913–935| dimensions/image naming | dimensions and duplicate/collision refs |
|937–997| simple table head/body order, first-cell empties, colspans | table shapes/options/fallback/state |

Old4: basic testsuite.native→writer.rst (-s,columns80,variablepandoc-version=), tables.native→tables.rst, lhs-test.native→lhs-test.rst and lhs-test.rst+lhs (wrap preserve,setext flag, latter literate_haskell). Command inventory uses original Tests.Command extraction:43writer blocks,21fullcommands available(18native/2LaTeX/1Doku),22missing-reader dependencies. All43writer ASTobservations andfulloriginalApp commands matchliteral sourceexpectations. DokuWiki→RST8182 full command is in scope. Full618/RSTreader/other formats remain open.


Runtime profile: Data.Text2.1.2-b3a1, exact1433lower mappings from vendored
CaseMapping SHA f939679161321c8961445c3060132bfc59c911db2c95a7b6fa12e66ccdbda9ad.
Reuse unchanged LaTeXReaderText.toLower/uniqueIdent after checking Shared531–574,
671–683; source GHC16 ParserCharacters/RegexCharacters, emoji/asciify and Builder
helpers remain pinned. CoreUnicodeText's separate2.1.3 profile is intentionally
unchanged. The retained extra raw-format fixture exercises all1460earlier-table
mapped points and the three identifier profiles exercise all27version deltas.
Fullmapping oracle enumerates1114112Chars and matches1433vendor entries exactly.

PersistentJUnit2725 =22original+2215expanded+318extra+83boundaries+15captions+
1rawformatmapping+3identifierprofiles+3displaymathstopping+12deep+4width+4Old+43commandAST+
1GHCclassifierdomain+1Textlowerdomain. Both full-domain rows are single JUnit
checks. Beforeinstall bounded2722PASS at512MiB/256KiB; narrow isolatedCLI93App+
21completecommands/outputregistryPASS. Final Gradle/installed proof must be
retained separately; no acceptance claim until their durable terminal results.

Default RST reporting records all ordered library logs but suppresses INFO on
console at WARNING. No --verbose/--quiet invented; generic621 gap retained.
New --list-tables/--reference-links use exact original optionalBoolean parsing,
case behavior and typederror6. RST malformedtemplates use original error5.
Main changes only RSTwriter registry/dispatch/inference; RSTreader is absent.
Historical shared CLI blobs/current owned diff linkage is integration.json.

Confirmed independent Doku consumer-profile defect is preserved in
 doku-identifier-profile-repro.json for a separate612 followup; no accepted
Doku/Muse implementation or expected bytes changed in this checkpoint.


Author source audit caught and fixed the source stop-recursion detail in
removeSpaceAfterDisplayMath: only the FIRST display-math element drops its
following Space elements; later display math retains the original tail. Three
separate original-module/App fixtures cover two/three display math, first with
no following spaces, InlineMath, SoftBreak and WrapPreserve. Old payloads are
unchanged. Before-fix originalCLI93+21 and Muse204+6Doku+4172 completed in a
durable copied distribution; final corrected artifact requires writer2725 and
96App+21commands. The earlier full10971 regression/XML proof remains valid for
unchanged Muse/Doku sources; relevant unchanged bytecode is bound separately.
