// Data.X509.ExtensionRaw, crypton-x509 1.9.2; Vincent Hanquez.
// Complete original notice: licenses/crypton-x509-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterStringChars

class X509ExtensionRaw private constructor(private val delayedOid: Lazy<Asn1Oid>,private val delayedCritical: Lazy<Boolean>,private val delayedContent: Lazy<ByteArray>) {
    constructor(oid: Asn1Oid,critical: Boolean,content: ByteArray): this(lazyOf(oid.toList()),lazyOf(critical),lazyOf(content.copyOf()))
    val oid get()=delayedOid.value.toList()
    val critical get()=delayedCritical.value
    val content get()=delayedContent.value.copyOf()
    override fun equals(other: Any?)=other is X509ExtensionRaw && oid==other.oid && critical==other.critical && delayedContent.value.contentEquals(other.delayedContent.value)
    override fun hashCode()=31*(31*oid.hashCode()+critical.hashCode())+delayedContent.value.contentHashCode()
    override fun toString()="ExtensionRaw {extRawOID = ${oid.joinToString(",","[","]")}, extRawCritical = ${if(critical) "True" else "False"}, extRawContent = ${HaskellString.quoteCodePoints(content.map {it.toInt() and 255}.toIntArray())}}"
    fun tryAsn1(): Asn1ParseResult<Sequence<Asn1Value>> = when(val result=Asn1BinaryEncoding().decode(content,Asn1EncodingRules.BER)) {
        is Asn1Result.Failure -> Asn1ParseResult.Failure.deferred {CharacterStringChars.fromString("fromASN1: X509.ExtensionRaw: OID=${oid.joinToString(",","[","]")}: cannot decode data: ${result.error.show()}")}
        is Asn1Result.Success -> Asn1ParseResult.Success(result.value)
    }
    @Deprecated("use tryAsn1 instead")
    fun asn1(): Sequence<Asn1Value> = sequence {
        when(val result=tryAsn1()) {
            is Asn1ParseResult.Failure -> error(buildString {result.message.points().forEach {appendCodePoint(it)}})
            is Asn1ParseResult.Success -> yieldAll(result.value)
        }
    }
    companion object {
        fun deferred(oid: () -> Asn1Oid,critical: () -> Boolean,content: () -> ByteArray)=X509ExtensionRaw(lazy {oid().toList()},lazy(critical),lazy {content().copyOf()})
    }
}
class X509Extensions private constructor(private val stored: Asn1Maybe<List<X509ExtensionRaw>>) {
    constructor(values: List<X509ExtensionRaw>?): this(if(values==null) Asn1Maybe.None else Asn1Maybe.Some(values.toList()))
    val isPresent get()=stored is Asn1Maybe.Some
    val values: List<X509ExtensionRaw>? get()=when(stored) {Asn1Maybe.None -> null;is Asn1Maybe.Some -> stored.value.toList()}
    override fun equals(other: Any?): Boolean {
        if(other !is X509Extensions || isPresent!=other.isPresent) return false
        if(!isPresent) return true
        val a=values!!;val b=other.values!!
        for(i in 0..<minOf(a.size,b.size)) if(a[i]!=b[i]) return false
        return a.size==b.size
    }
    override fun hashCode()=values?.hashCode() ?: 0
    override fun toString()=if(isPresent) "Extensions (Just ${values!!.joinToString(",","[","]")})" else "Extensions Nothing"
    companion object { fun presentDeferred(values: () -> List<X509ExtensionRaw>)=X509Extensions(Asn1Maybe.Some.deferred {values().toList()}) }
}
object X509ExtensionRawCodec: Asn1Object<X509ExtensionRaw> {
    private val seq=Asn1ConstructionType.Sequence
    override fun toAsn1(value: X509ExtensionRaw): Asn1StreamBuilder = {tail -> toAsn1Stream(value,tail.toList().asSequence()).toList()}
    fun toAsn1Stream(value: X509ExtensionRaw,tail: Sequence<Asn1Value> = emptySequence()): Sequence<Asn1Value> = sequence {
        yield(Asn1Value.Start(seq));yield(Asn1Value.Oid.deferred {value.oid})
        if(value.critical) yield(Asn1Value.BooleanValue(true))
        yield(Asn1Value.OctetString.deferred {value.content});yield(Asn1Value.End(seq));yieldAll(tail)
    }
    override fun fromAsn1(values: List<Asn1Value>): Asn1ParseResult<Asn1Parsed<X509ExtensionRaw>> {
        val owned=values.toList()
        var shown=owned
        if(owned.getOrNull(0)==Asn1Value.Start(seq) && owned.getOrNull(1) is Asn1Value.Oid) {
            shown=owned.drop(2)
            val flag=shown.getOrNull(0) as? Asn1Value.BooleanValue
            val at=if(flag==null) 0 else 1
            val octets=shown.getOrNull(at) as? Asn1Value.OctetString
            if(octets!=null && shown.getOrNull(at+1)==Asn1Value.End(seq)) {
                return Asn1ParseResult.Success(Asn1Parsed.deferred({X509ExtensionRaw.deferred({(owned[1] as Asn1Value.Oid).arcs},{flag?.value ?: false},{octets.bytes})},owned.drop(2+at+2)))
            }
        }
        return Asn1ParseResult.Failure.deferred {CharacterStringChars.fromString("fromASN1: X509.ExtensionRaw: unknown format:${shown.joinToString(",","[","]")}")}
    }
}
object X509ExtensionsCodec: Asn1Object<X509Extensions> {
    private val parser=Asn1Parser.onNextContainerMaybe(Asn1ConstructionType.Sequence,Asn1Parser.getMany(Asn1Parser.getObject(X509ExtensionRawCodec))).mapLazy { result ->
        when(val maybe=result.value) {Asn1Maybe.None -> X509Extensions(null);is Asn1Maybe.Some -> X509Extensions.presentDeferred {maybe.value}}
    }
    override fun fromAsn1(values: List<Asn1Value>)=parser.runState(values)
    override fun toAsn1(value: X509Extensions): Asn1StreamBuilder = {tail -> toAsn1Stream(value,tail.toList().asSequence()).toList()}
    fun toAsn1Stream(value: X509Extensions,tail: Sequence<Asn1Value> = emptySequence()): Sequence<Asn1Value> = sequence {
        if(value.isPresent) {
            yield(Asn1Value.Start(Asn1ConstructionType.Sequence))
            for(entry in value.values!!) yieldAll(X509ExtensionRawCodec.toAsn1Stream(entry))
            yield(Asn1Value.End(Asn1ConstructionType.Sequence))
        }
        yieldAll(tail)
    }
}
