// Original examples: Tests.MediaBag and pandoc-mediabag.lua, GPL-2.0-or-later.
package io.heapy.kotdoc.formats

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.resources.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.*
import kotlin.test.*

class MediaOperationsTest {
    @TempDir lateinit var temp: Path
    private fun fixtures() = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/media/operations.json")).readText()).jsonObject

    @TestFactory fun `data URI creation matches original including UTF8 rejection`(): List<DynamicTest> = fixtures().getValue("uri").jsonArray.map { entry ->
        val c = entry.jsonObject
        val mime = c.getValue("mime").jsonPrimitive.content
        val bytes = c.getValue("hex").jsonPrimitive.content.hexToByteArray()
        DynamicTest.dynamicTest("MIME=$mime hex=${bytes.toHexString()}") {
            if (c.getValue("accepted").jsonPrimitive.boolean)
                assertEquals(c.getValue("expected").jsonPrimitive.content, ResourceUri.makeDataUri(mime, bytes))
            else assertFailsWith<CharacterCodingException> { ResourceUri.makeDataUri(mime, bytes) }
        }
    }

    @TestFactory fun `fill extract and ZIP match original bytes AST and reports`(): List<DynamicTest> = fixtures().getValue("extract").jsonArray.map { entry ->
        DynamicTest.dynamicTest(entry.jsonObject.getValue("name").jsonPrimitive.content) {
            val work = Files.createTempDirectory(temp, "case")
            val c = Json.parseToJsonElement(entry.toString().replace("\$TEMP", work.toString())).jsonObject
            val bag = MediaBag()
            c.getValue("seeds").jsonArray.forEach { seed ->
                val s = seed.jsonObject
                bag.insert(s.getValue("path").jsonPrimitive.content, s["mime"]?.jsonPrimitive?.content, s.getValue("hex").jsonPrimitive.content.hexToByteArray())
            }
            val local = c.getValue("local").jsonObject
            local.forEach { (name, hex) -> work.resolve(name).writeBytes(hex.jsonPrimitive.content.hexToByteArray()) }
            val reports = mutableListOf<ResourceEvent>()
            val document = PandocJson.decode(c.getValue("document"))
            val filled = MediaOperations.fill(document, bag, ResourceContext(bag, listOf(work)), reports::add)
            val result = MediaOperations.extract(c.getValue("destination").jsonPrimitive.content, filled, bag, reports::add)
            assertEquals(PandocJson.decode(c.getValue("expected")), result)
            val expectedFiles = c.getValue("files").jsonObject.mapValues { it.value.jsonPrimitive.content }
            val actualFiles = Files.walk(work).use { paths -> paths.filter(Files::isRegularFile).toList() }
                .associate { work.relativize(it).toString() to it.readBytes().toHexString() }.filterKeys { it !in local }
            assertEquals(expectedFiles, actualFiles, "all extracted bytes, including entire ZIP headers and compression")
            val expectedReports = c.getValue("messages").jsonArray.map { message ->
                val m = message.jsonObject
                fun field(name: String) = m.getValue(name).jsonPrimitive.content
                when (field("type")) {
                    "Extracting" -> ResourceEvent.Extracting(field("path"))
                    "LoadedResource" -> ResourceEvent.Loaded(field("for"), field("from"))
                    "CouldNotFetchResource" -> ResourceEvent.CouldNotFetch(field("path"), field("message"))
                    else -> error("Unmapped media report: $m")
                }
            }
            assertEquals(expectedReports, reports)
        }
    }

    @Test fun `original MediaBag sanitization canonicalization and collision examples`() {
        val bag = MediaBag()
        bag.insert("foo..bar.png", null, "contents".toByteArray())
        assertEquals("foo..bar.png", bag["foo..bar.png"]!!.path)
        bag.insert("../evil.png", null, "contents".toByteArray())
        assertFalse(".." in bag["../evil.png"]!!.path.split('/'))
        bag.insert("img/../sub/./lalune.png", null, "contents".toByteArray())
        assertEquals("sub/lalune.png", bag["sub/lalune.png"]!!.path)
        assertEquals("sub/lalune.png", bag["img/../sub/lalune.png"]!!.path)
        bag.insert("a%20b.png", null, "contents1".toByteArray())
        bag.insert("a b.png", null, "contents2".toByteArray())
        assertNotEquals(bag["a%20b.png"]!!.path, bag["a b.png"]!!.path)
    }

    @Test fun `original fill and extract regression including encoded traversal payloads`() {
        val source = Path.of(System.getProperty("kotdoc.root"), "upstream/pandoc/test")
        val paths = listOf("lalune.jpg", source.resolve("lalune.jpg").toString(),
            "data:image/png;base64,cHJpbnQgImhlbGxvIgo=;.lua+%2f%2e%2e%2f%2e%2e%2fa%2elua",
            "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",
            "DATA:image/gif;base64,dXBwZXJjYXNlIGRhdGEgdXJpIHRlc3QK")
        val bag = MediaBag()
        val doc = document(paths)
        val filled = MediaOperations.fill(doc, bag, ResourceContext(bag, listOf(source)))
        assertEquals(doc, filled)
        val foo = temp.resolve("foo")
        MediaOperations.extract(foo.toString(), filled, bag)
        for (name in listOf("lalune.jpg", "f9d88c3dbe18f6a7f5670e994a947d51216cdf0e.jpg", "2a0eaa89f43fada3e6c577beea4f2f8f53ab6a1d.png",
                            "d5fceb6532643d0d84ffe09c40c481ecdf59e15a.gif", "81c7546d23179ce1b344a763aa9038c3a8ff85d0.gif")) assertTrue(foo.resolve(name).isRegularFile(), name)
        assertFalse(temp.resolve("a.lua").exists())
        bag.clear()
        val evil = document(listOf("data:image/png;base64,cHJpbnQgInB3bmVkIgo=;.lua+%252f%252e%252e%252f%252e%252e%252fb%252elua"))
        MediaOperations.fill(evil, bag)
        MediaOperations.extract(temp.resolve("bar").toString(), evil, bag)
        assertTrue(temp.resolve("bar/772ceca21a2751863ec46cb23db0e7fc35b9cff8.png").isRegularFile())
        assertFalse(temp.resolve("b.lua").exists())
    }

