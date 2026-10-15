These expectations are observations of pinned Pandoc 3.12.1, never Kotlin outputs.

`data.jsonl.gz` runs the complete original LaTeX.Parsing module under GHC 9.12.2
on macOS/aarch64. Only its module name, one public reexport import and additional
exports for observing private helpers differ. The generator checks the exact
transformation and all 2,888 tracked files of the prepared original library.

`numeric-macos-aarch64.jsonl.gz` and `numeric-linux-x86_64.jsonl.gz` run the same
numeric requests through byte-for-byte extracted pure declarations from
LaTeX.Parsing and Shared. The macOS extraction agrees with every numeric result
from the complete original module. Linux uses GHC 9.12.2, text 2.1.2 and Ubuntu
24.04/glibc 2.39 on x86_64. Its 89 libm-dependent results differ from macOS; all
other observations agree. Tests select exact native expectations, without numeric
tolerances. The source checks verify request parity and every permitted difference.

The extraction is test tooling only. Production conversion uses Kotlin and the
host C math library through the already existing JNA dependency.

This corpus does not replace the pending complete LaTeX reader, macro-definition
grammar, unit AST assertions, or command fixtures. It observes their parsing and
macro-expansion dependencies directly. See docs/PORTING.md for remaining scope.
