// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

/** Primitive JVM storage; logical dimensions and component addresses remain Int64. */
internal interface RasterStorage<C : Any, A : Any> {
    fun length(data: A): Int
    fun allocate(length: Long): A
    fun copy(data: A, start: Int, length: Int): A
    fun get(data: A, index: Int): C
    fun set(data: A, index: Int, value: C)
    fun bits(value: C): ULong
    fun fromBits(value: ULong): C
    fun equal(a: C, b: C): Boolean
    fun hash(value: C): Int
}

internal interface RasterCodec<P : Any, C : Any, A : Any> {
    val name: String
    val components: Int
    val storage: RasterStorage<C, A>
    fun read(data: A, address: (Int) -> Int): P
    fun write(data: A, value: P, address: (Int) -> Int)
    fun component(value: P, channel: Int): C
    fun fromComponents(read: (Int) -> C): P
}

/** A shared component slice. Checked access uses its logical length, not the parent. */
internal class RasterVector<C : Any, A : Any>(val storage: RasterStorage<C, A>, val data: A, val offset: Long = 0, val length: Long = storage.length(data).toLong() - offset) {
    init { checkSlice(offset, length, storage.length(data).toLong()) }
    fun slice(start: Long, count: Long): RasterVector<C, A> {
        checkSlice(start, count, length)
        return RasterVector(storage, data, offset + start, count)
    }
    fun address(index: Long, checked: Boolean): Int {
        if (checked && (index < 0 || index >= length)) throw ImagePixelException("index out of bounds ($index,$length)")
        return Math.toIntExact(offset + index)
    }
    operator fun get(index: Long): C = storage.get(data, address(index, true))
    fun set(index: Long, value: C) = storage.set(data, address(index, true), value)
    fun copy() = RasterVector(storage, storage.copy(data, Math.toIntExact(offset), Math.toIntExact(length)))
    companion object {
        fun checkSlice(start: Long, count: Long, length: Long) {
            if (start < 0 || count < 0 || start > length - count) throw ImagePixelException("invalid slice ($start,$count,$length)")
        }
        fun allocationLength(length: Long): Int {
            if (length < 0) throw ImagePixelException("negative length $length")
            return Math.toIntExact(length)
        }
    }
}

/** The data vector is lazy, as in the original Image record; dimensions are Int64. */
internal class RasterImage<P : Any, C : Any, A : Any>(val width: Long, val height: Long, val codec: RasterCodec<P, C, A>, private val data: Lazy<RasterVector<C, A>>) {
    constructor(width: Long, height: Long, codec: RasterCodec<P, C, A>, vector: RasterVector<C, A>) : this(width, height, codec, lazyOf(vector))
    val vector: RasterVector<C, A> get() = data.value
    fun withVector(vector: RasterVector<C, A>) = RasterImage(width, height, codec, vector)
    fun slice(start: Long, length: Long) = withVector(vector.slice(start, length))
    override fun equals(other: Any?): Boolean {
        if (other !is RasterImage<*, *, *> || codec !== other.codec || width != other.width || height != other.height) return false
        @Suppress("UNCHECKED_CAST") val that = other as RasterImage<P, C, A>
        val a = vector; val b = that.vector
        if (a.length != b.length) return false
        for (i in 0L..<a.length) if (!codec.storage.equal(a[i], b[i])) return false
        return true
    }
    override fun hashCode(): Int {
        var result = (codec.name.hashCode() * 31 + width.hashCode()) * 31 + height.hashCode()
        val v = vector
        for (i in 0L..<v.length) result = result * 31 + codec.storage.hash(v[i])
        return result
    }
}

internal class MutableRasterImage<P : Any, C : Any, A : Any>(val width: Long, val height: Long, val codec: RasterCodec<P, C, A>, val vector: RasterVector<C, A>)

/**
 * Lazy operands preserve short-circuit monoids, including Any and Last. Set
 * strictTail only for a pure append that always demands both operands; this
 * permits a stack-safe right fold.
 */
internal interface RasterMonoid<M> {
    val strictTail: Boolean get() = false
    fun empty(): M
    fun append(left: () -> M, right: () -> M): M
}

internal class RasterFoldResult<S, P : Any, C : Any, A : Any>(width: Long, height: Long, codec: RasterCodec<P, C, A>, compute: () -> Pair<S, RasterVector<C, A>>) {
    private val result = lazy(compute)
    val state: S get() = result.value.first
    val image = RasterImage(width, height, codec, lazy { result.value.second })
}

