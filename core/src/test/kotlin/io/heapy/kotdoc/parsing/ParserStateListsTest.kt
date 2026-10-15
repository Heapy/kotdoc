package io.heapy.kotdoc.parsing

import io.heapy.kotdoc.ast.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

/** Transport only. Expectations come from unchanged original Haskell modules. */
class ParserStateListsTest {
    // Every field outside the two original list counters has a non-default
    // sentinel. Original fixture expectations remain unmodified.
    private val seed = ParserState(
        options = io.heapy.kotdoc.options.ReaderOptions(columns = -17),
        parserContext = ParserContext.ListItemState, quoteContext = QuoteContext.InSingleQuote,
        allowLinks = false, allowLineBreaks = false, lastStrPos = SourcePos("last", -2, 0),
        keys = mapOf(Key("key") to (io.heapy.kotdoc.ast.Target("url") to Attr("attr"))),
        headerKeys = mapOf(Key("header") to (io.heapy.kotdoc.ast.Target("header") to Attr("header"))),
        substitutions = mapOf(Key("subst") to Builder.para(Builder.str("subst"))),
        notes = listOf("note" to "body"), parsedNotes = mapOf("parsed" to (SourcePos("note") to Future.pure(Builder.para(Builder.str("parsed"))))),
        noteRefs = setOf("ref"), inNote = true, noteNumber = -3,
        meta = mapOf("meta" to MetaValue.MetaString("value")), futureMeta = Future.asksValue { it.meta },
        citations = mapOf("citation" to "body"), headerTable = listOf(HeaderType.SingleHeader(0xd800)),
        identifiers = setOf("identifier"),
        macros = mapOf("macro" to io.heapy.kotdoc.tex.Macro(io.heapy.kotdoc.tex.MacroScope.GroupScope, io.heapy.kotdoc.tex.ExpansionPoint.ExpandWhenDefined, emptyList(), null, emptyList())),
        rstDefaultRole = "custom", rstHighlight = "kotlin", rstCustomRoles = mapOf("custom" to Triple("base", "html", Attr("role"))),
        caption = Builder.str("caption"), inHtmlBlock = "div", fencedDivLevel = -7,
        containers = listOf("a", "b"), logMessages = listOf(io.heapy.kotdoc.logging.LogMessage.SkippedContent("log", SourcePos("log"))),
        markdownAttribute = true,
    )
    private fun untouched(s: ParserState): List<Any?> = listOf(
        s.options, s.parserContext, s.quoteContext, s.allowLinks, s.allowLineBreaks, s.lastStrPos,
        s.keys, s.headerKeys, s.substitutions, s.notes, s.parsedNotes, s.noteRefs, s.inNote, s.noteNumber,
        s.meta, s.futureMeta, s.citations, s.headerTable, s.identifiers, s.macros, s.rstDefaultRole,
        s.rstHighlight, s.rstCustomRoles, s.caption, s.inHtmlBlock, s.fencedDivLevel, s.containers,
        s.logMessages, s.markdownAttribute,
    )
    private fun j(vararg xs: JsonElement) = JsonArray(xs.toList())
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.n() = jsonPrimitive.long
    private fun pair(value: Pair<ListNumberStyle,Long>) = j(JsonPrimitive(value.first.name),JsonPrimitive(value.second))
    private fun attributes(value: ListAttributes) = j(JsonPrimitive(value.start),JsonPrimitive(value.style.name),JsonPrimitive(value.delimiter.name))
    private fun core(v: JsonElement): Parsec<ParserState,JsonElement> {
        val a=v.jsonArray
        return when(a[0].s()) {
            "any" -> ListParsing.anyOrderedListMarker(ParserStateAccess).map(::attributes)
            "ordered" -> ListParsing.orderedListMarker(ListNumberStyle.valueOf(a[1].s()),ListNumberDelim.valueOf(a[2].s()),ParserStateAccess).map(::JsonPrimitive)
            "roman" -> ListParsing.romanNumeral<ParserState>(a[1].jsonPrimitive.boolean).map(::JsonPrimitive)
            "decimal" -> ListParsing.decimal<ParserState>().map(::pair)
            "lowerAlpha" -> ListParsing.lowerAlpha<ParserState>().map(::pair)
            "upperAlpha" -> ListParsing.upperAlpha<ParserState>().map(::pair)
            "lowerRoman" -> ListParsing.lowerRoman<ParserState>().map(::pair)
            "upperRoman" -> ListParsing.upperRoman<ParserState>().map(::pair)
            "eof" -> core(a[1]).before(Parsec.eof())
            "fallback" -> core(a[1]) or Parsec.pure(JsonPrimitive("fallback"))
            "many" -> core(a[1]).many().map(::JsonArray)
            "sequence" -> {
                val p=a[1].jsonArray.map(::core).foldRight(Parsec.zero<ParserState,JsonElement>()) { x,acc -> x or acc }
                p.flatMap { first -> PandocTextCharacters.char<ParserState>(','.code).then(p).many().map { JsonArray(listOf(first)+it) } }
            }
            else -> error(v)
        }
    }
    private fun position(p: SourcePos) = j(JsonPrimitive(p.name),JsonPrimitive(p.line),JsonPrimitive(p.column))
    private fun error(e: ParseError) = buildJsonObject {
        put("position",position(e.position));put("show",e.toString())
        put("messages",JsonArray(e.messages.sortedBy { it.kind }.map { j(JsonPrimitive(it.kind.ordinal),JsonPrimitive(it.text)) }))
    }
    private fun run(v: JsonElement): JsonElement {
        val o=v.jsonObject;val p=o.getValue("position").jsonArray
        val original=seed.copy(nextExample=o.getValue("next").n(),examples=o.getValue("examples").jsonArray.associate { it.jsonArray.let { a -> a[0].s() to a[1].n() } })
        val initial=ParseState(ParseInput(o.getValue("text").s()),SourcePos(p[0].s(),p[1].n(),p[2].n()),original)
        val reply=core(o.getValue("parser")).run(initial)
        return buildJsonObject {
            put("ok",reply.success);put("consumed",reply.consumed);put("error",error(reply.error))
            if(reply.success) {
                assertEquals(untouched(original),untouched(reply.state.user))
                put("value",checkNotNull(reply.value));put("position",position(reply.state.position))
                put("remaining",reply.state.input.text.substring(reply.state.input.index));put("next",reply.state.user.nextExample)
                put("examples",JsonArray(reply.state.user.examples.entries.sortedWith { a,b -> UnicodeTextOrder.compare(a.key,b.key) }
                    .map { j(JsonPrimitive(it.key),JsonPrimitive(it.value)) }))
                put("untouched","retained")
            }
        }
    }
    @TestFactory fun originalObservationsWithFullState(): List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/parsing/lists.jsonl.gz"))).bufferedReader().use { reader ->
            reader.lineSequence().drop(1).map { Json.parseToJsonElement(it).jsonObject }.filter { it.getValue("op").s() == "run" }.mapIndexed { index,c ->
                DynamicTest.dynamicTest("original list parser with complete state $index") {
                    assertEquals(c.getValue("expected"), run(c.getValue("input")), "case $index: ${c.getValue("input")}")
                }
            }.toList()
        }
}
