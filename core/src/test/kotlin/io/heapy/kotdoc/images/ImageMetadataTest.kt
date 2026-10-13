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
import kotlin.test.assertIs

class ImageMetadataTest {
    private fun JsonElement.text() = jsonPrimitive.content
    private fun JsonElement.long() = text().toBigInteger().toLong()
    private fun tag(n: Int): ExifTag = if (n < 0) ExifTag.Unknown(-n - 1) else ExifTag.fromWord16(n and 65535)
    private fun data(kind: String, values: List<Long>): ExifData = when (kind) {
        "none" -> ExifData.None
        "short" -> ExifData.ShortValue((values.single() and 65535).toInt())
        "long" -> ExifData.LongValue(values.single() and 0xffffffffL)
        "shorts" -> ExifData.Shorts(values.map { (it and 65535).toInt() })
        "longs" -> ExifData.Longs(values.map { it and 0xffffffffL })
        "string" -> ExifData.StringValue(values.map { (it and 255).toInt() })
        "undefined" -> ExifData.Undefined(values.map { (it and 255).toInt() })
        "rational" -> ExifData.Rational(values[0] and 0xffffffffL, values[1] and 0xffffffffL)
        "signed" -> ExifData.SignedRational(values[0].toInt(), values[1].toInt())
        "ifd" -> ExifData.Ifd(values.zipWithNext { a, b -> tag(a.toInt()) to ExifData.LongValue(b and 0xffffffffL) })
        else -> error(kind)
    }
    private fun entry(raw: JsonElement): ImageMetadataEntry<*> {
        val e = raw.jsonArray; val key = e[0].text(); val arg = e[1].text(); val kind = e[2].text(); val ns = e[3].jsonArray.map { it.long() }
        val str = { buildString { ns.forEach { appendCodePoint(it.toInt()) } } }
        return when (key) {
            "Gamma" -> ImageMetadataEntry(ImageMetadataKey.Gamma, Double.fromBits(ns.single()))
            "ColorSpace" -> ImageMetadataEntry(ImageMetadataKey.ColorSpace, when (arg) {
                "SRGB" -> ImageColorSpace.SRGB
                "ICCProfile" -> ImageColorSpace.ICCProfile(ns.map { (it and 255).toInt() })
                else -> ImageColorSpace.WindowsBitmapColorSpace(ns.map { (it and 255).toInt() })
            })
            "Format" -> ImageMetadataEntry(ImageMetadataKey.Format, ImageSourceFormat.entries[arg.toInt()])
            "DpiX" -> ImageMetadataEntry(ImageMetadataKey.DpiX, ns.single().toULong())
            "DpiY" -> ImageMetadataEntry(ImageMetadataKey.DpiY, ns.single().toULong())
            "Width" -> ImageMetadataEntry(ImageMetadataKey.Width, ns.single().toULong())
            "Height" -> ImageMetadataEntry(ImageMetadataKey.Height, ns.single().toULong())
            "Exif" -> ImageMetadataEntry(ImageMetadataKey.Exif(tag(arg.toInt())), data(kind, ns))
            "Unknown" -> ImageMetadataEntry(ImageMetadataKey.Unknown(arg), when (kind) {
                "int" -> ImageMetadataValue.IntValue(ns.single())
                "double" -> ImageMetadataValue.DoubleValue(Double.fromBits(ns.single()))
                else -> ImageMetadataValue.StringValue(str())
            })
            else -> {
                val k = listOf(ImageMetadataKey.Title, ImageMetadataKey.Description, ImageMetadataKey.Author, ImageMetadataKey.Copyright,
                    ImageMetadataKey.Software, ImageMetadataKey.Comment, ImageMetadataKey.Disclaimer, ImageMetadataKey.Source, ImageMetadataKey.Warning).single { it.label == key }
                ImageMetadataEntry(k, str())
            }
        }
    }
    private fun <T> insert(m: ImageMetadatas, e: ImageMetadataEntry<T>) = m.insert(e.key, e.value)
    private fun metas(es: JsonArray) = es.fold(ImageMetadatas.empty) { m, e -> insert(m, entry(e)) }
    private fun shownList(xs: List<*>) = xs.joinToString(",", "[", "]")
    private fun field(raw: JsonElement): ImageFileDirectory {
        val f = raw.jsonArray
        return ImageFileDirectory(tag(f[0].long().toInt()), IfdType.entries[f[1].long().toInt() - 1], f[2].long() and 0xffffffffL,
            f[3].long() and 0xffffffffL, data(f[4].text(), f[5].jsonArray.map { it.long() }))
    }
    private fun bytesJson(bytes: ByteArray) = JsonArray(bytes.map { JsonPrimitive(it.toInt() and 255) })
    private fun run(c: JsonObject): JsonObject = buildJsonObject {
        val file = c["source"]?.text()
        if (file != null) {
            val decoded = assertIs<JpgDecodeResult.Image>(JpegDecoder.decode(Path.of(System.getProperty("kotdoc.root")).resolve(file).readBytes()))
            val encoded = JpegEncoder.encodeWithMetadata(JpgEncodingColor.valueOf(decoded.color), decoded.width, decoded.height, decoded.pixels, 90, decoded.metadata)
            put("metadata", decoded.metadata.toString()); put("encodedLength", encoded.size)
            put("encodedHash", MessageDigest.getInstance("SHA-256").digest(encoded).toHexString())
            return@buildJsonObject
        }
        val left = c.getValue("left").jsonArray; val right = c.getValue("right").jsonArray
        val a = metas(left); val b = metas(right); val ns = c.getValue("ns").jsonArray.map { it.long() }
        fun jpeg(): ByteArray = JpegEncoder.encodeWithMetadata(JpgEncodingColor.YCbCr8, 16, 16, ByteArray(16 * 16 * 3) { if (it % 3 == 0) 255.toByte() else 128.toByte() }, 50, a)
        when (c.getValue("op").text()) {
            "values", "colors" -> {
                val values = left.map { entry(it).value }
                put("show", JsonArray(values.map { JsonPrimitive(it.toString()) }))
                put("equal", JsonArray(values.flatMap { x -> values.map { y -> JsonPrimitive(x == y) } }))
            }
            "store" -> {
                val union = a + b
                put("left", a.toString()); put("right", b.toString()); put("union", union.toString())
                put("deleted", right.fold(a) { m, e -> m.delete(entry(e).key) }.toString())
                put("lookups", JsonArray(right.map { e -> JsonPrimitive(a.lookup(entry(e).key)?.let { "Just ${metadataShow(it, 11)}" } ?: "Nothing") }))
                put("exif", union.extractExif().joinToString(",", "[", "]") { (tag, value) -> "($tag,$value)" })
                put("fold", JsonArray(union.fold(emptyList<String>()) { acc, e -> acc + e.toString() }.map(::JsonPrimitive)))
                put("foldMap", JsonArray(union.foldMap(emptyList<String>(), { x, y -> x + y }, { listOf(it.toString()) }).map(::JsonPrimitive)))
            }
            "helpers" -> {
                put("dpi", ImageMetadatas.dpi(ns[0].toULong()).toString()); put("size", ImageMetadatas.size(ns[0], ns[1]).toString())
                put("basic", ImageMetadatas.basic(ImageSourceFormat.entries[ns[4].toInt()], ns[0], ns[1]).toString())
                put("simple", ImageMetadatas.simple(ImageSourceFormat.entries[ns[4].toInt()], ns[0], ns[1], ns[2], ns[3]).toString())
                put("conversions", JsonArray(ns.take(4).map { n -> val w = n.toULong(); JsonArray(listOf(ImageMetadatas.dotsPerMeterToInch(w), ImageMetadatas.dotsPerInchToMeter(w), ImageMetadatas.dotsPerCentimeterToInch(w)).map { Json.parseToJsonElement(it.toString()) }) }))
            }
            "jfif" -> put("metadata", ImageExifMetadata.extractJfif(JpgJFIFApp0(JFifUnit.entries[ns[0].toInt()], ns[1].toInt(), ns[2].toInt(), null)).toString())
            "extract" -> put("metadata", ImageExifMetadata.extractTiff(c.getValue("fields").jsonArray.map(::field)).toString())
            "encode" -> {
                val fields = ImageExifMetadata.encodeTiff(a)
                put("fields", shownList(fields))
                val frames = ImageExifMetadata.encodeJfif(a) + if (fields.isEmpty()) emptyList() else listOf(JpgFrame.Exif(fields))
                put("frames", bytesJson(JpegOutput().also { JpegFrames.put(it, JpgImage(frames)) }.result()))
            }
            "jpeg", "originalWrite" -> put("encoded", bytesJson(jpeg()))
            "roundtrip", "pixelOnly" -> when (val result = JpegDecoder.decode(jpeg())) {
                is JpgDecodeResult.Failure -> put("error", result.message)
                is JpgDecodeResult.Image -> if (c.getValue("op").text() == "roundtrip") put("metadata", result.metadata.toString()) else put("length", result.pixels.size)
            }
            "decode", "decodePixels" -> when (val result = JpegDecoder.decode(ns.map(Long::toByte).toByteArray())) {
                is JpgDecodeResult.Failure -> put("error", result.message)
                is JpgDecodeResult.Image -> if (c.getValue("op").text() == "decode") put("metadata", result.metadata.toString()) else put("length", result.pixels.size)
            }
            "offset" -> put("field", ImageExifMetadata.exifOffsetIfd.toString())
            else -> error("Unknown operation")
        }
    }
    @TestFactory fun `typed metadata matches original JuicyPixels`(): Stream<DynamicTest> {
        val source = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/image-metadata.jsonl.gz"))).bufferedReader()
        return source.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").text()
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                val result = try { run(c) } catch (e: IllegalStateException) { buildJsonObject { put("exception", e.message) } }
                    catch (e: ArithmeticException) { buildJsonObject { put("exception", "divide by zero") } }
                assertEquals(c.getValue("expected"), result)
            }
        }.asStream().onClose { source.close() }
    }
}
