package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.highlighting.format.*
import io.heapy.kotdoc.markup.BlazeRenderer
import io.heapy.kotdoc.writers.HtmlWriterMarkup
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

class FormatterTest {
    private fun JsonElement.s()=jsonPrimitive.content
    private fun JsonElement.b()=jsonPrimitive.boolean
    private fun JsonElement.n()=jsonPrimitive.int
    private fun options(v: JsonElement): FormatOptions { val a=v.jsonArray; return FormatOptions(a[0].b(),a[1].jsonPrimitive.long,a[2].b(),a[3].b(),a[4].jsonArray.map { it.s() },a[5].jsonArray.map { it.s() },a[6].s(),ANSIColorLevel.valueOf(a[7].s())) }
    private fun color(v: JsonElement): HighlightColor? = if (v==JsonNull) null else v.jsonArray.let { HighlightColor(it[0].n().toUByte(),it[1].n().toUByte(),it[2].n().toUByte()) }
    private fun tokenStyle(v: JsonElement): TokenStyle { val a=v.jsonArray;return TokenStyle(color(a[0]),color(a[1]),a[2].b(),a[3].b(),a[4].b()) }
    private fun style(v: JsonElement): HighlightStyle { val a=v.jsonArray;return HighlightStyle(a[0].jsonArray.associate { it.jsonArray.let { TokenType.valueOf(it[0].s()) to tokenStyle(it[1]) } },color(a[1]),color(a[2]),color(a[3]),color(a[4])) }
    private fun lines(v: JsonElement)=v.jsonArray.map { line -> line.jsonArray.map { it.jsonArray.let { HighlightToken(TokenType.valueOf(it[0].s()),it[1].s()) } } }
    private fun encode(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Int -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        is UByte -> JsonPrimitive(v.toInt())
        is TokenType -> JsonPrimitive(v.name)
        is ANSIColorLevel -> JsonPrimitive(v.name)
        is HighlightColor -> encode(listOf(v.red,v.green,v.blue))
        is TokenStyle -> encode(listOf(v.color,v.background,v.bold,v.italic,v.underline))
        is HighlightStyle -> encode(listOf(v.tokenStyles,v.defaultColor,v.backgroundColor,v.lineNumberColor,v.lineNumberBackgroundColor))
        is FormatOptions -> encode(v.fields)
        is Map<*,*> -> JsonArray(v.entries.map { encode(listOf(it.key,it.value)) })
        is Iterable<*> -> JsonArray(v.map(::encode))
        else -> error(v)
    }
    private fun format(o: FormatOptions,s: HighlightStyle,ls: List<SourceLine>)=buildJsonObject {
        put("html-inline",HtmlFormat.formatInline(o,ls));put("html-block",HtmlFormat.formatBlock(o,ls));put("html4-block",HtmlFormat.formatHtml4Block(o,ls))
        put("latex-inline",LaTeXFormat.formatInline(o,ls));put("latex-block",LaTeXFormat.formatBlock(o,ls))
        put("context-inline",ConTeXtFormat.formatInline(o,ls));put("context-block",ConTeXtFormat.formatBlock(o,ls))
        put("typst-inline",TypstFormat.formatInline(o,ls));put("typst-block",TypstFormat.formatBlock(o,ls));put("ansi",AnsiFormat.format(o,s,ls))
    }
    private fun styleFormat(s: HighlightStyle)=buildJsonObject { put("css",HtmlFormat.styleToCss(s));put("latex",LaTeXFormat.styleToLaTeX(s));put("context",ConTeXtFormat.styleToConTeXt(s));put("typst",TypstFormat.styleToTypst(s)) }
    private fun builtin(name: String) = when (name) { "pygments"->HighlightStyles.pygments;"tango"->HighlightStyles.tango;"espresso"->HighlightStyles.espresso;"zenburn"->HighlightStyles.zenburn;"kate"->HighlightStyles.kate;"monochrome"->HighlightStyles.monochrome;"breezedark"->HighlightStyles.breezeDark;"haddock"->HighlightStyles.haddock;else->error(name) }
    @Suppress("UNCHECKED_CAST") private fun codec(kind: String): SyntaxBinaryCodec<Any> = when (kind) { "options"->HighlightBinary.formatOptions;"level"->HighlightBinary.ansiColorLevel;"color"->HighlightBinary.color;"token-style"->HighlightBinary.tokenStyle;"style"->HighlightBinary.style;else->error(kind) } as SyntaxBinaryCodec<Any>
    private fun value(kind: String,v: JsonElement): Any=when(kind) { "options"->options(v);"level"->ANSIColorLevel.valueOf(v.s());"color"->checkNotNull(color(v));"token-style"->tokenStyle(v);"style"->style(v);else->error(kind) }
    private fun observe(c: JsonObject): JsonElement {
        val input=c.getValue("input");val a=input as? JsonArray
        return when (c.getValue("op").s()) {
            "format"->format(options(a!![0]),style(a[1]),lines(a[2]))
            "ansi"->JsonPrimitive(AnsiFormat.format(options(a!![0]),style(a[1]),lines(a[2])))
            "style"->styleFormat(style(input))
            "builtin"->builtin(input.s()).let { JsonArray(listOf(encode(it),styleFormat(it))) }
            "opts"->options(input).let { v -> buildJsonObject { put("value",encode(v));put("show",v.toString());put("encoded",HighlightBinary.formatOptions.encode(v).toHexString()) } }
            "opts-order"->{ val left=options(a!![0]);val right=options(a[1]);val n=left.compareTo(right);buildJsonObject { put("order",if(n<0) "LT" else if(n>0) "GT" else "EQ");put("equal",left==right) } }
            "encode"->JsonPrimitive(codec(a!![0].s()).encode(value(a[0].s(),a[1])).toHexString())
            "decode"->{ val co=codec(a!![0].s());val result=co.decode(a[1].s().hexToByteArray());buildJsonObject {
                put("consumed",result.consumed);put("remaining",result.remaining.toHexString())
                when(result) {
                    is SyntaxBinaryResult.Failure->put("error",result.error)
                    is SyntaxBinaryResult.Success->{put("value",encode(result.value));put("show",result.value.toString());put("encoded",co.encode(result.value).toHexString())}
                }
            } }
            else->error(c)
        }
    }
    @TestFactory fun originalFormatters(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/highlighting/formatters.jsonl.gz"))).bufferedReader().use { reader ->
        val manifest=Json.parseToJsonElement(checkNotNull(reader.readLine())).jsonObject
        val rows=reader.readLines();assertEquals(manifest.getValue("cases").jsonPrimitive.int,rows.size)
        rows.mapIndexed { index,row ->
            // JUnit retains dynamic-test callbacks for the entire upstream suite.
            // Parse the large complete-output trees only when each test executes.
            val op=row.substringAfter("\"op\":\"").substringBefore('"')
            DynamicTest.dynamicTest("$index $op") {
                val c=Json.parseToJsonElement(row).jsonObject
                assertEquals(c.getValue("expected"),observe(c),"case $index: $row")
                if (c.getValue("op").s() == "format") {
                    val input = c.getValue("input").jsonArray
                    val o = options(input[0]); val source = lines(input[2])
                    val expected = c.getValue("expected").jsonObject
                    assertEquals(expected.getValue("html-inline").s(), BlazeRenderer.renderText(HtmlWriterMarkup.inline(o, source)))
                    assertEquals(expected.getValue("html-block").s(), BlazeRenderer.renderText(HtmlWriterMarkup.block(o, source, true)))
                    assertEquals(expected.getValue("html4-block").s(), BlazeRenderer.renderText(HtmlWriterMarkup.block(o, source, false)))
                }
            }
        }
    }
    @Test fun immutableOptionsAndDecodedMaps() {
        val code=mutableListOf("x");val container=mutableListOf("y")
        val o=FormatOptions(codeClasses=code,containerClasses=container);code.clear();container.clear()
        assertEquals(listOf("x"),o.codeClasses);assertEquals(listOf("y"),o.containerClasses)
        assertFailsWith<UnsupportedOperationException> { (o.codeClasses as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (o.containerClasses as MutableList).clear() }
        val sty=HighlightStyle(mapOf(TokenType.KeywordTok to TokenStyle(bold=true)))
        val bytes=HighlightBinary.style.encode(sty)
        val result=assertIs<SyntaxBinaryResult.Success<HighlightStyle>>(HighlightBinary.style.decode(bytes))
        bytes.fill(0)
        assertEquals(sty,result.value)
        assertFailsWith<UnsupportedOperationException> { (result.value.tokenStyles as MutableMap).clear() }
    }
}
