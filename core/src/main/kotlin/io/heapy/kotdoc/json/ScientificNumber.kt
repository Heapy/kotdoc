// Scientific 0.3.9.0: Bas van Dijk, BSD-3-Clause (licenses/Scientific-BSD-3-Clause.txt).
package io.heapy.kotdoc.json

import io.heapy.kotdoc.formats.HaskellDouble
import java.math.BigInteger

/** Stored Scientific fields, including the original scale seen by custom number formatters. */
data class ScientificNumber(val coefficient: BigInteger, val base10Exponent: Long) {
    constructor(coefficient: Long, base10Exponent: Long = 0) : this(BigInteger.valueOf(coefficient), base10Exponent)

    /** Absolute decimal digits and their decimal-point position; source Int arithmetic wraps. */
    fun decimalDigits(): Pair<String, Long> {
        if (coefficient.signum() == 0) return "0" to 0L
        val raw = coefficient.abs().toString()
        return raw.trimEnd('0') to (raw.length.toLong() + base10Exponent)
    }

    companion object {
        /** Decimal field transport, not a replacement for the full Scientific Read instance. */
        fun fromDecimal(text: String): ScientificNumber {
            val parts = text.lowercase().split('e')
            val mantissa = parts[0]
            val point = mantissa.indexOf('.')
            val decimals = if (point < 0) 0 else mantissa.length - point - 1
            return ScientificNumber(BigInteger(mantissa.replace(".", "")), (parts.getOrNull(1)?.toLong() ?: 0L) - decimals)
        }

        /** Original Scientific.fromFloatDigits, via the already ported GHC shortest-digit algorithm. */
        fun fromDouble(value: Double): ScientificNumber {
            require(value.isFinite())
            if (value == 0.0) return ScientificNumber(0)
            val parsed = fromDecimal(HaskellDouble.show(value))
            val raw = parsed.coefficient.abs().toString()
            val digits = raw.trimEnd('0')
            val coefficient = BigInteger(digits).let { if (value < 0) -it else it }
            return ScientificNumber(coefficient, parsed.base10Exponent + raw.length - digits.length)
        }
    }
}

enum class ScientificFormat { Generic, Exponent, Fixed }

/** Scientific's numeric builders, with virtual zero runs and demand-driven output. */
object ScientificFormatter {
    private data class Part(val text: String = "", val zeros: Long = 0) {
        val size: Long get() = if (zeros > 0) zeros else text.length.toLong()
    }
    private fun text(s: String) = listOf(Part(s))
    private fun zeros(n: Long) = if (n > 0) listOf(Part(zeros = n)) else emptyList()
    private fun nonempty(parts: List<Part>) = parts.filter { it.size > 0 }
    private fun mk0(parts: List<Part>) = nonempty(parts).ifEmpty { text("0") }
    private fun split(parts: List<Part>, count: Long): Pair<List<Part>, List<Part>> {
        var left = maxOf(count, 0)
        val before = mutableListOf<Part>(); val after = mutableListOf<Part>()
        for (p in parts) {
            val take = minOf(left, p.size)
            if (p.zeros > 0) {
                before += zeros(take); after += zeros(p.zeros - take)
            } else {
                if (take > 0) before.add(Part(p.text.take(take.toInt())))
                if (take < p.size) after.add(Part(p.text.drop(take.toInt())))
            }
            left -= take
        }
        return before to after
    }
    private fun init(parts: List<Part>): List<Part> {
        val result = nonempty(parts).toMutableList()
        if (result.isNotEmpty()) {
            val p = result.removeLast()
            if (p.zeros > 1) result.add(Part(zeros = p.zeros - 1))
            else if (p.zeros == 0L && p.text.length > 1) result.add(Part(p.text.dropLast(1)))
        }
        return result
    }

    fun format(value: ScientificNumber, mode: ScientificFormat = ScientificFormat.Generic, decimals: Long? = null): String =
        buildString { chunks(value, mode, decimals).forEach { append(it) } }

    fun chunks(value: ScientificNumber, mode: ScientificFormat = ScientificFormat.Generic, decimals: Long? = null): Sequence<String> = sequence {
        val parts = parts(value, mode, decimals)
        for (part in parts) {
            if (part.zeros > 0) {
                var left = part.zeros
                while (left > 0) { val n = minOf(left, 4096).toInt(); yield("0".repeat(n)); left -= n }
            } else if (part.text.isNotEmpty()) yield(part.text)
        }
    }

