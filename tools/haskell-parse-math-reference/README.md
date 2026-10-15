# Pandoc mathematical delimiter parser oracle

Links the complete unchanged pinned Pandoc and pandoc-types libraries; production
Math, General, Sources and Capabilities modules are never extracted or replaced.
The original HTML reader test module runs unchanged with all registrations,
including all sixty-nine ordinary assertions and the original QuickCheck property.
The test helper adapter executes the original assertion and separately captures
its input/expectation; OriginalHelpers differs only in its module declaration.

The observer serializes Parsec consumed/empty replies, values, remaining streams,
source positions, complete error messages and retained caller state. It exercises
all five original UpdateSourcePos stream instances and custom HasReaderOptions
getters. Build with GHC 9.12.2, freeze dependencies and repeat the build. The
library-only -O0 setting reuses the documented full Pandoc build configuration;
the observer and other dependencies retain -O2.

The complete original Markdown reader test module is also compiled unchanged.
Its math-groups subtree is selected by the original Tasty test path; original
reader-level assertions remain native evidence, while Kotlin directly checks
the shared delimiter-parser boundary on their original inputs and math spans.
