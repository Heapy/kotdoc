This small oracle retains all pure numerical declarations from the pinned
LaTeX.Parsing module, and the exact safeRead/safeStrRead declarations from Shared.
`import-latex-numeric-fixtures.py` checks the extraction byte-for-byte. No numerical
behavior is implemented in this observer. The full LaTeX module remains the main
oracle in the adjacent haskell-latex-kernel-reference project.

Use GHC 9.12.2 and text 2.1.2. Compile NumericReference.hs with `-O2 -rtsopts
-with-rtsopts=-K256m -package text`, retaining build outputs outside this directory.
Prepare requests with:

    python3 tools/import-latex-numeric-fixtures.py --platform linux-x86_64 --archive /tmp/latex-numeric-input.tar

For Linux, build the included Dockerfile using `docker build -t kotdoc-latex-ghc-noble - < tools/haskell-latex-numeric-reference/Dockerfile`.
It combines the pinned official GHC image with pinned Ubuntu 24.04. Run an ephemeral
container, unpack the request archive into /tmp/reference, compile the two .hs
files with the flags above, and run `./numeric < requests`. Capture stdout in a
JSONL file. Compiler diagnostics go to stderr. The observed environment was
GHC 9.12.2, text 2.1.2, glibc 2.39-0ubuntu8.9 on x86_64.

On macOS/aarch64, run the same compiled observer over the same requests. Import
each stdout file with `--platform PLATFORM --results FILE`. macOS imports must
match every numeric result of the complete original module. Both source checks
also run in CI. The exact numeric requests are independent of the Kotlin code.
