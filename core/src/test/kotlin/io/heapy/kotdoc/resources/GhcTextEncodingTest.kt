package io.heapy.kotdoc.resources

import com.sun.jna.Platform
import io.heapy.kotdoc.errors.GhcIOException
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream

class GhcTextEncodingTest {
    private fun observe(v: JsonObject): JsonElement = try {
        val encoding = if (v.getValue("char8").jsonPrimitive.boolean) GhcTextEncoding.char8
            else GhcTextEncoding.mkTextEncoding(v.getValue("encoding").jsonPrimitive.content)
        val values = v.getValue("batches").jsonArray.map { batch ->
            try {
                val points = batch.jsonArray.map { it.jsonPrimitive.int }.toIntArray()
                val value = when (v.getValue("api").jsonPrimitive.content) {
                    "decode" -> JsonArray(encoding.decode(points.map(Int::toByte).toByteArray()).map(::JsonPrimitive))
                    "encode" -> {
                        val before = points.copyOf()
                        val bytes = encoding.encode(points, v.getValue("terminate").jsonPrimitive.boolean)
                        assertArrayEquals(before, points, "recovery must not mutate caller-owned input")
                        JsonArray(bytes.map { JsonPrimitive(it.toInt() and 255) })
                    }
                    "representable" -> JsonArray(points.map { JsonPrimitive(encoding.charIsRepresentable(it)) })
                    else -> error(v)
                }
                buildJsonObject { put("value", value) }
            } catch (e: GhcIOException) { LookupTestMemory.ioError(e) }
        }
        buildJsonObject { put("name", encoding.textEncodingName); put("values", JsonArray(values)) }
    } catch (e: GhcIOException) { LookupTestMemory.ioError(e) }

    @TestFactory fun originalTextEncoding(): List<DynamicTest> {
        val platform = if (Platform.isMac() || Platform.isWindows()) "macos-aarch64" else "linux-x86_64"
        return GZIPInputStream(javaClass.getResourceAsStream("/ghc-text-encoding/$platform.jsonl.gz")!!).bufferedReader().use { reader ->
            val metadata = Json.parseToJsonElement(reader.readLine()).jsonObject
            val cases = reader.lineSequence().map { Json.parseToJsonElement(it).jsonObject }.toList()
            assertEquals(metadata.getValue("cases").jsonPrimitive.int, cases.size)
            cases.mapIndexedNotNull { i, c ->
                val request = c.getValue("request").jsonObject
                if (Platform.isWindows() && request.getValue("posixOnly").jsonPrimitive.boolean) null else
                DynamicTest.dynamicTest("$i ${c.getValue("group").jsonPrimitive.content}") {
                    assertEquals(c.getValue("expected"), observe(request))
                }
            }
        }
    }

    /** All 60 actual assertions in the retained encoding005 program. Direct
     * Latin1 constructors and named mkTextEncoding paths are both exercised. */
    @TestFactory fun upstreamEncoding005(): List<DynamicTest> = buildList {
        var number = 0
        fun test(name: String, expected: IntArray?, action: () -> IntArray) {
            add(DynamicTest.dynamicTest("${number++} $name") {
                val result = try { action() } catch (_: GhcIOException) { null }
                if (expected == null) assertNull(result) else assertArrayEquals(expected, result)
            })
        }
        val bytes = IntArray(256) { it }
        test("char8 decode", bytes) { GhcTextEncoding.char8.decode(bytes.map(Int::toByte).toByteArray()) }
        test("char8 modulo", IntArray(513) { it and 255 }) { GhcTextEncoding.char8.encode(IntArray(513) { it }, false).map { it.toInt() and 255 }.toIntArray() }
        for (direct in listOf(true, false)) for (mode in GhcCodingFailureMode.entries) {
            val ascii = if (direct) GhcEnvironmentEncoding("ASCII", failureMode = mode) else GhcTextEncoding.mkTextEncoding("ASCII${mode.suffix}").codec
            val latin = if (direct) GhcEnvironmentEncoding("latin1", failureMode = mode) else GhcTextEncoding.mkTextEncoding("latin1${mode.suffix}").codec
            fun decode(enc: GhcCStringCodec, points: IntArray) = enc.decode(points.map(Int::toByte).toByteArray())
            fun encode(enc: GhcCStringCodec, points: IntArray) = enc.encodeCString(points, false).map { it.toInt() and 255 }.toIntArray()
            val asciiInvalid = when (mode) {
                GhcCodingFailureMode.Error -> null
                GhcCodingFailureMode.Ignore -> intArrayOf(126,127,127,126)
                GhcCodingFailureMode.Transliterate -> intArrayOf(126,127,65533,65533,65533,127,126)
                GhcCodingFailureMode.Roundtrip -> intArrayOf(126,127,0xdc80,0xdc81,0xdc80,127,126)
            }
            test("$direct $mode ASCII decode", IntArray(128) { it }) { decode(ascii, IntArray(128) { it }) }
            test("$direct $mode ASCII invalid decode", asciiInvalid) { decode(ascii, intArrayOf(126,127,128,129,128,127,126)) }
            test("$direct $mode ASCII encode", IntArray(128) { it }) { encode(ascii, IntArray(128) { it }) }
            test("$direct $mode ASCII invalid encode", when (mode) {
                GhcCodingFailureMode.Ignore -> intArrayOf(126,127,127)
                GhcCodingFailureMode.Transliterate -> intArrayOf(126,127,63,63,63,127,63)
                else -> null
            }) { encode(ascii, intArrayOf(126,127,128,129,128,127,233)) }
            if (mode == GhcCodingFailureMode.Roundtrip)
                test("$direct ASCII roundtrip", bytes) { encode(ascii, decode(ascii, bytes)) }
            test("$direct $mode Latin1 decode", bytes) { decode(latin, bytes) }
            test("$direct $mode Latin1 encode", bytes) { encode(latin, bytes) }
            test("$direct $mode Latin1 invalid encode", when (mode) {
                GhcCodingFailureMode.Ignore -> intArrayOf(254,255,255,254)
                GhcCodingFailureMode.Transliterate -> intArrayOf(254,255,63,63,63,255,254)
                else -> null
            }) { encode(latin, intArrayOf(254,255,256,257,256,255,254)) }
        }
        check(number == 60)
    }
}
