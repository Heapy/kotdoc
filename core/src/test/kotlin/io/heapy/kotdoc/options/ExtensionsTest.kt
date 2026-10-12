// Original examples: pandoc-lua-engine/test/lua/module/pandoc-format.lua.
// Copyright Pandoc contributors. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.options

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

class ExtensionsTest {
    private val fixture = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/options/extensions.json")).readText()).jsonObject
    private fun cases(key: String, test: (JsonObject) -> Unit): List<DynamicTest> = fixture.getValue(key).jsonArray.mapIndexed { i, v ->
        DynamicTest.dynamicTest("$key $i: ${v.toString().take(100)}") { test(v.jsonObject) }
    }
    private fun JsonElement.strings(): List<String> = jsonArray.map { it.jsonPrimitive.content }

    @TestFactory fun `complete registry matches independent Pandoc Lua API`() = cases("formats") { c ->
        val name = c.getValue("name").jsonPrimitive.content
        val config = ExtensionRegistry.config(name)
        assertEquals(c.getValue("defaults").strings(), config.defaults.names())
        assertEquals(c.getValue("supported").strings(), config.supported.names())
        val statuses = config.supported.associate { it.wireName to (it in config.defaults) } +
            config.defaults.associate { it.wireName to true }
        assertEquals(c.getValue("config").jsonObject.mapValues { it.value.jsonPrimitive.boolean }, statuses)
    }

    @TestFactory fun `extension reading normalization and constructor order match Pandoc`() = cases("read") { c ->
        val actual = Extensions.read(c.getValue("input").strings())
        assertEquals(c.getValue("expected"), actual.toJson())
        assertEquals(actual, Extensions.fromJson(actual.toJson()))
    }

    @TestFactory fun `path and URI inference match Pandoc`() = cases("paths") { c ->
        val expected = c.getValue("expected").jsonPrimitive
        assertEquals(if (expected.isString) expected.content else null,
            FlavoredFormat.fromFilePaths(c.getValue("input").strings())?.name)
    }

    @TestFactory fun `native format specs preserve acceptance and errors`() = cases("specs") { c ->
        val spec = c.getValue("spec").jsonPrimitive.content
        val result = runCatching {
            val parsed = FlavoredFormat.parse(spec)
            // Reader dispatch precedes checking extensions, as in the original.
            if (parsed.name != "native") error("Unknown input format '${parsed.name}'")
            ExtensionRegistry.config(parsed.name).apply(parsed)
        }
        assertEquals(c.getValue("accepted").jsonPrimitive.boolean, result.isSuccess)
        if (result.isFailure) assertEquals(c.getValue("error").jsonPrimitive.content, result.exceptionOrNull()?.message)
    }

    @TestFactory fun `custom reader format modifiers match real Pandoc dispatch`() = cases("custom") { c ->
        val config = ExtensionsConfig(Extensions.of(ExtensionName.Smart), Extensions.read(listOf("smart", "citations", "custom", "east_asian_line_breaks")))
        val format = FlavoredFormat.parse("custom-reader+name.lua" + c.getValue("suffix").jsonPrimitive.content)
        assertEquals("custom-reader+name.lua", format.name)
        val actual = runCatching { config.apply(format) }
        assertEquals(c.getValue("accepted").jsonPrimitive.boolean, actual.isSuccess)
        if (actual.isSuccess) assertEquals(c.getValue("expected"), actual.getOrThrow().toJson())
        else assertEquals(c.getValue("error").jsonPrimitive.content, actual.exceptionOrNull()?.message)
    }

    @Test fun `original Lua registry examples`() {
        assertEquals(listOf("auto_identifiers"), ExtensionRegistry.defaults("docx").names())
        assertEquals(listOf("ascii_identifiers", "auto_identifiers", "citations", "east_asian_line_breaks",
            "empty_paragraphs", "gfm_auto_identifiers", "native_numbering", "styles"), ExtensionRegistry.supported("docx").sortedNames())
        val org = ExtensionRegistry.config("org")
        assertEquals(setOf("auto_identifiers", "citations", "special_strings", "task_lists"), org.defaults.names().toSet())
        assertEquals(10, org.supported.size)
    }

    @Test fun `custom constructors remain distinct even with a builtin spelling`() {
        val builtin = Extension.Builtin(ExtensionName.Smart)
        val custom = Extension.Custom("smart")
        val set = Extensions.from(listOf(custom, builtin, custom))
        assertEquals(listOf(builtin, custom), set.toList())
        assertEquals(listOf("smart", "smart"), set.names())
        assertTrue(builtin < custom)
        assertEquals(listOf("\ue000", "😀"), Extensions.from(listOf(Extension.Custom("😀"), Extension.Custom("\ue000"))).names())
        assertEquals(Extensions.from(listOf(custom)), set.disable(builtin))
    }

    @Test fun `JSON rejects nonstrings and nonarrays`() {
        for (value in listOf(JsonNull, JsonPrimitive(true), JsonPrimitive(3), JsonObject(emptyMap()), JsonArray(emptyList()))) {
            assertFailsWith<IllegalArgumentException> { Extension.fromJson(value) }
        }
        assertFailsWith<IllegalArgumentException> { Extensions.fromJson(JsonPrimitive("smart")) }
        assertFailsWith<IllegalArgumentException> { Extensions.fromJson(JsonArray(listOf(JsonNull))) }
    }

