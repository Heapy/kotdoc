// Data.ASN1.Prim, crypton-asn1-encoding 0.10.0; Vincent Hanquez 2010-2013.
// Complete notice: licenses/crypton-asn1-encoding-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.time.*
import java.math.BigInteger

class Asn1Primitives(private val time: HourglassFormat = HourglassFormat(), private val realBackend: Asn1IntegerFloatBackend = Asn1IntegerFloatBackend.GMP, private val nanProfile: Asn1NaNProfile = Asn1NaNProfile.system) {
    private fun failure(message: String) = Asn1Result.Failure(Asn1Error.TypeDecodingFailed(message))
    fun getBoolean(der: Boolean, bytes: ByteArray): Asn1Result<Asn1Value> = if (bytes.size != 1) failure("boolean: length not within bound") else when (bytes[0].toInt() and 255) {
        0 -> Asn1Result.Success(Asn1Value.BooleanValue(false))
        255 -> Asn1Result.Success(Asn1Value.BooleanValue(true))
        else -> if (der) Asn1Result.Failure(Asn1Error.PolicyFailed("DER","boolean value not canonical")) else Asn1Result.Success(Asn1Value.BooleanValue(true))
    }
    private fun integer(type: String, bytes: ByteArray, make: (BigInteger) -> Asn1Value): Asn1Result<Asn1Value> {
        if (bytes.isEmpty()) return failure("$type: null encoding")
        if (bytes.size > 1) {
            val a = bytes[0].toInt() and 255; val b = bytes[1].toInt() and 255
            if (a == 255 && b and 128 != 0 || a == 0 && b and 128 == 0) return failure("$type: not shortest encoding")
        }
        return Asn1Result.Success(make(Asn1Integers.signed(bytes).second))
    }
    fun getInteger(bytes: ByteArray) = integer("integer",bytes,Asn1Value::IntVal)
    fun getDouble(bytes: ByteArray): Asn1Result<Asn1Value> = when (val result = Asn1Real.decode(bytes,realBackend,nanProfile)) {
        is Asn1Result.Failure -> result
        is Asn1Result.Success -> Asn1Result.Success(Asn1Value.Real(result.value))
    }
    fun getBitString(bytes: ByteArray): Asn1Result<Asn1Value> {
        if (bytes.isEmpty()) error("Data.ByteString.head: empty ByteString")
        val first = bytes[0].toInt() and 255; val skip = if (first in 48..55) first-48 else first
        if (skip > 7) return failure("bitstring: skip number not within bound $skip ${HaskellString.quoteCodePoints(bytes.map { it.toInt() and 255 }.toIntArray())}")
        return Asn1Result.Success(Asn1Value.BitString(Asn1BitArray.fromBytes(bytes.copyOfRange(1,bytes.size),skip.toLong())))
    }
    fun getOctetString(bytes: ByteArray): Asn1Result<Asn1Value> = Asn1Result.Success(Asn1Value.OctetString(bytes))
    fun getNull(bytes: ByteArray): Asn1Result<Asn1Value> = if (bytes.isEmpty()) Asn1Result.Success(Asn1Value.Null) else failure("Null: data length not within bound")
    fun getOID(bytes: ByteArray): Asn1Result<Asn1Value> {
        if (bytes.isEmpty()) return failure("OID: no data")
        val first = bytes[0].toInt() and 255
        val arcs = mutableListOf(BigInteger.valueOf((first/40).toLong()),BigInteger.valueOf((first%40).toLong()))
        var acc = BigInteger.ZERO; var continued = false
        for (i in 1..<bytes.size) {
            val b = bytes[i].toInt() and 255; acc = acc.shiftLeft(7)+BigInteger.valueOf((b and 127).toLong()); continued = b and 128 != 0
            if (!continued) { arcs.add(acc); acc = BigInteger.ZERO }
        }
        if (continued) arcs.add(acc)
        return Asn1Result.Success(Asn1Value.Oid(arcs))
    }
    fun getTime(type: Asn1TimeType, bytes: ByteArray): Asn1Result<Asn1Value> {
        fun fail(reason: String) = failure("time format invalid for $type : $reason")
        if (bytes.any { it.toInt() and 255 > 127 }) return fail("contains non ASCII characters")
        val input = bytes.toString(Charsets.ISO_8859_1)
        val format = if (type == Asn1TimeType.TimeUTC) "YYMMDDHMIS" else "YYYYMMDDHMIS"
        val full = time.timeParseE(HourglassTimeFormatString.fromString(format),Chars.fromString(input))
        val parsed = if (full is HourglassFormatParseResult.Failure) time.timeParseE(HourglassTimeFormatString.fromString(format.dropLast(1)),Chars.fromString(input)) else full
        if (parsed is HourglassFormatParseResult.Failure) return fail("cannot convert string $input")
        parsed as HourglassFormatParseResult.Success
        val original = parsed.value
        var dt = HourglassDateTime.deferred({ original.date.let { if (type == Asn1TimeType.TimeUTC && it.year > 2050) it.copy(year=it.year-100) else it } },{ original.time })
        var rest = Chars.build(parsed.remaining)
        if (rest.startsWith('.')) {
            var n = 0; var count = 0
            while (count < 3 && count+1 < rest.length && rest[count+1] in '0'..'9') { n = n*10+(rest[count+1]-'0'); count++ }
            val nanos = n.toLong() * (if (count == 1) 100 else if (count == 2) 10 else 1) * 1000000
            val previous = dt
            dt = HourglassDateTime.deferred({ previous.date },{ previous.time.copy(nanoSecond=HourglassNanoSeconds(nanos)) })
            rest = rest.drop(count+1)
        }
        val timezone = when {
            rest.startsWith('+') || rest.startsWith('-') -> {
                if (rest.length != 5) null else {
                    var n = 0
                    for (c in rest.drop(1)) { if (c !in '0'..'9') break; n = n*10+(c-'0') }
                    HourglassTimezoneOffset((if (rest[0] == '-') -1 else 1)*(n/100*60L+n%100))
                }
            }
            rest == "Z" -> HourglassTimezoneOffset.UTC
            rest.isEmpty() -> null
            else -> return fail("unknown timezone format: $rest")
        }
        return Asn1Result.Success(Asn1Value.Time(type,dt,timezone))
    }
    fun putTime(type: Asn1TimeType, value: HourglassDateTime, timezone: HourglassTimezoneOffset?): ByteArray {
        val fmt = HourglassTimeFormatString.fromString(if (type == Asn1TimeType.TimeUTC) "YYMMDDHMIS" else "YYYYMMDDHMIS")
        val shown = Chars.build(time.timePrint(fmt,value,HourglassTimeCodecs().dateTime))
        val suffix = if (timezone == null) "" else if (timezone.minutes == 0L) "Z" else timezone.toString()
        return (shown+suffix).toByteArray(Charsets.ISO_8859_1)
    }
    fun putInteger(value: BigInteger) = Asn1Integers.signedBytes(value)
    fun putDouble(value: Double) = Asn1Real.encode(value)
    fun putBitString(value: Asn1BitArray) = byteArrayOf(((8uL-value.length%8uL) and 7uL).toByte())+value.data
    fun putString(value: ByteArray) = value.copyOf()
    fun putOID(arcs: Asn1Oid): ByteArray {
        if (arcs.size < 2) error("invalid OID format ${arcs.joinToString(",","[","]")}")
        val out = java.io.ByteArrayOutputStream(); out.write((arcs[0]*BigInteger.valueOf(40)+arcs[1]).toInt() and 255)
        for (arc in arcs.drop(2)) out.write(if (arc == BigInteger.ZERO) byteArrayOf(0) else Asn1Integers.variableBytes(arc))
        return out.toByteArray()
    }
    fun decodePrimitive(header: Asn1Header, bytes: ByteArray): Asn1Result<Asn1Value> {
        if (header.kind != Asn1Class.Universal) return Asn1Result.Success(Asn1Value.Other(header.kind,header.tag,bytes))
        return when (header.tag) {
            1L -> getBoolean(false,bytes); 2L -> getInteger(bytes); 3L -> getBitString(bytes); 4L -> getOctetString(bytes); 5L -> getNull(bytes); 6L -> getOID(bytes)
            7L -> Asn1Result.Failure(Asn1Error.TypeNotImplemented("Object Descriptor")); 8L -> Asn1Result.Failure(Asn1Error.TypeNotImplemented("External"))
            9L -> getDouble(bytes); 10L -> integer("enumerated",bytes,Asn1Value::Enumerated)
            11L -> Asn1Result.Failure(Asn1Error.TypeNotImplemented("EMBEDDED PDV")); 13L -> Asn1Result.Failure(Asn1Error.TypeNotImplemented("RELATIVE-OID"))
            16L -> Asn1Result.Failure(Asn1Error.TypePrimitiveInvalid("sequence")); 17L -> Asn1Result.Failure(Asn1Error.TypePrimitiveInvalid("set"))
            23L -> getTime(Asn1TimeType.TimeUTC,bytes); 24L -> getTime(Asn1TimeType.TimeGeneralized,bytes)
            else -> {
                val encoding = stringTags.indexOf(header.tag)
                if (encoding < 0) Asn1Result.Success(Asn1Value.Other(header.kind,header.tag,bytes)) else Asn1Result.Success(Asn1Value.StringValue(Asn1CharacterString(Asn1StringEncoding.entries[encoding],bytes)))
            }
        }
    }
    fun encodeHeader(constructed: Boolean, length: Asn1Length, value: Asn1Value): Asn1Header {
        val tag = when (value) {
            is Asn1Value.BooleanValue -> 1L; is Asn1Value.IntVal -> 2L; is Asn1Value.BitString -> 3L; is Asn1Value.OctetString -> 4L; Asn1Value.Null -> 5L
            is Asn1Value.Oid -> 6L; is Asn1Value.Real -> 9L; is Asn1Value.Enumerated -> 10L
            is Asn1Value.StringValue -> stringTags[value.value.encoding.ordinal]
            is Asn1Value.Time -> if (value.kind == Asn1TimeType.TimeUTC) 23L else 24L
            is Asn1Value.Other -> return Asn1Header(value.kind,value.tag,constructed,length)
            is Asn1Value.Start -> when (val kind = value.kind) { Asn1ConstructionType.Sequence -> 16L; Asn1ConstructionType.Set -> 17L; is Asn1ConstructionType.Container -> return Asn1Header(kind.kind,kind.tag,constructed,length) }
            is Asn1Value.End -> error("this should not happen")
        }
        return Asn1Header(Asn1Class.Universal,tag,constructed,length)
    }
    fun encodePrimitiveHeader(length: Asn1Length, value: Asn1Value) = encodeHeader(false,length,value)
    internal fun primitiveData(value: Asn1Value): ByteArray = when (value) {
        is Asn1Value.BooleanValue -> byteArrayOf(if (value.value) -1 else 0)
        is Asn1Value.IntVal -> putInteger(value.value); is Asn1Value.BitString -> putBitString(value.value); is Asn1Value.OctetString -> value.bytes
        Asn1Value.Null -> byteArrayOf(); is Asn1Value.Oid -> putOID(value.arcs); is Asn1Value.Real -> putDouble(value.value); is Asn1Value.Enumerated -> putInteger(value.value)
        is Asn1Value.StringValue -> value.value.rawData; is Asn1Value.Time -> putTime(value.kind,value.value,value.timezone); is Asn1Value.Other -> value.bytes
        else -> error("not a primitive $value")
    }
    fun encodePrimitive(value: Asn1Value) = Asn1Encoded.primitive(this,value)
    fun encodeOne(value: Asn1Value): Asn1Encoded {
        if (value is Asn1Value.Start) error("encode one cannot do start")
        return encodePrimitive(value)
    }
    fun encodeList(values: List<Asn1Value>) = Asn1Encoded.list(this,values,false)
    fun encodeConstructed(value: Asn1Value, children: List<Asn1Value>): Asn1Encoded {
        if (value !is Asn1Value.Start) error("not a start node")
        return Asn1Encoded.constructed(this,value,children)
    }
    companion object {
        private val stringTags = listOf(22L,12L,27L,25L,18L,19L,21L,26L,20L,28L,29L,30L)
        fun smallestLength(value: Long): Asn1Length {
            if (value < 128) return Asn1Length.Short(value)
            var count = 1L; var n = value
            while (n > 255) { count++; n /= 256 }
            return Asn1Length.LongForm(count,value)
        }
    }
}
