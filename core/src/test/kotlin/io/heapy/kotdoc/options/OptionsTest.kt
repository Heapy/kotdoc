// Ported Options examples and independent Pandoc oracle fixtures; GPL-2.0-or-later.
package io.heapy.kotdoc.options

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.test.*

class OptionsTest {
    private val fixtures = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/options/options.json")).readText()).jsonObject

    private fun decodeChoice(field: String, input: JsonElement): JsonElement = when (field) {
        "cite_method" -> CiteMethod.fromJson(input).toJson()
        "email_obfuscation" -> ObfuscationMethod.fromJson(input).toJson()
        "track_changes" -> TrackChanges.fromJson(input).toJson()
        "wrap_text" -> WrapOption.fromJson(input).toJson()
        "top_level_division" -> TopLevelDivision.fromJson(input).toJson()
        "reference_location" -> ReferenceLocation.fromJson(input).toJson()
        "figure_caption_position", "table_caption_position" -> CaptionPosition.fromJson(input).toJson()
        "html_math_method" -> MathMethod.fromJson(input).toJson()
        else -> error(field)
    }

    @TestFactory fun `option JSON codecs match forced original marshalers`(): List<DynamicTest> =
        fixtures.getValue("choices").jsonArray.mapIndexed { i, value ->
            val c = value.jsonObject
            val field = c.getValue("field").jsonPrimitive.content
            val input = c.getValue("input")
            DynamicTest.dynamicTest("$i $field $input") {
                if (c.getValue("accepted").jsonPrimitive.boolean) {
                    assertEquals(c.getValue("expected"), decodeChoice(field, input))
                } else {
                    val failure = assertFailsWith<IllegalArgumentException> { decodeChoice(field, input) }
                    val error = c.getValue("error").jsonPrimitive.content
                    // TrackChanges' Lua peeker combines Read and JSON, hiding the JSON-specific error.
                    if (error.startsWith("failed to decode: ")) assertEquals(error.removePrefix("failed to decode: "), failure.message)
                }
            }
        }

