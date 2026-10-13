// Codec.Picture.BitWriter: Copyright (c) 2011 Vincent Berthoux.
// Translated from JuicyPixels 3.3.9, BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.images

import java.io.ByteArrayOutputStream

/** Mutable execution of JuicyPixels' BoolReader state, including its EOF rules. */
class ImageBitReader private constructor(
    private var bytes: ByteArray,
    private var position: Int,
    private var bitIndex: Int,
    private var current: Int,
) {
    companion object {
        fun lsb(bytes: ByteArray): ImageBitReader = default().apply { setLsb(bytes) }
        fun msb(bytes: ByteArray): ImageBitReader = default().apply { setMsb(bytes) }
        fun jpeg(bytes: ByteArray): ImageBitReader = default().apply {
            this.bytes = bytes.copyOf()
            loadJpeg()
            // initBoolStateJpg's empty input differs from setDecodedStringJpg.
            if (bytes.isEmpty() || exhaustedAfterMarkers(bytes)) bitIndex = 0
        }
        fun default(): ImageBitReader = ImageBitReader(byteArrayOf(), 0, 0, 0)
        fun empty(): ImageBitReader = ImageBitReader(byteArrayOf(), 0, -1, 0)

        private fun exhaustedAfterMarkers(bytes: ByteArray): Boolean {
            var p = 0
            while (p + 1 < bytes.size && bytes[p] == (-1).toByte() && bytes[p + 1] != 0.toByte()) p += 2
            return p == bytes.size
        }
    }

    /** An independent continuation, analogous to reusing an immutable BoolState. */
    fun copy(): ImageBitReader = ImageBitReader(bytes, position, bitIndex, current)

    fun setLsb(bytes: ByteArray) {
        this.bytes = bytes.copyOf()
        position = 0
        loadLsb()
    }

    fun setMsb(bytes: ByteArray) {
        this.bytes = bytes.copyOf()
        position = 0
        loadMsb()
    }

    fun setJpeg(bytes: ByteArray) {
        this.bytes = bytes.copyOf()
        position = 0
        loadJpeg()
    }

    fun alignJpeg() {
        if (bitIndex != 7) loadJpeg()
    }

    fun readJpegBit(): Boolean {
        val value = current and (1 shl bitIndex) != 0
        if (bitIndex == 0) loadJpeg() else bitIndex--
        return value
    }

    fun readJpegInt(count: Int): Int {
        // Noncanonical baseline AC (run, 0) calls decodeInt(0), which asks
        // for -1 remaining bits and moves the bit cursor back one position.
        require(count >= -1)
        var remaining = count
        var acc = 0
        while (remaining != 0) {
            val left = bitIndex + 1
            if (remaining >= left) {
                val value = current.toLong() and mask(left)
                loadJpeg()
                remaining -= left
                acc = acc or (value shl remaining).toInt()
            } else {
                val rest = left - remaining
                acc = acc or ((current ushr rest).toLong() and mask(remaining)).toInt()
                bitIndex = rest - 1
                return acc
            }
        }
        return acc
    }

    fun readMsb(count: Int): UInt {
        require(count >= 0)
        var remaining = count
        var acc = 0L
        while (remaining != 0) {
            val left = bitIndex
            if (remaining >= left) {
                val value = current
                loadMsb()
                remaining -= left
                acc = acc or (value.toLong() shl remaining)
            } else {
                val rest = left - remaining
                acc = acc or (current ushr rest).toLong()
                current = current and mask(rest).toInt()
                bitIndex = rest
                return acc.toUInt()
            }
        }
        return acc.toUInt()
    }

    // GHC's unchecked Word32 shifts use a machine word before narrowing. On
    // the 64-bit reference this wraps the shift count modulo 64, not modulo
    // 32. Corrupt-code widths above 32 therefore cannot use UInt.shl directly.
    fun readLsb(count: Int): UInt {
        require(count >= 0)
        var result = 0u
        repeat(count) { i ->
            if (current and (1 shl bitIndex) != 0) result = result or (1L shl i).toUInt()
            if (bitIndex == 7) loadLsb() else bitIndex++
        }
        return result
    }

    private fun loadLsb() {
        current = nextByte()
        bitIndex = 0
    }
    private fun loadMsb() {
        current = nextByte()
        bitIndex = 8
    }
    private fun loadJpeg() {
        bitIndex = 7
        while (position < bytes.size) {
            val byte = nextByte()
            if (byte != 255) { current = byte; return }
            if (position == bytes.size) { current = 0; return }
            if (nextByte() == 0) { current = 255; return }
            // Every nonzero FF marker is skipped, not just restart markers.
        }
        current = 0
    }
    private fun nextByte(): Int = if (position < bytes.size) bytes[position++].toInt() and 255 else 0
    private fun mask(bits: Int): Long = if (bits == 32) 0xffffffffL else (1L shl bits) - 1
}

/** JPEG MSB/stuffing and GIF LSB bit output, sharing the upstream accumulator. */
class ImageBitWriter {
    private val output = ByteArrayOutputStream()
    private var current = 0
    private var count = 0

    fun writeJpeg(data: UInt, bits: Int) = write(data, bits, jpeg = true)
    fun writeGif(data: UInt, bits: Int) = write(data, bits, jpeg = false)

    private fun write(data: UInt, bits: Int, jpeg: Boolean) {
        require(bits in 0..32)
        var remaining = bits
        var value = data.toLong() and mask(bits)
        while (remaining + count > 8) {
            val left = 8 - count
            if (jpeg) {
                emit((current shl left) or (value ushr (remaining - left)).toInt(), jpeg = true)
                value = value and mask(remaining - left)
            } else {
                emit(current or (value shl count).toInt(), jpeg = false)
                value = value ushr left
            }
            remaining -= left
            current = 0
            count = 0
        }
        current = if (jpeg) (current shl remaining) or value.toInt() else current or (value shl count).toInt()
        current = current and 255
        count += remaining
        if (count == 8) {
            emit(current, jpeg)
            current = 0
            count = 0
        }
    }

    // Upstream finalize flushes the partial word without resetting its bit
    // accumulator. Repeated finalization and subsequent writes preserve that.
    fun finishJpeg(): ByteArray {
        if (count > 0) output.write((current shl (8 - count)) and 255)
        return output.toByteArray()
    }
    fun finishGif(): ByteArray {
        if (count > 0) output.write(current)
        return output.toByteArray()
    }
    private fun emit(value: Int, jpeg: Boolean) {
        val byte = value and 255
        output.write(byte)
        if (jpeg && byte == 255) output.write(0)
    }
    private fun mask(bits: Int): Long = if (bits == 32) 0xffffffffL else (1L shl bits) - 1
}