internal object RasterOps {
    fun <P : Any, C : Any, A : Any> pixelBaseIndex(image: RasterImage<P, C, A>, x: Long, y: Long) = (x + y * image.width) * image.codec.components
    fun <P : Any, C : Any, A : Any> readAt(codec: RasterCodec<P, C, A>, vector: RasterVector<C, A>, index: Long, checked: Boolean): P = codec.read(vector.data) { vector.address(index + it, checked) }
    fun <P : Any, C : Any, A : Any> writeAt(codec: RasterCodec<P, C, A>, vector: RasterVector<C, A>, index: Long, value: P, checked: Boolean) = codec.write(vector.data, value) { vector.address(index + it, checked) }
    fun <P : Any, C : Any, A : Any> pixelAt(image: RasterImage<P, C, A>, x: Long, y: Long) = readAt(image.codec, image.vector, pixelBaseIndex(image, x, y), true)
    fun <P : Any, C : Any, A : Any> readPixel(image: MutableRasterImage<P, C, A>, x: Long, y: Long) = readAt(image.codec, image.vector, (x + y * image.width) * image.codec.components, true)
    fun <P : Any, C : Any, A : Any> writePixel(image: MutableRasterImage<P, C, A>, x: Long, y: Long, value: P) = writeAt(image.codec, image.vector, (x + y * image.width) * image.codec.components, value, true)
    fun <P : Any, C : Any, A : Any> newMutableImage(width: Long, height: Long, codec: RasterCodec<P, C, A>): MutableRasterImage<P, C, A> =
        MutableRasterImage(width, height, codec, RasterVector(codec.storage, codec.storage.allocate(width * height * codec.components)))
    fun <P : Any, C : Any, A : Any> thawImage(image: RasterImage<P, C, A>) = MutableRasterImage(image.width, image.height, image.codec, image.vector.copy())
    fun <P : Any, C : Any, A : Any> unsafeThawImage(image: RasterImage<P, C, A>) = MutableRasterImage(image.width, image.height, image.codec, image.vector)
    fun <P : Any, C : Any, A : Any> freezeImage(image: MutableRasterImage<P, C, A>) = RasterImage(image.width, image.height, image.codec, image.vector.copy())
    fun <P : Any, C : Any, A : Any> unsafeFreezeImage(image: MutableRasterImage<P, C, A>) = RasterImage(image.width, image.height, image.codec, image.vector)

    private inline fun rows(width: Long, height: Long, action: (Long, Long) -> Unit) {
        var y = 0L
        while (y < height) { var x = 0L; while (x < width) { action(x, y); x++ }; y++ }
    }
    private inline fun enumeration(count: Long, action: (Long) -> Unit) {
        val end = count - 1 // Haskell [0 .. count-1], including Int64 overflow.
        if (end < 0) return
        var i = 0L
        while (true) { action(i); if (i == end) return; i++ }
    }
    fun <P : Any, C : Any, A : Any> generateMutableImage(codec: RasterCodec<P, C, A>, width: Long, height: Long, generate: (Long, Long) -> P): MutableRasterImage<P, C, A> {
        val image = newMutableImage(width, height, codec)
        var index = 0L
        rows(width, height) { x, y -> writeAt(codec, image.vector, index, generate(x, y), false); index += codec.components }
        return image
    }
    // Original runST executes generation before returning the observable Image record.
    fun <P : Any, C : Any, A : Any> generateImage(codec: RasterCodec<P, C, A>, width: Long, height: Long, generate: (Long, Long) -> P) =
        unsafeFreezeImage(generateMutableImage(codec, width, height, generate))
    fun <P : Any, C : Any, A : Any> createMutableImage(codec: RasterCodec<P, C, A>, width: Long, height: Long, background: P) =
        generateMutableImage(codec, width, height) { _, _ -> background }
    fun <P : Any, C : Any, A : Any> withImage(codec: RasterCodec<P, C, A>, width: Long, height: Long, generate: (Long, Long) -> P): RasterImage<P, C, A> {
        val image = newMutableImage(width, height, codec)
        var index = 0L
        enumeration(height) { y -> enumeration(width) { x -> writeAt(codec, image.vector, index, generate(x, y), false); index += codec.components } }
        return unsafeFreezeImage(image)
    }
    fun <S, P : Any, C : Any, A : Any> generateFoldImage(codec: RasterCodec<P, C, A>, initial: S, width: Long, height: Long, generate: (S, Long, Long) -> Pair<S, P>) = RasterFoldResult(width, height, codec) {
        val image = newMutableImage(width, height, codec)
        var state = initial
        enumeration(height) { y -> enumeration(width) { x ->
            val (next, pixel) = generate(state, x, y)
            writePixel(image, x, y, pixel)
            state = next
        } }
        state to image.vector
    }
    // Thunks expose original non-strict callback arguments to Kotlin callers.
    // Accumulators can likewise use Lazy<S> when callback demand is significant.
    fun <P : Any, C : Any, A : Any, Q : Any, D : Any, B : Any> pixelMapLazy(image: RasterImage<P, C, A>, target: RasterCodec<Q, D, B>, transform: (() -> P) -> Q) = pixelMapXYLazy(image, target) { _, _, pixel -> transform(pixel) }
    fun <P : Any, C : Any, A : Any, Q : Any, D : Any, B : Any> pixelMapXYLazy(image: RasterImage<P, C, A>, target: RasterCodec<Q, D, B>, transform: (Long, Long, () -> P) -> Q): RasterImage<Q, D, B> = RasterImage(image.width, image.height, target, lazy {
        val result = newMutableImage(image.width, image.height, target)
        var read = 0L; var write = 0L
        rows(image.width, image.height) { x, y ->
            val at = read
            val pixel = lazy { readAt(image.codec, image.vector, at, false) }
            writeAt(target, result.vector, write, transform(x, y) { pixel.value }, false)
            read += image.codec.components; write += target.components
        }
        result.vector
    })
    fun <P : Any, C : Any, A : Any, Q : Any, D : Any, B : Any> pixelMap(image: RasterImage<P, C, A>, target: RasterCodec<Q, D, B>, transform: (P) -> Q) = pixelMapLazy(image, target) { transform(it()) }
    fun <P : Any, C : Any, A : Any, Q : Any, D : Any, B : Any> pixelMapXY(image: RasterImage<P, C, A>, target: RasterCodec<Q, D, B>, transform: (Long, Long, P) -> Q) = pixelMapXYLazy(image, target) { x, y, pixel -> transform(x, y, pixel()) }

