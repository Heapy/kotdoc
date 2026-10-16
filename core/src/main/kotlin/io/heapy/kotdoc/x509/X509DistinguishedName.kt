// Data.X509.DistinguishedName / Internal.asn1Container, crypton-x509 1.9.2.
// Vincent Hanquez; complete notice: licenses/crypton-x509-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import java.math.BigInteger

enum class X509DnElement: Asn1Oidable {
    DnCommonName, DnCountry, DnOrganization, DnOrganizationUnit, DnEmailAddress;
    override fun getObjectId(): Asn1Oid = when(this) {
        DnCommonName -> listOf(2L,5,4,3); DnCountry -> listOf(2L,5,4,6)
        DnOrganization -> listOf(2L,5,4,10); DnOrganizationUnit -> listOf(2L,5,4,11)
        DnEmailAddress -> listOf(1L,2,840,113549,1,9,1)
    }.map(BigInteger::valueOf)
}
class X509DnAttribute private constructor(private val delayedOid: Lazy<Asn1Oid>,val value: Asn1CharacterString) {
    constructor(oid: Asn1Oid,value: Asn1CharacterString): this(lazyOf(oid.toList()),value)
    val oid get()=delayedOid.value.toList()
    override fun equals(other: Any?)=other is X509DnAttribute && oid==other.oid && value==other.value
    override fun hashCode()=31*oid.hashCode()+value.hashCode()
    override fun toString()="(${oid.joinToString(",","[","]")},$value)"
    companion object { fun deferred(oid: () -> Asn1Oid,value: Asn1CharacterString)=X509DnAttribute(lazy {oid().toList()},value) }
}
class X509DistinguishedName private constructor(private val delayed: Lazy<List<X509DnAttribute>>): Comparable<X509DistinguishedName> {
    constructor(elements: List<X509DnAttribute>): this(lazyOf(elements.toList()))
    val elements get()=delayed.value.toList()
    fun getElement(element: X509DnElement): Asn1CharacterString? {
        val oid=element.getObjectId()
        return delayed.value.firstOrNull {it.oid==oid}?.value
    }
    operator fun plus(other: X509DistinguishedName)=deferred {delayed.value+other.delayed.value}
    override fun equals(other: Any?): Boolean {
        if(other !is X509DistinguishedName) return false
        val a=delayed.value;val b=other.delayed.value
        for(i in 0..<minOf(a.size,b.size)) if(a[i]!=b[i]) return false
        return a.size==b.size
    }
    override fun hashCode()=delayed.value.hashCode()
    override fun compareTo(other: X509DistinguishedName): Int {
        val a=delayed.value;val b=other.delayed.value
        for(i in 0..<minOf(a.size,b.size)) {
            val x=a[i].oid;val y=b[i].oid
            var result=0
            for(j in 0..<minOf(x.size,y.size)) {result=x[j].compareTo(y[j]);if(result!=0) break}
            if(result==0) result=x.size.compareTo(y.size)
            if(result==0) result=a[i].value.compareTo(b[i].value)
            if(result!=0) return result
        }
        return a.size.compareTo(b.size)
    }
    override fun toString()="DistinguishedName {getDistinguishedElements = ${delayed.value.joinToString(",","[","]")}}"
    companion object {
        val Empty=X509DistinguishedName(emptyList())
        fun deferred(elements: () -> List<X509DnAttribute>)=X509DistinguishedName(lazy {elements().toList()})
    }
}
data class X509DistinguishedNameInner(val value: X509DistinguishedName) {
    override fun toString()="DistinguishedNameInner ($value)"
}
object X509Asn1 {
    fun container(kind: Asn1ConstructionType,values: List<Asn1Value>): List<Asn1Value> = listOf(Asn1Value.Start(kind))+values+Asn1Value.End(kind)
    fun containerStream(kind: Asn1ConstructionType,values: Sequence<Asn1Value>): Sequence<Asn1Value> = sequence {yield(Asn1Value.Start(kind));yieldAll(values);yield(Asn1Value.End(kind))}
}
private val parseOneDn: Asn1Parser<List<X509DnAttribute>> = Asn1Parser.onNextContainer(Asn1ConstructionType.Set,
    Asn1Parser.getMany(Asn1Parser.getNextContainer(Asn1ConstructionType.Sequence).flatMap { s ->
        if(s.size==2 && s[0] is Asn1Value.Oid && s[1] is Asn1Value.StringValue) {
            Asn1Parser.pureLazy {X509DnAttribute.deferred({(s[0] as Asn1Value.Oid).arcs},(s[1] as Asn1Value.StringValue).value)}
        } else Asn1Parser.failLazy {"expecting [OID,String] got ${s.joinToString(",","[","]")}"}
    }))
private val parseDnInner: Asn1Parser<X509DistinguishedName> = Asn1Parser.getMany(parseOneDn).mapLazy { groups -> X509DistinguishedName.deferred {groups.value.flatten()} }
object X509DistinguishedNameCodec: Asn1Object<X509DistinguishedName> {
    override fun fromAsn1(values: List<Asn1Value>)=Asn1Parser.onNextContainer(Asn1ConstructionType.Sequence,parseDnInner).runState(values)
    override fun toAsn1(value: X509DistinguishedName): Asn1StreamBuilder = {tail -> toAsn1Stream(value,tail.toList().asSequence()).toList()}
    fun toAsn1Stream(value: X509DistinguishedName,tail: Sequence<Asn1Value> = emptySequence()): Sequence<Asn1Value> = sequence {
        yieldAll(X509Asn1.containerStream(Asn1ConstructionType.Sequence,X509DistinguishedNameInnerCodec.toAsn1Stream(X509DistinguishedNameInner(value))))
        yieldAll(tail)
    }
}
object X509DistinguishedNameInnerCodec: Asn1Object<X509DistinguishedNameInner> {
    override fun fromAsn1(values: List<Asn1Value>)=parseDnInner.map(::X509DistinguishedNameInner).runState(values)
    override fun toAsn1(value: X509DistinguishedNameInner): Asn1StreamBuilder = {tail -> toAsn1Stream(value,tail.toList().asSequence()).toList()}
    fun toAsn1Stream(value: X509DistinguishedNameInner,tail: Sequence<Asn1Value> = emptySequence()): Sequence<Asn1Value> = sequence {
        for(entry in value.value.elements) {
            yield(Asn1Value.Start(Asn1ConstructionType.Set));yield(Asn1Value.Start(Asn1ConstructionType.Sequence))
            yield(Asn1Value.Oid.deferred {entry.oid});yield(Asn1Value.StringValue(entry.value))
            yield(Asn1Value.End(Asn1ConstructionType.Sequence));yield(Asn1Value.End(Asn1ConstructionType.Set))
        }
        yieldAll(tail)
    }
}
