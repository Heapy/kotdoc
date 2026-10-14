package io.heapy.kotdoc.ast

import io.heapy.kotdoc.layout.TextChars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

/** Transport only: expected text is GHC's Show for unchanged Definition.hs. */
class AstShowTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.i() = jsonPrimitive.int
    private inline fun <reified A : Enum<A>> enum(v: JsonElement): A = enumValueOf(v.jsonObject.getValue("t").s())
    private fun value(kind: String, v: JsonElement): Any = when (kind) {
        "pandoc" -> PandocJson.decode(v)
        "metaValue" -> PandocJson.decodeMetaValue(v)
        "inline" -> PandocJson.decodeInline(v)
        "block" -> PandocJson.decodeBlock(v)
        "attr" -> PandocJson.decodeAttr(v)
        "target" -> PandocJson.decodeTarget(v)
        "listAttributes" -> PandocJson.decodeListAttributes(v)
        "style" -> enum<ListNumberStyle>(v)
        "delim" -> enum<ListNumberDelim>(v)
        "alignment" -> enum<Alignment>(v)
        "quote" -> enum<QuoteType>(v)
        "math" -> enum<MathType>(v)
        "citationMode" -> enum<CitationMode>(v)
        "citation" -> PandocJson.decodeCitation(v)
        "format" -> Format(v.s())
        "colWidth" -> PandocJson.decodeColWidth(v)
        "colSpec" -> PandocJson.decodeColSpec(v)
        "row" -> PandocJson.decodeRow(v)
        "cell" -> PandocJson.decodeCell(v)
        "caption" -> PandocJson.decodeCaption(v)
        "tableHead" -> PandocJson.decodeTableHead(v)
        "tableBody" -> PandocJson.decodeTableBody(v)
        "tableFoot" -> PandocJson.decodeTableFoot(v)
        else -> error(kind)
    }
    private fun show(kind: String, v: JsonElement, p: Int): String = when (kind) {
        "meta" -> AstShow.meta(PandocJson.decodeMeta(v), p)
        "rowHeadColumns" -> AstShow.rowHeadColumns(v.jsonPrimitive.long, p)
        "rowSpan" -> AstShow.rowSpan(v.jsonPrimitive.long, p)
        "colSpan" -> AstShow.colSpan(v.jsonPrimitive.long, p)
        else -> AstShow.show(value(kind, v), p)
    }
    private fun partial(name: String, n: Int): String {
        fun badHead(message: String) = object : AbstractList<Inline>() {
            override val size = 1
            override fun get(index: Int): Inline = error(message)
        }
        val v: Any = when (name) {
            "inline-head" -> Inline.Emph(badHead("inline head"))
            "caption-head" -> Caption(badHead("caption head"), emptyList())
            "citation-head" -> Citation("id", badHead("citation head"))
            "inline-tail" -> Inline.Emph(object : AbstractList<Inline>() {
                override val size: Int get() = error("inline tail")
                override fun get(index: Int): Inline = error("unused get")
                override fun iterator() = object : Iterator<Inline> {
                    private var first = true
                    override fun hasNext(): Boolean = if (first) true else error("inline tail")
                    override fun next(): Inline { first = false; return Inline.Space }
                }
            })
            "infinite-list" -> Inline.Emph(object : AbstractList<Inline>() {
                override val size: Int get() = error("infinite size")
                override fun get(index: Int) = Inline.Space
                override fun iterator() = object : Iterator<Inline> {
                    override fun hasNext() = true
                    override fun next() = Inline.Space
                }
            })
            "infinite-node" -> {
                lateinit var node: Inline
                node = Inline.Emph(object : AbstractList<Inline>() {
                    override val size = 1
                    override fun get(index: Int) = node
                })
                node
            }
            "infinite-block" -> {
                lateinit var node: Block
                node = Block.BlockQuote(object : AbstractList<Block>() {
                    override val size = 1
                    override fun get(index: Int) = node
                })
                node
            }
            else -> error(name)
        }
        return AstShow.prefix(v, n)
    }
    private fun deep(name: String, n: Int): String = when (name) {
        "inline", "mixed" -> {
            var v: Inline = Inline.Space
            repeat(n) { v = if (name == "inline") Inline.Emph(listOf(v)) else Inline.Note(listOf(Block.Plain(listOf(v)))) }
            AstShow.show(v)
        }
        "block" -> {
            var v: Block = Block.HorizontalRule
            repeat(n) { v = Block.BlockQuote(listOf(v)) }
            AstShow.show(v)
        }
        "metaValue" -> {
            var v: MetaValue = MetaValue.MetaBool(true)
            repeat(n) { v = MetaValue.MetaList(listOf(v)) }
            AstShow.show(v)
        }
        else -> error(name)
    }
    private fun operation(a: JsonArray): String = when (a[0].s()) {
        "show" -> show(a[1].s(), a[3], a[2].i())
        "prefix" -> AstShow.prefix(value(a[1].s(), a[4]), a[3].i(), a[2].i())
        "partial" -> partial(a[1].s(), a[2].i())
        "deep" -> deep(a[1].s(), a[2].i())
        "double" -> AstShow.show(ColWidth.Width(Double.fromBits(a[2].s().toULong().toLong())), a[1].i())
        "text" -> {
            val t = TextChars.fromPoints(a[3].jsonArray.map { it.i() }.toIntArray())
            val v = when (a[1].s()) {
                "inline" -> Inline.Str(t)
                "metaValue" -> MetaValue.MetaString(t)
                "format" -> Format(t)
                else -> error(a)
            }
            AstShow.show(v, a[2].i())
        }
        else -> error(a)
    }
    @TestFactory fun originalObservations(): List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/ast-show/data.jsonl.gz"))).bufferedReader().use { reader ->
            reader.lineSequence().drop(1).mapIndexed { index, line -> DynamicTest.dynamicTest("original AST Show $index") {
                val c = Json.parseToJsonElement(line).jsonObject
                val request = c.getValue("request").jsonArray
                val actual = try { JsonPrimitive(operation(request)) } catch (e: IllegalStateException) {
                    buildJsonObject { put("exception", e.message.orEmpty().lineSequence().first()) }
                }
                assertEquals(c.getValue("expected"), actual, "case $index: ${request.toString().take(500)}")
            } }.toList()
        }
}
