// Data.X509.PublicKey, crypton-x509 1.9.2; Vincent Hanquez.
// Complete notice: licenses/crypton-x509-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import java.math.BigInteger

class X509PublicKeyParsed internal constructor(private val saved: Lazy<X509PublicKey>,private val rest: X509Input) {
    val key get()=saved.value
    private val view=object: AbstractList<Asn1Value>() {
        override val size get()=rest.all().size
        override fun isEmpty()=rest[0]==null
        override fun get(index: Int): Asn1Value {
            if(index<0) throw IndexOutOfBoundsException("index: $index")
            return rest[index] ?: throw IndexOutOfBoundsException("index: $index")
        }
        override fun iterator()=remainingSequence().iterator()
    }
    val remaining: List<Asn1Value> get()=view
    fun remainingSequence(): Sequence<Asn1Value> = sequence {var i=0;while(true) yield(rest[i++] ?: break)}
}
object X509PublicKeyCodec: Asn1Object<X509PublicKey> {
    override fun toAsn1(value: X509PublicKey): Asn1StreamBuilder = {tail -> toAsn1Stream(value,tail.toList().asSequence()).toList()}
    fun toAsn1Stream(value: X509PublicKey,tail: Sequence<Asn1Value> = emptySequence()): Sequence<Asn1Value> = sequence {
        yield(Asn1Value.Start(x509Seq))
        when(value) {
            is X509PublicKey.Dh -> error("encodeInner: unimplemented public key DH")
            is X509PublicKey.Ec -> if(value.key is X509EcPublicKey.Prime) error("encodeInner: unimplemented public key EC_Prime")
            else -> Unit
        }
        yield(Asn1Value.Start(x509Seq));yield(Asn1Value.Oid.deferred {value.algorithm.getObjectId()})
        when(value) {
            is X509PublicKey.Rsa,is X509PublicKey.Unknown -> yield(Asn1Value.Null)
            is X509PublicKey.Dsa -> {
                yield(Asn1Value.Start(x509Seq));yield(Asn1Value.IntVal.deferred {value.key.parameters.p});yield(Asn1Value.IntVal.deferred {value.key.parameters.q});yield(Asn1Value.IntVal.deferred {value.key.parameters.g});yield(Asn1Value.End(x509Seq))
            }
            is X509PublicKey.Ec -> yield(Asn1Value.Oid.deferred {val key=value.key as X509EcPublicKey.Named;X509CurveOids.forCurve(key.curve) ?: error("undefined curve OID: ${key.curve}")})
            else -> Unit
        }
        yield(Asn1Value.End(x509Seq))
        yield(Asn1Value.BitString.deferred {
            val bytes=when(value) {
                is X509PublicKey.Rsa -> Asn1BinaryEncoding().encodeDer(listOf(Asn1Value.Start(x509Seq),Asn1Value.IntVal.deferred {value.key.modulus},Asn1Value.IntVal.deferred {value.key.exponent},Asn1Value.End(x509Seq)))
                is X509PublicKey.Dsa -> Asn1BinaryEncoding().encodeDer(listOf(Asn1Value.IntVal.deferred {value.key.y}))
                is X509PublicKey.Ec -> (value.key as X509EcPublicKey.Named).point.bytes
                is X509PublicKey.Bytes -> value.bytes
                is X509PublicKey.Unknown -> value.bytes
                is X509PublicKey.Dh -> error("encodeInner: unimplemented public key DH")
            }
            Asn1BitArray.fromBytes(bytes,0)
        })
        yield(Asn1Value.End(x509Seq));yieldAll(tail)
    }
    fun encodeBytes(value: X509PublicKey)=Asn1BinaryEncoding().encodeDerSequence(toAsn1Stream(value))
    override fun fromAsn1(values: List<Asn1Value>): Asn1ParseResult<Asn1Parsed<X509PublicKey>> = when(val r=fromSequence(values.toList().asSequence())) {
        is Asn1ParseResult.Failure -> r
        is Asn1ParseResult.Success -> Asn1ParseResult.Success(Asn1Parsed.deferred({r.value.key},r.value.remaining))
    }
    fun fromSequence(values: Sequence<Asn1Value>): Asn1ParseResult<X509PublicKeyParsed> = when(val r=decode(X509Input.from(values))) {
        is Asn1ParseResult.Failure -> r
        is Asn1ParseResult.Success -> Asn1ParseResult.Success(X509PublicKeyParsed(r.value.value,r.value.rest))
    }
    private fun failure(prefix: String,input: X509Input): Asn1ParseResult<X509Read<X509PublicKey>> = x509FailureLazy {prefix+x509List(input.all().asSequence())}
    private fun rsa(input: X509Input): Asn1ParseResult<X509Read<X509RsaPublicKey>> {
        if(input[0]==Asn1Value.Start(x509Seq)) {
            val n=input[1] as? Asn1Value.IntVal
            val e=if(n!=null) input[2] as? Asn1Value.IntVal else null
            if(n!=null && e!=null && input[3]==Asn1Value.End(x509Seq)) {
                val modulus by lazy {val signed=n.value;if(signed.signum()<0) BigInteger(1,signed.toByteArray()) else signed}
                return x509Read({X509RsaPublicKey.deferred({((modulus.bitLength()+7)/8).toLong()},{modulus},{e.value})},input.drop(4))
            }
            val version=input[1] as? Asn1Value.IntVal
            if(version!=null && input[2]==Asn1Value.Start(x509Seq)) {
                val oid=input[3] as? Asn1Value.Oid
                if(oid!=null && input[4]==Asn1Value.Null && input[5]==Asn1Value.End(x509Seq)) {
                    val octets=input[6] as? Asn1Value.OctetString
                    if(octets!=null) {
                        if(version.value!=BigInteger.ZERO) return x509Failure("rsaPubFromASN1: Invalid version, expecting 0")
                        if(oid.arcs!=X509PublicKeyAlgorithm.RSA.getObjectId()) return x509Failure("rsaPubFromASN1: invalid OID")
                        return when(val b=x509Binary(octets.bytes)) {
                            is Asn1ParseResult.Failure -> x509FailureLazy {"fromASN1: RSA.PublicKey: ${x509KeyError(b)}"}
                            is Asn1ParseResult.Success -> when(val r=rsa(b.value)) {is Asn1ParseResult.Failure -> r;is Asn1ParseResult.Success -> Asn1ParseResult.Success(X509Read(r.value.value,input.drop(7)))}
                        }
                    }
                }
            }
        }
        return x509Failure("fromASN1: RSA.PublicKey: unexpected format")
    }
    private fun decode(input: X509Input): Asn1ParseResult<X509Read<X509PublicKey>> {
        if(input[0]!=Asn1Value.Start(x509Seq) || input[1]!=Asn1Value.Start(x509Seq) || input[2] !is Asn1Value.Oid) return failure("fromASN1: X509.PubKey: unknown format:",input)
        val oid=(input[2] as Asn1Value.Oid).arcs;val xs=input.drop(3)
        if(oid==X509PublicKeyAlgorithm.RSA.getObjectId()) {
            val rest=if(xs[0]==Asn1Value.Null) xs.drop(1) else xs
            if(rest[0]==Asn1Value.End(x509Seq)) {
                val bits=rest[1] as? Asn1Value.BitString
                if(bits!=null && rest[2]==Asn1Value.End(x509Seq)) return decodeBits("RSA",bits,rest.drop(3)) {s ->
                    when(val r=rsa(s)) {is Asn1ParseResult.Failure -> r;is Asn1ParseResult.Success -> x509Read({X509PublicKey.Rsa(r.value.value.value)},r.value.rest)}
                }
            }
            return failure("fromASN1: X509.PubKey: unknown RSA format: ",xs)
        }
        if(oid==X509PublicKeyAlgorithm.DSA.getObjectId()) {
            if(xs[0]==Asn1Value.Start(x509Seq)) {
                val p=xs[1] as? Asn1Value.IntVal;val q=if(p!=null) xs[2] as? Asn1Value.IntVal else null;val g=if(q!=null) xs[3] as? Asn1Value.IntVal else null
                if(g!=null && xs[4]==Asn1Value.End(x509Seq) && xs[5]==Asn1Value.End(x509Seq)) {
                    val bits=xs[6] as? Asn1Value.BitString
                    if(bits!=null && xs[7]==Asn1Value.End(x509Seq)) return decodeBits("DSA",bits,xs.drop(8)) {s ->
                        val y=s[0] as? Asn1Value.IntVal
                        if(y!=null && s[1]==null) x509Read({X509PublicKey.Dsa(X509DsaPublicKey.deferred(X509DsaParameters.deferred({p!!.value},{g.value},{q!!.value}),{y.value}))},s.drop(1)) else x509Failure("fromASN1: X509.PubKey: unknown DSA format")
                    }
                }
            }
            return x509Failure("fromASN1: X509.PubKey: unknown DSA format")
        }
        if(oid==X509PublicKeyAlgorithm.EC.getObjectId()) return ec(xs)
        for(kind in X509BytePublicKeyKind.entries) if(oid==kind.algorithm.getObjectId()) {
            if(xs[0]==Asn1Value.End(x509Seq)) {
                val bits=xs[1] as? Asn1Value.BitString
                if(bits!=null && xs[2]==Asn1Value.End(x509Seq)) {
                    val bytes=bits.value.data
                    if(bytes.size!=kind.size) return x509Failure("fromASN1: X509.PubKey $kind bitarray contains an invalid public key: CryptoError_PublicKeySizeInvalid")
                    return x509Read({X509PublicKey.Bytes(kind,bytes)},xs.drop(3))
                }
            }
            return failure("fromASN1: X509.PubKey: unknown $kind format: ",xs)
        }
        return x509FailureLazy {"fromASN1: unknown public key OID: ${x509List(oid.asSequence())}"}
    }
    private fun decodeBits(format: String,bits: Asn1Value.BitString,rest: X509Input,f: (X509Input)->Asn1ParseResult<X509Read<X509PublicKey>>): Asn1ParseResult<X509Read<X509PublicKey>> = when(val b=x509Binary(bits.value.data)) {
        is Asn1ParseResult.Failure -> x509FailureLazy {"fromASN1: X509.PubKey $format bitarray cannot be parsed: ${x509KeyError(b)}"}
        is Asn1ParseResult.Success -> when(val r=f(b.value)) {
            is Asn1ParseResult.Failure -> r
            is Asn1ParseResult.Success -> {
                val tail=X509Input.from(sequence {var i=0;while(true) yield(r.value.rest[i++] ?: break);i=0;while(true) yield(rest[i++] ?: break)})
                Asn1ParseResult.Success(X509Read(r.value.value,tail))
            }
        }
    }
    private fun ec(xs: X509Input): Asn1ParseResult<X509Read<X509PublicKey>> {
        val oid=xs[0] as? Asn1Value.Oid
        if(oid!=null && xs[1]==Asn1Value.End(x509Seq)) {
            val bits=xs[2] as? Asn1Value.BitString
            if(bits!=null && xs[3]==Asn1Value.End(x509Seq)) {
                val curve=X509CurveOids.byOid(oid.arcs) ?: return x509FailureLazy {"fromASN1: X509.Pubkey: EC unknown curve ${x509List(oid.arcs.asSequence())}"}
                return x509Read({X509PublicKey.Ec(X509EcPublicKey.Named(curve,X509SerializedPoint.deferred {bits.value.data}))},xs.drop(4))
            }
        }
        var at=0
        fun exact(v: Asn1Value)=xs[at++]==v
        fun integer()=xs[at++] as? Asn1Value.IntVal
        fun octets()=xs[at++] as? Asn1Value.OctetString
        fun bits()=xs[at++] as? Asn1Value.BitString
        if(exact(Asn1Value.Start(x509Seq)) && exact(Asn1Value.IntVal(BigInteger.ONE)) && exact(Asn1Value.Start(x509Seq)) && exact(Asn1Value.Oid(x509Oid(1,2,840,10045,1,1)))) {
            val prime=integer()
            if(prime!=null && exact(Asn1Value.End(x509Seq)) && exact(Asn1Value.Start(x509Seq))) {
                val a=octets();val b=if(a!=null) octets() else null;val seed=if(b!=null) bits() else null
                if(seed!=null && exact(Asn1Value.End(x509Seq))) {
                    val generator=octets();val order=if(generator!=null) integer() else null;val cofactor=if(order!=null) integer() else null
                    if(cofactor!=null && exact(Asn1Value.End(x509Seq)) && exact(Asn1Value.End(x509Seq))) {
                        val point=bits()
                        if(point!=null && exact(Asn1Value.End(x509Seq))) return x509Read({X509PublicKey.Ec(X509EcPublicKey.Prime(X509SerializedPoint.deferred {point.value.data},lazy {unsigned(a!!.bytes)},lazy {unsigned(b!!.bytes)},lazy {prime.value},X509SerializedPoint.deferred {generator!!.bytes},lazy {order!!.value},lazy {cofactor.value},lazy {unsigned(seed.value.data)}))},xs.drop(at))
                    }
                }
            }
        }
        return failure("fromASN1: X509.PubKey: unknown EC format: ",xs)
    }
    private fun unsigned(bytes: ByteArray)=if(bytes.isEmpty()) BigInteger.ZERO else BigInteger(1,bytes)
}

private fun x509KeyError(error: Asn1ParseResult.Failure)=buildString {error.message.points().forEach(::appendCodePoint)}
