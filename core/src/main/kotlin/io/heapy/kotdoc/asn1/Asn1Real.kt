// Data.ASN1.Prim, crypton-asn1-encoding 0.10.0; Vincent Hanquez 2010-2013.
// GHC.Num.Integer/Backend.{GMP,Native} 1.3 and rts/StgPrimFloat.c (GHC 9.12.2).
// Full notices: crypton-asn1-encoding-BSD-3-Clause.txt, ghc-bignum-BSD-3-Clause.txt,
// and GHC-base-BSD-3-Clause.txt in licenses/.
package io.heapy.kotdoc.asn1

import java.math.BigInteger

enum class Asn1IntegerFloatBackend { GMP, Native }
enum class Asn1NaNProfile(val bits: Long) {
    Positive(0x7ff8000000000000L), Negative(0xfff8000000000000uL.toLong());
    companion object {
        // The pinned AArch64 and x86-64 GHC builds give different signs to 0/0.
        val system: Asn1NaNProfile get() = if (System.getProperty("os.arch").lowercase() in setOf("amd64","x86_64","x86","i386","i686")) Negative else Positive
    }
}
object Asn1Real {
    private val minInt = BigInteger.valueOf(Long.MIN_VALUE)
    private val maxInt = BigInteger.valueOf(Long.MAX_VALUE)
    /** Preserve the pinned runtime's conversion, including its intermediate rounding. */
    fun encodeFloat(mantissa: BigInteger, exponent: Long, backend: Asn1IntegerFloatBackend = Asn1IntegerFloatBackend.GMP): Double {
        if (mantissa >= minInt && mantissa <= maxInt) {
            val value = mantissa.toLong()
            if (exponent == 0L) return value.toDouble()
            // __int_encodeDouble takes abs in signed machine Int before casting.
            val absolute = kotlin.math.abs(value).toDouble()
            val scaled = if (absolute == 0.0) absolute else Math.scalb(absolute,clamped(exponent))
            return if (value < 0) -scaled else scaled
        }
        val magnitude = mantissa.abs()
        val value = when (backend) {
            Asn1IntegerFloatBackend.GMP -> {
                val count = magnitude.bitLength()
                val kept = minOf(count,53)
                val top = magnitude.shiftRight(count-kept).toDouble()
                if (exponent == 0L) Math.scalb(top,count-kept)
                else {
                    val fraction = Math.scalb(top,-kept)
                    // The GMP wrapper adds HsInt/long, then passes the result
                    // to ldexp's C int parameter without the RTS clamp.
                    Math.scalb(fraction,(count.toLong()+exponent).toInt())
                }
            }
            Asn1IntegerFloatBackend.Native -> {
                var remaining = magnitude; var e = exponent; var sum = 0.0
                val mask = BigInteger.ONE.shiftLeft(64)-BigInteger.ONE
                while (remaining.signum() != 0) {
                    val word = remaining.and(mask).toLong().toULong().toDouble()
                    sum += if (word == 0.0) word else Math.scalb(word,clamped(e))
                    remaining = remaining.shiftRight(64); e += 64
                }
                sum
            }
        }
        return if (mantissa.signum() < 0) -value else value
    }
    private fun clamped(value: Long) = value.coerceIn(Int.MIN_VALUE.toLong(),Int.MAX_VALUE.toLong()).toInt()
    fun decode(bytes: ByteArray, backend: Asn1IntegerFloatBackend = Asn1IntegerFloatBackend.GMP, nanProfile: Asn1NaNProfile = Asn1NaNProfile.system): Asn1Result<Double> {
        if (bytes.isEmpty()) return Asn1Result.Success(0.0)
        val h = bytes[0].toInt() and 255
        if (h == 0x40) return Asn1Result.Success(Double.POSITIVE_INFINITY)
        if (h == 0x41) return Asn1Result.Success(Double.NEGATIVE_INFINITY)
        if (h == 0x42) return Asn1Result.Success(Double.fromBits(nanProfile.bits))
        fun fail(s: String) = Asn1Result.Failure(Asn1Error.TypeDecodingFailed(s))
        val baseMultiplier = when (h and 0x30) { 0 -> 1; 0x10 -> 3; 0x20 -> 4; else -> return fail("real: invalid base detected") }
        val length = if (h and 3 == 3) {
            if (bytes.size <= 1) return fail("real: not enough input to decode exponent length")
            bytes[1].toInt() and 255
        } else (h and 3)+1
        if (bytes.size <= 1+length) return fail("real: not enough input for exponent and mantissa")
        val exponent = Asn1Integers.signed(bytes.copyOfRange(1,1+length)).second * BigInteger.valueOf(baseMultiplier.toLong()) - BigInteger.valueOf(((h and 12) ushr 2).toLong())
        val magnitude = Asn1Integers.unsigned(bytes.copyOfRange(1+length,bytes.size)).second
        return Asn1Result.Success(encodeFloat(if (h and 64 != 0) -magnitude else magnitude,exponent.toLong(),backend))
    }
    fun encode(value: Double): ByteArray {
        if (value == 0.0) return byteArrayOf()
        if (value == Double.POSITIVE_INFINITY) return byteArrayOf(0x40)
        if (value == Double.NEGATIVE_INFINITY) return byteArrayOf(0x41)
        if (value.isNaN()) return byteArrayOf(0x42)
        val bits = value.toRawBits(); val exponentBits = ((bits ushr 52) and 2047).toInt()
        var mantissa = bits and 0x000fffffffffffffL
        var exponent: Int
        if (exponentBits == 0) {
            val shift = 53-(64-java.lang.Long.numberOfLeadingZeros(mantissa))
            mantissa = mantissa shl shift; exponent = -1074-shift
        } else { mantissa = mantissa or (1L shl 52); exponent = exponentBits-1023-52 }
        val zeros = java.lang.Long.numberOfTrailingZeros(mantissa)
        mantissa = mantissa ushr zeros; exponent += zeros
        val e = Asn1Integers.signedBytes(BigInteger.valueOf(exponent.toLong()))
        val m = Asn1Integers.signedBytes(BigInteger.valueOf(mantissa))
        return byteArrayOf(((if (value < 0) 0xc0 else 0x80) or (e.size-1)).toByte())+e+m
    }
}
