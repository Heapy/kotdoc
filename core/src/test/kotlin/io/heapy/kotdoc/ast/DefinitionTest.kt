// Tests of the ported pandoc-types Definition metadata, Format, and SimpleFigure rules.
package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.*

class DefinitionTest {
    @Test
    fun `Format equality uses full Unicode folding with original spelling preserved`() {
        val oldLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            for ((a, b) in listOf("HTML" to "html", "Straße" to "STRASSE", "Σς" to "σσ", "ﬃ" to "ffi", "İ" to "i\u0307")) {
                assertEquals(Format(a), Format(b))
                assertEquals(Format(a).hashCode(), Format(b).hashCode())
                assertEquals(0, Format(a).compareTo(Format(b)))
                assertEquals(a, Format(a).value)
            }
            assertNotEquals(Format("ı"), Format("I"))
            assertEquals("strasse", Format.literal("Straße").value)
            val raw = Inline.RawInline(Format("HTML"), "<b>x</b>")
            val json = PandocJson.encodeInline(raw)
            assertEquals("HTML", json.jsonObject.getValue("c").jsonArray[0].jsonPrimitive.content)
            assertEquals("HTML", (PandocJson.decodeInline(json) as Inline.RawInline).format.value)
        } finally {
            Locale.setDefault(oldLocale)
        }
    }

    @Test
    fun `Unicode ordering follows code points rather than UTF16 surrogates`() {
        val bmp = "\uE000"
        val supplementary = "\uD800\uDC00"
        assertTrue(Format(bmp) < Format(supplementary))
        val meta = mapOf(supplementary to MetaValue.MetaString("last"), bmp to MetaValue.MetaString("first"))
        assertEquals(listOf(bmp, supplementary), PandocJson.encodeMeta(meta).keys.toList())
        val order = mutableListOf<String>()
        object : PandocVisitor() {
            override fun visitMetaValue(value: MetaValue) { if (value is MetaValue.MetaString) order += value.value }
        }.walk(Pandoc(meta))
        assertEquals(listOf("first", "last"), order)
    }

    @Test
    fun `document title and date extract precisely the accepted metadata shapes`() {
        val content = listOf(Inline.Str("two words"))
        for (value in listOf(MetaValue.MetaString("two words"), MetaValue.MetaInlines(content),
            MetaValue.MetaBlocks(listOf(Block.Plain(content))), MetaValue.MetaBlocks(listOf(Block.Para(content))))) {
            assertEquals(content, docTitle(mapOf("title" to value)))
            assertEquals(content, docDate(mapOf("date" to value)))
        }
        for (value in listOf(MetaValue.MetaBlocks(listOf(Block.Para(content), Block.Para(content))),
            MetaValue.MetaBool(true), MetaValue.MetaList(listOf(MetaValue.MetaInlines(content))))) {
            assertEquals(emptyList(), docTitle(mapOf("title" to value)))
        }
        assertEquals(emptyList(), docTitle(emptyMap()))
    }

    @Test
    fun `author list groups by upstream representation and ignores unsupported entries`() {
        fun text(s: String) = listOf(Inline.Str(s))
        val value = MetaValue.MetaList(listOf(MetaValue.MetaString("s1"), MetaValue.MetaBlocks(listOf(Block.Para(text("p")))),
            MetaValue.MetaInlines(text("i1")), MetaValue.MetaBlocks(listOf(Block.Plain(text("plain")))),
            MetaValue.MetaBool(true), MetaValue.MetaInlines(text("i2")), MetaValue.MetaString("s2"),
            MetaValue.MetaBlocks(listOf(Block.Plain(text("ignored")), Block.Plain(text("ignored"))))))
        assertEquals(listOf("i1", "i2", "plain", "p", "s1", "s2").map(::text), docAuthors(mapOf("author" to value)))
        assertEquals(emptyList(), docAuthors(mapOf("author" to MetaValue.MetaBlocks(listOf(Block.Para(text("standalone")))))))
    }

    @Test
    fun `metadata setters and document concatenation prefer right hand values`() {
        val left = Pandoc(mapOf("a" to MetaValue.MetaBool(false)), listOf(Block.HorizontalRule))
        val right = Pandoc(mapOf("a" to MetaValue.MetaBool(true)), listOf(Block.Para(emptyList())))
        assertEquals(Pandoc(right.meta, left.blocks + right.blocks), left + right)
        val doc = left.setTitle(Builder.text("The title")).setAuthors(listOf(Builder.str("Author"))).setDate(Builder.str("2026"))
        assertEquals(Builder.text("The title").values, docTitle(doc.meta))
        assertEquals(listOf(Builder.str("Author").values), docAuthors(doc.meta))
        assertEquals(Builder.str("2026").values, docDate(doc.meta))
        assertFalse(doc.deleteMeta("title").meta.containsKey("title"))
        assertEquals(left.blocks, doc.blocks)
    }

    @Test
    fun `legacy SimpleFigure pattern preserves target and requires exact fig prefix`() {
        val figure = SimpleFigure(Attr("image"), listOf(Inline.Str("caption")), Target("url", "title"))
        assertEquals(figure, SimpleFigure.fromBlock(figure.toBlock()))
        val image = (figure.toBlock().content.single() as Inline.Image)
        assertEquals("fig:title", image.target.title)
        assertNull(SimpleFigure.fromBlock(Block.Para(listOf(image.copy(target = Target("url", "Fig:title"))))))
        assertNull(SimpleFigure.fromBlock(Block.Para(listOf(image, Inline.Space))))
        assertNull(SimpleFigure.fromBlock(Block.Plain(listOf(image))))
    }

    @Test
    fun `numeric AST fields retain signed 64 bit Haskell Int values`() {
        val doc = Pandoc(blocks = listOf(Block.Header(Long.MAX_VALUE, Attr(), emptyList()),
            Block.OrderedList(ListAttributes(Long.MIN_VALUE), emptyList()),
            Block.Para(listOf(Inline.Cite(listOf(Citation("id", citationNoteNum = Long.MAX_VALUE, citationHash = Long.MIN_VALUE)), emptyList()))),
            Block.Table(Attr(), Tables.emptyCaption, emptyList(), TableHead(Attr(), listOf(Row(Attr(), listOf(Cell(Attr(), Alignment.AlignLeft, Long.MAX_VALUE, Long.MIN_VALUE, emptyList()))))),
                listOf(TableBody(Attr(), Long.MAX_VALUE, emptyList(), emptyList())), TableFoot(Attr(), emptyList()))))
        assertEquals(doc, PandocJson.decode(PandocJson.encode(doc)))
        for (number in listOf("2147483648", "2147483648.0", "2.147483648e9")) {
            val header = PandocJson.decodeBlock(Json.parseToJsonElement("""{"t":"Header","c":[$number,["",[],[]],[]]}"""))
            assertEquals(2147483648L, (header as Block.Header).level)
        }
        for (number in listOf("9223372036854775808", "-9223372036854775809", "1.5", "\"1\"")) {
            assertFails { PandocJson.decodeBlock(Json.parseToJsonElement("""{"t":"Header","c":[$number,["",[],[]],[]]}""")) }
        }
    }

    @Test
    fun `normalization clips huge dimensions before allocation or narrowing`() {
        val rows = listOf(Row(Attr(), listOf(Cell(Attr("large"), Alignment.AlignLeft, Long.MAX_VALUE, Long.MAX_VALUE, emptyList()))))
        val body = Tables.normalizeBody(2, TableBody(Attr(), Long.MAX_VALUE, rows, rows))
        assertEquals(2L, body.rowHeadColumns)
        assertEquals(1L, body.body.single().cells.single().rowSpan)
        assertEquals(2L, body.body.single().cells.single().colSpan)
    }
}
