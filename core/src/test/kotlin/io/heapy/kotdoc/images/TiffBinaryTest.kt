package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.io.path.readBytes
import kotlin.streams.asStream
import kotlin.test.assertEquals

class TiffBinaryTest {
    @TestFactory fun `typed TIFF and Exif match original BinaryParam`(): Stream<DynamicTest> {
        val source = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/tiff-binary.jsonl.gz"))).bufferedReader()
        return source.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                assertEquals(c.getValue("expected"), execute(c))
            }
        }.asStream().onClose { source.close() }
    }
    private fun tag(value: Long) = if (value < 0) ExifTag.Unknown((-value - 1).toInt()) else ExifTag.fromWord16(value.toInt())
    private fun exif(kind: String, ns: List<Long>): ExifData = when (kind) {
        "none" -> ExifData.None
        "long" -> ExifData.LongValue(ns[0])
        "short" -> ExifData.ShortValue(ns[0].toInt())
        "string" -> ExifData.StringValue(ns.map(Long::toInt))
        "undefined" -> ExifData.Undefined(ns.map(Long::toInt))
        "shorts" -> ExifData.Shorts(ns.map(Long::toInt))
        "longs" -> ExifData.Longs(ns)
        "rational" -> ExifData.Rational(ns[0], ns[1])
        "signed" -> ExifData.SignedRational(ns[0].toInt(), ns[1].toInt())
        "ifd" -> ExifData.Ifd(ns.zipWithNext().map { (t, v) -> tag(t) to ExifData.LongValue(v) })
        else -> error(kind)
    }
    private fun fields(c: JsonObject): List<List<ImageFileDirectory>> = c.getValue("rows").jsonArray.map { row ->
        row.jsonArray.map { item ->
            val a = item.jsonArray
            ImageFileDirectory(tag(a[0].jsonPrimitive.long), IfdType.entries[a[1].jsonPrimitive.int - 1],
                a[2].jsonPrimitive.long, a[3].jsonPrimitive.long,
                exif(a[4].jsonPrimitive.content, a[5].jsonArray.map { it.jsonPrimitive.long }))
        }
    }
    private fun bytes(bytes: ByteArray) = JsonArray(bytes.map { JsonPrimitive(it.toInt() and 255) })
    private fun suffix(bytes: ByteArray, offset: Int): JsonArray = JsonArray(listOf(JsonPrimitive(bytes.size - offset),
        bytes(bytes.copyOfRange(offset, minOf(offset + 16, bytes.size))), bytes(bytes.copyOfRange(maxOf(offset, bytes.size - 16), bytes.size))))
    private fun shown(value: Any): String = if (value is List<*>) value.joinToString(",", "[", "]") else value.toString()
    private fun value(value: Any, write: (JpegOutput) -> Unit): JsonObject = buildJsonObject {
        put("show", shown(value)); put("encoded", bytes(JpegOutput().also(write).result()))
    }
    private fun execute(c: JsonObject): JsonObject {
        val op = c.getValue("op").jsonPrimitive.content
        val ns = c.getValue("values").jsonArray.map { it.jsonPrimitive.long }
        val endian = TiffEndian.entries[c.getValue("endian").jsonPrimitive.int]
        val raw = c["source"]?.jsonPrimitive?.content?.let { Path.of(System.getProperty("kotdoc.root")).resolve(it).readBytes() }
            ?: c.getValue("hex").jsonPrimitive.content.hexToByteArray()
        val rows = fields(c)
        if (op == "tag") return buildJsonObject {
            val tags = ns.map(::tag)
            put("values", JsonArray(tags.map { JsonArray(listOf(JsonPrimitive(it.toString()), JsonPrimitive(it.code), JsonPrimitive(it.isInIFD0))) }))
            put("sorted", shown(tags.sortedWith(ExifTag::compare)))
        }
        if (op == "deepEq") {
            fun nested(leaf: Long): ExifData {
                var value: ExifData = ExifData.LongValue(leaf)
                repeat(ns[0].toInt()) { value = ExifData.Ifd(listOf(ExifTag.Known.TagExifOffset to value)) }
                return value
            }
            val left = nested(1); val right = nested(1); val different = nested(2)
            assertEquals(left.hashCode(), right.hashCode())
            return buildJsonObject { put("values", JsonArray(listOf(JsonPrimitive(left == right), JsonPrimitive(left == different)))) }
        }
        if (op == "extra") return buildJsonObject { put("code", TiffExtraSample.entries[ns[0].toInt()].code) }
        if (op == "parseExif") return buildJsonObject { put("show", JpegExif.parse(raw)?.let { "Just ${shown(it)}" } ?: "Nothing") }
        if (op == "writeExif") return buildJsonObject { put("encoded", bytes(JpegOutput().also { JpegExif.put(it, rows[0]) }.result())) }
        if (op.startsWith("write")) return when (op) {
            "writeHeader" -> { val h = TiffHeader(endian, ns[0]); value(h) { TiffBinary.put(it, h) } }
            "writeField" -> { val f = rows[0][0]; value(f) { TiffBinary.put(it, endian, f) } }
            "writeDirectory" -> value(rows[0]) { TiffBinary.putDirectory(it, endian, rows[0]) }
            "writeDocument" -> { val d = TiffDocument(TiffHeader(endian, ns[0]), rows); value(d) { TiffBinary.put(it, raw, d) } }
            "writeExtended" -> { val e = rows[0][0].extended; value(e) { TiffBinary.putExtended(it, endian, e) } }
            else -> error(op)
        }
        val input = JpegInput(raw)
        return try {
            val description = when (op) {
                "endian" -> { val e = TiffBinary.endian(input); value(e) { TiffBinary.put(it, e) } }
                "header" -> { val h = TiffBinary.header(input); value(h) { TiffBinary.put(it, h) } }
                "type" -> { val t = TiffBinary.type(input, endian); value(t) { TiffBinary.putWord(it, endian, 2, t.code.toLong()) } }
                "field" -> { val f = TiffBinary.field(input, endian); value(f) { TiffBinary.put(it, endian, f) } }
                "directory" -> { val d = TiffBinary.directory(input, endian); value(d) { TiffBinary.putDirectory(it, endian, d) } }
                "document" -> { val d = TiffBinary.document(input); value(d) { TiffBinary.put(it, byteArrayOf(), d) } }
                "extended" -> {
                    input.skip(ns[0].toInt())
                    val e = TiffBinary.extended(input, endian, ns[1], rows[0][0])
                    value(e) { TiffBinary.putExtended(it, endian, e) }
                }
                "word16", "word32", "int32" -> {
                    val size = if (op == "word16") 2 else 4
                    val n = TiffBinary.readWord(input, endian, size)
                    value(if (op == "int32") n.toInt() else n) { TiffBinary.putWord(it, endian, size, n) }
                }
                "planar", "compression", "sample", "color", "predictor" -> buildJsonObject {
                    put("code", when (op) {
                        "planar" -> TiffBinary.planar(input, ns[0]).code
                        "compression" -> TiffBinary.compression(input, ns[0]).code
                        "sample" -> TiffBinary.sampleFormat(input, ns[0]).code
                        "color" -> TiffBinary.colorspace(input, ns[0]).code
                        else -> TiffBinary.predictor(input, ns[0]).ordinal + 1
                    })
                }
                else -> error(op)
            }
            buildJsonObject {
                put("offset", input.position); put("rest", suffix(raw, input.position))
                description.forEach { (key, value) -> put(key, value) }
            }
        } catch (e: JpegBinaryException) {
            buildJsonObject { put("error", e.message); put("offset", e.offset); put("rest", suffix(raw, e.offset)) }
        }
    }
}
