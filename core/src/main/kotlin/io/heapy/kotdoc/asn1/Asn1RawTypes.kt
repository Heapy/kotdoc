// crypton-asn1-types 0.4.1 / crypton-asn1-encoding 0.10.0.
// Vincent Hanquez 2010-2013; complete notices in licenses/crypton-asn1-*-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

import io.heapy.kotdoc.formats.HaskellString

enum class Asn1Class { Universal, Application, Context, Private }
sealed interface Asn1Length {
    data class Short(val value: Long) : Asn1Length { override fun toString() = show() }
    data class LongForm(val octets: Long, val value: Long) : Asn1Length { override fun toString() = show() }
    data object Indefinite : Asn1Length { override fun toString() = show() }
    fun show(): String = when (this) { is Short -> "LenShort ${shownArgument(value)}"; is LongForm -> "LenLong ${shownArgument(octets)} ${shownArgument(value)}"; Indefinite -> "LenIndefinite" }
}
private fun shownArgument(value: Long) = if (value < 0) "($value)" else value.toString()
data class Asn1Header(val kind: Asn1Class, val tag: Long, val constructed: Boolean, val length: Asn1Length) {
    override fun toString() = "ASN1Header $kind ${shownArgument(tag)} ${if (constructed) "True" else "False"} " + if (length == Asn1Length.Indefinite) length.show() else "(${length.show()})"
}
sealed interface Asn1Event {
    class Header private constructor(private val delayed: Lazy<Asn1Header>) : Asn1Event {
        constructor(header: Asn1Header): this(lazyOf(header))
        val header get() = delayed.value
        operator fun component1() = header
        fun copy(header: Asn1Header = this.header) = Header(header)
        override fun equals(other: Any?) = other is Header && header == other.header
        override fun hashCode() = header.hashCode()
        override fun toString() = "Header ($header)"
        companion object { fun deferred(header: () -> Asn1Header) = Header(lazy(header)) }
    }
    class Primitive(bytes: ByteArray) : Asn1Event {
        private val data = bytes.copyOf()
        val bytes: ByteArray get() = data.copyOf()
        override fun equals(other: Any?) = other is Primitive && data.contentEquals(other.data)
        override fun hashCode() = data.contentHashCode()
        override fun toString() = "Primitive " + HaskellString.quoteCodePoints(data.map { it.toInt() and 255 }.toIntArray())
    }
    data object ConstructionBegin : Asn1Event
    data object ConstructionEnd : Asn1Event
}
sealed interface Asn1Error {
    data object StreamUnexpectedEOC : Asn1Error
    data object StreamInfinitePrimitive : Asn1Error
    data object StreamConstructionWrongSize : Asn1Error
    data class StreamUnexpectedSituation(val message: String) : Asn1Error
    data class ParsingHeaderFail(val message: String) : Asn1Error
    data object ParsingPartial : Asn1Error
    data class TypeNotImplemented(val message: String) : Asn1Error
    data class TypeDecodingFailed(val message: String) : Asn1Error
    data class TypePrimitiveInvalid(val message: String) : Asn1Error
    data class PolicyFailed(val policy: String, val message: String) : Asn1Error
    fun show(): String = when (this) {
        StreamUnexpectedEOC -> "StreamUnexpectedEOC"
        StreamInfinitePrimitive -> "StreamInfinitePrimitive"
        StreamConstructionWrongSize -> "StreamConstructionWrongSize"
        is StreamUnexpectedSituation -> "StreamUnexpectedSituation ${HaskellString.quote(message)}"
        is ParsingHeaderFail -> "ParsingHeaderFail ${HaskellString.quote(message)}"
        ParsingPartial -> "ParsingPartial"
        is TypeNotImplemented -> "TypeNotImplemented ${HaskellString.quote(message)}"
        is TypeDecodingFailed -> "TypeDecodingFailed ${HaskellString.quote(message)}"
        is TypePrimitiveInvalid -> "TypePrimitiveInvalid ${HaskellString.quote(message)}"
        is PolicyFailed -> "PolicyFailed ${HaskellString.quote(policy)} ${HaskellString.quote(message)}"
    }
}
sealed interface Asn1Result<out A> {
    data class Success<A>(val value: A) : Asn1Result<A>
    data class Failure(val error: Asn1Error) : Asn1Result<Nothing>
}
