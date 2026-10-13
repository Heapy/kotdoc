// Original Chunks/structure examples: John MacFarlane and Albert Krewinkel.
// GPL-2.0-or-later; independent Pandoc captures described in tools/import-chunk-fixtures.py.
package io.heapy.kotdoc.structure

import io.heapy.kotdoc.ast.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.IdentityHashMap
import java.util.zip.GZIPInputStream
import kotlin.test.*

class ChunksTest {
    companion object {
        private val fixtures by lazy {
            GZIPInputStream(checkNotNull(ChunksTest::class.java.getResourceAsStream("/structure/chunks.json.gz")))
                .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
        }
    }
    private fun text(s: String) = listOf(Inline.Str(s))
    private fun header(s: String, level: Long = 1, id: String = "") = Block.Header(level, Attr(id), text(s))
    private fun plain(s: String) = Block.Plain(text(s))
    private fun base(value: JsonElement): Chunk {
        val c = value.jsonObject
        return Chunk(c.getValue("heading").jsonArray.map(PandocJson::decodeInline),
            c.getValue("id").jsonPrimitive.content, c.getValue("level").jsonPrimitive.long,
            c.getValue("number").jsonPrimitive.long, c.getValue("sectionNumber").jsonPrimitive.contentOrNull,
            c.getValue("path").jsonPrimitive.content, unlisted = c.getValue("unlisted").jsonPrimitive.boolean,
            contents = c.getValue("contents").jsonArray.map(PandocJson::decodeBlock))
    }
    private fun tree(value: JsonElement): Tree<SecInfo> {
        val s = value.jsonObject
        return Tree(SecInfo(s.getValue("title").jsonArray.map(PandocJson::decodeInline),
            s.getValue("number").jsonPrimitive.contentOrNull, s.getValue("id").jsonPrimitive.content,
            s.getValue("path").jsonPrimitive.content, s.getValue("level").jsonPrimitive.long),
            s.getValue("children").jsonArray.map(::tree))
    }

    @TestFactory fun `complete chunk output and navigation snapshots match original Pandoc`(): List<DynamicTest> =
        fixtures.getValue("cases").jsonArray.map { value ->
            val c = value.jsonObject
            DynamicTest.dynamicTest(c.getValue("name").jsonPrimitive.content) {
                val actual = Chunks.splitIntoChunks(PandocJson.decode(c.getValue("document")),
                    PathTemplate(c.getValue("template").jsonPrimitive.content),
                    c.getValue("numbers").jsonPrimitive.boolean,
                    c.getValue("base").jsonPrimitive.longOrNull,
                    c.getValue("level").jsonPrimitive.long)
                val expected = c.getValue("expected").jsonObject
                assertEquals(PandocJson.decodeMeta(expected.getValue("meta")), actual.meta, "metadata")
                assertEquals(tree(expected.getValue("toc")), actual.toc, "TOC tree")
                assertEquals(PandocJson.decodeBlock(expected.getValue("tocList")), TableOfContents.tocToList(
                    actual.toc, c.getValue("numbers").jsonPrimitive.boolean, 6), "TOC list")
                val bases = expected.getValue("chunks").jsonArray
                val navigation = expected.getValue("navigation").jsonArray
                assertEquals(bases.size, actual.chunks.size, "chunk count")
                val expectedChunks = bases.map(::base)
                assertEquals(expectedChunks, actual.chunks.map { it.copy(up = null, prev = null, next = null) }, "chunk fields and contents")
                val cached = IdentityHashMap<Chunk, Chunk>()
                fun snapshot(shape: JsonElement?, chunk: Chunk?) {
                    if (shape == null || shape == JsonNull) { assertNull(chunk); return }
                    assertNotNull(chunk)
                    val n = shape.jsonObject
                    assertEquals(n.getValue("number").jsonPrimitive.long, chunk.number)
                    assertEquals(expectedChunks[chunk.number.toInt() - 1], cached.getOrPut(chunk) { chunk.copy(up = null, prev = null, next = null) }, "snapshot fields ${chunk.number}")
                    snapshot(n["up"], chunk.up)
                    snapshot(n["prev"], chunk.prev)
                    snapshot(n["next"], chunk.next)
                }
                navigation.zip(actual.chunks).forEach { (shape, chunk) -> snapshot(shape, chunk) }

                // Independent recursive JSON selection checks the query order and excluded fields.
                val bodyThenMeta = JsonArray(bases.map { it.jsonObject.getValue("contents") } + expected.getValue("meta"))
                val strings = select(bodyThenMeta, "Str").map { it.getValue("c").jsonPrimitive.content }
                assertEquals(strings, actual.queryInlines { if (it is Inline.Str) listOf(it.text) else emptyList() })
                // Format equality folds case; compare raw spellings separately.
                fun raw(tag: String) = select(bodyThenMeta, tag).map {
                    val c = it.getValue("c").jsonArray
                    c[0].jsonPrimitive.content to c[1].jsonPrimitive.content
                }
                assertEquals(raw("RawBlock"), actual.queryBlocks {
                    if (it is Block.RawBlock) listOf(it.format.value to it.text) else emptyList()
                })
                assertEquals(raw("RawInline"), actual.queryInlines {
                    if (it is Inline.RawInline) listOf(it.format.value to it.text) else emptyList()
                })
                val headers = select(bodyThenMeta, "Header").map(PandocJson::decodeBlock)
                assertEquals(headers, actual.queryBlocks { if (it is Block.Header) listOf(it) else emptyList() })
            }
        }

