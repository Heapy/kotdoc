// Data.X509.AlgorithmIdentifier, crypton-x509 1.9.2; Vincent Hanquez.
// Complete original notice: licenses/crypton-x509-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import java.math.BigInteger

enum class X509HashAlgorithm { HashMD2, HashMD5, HashSHA1, HashSHA224, HashSHA256, HashSHA384, HashSHA512 }

sealed class X509PublicKeyAlgorithm: Asn1Oidable {
    data object RSA: X509PublicKeyAlgorithm()
    data object RSAPSS: X509PublicKeyAlgorithm()
    data object DSA: X509PublicKeyAlgorithm()
    data object EC: X509PublicKeyAlgorithm()
    data object X25519: X509PublicKeyAlgorithm()
    data object X448: X509PublicKeyAlgorithm()
    data object Ed25519: X509PublicKeyAlgorithm()
    data object Ed448: X509PublicKeyAlgorithm()
    data object DH: X509PublicKeyAlgorithm()
    class Unknown(oid: Asn1Oid): X509PublicKeyAlgorithm() {
        private val stored=oid.toList()
        val oid get()=stored.toList()
        override fun equals(other: Any?)=other is Unknown && stored==other.stored
        override fun hashCode()=stored.hashCode()
    }
    final override fun getObjectId(): Asn1Oid = when(this) {
        RSA -> oid(1,2,840,113549,1,1,1); RSAPSS -> oid(1,2,840,113549,1,1,10)
        DSA -> oid(1,2,840,10040,4,1); EC -> oid(1,2,840,10045,2,1)
        X25519 -> oid(1,3,101,110); X448 -> oid(1,3,101,111)
        Ed25519 -> oid(1,3,101,112); Ed448 -> oid(1,3,101,113)
        DH -> oid(1,2,840,10046,2,1); is Unknown -> this.oid
    }
    final override fun toString()="PubKeyALG_"+when(this) {
        RSA -> "RSA"; RSAPSS -> "RSAPSS"; DSA -> "DSA"; EC -> "EC"; X25519 -> "X25519"; X448 -> "X448"
        Ed25519 -> "Ed25519"; Ed448 -> "Ed448"; DH -> "DH"; is Unknown -> "Unknown ${oid.joinToString(",","[","]")}"
    }
}
sealed class X509SignatureAlgorithm {
    data class WithHash(val hash: X509HashAlgorithm,val key: X509PublicKeyAlgorithm): X509SignatureAlgorithm()
    data class IntrinsicHash(val key: X509PublicKeyAlgorithm): X509SignatureAlgorithm()
    class Unknown(oid: Asn1Oid): X509SignatureAlgorithm() {
        private val stored=oid.toList()
        val oid get()=stored.toList()
        override fun equals(other: Any?)=other is Unknown && stored==other.stored
        override fun hashCode()=stored.hashCode()
    }
    final override fun toString(): String {
        fun key(k: X509PublicKeyAlgorithm)=if(k is X509PublicKeyAlgorithm.Unknown) "($k)" else "$k"
        return when(this) {
            is WithHash -> "SignatureALG $hash ${key(key)}"
            is IntrinsicHash -> "SignatureALG_IntrinsicHash ${key(key)}"
            is Unknown -> "SignatureALG_Unknown ${oid.joinToString(",","[","]")}"
        }
    }
}
private fun oid(vararg arcs: Long): Asn1Oid = arcs.map(BigInteger::valueOf)

