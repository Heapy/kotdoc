// Text.Pandoc.Writers.Shared, Pandoc 3.12.1.
// Copyright (C) 2013-2024 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*

/** Shared table helpers. Span maps retain even nonpositive preexisting entries,
 * as in the source; these functions do not normalize tables or spans. */
object WriterTables {
    data class Span(val rowSpan: Long, val colSpan: Long)
    fun allRowsEmpty(rows: List<Row>) = rows.all { row -> row.cells.all { it.blocks.isEmpty() } }
    fun tableBodiesToRows(bodies: List<TableBody>) = bodies.flatMap { it.head + it.body }
    fun insertCurrentSpansAtColumn(column: Long, previous: Map<Long, Span>, rowSpan: Long, colSpan: Long): Map<Long, Span> =
        if (rowSpan > 1) previous + (column to Span(rowSpan - 1, colSpan)) else previous
    fun takePreviousSpansAtColumn(column: Long, previous: Map<Long, Span>): Pair<Long, Map<Long, Span>>? {
        val span = previous[column] ?: return null
        if (span.rowSpan < 1) return null
        return span.colSpan to if (span.rowSpan > 1) previous + (column to span.copy(rowSpan = span.rowSpan - 1)) else previous - column
    }
    fun decrementTrailingRowSpans(column: Long, previous: Map<Long, Span>): Map<Long, Span> = buildMap {
        for ((at, span) in previous) {
            if (at >= column && span.rowSpan >= 1) { if (span.rowSpan > 1) put(at, span.copy(rowSpan = span.rowSpan - 1)) }
            else put(at, span)
        }
    }
}
