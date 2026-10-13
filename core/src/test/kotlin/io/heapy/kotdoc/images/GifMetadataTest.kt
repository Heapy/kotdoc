// Independent oracle captures use Pandoc 3.12.1 and the original JuicyPixels GIFs.
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

class GifMetadataTest {
    @TestFactory fun `GIF metadata matches the original decoder including errors`(): Stream<DynamicTest> {
        val reader = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/gif-metadata.jsonl.gz"))).bufferedReader()
        val root = Path.of(System.getProperty("kotdoc.root"))
        return reader.lineSequence().drop(1).map { line ->
            val c = Json.parseToJsonElement(line).jsonObject
            DynamicTest.dynamicTest(c.getValue("name").jsonPrimitive.content) {
                val bytes = c["source"]?.jsonPrimitive?.content?.let { root.resolve(it).readBytes() }
                    ?: c.getValue("hex").jsonPrimitive.content.hexToByteArray()
                val size = c["size"]?.jsonArray?.map { it.jsonPrimitive.content.toBigInteger() }
                val expected = if (size != null) {
                    GifMetadata.Result.Success(ImageSize(size[0], size[1], size[2], size[3]))
                } else {
                    GifMetadata.Result.Failure(c.getValue("gifError").jsonPrimitive.content)
                }
                assertEquals(expected, GifMetadata.read(bytes), c["error"]?.jsonPrimitive?.content)
            }
        }.asStream().onClose { reader.close() }
    }

    @Test fun `direct decoder errors precede type detection and preserve signature bytes`() {
        for (length in 0..5) {
            assertEquals(GifMetadata.Result.Failure("not enough bytes"), GifMetadata.read(ByteArray(length)))
        }
        assertEquals(
            GifMetadata.Result.Failure("Invalid Gif signature : \u0000\u0001\u007f\u0080\u00fe\u00ff"),
            GifMetadata.read(byteArrayOf(0, 1, 127, -128, -2, -1)),
        )
    }

    @Test fun `many frames and extension subblocks do not consume the JVM call stack`() {
        val header = "474946383961fffffeff000000".hexToByteArray()
        val frame = "2c000000000000000000ff00".hexToByteArray()
        val output = java.io.ByteArrayOutputStream()
        output.write(header)
        repeat(20000) { output.write(frame) }
        output.write(byteArrayOf(0x21, 0xfe.toByte()))
        repeat(20000) { output.write(byteArrayOf(1, 0xff.toByte())) }
        output.write(byteArrayOf(0, 0x3b))
        // Zero local dimensions and an invalid LZW root still produce logical
        // screen metadata. No image buffers are allocated in this consumer.
        assertEquals(
            GifMetadata.Result.Success(ImageSize(65535.toBigInteger(), 65534.toBigInteger())),
            GifMetadata.read(output.toByteArray()),
        )
    }
}
