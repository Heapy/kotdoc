// Data.ASN1.Serialize, crypton-asn1-encoding 0.10.0; Vincent Hanquez.
// Full notice: licenses/crypton-asn1-encoding-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

import java.io.ByteArrayOutputStream
import java.math.BigInteger

internal object Asn1Serialize {
    private val longTag: Asn1Get<Long> = Asn1Get { initial ->
        fun read(state: Asn1GetState, accumulator: Long, first: Boolean): Asn1GetResult<Long> {
            var current = state; var value = accumulator; var initialByte = first
            fun accept(result: Asn1GetResult<Int>, before: Long, firstByte: Boolean): Asn1GetResult<Long> = when (result) {
                is Asn1GetResult.Failed -> result
                is Asn1GetResult.Partial -> Asn1GetResult.Partial { accept(result.feed(it), before, firstByte) }
                is Asn1GetResult.Done -> when {
                    firstByte && result.value == 128 -> Asn1GetResult.Failed("Failed reading: non canonical encoding of long tag", result.state)
                    result.value and 128 == 0 -> Asn1GetResult.Done(if (firstByte) result.value.toLong() else (before shl 7) + result.value, result.state)
                    else -> read(result.state, if (firstByte) (result.value and 127).toLong() else (before shl 7) + (result.value and 127), false)
                }
            }
            while (true) {
                when (val result = Asn1Get.word8.run(current)) {
                    is Asn1GetResult.Done -> {
                        val byte = result.value
                        if (initialByte && byte == 128) return Asn1GetResult.Failed("Failed reading: non canonical encoding of long tag", result.state)
                        if (byte and 128 == 0) return Asn1GetResult.Done(if (initialByte) byte.toLong() else (value shl 7) + byte, result.state)
                        value = if (initialByte) (byte and 127).toLong() else (value shl 7) + (byte and 127)
                        initialByte = false; current = result.state
                    }
                    else -> return accept(result, value, initialByte)
                }
            }
        }
        read(initial, 0, true)
    }
    private val length: Asn1Get<Asn1Length> = Asn1Get.word8.flatMap { first ->
        when {
            first < 128 -> Asn1Get.pure(Asn1Length.Short(first.toLong()))
            first == 128 -> Asn1Get.pure(Asn1Length.Indefinite)
            else -> Asn1Get.bytes((first and 127).toLong()).map { bytes -> Asn1Length.LongForm((first and 127).toLong(), bytes.fold(0L) { n, b -> (n shl 8) + (b.toInt() and 255) }) }
        }
    }
    val header: Asn1Get<Asn1Header> = Asn1Get.word8.flatMap { first ->
        (if (first and 31 == 31) longTag else Asn1Get.pure((first and 31).toLong())).flatMap { tag ->
            length.map { Asn1Header(Asn1Class.entries[first ushr 6], tag, first and 32 != 0, it) }
        }
    }
    fun putHeader(header: Asn1Header): ByteArray = ByteArrayOutputStream().apply {
        write((header.kind.ordinal shl 6) or (if (header.constructed) 32 else 0) or (if (header.tag < 31) header.tag.toInt() and 255 else 31))
        if (header.tag >= 31) write(Asn1Integers.variableBytes(BigInteger.valueOf(header.tag)))
        when (val length = header.length) {
            is Asn1Length.Short -> {
                check(length.value in 0..127) { "putLength: short length is not between 0x0 and 0x80" }; write(length.value.toInt())
            }
            is Asn1Length.LongForm -> {
                check(length.value >= 0) { "putLength: long length is negative" }
                val bytes = Asn1Integers.unsignedBytes(BigInteger.valueOf(length.value)); write(bytes.size or 128); write(bytes)
            }
            Asn1Length.Indefinite -> write(128)
        }
    }.toByteArray()
}
