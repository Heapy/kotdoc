// Kotlin translation of pandoc-types/test/test-pandoc-types.hs table examples/properties.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.*

class TablesTest {
    private fun cell(id: String, height: Int, width: Int) = Cell(Attr(id), Alignment.AlignDefault, height, width, emptyList())
    private fun rows(vararg cells: List<Cell>) = cells.map { Row(Attr(), it) }

    @Test
    fun `upstream table sanitisation`() {
        fun content(text: String): List<Block> = listOf(Block.Plain(listOf(Inline.Str(text))))
        val result = Tables.simpleTable(listOf(content("foo"), content("bar")), listOf(listOf(emptyList()), emptyList()))
        val emptyRow = Row(Attr(), List(2) { Tables.emptyCell })
        val expected = Block.Table(Attr(), Caption(null, emptyList()),
            List(2) { ColSpec(Alignment.AlignDefault, ColWidth.Default) },
            TableHead(Attr(), listOf(Row(Attr(), listOf(Tables.emptyCell.copy(blocks = content("foo")), Tables.emptyCell.copy(blocks = content("bar")))))),
            listOf(TableBody(Attr(), 0, emptyList(), listOf(emptyRow, emptyRow))), TableFoot(Attr(), emptyList()))
        assertEquals(expected, result)
    }

    @Test
    fun `upstream table normalization example`() {
        val initialHeads = rows(listOf(cell("a", 1, 1), cell("b", 3, 2)), listOf(cell("c", 2, 2), cell("d", 1, 1)))
        val finalHeads = rows(listOf(cell("a", 1, 1), cell("b", 2, 2)), listOf(cell("c", 1, 1)))
        val initialBody = TableBody(Attr(), 1,
            rows(emptyList(), listOf(cell("g", -7, 0), cell("h", 4, 1))),
            rows(listOf(cell("e", 4, 3), cell("f", 4, 3)), emptyList(), listOf(Tables.emptyCell)))
        val finalBody = TableBody(Attr(), 1,
            rows(List(3) { Tables.emptyCell }, listOf(cell("g", 1, 1), cell("h", 1, 1), Tables.emptyCell)),
            rows(listOf(cell("e", 3, 1), cell("f", 3, 2)), emptyList(), emptyList()))
        val columns = List(3) { ColSpec(Alignment.AlignDefault, ColWidth.Default) }
        val initial = Block.Table(Attr(), Tables.emptyCaption, columns, TableHead(Attr(), initialHeads), listOf(initialBody), TableFoot(Attr(), initialHeads))
        val expected = Block.Table(Attr(), Tables.emptyCaption, columns, TableHead(Attr(), finalHeads), listOf(finalBody), TableFoot(Attr(), finalHeads))
        assertEquals(expected, Tables.normalize(initial))
    }

    @Test
    fun `upstream head normalization is idempotent`() = samples { width, rows, _ ->
        val normalized = Tables.normalizeHead(width, TableHead(Attr("head"), rows))
        assertEquals(normalized, Tables.normalizeHead(width, normalized))
    }

    @Test
    fun `upstream body normalization is idempotent`() = samples { width, rows, body ->
        val normalized = Tables.normalizeBody(width, body.copy(head = rows))
        assertEquals(normalized, Tables.normalizeBody(width, normalized))
    }

    @Test
    fun `upstream foot normalization is idempotent`() = samples { width, rows, _ ->
        val normalized = Tables.normalizeFoot(width, TableFoot(Attr("foot"), rows))
        assertEquals(normalized, Tables.normalizeFoot(width, normalized))
    }

    @Test
    fun `upstream head normalization preserves cell subset`() = samples { width, rows, _ ->
        assertRowsSubset(Tables.normalizeHead(width, TableHead(Attr(), rows)).rows, rows)
    }

    @Test
    fun `upstream foot normalization preserves cell subset`() = samples { width, rows, _ ->
        assertRowsSubset(Tables.normalizeFoot(width, TableFoot(Attr(), rows)).rows, rows)
    }

    @Test
    fun `upstream body normalization preserves cell subset`() = samples { width, _, body ->
        val normalized = Tables.normalizeBody(width, body)
        assertRowsSubset(normalized.head, body.head)
        if (body.body.isNotEmpty()) {
            val headWidth = normalized.rowHeadColumns
            val (newHead, newBody) = splitAtWidth(normalized.body.first().cells, headWidth)
            val (oldHead, oldBody) = splitAtWidth(body.body.first().cells, headWidth)
            assertEquals(headWidth, newHead.sumOf { it.colSpan })
            assertEquals(width, headWidth + newBody.sumOf { it.colSpan })
            assertCellsSubset(newHead, oldHead)
            assertCellsSubset(newBody, oldBody)
        }
    }

    @Test
    fun `upstream head first row has correct width`() = samples { width, rows, _ ->
        assertFirstWidth(width, Tables.normalizeHead(width, TableHead(Attr(), rows)).rows)
    }

