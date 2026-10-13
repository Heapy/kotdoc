// Original examples: Tests.ImageSize, Copyright © 2025 John MacFarlane,
// GPL-2.0-or-later. Oracle capture data comes from the pinned Pandoc executable.
package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class ImageHeadersTest {
    @TestFactory fun `binary headers match the original Pandoc Lua API`(): Stream<DynamicTest> {
        val reader = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/headers.jsonl.gz"))).bufferedReader()
        return reader.lineSequence().drop(1).flatMap { line ->
            val c = Json.parseToJsonElement(line).jsonObject
            val name = c.getValue("name").jsonPrimitive.content
            val bytes = c.getValue("hex").jsonPrimitive.content.hexToByteArray()
            val format = c.getValue("format").jsonPrimitive.content.takeUnless { it == "false" }
            val detection = DynamicTest.dynamicTest("type $name") {
                assertEquals(format, ImageHeaders.imageType(bytes)?.name?.lowercase())
            }
            val scanner = c.getValue("scanner").jsonPrimitive.contentOrNull
            val sizes = if (scanner == null || format != scanner) emptySequence() else sequenceOf(DynamicTest.dynamicTest("size $name") {
                val dpi = c.getValue("dpi").jsonPrimitive.content.toBigInteger()
                val actual = when (scanner) {
                    "png" -> ImageHeaders.pngSize(bytes)
                    "jpeg" -> ImageHeaders.jpegSize(bytes)
                    "emf" -> ImageHeaders.emfSize(bytes)
                    "webp" -> ImageHeaders.webpSize(bytes,dpi)
                    "avif" -> ImageHeaders.avifSize(bytes,dpi)
                    else -> error("Unknown scanner: $scanner")
                }?.checkDpi()
                val values = c["size"]?.jsonArray?.map { it.jsonPrimitive.content.toBigInteger() }
                val expected = values?.let { ImageSize(it[0],it[1],it[2],it[3]) }
                assertEquals(expected, actual, c["error"]?.jsonPrimitive?.content)
            })
            sequenceOf(detection) + sizes
        }.asStream().onClose { reader.close() }
    }

    @Test fun `intrinsic sizes preserve arbitrary integers and exact arithmetic order`() {
        val large = BigInteger.ONE.shiftLeft(200)
        val s = ImageSize(large,300.toBigInteger(),144.toBigInteger(),100.toBigInteger())
        assertEquals(large to 300.toBigInteger(),s.sizeInPixels())
        assertEquals((large.toDouble()*72/144) to 216.0,s.sizeInPoints())
        assertEquals(300.toBigInteger() to 200.toBigInteger(),ImageSize().sizeInPixels())
        val zero = ImageSize(BigInteger.ZERO,BigInteger.ONE,BigInteger.ZERO,BigInteger.ZERO).sizeInPoints()
        assertTrue(zero.first.isNaN())
        assertEquals(Double.POSITIVE_INFINITY,zero.second)
        assertTrue(ImageSize(large,large,(-1).toBigInteger(),large).sizeInPoints().first < 0)
    }

    @Test fun `nonpositive resolution normalization is separate from scanners`() {
        val raw = ImageHeaders.webpSize("5249464600000000574542505650384c000000002f63c03100".hexToByteArray(),(-1).toBigInteger())!!
        assertEquals((-1).toBigInteger(),raw.dpiX)
        assertEquals(72.toBigInteger(),raw.checkDpi().dpiX)
        assertEquals(72.toBigInteger(),raw.copy(dpiX=BigInteger.ZERO).checkDpi().dpiX)
        val huge = BigInteger.ONE.shiftLeft(200)
        assertEquals(huge,raw.copy(dpiX=huge).checkDpi().dpiX)
    }

    @Test fun `header parsers retain source permissiveness independently of detection`() {
        // pngSize skips the signature; only imageType checks it.
        val png = "00000000000000000000000d494844520000000100000002080300000000000000".hexToByteArray()
        assertNull(ImageHeaders.imageType(png))
        assertEquals(ImageSize(BigInteger.ONE,2.toBigInteger()),ImageHeaders.pngSize(png))
        // pHYs length/CRC are not validated and no IDAT is needed for metadata.
        val phys = "000000007048597300000f6100000f6101".hexToByteArray()
        assertEquals(99.toBigInteger(),ImageHeaders.pngSize(png+phys)?.dpiX)
        // Neither a frame's declared segment length nor its components are read.
        val jpeg = "0000ffc000000800020001".hexToByteArray()
        assertNull(ImageHeaders.imageType(jpeg))
        assertEquals(ImageSize(BigInteger.ONE,2.toBigInteger()),ImageHeaders.jpegSize(jpeg))
    }

    @Test fun `AVIF searches use an iterative stack-free tail traversal`() {
        fun box(name: String, body: ByteArray): ByteArray {
            val n=body.size+8
            return byteArrayOf((n ushr 24).toByte(),(n ushr 16).toByte(),(n ushr 8).toByte(),n.toByte())+name.toByteArray()+body
        }
        val count=20000
        val b=java.io.ByteArrayOutputStream()
        b.write(box("ftyp","avif\u0000\u0000\u0000\u0000".toByteArray()))
        repeat(count) { i ->
            val n=(count-i)*8+20
            b.write(byteArrayOf((n ushr 24).toByte(),(n ushr 16).toByte(),(n ushr 8).toByte(),n.toByte()))
            b.write("ipco".toByteArray())
        }
        b.write(box("ispe","000000000000000100000002".hexToByteArray()))
        assertEquals(ImageSize(BigInteger.ONE,2.toBigInteger(),96.toBigInteger(),96.toBigInteger()),ImageHeaders.avifSize(b.toByteArray()))
    }
}
