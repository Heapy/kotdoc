package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.io.path.readBytes
import kotlin.streams.asStream
import kotlin.test.*

class TiffMetadataTest {
    @TestFactory fun `TIFF metadata and strict format checks match original Pandoc`(): Stream<DynamicTest> {
        val reader = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/tiff-metadata.jsonl.gz"))).bufferedReader()
        val root = Path.of(System.getProperty("kotdoc.root"))
        return reader.lineSequence().drop(1).map { line ->
            val c = Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest(c.getValue("name").jsonPrimitive.content) {
                val bytes = c["source"]?.jsonPrimitive?.content?.let { root.resolve(it).readBytes() }
                    ?: c.getValue("hex").jsonPrimitive.content.hexToByteArray()
                if ("exception" in c) {
                    assertFailsWith<ArithmeticException> { TiffMetadata.read(bytes) }
                } else {
                    val values = c["size"]?.jsonArray?.map { it.jsonPrimitive.content.toBigInteger() }
                    val expected = if (values != null) {
                        TiffMetadata.Result.Success(ImageSize(values[0], values[1], values[2], values[3]))
                    } else TiffMetadata.Result.Failure(c.getValue("tiffError").jsonPrimitive.content)
                    val result = TiffMetadata.read(bytes)
                    val actual = if (result is TiffMetadata.Result.Success) result.copy(size = result.size.checkDpi()) else result
                    assertEquals(expected, actual, c["error"]?.jsonPrimitive?.content)
                }
            }
        }.asStream().onClose { reader.close() }
    }

    @Test fun `raw decoder checks endian and magic without Pandoc type gating`() {
        assertEquals(TiffMetadata.Result.Failure("not enough bytes"), TiffMetadata.read(byteArrayOf()))
        assertEquals(TiffMetadata.Result.Failure("not enough bytes"), TiffMetadata.read(byteArrayOf(0)))
        assertEquals(TiffMetadata.Result.Failure("Invalid endian tag value"), TiffMetadata.read(byteArrayOf(0, 0)))
        assertEquals(TiffMetadata.Result.Failure("Invalid TIFF magic number"), TiffMetadata.read("49492b00".hexToByteArray()))
        assertEquals(TiffMetadata.Result.Failure("Invalid TIFF magic number"), TiffMetadata.read("4d4d002b".hexToByteArray()))
    }

    @Test fun `original zero height and zero row recursion remains cancellable`() {
        val entries = listOf(256 to 1, 257 to 0, 258 to 8, 259 to 1, 262 to 1, 273 to 0, 277 to 1, 278 to 0, 279 to 0)
        val b = java.nio.ByteBuffer.allocate(8 + 2 + entries.size * 12 + 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.putShort(0x4949).putShort(42).putInt(8).putShort(entries.size.toShort())
        entries.forEach { (tag, value) -> b.putShort(tag.toShort()).putShort(4).putInt(1).putInt(value) }
        b.putInt(0)
        try {
            Thread.currentThread().interrupt()
            assertFailsWith<CancellationException> { TiffMetadata.read(b.array()) }
        } finally {
            Thread.interrupted()
        }
    }
}
