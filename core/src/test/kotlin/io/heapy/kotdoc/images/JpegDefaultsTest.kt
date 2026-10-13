package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread
import kotlin.streams.asStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JpegDefaultsTest {
    private fun assertZeroTableLoop(input: ByteArray) {
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        val worker = thread(isDaemon = true, name = "jpeg-missing-dht") {
            started.countDown()
            try { JpegDecoder.decode(input) } catch (failure: Throwable) { error.set(failure) }
            finally { finished.countDown() }
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS), "Decoder thread did not start")
            assertFalse(finished.await(200, TimeUnit.MILLISECONDS), "Missing DHT returned instead of traversing the original zero table: ${error.get()}")
            assertTrue(worker.stackTrace.any { it.className == JpegHuffman::class.java.name && it.methodName == "decode" }, "Decoder must be executing the zero-table traversal")
        } finally {
            worker.interrupt()
            worker.join(5000)
            assertFalse(worker.isAlive, "Decoder did not respond to interruption")
        }
        assertIs<CancellationException>(error.get())
    }
    @TestFactory fun `missing and late JPEG tables match original state`(): Stream<DynamicTest> {
        val source = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/jpeg-defaults.jsonl.gz"))).bufferedReader()
        return source.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                val input = c.getValue("hex").jsonPrimitive.content.hexToByteArray()
                val expected = c.getValue("expected").jsonObject
                if ("observation" in expected) assertZeroTableLoop(input)
                else {
                    val actual = when (val result = JpegDecoder.decode(input)) {
                        is JpgDecodeResult.Failure -> buildJsonObject { put("error", result.message) }
                        is JpgDecodeResult.Image -> buildJsonObject {
                            put("color", result.color); put("width", result.width); put("height", result.height)
                            put("pixels", JsonArray(result.pixels.map { JsonPrimitive(it.toInt() and 255) }))
                        }
                    }
                    assertEquals(expected, actual)
                }
            }
        }.asStream().onClose { source.close() }
    }
}
