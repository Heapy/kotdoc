package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class PngEncoderTest {
    private fun JsonObject.s(key: String) = getValue(key).jsonPrimitive.content
    private fun lines() = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/png-encoder.jsonl.gz"))).bufferedReader().use { it.readLines().drop(1) }
    private fun metadata(c: JsonObject): ImageMetadatas {
        if (c.s("mode") == "bad-metadata") error("metadata forced")
        return c.getValue("entries").jsonArray.fold(ImageMetadatas.empty) { m, raw ->
            val e = raw.jsonArray; val key = e[0].jsonPrimitive.content; val arg = e[1].jsonPrimitive.content; val kind = e[2].jsonPrimitive.content
            val ns = e[3].jsonArray.map { it.jsonPrimitive.content.toULong() }
            fun str() = buildString { ns.forEach { appendCodePoint(it.toInt()) } }
            when (key) {
                "Gamma" -> m.insert(ImageMetadataKey.Gamma, Double.fromBits(ns.single().toLong()))
                "DpiX" -> m.insert(ImageMetadataKey.DpiX, ns.single())
                "DpiY" -> m.insert(ImageMetadataKey.DpiY, ns.single())
                "Width" -> m.insert(ImageMetadataKey.Width, ns.single())
                "Height" -> m.insert(ImageMetadataKey.Height, ns.single())
                "Format" -> m.insert(ImageMetadataKey.Format, ImageSourceFormat.SourceJpeg)
                "ColorSpace" -> m.insert(ImageMetadataKey.ColorSpace, ImageColorSpace.SRGB)
                "Exif" -> m.insert(ImageMetadataKey.Exif(ExifTag.Unknown(0)), ExifData.None)
                "Unknown" -> m.insert(ImageMetadataKey.Unknown(arg), if (kind == "int") ImageMetadataValue.IntValue(ns.single().toLong()) else ImageMetadataValue.StringValue(str()))
                else -> {
                    val k = when (key) {
                        "Title" -> ImageMetadataKey.Title; "Author" -> ImageMetadataKey.Author; "Description" -> ImageMetadataKey.Description
                        "Copyright" -> ImageMetadataKey.Copyright; "Software" -> ImageMetadataKey.Software; "Comment" -> ImageMetadataKey.Comment
                        "Disclaimer" -> ImageMetadataKey.Disclaimer; "Source" -> ImageMetadataKey.Source; "Warning" -> ImageMetadataKey.Warning
                        else -> error(key)
                    }
                    m.insert(k, str())
                }
            }
        }
    }
    private fun result(out: PngDecoder.Result<Lazy<ByteArray>>, observe: String): Map<String, Any> = when (out) {
        is PngDecoder.Result.Failure -> mapOf("error" to out.message)
        is PngDecoder.Result.Success -> if (observe == "either") emptyMap() else mapOf("hex" to out.value.value)
    }
    private fun <P : Any, C : Any, A : Any> typed(codec: RasterCodec<P, C, A>, wrap: (Lazy<RasterImage<P, C, A>>) -> DynamicRaster, c: JsonObject): Map<String, Any> {
        val p = c.getValue("params").jsonArray.map { it.jsonPrimitive.long }; val mode = c.s("mode")
        val values = c.getValue("values").jsonArray.map { it.jsonPrimitive.content.toULong() }
        val data = lazy {
            if (mode == "bad-data") error("image data forced")
            val array = codec.storage.allocate(values.size.toLong())
            values.forEachIndexed { i, value -> codec.storage.set(array, i, codec.storage.fromBits(value)) }
            RasterVector(codec.storage, array).slice(p[2], p[3])
        }
        val image = lazy { if (mode == "bad-image") error("image forced"); RasterImage(p[0], p[1], codec, data) }
        return result(if (c.s("op") == "dynamic") PngEncoder.encodeDynamic(wrap(image)) else PngDecoder.Result.Success(PngEncoder.encode(codec, { image.value }, { metadata(c) })), c.s("observe"))
    }
    private fun <P : Any> palette(codec: RasterCodec<P, UByte, ByteArray>, c: JsonObject): Map<String, Any> {
        val p = c.getValue("params").jsonArray.map { it.jsonPrimitive.long }; val mode = c.s("mode")
        val image = lazy {
            if (mode == "bad-image") error("image forced")
            RasterImage(p[0], p[1], RasterCodecs.Y8, lazy {
                if (mode == "bad-data") error("image data forced")
                RasterVector(RasterStorages.Bytes, c.getValue("values").jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()).slice(p[2], p[3])
            })
        }
        val palette = lazy {
            if (mode == "bad-palette") error("palette forced")
            RasterImage(p[4], p[5], codec, lazy {
                if (mode == "bad-palette-data") error("palette data forced")
                RasterVector(RasterStorages.Bytes, c.getValue("palette").jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()).slice(p[6], p[7])
            })
        }
        return result(PngEncoder.encodePaletted(codec, { palette.value }, { image.value }, { metadata(c) }), c.s("observe"))
    }
    private fun actual(c: JsonObject): Map<String, Any> {
        val type = c.s("typ"); val op = c.s("op")
        if (op == "write") {
            val path = Files.createTempFile("kotdoc-png-error-", ".png")
            try {
                Files.write(path, byteArrayOf(9, 8, 7))
                val error = assertFailsWith<IllegalStateException> {
                    if (c.s("mode") == "typed") PngEncoder.write(path, RasterCodecs.RGB8) { error("image forced") }
                    else PngEncoder.writeDynamic(path, DynamicRaster.RGB8(lazy { error("image forced") }))
                }
                return mapOf("exception" to error.message.orEmpty(), "after" to Files.readAllBytes(path).toHexString())
            } finally { Files.deleteIfExists(path) }
        }
        if (op in listOf("transcode", "plane")) {
            val bytes = Files.readAllBytes(Path.of(System.getProperty("kotdoc.root"), c.s("source")))
            return when (val decoded = PngDecoder.decode(bytes)) {
                is PngDecoder.Result.Failure -> mapOf("error" to decoded.message)
                is PngDecoder.Result.Success -> {
                    val image = decoded.value.value
                    if (op == "transcode") result(PngEncoder.encodeDynamic(image), c.s("observe"))
                    else {
                        val component = c.getValue("params").jsonArray.first().jsonPrimitive.long
                        val plane = when (image) {
                            is DynamicRaster.RGB8 -> image.image.value.let { ImagePlanes.unsafeExtractComponent(component, ImageRGB8(it.width.toInt(), it.height.toInt(), it.vector.data)) }
                            is DynamicRaster.RGBA8 -> image.image.value.let { ImagePlanes.unsafeExtractComponent(component, ImageRGBA8(it.width.toInt(), it.height.toInt(), it.vector.data)) }
                            else -> error("Unexpected plane input")
                        }
                        mapOf("hex" to PngEncoder.encode(RasterCodecs.Y8, { RasterBridge.from(plane) }).value)
                    }
                }
            }
        }
        if (op == "palette") return if (type == "rgb8") palette(RasterCodecs.RGB8, c) else palette(RasterCodecs.RGBA8, c)
        return when (type) {
            "y8" -> typed(RasterCodecs.Y8, DynamicRaster::Y8, c); "y16" -> typed(RasterCodecs.Y16, DynamicRaster::Y16, c)
            "ya8" -> typed(RasterCodecs.YA8, DynamicRaster::YA8, c); "ya16" -> typed(RasterCodecs.YA16, DynamicRaster::YA16, c)
            "rgb8" -> typed(RasterCodecs.RGB8, DynamicRaster::RGB8, c); "rgb16" -> typed(RasterCodecs.RGB16, DynamicRaster::RGB16, c)
            "rgba8" -> typed(RasterCodecs.RGBA8, DynamicRaster::RGBA8, c); "rgba16" -> typed(RasterCodecs.RGBA16, DynamicRaster::RGBA16, c)
            else -> result(PngEncoder.encodeDynamic(when (type) {
                "y32" -> DynamicRaster.Y32(lazy { error("image forced") }); "yf" -> DynamicRaster.YF(lazy { error("image forced") })
                "rgbf" -> DynamicRaster.RGBF(lazy { error("image forced") }); "ycbcr8" -> DynamicRaster.YCbCr8(lazy { error("image forced") })
                "cmyk8" -> DynamicRaster.CMYK8(lazy { error("image forced") }); "cmyk16" -> DynamicRaster.CMYK16(lazy { error("image forced") })
                else -> error(type)
            }), c.s("observe"))
        }
    }
    private fun check(c: JsonObject) {
        val actual = try { actual(c) } catch (error: RuntimeException) { mapOf("exception" to error.message.orEmpty().lineSequence().first()) }
        val expected = c.getValue("expected").jsonObject
        if ("length" in expected) {
            val data = actual["hex"] as? ByteArray
            assertNotNull(data, "expected PNG bytes, actual $actual")
            assertEquals(expected.getValue("length").jsonPrimitive.int, data.size)
            assertEquals(expected.s("sha256"), MessageDigest.getInstance("SHA-256").digest(data).toHexString())
            if ("hex" in expected) assertContentEquals(expected.s("hex").hexToByteArray(), data)
            assertEquals(setOf("hex"), actual.keys)
        } else assertEquals(expected.mapValues { it.value.jsonPrimitive.content }, actual)
    }
    @TestFactory fun originalPngEncoder(): Stream<DynamicTest> = lines().asSequence().map { line ->
        DynamicTest.dynamicTest(Json.parseToJsonElement(line).jsonObject.s("name")) { check(Json.parseToJsonElement(line).jsonObject) }
    }.asStream()
    @Test fun fileHelpersWriteOriginalBytesAndUnsupportedTypesLeaveFilesUntouched() {
        val c = lines().asSequence().map { Json.parseToJsonElement(it).jsonObject }.first { it.s("name") == "typed rgb8 1x1 pattern" }
        val data = byteArrayOf(71, 78, 85) // 71, 1102, 2133 reduced to Word8.
        val image = RasterImage(1, 1, RasterCodecs.RGB8, RasterVector(RasterStorages.Bytes, data))
        val path = Files.createTempFile("kotdoc-png-", ".png")
        try {
            PngEncoder.write(path, RasterCodecs.RGB8) { image }
            assertEquals(c.getValue("expected").jsonObject.s("sha256"), MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).toHexString())
            val original = Files.readAllBytes(path)
            assertIs<PngDecoder.Result.Success<Boolean>>(PngEncoder.writeDynamic(path, DynamicRaster.RGB8(lazyOf(image))))
            assertContentEquals(original, Files.readAllBytes(path))
            assertIs<PngDecoder.Result.Failure>(PngEncoder.writeDynamic(path, DynamicRaster.YF(lazy { error("image forced") })))
            assertContentEquals(original, Files.readAllBytes(path))
        } finally { Files.deleteIfExists(path) }
    }
}
