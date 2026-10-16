// Crypto.Number.F2m, crypton 2.0.1; Danny Navarro and crypton contributors.
// Full upstream notice: licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

import java.math.BigInteger

typealias BinaryPolynomial = BigInteger

/** The constructor and its payload have separate demand boundaries, as in Maybe Integer. */
sealed interface F2mMaybe {
    data object None : F2mMaybe
    class Some internal constructor(private val stored: Lazy<BigInteger>) : F2mMaybe {
        val value: BigInteger get() = stored.value
        override fun equals(other: Any?) = other is Some && value == other.value
        override fun hashCode() = value.hashCode()
    }
}

/** Binary-polynomial arithmetic. Like upstream, these operations are not constant time. */
object F2m {
    private val zero = BigInteger.ZERO
    private val one = BigInteger.ONE
    private val spread = IntArray(256) { b ->
        (0..<8).fold(0) { acc, bit -> if (b and (1 shl bit) != 0) acc or (1 shl (2 * bit)) else acc }
    }

    fun addF2m(a: BigInteger, b: BigInteger): BigInteger = a.xor(b)
    fun addF2mDeferred(a: Lazy<BigInteger>, b: Lazy<BigInteger>): BigInteger {
        // The pinned optimized Integer XOR worker demands the right operand first.
        val right = b.value
        return a.value.xor(right)
    }

    fun modF2m(f: BinaryPolynomial, a: BigInteger) = modF2mDeferred(lazyOf(f), lazyOf(a))
    fun modF2mDeferred(f: Lazy<BinaryPolynomial>, a: Lazy<BigInteger>): BigInteger {
        if (f.value.signum() < 0 || a.value.signum() < 0)
            error("modF2m: negative number represent no binary polynomial")
        if (f.value == zero) error("modF2m: cannot divide by zero polynomial")
        if (f.value == one) return zero
        val degree = log2(f.value)
        val exponents = (0..<degree).filter { f.value.testBit(it) }
        var n = a.value
        if (exponents.size <= 17) {
            val mask = one.shiftLeft(degree).subtract(one)
            while (n > mask) {
                val hi = n.shiftRight(degree)
                n = exponents.fold(n.and(mask)) { acc, bit -> acc.xor(hi.shiftLeft(bit)) }
            }
        } else {
            while (n.bitLength() > degree) n = n.xor(f.value.shiftLeft(log2(n) - degree))
        }
        return n
    }

    fun mulF2m(f: BinaryPolynomial, a: BigInteger, b: BigInteger) = mulF2mDeferred(lazyOf(f), lazyOf(a), lazyOf(b))
    fun mulF2mDeferred(f: Lazy<BinaryPolynomial>, a: Lazy<BigInteger>, b: Lazy<BigInteger>): BigInteger {
        // Preserve the pinned GHC worker's strict argument entry order before its guards.
        b.value; a.value; f.value
        return multiplyGuarded(f, a, b)
    }
    private fun multiplyGuarded(f: Lazy<BinaryPolynomial>, a: Lazy<BigInteger>, b: Lazy<BigInteger>): BigInteger {
        if (f.value.signum() < 0 || a.value.signum() < 0 || b.value.signum() < 0)
            error("mulF2m: negative number represent no binary polynomial")
        if (f.value == zero) error("mulF2m: cannot multiply modulo zero polynomial")
        val multiples = Array(16) { zero }
        for (i in 1..<16) {
            val bit = Integer.numberOfTrailingZeros(i)
            multiples[i] = multiples[i xor (1 shl bit)].xor(a.value.shiftLeft(bit))
        }
        var v = b.value
        var shift = 0
        var product = zero
        while (v != zero) {
            product = product.xor(multiples[v.toInt() and 15].shiftLeft(shift))
            shift += 4
            v = v.shiftRight(4)
        }
        return modF2m(f.value, product)
    }

    /** Upstream squareF2m', before modular reduction. */
    fun squareF2mRaw(a: BigInteger): BigInteger {
        if (a.signum() < 0) error("mulF2m: negative number represent no binary polynomial")
        var v = a
        var shift = 0
        var result = zero
        while (v != zero) {
            result = result.or(BigInteger.valueOf(spread[v.toInt() and 255].toLong()).shiftLeft(shift))
            shift += 16
            v = v.shiftRight(8)
        }
        return result
    }

    fun squareF2m(f: BinaryPolynomial, a: BigInteger) = squareF2mDeferred(lazyOf(f), lazyOf(a))
    fun squareF2mDeferred(f: Lazy<BinaryPolynomial>, a: Lazy<BigInteger>) =
        modF2mDeferred(f, lazy { squareF2mRaw(a.value) })