    @Test fun `original Lua fill items clear and make data URI examples`() {
        val source = Path.of(System.getProperty("kotdoc.root"), "upstream/pandoc/pandoc-lua-engine/test")
        val bag = MediaBag()
        MediaOperations.fill(document(listOf("lua/module/sample.svg")), bag, ResourceContext(bag, listOf(source)))
        assertEquals("image/svg+xml", bag["lua/module/sample.svg"]!!.mimeType)
        assertTrue(bag["lua/module/sample.svg"]!!.contents.toString(Charsets.UTF_8).startsWith("<?xml"))
        bag.clear()
        assertTrue(bag.items().isEmpty())
        val input = mapOf("test.html" to ("text/html" to "<aside>Really?</aside>"),
            "test.css" to ("text/plain" to "aside { color: red; }"), "test.js" to ("application/javascript" to "alert(\"HI MOM!\")"))
        input.forEach { (name, value) -> bag.insert(name, value.first, value.second.toByteArray()) }
        assertEquals(input, bag.items().associate { it.path to (it.mimeType to it.contents.toString(Charsets.UTF_8)) })
        val uri = ResourceUri.makeDataUri("text/plain", "foo".toByteArray())
        assertTrue(uri.startsWith("data:"))
        assertEquals("text/plain", uri.substring(5, 15))
    }

    @Test fun `fill only handles recoverable resource errors and keeps successful bag entries`() {
        val bag = MediaBag()
        val reports = mutableListOf<ResourceEvent>()
        val access = ResourceAccess { url, _ -> when (url) {
            "ok" -> FetchedResource(byteArrayOf(1), "image/png")
            "http" -> throw ResourceHttpError("redirected", "HTTP failure")
            "io" -> throw ResourceIOError("file", "IO failure")
            else -> throw ResourceException("unrelated failure")
        } }
        val result = MediaOperations.fill(document(listOf("ok", "http", "io")), bag, access, reports::add)
        assertIs<Inline.Image>((result.blocks[0] as Block.Para).content.single())
        assertEquals(listOf<ResourceEvent>(ResourceEvent.CouldNotFetch("redirected", "HTTP failure\nReplacing image with description."),
            ResourceEvent.CouldNotFetch("file", "IO failure\nReplacing image with description.")), reports)
        assertNotNull(bag["ok"])
        assertFailsWith<ResourceException> { MediaOperations.fill(document(listOf("unknown")), bag, access) }
        MediaOperations.fill(document(listOf("ok")), bag, ResourceAccess { _, _ -> error("must skip existing key") })
    }

    @Test fun `write logs file errors but directory creation errors propagate`() {
        val bag = MediaBag().also { it.insert("image.png", "image/png", byteArrayOf(1)) }
        temp.resolve("image.png").createDirectory()
        val reports = mutableListOf<ResourceEvent>()
        MediaOperations.write(temp.toString(), bag, report = reports::add)
        assertIs<ResourceEvent.Extracting>(reports[0])
        assertIs<ResourceEvent.IgnoredIOError>(reports[1])
        val file = temp.resolve("file").also { it.writeText("existing") }
        assertFailsWith<ResourceIOError> { MediaOperations.write(file.toString(), bag) }
        assertFailsWith<ResourceException> { MediaOperations.write(temp.toString(), bag, "absent") }
        assertEquals("existing", file.readText())
    }

    @Test fun `written ZIP is independently readable and uses deterministic metadata`() {
        val bag = MediaBag().also {
            it.insert("b.txt", "text/plain", "repeated".repeat(100).toByteArray())
            it.insert("a.bin", null, byteArrayOf(0, 1, -1))
        }
        val archive = temp.resolve("media.zip")
        MediaOperations.extract(archive.toString(), document(listOf("b.txt")), bag)
        ZipInputStream(archive.inputStream()).use { zip ->
            assertEquals("a.bin", zip.nextEntry.name); assertContentEquals(byteArrayOf(0, 1, -1), zip.readBytes())
            val entry = zip.nextEntry
            assertEquals("b.txt", entry.name); assertEquals(8, entry.method)
            assertEquals("repeated".repeat(100), zip.readBytes().toString(Charsets.UTF_8))
            assertNull(zip.nextEntry)
        }
    }

    private fun document(paths: List<String>) = Pandoc(blocks = paths.map { Block.Para(listOf(Inline.Image(Attr(), listOf(Inline.Str("alt")), Target(it)))) })
}
