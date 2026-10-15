The observer links the complete original HTML reader from pinned Pandoc 3.12.1
with GHC 9.12.2 and the frozen Cabal dependencies. Only the same four private
module packaging exposures differ in the prepared library; all 2,888 tracked
Pandoc files are verified before capture. No production reader implementation
is copied into the observer or substituted with Kotlin behavior.

OriginalReaderTests retains all 69 original input/expected expressions and the
original roundTrip, makeRoundTrip and removeRawInlines functions. Only the module
export and framework imports change. ReaderCases reproduces Helpers ToString
and the original Haskell lines comparison; it emits unit observations only
after the original assertion succeeds. Unit ReaderOptions are derived from the
source's named groups and independently checked by running the original reader
again and comparing its entire result. The original fixed reader Native golden
is independently validated with the original Native writer.

The original Round trip predicate is translated, including its normalization
and all three writeHtml5String WrapPreserve/readHtml rewrites. Captures preserve
every rendered HTML string and intermediate AST, not just the final Boolean.
The corpus includes 25 extra reproducible original Arbitrary Blocks samples,
all 266 original documents, and a separate actual QuickCheck run using its
original generator, shrinker, size schedule and withMaxSuccess limit. The
framework's replay seed is explicit; its success and evaluation count are
recorded. This does not claim a complete Kotlin port of QuickCheck or all
remaining Pandoc generators/shrinkers.

All eight original HTML files, including the six root files, and all 173 matching command inputs exercise
the actual full reader with extension modifiers. The final command/writer flags
and application behavior remain separate work. The library also exports the
original Sources htmlTag/htmlInBalanced functionality. The observer records
exact values/errors, remaining source chunks, positions and quote/last-string
state, with direct probes for all public Smart parsers and TagSoup rendering.

For URL effects a small ReaderT/StateT PandocMonad fixture overrides only
openURL and delegates every other operation to PandocPure. The original reader
handles MIME routing, nested frames, the depth bound, errors and report order.
The StateT attempted URL append is rolled back when the callback throws; the
Kotlin test adapter models that fixture behavior. Production URL transport is
injected at the reader boundary; this observer does not replace it with a
Pandoc or calibre production dependency.

Strict iframe UTF-8 follows original UTF8.toText: one BOM and every CR byte are
removed, then text-2.1.2 strict decoding may throw a host UnicodeException.
It is not converted into a PandocMonad typed error. All 65,536 byte pairs,
malformed prefixes and error ordering are observed independently. Incomplete
prefixes report the last buffered byte due to StrictTextBuilder's right-biased
forcing; invalid sequences report their lead byte. Source/typed errors and
host exceptions are observed separately.

Prepare a fresh original source tree and frozen project, then build with
GHC 9.12.2 and Cabal (the directory must be outside the tracked sources):

    python3 tools/haskell-html-reader-reference/prepare.py \
      --directory /tmp/kotdoc-html-reader-original
    cabal build html-reader-reference \
      --project-file=/tmp/kotdoc-html-reader-original/cabal.project
    cabal list-bin html-reader-reference \
      --project-file=/tmp/kotdoc-html-reader-original/cabal.project

Use the executable printed by list-bin to capture:

    python3 tools/import-html-reader-fixtures.py \
      --oracle /path/to/html-reader-reference \
      --prepared /tmp/kotdoc-html-reader-original

Revalidate the corpus without Haskell:

    python3 tools/import-html-reader-fixtures.py --check-source

Recapture preserves all earlier observations. The manifest inherits every
LaTeX Reader source hash and adds this observer, the unchanged original HTML
reader/tests/commands and the Smart, TagSoup and strict Text/UTF8 dependencies.

OriginalSmart.hs is the complete original Smart module. Only its module name
and private General import change; all needed General functions are original
public re-exports, and the non-re-exported isSpaceChar helper is copied verbatim.
The importer verifies the entire transformation and helper. This makes the two
direct quoted parsers observable without rebuilding or altering Pandoc itself.

A separate length-prefixed SHA256 witness checks every original UTF8-encoded
Haskell Char through strict UTF8.toText (1,114,112 observations inside one test),
including surrogate replacement, leading BOM removal and CR removal.
