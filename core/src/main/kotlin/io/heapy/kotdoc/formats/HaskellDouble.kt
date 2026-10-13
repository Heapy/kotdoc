// Decimal specialization of GHC 9.12.2 GHC.Internal.Float.floatToDigits/formatRealFloatAlt.
// (c) The University of Glasgow 1994-2002; portions from hbc (c) Lennart Augusstson.
// BSD-3-Clause; see licenses/GHC-base-BSD-3-Clause.txt.
// Source: https://github.com/ghc/ghc/blob/ghc-9.12.2-release/libraries/ghc-internal/src/GHC/Internal/Float.hs
package io.heapy.kotdoc.formats

import java.math.BigInteger
import kotlin.math.abs

/** GHC's strict rounding margins differ from Java's Double.toString at values such as 1e23. */
internal object HaskellDouble {
    private val one = BigInteger.ONE
    private val two = BigInteger.TWO
    private val ten = BigInteger.TEN

    fun show(value: Double): String {
        if (value.isNaN()) return "NaN"
        if (value == Double.POSITIVE_INFINITY) return "Infinity"
        if (value == Double.NEGATIVE_INFINITY) return "-Infinity"
        val sign = if (value.toRawBits() < 0) "-" else ""
        if (value == 0.0) return sign + "0.0"
        val (digits, exponent) = digits(abs(value))
        val rendered = when {
            exponent < 0 || exponent > 7 -> digits.first() + "." + digits.drop(1).ifEmpty { "0" } + "e" + (exponent - 1)
            exponent <= 0 -> "0." + "0".repeat(-exponent) + digits
            else -> digits.take(exponent).padEnd(exponent, '0') + "." + digits.drop(exponent).ifEmpty { "0" }
        }
        return sign + rendered
    }

    /** GHC FFFixed rounds its shortest decimal digits, not the exact binary value. */
    fun showFixed(value: Double, decimals: Int): String {
        if (!value.isFinite()) return show(value)
        val sign = if (value.toRawBits() < 0) "-" else ""
        val (digits, exponent) = if (value == 0.0) "0" to 0 else digits(abs(value))
        val decimal = java.math.BigDecimal(BigInteger(digits), digits.length - exponent)
        return sign + decimal.setScale(decimals.coerceAtLeast(0), java.math.RoundingMode.HALF_EVEN).toPlainString()
    }

    private fun digits(value: Double): Pair<String, Int> {
        val bits = value.toRawBits()
        val exponentBits = ((bits ushr 52) and 0x7ff).toInt()
        val fraction = bits and 0x000fffffffffffffL
        val f = BigInteger.valueOf(if (exponentBits == 0) fraction else fraction or (1L shl 52))
        val e = if (exponentBits == 0) -1074 else exponentBits - 1023 - 52
        // Haskell decodeFloat normalizes even subnormal significands to 53 bits.
        val e0 = if (exponentBits == 0) e - (53 - f.bitLength()) else e
        val boundary = f == one.shiftLeft(52)
        var r: BigInteger
        var s: BigInteger
        var upper: BigInteger
        var lower: BigInteger
        if (e >= 0) {
            val be = one.shiftLeft(e)
            if (boundary) { r = f * be * BigInteger.valueOf(4); s = BigInteger.valueOf(4); upper = be * two; lower = be }
            else { r = f * be * two; s = two; upper = be; lower = be }
        } else if (e > -1074 && boundary) {
            r = f * BigInteger.valueOf(4); s = one.shiftLeft(-e + 1) * two; upper = two; lower = one
        } else {
            r = f * two; s = one.shiftLeft(-e) * two; upper = one; lower = one
        }
        val lx = 52 + e0
        var exponent = lx * 8651 / 28738 + if (lx >= 0) 1 else 0
        while (if (exponent >= 0) r + upper > ten.pow(exponent) * s else ten.pow(-exponent) * (r + upper) > s) exponent++
        if (exponent >= 0) s *= ten.pow(exponent) else {
            val scale = ten.pow(-exponent)
            r *= scale; upper *= scale; lower *= scale
        }
        val digits = buildString {
            while (true) {
                val qr = (r * ten).divideAndRemainder(s)
                var digit = qr[0].toInt()
                r = qr[1]
                upper *= ten
                lower *= ten
                val low = r < lower
                val high = r + upper > s
                if (high && (!low || r * two >= s)) digit++
                check(digit in 0..9) { "GHC decimal conversion produced an invalid digit" }
                append(('0'.code + digit).toChar())
                if (low || high) break
            }
        }
        return digits to exponent
    }
}
