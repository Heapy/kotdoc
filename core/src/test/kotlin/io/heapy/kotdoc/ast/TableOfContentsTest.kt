// Original Tests.Shared and pandoc-structure.lua examples, plus independent oracles.
// Copyright (C) 2006-2024 John MacFarlane, Albert Krewinkel. GPL-2.0-or-later.
package io.heapy.kotdoc.ast

import io.heapy.kotdoc.structure.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

class TableOfContentsTest {
    private val fixtures = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/structure/toc.json")).readText()).jsonObject
    private fun text(s: String) = listOf(Inline.Str(s))
    private fun header(s: String, level: Long = 1, id: String = "") = Block.Header(level, Attr(id), text(s))
    private fun plain(s: String) = Block.Plain(text(s))
    private fun link(s: String, id: String) = Block.Plain(listOf(Inline.Link(Attr("toc-$id"), text(s), Target("#$id"))))

    @TestFactory fun `all seven original Haskell TOC examples`(): List<DynamicTest> {
        val examples = listOf(
            emptyList<Block>() to Block.BulletList(emptyList()),
            listOf(Block.HorizontalRule) to Block.BulletList(emptyList()),
            listOf(header("H1")) to Block.BulletList(listOf(listOf(plain("H1")))),
            listOf(header("H1", id = "h1")) to Block.BulletList(listOf(listOf(link("H1", "h1")))),
            listOf(header("H1a"), header("H2", 2)) to Block.BulletList(listOf(listOf(plain("H1a"), Block.BulletList(listOf(listOf(plain("H2"))))))),
            listOf(header("H1a"), header("H2", 2, "h2")) to Block.BulletList(listOf(listOf(plain("H1a"), Block.BulletList(listOf(listOf(link("H2", "h2"))))))),
            listOf(Block.Div(Attr("sec", listOf("section")), listOf(header("H1")))) to Block.BulletList(listOf(listOf(link("H1", "sec")))),
        )
        return examples.mapIndexed { i, (input, expected) -> DynamicTest.dynamicTest("original $i") {
            assertEquals(expected, TableOfContents.toTableOfContents(input))
        } }
    }

    @TestFactory fun `TOCs for every original Native document`(): List<DynamicTest> =
        fixtures.getValue("corpus").jsonArray.flatMap { value ->
            val c = value.jsonObject
            listOf(false, true).map { numbers -> DynamicTest.dynamicTest("${c.getValue("path")} numbered=$numbers") {
                val input = PandocJson.decode(c.getValue("document")).blocks
                val expected = c.getValue(if (numbers) "numbered" else "default")
                assertEquals(expected, PandocJson.encodeBlock(TableOfContents.toTableOfContents(input, numbers, if (numbers) 6 else 3)))
            } }
        }

    @TestFactory fun `independent section and inline cleaning cases`(): List<DynamicTest> =
        fixtures.getValue("edges").jsonArray.map { value ->
            val c = value.jsonObject
            DynamicTest.dynamicTest(c.getValue("name").jsonPrimitive.content) {
                assertEquals(c.getValue("expected"), PandocJson.encodeBlock(TableOfContents.toTableOfContents(
                    PandocJson.decode(c.getValue("document")).blocks,
                    c.getValue("numbers").jsonPrimitive.boolean, c.getValue("depth").jsonPrimitive.long)))
            }
        }

    private fun tree(json: JsonElement): Tree<SecInfo> {
        val t = json.jsonObject
        return Tree(SecInfo(t.getValue("title").jsonArray.map(PandocJson::decodeInline),
            t.getValue("number").jsonPrimitive.contentOrNull, t.getValue("id").jsonPrimitive.content,
            t.getValue("path").jsonPrimitive.content, t.getValue("level").jsonPrimitive.long),
            t.getValue("children").jsonArray.map(::tree))
    }

    @TestFactory fun `original chunk TOC trees preserve paths and depths`(): List<DynamicTest> =
        fixtures.getValue("trees").jsonArray.map { value ->
            val c = value.jsonObject
            DynamicTest.dynamicTest(c.getValue("name").jsonPrimitive.content) {
                assertEquals(c.getValue("expected"), PandocJson.encodeBlock(TableOfContents.tocToList(
                    tree(c.getValue("tree")), c.getValue("numbers").jsonPrimitive.boolean, c.getValue("depth").jsonPrimitive.long)))
            }
        }

