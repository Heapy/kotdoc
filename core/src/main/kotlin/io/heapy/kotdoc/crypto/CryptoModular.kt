// Crypto.Number.ModArithmetic, crypton 2.0.1; Vincent Hanquez and contributors.
// Full upstream notice: licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

import java.math.BigInteger

class CoprimesAssertionError : RuntimeException("CoprimesAssertionError")
class ModulusAssertionError : RuntimeException("ModulusAssertionError")

object CryptoModular {
    private val zero = BigInteger.ZERO
    private val one = BigInteger.ONE
    private val two = BigInteger.TWO
    private val three = BigInteger.valueOf(3)
    private val eight = BigInteger.valueOf(8)

    fun expFast(base: BigInteger, exponent: BigInteger, modulus: BigInteger) = expFastDeferred(lazyOf(base), lazyOf(exponent), lazyOf(modulus))
    fun expFastDeferred(base: Lazy<BigInteger>, exponent: Lazy<BigInteger>, modulus: Lazy<BigInteger>): BigInteger {
        // Entry order of the pinned GHC/GMP worker, including zero/one moduli.
        val m = modulus.value.abs(); val b = base.value; val e = exponent.value
        if (m <= one) return zero
        return try { b.modPow(e, m) } catch (_: ArithmeticException) { zero }
    }

    fun expSafe(base: BigInteger, exponent: BigInteger, modulus: BigInteger) = expSafeDeferred(lazyOf(base), lazyOf(exponent), lazyOf(modulus))
    fun expSafeDeferred(base: Lazy<BigInteger>, exponent: Lazy<BigInteger>, modulus: Lazy<BigInteger>): BigInteger =
        expSafeAudited(base, exponent, modulus, null)

    internal fun expSafeAudited(base: Lazy<BigInteger>, exponent: Lazy<BigInteger>, modulus: Lazy<BigInteger>, audit: CryptoMontgomery.Audit?, allocate: (Int) -> LongArray = { LongArray(it) }): BigInteger {
        val m = modulus.value
        if (!m.testBit(0) || m <= one || exponent.value.signum() < 0) return expFastDeferred(base, exponent, modulus)
        val e = exponent.value
        val width = (m.bitLength() + 7) / 8
        val eWidth = 8 * (((e.bitLength() + 7) / 8 + 7) / 8)
        val b = encodeFixed(base.value.mod(m), width)
        val expo = encodeFixed(e, eWidth)
        val modu = encodeFixed(m, width)
        val out = ByteArray(width)
        val status = CryptoMontgomery.powInto(out, b, expo, modu, audit, allocate)
        expo.fill(0)
        audit?.exponentCleared = expo.all { it == 0.toByte() }
        return if (status == 0) BigInteger(1, out) else expFastDeferred(base, exponent, modulus)
    }

    private fun encodeFixed(n: BigInteger, width: Int): ByteArray {
        val result = ByteArray(width)
        val bytes = n.toByteArray()
        val count = minOf(width, bytes.size)
        bytes.copyInto(result, width - count, bytes.size - count)
        bytes.fill(0)
        return result
    }

    fun inverse(g: BigInteger, m: BigInteger) = inverseDeferred(lazyOf(g), lazyOf(m))
    fun inverseDeferred(g: Lazy<BigInteger>, m: Lazy<BigInteger>): CryptoMaybe<BigInteger> {
        val value = g.value
        if (value == zero) return CryptoMaybe.None
        val modulus = m.value.abs()
        if (modulus <= one) return CryptoMaybe.None
        val r = try { value.modInverse(modulus) } catch (_: ArithmeticException) { return CryptoMaybe.None }
        return if (r == zero) CryptoMaybe.None else CryptoMaybe.Some(r)
    }

    fun inverseCoprimes(g: BigInteger, m: BigInteger) = inverseCoprimesDeferred(lazyOf(g), lazyOf(m))
    fun inverseCoprimesDeferred(g: Lazy<BigInteger>, m: Lazy<BigInteger>): BigInteger = when (val value = inverseDeferred(g, m)) {
        CryptoMaybe.None -> throw CoprimesAssertionError()
        is CryptoMaybe.Some -> value.value
    }

    fun inverseFermat(g: BigInteger, p: BigInteger) = inverseFermatDeferred(lazyOf(g), lazyOf(p))
    fun inverseFermatDeferred(g: Lazy<BigInteger>, p: Lazy<BigInteger>) = expSafeDeferred(g, lazy { p.value - two }, p)

