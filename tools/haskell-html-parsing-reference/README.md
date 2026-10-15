# Original HTML token parsing observer

The observer calls all exports of `Text.Pandoc.Readers.HTML.Parsing` and all
TagCategories values from the complete original Pandoc library. `prepare.py`
creates a separate source copy and changes **only** the Cabal exposure of
Parsing, Types and TagCategories. It verifies every original tracked file:
implementation, tests, data and notices are byte-exact. No module is extracted,
replaced, or reimplemented in the observer. The original checkout is untouched.
The library-only -O0 setting retains the previously documented compiler resource
setting; the observer and dependencies retain -O2.

Build with GHC 9.12.2 and the repository's pinned Cabal environment:

```
python3 tools/haskell-html-parsing-reference/prepare.py --directory /private/tmp/kotdoc-html-parsing-original
cabal build html-parsing-reference --project-file=/private/tmp/kotdoc-html-parsing-original/cabal.project --builddir=/private/tmp/kotdoc-html-parsing-build
```

Freeze dependencies, retain that freeze and repeat the build before capturing.
The complete original HTML test module runs with all registrations and its
original helper assertions; a helper adapter separately serializes the original
inputs/expectations. Its copied original helper differs only in the module name.
Those are native reader-level tests. Kotlin tests in this stage compare the
shared tag-parser boundary on their inputs, not the complete reader AST.