    @TestFactory fun `full ReaderOptions JSON matches original external filter environment`(): List<DynamicTest> {
        val app = ReaderOptions(columns = 72, extensions = Extensions.of(ExtensionName.AutoIdentifiers),
            abbreviations = Path.of(System.getProperty("kotdoc.root"), "upstream/pandoc/data/abbreviations").readLines().toSet())
        return fixtures.getValue("reader").jsonArray.map { entry ->
            val c = entry.jsonObject
            val name = c.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                // Explicit typed expectations are independent of the codec under test.
                val expected = when (name) {
                    "library defaults" -> ReaderOptions()
                    "application defaults" -> app
                    "columns and tabs" -> app.copy(columns = 66, tabStop = 8)
                    "standalone" -> app.copy(standalone = true)
                    "comments and image extension" -> app.copy(stripComments = true, defaultImageExtension = "png")
                    "code classes" -> app.copy(indentedCodeClasses = listOf("kotlin", "sourceCode", "kotlin"))
                    "reject changes" -> app.copy(trackChanges = TrackChanges.RejectChanges)
                    "all changes" -> app.copy(trackChanges = TrackChanges.AllChanges)
                    "typst inputs preserve repeated keys" -> app.copy(typstInputs = listOf("unicode" to "Привет 😀", "empty" to "", "k" to "other", "k" to "v"))
                    "extensions" -> app.copy(extensions = Extensions.of(ExtensionName.AutoIdentifiers, ExtensionName.EastAsianLineBreaks))
                    "custom abbreviations" -> app.copy(abbreviations = setOf("foo.", "😀.", "\ue000."))
                    "empty abbreviations" -> app.copy(abbreviations = emptySet())
                    "large columns" -> app.copy(columns = 2147483648, tabStop = 17)
                    else -> error(name)
                }
                val originalJson = c.getValue("expected")
                assertEquals(originalJson, expected.toJson())
                assertEquals(expected, ReaderOptions.fromJson(originalJson))
            }
        }
    }

    @TestFactory fun `HTML slide tags match original deriving declaration`(): List<DynamicTest> =
        fixtures.getValue("htmlSlides").jsonArray.map { entry ->
            val c = entry.jsonObject
            val constructor = c.getValue("constructor").jsonPrimitive.content
            DynamicTest.dynamicTest(constructor) {
                val value = HTMLSlideVariant.valueOf(constructor)
                assertEquals(c.getValue("expected"), value.toJson())
                assertEquals(value, HTMLSlideVariant.fromJson(c.getValue("expected")))
                assertFailsWith<IllegalArgumentException> { HTMLSlideVariant.fromJson(JsonPrimitive(constructor)) }
            }
        }

    @Test fun `original Lua ReaderOptions construction and copying examples`() {
        assertEquals(66, ReaderOptions(columns = 66).columns)
        val original = ReaderOptions(columns = 65)
        val copy = original.copy()
        assertEquals(original, copy)
        assertEquals(65, copy.columns)
        assertEquals(80, ReaderOptions().columns)
        assertEquals(28, ReaderOptions().abbreviations.size)
    }

    @TestFactory fun `every reader JSON record field is required and nonnullable`(): List<DynamicTest> {
        val base = ReaderOptions().toJson()
        return base.keys.flatMap { key -> listOf(
            DynamicTest.dynamicTest("missing $key") {
                assertFailsWith<IllegalArgumentException> { ReaderOptions.fromJson(JsonObject(base - key)) }
            },
            DynamicTest.dynamicTest("null $key") {
                assertFails { ReaderOptions.fromJson(JsonObject(base + (key to JsonNull))) }
            },
        ) }
    }

    @Test fun `unknown fields are ignored and arrays preserve their intended collection types`() {
        val base = ReaderOptions().toJson()
        assertEquals(ReaderOptions(), ReaderOptions.fromJson(JsonObject(base + ("future-field" to JsonPrimitive(true)))))
        val value = JsonObject(base + mapOf(
            "extensions" to JsonArray(listOf(JsonPrimitive("lhs"), JsonPrimitive("literate_haskell"))),
            "abbreviations" to JsonArray(listOf(JsonPrimitive("x"), JsonPrimitive("x"))),
            "typst-inputs" to JsonArray(listOf(JsonArray(listOf(JsonPrimitive("x"), JsonPrimitive("a"))), JsonArray(listOf(JsonPrimitive("x"), JsonPrimitive("b"))))),
        ))
        val parsed = ReaderOptions.fromJson(value)
        assertEquals(setOf("x"), parsed.abbreviations)
        assertEquals(listOf("x" to "a", "x" to "b"), parsed.typstInputs)
        assertEquals(Extensions.of(ExtensionName.LiterateHaskell), parsed.extensions)
        assertTrue(parsed.isEnabled(ExtensionName.LiterateHaskell))
        assertFalse(parsed.isEnabled(Extension.read("smart")))
    }

    @Test fun `reader integers cover full Haskell Int and integral scientific JSON`() {
        val base = ReaderOptions().toJson()
        for ((encoded, expected) in listOf("9223372036854775807" to Long.MAX_VALUE, "-9223372036854775808" to Long.MIN_VALUE,
            "1e3" to 1000L, "8.0" to 8L, "-0.0" to 0L, "-2" to -2L)) {
            for (key in listOf("columns", "tab-stop")) {
                val value = ReaderOptions.fromJson(JsonObject(base + (key to Json.parseToJsonElement(encoded))))
                assertEquals(expected, if (key == "columns") value.columns else value.tabStop)
            }
        }
        for (encoded in listOf("9223372036854775808", "-9223372036854775809", "0.5", "1e100", "true", "\"80\"")) {
            assertFails { ReaderOptions.fromJson(JsonObject(base + ("columns" to Json.parseToJsonElement(encoded)))) }
        }
    }

    @Test fun `reader JSON rejects incorrect field and tuple types`() {
        val base = ReaderOptions().toJson()
        for (key in listOf("standalone", "strip-comments")) {
            for (value in listOf(JsonPrimitive(1), JsonPrimitive("true"), JsonArray(emptyList())))
                assertFails { ReaderOptions.fromJson(JsonObject(base + (key to value))) }
        }
        for (key in listOf("indented-code-classes", "abbreviations")) {
            assertFails { ReaderOptions.fromJson(JsonObject(base + (key to JsonPrimitive("x")))) }
            assertFails { ReaderOptions.fromJson(JsonObject(base + (key to JsonArray(listOf(JsonPrimitive(1)))))) }
        }
        for (pair in listOf("[]", "[\"x\"]", "[\"x\",\"y\",\"z\"]", "[\"x\",2]", "{}")) {
            assertFails { ReaderOptions.fromJson(JsonObject(base + ("typst-inputs" to JsonArray(listOf(Json.parseToJsonElement(pair)))))) }
        }
        assertFails { ReaderOptions.fromJson(JsonArray(emptyList())) }
    }

    @Test fun `math URL variants retain original noninvertible empty URL encoding`() {
        val variants = listOf(MathMethod.WebTeX(""), MathMethod.MathJax(""), MathMethod.KaTeX(""))
        val defaults = listOf(MathMethod.WebTeX(DEFAULT_WEB_TEX_URL), MathMethod.MathJax(DEFAULT_MATH_JAX_URL), MathMethod.KaTeX(DEFAULT_KA_TEX_URL))
        for ((value, default) in variants.zip(defaults)) {
            assertTrue(value.toJson() is JsonPrimitive)
            assertEquals(default, MathMethod.fromJson(value.toJson()))
            assertNotEquals(value, MathMethod.fromJson(value.toJson()))
        }
    }
}
