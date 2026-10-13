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

class RasterTraversalTest {
    private class ConstTag<M>
    private class ConstValue<M, A>(val value: Lazy<M>) : RasterEffect<ConstTag<M>, A>
    private class Const<M>(val empty: () -> M, val append: (() -> M, () -> M) -> M) : RasterApplicative<ConstTag<M>> {
        fun <A> of(value: () -> M): RasterEffect<ConstTag<M>, A> = ConstValue(lazy(value))
        @Suppress("UNCHECKED_CAST") fun <A> run(value: RasterEffect<ConstTag<M>, A>): M = (value as ConstValue<M, A>).value.value
        override fun <A> pure(value: () -> A): RasterEffect<ConstTag<M>, A> = of(empty)
        override fun <A, B> map(effect: () -> RasterEffect<ConstTag<M>, A>, transform: (() -> A) -> B): RasterEffect<ConstTag<M>, B> = of { run(effect()) }
        override fun <A, B, C> map2(left: () -> RasterEffect<ConstTag<M>, A>, right: () -> RasterEffect<ConstTag<M>, B>, combine: (() -> A, () -> B) -> C): RasterEffect<ConstTag<M>, C> = of { append({ run(left()) }, { run(right()) }) }
    }
    private object IoTag
    private class IoValue<A>(val action: () -> Lazy<A>) : RasterEffect<IoTag, A>
    private object Io : RasterApplicative<IoTag> {
        fun <A> action(action: () -> Lazy<A>): RasterEffect<IoTag, A> = IoValue(action)
        @Suppress("UNCHECKED_CAST") fun <A> run(value: RasterEffect<IoTag, A>): Lazy<A> = (value as IoValue<A>).action()
        override fun <A> pure(value: () -> A): RasterEffect<IoTag, A> = action { lazy(value) }
        override fun <A, B> map(effect: () -> RasterEffect<IoTag, A>, transform: (() -> A) -> B): RasterEffect<IoTag, B> = action { val a = run(effect()); lazy { transform { a.value } } }
        override fun <A, B, C> map2(left: () -> RasterEffect<IoTag, A>, right: () -> RasterEffect<IoTag, B>, combine: (() -> A, () -> B) -> C): RasterEffect<IoTag, C> = action { val a = run(left()); val b = run(right()); lazy { combine({ a.value }, { b.value }) } }
    }
    private object MaybeTag
    private sealed interface MaybeValue<out A> : RasterEffect<MaybeTag, A>
    private data object None : MaybeValue<Nothing>
    private class Some<A>(val value: Lazy<A>) : MaybeValue<A>
    private object Maybe : RasterApplicative<MaybeTag> {
        @Suppress("UNCHECKED_CAST") fun <A> run(value: RasterEffect<MaybeTag, A>): MaybeValue<A> = value as MaybeValue<A>
        override fun <A> pure(value: () -> A): RasterEffect<MaybeTag, A> = Some(lazy(value))
        override fun <A, B> map(effect: () -> RasterEffect<MaybeTag, A>, transform: (() -> A) -> B): RasterEffect<MaybeTag, B> = when (val a = run(effect())) { None -> None; is Some -> Some(lazy { transform { a.value.value } }) }
        override fun <A, B, C> map2(left: () -> RasterEffect<MaybeTag, A>, right: () -> RasterEffect<MaybeTag, B>, combine: (() -> A, () -> B) -> C): RasterEffect<MaybeTag, C> = when (val a = run(left())) {
            None -> None
            is Some -> when (val b = run(right())) { None -> None; is Some -> Some(lazy { combine({ a.value.value }, { b.value.value }) }) }
        }
    }
    private object ListTag
    private class Branches<A>(val values: List<Lazy<A>>) : RasterEffect<ListTag, A>
    private object ListApp : RasterApplicative<ListTag> {
        @Suppress("UNCHECKED_CAST") fun <A> run(value: RasterEffect<ListTag, A>): List<Lazy<A>> = (value as Branches<A>).values
        override fun <A> pure(value: () -> A): RasterEffect<ListTag, A> = Branches(listOf(lazy(value)))
        override fun <A, B> map(effect: () -> RasterEffect<ListTag, A>, transform: (() -> A) -> B): RasterEffect<ListTag, B> = Branches(run(effect()).map { a -> lazy { transform { a.value } } })
        override fun <A, B, C> map2(left: () -> RasterEffect<ListTag, A>, right: () -> RasterEffect<ListTag, B>, combine: (() -> A, () -> B) -> C): RasterEffect<ListTag, C> {
            val a = run(left()); if (a.isEmpty()) return Branches(emptyList())
            val b = run(right())
            return Branches(a.flatMap { x -> b.map { y -> lazy { combine({ x.value }, { y.value }) } } })
        }
    }
    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
    private fun bytes(codec: RasterCodec<*, *, *>) = when (codec.name) { "yf", "rgbf", "y32" -> 4; else -> if (codec.name.endsWith("16")) 2 else 1 }
    private fun <C : Any, A : Any> hex(vector: RasterVector<C, A>, bytes: Int): String = buildString {
        for (i in 0L..<vector.length) {
            val word = vector.storage.bits(vector[i])
            for (b in 0..<bytes) append(((word shr (b * 8)) and 255u).toString(16).padStart(2, '0'))
        }
    }
    private fun <P : Any, C : Any, A : Any, Q : Any, D : Any, B : Any> observe(c: JsonObject, source: RasterCodec<P, C, A>, target: RasterCodec<Q, D, B>): JsonObject {
        val op = c.string("op"); val mode = c.string("mode"); val observation = c.string("observe"); val effect = c.string("effect")
        val params = c.getValue("params").jsonArray.map { it.jsonPrimitive.long }
        val (w, h, offset, length, fx) = params; val fy = params[5]
        val src = RasterImage(w, h, source, lazy {
            if (observation.startsWith("bad-")) error("source data forced")
            val values = c.getValue("left").jsonArray
            val raw = RasterVector(source.storage, source.storage.allocate(values.size.toLong()))
            values.forEachIndexed { i, value -> raw.set(i.toLong(), source.storage.fromBits(value.jsonPrimitive.content.toULong())) }
            raw.slice(offset, length)
        })
        fun fingerprint(pixel: P): BigInteger = (0..<source.components).fold(BigInteger.ZERO) { sum, i -> sum + source.storage.bits(source.component(pixel, i)).toString().toBigInteger() * (i + 1).toBigInteger() }.mod(BigInteger.ONE.shiftLeft(32))
        fun value(x: Long, y: Long, pixel: () -> P): BigInteger = if (mode == "prefix-fail" && fingerprint(pixel()) == BigInteger.ZERO) error("prefix forced") else if (mode == "ignore" || mode == "missing") 91.toBigInteger() else fingerprint(pixel()) + (x * 37 + y * 257).toBigInteger()
        fun make(value: BigInteger): Q = target.fromComponents { channel -> target.storage.fromBits((value + (channel * 17).toBigInteger()).and(BigInteger.ONE.shiftLeft(64) - BigInteger.ONE).toString().toULong()) }
        fun pixel(x: Long, y: Long, pixel: () -> P): Q { if (mode == "value-fail") error("pixel value forced"); return make(value(x, y, pixel)) }
        fun guard(x: Long, y: Long) { if (mode == "effect-fail" || mode == "effect-fail-at" && x == fx && y == fy) error("effect forced") }
        fun summary(image: RasterImage<Q, D, B>): JsonObject = buildJsonObject {
            put("width", image.width); put("height", image.height)
            if (!observation.endsWith("metadata")) put("hex", hex(image.vector, bytes(target)))
        }
        val trace = mutableListOf<JsonElement>()
        val result = linkedMapOf<String, JsonElement>()
        // All effect instances below only implement Applicative; traversal comes
        // exclusively from production RasterTraversal.
        fun <F> traverse(app: RasterApplicative<F>, callback: (Long, Long, () -> P) -> RasterEffect<F, Q>): RasterEffect<F, RasterImage<Q, D, B>> =
            if (op == "pixels") RasterTraversal.imagePixels(src, target, app) { callback(0, 0, it) }
            else RasterTraversal.imageIPixels(src, target, app, callback)
        try {
            when (effect) {
                "identity" -> result.putAll(summary(RasterIdentity.run(traverse(RasterIdentity) { x, y, p -> guard(x, y); RasterIdentity.pure { pixel(x, y, p) } })))
                "io" -> {
                    val transformed = traverse(Io) { x, y, p -> guard(x, y); Io.action {
                        trace.add(JsonArray(listOf(JsonPrimitive(x), JsonPrimitive(y))))
                        if (mode == "action-fail" && x == fx && y == fy) error("IO action forced")
                        lazy { pixel(x, y, p) }
                    } }
                    result["image"] = summary(Io.run(transformed).value)
                }
                "const-list" -> {
                    val app = Const<List<JsonElement>>({ emptyList() }) { a, b -> a() + b() }
                    result["values"] = JsonArray(app.run(traverse(app) { x, y, p -> guard(x, y); app.of { listOf(JsonArray(listOf(JsonPrimitive(x), JsonPrimitive(y), JsonPrimitive(value(x, y, p).toBigDecimal())))) } }))
                }
                "const-any" -> {
                    val app = Const({ false }) { a, b -> a() || b() }
                    result["bool"] = JsonPrimitive(app.run(traverse(app) { x, y, _ -> guard(x, y); app.of { true } }))
                }
                "const-last" -> {
                    val app = Const<BigInteger?>({ null }) { a, b -> b() ?: a() }
                    val last = if (op == "foldmap") RasterOps.pixelFoldMap(src, object : RasterMonoid<BigInteger?> {
                        override fun empty(): BigInteger? = null
                        override fun append(left: () -> BigInteger?, right: () -> BigInteger?) = right() ?: left()
                    }) { value(0, 0, it) } else app.run(traverse(app) { x, y, p -> guard(x, y); app.of { value(x, y, p) } })
                    result["last"] = last?.let { JsonPrimitive(it.toBigDecimal()) } ?: JsonNull
                }
                "const-bracket" -> {
                    val app = Const({ "E" }) { a, b -> "(${a()}|${b()})" }
                    result["value"] = JsonPrimitive(app.run(traverse(app) { x, y, p -> guard(x, y); app.of { value(x, y, p).toString() } }))
                }
                "maybe" -> {
                    val transformed = traverse(Maybe) { x, y, p -> guard(x, y); if (mode == "missing" || mode == "missing-at" && x == fx && y == fy) None else Maybe.pure { pixel(x, y, p) } }
                    result["image"] = when (val output = Maybe.run(transformed)) { None -> JsonNull; is Some -> summary(output.value.value) }
                }
                "list" -> {
                    val transformed = traverse(ListApp) { x, y, p -> guard(x, y); Branches(listOf(lazy { pixel(x, y, p) }, lazy { make(value(x, y, p) + 1009.toBigInteger()) })) }
                    result["images"] = JsonArray(ListApp.run(transformed).map { summary(it.value) })
                }
                else -> error(effect)
            }
        } catch (e: Exception) {
            result.clear(); result["exception"] = JsonPrimitive(e.message?.lineSequence()?.first())
        }
        if (effect == "io") result["trace"] = JsonArray(trace)
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
    fun originalApplicativeObservations(): Stream<DynamicTest> {
        val input = checkNotNull(javaClass.getResourceAsStream("/images/raster-traversal.jsonl.gz"))
        val lines = GZIPInputStream(input).bufferedReader().use { it.readLines().drop(1) }
        return lines.map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.string("name")
            DynamicTest.dynamicTest(name) { val c = Json.parseToJsonElement(line).jsonObject; assertEquals(c.getValue("expected"), dispatch(c)) }
        }.asSequence().asStream()
    }
    @Test
    fun largeIdentityTraversalExecutesWritesWithoutRecursiveStackGrowth() {
        val source = RasterOps.generateImage(RasterCodecs.Y8, 100_000, 1) { x, _ -> x.toUByte() }
        val transformed = RasterIdentity.run(RasterTraversal.imageIPixels(source, RasterCodecs.RGBA16, RasterIdentity) { x, y, p ->
            RasterIdentity.pure { PixelRGBA16(x.toUShort(), y.toUShort(), p().toUShort(), 65535u) }
        })
        assertEquals(400_000L, transformed.vector.length)
        for (x in listOf(0L, 1L, 65535L, 99999L)) assertEquals(PixelRGBA16(x.toUShort(), 0u, x.toUByte().toUShort(), 65535u), RasterOps.pixelAt(transformed, x, 0))
    }
    @Test
    fun branchedResultsAllocateIndependentBuffersAndIoCanBeReplayed() {
        val source = RasterOps.generateImage(RasterCodecs.Y8, 2, 1) { x, _ -> x.toUByte() }
        val branches = ListApp.run(RasterTraversal.imagePixels(source, RasterCodecs.Y8, ListApp) { p -> Branches(listOf(lazy { p() }, lazy { (p() + 10u).toUByte() })) }).map { it.value }
        assertEquals(listOf("0001", "000b", "0a01", "0a0b"), branches.map { hex(it.vector, 1) })
        for (a in branches.indices) for (b in 0..<a) assertNotSame(branches[a].vector.data, branches[b].vector.data)
        RasterOps.unsafeThawImage(branches.first()).vector.set(0, 99u)
        assertEquals(listOf("6301", "000b", "0a01", "0a0b"), branches.map { hex(it.vector, 1) })
        val trace = mutableListOf<Long>()
        val action = RasterTraversal.imageIPixels(source, RasterCodecs.Y8, Io) { x, _, p -> Io.action { trace.add(x); lazy { p() } } }
        val first = Io.run(action).value; val second = Io.run(action).value
        assertEquals(listOf(0L, 1L, 0L, 1L), trace)
        assertNotSame(first.vector.data, second.vector.data)
    }
}
