// Copyright 2013 The Android Open Source Project; crypton 2.0.1 p256_gf.h.
// Complete upstream notice: licenses/crypton-p256-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

/** The two original redundant Montgomery field representations, with R = 2^257. */
internal class CryptoP256Field(val bits: Int) {
    init { require(bits == 32 || bits == 64) }
    val limbs = if (bits == 32) 9 else 5
    private val even = if (bits == 32) 29 else 51
    private val odd = if (bits == 32) 28 else 52
    private val digitMask = if (bits == 32) 0xffffffffL else -1L
    private val words = CryptoP256Words(bits)
    typealias Region = CryptoP256Words.Region
    class Audit {
        val operations = LongArray(13)
        val trace = mutableListOf<Pair<String, LongArray>>()
        val words = CryptoP256Words.Audit()
        fun enter(index: Int) { operations[index]++ }
        fun snapshot(name: String, data: LongArray) { trace += name to data.copyOf() }
    }
    private fun width(i: Int) = if (i and 1 == 0) even else odd
    private fun mask(i: Int) = (1L shl width(i)) - 1
    private fun put(out: Region, i: Int, value: Long) { out[i] = value and digitMask }
    private fun ones(x: Long) = (((x - 1) and digitMask) ushr (bits - 1)) - 1 and digitMask
    private fun snapshot(audit: Audit?, label: String, data: Region, size: Int) {
        audit?.snapshot(label, LongArray(size) { data[it] })
    }
    fun constant(name: String): LongArray = CryptoP256FieldTables.get(bits, name).copyOf()
    fun reduceCarry(out: Region, carry: Long, audit: Audit? = null) {
        audit?.enter(0)
        val take = ones(carry)
        put(out, 0, out[0] + (carry shl 1))
        if (bits == 32) {
            put(out, 3, out[3] + (0x10000000L and take) - (carry shl 11))
            put(out, 4, out[4] + (0x1fffffffL and take))
            put(out, 5, out[5] + (0xfffffffL and take))
            put(out, 6, out[6] + (0x1fffffffL and take) - (carry shl 22))
            put(out, 7, out[7] - (1L and take) + (carry shl 25))
        } else {
            put(out, 1, out[1] + (0x10000000000000L and take) - (carry shl 46))
            put(out, 2, out[2] + (0x7ffffffffffffL and take))
            put(out, 3, out[3] + (0xfffffffffffffL and take) - (carry shl 39))
            put(out, 4, out[4] - (1L and take) + (carry shl 19))
        }
        snapshot(audit, "carry", out, limbs)
    }
    fun sum(out: Region, a: Region, b: Region, audit: Audit? = null) {
        audit?.enter(1); var carry = 0L
        for (i in 0..<limbs) {
            val value = (a[i] + b[i] + carry) and digitMask
            carry = value ushr width(i); put(out, i, value and mask(i))
        }
        reduceCarry(out, carry, audit)
    }
    fun difference(out: Region, a: Region, b: Region, audit: Audit? = null) {
        audit?.enter(2); var carry = 0L
        val zero = CryptoP256FieldTables.get(bits, "zeroOffset")
        for (i in 0..<limbs) {
            val value = (a[i] - b[i] + zero[i] + carry) and digitMask
            carry = value ushr width(i); put(out, i, value and mask(i))
        }
        reduceCarry(out, carry, audit)
    }
    fun assign(out: Region, a: Region, audit: Audit? = null) {
        audit?.enter(6); out.copyFrom(a, limbs)
    }
    fun scalar(out: Region, factor: Int, audit: Audit? = null) {
        require(factor == 3 || factor == 4 || factor == 8)
        audit?.enter(if (factor == 3) 7 else if (factor == 4) 8 else 9)
        var carry = 0L
        for (i in 0..<limbs) {
            if (factor == 3) {
                val value = (out[i] * 3 + carry) and digitMask
                carry = value ushr width(i); put(out, i, value and mask(i))
            } else {
                val shift = if (factor == 4) 2 else 3
                val next = out[i] ushr (width(i) - shift)
                val value = (((out[i] shl shift) and mask(i)) + carry) and digitMask
                carry = next + (value ushr width(i)); put(out, i, value and mask(i))
            }
        }
        reduceCarry(out, carry, audit)
    }
    fun isZero(a: Region, audit: Audit? = null): Boolean {
        audit?.enter(10)
        val tmp = Region(LongArray(limbs)); assign(tmp, a, audit)
        do {
            var carry = 0L
            for (i in 0..<limbs) {
                val value = (tmp[i] + carry) and digitMask
                carry = value ushr width(i); tmp[i] = value and mask(i)
            }
            reduceCarry(tmp, carry, audit)
            snapshot(audit, "zero-pass", tmp, limbs)
        } while (carry != 0L)
        return listOf("zero", "p", "2p").any { name ->
            val value = CryptoP256FieldTables.get(bits, name)
            (0..<limbs).all { tmp[it] == value[it] }
        }
    }
    fun toMontgomery(out: Region, a: Region, audit: Audit? = null) {
        audit?.enter(11)
        val tmp = Region(LongArray(words.digits))
        words.init(tmp, audit?.words)
        words.modMul(words.p, a, 0, Region(constant("r")), tmp, audit?.words)
        for (i in 0..<limbs) {
            out[i] = tmp[0] and mask(i)
            words.shiftRight(tmp, width(i), tmp, audit?.words)
        }
        words.clear(tmp, audit?.words)
        snapshot(audit, "to-cleared", tmp, words.digits)
    }
    fun fromMontgomery(out: Region, a: Region, audit: Audit? = null) {
        audit?.enter(12)
        val result = Region(LongArray(words.digits)); val tmp = Region(LongArray(words.digits))
        words.init(result, audit?.words); words.init(tmp, audit?.words)
        words.addDigit(tmp, a[limbs - 1], result, audit?.words)
        var top = 0
        for (i in limbs - 2 downTo 0) {
            top = words.shiftLeft(result, width(i), tmp, audit?.words).toInt()
            val carry = words.addDigit(tmp, a[i], result, audit?.words)
            top = if (bits == 32) top or carry else top + carry
        }
        words.modMul(words.p, Region(constant("rInv")), top.toLong() and digitMask, result, out, audit?.words)
        words.clear(result, audit?.words); words.clear(tmp, audit?.words)
        snapshot(audit, "from-cleared-result", result, words.digits)
        snapshot(audit, "from-cleared-tmp", tmp, words.digits)
    }

