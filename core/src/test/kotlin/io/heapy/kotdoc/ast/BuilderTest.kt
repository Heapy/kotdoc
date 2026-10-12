// Ported examples/properties from pandoc-types Builder.hs and test/test-pandoc-types.hs.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

import io.heapy.kotdoc.ast.Inline.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.random.Random
import kotlin.test.*

class BuilderTest {
    @Test
    fun `text builder separates only the four upstream whitespace characters`() {
        assertEquals(Inlines(), Builder.text(""))
        assertEquals(Inlines(listOf(Space, Str("Привет"), SoftBreak, Str("мир"), Space)), Builder.text(" \tПривет\r\n\t мир "))
        assertEquals(Inlines(listOf(Str("a\u00a0b\u2003c\u000bd\u000ce😀"))), Builder.text("a\u00a0b\u2003c\u000bd\u000ce😀"))
        assertEquals(Inlines(listOf(Str("a b\n"))), Builder.str("a b\n"))
    }

    @TestFactory
    fun `upstream inline concatenation rules`(): List<DynamicTest> {
        val a = listOf(Str("a"))
        val b = listOf(Str("b"))
        val ab = a + b
        val cases = listOf(
            Triple(Space, Space, Space), Triple(Space, SoftBreak, SoftBreak), Triple(SoftBreak, Space, SoftBreak),
            Triple(SoftBreak, SoftBreak, SoftBreak), Triple(Space, LineBreak, LineBreak), Triple(LineBreak, Space, LineBreak),
            Triple(SoftBreak, LineBreak, LineBreak), Triple(LineBreak, SoftBreak, LineBreak),
            Triple(Str("a"), Str("b"), Str("ab")), Triple(Emph(a), Emph(b), Emph(ab)),
            Triple(Underline(a), Underline(b), Underline(ab)), Triple(Strong(a), Strong(b), Strong(ab)),
            Triple(Subscript(a), Subscript(b), Subscript(ab)), Triple(Superscript(a), Superscript(b), Superscript(ab)),
            Triple(Strikeout(a), Strikeout(b), Strikeout(ab)),
        )
        return cases.mapIndexed { i, (left, right, expected) -> DynamicTest.dynamicTest("rule $i: $left + $right") {
            assertEquals(Inlines(listOf(Code(Attr(), "before"), expected, Code(Attr(), "after"))),
                Inlines(listOf(Code(Attr(), "before"), left)) + Inlines(listOf(right, Code(Attr(), "after"))))
        } }
    }

    @Test
    fun `concatenation preserves nonmergeable boundaries and unnormalized interiors`() {
        for (value in listOf(SmallCaps(listOf(Str("a"))), LineBreak, Code(Attr(), "a"))) {
            assertEquals(Inlines(listOf(value, value)), Inlines(listOf(value)) + Inlines(listOf(value)))
        }
        val original = Inlines(listOf(Str("a"), Str("b")))
        assertEquals(original, original + Inlines())
        assertEquals(original, Inlines() + original)
        assertEquals(Inlines(listOf(Str("a"), Str("bc"), Str("d"))), original + Inlines(listOf(Str("c"), Str("d"))))
    }

    @Test
    fun `trim removes spaces and soft breaks but preserves hard breaks and empty strings`() {
        assertEquals(Inlines(listOf(LineBreak, Space, Str(""))), Inlines(listOf(Space, SoftBreak, LineBreak, Space, Str(""), SoftBreak)).trim())
        assertEquals(Inlines(), Inlines(listOf(Space, SoftBreak, Space)).trim())
    }

    @Test
    fun `plain drops empty content while para retains it`() {
        assertEquals(Blocks(), Builder.plain(Inlines()))
        assertEquals(Blocks(listOf(Block.Para(emptyList()))), Builder.para(Inlines()))
        assertEquals(Blocks(listOf(Block.Plain(listOf(Str(""))))), Builder.plain(Builder.str("")))
    }

    @Test
    fun `upstream figure representation property`() {
        val random = Random(0x464947)
        repeat(500) {
            val caption = List(random.nextInt(10)) {
                listOf<Inline>(Str(""), Str("caption"), Emph(listOf(Str("x"))), Space, SoftBreak).random(random)
            }
            val attr = Attr("test")
            val expected = Blocks(listOf(Block.Figure(Attr(), Caption(null,
                if (caption.isEmpty()) emptyList() else listOf(Block.Plain(caption))),
                listOf(Block.Plain(listOf(Image(attr, emptyList(), Target("url", "title"))))))))
            assertEquals(expected, Builder.simpleFigureWith(attr, Inlines(caption), "url", "title"))
        }
    }

    @Test
    fun `builder document example composes formatted text and a list`() {
        val result = Builder.doc(Builder.para(Builder.text("Hello ") + Builder.emph(Builder.text("world")) + Builder.text("!")) +
            Builder.bulletList(listOf(Builder.plain(Builder.text("one")), Builder.plain(Builder.text("two")))))
        assertEquals(Pandoc(blocks = listOf(
            Block.Para(listOf(Str("Hello"), Space, Emph(listOf(Str("world"))), Str("!"))),
            Block.BulletList(listOf(listOf(Block.Plain(listOf(Str("one")))), listOf(Block.Plain(listOf(Str("two")))))),
        )), result)
    }

    @Test
    fun `metadata converters compose across maps and lists`() {
        assertEquals(MetaValue.MetaMap(mapOf("tags" to MetaValue.MetaList(listOf(MetaValue.MetaString("a"), MetaValue.MetaString("b"))))),
            mapOf("tags" to listOf("a", "b")).toMetaValue { it.toMetaValue(String::toMetaValue) })
        assertEquals(MetaValue.MetaBool(true), true.toMetaValue())
        assertEquals(MetaValue.MetaInlines(listOf(Str("title"))), Builder.str("title").toMetaValue())
        assertEquals(MetaValue.MetaBlocks(listOf(Block.Para(emptyList()))), Builder.para(Inlines()).toMetaValue())
    }
}