    private fun select(value: JsonElement, tag: String): List<JsonObject> = when (value) {
        is JsonArray -> value.flatMap { select(it, tag) }
        is JsonObject -> if (value["t"]?.jsonPrimitive?.content == tag)
            listOf(value) + value.values.flatMap { select(it, tag) }
        else value.values.flatMap { select(it, tag) }
        else -> emptyList()
    }

    @Test fun `original Lua empty result and linked chunk TOC examples`() {
        val empty: ChunkedDoc = Chunks.splitIntoChunks(Pandoc())
        assertEquals(emptyList(), empty.chunks)
        assertEquals(Tree(SecInfo(emptyList(), null, "", "#", 0)), empty.toc)
        val document = Pandoc(blocks = listOf(header("First", id = "first"), plain("body"),
            header("Subsection", 2, "subsection"), plain("body"), header("Second", id = "second"), plain("body")))
        val chunks = Chunks.splitIntoChunks(document, chunkLevel = 2)
        fun entry(title: String, id: String, number: String) = Block.Plain(listOf(
            Inline.Link(Attr("toc-$id"), text(title), Target("chunk-$number#$id"))))
        assertEquals(Block.BulletList(listOf(
            listOf(entry("First", "first", "001"), Block.BulletList(listOf(listOf(entry("Subsection", "subsection", "002"))))),
            listOf(entry("Second", "second", "003")))), TableOfContents.tocToList(chunks.toc))
        assertEquals(Block.BulletList(listOf(listOf(entry("First", "first", "001")), listOf(entry("Second", "second", "002")))),
            TableOfContents.tocToList(Chunks.splitIntoChunks(document).toc, depth = 1))
    }
    @Test fun `nested sections reserve generated identifiers before parents`() {
        val chunks = Chunks.splitIntoChunks(Pandoc(blocks = listOf(header("Same"), header("Same", 2),
            header("Same", 3), header("Same"))), chunkLevel = 3)
        assertEquals(listOf("same-2", "same-1", "same", "same-3"), chunks.chunks.map { it.id })
        val explicit = Chunks.splitIntoChunks(Pandoc(blocks = listOf(header("Same"), header("Explicit", id = "same"), header("Same"))))
        assertEquals(listOf("same", "same", "same-1"), explicit.chunks.map { it.id })
    }
    @Test fun `up navigation uses adjacent levels and does not recover an ancestor after a level decrease`() {
        val chunks = Chunks.splitIntoChunks(Pandoc(blocks = listOf(header("A",1),header("B",2),header("C",3),
            header("D",2),header("E",3))), chunkLevel = 3).chunks
        assertEquals(listOf(null,1L,2L,null,4L), chunks.map { it.up?.number })
        assertEquals(listOf(null,1L,2L,3L,4L), chunks.map { it.prev?.number })
        assertEquals(listOf(2L,3L,4L,5L,null), chunks.map { it.next?.number })
        val second = assertNotNull(chunks.first().next)
        assertEquals(1L, second.prev?.number)
        assertNull(second.next)
        assertNull(second.prev?.prev)
        assertNull(second.prev?.next)
        assertNull(second.up?.next)
    }
    @Test fun `walk and query preserve the original distinct scopes and order`() {
        val untouched = Chunk(text("navigation heading"), "nav", 0, 99, null, "nav", contents = listOf(plain("navigation contents")))
        val first = Chunk(text("first heading"), "first", 1, 1, null, "first", next = untouched, contents = listOf(plain("first contents")))
        val second = Chunk(text("second heading"), "second", 1, 2, null, "second", prev = untouched, contents = listOf(plain("second contents")))
        val toc = Tree(SecInfo(text("toc contents"), "1", "toc", "toc", 1))
        val document = ChunkedDoc(mapOf("key" to MetaValue.MetaInlines(text("meta contents"))), toc, listOf(first, second))
        val order = mutableListOf<String>()
        val changed = document.walk(object : PandocVisitor() {
            override fun transformInline(value: Inline): Inline {
                if (value !is Inline.Str) return value
                order.add(value.text)
                return value.copy(text = value.text.uppercase())
            }
        })
        assertEquals(listOf("meta contents", "first contents", "second contents"), order)
        assertEquals(listOf("first contents", "second contents", "meta contents"), document.queryInlines {
            if (it is Inline.Str) listOf(it.text) else emptyList()
        })
        assertEquals(listOf("first contents"), first.queryInlines { if (it is Inline.Str) listOf(it.text) else emptyList() })
        assertEquals(listOf("toc contents"), toc.value.queryInlines { if (it is Inline.Str) listOf(it.text) else emptyList() })
        assertSame(toc, changed.toc)
        assertSame(untouched, changed.chunks.first().next)
        assertSame(first.heading, changed.chunks.first().heading)
        assertEquals(listOf(plain("FIRST CONTENTS")), changed.chunks.first().contents)
        assertEquals(listOf(plain("first contents")), first.contents)
        val blocks = document.copy(meta = mapOf("key" to MetaValue.MetaBlocks(listOf(header("meta")))))
        val visited = mutableListOf<String>()
        blocks.walk(object : PandocVisitor() {
            override fun visitBlock(value: Block) { visited += value::class.simpleName!! }
        })
        assertEquals(listOf("Header", "Plain", "Plain"), visited)
        assertEquals(listOf("Plain", "Plain", "Header"), blocks.queryBlocks { listOf(it::class.simpleName!!) })
    }
    @Test fun `reference rewriting excludes copied headings TOC and image targets`() {
        val target = Inline.Link(Attr(), text("link"), Target("#second", "title"))
        val image = Inline.Image(Attr(), listOf(target), Target("#second", "image title"))
        val document = Pandoc(mapOf("title" to MetaValue.MetaInlines(listOf(target))), listOf(
            Block.Header(1, Attr("first"), listOf(target)), Block.Para(listOf(image)), header("Second", id = "second")))
        val result = Chunks.splitIntoChunks(document)
        val rewritten = target.copy(target = target.target.copy(url = "chunk-002#second"))
        assertEquals(listOf(rewritten), (result.meta.getValue("title") as MetaValue.MetaInlines).content)
        assertEquals(listOf(target), result.chunks.first().heading)
        assertEquals(listOf(target), result.toc.children.first().value.title)
        val actualImage = result.chunks.first().queryInlines { if (it is Inline.Image) listOf(it) else emptyList() }.single()
        assertEquals(image.target, actualImage.target)
        assertEquals(listOf(rewritten), actualImage.content)
        assertEquals(result.chunks.first().contents, result.chunks[1].prev?.contents)
    }
    @Test fun `duplicate identifiers prefer earlier chunks and preserve a same-chunk path prefix`() {
        val link = Inline.Link(Attr(), text("link"), Target("#dup"))
        val result = Chunks.splitIntoChunks(Pandoc(blocks = listOf(
            header("One", id = "dup"), Block.Para(listOf(link)), header("Two", id = "dup"), Block.Para(listOf(link)))))
        assertEquals(listOf("chunk-001#dup", "chunk-001#dup"), result.queryInlines {
            if (it is Inline.Link) listOf(it.target.url) else emptyList()
        })
        assertEquals(listOf("chunk-001#dup", "chunk-001#dup"), result.toc.children.map { it.value.path })
    }
    @Test fun `many sibling sections preserve order and numbering past three digits`() {
        val chunks = Chunks.splitIntoChunks(Pandoc(blocks = (1..1001).map { header("Section $it", id = "id$it") })).chunks
        assertEquals(1001, chunks.size)
        assertEquals("chunk-001", chunks.first().path)
        assertEquals("chunk-1001", chunks.last().path)
        assertEquals(1000L, chunks.last().prev?.number)
        assertNull(chunks.last().next)
        assertEquals((1L..1001L).toList(), chunks.map { it.number })
    }
}
