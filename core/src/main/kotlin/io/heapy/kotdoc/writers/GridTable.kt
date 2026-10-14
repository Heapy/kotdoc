// Text.Pandoc.Writers.Shared, Pandoc 3.12.1.
// Copyright (C) 2013-2024 John MacFarlane; GPL-2.0-or-later.
// floorDoubleInt: Copyright (c) Daniel Fischer 2010; GHC BSD-3-Clause.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.options.WrapOption
import io.heapy.kotdoc.options.WriterOptions

/** The shared grid-table writer. Its callback receives normalized cell blocks
 * and the original options, in source order: head, foot, then each body. */
object GridTable {
    private val layout = DocLayout.Text

    fun gridTable(options: WriterOptions, blocksToDoc: (WriterOptions, List<Block>) -> Doc<String>,
                  columns: List<ColSpec>, head: TableHead, bodies: List<TableBody>, foot: TableFoot): Doc<String> {
        val table = AnnotatedTable.toTable(Attr(), Tables.emptyCaption, columns, head, bodies, foot)
        fun renderRows(rows: List<List<AnnotatedTable.Cell>>) = addDummies(rows.map { gridRow(options, blocksToDoc, it) })
        val headCells = renderRows(table.head.rows.map { it.cells })
        val footCells = renderRows(table.foot.rows.map { it.cells })
        val bodyCells = table.bodies.flatMap { body -> renderRows(body.head.map { it.cells } + body.body.map { it.head + it.body }) }
        val rows = setTopBorder(LineStyle.SingleLine, setBottomBorder(LineStyle.DoubleHeaderLine, headCells)) +
            setTopBorder(if (headCells.isEmpty()) LineStyle.SingleHeaderLine else LineStyle.SingleLine,
                setBottomBorder(LineStyle.SingleLine, bodyCells)) +
            setTopBorder(LineStyle.DoubleLine, setBottomBorder(LineStyle.DoubleLine, footCells))
        return gridRows(redoWidths(options, table.columns, rows))
    }

    fun gridTable(options: WriterOptions, blocksToDoc: (WriterOptions, List<Block>) -> Doc<String>, table: Block.Table) =
        gridTable(options, blocksToDoc, table.columns, table.head, table.bodies, table.foot)

    // Internal types/functions retain the source pipeline for independent oracle
    // observations. They are not alternative public table representations.
    internal enum class LineStyle { NoLine, SingleLine, DoubleLine, SingleHeaderLine, DoubleHeaderLine }
    internal data class ColWidthInfo(val specified: Long, val full: Long, val min: Long, val used: Long)
    internal data class RenderedCell<A>(val column: Long, val colSpan: Long, val columns: AnnotatedTable.ColumnSpecs,
        val align: Alignment, val rowSpan: Long, val width: Long, val contents: Doc<A>,
        val bottom: LineStyle, val top: LineStyle)

    internal fun getColWidth(spec: ColSpec) = when (val w = spec.width) { is ColWidth.Width -> w.value; ColWidth.Default -> 0.0 }

    internal fun toCharWidth(options: WriterOptions, width: Double): Long {
        val scaled = width * options.columns.toDouble()
        // GHC's optimized floor :: Double -> Int truncates, compares, then
        // decrements with Int64 wrap. JVM conversion matches the captured ARM64
        // double2Int# baseline, including NaN and saturation outside Int64.
        val truncated = scaled.toLong()
        val floored = if (scaled < truncated.toDouble()) truncated - 1 else truncated
        return maxOf(0, floored - 3)
    }

    internal fun <A> gridRow(options: WriterOptions, blocksToDoc: (WriterOptions, List<Block>) -> Doc<A>,
                             cells: List<AnnotatedTable.Cell>): List<RenderedCell<A>> = cells.map { c ->
        val specs = c.columns.toList()
        // Preserve source summation order across the covered column specs.
        val width = toCharWidth(options, specs.fold(0.0) { a, s -> a + getColWidth(s) })
        val rendered = blocksToDoc(options, c.cell.blocks)
        RenderedCell(c.columnNumber, specs.size.toLong(), c.columns, specs.first().alignment,
            c.cell.rowSpan, width, rendered, if (c.cell.rowSpan < 2) LineStyle.SingleLine else LineStyle.NoLine, LineStyle.SingleLine)
    }