object X509SignatureAlgorithmCodec: Asn1Object<X509SignatureAlgorithm> {
    private val seq=Asn1ConstructionType.Sequence
    private val pss=oid(1,2,840,113549,1,1,10)
    private val mgf=oid(1,2,840,113549,1,1,8)
    private val table: List<Pair<Asn1Oid,X509SignatureAlgorithm>> = buildList {
        fun add(oid: Asn1Oid,h: X509HashAlgorithm,k: X509PublicKeyAlgorithm) { add(oid to X509SignatureAlgorithm.WithHash(h,k)) }
        val rsa=X509PublicKeyAlgorithm.RSA; val dsa=X509PublicKeyAlgorithm.DSA; val ec=X509PublicKeyAlgorithm.EC; val pss=X509PublicKeyAlgorithm.RSAPSS
        add(oid(1,2,840,113549,1,1,5),X509HashAlgorithm.HashSHA1,rsa)
        add(oid(1,2,840,113549,1,1,4),X509HashAlgorithm.HashMD5,rsa)
        add(oid(1,2,840,113549,1,1,2),X509HashAlgorithm.HashMD2,rsa)
        add(oid(1,2,840,113549,1,1,11),X509HashAlgorithm.HashSHA256,rsa)
        add(oid(1,2,840,113549,1,1,12),X509HashAlgorithm.HashSHA384,rsa)
        add(oid(1,2,840,113549,1,1,13),X509HashAlgorithm.HashSHA512,rsa)
        add(oid(1,2,840,113549,1,1,14),X509HashAlgorithm.HashSHA224,rsa)
        add(oid(1,2,840,10040,4,3),X509HashAlgorithm.HashSHA1,dsa)
        add(oid(1,2,840,10045,4,1),X509HashAlgorithm.HashSHA1,ec)
        for((last,h) in listOf(1L to X509HashAlgorithm.HashSHA224,2L to X509HashAlgorithm.HashSHA256,3L to X509HashAlgorithm.HashSHA384,4L to X509HashAlgorithm.HashSHA512)) add(oid(1,2,840,10045,4,3,last),h,ec)
        for((last,h) in listOf(1L to X509HashAlgorithm.HashSHA256,2L to X509HashAlgorithm.HashSHA384,3L to X509HashAlgorithm.HashSHA512,4L to X509HashAlgorithm.HashSHA224)) add(oid(2,16,840,1,101,3,4,2,last),h,pss)
        add(oid(2,16,840,1,101,3,4,3,1),X509HashAlgorithm.HashSHA224,dsa)
        add(oid(2,16,840,1,101,3,4,3,2),X509HashAlgorithm.HashSHA256,dsa)
        add(oid(1,3,101,112) to X509SignatureAlgorithm.IntrinsicHash(X509PublicKeyAlgorithm.Ed25519))
        add(oid(1,3,101,113) to X509SignatureAlgorithm.IntrinsicHash(X509PublicKeyAlgorithm.Ed448))
    }
    private fun fromOid(oid: Asn1Oid)=table.firstOrNull { it.first==oid }?.second ?: X509SignatureAlgorithm.Unknown(oid)
    private fun toOid(value: X509SignatureAlgorithm)=if(value is X509SignatureAlgorithm.Unknown) value.oid else table.firstOrNull { it.second==value }?.first ?: error("unknown OID for $value")
    private fun saltLength(hash: X509HashAlgorithm): BigInteger = BigInteger.valueOf(when(hash) {
        X509HashAlgorithm.HashSHA256 -> 32; X509HashAlgorithm.HashSHA384 -> 48; X509HashAlgorithm.HashSHA512 -> 64; X509HashAlgorithm.HashSHA224 -> 28
        else -> error("toASN1: X509.SignatureAlg.HashAlg: Unknown hash")
    }.toLong())
    override fun toAsn1(value: X509SignatureAlgorithm): Asn1StreamBuilder = { tail -> toAsn1Stream(value,tail.toList().asSequence()).toList() }
    fun toAsn1Stream(value: X509SignatureAlgorithm,tail: Sequence<Asn1Value> = emptySequence()): Sequence<Asn1Value> = sequence {
        yield(Asn1Value.Start(seq))
        if(value is X509SignatureAlgorithm.WithHash && value.key==X509PublicKeyAlgorithm.RSAPSS) {
            yield(Asn1Value.Oid(pss)); yield(Asn1Value.Start(seq))
            yield(Asn1Value.Start(Asn1ConstructionType.Container(Asn1Class.Context,0))); yield(Asn1Value.Start(seq))
            yield(Asn1Value.Oid.deferred { toOid(value) }); yield(Asn1Value.End(seq)); yield(Asn1Value.End(Asn1ConstructionType.Container(Asn1Class.Context,0)))
            yield(Asn1Value.Start(Asn1ConstructionType.Container(Asn1Class.Context,1))); yield(Asn1Value.Start(seq)); yield(Asn1Value.Oid(mgf)); yield(Asn1Value.Start(seq))
            yield(Asn1Value.Oid.deferred { toOid(value) }); yield(Asn1Value.End(seq)); yield(Asn1Value.End(seq)); yield(Asn1Value.End(Asn1ConstructionType.Container(Asn1Class.Context,1)))
            yield(Asn1Value.Start(Asn1ConstructionType.Container(Asn1Class.Context,2))); yield(Asn1Value.IntVal.deferred { saltLength(value.hash) }); yield(Asn1Value.End(Asn1ConstructionType.Container(Asn1Class.Context,2)))
            yield(Asn1Value.End(seq)); yield(Asn1Value.End(seq))
        } else {
            yield(Asn1Value.Oid.deferred { toOid(value) })
            if(value !is X509SignatureAlgorithm.IntrinsicHash) yield(Asn1Value.Null)
            yield(Asn1Value.End(seq))
        }
        yieldAll(tail)
    }
    override fun fromAsn1(values: List<Asn1Value>): Asn1ParseResult<Asn1Parsed<X509SignatureAlgorithm>> {
        val owned=values.toList()
        fun success(n: Int,oid: Asn1Value.Oid)=Asn1ParseResult.Success(Asn1Parsed.deferred({fromOid(oid.arcs)},owned.drop(n)))
        if(owned.getOrNull(0)==Asn1Value.Start(seq) && owned.getOrNull(1) is Asn1Value.Oid) {
            val oid=owned[1] as Asn1Value.Oid
            if(owned.getOrNull(2)==Asn1Value.Null && owned.getOrNull(3)==Asn1Value.End(seq)) {
                return if(fromOid(oid.arcs) is X509SignatureAlgorithm.IntrinsicHash) Asn1ParseResult.Failure("fromASN1: X509.SignatureALG: EdDSA requires absent parameter") else success(4,oid)
            }
            if(owned.getOrNull(2)==Asn1Value.End(seq)) return success(3,oid)
        }
        for(nulls in listOf(false,true)) {
            var at=0
            fun exact(value: Asn1Value): Boolean = owned.getOrNull(at++)==value
            fun start(): Boolean = owned.getOrNull(at++) is Asn1Value.Start
            fun end(): Boolean = owned.getOrNull(at++) is Asn1Value.End
            fun oid(): Asn1Value.Oid? = owned.getOrNull(at++) as? Asn1Value.Oid
            if(!exact(Asn1Value.Start(seq)) || !exact(Asn1Value.Oid(pss)) || !exact(Asn1Value.Start(seq)) || !start() || !exact(Asn1Value.Start(seq))) continue
            val hash=oid() ?: continue
            if((nulls && !exact(Asn1Value.Null)) || !exact(Asn1Value.End(seq)) || !end() || !start() || !exact(Asn1Value.Start(seq)) || !exact(Asn1Value.Oid(mgf)) || !exact(Asn1Value.Start(seq)) || oid()==null) continue
            if((nulls && !exact(Asn1Value.Null)) || !exact(Asn1Value.End(seq)) || !exact(Asn1Value.End(seq)) || !end() || !start() || owned.getOrNull(at++) !is Asn1Value.IntVal || !end() || !exact(Asn1Value.End(seq)) || !exact(Asn1Value.End(seq))) continue
            return success(at,hash)
        }
        return Asn1ParseResult.Failure("fromASN1: X509.SignatureALG: unknown format")
    }
}
