package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.assertEquals

class ImageBitStreamTest {
    @TestFactory fun `bit streams match the unmodified Haskell module`(): Stream<DynamicTest> {
        val reader = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/bitstream.jsonl.gz"))).bufferedReader()
        return reader.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                // JUnit retains dynamic-test closures until the suite completes.
                // Retain the compact source line, not thousands of JSON nodes.
                val c = Json.parseToJsonElement(line).jsonObject
                val mode = c.getValue("mode").jsonPrimitive.content
                val data = c.getValue("hex").jsonPrimitive.content.hexToByteArray()
                val ops = c.getValue("ops").jsonArray.map { it.jsonArray }
                if (mode == "writer") {
                    val writer = ImageBitWriter()
                    val outputs = mutableListOf<ByteArray>()
                    fun execute(op: JsonArray) {
                        val value = op[1].jsonPrimitive.long.toUInt()
                        val count = op[2].jsonPrimitive.int
                        when (op[0].jsonPrimitive.content) {
                            "jpeg" -> writer.writeJpeg(value, count)
                            "gif" -> writer.writeGif(value, count)
                            "finishJpeg" -> outputs.add(writer.finishJpeg())
                            "finishGif" -> outputs.add(writer.finishGif())
                            else -> error("unknown writer command")
                        }
                    }
                    val count = c["repeat"]?.jsonPrimitive?.int
                    if (count == null) ops.forEach(::execute)
                    else {
                        c.getValue("prefix").jsonArray.forEach { execute(it.jsonArray) }
                        repeat(count) { execute(ops.first()) }
                        ops.drop(1).forEach(::execute)
                    }
                    val hashes = c["outputHashes"]?.jsonArray
                    if (hashes == null) assertEquals(c.getValue("outputs").jsonArray.map { it.jsonPrimitive.content }, outputs.map { it.toHexString() })
                    else {
                        assertEquals(hashes.size, outputs.size)
                        for ((hash, output) in hashes.zip(outputs)) {
                            assertEquals(hash.jsonObject.getValue("length").jsonPrimitive.int, output.size)
                            assertEquals(hash.jsonObject.getValue("sha256").jsonPrimitive.content,
                                MessageDigest.getInstance("SHA-256").digest(output).toHexString())
                        }
                    }
                } else {
                    var input = when (mode) {
                        "lsb" -> ImageBitReader.lsb(data)
                        "msb" -> ImageBitReader.msb(data)
                        "jpeg" -> ImageBitReader.jpeg(data)
                        "empty" -> ImageBitReader.empty()
                        "default" -> ImageBitReader.default()
                        else -> error("unknown reader mode")
                    }
                    var saved: ImageBitReader? = null
                    val values = mutableListOf<Long>()
                    for (op in ops) {
                        val count = op[1].jsonPrimitive.int
                        val bytes = op[3].jsonPrimitive.content.hexToByteArray()
                        when (op[0].jsonPrimitive.content) {
                            "lsb" -> values.add(input.readLsb(count).toLong())
                            "msb" -> values.add(input.readMsb(count).toLong())
                            "jpeg" -> values.add(input.readJpegInt(count).toLong())
                            "bit" -> values.add(if (input.readJpegBit()) 1L else 0L)
                            "align" -> input.alignJpeg()
                            "setLsb" -> input.setLsb(bytes)
                            "setMsb" -> input.setMsb(bytes)
                            "setJpeg" -> input.setJpeg(bytes)
                            "save" -> saved = input.copy()
                            "restore" -> input = checkNotNull(saved).copy()
                            else -> error("unknown reader command")
                        }
                    }
                    assertEquals(c.getValue("values").jsonArray.map { it.jsonPrimitive.long }, values)
                }
            }
        }.asStream().onClose { reader.close() }
    }
}
