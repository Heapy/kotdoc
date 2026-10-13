// Original Text.Emoji tests (John MacFarlane, BSD-3-Clause) and Pandoc Lua
// identifier examples (Albert Krewinkel, GPL-2.0-or-later); see licenses/.
package io.heapy.kotdoc.ast

import io.heapy.kotdoc.options.Extensions
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

class IdentifiersTest {
    companion object {
        private val fixtures by lazy {
            GZIPInputStream(checkNotNull(IdentifiersTest::class.java.getResourceAsStream("/structure/identifiers.json.gz"))).bufferedReader().use {
                Json.parseToJsonElement(it.readText()).jsonObject
            }
        }
    }
    private val variants = fixtures.getValue("variants").jsonArray.map { Extensions.read(it.jsonArray.map { it.jsonPrimitive.content }) }
    private fun text(s: String) = listOf(Inline.Str(s))

    @Test fun `all seven original emojis package assertions`() {
        assertEquals("💯", Emoji.emojiFromAlias("100"))
        assertEquals("😡", Emoji.emojiFromAlias("rage"))
        assertEquals("😡", Emoji.emojiFromAlias("pout"))
        assertNull(Emoji.emojiFromAlias("saotehusatnoeus"))
        assertNull(Emoji.aliasesFromEmoji("oo"))
        assertEquals(listOf("rage", "pout"), Emoji.aliasesFromEmoji("😡"))
        assertEquals("myα→ rage(😡) is 100(💯)", Emoji.replaceEmojis("myα→ 😡 is 💯") { t, a ->
            a.firstOrNull()?.let { "$it($t)" } ?: t
        })
    }

    @TestFactory fun `every alias matches original Pandoc emoji parsing`(): List<DynamicTest> =
        fixtures.getValue("aliases").jsonArray.map { value ->
            val c = value.jsonObject
            val alias = c.getValue("alias").jsonPrimitive.content
            DynamicTest.dynamicTest(alias) {
                val span = assertNotNull(Emoji.emojiToInline(alias))
                assertEquals(c.getValue("expected"), JsonArray(listOf(PandocJson.encodeBlock(Block.Para(listOf(span))))))
                assertEquals((span.content.single() as Inline.Str).text, Emoji.emojiFromAlias(alias))
                assertTrue(alias in assertNotNull(Emoji.aliasesFromEmoji((span.content.single() as Inline.Str).text)))
            }
        }

    @TestFactory fun `all emoji sequences preserve matching and first alias semantics`(): List<DynamicTest> =
        fixtures.getValue("emojis").jsonArray.map { value ->
            val c = value.jsonObject
            val s = c.getValue("text").jsonPrimitive.content
            DynamicTest.dynamicTest(s) {
                variants.forEachIndexed { i, exts ->
                    assertEquals(c.getValue("expected").jsonArray[i].jsonPrimitive.content,
                        Identifiers.uniqueIdent(text(s), extensions = exts), "${exts.names()} $s")
                }
            }
        }

    @TestFactory fun `all Unicode scalars match original filtering lowercasing and ASCII conversion`(): List<DynamicTest> =
        fixtures.getValue("unicode").jsonArray.flatMap { value ->
            val c = value.jsonObject
            val start = c.getValue("start").jsonPrimitive.int
            val end = c.getValue("end").jsonPrimitive.int
            val input = buildString { for (cp in start..<end) if (cp !in 0xd800..0xdfff) { append('a'); appendCodePoint(cp); append('z') } }
            variants.mapIndexed { i, exts -> DynamicTest.dynamicTest("U+${start.toString(16)}..${(end-1).toString(16)} ${exts.names()}") {
                val expected = c.getValue("expected").jsonArray[i].jsonPrimitive.content
                val actual = Identifiers.textToIdentifier(input, exts)
                // Keep failure reports legible even when a Unicode-version mismatch affects a large range.
                if (expected != actual) {
                    val at = expected.zip(actual).indexOfFirst { (a,b) -> a != b }.let { if (it < 0) minOf(expected.length, actual.length) else it }
                    fail("Mismatch at UTF-16 offset $at: expected ${expected.drop(at).take(32)}, actual ${actual.drop(at).take(32)}")
                }
            } } + listOf(DynamicTest.dynamicTest("case conversion U+${start.toString(16)}") {
                assertEquals(c.getValue("lower").jsonPrimitive.content, UnicodeText.toLower(input))
                assertEquals(c.getValue("upper").jsonPrimitive.content, UnicodeText.toUpper(input))
                // Original text case-conversion length and idempotence properties.
                for (convert in listOf(UnicodeText::toLower, UnicodeText::toUpper, UnicodeText::toCaseFold)) {
                    val result = convert(input)
                    assertEquals(result, convert(result))
                    assertTrue(result.codePointCount(0, result.length) >= input.codePointCount(0, input.length))
                }
            })
        }

    @TestFactory fun `Format equality agrees with original full case folding`(): List<DynamicTest> =
        fixtures.getValue("formatPairs").jsonArray.mapIndexed { i, value ->
            val c = value.jsonObject
            DynamicTest.dynamicTest("format fold $i") {
                val a = Format(c.getValue("a").jsonPrimitive.content)
                val b = Format(c.getValue("b").jsonPrimitive.content)
                val expected = c.getValue("expected").jsonPrimitive.boolean
                assertEquals(expected, a == b)
                assertEquals(expected, a.compareTo(b) == 0)
                if (expected) assertEquals(a.hashCode(), b.hashCode())
            }
        }

