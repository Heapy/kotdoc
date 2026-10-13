package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.io.path.readBytes
import kotlin.streams.asStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class JpegEncoderTest {
    @TestFactory fun `complete JPEG bytes and DCT match original encoder`(): Stream<DynamicTest> {
        val source = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/jpeg-encoder.jsonl.gz"))).bufferedReader()
        return source.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                val expected = c.getValue("expected").jsonObject
                val file = c["source"]?.jsonPrimitive?.content
                if (file != null) {
                    val decoded = assertIs<JpgDecodeResult.Image>(JpegDecoder.decode(Path.of(System.getProperty("kotdoc.root")).resolve(file).readBytes()))
                    val encoded = JpegEncoder.encode(JpgEncodingColor.valueOf(decoded.color), decoded.width, decoded.height, decoded.pixels, c.getValue("quality").jsonPrimitive.int)
                    assertEquals(expected.getValue("encodedLength").jsonPrimitive.int, encoded.size)
                    assertEquals(expected.getValue("encodedHash").jsonPrimitive.content, MessageDigest.getInstance("SHA-256").digest(encoded).toHexString())
                } else {
                    fun ints(key: String) = c.getValue(key).jsonArray.map { it.jsonPrimitive.int }
                    val values = ints("values")
                    if (c.getValue("op").jsonPrimitive.content == "dct") {
                        val samples = values.map(Int::toShort).toShortArray()
                        val work = IntArray(64) { 0x12345678 }
                        assertSame(work, JpegEncoder.fastDct(work, samples))
                        assertContentEquals(expected.getValue("coefficients").jsonArray.map { it.jsonPrimitive.int }.toIntArray(), work)
                        assertContentEquals(expected.getValue("input").jsonArray.map { it.jsonPrimitive.int.toShort() }.toShortArray(), samples)
                    } else {
                        val dims = ints("dims")
                        val dpi = c.getValue("dpi").jsonArray.map { it.jsonPrimitive.content.toULong() }
                        val exif = ints("exif")
                        // Construct pre-encoded frames only. The full typed metadata API is separate.
                        val metadata = buildList {
                            if (dpi.size == 2) add(JpgFrame.Jfif(JpgJFIFApp0(JFifUnit.JFifPixelsPerInch, (dpi[0] and 65535u).toInt(), (dpi[1] and 65535u).toInt(), null)))
                            if (exif.isNotEmpty()) add(JpgFrame.Exif(listOf(ImageFileDirectory(ExifTag.Unknown(65000), IfdType.TypeAscii, exif.size.toLong(), 0, ExifData.StringValue(exif)))))
                        }
                        val color = JpgEncodingColor.valueOf(c.getValue("color").jsonPrimitive.content)
                        val pixels = values.map(Int::toByte).toByteArray()
                        val encoded = JpegEncoder.encode(color, dims[0], dims[1], pixels, dims[2], metadata)
                        val original = expected.getValue("encoded").jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
                        assertContentEquals(original, encoded)
                        if (dims[2] == 50) assertContentEquals(original, JpegEncoder.encode(color, dims[0], dims[1], pixels, metadata = metadata))
                    }
                }
            }
        }.asStream().onClose { source.close() }
    }
}
