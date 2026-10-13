package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.io.path.readBytes
import kotlin.streams.asStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PngMetadataTest {
    @TestFactory fun `PNG fallback agrees with the original forced metadata consumer`(): Stream<DynamicTest> {
        val reader = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/png-metadata.jsonl.gz"))).bufferedReader()
        val root = Path.of(System.getProperty("kotdoc.root"))
        return reader.lineSequence().drop(1).map { line ->
            val c = Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest(c.getValue("name").jsonPrimitive.content) {
                val bytes = c["source"]?.jsonPrimitive?.content?.let {
                    root.resolve(it).readBytes().apply {
                        // Recorded oracle transformation bypasses Pandoc's fast
                        // scanner. JuicyPixels itself ignores the IHDR length.
                        byteArrayOf(137.toByte(), 80, 78, 71).copyInto(this)
                        fill(0, 8, 12)
                    }
                } ?: c.getValue("hex").jsonPrimitive.content.hexToByteArray()
                if (c["unsafeScanlines"]?.jsonPrimitive?.boolean == true) {
                    // A bounded JVM failure for an original unsafe memory read;
                    // explicitly not an assertion of portable upstream parity.
                    assertFailsWith<PngMetadata.TruncatedScanlineException> { PngMetadata.read(bytes) }
                    return@dynamicTest
                }
                when (c["exception"]?.jsonPrimitive?.content) {
                    "zlib" -> assertEquals(c.getValue("exceptionMessage").jsonPrimitive.content,
                        assertFailsWith<PngMetadata.DecompressionException> { PngMetadata.read(bytes) }.message)
                    "bitDepth" -> assertEquals("Impossible bit depth", assertFailsWith<IllegalStateException> { PngMetadata.read(bytes) }.message)
                    else -> {
                        val size = c["size"]?.jsonArray?.map { it.jsonPrimitive.content.toBigInteger() }
                        val expected = if (size == null) PngMetadata.Result.Failure(c.getValue("pngError").jsonPrimitive.content)
                            else PngMetadata.Result.Success(ImageSize(size[0], size[1], size[2], size[3]))
                        val result = when (val r = PngMetadata.read(bytes)) {
                            is PngMetadata.Result.Success -> r.copy(size = r.size.checkDpi())
                            is PngMetadata.Result.Failure -> r
                        }
                        assertEquals(expected, result, c["error"]?.jsonPrimitive?.content)
                    }
                }
            }
        }.asStream().onClose { reader.close() }
    }

    @Test fun `signature validation precedes IHDR parsing`() {
        for (n in 0..7) assertEquals(PngMetadata.Result.Failure("not enough bytes"), PngMetadata.read(ByteArray(n)))
        assertEquals(PngMetadata.Result.Failure("Invalid PNG file, signature broken"), PngMetadata.read(ByteArray(8)))
    }
}
