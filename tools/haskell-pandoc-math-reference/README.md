# Original Pandoc math bridge oracle

Compiles complete unchanged Text.Pandoc.Writers.Math and complete unchanged
texmath 0.13.3.1. PandocMonad/Shared/Citeproc are byte-identical copies of the
protected earlier state reference's exact source projections, not replacement
algorithms. Options contains only the byte-exact two URL definitions re-exported
by this module. The importer verifies these projections against retained sources.

The native state instance observes output, typed/host failures and re-entrant
callbacks. It does not implement report or math conversion. Generic pure writers
are instrumented with a NOINLINE demand observer outside the unchanged original
module, with CSE/full-laziness disabled only for the adapter. Repeated forcing
checks sharing. Expectations include AST JSON/Show, all state fields, chronological
logs, output events and writer demand. No Kotlin code participates in capture.
