// Kotlin translation of pandoc-types/test/test-pandoc-types.hs JSON examples.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

import io.heapy.kotdoc.ast.Block.*
import io.heapy.kotdoc.ast.Inline.*
import io.heapy.kotdoc.ast.MetaValue.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

class UpstreamJsonTest {
    private val attr = Attr("id", listOf("kls"), listOf("k1" to "v1", "k2" to "v2"))
    private val hello = listOf(Str("Hello"))
    private fun p(text: String) = listOf(Para(listOf(Str(text))))

    /** Expected Kotlin objects are translated from Haskell, never decoded from the golden JSON. */
    private val expected: Map<String, Any> = mapOf(
        "t_meta" to mapOf("foo" to MetaBool(true)),
        "t_metamap" to MetaMap(mapOf("foo" to MetaBool(true))),
        "t_metalist" to MetaList(listOf(MetaBool(true), MetaString("baz"))),
        "t_metabool" to MetaBool(false),
        "t_metastring" to MetaString("Hello"),
        "t_metainlines" to MetaInlines(listOf(Space, SoftBreak)),
        "t_metablocks" to MetaBlocks(listOf(HorizontalRule, HorizontalRule)),
        "t_singlequote" to QuoteType.SingleQuote,
        "t_doublequote" to QuoteType.DoubleQuote,
        "t_authorintext" to CitationMode.AuthorInText,
        "t_suppressauthor" to CitationMode.SuppressAuthor,
        "t_normalcitation" to CitationMode.NormalCitation,
        "t_citation" to Citation("jameson:unconscious", listOf(Str("cf")), listOf(Space, Str("123"))),
        "t_displaymath" to MathType.DisplayMath,
        "t_inlinemath" to MathType.InlineMath,
        "t_str" to Str("Hello"),
        "t_emph" to Emph(hello),
        "t_underline" to Underline(hello),
        "t_strong" to Strong(hello),
        "t_strikeout" to Strikeout(hello),
        "t_superscript" to Superscript(hello),
        "t_subscript" to Subscript(hello),
        "t_smallcaps" to SmallCaps(hello),
        "t_quoted" to Quoted(QuoteType.SingleQuote, hello),
        "t_cite" to Cite(
            listOf(Citation("jameson:unconscious", listOf(Str("cf")), listOf(Space, Str("12")))),
            listOf(Str("[cf"), Space, Str("@jameson:unconscious"), Space, Str("12]")),
        ),
        "t_code" to Code(Attr(keyValues = listOf("language" to "haskell")), "foo bar"),
        "t_space" to Space,
        "t_softbreak" to SoftBreak,
        "t_linebreak" to LineBreak,
        "t_rawinline" to RawInline("tex", "\\foo{bar}"),
        "t_link" to Link(attr, listOf(Str("a"), Space, Str("famous"), Space, Str("site")), Target("https://www.google.com", "google")),
        "t_image" to Image(attr, listOf(Str("a"), Space, Str("famous"), Space, Str("image")), Target("my_img.png", "image")),
        "t_note" to Note(p("Hello")),
        "t_span" to Span(attr, hello),
        "t_plain" to Plain(hello),
        "t_para" to Para(hello),
        "t_lineblock" to LineBlock(listOf(hello, listOf(Str("Moin")))),
        "t_codeblock" to CodeBlock(attr, "Foo Bar"),
        "t_rawblock" to RawBlock("tex", "\\foo{bar}"),
        "t_blockquote" to BlockQuote(p("Hello")),
        "t_orderedlist" to OrderedList(ListAttributes(1, ListNumberStyle.Decimal, ListNumberDelim.Period), listOf(p("foo"), p("bar"))),
        "t_bulletlist" to BulletList(listOf(p("foo"), p("bar"))),
        "t_definitionlist" to DefinitionList(listOf(listOf(Str("foo")) to listOf(p("bar")), listOf(Str("fizz")) to listOf(p("pop")))),
        "t_header" to Header(2, attr, listOf(Str("Head"))),
        "t_row" to Row(attr, listOf(Cell(Attr(), Alignment.AlignRight, 2, 3, p("bar")))),
        "t_caption" to Caption(listOf(Str("foo")), p("bar")),
        "t_tablehead" to TableHead(attr, listOf(Row(attr, emptyList()))),
        "t_tablebody" to TableBody(attr, 3, listOf(Row(attr, emptyList())), listOf(Row(Attr("id'", listOf("kls'"), attr.keyValues), emptyList()))),
        "t_tablefoot" to TableFoot(attr, listOf(Row(attr, emptyList()))),
        "t_cell" to Cell(attr, Alignment.AlignLeft, 1, 1, p("bar")),
        "t_rowheadcolumns" to 1L,
        "t_rowspan" to 1L,
        "t_colspan" to 1L,
        "t_table" to upstreamTable(),
        "t_figure" to Figure(attr, Caption(listOf(Str("hello")), p("cap content")), p("fig content")),
        "t_div" to Div(attr, p("Hello")),
    )

