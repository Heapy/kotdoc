// Text.Pandoc.Readers.LaTeX.Table, Copyright (C) John MacFarlane.
// GPL-2.0-or-later; licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.latex

import io.heapy.kotdoc.ast.*

/** Source overhang lists, with runs representing lazy replicate/repeat Nothing.
 * This preserves spans larger than Int.MAX_VALUE without allocating per column. */
object LaTeXTableRows {
    data class Hang(val columns: Long, val rows: Long)
    private data class Run(val hang: Hang?, val empty: Long = 0, val infinite: Boolean = false)
    private class Link(val run: Run, val next: Link?)
    private fun toHang(c: Long, r: Long): Run? = when {
        r > 1 -> Run(Hang(c, r))
        c > 0 -> Run(null, c)
        else -> null
    }
    private fun append(prefix: List<Run>, tail: Link?): Link? {
        var result = tail
        for (i in prefix.indices.reversed()) result = Link(prefix[i], result)
        return result
    }
    private fun drop(hang: Link?, requested: Long): Link? {
        var rest = hang; var n = requested
        while (n > 0 && rest != null) {
            val run = rest.run
            if (run.hang != null) { n -= run.hang.columns; rest = rest.next }
            else if (run.infinite) return rest
            else if (run.empty > n) return Link(run.copy(empty = run.empty - n), rest.next)
            else { n -= run.empty; rest = rest.next }
        }
        return rest
    }
    private fun fix(hang: Link?, cells: List<Cell>): Pair<Link?, List<Cell>> {
        var old = hang; var index = 0
        val output = ArrayList<Cell>(); val prefix = ArrayList<Run>()
        while (true) {
            var rest = old; var n = 0L
            val occupied = ArrayList<Run>()
            while (rest?.run?.hang != null) {
                val h = rest.run.hang
                n += h.columns
                toHang(h.columns, h.rows - 1)?.let(occupied::add)
                rest = rest.next
            }
            if (n > 0) {
                while (n > 0 && index < cells.size) n -= cells[index++].colSpan
                prefix.addAll(occupied); old = rest
            } else if (index < cells.size) {
                val c = cells[index++]; val h = maxOf(1, c.rowSpan); val w = maxOf(1, c.colSpan)
                old = drop(old, w); toHang(w, h)?.let(prefix::add); output.add(c)
            } else return append(prefix, old) to output
        }
    }
    fun fixRows(rows: List<Row>): List<Row> {
        var hang: Link? = Link(Run(null, infinite = true), null)
        return rows.map { row ->
            val (next, cells) = fix(hang, row.cells); hang = next
            row.copy(cells = cells)
        }
    }
    fun fixRow(hang: List<Hang?>, cells: List<Cell>): Pair<List<Hang?>, List<Cell>> = fixRowPrefix(hang, cells, Long.MAX_VALUE)
    fun fixRowPrefix(hang: List<Hang?>, cells: List<Cell>, limit: Long): Pair<List<Hang?>, List<Cell>> {
        val (next, fixed) = fix(append(hang.map { if (it == null) Run(null, 1) else Run(it) }, null), cells)
        val output = ArrayList<Hang?>(); var node = next
        while (node != null && output.size.toLong() < limit) {
            val run = node.run
            if (run.hang != null) output.add(run.hang) else {
                var n = minOf(run.empty, limit - output.size)
                while (n-- > 0) output.add(null)
            }
            node = node.next
        }
        return output to fixed
    }
}
