package io.heapy.kotdoc.templates

import io.heapy.kotdoc.layout.DocLayout
import io.heapy.kotdoc.resources.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.test.*

class TemplateResourcesTest {
    @TempDir lateinit var temp: Path
    private val fixtures by lazy {
        GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/templates/resources.jsonl.gz")))
            .bufferedReader().use { it.readLines().map { line -> Json.parseToJsonElement(line).jsonObject } }
    }
    private fun JsonElement.s() = jsonPrimitive.content
    private fun outcome(f: () -> String): JsonObject = try { buildJsonObject { put("value", f()) } }
        catch (e: ResourceHttpError) { buildJsonObject { put("errorKind", "http") } }
        catch (e: TemplateCompilationError) { buildJsonObject { put("error", e.detail) } }
        catch (e: ResourceException) { buildJsonObject { put("error", e.description) } }

    @TestFactory fun `original Pandoc template resources aliases decoding and compilation`(): List<DynamicTest> {
        val root = temp
        for ((name, hex) in fixtures.first().getValue("setup").jsonObject) {
            val file = root.resolve(name); Files.createDirectories(file.parent); Files.write(file, hex.s().hexToByteArray())
        }
        Files.createDirectories(root.resolve("empty-user"))
        return fixtures.drop(1).mapIndexed { index, c -> DynamicTest.dynamicTest("$index ${c.getValue("op").s()}") {
            if (c.getValue("op").s() == "path") {
                val paths = if (c.getValue("style").s() == "posix") PartialPaths.Posix else PartialPaths.Windows
                val path = c.getValue("path").s()
                val actual = buildJsonObject {
                    put("file", paths.fileName(path)); put("relative", paths.isRelative(path))
                    put("combined", paths.combine(c.getValue("directory").s(), path)); put("canonical", paths.canonical(path))
                    put("extended", paths.addExtension(path, c.getValue("directory").s()))
                }
                assertEquals(c.getValue("expected"), actual, c.toString())
            } else if (c["http"]?.jsonPrimitive?.booleanOrNull != true) {
                assertEquals(c.getValue("expected"), actual(c, root), c.toString())
            } else {
                val server = server()
                server.start()
                try {
                    val origin = "http://127.0.0.1:${server.address.port}"
                    val request = Json.parseToJsonElement(c.toString().replace("@ORIGIN@", origin)).jsonObject
                    val actual = actual(request, root)
                    val normalized = Json.parseToJsonElement(actual.toString().replace(origin, "<origin>"))
                    assertEquals(c.getValue("expected"), normalized, c.toString())
                } finally { server.stop(0) }
            }
        } }
    }
    private fun actual(c: JsonObject, root: Path): JsonElement {
        val bag = MediaBag()
        c["media"]?.jsonArray?.forEach { row -> bag.insert(row.jsonArray[0].s(), null, row.jsonArray[1].s().hexToByteArray()) }
        val resources = ResourceContext(bag,
            c["resourcePaths"]?.jsonArray?.map { root.resolve(it.s()) } ?: listOf(root.resolve("resources")),
            sourceUrl = "http://127.0.0.1:1/document.native")
        val user = root.resolve(if (c["user"]?.jsonPrimitive?.booleanOrNull == true) "user" else "empty-user")
        val templates = PandocTemplates(resources, BundledTemplateDataFiles(user))
        fun path(): String = c.getValue("path").s().replace("@WORK@", root.toString())
        fun decode(hex: String, name: String): JsonObject = outcome {
            bag.insert(name, null, hex.hexToByteArray()); templates.getTemplate(name)
        }
        val result = when (c.getValue("op").s()) {
            "default" -> outcome { templates.getDefaultTemplate(c.getValue("writer").s()) }
            "get" -> outcome { templates.getTemplate(path()) }
            "decode" -> decode(c.getValue("hex").s(), path())
            "decode-batch" -> JsonArray(c.getValue("items").jsonArray.map { decode(it.s(), "pair") })
            "compile", "compile-default" -> outcome {
                val template = if (c.getValue("op").s() == "compile-default") templates.compileDefaultTemplate(c.getValue("writer").s())
                else {
                    val text = c.getValue("text").s()
                    val compiled = if (c["path"] == null || c["path"] == JsonNull) templates.compileWithDefaultPartials("templates/default", text)
                        else templates.compileWithPartials(path(), text)
                    when (compiled) {
                        is CompiledTemplate.Failure -> throw TemplateCompilationError(compiled.error)
                        is CompiledTemplate.Success -> compiled.template
                    }
                }
                val values = TemplateValues.Text
                val context = TemplateRuntimeTest().context(JsonArray(listOf(JsonPrimitive("json"), c["context"] ?: JsonObject(emptyMap()))), values)
                val doc = TemplateRenderer.Text.render(template, context, values.context)
                DocLayout.Text.render(c["width"]?.jsonPrimitive?.longOrNull, doc)
            }
            else -> error("op: $c")
        }
        return Json.parseToJsonElement(result.toString().replace(root.toString(), "<work>"))
    }
    private fun server(): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { server ->
        server.createContext("/") { e ->
            when (e.requestURI.path) {
                "/missing/default.html5", "/missing/fallback.tpl" -> { e.sendResponseHeaders(404, -1); e.close() }
                "/redirect" -> { e.responseHeaders.add("Location", "/remote.tpl"); e.sendResponseHeaders(302, -1); e.close() }
                else -> {
                    val body = (if (e.requestURI.path == "/remote.tpl") "REMOTE\r\n" else "CHILD\r\n").toByteArray()
                    e.sendResponseHeaders(200, body.size.toLong()); e.responseBody.use { it.write(body) }
                }
            }
        }
    }