    private fun upstreamTable(): Table {
        val cellAttr = Attr("a", listOf("b"), listOf("c" to "d", "e" to "f"))
        fun cell(text: String, alternate: Boolean = false) = Cell(if (alternate) attr else cellAttr, Alignment.AlignDefault, 1, 1, listOf(Plain(listOf(Str(text)))))
        fun row(vararg texts: String) = Row(attr, texts.map { cell(it) })
        fun sectionAttr(suffix: String) = Attr("id$suffix", listOf("kls$suffix"), listOf("k1$suffix" to "v1$suffix", "k2$suffix" to "v2$suffix"))
        return Table(
            attr,
            Caption(listOf(Str("short")), listOf(Para(listOf(Str("Demonstration"), Space, Str("of"), Space, Str("simple"), Space, Str("table"), Space, Str("syntax."))))),
            listOf(Alignment.AlignDefault, Alignment.AlignRight, Alignment.AlignLeft, Alignment.AlignCenter, Alignment.AlignDefault).map { ColSpec(it, ColWidth.Default) },
            TableHead(sectionAttr("h"), listOf(row("Head", "Right", "Left", "Center", "Default"))),
            listOf(TableBody(sectionAttr("b"), 1,
                listOf(row("ihead12", "i12", "i12", "i12", "i12")),
                listOf(Row(attr, listOf(cell("head12"), cell("12", true), cell("12"), cell("12", true), cell("12"))),
                    row("head123", "123", "123", "123", "123"), row("head1", "1", "1", "1", "1")))),
            TableFoot(sectionAttr("f"), listOf(row("foot", "footright", "footleft", "footcenter", "footdefault"))),
        )
    }

    private fun fixtures(): JsonArray = Json.parseToJsonElement(
        checkNotNull(javaClass.getResource("/pandoc-types/json-fixtures.json")).readText()
    ).jsonArray

    @Test
    fun `every imported upstream fixture has a translated expectation`() {
        assertEquals(56, fixtures().size)
        assertEquals(fixtures().map { it.jsonObject.getValue("name").jsonPrimitive.content }.toSet(), expected.keys)
    }

    @TestFactory
    fun `upstream exact JSON encoding and typed decoding`(): List<DynamicTest> = fixtures().flatMap { entry ->
        val fixture = entry.jsonObject
        val name = fixture.getValue("name").jsonPrimitive.content
        val type = fixture.getValue("type").jsonPrimitive.content
        val raw = fixture.getValue("json").jsonPrimitive.content
        val json = Json.parseToJsonElement(raw)
        val value = expected.getValue(name)
        listOf(
            DynamicTest.dynamicTest("$name encoding") { assertEquals(raw, encode(type, value).toString()) },
            DynamicTest.dynamicTest("$name decoding") { assertEquals(value, decode(type, json)) },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun encode(type: String, value: Any): JsonElement = with(PandocJson) {
        when (type) {
            "Meta" -> encodeMeta(value as Map<String, MetaValue>)
            "MetaValue" -> encodeMetaValue(value as MetaValue)
            "QuoteType", "CitationMode", "MathType" -> encodeEnum(value as Enum<*>)
            "Citation" -> encodeCitation(value as Citation)
            "Inline" -> encodeInline(value as Inline)
            "Block" -> encodeBlock(value as Block)
            "Row" -> encodeRow(value as Row)
            "Caption" -> encodeCaption(value as Caption)
            "TableHead" -> encodeTableHead(value as TableHead)
            "TableBody" -> encodeTableBody(value as TableBody)
            "TableFoot" -> encodeTableFoot(value as TableFoot)
            "Cell" -> encodeCell(value as Cell)
            // Haskell newtypes are represented as Long properties in Kotlin; exercise their real field codecs.
            "RowHeadColumns" -> encodeTableBody(TableBody(Attr(), value as Long, emptyList(), emptyList())).jsonArray[1]
            "RowSpan" -> encodeCell(Cell(Attr(), Alignment.AlignDefault, value as Long, 1, emptyList())).jsonArray[2]
            "ColSpan" -> encodeCell(Cell(Attr(), Alignment.AlignDefault, 1, value as Long, emptyList())).jsonArray[3]
            else -> error("Unported fixture type $type")
        }
    }

    private fun decode(type: String, value: JsonElement): Any = with(PandocJson) {
        when (type) {
            "Meta" -> decodeMeta(value)
            "MetaValue" -> decodeMetaValue(value)
            "QuoteType" -> decodeEnum<QuoteType>(value)
            "CitationMode" -> decodeEnum<CitationMode>(value)
            "MathType" -> decodeEnum<MathType>(value)
            "Citation" -> decodeCitation(value)
            "Inline" -> decodeInline(value)
            "Block" -> decodeBlock(value)
            "Row" -> decodeRow(value)
            "Caption" -> decodeCaption(value)
            "TableHead" -> decodeTableHead(value)
            "TableBody" -> decodeTableBody(value)
            "TableFoot" -> decodeTableFoot(value)
            "Cell" -> decodeCell(value)
            "RowHeadColumns" -> decodeTableBody(Json.parseToJsonElement("[[\"\",[],[]],$value,[],[]]")).rowHeadColumns
            "RowSpan" -> decodeCell(Json.parseToJsonElement("[[\"\",[],[]],{\"t\":\"AlignDefault\"},$value,1,[]]")).rowSpan
            "ColSpan" -> decodeCell(Json.parseToJsonElement("[[\"\",[],[]],{\"t\":\"AlignDefault\"},1,$value,[]]")).colSpan
            else -> error("Unported fixture type $type")
        }
    }
}
