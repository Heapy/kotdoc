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

class JpegBlocksTest {
    @TestFactory fun `JPEG blocks match original Common and FastIdct`(): Stream<DynamicTest> {
        val source = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/jpeg-blocks.jsonl.gz"))).bufferedReader()
        return source.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                val expected = c.getValue("expected").jsonObject.getValue("values").jsonArray
                    .map { row -> row.jsonArray.map { it.jsonPrimitive.long } }
                assertEquals(expected, execute(c))
            }
        }.asStream().onClose { source.close() }
    }

    private fun execute(c: JsonObject): List<List<Long>> {
        fun ints(key: String) = c.getValue(key).jsonArray.map { it.jsonPrimitive.int }
        val op = c.getValue("op").jsonPrimitive.content
        val options = c.getValue("options").jsonArray.map { it.jsonPrimitive.long }
        val values = ints("block")
        val destination = ints("destination")
        when (op) {
            "parameter" -> {
                val parameter = JpgUnpackerParameter(values.toIntArray(), ints("quant").toIntArray(),
                    options[0], options[1], options[2], options[3], options[4] to options[5],
                    options[6] to options[7], options[8] to options[9], options[10], options[11],
                    options[12], options[13], options[14])
                return listOf(parameter.toString().map { it.code.toLong() })
            }
            "blockSize" -> return listOf(options.map(JpegBlocks::toBlockSize))
            "raster" -> return listOf(buildList {
                JpegBlocks.rasterMap(options[0], options[1]) { x, y -> add(x); add(y) }
            })
            "forwardValues" -> return listOf(JpegBlocks.zigZagForwardValues(values).map(Int::toLong))
            "forwardInt" -> {
                val b = values.toIntArray()
                val d = if (options[0] == 1L) b else destination.toIntArray()
                assertSame(d, JpegBlocks.zigZagForward(d, b))
                val genericInput = values.toMutableList()
                val genericDestination = if (options[0] == 1L) genericInput else destination.toMutableList()
                assertSame(genericDestination, JpegBlocks.zigZagForward(genericDestination, genericInput))
                assertEquals(d.toList(), genericDestination)
                assertEquals(b.toList(), genericInput)
                return listOf(d.map(Int::toLong), b.map(Int::toLong))
            }
            "forwardByte" -> {
                val b = values.map(Int::toByte).toByteArray()
                val d = if (options[0] == 1L) b else destination.map(Int::toByte).toByteArray()
                assertSame(d, JpegBlocks.zigZagForward(d, b))
                return listOf(d.map { it.toLong() and 255 }, b.map { it.toLong() and 255 })
            }
        }
        if (op in setOf("decodeInt", "unpackInt", "dc", "ac", "dcDefault", "acDefault", "restart")) {
            val reader = ImageBitReader.default().apply { setJpeg(c.getValue("hex").jsonPrimitive.content.hexToByteArray()) }
            reader.readJpegInt(options[1].toInt())
            val symbol = options[0].toInt()
            val dc = if (symbol == 0) JpegDefaultTables.dcLumaHuffmanTree else JpegDefaultTables.dcChromaHuffmanTree
            val ac = if (symbol == 0) JpegDefaultTables.acLumaHuffmanTree else JpegDefaultTables.acChromaHuffmanTree
            val tree = when (op) {
                "dcDefault" -> JpegHuffman.pack(dc)
                "acDefault" -> JpegHuffman.pack(ac)
                "dc", "ac" -> JpegHuffman.pack(HuffmanTree.Branch(HuffmanTree.Leaf(symbol), HuffmanTree.Leaf(symbol)))
                else -> intArrayOf()
            }
            val result = buildList {
                repeat(options[2].toInt()) {
                    when (op) {
                        "decodeInt" -> add(JpegBlocks.decodeInt(reader, symbol).toLong())
                        "unpackInt" -> add(JpegBlocks.unpackInt(reader, symbol).toLong())
                        "dc", "dcDefault" -> add(JpegBlocks.dcCoefficientDecode(tree, reader).toLong())
                        "ac", "acDefault" -> {
                            val (run, size) = JpegBlocks.decodeRrrrSsss(tree, reader)
                            add(run.toLong()); add(size.toLong())
                        }
                        "restart" -> add(JpegBlocks.decodeRestartInterval().toLong())
                    }
                }
                add(reader.readJpegInt(32).toLong())
            }
            return listOf(result)
        }
        val b = values.map(Int::toShort).toShortArray()
        if (op == "unpack") {
            val image = ByteArray((options[0] * options[6] * options[7]).toInt()) { options[8].toByte() }
            JpegBlocks.unpackMacroBlock(options[0], options[1], options[2], options[3], options[4], options[5],
                options[6], options[7], image, b)
            return listOf(image.map { it.toLong() and 255 })
        }
        val d = if (options[0] == 1L) b else destination.map(Int::toShort).toShortArray()
        val q = ints("quant").map(Int::toShort).toShortArray()
        val result = when (op) {
            "empty" -> JpegBlocks.empty()
            "fast" -> JpegBlocks.fastIdct(b)
            "shift" -> JpegBlocks.levelShift(b)
            "idct" -> JpegBlocks.inverseDct(b)
            "dequant" -> JpegBlocks.deQuantize(q, b)
            "zig" -> JpegBlocks.zigZagReorder(d, b)
            "forward" -> JpegBlocks.zigZagForward(d, b)
            "macro" -> JpegBlocks.decodeMacroBlock(q, d, b)
            else -> error("Unknown reference operation $op")
        }
        when (op) {
            "empty" -> { assertNotSame(result, b); assertNotSame(result, d) }
            "zig", "forward", "macro" -> assertSame(d, result)
            else -> assertSame(b, result)
        }
        return listOf(result.map(Short::toLong), b.map(Short::toLong), d.map(Short::toLong))
    }

    @Test fun `truncated unsafe original blocks have bounded JVM failures`() {
        assertFailsWith<IndexOutOfBoundsException> { JpegBlocks.fastIdct(ShortArray(63)) }
        assertFailsWith<IndexOutOfBoundsException> { JpegBlocks.levelShift(ShortArray(63)) }
        assertFailsWith<IndexOutOfBoundsException> { JpegBlocks.deQuantize(ShortArray(63), ShortArray(64)) }
        assertFailsWith<IndexOutOfBoundsException> { JpegBlocks.deQuantize(ShortArray(64), ShortArray(63)) }
        assertFailsWith<IndexOutOfBoundsException> { JpegBlocks.zigZagReorder(ShortArray(63), ShortArray(64)) }
        assertFailsWith<IndexOutOfBoundsException> { JpegBlocks.zigZagForward(ShortArray(64), ShortArray(63)) }
        assertFailsWith<IndexOutOfBoundsException> { JpegBlocks.zigZagForwardValues(List(63) { it }) }
    }

    @Test fun `invalid pixel offsets fail within JVM bounds`() {
        assertFailsWith<IndexOutOfBoundsException> {
            JpegBlocks.unpackMacroBlock(3, 1, 1, 0, -1, 0, 8, 8, ByteArray(192), ShortArray(64))
        }
    }

    @Test fun `negative signed field width is outside the supported domain`() {
        for (width in listOf(-1, Int.MIN_VALUE)) {
            assertFailsWith<IllegalArgumentException> { JpegBlocks.decodeInt(ImageBitReader.jpeg(byteArrayOf()), width) }
        }
    }

    @Test fun `huge raster traversal can be interrupted`() {
        Thread.currentThread().interrupt()
        try {
            assertFailsWith<CancellationException> { JpegBlocks.rasterMap(0, Long.MAX_VALUE) { _, _ -> fail() } }
        } finally { Thread.interrupted() }
    }
}
