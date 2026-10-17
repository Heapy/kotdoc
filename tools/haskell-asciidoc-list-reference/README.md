# AsciiDoc ordinary and explicit source list boundary

This is an additive correction after two rejected bundles. Neither historical
bundle is accepted evidence of source-demand completeness. All previous expected
bytes and source manifests remain unchanged.

`AsciiDocWriter` now uses the same memoized list cursor for every inline list,
with the source two-cell pattern inspected before the inline guards. Section
`go` uses indexed prefix views instead of full extent. Section minimum-level
query is a local iterative translation of the Block query closure: Block/Inline,
Note, citation prefix/suffix, Caption, table head/body/foot, rows/cells and list
containers. Header level is visited before children. This replaces only the
writer's use of the extent-eager generic Visitor, not the shared Visitor itself.

`AsciiDocListSpine` and `AsciiDocListCell` are an explicit caller protocol for
independent cons presence, head and tail values. `List` adapters still use their
public iterator; they do not pretend that `next()` can expose a tail without
computing its head. In particular, the source input
`Para (error "inline-head" : error "inline-tail")` demands inline-tail while
matching two cells. An ordinary iterator whose next evaluates inline-head cannot
represent that independent spine. The explicit protocol executes this case and
matches the oracle. This exception is not applied to reachable ordinary errors:
the supplied PrefixRoot cases and all 18 other ordinary representable cases run
against the same cursor implementation. No marker-only renderer branch remains.

19 independently captured original observations produce 37 Kotlin writer
comparisons: 19 explicit source-list inputs and 18 ordinary list inputs. The
existing 38 controls remain unchanged. A separate JVM adapter test verifies
head/tail sharing and lack of extent reads. Query tests include nested Note,
citation, Figure caption and table cell paths. The adjacent caption-prefix
original intentionally demands caption-tail; that expectation is retained.
Column mapping uses source action-before-next-tail traversal.

Original source: pinned Pandoc Writers.AsciiDoc and Shared.makeSectionsWithOffsets
False/Nothing/empty-offset closure; pandoc-types Walk Block traversal. Pandoc
code is GPL-2.0-or-later, copyright Pandoc authors. Walk traversal is BSD-3-Clause;
existing repository notices retained. Oracles are development-only.

Shared 612 resource/demand gaps and wider 621 App errors/options remain open;
27 dependent command routes and 4 Old --quiet routes remain unimplemented.


## Confirmed generic Visitor gap and local comma closure

`comma-pre-fix-repro.json` binds four additional reachable ordinary/protocol Link
failures to the preserved 1709-test artifact. Generic Visitor collection mapping
measures extent before the first mapped child; original `walk (concatMap
fixCommas)` does not. The sole remaining writer Visitor call is replaced by
AsciiDocCommaWalk, a private lazy rebuild of all Inline/Block, citation, caption,
table and row/cell children. Shared Visitor remains unchanged and its defect must
be retained under 611/612; local acceptance cannot close that shared task.

Five additive writer observations execute in both boundary modes. A direct
pinned Walk oracle independently captures exact JSON AST transformations for all
34 Inline/Block constructors, including nested citation/caption/table children.
Inputs are selected from retained branch/original/golden/extra/command AST inputs,
then every Str payload is changed to `,a,,b,`; expected ASTs come solely from the
original Walk expression. Empty split fields and postorder traversal are retained.
Thus new JUnit contains 47 writer observations, 34 exact AST comparisons and one
JVM sharing check (82 registrations). Original 38 family controls remain separate.

The successful 1709-test intermediate artifact, source snapshot and XML are
retained separately from the corrected final artifact. It was never accepted and
did not run final CLI after the additional source gate found its known failures.