    internal fun extractColWidths(options: WriterOptions, rows: List<List<RenderedCell<String>>>): List<ColWidthInfo> {
        fun widths(measure: (RenderedCell<String>) -> Long): List<Long> {
            val result = mutableListOf<Long>()
            for (row in rows) {
                var column = 0
                for (cell in row) {
                    if (cell.colSpan <= 0) continue // replicate does not force its value here.
                    val offset = measure(cell)
                    val width = Math.floorDiv(offset, cell.colSpan) + offset % cell.colSpan
                    repeat(Math.toIntExact(cell.colSpan)) {
                        if (column == result.size) result.add(width) else result[column] = maxOf(result[column], width)
                        column++
                    }
                }
            }
            return result
        }
        val full = widths { maxOf(1, layout.offset(it.contents)) }
        val minimum = if (options.wrapText == WrapOption.WrapNone) full else widths { layout.minOffset(it.contents) }
        val specified = widths { it.width }
        return specified.indices.map { i -> ColWidthInfo(specified[i], full[i], minimum[i],
            if (specified[i] == 0L) 0 else maxOf(specified[i], minimum[i])) }
    }

    internal fun resetWidths(widths: List<Long>, cells: List<RenderedCell<String>>): List<RenderedCell<String>> {
        var at = 0
        return cells.map { c ->
            if (at >= widths.size) c else {
                val w = widths[at++]
                when {
                    c.colSpan == 1L -> c.copy(width = w)
                    c.colSpan < 1 -> c
                    else -> {
                        val n = c.colSpan - 1
                        val end = at + minOf(n, (widths.size - at).toLong()).toInt()
                        val updated = c.copy(width = w + widths.subList(at, end).sum() + 3 * n)
                        at = end
                        updated
                    }
                }
            }
        }
    }

    internal fun recalculateWidths(available: Long, initial: List<ColWidthInfo>): List<ColWidthInfo> {
        var infos = initial
        var finalRun = false
        var runs = 1
        while (true) {
            val unassigned = infos.count { it.used == 0L }.toLong()
            val unused = available - infos.sumOf { it.used }
            val defaultWidth = if (unassigned == 0L) 0 else Math.floorDiv(unused, unassigned)
            val updated = infos.map { info ->
                if (info.used != 0L) info
                else if (finalRun) info.copy(used = maxOf(info.min, defaultWidth))
                else if (info.full <= defaultWidth) info.copy(used = info.full)
                else info
            }
            if (finalRun || runs > 4) return updated
            if (infos == updated) finalRun = true else infos = updated
            runs++
        }
    }

    internal fun redoWidths(options: WriterOptions, columns: List<ColSpec>, rows: List<List<RenderedCell<String>>>): List<List<RenderedCell<String>>> {
        if (rows.isEmpty()) return rows
        val widths = recalculateWidths(options.columns - 3 * columns.size.toLong() - 1, extractColWidths(options, rows)).map { it.used }
        return rows.map { resetWidths(widths, it) }
    }

    internal fun makeDummy(c: RenderedCell<String>) = c.copy(align = Alignment.AlignDefault, rowSpan = c.rowSpan - 1,
        contents = Doc.Empty, bottom = LineStyle.NoLine, top = LineStyle.NoLine)

    internal fun addDummies(rows: List<List<RenderedCell<String>>>): List<List<RenderedCell<String>>> {
        val result = mutableListOf<List<RenderedCell<String>>>()
        for (current in rows) {
            val previous = result.lastOrNull()
            if (previous == null) { result.add(current); continue }
            val cells = mutableListOf<RenderedCell<String>>()
            var p = 0
            var c = 0
            while (p < previous.size && c < current.size) {
                val old = previous[p]
                val new = current[c]
                if (old.column < new.column) { cells.add(makeDummy(old)); p++ }
                else {
                    cells.add(new); c++
                    while (p < previous.size && previous[p].column < new.column + new.colSpan) p++
                }
            }
            while (p < previous.size) cells.add(makeDummy(previous[p++]))
            while (c < current.size) cells.add(current[c++])
            result.add(cells)
        }
        return result
    }

