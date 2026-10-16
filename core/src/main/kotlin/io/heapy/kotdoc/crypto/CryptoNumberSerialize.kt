// Crypto.Number.Serialize{,.LE,.Internal,.Internal.LE}, crypton 2.0.1.
// Full upstream notice: licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

import java.math.BigInteger

object CryptoSerialize : CryptoNumberSerialization(false)
object CryptoSerializeLE : CryptoNumberSerialization(true)

/** The two source modules differ only in byte order and zero-padding position. */
open class CryptoNumberSerialization internal constructor(private val littleEndian: Boolean) {
    fun i2osp(n: BigInteger): ByteArray {
        val size = if (n == BigInteger.ZERO) 1 else CryptoNumberBasic.numBytes(n)
        val bytes = ByteArray(size)
        rawI2osp(n, bytes, 0, size)
        return bytes
    }

    fun i2ospOf(length: Int, n: BigInteger) = i2ospOfDeferred(lazyOf(length), lazyOf(n))
    fun i2ospOfDeferred(length: Lazy<Int>, n: Lazy<BigInteger>): CryptoMaybe<ByteArray> {
        // The source bang on sz demands the integer even when length is invalid.
        val value = n.value
        val size = CryptoNumberBasic.numBytes(value)
        val len = length.value
        if (len <= 0 || value.signum() < 0 || size > len) return CryptoMaybe.None
        return CryptoMaybe.Some.deferred {
            val bytes = ByteArray(len)
            rawI2ospOf(value, bytes, 0, len)
            bytes
        }
    }

    fun i2ospOf_(length: Int, n: BigInteger): ByteArray = i2ospOfUnsafeDeferred(lazyOf(length), lazyOf(n))
    fun i2ospOfUnsafeDeferred(length: Lazy<Int>, n: Lazy<BigInteger>): ByteArray = when (val value = i2ospOfDeferred(length, n)) {
        CryptoMaybe.None -> error("i2ospOf_: integer is larger than expected")
        is CryptoMaybe.Some -> value.value
    }

    fun os2ip(bytes: ByteArray): BigInteger = rawOs2ip(bytes, 0, bytes.size)
    fun rawOs2ip(bytes: ByteArray, offset: Int, length: Int): BigInteger {
        if (length <= 0) return BigInteger.ZERO
        val buffer = bytes.copyOfRange(offset, offset + length)
        if (littleEndian) buffer.reverse()
        return try { BigInteger(1, buffer) } finally { buffer.fill(0) }
    }

    fun rawI2osp(n: BigInteger, bytes: ByteArray, offset: Int, length: Int) =
        rawWriteDeferred(lazyOf(n), bytes, offset, lazyOf(length), false)
    fun rawI2ospOf(n: BigInteger, bytes: ByteArray, offset: Int, length: Int) =
        rawWriteDeferred(lazyOf(n), bytes, offset, lazyOf(length), true)

    internal fun rawWriteDeferred(n: Lazy<BigInteger>, bytes: ByteArray, offset: Int, length: Lazy<Int>, padded: Boolean): Int {
        val value = n.value
        val size = CryptoNumberBasic.numBytes(value)
        val len = length.value
        if (len <= 0 || value.signum() < 0) return 0
        if (!padded && value == BigInteger.ZERO) { bytes[offset] = 0; return 1 }
        if (len < size) return 0
        if (padded) bytes.fill(0, offset, offset + len)
        // GMP exports zero as zero bytes, unlike the nonpadded i2osp zero case.
        if (size != 0) {
            val source = value.toByteArray()
            try {
                val start = if (padded && !littleEndian) offset + len - size else offset
                for (i in 0..<size) bytes[start + i] = source[if (littleEndian) source.lastIndex - i else source.size - size + i]
            } finally { source.fill(0) }
        }
        return if (padded) len else size
    }
}