    fun inverseSafe(g: BigInteger, m: BigInteger) = inverseSafeDeferred(lazyOf(g), lazyOf(m))
    fun inverseSafeDeferred(g: Lazy<BigInteger>, m: Lazy<BigInteger>): CryptoMaybe<BigInteger> {
        if (m.value > one) {
            val r = inverseFermatDeferred(g, m)
            if ((g.value * r).mod(m.value) == one) return CryptoMaybe.Some(r)
        }
        return inverseDeferred(g, m)
    }

    fun jacobi(a: BigInteger, n: BigInteger) = jacobiDeferred(lazyOf(a), lazyOf(n))
    fun jacobiDeferred(input: Lazy<BigInteger>, modulus: Lazy<BigInteger>): CryptoMaybe<BigInteger> {
        var n = modulus.value
        if (n < three || !n.testBit(0)) return CryptoMaybe.None
        var a = input.value
        var sign = 1
        while (true) {
            if (a == zero || a == one) return CryptoMaybe.Some(a * BigInteger.valueOf(sign.toLong()))
            if (n <= a) { a = a.mod(n); continue }
            if (a.signum() < 0) {
                if (n.and(three) != one) sign = -sign
                a = -a
                continue
            }
            val exponent = a.lowestSetBit
            val odd = a.shiftRight(exponent)
            val nMod8 = n.and(BigInteger.valueOf(7)).toInt()
            var s = if (exponent % 2 == 0 || nMod8 == 1 || nMod8 == 7) 1 else -1
            if (n.and(three) == three && odd.and(three) == three) s = -s
            sign *= s
            if (odd == one) return CryptoMaybe.Some(BigInteger.valueOf(sign.toLong()))
            a = n.mod(odd)
            n = odd
        }
    }

    fun squareRoot(p: BigInteger, g: BigInteger) = squareRootDeferred(lazyOf(p), lazyOf(g))
    fun squareRootDeferred(modulus: Lazy<BigInteger>, input: Lazy<BigInteger>): CryptoMaybe<BigInteger> {
        val p = modulus.value
        if (p < two) throw ModulusAssertionError()
        val parts = p.divideAndRemainder(eight)
        val quotient = parts[0]
        fun validate(y: BigInteger): CryptoMaybe<BigInteger> = if ((y * y - input.value).mod(p) == zero) CryptoMaybe.Some(y) else CryptoMaybe.None
        return when (parts[1].toInt()) {
            3 -> validate(expFastDeferred(input, lazyOf(two * quotient + one), modulus))
            7 -> validate(expFastDeferred(input, lazyOf(two * quotient + two), modulus))
            5 -> {
                val gamma = expFast(two * input.value, quotient, p)
                val gGamma = input.value * gamma
                val i = (two * gGamma * gamma).mod(p)
                validate((gGamma * (i - one)).mod(p))
            }
            1 -> tonelliShanks(p, input)
            2 -> if (quotient == zero) CryptoMaybe.Some.deferred { if (input.value.testBit(0)) one else zero } else throw ModulusAssertionError()
            else -> throw ModulusAssertionError()
        }
    }

    private fun tonelliShanks(p: BigInteger, input: Lazy<BigInteger>): CryptoMaybe<BigInteger> {
        val a = input.value.mod(p)
        if (a == zero) return CryptoMaybe.Some(zero)
        val p1 = p - one
        val p2 = p1 / two
        val b = expFast(a, p2, p)
        if (b == p1) return CryptoMaybe.None
        if (b != one) throw ModulusAssertionError()
        return CryptoMaybe.Some.deferred {
            val e = p1.lowestSetBit
            val s = p1.shiftRight(e)
            var x = expFast(a, (s + one) / two, p)
            var current = expFast(a, s, p)
            var nonResidue = two
            while (expFast(nonResidue, p2, p) != p1) nonResidue += one
            var g = expFast(nonResidue, s, p)
            var r = e.toLong()
            while (current != one) {
                var power = current
                var rNext = 0L
                while (power != one) { power = (power * power).mod(p); rNext++ }
                var z = g
                var count = r - rNext - 1
                while (count != 0L) { z = (z * z).mod(p); count-- }
                x = (x * z).mod(p)
                g = (z * z).mod(p)
                current = (current * g).mod(p)
                r = rNext
            }
            x
        }
    }
}
