package io.heapy.kotdoc.yaml

import io.heapy.kotdoc.json.PrettyValue
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.logging.assertLogJsonEquals
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Transports input bytes only; all expected reader/schema fields are native. */
class YamlFoundationTest {
    private fun JsonElement.s() = jsonPrimitive.content
    private fun text(v: JsonElement) = TextChars.fromPoints(v.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun bytes(v: JsonElement) = v.jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
    private fun points(s: String) = JsonArray(s.codePoints().toArray().map(::JsonPrimitive))
    private fun shape(v: PrettyValue): JsonElement = when(v) {
        PrettyValue.Null -> buildJsonArray { add("null") }
        is PrettyValue.Bool -> buildJsonArray { add("bool"); add(v.value) }
        is PrettyValue.Text -> buildJsonArray { add("text"); add(v.value) }
        is PrettyValue.Number -> buildJsonArray { add("number"); add(v.value.coefficient.toString()); add(v.value.base10Exponent) }
        else -> error("unexpected scalar value")
    }
    private fun tag(name: String): YamlTag = when(name) {
        "StrTag" -> YamlTag.StrTag; "FloatTag" -> YamlTag.FloatTag; "NullTag" -> YamlTag.NullTag
        "BoolTag" -> YamlTag.BoolTag; "SetTag" -> YamlTag.SetTag; "IntTag" -> YamlTag.IntTag
        "SeqTag" -> YamlTag.SeqTag; "MapTag" -> YamlTag.MapTag; "NoTag" -> YamlTag.NoTag
        else -> YamlTag.UriTag(CharacterStringChars.fromString("!custom"))
    }
    private fun utf8(cp: Int): ByteArray = when {
        cp < 128 -> byteArrayOf(cp.toByte())
        cp < 2048 -> byteArrayOf((192 + (cp shr 6)).toByte(), (128 + (cp and 63)).toByte())
        cp < 65536 -> byteArrayOf((224 + (cp shr 12)).toByte(), (128 + ((cp shr 6) and 63)).toByte(), (128 + (cp and 63)).toByte())
        else -> byteArrayOf((240 + (cp shr 18)).toByte(), (128 + ((cp shr 12) and 63)).toByte(), (128 + ((cp shr 6) and 63)).toByte(), (128 + (cp and 63)).toByte())
    }
    private fun domain(a: JsonArray): JsonElement {
        val encoding = if (a[0].s() == "domain") 1 else a[3].jsonPrimitive.int
        return JsonArray((a[1].jsonPrimitive.int until a[2].jsonPrimitive.int).map { cp ->
            val data = if (encoding == 1) utf8(cp) else byteArrayOf((cp and 255).toByte(), (cp shr 8).toByte()).let {
                if (encoding == 3) it.reversedArray() else it
            }
            var delivered = false
            val r = YamlReader(YamlReadHandler { if (delivered) byteArrayOf() else { delivered = true; data } }, YamlEncoding.entries[encoding])
            r.ensure(1)
            buildJsonArray { add(r.failure?.problem?.let(::JsonPrimitive) ?: JsonNull); add(r.failure?.value ?: 0) }
        })
    }
    private fun reader(a: JsonArray): JsonElement {
        val data = bytes(a[1]); val chunks = a[2].jsonArray.map { it.jsonPrimitive.int }
        var position = 0; var chunkIndex = 0; var calls = 0; var lastRequest = 0
        val handler = YamlReadHandler { maximum ->
            calls++; lastRequest = maximum
            val chunk = if (chunkIndex < chunks.size) chunks[chunkIndex++] else 16384
            if (chunk < 0) null else {
                val size = minOf(chunk, maximum, data.size - position)
                data.copyOfRange(position, position + size).also { position += size }
            }
        }
        val r = YamlReader(handler, YamlEncoding.entries[a[3].jsonPrimitive.int], a[4].s().toULong())
        fun state(result: Boolean) = buildJsonObject {
            put("result", result); put("encoding", r.encoding.ordinal); put("offset", r.offset.toString())
            put("eof", r.eof); put("unread", r.unread); put("buffer", r.bufferedBytes().toHexString())
            put("raw", r.remainingRawBytes().toHexString()); put("calls", calls); put("inputPosition", position)
            put("chunkIndex", chunkIndex); put("lastRequest", lastRequest)
            put("failure", r.failure?.let { buildJsonArray { add(it.problem); add(it.offset.toString()); add(it.value) } } ?: JsonNull)
        }
        val states = mutableListOf<JsonElement>(state(true))
        for (step in a[5].jsonArray) {
            val s = step.jsonArray
            val result = if (s[0].jsonPrimitive.int == 0) r.ensure(s[1].jsonPrimitive.int)
                else { r.consume(s[1].jsonPrimitive.int); true }
            states.add(state(result))
            if (!result) break
        }
        return JsonArray(states)
    }
    private fun run(a: JsonArray): JsonElement = when(a[0].s()) {
        "reader" -> reader(a)
        "domain", "units" -> domain(a)
        "scientific" -> {
            val t = text(a[1]); val n = YamlScalars.textToScientific(t)
            val (tag, style) = YamlScalars.defaultStringStyle(t)
            buildJsonObject {
                put("numeric", when(n) {
                    is YamlScientificResult.Success -> buildJsonArray { add("success"); add(n.value.coefficient.toString()); add(n.value.base10Exponent) }
                    is YamlScientificResult.Failure -> buildJsonArray { add("failure"); add(n.error) }
                })
                put("isNumeric", YamlScalars.isNumeric(t)); put("special", YamlScalars.isSpecialString(t))
                put("tag", tag.toString()); put("style", style.name)
            }
        }
        "scalar" -> buildJsonObject {
            put("value", shape(YamlScalars.resolve(YamlScalarEvent(bytes(a[1]), tag(a[3].s()), YamlStyle.entries[a[2].jsonPrimitive.int], null))))
            put("warnings", JsonArray(emptyList()))
        }
        "string" -> {
            val s = YamlScalars.stringScalar(text(a[1]), if (a[2] == JsonNull) null else text(a[2]))
            buildJsonObject {
                put("bytes", JsonArray(s.contents.map { JsonPrimitive(it.toInt() and 255) }))
                put("tag", s.tag.toString()); put("style", s.style.name)
                put("anchor", s.anchor?.let { JsonArray(it.points().map(::JsonPrimitive)) } ?: JsonNull)
            }
        }
        else -> error(a[0])
    }
    @TestFactory
    fun nativeYamlObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/yaml/data.jsonl.gz"))).bufferedReader().use { r ->
        r.readLine()
        r.lineSequence().mapIndexed { i,line ->
            // JUnit retains dynamic-test lambdas for reporting. Keep transport
            // text until execution, then release it instead of retaining millions
            // of parsed JSON nodes across the complete upstream test suite.
            var transport: String? = line
            DynamicTest.dynamicTest("YAML ${i + 1}") {
                val c = Json.parseToJsonElement(checkNotNull(transport)).jsonObject
                transport = null
                assertLogJsonEquals(c.getValue("expected"), run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
    @Test
    fun ownedBytesAndEmptyStringStyleDoNotEvaluateTheCallback() {
        val input = byteArrayOf(65)
        val e = YamlScalarEvent(input, YamlTag.NoTag, YamlStyle.Plain, null)
        input[0] = 66; e.contents[0] = 67
        assertContentEquals(byteArrayOf(65), e.contents)
        val empty = YamlScalars.stringScalar("", "anchor") { error("empty string must bypass style callback") }
        assertEquals(YamlStyle.SingleQuoted, empty.style); assertSame(YamlTag.NoTag, empty.tag)
        assertContentEquals("anchor".codePoints().toArray(), empty.anchor?.points())
        assertEquals("", assertIs<PrettyValue.Text>(YamlScalars.resolve(empty)).value)
    }
}
