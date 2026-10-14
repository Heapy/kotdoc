# Original KDE regex reference

The frozen GHC 9.12.2 adapter compiles unchanged Regex.KDE modules from
skylighting-core 0.15 and Data.ByteString.UTF8 from utf8-string 1.0.2 as home
modules. OriginalRegexTests.hs contains the unchanged regexTests and
regexErrorTests declarations extracted from the retained original test suite;
the importer verifies that text. All 186 original literal results and 16 original
compile-error assertions must pass before capture. This does not claim the whole
Skylighting tokenizer/parser/formatter suite has been ported.

The adapter observes full regex Show trees, group extraction, both matching APIs,
matched bytes and capture offsets/lengths/text, explicit ASTs/custom group maps,
and linear full-structure fingerprints for very large compiled trees. Original
Regex.Show uses String append and is quadratic on long right-nested trees; its
exact text remains observed for the ordinary/deep corpus, while the 65,535-repeat
boundary and the largest nesting/flat cases use the complete preorder structure,
strict ByteString UTF-8 decoding and text's separate lenient decoder. Character
and predicate observations enumerate all Haskell Chars; the importer hashes the
complete independent native bytes, and Kotlin recomputes those hashes. Production
Unicode tables are generated from original UnicodeData and GHC definitions, not
from these expectations. Original XML RegExprs are inventoried with their source
paths; the only preprocessing is Parser.hs's removal of a leading ^ (the parser
converts it into a rule-column constraint).

```sh
cabal build --project-dir=tools/haskell-regex-reference regex-reference
PATH_TO_REGEX_REFERENCE --original-tests
python3 tools/import-regex-fixtures.py --oracle PATH_TO_REGEX_REFERENCE
python3 tools/import-regex-fixtures.py --check-source
python3 tools/import-regex-char-data.py --check
```

Show/Read, JSON/Binary/Data instances of Skylighting.Regex.RE and the rest of
utf8-string's String/Generic/stream APIs remain distinct pending work. The Kotlin
matcher intentionally preserves the original 2,000-state pruning and its known
false-negative boundary; it does not substitute Java's regex semantics.

A separate original-skylighting-tests target compiles the full unchanged test
file plus unchanged Core/Types/Parser/Loader/Tokenizer/Styles/Regex modules. Run
its executable from vendor/skylighting-core-0.15 so it can read all original XML,
input and golden files. Its native success is an upstream dependency baseline;
XML loading, syntax maps, tokenization and their Kotlin test ports remain pending.