    @Test fun `tree construction takes div ids and header attributes without inventing sections`() {
        val child = Block.Div(Attr("child", listOf("arbitrary")), listOf(header("Child", 2, "ignored")))
        val title = header("Parent").copy(attr = Attr("header-id", listOf("unlisted"), listOf("number" to "", "number" to "second")))
        val parent = Block.Div(Attr("parent", listOf("unlisted"), listOf("number" to "div-number")), listOf(title, child))
        val wrapped = Block.Div(Attr("outer"), listOf(Block.Div(Attr("middle"), listOf(parent))))
        val unlisted = Block.Div(Attr("hidden"), listOf(header("Hidden").copy(attr = Attr(classes = listOf("unlisted"))), child))
        val expected = Tree(SecInfo(emptyList(), null, "", "", 0), listOf(
            Tree(SecInfo(text("Parent"), "", "parent", "", 1), listOf(Tree(SecInfo(text("Child"), null, "child", "", 2))))))
        assertEquals(expected, TableOfContents.toTOCTree(listOf(header("Bare header"), wrapped, unlisted,
            Block.BlockQuote(listOf(child)), Block.Div(Attr(), listOf(plain("Before"), child)))))
    }

    @Test fun `tree conversion uses explicit paths empty ids and parent filtering`() {
        val root = SecInfo(text("Ignored root"), null, "root", "root.html", 99)
        val deep = Tree(SecInfo(text("Deep"), null, "deep", "", 4), listOf(Tree(SecInfo(text("Shallow child"), null, "", "", 1))))
        val noId = Tree(SecInfo(text("No ID"), null, "", "chapter.html#", 1))
        assertEquals(Block.BulletList(listOf(listOf(Block.Plain(listOf(Inline.Link(Attr(), text("No ID"), Target("chapter.html#"))))))),
            TableOfContents.tocToList(Tree(root, listOf(deep, noId)), depth = 2))
    }

    @Test fun `SecInfo walk visits title only and preserves section information`() {
        val source = SecInfo(listOf(Inline.Str("a"), Inline.Emph(text("b"))), "a", "a", "a", 7)
        val seen = mutableListOf<String>()
        val walked = source.walk(object : PandocVisitor() {
            override fun visitInline(value: Inline) { if (value is Inline.Str) seen += value.text }
            override fun transformInline(value: Inline): Inline = if (value is Inline.Str) value.copy(text = value.text.uppercase()) else value
        })
        assertEquals(listOf("a", "b"), seen)
        assertEquals(source.copy(title = listOf(Inline.Str("A"), Inline.Emph(text("B")))), walked)
    }

    @TestFactory fun `path templates match actual original chunk paths`(): List<DynamicTest> =
        fixtures.getValue("paths").jsonArray.mapIndexed { i, value ->
            val c = value.jsonObject
            DynamicTest.dynamicTest("path $i") {
                assertEquals(c.getValue("expected").jsonPrimitive.content, PathTemplate(c.getValue("template").jsonPrimitive.content).resolve(
                    c.getValue("chunkNumber").jsonPrimitive.long, c.getValue("heading").jsonPrimitive.content,
                    c.getValue("identifier").jsonPrimitive.content, c.getValue("sectionNumber").jsonPrimitive.content))
            }
        }

    @Test fun `path number formatting follows signed Int printf including boundaries`() {
        val template = PathTemplate("%n")
        for ((number, expected) in listOf(0L to "000", -1L to "-01", -12L to "-12", -123L to "-123",
            Long.MIN_VALUE to "-9223372036854775808", Long.MAX_VALUE to "9223372036854775807")) {
            assertEquals(expected, template.resolve(number, "", "", ""))
        }
    }

    @Test fun `path JSON is strictly a string and preserves every template character`() {
        val value = "../%i/Привет 😀\\%n\u0000"
        assertEquals(JsonPrimitive(value), PathTemplate(value).toJson())
        assertEquals(PathTemplate(value), PathTemplate.fromJson(JsonPrimitive(value)))
        for (invalid in listOf(JsonNull, JsonPrimitive(1), JsonPrimitive(true), JsonArray(emptyList()), JsonObject(emptyMap()))) {
            assertFailsWith<IllegalArgumentException> { PathTemplate.fromJson(invalid) }
        }
    }
}
