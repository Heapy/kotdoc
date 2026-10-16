package io.heapy.kotdoc.asn1

import io.heapy.kotdoc.layout.CharacterString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream

class Asn1TypesTest {
    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.bytes(key: String) = getValue(key).jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
    private fun bytesJSON(bytes: ByteArray) = JsonArray(bytes.map { JsonPrimitive(it.toInt() and 255) })
    private fun readString(value: JsonObject) = Asn1CharacterString(Asn1StringEncoding.entries[value.getValue("encoding").jsonPrimitive.int], value.bytes("bytes"))
    private fun readBits(value: JsonObject): Asn1BitArray = if (value.text("constructor") == "raw") Asn1BitArray(value.text("length").toULong(), value.bytes("bytes")) else Asn1BitArray.fromBytes(value.bytes("bytes"), value.text("skip").toLong())
    private fun stringJSON(value: Asn1CharacterString) = buildJsonObject { put("encoding", value.encoding.name); put("bytes", bytesJSON(value.rawData)); put("shown", value.toString()) }
    private fun bitsJSON(value: Asn1BitArray) = buildJsonObject { put("length", JsonPrimitive(value.length)); put("bytes", bytesJSON(value.data)); put("shown", value.toString()) }
    private fun capture(action: () -> JsonElement): JsonElement = try { action() } catch (e: RuntimeException) { buildJsonObject { put("fatal", e.message?.lineSequence()?.first()) } }
    private fun decode(value: Asn1CharacterString, limit: Int? = null): JsonElement {
        val sequence = value.decodeLazy() ?: return JsonNull
        return JsonArray((if (limit == null) sequence else sequence.take(maxOf(0, limit))).toList().map { JsonPrimitive(it.value) })
    }
    private fun decodePoints(value: Asn1CharacterString, limit: Int? = null): JsonElement {
        val sequence = value.decodePoints() ?: return JsonNull
        return JsonArray((if (limit == null) sequence else sequence.take(maxOf(0, limit))).map(::JsonPrimitive).toList())
    }
    private fun observe(value: JsonObject): JsonElement = capture {
        when (value.text("api")) {
            "encode" -> {
                fun encoded() = Asn1CharacterString.encode(Asn1StringEncoding.entries[value.getValue("encoding").jsonPrimitive.int], CharacterString(value.getValue("points").jsonArray.map { it.jsonPrimitive.int }.toIntArray()))
                buildJsonObject { put("encoded", capture { stringJSON(encoded()) }); put("decoded", capture { decode(encoded()) }) }
            }
            "literal" -> stringJSON(Asn1CharacterString.fromString(CharacterString(value.getValue("points").jsonArray.map { it.jsonPrimitive.int }.toIntArray())))
            "decode" -> {
                val string = readString(value)
                val decoded = capture { decode(string) }
                // Both public strict and lazy views must observe the same complete String.
                assertEquals(decoded, capture { string.decode()?.let { JsonArray(it.points().map(::JsonPrimitive)) } ?: JsonNull })
                buildJsonObject {
                    put("raw", stringJSON(string)); put("decoded", decoded)
                    put("prefixes", JsonArray(value.getValue("prefixes").jsonArray.map { n -> capture { decode(string, n.jsonPrimitive.int) } }))
                    put("pointwise", capture { decodePoints(string) })
                    put("pointPrefixes", JsonArray(value.getValue("prefixes").jsonArray.map { n -> capture { decodePoints(string, n.jsonPrimitive.int) } }))
                }
            }
            "compare" -> {
                val first = readString(value.getValue("first").jsonObject); val second = readString(value.getValue("second").jsonObject)
                if (first == second) assertEquals(first.hashCode(), second.hashCode())
                buildJsonObject { put("equal", first == second); put("ordering", when { first < second -> "LT"; first > second -> "GT"; else -> "EQ" }) }
            }
            "bits" -> {
                val bits = readBits(value)
                val operations = value.getValue("operations").jsonArray.map { element ->
                    val op = element.jsonObject
                    capture {
                        val index = op.text("index").toULong()
                        when (op.text("op")) {
                            "get" -> JsonPrimitive(bits.getBit(index))
                            "set" -> bitsJSON(bits.setBit(index))
                            "clear" -> bitsJSON(bits.clearBit(index))
                            "value" -> bitsJSON(bits.setBitValue(index, op.getValue("value").jsonPrimitive.boolean))
                            else -> error(op)
                        }
                    }
                }
                val copy = Asn1BitArray(bits.length, bits.data)
                assertEquals(bits.hashCode(), copy.hashCode())
                buildJsonObject { put("raw", bitsJSON(bits)); put("operations", JsonArray(operations)); put("equal", bits == copy); put("differentLength", bits == Asn1BitArray(bits.length + 1u, bits.data)); put("differentBytes", bits == Asn1BitArray(bits.length, bits.data + byteArrayOf(0))) }
            }
            else -> error(value)
        }
    }

    @TestFactory
    fun originalObservations(): List<DynamicTest> {
        val lines = GZIPInputStream(javaClass.getResourceAsStream("/asn1-types/data.jsonl.gz")!!).bufferedReader().use { it.readLines() }
        val metadata = Json.parseToJsonElement(lines.first()).jsonObject
        assertEquals(metadata.getValue("cases").jsonPrimitive.int, lines.size - 1)
        return lines.drop(1).mapIndexed { index, line ->
            val case = Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$index ${case.text("group")}") { assertEquals(case.getValue("expected"), observe(case.getValue("request").jsonObject)) }
        }
    }

    @Test
    fun immutableDataAndTypedBounds() {
        val data = byteArrayOf(0x80.toByte(), 1)
        val bits = Asn1BitArray(9u, data)
        val string = Asn1CharacterString(Asn1StringEncoding.T61, data)
        data[0] = 0; bits.data[0] = 0; string.rawData[0] = 0
        assertTrue(bits.getBit(0u)); assertEquals(128, string.decode()!!.points()[0])
        assertTrue(bits.getBit(0u)); assertFalse(bits.clearBit(0u).getBit(0u))
        val error = assertThrows(Asn1BitArrayOutOfBound::class.java) { bits.getBit(9u) }
        assertEquals(9uL, error.index); assertEquals(Asn1BitArrayOutOfBound(9u), error)
        assertEquals(Asn1CharacterString.fromString("A😀"), Asn1CharacterString.fromString(CharacterString(intArrayOf(65, 0x1f600))))
        val oid: Asn1Oid = listOf("18446744073709551616".toBigInteger(), (-1).toBigInteger())
        val identifiable = object : Asn1Oidable { override fun getObjectId() = oid }
        val named = Asn1OidNameable<String> { if (it == oid) "value" else null }
        assertEquals("value", named.fromObjectId(identifiable.getObjectId())); assertNull(named.fromObjectId(emptyList()))
    }
}
