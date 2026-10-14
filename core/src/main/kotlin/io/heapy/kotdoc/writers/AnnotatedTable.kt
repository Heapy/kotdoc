// Text.Pandoc.Writers.AnnotatedTable, Pandoc 3.12.1.
// Copyright 2020 Christian Despres; GPL-2.0-or-later, licenses/pandoc-GPL-2.0.md.
package io.heapy.kotdoc.writers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Cell as CoreCell
import io.heapy.kotdoc.ast.TableHead as CoreHead
import io.heapy.kotdoc.ast.TableBody as CoreBody
import io.heapy.kotdoc.ast.TableFoot as CoreFoot
import java.util.Collections

/** Intermediate table used by writers that need column, row and stub data.
 * Public constructors deliberately do not enforce a normalized table: upstream
 * also permits inconsistent annotations. Only toTable normalizes the input. */
object AnnotatedTable {
    class ColumnSpecs(val head: ColSpec, tail: List<ColSpec> = emptyList()) {
        val tail: List<ColSpec> = Collections.unmodifiableList(tail.toList())
        fun toList(): List<ColSpec> = listOf(head) + tail
        override fun equals(other: Any?) = other is ColumnSpecs && head == other.head && tail == other.tail
        override fun hashCode() = 31 * head.hashCode() + tail.hashCode()
    }
    data class Cell(val columns: ColumnSpecs, val columnNumber: Long, val cell: CoreCell)
    data class HeaderRow(val attr: Attr, val rowNumber: Long, val cells: List<Cell>)
    data class BodyRow(val attr: Attr, val rowNumber: Long, val head: List<Cell>, val body: List<Cell>)
    data class TableHead(val attr: Attr, val rows: List<HeaderRow>)
    data class TableBody(val attr: Attr, val rowHeadColumns: Long, val head: List<HeaderRow>, val body: List<BodyRow>)
    data class TableFoot(val attr: Attr, val rows: List<HeaderRow>)
    data class Table(val attr: Attr, val caption: Caption, val columns: List<ColSpec>, val head: TableHead, val bodies: List<TableBody>, val foot: TableFoot)

    fun toTable(table: Block.Table): Table = toTable(table.attr, table.caption, table.columns, table.head, table.bodies, table.foot)

    fun toTable(attr: Attr, caption: Caption, columns: List<ColSpec>, head: CoreHead, bodies: List<CoreBody>, foot: CoreFoot): Table {
        val specs = columns.toList()
        val context = Annotation(specs)
        val newHead = TableHead(head.attr, context.headers(head.rows))
        val newBodies = bodies.map { body ->
            val stubWidth = body.rowHeadColumns.coerceIn(0, specs.size.toLong()).toInt()
            val newHeaders = context.headers(body.head)
            TableBody(body.attr, stubWidth.toLong(), newHeaders, context.bodies(stubWidth, body.body))
        }
        val newFoot = TableFoot(foot.attr, context.headers(foot.rows))
        return Table(attr, caption, specs, newHead, newBodies, newFoot)
    }

    fun fromTable(table: Table): Block.Table = Block.Table(
        table.attr, table.caption, table.columns,
        CoreHead(table.head.attr, table.head.rows.map(::fromHeaderRow)),
        table.bodies.map { body -> CoreBody(body.attr, body.rowHeadColumns, body.head.map(::fromHeaderRow), body.body.map { row ->
            Row(row.attr, (row.head + row.body).map { it.cell })
        }) },
        CoreFoot(table.foot.attr, table.foot.rows.map(::fromHeaderRow)),
    )
    private fun fromHeaderRow(row: HeaderRow) = Row(row.attr, row.cells.map { it.cell })

