// Port of pandoc-types Text.Pandoc.Builder table algorithms.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

object Tables {
    val emptyCell = Cell(Attr(), Alignment.AlignDefault, 1, 1, emptyList())
    val emptyCaption = Caption(null, emptyList())

    fun normalizeHead(width: Int, head: TableHead): TableHead =
        head.copy(rows = normalizeSection(width, head.rows))

    fun normalizeFoot(width: Int, foot: TableFoot): TableFoot =
        foot.copy(rows = normalizeSection(width, foot.rows))

    fun normalizeBody(width: Int, body: TableBody): TableBody {
        val headWidth = maxOf(0, minOf(width, body.rowHeadColumns))
        var headHang = List(headWidth) { 1 }
        var bodyHang = List(maxOf(0, width - headWidth)) { 1 }
        val rows = clipRows(body.body).map { row ->
            val cells = padded(row.cells)
            val head = placeRowSection(headHang, cells)
            val rest = placeRowSection(bodyHang, cells)
            headHang = head.overhang
            bodyHang = rest.overhang
            row.copy(cells = head.cells + rest.cells)
        }
        return body.copy(rowHeadColumns = headWidth, head = normalizeSection(width, body.head), body = rows)
    }

    fun normalize(table: Block.Table): Block.Table {
        val width = table.columns.size
        return table.copy(head = normalizeHead(width, table.head),
            bodies = table.bodies.map { normalizeBody(width, it) }, foot = normalizeFoot(width, table.foot))
    }

    fun simpleTable(headers: List<List<Block>>, rows: List<List<List<Block>>>): Block.Table {
        val width = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
        fun row(cells: List<List<Block>>) = Row(Attr(), cells.map { emptyCell.copy(blocks = it) })
        return normalize(Block.Table(Attr(), emptyCaption,
            List(width) { ColSpec(Alignment.AlignDefault, ColWidth.Default) },
            TableHead(Attr(), if (headers.isEmpty()) emptyList() else listOf(row(headers))),
            listOf(TableBody(Attr(), 0, emptyList(), rows.map(::row))), TableFoot(Attr(), emptyList())))
    }

    /** Row spans are bounded by the remaining height of this section, never the whole table. */
    fun clipRows(rows: List<Row>): List<Row> = rows.mapIndexed { index, row ->
        row.copy(cells = row.cells.map { cell -> cell.copy(rowSpan = cell.rowSpan.coerceIn(1, rows.size - index)) })
    }

    data class Placement(val overhang: List<Int>, val cells: List<Cell>)

    /**
     * Consume cells only when they fit; leave remaining iterator elements for the next section.
     * Iterative translation of placeRowSection avoids a stack frame per table column.
     */
    fun placeRowSection(previous: List<Int>, cells: Iterator<Cell>): Placement {
        val next = mutableListOf<Int>()
        val placed = mutableListOf<Cell>()
        var column = 0
        while (column < previous.size) {
            val hang = previous[column]
            if (hang > 1) {
                next += hang - 1
                column++
                continue
            }
            // Upstream stops on invalid nonpositive overhang, without consuming a cell.
            if (hang != 1 || !cells.hasNext()) break
            val cell = cells.next()
            val requestedWidth = maxOf(1, cell.colSpan)
            var actualWidth = 0
            while (actualWidth < requestedWidth && column + actualWidth < previous.size && previous[column + actualWidth] == 1) {
                actualWidth++
            }
            placed += cell.copy(colSpan = actualWidth)
            repeat(actualWidth) { next += cell.rowSpan }
            column += actualWidth
        }
        return Placement(next, placed)
    }

    private fun normalizeSection(width: Int, rows: List<Row>): List<Row> {
        var hang = List(maxOf(0, width)) { 1 }
        return clipRows(rows).map { row ->
            val placed = placeRowSection(hang, padded(row.cells))
            hang = placed.overhang
            row.copy(cells = placed.cells)
        }
    }

    private fun padded(cells: List<Cell>): Iterator<Cell> =
        (cells.asSequence() + generateSequence { emptyCell }).iterator()
}
