package io.heapy.kotdoc.ast

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.*

class PandocJsonTest {
    @Test
    fun `API compatibility uses major and minor not patch`() {
        for (version in listOf("[1,23]", "[1,23,0]", "[1,23,99,7]")) {
            assertEquals(Pandoc(), PandocJson.decode("""{"pandoc-api-version":$version,"meta":{},"blocks":[]}"""))
        }
        for (version in listOf("[]", "[1]", "[1,22]", "[2,23]", "null", "[\"1\",23]")) {
            assertFails { PandocJson.decode("""{"pandoc-api-version":$version,"meta":{},"blocks":[]}""") }
        }
        for (input in listOf("{}", "null", "[]", """{"pandoc-api-version":[1,23]}""")) {
            assertFails { PandocJson.decode(input) }
        }
    }

    @Test
    fun `malformed node payloads are rejected`() {
        for (input in listOf(
            """{"t":"Unknown","c":[]}""",
            """{"t":"Str","c":null}""",
            """{"t":"Str","c":17}""",
            """{"t":"Code","c":[["",[],[]]]}""",
            """{"t":"Emph","c":[{"t":"Para","c":[]}]}""",
            """{"t":"Math","c":[{"t":"Unknown"},"x"]}""",
        )) assertFails(input) { PandocJson.decodeInline(Json.parseToJsonElement(input)) }
    }

    @Test
    fun `duplicate attributes and unicode survive JSON roundtrip`() {
        val attr = Attr("😀", listOf("a", "a"), listOf("key" to "first", "key" to "second"))
        val doc = Pandoc(mapOf("тема" to MetaValue.MetaString("Привет 日本語 👩🏽‍💻\n\t\u0000")),
            listOf(Block.Para(listOf(Inline.Link(attr, listOf(Inline.Str("a\"\\b")), Target("https://example.com/é", "中文"))))))
        assertEquals(doc, PandocJson.decode(PandocJson.encode(doc)))
    }

    @Test
    fun `constructors omitted from upstream JSON examples`() {
        val inline = Inline.Math(MathType.DisplayMath, "x²")
        assertEquals("""{"t":"Math","c":[{"t":"DisplayMath"},"x²"]}""", PandocJson.encodeInline(inline).toString())
        assertEquals(inline, PandocJson.decodeInline(PandocJson.encodeInline(inline)))
        assertEquals(Block.HorizontalRule, PandocJson.decodeBlock(Json.parseToJsonElement("""{"t":"HorizontalRule"}""")))
        for (alignment in Alignment.entries) assertEquals(alignment, PandocJson.decodeEnum<Alignment>(PandocJson.encodeEnum(alignment)))
        for (style in ListNumberStyle.entries) for (delimiter in ListNumberDelim.entries) {
            val attrs = ListAttributes(-3, style, delimiter)
            assertEquals(attrs, PandocJson.decodeListAttributes(PandocJson.encodeListAttributes(attrs)))
        }
        val caption = Caption(null, emptyList())
        assertEquals("[null,[]]", PandocJson.encodeCaption(caption).toString())
        assertEquals(caption, PandocJson.decodeCaption(PandocJson.encodeCaption(caption)))
        for (width in listOf(0.0, 0.25, -1.0, 1.0e200)) {
            assertEquals(ColWidth.Width(width), PandocJson.decodeColWidth(PandocJson.encodeColWidth(ColWidth.Width(width))))
        }
    }

    @Test
    fun `upstream roundtrip property with reproducible recursive documents`() {
        val random = Random(0x50414E44)
        repeat(500) { index ->
            val doc = Pandoc(mapOf("title" to MetaValue.MetaBlocks(randomBlocks(random, 3))), randomBlocks(random, 3))
            assertEquals(doc, PandocJson.decode(PandocJson.encode(doc)), "Generated document $index")
        }
    }

    private fun randomBlocks(r: Random, depth: Int): List<Block> = List(r.nextInt(4)) {
        if (depth == 0) Block.Para(randomInlines(r, 0)) else when (r.nextInt(7)) {
            0 -> Block.BlockQuote(randomBlocks(r, depth - 1))
            1 -> Block.BulletList(List(r.nextInt(3)) { randomBlocks(r, depth - 1) })
            2 -> Block.Div(Attr("section"), randomBlocks(r, depth - 1))
            3 -> Block.Figure(Attr(), Caption(randomInlines(r, depth - 1), randomBlocks(r, depth - 1)), randomBlocks(r, depth - 1))
            4 -> Block.HorizontalRule
            5 -> Block.OrderedList(ListAttributes(r.nextLong(), ListNumberStyle.entries.random(r), ListNumberDelim.entries.random(r)), listOf(randomBlocks(r, depth - 1)))
            else -> Block.Para(randomInlines(r, depth - 1))
        }
    }

    private fun randomInlines(r: Random, depth: Int): List<Inline> = List(r.nextInt(5)) {
        if (depth == 0) Inline.Str(listOf("", "Привет", "😀", "\u0000\n", "<>&\"", "x").random(r)) else when (r.nextInt(6)) {
            0 -> Inline.Emph(randomInlines(r, depth - 1))
            1 -> Inline.Note(randomBlocks(r, depth - 1))
            2 -> Inline.Cite(listOf(Citation("ref", randomInlines(r, depth - 1), randomInlines(r, depth - 1))), randomInlines(r, depth - 1))
            3 -> Inline.Space
            4 -> Inline.Math(MathType.entries.random(r), "x+y")
            else -> Inline.Link(Attr(), randomInlines(r, depth - 1), Target("url", "title"))
        }
    }
}
