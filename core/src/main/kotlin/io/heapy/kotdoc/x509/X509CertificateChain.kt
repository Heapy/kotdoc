// Data.X509.CertificateChain / Data.X509, crypton-x509 1.9.2; Vincent Hanquez.
// Complete notice: licenses/crypton-x509-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.layout.CharacterString
import java.security.MessageDigest

typealias X509SignedCertificate = X509SignedExact<X509Certificate>
typealias X509SignedCrl = X509SignedExact<X509Crl>

private fun <A> chainView(values: X509Values<A>, copy: (A) -> A): List<A> = object : AbstractList<A>() {
    override val size get() = values.cells().count()
    override fun isEmpty() = !values.cells().iterator().hasNext()
    override fun get(index: Int): A {
        if (index < 0) throw IndexOutOfBoundsException("index: $index")
        return copy(values.cells().drop(index).firstOrNull()?.value ?: throw IndexOutOfBoundsException("index: $index"))
    }
    override fun iterator() = values.sequence().map(copy).iterator()
}
private fun <A> chainEquals(first: X509Values<A>, second: X509Values<A>, equal: (A,A) -> Boolean): Boolean {
    val a = first.sequence().iterator(); val b = second.sequence().iterator()
    while (true) {
        // The pinned derived newtype Eq demands the right spine first;
        // element equality still demands the left value before the right.
        val right = b.hasNext(); val left = a.hasNext()
        if (!right || !left) return right == left
        if (!equal(a.next(),b.next())) return false
    }
}
class X509CertificateChain private constructor(private val values: X509Values<X509SignedCertificate>) {
    constructor(certificates: List<X509SignedCertificate>) : this(X509Values.owned(certificates))
    val certificates = chainView(values) { it }
    fun certificateCells() = values.cells()
    override fun equals(other: Any?) = other is X509CertificateChain && chainEquals(values, other.values) { a,b -> a == b }
    override fun hashCode() = certificates.hashCode()
    override fun toString() = "CertificateChain ${x509List(values.sequence())}"
    companion object { fun deferred(certificates: () -> Sequence<Lazy<X509SignedCertificate>>) = X509CertificateChain(X509Values(certificates)) }
}
class X509CertificateChainRaw private constructor(private val values: X509Values<ByteArray>) {
    constructor(certificates: List<ByteArray>) : this(X509Values.owned(certificates.map { it.copyOf() }))
    val certificates = chainView(values) { it.copyOf() }
    fun rawCells(): Sequence<Lazy<ByteArray>> = values.cells().map { cell -> lazy { cell.value.copyOf() } }
    override fun equals(other: Any?): Boolean {
        if (other !is X509CertificateChainRaw) return false
        return chainEquals(values,other.values) { a,b -> a.contentEquals(b) }
    }
    override fun hashCode() = values.sequence().fold(1) { h, b -> 31 * h + b.contentHashCode() }
    override fun toString() = "CertificateChainRaw ${x509List(values.sequence().map(::x509Quote))}"
    companion object {
        fun deferred(certificates: () -> Sequence<Lazy<ByteArray>>) = X509CertificateChainRaw(X509Values {
            certificates().map { cell -> lazy { cell.value.copyOf() } }
        })
    }
}
sealed interface X509CertificateChainResult {
    class Success internal constructor(private val saved: Lazy<X509CertificateChain>) : X509CertificateChainResult { val chain get() = saved.value }
    class Failure internal constructor(val index: Long, private val saved: Lazy<CharacterString>) : X509CertificateChainResult { val message get() = saved.value }
}
class X509CertificateChainCodec(private val certificate: X509SignedCodec<X509Certificate> = X509SignedCodec(X509CertificateCodec)) {
    fun encodeCertificateChain(chain: X509CertificateChain) = X509CertificateChainRaw.deferred {
        chain.certificateCells().map { cell -> lazy { cell.value.encodeSignedObject() } }
    }
    fun decodeCertificateChain(raw: X509CertificateChainRaw): X509CertificateChainResult {
        val certificates = mutableListOf<Lazy<X509SignedCertificate>>()
        var index = 0L
        for (cell in raw.rawCells()) {
            when (val parsed = certificate.decodeSignedObject(cell.value)) {
                is Asn1ParseResult.Failure -> return X509CertificateChainResult.Failure(index, lazy { parsed.message })
                is Asn1ParseResult.Success -> certificates.add(lazy { parsed.value })
            }
            index++
        }
        val owned = certificates.toList()
        return X509CertificateChainResult.Success(lazy { X509CertificateChain.deferred { owned.asSequence() } })
    }
}
object X509 {
    fun getCertificate(value: X509SignedCertificate) = value.signed.signedObject
    fun getCrl(value: X509SignedCrl) = value.signed.signedObject
    fun decodeSignedCertificate(bytes: ByteArray) = X509SignedCodec(X509CertificateCodec).decodeSignedObject(bytes)
    fun decodeSignedCrl(bytes: ByteArray) = X509SignedCodec(X509CrlCodec).decodeSignedObject(bytes)
    fun hashDn(value: X509DistinguishedName): ByteArray {
        val lower = X509DistinguishedName.deferred {
            value.elements.map { entry -> X509DnAttribute.deferred({ entry.oid }, Asn1CharacterString.deferred(Asn1StringEncoding.UTF8) {
                entry.value.rawData.map { byte -> val c = byte.toInt() and 255; (if (c in 65..90) c + 32 else c).toByte() }.toByteArray()
            }) }
        }
        val encoded = Asn1BinaryEncoding().encodeDerSequence(X509DistinguishedNameInnerCodec.toAsn1Stream(X509DistinguishedNameInner(lower)))
        return shorten(MessageDigest.getInstance("SHA-1").digest(encoded))
    }
    fun hashDnOld(value: X509DistinguishedName): ByteArray = shorten(MessageDigest.getInstance("MD5").digest(
        Asn1BinaryEncoding().encodeDerSequence(X509DistinguishedNameCodec.toAsn1Stream(value))))
    private fun shorten(digest: ByteArray) = byteArrayOf(digest[3], digest[2], digest[1], digest[0])
}
