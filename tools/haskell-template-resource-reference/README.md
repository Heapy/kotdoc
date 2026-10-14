# Pandoc template-resource path oracle

`template-paths-reference` compiles unchanged retained FilePath 1.5.4.0 modules.
CanonicalPosix.hs and CanonicalWindows.hs contain the unchanged makeCanonical
definition from the pinned Pandoc Shared.hs, with the splitDirectories import
selected for each platform. The importer checks that definition byte for byte.
The adapter observes takeFileName, isRelative, combine, addExtension and makeCanonical; no
Kotlin algorithm participates in expected-result generation.

Build with the pinned GHC 9.12.2 and frozen Cabal closure, then capture:

```sh
cabal build --project-dir=tools/haskell-template-resource-reference template-paths-reference
python3 tools/import-template-resource-fixtures.py --pandoc PATH_TO_OFFICIAL_PANDOC --paths-oracle PATH_TO_EXECUTABLE
python3 tools/import-template-resource-fixtures.py --check-source
python3 tools/import-template-data.py --check
```

Other template resource expectations call the official original Pandoc 3.12.1
macOS ARM64 release through its Lua API (archive/executable hashes are retained
in PANDOC-REFERENCE.json). The installed Homebrew build lacks HTTP support; the
official release successfully fetches loopback HTTP resources. The original getTemplate/getDefaultTemplate and template
compiler/renderer perform all loading and conversion. Input is fetched from a
loopback HTTP document, so every relative template operation also checks source
URL suppression. Local files, media entries and the user-data overlay are built
from the retained fixture setup. HTTP failure cases preserve failure kind, not
platform-specific exception text. File paths and server origin in diagnostics
are normalized. Every built-in template is retained and packaged verbatim.

Lua exposes compileTemplate via two partial resolvers, but does not expose
compileDefaultTemplate directly. Default compilation is observed by loading the
original default and compiling with its writer path; its resource tree contains
no shadow for those partial paths. Additional Kotlin effect tests check that
compileDefaultTemplate always uses the default-only resolver, preserves the
original alias name in the compiler path, and raises TemplateCompilationError.
The Kotlin general Pandoc data API, reference archive construction and default
user-data directory discovery are still separate pending work.

Before capture, all 16 unchanged original Lua template tests run through a
minimal tasty assertion/runner adapter (template-original-tests.lua). The
adapter supplies only equality, falsiness and error-match assertions and test
group execution. It contains no loader, compiler or renderer. Binding/module
type assertions are native baselines; Kotlin covers the corresponding typed
operations and captured behavior without claiming a Lua API port.
