// QuickCheck 2.16.0.0: Copyright (c) 2000-2019 Koen Claessen,
// 2006-2008 Björn Bringert, 2009-2019 Nick Smallbone. BSD-3-Clause.
// splitmix 0.1.3.2: Copyright (c) 2017 Oleg Grenrus. BSD-3-Clause.
// random 1.3.1: Copyright (c) The University of Glasgow 2001.
// Original mixed BSD-compatible license; licenses/random-LICENSE.txt.
// See licenses/ and retained vendor sources.
package io.heapy.kotdoc.ast

/** Actual QCGen/Gen split tree, rather than a sequential Kotlin Random stream. */
internal object OriginalQuickCheck {
    private fun mix(x: Long): Long {
        var z = (x xor (x ushr 33)) * 0xff51afd7ed558ccdUL.toLong()
        z = (z xor (z ushr 33)) * 0xc4ceb9fe1a85ec53UL.toLong()
        return z xor (z ushr 33)
    }
    private fun gamma(x: Long): Long {
        var z = (x xor (x ushr 30)) * 0xbf58476d1ce4e5b9UL.toLong()
        z = (z xor (z ushr 27)) * 0x94d049bb133111ebUL.toLong()
        z = (z xor (z ushr 31)) or 1L
        return if ((z xor (z ushr 1)).countOneBits() >= 24) z else z xor 0xaaaaaaaaaaaaaaaaUL.toLong()
    }
    data class Seed(val state: Long, val gamma: Long) {
        fun split(): Pair<Seed,Seed> {
            val first = state + gamma
            val second = first + gamma
            return Seed(second,gamma) to Seed(mix(first), OriginalQuickCheck.gamma(second))
        }
        fun next(): Pair<Long,Seed> = (state + gamma).let { mix(it) to copy(state=it) }
        fun bounded(range: ULong, randomPackage: Boolean = false): Long {
            val top = if (randomPackage) range or 1UL else range
            val mask = if (top == 0UL) 0UL else ULong.MAX_VALUE shr top.countLeadingZeroBits()
            var cursor = this
            while (true) {
                val (word,next) = cursor.next(); cursor = next
                val value = word.toULong() and mask
                if (value <= range) return value.toLong()
            }
        }
        companion object {
            fun fromInt(seed: Long) = Seed(mix(seed), OriginalQuickCheck.gamma(seed + 0x9e3779b97f4a7c15UL.toLong()))
        }
    }
    class Gen<A>(val run: (Seed,Int) -> A) {
        fun <B> map(f: (A) -> B) = Gen<B> { r,n -> f(run(r,n)) }
        fun <B> bind(f: (A) -> Gen<B>) = Gen<B> { r,n ->
            val (left,right) = r.split(); f(run(left,n)).run(right,n)
        }
        fun resize(size: Int): Gen<A> { require(size >= 0); return Gen { r,_ -> run(r,size) } }
    }
    fun <A> pure(x: A) = Gen<A> { _,_ -> x }
    fun <A> sized(f: (Int) -> Gen<A>) = Gen<A> { r,n -> f(n).run(r,n) }
    /** Gen's <*> is ap, including the second bind before return. */
    fun <A,B> apply(f: Gen<(A)->B>, x: Gen<A>): Gen<B> = f.bind { g -> x.bind { pure(g(it)) } }
    fun <A,B,C> zip(a: Gen<A>, b: Gen<B>, f: (A,B)->C) = apply(a.map { av -> { bv: B -> f(av,bv) } },b)
    fun choose(low: Long, high: Long): Gen<Long> = Gen { r,_ ->
        val lo = minOf(low,high); val hi = maxOf(low,high)
        if (lo == hi) lo else lo + r.bounded(hi.toULong() - lo.toULong(),true)
    }
    fun chooseInt(low: Int, high: Int): Gen<Int> {
        val lo = minOf(low,high); val hi = maxOf(low,high)
        val raw = Gen<Long> { r,_ -> r.bounded(hi.toLong().toULong() - lo.toLong().toULong()) }
        return raw.bind { pure((it + lo).toInt()) }
    }
    fun <A> elements(xs: List<A>): Gen<A> = chooseInt(0,xs.lastIndex).map { xs[it] }
    fun <A> frequency(xs: List<Pair<Int,Gen<A>>>): Gen<A> = chooseInt(1,xs.sumOf { it.first }).bind { ticket ->
        var remaining = ticket
        xs.first { (weight,_) -> remaining -= weight; remaining <= 0 }.second
    }
    private class VectorCell<A>(val result: Result<A>)
    /** replicateM/liftA2(:) preserves lazy, shared elements on a finite spine. */
    fun <A> vector(size: Int, gen: Gen<A>): Gen<List<A>> = Gen { r,n ->
        val count = size
        if (count <= 0) emptyList() else object : AbstractList<A>() {
            override val size = count
            private val seeds = mutableListOf(r)
            private val cells = mutableMapOf<Int,VectorCell<A>>()
            override fun get(index: Int): A = synchronized(this) {
                if (index < 0 || index >= size) throw IndexOutOfBoundsException("index=$index size=$size")
                cells[index]?.let { return@synchronized it.result.getOrThrow() }
                // ap's first bind runs the element at left. Its second bind runs
                // the tail at right.left; right.right feeds the ignored pure.
                while (seeds.size <= index) seeds += seeds.last().split().second.split().first
                val cell = VectorCell(runCatching { gen.run(seeds[index].split().first,n) })
                cells[index] = cell // Include null values and synchronous failures.
                cell.result.getOrThrow()
            }
        }
    }
    fun <A> list(gen: Gen<A>, nonempty: Boolean = false): Gen<List<A>> = sized { n ->
        chooseInt(if(nonempty) 1 else 0, if(nonempty) maxOf(1,n) else n).bind { vector(it,gen) }
    }
    fun <A> suchThat(gen: Gen<A>, predicate: (A)->Boolean): Gen<A> {
        fun attempt(m: Int, end: Int): Gen<A?> = if (m > end) pure(null) else gen.resize(m).bind { x ->
            if (predicate(x)) pure(x) else attempt(m+1,end)
        }
        return sized { n -> attempt(n,2*n) }.bind { result ->
            if(result != null) pure(result) else sized { n -> suchThat(gen,predicate).resize(n+1) }
        }
    }
    fun integral(): Gen<Long> = sized { n -> suchThat(chooseInt(-n,n)) { true }.map { it.toLong() } }
}
