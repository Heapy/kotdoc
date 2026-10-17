# Complete pinned RST writer oracle

This development-only oracle compiles the complete997-line original
`Text.Pandoc.Writers.RST` from Pandoc e51c9c6054c8f4ec5c3209d5abe10939dfe2963e
with pandoc-types b2cbcefc4547296f47b8120740997a9c021d756f. Production conversion
is Kotlin; no subprocess/native writer is used by it. Original writer and Shared
helpers are Copyright(C)2006–2024 John MacFarlane, GNU GPL2 or above. The original
notices remain in the copied module and retained upstream fixtures. Classifier
ranges derive from GHC9.12.2 Data.Char/Unicode16; retain the existing GHC BSD and
Unicode3.0 licenses in licenses/.

`prepare.py` checks exact original module/test hashes. The complete module only
changes module name, the public PandocMonad import, and URI module import.
SourceUriPosix is the existing complete original URI module with its module name
changed; the read-only checker verifies this exact transform. Original178-line
RST tests retain every input and expected expression. Only test-tree/helper
imports and writer-function tags change. Three exported flatten units capture
original inputs, actual results and literal expected ASTs independently; the
other19 writer units retain original literal text and raw module output. T.lines
comparison follows Tests.Helpers; raw module output is also compared exactly.

`build-cached.py` uses the cached pinned full Pandoc library/package database and
GHC9.12.2; it does not fetch/build a replacement dependency graph. The adapter
imports the copied original module, not the Kotlin implementation. Its custom
JSON options transport includes every relevant RST option, template source and
variables. Reports come from the original PandocPure ordered log. Full shared
source dependencies include Writers.Shared, Shared, ImageSize, URI, Options,
Extensions, Logging, Error, Class.IO/PandocMonad, App/OutputSettings/Input and
CommandLineOptions; pandoc-types Definition/Builder/Walk. The manifest pins the
source modules and all Kotlin helpers actually used by the translated writer.

Persistent accounting:22original leaves +2215 source-branch observations +318
extra helper/list/state probes +83Unicode/raw-case/label-state boundaries +15
caption Block branches +1all-lowercase-mapping raw-format case +3all27difference identifier profiles +3display-math stopping cases +12deep cases +4nonfinite/overflow list widths +4Old
source goldens +43command writer-AST observations =2723 observations. Two full
1114112-codepoint assertions (classifier and sourceTextlowercase) check all four relevant Data.Char predicates:
isAlphaNum,isSpace,and both punctuation-category sets. Thus2725JUnit checks,
not1114112additional JUnit cases. Narrow Unicode ranges1953 avoid host-JDK
category drift. All source/fixture expectations originate in the original code.
`requests.py` contains deterministic inputs only. `--capture` refuses overwrite.

Four Old cases follow Tests.Old generic expansion exactly: testsuite.native to
writer.rst (-s,columns80,pandoc-version empty), tables.native to tables.rst,
and lhs-test.native to lhs-test.rst/lhs-test.rst+lhs (wrap preserve,setext flag).
All four original golden files remain verbatim. Library golden outputs match
those bytes; full App observations separately bind the real command options.

Commands are extracted with the original Markdown reader/code-block selection
and Tests.Command continuation/lines/unlines/%/^D/dot rules. All43full original
App commands match their source expectations. Eighteen native,two LaTeX,and one
DokuWiki command can run through currently registered Kotlin readers (21total).
Twenty-two Markdown/GFM/Org/HTML/man-dependent full commands remain pending; their
original reader-produced ASTs exercise this writer but do not close full-command
coverage. DokuWiki8182 is executed as a complete command. Source3974 has no final
newline in the module body: the App.ensureNl step supplies the command newline;
that application boundary must not be confused with fixture fence extraction.

Heap continuations render blocks/inlines/tables and caption conversion. Deep
coverage includes4096Emph/Span/Note/Div/Quote/simple-caption/grid-caption,
512bullet/ordered,256definition,and128nestedtable/mixed. RST always evaluates
its legacy caption before table selection (unlike Muse grid's ignored caption).
The owned complete Shared.blockToInlines/blocksToInlines translation is used
locally; shared DocumentShared recursion/eager-caption gap612 remains for other
consumers. Source table evaluation order is preserved, including simple-render
then grid fallback and duplicate caption rendering for list tables.

Application observations bind optionalBoolean spelling/case/errors6 for new
--list-tables/--reference-links, default/custom templates and compilation error5,
standalone heading normalization, inference/output aliases, raw INFO suppression,
and Doku input warning/binary/trace controls. Class.PandocMonad.report records
all library reports but emits only ERROR/WARNING at the default WARNING level;
RstCliWriter respects this. Generic --verbose/--quiet remain task621 and are not
invented as RST-only flags. General -M and generic template error flows outside
Muse/RST also remain task621. RSTreader,22reader-dependent commands,remaining
text formats,all global suites/full618/Pandoc/calibre remain open.

Typical read-only verification after installation:

```
python3 tools/import-rst-writer-fixtures.py --check-source
python3 tools/import-rst-writer-fixtures.py --verify-oracle --oracle /tmp/kotdoc-rst-writer/oracle
python3 tools/check-rst-writer-cli.py --cli /tmp/kotdoc-rst-writer/installed-cli/bin/cli
```

Final installed App checks run through a durable wrapper that atomically writes
status/terminal JSON and separate stdout/stderr, with exact commands/PIDs/start
and exit codes/input/checker/jar hashes retained outside the repository. Historical
Muse/Doku manifests remain immutable; new integration provenance links accepted
shared CLI blobs to current owned changes instead of rewriting old pins.

The original Pandoc runtime selects text-2.1.2-b3a1, with1433lower mappings;
CoreUnicodeText intentionally ports a separate text-2.1.3/1460mapping profile.
The raw-format all-mapping probe exposed this distinction before core install.
RST reuses unchanged LaTeXReaderText.toLower and uniqueIdent: the complete
Shared helper already has the correct text-2.1.2/GHC Unicode16 dictionaries.
The native oracle enumerates every Haskell Char, and its1433mapping results
match the vendored text-2.1.2 CaseMapping source exactly. runtime-profile.json
pins the full Cabal plan hash, selected package DB entries, exact old source
hash and original CLI binary. No shared case table was changed. The existing
Doku consumer mismatch is separately confirmed in
 doku-identifier-profile-repro.json and must be fixed after this checkpoint;
it is not hidden by changing old expected data or the RST command accounting.


Author source audit caught and fixed the source stop-recursion detail in
removeSpaceAfterDisplayMath: only the FIRST display-math element drops its
following Space elements; later display math retains the original tail. Three
separate original-module/App fixtures cover two/three display math, first with
no following spaces, InlineMath, SoftBreak and WrapPreserve. Old payloads are
unchanged. Before-fix originalCLI93+21 and Muse204+6Doku+4172 completed in a
durable copied distribution; final corrected artifact requires writer2725 and
96App+21commands. The earlier full10971 regression/XML proof remains valid for
unchanged Muse/Doku sources; relevant unchanged bytecode is bound separately.