    @Test
    fun `upstream foot first row has correct width`() = samples { width, rows, _ ->
        assertFirstWidth(width, Tables.normalizeFoot(width, TableFoot(Attr(), rows)).rows)
    }

    @Test
    fun `upstream body first rows have correct width`() = samples { width, _, body ->
        val normalized = Tables.normalizeBody(width, body)
        assertFirstWidth(width, normalized.head)
        assertFirstWidth(width, normalized.body)
    }

    @Test
    fun `every normalized grid slot is occupied exactly once`() {
        val random = Random(924)
        repeat(500) {
            val width = random.nextInt(0, 17)
            val rows = randomRows(random, malformed = true)
            val body = Tables.normalizeBody(width, TableBody(Attr(), random.nextInt(-10, 25), rows, rows))
            assertGrid(width, Tables.normalizeHead(width, TableHead(Attr(), rows)).rows)
            assertGrid(width, body.head)
            assertGrid(width, body.body)
            assertGrid(width, Tables.normalizeFoot(width, TableFoot(Attr(), rows)).rows)
        }
    }

    @Test
    fun `placement leaves overflow cells for next section and handles occupied columns`() {
        val cells = listOf(cell("a", 2, 99), cell("b", 1, 1), cell("c", 1, 1)).iterator()
        assertEquals(Tables.Placement(listOf(2, 2, 2), listOf(cell("a", 2, 2))), Tables.placeRowSection(listOf(1, 1, 3), cells))
        assertEquals(Tables.Placement(listOf(1), listOf(cell("b", 1, 1))), Tables.placeRowSection(listOf(1), cells))
        assertEquals(cell("c", 1, 1), cells.next())
    }

    @Test
    fun `zero column tables and empty tables remain valid`() {
        assertEquals(emptyList(), Tables.simpleTable(emptyList(), emptyList()).columns)
        assertEquals(listOf(Row(Attr(), emptyList())), Tables.normalizeHead(0, TableHead(Attr(), rows(listOf(cell("drop", 1, 1))))).rows)
    }

    private fun samples(check: (Int, List<Row>, TableBody) -> Unit) {
        val random = Random(0x5441424C)
        repeat(500) {
            val width = random.nextInt(2, 17)
            val rows = randomRows(random)
            check(width, rows, TableBody(Attr("body"), random.nextInt(0, 20), randomRows(random), randomRows(random)))
        }
    }

    private fun randomRows(random: Random, malformed: Boolean = false): List<Row> = List(random.nextInt(8)) { row ->
        Row(Attr("row-$row"), List(random.nextInt(9)) { col ->
            cell("$row-$col", random.nextInt(if (malformed) -5 else 1, 10), random.nextInt(if (malformed) -5 else 1, 10))
        })
    }

    private fun assertFirstWidth(width: Int, rows: List<Row>) {
        rows.firstOrNull()?.let { assertEquals(width, it.cells.sumOf { c -> c.colSpan }) }
    }

    private fun assertRowsSubset(normalized: List<Row>, original: List<Row>) {
        assertEquals(original.size, normalized.size)
        normalized.zip(original).forEach { (a, b) ->
            assertEquals(b.attr, a.attr)
            assertCellsSubset(a.cells, b.cells)
        }
    }

    private fun assertCellsSubset(normalized: List<Cell>, original: List<Cell>) {
        normalized.forEachIndexed { index, a ->
            val b = original.getOrNull(index)
            if (b == null) assertEquals(Tables.emptyCell, a) else {
                assertEquals(b.attr, a.attr)
                assertEquals(b.alignment, a.alignment)
                assertEquals(b.blocks, a.blocks)
                assertTrue(a.rowSpan in 1..maxOf(1, b.rowSpan))
                assertTrue(a.colSpan in 1..maxOf(1, b.colSpan))
            }
        }
    }

    private fun splitAtWidth(cells: List<Cell>, width: Int): Pair<List<Cell>, List<Cell>> {
        var count = 0
        var index = 0
        while (count < width && index < cells.size) count += cells[index++].colSpan
        return cells.take(index) to cells.drop(index)
    }

    /** Independent occupancy oracle: no use of the production overhang algorithm. */
    private fun assertGrid(width: Int, rows: List<Row>) {
        val occupied = Array(rows.size) { BooleanArray(width) }
        rows.forEachIndexed { rowIndex, row ->
            var column = 0
            for (cell in row.cells) {
                while (column < width && occupied[rowIndex][column]) column++
                assertTrue(cell.rowSpan >= 1 && cell.colSpan >= 1)
                assertTrue(rowIndex + cell.rowSpan <= rows.size && column + cell.colSpan <= width)
                for (r in rowIndex until rowIndex + cell.rowSpan) for (c in column until column + cell.colSpan) {
                    assertFalse(occupied[r][c], "Overlapping cell at $r/$c")
                    occupied[r][c] = true
                }
                column += cell.colSpan
            }
        }
        assertTrue(occupied.all { row -> row.all { it } }, "Unoccupied grid slot")
    }
}
