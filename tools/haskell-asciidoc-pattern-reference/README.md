# AsciiDoc source-pattern correction

Additive correction to the rejected original AsciiDoc checkpoint. The original
115-path bundle and d9ddd6eb core JAR remain a failed baseline, not accepted evidence.
Original expectations in the writer-reference directory are unchanged.

The 30 patterns and eight adjacent controls are independently evaluated by
PatternOracle.hs and PatternExtraOracle.hs against the unchanged pinned
SourceAsciiDocWriter module. They cover constructor-before-tail matching for
Note, Emph/Strong, figure Plain/Image, image-alt equality, table cells, list task
conversion, section merge guards and class-list comparisons. StrictData body
controls remain distinct from deferred list-head/tail controls.

The Kotlin prefix carrier replays observations made by a source pattern. It does
not claim that every arbitrary Kotlin List models every Haskell bottom. Ordinary
finite AST behavior and all 72 retained deep cases are checked separately.
The section correction preserves the source `all` guard: visit each next head
before demanding the subsequent tail. Existing shared DocumentShared and
DocLayout resource/demand gaps remain open under local:612.

historical-source-bindings.json resolves three old manifest pins to exact,
retained source snapshots. It never substitutes a historical hash for a current
implementation check; the new current-source manifest binds the replacement
files separately. No old fixture or source manifest is rewritten.

All source-derived code/controls retain Pandoc authors' copyright and
GPL-2.0-or-later licensing. Development oracles are never runtime dependencies.