    internal fun setTopBorder(style: LineStyle, rows: List<List<RenderedCell<String>>>) = rows.mapIndexed { i, row ->
        if (i == 0) row.map { it.copy(top = style) } else row
    }
    internal fun setBottomBorder(style: LineStyle, rows: List<List<RenderedCell<String>>>) = rows.mapIndexed { i, row ->
        if (i == rows.lastIndex) row.map { it.copy(bottom = style) } else row
    }
    internal fun isHeaderStyle(style: LineStyle) = style == LineStyle.SingleHeaderLine || style == LineStyle.DoubleHeaderLine

    internal fun combineBorders(first: String, second: String): String {
        if (first.isEmpty()) return second
        val a = TextChars.points(first)
        val b = TextChars.points(second)
        return TextChars.fromPoints(IntArray(minOf(a.size, b.size)) { i ->
            val x = a[i]; val y = b[i]
            when {
                x == 43 || y == 43 -> 43
                x == 58 || y == 58 -> 58
                (x == 124 && (y == 45 || y == 61)) || (y == 124 && (x == 45 || x == 61)) -> 43
                x == 61 || y == 61 -> 61
                x == 32 -> y
                else -> x
            }
        })
    }

    internal fun <A> formatBorder(style: (RenderedCell<A>) -> LineStyle, alignMarkers: Boolean, cells: List<RenderedCell<A>>): Doc<String> {
        var previous = LineStyle.NoLine
        var parts: Doc<String> = Doc.Empty
        for (cell in cells) {
            val current = style(cell)
            val junction = if (current == LineStyle.NoLine && previous == LineStyle.NoLine) '|' else '+'
            val line = when (current) { LineStyle.NoLine -> ' '; LineStyle.SingleLine, LineStyle.SingleHeaderLine -> '-'; else -> '=' }
            val left = if (alignMarkers && (cell.align == Alignment.AlignLeft || cell.align == Alignment.AlignCenter)) ':' else line
            val right = if (alignMarkers && (cell.align == Alignment.AlignRight || cell.align == Alignment.AlignCenter)) ':' else line
            val section = layout.text(left + line.toString().repeat(Math.toIntExact(maxOf(0, cell.width))) + right)
            parts += layout.char(junction.code) + section
            previous = current
        }
        return parts + layout.char(if (previous == LineStyle.NoLine) '|'.code else '+'.code)
    }

    internal fun formatHeaderLine(style: LineStyle, rows: List<List<RenderedCell<String>>>): Doc<String> =
        layout.literal(rows.fold("") { text, row -> combineBorders(text, layout.render(null, formatBorder({ style }, true, row))) })

    internal fun gridRows(rows: List<List<RenderedCell<String>>>): Doc<String> {
        if (rows.isEmpty()) return Doc.Empty
        fun border(row: List<RenderedCell<String>>, top: Boolean): Doc<String> {
            val cell = row.firstOrNull() ?: return Doc.Empty
            val style = if (top) cell.top else cell.bottom
            return if (isHeaderStyle(style)) formatHeaderLine(style, rows) else formatBorder({ if (top) it.top else it.bottom }, false, row)
        }
        val renderedRows = rows.mapIndexed { i, row ->
            val docs = row.map { layout.lblock(it.width, it.contents) }
            val interspersed = docs.flatMapIndexed { index, d -> if (index == 0) listOf(d) else listOf(layout.vfill(" | "), d) }
            val contents = layout.vfill("| ") + (layout.hcat(interspersed) + layout.vfill(" |"))
            val next = rows.getOrElse(i + 1) { emptyList() }
            val below = border(row, false)
            val combined = if (next.isEmpty()) below else layout.literal(combineBorders(layout.render(null, below), layout.render(null, border(next, true))))
            layout.above(contents, combined)
        }
        return layout.above(border(rows.first(), true), layout.vcat(renderedRows))
    }
}
