package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class RasterTest {
    private val zero = BigInteger.ZERO
    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.words(key: String) = getValue(key).jsonArray.map { it.jsonPrimitive.content.toULong() }
    private fun <C : Any, A : Any> vector(storage: RasterStorage<C, A>, words: List<ULong>): RasterVector<C, A> {
        val result = RasterVector(storage, storage.allocate(words.size.toLong()))
        words.forEachIndexed { i, word -> result.set(i.toLong(), storage.fromBits(word)) }
        return result
    }
    private fun <C : Any, A : Any> hex(vector: RasterVector<C, A>, bytes: Int): String = buildString {
        for (i in 0L..<vector.length) {
            val word = vector.storage.bits(vector[i])
            for (b in 0..<bytes) append(((word shr (b * 8)) and 255u).toString(16).padStart(2, '0'))
        }
    }
    private fun bytes(codec: RasterCodec<*, *, *>) = when (codec.name) { "yf", "rgbf", "y32" -> 4; else -> if (codec.name.endsWith("16")) 2 else 1 }
    private fun <P : Any, C : Any, A : Any, Q : Any, D : Any, B : Any> observe(c: JsonObject, source: RasterCodec<P, C, A>, target: RasterCodec<Q, D, B>): JsonObject {
        val op = c.string("op"); val mode = c.string("mode"); val observation = c.string("observe")
        val p = c.getValue("params").jsonArray.map { it.jsonPrimitive.long }
        val (w, h, offset, length, seed) = p
        val w2 = p[5]; val h2 = p[6]
        val raw = vector(source.storage, c.words("left"))
        val src = RasterImage(w, h, source, lazy {
            if (observation == "bad-data") error("source data forced")
            raw.slice(offset, length)
        })
        val result = linkedMapOf<String, JsonElement>()
        fun number(key: String, value: Long) { result[key] = JsonPrimitive(value) }
        fun state(value: BigInteger) { result["state"] = JsonPrimitive(value.toString().toBigDecimal()) }
        fun fingerprint(pixel: P): BigInteger = (0..<source.components).fold(zero) { sum, i ->
            sum + source.storage.bits(source.component(pixel, i)).toString().toBigInteger() * (i + 1).toBigInteger()
        }.mod(BigInteger.ONE.shiftLeft(32))
        fun make(value: BigInteger): Q = target.fromComponents { channel -> target.storage.fromBits((value + (channel * 17).toBigInteger()).and(BigInteger.ONE.shiftLeft(64) - BigInteger.ONE).toString().toULong()) }
        fun generate(x: Long, y: Long): Q {
            if (mode == "fail") error("callback forced")
            return make((x * 37 + y * 257 + seed).toBigInteger())
        }
        fun transform(x: Long, y: Long, pixel: () -> P): Q {
            if (mode == "fail") error("callback forced")
            return make((if (mode == "ignore") 91.toBigInteger() else fingerprint(pixel())) + (x * 37 + y * 257).toBigInteger())
        }
        fun summary(image: RasterImage<Q, D, B>) {
            number("width", image.width); number("height", image.height)
            if (observation != "metadata") result["hex"] = JsonPrimitive(hex(image.vector, bytes(target)))
        }
        fun step(acc: () -> BigInteger, x: Long, y: Long, pixel: () -> P): BigInteger {
            if (mode == "fail") error("callback forced")
            return acc() * 31.toBigInteger() + (x * 17 + y * 257 + if (mode == "ignore") 1 else 0).toBigInteger() + if (mode == "ignore") zero else fingerprint(pixel())
        }
        val trace = mutableListOf<JsonElement>()
        try {
            when (op) {
                "generate" -> summary(RasterOps.generateImage(target, w, h, ::generate))
                "with" -> summary(RasterOps.withImage(target, w, h) { x, y -> trace.add(JsonArray(listOf(JsonPrimitive(x), JsonPrimitive(y)))); generate(x, y) })
                "generate-fold" -> {
                    val folded = RasterOps.generateFoldImage(target, seed.toBigInteger(), w, h) { acc, x, y ->
                        val next = acc * 31.toBigInteger() + (x * 17 + y * 257 + 1).toBigInteger()
                        next to make(next + seed.toBigInteger())
                    }
                    summary(folded.image)
                    if (observation != "metadata") state(folded.state)
                }
                "map" -> summary(RasterOps.pixelMapLazy(src, target) { transform(0, 0, it) })
                "mapxy" -> summary(RasterOps.pixelMapXYLazy(src, target, ::transform))
                "fold", "foldm" -> {
                    val initial = lazyOf(seed.toBigInteger())
                    val fold: (Lazy<BigInteger>, Long, Long, () -> P) -> Lazy<BigInteger> = { acc, x, y, pixel ->
                        if (op == "foldm") trace.add(JsonArray(listOf(JsonPrimitive(x), JsonPrimitive(y))))
                        lazy { step({ acc.value }, x, y, pixel) }
                    }
                    val value = if (op == "fold") RasterOps.pixelFoldLazy(src, initial, fold) else RasterOps.pixelFoldMLazy(src, initial, fold)
                    state(value.value)
                }
                "foldmap" -> when (mode) {
                    "any" -> result["bool"] = JsonPrimitive(RasterOps.pixelFoldMap(src, object : RasterMonoid<Boolean> {
                        override fun empty() = false
                        override fun append(left: () -> Boolean, right: () -> Boolean) = left() || right()
                    }) { true })
                    "bracket" -> result["value"] = JsonPrimitive(RasterOps.pixelFoldMap(src, object : RasterMonoid<String> {
                        override val strictTail = true
                        override fun empty() = "E"
                        override fun append(left: () -> String, right: () -> String) = "(${left()}|${right()})"
                    }) { fingerprint(it()).toString() })
                    else -> state(RasterOps.pixelFoldMap(src, object : RasterMonoid<BigInteger> {
                        override val strictTail = true
                        override fun empty() = zero
                        override fun append(left: () -> BigInteger, right: () -> BigInteger) = left() + right()
                    }) { if (mode == "ignore") BigInteger.ONE else fingerprint(it()) })
                }
                "zip" -> {
                    val b = RasterImage(w2, h2, source, vector(source.storage, c.words("right")))
                    val d = RasterImage(w, h, source, vector(source.storage, c.words("third")))
                    val combined = RasterOps.zipPixelComponent3(src, b, d) { x, y, z -> source.storage.fromBits((source.storage.bits(x) * 3u + source.storage.bits(y) * 5u + source.storage.bits(z) * 7u) and 0xffffffffu) }
                    if (observation == "metadata") { number("width", combined.width); number("height", combined.height) }
                    else result["hex"] = JsonPrimitive(hex(combined.vector, bytes(source)))
                }
                "eq" -> result["bool"] = JsonPrimitive(if (mode == "self") src == src else src == RasterImage(w2, h2, source, vector(source.storage, c.words("right"))))
                "base" -> number("index", RasterOps.pixelBaseIndex(src, w2, h2))
                "pixel", "read" -> {
                    val value = if (op == "pixel") RasterOps.pixelAt(src, w2, h2) else RasterOps.readPixel(MutableRasterImage(w, h, source, raw.slice(offset, length)), w2, h2)
                    val pixel = vector(source.storage, (0..<source.components).map { source.storage.bits(source.component(value, it)) })
                    result["hex"] = JsonPrimitive(hex(pixel, bytes(source)))
                }
                "write" -> {
                    val mutable = MutableRasterImage(w, h, source, raw.slice(offset, length))
                    val fields = c.words("right")
                    val value = source.fromComponents { source.storage.fromBits(fields[it]) }
                    try { RasterOps.writePixel(mutable, w2, h2, value) }
                    catch (e: ImagePixelException) { result["exception"] = JsonPrimitive(e.message) }
                    result["parent"] = JsonPrimitive(hex(raw, bytes(source)))
                }
                "slice" -> result["hex"] = JsonPrimitive(hex(src.vector, bytes(source)))
                "freeze" -> {
                    val mutable = MutableRasterImage(w, h, source, raw.slice(offset, length))
                    val snapshot = RasterOps.freezeImage(mutable)
                    if (length > 0) mutable.vector.set(0, source.storage.fromBits(seed.toULong()))
                    result["hex"] = JsonPrimitive(hex(snapshot.vector, bytes(source)))
                    result["parent"] = JsonPrimitive(hex(raw, bytes(source)))
                }
                "thaw" -> {
                    val mutable = RasterOps.thawImage(src)
                    if (length > 0) mutable.vector.set(0, source.storage.fromBits(seed.toULong()))
                    result["hex"] = JsonPrimitive(hex(RasterOps.freezeImage(mutable).vector, bytes(source)))
                    result["parent"] = JsonPrimitive(hex(raw, bytes(source)))
                }
                else -> error(op)
            }
        } catch (e: Exception) {
            result.clear()
            result["exception"] = JsonPrimitive(e.message?.lineSequence()?.first())
        }
        if (op == "with" || op == "foldm") result["trace"] = JsonArray(trace)
        return JsonObject(result)
    }
    private fun <P : Any, C : Any, A : Any> dispatch(c: JsonObject, source: RasterCodec<P, C, A>): JsonObject = when (c.string("target")) {
        "y8" -> observe(c, source, RasterCodecs.Y8)
        "y16" -> observe(c, source, RasterCodecs.Y16)
        "y32" -> observe(c, source, RasterCodecs.Y32)
        "yf" -> observe(c, source, RasterCodecs.YF)
        "ya8" -> observe(c, source, RasterCodecs.YA8)
        "ya16" -> observe(c, source, RasterCodecs.YA16)
        "rgb8" -> observe(c, source, RasterCodecs.RGB8)
        "rgb16" -> observe(c, source, RasterCodecs.RGB16)
        "rgbf" -> observe(c, source, RasterCodecs.RGBF)
        "rgba8" -> observe(c, source, RasterCodecs.RGBA8)
        "rgba16" -> observe(c, source, RasterCodecs.RGBA16)
        "ycbcr8" -> observe(c, source, RasterCodecs.YCbCr8)
        "ycbcrk8" -> observe(c, source, RasterCodecs.YCbCrK8)
        "cmyk8" -> observe(c, source, RasterCodecs.CMYK8)
        "cmyk16" -> observe(c, source, RasterCodecs.CMYK16)
        else -> error("unknown target")
    }
    private fun dispatch(c: JsonObject): JsonObject = when (c.string("source")) {
        "y8" -> dispatch(c, RasterCodecs.Y8)
        "y16" -> dispatch(c, RasterCodecs.Y16)
        "y32" -> dispatch(c, RasterCodecs.Y32)
        "yf" -> dispatch(c, RasterCodecs.YF)
        "ya8" -> dispatch(c, RasterCodecs.YA8)
        "ya16" -> dispatch(c, RasterCodecs.YA16)
        "rgb8" -> dispatch(c, RasterCodecs.RGB8)
        "rgb16" -> dispatch(c, RasterCodecs.RGB16)
        "rgbf" -> dispatch(c, RasterCodecs.RGBF)
        "rgba8" -> dispatch(c, RasterCodecs.RGBA8)
        "rgba16" -> dispatch(c, RasterCodecs.RGBA16)
        "ycbcr8" -> dispatch(c, RasterCodecs.YCbCr8)
        "ycbcrk8" -> dispatch(c, RasterCodecs.YCbCrK8)
        "cmyk8" -> dispatch(c, RasterCodecs.CMYK8)
        "cmyk16" -> dispatch(c, RasterCodecs.CMYK16)
        else -> error("unknown source")
    }
    @TestFactory
    fun originalRasterObservations(): Stream<DynamicTest> {
        val input = checkNotNull(javaClass.getResourceAsStream("/images/raster.jsonl.gz"))
        val lines = GZIPInputStream(input).bufferedReader().use { it.readLines().drop(1) }
        return lines.map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.string("name")
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                assertEquals(c.getValue("expected"), dispatch(c))
            }
        }.asSequence().asStream()
    }
    @Test
    fun logicalViewsShareUnsafeLifecycleAndCheckFlattenedCoordinates() {
        val parent = byteArrayOf(99, 1, 2, 3, 4, 5, 88)
        val image = RasterImage(Long.MAX_VALUE, Long.MIN_VALUE, RasterCodecs.YA8, RasterVector(RasterStorages.Bytes, parent).slice(1, 5))
        val mutable = RasterOps.unsafeThawImage(image)
        assertSame(image.vector, mutable.vector)
        assertSame(image.vector, RasterOps.unsafeFreezeImage(mutable).vector)
        // Flattened address (-Long.MAX_VALUE + Long.MAX_VALUE) * 2 = 0.
        assertEquals(PixelYA8(1u, 2u), RasterOps.pixelAt(image, -Long.MAX_VALUE, 1))
        val error = assertFailsWith<ImagePixelException> { RasterOps.writePixel(mutable, 2, 0, PixelYA8(17u, 19u)) }
        assertEquals("index out of bounds (5,5)", error.message)
        assertContentEquals(byteArrayOf(99, 1, 2, 3, 4, 17, 88), parent)
        assertEquals(Long.MAX_VALUE, RasterOps.freezeImage(mutable).width)
        assertEquals(Long.MIN_VALUE, RasterOps.freezeImage(mutable).height)
    }
    @Test
    fun strictFoldMapIsStackSafeAndLazyMonoidsCanDiscardTail() {
        val codec = RasterCodecs.Y8
        val image = RasterOps.generateImage(codec, 100_000, 1) { _, _ -> 1u.toUByte() }
        assertEquals(100_000L, RasterOps.pixelFoldMap(image, object : RasterMonoid<Long> {
            override val strictTail = true
            override fun empty() = 0L
            override fun append(left: () -> Long, right: () -> Long) = left() + right()
        }) { it().toLong() })
        var callbacks = 0
        assertTrue(RasterOps.pixelFoldMap(image, object : RasterMonoid<Boolean> {
            override fun empty() = false
            override fun append(left: () -> Boolean, right: () -> Boolean) = left() || right()
        }) { callbacks++; it() == 1u.toUByte() })
        assertEquals(1, callbacks)
    }
    private fun <P : Any, C : Any, A : Any> checkBridge(image: RasterImage<P, C, A>, mutable: MutableRasterImage<P, C, A>, backing: A) {
        assertSame(backing, image.vector.data)
        assertSame(backing, mutable.vector.data)
        assertEquals(-2L, image.width)
        assertEquals(-3L, mutable.height)
        val pixel = RasterOps.readAt(image.codec, image.vector, 0, true)
        val background = RasterOps.createMutableImage(image.codec, 3, 2, pixel)
        for (y in 0L..<2) for (x in 0L..<3) {
            val value = RasterOps.readPixel(background, x, y)
            for (channel in 0..<image.codec.components) assertEquals(image.codec.storage.bits(image.codec.component(pixel, channel)), image.codec.storage.bits(image.codec.component(value, channel)))
        }
        assertNotSame(backing, RasterOps.thawImage(image).vector.data)
        assertNotSame(backing, RasterOps.freezeImage(mutable).vector.data)
    }
    @Test
    fun everyExistingPixelBufferCanUseGenericOperationsWithoutCopying() {
        run { val data = ByteArray(4) { it.toByte() }; checkBridge(RasterBridge.from(ImageY8(-2, -3, data)), RasterBridge.from(MutableImageY8(-2, -3, data)), data) }
        run { val data = ShortArray(4) { it.toShort() }; checkBridge(RasterBridge.from(ImageY16(-2, -3, data)), RasterBridge.from(MutableImageY16(-2, -3, data)), data) }
        run { val data = IntArray(4) { it }; checkBridge(RasterBridge.from(ImageY32(-2, -3, data)), RasterBridge.from(MutableImageY32(-2, -3, data)), data) }
        run { val data = FloatArray(4) { it.toFloat() }; checkBridge(RasterBridge.from(ImageYF(-2, -3, data)), RasterBridge.from(MutableImageYF(-2, -3, data)), data) }
        run { val data = ByteArray(8) { it.toByte() }; checkBridge(RasterBridge.from(ImageYA8(-2, -3, data)), RasterBridge.from(MutableImageYA8(-2, -3, data)), data) }
        run { val data = ShortArray(8) { it.toShort() }; checkBridge(RasterBridge.from(ImageYA16(-2, -3, data)), RasterBridge.from(MutableImageYA16(-2, -3, data)), data) }
        run { val data = ByteArray(12) { it.toByte() }; checkBridge(RasterBridge.from(ImageRGB8(-2, -3, data)), RasterBridge.from(MutableImageRGB8(-2, -3, data)), data) }
        run { val data = ShortArray(12) { it.toShort() }; checkBridge(RasterBridge.from(ImageRGB16(-2, -3, data)), RasterBridge.from(MutableImageRGB16(-2, -3, data)), data) }
        run { val data = FloatArray(12) { it.toFloat() }; checkBridge(RasterBridge.from(ImageRGBF(-2, -3, data)), RasterBridge.from(MutableImageRGBF(-2, -3, data)), data) }
        run { val data = ByteArray(16) { it.toByte() }; checkBridge(RasterBridge.from(ImageRGBA8(-2, -3, data)), RasterBridge.from(MutableImageRGBA8(-2, -3, data)), data) }
        run { val data = ShortArray(16) { it.toShort() }; checkBridge(RasterBridge.from(ImageRGBA16(-2, -3, data)), RasterBridge.from(MutableImageRGBA16(-2, -3, data)), data) }
        run { val data = ByteArray(12) { it.toByte() }; checkBridge(RasterBridge.from(ImageYCbCr8(-2, -3, data)), RasterBridge.from(MutableImageYCbCr8(-2, -3, data)), data) }
        run { val data = ByteArray(16) { it.toByte() }; checkBridge(RasterBridge.from(ImageYCbCrK8(-2, -3, data)), RasterBridge.from(MutableImageYCbCrK8(-2, -3, data)), data) }
        run { val data = ByteArray(16) { it.toByte() }; checkBridge(RasterBridge.from(ImageCMYK8(-2, -3, data)), RasterBridge.from(MutableImageCMYK8(-2, -3, data)), data) }
        run { val data = ShortArray(16) { it.toShort() }; checkBridge(RasterBridge.from(ImageCMYK16(-2, -3, data)), RasterBridge.from(MutableImageCMYK16(-2, -3, data)), data) }
    }

}