    @Test fun `default compilation keeps alias path and restricts partial effects`() {
        val bag = MediaBag().also { it.insert("templates/partial.gfm", null, "WRONG RESOURCE".toByteArray()) }
        val reads = mutableListOf<String>()
        val data = TemplateDataFiles { name -> reads.add(name); when (name) {
            "templates/default.commonmark" -> "\$partial()$".toByteArray()
            "templates/partial.gfm" -> "DATA".toByteArray()
            else -> throw TemplateDataFileNotFound(name)
        } }
        val templates = PandocTemplates(ResourceContext(bag, emptyList()), data)
        val compiled = templates.compileDefaultTemplate("gfm")
        assertEquals(listOf("templates/default.commonmark", "templates/partial.gfm"), reads)
        assertEquals("DATA", DocLayout.Text.render(null, TemplateRenderer.Text.render(compiled, Context<String>(), TemplateValues.Text.context)))
        reads.clear()
        val bad = PandocTemplates(dataFiles = TemplateDataFiles { "\$x/nope$".toByteArray() })
        val error = assertFailsWith<TemplateCompilationError> { bad.compileDefaultTemplate("html") }
        assertTrue(error.detail.startsWith("\"templates/default.html\" (line 1, column "))
        assertEquals("Error compiling template '${error.detail}'", error.description)
    }
    @Test fun `template URL suppression preserves shared resource configuration and caller state`() {
        val server = server(); server.start()
        try {
            val origin = "http://127.0.0.1:${server.address.port}"
            val resources = ResourceContext(sourceUrl = "$origin/base/document")
            val events = mutableListOf<ResourceEvent>()
            val templates = PandocTemplates(resources, report = events::add)
            assertTrue(templates.getTemplate("default.html5").contains("<html"))
            assertTrue(events.none { it is ResourceEvent.Fetching })
            assertEquals("$origin/base/document", resources.sourceUrl)
            assertEquals("CHILD\r\n", resources.fetch("image") {}.contents.toString(Charsets.UTF_8))
            assertFailsWith<TemplateDataFileNotFound> { templates.getTemplate("unknown.nope") }
            assertEquals("$origin/base/document", resources.sourceUrl)
            assertFailsWith<ResourceHttpError> { templates.getTemplate("$origin/missing/default.html5") }
            assertEquals("$origin/base/document", resources.sourceUrl)
        } finally { server.stop(0) }
    }
    @Test fun `default data uses exact original canonicalization and user guard`() {
        val user = temp.resolve("user"); Files.createDirectories(user.resolve("templates"))
        Files.writeString(user.resolve("templates/default.html5"), "USER")
        val data = BundledTemplateDataFiles(user)
        assertEquals("USER", data.read("templates/default.html5").toString(Charsets.UTF_8))
        assertContentEquals(BundledTemplateDataFiles().read("templates/default.html5"), data.read("/../templates/default.html5"))
        assertFailsWith<TemplateDataFileNotFound> { data.read("../templates/default.html5") }
        assertContentEquals(BundledTemplateDataFiles().read("templates/default.html5"), data.read("x/../templates/default.html5"))
    }
    @Test fun `absolute IO failures other than missing files do not use data fallback`() {
        val directory = Files.createDirectory(temp.resolve("default.html5"))
        val resources = ResourceContext(sourceUrl = "https://example.invalid/document")
        val templates = PandocTemplates(resources, TemplateDataFiles { error("Unexpected fallback: $it") })
        val error = assertFailsWith<ResourceIOError> { templates.getTemplate(directory.toString()) }
        assertFalse(error.cause is java.nio.file.NoSuchFileException)
        assertEquals("https://example.invalid/document", resources.sourceUrl)
    }
}
