# Pinned Org writer reference

Scope: complete735-line Text.Pandoc.Writers.Org, all8 original writer tests,
2 Tests.Old goldens, writer-used Citeproc.Locator closure from the334-line module,
and5 currently dependency-ready complete commands. This is not the Org reader,
the CSL Locale API, or completion of618/Pandoc/calibre.

The original module is copied verbatim except its module/export name and three
import routes (public Pandoc.Class, source-pinned POSIX URI adapter, source locator
closure). Locator retains all parseLocator helpers and its53-alias writer map;
only unused toLocatorMap/CSL Locale dependency is removed. Util.splitStrWhen is
copied exactly. prepare.py checks these transformations independently. Original
8 test literals and both Old fixtures are unchanged; all other expectations are
captured solely by original Haskell or original full App. No Kotlin-generated
expected outputs are used. GPL notices remain in translated files/original copies.
The1557 text2.1.2 casefold mappings carry the existing text BSD-2-Clause notice.

Development runtime is GHC9.12.2, exact cached Pandoc3.12.1-inplace with
text-2.1.2-b3a1. runtime-profile.json pins the Cabal plan, compiled CLI, vendored
case mapping and relevant package entries; provenance.json pins source/tools,
actual used URI helper, payloads, package DB/plan and oracle binaries. build-cached.py
reuses that library with no network or upstream rebuild. Production is Kotlin.

## Verification

```
python3 tools/haskell-org-writer-reference/prepare.py --check-source
python3 tools/haskell-org-writer-reference/prepare.py --verify-oracle
JAVA_HOME=/Users/yoda/.sdkman/candidates/java/21.0.12-librca ./gradlew --no-daemon --no-build-cache --project-cache-dir /tmp/kotdoc-org-gradle -Dorg.gradle.vfs.watch=false -Pkotlin.compiler.execution.strategy=in-process :core:test --tests io.heapy.kotdoc.writers.OrgWriterTest
```

Persistent suite:5345 test bodies =8 original +755 branches +66 tables/figures
+2 Old +337 remaining branches +11 deep +3 Int64 boundaries +10 display-width tags +1297 locator
+2854 direct helpers +2 exhaustive domain tests. Domain iterations are not counted
as JUnit leaves. The standalone runner executes the same test bodies with512MiB
heap/256KiB stack. Captures refuse overwriting existing expectations.

CLI fixtures retain52 full App comparisons plus3 additive Int64 and10 display-width tag boundary cases,
including the5 complete source commands. Tests.Command extraction and App.ensureNl
explain command fence/application newlines; source goldens are never adjusted to
Kotlin output. The other22 Org-output commands require absent Org/Markdown/HTML/
IPYNB CLI reader routes and/or citeproc. AST/library observations do not discharge
those commands. Existing RST HTTP/general IO and global621 CLI gaps stay open.

Final CLI proof must use an immutable copied distribution, for example:

```
python3 tools/haskell-org-writer-reference/run-cli-proof.py --cli /tmp/kotdoc-org-writer/installed-cli/bin/cli --fixtures tools/haskell-org-writer-reference/fixtures/cli-observations.jsonl.gz --output-dir /tmp/kotdoc-org-writer/final-cli-proof --freeze /tmp/kotdoc-org-writer/frozen-paths.json --regressions
```

The wrapper writes child PID/start/command, actual jar/checker/input hashes,
stdout/stderr, per-child terminal code and atomic terminal.json independent of
transient tool handles. Acceptance requires terminal results. Existing Muse204
App +6 Doku controls +2 commands4172 and RST96 App +21 full commands are relevant
shared CLI regressions; they preserve their accepted fixtures/historical manifests.
Historical and current Main/options bindings are linked separately in integration.json.
