package io.heapy.kotdoc.crypto

/** A borrowed byte-buffer region, corresponding to a pointer and explicit length. */
internal class CryptoByteSpan(val data: ByteArray, val offset: Int = 0, val size: Int = data.size - offset) {
    operator fun get(index: Int) = data[offset + index]
    operator fun set(index: Int, value: Byte) { data[offset + index] = value }
    fun clear() = data.fill(0, offset, offset + size)
}
