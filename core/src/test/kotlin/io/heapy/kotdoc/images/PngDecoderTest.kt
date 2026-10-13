package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class PngDecoderTest {
    private fun JsonObject.s(key: String) = getValue(key).jsonPrimitive.content
    private fun lines() = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/png-decoder.jsonl.gz"))).bufferedReader().use { it.readLines().drop(1) }
    private fun bytes(c: JsonObject): ByteArray = if ("source" in c) Files.readAllBytes(Path.of(System.getProperty("kotdoc.root"), c.s("source"))) else c.s("hex").hexToByteArray()
    private fun sha(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).toHexString()
    private fun <C : Any, A : Any> serialized(vector: RasterVector<C, A>, componentBytes: Int): ByteArray = ByteArray(Math.toIntExact(vector.length * componentBytes)) { i ->
        (vector.storage.bits(vector[(i / componentBytes).toLong()]) shr ((i % componentBytes) * 8)).toByte()
    }
    private fun dynamic(image: DynamicRaster): Map<String, Any> = image.dynamicMap(object : DynamicRasterVisitor<Map<String, Any>> {
        override fun <P : Any, C : Any, A : Any> visit(codec: RasterCodec<P, C, A>, image: () -> RasterImage<P, C, A>): Map<String, Any> {
            val i = image()
            return mapOf("tag" to codec.name, "width" to i.width, "height" to i.height,
                "hex" to serialized(i.vector, if (codec.name.endsWith("16")) 2 else 1))
        }
    })
    private fun paletteTag(image: PalettedRaster) = when (image) {
        is PalettedRaster.TrueColorImage -> "truecolor"
        is PalettedRaster.Y8 -> "y8"
        is PalettedRaster.RGB8 -> "rgb8"
        is PalettedRaster.RGBA8 -> "rgba8"
        is PalettedRaster.RGB16 -> "rgb16"
    }
    private fun <P : Any> indexed(tag: String, indices: RasterImage<UByte, UByte, ByteArray>, palette: RasterPalette<P, UByte, ByteArray>): Map<String, Any> =
        mapOf("tag" to tag, "width" to indices.width, "height" to indices.height, "hex" to serialized(indices.vector, 1), "palette" to serialized(palette.vector, 1), "size" to palette.size)
    private fun palette(image: PalettedRaster): Map<String, Any> = when (image) {
        is PalettedRaster.TrueColorImage -> dynamic(image.palettedToTrueColor())
        is PalettedRaster.RGB8 -> indexed("rgb8", image.indices.value, image.palette.value)
        is PalettedRaster.RGBA8 -> indexed("rgba8", image.indices.value, image.palette.value)
        else -> error("Unexpected PNG palette")
    }
    private fun observe(c: JsonObject, wrapper: Boolean = false): Map<String, Any> {
        val bytes = bytes(c)
        if (wrapper) return when (val out = PngDecoder.decode(bytes)) {
            is PngDecoder.Result.Failure -> mapOf("error" to out.message)
            is PngDecoder.Result.Success -> dynamic(out.value.value)
        }
        return when (val out = PngDecoder.decodeWithPaletteAndMetadata(bytes)) {
            is PngDecoder.Result.Failure -> mapOf("error" to out.message)
            is PngDecoder.Result.Success -> when (c.s("observe")) {
                "either" -> emptyMap()
                "palette-tag" -> mapOf("tag" to paletteTag(out.value.image))
                "palette-data" -> palette(out.value.image)
                "dynamic-tag" -> mapOf("tag" to out.value.image.palettedToTrueColor().tag)
                "dynamic-data" -> dynamic(out.value.image.palettedToTrueColor())
                "metadata" -> mapOf("metadata" to out.value.metadata.value.toString())
                "metadata-keys" -> mapOf("metadata" to out.value.metadata.value.entries.joinToString(",", "[", "]") { io.heapy.kotdoc.formats.HaskellString.quote(it.key.toString()) })
                "metadata-title" -> mapOf("metadata" to (out.value.metadata.value.lookup(ImageMetadataKey.Title)?.let { "Just " + io.heapy.kotdoc.formats.HaskellString.quote(it) } ?: "Nothing"))
                "metadata-dpi" -> mapOf("metadata" to (out.value.metadata.value.lookup(ImageMetadataKey.DpiX)?.let { "Just $it" } ?: "Nothing"))
                else -> error(c.s("observe"))
            }
        }
    }
    private fun check(c: JsonObject, wrapper: Boolean = false) {
        val actual = try { observe(c, wrapper) } catch (error: RuntimeException) { mapOf("exception" to error.message.orEmpty().lineSequence().first()) }
        val expected = c.getValue("expected").jsonObject
        for ((key, value) in expected) {
            when {
                key in listOf("hex", "palette") -> assertContentEquals(value.jsonPrimitive.content.hexToByteArray(), actual[key] as? ByteArray, key)
                key.endsWith("Length") -> assertEquals(value.jsonPrimitive.int, (actual[key.removeSuffix("Length")] as? ByteArray)?.size, key)
                key.endsWith("Sha256") -> assertEquals(value.jsonPrimitive.content, (actual[key.removeSuffix("Sha256")] as? ByteArray)?.let(::sha), key)
                key in listOf("width", "height", "size") -> assertEquals(value.jsonPrimitive.long, actual[key], key)
                else -> assertEquals(value.jsonPrimitive.content, actual[key], key)
            }
        }
        assertEquals(expected.keys.filterNot { it.endsWith("Length") || it.endsWith("Sha256") }.toSet() +
            expected.keys.filter { it.endsWith("Length") }.map { it.removeSuffix("Length") }, actual.keys)
    }
    @TestFactory fun originalPngDecoder(): Stream<DynamicTest> = lines().asSequence().map { line ->
        DynamicTest.dynamicTest(Json.parseToJsonElement(line).jsonObject.s("name")) { check(Json.parseToJsonElement(line).jsonObject) }
    }.asStream()
    @TestFactory fun decodeWrapper(): Stream<DynamicTest> = lines().asSequence().filter { line ->
        val c = Json.parseToJsonElement(line).jsonObject
        "source" in c && c.s("observe") == "dynamic-data"
    }.map { line -> DynamicTest.dynamicTest("decode wrapper " + Json.parseToJsonElement(line).jsonObject.s("name")) { check(Json.parseToJsonElement(line).jsonObject, true) } }.asStream()
    @Test fun compressedTruncatedRowsHaveAnExplicitJvmBoundary() {
        val c = lines().asSequence().map { Json.parseToJsonElement(it).jsonObject }.first { it.s("name").startsWith("row filter 0 /") }
        // Increasing height preserves the ignored IHDR CRC but exhausts the
        // decompressed scanlines. Native unsafeIndex has no portable result.
        val data = bytes(c).also { it[23] = 127 }
        val out = assertIs<PngDecoder.Result.Success<PngDecoder.Decoded>>(PngDecoder.decodeWithPaletteAndMetadata(data))
        assertFailsWith<PngMetadata.TruncatedScanlineException> { out.value.image.palettedToTrueColor() }
    }
}
