// Data.ASN1.Pretty, crypton-asn1-types 0.4.1; Vincent Hanquez 2010-2013.
// Complete notice: licenses/crypton-asn1-types-BSD-3-Clause.txt.
// Hex output agrees with base16 1.0; licenses/base16-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

import io.heapy.kotdoc.formats.HaskellDouble
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.time.HourglassDateTime
import io.heapy.kotdoc.time.HourglassTimezoneOffset
import java.math.BigInteger

sealed interface Asn1PrettyType {
    data class Multiline(val initial: Long): Asn1PrettyType {
        override fun toString() = "Multiline ${if (initial < 0) "($initial)" else initial.toString()}"
    }
    data object SingleLine: Asn1PrettyType
}
object Asn1Pretty {
    fun pretty(type: Asn1PrettyType, values: List<Asn1Value>) = text(stream(type,values))
    fun stream(type: Asn1PrettyType, values: List<Asn1Value>) = stream(type,values.toList().asSequence())
    /** Haskell String demand: prefixes do not evaluate a later value or suffix. */
    fun stream(type: Asn1PrettyType, values: Sequence<Asn1Value>): Sequence<Int> = sequence {
        var indentation = (type as? Asn1PrettyType.Multiline)?.initial ?: 0
        for (value in values) {
            if (type is Asn1PrettyType.Multiline) {
                val at = if (value is Asn1Value.End) indentation-1 else indentation
                var n = at
                while (n > 0) { yield(32); n-- }
            }
            yieldAll(render(value))
            if (type == Asn1PrettyType.SingleLine) yield(44)
            else when (value) { is Asn1Value.Start -> indentation++; is Asn1Value.End -> indentation--; else -> Unit }
        }
    }
    private fun chars(s: String) = s.codePoints().toArray().asSequence()
    private fun text(s: Sequence<Int>) = buildString { for (point in s) appendCodePoint(point) }
    private fun quote(bytes: ByteArray) = HaskellString.quoteCodePoints(bytes.map { it.toInt() and 255 }.toIntArray())
    private fun hex(bytes: ByteArray) = buildString {
        append('"'); val digits = "0123456789abcdef"
        for (byte in bytes) { val b = byte.toInt() and 255; append(digits[b ushr 4]); append(digits[b and 15]) }
        append('"')
    }
    private fun shownDateTime(value: HourglassDateTime): Sequence<Int> = sequence {
        yieldAll(chars("DateTime {dtDate = ")); yieldAll(chars(value.date.toString()))
        yieldAll(chars(", dtTime = ")); yieldAll(chars(value.time.toString())); yield(125)
    }
    private fun shownTimezone(value: HourglassTimezoneOffset): Sequence<Int> = sequence {
        yieldAll(chars(if (value.minutes < 0) "-" else "+"))
        val absolute = kotlin.math.abs(value.minutes)
        yieldAll(chars(io.heapy.kotdoc.time.HourglassPadding.pad2(BigInteger.valueOf(Math.floorDiv(absolute,60L)))))
        yieldAll(chars(io.heapy.kotdoc.time.HourglassPadding.pad2(BigInteger.valueOf(Math.floorMod(absolute,60L)))))
    }
    private fun render(value: Asn1Value): Sequence<Int> = sequence {
        when (value) {
            is Asn1Value.BooleanValue -> { yieldAll(chars("bool: ")); yieldAll(chars(if (value.value) "True" else "False")) }
            is Asn1Value.IntVal -> {
                yieldAll(chars("int: "))
                if (value.value.signum() < 0) error("GHC.Internal.Numeric.showIntAtBase: applied to negative number ${value.value}")
                yieldAll(chars(value.value.toString(16)))
            }
            is Asn1Value.BitString -> { yieldAll(chars("bitstring: ")); yieldAll(chars(hex(value.value.data))) }
            is Asn1Value.OctetString -> { yieldAll(chars("octetstring: ")); yieldAll(chars(hex(value.bytes))) }
            Asn1Value.Null -> yieldAll(chars("null"))
            is Asn1Value.Oid -> { yieldAll(chars("OID: ")); yieldAll(chars(value.arcs.joinToString(",","[","]"))) }
            is Asn1Value.Real -> { yieldAll(chars("real: ")); yieldAll(chars(HaskellDouble.show(value.value))) }
            is Asn1Value.Enumerated -> yieldAll(chars("enum"))
            is Asn1Value.Start, is Asn1Value.End -> {
                val start = value is Asn1Value.Start
                val kind = if (value is Asn1Value.Start) value.kind else (value as Asn1Value.End).kind
                when (kind) {
                    Asn1ConstructionType.Sequence -> yield(if (start) 123 else 125)
                    Asn1ConstructionType.Set -> yield(if (start) 91 else 93)
                    is Asn1ConstructionType.Container -> { yieldAll(chars(if (start) "< " else "> ")); yieldAll(chars(kind.kind.toString())); yield(32); yieldAll(chars(kind.tag.toString())) }
                }
            }
            is Asn1Value.StringValue -> {
                val cs = value.value
                val (prefix,asHex) = when (cs.encoding) {
                    Asn1StringEncoding.UTF8 -> "utf8string:" to false
                    Asn1StringEncoding.Numeric -> "numericstring:" to true
                    Asn1StringEncoding.Printable -> "printablestring: " to false
                    Asn1StringEncoding.T61 -> "t61string:" to false
                    Asn1StringEncoding.VideoTex -> "videotexstring:" to true
                    Asn1StringEncoding.IA5 -> "ia5string:" to false
                    Asn1StringEncoding.Graphic -> "graphicstring:" to true
                    Asn1StringEncoding.Visible -> "visiblestring:" to true
                    Asn1StringEncoding.General -> "generalstring:" to true
                    Asn1StringEncoding.UTF32 -> "universalstring:" to false
                    Asn1StringEncoding.Character -> "characterstring:" to true
                    Asn1StringEncoding.BMP -> "bmpstring: " to false
                }
                yieldAll(chars(prefix)); yieldAll(chars(if (asHex) hex(cs.rawData) else quote(cs.rawData)))
            }
            is Asn1Value.Time -> {
                yieldAll(chars(if (value.kind == Asn1TimeType.TimeUTC) "utctime: " else "generalizedtime: "))
                yieldAll(shownDateTime(value.value)); yield(32)
                val tz = value.timezone
                if (tz == null) yieldAll(chars("Nothing")) else { yieldAll(chars("Just ")); yieldAll(shownTimezone(tz)) }
            }
            is Asn1Value.Other -> {
                yieldAll(chars("other(")); yieldAll(chars(value.kind.toString())); yield(44)
                yieldAll(chars(value.tag.toString())); yield(44); yieldAll(chars(quote(value.bytes))); yield(41)
            }
        }
    }
}
