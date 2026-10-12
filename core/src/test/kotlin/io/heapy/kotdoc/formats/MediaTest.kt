package io.heapy.kotdoc.formats

import io.heapy.kotdoc.resources.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.io.path.*
import kotlin.test.*

class MediaTest {
    @TempDir lateinit var temp: Path
    private fun fixtures() = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/media/oracle.json")).readText()).jsonObject

    @TestFactory
    fun `MediaBag paths and full MIME registry match upstream`(): List<DynamicTest> = fixtures().getValue("media").jsonArray.map { entry ->
        val f=entry.jsonObject
        DynamicTest.dynamicTest(f.getValue("name").jsonPrimitive.content) {
            val bag=MediaBag()
            val path=f.getValue("path").jsonPrimitive.content
            bag.insert(path,f["mime"]?.jsonPrimitive?.content,"oracle".toByteArray())
            val expected=f.getValue("expected").jsonArray.single().jsonObject
            val item=bag.items().single()
            assertEquals(expected.getValue("path").jsonPrimitive.content,item.path,"extraction path")
            assertEquals(expected.getValue("type").jsonPrimitive.content,item.mimeType,"MIME")
            assertEquals(expected.getValue("length").jsonPrimitive.int,item.size)
            assertEquals(f.getValue("lookup_mime").jsonPrimitive.content,bag[path]?.mimeType)
            assertContentEquals("oracle".toByteArray(),bag[path]?.contents)
        }
    }

    @TestFactory
    fun `data URI fetching preserves original binary octets`(): List<DynamicTest> = fixtures().getValue("data").jsonArray.map { entry ->
        val f=entry.jsonObject
        val url=f.getValue("url").jsonPrimitive.content
        DynamicTest.dynamicTest(url) {
            val value=ResourceContext(resourcePaths=emptyList()).fetch(url) { error("Data URI must not do IO: $it") }
            assertEquals(f.getValue("mime").jsonPrimitive.content,value.mimeType)
            assertEquals(f.getValue("hex").jsonPrimitive.content,value.contents.toHexString())
        }
    }

    // Translated insert/delete/items/lookup examples from pandoc-mediabag.lua.
    @Test fun `original insert and idempotence cases`() {
        val bag=MediaBag()
        assertTrue(bag.items().isEmpty())
        repeat(2) { bag.insert("media/hello.txt","text/plain","Hello, World!".toByteArray()) }
        val item=bag.items().single()
        assertEquals("media/hello.txt",item.path)
        assertEquals("text/plain",item.mimeType)
        assertEquals(13,item.size)
    }
    @Test fun `original deletion and lookup cases`() {
        val bag=MediaBag()
        bag.insert("test.html","text/html","<aside>Who cares?</aside>".toByteArray())
        bag.insert("test.css","text/plain","aside { color: red; }".toByteArray())
        assertEquals(2,bag.items().size)
        assertEquals("<aside>Who cares?</aside>",bag["test.html"]!!.contents.toString(Charsets.UTF_8))
        bag.delete("test.html")
        assertEquals("test.css",bag.items().single().path)
        assertEquals(21,bag.items().single().size)
    }
    @Test fun `canonical keys replace equivalent names and map union is left biased`() {
        val bag=MediaBag()
        bag.insert("a/../b.txt",null,"first".toByteArray())
        bag.insert("./b.txt",null,"second".toByteArray())
        assertEquals(1,bag.items().size)
        val right=MediaBag().also { it.insert("b.txt",null,"right".toByteArray()) }
        assertEquals("second",(bag+right)["b.txt"]!!.contents.toString(Charsets.UTF_8))
        bag.delete("x/../b.txt")
        assertTrue(bag.items().isEmpty())
    }
    @Test fun `media contents cannot be mutated through caller arrays`() {
        val bytes=byteArrayOf(1,2,3)
        val bag=MediaBag()
        bag.insert("x",null,bytes)
        bytes[0]=9
        bag["x"]!!.contents[1]=9
        assertContentEquals(byteArrayOf(1,2,3),bag["x"]!!.contents)
    }
    @Test fun `original sample SVG fetch uses resource paths`() {
        val source=Path.of(System.getProperty("kotdoc.root"),"upstream/pandoc/pandoc-lua-engine/test")
        val events=mutableListOf<ResourceEvent>()
        val value=ResourceContext(resourcePaths=listOf(source)).fetch("lua/module/sample.svg",events::add)
        assertEquals("image/svg+xml",value.mimeType)
        assertTrue(value.contents.toString(Charsets.UTF_8).startsWith("<?xml"))
        assertEquals(1,events.size)
        assertIs<ResourceEvent.Loaded>(events.single())
    }
    @Test fun `resource paths search in order and media bag has priority`() {
        val first=temp.resolve("one").createDirectory()
        val second=temp.resolve("two").createDirectory()
        first.resolve("a.png").writeBytes(byteArrayOf(1))
        second.resolve("a.png").writeBytes(byteArrayOf(2))
        second.resolve("b.jpg").writeBytes(byteArrayOf(3))
        val bag=MediaBag()
        val context=ResourceContext(bag,listOf(first,second))
        assertContentEquals(byteArrayOf(1),context.fetch("a.png") {}.contents)
        assertEquals("image/jpeg",context.fetch("b.jpg") {}.mimeType)
        bag.insert("a.png","custom/type",byteArrayOf(4))
        assertEquals("custom/type",context.fetch("a.png") { error("Bag hit must not read IO") }.mimeType)
        val error=assertFailsWith<ResourceException> { context.fetch("missing.png") {} }
        assertEquals("PandocResourceNotFound \"missing.png\"",error.description)
    }
    @Test fun `HTTP relative resolution redirects custom headers gzip and missing MIME`() {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/base/image") { exchange ->
            assertEquals("yes",exchange.requestHeaders.getFirst("X-Kotdoc-Test"))
            val bytes=java.io.ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write("image".toByteArray()) } }.toByteArray()
            exchange.responseHeaders.add("Content-Type","image/png; charset=binary")
            exchange.responseHeaders.add("Content-Encoding","gzip")
            exchange.sendResponseHeaders(200,bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/redirect") { exchange -> exchange.responseHeaders.add("Location","/base/image"); exchange.sendResponseHeaders(302,-1); exchange.close() }
        server.createContext("/no-mime") { exchange -> exchange.sendResponseHeaders(200,1); exchange.responseBody.use { it.write(1) } }
        server.createContext("/missing") { exchange -> exchange.sendResponseHeaders(404,-1); exchange.close() }
        server.start()
        try {
            val origin="http://127.0.0.1:${server.address.port}"
            val events=mutableListOf<ResourceEvent>()
            val context=ResourceContext(sourceUrl="$origin/base/document",requestHeaders=listOf("X-Kotdoc-Test" to "yes"))
            assertEquals("image",context.fetch("image",events::add).contents.toString(Charsets.UTF_8))
            assertEquals("image/png; charset=binary",context.fetch("/redirect",events::add).mimeType)
            assertNull(context.fetch("/no-mime",events::add).mimeType)
            assertFailsWith<ResourceException> { context.fetch("/missing",events::add) }
            assertEquals(4,events.size)
        } finally { server.stop(0) }
    }
}
