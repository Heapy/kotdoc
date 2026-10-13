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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JpegDecoderTest {
    @TestFactory fun `complete JPEG pixels match original decoder`(): Stream<DynamicTest> {
        val source = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/jpeg-decoder.jsonl.gz"))).bufferedReader()
        return source.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                val file = c["source"]?.jsonPrimitive?.content
                val input = file?.let { Path.of(System.getProperty("kotdoc.root")).resolve(it).readBytes() }
                    ?: c.getValue("hex").jsonPrimitive.content.hexToByteArray()
                val expected = c.getValue("expected").jsonObject
                val exception = expected["exception"]?.jsonPrimitive?.content
                if (exception != null && "index out of bounds" in exception) {
                    assertFailsWith<IndexOutOfBoundsException> { JpegDecoder.decode(input) }
                    return@dynamicTest
                }
                val actual = try {
                    when (val result = JpegDecoder.decode(input)) {
                        is JpgDecodeResult.Failure -> buildJsonObject { put("error", result.message) }
                        is JpgDecodeResult.Image -> buildJsonObject {
                            put("color", result.color); put("width", result.width); put("height", result.height)
                            if (file == null) put("pixels", JsonArray(result.pixels.map { JsonPrimitive(it.toInt() and 255) }))
                            else {
                                put("pixelHash", MessageDigest.getInstance("SHA-256").digest(result.pixels).toHexString())
                                put("pixelLength", result.pixels.size)
                            }
                        }
                    }
                } catch (e: IllegalStateException) {
                    buildJsonObject { put("exception", e.message) }
                } catch (e: NoSuchElementException) {
                    buildJsonObject { put("exception", "Prelude.maximum: empty list") }
                }
                assertEquals(c.getValue("expected"), actual)
            }
        }.asStream().onClose { source.close() }
    }
}