    fun <S, P : Any, C : Any, A : Any> pixelFoldLazy(image: RasterImage<P, C, A>, initial: S, fold: (S, Long, Long, () -> P) -> S): S {
        val vector = image.vector // Original pixelFold has a strict !vec, even for an empty raster.
        var state = initial; var index = 0L
        rows(image.width, image.height) { x, y ->
            val at = index; val pixel = lazy { readAt(image.codec, vector, at, false) }
            state = fold(state, x, y) { pixel.value }; index += image.codec.components
        }
        return state
    }
    fun <S, P : Any, C : Any, A : Any> pixelFoldMLazy(image: RasterImage<P, C, A>, initial: S, fold: (S, Long, Long, () -> P) -> S): S {
        var state = initial
        rows(image.width, image.height) { x, y -> val pixel = lazy { pixelAt(image, x, y) }; state = fold(state, x, y) { pixel.value } }
        return state
    }
    fun <S, P : Any, C : Any, A : Any> pixelFold(image: RasterImage<P, C, A>, initial: S, fold: (S, Long, Long, P) -> S) = pixelFoldLazy(image, initial) { state, x, y, pixel -> fold(state, x, y, pixel()) }
    fun <S, P : Any, C : Any, A : Any> pixelFoldM(image: RasterImage<P, C, A>, initial: S, fold: (S, Long, Long, P) -> S) = pixelFoldMLazy(image, initial) { state, x, y, pixel -> fold(state, x, y, pixel()) }
    fun <M, P : Any, C : Any, A : Any> pixelFoldMap(image: RasterImage<P, C, A>, monoid: RasterMonoid<M>, transform: (() -> P) -> M): M {
        val count = image.width * image.height * image.codec.components
        if (monoid.strictTail) {
            val values = ArrayList<M>()
            var index = 0L
            while (index < count) { val at = index; val pixel = lazy { readAt(image.codec, image.vector, at, false) }; values.add(transform { pixel.value }); index += image.codec.components }
            var result = monoid.empty()
            for (i in values.indices.reversed()) { val tail = result; result = monoid.append({ values[i] }) { tail } }
            return result
        }
        fun folder(index: Long): M {
            if (index >= count) return monoid.empty()
            val pixel = lazy { readAt(image.codec, image.vector, index, false) }
            val tail = lazy { folder(index + image.codec.components) }
            val head = lazy { transform { pixel.value } }
            return monoid.append({ head.value }) { tail.value }
        }
        return folder(0)
    }
    fun <P : Any, C : Any, A : Any> zipPixelComponent3(a: RasterImage<P, C, A>, b: RasterImage<P, C, A>, c: RasterImage<P, C, A>, combine: (C, C, C) -> C): RasterImage<P, C, A> {
        if (a.width != b.width || a.width != c.width || a.height != b.height || a.height != c.height) error("Different image size zipPairwisePixelComponent")
        return RasterImage(a.width, a.height, a.codec, lazy {
            val x = a.vector; val y = b.vector; val z = c.vector
            val data = a.codec.storage.allocate(minOf(x.length, y.length, z.length))
            val out = RasterVector(a.codec.storage, data)
            for (i in 0L..<out.length) out.set(i, combine(x[i], y[i], z[i]))
            out
        })
    }
}
