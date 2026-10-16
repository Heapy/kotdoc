# Original ordered shrinking and demand closure

The executable invokes the pinned original Arbitrary instances unchanged. It
retains 134 named finite cases: every20 Inline/14 Block constructors, every
wrapper flatten/no-flatten branch, six table/caption records, both optional
caption shapes, all eight Attr empty/nonempty component combinations, list and
Text lengths0/1/2/3/4/7/8/9, deep32/128/256 Inline/Block prefixes and a large
Cartesian Attr prefix. Original complete candidate order and duplicates remain
unchanged. Bounded cases carry limit/truncated metadata; they are not represented
as complete lists.

Mode `chars` executes shrink(Str(Text.singleton(chr n))) for every Haskell Char
codepoint0..0x10ffff, including surrogate replacement by actual Text.pack. Each
1024-input batch outputs the complete ordered candidate codepoint arrays. The
importer retains their SHA256 (JSON arrays of decimal integers, no whitespace),
plus the entire native raw-stream SHA256. Kotlin reconstructs all1114112 complete
candidate lists and checks all1088 batch hashes. Expectations come exclusively
from actual original code, not Kotlin character predicates or JVM Unicode data.

Mode `demand` catches actual native evaluation results for six named finite-list
probes: two100000-element removals, a retained element error, full enumeration
on a small9-element list, a100000-element DefinitionList first candidate, and a
Citation no-op element list. Kotlin AbstractList models a known finite spine with
partial elements. Infinite/partial spines and bottom-valued AST fields are not
representable by the strict Kotlin AST; these observations do not claim a full
GHC evaluation runtime. A native `last` of100000-element removals exhausted the
GHC stack during exploration; the retained complete-enumeration probe is the
explicit finite9-element source case, and large cases retain bounded demand.

```sh
cabal build exe:types-shrink-closure-reference --project-dir=tools/haskell-types-shrink-closure-reference --builddir=/tmp/kotdoc-types-shrink-closure-build
python3 tools/import-types-shrink-closure-fixtures.py --oracle PATH
python3 tools/import-types-shrink-closure-fixtures.py --check-source
```

Production conversion remains Kotlin. Original BSD notices/licenses and upstream
source EOF bytes remain untouched; this standalone oracle is development-only.
