package io.heapy.kotdoc.images
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class DynamicRasterTest {
    private fun JsonObject.s(key: String) = getValue(key).jsonPrimitive.content
    private fun bytes(codec: RasterCodec<*, *, *>) = if (codec.name in listOf("yf", "rgbf", "y32")) 4 else if (codec.name.endsWith("16")) 2 else 1
    private fun <C : Any, A : Any> hex(v: RasterVector<C, A>, bytes: Int) = buildString {
        for (i in 0L..<v.length) for (b in 0..<bytes) append(((v.storage.bits(v[i]) shr (8 * b)) and 255u).toString(16).padStart(2, '0'))
    }
    private fun <P : Any, C : Any, A : Any> summary(codec: RasterCodec<P, C, A>, image: () -> RasterImage<P, C, A>, observe: String) = buildJsonObject {
        put("tag", codec.name)
        if (observe != "tag") { val i = image(); put("width", i.width); put("height", i.height); if (observe != "metadata") put("hex", hex(i.vector, bytes(codec))) }
    }
    private fun summary(dyn: DynamicRaster, observe: String) = dyn.dynamicMap(object : DynamicRasterVisitor<JsonObject> {
        override fun <P : Any, C : Any, A : Any> visit(codec: RasterCodec<P, C, A>, image: () -> RasterImage<P, C, A>) = summary(codec, image, observe)
    })
    private fun <P : Any, C : Any, A : Any> observe(c: JsonObject, codec: RasterCodec<P, C, A>, wrap: (Lazy<RasterImage<P, C, A>>) -> DynamicRaster, expand: ((Lazy<RasterImage<UByte, UByte, ByteArray>>, Lazy<RasterPalette<P, C, A>>) -> PalettedRaster)?): JsonObject {
        val mode = c.s("mode"); val observation = c.s("observe"); val op = c.s("op")
        val p = c.getValue("params").jsonArray.map { it.jsonPrimitive.long }
        val (w, h, offset, length, size) = p; val po = p[5]; val pl = p[6]
        fun vector(values: JsonArray): RasterVector<C, A> {
            val v = RasterVector(codec.storage, codec.storage.allocate(values.size.toLong()))
            values.forEachIndexed { i, x -> v.set(i.toLong(), codec.storage.fromBits(x.jsonPrimitive.content.toULong())) }; return v
        }
        val image = lazy {
            if (mode == "bad-image") error("source image forced")
            RasterImage(w, h, codec, lazy { if (mode in listOf("bad-data", "both-data")) error("source data forced"); vector(c.getValue("left").jsonArray).slice(offset, length) })
        }
        val palette = lazy {
            if (mode == "bad-palette") error("palette forced")
            if (mode in listOf("bad-palette-data", "both-data")) error("palette data forced")
            RasterPalette(size, codec, vector(c.getValue("right").jsonArray).slice(po, pl))
        }
        val dyn by lazy { wrap(image) }
        return try {
            when (op) {
                "force" -> { dyn.force(); buildJsonObject { put("forced", true) } }
                "map" -> buildJsonObject { put("value", dyn.dynamicMap(object : DynamicRasterVisitor<JsonElement> {
                    override fun <Q : Any, D : Any, B : Any> visit(codec: RasterCodec<Q, D, B>, image: () -> RasterImage<Q, D, B>): JsonElement = when (observation) {
                        "constant" -> JsonPrimitive(91)
                        "metadata" -> image().let { JsonArray(listOf(JsonPrimitive(it.width), JsonPrimitive(it.height))) }
                        else -> JsonPrimitive(image().vector.length)
                    }
                })) }
                "transform" -> summary(dyn.dynamicPixelMap(object : DynamicRasterTransform {
                    override fun <Q : Any, D : Any, B : Any> transform(codec: RasterCodec<Q, D, B>, image: () -> RasterImage<Q, D, B>): RasterImage<Q, D, B> = when (mode) {
                        "fail" -> error("transform forced")
                        "empty" -> RasterImage(-17, 0, codec, RasterVector(codec.storage, codec.storage.allocate(0)))
                        "swap" -> image().let { src -> RasterImage(src.height, src.width, codec, lazy { src.vector }) }
                        "map" -> RasterOps.pixelMapLazy(image(), codec) { it() }
                        else -> image()
                    }
                }), observation)
                "palette-image" -> {
                    val src = palette.value; val img = src.palettedAsImage()
                    JsonObject(summary(codec, { img }, observation) + ("shared" to JsonPrimitive(src.vector === img.vector)))
                }
                "expand" -> {
                    val indices = lazy {
                        if (mode == "bad-image") error("source image forced")
                        RasterImage(w, h, RasterCodecs.Y8, lazy {
                            if (mode in listOf("bad-data", "both-data")) error("source data forced")
                            RasterVector(RasterStorages.Bytes, c.getValue("left").jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()).slice(offset, length)
                        })
                    }
                    summary(checkNotNull(expand)(indices, palette).palettedToTrueColor(), observation)
                }
                "fallback" -> { val out = PalettedRaster.TrueColorImage { dyn }.palettedToTrueColor(); JsonObject(summary(out, observation) + ("same" to JsonPrimitive(out === dyn))) }
                "eq" -> buildJsonObject { put("bool", if (mode == "self") dyn == dyn else dyn == wrap(lazyOf(RasterImage(w, h, codec, vector(c.getValue("right").jsonArray)))) ) }
                "tag-eq" -> buildJsonObject { put("bool", dyn == DynamicRaster.RGB8(lazy { error("other image forced") })) }
                else -> error(op)
            }
        } catch (e: Exception) { buildJsonObject { put("exception", e.message?.lineSequence()?.first()) } }
    }
    private fun dispatch(c: JsonObject): JsonObject = when (c.s("type")) {
        "y8" -> observe(c, RasterCodecs.Y8, DynamicRaster::Y8, PalettedRaster::Y8)
        "y16" -> observe(c, RasterCodecs.Y16, DynamicRaster::Y16, null)
        "y32" -> observe(c, RasterCodecs.Y32, DynamicRaster::Y32, null)
        "yf" -> observe(c, RasterCodecs.YF, DynamicRaster::YF, null)
        "ya8" -> observe(c, RasterCodecs.YA8, DynamicRaster::YA8, null)
        "ya16" -> observe(c, RasterCodecs.YA16, DynamicRaster::YA16, null)
        "rgb8" -> observe(c, RasterCodecs.RGB8, DynamicRaster::RGB8, PalettedRaster::RGB8)
        "rgb16" -> observe(c, RasterCodecs.RGB16, DynamicRaster::RGB16, PalettedRaster::RGB16)
        "rgbf" -> observe(c, RasterCodecs.RGBF, DynamicRaster::RGBF, null)
        "rgba8" -> observe(c, RasterCodecs.RGBA8, DynamicRaster::RGBA8, PalettedRaster::RGBA8)
        "rgba16" -> observe(c, RasterCodecs.RGBA16, DynamicRaster::RGBA16, null)
        "ycbcr8" -> observe(c, RasterCodecs.YCbCr8, DynamicRaster::YCbCr8, null)
        "ycbcrk8" -> observe(c, RasterCodecs.YCbCrK8, { error("no dynamic YCbCrK8") }, null)
        "cmyk8" -> observe(c, RasterCodecs.CMYK8, DynamicRaster::CMYK8, null)
        "cmyk16" -> observe(c, RasterCodecs.CMYK16, DynamicRaster::CMYK16, null)
        else -> error("unknown pixel type")
    }
    @TestFactory
    fun originalDynamicAndPaletteObservations(): Stream<DynamicTest> {
        val lines = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/dynamic-raster.jsonl.gz"))).bufferedReader().use { it.readLines().drop(1) }
        return lines.map { line -> DynamicTest.dynamicTest(Json.parseToJsonElement(line).jsonObject.s("name")) {
            val c = Json.parseToJsonElement(line).jsonObject
            assertEquals(c.getValue("expected"), dispatch(c))
        } }.asSequence().asStream()
    }
    @Test
    fun trueColorFallbackMemoizesItsLazyPayload() {
        var evaluated = 0
        val fallback = PalettedRaster.TrueColorImage {
            evaluated++
            DynamicRaster.Y8(lazy { RasterOps.generateImage(RasterCodecs.Y8, 1, 1) { _, _ -> 17u.toUByte() } })
        }
        assertEquals(0, evaluated)
        val first = fallback.palettedToTrueColor()
        assertSame(first, fallback.palettedToTrueColor())
        assertEquals(1, evaluated)
    }

}
