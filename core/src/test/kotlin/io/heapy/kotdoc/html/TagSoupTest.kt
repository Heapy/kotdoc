// Original TagSoup tests: Copyright (c) 2006–2019 Neil Mitchell, BSD-3-Clause.
package io.heapy.kotdoc.html

import io.heapy.kotdoc.html.HtmlTag.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.random.Random
import kotlin.test.*

class TagSoupTest {
    companion object {
        private val fixtures = Json.parseToJsonElement(checkNotNull(
            TagSoupTest::class.java.getResource("/html/tagsoup.json")).readText()).jsonObject
    }
    private fun decode(value: JsonElement): HtmlTag {
        val a = value.jsonArray
        fun s(i: Int) = a[i].jsonPrimitive.content
        return when (s(0)) {
            "TagOpen" -> Open(s(1), a[2].jsonArray.map { it.jsonArray[0].jsonPrimitive.content to it.jsonArray[1].jsonPrimitive.content })
            "TagClose" -> Close(s(1))
            "TagText" -> Text(s(1))
            "TagComment" -> Comment(s(1))
            "TagWarning" -> Warning(s(1))
            "TagPosition" -> Position(s(1).toLong(), s(2).toLong())
            else -> error("Unknown fixture tag: $a")
        }
    }
    @TestFactory fun `all original parser and position examples`() = fixtures.getValue("parse").jsonArray.mapIndexed { i, value ->
        val c = value.jsonObject
        DynamicTest.dynamicTest("original parse $i ${c.getValue("input")}") {
            assertEquals(c.getValue("expected").jsonArray.map(::decode), TagSoup.parseTags(
                c.getValue("input").jsonPrimitive.content,
                HtmlParseOptions(tagPosition = c.getValue("positions").jsonPrimitive.boolean)))
        }
    }
    @TestFactory fun `all original entity examples`() = fixtures.getValue("entities").jsonArray.map { value ->
        val c = value.jsonObject
        val name = c.getValue("function").jsonPrimitive.content
        val input = c.getValue("input").jsonPrimitive.content
        DynamicTest.dynamicTest("$name $input") {
            val actual = when (name) {
                "lookupNumericEntity" -> HtmlEntities.lookupNumericEntity(input)
                "lookupNamedEntity" -> HtmlEntities.lookupNamedEntity(input)
                "escapeXML" -> HtmlEntities.escapeXml(input)
                else -> error(name)
            }
            assertEquals(c.getValue("expected").jsonPrimitive.contentOrNull, actual)
        }
    }
    @TestFactory fun `complete independent HTML5 entity table`() = fixtures.getValue("html5").jsonArray.map { value ->
        val name = value.jsonArray[0].jsonPrimitive.content
        val expected = value.jsonArray[1].jsonPrimitive.content
        DynamicTest.dynamicTest("entity $name") {
            assertEquals(expected, HtmlEntities.lookupNamedEntity(name))
            assertEquals(expected, HtmlEntities.lookupEntity(name))
            assertEquals(listOf(Text(expected)), TagSoup.parseTags("&$name"))
            assertEquals(listOf(Open("a", listOf("id" to expected))), TagSoup.parseTags("<a id='&$name'>"))
        }
    }
    @TestFactory fun `raw HTML identifier discovery matches original Pandoc`(): List<DynamicTest> {
        val corpus = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/html/identifiers.json")).readText()).jsonObject
        return corpus.getValue("cases").jsonArray.map { value ->
            val c = value.jsonObject
            DynamicTest.dynamicTest(c.getValue("name").jsonPrimitive.content) {
                val ids = TagSoup.parseTextTags(c.getValue("html").jsonPrimitive.content)
                    .filterIsInstance<Open>().map { it.attribute("id") }.filter { it.isNotEmpty() }.toSet()
                val candidates = c.getValue("candidates").jsonArray.map { it.jsonPrimitive.content }
                assertEquals(c.getValue("found").jsonArray.map { it.jsonPrimitive.content }, candidates.filter { it in ids })
            }
        }
    }
    @Test fun `original warning position example`() {
        val tags = TagSoup.parseTags("neil &foo bar", HtmlParseOptions(tagPosition = true, tagWarning = true))
        val warnings = tags.zipWithNext().mapNotNull { (a, b) ->
            if (a is Warning && b is Position) a.message to b.column else null
        }
        assertEquals(listOf("Unknown entity: foo" to 6L), warnings)
    }
    @TestFactory fun `original option properties with reproducible generated HTML`(): List<DynamicTest> {
        val rng = Random(0x544147)
        val fragments = " \n!-</>#&;xy01[]?'\"".map { it.toString() } + listOf("CDATA", "amp", "gt", "lt")
        return (0..<1000).map { n ->
            val input = (0..<rng.nextInt(101)).joinToString("") { fragments.random(rng) }
            DynamicTest.dynamicTest("original option property $n") {
                for (positions in listOf(false, true)) for (warnings in listOf(false, true)) for (merge in listOf(false, true)) {
                    val tags = TagSoup.parseTags(input, HtmlParseOptions(positions, warnings, merge))
                    if (!warnings) assertFalse(tags.any { it is Warning })
                    if (!positions) assertFalse(tags.any { it is Position })
                    else {
                        assertEquals(0, tags.size % 2, "$input: $tags")
                        var previous = HtmlPosition()
                        for ((p, tag) in tags.chunked(2)) {
                            assertIs<Position>(p, "$input: $tags")
                            assertFalse(tag is Position)
                            val current = HtmlPosition(p.row, p.column)
                            assertTrue(previous <= current, "$input: $tags")
                            previous = current
                        }
                    }
                    if (merge) {
                        var allowText = true
                        for (tag in tags) when (tag) {
                            is Text -> { assertTrue(allowText, "$input: $tags"); allowText = false }
                            is Position, is Warning -> Unit
                            else -> allowText = true
                        }
                    }
                }
            }
        }
    }
    @Test fun `numeric bounds ASCII digits and surrogate representations`() {
        for (cp in listOf(0, 9, 13, 0x80, 0xd7ff, 0xd800, 0xdfff, 0xe000, 0xffff, 0x10000, 0x10ffff)) {
            val string = String(Character.toChars(cp))
            assertEquals(string, HtmlEntities.lookupEntity("#$cp"))
            assertEquals(string, HtmlEntities.lookupNumericEntity("x${cp.toString(16)}"))
            assertEquals(listOf(Text(string)), TagSoup.parseTags("&#$cp;"))
            assertEquals(listOf(Text(if (cp in 0xd800..0xdfff) "�" else string)), TagSoup.parseTextTags("&#$cp;"))
        }
        assertEquals(listOf(Text("��")), TagSoup.parseTextTags("&#xD800;&#xDC00;"))
        assertEquals(listOf(Open("a", listOf("id" to "��"))), TagSoup.parseTextTags("<a id='&#xD800;&#xDC00;'>"))
        assertEquals("A", HtmlEntities.lookupNumericEntity("0".repeat(10000) + "65"))
        for (invalid in listOf("-1", "+65", " 65", "65 ", "65;", "#65", "x", "X", "0x41", "١", "１", "xＦ", "1114112", "9".repeat(10000))) {
            assertNull(HtmlEntities.lookupNumericEntity(invalid), invalid)
        }
        assertEquals(listOf(Text("?")), TagSoup.parseTags("&#1114112;"))
        assertEquals(listOf(Text("&#x; &#١;")), TagSoup.parseTags("&#x; &#١;"))
    }
    @Test fun `references consume full names without browser prefix decoding or C1 remapping`() {
        assertEquals(listOf(Text("&notit; &ampx; &AMPX; &amp:foo; &amp-bar; &amp_0;")),
            TagSoup.parseTags("&notit; &ampx; &AMPX; &amp:foo; &amp-bar; &amp_0;"))
        assertEquals(listOf(Text("\u0080\u0000")), TagSoup.parseTags("&#128;&#0;"))
        assertEquals(listOf(Text("&AMP; &amp;")), TagSoup.parseTags("&amp;AMP; &amp;amp;"))
    }
    @Test fun `case ordered duplicates unquoted slash and declaration attributes`() {
        val tag = TagSoup.parseTags("<A ID='upper' id='' id='second' a=b/>").single() as Open
        assertEquals("A", tag.name)
        assertEquals(listOf("ID" to "upper", "id" to "", "id" to "second", "a" to "b/"), tag.attributes)
        assertEquals("", tag.attribute("id"))
        assertEquals("upper", tag.attribute("ID"))
        assertEquals("", tag.attribute("missing"))
        assertEquals(listOf(Open("script", listOf("" to "foo")), Text("x")), TagSoup.parseTags("<script 'foo'>x"))
        assertEquals(listOf(Open("!D", listOf("a" to "", "" to "value"))), TagSoup.parseTags("<!D a 'value'>"))
        assertEquals(listOf(Close("!x"), Close("?y")), TagSoup.parseTags("</!x></?y>"))
        assertEquals(listOf(Text("</")), TagSoup.parseTags("</"))
    }
    @Test fun `script matching CDATA and literal line endings`() {
        assertEquals(listOf(Open("script"), Text("&amp; <a id='hidden'> </scriptx> "), Close("sCrIpT"), Open("b")),
            TagSoup.parseTags("<script>&amp; <a id='hidden'> </scriptx> </sCrIpT><b>"))
        assertEquals(listOf(Open("style"), Text("&"), Open("b"), Close("style")), TagSoup.parseTags("<style>&amp;<b></style>"))
        assertEquals(listOf(Text("<&amp;\r\n")), TagSoup.parseTags("<![CDATA[<&amp;]]>\r\n"))
        assertEquals(HtmlPosition(2, 10), HtmlPosition().advance("😀\t\r\n\tX"))
        assertEquals(listOf(Position(1, 1), Text("😀\t"), Position(1, 9), Open("a")),
            TagSoup.parseTags("😀\t<a>", HtmlParseOptions(tagPosition = true)))
    }
    @Test fun `comment end states preserve original recovery rules`() {
        val inputs = listOf("<!-->" to "", "<!--->" to "", "<!--x-" to "x", "<!--x--" to "x",
            "<!--x--!>" to "x", "<!--x-- >" to "x-- ", "<!--x--!y-->" to "x--!y",
            "<!--x---y-->" to "x---y", "<!?oops>" to "?oops", "</1oops>" to "1oops")
        for ((input, expected) in inputs) assertEquals(listOf(Comment(expected)), TagSoup.parseTags(input), input)
    }
    @Test fun `entity hooks fast mode and delayed warnings`() {
        assertEquals(listOf(Text("a"), Text("&"), Text("b")), TagSoup.parseTags("a&amp;b", HtmlParseOptions.Fast))
        assertEquals(listOf(Text("&amp;")), TagSoup.parseTags("&amp;", HtmlParseOptions.entities { null }))
        assertEquals(listOf(Text("custom")), TagSoup.parseTags("&foo;", HtmlParseOptions.entities { if (it == "foo;") "custom" else null }))
        val options = HtmlParseOptions(tagWarning = true,
            entityData = { n, s -> listOf(Text("$n/$s"), Warning("callback"), Open("x"), Text("tail")) },
            entityAttribute = { n, s -> "$n/$s" to listOf(Warning("attribute callback"), Open("extra")) })
        assertEquals(listOf(Text("af/true"), Warning("callback"), Open("x"), Text("tailb")), TagSoup.parseTags("a&f;b", options))
        assertEquals(listOf(Open("a", listOf("v" to "f/true")), Warning("attribute callback"), Open("extra")),
            TagSoup.parseTags("<a v='&f;'>", options))
        // Upstream filters all attribute callback tags when warnings are disabled.
        assertEquals(listOf(Open("a", listOf("v" to "f/true"))), TagSoup.parseTags("<a v='&f;'>", options.copy(tagWarning = false)))
        assertEquals(listOf(Text("af/true"), Open("x"), Text("tailb")), TagSoup.parseTags("a&f;b", options.copy(tagWarning = false)))
        assertEquals(listOf(Text("a&unknown;b"), Warning("Unknown entity: unknown")), TagSoup.parseTags("a&unknown;b", HtmlParseOptions(tagWarning = true)))
        assertEquals(listOf(Close("a"), Warning("Unexpected attributes in close tag"), Warning("Unexpected self-closing in close tag")),
            TagSoup.parseTags("</a b/>", HtmlParseOptions(tagWarning = true)))
    }
    @Test fun `long finite input does not recurse on characters or attributes`() {
        val text = "abc😀".repeat(25000)
        assertEquals(listOf(Text(text)), TagSoup.parseTags(text))
        assertEquals(listOf(Open("a", List(20000) { "x" to "y" })), TagSoup.parseTags("<a " + "x=y ".repeat(20000) + ">"))
    }
    @Test fun `entity inventories and XML escaping`() {
        assertEquals(2231, HtmlEntities.htmlEntities.size)
        assertEquals(listOf("quot" to "\"", "amp" to "&", "lt" to "<", "gt" to ">"), HtmlEntities.xmlEntities)
        assertEquals("&quot;&amp;&lt;&gt;&#39;😀", HtmlEntities.escapeXml("\"&<>'😀"))
        assertFailsWith<UnsupportedOperationException> { (HtmlEntities.htmlEntities as MutableList).clear() }
    }
}
