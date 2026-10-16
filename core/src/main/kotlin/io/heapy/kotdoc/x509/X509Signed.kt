// Data.X509.Signed, crypton-x509 1.9.2; Vincent Hanquez.
// Complete notice: licenses/crypton-x509-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.formats.HaskellString

class X509Signed<A> private constructor(private val obj: Lazy<A>, private val alg: Lazy<X509SignatureAlgorithm>, signature: () -> ByteArray) {
    private val sig = lazy { signature().copyOf() }
    constructor(value: A, algorithm: X509SignatureAlgorithm, signature: ByteArray) : this(lazyOf(value), lazyOf(algorithm), signature.copyOf().let { owned -> { owned } })
    val signedObject get() = obj.value
    val signedAlgorithm get() = alg.value
    val signedSignature get() = sig.value.copyOf()
    override fun equals(other: Any?) = other is X509Signed<*> && signedObject == other.signedObject && signedAlgorithm == other.signedAlgorithm && sig.value.contentEquals(other.sig.value)
    override fun hashCode() = 31 * (31 * (signedObject?.hashCode() ?: 0) + signedAlgorithm.hashCode()) + sig.value.contentHashCode()
    override fun toString(): String {
        val objectText = signedObject.toString()
        val algorithmText = signedAlgorithm.toString()
        return "Signed {signedObject = $objectText, signedAlg = $algorithmText, signedSignature = ${x509Quote(sig.value)}}"
    }
    companion object {
        fun <A> deferred(value: () -> A, algorithm: () -> X509SignatureAlgorithm, signature: () -> ByteArray) = X509Signed(lazy(value), lazy(algorithm), signature)
    }
}

class X509SignedExact<A> internal constructor(private val value: Lazy<X509Signed<A>>, objectRaw: () -> ByteArray, encoded: () -> ByteArray) {
    private val raw = lazy { objectRaw().copyOf() }
    private val bytes = lazy { encoded().copyOf() }
    val signed get() = value.value
    val signedData get() = raw.value.copyOf()
    fun encodeSignedObject() = bytes.value.copyOf()
    override fun equals(other: Any?) = other is X509SignedExact<*> && signed == other.signed && raw.value.contentEquals(other.raw.value) && bytes.value.contentEquals(other.bytes.value)
    override fun hashCode() = 31 * (31 * signed.hashCode() + raw.value.contentHashCode()) + bytes.value.contentHashCode()
    override fun toString(): String {
        val signedText = signed.toString()
        val rawText = x509Quote(raw.value)
        return "SignedExact {getSigned = $signedText, exactObjectRaw = $rawText, encodeSignedObject = ${x509Quote(bytes.value)}}"
    }
}

/** A higher-kinded witness: the caller supplies its own effect and fmap. */
interface X509Kind<F, out A>
interface X509Functor<F> {
    fun <A, B> map(value: X509Kind<F, A>, transform: (A) -> B): X509Kind<F, B>
}
class X509Signature private constructor(signature: () -> ByteArray, private val algorithm: Lazy<X509SignatureAlgorithm>) {
    private val bytes = lazy { signature().copyOf() }
    constructor(signature: ByteArray, algorithm: X509SignatureAlgorithm) : this(signature.copyOf().let { owned -> { owned } }, lazyOf(algorithm))
    val signature get() = bytes.value.copyOf()
    val signatureAlgorithm get() = algorithm.value
    companion object { fun deferred(signature: () -> ByteArray, algorithm: () -> X509SignatureAlgorithm) = X509Signature(signature, lazy(algorithm)) }
}
class X509SignatureResult<R> private constructor(private val sig: Lazy<X509Signature>, private val result: Lazy<R>) {
    constructor(signature: X509Signature, result: R) : this(lazyOf(signature), lazyOf(result))
    val signature get() = sig.value
    val value get() = result.value
    companion object { fun <R> deferred(signature: () -> X509Signature, value: () -> R) = X509SignatureResult(lazy(signature), lazy(value)) }
}
class X509SigningResult<A, R> internal constructor(private val exact: Lazy<X509SignedExact<A>>, private val result: Lazy<R>) {
    val signedExact get() = exact.value
    val signed get() = signedExact.signed
    val value get() = result.value
}

