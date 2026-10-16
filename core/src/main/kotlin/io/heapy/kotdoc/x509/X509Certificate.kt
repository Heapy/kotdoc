// Data.X509.Cert, crypton-x509 1.9.2; Vincent Hanquez.
// Complete notice: licenses/crypton-x509-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.time.*
import java.math.BigInteger

class X509CertificateValidity private constructor(private val first: Lazy<HourglassDateTime>,private val second: Lazy<HourglassDateTime>) {
    constructor(notBefore: HourglassDateTime,notAfter: HourglassDateTime): this(lazyOf(notBefore),lazyOf(notAfter))
    val notBefore get()=first.value;val notAfter get()=second.value
    override fun equals(other: Any?)=other is X509CertificateValidity && notBefore==other.notBefore && notAfter==other.notAfter
    override fun hashCode()=31*notBefore.hashCode()+notAfter.hashCode()
    override fun toString()="($notBefore,$notAfter)"
    companion object {fun deferred(notBefore: ()->HourglassDateTime,notAfter: ()->HourglassDateTime)=X509CertificateValidity(lazy(notBefore),lazy(notAfter))}
}
class X509Certificate private constructor(private val v: Lazy<Long>,private val s: Lazy<BigInteger>,private val alg: Lazy<X509SignatureAlgorithm>,private val issuer: Lazy<X509DistinguishedName>,private val dates: Lazy<X509CertificateValidity>,private val subject: Lazy<X509DistinguishedName>,private val pk: Lazy<X509PublicKey>,private val exts: Lazy<X509Extensions>) {
    constructor(version: Long,serial: BigInteger,signatureAlgorithm: X509SignatureAlgorithm,issuerDN: X509DistinguishedName,validity: X509CertificateValidity,subjectDN: X509DistinguishedName,publicKey: X509PublicKey,extensions: X509Extensions): this(lazyOf(version),lazyOf(serial),lazyOf(signatureAlgorithm),lazyOf(issuerDN),lazyOf(validity),lazyOf(subjectDN),lazyOf(publicKey),lazyOf(extensions))
    val version get()=v.value;val serial get()=s.value;val signatureAlgorithm get()=alg.value;val issuerDN get()=issuer.value;val validity get()=dates.value;val subjectDN get()=subject.value;val publicKey get()=pk.value;val extensions get()=exts.value
    override fun equals(other: Any?)=other is X509Certificate && version==other.version && serial==other.serial && signatureAlgorithm==other.signatureAlgorithm && issuerDN==other.issuerDN && validity==other.validity && subjectDN==other.subjectDN && publicKey==other.publicKey && extensions==other.extensions
    override fun hashCode()=listOf(version,serial,signatureAlgorithm,issuerDN,validity,subjectDN,publicKey,extensions).hashCode()
    override fun toString()="Certificate {certVersion = $version, certSerial = $serial, certSignatureAlg = $signatureAlgorithm, certIssuerDN = $issuerDN, certValidity = $validity, certSubjectDN = $subjectDN, certPubKey = $publicKey, certExtensions = $extensions}"
    companion object {
        fun deferred(version: ()->Long,serial: ()->BigInteger,signatureAlgorithm: ()->X509SignatureAlgorithm,issuerDN: ()->X509DistinguishedName,validity: ()->X509CertificateValidity,subjectDN: ()->X509DistinguishedName,publicKey: ()->X509PublicKey,extensions: ()->X509Extensions)=X509Certificate(lazy(version),lazy(serial),lazy(signatureAlgorithm),lazy(issuerDN),lazy(validity),lazy(subjectDN),lazy(publicKey),lazy(extensions))
    }
}
class X509CertificateParsed internal constructor(private val stored: Lazy<X509Certificate>,private val rest: X509Input) {
    val certificate get()=stored.value
    val remaining get()=rest.view()
    fun remainingSequence()=rest.sequence()
}
object X509CertificateCodec: Asn1Object<X509Certificate> {
    private val threshold=HourglassDateTime(HourglassDate(2050,HourglassMonth.January,1),HourglassTimeOfDay(HourglassHours(0),HourglassMinutes(0),HourglassSeconds(0),HourglassNanoSeconds(0)))
    private fun timeType(t: HourglassDateTime)=if(t>=threshold) Asn1TimeType.TimeGeneralized else Asn1TimeType.TimeUTC
    override fun toAsn1(value: X509Certificate): Asn1StreamBuilder = {tail -> toAsn1Stream(value,tail.toList().asSequence()).toList()}
    fun toAsn1Stream(value: X509Certificate,tail: Sequence<Asn1Value> = emptySequence()): Sequence<Asn1Value> = sequence {
        yield(Asn1Value.Start(x509Context(0)));yield(Asn1Value.IntVal.deferred {BigInteger.valueOf(value.version)});yield(Asn1Value.End(x509Context(0)))
        yield(Asn1Value.IntVal.deferred {value.serial})
        yieldAll(X509SignatureAlgorithmCodec.toAsn1Stream(value.signatureAlgorithm))
        yield(Asn1Value.Start(x509Seq));yieldAll(X509DistinguishedNameCodec.toAsn1Stream(value.issuerDN).drop(1))
        yield(Asn1Value.Start(x509Seq))
        yield(Asn1Value.Time.deferred({timeType(value.validity.notBefore)},{value.validity.notBefore},{HourglassTimezoneOffset.UTC}))
        yield(Asn1Value.Time.deferred({timeType(value.validity.notAfter)},{value.validity.notAfter},{HourglassTimezoneOffset.UTC}))
        yield(Asn1Value.End(x509Seq))
        yield(Asn1Value.Start(x509Seq));yieldAll(X509DistinguishedNameCodec.toAsn1Stream(value.subjectDN).drop(1))
        yield(Asn1Value.Start(x509Seq));yieldAll(X509PublicKeyCodec.toAsn1Stream(value.publicKey).drop(1))
        val extensions=value.extensions
        if(extensions.isPresent) {yield(Asn1Value.Start(x509Context(3)));yieldAll(X509ExtensionsCodec.toAsn1Stream(extensions));yield(Asn1Value.End(x509Context(3)))}
        yieldAll(tail)
    }
    /** Bare header fields; the Signed layer supplies the enclosing containers. */
    fun encodeBytes(value: X509Certificate)=Asn1BinaryEncoding().encodeDerSequence(toAsn1Stream(value))
    override fun fromAsn1(values: List<Asn1Value>): Asn1ParseResult<Asn1Parsed<X509Certificate>> = when(val r=fromSequence(values.toList().asSequence())) {
        is Asn1ParseResult.Failure -> r
        is Asn1ParseResult.Success -> Asn1ParseResult.Success(Asn1Parsed.deferred({r.value.certificate},r.value.remaining))
    }
    fun fromSequence(values: Sequence<Asn1Value>): Asn1ParseResult<X509CertificateParsed> = when(val r=fromInput(X509Input.from(values))) {
        is Asn1ParseResult.Failure -> r
        is Asn1ParseResult.Success -> Asn1ParseResult.Success(X509CertificateParsed(r.value.value,r.value.rest))
    }
    internal fun fromInput(input: X509Input): Asn1ParseResult<X509Read<X509Certificate>> {
        var rest=input
        var version: Lazy<Long> = lazyOf(0)
        if(rest[0]==Asn1Value.Start(x509Context(0))) {
            val v=x509Container(rest,x509Context(0)) {s ->
                val first=s[0] ?: return@x509Container x509Failure("empty")
                if(first is Asn1Value.IntVal) x509Read({first.value.toLong()},s.drop(1)) else x509Failure("unexpected type for version")
            }
            if(v is Asn1ParseResult.Failure) return v
            v as Asn1ParseResult.Success;version=v.value.value;rest=v.value.rest
        }
        val serial=rest[0] ?: return x509Failure("empty")
        if(serial !is Asn1Value.IntVal) return x509FailureLazy {"missing serial$serial"}
        rest=rest.drop(1)
        val signature=X509SignatureAlgorithmCodec.fromInput(rest)
        if(signature is Asn1ParseResult.Failure) return signature
        signature as Asn1ParseResult.Success;rest=signature.value.rest
        val issuer=x509DnFromInput(rest)
        if(issuer is Asn1ParseResult.Failure) return issuer
        issuer as Asn1ParseResult.Success;rest=issuer.value.rest
        if(rest[0]==null) return x509Failure("empty")
        if(rest[0]!=Asn1Value.Start(x509Seq)) return x509Failure("not an expected container")
        val (times,afterTimes)=rest.drop(1).split()
        val first=times[0] as? Asn1Value.Time
        val second=if(first!=null) times[1] as? Asn1Value.Time else null
        if(first==null || second==null || times[2]!=null) return x509Failure("bad validity format")
        val validity=lazy {X509CertificateValidity.deferred({first.value},{second.value})};rest=afterTimes
        val subject=x509DnFromInput(rest)
        if(subject is Asn1ParseResult.Failure) return subject
        subject as Asn1ParseResult.Success;rest=subject.value.rest
        val publicKey=X509PublicKeyCodec.fromInput(rest)
        if(publicKey is Asn1ParseResult.Failure) return publicKey
        publicKey as Asn1ParseResult.Success;rest=publicKey.value.rest
        var extensions: Lazy<X509Extensions> = lazy {X509Extensions(null)}
        if(rest[0]==Asn1Value.Start(x509Context(3))) {
            val parsed=x509Container(rest,x509Context(3),X509ExtensionsCodec::fromInput)
            if(parsed is Asn1ParseResult.Failure) return parsed
            parsed as Asn1ParseResult.Success;extensions=parsed.value.value;rest=parsed.value.rest
        }
        return x509Read({X509Certificate.deferred({version.value},{serial.value},{signature.value.value.value},{issuer.value.value.value},{validity.value},{subject.value.value.value},{publicKey.value.value.value},{extensions.value})},rest)
    }
}
