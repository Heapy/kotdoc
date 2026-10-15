This observer links the complete, original Text.Pandoc.Readers.LaTeX module
from pinned Pandoc 3.12.1, using GHC 9.12.2 and the frozen Cabal project.
The prepared library changes only four private-module packaging exposures;
its implementation files are unchanged. Capture verifies all 2,888 tracked
Pandoc files against this preparation and every vendored time-1.14 file
against the SHA256-pinned original release archive.

OriginalReaderTests.hs retains all 90 original test inputs and expected
expressions. Only the module name and test-framework imports change.
ReaderCases reproduces the original Helpers ToString/line comparison and
emits input, expected native text and full actual AST only after the original
assertion passes. Neither observer nor importer obtains expectations from
Kotlin. Both original fixed reader goldens are also checked with the original
Native writer before capture.

The read operation invokes actual readLaTeX in PandocPure, preserving complete
AST or typed errors, ordered common-state reports, resource paths and language
cache. File bytes and environment variables are supplied to the original Pure
runtime. The bridge operation invokes the three original public Sources parser
entry points, retaining result, error, position, remaining chunks and macros.
The existing applyMacros bridge is independently tested in the kernel corpus.

All 362 matching original command inputs from 145 files exercise the full
reader. Their final command output and unrelated application/writer flags are
not asserted by these reader observations; those original command tests remain
in scope. Installed CLI checks separately cover all 90 unit expectations and
both fixed reader goldens, plus named input, include paths, dates, image defaults
and invalid UTF-8. An end-to-end graphicspath check also preserves the exact
original PNG bytes through HTML output and ZIP media extraction. The multi-line image expectation was independently verified
with this observer's original readLaTeX and Native writer.

Additional observations cover every environment, extension boundaries, Unicode
case transforms, text-2.1.2 header identifiers, filecontents/TEXINPUTS, includes,
strict UTF-8/BOM/CR behavior, listings, footnote reads, raw Sources APIs and
Shared.tabFilter. Case-data enumerates all Haskell Chars with the original
text-2.1.2 upper/lower/title operations. Calendar observations preserve arbitrary
Integer dates, leap seconds, Int64 timezone arithmetic and four complete
146,097-day Gregorian cycles. This is the date path used by LaTeX's today;
it does not claim a complete port of the time package or all its tests.

Build with the checked-in frozen project, pointing its prepared Pandoc package
at the verified original library as for preceding LaTeX observers. Capture:

    python3 tools/import-latex-reader-fixtures.py \
      --oracle /path/to/latex-reader-reference \
      --prepared /path/to/prepared \
      --time-archive /path/to/time-1.14.tar.gz

Revalidate without a Haskell runtime:

    python3 tools/import-latex-reader-fixtures.py --check-source
    python3 tools/import-text-212-case.py --check

Recapture preserves every preceding captured observation. Source manifests
inherit the Table corpus's full dependency hashes and include all new observer,
importer, CLI-check, original command, text case and time source files.
