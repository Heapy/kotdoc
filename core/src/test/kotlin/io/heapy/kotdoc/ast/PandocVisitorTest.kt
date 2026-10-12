// Based on the walk/query equivalence tests in pandoc-types/test/test-pandoc-types.hs.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.*

class PandocVisitorTest {
    private fun documents(): List<Pandoc> {
        val fixtures = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/pandoc-types/json-fixtures.json")).readText()).jsonArray
        return fixtures.mapNotNull {
            val row = it.jsonObject
            val value = Json.parseToJsonElement(row.getValue("json").jsonPrimitive.content)
            when (row.getValue("type").jsonPrimitive.content) {
                "Block" -> Pandoc(blocks = listOf(PandocJson.decodeBlock(value)))
                "Inline" -> Pandoc(blocks = listOf(Block.Para(listOf(PandocJson.decodeInline(value)))))
                "MetaValue" -> Pandoc(mapOf("value" to PandocJson.decodeMetaValue(value)))
                "Meta" -> Pandoc(PandocJson.decodeMeta(value))
                else -> null
            }
        } + Pandoc(mapOf("z" to MetaValue.MetaList(listOf(MetaValue.MetaString("z"))), "a" to MetaValue.MetaMap(mapOf("v" to MetaValue.MetaString("a")))))
    }

    @Test
    fun `upstream inline transformation agrees with independent generic traversal`() {
        val visitor = object : PandocVisitor() {
            override fun transformInline(value: Inline): Inline = when (value) {
                is Inline.Str -> value.copy(text = value.text.uppercase(Locale.ROOT))
                is Inline.Emph -> Inline.Strong(value.content)
                else -> value
            }
        }
        documents().forEach { doc ->
            val expected = bottomUp(PandocJson.toJson(doc)) { obj ->
                when (obj["t"]?.jsonPrimitive?.content) {
                    "Str" -> JsonObject(obj + ("c" to JsonPrimitive(obj.getValue("c").jsonPrimitive.content.uppercase(Locale.ROOT))))
                    "Emph" -> JsonObject(obj + ("t" to JsonPrimitive("Strong")))
                    else -> obj
                }
            }
            assertEquals(PandocJson.decode(expected), visitor.walk(doc))
        }
    }

    @Test
    fun `upstream block transformation agrees with independent generic traversal`() {
        val visitor = object : PandocVisitor() {
            override fun transformBlock(value: Block): Block = when (value) {
                is Block.Plain -> Block.Para(value.content)
                is Block.BlockQuote -> Block.Div(Attr(classes = listOf("special")), value.blocks)
                else -> value
            }
        }
        documents().forEach { doc ->
            val expected = bottomUp(PandocJson.toJson(doc)) { obj ->
                when (obj["t"]?.jsonPrimitive?.content) {
                    "Plain" -> JsonObject(obj + ("t" to JsonPrimitive("Para")))
                    "BlockQuote" -> buildJsonObject {
                        put("t", "Div")
                        put("c", JsonArray(listOf(Json.parseToJsonElement("""["",["special"],[]]"""), obj.getValue("c"))))
                    }
                    else -> obj
                }
            }
            assertEquals(PandocJson.decode(expected), visitor.walk(doc))
        }
    }

    @Test
    fun `upstream metadata value transformation agrees with generic traversal`() {
        val visitor = object : PandocVisitor() {
            override fun transformMetaValue(value: MetaValue): MetaValue = when (value) {
                is MetaValue.MetaBool -> value.copy(value = !value.value)
                is MetaValue.MetaString -> value.copy(value = value.value.uppercase(Locale.ROOT))
                else -> value
            }
        }
        documents().forEach { doc ->
            val expected = bottomUp(PandocJson.toJson(doc)) { obj ->
                when (obj["t"]?.jsonPrimitive?.content) {
                    "MetaBool" -> JsonObject(obj + ("c" to JsonPrimitive(!obj.getValue("c").jsonPrimitive.boolean)))
                    "MetaString" -> JsonObject(obj + ("c" to JsonPrimitive(obj.getValue("c").jsonPrimitive.content.uppercase(Locale.ROOT))))
                    else -> obj
                }
            }
            assertEquals(PandocJson.decode(expected), visitor.walk(doc))
        }
    }

    @Test
    fun `metadata walk transforms top-level map only`() {
        val visitor = object : PandocVisitor() {
            override fun transformMeta(value: Map<String, MetaValue>) = value.mapKeys { it.key.uppercase(Locale.ROOT) }
        }
        documents().forEach { doc -> assertEquals(doc.copy(meta = doc.meta.mapKeys { it.key.uppercase(Locale.ROOT) }), visitor.walk(doc)) }
    }

