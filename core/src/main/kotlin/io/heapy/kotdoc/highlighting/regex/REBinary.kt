// Skylighting.Regex 0.15 / Data.Binary.Class and Get.Internal 0.8.9.3.
// Copyright (c) 2016-2024 John MacFarlane; binary contributors.
// BSD-3-Clause; licenses/Skylighting-core-BSD-3-Clause.txt and Binary-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting.regex

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Original Binary RE representation; compilation is reconstructed lazily. */
fun RE.encodeBinary(): ByteArray {
    val content = bytes
    return ByteBuffer.allocate(8 + content.size + 2).order(ByteOrder.BIG_ENDIAN)
        .putLong(content.size.toLong()).put(content).put(if (caseSensitive) 1.toByte() else 0.toByte())
        .put(if (minimal) 1.toByte() else 0.toByte()).array()
}

sealed class REBinaryResult(remaining: ByteArray, val consumed: Long) {
    private val rest = remaining.copyOf()
    val remaining: ByteArray get() = rest.copyOf()
    class Success(val value: RE, remaining: ByteArray, consumed: Long) : REBinaryResult(remaining, consumed)
    class Failure(val error: String, remaining: ByteArray, consumed: Long) : REBinaryResult(remaining, consumed)
}

/** decodeOrFail boundary: retains unconsumed bytes and original error offsets. */
fun decodeREBinary(bytes: ByteArray): REBinaryResult {
    val content = bytes.copyOf()
    var offset = 0
    fun failure(error: String) = REBinaryResult.Failure(error, content.copyOfRange(offset, content.size), offset.toLong())
    if (content.size < 8) return failure("not enough bytes")
    val length = ByteBuffer.wrap(content, 0, 8).order(ByteOrder.BIG_ENDIAN).long; offset = 8
    // getByteString returns empty for every nonpositive length, including negatives.
    val count = length.coerceAtLeast(0)
    if (count > content.size - offset) return failure("not enough bytes")
    val pattern = content.copyOfRange(offset, offset + count.toInt()); offset += count.toInt()
    if (offset == content.size) return failure("not enough bytes")
    val sensitive = content[offset++].toInt() and 255
    if (sensitive > 1) return failure("Could not map value $sensitive to Bool")
    if (offset == content.size) return failure("not enough bytes")
    val minimal = content[offset++].toInt() and 255
    if (minimal > 1) return failure("Could not map value $minimal to Bool")
    return REBinaryResult.Success(RE(pattern, sensitive == 1, minimal == 1), content.copyOfRange(offset, content.size), offset.toLong())
}
