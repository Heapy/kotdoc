// GHC.Internal.Read/Text.Read.Lex and Float.RealFracMethods, GHC 9.12.2.
// (c) The University of Glasgow; BSD-3-Clause, licenses/GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.formats

import java.math.BigInteger

/** The whole-input Read behavior used by Pandoc's safeRead for numeric types. */
internal object HaskellNumber {
    fun isSpace(c: Char): Boolean = c in '\t'..'\r' || c == ' ' || Character.getType(c) == Character.SPACE_SEPARATOR.toInt()
    private val decimal = Regex("[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
    private val integer = Regex("[0-9]+")
    private val based = Regex("0(?:[xX][0-9a-fA-F]+|[oO][0-7]+|[bB][01]+)")

    private fun literal(text: String): Pair<Boolean, String> {
        var s = text.trim(::isSpace)
        while (s.startsWith('(') && s.endsWith(')')) s = s.substring(1,s.length-1).trim(::isSpace)
        val negative = s.startsWith('-')
        if (negative) s = s.drop(1).trimStart(::isSpace)
        return negative to s
    }
    private fun baseInteger(s: String): BigInteger = s.drop(2).toBigInteger(when (s[1].lowercaseChar()) { 'x' -> 16; 'o' -> 8; else -> 2 })
    fun readInteger(text: String): BigInteger? {
        val (negative,s) = literal(text)
        val n = when { based.matches(s) -> baseInteger(s); integer.matches(s) -> s.toBigInteger(); else -> return null }
        return if (negative) -n else n
    }
    fun readDouble(text: String): Double? {
        val (negative,s) = literal(text)
        val n = when {
            s == "NaN" -> Double.NaN
            s == "Infinity" -> Double.POSITIVE_INFINITY
            based.matches(s) -> baseInteger(s).toDouble()
            decimal.matches(s) -> s.toDouble()
            else -> return null
        }
        return if (negative) -n else n
    }

    /** Bit decomposition also preserves GHC's unusual non-finite Integer results. */
    fun floor(value: Double): BigInteger {
        if (value == 0.0) return BigInteger.ZERO
        val bits = value.toRawBits()
        val exponent = ((bits ushr 52) and 0x7ff).toInt()
        val fraction = bits and 0x000fffffffffffffL
        var mantissa = BigInteger.valueOf(if (exponent == 0) fraction else fraction or (1L shl 52))
        if (bits < 0) mantissa = -mantissa
        val power = if (exponent == 0) -1074 else exponent - 1075
        return if (power >= 0) mantissa.shiftLeft(power) else mantissa.shiftRight(-power)
    }
    fun round(value: Double): BigInteger = floor(Math.rint(value))
}