    @Test
    fun `inline query matches generic query including metadata captions cells and citation affixes`() {
        documents().forEach { doc ->
            val found = mutableListOf<String>()
            object : PandocVisitor() {
                override fun visitInline(value: Inline) { if (value is Inline.Str) found += value.text }
            }.walk(doc)
            assertEquals(query(PandocJson.toJson(doc), "Str").map { it.getValue("c").jsonPrimitive.content }, found)
        }
    }

    @Test
    fun `block query matches generic header query`() {
        documents().forEach { doc ->
            val found = mutableListOf<Int>()
            object : PandocVisitor() {
                override fun visitBlock(value: Block) { if (value is Block.Header) found += value.level }
            }.walk(doc)
            assertEquals(query(PandocJson.toJson(doc), "Header").map { it.getValue("c").jsonArray[0].jsonPrimitive.int }, found)
        }
    }

    @Test
    fun `metadata query matches generic strings and map size`() {
        documents().forEach { doc ->
            val strings = mutableListOf<String>()
            val sizes = mutableListOf<Int>()
            object : PandocVisitor() {
                override fun visitMetaValue(value: MetaValue) { if (value is MetaValue.MetaString) strings += value.value }
                override fun visitMeta(value: Map<String, MetaValue>) { sizes += value.size }
            }.walk(doc)
            assertEquals(query(PandocJson.toJson(doc), "MetaString").map { it.getValue("c").jsonPrimitive.content }, strings)
            assertEquals(listOf(doc.meta.size), sizes)
        }
    }

    @Test
    fun `visit is preorder transform is postorder and new nodes are not revisited`() {
        val order = mutableListOf<String>()
        val input = Inline.Emph(listOf(Inline.Str("x")))
        val result = object : PandocVisitor() {
            override fun visitInline(value: Inline) { order += "visit:${value::class.simpleName}" }
            override fun transformInline(value: Inline): Inline {
                order += "transform:${value::class.simpleName}"
                return if (value is Inline.Str) Inline.Strong(listOf(value)) else value
            }
        }.walkInline(input)
        assertEquals(listOf("visit:Emph", "visit:Str", "transform:Str", "transform:Emph"), order)
        assertEquals(Inline.Emph(listOf(Inline.Strong(listOf(Inline.Str("x"))))), result)
    }

    @Test
    fun `list transforms can remove content and unwrap nested blocks`() {
        val visitor = object : PandocVisitor() {
            override fun transformInlines(value: List<Inline>): List<Inline> = if (value.all {
                it == Inline.Space || it == Inline.SoftBreak || it == Inline.LineBreak || it == Inline.Str("")
            }) emptyList() else value
            override fun transformBlocks(value: List<Block>): List<Block> = when (val single = value.singleOrNull()) {
                is Block.CodeBlock -> emptyList()
                is Block.BlockQuote -> single.blocks
                is Block.Div -> single.blocks
                else -> value
            }
        }
        val para = Block.Para(listOf(Inline.Space, Inline.SoftBreak, Inline.Str("")))
        val doc = Pandoc(blocks = listOf(Block.Div(Attr(), listOf(Block.BlockQuote(listOf(para))))))
        assertEquals(Pandoc(blocks = listOf(Block.Para(emptyList()))), visitor.walk(doc))
        assertEquals(Pandoc(), visitor.walk(Pandoc(blocks = listOf(Block.CodeBlock(Attr(), "code")))))
    }

    @Test
    fun `empty lists are visited and absent short captions are not`() {
        val sizes = mutableListOf<Int>()
        val visitor = object : PandocVisitor() {
            override fun visitInlines(value: List<Inline>) { sizes += value.size }
        }
        visitor.walkCaption(Caption(null, listOf(Block.Para(emptyList()))))
        assertEquals(listOf(0), sizes)
        sizes.clear()
        visitor.walkCaption(Caption(emptyList(), listOf(Block.Para(emptyList()))))
        assertEquals(listOf(0, 0), sizes)
    }

    // Independent untyped traversals, analogous to upstream Data.Generics oracle.
    private fun bottomUp(value: JsonElement, f: (JsonObject) -> JsonObject): JsonElement = when (value) {
        is JsonObject -> f(JsonObject(value.mapValues { bottomUp(it.value, f) }))
        is JsonArray -> JsonArray(value.map { bottomUp(it, f) })
        else -> value
    }

    private fun query(value: JsonElement, tag: String): List<JsonObject> = when (value) {
        is JsonObject -> (if (value["t"]?.jsonPrimitive?.content == tag) listOf(value) else emptyList()) + value.values.flatMap { query(it, tag) }
        is JsonArray -> value.flatMap { query(it, tag) }
        else -> emptyList()
    }
}
