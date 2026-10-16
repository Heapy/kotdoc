// Data.ASN1.BitArray, crypton-asn1-types 0.4.1; Vincent Hanquez 2010-2013.
// Complete notice: licenses/crypton-asn1-types-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

import io.heapy.kotdoc.formats.HaskellString

class Asn1BitArrayOutOfBound(val index: ULong) : RuntimeException("BitArrayOutOfBound $index") {
    override fun equals(other: Any?) = other is Asn1BitArrayOutOfBound && index == other.index
    override fun hashCode() = index.hashCode()
    override fun toString() = message!!
}

/** Length and padded bytes are independent, as in the original exported constructor. */
class Asn1BitArray(val length: ULong, data: ByteArray) {
    private val storedData = data.copyOf()
    val data: ByteArray get() = storedData.copyOf()

    fun getBit(index: ULong): Boolean {
        if (index >= length) throw Asn1BitArrayOutOfBound(index)
        val offset = index / 8u
        if (offset >= storedData.size.toULong()) error("Data.ByteString.index: index too large: $offset, length = ${storedData.size}")
        return storedData[offset.toInt()].toInt() and (1 shl (7 - (index % 8u).toInt())) != 0
    }

    fun setBitValue(index: ULong, value: Boolean): Asn1BitArray {
        if (index >= length) throw Asn1BitArrayOutOfBound(index)
        val offset = index / 8u
        if (offset >= storedData.size.toULong()) error("Maybe.fromJust: Nothing")
        val bytes = storedData.copyOf()
        val mask = 1 shl (7 - (index % 8u).toInt())
        val byte = bytes[offset.toInt()].toInt()
        bytes[offset.toInt()] = (if (value) byte or mask else byte and mask.inv()).toByte()
        return Asn1BitArray(length, bytes)
    }

    fun setBit(index: ULong) = setBitValue(index, true)
    fun clearBit(index: ULong) = setBitValue(index, false)
    override fun equals(other: Any?) = other is Asn1BitArray && length == other.length && storedData.contentEquals(other.storedData)
    override fun hashCode() = 31 * length.hashCode() + storedData.contentHashCode()
    override fun toString() = "BitArray $length ${HaskellString.quoteCodePoints(storedData.map { it.toInt() and 255 }.toIntArray())}"

    companion object {
        /** The original accepts any signed machine Int padding and wraps the resulting Word64. */
        fun fromBytes(bytes: ByteArray, toSkip: Long) = Asn1BitArray((bytes.size.toLong() * 8 - toSkip).toULong(), bytes)
    }
}
