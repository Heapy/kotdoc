// Crypto.Number.Basic/Compat, crypton 2.0.1; Vincent Hanquez and contributors.
// Full upstream notice: licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

import java.math.BigInteger

/** Numeric results follow the pinned GHC 9.12.2/integer-gmp build. */
object CryptoNumberBasic {
    private val zero = BigInteger.ZERO
    private val one = BigInteger.ONE
    private val two = BigInteger.TWO

    fun numBits(n: BigInteger): Int = n.abs().bitLength()
    fun numBytes(n: BigInteger): Int = (numBits(n) + 7) / 8
    fun log2(n: BigInteger): Int = if (n <= zero) 0 else n.bitLength() - 1

    fun sqrti(n: BigInteger): Pair<BigInteger, BigInteger> {
        if (n < zero) error("cannot compute negative square root")
        if (n <= one) return n to n
        if (n == two) return one to two
        val digits = n.toString().length
        val even = digits % 2 == 0
        val power = (if (even) digits - 2 else digits - 1) / 2
        val initial = BigInteger.valueOf(if (even) 2 else 6) * BigInteger.TEN.pow(power)
        if (initial * initial == n) return initial to initial
        var lower: BigInteger
        var upper: BigInteger
        if (initial * initial < n) {
            lower = initial
            upper = lower * two
            while (upper * upper < n) { lower = upper; upper = lower * two }
        } else {
            upper = initial
            lower = upper / two
            while (lower * lower >= n) { upper = lower; lower = upper / two }
        }
        while (lower != upper && lower + one != upper) {
            val d = (upper - lower) / two
            if ((lower + d).pow(2) >= n) upper -= d else lower += d
        }
        return lower to upper
    }

    fun asPowerOf2AndOdd(n: BigInteger): Pair<Int, BigInteger> {
        if (n == zero) return 0 to zero
        val power = n.lowestSetBit
        return power to n.shiftRight(power)
    }

    fun areEven(values: Iterable<BigInteger>): Boolean = values.all { !it.testBit(0) }
    fun areEvenDeferred(values: Sequence<Lazy<BigInteger>>): Boolean = values.all { !it.value.testBit(0) }

    class Bezout internal constructor(private val coefficient: Lazy<Pair<BigInteger, BigInteger>>, private val a: Lazy<BigInteger>, private val b: Lazy<BigInteger>) {
        val x: BigInteger get() = coefficient.value.first
        val y: BigInteger get() {
            val result = coefficient.value
            val denominator = b.value
            if (denominator == BigInteger.ZERO) throw ArithmeticException("divide by zero")
            return (result.second - result.first * a.value) / denominator
        }
        val gcd: BigInteger get() = coefficient.value.second
    }

    fun gcde(a: BigInteger, b: BigInteger) = gcdeDeferred(lazyOf(a), lazyOf(b))
    fun gcdeDeferred(a: Lazy<BigInteger>, b: Lazy<BigInteger>): Bezout = Bezout(lazy {
        // The legacy GMP wrapper returns its triple before entering either integer.
        val av = a.value; val bv = b.value
        var r0 = av.abs(); var r1 = bv.abs(); var s0 = one; var s1 = zero
        if (r0 == zero && r1 == zero) return@lazy zero to zero
        while (r1 != zero) {
            val q = r0 / r1
            val r = r0 - q * r1; val s = s0 - q * s1
            r0 = r1; r1 = r; s0 = s1; s1 = s
        }
        if (bv != zero) {
            val period = bv.abs() / r0
            s0 = s0.mod(period)
            // GMP selects the positive half-period coefficient for positive a.
            if (s0 * two > period) s0 -= period
        }
        if (av.signum() < 0) s0 = -s0
        s0 to r0
    }, a, b)
}