    fun powF2m(f: BinaryPolynomial, a: BigInteger, exponent: BigInteger) =
        powF2mDeferred(lazyOf(f), lazyOf(a), lazyOf(exponent))
    fun powF2mDeferred(f: Lazy<BinaryPolynomial>, a: Lazy<BigInteger>, exponent: Lazy<BigInteger>): BigInteger {
        val b = exponent.value
        f.value
        if (b.signum() < 0) error("powF2m: negative exponents disallowed")
        var remaining = b
        val steps = ArrayList<Boolean>()
        while (remaining != zero) {
            val odd = remaining.testBit(0)
            // An even step enters modF2m's first guard before its recursive argument.
            if (!odd && f.value.signum() < 0) error("modF2m: negative number represent no binary polynomial")
            steps.add(odd)
            remaining = remaining.shiftRight(1)
        }
        var result = if (f.value > one) one else zero
        for (i in steps.indices.reversed()) {
            result = if (steps[i]) mulF2mDeferred(f, a, lazyOf(squareF2mRaw(result)))
            else squareF2m(f.value, result)
        }
        return result
    }

    fun sqrtF2m(f: BinaryPolynomial, a: BigInteger) = sqrtF2mDeferred(lazyOf(f), lazyOf(a))
    fun sqrtF2mDeferred(f: Lazy<BinaryPolynomial>, a: Lazy<BigInteger>): BigInteger {
        val initial = a.value
        var count = log2(f.value) - 1
        if (count >= 0) {
            var result = initial
            repeat(count) { result = squareF2m(f.value, result) }
            return result
        }
        var x = a
        // No finite fallback is introduced for the upstream undefined degree-zero case.
        while (count != 0) {
            val previous = x
            x = lazy { squareF2mDeferred(f, previous) }
            count--
        }
        return x.value
    }

    fun invF2m(f: BinaryPolynomial, a: BigInteger) = invF2mDeferred(lazyOf(f), lazyOf(a))
    fun invF2mDeferred(f: Lazy<BinaryPolynomial>, a: Lazy<BigInteger>): F2mMaybe {
        var r1 = f.value
        var r0 = a.value
        var s0 = one
        var s1 = zero
        // The unused third Bezout coefficient is never demanded by invF2m.
        while (r1 != zero) {
            val shift = (log2(r0) - log2(r1)).coerceAtLeast(0)
            val nextR = r0.xor(r1.shiftLeft(shift))
            val nextS = s0.xor(s1.shiftLeft(shift))
            r0 = r1; r1 = nextR; s0 = s1; s1 = nextS
        }
        val coefficient = s0
        return if (r0 == one) F2mMaybe.Some(lazy { modF2mDeferred(f, lazyOf(coefficient)) }) else F2mMaybe.None
    }

    fun divF2m(f: BinaryPolynomial, a: BigInteger, b: BigInteger) = divF2mDeferred(lazyOf(f), lazyOf(a), lazyOf(b))
    fun divF2mDeferred(f: Lazy<BinaryPolynomial>, a: Lazy<BigInteger>, b: Lazy<BigInteger>): F2mMaybe =
        when (val inverse = invF2mDeferred(f, b)) {
            F2mMaybe.None -> F2mMaybe.None
            // The mapped call retains the source guards before demanding the inverse payload;
            // it does not enter the standalone multiplication worker first.
            is F2mMaybe.Some -> F2mMaybe.Some(lazy { multiplyGuarded(f, a, lazy { inverse.value }) })
        }

    fun quadraticF2m(f: BinaryPolynomial, a: BigInteger) = quadraticF2mDeferred(lazyOf(f), lazyOf(a))
    fun quadraticF2mDeferred(f: Lazy<BinaryPolynomial>, a: Lazy<BigInteger>): F2mMaybe {
        val degree = log2(f.value)
        if (trace(f, a, degree, false) != zero) return F2mMaybe.None
        return F2mMaybe.Some(lazy { trace(f, a, 1 + (degree ushr 1), true) })
    }

    private fun trace(f: Lazy<BinaryPolynomial>, a: Lazy<BigInteger>, count: Int, twice: Boolean): BigInteger {
        var result = zero
        var x = a
        repeat(count) { i ->
            result = result.xor(x.value)
            if (i + 1 < count) {
                val previous = x
                x = lazy { if (twice) squareF2m(f.value, squareF2mDeferred(f, previous)) else squareF2mDeferred(f, previous) }
            }
        }
        return result
    }

    // Crypto.Number.Basic's non-GMP fallback, including zero and negative inputs.
    private fun log2(n: BigInteger) = if (n < BigInteger.TWO) 0 else n.bitLength() - 1
}