    private fun parts(value: ScientificNumber, mode: ScientificFormat, decimals: Long?): List<Part> {
        val (ds, e) = value.decimalDigits()
        val fmt = if (mode == ScientificFormat.Generic) {
            if (e < 0 || e > 7) ScientificFormat.Exponent else ScientificFormat.Fixed
        } else mode
        val sign = if (value.coefficient.signum() < 0) text("-") else emptyList()
        val digits = text(ds)
        val body = if (fmt == ScientificFormat.Exponent) {
            if (decimals == null) {
                if (ds == "0") text("0.0e0")
                else text(ds[0] + "." + ds.drop(1).ifEmpty { "0" } + "e" + (e - 1))
            } else {
                val dec = maxOf(decimals, 1)
                if (ds == "0") text("0.") + zeros(dec) + text("e0")
                else {
                    val (carry, rounded) = round(dec + 1, ds)
                    val result = if (carry > 0) init(rounded) else rounded
                    val (first, rest) = split(result, 1)
                    if (first.isEmpty()) emptyList() else first + text(".") + rest + text("e" + (e - 1 + carry))
                }
            }
        } else if (decimals == null) {
            if (e <= 0) text("0.") + zeros(-e) + digits
            else {
                val (first, rest) = split(digits, e)
                first + zeros(e - ds.length) + text(".") + mk0(rest)
            }
        } else {
            val dec = maxOf(decimals, 0)
            if (e >= 0) {
                val (carry, rounded) = round(dec + e, ds)
                val (first, rest) = split(rounded, e + carry)
                mk0(first) + if (rest.isEmpty()) emptyList() else text(".") + rest
            } else {
                val (carry, rounded) = round(dec, ds, maxOf(-e, 0))
                val result = if (carry > 0) rounded else text("0") + rounded
                val (first, rest) = split(result, 1)
                first + if (rest.isEmpty()) emptyList() else text(".") + rest
            }
        }
        return sign + body
    }

    // Utils.roundTo; virtual zero runs preserve output without allocating unused huge prefixes.
    private fun round(n: Long, digits: String, leading: Long = 0): Pair<Long, List<Part>> {
        if (n < 0 || (n >= leading && n - leading >= digits.length)) {
            return 0L to (zeros(leading) + text(digits) + zeros(n - leading - digits.length))
        }
        val leadingKept = minOf(n, leading)
        val suffix = digits.take(maxOf(n - leading, 0).toInt()).toCharArray()
        val next = if (n < leading) '0' else digits[(n - leading).toInt()]
        val evenPrevious = suffix.isEmpty() || (suffix.last() - '0') % 2 == 0
        val restZero = digits.drop(maxOf(n + 1 - leading, 0).toInt()).all { it == '0' }
        var carry = next >= '5' && !(next == '5' && evenPrevious && restZero)
        var i = suffix.lastIndex
        while (carry && i >= 0) {
            if (suffix[i] == '9') suffix[i] = '0' else { suffix[i]++; carry = false }
            i--
        }
        if (carry) {
            return if (leadingKept > 0) 0L to (zeros(leadingKept - 1) + text("1") + text(suffix.concatToString()))
            else 1L to (text("1") + text(suffix.concatToString()))
        }
        return 0L to (zeros(leadingKept) + text(suffix.concatToString()))
    }

    internal fun aesonTextChunks(value: ScientificNumber): Sequence<String> =
        if (value.base10Exponent < 0) chunks(value) else chunks(value, ScientificFormat.Fixed, 0)

    internal fun prettyGenericChunks(value: ScientificNumber): Sequence<String> {
        if (value.coefficient.signum() == 0) return aesonTextChunks(value)
        // Scientific.cmpScientific's wrapped logarithm; compare against +/- 1e19.
        val raw = value.coefficient.abs().toString()
        val log = raw.length.toLong() - 1 + value.base10Exponent
        val greater = log > 19 || (log == 19L && (raw[0] > '1' || raw.drop(1).any { it != '0' }))
        return if (greater) chunks(value, ScientificFormat.Exponent) else aesonTextChunks(value)
    }
}
