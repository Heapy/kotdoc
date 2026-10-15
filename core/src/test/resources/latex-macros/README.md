All expectations are observations of the complete pinned original Macro and
Parsing modules under GHC 9.12.2, not outputs of the Kotlin port.

The observer changes Macro's module/import names and exposes two private helpers.
A LINE pragma preserves the canonical source filename and line numbers in GHC's
pattern-match errors. The generator checks these exact packaging transformations,
the retained complete Parsing observer, and every tracked prepared Pandoc file.

The corpus exercises macroDef, all definition commands, argument specification
parsing, insertion/lookup, copying, scopes, reporting, extension-disabled raw
definitions, and expansion through the original token grammar. It includes all
127 matching command inputs from 44 files and all 90 original reader unit inputs.
Each is exercised with latex_macros enabled and disabled. Their complete reader
AST and CLI assertions remain pending; this driver observes the definition/token
component and does not stand in for the full LaTeX reader.

There are also malformed definitions, every xparse specifier/modifier/processor,
integer wrapping, exact remaining input/positions/state, raw and failure wrappers,
every Haskell Char through letterize, and 100,000-level global scope/definition
stream witnesses. Deep results include complete output hashes and final state.

Numerical expansion uses the existing token kernel, whose adjacent native
numeric corpora preserve platform-dependent libm results. This corpus introduces
no numeric tolerance. It is additionally executed on Linux with JDK 21.
