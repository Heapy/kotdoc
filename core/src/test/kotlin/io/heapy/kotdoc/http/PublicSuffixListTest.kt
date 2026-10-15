package io.heapy.kotdoc.http

import io.heapy.kotdoc.layout.TextChars
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.charset.CharacterCodingException
import java.util.zip.GZIPInputStream

class PublicSuffixListTest {
    private fun text(value: JsonElement) = TextChars.fromPoints(value.jsonArray.map { it.jsonPrimitive.int }.toIntArray())
    private fun tree(value: JsonElement): PublicSuffixTree = PublicSuffixTree(
        value.jsonArray.associate { entry -> entry.jsonArray.let { text(it[0]) to tree(it[1]) } },
    )
    private fun data(value: JsonElement) = value.jsonArray.let { PublicSuffixData(tree(it[0]), tree(it[1])) }
    private fun view(tree: PublicSuffixTree): JsonArray = JsonArray(tree.children.map { (key, value) ->
        JsonArray(listOf(JsonArray(key.codePoints().toArray().map(::JsonPrimitive)), view(value)))
    })
    private fun view(data: PublicSuffixData) = JsonArray(listOf(view(data.rules), view(data.exceptions)))
    private fun bytes(bytes: ByteArray) = JsonArray(bytes.map { JsonPrimitive(it.toInt() and 255) })
    private fun observe(request: JsonObject): JsonElement = try {
        when (request.getValue("mode").jsonPrimitive.content) {
            "snapshot" -> bytes(PublicSuffixList.putDataStructure(PublicSuffixList.dataStructure))
            "snapshot-tree" -> view(PublicSuffixList.dataStructure)
            "lookup" -> {
                val domain = text(request.getValue("domain"))
                val custom = request["data"]?.let(::data)
                val effective = if (custom == null) PublicSuffixList.effectiveTLDPlusOne(domain)
                    else PublicSuffixList.effectiveTLDPlusOne(custom, domain)
                val suffix = if (custom == null) PublicSuffixList.isSuffix(domain)
                    else PublicSuffixList.isSuffix(custom, domain)
                buildJsonObject { put("effective", effective?.let(::JsonPrimitive) ?: JsonNull); put("suffix", suffix) }
            }
            "encode", "decode" -> {
                val data = request["data"]?.let(::data) ?: PublicSuffixList.getDataStructure(
                    request.getValue("bytes").jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray(),
                )
                buildJsonObject { put("bytes", bytes(PublicSuffixList.putDataStructure(data))); put("tree", view(data)) }
            }
            else -> error("Unknown original operation")
        }
    } catch (_: CharacterCodingException) {
        // The original strict Text decoder fails too; host-specific exception Show
        // and lazy evaluation order are deliberately not normalized into success.
        buildJsonObject { put("error", "decode") }
    }

    @TestFactory
    fun originalObservations(): List<DynamicTest> = GZIPInputStream(
        javaClass.getResourceAsStream("/public-suffix/data.jsonl.gz")!!,
    ).bufferedReader().use { reader ->
        reader.readLine()
        reader.lineSequence().mapIndexed { index, line ->
            val case = Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest("$index ${case.getValue("group").jsonPrimitive.content}") {
                assertEquals(case.getValue("expected"), observe(case.getValue("request").jsonObject))
            }
        }.toList()
    }

    @Test
    fun treeRetainsAnImmutableMapSnapshot() {
        val source = linkedMapOf("com" to PublicSuffixTree())
        val tree = PublicSuffixTree(source)
        source.clear()
        assertEquals(setOf("com"), tree.children.keys)
        assertThrows(UnsupportedOperationException::class.java) { (tree.children as MutableMap).clear() }
        assertEquals(PublicSuffixTree(mapOf("com" to PublicSuffixTree())), tree)
        assertEquals(PublicSuffixTree(mapOf("com" to PublicSuffixTree())).hashCode(), tree.hashCode())
    }

    @Test
    fun serializedBytesDoNotExposeMutableInternalState() {
        val bytes = PublicSuffixList.putDataStructure(PublicSuffixList.dataStructure)
        val data = PublicSuffixList.getDataStructure(bytes)
        val expected = bytes.copyOf()
        bytes.fill(0)
        assertArrayEquals(expected, PublicSuffixList.putDataStructure(data))
        assertArrayEquals(expected, PublicSuffixList.putDataStructure(PublicSuffixList.dataStructure))
    }
}
