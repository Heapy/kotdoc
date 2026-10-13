package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.concurrent.CancellationException
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class JpegHuffmanTest {
    @TestFactory fun `JPEG tables match unmodified Haskell`(): Stream<DynamicTest> {
        val source = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/jpeg-tables.jsonl.gz"))).bufferedReader()
        return source.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                val expected = c.getValue("expected").jsonObject
                val error = expected["error"]?.jsonPrimitive?.content
                when {
                    error == null -> assertEquals(expected.getValue("values").jsonArray.map { it.jsonPrimitive.long }, execute(c))
                    error == "Inserting in value, shouldn't happen" ->
                        assertEquals(error, assertFailsWith<IllegalStateException> { execute(c) }.message)
                    error.contains("index out of bounds") -> assertFailsWith<IndexOutOfBoundsException> { execute(c) }
                    else -> fail("Unclassified upstream error: $error")
                }
            }
        }.asStream().onClose { source.close() }
    }

    private fun execute(c: JsonObject): List<Long> {
        val which = c.getValue("which").jsonPrimitive.content
        val op = c.getValue("op").jsonPrimitive.content
        val values = c.getValue("values").jsonArray.map { it.jsonPrimitive.int }
        val rows = when (which) {
            "dc-luma" -> JpegDefaultTables.dcLumaHuffmanTable
            "dc-chroma" -> JpegDefaultTables.dcChromaHuffmanTable
            "ac-luma" -> JpegDefaultTables.acLumaHuffmanTable
            "ac-chroma" -> JpegDefaultTables.acChromaHuffmanTable
            else -> c.getValue("rows").jsonArray.map { row -> row.jsonArray.map { it.jsonPrimitive.int } }
        }
        val quant by lazy {
            when (which) {
                "luma" -> JpegDefaultTables.lumaQuantizationTable.toShortArray()
                "chroma" -> JpegDefaultTables.chromaQuantizationTable.toShortArray()
                else -> values.map { it.toShort() }.toShortArray()
            }
        }
        val tree by lazy {
            when (which) {
                "dc-luma" -> JpegDefaultTables.dcLumaHuffmanTree
                "dc-chroma" -> JpegDefaultTables.dcChromaHuffmanTree
                "ac-luma" -> JpegDefaultTables.acLumaHuffmanTree
                "ac-chroma" -> JpegDefaultTables.acChromaHuffmanTree
                "direct" -> {
                    var index = 0
                    fun read(): HuffmanTree = when (val v = values[index++]) {
                        -2 -> HuffmanTree.Empty
                        -1 -> HuffmanTree.Branch(read(), read())
                        else -> HuffmanTree.Leaf(v)
                    }
                    read()
                }
                else -> JpegHuffman.build(rows)
            }
        }
        fun flatten(tree: HuffmanTree): List<Long> = when (tree) {
            HuffmanTree.Empty -> listOf(-2L)
            is HuffmanTree.Leaf -> listOf(tree.value.toLong())
            is HuffmanTree.Branch -> listOf(-1L) + flatten(tree.left) + flatten(tree.right)
        }
        return when (op) {
            "table" -> rows.flatMap { listOf(-1L) + it.map(Int::toLong) }
            "quant" -> quant.map(Short::toLong)
            "tree" -> flatten(tree)
            "pack" -> JpegHuffman.pack(tree).map(Int::toLong)
            "inverse" -> JpegHuffman.inverse(tree).flatMap { listOf(it.depth.toLong(), it.code.toLong()) }
            "macro" -> JpegHuffman.macroBlock(values).map(Int::toLong)
            "scale" -> JpegHuffman.scaleQuantisation(c.getValue("quality").jsonPrimitive.long, quant).map(Short::toLong)
            "decode" -> {
                val reader = ImageBitReader.default().apply { setJpeg(c.getValue("hex").jsonPrimitive.content.hexToByteArray()) }
                val packed = if (which == "packed") values.toIntArray() else JpegHuffman.pack(tree)
                List(c.getValue("count").jsonPrimitive.int) { JpegHuffman.decode(packed, reader).toLong() } + reader.readJpegInt(32).toLong()
            }
            else -> error("Unknown reference operation $op")
        }
    }

    @Test fun `unsafe original packed write has bounded JVM failure`() {
        var tree: HuffmanTree = HuffmanTree.Leaf(1)
        repeat(300) { tree = HuffmanTree.Branch(tree, HuffmanTree.Empty) }
        assertFailsWith<IndexOutOfBoundsException> { JpegHuffman.pack(tree) }
    }

    @Test fun `unsafe original packed read has bounded JVM failure`() {
        assertFailsWith<IndexOutOfBoundsException> {
            JpegHuffman.decode(intArrayOf(512, 512), ImageBitReader.jpeg(byteArrayOf()))
        }
    }

    @Test fun `cyclic malformed packed table is interruptible`() {
        Thread.currentThread().interrupt()
        try {
            assertFailsWith<CancellationException> {
                JpegHuffman.decode(intArrayOf(0, 0), ImageBitReader.jpeg(byteArrayOf()))
            }
        } finally {
            Thread.interrupted()
        }
    }
}