    /** Exact unsigned 128-bit operations used by the original 64-bit profile. */
    internal data class Wide(val low: Long, val high: Long) {
        operator fun plus(other: Wide): Wide {
            val sum = low + other.low
            val carry = ((low and other.low) or ((low or other.low) and sum.inv())) ushr 63
            return Wide(sum, high + other.high + carry)
        }
        infix fun ushr(n: Int): Wide = when {
            n == 0 -> this
            n < 64 -> Wide((low ushr n) or (high shl (64 - n)), high ushr n)
            else -> Wide(high ushr (n - 64), 0)
        }
        infix fun shl(n: Int): Wide = when {
            n == 0 -> this
            n < 64 -> Wide(low shl n, (high shl n) or (low ushr (64 - n)))
            else -> Wide(0, low shl (n - 64))
        }
        infix fun and(other: Wide) = Wide(low and other.low, high and other.high)
        companion object {
            fun of(value: Long) = Wide(value, 0)
            fun product(a: Long, b: Long) = Wide(a * b, Math.unsignedMultiplyHigh(a, b))
        }
    }
    fun square(out: Region, a: Region, audit: Audit? = null) {
        audit?.enter(4)
        if (bits == 32) reduceDegree(out, products32(a, a, true), null, audit)
        else {
            val tmp = products64(a, a, true)
            reduceDegree(out, tmp.map { it.low }.toLongArray(), tmp.map { it.high }.toLongArray(), audit)
        }
    }
    fun multiply(out: Region, a: Region, b: Region, audit: Audit? = null) {
        audit?.enter(5)
        if (bits == 32) reduceDegree(out, products32(a, b, false), null, audit)
        else {
            val tmp = products64(a, b, false)
            reduceDegree(out, tmp.map { it.low }.toLongArray(), tmp.map { it.high }.toLongArray(), audit)
        }
    }
    // BEGIN PINNED EXPRESSIONS
    private fun products32(a: Region, b: Region, square: Boolean): LongArray {
        val tmp = LongArray(17)
        if (square) {
            tmp[0] = (a[0] * a[0])
            tmp[1] = (a[0] * ((a[1] shl 1) and 0xffffffffL))
            tmp[2] = ((a[0] * ((a[2] shl 1) and 0xffffffffL)) + (a[1] * ((a[1] shl 1) and 0xffffffffL)))
            tmp[3] = ((a[0] * ((a[3] shl 1) and 0xffffffffL)) + (a[1] * ((a[2] shl 1) and 0xffffffffL)))
            tmp[4] = (((a[0] * ((a[4] shl 1) and 0xffffffffL)) + (a[1] * ((a[3] shl 2) and 0xffffffffL))) + (a[2] * a[2]))
            tmp[5] = (((a[0] * ((a[5] shl 1) and 0xffffffffL)) + (a[1] * ((a[4] shl 1) and 0xffffffffL))) + (a[2] * ((a[3] shl 1) and 0xffffffffL)))
            tmp[6] = ((((a[0] * ((a[6] shl 1) and 0xffffffffL)) + (a[1] * ((a[5] shl 2) and 0xffffffffL))) + (a[2] * ((a[4] shl 1) and 0xffffffffL))) + (a[3] * ((a[3] shl 1) and 0xffffffffL)))
            tmp[7] = ((((a[0] * ((a[7] shl 1) and 0xffffffffL)) + (a[1] * ((a[6] shl 1) and 0xffffffffL))) + (a[2] * ((a[5] shl 1) and 0xffffffffL))) + (a[3] * ((a[4] shl 1) and 0xffffffffL)))
            tmp[8] = (((((a[0] * ((a[8] shl 1) and 0xffffffffL)) + (a[1] * ((a[7] shl 2) and 0xffffffffL))) + (a[2] * ((a[6] shl 1) and 0xffffffffL))) + (a[3] * ((a[5] shl 2) and 0xffffffffL))) + (a[4] * a[4]))
            tmp[9] = ((((a[1] * ((a[8] shl 1) and 0xffffffffL)) + (a[2] * ((a[7] shl 1) and 0xffffffffL))) + (a[3] * ((a[6] shl 1) and 0xffffffffL))) + (a[4] * ((a[5] shl 1) and 0xffffffffL)))
            tmp[10] = ((((a[2] * ((a[8] shl 1) and 0xffffffffL)) + (a[3] * ((a[7] shl 2) and 0xffffffffL))) + (a[4] * ((a[6] shl 1) and 0xffffffffL))) + (a[5] * ((a[5] shl 1) and 0xffffffffL)))
            tmp[11] = (((a[3] * ((a[8] shl 1) and 0xffffffffL)) + (a[4] * ((a[7] shl 1) and 0xffffffffL))) + (a[5] * ((a[6] shl 1) and 0xffffffffL)))
            tmp[12] = (((a[4] * ((a[8] shl 1) and 0xffffffffL)) + (a[5] * ((a[7] shl 2) and 0xffffffffL))) + (a[6] * a[6]))
            tmp[13] = ((a[5] * ((a[8] shl 1) and 0xffffffffL)) + (a[6] * ((a[7] shl 1) and 0xffffffffL)))
            tmp[14] = ((a[6] * ((a[8] shl 1) and 0xffffffffL)) + (a[7] * ((a[7] shl 1) and 0xffffffffL)))
            tmp[15] = (a[7] * ((a[8] shl 1) and 0xffffffffL))
            tmp[16] = (a[8] * a[8])
        } else {
            tmp[0] = (a[0] * b[0])
            tmp[1] = ((a[0] * ((b[1] shl 0) and 0xffffffffL)) + (a[1] * ((b[0] shl 0) and 0xffffffffL)))
            tmp[2] = (((a[0] * ((b[2] shl 0) and 0xffffffffL)) + (a[1] * ((b[1] shl 1) and 0xffffffffL))) + (a[2] * ((b[0] shl 0) and 0xffffffffL)))
            tmp[3] = ((((a[0] * ((b[3] shl 0) and 0xffffffffL)) + (a[1] * ((b[2] shl 0) and 0xffffffffL))) + (a[2] * ((b[1] shl 0) and 0xffffffffL))) + (a[3] * ((b[0] shl 0) and 0xffffffffL)))
            tmp[4] = (((((a[0] * ((b[4] shl 0) and 0xffffffffL)) + (a[1] * ((b[3] shl 1) and 0xffffffffL))) + (a[2] * ((b[2] shl 0) and 0xffffffffL))) + (a[3] * ((b[1] shl 1) and 0xffffffffL))) + (a[4] * ((b[0] shl 0) and 0xffffffffL)))
            tmp[5] = ((((((a[0] * ((b[5] shl 0) and 0xffffffffL)) + (a[1] * ((b[4] shl 0) and 0xffffffffL))) + (a[2] * ((b[3] shl 0) and 0xffffffffL))) + (a[3] * ((b[2] shl 0) and 0xffffffffL))) + (a[4] * ((b[1] shl 0) and 0xffffffffL))) + (a[5] * ((b[0] shl 0) and 0xffffffffL)))
            tmp[6] = (((((((a[0] * ((b[6] shl 0) and 0xffffffffL)) + (a[1] * ((b[5] shl 1) and 0xffffffffL))) + (a[2] * ((b[4] shl 0) and 0xffffffffL))) + (a[3] * ((b[3] shl 1) and 0xffffffffL))) + (a[4] * ((b[2] shl 0) and 0xffffffffL))) + (a[5] * ((b[1] shl 1) and 0xffffffffL))) + (a[6] * ((b[0] shl 0) and 0xffffffffL)))
            tmp[7] = ((((((((a[0] * ((b[7] shl 0) and 0xffffffffL)) + (a[1] * ((b[6] shl 0) and 0xffffffffL))) + (a[2] * ((b[5] shl 0) and 0xffffffffL))) + (a[3] * ((b[4] shl 0) and 0xffffffffL))) + (a[4] * ((b[3] shl 0) and 0xffffffffL))) + (a[5] * ((b[2] shl 0) and 0xffffffffL))) + (a[6] * ((b[1] shl 0) and 0xffffffffL))) + (a[7] * ((b[0] shl 0) and 0xffffffffL)))
            tmp[8] = (((((((((a[0] * ((b[8] shl 0) and 0xffffffffL)) + (a[1] * ((b[7] shl 1) and 0xffffffffL))) + (a[2] * ((b[6] shl 0) and 0xffffffffL))) + (a[3] * ((b[5] shl 1) and 0xffffffffL))) + (a[4] * ((b[4] shl 0) and 0xffffffffL))) + (a[5] * ((b[3] shl 1) and 0xffffffffL))) + (a[6] * ((b[2] shl 0) and 0xffffffffL))) + (a[7] * ((b[1] shl 1) and 0xffffffffL))) + (a[8] * ((b[0] shl 0) and 0xffffffffL)))
            tmp[9] = ((((((((a[1] * ((b[8] shl 0) and 0xffffffffL)) + (a[2] * ((b[7] shl 0) and 0xffffffffL))) + (a[3] * ((b[6] shl 0) and 0xffffffffL))) + (a[4] * ((b[5] shl 0) and 0xffffffffL))) + (a[5] * ((b[4] shl 0) and 0xffffffffL))) + (a[6] * ((b[3] shl 0) and 0xffffffffL))) + (a[7] * ((b[2] shl 0) and 0xffffffffL))) + (a[8] * ((b[1] shl 0) and 0xffffffffL)))
            tmp[10] = (((((((a[2] * ((b[8] shl 0) and 0xffffffffL)) + (a[3] * ((b[7] shl 1) and 0xffffffffL))) + (a[4] * ((b[6] shl 0) and 0xffffffffL))) + (a[5] * ((b[5] shl 1) and 0xffffffffL))) + (a[6] * ((b[4] shl 0) and 0xffffffffL))) + (a[7] * ((b[3] shl 1) and 0xffffffffL))) + (a[8] * ((b[2] shl 0) and 0xffffffffL)))
            tmp[11] = ((((((a[3] * ((b[8] shl 0) and 0xffffffffL)) + (a[4] * ((b[7] shl 0) and 0xffffffffL))) + (a[5] * ((b[6] shl 0) and 0xffffffffL))) + (a[6] * ((b[5] shl 0) and 0xffffffffL))) + (a[7] * ((b[4] shl 0) and 0xffffffffL))) + (a[8] * ((b[3] shl 0) and 0xffffffffL)))
            tmp[12] = (((((a[4] * ((b[8] shl 0) and 0xffffffffL)) + (a[5] * ((b[7] shl 1) and 0xffffffffL))) + (a[6] * ((b[6] shl 0) and 0xffffffffL))) + (a[7] * ((b[5] shl 1) and 0xffffffffL))) + (a[8] * ((b[4] shl 0) and 0xffffffffL)))
            tmp[13] = ((((a[5] * ((b[8] shl 0) and 0xffffffffL)) + (a[6] * ((b[7] shl 0) and 0xffffffffL))) + (a[7] * ((b[6] shl 0) and 0xffffffffL))) + (a[8] * ((b[5] shl 0) and 0xffffffffL)))
            tmp[14] = (((a[6] * ((b[8] shl 0) and 0xffffffffL)) + (a[7] * ((b[7] shl 1) and 0xffffffffL))) + (a[8] * ((b[6] shl 0) and 0xffffffffL)))
            tmp[15] = ((a[7] * ((b[8] shl 0) and 0xffffffffL)) + (a[8] * ((b[7] shl 0) and 0xffffffffL)))
            tmp[16] = (a[8] * ((b[8] shl 0) and 0xffffffffL))
        }
        return tmp
    }
    private fun products64(a: Region, b: Region, square: Boolean): Array<Wide> {
        val tmp = Array(9) { Wide.of(0) }
        if (square) {
            val x1x1 = Wide.product(a[1], a[1])
            val x3x3 = Wide.product(a[3], a[3])
            tmp[0] = Wide.product(a[0], (a[0] shl 0))
            tmp[1] = (Wide.product(a[0], (a[1] shl 1)) + ((x1x1 and Wide.of(1L)) shl 51))
            tmp[2] = (Wide.product(a[0], (a[2] shl 1)) + (x1x1 ushr 1))
            tmp[3] = (Wide.product(a[0], (a[3] shl 1)) + Wide.product(a[1], (a[2] shl 1)))
            tmp[4] = ((Wide.product(a[0], (a[4] shl 1)) + Wide.product(a[1], (a[3] shl 0))) + Wide.product(a[2], (a[2] shl 0)))
            tmp[5] = ((Wide.product(a[1], (a[4] shl 1)) + Wide.product(a[2], (a[3] shl 1))) + ((x3x3 and Wide.of(1L)) shl 51))
            tmp[6] = (Wide.product(a[2], (a[4] shl 1)) + (x3x3 ushr 1))
            tmp[7] = Wide.product(a[3], (a[4] shl 1))
            tmp[8] = Wide.product(a[4], (a[4] shl 0))
        } else {
            val x1y1 = Wide.product(a[1], b[1])
            val x1y3 = Wide.product(a[1], b[3])
            val x3y1 = Wide.product(a[3], b[1])
            val x3y3 = Wide.product(a[3], b[3])
            tmp[0] = Wide.product(a[0], b[0])
            tmp[1] = ((Wide.product(a[0], b[1]) + Wide.product(a[1], b[0])) + ((x1y1 and Wide.of(1L)) shl 51))
            tmp[2] = ((Wide.product(a[0], b[2]) + (x1y1 ushr 1)) + Wide.product(a[2], b[0]))
            tmp[3] = (((((Wide.product(a[0], b[3]) + Wide.product(a[1], b[2])) + Wide.product(a[2], b[1])) + ((x1y3 and Wide.of(1L)) shl 51)) + Wide.product(a[3], b[0])) + ((x3y1 and Wide.of(1L)) shl 51))
            tmp[4] = ((((Wide.product(a[0], b[4]) + (x1y3 ushr 1)) + Wide.product(a[2], b[2])) + (x3y1 ushr 1)) + Wide.product(a[4], b[0]))
            tmp[5] = ((((Wide.product(a[1], b[4]) + Wide.product(a[2], b[3])) + Wide.product(a[3], b[2])) + Wide.product(a[4], b[1])) + ((x3y3 and Wide.of(1L)) shl 51))
            tmp[6] = ((Wide.product(a[2], b[4]) + (x3y3 ushr 1)) + Wide.product(a[4], b[2]))
            tmp[7] = (Wide.product(a[3], b[4]) + Wide.product(a[4], b[3]))
            tmp[8] = Wide.product(a[4], b[4])
        }
        return tmp
    }
    fun reduceDegree(out: Region, low: LongArray, high: LongArray? = null, audit: Audit? = null) {
        audit?.enter(3)
        audit?.snapshot("product-low", low)
        if (bits == 32) reduceDegree32(out, low, audit) else {
            require(high != null)
            audit?.snapshot("product-high", high)
            reduceDegree64(out, Array(9) { Wide(low[it], high[it]) }, audit)
        }
    }
    private fun reduceDegree32(out: Region, tmp: LongArray, audit: Audit?) {
        val tmp2 = LongArray(18)
        var carry: Long
        var x: Long
        var xMask: Long
        var i: Int
        tmp2[0] = (((tmp[0] and 0x1fffffffL) and 0xffffffffL) and 0xffffffffL)
        tmp2[1] = ((((tmp[0] and 0xffffffffL) ushr 29) and 0xffffffffL) and 0xffffffffL)
        tmp2[1] = (((tmp2[1] or ((((((tmp[0] ushr 32) and 0xffffffffL) shl 3) and 0xffffffffL) and 0xfffffffL) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
        tmp2[1] = (((tmp2[1] + (((tmp[1] and 0xffffffffL) and 0xfffffffL) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
        carry = (((tmp2[1] ushr 28) and 0xffffffffL) and 0xffffffffL)
        tmp2[1] = (((tmp2[1] and 0xfffffffL) and 0xffffffffL) and 0xffffffffL)
        i = 2
        while (i < 17) {
            tmp2[i] = (((((tmp[i-2] ushr 32) and 0xffffffffL) ushr 25) and 0xffffffffL) and 0xffffffffL)
            tmp2[i] = (((tmp2[i] + (((tmp[i-1] and 0xffffffffL) ushr 28) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i] = (((tmp2[i] + ((((((tmp[i-1] ushr 32) and 0xffffffffL) shl 4) and 0xffffffffL) and 0x1fffffffL) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i] = (((tmp2[i] + (((tmp[i] and 0xffffffffL) and 0x1fffffffL) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i] = (((tmp2[i] + carry) and 0xffffffffL) and 0xffffffffL)
            carry = (((tmp2[i] ushr 29) and 0xffffffffL) and 0xffffffffL)
            tmp2[i] = (((tmp2[i] and 0x1fffffffL) and 0xffffffffL) and 0xffffffffL)
            i++
            if (i == 17) {
                break
            }
            tmp2[i] = (((((tmp[i-2] ushr 32) and 0xffffffffL) ushr 25) and 0xffffffffL) and 0xffffffffL)
            tmp2[i] = (((tmp2[i] + (((tmp[i-1] and 0xffffffffL) ushr 29) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i] = (((tmp2[i] + ((((((tmp[i-1] ushr 32) and 0xffffffffL) shl 3) and 0xffffffffL) and 0xfffffffL) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i] = (((tmp2[i] + (((tmp[i] and 0xffffffffL) and 0xfffffffL) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i] = (((tmp2[i] + carry) and 0xffffffffL) and 0xffffffffL)
            carry = (((tmp2[i] ushr 28) and 0xffffffffL) and 0xffffffffL)
            tmp2[i] = (((tmp2[i] and 0xfffffffL) and 0xffffffffL) and 0xffffffffL)
            i++
        }
        tmp2[17] = (((((tmp[15] ushr 32) and 0xffffffffL) ushr 25) and 0xffffffffL) and 0xffffffffL)
        tmp2[17] = (((tmp2[17] + (((tmp[16] and 0xffffffffL) ushr 29) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
        tmp2[17] = (((tmp2[17] + ((((tmp[16] ushr 32) and 0xffffffffL) shl 3) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
        tmp2[17] = (((tmp2[17] + carry) and 0xffffffffL) and 0xffffffffL)
        audit?.snapshot("normalized", tmp2)
        i = 0
        while (true) {
            tmp2[i + 1] = (((tmp2[i+1] + ((tmp2[i] ushr 29) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            x = (((tmp2[i] and 0x1fffffffL) and 0xffffffffL) and 0xffffffffL)
            xMask = (ones(x) and 0xffffffffL)
            tmp2[i] = (0L and 0xffffffffL)
            tmp2[i + 3] = (((tmp2[i+3] + ((((x shl 10) and 0xffffffffL) and 0xfffffffL) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 4] = (((tmp2[i+4] + ((x ushr 18) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 6] = (((tmp2[i+6] + ((((x shl 21) and 0xffffffffL) and 0x1fffffffL) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 7] = (((tmp2[i+7] + ((x ushr 8) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 7] = (((tmp2[i+7] + ((268435456L and xMask) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 8] = (((tmp2[i+8] + ((((x - 1L) and 0xffffffffL) and xMask) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 7] = (((tmp2[i+7] - ((((x shl 24) and 0xffffffffL) and 0xfffffffL) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 8] = (((tmp2[i+8] - ((x ushr 4) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 8] = (((tmp2[i+8] + ((536870912L and xMask) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 8] = (((tmp2[i+8] - x) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 8] = (((tmp2[i+8] + ((((x shl 28) and 0xffffffffL) and 0x1fffffffL) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 9] = (((tmp2[i+9] + ((((((x ushr 1) and 0xffffffffL) - 1L) and 0xffffffffL) and xMask) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            audit?.snapshot("eliminate-even", tmp2)
            if (i+1 == limbs) {
                break
            }
            tmp2[i + 2] = (((tmp2[i+2] + ((tmp2[i+1] ushr 28) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            x = (((tmp2[i+1] and 0xfffffffL) and 0xffffffffL) and 0xffffffffL)
            xMask = (ones(x) and 0xffffffffL)
            tmp2[i + 1] = (0L and 0xffffffffL)
            tmp2[i + 4] = (((tmp2[i+4] + ((((x shl 11) and 0xffffffffL) and 0x1fffffffL) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 5] = (((tmp2[i+5] + ((x ushr 18) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 7] = (((tmp2[i+7] + ((((x shl 21) and 0xffffffffL) and 0xfffffffL) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 8] = (((tmp2[i+8] + ((x ushr 7) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 8] = (((tmp2[i+8] + ((536870912L and xMask) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 9] = (((tmp2[i+9] + ((((x - 1L) and 0xffffffffL) and xMask) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 8] = (((tmp2[i+8] - ((((x shl 25) and 0xffffffffL) and 0x1fffffffL) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 9] = (((tmp2[i+9] - ((x ushr 4) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 9] = (((tmp2[i+9] + ((268435456L and xMask) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 9] = (((tmp2[i+9] - x) and 0xffffffffL) and 0xffffffffL)
            tmp2[i + 10] = (((tmp2[i+10] + ((((x - 1L) and 0xffffffffL) and xMask) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            audit?.snapshot("eliminate-odd", tmp2)
            i += 2
        }
        carry = (0L and 0xffffffffL)
        i = 0
        while (i < 8) {
            out[i] = (tmp2[i+9] and 0xffffffffL)
            out[i] = (((out[i] + carry) and 0xffffffffL) and 0xffffffffL)
            out[i] = (((out[i] + ((((tmp2[i+10] shl 28) and 0xffffffffL) and 0x1fffffffL) and 0xffffffffL)) and 0xffffffffL) and 0xffffffffL)
            carry = (((out[i] ushr 29) and 0xffffffffL) and 0xffffffffL)
            out[i] = (((out[i] and 0x1fffffffL) and 0xffffffffL) and 0xffffffffL)
            i++
            out[i] = (((tmp2[i+9] ushr 1) and 0xffffffffL) and 0xffffffffL)
            out[i] = (((out[i] + carry) and 0xffffffffL) and 0xffffffffL)
            carry = (((out[i] ushr 28) and 0xffffffffL) and 0xffffffffL)
            out[i] = (((out[i] and 0xfffffffL) and 0xffffffffL) and 0xffffffffL)
            i++
        }
        out[8] = (tmp2[17] and 0xffffffffL)
        out[8] = (((out[8] + carry) and 0xffffffffL) and 0xffffffffL)
        carry = (((out[8] ushr 29) and 0xffffffffL) and 0xffffffffL)
        out[8] = (((out[8] and 0x1fffffffL) and 0xffffffffL) and 0xffffffffL)
        reduceCarry(out, carry, audit)
    }
    private fun reduceDegree64(out: Region, tmp: Array<Wide>, audit: Audit?) {
        val tmp2 = LongArray(10)
        var carry: Long
        var x: Long
        var xShiftedMask: Long
        var i: Int
        tmp2[0] = ((tmp[0] and Wide.of(0x7ffffffffffffL))).low
        tmp2[1] = ((tmp[0]).low ushr 51)
        tmp2[1] = (tmp2[1] or ((((tmp[0] ushr 64)).low shl 13) and 0xfffffffffffffL))
        tmp2[1] = (tmp2[1] + ((tmp[1]).low and 0xfffffffffffffL))
        carry = (tmp2[1] ushr 52)
        tmp2[1] = (tmp2[1] and 0xfffffffffffffL)
        i = 2
        while (i < 9) {
            tmp2[i] = (((tmp[i-2] ushr 64)).low ushr 39)
            tmp2[i] = (tmp2[i] + ((tmp[i-1]).low ushr 52))
            tmp2[i] = (tmp2[i] + ((((tmp[i-1] ushr 64)).low shl 12) and 0x7ffffffffffffL))
            tmp2[i] = (tmp2[i] + ((tmp[i]).low and 0x7ffffffffffffL))
            tmp2[i] = (tmp2[i] + carry)
            carry = (tmp2[i] ushr 51)
            tmp2[i] = (tmp2[i] and 0x7ffffffffffffL)
            i++
            if (i == 9) {
                break
            }
            tmp2[i] = (((tmp[i-2] ushr 64)).low ushr 39)
            tmp2[i] = (tmp2[i] + ((tmp[i-1]).low ushr 51))
            tmp2[i] = (tmp2[i] + ((((tmp[i-1] ushr 64)).low shl 13) and 0xfffffffffffffL))
            tmp2[i] = (tmp2[i] + ((tmp[i]).low and 0xfffffffffffffL))
            tmp2[i] = (tmp2[i] + carry)
            carry = (tmp2[i] ushr 52)
            tmp2[i] = (tmp2[i] and 0xfffffffffffffL)
            i++
        }
        tmp2[9] = (((tmp[7] ushr 64)).low ushr 39)
        tmp2[9] = (tmp2[9] + ((tmp[8]).low ushr 51))
        tmp2[9] = (tmp2[9] + (((tmp[8] ushr 64)).low shl 13))
        tmp2[9] = (tmp2[9] + carry)
        audit?.snapshot("normalized", tmp2)
        i = 0
        while (true) {
            tmp2[i + 1] = (tmp2[i+1] + (tmp2[i] ushr 51))
            x = (tmp2[i] and 0x7ffffffffffffL)
            xShiftedMask = ones((x ushr 1))
            tmp2[i] = 0L
            tmp2[i + 1] = (tmp2[i+1] + ((x shl 45) and 0xfffffffffffffL))
            tmp2[i + 2] = (tmp2[i+2] + (x ushr 7))
            tmp2[i + 3] = (tmp2[i+3] + ((x shl 38) and 0xfffffffffffffL))
            tmp2[i + 4] = (tmp2[i+4] + (x ushr 14))
            tmp2[i + 4] = (tmp2[i+4] + (2251799813685248L and xShiftedMask))
            tmp2[i + 5] = (tmp2[i+5] - (1L and xShiftedMask))
            tmp2[i + 4] = (tmp2[i+4] - ((x shl 18) and 0x7ffffffffffffL))
            tmp2[i + 4] = (tmp2[i+4] + ((x shl 50) and 0x7ffffffffffffL))
            tmp2[i + 5] = (tmp2[i+5] + ((x ushr 1) - (x ushr 33)))
            audit?.snapshot("eliminate-even", tmp2)
            if (i+1 == limbs) {
                break
            }
            tmp2[i + 2] = (tmp2[i+2] + (tmp2[i+1] ushr 52))
            x = (tmp2[i+1] and 0xfffffffffffffL)
            xShiftedMask = ones((x ushr 2))
            tmp2[i + 1] = 0L
            tmp2[i + 2] = (tmp2[i+2] + ((x shl 44) and 0x7ffffffffffffL))
            tmp2[i + 3] = (tmp2[i+3] + (x ushr 7))
            tmp2[i + 4] = (tmp2[i+4] + ((x shl 37) and 0x7ffffffffffffL))
            tmp2[i + 5] = (tmp2[i+5] + (x ushr 14))
            tmp2[i + 5] = (tmp2[i+5] + (4503599627370496L and xShiftedMask))
            tmp2[i + 6] = (tmp2[i+6] - (1L and xShiftedMask))
            tmp2[i + 5] = (tmp2[i+5] - ((x shl 18) and 0xfffffffffffffL))
            tmp2[i + 5] = (tmp2[i+5] + ((x shl 50) and 0xfffffffffffffL))
            tmp2[i + 6] = (tmp2[i+6] + ((x ushr 2) - (x ushr 34)))
            audit?.snapshot("eliminate-odd", tmp2)
            i += 2
        }
        carry = 0L
        i = 0
        while (i < 4) {
            out[i] = tmp2[i+5]
            out[i] = (out[i] + carry)
            carry = (out[i] ushr 51)
            out[i] = (out[i] and 0x7ffffffffffffL)
            i++
            out[i] = (tmp2[i+5] shl 1)
            out[i] = (out[i] + carry)
            carry = (out[i] ushr 52)
            out[i] = (out[i] and 0xfffffffffffffL)
            i++
        }
        out[4] = tmp2[9]
        out[4] = (out[4] + carry)
        carry = (out[4] ushr 51)
        out[4] = (out[4] and 0x7ffffffffffffL)
        reduceCarry(out, carry, audit)
    }
}
