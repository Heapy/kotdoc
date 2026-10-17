// Port of pandoc-types Text.Pandoc.Builder table algorithms.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

object Tables {
    val emptyCell = Cell(Attr(), Alignment.AlignDefault, 1, 1, emptyList())
    val emptyCaption = Caption(null, emptyList())

    fun normalizeHead(width: Int, head: TableHead): TableHead =
        PandocTables.normalizeTableHead(width.toLong(), head)

    fun normalizeFoot(width: Int, foot: TableFoot): TableFoot =
        PandocTables.normalizeTableFoot(width.toLong(), foot)

    fun normalizeBody(width: Int, body: TableBody): TableBody =
        PandocTables.normalizeTableBody(width.toLong(), body)

    fun normalize(table: Block.Table): Block.Table =
        TableBuilders.tableWith({table.attr}, {table.caption}, {table.columns},
            {table.head}, {table.bodies}, {table.foot}).toSourceList()[0] as Block.Table

    fun simpleTable(headers: List<List<Block>>, rows: List<List<List<Block>>>): Block.Table =
        TableBuilders.simpleTable(
            {TableStreams.mapped({headers}) { Many.fromList(it()) }},
            {TableStreams.mapped({rows}) { row -> TableStreams.mapped(row) { Many.fromList(it()) } }}
        ).toSourceList()[0] as Block.Table

    /** Row spans are bounded by the remaining height of this section, never the whole table. */
    fun clipRows(rows: List<Row>): List<Row> = PandocTables.clipRows { rows }

    data class Placement(val overhang: List<Long>, val cells: List<Cell>)

    /**
     * Consume cells only when they fit; leave remaining iterator elements for the next section.
     * Iterative translation of placeRowSection avoids a stack frame per table column.
     */
    fun placeRowSection(previous: List<Long>, cells: Iterator<Cell>): Placement {
        val next = mutableListOf<Long>()
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
            if (hang != 1L || !cells.hasNext()) break
            val cell = cells.next()
            val requestedWidth = maxOf(1, cell.colSpan)
            var actualWidth = 0
            while (actualWidth < requestedWidth && column + actualWidth < previous.size && previous[column + actualWidth] == 1L) {
                actualWidth++
            }
            placed += cell.copy(colSpan = actualWidth.toLong())
            repeat(actualWidth) { next += cell.rowSpan }
            column += actualWidth
        }
        return Placement(next, placed)
    }

    private fun normalizeSection(width: Int, rows: List<Row>): List<Row> {
        var hang = List(maxOf(0, width)) { 1L }
        return clipRows(rows).map { row ->
            val placed = placeRowSection(hang, padded(row.cells))
            hang = placed.overhang
            row.copy(cells = placed.cells)
        }
    }

    private fun padded(cells: List<Cell>): Iterator<Cell> =
        (cells.asSequence() + generateSequence { emptyCell }).iterator()
}
