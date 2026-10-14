package io.heapy.kotdoc.parsing

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.writers.WriterShared
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Transport only. Expectations come from unchanged original Haskell modules. */
class ListParsingTest {
    private data class User(val next: Long, val examples: Map<String, Long>, val untouched: Any)
    private object Access : ExampleNumberState<User> {
        override fun nextExample(state: User) = state.next
        override fun examples(state: User) = state.examples
        override fun update(state: User,nextExample: Long,examples: Map<String,Long>) = state.copy(next=nextExample,examples=examples)
    }
    private fun j(vararg xs: JsonElement) = JsonArray(xs.toList())
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.n() = jsonPrimitive.long
    private fun pair(value: Pair<ListNumberStyle,Long>) = j(JsonPrimitive(value.first.name),JsonPrimitive(value.second))
    private fun attributes(value: ListAttributes) = j(JsonPrimitive(value.start),JsonPrimitive(value.style.name),JsonPrimitive(value.delimiter.name))
    private fun core(v: JsonElement): Parsec<User,JsonElement> {
        val a=v.jsonArray
        return when(a[0].s()) {
            "any" -> ListParsing.anyOrderedListMarker(Access).map(::attributes)
            "ordered" -> ListParsing.orderedListMarker(ListNumberStyle.valueOf(a[1].s()),ListNumberDelim.valueOf(a[2].s()),Access).map(::JsonPrimitive)
            "roman" -> ListParsing.romanNumeral<User>(a[1].jsonPrimitive.boolean).map(::JsonPrimitive)
            "decimal" -> ListParsing.decimal<User>().map(::pair)
            "lowerAlpha" -> ListParsing.lowerAlpha<User>().map(::pair)
            "upperAlpha" -> ListParsing.upperAlpha<User>().map(::pair)
            "lowerRoman" -> ListParsing.lowerRoman<User>().map(::pair)
            "upperRoman" -> ListParsing.upperRoman<User>().map(::pair)
            "eof" -> core(a[1]).before(Parsec.eof())
            "fallback" -> core(a[1]) or Parsec.pure(JsonPrimitive("fallback"))
            "many" -> core(a[1]).many().map(::JsonArray)
            "sequence" -> {
                val p=a[1].jsonArray.map(::core).foldRight(Parsec.zero<User,JsonElement>()) { x,acc -> x or acc }
                p.flatMap { first -> PandocTextCharacters.char<User>(','.code).then(p).many().map { JsonArray(listOf(first)+it) } }
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
        val original=User(o.getValue("next").n(),o.getValue("examples").jsonArray.associate { it.jsonArray.let { a -> a[0].s() to a[1].n() } },Any())
        val initial=ParseState(ParseInput(o.getValue("text").s()),SourcePos(p[0].s(),p[1].n(),p[2].n()),original)
        val reply=core(o.getValue("parser")).run(initial)
        return buildJsonObject {
            put("ok",reply.success);put("consumed",reply.consumed);put("error",error(reply.error))
            if(reply.success) {
                assertSame(original.untouched,reply.state.user.untouched)
                put("value",checkNotNull(reply.value));put("position",position(reply.state.position))
                put("remaining",reply.state.input.text.substring(reply.state.input.index));put("next",reply.state.user.next)
                put("examples",JsonArray(reply.state.user.examples.entries.sortedWith { a,b -> UnicodeTextOrder.compare(a.key,b.key) }
                    .map { j(JsonPrimitive(it.key),JsonPrimitive(it.value)) }))
                put("untouched","retained")
            }
        }
    }
    private fun partial(kind: String): JsonElement {
        val unused = object: ExampleNumberState<Unit> {
            override fun nextExample(state: Unit): Long = error("unused next example")
            override fun examples(state: Unit) = if(kind=="reuse-next") mapOf("label" to 42L) else error("unused examples")
            override fun update(state: Unit,nextExample: Long,examples: Map<String,Long>): Unit = error("unused state update")
        }
        val parser: Parsec<Unit,JsonElement> = when(kind) {
            "reuse-next", "decimal-state" -> ListParsing.anyOrderedListMarker(unused).map(::attributes)
            "default-state" -> ListParsing.orderedListMarker(ListNumberStyle.Example,ListNumberDelim.TwoParens,unused).map(::JsonPrimitive)
            else -> error(kind)
        }
        val text=when(kind) { "reuse-next"->"@label.";"decimal-state"->"123.";else->"(#)" }
        return checkNotNull(parser.run(ParseState(ParseInput(text),SourcePos(""),Unit)).value)
    }
    @TestFactory fun originalObservations(): List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/parsing/lists.jsonl.gz"))).bufferedReader().use { reader ->
            reader.lineSequence().drop(1).mapIndexed { index,line -> DynamicTest.dynamicTest("original list parser $index") {
                val c=Json.parseToJsonElement(line).jsonObject
                val input=c.getValue("input")
                val actual=when(c.getValue("op").s()) {
                    "run" -> run(input)
                    "marker" -> JsonPrimitive(WriterShared.isOrderedListMarker(input.s()))
                    "partial" -> partial(input.s())
                    "read" -> {
                        val r=ListParsing.decimal<Unit>().before(Parsec.eof()).run(ParseState(ParseInput(input.s()),SourcePos(""),Unit))
                        if(r.success) JsonPrimitive(checkNotNull(r.value).second) else JsonNull
                    }
                    else -> error(c)
                }
                assertEquals(c.getValue("expected"),actual,"case $index: $input")
            } }.toList()
        }
}
