// Tests.Shared legacy-table examples and section properties, copyright
// (C) 2006-2024 John MacFarlane. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.ast

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.*

class DocumentSharedTest {
    private fun plain(text: String) = listOf(Block.Plain(listOf(Inline.Str(text))))
    private fun cell(text: String, height: Long, width: Long) = Cell(Attr(), Alignment.AlignDefault, height, width, plain(text))
    private fun rows(vararg rows: List<Cell>) = rows.map { Row(Attr(), it) }
    private val specs = listOf(ColSpec(Alignment.AlignDefault, ColWidth.Width(0.5)), ColSpec(Alignment.AlignDefault, ColWidth.Width(0.5)), ColSpec(Alignment.AlignRight, ColWidth.Default))
    private val head = TableHead(Attr(), rows(listOf(cell("a",1,1),cell("b",2,2)),listOf(cell("c",1,1))))
    private val body = TableBody(Attr(), 1, rows(listOf(cell("e",3,1),cell("f",3,2)),emptyList(),emptyList()),
        rows(List(3) { Tables.emptyCell },listOf(cell("g",1,1),Tables.emptyCell,Tables.emptyCell)))
    private val foot = TableFoot(Attr(),rows(listOf(cell("h",1,2),cell("i",2,1)),listOf(cell("j",1,2))))
    private val expectedBody = listOf(listOf(plain("e"),plain("f"),emptyList()), List(3) { emptyList<Block>() },List(3) { emptyList() },
        List(3) { emptyList() }, listOf(plain("g"),emptyList(),emptyList()),listOf(plain("h"),emptyList(),plain("i")),listOf(plain("j"),emptyList(),emptyList()))

    @Test fun `decomposes a table with head`() {
        assertEquals(DocumentShared.LegacyTable(listOf(Inline.Str("caption")), specs.map { it.alignment }, listOf(0.5,0.5,0.0),
            listOf(plain("a"),plain("b"),emptyList()), listOf(listOf(plain("c"),emptyList(),emptyList()))+expectedBody),
            DocumentShared.toLegacyTable(Block.Table(Attr(),Caption(null,plain("caption")),specs,head,listOf(body),foot)))
    }
    @Test fun `decomposes a table without head`() {
        assertEquals(DocumentShared.LegacyTable(emptyList(),specs.map { it.alignment },listOf(0.5,0.5,0.0),emptyList(),expectedBody),
            DocumentShared.toLegacyTable(Block.Table(Attr(),Tables.emptyCaption,specs,TableHead(Attr(),emptyList()),listOf(body),foot)))
    }
    @Test fun `decomposes the table from issue 7683`() {
        val specs = List(4) { ColSpec(Alignment.AlignDefault,ColWidth.Default) }
        val body = TableBody(Attr(),0,emptyList(),rows(listOf(cell("a",2,1),cell("b",1,2),cell("c",2,1)),listOf(cell("d",1,1),cell("e",1,1))))
        assertEquals(DocumentShared.LegacyTable(emptyList(),List(4) { Alignment.AlignDefault },List(4) { 0.0 },emptyList(),
            listOf(listOf(plain("a"),plain("b"),emptyList(),plain("c")),listOf(emptyList(),plain("d"),plain("e"),emptyList()))),
            DocumentShared.toLegacyTable(Block.Table(Attr(),Tables.emptyCaption,specs,TableHead(Attr(),emptyList()),listOf(body),TableFoot(Attr(),emptyList()))))
    }
    @Test fun `makeSections original idempotence example`() {
        val blocks = listOf(1L to "H1",2L to "H2",3L to "H3",2L to "H2a",4L to "H4",1L to "H1a").map { (level,name) -> Block.Header(level,Attr(),listOf(Inline.Str(name))) }
        val sections = DocumentShared.makeSections(blocks)
        assertEquals(sections, DocumentShared.makeSections(sections))
    }
    @Test fun `section idempotence over seeded nested header and div trees`() {
        val random=Random(0x534543)
        fun blocks(depth: Int): List<Block> = List(random.nextInt(12)) { i -> when (random.nextInt(if (depth == 0) 2 else 3)) {
            0 -> Block.Para(listOf(Inline.Str("p$i")))
            1 -> Block.Header(random.nextLong(1,8),Attr("h$i"),listOf(Inline.Str("title$i")))
            else -> Block.Div(Attr("d$i",listOf(listOf("section","column","other").random(random))),blocks(depth-1))
        } }
        repeat(500) { val result=DocumentShared.makeSections(blocks(3)); assertEquals(result,DocumentShared.makeSections(result)) }
    }
    @Test fun `combineAttr preserves source precedence and second-list reverse-fold semantics`() {
        assertEquals(Attr("first",listOf("a","b"),listOf("new" to "last", "old" to "first")),
            DocumentShared.combineAttr(Attr("first",listOf("a"),listOf("old" to "first")),Attr("second",listOf("a","b"),listOf("old" to "second","new" to "earlier","new" to "last"))))
    }
}
