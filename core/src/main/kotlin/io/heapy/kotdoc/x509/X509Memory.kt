// Data.X509.Memory/File, crypton-x509-store 1.9.0; Vincent Hanquez.
// licenses/crypton-x509-store-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.pem.*
import io.heapy.kotdoc.formats.HaskellString
import java.nio.file.Files
import java.nio.file.Path
import java.math.BigInteger

class X509PemError(val displayPemError: String): RuntimeException(displayPemError) {
    override fun toString()="PEMError {displayPEMError = ${HaskellString.quoteCodePoints(displayPemError.codePoints().toArray())}}"
}

object X509Memory {
    fun readKeyFileFromMemory(bytes: ByteArray): Sequence<X509PrivateKey> = keys(parseOrEmpty(bytes.copyOf()))
    fun <A> readSignedObjectFromMemory(bytes: ByteArray, codec: X509SignedCodec<A>): Sequence<X509SignedExact<A>> = signed(parseOrEmpty(bytes.copyOf()),codec)
    fun readSignedCertificates(bytes: ByteArray) = readSignedObjectFromMemory(bytes,X509SignedCodec(X509CertificateCodec))
    private fun parseOrEmpty(bytes: ByteArray): List<PemSection> = when(val p=Pem.parse(bytes)) {
        is PemParseResult.Failure -> emptyList()
        is PemParseResult.Success -> p.sections
    }
    internal fun <A> signed(pems: List<PemSection>,codec: X509SignedCodec<A>): Sequence<X509SignedExact<A>> {
        val saved=X509Values {sequence {
            for(pem in pems) when(val decoded=codec.decodeSignedObject(pem.content)) {
                is Asn1ParseResult.Failure -> Unit
                is Asn1ParseResult.Success -> yield(lazy {decoded.value})
            }
        }}
        return saved.sequence()
    }
    internal fun keys(pems: List<PemSection>): Sequence<X509PrivateKey> {
        var acc=emptySequence<Lazy<X509PrivateKey?>>()
        for(pem in pems) acc=pemToKey(acc,pem)
        val saved=X509Values {acc.mapNotNull {cell->cell.value?.let(::lazyOf)}}
        return saved.sequence()
    }
    /** Each block prepends its candidates; later PEM blocks therefore precede earlier ones. */
    fun pemToKey(acc: Sequence<Lazy<X509PrivateKey?>>,pem: PemSection): Sequence<Lazy<X509PrivateKey?>> {
        val saved=X509Values<X509PrivateKey?> {sequence {
        val decoded=x509Binary(pem.content)
        if(decoded is Asn1ParseResult.Success) {
            val name=buildString {pem.name.points().forEach(::appendCodePoint)}
            val s=decoded.value
            fun candidate(parse: ()->Asn1ParseResult<X509Read<X509PrivateKey>>) = lazy {
                when(val r=parse()) {is Asn1ParseResult.Failure->null;is Asn1ParseResult.Success->r.value.value.value}
            }
            val rsa=candidate {memoryRsa(s)}
            val dsa=candidate {X509PrivateKeyCodec.dsa(s)}
            val ec=candidate {X509PrivateKeyCodec.ec(s,X509Input {null})}
            val modern=lazy {
                when(val r=X509PrivateKeyCodec.fromSequence(s.sequence())) {
                    is Asn1ParseResult.Failure -> null
                    is Asn1ParseResult.Success -> r.value.key as? X509PrivateKey.Bytes
                }
            }
            when(name) {
                "PRIVATE KEY" -> {yield(rsa);yield(modern);yield(ec);yield(dsa)}
                "RSA PRIVATE KEY" -> yield(rsa)
                "DSA PRIVATE KEY" -> yield(dsa)
                "EC PRIVATE KEY" -> yield(ec)
                "X25519 PRIVATE KEY","X448 PRIVATE KEY","ED25519 PRIVATE KEY","ED448 PRIVATE KEY" -> yield(modern)
            }
        }
        yieldAll(acc)
        }}
        return saved.cells()
    }
    private fun memoryRsa(s: X509Input): Asn1ParseResult<X509Read<X509PrivateKey>> {
        // Unlike Data.X509.PrivateKey, this wrapper does not require an outer End.
        if(s[0]==Asn1Value.Start(x509Seq) && (s[1] as? Asn1Value.IntVal)?.value==BigInteger.ZERO &&
            s[2]==Asn1Value.Start(x509Seq) && s[3]==Asn1Value.Oid(X509PublicKeyAlgorithm.RSA.getObjectId()) &&
            s[4]==Asn1Value.Null && s[5]==Asn1Value.End(x509Seq)) {
            val content=s[6] as? Asn1Value.OctetString
            if(content!=null) return when(val raw=x509Binary(content.bytes)) {
                is Asn1ParseResult.Failure -> raw
                is Asn1ParseResult.Success -> when(val r=memoryRsa(raw.value)) {
                    is Asn1ParseResult.Failure -> r
                    is Asn1ParseResult.Success -> Asn1ParseResult.Success(X509Read(r.value.value,s.drop(7)))
                }
            }
        }
        return X509PrivateKeyCodec.rsa(s)
    }
}

object X509File {
    private fun readPems(path: Path): List<PemSection> = when(val p=Files.newInputStream(path).use {input ->
        // Lazy ByteString hGetContents uses hGetSome with a 32 KiB-minus-overhead
        // chunk. A malformed PEM prefix must not drain the rest of the file.
        Pem.parseChunks(sequence {
            val buffer=ByteArray(32752)
            while(true) {val n=input.read(buffer);if(n<0) break;if(n>0) yield(buffer.copyOf(n))}
        })
    }) {
        is PemParseResult.Failure -> throw X509PemError(p.message)
        is PemParseResult.Success -> p.sections
    }
    fun readKeyFile(path: Path) = X509Memory.keys(readPems(path))
    fun <A> readSignedObject(path: Path,codec: X509SignedCodec<A>) = X509Memory.signed(readPems(path),codec)
    fun readSignedCertificates(path: Path) = readSignedObject(path,X509SignedCodec(X509CertificateCodec))
}
