package io.heapy.kotdoc.highlighting

import io.heapy.kotdoc.options.HighlightMethod
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class HighlightStyleTest {
    private fun lines() = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/highlighting/styles.jsonl.gz"))).bufferedReader().use { it.readLines() }
    private fun JsonObject.s(key: String) = getValue(key).jsonPrimitive.content
    private fun success(value: Any): JsonObject {
        val json = when (value) {
            is HighlightColor -> value.toJson(); is TokenType -> value.toJson(); is TokenStyle -> value.toJson()
            is HighlightStyle -> value.toJson(); is HighlightMethod -> value.toJson(); else -> error(value)
        }
        return buildJsonObject {
            put("accepted", true); put("value", json); put("encoded", (if (value is TokenType) value.encodingJson() else json).toString()); put("show", value.toString())
        }
    }
    private fun colorResult(color: HighlightColor?): JsonObject = if (color == null) buildJsonObject { put("accepted", false) } else buildJsonObject {
        val (r, g, b) = color.asDoubles()
        put("result", success(color)); put("fractionBits", JsonArray(listOf(r, g, b).map { JsonPrimitive(it.toRawBits().toULong()) }))
    }
    private fun <T : Comparable<T>> order(input: JsonElement, parse: (JsonElement) -> T): JsonObject {
        val (a, b) = input.jsonArray.map(parse); val result = a.compareTo(b)
        return buildJsonObject { put("order", if (result < 0) "LT" else if (result > 0) "GT" else "EQ"); put("equal", a == b) }
    }
    private fun actual(op: String, input: JsonElement): JsonObject = try {
        when (op) {
            "color" -> success(HighlightColor.fromJson(input))
            "token" -> success(TokenType.fromJson(input))
            "token-style" -> success(TokenStyle.fromJson(input))
            "style" -> success(HighlightStyle.fromJson(input))
            "highlight" -> success(HighlightMethod.fromJson(input))
            "theme" -> success(HighlightStyle.parseTheme(input.jsonPrimitive.content))
            "builtin" -> success(HighlightStyles.all.getValue(input.jsonPrimitive.content))
            "color-string" -> colorResult(HighlightColor.fromString(input.jsonPrimitive.content))
            "color-int" -> colorResult(HighlightColor.fromInt(input.jsonPrimitive.long))
            "color-double" -> {
                val (r, g, b) = input.jsonArray.map { Double.fromBits(it.jsonPrimitive.content.toULong().toLong()) }
                colorResult(HighlightColor.fromDoubles(r, g, b))
            }
            "color-order" -> order(input, HighlightColor::fromJson)
            "token-order" -> order(input, TokenType::fromJson)
            "token-style-order" -> order(input, TokenStyle::fromJson)
            "style-order" -> order(input, HighlightStyle::fromJson)
            else -> error(op)
        }
    } catch (error: HighlightJsonException) {
        buildJsonObject { put("accepted", false); put("error", error.message) }
    }
    private fun check(c: JsonObject) {
        if (c["syntax"]?.jsonPrimitive?.boolean == true) {
            assertFalse(c.getValue("expected").jsonObject.getValue("accepted").jsonPrimitive.boolean)
            assertFailsWith<SerializationException> { actual(c.s("op"), c.getValue("input")) }
            return
        }
        val actual = actual(c.s("op"), c.getValue("input")); val expected = c.getValue("expected").jsonObject
        fun normalized(v: JsonElement): JsonElement = when (v) {
            is JsonObject -> JsonObject(v.mapValues { (key, value) -> if (key == "encoded") Json.parseToJsonElement(value.jsonPrimitive.content) else normalized(value) })
            is JsonArray -> JsonArray(v.map(::normalized))
            else -> v
        }
        // JSON object order is not semantic. The separate toJSON/toEncoding
        // values still distinguish the original TokenType representations.
        assertEquals(normalized(expected), normalized(actual))
    }
    @TestFactory fun originalStyles(): Stream<DynamicTest> = lines().drop(1).asSequence().map { line ->
        DynamicTest.dynamicTest(Json.parseToJsonElement(line).jsonObject.s("name")) { check(Json.parseToJsonElement(line).jsonObject) }
    }.asStream()
    @TestFactory fun originalPandocStyleLookup(): Stream<DynamicTest> = Json.parseToJsonElement(lines().first()).jsonObject.getValue("cliLookups").jsonArray.asSequence().map { raw ->
        DynamicTest.dynamicTest("style lookup ${raw.jsonObject.s("name")}") {
            val c = raw.jsonObject; val name = c.s("name"); var reads = 0
            if ("themeBytes" in c) {
                val directory = Files.createTempDirectory("kotdoc-theme-")
                val path = directory.resolve(name)
                try {
                    Files.write(path, Base64.getDecoder().decode(c.s("themeBytes")))
                    if ("error" in c) assertEquals(c.s("error"), assertFailsWith<HighlightStyleLookupException> {
                        HighlightStyles.lookup(path.toString())
                    }.message?.replace(path.toString(), name))
                    else assertEquals(c.getValue("value"), HighlightStyles.lookup(path.toString()).toJson())
                } finally { Files.deleteIfExists(path); Files.delete(directory) }
                return@dynamicTest
            }
            fun lookup() = HighlightStyles.lookup(name) { requested -> reads++; assertEquals(name, requested); c.s("theme") }
            if ("error" in c) assertEquals(c.s("error"), assertFailsWith<HighlightStyleLookupException> { lookup() }.message)
            else assertEquals(c.getValue("value"), lookup().toJson())
            assertEquals(if ("theme" in c) 1 else 0, reads)
        }
    }.asStream()
    @Test fun originalSimpleColorExample() = assertEquals(HighlightColor(0x15u, 0xffu, 0xa0u), HighlightColor.fromJson(JsonPrimitive("#15ffa0")))
    @Test fun originalTokenStyleExample() = assertEquals(TokenStyle(HighlightColor(0x1fu, 0x1cu, 0x1bu), bold = true), TokenStyle.fromJson(Json.parseToJsonElement("""{"text-color":"#1f1c1b","bold":true}""")))
    @Test fun originalDefaultThemeExample() {
        val path = Path.of(System.getProperty("kotdoc.root"), "vendor/skylighting-core-0.15/test/default.theme")
        assertEquals(HighlightStyles.kate, HighlightStyle.parseTheme(Files.readString(path)))
    }
    @Test fun originalKateThemeRoundtripExample() = assertEquals(HighlightStyles.kate, HighlightStyle.parseTheme(HighlightStyles.kate.toJson().toString()))
}
