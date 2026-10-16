package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

class OriginalTypesShrinkTest {
    private fun decode(kind: String, x: JsonElement): Any = when (kind) {
        "inline" -> PandocJson.decodeInline(x)
        "block" -> PandocJson.decodeBlock(x)
        "inlines" -> x.jsonArray.map(PandocJson::decodeInline)
        "blocks" -> x.jsonArray.map(PandocJson::decodeBlock)
        "caption" -> PandocJson.decodeCaption(x)
        "row" -> PandocJson.decodeRow(x)
        "cell" -> PandocJson.decodeCell(x)
        "head" -> PandocJson.decodeTableHead(x)
        "body" -> PandocJson.decodeTableBody(x)
        "foot" -> PandocJson.decodeTableFoot(x)
        else -> error(kind)
    }
    @Suppress("UNCHECKED_CAST")
    private fun shrink(kind: String, x: Any): Sequence<Any> = when (kind) {
        "inline" -> OriginalTypesShrink.inline(x as Inline)
        "block" -> OriginalTypesShrink.block(x as Block)
        "inlines" -> OriginalTypesShrink.inlines(x as List<Inline>)
        "blocks" -> OriginalTypesShrink.blocks(x as List<Block>)
        "caption" -> OriginalTypesShrink.caption(x as Caption)
        "row" -> OriginalTypesShrink.row(x as Row)
        "cell" -> OriginalTypesShrink.cell(x as Cell)
        "head" -> OriginalTypesShrink.head(x as TableHead)
        "body" -> OriginalTypesShrink.body(x as TableBody)
        "foot" -> OriginalTypesShrink.foot(x as TableFoot)
        else -> error(kind)
    }
    @Test fun removedElementsStayUnevaluated() {
        val partial = object : AbstractList<Inline>() {
            override val size = 100_000
            override fun get(index: Int): Inline = error("removed source head was forced")
        }
        assertEquals(emptyList(), OriginalTypesShrink.inlines(partial).first())
    }
    @TestFactory fun originalOrderedShrinkCandidates(): List<DynamicTest> {
        val stream = javaClass.getResourceAsStream("/pandoc-types/shrink.jsonl.gz")!!
        return GZIPInputStream(stream).bufferedReader().use { it.readLines() }.map { line ->
            val r = Json.parseToJsonElement(line).jsonObject
            val kind = r.getValue("kind").jsonPrimitive.content
            DynamicTest.dynamicTest("$kind seed=${r["seed"]}") {
                val expected = r.getValue("expected").jsonArray.map { decode(kind,it) }
                val actual = shrink(kind,decode(kind,r.getValue("input")))
                val limit = r["limit"]?.jsonPrimitive?.int
                assertEquals(expected, if (limit == null) actual.toList() else actual.take(limit).toList())
                if (limit != null) assertEquals(r.getValue("truncated").jsonPrimitive.boolean, actual.drop(limit).any())
            }
        }
    }
}
