// Data.X509.PrivateKey, crypton-x509 1.9.2; Vincent Hanquez.
// licenses/crypton-x509-BSD-3-Clause.txt; crypton conversions: crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import java.math.BigInteger

class X509PrivateKeyParsed internal constructor(private val saved: Lazy<X509PrivateKey>,private val rest: X509Input) {
    val key get()=saved.value
    private val view=object: AbstractList<Asn1Value>() {
        override val size get()=rest.all().size
        override fun isEmpty()=rest[0]==null
        override fun get(index: Int): Asn1Value {if(index<0) throw IndexOutOfBoundsException("index: $index");return rest[index] ?: throw IndexOutOfBoundsException("index: $index")}
        override fun iterator()=remainingSequence().iterator()
    }
    val remaining: List<Asn1Value> get()=view
    fun remainingSequence(): Sequence<Asn1Value> = sequence {var i=0;while(true) yield(rest[i++] ?: break)}
}
internal fun x509Unsigned(bytes: ByteArray)=if(bytes.isEmpty()) BigInteger.ZERO else BigInteger(1,bytes)
internal fun x509IntegerOctets(n: BigInteger): ByteArray {
    // The upstream negative-input path returns unwritten native allocation bytes.
    if(n.signum()<0) throw UnsupportedOperationException("crypton i2osp negative input has undefined buffer contents")
    val bytes=n.toByteArray();return if(bytes.size>1 && bytes[0]==0.toByte()) bytes.copyOfRange(1,bytes.size) else bytes
}
internal fun x509DsaPublic(base: BigInteger,exponent: BigInteger,modulus: BigInteger): BigInteger {
    if(modulus==BigInteger.ZERO) return BigInteger.ZERO
    return try {base.modPow(exponent,modulus.abs())} catch(_: ArithmeticException) {BigInteger.ZERO}
}
object X509PrivateKeyCodec: Asn1Object<X509PrivateKey> {
    override fun toAsn1(value: X509PrivateKey): Asn1StreamBuilder = {tail -> toAsn1Stream(value,tail.toList().asSequence()).toList()}
    fun encodeBytes(value: X509PrivateKey)=Asn1BinaryEncoding().encodeDerSequence(toAsn1Stream(value))
    fun toAsn1Stream(value: X509PrivateKey,tail: Sequence<Asn1Value> = emptySequence()): Sequence<Asn1Value> = sequence {
        yield(Asn1Value.Start(x509Seq))
        when(value) {
            is X509PrivateKey.Rsa -> {
                yield(Asn1Value.IntVal(BigInteger.ZERO))
                for(get in listOf<()->BigInteger>({value.key.publicKey.modulus},{value.key.publicKey.exponent},{value.key.d},{value.key.p},{value.key.q},{value.key.dP},{value.key.dQ},{value.key.qinv})) yield(Asn1Value.IntVal.deferred(get))
            }
            is X509PrivateKey.Dsa -> {
                yield(Asn1Value.IntVal(BigInteger.ZERO))
                for(get in listOf<()->BigInteger>({value.key.parameters.p},{value.key.parameters.q},{value.key.parameters.g},{x509DsaPublic(value.key.parameters.g,value.key.x,value.key.parameters.p)},{value.key.x})) yield(Asn1Value.IntVal.deferred(get))
            }
            is X509PrivateKey.Ec -> {
                val key=value.key
                yield(Asn1Value.IntVal(BigInteger.ONE));yield(Asn1Value.OctetString.deferred {x509IntegerOctets(key.scalar)})
                yield(Asn1Value.Start(x509Context(0)))
                when(key) {
                    is X509EcPrivateKey.Named -> yield(Asn1Value.Oid.deferred {X509CurveOids.forCurve(key.curve) ?: error("ECDSA.PrivateKey.toASN1: missing named curve ${key.curve}")})
                    is X509EcPrivateKey.Prime -> {
                        yield(Asn1Value.Start(x509Seq));yield(Asn1Value.IntVal(BigInteger.ONE));yield(Asn1Value.Start(x509Seq));yield(Asn1Value.Oid(x509Oid(1,2,840,10045,1,1)));yield(Asn1Value.IntVal.deferred {key.prime});yield(Asn1Value.End(x509Seq));yield(Asn1Value.Start(x509Seq))
                        yield(Asn1Value.OctetString.deferred {x509IntegerOctets(key.a)});yield(Asn1Value.OctetString.deferred {x509IntegerOctets(key.b)})
                        yield(Asn1Value.BitString.deferred {Asn1BitArray.fromBytes(x509IntegerOctets(key.seed),0)})
                        yield(Asn1Value.End(x509Seq));yield(Asn1Value.OctetString.deferred {key.generator.bytes});yield(Asn1Value.IntVal.deferred {key.order});yield(Asn1Value.IntVal.deferred {key.cofactor});yield(Asn1Value.End(x509Seq))
                    }
                }
                yield(Asn1Value.End(x509Context(0)))
            }
            is X509PrivateKey.Bytes -> {
                yield(Asn1Value.IntVal(BigInteger.ZERO));yield(Asn1Value.Start(x509Seq));yield(Asn1Value.Oid(value.algorithm.getObjectId()));yield(Asn1Value.End(x509Seq))
                yield(Asn1Value.OctetString.deferred {Asn1BinaryEncoding().encodeDer(listOf(Asn1Value.OctetString(value.bytes)))})
            }
        }
        yield(Asn1Value.End(x509Seq));yieldAll(tail)
    }
    override fun fromAsn1(values: List<Asn1Value>): Asn1ParseResult<Asn1Parsed<X509PrivateKey>> = when(val r=fromSequence(values.toList().asSequence())) {
        is Asn1ParseResult.Failure -> r
        is Asn1ParseResult.Success -> Asn1ParseResult.Success(Asn1Parsed.deferred({r.value.key},r.value.remaining))
    }
    fun fromSequence(values: Sequence<Asn1Value>): Asn1ParseResult<X509PrivateKeyParsed> = when(val r=parse(X509Input.from(values))) {
        is Asn1ParseResult.Failure -> r
        is Asn1ParseResult.Success -> Asn1ParseResult.Success(X509PrivateKeyParsed(r.value.value,r.value.rest))
    }
    private fun parse(s: X509Input): Asn1ParseResult<X509Read<X509PrivateKey>> {
        for(parser in listOf<(X509Input)->Asn1ParseResult<X509Read<X509PrivateKey>>>(::rsa,::dsa,{ec(it,empty())})) {
            val r=parser(s);if(r is Asn1ParseResult.Success) return r
        }
        return newCurve(s)
    }
    private fun version(s: X509Input,n: Long)=s[0]==Asn1Value.Start(x509Seq) && (s[1] as? Asn1Value.IntVal)?.value==BigInteger.valueOf(n)
    private fun rsa(s: X509Input): Asn1ParseResult<X509Read<X509PrivateKey>> {
        if(version(s,0)) {
            val ints=mutableListOf<Asn1Value.IntVal>();var at=2
            while(ints.size<8) {val n=s[at++] as? Asn1Value.IntVal ?: break;ints.add(n)}
            if(ints.size==8 && s[10]==Asn1Value.End(x509Seq)) return x509Read({
                val pub=X509RsaPublicKey.deferred({val n=ints[0].value;if(n.signum()<=0) 1L else ((n.bitLength()+7)/8).toLong()},{ints[0].value},{ints[1].value})
                X509PrivateKey.Rsa(X509RsaPrivateKey(pub,X509SecretIntegers(ints.drop(2).map {n->lazy {n.value}})))
            },s.drop(11))
            if(s[2]==Asn1Value.Start(x509Seq) && s[3]==Asn1Value.Oid(X509PublicKeyAlgorithm.RSA.getObjectId()) && s[4]==Asn1Value.Null && s[5]==Asn1Value.End(x509Seq)) {
                val b=s[6] as? Asn1Value.OctetString
                if(b!=null && s[7]==Asn1Value.End(x509Seq)) return when(val raw=x509Binary(b.bytes)) {
                    is Asn1ParseResult.Failure -> prefixFailure("rsaFromASN1: ",raw)
                    is Asn1ParseResult.Success -> when(val r=rsa(raw.value)) {is Asn1ParseResult.Failure -> r;is Asn1ParseResult.Success -> Asn1ParseResult.Success(X509Read(r.value.value,s.drop(8)))}
                }
            }
        }
        return x509Failure("rsaFromASN1: unexpected format")
    }
    private fun dsa(s: X509Input): Asn1ParseResult<X509Read<X509PrivateKey>> {
        if(version(s,0)) {
            val ints=mutableListOf<Asn1Value.IntVal>();var at=2
            while(ints.size<5) {val n=s[at++] as? Asn1Value.IntVal ?: break;ints.add(n)}
            if(ints.size==5 && s[7]==Asn1Value.End(x509Seq)) return x509Read({X509PrivateKey.Dsa(X509DsaPrivateKey(X509DsaParameters.deferred({ints[0].value},{ints[2].value},{ints[1].value}),lazy {ints[4].value}))},s.drop(8))
            if(s[2]==Asn1Value.Start(x509Seq) && s[3]==Asn1Value.Oid(X509PublicKeyAlgorithm.DSA.getObjectId()) && s[4]==Asn1Value.Start(x509Seq)) {
                val p=s[5] as? Asn1Value.IntVal;val q=if(p!=null) s[6] as? Asn1Value.IntVal else null;val g=if(q!=null) s[7] as? Asn1Value.IntVal else null
                if(g!=null && s[8]==Asn1Value.End(x509Seq) && s[9]==Asn1Value.End(x509Seq)) {
                    val b=s[10] as? Asn1Value.OctetString
                    if(b!=null && s[11]==Asn1Value.End(x509Seq)) return when(val raw=x509Binary(b.bytes)) {
                        is Asn1ParseResult.Failure -> prefixFailure("DSA.PrivateKey.fromASN1: ",raw)
                        is Asn1ParseResult.Success -> {
                            val x=raw.value[0] as? Asn1Value.IntVal
                            if(x!=null && raw.value[1]==null) x509Read({X509PrivateKey.Dsa(X509DsaPrivateKey(X509DsaParameters.deferred({p!!.value},{g.value},{q!!.value}),lazy {x.value}))},s.drop(12)) else x509Failure("DSA.PrivateKey.fromASN1: unexpected format")
                        }
                    }
                }
            }
        }
        return x509Failure("DSA.PrivateKey.fromASN1: unexpected format")
    }
    private fun ec(s: X509Input,acc: X509Input): Asn1ParseResult<X509Read<X509PrivateKey>> {
        if(version(s,1)) {
            val bytes=s[2] as? Asn1Value.OctetString
            if(bytes!=null) {
                val (params,rest)=spanTag(s.drop(3),0);val (_,end)=spanTag(rest,1)
                val key=ecParameters(join(params,acc),lazy {x509Unsigned(bytes.bytes)})
                if(key is Asn1ParseResult.Failure) return key
                key as Asn1ParseResult.Success
                return if(end[0]==Asn1Value.End(x509Seq)) x509Read({X509PrivateKey.Ec(key.value)},end.drop(1)) else x509Failure("ECDSA.PrivateKey.fromASN1: unexpected EC format")
            }
        }
        if(version(s,0) && s[2]==Asn1Value.Start(x509Seq) && s[3]==Asn1Value.Oid(X509PublicKeyAlgorithm.EC.getObjectId())) {
            val (params,rest)=s.drop(4).split();val bytes=rest[0] as? Asn1Value.OctetString
            if(bytes!=null) return when(val raw=x509Binary(bytes.bytes)) {
                is Asn1ParseResult.Failure -> prefixFailure("ECDSA.PrivateKey.fromASN1: ",raw)
                is Asn1ParseResult.Success -> when(val r=ec(raw.value,join(params,acc))) {is Asn1ParseResult.Failure -> r;is Asn1ParseResult.Success -> Asn1ParseResult.Success(X509Read(r.value.value,rest.drop(1)))}
            }
        }
        return x509Failure("ECDSA.PrivateKey.fromASN1: unexpected EC format")
    }
    private fun ecParameters(input: X509Input,d: Lazy<BigInteger>): Asn1ParseResult<X509EcPrivateKey> {
        var s=input
        while(s[0]==Asn1Value.Null) s=s.drop(1)
        val oid=s[0] as? Asn1Value.Oid
        if(oid!=null) {
            val curve=X509CurveOids.byOid(oid.arcs) ?: return x509FailureLazy {"ECDSA.PrivateKey.fromASN1: unknown curve ${x509List(oid.arcs.asSequence())}"}
            return Asn1ParseResult.Success(X509EcPrivateKey.Named(curve,d))
        }
        if(version(s,1) && s[2]==Asn1Value.Start(x509Seq) && s[3]==Asn1Value.Oid(x509Oid(1,2,840,10045,1,1))) {
            val p=s[4] as? Asn1Value.IntVal
            if(p!=null && s[5]==Asn1Value.End(x509Seq) && s[6]==Asn1Value.Start(x509Seq)) {
                val a=s[7] as? Asn1Value.OctetString;val b=if(a!=null) s[8] as? Asn1Value.OctetString else null;val seed=if(b!=null) s[9] as? Asn1Value.BitString else null
                if(seed!=null && s[10]==Asn1Value.End(x509Seq)) {
                    val g=s[11] as? Asn1Value.OctetString;val order=if(g!=null) s[12] as? Asn1Value.IntVal else null;val cofactor=if(order!=null) s[13] as? Asn1Value.IntVal else null
                    if(cofactor!=null && s[14]==Asn1Value.End(x509Seq)) return Asn1ParseResult.Success(X509EcPrivateKey.Prime(X509SecretIntegers(listOf(d,lazy {x509Unsigned(a!!.bytes)},lazy {x509Unsigned(b!!.bytes)},lazy {p.value},lazy {order!!.value},lazy {cofactor.value},lazy {x509Unsigned(seed.value.data)})),X509SerializedPoint.deferred {g!!.bytes}))
                }
            }
        }
        return x509Failure(if(s[0]==null) "ECDSA.PrivateKey.fromASN1: curve is missing" else "ECDSA.PrivateKey.fromASN1: unexpected curve format")
    }
    private fun newCurve(s: X509Input): Asn1ParseResult<X509Read<X509PrivateKey>> {
        if(s[0]==Asn1Value.Start(x509Seq)) {
            val version=s[1] as? Asn1Value.IntVal
            if(version!=null && s[2]==Asn1Value.Start(x509Seq)) {
                val oid=s[3] as? Asn1Value.Oid
                if(oid!=null && s[4]==Asn1Value.End(x509Seq)) {
                    val b=s[5] as? Asn1Value.OctetString
                    if(b!=null) {
                        val v=version.value
                        if(v<BigInteger.ZERO || v>BigInteger.ONE) return x509Failure("newcurveFromASN1: unexpected version: $v")
                        val (_,rest)=spanTag(s.drop(6),0)
                        val optional=rest[0] as? Asn1Value.Other
                        val end=if(optional!=null && optional.tag==1L) rest.drop(1) else rest
                        if(end[0]!=Asn1Value.End(x509Seq)) return x509Failure("newcurveFromASN1: unexpected end format")
                        val kind=X509BytePublicKeyKind.entries.firstOrNull {it.algorithm.getObjectId()==oid.arcs} ?: return x509FailureLazy {"newcurveFromASN1: unexpected OID ${x509List(oid.arcs.asSequence())}"}
                        fun fail(msg: String): Asn1ParseResult<X509Read<X509PrivateKey>> = x509Failure("$kind.SecretKey.fromASN1: $msg")
                        return when(val raw=x509Binary(b.bytes)) {
                            is Asn1ParseResult.Failure -> prefixFailure("$kind.SecretKey.fromASN1: ",raw)
                            is Asn1ParseResult.Success -> {
                                val bytes=raw.value[0] as? Asn1Value.OctetString
                                if(bytes==null || raw.value[1]!=null) fail("unexpected inner format") else {
                                    val data=bytes.bytes
                                    if(data.size!=kind.size) fail("invalid secret key: "+if(kind==X509BytePublicKeyKind.Ed25519 || kind==X509BytePublicKeyKind.Ed448) "CryptoError_SecretKeyStructureInvalid" else "CryptoError_SecretKeySizeInvalid") else x509Read({X509PrivateKey.Bytes(kind,data)},end.drop(1))
                                }
                            }
                        }
                    }
                }
            }
        }
        return x509Failure("newcurveFromASN1: unexpected format")
    }
    private fun empty()=X509Input {null}
    private fun spanTag(s: X509Input,tag: Long): Pair<X509Input,X509Input> {
        val first=s[0] as? Asn1Value.Start;val kind=first?.kind as? Asn1ConstructionType.Container
        return if(kind!=null && kind.tag==tag) s.drop(1).split() else empty() to s
    }
    private fun join(a: X509Input,b: X509Input)=X509Input.from(sequence {var i=0;while(true) yield(a[i++] ?: break);i=0;while(true) yield(b[i++] ?: break)})
    private fun <A> prefixFailure(prefix: String,f: Asn1ParseResult.Failure): Asn1ParseResult<A> = x509FailureLazy {prefix+buildString {f.message.points().forEach(::appendCodePoint)}}
}
