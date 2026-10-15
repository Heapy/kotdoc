package io.heapy.kotdoc.uri

import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.parsing.Deferred
import io.heapy.kotdoc.parsing.attoparsec.*
import io.heapy.kotdoc.resources.ResourceUri
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

class PandocURITest {
    private fun raw(v: JsonElement) = TextChars.fromPoints(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun points(s: String) = JsonArray(s.codePoints().toArray().map(::JsonPrimitive))
    private fun bytes(s: ByteArray) = JsonArray(s.map { JsonPrimitive(it.toInt() and 255) })
    private fun array(vararg v: JsonElement) = JsonArray(v.toList())
    private fun expr(v: JsonElement): TextParser<JsonElement> {
        val a = v.jsonArray
        fun sub(i: Int) = expr(a[i])
        return when (a[0].jsonPrimitive.content) {
            "pure" -> TextParser.pure(a[1])
            "fail" -> TextParser.fail(a[1].jsonPrimitive.content)
            "char" -> TextParser.char(a[1].jsonPrimitive.int).map(::JsonPrimitive)
            "string" -> TextParser.string(raw(a[1])).map(::points)
            "ascii" -> TextParser.asciiCI(raw(a[1])).map(::points)
            "take" -> {
                val allowed = raw(a[1]).codePoints().toArray().toSet()
                (if (a[2].jsonPrimitive.boolean) TextParser.takeWhile1 { it in allowed }
                 else TextParser.takeWhile { it in allowed }).map(::points)
            }
            "eof" -> TextParser.endOfInput().map { JsonNull }
            "or" -> sub(1) or sub(2)
            "then" -> sub(1).then(sub(2))
            "before" -> sub(1).before(sub(2))
            "pair" -> sub(1).flatMap { first -> sub(2).map { second -> array(first, second) } }
            "many" -> sub(1).many().map { JsonArray(it) }
            "many1" -> sub(1).many1().map { JsonArray(it) }
            "skip" -> sub(1).skipMany().map { JsonNull }
            "look" -> sub(1).lookAhead()
            "match" -> sub(1).match().map { array(points(it.text.value()), it.parsed.value()) }
            "label" -> sub(1).label(a[2].jsonPrimitive.content)
            else -> error(a)
        }
    }
    private fun result(r: AttoResult<JsonElement>): JsonElement = buildJsonObject {
        when (r) {
            is AttoResult.Partial -> put("tag", "partial")
            is AttoResult.Done -> { put("tag", "done"); put("rest", points(r.remaining)); put("value", r.value) }
            is AttoResult.Fail -> {
                put("tag", "fail"); put("rest", points(r.remaining))
                put("contexts", JsonArray(r.contexts.map { JsonPrimitive(Chars.build(it)) }))
                put("message", Chars.build(r.message))
            }
        }
    }
    private fun trace(p: TextParser<JsonElement>, chunks: JsonArray): JsonElement {
        var r = p.parse(raw(chunks.first()))
        return buildJsonArray {
            add(result(r))
            for (chunk in chunks.drop(1)) { r = r.feed(raw(chunk)); add(result(r)) }
        }
    }
    private fun dataParser(full: Boolean): TextParser<JsonElement> {
        val p = if (full) PandocURI.pBase64DataURI.before(TextParser.endOfInput()) else PandocURI.pBase64DataURI
        return p.map { array(bytes(it.contents.value()), points(it.mime.value())) }
    }
    private fun exports(s: String) = array(points(PandocURI.urlEncode(s)), points(PandocURI.escapeURI(s)),
        points(PandocURI.uriPathToPath(s, false)), points(PandocURI.uriPathToPath(s, true)))
    private fun unicode(start: Int, count: Int): JsonElement {
        val digest = MessageDigest.getInstance("SHA-256")
        for (cp in start..<start + count) {
            val s = TextChars.fromPoints(intArrayOf(cp))
            val b = exports(s).toString().toByteArray(Charsets.UTF_8)
            digest.update(byteArrayOf((b.size ushr 24).toByte(), (b.size ushr 16).toByte(), (b.size ushr 8).toByte(), b.size.toByte()))
            digest.update(b)
        }
        return JsonPrimitive(digest.digest().toHexString())
    }
    private fun observe(v: JsonElement): JsonElement {
        val a = v.jsonArray
        return when (a[0].jsonPrimitive.content) {
            "uri" -> buildJsonObject { val s = raw(a[1]); put("exports", exports(s)); put("isURI", PandocURI.isURI(s)) }
            "schemes" -> JsonArray(PandocURI.schemes.map(::JsonPrimitive))
            "data" -> trace(dataParser(a[1].jsonPrimitive.boolean), a[2].jsonArray)
            "complete" -> {
                val p = dataParser(a[1].jsonPrimitive.boolean)
                when (val r = p.parseComplete(raw(a[2]))) {
                    is AttoResult.Done -> buildJsonObject { put("tag", "done"); put("rest", JsonArray(emptyList())); put("value", r.value) }
                    is AttoResult.Fail -> buildJsonObject {
                        put("tag", "fail"); put("rest", JsonArray(emptyList())); put("contexts", JsonArray(emptyList())); put("message", r.formatted)
                    }
                    else -> error("Complete input remained Partial")
                }
            }
            "kernel" -> trace(expr(a[1]), a[2].jsonArray)
            "branch" -> {
                val r = expr(a[1]).parse(raw(a[2]))
                array(*listOf(a[3], a[4]).map { chunks ->
                    var state = r
                    buildJsonArray { add(result(state)); for (c in chunks.jsonArray) { state = state.feed(raw(c)); add(result(state)) } }
                }.toTypedArray())
            }
            "base64" -> bytes(ResourceUri.decodeBase64Lenient(a[1].jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()))
            "unicode" -> unicode(a[1].jsonPrimitive.int, a[2].jsonPrimitive.int)
            "deep" -> {
                var p: TextParser<JsonElement> = TextParser.pure(JsonPrimitive(0))
                repeat(a[2].jsonPrimitive.int) {
                    p = if (a[1].jsonPrimitive.int == 0) p.map { JsonPrimitive(it.jsonPrimitive.int + 1) }
                        else p.flatMap { TextParser.pure(JsonPrimitive(it.jsonPrimitive.int + 1)) }
                }
                result(p.parse(""))
            }
            "fragmented" -> {
                val n = a[2].jsonPrimitive.int; val kind = a[1].jsonPrimitive.int
                val p: TextParser<JsonElement> = when (kind) {
                    0 -> TextParser.string("a".repeat(n)).map<JsonElement> { JsonPrimitive(n) }
                    1 -> TextParser.char('a').many().map<JsonElement> { JsonPrimitive(it.size) }
                    2 -> (TextParser.string("a".repeat(n) + "b") or TextParser.string("a".repeat(n) + "c")).map<JsonElement> { JsonNull }
                    else -> error(kind)
                }.before(TextParser.endOfInput())
                var r = p.parse("")
                repeat(n) { r = r.feed("a") }
                r = r.feed(if (kind == 2) "c" else "")
                result(r.feed(""))
            }
            "lazy" -> {
                val bad = TextParser.pureDeferred(Deferred.later<Int> { error("unforced result") })
                val p: TextParser<JsonElement> = when (a[1].jsonPrimitive.int) {
                    0 -> bad.then(TextParser.pure(JsonNull))
                    1 -> bad.map { it.toString() }.mapDeferred { Deferred.now(JsonNull) }
                    2 -> TextParser.char('a').then(bad).many().mapDeferred { Deferred.now(JsonNull) }
                    3 -> bad.match().mapDeferred { Deferred.now(JsonNull) }
                    else -> error(a)
                }
                result(p.parse("aaa!").feed(""))
            }
            else -> error(a)
        }
    }
    @TestFactory
    fun originalObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/pandoc-uri/data.jsonl.gz")))
        .bufferedReader().useLines { lines -> lines.drop(1).mapIndexed { i, line ->
            val c = Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$i ${c.getValue("group").jsonPrimitive.content}") {
                assertEquals(c.getValue("expected"), observe(c.getValue("request")), c.getValue("request").toString().take(500))
            }
        }.toList() }

    @Test
    fun `scheme registry is immutable and decoder results are not demanded by recognition`() {
        assertThrows(UnsupportedOperationException::class.java) { (PandocURI.schemes as MutableSet<String>).add("custom") }
        val p = PandocURI.pBase64DataURI.before(TextParser.endOfInput())
        val r = p.parseComplete("data:a/b;base64,%3D%3DQQ")
        assertTrue(r is AttoResult.Done)
        assertEquals("a/b", (r as AttoResult.Done).value.mime.value())
        assertArrayEquals(byteArrayOf(65), r.value.contents.value())
    }
}
