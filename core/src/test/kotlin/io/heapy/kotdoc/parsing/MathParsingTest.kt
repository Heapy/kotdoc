package io.heapy.kotdoc.parsing

import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.logging.assertLogJsonEquals
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Native observation transport and caller setup; expected results are independent. */
class MathParsingTest {
    private fun points(v: JsonElement) = v.jsonArray.map { it.jsonPrimitive.int }.toIntArray()
    private fun text(v: JsonElement) = TextChars.fromPoints(points(v))
    private fun cp(s: String) = JsonArray(s.codePoints().toArray().map(::JsonPrimitive))
    private fun cp(s: CharacterString) = JsonArray(s.points().map(::JsonPrimitive))
    private fun str(v: JsonElement) = v.jsonPrimitive.content
    private fun pos(v: JsonElement) = v.jsonArray.let { SourcePos(CharacterString(points(it[0])), it[1].jsonPrimitive.long, it[2].jsonPrimitive.long) }
    private fun pos(p: SourcePos) = buildJsonArray { add(cp(p.nameRaw)); add(p.line); add(p.column) }
    private data class User(val options: ReaderOptions, val sentinel: String, val calls: Long, val mode: String)
    private val plain = ReaderOptionsAccess<User> { it.options }
    private val custom = object : ReaderOptionsAccess<User> {
        override fun extractReaderOptions(state: User) = state.options
        override fun <A> getOption(select: (ReaderOptions) -> A): Parsec<User, A> = Parsec.state<User>().flatMap { state ->
            val consume = if (state.user.mode == "consume") Parsec.anyToken<User>().map { Unit } else Parsec.pure(Unit)
            consume.then(Parsec.modify<User> { it.copy(user = state.user.copy(calls = state.user.calls + 1)) }).then(
                if (state.user.mode == "fail") Parsec.fail("reader option failure") else Parsec.pure(select(state.user.options)))
        }
    }
    private fun parser(v: JsonElement, access: ReaderOptionsAccess<User>): Parsec<User, JsonElement> {
        val xs = v.jsonArray
        return when (str(xs[0])) {
            "inline" -> MathParsing.mathInline(access).map(::JsonPrimitive)
            "display" -> MathParsing.mathDisplay(access).map(::JsonPrimitive)
            "either" -> (MathParsing.mathDisplay(access) or MathParsing.mathInline(access)).map(::JsonPrimitive)
            "enabled" -> ReaderOptionGuards.guardEnabled(access, Extension.fromJson(xs[1])).map { JsonPrimitive("()") }
            "disabled" -> ReaderOptionGuards.guardDisabled(access, Extension.fromJson(xs[1])).map { JsonPrimitive("()") }
            "option" -> access.getOption { it.toJson() }
            "eof" -> parser(xs[1], access).before(Parsec.eof())
            "fallback" -> parser(xs[1], access) or Parsec.pure(JsonPrimitive("fallback"))
            "look" -> parser(xs[1], access).lookAhead()
            "attempt" -> parser(xs[1], access).attempt()
            "many" -> parser(xs[1], access).many().map(::JsonArray)
            "sequence" -> xs[1].jsonArray.map { parser(it, access) }.fold(Parsec.pure<User, List<JsonElement>>(emptyList())) { result, p ->
                result.flatMap { values -> p.map { values + it } }
            }.map(::JsonArray)
            else -> error("parser")
        }
    }
    private fun error(e: ParseError) = buildJsonObject {
        put("position", pos(e.position)); put("show", e.showText()); put("characters", cp(e.showCharacters()))
        put("messages", JsonArray(e.messages.sortedBy { it.kind }.map { buildJsonArray { add(it.kind.ordinal); add(it.text) } }))
    }
    private fun remaining(stream: CharacterStream): JsonElement = when (stream) {
        is TextStream -> cp(stream.text())
        is CharacterListStream -> JsonArray(stream.points().map(::JsonPrimitive))
        is ByteStream -> JsonArray(stream.bytes().map { JsonPrimitive(it.toInt() and 255) })
        is LazyByteStream -> JsonArray(stream.byteChunks().map { bytes -> JsonArray(bytes.map { JsonPrimitive(it.toInt() and 255) }) })
        is Sources -> JsonArray(stream.chunks().map { buildJsonArray { add(pos(it.position)); add(cp(it.text)) } })
        else -> error("stream")
    }
    private fun runRequest(o: JsonObject): JsonElement {
        val input = o.getValue("input")
        val stream = when (str(o.getValue("stream"))) {
            "text" -> TextStream(text(input))
            "chars" -> CharacterListStream(points(input))
            "bytes" -> ByteStream(points(input).map(Int::toByte).toByteArray())
            "lazy-bytes" -> LazyByteStream.fromChunks(input.jsonArray.map { points(it).map(Int::toByte).toByteArray() })
            "sources" -> Sources(input.jsonArray.map { it.jsonArray.let { x -> SourceChunk(pos(x[0]), text(x[1])) } })
            else -> error("stream")
        }
        val mode = str(o.getValue("access"))
        val user = User(ReaderOptions.fromJson(o.getValue("options")), text(o.getValue("sentinel")), o.getValue("calls").jsonPrimitive.long, mode)
        val r = parser(o.getValue("parser"), if (mode == "default") plain else custom).run(ParseState(ParseInput(stream), pos(o.getValue("position")), user))
        return buildJsonObject {
            put("ok", r.success); put("consumed", r.consumed); put("error", error(r.error))
            if (r.success) {
                put("value", checkNotNull(r.value)); put("position", pos(r.state.position)); put("remaining", remaining(checkNotNull(r.state.input.stream)))
                put("user", buildJsonObject { put("options", r.state.user.options.toJson()); put("sentinel", r.state.user.sentinel); put("calls", r.state.user.calls) })
            }
        }
    }
    private fun run(v: JsonArray): JsonElement = when (str(v[0])) {
        "run" -> runRequest(v[1].jsonObject)
        "trim" -> JsonPrimitive(MathParsing.trimMath(text(v[1])))
        "unicode" -> {
            val digest = MessageDigest.getInstance("SHA-256")
            fun word(n: Int) { digest.update(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())) }
            val first = v[1].jsonPrimitive.int; val count = v[2].jsonPrimitive.int
            val opts = ReaderOptions(extensions = Extensions.of(ExtensionName.TexMathDollars))
            val inline = MathParsing.mathInline(); val display = MathParsing.mathDisplay()
            for (c in first until first + count) {
                val s = TextChars.fromPoints(intArrayOf(c))
                for ((p, source) in listOf(inline to ("$" + s + "$"), display to ("$$" + s + "$$"))) {
                    val r = p.run(ParseState(ParseInput(TextStream(source)), SourcePos("unicode"), opts))
                    val out = if (r.success) "R" + r.value else "L" + r.error.toString()
                    word(out.codePointCount(0, out.length)); out.codePoints().forEach(::word)
                }
            }
            JsonPrimitive(digest.digest().toHexString())
        }
        else -> error("request")
    }
    @TestFactory
    fun completeOriginalMathParserObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/parse-math/data.jsonl.gz"))).bufferedReader().use { reader ->
        reader.readLine(); reader.lineSequence().mapIndexed { i, line -> var transport: String? = line
            DynamicTest.dynamicTest("Math parser ${i + 1}") {
                val c = Json.parseToJsonElement(checkNotNull(transport)).jsonObject; transport = null
                assertLogJsonEquals(c.getValue("expected"), run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
}