    @Test fun `original full case folding and uppercase exception baselines`() {
        val examples = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/structure/case-exceptions.json")).readText())
            .jsonObject.getValue("examples").jsonObject
        for ((name, convert) in listOf("fold" to UnicodeText::toCaseFold, "upper" to UnicodeText::toUpper)) {
            val c = examples.getValue(name).jsonObject
            assertEquals(c.getValue("expected").jsonPrimitive.content, convert(c.getValue("input").jsonPrimitive.content))
        }
        assertEquals("i\u0307", UnicodeText.toLower("İ"))
        for (cp in 0x13a0..0x13f7) assertEquals(cp.toChar().toString(), UnicodeText.toCaseFold(cp.toChar().toString()))
        for (cp in 0xab70..0xabbf) assertTrue(UnicodeText.toCaseFold(cp.toChar().toString()).single().code in 0x13a0..0x13f7)
        val ascii = (0..127).map(Int::toChar).joinToString("")
        assertEquals(ascii.lowercase(), UnicodeText.toLower(ascii))
        assertEquals(ascii.uppercase(), UnicodeText.toUpper(ascii))
        assertEquals(ascii.lowercase(), UnicodeText.toCaseFold(ascii))
        for (convert in listOf(UnicodeText::toLower, UnicodeText::toUpper, UnicodeText::toCaseFold)) assertEquals("", convert(""))
    }

    @TestFactory fun `original identifier and collision boundaries`(): List<DynamicTest> =
        fixtures.getValue("edges").jsonArray.map { value ->
            val c = value.jsonObject
            DynamicTest.dynamicTest(c.getValue("name").jsonPrimitive.content) {
                val inlines = (PandocJson.decode(c.getValue("document")).blocks.single() as Block.Plain).content
                val used = c.getValue("used").jsonArray.map { it.jsonPrimitive.content }.toMutableSet()
                c["usedRange"]?.jsonPrimitive?.int?.let { n -> used += "title"; for (i in 1..n) used += "title-$i" }
                variants.forEachIndexed { i, exts ->
                    assertEquals(c.getValue("expected").jsonArray[i].jsonPrimitive.content, Identifiers.uniqueIdent(inlines, used, exts), exts.names().toString())
                }
            }
        }

    @Test fun `all four original Lua unique identifier examples`() {
        assertEquals("this-is-nice", Identifiers.uniqueIdent(listOf(Inline.Emph(text("This")), Inline.Space) + text("is nice")))
        assertEquals("hello-world-1", Identifiers.uniqueIdent(text("Hello, World!"), setOf("hello-world")))
        assertEquals("mr.-jones", Identifiers.uniqueIdent(text("Mr. Jones")))
        assertEquals("mr-jones", Identifiers.uniqueIdent(text("Mr. Jones"), extensions = Extensions.read(listOf("gfm_auto_identifiers"))))
    }

    @Test fun `emoji replacement is greedy exact and does not rescan callback output`() {
        val consumed = mutableListOf<String>()
        assertEquals("XXX❤", Emoji.replaceEmojis("❤️‍🔥❤️😡❤") { s, _ -> consumed += s; "X" })
        assertEquals(listOf("❤️‍🔥", "❤️", "😡"), consumed)
        assertEquals("💯", Emoji.replaceEmojis("😡") { _, _ -> "💯" })
        assertEquals("❤", Emoji.replaceEmojis("❤") { _, _ -> "wrong" })
        assertEquals("[heart]‍x", Emoji.replaceEmojis("❤️‍x") { _, aliases -> "[${aliases.first()}]" })
        assertNull(Emoji.emojiToInline("RAGE"))
        assertNull(Emoji.aliasesFromEmoji("❤"))
    }

    @Test fun `emoji source order and tables cannot be mutated`() {
        assertEquals("grinning" to "😀", Emoji.aliases.first())
        assertEquals(1913, Emoji.aliases.size)
        assertEquals(2339, Emoji.baseEmojis.size)
        assertEquals(1614, Emoji.zwjEmojis.size)
        assertFailsWith<UnsupportedOperationException> { (Emoji.aliases as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (Emoji.emojis as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { (Emoji.baseEmojis as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (Emoji.zwjEmojis as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (Emoji.aliasesFromEmoji("😡") as MutableList).clear() }
    }

    @Test fun `ASCII conversion uses canonical decomposition and Turkish exception`() {
        assertEquals("Aei i I", Asciify.toAsciiText("Àéı ı İ"))
        assertEquals("", Asciify.toAsciiText("ÆœØŁﬃ😀"))
        assertEquals("ae", Asciify.toAsciiText("a\u0327\u0301e\u0308"))
        assertEquals("\u0000\t\r\n\u007f", Asciify.toAsciiText("\u0000\t\r\n\u007f"))
        for ((cp, expected) in listOf(0 to '\u0000', 127 to '\u007f', 'é'.code to 'e', 'À'.code to 'A',
            'İ'.code to 'I', 'ı'.code to 'i', 'Ǻ'.code to 'A', 'Æ'.code to null, 'ﬃ'.code to null,
            0x301 to null, 0x1f600 to null, 0x1d400 to null, 0xd800 to null)) {
            assertEquals(expected, Asciify.toAsciiChar(cp), "U+${cp.toString(16)}")
        }
    }
}
