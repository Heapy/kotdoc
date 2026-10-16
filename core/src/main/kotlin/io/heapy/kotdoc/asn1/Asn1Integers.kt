// Data.ASN1.Internal, crypton-asn1-encoding 0.10.0, Vincent Hanquez.
// Full notice: licenses/crypton-asn1-encoding-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

import java.math.BigInteger

object Asn1Integers {
    fun unsigned(bytes: ByteArray): Pair<Int, BigInteger> = bytes.size to if (bytes.isEmpty()) BigInteger.ZERO else BigInteger(1, bytes)
    fun signed(bytes: ByteArray): Pair<Int, BigInteger> = bytes.size to if (bytes.isEmpty()) BigInteger.ZERO else BigInteger(bytes)
    fun unsignedBytes(value: BigInteger): ByteArray {
        if (value <= BigInteger.valueOf(255)) return byteArrayOf(value.toByte())
        val bytes = value.toByteArray()
        return if (bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }
    fun signedBytes(value: BigInteger): ByteArray = value.toByteArray()
    fun variableBytes(value: BigInteger): ByteArray {
        val bytes = mutableListOf<Byte>(); var current = value; var first = true
        while (current.signum() > 0) {
            bytes.add(((current.toInt() and 127) or if (first) 0 else 128).toByte()); first = false; current = current.shiftRight(7)
        }
        return bytes.asReversed().toByteArray()
    }
}
