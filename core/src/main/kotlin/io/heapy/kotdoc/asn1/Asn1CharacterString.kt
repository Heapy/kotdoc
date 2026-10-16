// Data.ASN1.Types.String, crypton-asn1-types 0.4.1; Vincent Hanquez 2010-2013.
// Complete notice: licenses/crypton-asn1-types-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import java.io.ByteArrayOutputStream

enum class Asn1StringEncoding { IA5, UTF8, General, Graphic, Numeric, Printable, VideoTex, Visible, T61, UTF32, Character, BMP }

class Asn1CharacterString(val encoding: Asn1StringEncoding, rawData: ByteArray) : Comparable<Asn1CharacterString> {
    private val storedData = rawData.copyOf()
    val rawData: ByteArray get() = storedData.copyOf()

    /** Both the list spine and each Char are lazy in the original Haskell String. */
    fun decodeLazy(): Sequence<Lazy<Int>>? {
        if (encoding == Asn1StringEncoding.VideoTex || encoding == Asn1StringEncoding.Character) return null
        return sequence {
            fun byte(at: Int) = storedData[at].toInt() and 255
            fun character(point: Long): Int {
                if (point !in 0..0x10ffffL) error("Prelude.chr: bad argument: $point")
                return point.toInt()
            }
            when (encoding) {
                Asn1StringEncoding.UTF8 -> {
                    var at = 0
                    while (at < storedData.size) {
                        val head = byte(at++)
                        if (head < 0x80) { yield(lazyOf(head)); continue }
                        val count: Int
                        val initial: Int
                        when {
                            head < 0xc0 -> error("continuation byte in heading context")
                            head < 0xe0 -> { count = 1; initial = head and 0x1f }
                            head < 0xf0 -> { count = 2; initial = head and 0xf }
                            head < 0xf8 -> { count = 3; initial = head and 7 }
                            else -> error("too many byte")
                        }
                        if (storedData.size - at < count) error("truncated continuation, expecting $count ${if (count == 1) "byte" else "bytes"}")
                        val start = at
                        at += count
                        yield(lazy {
                            var point = initial
                            repeat(count) { offset ->
                                val next = byte(start + offset)
                                if (next and 0xc0 != 0x80) error("continuation bytes invalid")
                                point = (point shl 6) + (next and 0x3f)
                            }
                            // The source accepts overlong encodings and surrogate Chars.
                            character(point.toLong())
                        })
                    }
                }
                Asn1StringEncoding.BMP -> {
                    if (storedData.size % 2 != 0) error("not a valid BMP string")
                    for (at in storedData.indices step 2) yield(lazy { (byte(at) shl 8) or byte(at + 1) })
                }
                Asn1StringEncoding.UTF32 -> {
                    if (storedData.size % 4 != 0) error("not a valid UTF32 string")
                    for (at in storedData.indices step 4) {
                        yield(lazy {
                            val point = (byte(at).toLong() shl 24) or (byte(at + 1).toLong() shl 16) or (byte(at + 2).toLong() shl 8) or byte(at + 3).toLong()
                            character(point)
                        })
                    }
                }
                else -> for (at in storedData.indices) yield(lazy { byte(at) })
            }
        }
    }

    /** Force each Char as it is requested; unsupported encodings return null. */
    fun decodePoints(): Sequence<Int>? = decodeLazy()?.map { it.value }

    /** Materialize the list spine before forcing its separately lazy elements. */
    fun decode(): CharacterString? = decodeLazy()?.toList()?.let { chars -> CharacterString(chars.map { it.value }.toIntArray()) }
    override fun compareTo(other: Asn1CharacterString): Int {
        encoding.compareTo(other.encoding).let { if (it != 0) return it }
        for (at in 0..<minOf(storedData.size, other.storedData.size)) {
            ((storedData[at].toInt() and 255).compareTo(other.storedData[at].toInt() and 255)).let { if (it != 0) return it }
        }
        return storedData.size.compareTo(other.storedData.size)
    }
    override fun equals(other: Any?) = other is Asn1CharacterString && encoding == other.encoding && storedData.contentEquals(other.storedData)
    override fun hashCode() = 31 * encoding.hashCode() + storedData.contentHashCode()
    override fun toString() = "ASN1CharacterString {characterEncoding = $encoding, getCharacterStringRawData = ${HaskellString.quoteCodePoints(storedData.map { it.toInt() and 255 }.toIntArray())}}"

    companion object {
        fun fromString(text: String) = fromString(CharacterStringChars.fromString(text))
        fun fromString(text: CharacterString) = encode(Asn1StringEncoding.UTF8, text)
        fun encode(encoding: Asn1StringEncoding, text: CharacterString): Asn1CharacterString {
            if (encoding == Asn1StringEncoding.VideoTex || encoding == Asn1StringEncoding.Character) error("cannot encode ASN1 Character String $encoding from string")
            val bytes = ByteArrayOutputStream()
            fun cont(point: Int) = bytes.write(0x80 or (point and 0x3f))
            for (point in text.points()) when (encoding) {
                Asn1StringEncoding.UTF8 -> when {
                    point < 0x80 -> bytes.write(point)
                    point < 0x800 -> { bytes.write(0xc0 or (point shr 6)); cont(point) }
                    point < 0x10000 -> { bytes.write(0xe0 or (point shr 12)); cont(point shr 6); cont(point) }
                    else -> { bytes.write(0xf0 or (point shr 18)); cont(point shr 12); cont(point shr 6); cont(point) }
                }
                Asn1StringEncoding.BMP -> { bytes.write(point shr 8); bytes.write(point) }
                Asn1StringEncoding.UTF32 -> { bytes.write(point shr 24); bytes.write(point shr 16); bytes.write(point shr 8); bytes.write(point) }
                else -> bytes.write(point)
            }
            return Asn1CharacterString(encoding, bytes.toByteArray())
        }
    }
}
