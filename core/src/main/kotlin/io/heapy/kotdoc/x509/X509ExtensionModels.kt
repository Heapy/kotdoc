// Data.X509.Ext, crypton-x509 1.9.2; Vincent Hanquez.
// Complete notice: licenses/crypton-x509-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import java.math.BigInteger

internal class X509Values<A>(source: () -> Sequence<Lazy<A>>) {
    private val iterator by lazy {source().iterator()}
    private val cache=mutableListOf<Lazy<A>>();private var finished=false
    @Synchronized private fun at(index: Int): Lazy<A>? {
        while(cache.size<=index && !finished) {if(iterator.hasNext()) cache.add(iterator.next()) else finished=true}
        return cache.getOrNull(index)
    }
    fun cells(): Sequence<Lazy<A>> = sequence {var n=0;while(true) yield(at(n++) ?: break)}
    fun sequence()=cells().map {it.value}
    fun list()=sequence().toList()
    companion object {fun <A> owned(values: List<A>): X509Values<A> {val copy=values.toList();return X509Values {copy.asSequence().map(::lazyOf)}}}
}
internal fun x509Quote(bytes: ByteArray)=HaskellString.quoteCodePoints(bytes.map {it.toInt() and 255}.toIntArray())
internal fun x509Arg(n: BigInteger)=if(n.signum()<0) "($n)" else "$n"
internal fun x509MaybeInt(n: BigInteger?)=n?.let {"(Just ${x509Arg(it)})"} ?: "Nothing"
internal fun <A> x509List(values: Sequence<A>)=values.joinToString(",","[","]")
internal fun <A> x509ListEq(a: Sequence<A>,b: Sequence<A>): Boolean {
    val x=a.iterator();val y=b.iterator()
    while(x.hasNext() && y.hasNext()) if(x.next()!=y.next()) return false
    return !x.hasNext() && !y.hasNext()
}
internal fun <A: Comparable<A>> x509ListCompare(a: Sequence<A>,b: Sequence<A>): Int {
    val x=a.iterator();val y=b.iterator()
    while(x.hasNext() && y.hasNext()) {val r=x.next().compareTo(y.next());if(r!=0) return r}
    return if(x.hasNext()) 1 else if(y.hasNext()) -1 else 0
}
enum class X509ExtensionKind {BasicConstraints,KeyUsage,ExtendedKeyUsage,SubjectKeyId,SubjectAltName,AuthorityKeyId,CrlDistributionPoints,NameConstraints,NetscapeComment}
sealed class X509TypedExtension(val kind: X509ExtensionKind)
data class X509BasicConstraints(val authority: Boolean,val pathLength: BigInteger?): X509TypedExtension(X509ExtensionKind.BasicConstraints) {
    override fun toString()="ExtBasicConstraints ${if(authority) "True" else "False"} ${x509MaybeInt(pathLength)}"
}
enum class X509KeyUsageFlag {
    KeyUsage_digitalSignature,KeyUsage_nonRepudiation,KeyUsage_keyEncipherment,KeyUsage_dataEncipherment,KeyUsage_keyAgreement,KeyUsage_keyCertSign,KeyUsage_cRLSign,KeyUsage_encipherOnly,KeyUsage_decipherOnly;
    companion object {fun fromOrdinal(n: Long)=entries.getOrNull(n.toInt()).takeIf {n in 0..8} ?: error("Enum.toEnum{ExtKeyUsageFlag}: tag ($n) is outside of enumeration's range (0,8)")}
}
class X509KeyUsage private constructor(private val stored: X509Values<X509KeyUsageFlag>): X509TypedExtension(X509ExtensionKind.KeyUsage) {
    constructor(flags: List<X509KeyUsageFlag>): this(X509Values.owned(flags))
    val flags get()=stored.list()
    fun flagCells()=stored.cells()
    override fun equals(other: Any?)=other is X509KeyUsage && x509ListEq(stored.sequence(),other.stored.sequence())
    override fun hashCode()=flags.hashCode()
    override fun toString()="ExtKeyUsage ${x509List(stored.sequence())}"
    companion object {internal fun deferred(flags: () -> Sequence<Lazy<X509KeyUsageFlag>>)=X509KeyUsage(X509Values(flags))}
}
sealed class X509KeyUsagePurpose: Comparable<X509KeyUsagePurpose> {
    data object ServerAuth: X509KeyUsagePurpose();data object ClientAuth: X509KeyUsagePurpose();data object CodeSigning: X509KeyUsagePurpose();data object EmailProtection: X509KeyUsagePurpose();data object TimeStamping: X509KeyUsagePurpose();data object OCSPSigning: X509KeyUsagePurpose()
    class Unknown(oid: Asn1Oid): X509KeyUsagePurpose() {private val saved=oid.toList();val oid get()=saved.toList();override fun equals(other: Any?)=other is Unknown && saved==other.saved;override fun hashCode()=saved.hashCode()}
    internal val order get()=when(this) {ServerAuth->0;ClientAuth->1;CodeSigning->2;EmailProtection->3;TimeStamping->4;OCSPSigning->5;is Unknown->6}
    override fun compareTo(other: X509KeyUsagePurpose): Int {val n=order.compareTo(other.order);return if(n==0 && this is Unknown && other is Unknown) x509ListCompare(oid.asSequence(),other.oid.asSequence()) else n}
    final override fun toString()="KeyUsagePurpose_"+when(this) {ServerAuth->"ServerAuth";ClientAuth->"ClientAuth";CodeSigning->"CodeSigning";EmailProtection->"EmailProtection";TimeStamping->"TimeStamping";OCSPSigning->"OCSPSigning";is Unknown->"Unknown ${x509List(oid.asSequence())}"}
}
class X509ExtendedKeyUsage private constructor(private val stored: X509Values<X509KeyUsagePurpose>): X509TypedExtension(X509ExtensionKind.ExtendedKeyUsage) {
    constructor(purposes: List<X509KeyUsagePurpose>): this(X509Values.owned(purposes))
    val purposes get()=stored.list()
    fun purposeCells()=stored.cells()
    override fun equals(other: Any?)=other is X509ExtendedKeyUsage && x509ListEq(stored.sequence(),other.stored.sequence())
    override fun hashCode()=purposes.hashCode()
    override fun toString()="ExtExtendedKeyUsage ${x509List(stored.sequence())}"
    companion object {internal fun deferred(purposes: () -> Sequence<Lazy<X509KeyUsagePurpose>>)=X509ExtendedKeyUsage(X509Values(purposes))}
}
sealed class X509ByteExtension(kind: X509ExtensionKind,private val stored: Lazy<ByteArray>): X509TypedExtension(kind) {
    val bytes get()=stored.value.copyOf()
    override fun equals(other: Any?)=other is X509ByteExtension && kind==other.kind && stored.value.contentEquals(other.stored.value)
    override fun hashCode()=31*kind.hashCode()+stored.value.contentHashCode()
    final override fun toString()="Ext${kind} ${x509Quote(bytes)}"
}
class X509SubjectKeyId private constructor(bytes: Lazy<ByteArray>): X509ByteExtension(X509ExtensionKind.SubjectKeyId,bytes) {
    constructor(bytes: ByteArray): this(lazyOf(bytes.copyOf()))
    companion object {internal fun deferred(bytes: () -> ByteArray)=X509SubjectKeyId(lazy {bytes().copyOf()})}
}
class X509AuthorityKeyId private constructor(bytes: Lazy<ByteArray>): X509ByteExtension(X509ExtensionKind.AuthorityKeyId,bytes) {
    constructor(bytes: ByteArray): this(lazyOf(bytes.copyOf()))
    companion object {internal fun deferred(bytes: () -> ByteArray)=X509AuthorityKeyId(lazy {bytes().copyOf()})}
}
class X509NetscapeComment private constructor(bytes: Lazy<ByteArray>): X509ByteExtension(X509ExtensionKind.NetscapeComment,bytes) {
    constructor(bytes: ByteArray): this(lazyOf(bytes.copyOf()))
    companion object {internal fun deferred(bytes: () -> ByteArray)=X509NetscapeComment(lazy {bytes().copyOf()})}
}
enum class X509TextNameKind(val order: Int,val shown: String) {RFC822(0,"AltNameRFC822"),DNS(1,"AltNameDNS"),URI(3,"AltNameURI"),XMPP(5,"AltNameXMPP"),DNSSRV(6,"AltNameDNSSRV")}
sealed class X509AltName: Comparable<X509AltName> {
    abstract val order: Int
    class Text private constructor(val type: X509TextNameKind,private val stored: X509Values<Int>): X509AltName() {
        constructor(type: X509TextNameKind,text: CharacterString): this(type,X509Values.owned(text.points().toList()))
        override val order get()=type.order
        val text get()=CharacterString(stored.sequence().toList().toIntArray())
        internal fun cells()=stored.cells()
        override fun equals(other: Any?)=other is Text && type==other.type && x509ListEq(stored.sequence(),other.stored.sequence())
        override fun hashCode()=31*type.hashCode()+text.hashCode()
        override fun toString()="${type.shown} ${HaskellString.quoteCodePoints(text.points())}"
        companion object {internal fun deferred(type: X509TextNameKind,text: () -> Sequence<Lazy<Int>>)=Text(type,X509Values(text))}
    }
    class DN(val value: X509DistinguishedName): X509AltName() {override val order=2;override fun equals(other: Any?)=other is DN && value==other.value;override fun hashCode()=value.hashCode();override fun toString()="AltNameDN ($value)"}
    class IP private constructor(private val saved: Lazy<ByteArray>): X509AltName() {
        constructor(bytes: ByteArray): this(lazyOf(bytes.copyOf()))
        val bytes get()=saved.value.copyOf();override val order=4
        override fun equals(other: Any?)=other is IP && saved.value.contentEquals(other.saved.value)
        override fun hashCode()=saved.value.contentHashCode();override fun toString()="AltNameIP ${x509Quote(saved.value)}"
        companion object {internal fun deferred(bytes: () -> ByteArray)=IP(lazy {bytes().copyOf()})}
    }
    override fun compareTo(other: X509AltName): Int {
        val r=order.compareTo(other.order);if(r!=0) return r
        return when {this is Text && other is Text -> x509ListCompare(cells().map {it.value},other.cells().map {it.value});this is DN && other is DN -> value.compareTo(other.value);this is IP && other is IP -> x509ListCompare(bytes.map {it.toInt() and 255}.asSequence(),other.bytes.map {it.toInt() and 255}.asSequence());else -> 0}
    }
}
class X509SubjectAltName private constructor(private val stored: X509Values<X509AltName>): X509TypedExtension(X509ExtensionKind.SubjectAltName),Comparable<X509SubjectAltName> {
    constructor(names: List<X509AltName>): this(X509Values.owned(names))
    val names get()=stored.list()
    internal fun cells()=stored.cells()
    override fun equals(other: Any?)=other is X509SubjectAltName && x509ListEq(stored.sequence(),other.stored.sequence())
    override fun hashCode()=names.hashCode()
    override fun compareTo(other: X509SubjectAltName)=x509ListCompare(stored.sequence(),other.stored.sequence())
    override fun toString()="ExtSubjectAltName ${x509List(stored.sequence())}"
    companion object {internal fun deferred(names: () -> Sequence<Lazy<X509AltName>>)=X509SubjectAltName(X509Values(names))}
}
enum class X509ReasonFlag {Reason_Unused,Reason_KeyCompromise,Reason_CACompromise,Reason_AffiliationChanged,Reason_Superseded,Reason_CessationOfOperation,Reason_CertificateHold,Reason_PrivilegeWithdrawn,Reason_AACompromise;
    companion object {fun fromOrdinal(n: Long)=entries.getOrNull(n.toInt()).takeIf {n in 0..8} ?: error("Enum.toEnum{ReasonFlag}: tag ($n) is outside of enumeration's range (0,8)")}
}
sealed class X509DistributionPoint {
    class FullName(names: List<X509AltName>): X509DistributionPoint() {private val saved=names.toList();val names get()=saved.toList();override fun equals(other: Any?)=other is FullName && x509ListEq(saved.asSequence(),other.saved.asSequence());override fun hashCode()=saved.hashCode();override fun toString()="DistributionPointFullName ${x509List(saved.asSequence())}"}
    class Relative(val name: X509DistinguishedName): X509DistributionPoint() {override fun equals(other: Any?)=other is Relative && name==other.name;override fun hashCode()=name.hashCode();override fun toString()="DistributionNameRelative ($name)"}
}
class X509CrlDistributionPoints(points: List<X509DistributionPoint>): X509TypedExtension(X509ExtensionKind.CrlDistributionPoints) {
    private val saved=points.toList();val points get()=saved.toList()
    override fun equals(other: Any?)=other is X509CrlDistributionPoints && x509ListEq(saved.asSequence(),other.saved.asSequence())
    override fun hashCode()=saved.hashCode();override fun toString()="ExtCrlDistributionPoints ${x509List(saved.asSequence())}"
}
class X509GeneralSubtree(val base: X509AltName,val minimum: BigInteger,val maximum: BigInteger?) {
    override fun equals(other: Any?)=other is X509GeneralSubtree && base==other.base && minimum==other.minimum && maximum==other.maximum
    override fun hashCode()=31*(31*base.hashCode()+minimum.hashCode())+(maximum?.hashCode() ?: 0)
    override fun toString()="GeneralSubtree ($base) ${x509Arg(minimum)} ${x509MaybeInt(maximum)}"
}
class X509NameConstraints(permitted: List<X509GeneralSubtree>,excluded: List<X509GeneralSubtree>): X509TypedExtension(X509ExtensionKind.NameConstraints) {
    private val p=permitted.toList();private val e=excluded.toList();val permitted get()=p.toList();val excluded get()=e.toList()
    override fun equals(other: Any?)=other is X509NameConstraints && x509ListEq(p.asSequence(),other.p.asSequence()) && x509ListEq(e.asSequence(),other.e.asSequence())
    override fun hashCode()=31*p.hashCode()+e.hashCode();override fun toString()="ExtNameConstraints ${x509List(p.asSequence())} ${x509List(e.asSequence())}"
}

fun x509KeyUsageRange(first: X509KeyUsageFlag,next: X509KeyUsageFlag?=null,last: X509KeyUsageFlag?=null): Sequence<X509KeyUsageFlag> = x509EnumRange(first.ordinal,next?.ordinal,last?.ordinal).map {X509KeyUsageFlag.entries[it]}
fun x509ReasonRange(first: X509ReasonFlag,next: X509ReasonFlag?=null,last: X509ReasonFlag?=null): Sequence<X509ReasonFlag> = x509EnumRange(first.ordinal,next?.ordinal,last?.ordinal).map {X509ReasonFlag.entries[it]}
private fun x509EnumRange(first: Int,next: Int?,last: Int?): Sequence<Int> = sequence {
    val step=(next?.minus(first) ?: 1).toLong();val end=last?.toLong() ?: if(step<0) 0 else 8
    var n=first.toLong()
    while(if(step<0) n>=end else n<=end) {yield(n.toInt());n+=step}
}
