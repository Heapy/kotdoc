package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.io.path.readBytes
import kotlin.streams.asStream
import kotlin.test.assertEquals

class JpegFramesTest {
    @TestFactory fun `JPEG frames match original strict and semi lazy parsers`(): Stream<DynamicTest> {
        val source = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/jpeg-frames.jsonl.gz"))).bufferedReader()
        return source.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                assertEquals(c.getValue("expected"), execute(c))
            }
        }.asStream().onClose { source.close() }
    }
    @Test fun `frame prefix does not request the scan tail and tail is memoized`() {
        var requests = 0
        val first = JpgFrame.Restart(1)
        val second = JpgFrame.Restart(2)
        val frames = JpgFrameSequence(listOf(first)) {
            requests++
            JpgFrameSequence(listOf(second))
        }
        assertEquals(listOf(first), frames.take(1))
        assertEquals(0, requests)
        assertEquals(listOf(first, second), frames.toList())
        assertEquals(listOf(first, second), frames.toList())
        assertEquals(1, requests)
    }

    @Test fun `serializing Huffman frames does not evaluate the decoder tree`() {
        val spec = JpegBinary.huffmanTable(JpegInput(byteArrayOf(0, 1) + ByteArray(15) + byteArrayOf(17)))
        val entry = JpgHuffmanEntry(spec) { error("unused decoder tree") }
        val output = JpegOutput()
        JpegFrames.put(output, JpgImage(listOf(JpgFrame.Huffman(listOf(entry)))))
        assertEquals("ffd8ffc40014000100000000000000000000000000000011ffd9", output.result().toHexString())
    }

    private fun JsonElement.ints() = jsonArray.map { it.jsonPrimitive.int }
    private fun List<Int>.bytes() = map(Int::toByte).toByteArray()
    private fun bytes(bytes: ByteArray) = JsonArray(bytes.map { JsonPrimitive(it.toInt() and 255) })
    private fun suffix(input: JpegInput) = JsonArray(listOf(JsonPrimitive(input.bytes.size - input.position),
        bytes(input.bytes.copyOfRange(input.position, minOf(input.position + 16, input.bytes.size))),
        bytes(input.bytes.copyOfRange(maxOf(input.position, input.bytes.size - 16), input.bytes.size))))
    private fun shown(value: Iterable<*>) = value.joinToString(",", "[", "]")
    private fun maybe(value: Any?) = value?.let { "Just ($it)" } ?: "Nothing"
    private fun part(row: JsonElement): JpgFrame {
        val a = row.jsonArray
        val name = a[0].jsonPrimitive.content; val n = a[1].jsonPrimitive.int
        val header = a[2].ints(); val raw = a[3].ints().bytes()
        val input = JpegInput(raw)
        return when (name) {
            "app" -> JpgFrame.App(n, JpgBytes(raw))
            "extension" -> JpgFrame.Extension(n, JpgBytes(raw))
            "adobe" -> JpgFrame.Adobe(JpgAdobeApp14(header[0], header[1], header[2], AdobeTransform.entries[header[3]]))
            "jfif" -> JpgFrame.Jfif(JpgJFIFApp0(JFifUnit.entries[header[0]], header[1], header[2], n.toLong()))
            "exif" -> JpgFrame.Exif(TiffBinary.document(input).directories[0])
            "quant" -> JpgFrame.Quant(JpegBinary.tableList(input, JpegBinary::quantTable))
            "huff" -> JpgFrame.Huffman(JpegBinary.tableList(input, JpegBinary::huffmanTable).map { JpgHuffmanEntry(it) { header } })
            "scans" -> JpgFrame.Scans(JpegBinary.kind(JpegInput(byteArrayOf(n.toByte()))), JpegBinary.frameHeader(JpegInput(header.bytes())))
            "blob" -> JpgFrame.ScanBlob(JpegBinary.scanHeader(JpegInput(header.bytes())), JpgBytes(raw))
            "restart" -> JpgFrame.Restart(n)
            else -> error(name)
        }
    }
    private fun execute(c: JsonObject): JsonObject {
        val op = c.getValue("op").jsonPrimitive.content
        val ns = c.getValue("values").jsonArray.map { it.jsonPrimitive.long }
        val file = c["source"]?.jsonPrimitive?.content
        val raw = file?.let { Path.of(System.getProperty("kotdoc.root")).resolve(it).readBytes() }
            ?: c.getValue("hex").jsonPrimitive.content.hexToByteArray()
        val input = JpegInput(raw)
        var show: String? = null
        var encoded: ByteArray? = null
        fun encode(image: JpgImage) { encoded = JpegOutput().also { JpegFrames.put(it, image) }.result() }
        return try {
            when (op) {
                "write", "writeOnly" -> {
                    val image = JpgImage(c.getValue("parts").jsonArray.map(::part))
                    if (op == "write") show = image.toString()
                    encode(image)
                }
                "printMutable" -> show = JpegFrames.printMacroBlock(ns)
                "printPure" -> show = JpegFrames.printPureMacroBlock(ns)
                "strict", "semi", "strictEncode", "semiEncode", "take", "firstInImage" -> {
                    val image = if (op.startsWith("strict")) JpegFrames.strictImage(input) else JpegFrames.image(input)
                    when (op) {
                        "take" -> { val frames = image.frames.take(ns[0].coerceAtLeast(0).toInt()); show = shown(frames); encode(JpgImage(frames)) }
                        "firstInImage" -> show = maybe((image.frames.firstOrNull { it is JpgFrame.Scans } as? JpgFrame.Scans)?.header)
                        else -> { if (!op.endsWith("Encode")) show = image.toString(); encode(image) }
                    }
                }
                "frames" -> { JpegBinary.skipUntilFrames(input); val frames = JpegFrames.frames(input); show = shown(frames); encode(JpgImage(frames)) }
                "first" -> { JpegBinary.skipUntilFrames(input); show = maybe(JpegFrames.firstHeader(input)) }
                "kinds" -> { JpegBinary.skipUntilFrames(input); show = shown(JpegFrames.kinds(input)) }
                "frame" -> { val frame = JpegFrames.frame(input, JpegBinary.kind(input)); show = maybe(frame); encode(JpgImage(listOfNotNull(frame))) }
                else -> error(op)
            }
            buildJsonObject {
                if (!op.startsWith("write") && !op.startsWith("print")) { put("offset", input.position); put("rest", suffix(input)) }
                fun output(key: String, raw: ByteArray, value: () -> JsonElement) {
                    if (file != null) {
                        put(key + "Hash", MessageDigest.getInstance("SHA-256").digest(raw).toHexString())
                        put(key + "Length", raw.size)
                    } else put(key, value())
                }
                show?.let { s -> output("show", s.toByteArray()) { JsonPrimitive(s) } }
                encoded?.let { b -> output("encoded", b) { bytes(b) } }
            }
        } catch (e: JpegBinaryException) {
            buildJsonObject { put("error", e.message); put("offset", e.offset); put("rest", suffix(input)) }
        } catch (e: IllegalStateException) {
            buildJsonObject { put("exception", e.message?.lineSequence()?.first()) }
        }
    }
}
