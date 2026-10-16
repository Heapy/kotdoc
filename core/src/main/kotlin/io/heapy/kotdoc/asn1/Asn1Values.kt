// Data.ASN1.Types, crypton-asn1-types 0.4.1; Vincent Hanquez 2010-2013.
// Full notice: licenses/crypton-asn1-types-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

import io.heapy.kotdoc.formats.HaskellDouble
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.time.HourglassDateTime
import io.heapy.kotdoc.time.HourglassTimezoneOffset
import java.math.BigInteger

typealias Asn1Tag = Long

sealed class Asn1ConstructionType {
    data object Sequence : Asn1ConstructionType()
    data object Set : Asn1ConstructionType()
    data class Container(val kind: Asn1Class, val tag: Asn1Tag) : Asn1ConstructionType()
    final override fun toString() = when (this) { Sequence -> "Sequence"; Set -> "Set"; is Container -> "Container $kind ${asn1Argument(tag)}" }
}
enum class Asn1TimeType { TimeUTC, TimeGeneralized }

sealed class Asn1Value {
    data class BooleanValue(val value: Boolean) : Asn1Value()
    class IntVal private constructor(private val stored: Lazy<BigInteger>) : Asn1Value() {
        constructor(value: BigInteger): this(lazyOf(value))
        val value get() = stored.value
        operator fun component1() = value
        fun copy(value: BigInteger = this.value) = IntVal(value)
        override fun equals(other: Any?) = other is IntVal && value == other.value
        override fun hashCode() = value.hashCode()
        companion object { fun deferred(value: () -> BigInteger) = IntVal(lazy(value)) }
    }
    data class BitString(val value: Asn1BitArray) : Asn1Value()
    class OctetString private constructor(private val stored: Lazy<ByteArray>) : Asn1Value() {
        constructor(bytes: ByteArray): this(lazyOf(bytes.copyOf()))
        val bytes: ByteArray get() = stored.value.copyOf()
        override fun equals(other: Any?) = other is OctetString && stored.value.contentEquals(other.stored.value)
        override fun hashCode() = stored.value.contentHashCode()
        companion object { fun deferred(bytes: () -> ByteArray) = OctetString(lazy {bytes().copyOf()}) }
    }
    data object Null : Asn1Value()
    class Oid private constructor(private val stored: Lazy<Asn1Oid>) : Asn1Value() {
        constructor(arcs: Asn1Oid): this(lazyOf(arcs.toList()))
        val arcs: Asn1Oid get() = stored.value.toList()
        override fun equals(other: Any?) = other is Oid && stored.value == other.stored.value
        override fun hashCode() = stored.value.hashCode()
        companion object { fun deferred(arcs: () -> Asn1Oid) = Oid(lazy { arcs().toList() }) }
    }
    class Real(val value: Double) : Asn1Value() {
        // Haskell Double Eq: NaN differs from itself; signed zeros are equal.
        override fun equals(other: Any?) = other is Real && value == other.value
        override fun hashCode() = (if (value == 0.0) 0.0 else value).hashCode()
    }
    data class Enumerated(val value: BigInteger) : Asn1Value()
    data class StringValue(val value: Asn1CharacterString) : Asn1Value()
    data class Time(val kind: Asn1TimeType, val value: HourglassDateTime, val timezone: HourglassTimezoneOffset?) : Asn1Value()
    class Other(val kind: Asn1Class, val tag: Asn1Tag, bytes: ByteArray) : Asn1Value() {
        private val stored = bytes.copyOf()
        val bytes: ByteArray get() = stored.copyOf()
        override fun equals(other: Any?) = other is Other && kind == other.kind && tag == other.tag && stored.contentEquals(other.stored)
        override fun hashCode() = 31 * (31 * kind.hashCode() + tag.hashCode()) + stored.contentHashCode()
    }
    data class Start(val kind: Asn1ConstructionType) : Asn1Value()
    data class End(val kind: Asn1ConstructionType) : Asn1Value()

    final override fun toString(): String = when (this) {
        is BooleanValue -> "Boolean ${if (value) "True" else "False"}"
        is IntVal -> "IntVal ${asn1Argument(value)}"
        is BitString -> "BitString ($value)"
        is OctetString -> "OctetString ${asn1QuoteBytes(bytes)}"
        Null -> "Null"
        is Oid -> "OID ${arcs.joinToString(",", "[", "]")}"
        is Real -> "Real ${HaskellDouble.show(value).let { if (it.startsWith('-')) "($it)" else it }}"
        is Enumerated -> "Enumerated ${asn1Argument(value)}"
        is StringValue -> "ASN1String ($value)"
        is Time -> "ASN1Time $kind ($value) ${timezone?.let { "(Just $it)" } ?: "Nothing"}"
        is Other -> "Other $kind ${asn1Argument(tag)} ${asn1QuoteBytes(bytes)}"
        is Start -> "Start ${asn1ConstructionArgument(kind)}"
        is End -> "End ${asn1ConstructionArgument(kind)}"
    }
}
private fun asn1Argument(value: Any) = value.toString().let { if (it.startsWith('-')) "($it)" else it }
private fun asn1ConstructionArgument(value: Asn1ConstructionType) = if (value is Asn1ConstructionType.Container) "($value)" else value.toString()
private fun asn1QuoteBytes(bytes: ByteArray) = HaskellString.quoteCodePoints(bytes.map { it.toInt() and 255 }.toIntArray())
internal fun asn1ShowList(values: List<Asn1Value>) = values.joinToString(",", "[", "]")

typealias Asn1StreamBuilder = (List<Asn1Value>) -> List<Asn1Value>
interface Asn1Object<A> {
    fun toAsn1(value: A): Asn1StreamBuilder
    fun fromAsn1(values: List<Asn1Value>): Asn1ParseResult<Asn1Parsed<A>>
}