    @Test fun `diff composition has ordered overriding semantics`() {
        val smart = Extensions.of(ExtensionName.Smart)
        val on = ExtensionsDiff(enable = smart)
        val off = ExtensionsDiff(disable = smart)
        assertEquals(off, on + off)
        assertEquals(on, off + on)
        // Parsing a single spec is different: both sets retain the extension.
        for (spec in listOf("markdown+smart-smart", "markdown-smart+smart")) {
            val parsed = FlavoredFormat.parse(spec)
            assertEquals(ExtensionsDiff(smart, smart), parsed.extensions)
            assertTrue(ExtensionName.Smart in ExtensionRegistry.config("markdown").apply(parsed))
        }
    }

    @Test fun `set difference composition and comparison obey their laws`() {
        val sets = listOf(Extensions.Empty, Extensions.of(ExtensionName.Smart), Extensions.of(ExtensionName.Citations),
            Extensions.of(ExtensionName.Citations, ExtensionName.Smart), Extensions.read(listOf("custom", "😀")), ExtensionRegistry.pandocExtensions)
        val supported = sets.fold(Extensions.Empty) { a, b -> a + b }
        for (a in sets) for (b in sets) {
            val diff = ExtensionsDiff.between(a, b)
            assertEquals(b, ExtensionsConfig(a, supported).apply(FlavoredFormat("test", diff)))
            assertEquals(a + b, b + a)
            assertEquals(a, a + Extensions.Empty)
            assertEquals(Extensions.Empty, a - a)
            assertEquals(a == b, a.compareTo(b) == 0)
            for (c in sets) {
                val next = ExtensionsDiff.between(b, c)
                assertEquals(c, ExtensionsConfig(a, supported).apply(FlavoredFormat("test", diff + next)))
                val third = ExtensionsDiff.between(c, a)
                assertEquals((diff + next) + third, diff + (next + third))
            }
        }
    }

    @Test fun `file suffix splitting case and empty modifiers preserve source behavior`() {
        assertEquals(FlavoredFormat("markdown", ExtensionsDiff(enable = Extensions.of(ExtensionName.Smart))), FlavoredFormat.parse("MARKDOWN+SMART"))
        assertEquals("markdown+foo.bar", FlavoredFormat.parse("markdown+foo.bar").name)
        assertEquals("My-Script.LUA", FlavoredFormat.parse("My-Script.LUA+smart").name)
        assertEquals("οσ", FlavoredFormat.parse("ΟΣ").name)
        assertEquals("i\u0307", FlavoredFormat.parse("İ").name)
        assertEquals(Extensions.read(listOf("")), FlavoredFormat.parse("native+").extensions.enable)
        assertEquals(Extensions.of(ExtensionName.LiterateHaskell), FlavoredFormat.fromFilePaths(listOf("x.lhs"))?.extensions?.enable)
        assertNull(FlavoredFormat.fromFilePaths(listOf("x.Rmd")))
    }

    @Test fun `unknown format defaults are not forced into supported set`() {
        val unknown = ExtensionRegistry.config("unknown")
        assertEquals(Extensions.of(ExtensionName.AutoIdentifiers), unknown.defaults)
        assertEquals(Extensions.of(ExtensionName.EastAsianLineBreaks), unknown.supported)
        assertEquals(unknown.defaults, unknown.apply(FlavoredFormat("unknown")))
    }

    @Test fun `all six named presets retain their original contents`() {
        assertEquals(6, ExtensionRegistry.presets.size)
        assertEquals(ExtensionRegistry.defaults("markdown"), ExtensionRegistry.pandocExtensions)
        assertEquals(ExtensionRegistry.defaults("plain"), ExtensionRegistry.plainExtensions)
        assertEquals(ExtensionRegistry.defaults("markdown_strict"), ExtensionRegistry.strictExtensions)
        assertEquals(ExtensionRegistry.defaults("markdown_phpextra"), ExtensionRegistry.phpMarkdownExtraExtensions)
        assertEquals(ExtensionRegistry.defaults("markdown_mmd"), ExtensionRegistry.multimarkdownExtensions)
        assertEquals(ExtensionRegistry.defaults("markdown_github"), ExtensionRegistry.githubMarkdownExtensions +
            Extensions.of(ExtensionName.AllSymbolsEscapable, ExtensionName.BacktickCodeBlocks,
                ExtensionName.FencedCodeBlocks, ExtensionName.SpaceInAtxHeader, ExtensionName.IntrawordUnderscores,
                ExtensionName.ListsWithoutPrecedingBlankline, ExtensionName.ShortcutReferenceLinks))
    }

    @Test fun `exposed collection operations cannot mutate registry extension sets`() {
        val set = ExtensionRegistry.pandocExtensions
        val original = set.toList()
        val iterator = set.iterator()
        iterator.next()
        if (iterator is MutableIterator) runCatching { iterator.remove() }
        val list = set.toList()
        if (list is MutableList) runCatching { list.clear() }
        assertEquals(original, set.toList())
        assertEquals(original, ExtensionRegistry.pandocExtensions.toList())
    }
}