class X509SignedCodec<A>(private val objectCodec: Asn1Object<A>, private val binary: Asn1BinaryEncoding = Asn1BinaryEncoding()) {
    private fun objectValues(value: A) = listOf(Asn1Value.Start(x509Seq)) + objectCodec.toAsn1(value)(emptyList()) + Asn1Value.End(x509Seq)
    private fun build(value: Lazy<A>, signature: X509Signature, raw: Lazy<ByteArray>, values: Lazy<List<Asn1Value>>): X509SignedExact<A> {
        val signed = lazy { X509Signed.deferred({ value.value }, { signature.signatureAlgorithm }, { signature.signature }) }
        return X509SignedExact(signed, { raw.value }) {
            binary.encodeDer(listOf(Asn1Value.Start(x509Seq)) + values.value +
                X509SignatureAlgorithmCodec.toAsn1(signature.signatureAlgorithm)(listOf(
                    Asn1Value.BitString.deferred { Asn1BitArray.fromBytes(signature.signature, 0) }, Asn1Value.End(x509Seq))))
        }
    }
    fun <R> objectToSignedExact(value: A, signature: (ByteArray) -> X509SignatureResult<R>) =
        objectToSignedExactDeferred(lazyOf(value)) { raw -> signature(raw.value.copyOf()) }
    fun <R> objectToSignedExactDeferred(value: Lazy<A>, signature: (Lazy<ByteArray>) -> X509SignatureResult<R>): X509SigningResult<A, R> {
        val values = lazy { objectValues(value.value) }
        val raw = lazy { binary.encodeDer(values.value) }
        val produced = lazy { signature(lazy { raw.value.copyOf() }) }
        return X509SigningResult(lazy { build(value, X509Signature.deferred({ produced.value.signature.signature }, { produced.value.signature.signatureAlgorithm }), raw, values).also { produced.value } }, lazy { produced.value.value })
    }
    fun <R> objectToSigned(value: A, signature: (ByteArray) -> X509SignatureResult<R>) = objectToSignedExact(value, signature)
    fun <R> objectToSignedDeferred(value: Lazy<A>, signature: (Lazy<ByteArray>) -> X509SignatureResult<R>) = objectToSignedExactDeferred(value, signature)
    fun signedToExact(value: X509Signed<A>) = signedToExactDeferred(lazyOf(value))
    fun signedToExactDeferred(value: Lazy<X509Signed<A>>): X509SignedExact<A> {
        val obj = lazy { value.value.signedObject }
        val values = lazy { objectValues(obj.value) }
        val raw = lazy { binary.encodeDer(values.value) }
        // The source reconstructs a Signed record; fields do not demand objRaw.
        return build(obj, X509Signature.deferred({ value.value.signedSignature }, { value.value.signedAlgorithm }), raw, values)
    }
    fun <F> objectToSignedExactF(value: A, signature: (Lazy<ByteArray>) -> X509Kind<F, X509Signature>, functor: X509Functor<F>) =
        objectToSignedExactFDeferred(lazyOf(value), signature, functor).value
    fun <F> objectToSignedExactFDeferred(value: Lazy<A>, signature: (Lazy<ByteArray>) -> X509Kind<F, X509Signature>, functor: X509Functor<F>): Lazy<X509Kind<F, X509SignedExact<A>>> = lazy {
        val values = lazy { objectValues(value.value) }
        val raw = lazy { binary.encodeDer(values.value) }
        functor.map(signature(lazy { raw.value.copyOf() })) { build(value, it, raw, values) }
    }
    fun decodeSignedObject(bytes: ByteArray): Asn1ParseResult<X509SignedExact<A>> {
        val owned = bytes.copyOf()
        val decoded = binary.decodeRepresentation(owned, Asn1EncodingRules.BER)
        if (decoded is Asn1Result.Failure) return x509FailureLazy { decoded.error.show() }
        decoded as Asn1Result.Success
        // The original consumes the first outer container plus one-cell
        // lookahead in getConstructedEndRepr'; later values remain unevaluated.
        val iterator = decoded.value.iterator()
        val outer = mutableListOf<Asn1Representation>()
        if (iterator.hasNext()) {
            val first = iterator.next(); outer.add(first)
            if (first.value is Asn1Value.Start) {
                var depth = 1
                while (depth != 0 && iterator.hasNext()) {
                    val r = iterator.next(); outer.add(r)
                    when (r.value) { is Asn1Value.Start -> depth++; is Asn1Value.End -> depth--; else -> Unit }
                }
                if (depth == 0) iterator.hasNext()
            }
        }
        fun inside(values: List<Asn1Representation>) =
            if (values.firstOrNull()?.value is Asn1Value.Start && values.lastOrNull()?.value is Asn1Value.End) values.drop(1).dropLast(1) else emptyList()
        val body = inside(outer)
        val (obj, rem1) = Asn1Stream.getConstructedEndRepr(body)
        val (algorithm, rem2) = Asn1Stream.getConstructedEndRepr(rem1)
        val (sig, _) = Asn1Stream.getConstructedEndRepr(rem2)
        val parsed = objectCodec.fromAsn1(inside(obj).map { it.value })
        if (parsed is Asn1ParseResult.Failure) return x509FailureLazy {
            "signed object error: " + HaskellString.quoteCodePoints(parsed.message.points().toList().toIntArray())
        }
        parsed as Asn1ParseResult.Success
        if (parsed.value.remaining.isNotEmpty()) return x509FailureLazy { "signed object error: remaining stream in object: ${x509List(parsed.value.remaining.asSequence())}" }
        if (sig.size != 1 || sig[0].value !is Asn1Value.BitString) return x509Failure("signed object error: remaining stream in object: []")
        val alg = X509SignatureAlgorithmCodec.fromAsn1(algorithm.map { it.value })
        if (alg is Asn1ParseResult.Failure) return Asn1ParseResult.Failure.deferred {
            CharacterStringChars.concat(listOf(CharacterStringChars.fromString("signed object error sigalg: "), alg.message))
        }
        alg as Asn1ParseResult.Success
        return Asn1ParseResult.Success.deferred {
            X509SignedExact(lazy { X509Signed.deferred({ parsed.value.value }, { alg.value.value }, { (sig[0].value as Asn1Value.BitString).value.data }) },
                { Asn1Raw.write(obj.flatMap { it.events }) }, { owned })
        }
    }
}
