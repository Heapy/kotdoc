// Data.X509.CRL, crypton-x509 1.9.2; Vincent Hanquez.
// Complete notice: licenses/crypton-x509-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.time.*
import java.math.BigInteger

class X509RevokedCertificate private constructor(private val serial: Lazy<BigInteger>,private val date: Lazy<HourglassDateTime>,private val exts: Lazy<X509Extensions>) {
    constructor(serialNumber: BigInteger,revocationDate: HourglassDateTime,extensions: X509Extensions): this(lazyOf(serialNumber),lazyOf(revocationDate),lazyOf(extensions))
    val serialNumber get()=serial.value;val revocationDate get()=date.value;val extensions get()=exts.value
    override fun equals(other: Any?)=other is X509RevokedCertificate && serialNumber==other.serialNumber && revocationDate==other.revocationDate && extensions==other.extensions
    override fun hashCode()=31*(31*serialNumber.hashCode()+revocationDate.hashCode())+extensions.hashCode()
    override fun toString()="RevokedCertificate {revokedSerialNumber = $serialNumber, revokedDate = $revocationDate, revokedExtensions = $extensions}"
    companion object {fun deferred(serialNumber: ()->BigInteger,revocationDate: ()->HourglassDateTime,extensions: ()->X509Extensions)=X509RevokedCertificate(lazy(serialNumber),lazy(revocationDate),lazy(extensions))}
}
class X509Crl private constructor(private val v: Lazy<BigInteger>,private val alg: Lazy<X509SignatureAlgorithm>,private val dn: Lazy<X509DistinguishedName>,private val current: Lazy<HourglassDateTime>,private val next: Lazy<Asn1Maybe<HourglassDateTime>>,private val revoked: X509Values<X509RevokedCertificate>,private val exts: Lazy<X509Extensions>) {
    constructor(version: BigInteger,signatureAlgorithm: X509SignatureAlgorithm,issuer: X509DistinguishedName,thisUpdate: HourglassDateTime,nextUpdate: HourglassDateTime?,revokedCertificates: List<X509RevokedCertificate>,extensions: X509Extensions): this(lazyOf(version),lazyOf(signatureAlgorithm),lazyOf(issuer),lazyOf(thisUpdate),lazyOf(if(nextUpdate==null) Asn1Maybe.None else Asn1Maybe.Some(nextUpdate)),X509Values.owned(revokedCertificates),lazyOf(extensions))
    val version get()=v.value;val signatureAlgorithm get()=alg.value;val issuer get()=dn.value;val thisUpdate get()=current.value
    val nextUpdateMaybe get()=next.value
    val nextUpdate get()=when(val n=nextUpdateMaybe) {Asn1Maybe.None->null;is Asn1Maybe.Some->n.value}
    private val revokedView=object: AbstractList<X509RevokedCertificate>() {
        override val size get()=revoked.cells().count()
        override fun isEmpty()=!revoked.cells().iterator().hasNext()
        override fun get(index: Int): X509RevokedCertificate {if(index<0) throw IndexOutOfBoundsException("index: $index");return revoked.cells().drop(index).firstOrNull()?.value ?: throw IndexOutOfBoundsException("index: $index")}
        override fun iterator()=revoked.sequence().iterator()
    }
    val revokedCertificates: List<X509RevokedCertificate> get()=revokedView
    val extensions get()=exts.value
    fun revokedCells()=revoked.cells()
    override fun equals(other: Any?)=other is X509Crl && version==other.version && signatureAlgorithm==other.signatureAlgorithm && issuer==other.issuer && thisUpdate==other.thisUpdate && nextUpdateMaybe==other.nextUpdateMaybe && x509ListEq(revoked.sequence(),other.revoked.sequence()) && extensions==other.extensions
    override fun hashCode()=listOf(version,signatureAlgorithm,issuer,thisUpdate,nextUpdate,revokedCertificates,extensions).hashCode()
    override fun toString()="CRL {crlVersion = $version, crlSignatureAlg = $signatureAlgorithm, crlIssuer = $issuer, crlThisUpdate = $thisUpdate, crlNextUpdate = ${when(val n=nextUpdateMaybe) {Asn1Maybe.None->"Nothing";is Asn1Maybe.Some->"Just (${n.value})"}}, crlRevokedCertificates = ${x509List(revoked.sequence())}, crlExtensions = $extensions}"
    companion object {
        fun deferred(version: ()->BigInteger,signatureAlgorithm: ()->X509SignatureAlgorithm,issuer: ()->X509DistinguishedName,thisUpdate: ()->HourglassDateTime,nextUpdate: ()->Asn1Maybe<HourglassDateTime>,revokedCertificates: ()->Sequence<Lazy<X509RevokedCertificate>>,extensions: ()->X509Extensions)=X509Crl(lazy(version),lazy(signatureAlgorithm),lazy(issuer),lazy(thisUpdate),lazy(nextUpdate),X509Values(revokedCertificates),lazy(extensions))
    }
}
class X509CrlParsed internal constructor(private val saved: Lazy<X509Crl>,private val rest: X509Input) {
    val crl get()=saved.value;val remaining get()=rest.view()
    fun remainingSequence()=rest.sequence()
}
object X509RevokedCertificateCodec: Asn1Object<X509RevokedCertificate> {
    override fun toAsn1(value: X509RevokedCertificate): Asn1StreamBuilder = {tail->toAsn1Stream(value,tail.toList().asSequence()).toList()}
    fun toAsn1Stream(value: X509RevokedCertificate,tail: Sequence<Asn1Value> = emptySequence()): Sequence<Asn1Value> = sequence {
        yield(Asn1Value.Start(x509Seq));yield(Asn1Value.IntVal.deferred {value.serialNumber})
        yield(Asn1Value.Time.deferred({Asn1TimeType.TimeGeneralized},{value.revocationDate},{HourglassTimezoneOffset.UTC}))
        yieldAll(X509ExtensionsCodec.toAsn1Stream(value.extensions));yield(Asn1Value.End(x509Seq));yieldAll(tail)
    }
    override fun fromAsn1(values: List<Asn1Value>)=fromSequence(values.toList().asSequence())
    fun fromSequence(values: Sequence<Asn1Value>): Asn1ParseResult<Asn1Parsed<X509RevokedCertificate>> = when(val r=fromInput(X509Input.from(values))) {
        is Asn1ParseResult.Failure->r
        is Asn1ParseResult.Success->Asn1ParseResult.Success(Asn1Parsed.deferred({r.value.value.value},r.value.rest.view()))
    }
    internal fun fromInput(input: X509Input): Asn1ParseResult<X509Read<X509RevokedCertificate>> = x509Container(input,x509Seq) {s->
        val serial=s[0] ?: return@x509Container x509Failure("empty")
        if(serial !is Asn1Value.IntVal) return@x509Container x509FailureLazy {"missing serial$serial"}
        val date=s[1] ?: return@x509Container x509Failure("empty")
        if(date !is Asn1Value.Time) return@x509Container x509Failure("bad revocation date")
        when(val e=X509ExtensionsCodec.fromInput(s.drop(2))) {
            is Asn1ParseResult.Failure->e
            is Asn1ParseResult.Success->x509Read({X509RevokedCertificate.deferred({serial.value},{date.value},{e.value.value.value})},e.value.rest)
        }
    }
}
object X509CrlCodec: Asn1Object<X509Crl> {
    override fun toAsn1(value: X509Crl): Asn1StreamBuilder = {tail->toAsn1Stream(value,tail.toList().asSequence()).toList()}
    fun toAsn1Stream(value: X509Crl,tail: Sequence<Asn1Value> = emptySequence()): Sequence<Asn1Value> = sequence {
        yield(Asn1Value.IntVal.deferred {value.version})
        yieldAll(X509SignatureAlgorithmCodec.toAsn1Stream(value.signatureAlgorithm))
        yield(Asn1Value.Start(x509Seq));yieldAll(X509DistinguishedNameCodec.toAsn1Stream(value.issuer).drop(1))
        yield(Asn1Value.Time.deferred({Asn1TimeType.TimeGeneralized},{value.thisUpdate},{HourglassTimezoneOffset.UTC}))
        when(val next=value.nextUpdateMaybe) {Asn1Maybe.None->Unit;is Asn1Maybe.Some->yield(Asn1Value.Time.deferred({Asn1TimeType.TimeGeneralized},{next.value},{HourglassTimezoneOffset.UTC}))}
        val cells=value.revokedCells().iterator()
        if(cells.hasNext()) {
            yield(Asn1Value.Start(x509Seq))
            do {yieldAll(X509RevokedCertificateCodec.toAsn1Stream(cells.next().value))} while(cells.hasNext())
            yield(Asn1Value.End(x509Seq))
        }
        val exts=value.extensions
        if(exts.isPresent) {yield(Asn1Value.Start(x509Context(0)));yieldAll(X509ExtensionsCodec.toAsn1Stream(exts));yield(Asn1Value.End(x509Context(0)))}
        yieldAll(tail)
    }
    /** Encodes the bare unsigned field stream; Signed supplies outer containers. */
    fun encodeBytes(value: X509Crl)=Asn1BinaryEncoding().encodeDerSequence(toAsn1Stream(value))
    override fun fromAsn1(values: List<Asn1Value>): Asn1ParseResult<Asn1Parsed<X509Crl>> = when(val r=fromSequence(values.toList().asSequence())) {
        is Asn1ParseResult.Failure->r
        is Asn1ParseResult.Success->Asn1ParseResult.Success(Asn1Parsed.deferred({r.value.crl},r.value.remaining))
    }
    fun fromSequence(values: Sequence<Asn1Value>): Asn1ParseResult<X509CrlParsed> = when(val r=fromInput(X509Input.from(values))) {
        is Asn1ParseResult.Failure->r
        is Asn1ParseResult.Success->Asn1ParseResult.Success(X509CrlParsed(r.value.value,r.value.rest))
    }
    internal fun fromInput(input: X509Input): Asn1ParseResult<X509Read<X509Crl>> {
        val version=input[0] ?: return x509Failure("empty")
        if(version !is Asn1Value.IntVal) return x509Failure("unexpected type for version")
        val signature=X509SignatureAlgorithmCodec.fromInput(input.drop(1))
        if(signature is Asn1ParseResult.Failure) return signature
        signature as Asn1ParseResult.Success
        val issuer=x509DnFromInput(signature.value.rest)
        if(issuer is Asn1ParseResult.Failure) return issuer
        issuer as Asn1ParseResult.Success
        var rest=issuer.value.rest
        val current=rest[0] ?: return x509Failure("empty")
        if(current !is Asn1Value.Time) return x509Failure("bad this update format, expecting time")
        rest=rest.drop(1)
        val next=rest[0] as? Asn1Value.Time
        val nextMaybe: Asn1Maybe<HourglassDateTime> = if(next==null) Asn1Maybe.None else Asn1Maybe.Some.deferred {next.value}
        if(next!=null) rest=rest.drop(1)
        var revoked: Lazy<List<Lazy<X509RevokedCertificate>>> = lazyOf(emptyList())
        if(rest[0]==Asn1Value.Start(x509Seq)) {
            val r=x509Container(rest,x509Seq) {x509Many(it,X509RevokedCertificateCodec::fromInput)}
            if(r is Asn1ParseResult.Failure) return r
            r as Asn1ParseResult.Success;revoked=r.value.value;rest=r.value.rest
        }
        var exts: Lazy<X509Extensions> = lazy {X509Extensions(null)}
        if(rest[0]==Asn1Value.Start(x509Context(0))) {
            val r=x509Container(rest,x509Context(0),X509ExtensionsCodec::fromInput)
            if(r is Asn1ParseResult.Failure) return r
            r as Asn1ParseResult.Success;exts=r.value.value;rest=r.value.rest
        }
        return x509Read({X509Crl.deferred({version.value},{signature.value.value.value},{issuer.value.value.value},{current.value},{nextMaybe},{revoked.value.asSequence()},{exts.value})},rest)
    }
}
