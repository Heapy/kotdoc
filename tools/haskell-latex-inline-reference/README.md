Build latex-inline-reference with this Cabal project/freeze, GHC 9.12.2 and the
complete prepared Pandoc 3.12.1 library. OriginalInline.hs retains the complete
original Inline module. Only the module/Parsing import, two private exports and
PandocMonad's public Class re-export import change. The SIunitx observer copy
changes only its module name; other dependencies compile from protected folders.

The generator verifies the immutable Hackage archives and every vendored
source/test/license byte for Parsec, unicode-transforms and unicode-data. It
checks all packaging transformations, hashes original implementations/tests and
the complete observer dependency tree, and validates all 2,888 prepared Pandoc
files. Only pandoc.cabal differs, exposing the same four documented modules.
Original sources, test files and full license notices are preserved.

The driver supplies explicit component callbacks for literal/group/formatting,
macro/citation/language/SIunitx and inline-command parsing. This is not the full
readLaTeX grammar; original full-reader AST/CLI/report assertions remain required.
Ordinary observations record complete ASTs, all parser state, raw capture,
position, token remainder, errors and common-state/log/cache values.

Name commands use the complete original translation/Data/PandocPure machinery,
including built-in translation assets and user replacements in PureState.stFiles.
No PandocMonad or translation implementation is substituted. Kotlin tests use
the existing Data/translation layer with matching injected file effects.

Cases cover all eight registries, literal symbols, accents and callback shapes,
Roman values/errors/wrapping, verbatim/lstinline/mintinline delimiters/options,
references/raw_tex, acronyms and translated names. All original inline lists
exercise accents and BibLaTeX wrapping. The original normalization corpus's five
NFC assertions per row are retained alongside original-library verdicts. Unicode
digest witnesses compare every Haskell Char with every distinct accent mark;
deep finite cases compare complete original AST Show hashes and final state.

Capture: tools/import-latex-inline-fixtures.py --oracle BINARY --prepared DIR
  --parsec-archive PARSEC_TAR_GZ --unicode-cache HACKAGE_PACKAGE_CACHE
where DIR contains pandoc/. Revalidate with --check-source. The Unicode package
cache contains unicode-transforms/0.4.0.1/ and unicode-data/0.9.0/ archives.

The NFC dependency is implemented in Kotlin using the pinned normalization
algorithm and unicode-data 0.9.0 tables. import-unicode-nfc.py verifies all
nonzero combining classes, combining/starter predicates and decomposable
characters over every Haskell Char against the original compiled functions,
plus every source composition pair. Table extraction retains immediate
canonical decompositions; Hangul remains algorithmic. Production does not use
JDK/ICU NFC: its Unicode version assigns different classes to some marks.

Supplemental independent original observations exercise every pinned canonical
decomposition, combining class and composition with ordering/blocking probes,
all 11,172 Hangul syllables, all 38,025 jamo-range pairs including gaps and
U+11A7, and 1,024 deterministic mixed sequences. The initial 121,106 captured
observations must remain identical when this supplemental corpus is captured.
The 90 original command inputs come from 49 files; 268 matching command files
are source-hashed, including files with non-LaTeX commands/output occurrences.