    // Exactly the three source Walkable instances; annotations are not walked.
    fun walk(cell: Cell, transform: (CoreCell) -> CoreCell): Cell = cell.copy(cell = transform(cell.cell))
    fun walk(row: HeaderRow, transform: (CoreCell) -> CoreCell): HeaderRow = row.copy(cells = row.cells.map { walk(it, transform) })
    fun walk(head: TableHead, transform: (CoreCell) -> CoreCell): TableHead = head.copy(rows = head.rows.map { walk(it, transform) })
    fun walk(cell: Cell, visitor: PandocVisitor): Cell = walk(cell, visitor::walkCell)
    fun walk(row: HeaderRow, visitor: PandocVisitor): HeaderRow = walk(row, visitor::walkCell)
    fun walk(head: TableHead, visitor: PandocVisitor): TableHead = walk(head, visitor::walkCell)
    fun <A> query(cell: Cell, query: (CoreCell) -> List<A>): List<A> = query(cell.cell)
    fun <A> query(row: HeaderRow, query: (CoreCell) -> List<A>): List<A> = row.cells.flatMap { query(it, query) }
    fun <A> query(head: TableHead, query: (CoreCell) -> List<A>): List<A> = head.rows.flatMap { query(it, query) }

    private class Hang(val span: Long, val spec: ColSpec)
    private class Placement(val column: Long, val hang: List<Hang>, val cells: List<Cell>)
    private class Annotation(private val columns: List<ColSpec>) {
        private var rowNumber = 0L
        private fun freshHang(specs: List<ColSpec>) = specs.map { Hang(1, it) }
        private fun padded(cells: List<CoreCell>, remainingRows: Int) = CellStream(
            (cells.asSequence().map { it.copy(rowSpan = it.rowSpan.coerceIn(1, remainingRows.toLong())) }
                + generateSequence { Tables.emptyCell }).iterator())

        fun headers(rows: List<Row>): List<HeaderRow> {
            var hang = freshHang(columns)
            return rows.mapIndexed { index, row ->
                val placement = place(0, hang, padded(row.cells, rows.size - index))
                hang = placement.hang
                HeaderRow(row.attr, rowNumber++, placement.cells)
            }
        }
        fun bodies(stubWidth: Int, rows: List<Row>): List<BodyRow> {
            var headHang = freshHang(columns.take(stubWidth))
            var bodyHang = freshHang(columns.drop(stubWidth))
            return rows.mapIndexed { index, row ->
                val cells = padded(row.cells, rows.size - index)
                val head = place(0, headHang, cells)
                val body = place(head.column, bodyHang, cells)
                headHang = head.hang
                bodyHang = body.hang
                BodyRow(row.attr, rowNumber++, head.cells, body.cells)
            }
        }
    }

    /** Iterative annotateRowSection/splitCellHang. The shared iterator retains
     * excess cells for the body after the stub; overhang consumes columns only. */
    private class CellStream(private val input: Iterator<CoreCell>) {
        private var pending: CoreCell? = null
        fun peek(): CoreCell = pending ?: input.next().also { pending = it }
        fun take(): CoreCell = peek().also { pending = null }
    }
    private fun place(start: Long, previous: List<Hang>, cells: CellStream): Placement {
        var at = 0
        var column = start
        val next = mutableListOf<Hang>()
        val placed = mutableListOf<Cell>()
        while (true) {
            val h = previous.getOrNull(at)
            if (h != null && h.span > 1) {
                next.add(Hang(h.span - 1, h.spec))
                column++
                at++
                continue
            }
            // This guard also preserves splitCellHang's exact == 1 condition.
            // The source's second pattern guard examines one cell even when
            // no columns remain. Peek retains that cell for the next section.
            val cell = cells.peek()
            if (h == null || h.span != 1L) break
            cells.take()
            val requested = maxOf(1, cell.colSpan)
            var width = 0
            while (width.toLong() < requested && width < previous.size - at && previous[at + width].span == 1L) width++
            val specs = ArrayList<ColSpec>(width)
            repeat(width) { offset ->
                val spec = previous[at + offset].spec
                specs.add(spec)
                next.add(Hang(cell.rowSpan, spec))
            }
            placed.add(Cell(ColumnSpecs(specs.first(), specs.drop(1)), column, cell.copy(colSpan = width.toLong())))
            at += width
            column += width
        }
        return Placement(column, next, placed)
    }
}
