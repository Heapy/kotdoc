package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

class OriginalTypesShrinkClosureTest {
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
    private fun digest(s: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun records(name: String): List<JsonObject> {
        val stream=javaClass.getResourceAsStream("/pandoc-types/$name.jsonl.gz")!!
        return GZIPInputStream(stream).bufferedReader().use { it.readLines() }.map { Json.parseToJsonElement(it).jsonObject }
    }
    @TestFactory fun allSourceCharacters(): List<DynamicTest> = records("shrink-char-sweep").map { r ->
        DynamicTest.dynamicTest("Text.pack original Char ${r["start"]}..${r["end"]}") {
            val start=r.getValue("start").jsonPrimitive.int; val end=r.getValue("end").jsonPrimitive.int
            val actual=buildString {
                append('[')
                for (p in start..end) {
                    if(p!=start) append(',')
                    // Text.pack replaces surrogate Char values with U+FFFD.
                    val input=String(Character.toChars(if(p in 0xd800..0xdfff) 0xfffd else p))
                    append('[')
                    OriginalTypesShrink.text(input).forEachIndexed { index, text ->
                        if(index!=0) append(',')
                        append('['); text.codePoints().toArray().forEachIndexed { i,cp -> if(i!=0) append(','); append(cp) }; append(']')
                    }
                    append(']')
                }
                append(']')
            }
            assertEquals(r.getValue("sha256").jsonPrimitive.content,digest(actual))
        }
    }
    @TestFactory fun finiteDemand(): List<DynamicTest> = records("shrink-demand").map { r ->
        val name=r.getValue("name").jsonPrimitive.content
        DynamicTest.dynamicTest(name) {
            fun <A> partial(n: Int) = object : AbstractList<A>() {
                override val size=n
                override fun get(index: Int): A = error("element demanded")
            }
            val actual=runCatching {
                when(name) {
                    "removed-all-size" -> OriginalTypesShrink.listWithoutElementShrinks(partial<Int>(100000)).first().size
                    "removed-half-size" -> OriginalTypesShrink.listWithoutElementShrinks(partial<Int>(100000)).drop(1).first().size
                    "retained-element-error" -> OriginalTypesShrink.listWithoutElementShrinks(partial<Int>(100000)).drop(1).first().first()
                    "last-small-removal-size" -> OriginalTypesShrink.listWithoutElementShrinks(partial<Int>(9)).last().size
                    "definition-first-size" -> (OriginalTypesShrink.block(Block.DefinitionList(partial(100000))).first() as Block.DefinitionList).items.size
                    "citation-noop-list-count" -> OriginalTypesShrink.inline(Inline.Cite(partial(1),emptyList())).count()
                    else -> error(name)
                }
            }.getOrNull()
            assertEquals(r.getValue("value").jsonPrimitive.intOrNull,actual)
        }
    }
    @TestFactory fun namedOrderedShrinkCandidates(): List<DynamicTest> = records("shrink-closure").map { r ->
        val kind=r.getValue("kind").jsonPrimitive.content
        DynamicTest.dynamicTest("$kind ${r["name"]}") {
            val expected=r.getValue("expected").jsonArray.map { decode(kind,it) }
            val actual=shrink(kind,decode(kind,r.getValue("input")))
            val limit=r.getValue("limit").jsonPrimitive.intOrNull
            assertEquals(expected,if(limit==null) actual.toList() else actual.take(limit).toList())
            assertEquals(r.getValue("truncated").jsonPrimitive.boolean,if(limit==null) false else actual.drop(limit).any())
        }
    }
}
