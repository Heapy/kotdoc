// Codec.Picture.Types: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

/** Higher-kinded carrier for a caller's Applicative instance. */
internal interface RasterEffect<F, out A>

/**
 * Operands and their values are thunks, matching Haskell's non-strict (<$>)/(<*>).
 * An instance can collect effects, fail, branch, or discard values without
 * evaluating pixels or creating an output buffer.
 */
internal interface RasterApplicative<F> {
    fun <A> pure(value: () -> A): RasterEffect<F, A>
    fun <A, B> map(effect: () -> RasterEffect<F, A>, transform: (() -> A) -> B): RasterEffect<F, B>
    fun <A, B, C> map2(left: () -> RasterEffect<F, A>, right: () -> RasterEffect<F, B>, combine: (() -> A, () -> B) -> C): RasterEffect<F, C>
}

internal object RasterIdentity : RasterApplicative<RasterIdentity.Tag> {
    object Tag
    private class Value<A>(val value: Lazy<A>) : RasterEffect<Tag, A>
    @Suppress("UNCHECKED_CAST")
    fun <A> run(effect: RasterEffect<Tag, A>): A = (effect as Value<A>).value.value
    override fun <A> pure(value: () -> A): RasterEffect<Tag, A> = Value(lazy(value))
    override fun <A, B> map(effect: () -> RasterEffect<Tag, A>, transform: (() -> A) -> B): RasterEffect<Tag, B> {
        val value = lazy { run(effect()) }
        return Value(lazy { transform { value.value } })
    }
    override fun <A, B, C> map2(left: () -> RasterEffect<Tag, A>, right: () -> RasterEffect<Tag, B>, combine: (() -> A, () -> B) -> C): RasterEffect<Tag, C> {
        val a = lazy { run(left()) }; val b = lazy { run(right()) }
        return Value(lazy { combine({ a.value }, { b.value }) })
    }
}

internal object RasterTraversal {
    // Original GenST actions are accumulated by writePx, then executed only when
    // imageData is demanded. An immutable plan avoids a JVM call frame per write.
    private sealed interface Plan<Q : Any> {
        class Initial<Q : Any> : Plan<Q>
        class Write<Q : Any>(val previous: () -> Plan<Q>, val index: Long, val pixel: () -> Q) : Plan<Q>
    }
    private fun <Q : Any, D : Any, B : Any> execute(codec: RasterCodec<Q, D, B>, width: Long, height: Long, plan: () -> Plan<Q>): RasterVector<D, B> {
        val writes = ArrayList<Plan.Write<Q>>()
        var current = plan()
        while (current is Plan.Write) { writes.add(current); current = current.previous() }
        val vector = RasterVector(codec.storage, codec.storage.allocate(width * height * codec.components))
        for (i in writes.indices.reversed()) {
            val write = writes[i]
            RasterOps.writeAt(codec, vector, write.index, write.pixel(), false)
        }
        return vector
    }
    fun <F, P : Any, C : Any, A : Any, Q : Any, D : Any, B : Any> imagePixels(
        image: RasterImage<P, C, A>, target: RasterCodec<Q, D, B>, applicative: RasterApplicative<F>, transform: (() -> P) -> RasterEffect<F, Q>,
    ): RasterEffect<F, RasterImage<Q, D, B>> = traverse(image, target, applicative, false) { _, _, pixel -> transform(pixel) }

    fun <F, P : Any, C : Any, A : Any, Q : Any, D : Any, B : Any> imageIPixels(
        image: RasterImage<P, C, A>, target: RasterCodec<Q, D, B>, applicative: RasterApplicative<F>, transform: (Long, Long, () -> P) -> RasterEffect<F, Q>,
    ): RasterEffect<F, RasterImage<Q, D, B>> = traverse(image, target, applicative, true, transform)

    private fun <F, P : Any, C : Any, A : Any, Q : Any, D : Any, B : Any> traverse(
        image: RasterImage<P, C, A>, target: RasterCodec<Q, D, B>, applicative: RasterApplicative<F>, indexed: Boolean, transform: (Long, Long, () -> P) -> RasterEffect<F, Q>,
    ): RasterEffect<F, RasterImage<Q, D, B>> {
        val completed = lazy { buildEffects(image, target, applicative, indexed, transform) }
        return applicative.map({ completed.value }) { plan ->
            RasterImage(image.width, image.height, target, lazy { execute(target, image.width, image.height, plan) })
        }
    }
    private fun <F, P : Any, C : Any, A : Any, Q : Any, D : Any, B : Any> buildEffects(
        image: RasterImage<P, C, A>, target: RasterCodec<Q, D, B>, applicative: RasterApplicative<F>, indexed: Boolean, transform: (Long, Long, () -> P) -> RasterEffect<F, Q>,
    ): RasterEffect<F, Plan<Q>> {
        var effect: Lazy<RasterEffect<F, Plan<Q>>> = lazy { applicative.pure { Plan.Initial<Q>() } }
        var read = 0L; var write = 0L
        fun append(x: Long, y: Long) {
            val previous = effect; val readAt = read; val writeAt = write
            val pixel = lazy { RasterOps.readAt(image.codec, image.vector, readAt, false) }
            val transformed = lazy { transform(x, y) { pixel.value } }
            effect = lazy {
                applicative.map2({ previous.value }, { transformed.value }) { before, after -> Plan.Write(before, writeAt, after) }
            }
            read += image.codec.components; write += target.components
        }
        if (indexed) {
            var y = 0L
            while (y < image.height) { var x = 0L; while (x < image.width) { append(x, y); x++ }; y++ }
        } else {
            val limit = image.width * image.height * image.codec.components
            while (read < limit) append(0, 0)
        }
        return effect.value
    }
}
