package io.heapy.kotdoc.parsing

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Transport only; all corpus expectations are from the unchanged Haskell module. */
class SourcesTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun JsonElement.n() = jsonPrimitive.long
    private fun points(v: JsonElement) = v.jsonArray.map { it.jsonPrimitive.int }.toIntArray()
    private fun text(v: JsonElement) = io.heapy.kotdoc.layout.TextChars.fromPoints(points(v))
    private fun js(points: IntArray) = JsonArray(points.map(::JsonPrimitive))
    private fun js(text: String) = js(text.codePoints().toArray())
    private fun j(vararg values: JsonElement) = JsonArray(values.toList())
    private fun pos(v: JsonElement) = v.jsonArray.let { SourcePos(it[0].s(),it[1].n(),it[2].n()) }
    private fun pos(p: SourcePos) = j(JsonPrimitive(p.name),JsonPrimitive(p.line),JsonPrimitive(p.column))
    private fun source(v: JsonElement) = Sources(v.jsonArray.map { it.jsonArray.let { a -> SourceChunk(pos(a[0]),text(a[1])) } })
    private fun encSource(s: Sources) = JsonArray(s.chunks().map { j(pos(it.position),js(it.text)) })
    private fun predicate(v: JsonElement): (Int) -> Boolean {
        val a=v.jsonArray
        return when(a[0].s()) {
            "true" -> { _ -> true }; "false" -> { _ -> false }
            "space" -> ParserCharacters::isSpace
            "letter" -> ParserCharacters::isAlpha
            "alphaNum" -> ParserCharacters::isAlphaNum
            "digit" -> ParserCharacters::isDigit
            "hexDigit" -> { c -> c in 48..57 || c in 65..70 || c in 97..102 }
            "oneOf" -> { c -> c in points(a[1]) }
            "noneOf" -> { c -> c !in points(a[1]) }
            else -> error(v)
        }
    }
    private fun core(v: JsonElement): Parsec<Int,JsonElement> {
        val a=v.jsonArray
        return when(a[0].s()) {
            "satisfy" -> SourceParsers.satisfy<Int>(predicate(a[1])).map(::JsonPrimitive)
            "char" -> SourceParsers.char<Int>(a[1].jsonPrimitive.int).map(::JsonPrimitive)
            "any" -> SourceParsers.anyChar<Int>().map(::JsonPrimitive)
            "newline" -> SourceParsers.newline<Int>().map(::JsonPrimitive)
            "space" -> SourceParsers.space<Int>().map(::JsonPrimitive)
            "spaces" -> SourceParsers.spaces<Int>().map { JsonNull }
            "letter" -> SourceParsers.letter<Int>().map(::JsonPrimitive)
            "alphaNum" -> SourceParsers.alphaNum<Int>().map(::JsonPrimitive)
            "digit" -> SourceParsers.digit<Int>().map(::JsonPrimitive)
            "hexDigit" -> SourceParsers.hexDigit<Int>().map(::JsonPrimitive)
            "oneOf" -> SourceParsers.oneOf<Int>(points(a[1])).map(::JsonPrimitive)
            "noneOf" -> SourceParsers.noneOf<Int>(points(a[1])).map(::JsonPrimitive)
            "string" -> SourceParsers.string<Int>(points(a[1])).map { JsonArray(it.map(::JsonPrimitive)) }
            "ordinary" -> Parsec.char<Int>(a[1].jsonPrimitive.int).map(::JsonPrimitive)
            "eof" -> Parsec.eof<Int>().map { JsonNull }
            "take" -> SourceParsers.takeWhileP<Int>(predicate(a[1])).map { js(it) }
            "take1" -> SourceParsers.takeWhile1P<Int>(predicate(a[1])).map { js(it) }
            "manyText" -> SourceParsers.satisfy<Int>(predicate(a[1])).many().map { js(io.heapy.kotdoc.layout.TextChars.fromPoints(it.toIntArray())) }
            "many1Text" -> SourceParsers.satisfy<Int>(predicate(a[1])).many1().map { js(io.heapy.kotdoc.layout.TextChars.fromPoints(it.toIntArray())) }
            "many" -> core(a[1]).many().map(::JsonArray)
            "try" -> core(a[1]).attempt()
            "look" -> core(a[1]).lookAhead()
            "fallback" -> core(a[1]) or Parsec.pure(JsonPrimitive("fallback"))
            "label" -> core(a[2]).label(a[1].s())
            "sequence" -> a[1].jsonArray.map(::core).fold(Parsec.pure<Int,List<JsonElement>>(emptyList())) { acc,p ->
                acc.flatMap { values -> p.map { values + it } }
            }.map(::JsonArray)
            "user" -> Parsec.modify<Int> { it.copy(user=a[1].jsonPrimitive.int) }.map { JsonNull }
            "add" -> SourceParsers.addToSources<Int>(pos(a[1]),text(a[2])).map { JsonNull }
            "prepend" -> SourceParsers.addToInput<Int>(text(a[1])).map { JsonNull }
            else -> error(v)
        }
    }
    private fun encError(e: ParseError) = buildJsonObject {
        put("position",pos(e.position));put("show",e.toString())
        put("messages",JsonArray(e.messages.sortedBy { it.kind }.map { j(JsonPrimitive(it.kind.ordinal),JsonPrimitive(it.text)) }))
    }
    private fun run(v: JsonElement): JsonElement {
        val o=v.jsonObject;val input=o.getValue("input")
        val stream=when(o.getValue("type").s()) {
            "sources" -> source(input)
            "text" -> TextStream(text(input))
            "chars" -> CharacterListStream(points(input))
            "bytes" -> ByteStream(points(input).map(Int::toByte).toByteArray())
            "lazyBytes" -> LazyByteStream.fromChunks(input.jsonArray.map { points(it).map(Int::toByte).toByteArray() })
            else -> error(o)
        }
        val reply=core(o.getValue("parser")).run(ParseState(ParseInput(stream),pos(o.getValue("position")),o.getValue("user").jsonPrimitive.int))
        return buildJsonObject {
            put("ok",reply.success);put("consumed",reply.consumed);put("error",encError(reply.error))
            if (reply.success) {
                put("value",checkNotNull(reply.value));put("position",pos(reply.state.position));put("user",reply.state.user)
                put("remaining",when(val rest=reply.state.input.stream) {
                    is Sources -> encSource(rest)
                    is TextStream -> js(rest.text())
                    is CharacterListStream -> js(rest.points())
                    is ByteStream -> js(rest.bytes().map { it.toInt() and 255 }.toIntArray())
                    is LazyByteStream -> JsonArray(rest.byteChunks().map { js(it.map { b -> b.toInt() and 255 }.toIntArray()) })
                    else -> error(rest.toString())
                })
            }
        }
    }
    private fun partial(kind: String): JsonElement {
        fun <A> bad(message: String): Deferred<A> = Deferred.later { error(message) }
        val nil: Deferred<DeferredChain<SourceChunk>> = Deferred.now(DeferredChain.Nil)
        fun cons(head: Deferred<SourceChunk>,tail: Deferred<DeferredChain<SourceChunk>> = nil) = Deferred.now<DeferredChain<SourceChunk>>(DeferredChain.Cons(head,tail))
        fun chunk(position: Deferred<SourcePos>,text: Deferred<String>) = Deferred.now(SourceChunk.deferred(position,text))
        fun initial(s: Sources) = JsonPrimitive(s.initialSourceName())
        fun any(s: Sources) = JsonPrimitive(checkNotNull(SourceParsers.anyChar<Unit>().run(ParseState(ParseInput(s),SourcePos("parser"),Unit)).value))
        val unusedPosition=bad<SourcePos>("unused position")
        return when(kind) {
            "initial-ignore-text-tail" -> initial(Sources(cons(chunk(Deferred.now(SourcePos("first")),bad("unused text")),cons(bad("unused pair")))))
            "initial-ignore-tail" -> initial(Sources(cons(chunk(Deferred.now(SourcePos("first")),bad("unused text")),bad("unused tail"))))
            "concat-ignore-position" -> JsonPrimitive(Sources(cons(chunk(unusedPosition,Deferred.now("abc")))).sourcesToText())
            "uncons-ignore-position" -> JsonPrimitive(Sources(cons(chunk(unusedPosition,Deferred.now("x")))).uncons()!!.token.value())
            "uncons-ignore-empty-position" -> JsonPrimitive(Sources(cons(chunk(unusedPosition,Deferred.now("")),cons(chunk(unusedPosition,Deferred.now("x"))))).uncons()!!.token.value())
            "uncons-ignore-char" -> JsonPrimitive((CharacterListStream(Deferred.now(DeferredChain.Cons(bad("unused char"),Deferred.now(DeferredChain.Nil)))).uncons()!!.tail as CharacterListStream).points().isEmpty())
            "append-ignore-rhs" -> initial(Sources(cons(chunk(Deferred.now(SourcePos("left")),bad("unused text")))) + Sources(bad("unused rhs")))
            "append-ignore-left-fields" -> JsonPrimitive((Sources(cons(chunk(bad("unused pos"),Deferred.now("abc"))))+Sources.empty()).sourcesToText())
            "update-ignore-single-text" -> pos(Sources(cons(chunk(bad("unused pos"),bad("unused text")))).updateSourcePos(SourcePos("parser"),9))
            "update-ignore-next-text" -> pos(Sources(cons(chunk(bad("unused pos"),Deferred.now("")),cons(chunk(Deferred.now(SourcePos("next",4,6)),bad("unused text"))))).updateSourcePos(SourcePos("parser"),120))
            "update-demand-tail" -> pos(Sources(cons(chunk(bad("unused pos"),Deferred.now("x")),bad("demanded tail"))).updateSourcePos(SourcePos("parser"),120))
            "update-demand-next-pair" -> pos(Sources(cons(chunk(bad("unused pos"),Deferred.now("x")),cons(bad("demanded pair")))).updateSourcePos(SourcePos("parser"),120))
            "ensure-ignore-earlier-pair" -> initial(Sources(cons(bad("earlier pair"),cons(chunk(Deferred.now(SourcePos("last")),Deferred.now("\n\n"))))).ensureFinalNewlines(2))
            "ensure-ignore-earlier-text" -> initial(Sources(cons(chunk(Deferred.now(SourcePos("first")),bad("unused text")),cons(chunk(bad("unused pos"),Deferred.now("\n\n"))))).ensureFinalNewlines(2))
            "take-false-ignore-tail" -> JsonPrimitive(SourceParsers.takeWhileP<Unit> { false }.run(ParseState(ParseInput(Sources(cons(chunk(bad("unused pos"),Deferred.now("x")),bad("unused tail")))),SourcePos("parser"),Unit)).value)
            "any-ignore-last-position" -> any(Sources(cons(chunk(bad("unused pos"),Deferred.now("x")))))
            "any-demand-tail" -> any(Sources(cons(chunk(bad("unused pos"),Deferred.now("x")),bad("demanded tail"))))
            "prepend-demand-spine", "prepend-demand-pair", "prepend-ignore-fields", "add-ignore-old-spine", "add-ignore-old-pair" -> {
                val s=when(kind) {
                    "prepend-demand-spine" -> Sources(bad("demanded spine"))
                    "prepend-demand-pair" -> Sources(cons(bad("demanded pair")))
                    "prepend-ignore-fields" -> Sources(cons(chunk(bad("unused pos"),bad("unused text"))))
                    "add-ignore-old-spine" -> Sources(bad("unused spine"))
                    else -> Sources(cons(bad("unused pair")))
                }
                val parser=if(kind.startsWith("prepend")) SourceParsers.addToInput<Unit>("a") else SourceParsers.addToSources<Unit>(SourcePos("new"),"a")
                assertTrue(parser.run(ParseState(ParseInput(s),SourcePos("parser"),Unit)).success)
                JsonNull
            }
            "ensure-zero-demand-last-text" -> initial(Sources(cons(chunk(Deferred.now(SourcePos("last")),bad("demanded text")))).ensureFinalNewlines(0))
            "take-whole-demand-next-position" -> JsonPrimitive(SourceParsers.takeWhileP<Unit> { true }.run(ParseState(ParseInput(Sources(cons(chunk(bad("unused pos"),Deferred.now("x")),cons(chunk(bad("demanded next position"),Deferred.now("")))))),SourcePos("parser"),Unit)).value)
            "take-prefix-ignore-tail" -> JsonPrimitive(SourceParsers.takeWhileP<Unit> { it==120 }.run(ParseState(ParseInput(Sources(cons(chunk(bad("unused pos"),Deferred.now("x!")),bad("unused tail")))),SourcePos("parser"),Unit)).value)
            "take-empty-retain-pos" -> JsonPrimitive(SourceParsers.takeWhileP<Unit> { false }.run(ParseState(ParseInput(Sources(cons(chunk(bad("unused pos"),Deferred.now("")),cons(chunk(bad("unused pos"),Deferred.now("x")))))),SourcePos("parser"),Unit)).state.position.name)
            "update-jump-ignore-pos-char" -> pos(SourcePositionInstances.sources.updateSourcePos(bad("unused current position"),bad("unused char"),Deferred.now(Sources(cons(chunk(bad("unused pos"),Deferred.now("")),cons(chunk(Deferred.now(SourcePos("next",4,6)),bad("unused text"))))))).value())
            "update-fallback-demand-char" -> pos(SourcePositionInstances.sources.updateSourcePos(Deferred.now(SourcePos("parser")),bad("demanded char"),Deferred.now(Sources(cons(chunk(bad("unused pos"),Deferred.now("x")))))).value())
            "mconcat-ignore-tail" -> initial(Sources.concat(Deferred.now(DeferredChain.Cons(Deferred.now(Sources(cons(chunk(Deferred.now(SourcePos("first")),bad("unused text"))))),bad("unused source list")))))
            "infinite-initial", "infinite-prefix" -> {
                val c=Deferred.now(SourceChunk(SourcePos("infinite"),"x"))
                lateinit var chain: Deferred<DeferredChain<SourceChunk>>
                chain=Deferred.later { DeferredChain.Cons(c,chain) }
                val s=Sources(chain)
                if (kind=="infinite-initial") initial(s) else JsonPrimitive(SourceParsers.string<Unit>(IntArray(10000) { 120 }).run(ParseState(ParseInput(s),SourcePos("parser"),Unit)).value!!.size)
            }
            else -> error(kind)
        }
    }
    private fun operation(request: JsonArray): JsonElement = when(request[0].s()) {
        "list" -> {
            val o=request[1].jsonObject
            fun attributes(v: io.heapy.kotdoc.ast.ListAttributes) = j(JsonPrimitive(v.start),JsonPrimitive(v.style.name),JsonPrimitive(v.delimiter.name))
            val parser: Parsec<ExampleNumbers,JsonElement> = when(o.getValue("parser").s()) {
                "any" -> ListParsing.anyOrderedListMarker(ExampleNumbers).map(::attributes)
                "eof" -> ListParsing.anyOrderedListMarker(ExampleNumbers).before(Parsec.eof()).map(::attributes)
                "roman" -> ListParsing.romanNumeral<ExampleNumbers>(true).map(::JsonPrimitive)
                "decimal" -> ListParsing.decimal<ExampleNumbers>().map { j(JsonPrimitive(it.first.name),JsonPrimitive(it.second)) }
                else -> error(o)
            }
            val reply=parser.run(ParseState(ParseInput(source(o.getValue("input"))),pos(o.getValue("position")),ExampleNumbers()))
            buildJsonObject {
                put("ok",reply.success);put("consumed",reply.consumed);put("error",encError(reply.error))
                if(reply.success) {
                    put("value",checkNotNull(reply.value));put("position",pos(reply.state.position));put("remaining",encSource(reply.state.input.stream as Sources))
                    put("next",reply.state.user.nextExample);put("examples",JsonArray(reply.state.user.examples.entries.sortedBy { it.key }.map { j(JsonPrimitive(it.key),JsonPrimitive(it.value)) }));put("untouched","retained")
                }
            }
        }
        "run" -> run(request[1])
        "source" -> source(request[1]).let { s -> buildJsonObject {
            put("chunks",encSource(Sources.toSources(s)));put("text",s.sourcesToText());put("name",s.initialSourceName());put("show",s.show());put("show11",s.show(11))
        } }
        "from" -> encSource(when(request[1].s()) {
            "text" -> SourceConversions.text.toSources(text(request[2]))
            "string" -> SourceConversions.characters.toSources(io.heapy.kotdoc.layout.CharacterString(points(request[2])))
            "files" -> SourceConversions.files.toSources(request[2].jsonArray.map { it.jsonArray.let { a -> a[0].s() to text(a[1]) } })
            else -> error(request)
        })
        "ensure" -> encSource(source(request[2]).ensureFinalNewlines(request[1].n()))
        "concat" -> encSource(Sources.concat(request[1].jsonArray.map(::source)))
        "append" -> encSource(source(request[1])+source(request[2]))
        "uncons" -> source(request[1]).uncons()?.let { j(JsonPrimitive(it.token.value()),encSource(it.tail as Sources)) } ?: JsonNull
        "update" -> pos(if(request[1].s()=="sources") source(request[4]).updateSourcePos(pos(request[2]),request[3].jsonPrimitive.int)
            else when(request[1].s()) {
                "text" -> SourcePositionInstances.text.updateSourcePos(pos(request[2]),request[3].jsonPrimitive.int,Deferred.later { error("unused Text") })
                "chars" -> SourcePositionInstances.characters.updateSourcePos(pos(request[2]),request[3].jsonPrimitive.int,Deferred.later { error("unused [Char]") })
                "bytes" -> SourcePositionInstances.bytes.updateSourcePos(pos(request[2]),request[3].jsonPrimitive.int,Deferred.later { error("unused ByteString") })
                "lazyBytes" -> SourcePositionInstances.lazyBytes.updateSourcePos(pos(request[2]),request[3].jsonPrimitive.int,Deferred.later { error("unused ByteString") })
                else -> error(request)
            })
        "partial" -> partial(request[1].s())
        else -> error(request)
    }
    @TestFactory fun originalObservations(): List<DynamicTest> =
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/parsing/sources.jsonl.gz"))).bufferedReader().use { reader ->
            reader.lineSequence().drop(1).mapIndexed { index,line -> DynamicTest.dynamicTest("original Sources $index") {
                val c=Json.parseToJsonElement(line).jsonObject;val request=c.getValue("request").jsonArray
                if (request[0].s()=="classes") {
                    val rows=c.getValue("expected").jsonArray
                    var at=0
                    for (point in 0..0x10ffff) {
                        if (at+1<rows.size && point==rows[at+1].jsonArray[0].jsonPrimitive.int) at++
                        val flags=ParserCharacters.flags(point) or if(point in 48..57 || point in 65..70 || point in 97..102) 16 else 0
                        assertEquals(rows[at].jsonArray[1].jsonPrimitive.int,flags,"Char $point")
                    }
                } else {
                    val actual=try { operation(request) } catch (e: IllegalStateException) { buildJsonObject { put("exception",e.message.orEmpty().lineSequence().first()) } }
                    assertEquals(c.getValue("expected"),actual,"case $index: ${request.toString().take(600)}")
                }
            } }.toList()
        }
    @Test fun persistentStreamsPreserveCallerStateAndSnapshots() {
        val points=mutableListOf(65,66);val chain=CharacterListStream(DeferredChain.of(points));points[0]=90
        assertEquals(65,chain.uncons()!!.token.value())
        val bytes=byteArrayOf(65,66);val stream=ByteStream(bytes);bytes[0]=90
        assertEquals(65,stream.uncons()!!.token.value())
        val initial=Sources(listOf(SourceChunk(SourcePos("first"),"ab"),SourceChunk(SourcePos("second",8,9),"\t!")))
        val marker=Any();val state=ParseState(ParseInput(initial),SourcePos("parser",3,7),marker)
        val first=SourceParsers.string<Any>("ab").run(state)
        assertSame(marker,first.state.user);assertEquals(SourcePos("second",8,9),first.state.position)
        assertEquals("ab\t!",initial.sourcesToText())
        val tab=SourceParsers.anyChar<Any>().run(first.state)
        assertEquals(SourcePos("second",8,13),tab.state.position)
        assertSame(initial,Sources.toSources(initial));assertSame(initial,initial.ensureFinalNewlines(-1))
    }
}
