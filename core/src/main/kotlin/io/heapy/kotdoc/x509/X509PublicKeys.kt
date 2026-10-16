// Data.X509.PublicKey/OID, crypton-x509 1.9.2; Crypto public-key records, crypton 2.0.1.
// Vincent Hanquez. Notices: licenses/crypton-x509-BSD-3-Clause.txt, crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.*
import java.math.BigInteger

enum class X509CurveName {
SEC_p112r1,SEC_p112r2,SEC_p128r1,SEC_p128r2,SEC_p160k1,SEC_p160r1,SEC_p160r2,SEC_p192k1,SEC_p192r1,SEC_p224k1,SEC_p224r1,SEC_p256k1,SEC_p256r1,SEC_p384r1,SEC_p521r1,SEC_t113r1,SEC_t113r2,SEC_t131r1,SEC_t131r2,SEC_t163k1,SEC_t163r1,SEC_t163r2,SEC_t193r1,SEC_t193r2,SEC_t233k1,SEC_t233r1,SEC_t239k1,SEC_t283k1,SEC_t283r1,SEC_t409k1,SEC_t409r1,SEC_t571k1,SEC_t571r1
}
class X509OidEntry<out A> private constructor(private val stored: Lazy<A>,private val arcs: Lazy<Asn1Oid>) {
    constructor(value: A,oid: Asn1Oid): this(lazyOf(value),lazyOf(oid.toList()))
    val value get()=stored.value;val oid get()=arcs.value.toList()
    companion object {fun <A> deferred(value: ()->A,oid: ()->Asn1Oid)=X509OidEntry(lazy(value),lazy {oid().toList()})}
}
typealias X509OidTable<A> = List<X509OidEntry<A>>
object X509OidLookup {
    fun <A> byOid(table: Iterable<X509OidEntry<A>>,oid: Asn1Oid)=byOidDeferred(table) {oid}
    fun <A> byOidDeferred(table: Iterable<X509OidEntry<A>>,oid: ()->Asn1Oid): Asn1Maybe<A> {
        val target by lazy(oid)
        for(row in table) if(target==row.oid) return Asn1Maybe.Some.deferred {row.value}
        return Asn1Maybe.None
    }
    fun <A> forValue(table: Iterable<X509OidEntry<A>>,value: A)=forValueDeferred(table) {value}
    fun <A> forValueDeferred(table: Iterable<X509OidEntry<A>>,value: ()->A): Asn1Maybe<Asn1Oid> {
        val target by lazy(value)
        for(row in table) if(target==row.value) return Asn1Maybe.Some.deferred {row.oid}
        return Asn1Maybe.None
    }
}
object X509CurveOids {
    private val table=listOf(
        X509CurveName.SEC_p112r1 to x509Oid(1, 3, 132, 0, 6),
        X509CurveName.SEC_p112r2 to x509Oid(1, 3, 132, 0, 7),
        X509CurveName.SEC_p128r1 to x509Oid(1, 3, 132, 0, 28),
        X509CurveName.SEC_p128r2 to x509Oid(1, 3, 132, 0, 29),
        X509CurveName.SEC_p160k1 to x509Oid(1, 3, 132, 0, 9),
        X509CurveName.SEC_p160r1 to x509Oid(1, 3, 132, 0, 8),
        X509CurveName.SEC_p160r2 to x509Oid(1, 3, 132, 0, 30),
        X509CurveName.SEC_p192k1 to x509Oid(1, 3, 132, 0, 31),
        X509CurveName.SEC_p192r1 to x509Oid(1, 2, 840, 10045, 3, 1, 1),
        X509CurveName.SEC_p224k1 to x509Oid(1, 3, 132, 0, 32),
        X509CurveName.SEC_p224r1 to x509Oid(1, 3, 132, 0, 33),
        X509CurveName.SEC_p256k1 to x509Oid(1, 3, 132, 0, 10),
        X509CurveName.SEC_p256r1 to x509Oid(1, 2, 840, 10045, 3, 1, 7),
        X509CurveName.SEC_p384r1 to x509Oid(1, 3, 132, 0, 34),
        X509CurveName.SEC_p521r1 to x509Oid(1, 3, 132, 0, 35),
        X509CurveName.SEC_t113r1 to x509Oid(1, 3, 132, 0, 4),
        X509CurveName.SEC_t113r2 to x509Oid(1, 3, 132, 0, 5),
        X509CurveName.SEC_t131r1 to x509Oid(1, 3, 132, 0, 22),
        X509CurveName.SEC_t131r2 to x509Oid(1, 3, 132, 0, 23),
        X509CurveName.SEC_t163k1 to x509Oid(1, 3, 132, 0, 1),
        X509CurveName.SEC_t163r1 to x509Oid(1, 3, 132, 0, 2),
        X509CurveName.SEC_t163r2 to x509Oid(1, 3, 132, 0, 15),
        X509CurveName.SEC_t193r1 to x509Oid(1, 3, 132, 0, 24),
        X509CurveName.SEC_t193r2 to x509Oid(1, 3, 132, 0, 25),
        X509CurveName.SEC_t233k1 to x509Oid(1, 3, 132, 0, 26),
        X509CurveName.SEC_t233r1 to x509Oid(1, 3, 132, 0, 27),
        X509CurveName.SEC_t239k1 to x509Oid(1, 3, 132, 0, 3),
        X509CurveName.SEC_t283k1 to x509Oid(1, 3, 132, 0, 16),
        X509CurveName.SEC_t283r1 to x509Oid(1, 3, 132, 0, 17),
        X509CurveName.SEC_t409k1 to x509Oid(1, 3, 132, 0, 36),
        X509CurveName.SEC_t409r1 to x509Oid(1, 3, 132, 0, 37),
        X509CurveName.SEC_t571k1 to x509Oid(1, 3, 132, 0, 38),
        X509CurveName.SEC_t571r1 to x509Oid(1, 3, 132, 0, 39))
    val entries get()=table.map {(n,o)->n to o.toList()}
    fun byOid(oid: Asn1Oid)=table.firstOrNull {it.second==oid}?.first
    fun forCurve(curve: X509CurveName)=table.firstOrNull {it.first==curve}?.second?.toList()
}
class X509SerializedPoint private constructor(private val stored: Lazy<ByteArray>) {
    constructor(bytes: ByteArray): this(lazyOf(bytes.copyOf()))
    val bytes get()=stored.value.copyOf()
    override fun equals(other: Any?)=other is X509SerializedPoint && stored.value.contentEquals(other.stored.value)
    override fun hashCode()=stored.value.contentHashCode()
    override fun toString()="SerializedPoint ${x509Quote(stored.value)}"
    companion object {internal fun deferred(bytes: ()->ByteArray)=X509SerializedPoint(lazy {bytes().copyOf()})}
}
class X509RsaPublicKey private constructor(private val sz: Lazy<Long>,private val n: Lazy<BigInteger>,private val e: Lazy<BigInteger>) {
    constructor(size: Long,modulus: BigInteger,exponent: BigInteger): this(lazyOf(size),lazyOf(modulus),lazyOf(exponent))
    val size get()=sz.value;val modulus get()=n.value;val exponent get()=e.value
    override fun equals(other: Any?)=other is X509RsaPublicKey && size==other.size && modulus==other.modulus && exponent==other.exponent
    override fun hashCode()=31*(31*size.hashCode()+modulus.hashCode())+exponent.hashCode()
    override fun toString()="PublicKey {public_size = $size, public_n = $modulus, public_e = $exponent}"
    companion object {internal fun deferred(size: ()->Long,n: ()->BigInteger,e: ()->BigInteger)=X509RsaPublicKey(lazy(size),lazy(n),lazy(e))}
}
class X509DsaParameters private constructor(private val pp: Lazy<BigInteger>,private val gg: Lazy<BigInteger>,private val qq: Lazy<BigInteger>) {
    constructor(p: BigInteger,g: BigInteger,q: BigInteger): this(lazyOf(p),lazyOf(g),lazyOf(q))
    val p get()=pp.value;val g get()=gg.value;val q get()=qq.value
    override fun equals(other: Any?)=other is X509DsaParameters && p==other.p && g==other.g && q==other.q
    override fun hashCode()=31*(31*p.hashCode()+g.hashCode())+q.hashCode()
    override fun toString()="Params {params_p = $p, params_g = $g, params_q = $q}"
    companion object {internal fun deferred(p: ()->BigInteger,g: ()->BigInteger,q: ()->BigInteger)=X509DsaParameters(lazy(p),lazy(g),lazy(q))}
}
class X509DsaPublicKey private constructor(val parameters: X509DsaParameters,private val yy: Lazy<BigInteger>) {
    constructor(parameters: X509DsaParameters,y: BigInteger): this(parameters,lazyOf(y))
    val y get()=yy.value
    override fun equals(other: Any?)=other is X509DsaPublicKey && parameters==other.parameters && y==other.y
    override fun hashCode()=31*parameters.hashCode()+y.hashCode()
    override fun toString()="PublicKey {public_params = $parameters, public_y = $y}"
    companion object {internal fun deferred(params: X509DsaParameters,y: ()->BigInteger)=X509DsaPublicKey(params,lazy(y))}
}
sealed class X509EcPublicKey {
    class Named(val curve: X509CurveName,val point: X509SerializedPoint): X509EcPublicKey() {
        override fun equals(other: Any?)=other is Named && curve==other.curve && point==other.point
        override fun hashCode()=31*curve.hashCode()+point.hashCode()
        override fun toString()="PubKeyEC_Named {pubkeyEC_name = $curve, pubkeyEC_pub = $point}"
    }
    class Prime internal constructor(val point: X509SerializedPoint,private val aa: Lazy<BigInteger>,private val bb: Lazy<BigInteger>,private val pp: Lazy<BigInteger>,val generator: X509SerializedPoint,private val nn: Lazy<BigInteger>,private val hh: Lazy<BigInteger>,private val ss: Lazy<BigInteger>): X509EcPublicKey() {
        constructor(point: X509SerializedPoint,a: BigInteger,b: BigInteger,prime: BigInteger,generator: X509SerializedPoint,order: BigInteger,cofactor: BigInteger,seed: BigInteger): this(point,lazyOf(a),lazyOf(b),lazyOf(prime),generator,lazyOf(order),lazyOf(cofactor),lazyOf(seed))
        val a get()=aa.value;val b get()=bb.value;val prime get()=pp.value;val order get()=nn.value;val cofactor get()=hh.value;val seed get()=ss.value
        override fun equals(other: Any?)=other is Prime && point==other.point && a==other.a && b==other.b && prime==other.prime && generator==other.generator && order==other.order && cofactor==other.cofactor && seed==other.seed
        override fun hashCode()=listOf(point,a,b,prime,generator,order,cofactor,seed).hashCode()
        override fun toString()="PubKeyEC_Prime {pubkeyEC_pub = $point, pubkeyEC_a = $a, pubkeyEC_b = $b, pubkeyEC_prime = $prime, pubkeyEC_generator = $generator, pubkeyEC_order = $order, pubkeyEC_cofactor = $cofactor, pubkeyEC_seed = $seed}"
    }
}
sealed class X509PublicKey {
    abstract val algorithm: X509PublicKeyAlgorithm
    class Rsa(val key: X509RsaPublicKey): X509PublicKey() {
        override val algorithm=X509PublicKeyAlgorithm.RSA
        override fun equals(other: Any?)=other is Rsa && key==other.key
        override fun hashCode()=key.hashCode();override fun toString()="PubKeyRSA ($key)"
    }
    class Dsa(val key: X509DsaPublicKey): X509PublicKey() {
        override val algorithm=X509PublicKeyAlgorithm.DSA
        override fun equals(other: Any?)=other is Dsa && key==other.key
        override fun hashCode()=key.hashCode();override fun toString()="PubKeyDSA ($key)"
    }
    class Ec(val key: X509EcPublicKey): X509PublicKey() {
        override val algorithm=X509PublicKeyAlgorithm.EC
        override fun equals(other: Any?)=other is Ec && key==other.key
        override fun hashCode()=key.hashCode();override fun toString()="PubKeyEC ($key)"
    }
    class Dh(val p: BigInteger,val g: BigInteger,val q: BigInteger,val j: BigInteger?,seed: List<Int>,val counter: BigInteger): X509PublicKey() {
        private val saved=seed.map {it and 255};val seed get()=saved.toList()
        override val algorithm=X509PublicKeyAlgorithm.DH
        override fun equals(other: Any?)=other is Dh && p==other.p && g==other.g && q==other.q && j==other.j && saved==other.saved && counter==other.counter
        override fun hashCode()=listOf(p,g,q,j,saved,counter).hashCode()
        override fun toString()="PubKeyDH ($p,$g,$q,${j?.let {"Just ${x509Arg(it)}"} ?: "Nothing"},(${x509List(saved.asSequence())},$counter))"
    }
    class Unknown private constructor(private val oo: Lazy<Asn1Oid>,private val bb: Lazy<ByteArray>): X509PublicKey() {
        constructor(oid: Asn1Oid,bytes: ByteArray): this(lazyOf(oid.toList()),lazyOf(bytes.copyOf()))
        val oid get()=oo.value.toList();val bytes get()=bb.value.copyOf()
        override val algorithm get()=X509PublicKeyAlgorithm.Unknown(oid)
        override fun equals(other: Any?)=other is Unknown && oid==other.oid && bb.value.contentEquals(other.bb.value)
        override fun hashCode()=31*oid.hashCode()+bb.value.contentHashCode();override fun toString()="PubKeyUnknown ${x509List(oid.asSequence())} ${x509Quote(bb.value)}"
        companion object {internal fun deferred(oid: ()->Asn1Oid,bytes: ()->ByteArray)=Unknown(lazy {oid().toList()},lazy {bytes().copyOf()})}
    }
    class Bytes private constructor(val type: X509BytePublicKeyKind,private val stored: Lazy<ByteArray>): X509PublicKey() {
        constructor(type: X509BytePublicKeyKind,bytes: ByteArray): this(type,lazyOf(type.validate(bytes)))
        val bytes get()=stored.value.copyOf();override val algorithm get()=type.algorithm
        override fun equals(other: Any?)=other is Bytes && type==other.type && stored.value.contentEquals(other.stored.value)
        override fun hashCode()=31*type.hashCode()+stored.value.contentHashCode()
        override fun toString()="PubKey${type} (PublicKey ${x509Quote(stored.value)})"
        companion object {internal fun deferred(type: X509BytePublicKeyKind,bytes: ()->ByteArray)=Bytes(type,lazy {type.validate(bytes())})}
    }
}
enum class X509BytePublicKeyKind(val size: Int,val algorithm: X509PublicKeyAlgorithm) {
    X25519(32,X509PublicKeyAlgorithm.X25519),X448(56,X509PublicKeyAlgorithm.X448),Ed25519(32,X509PublicKeyAlgorithm.Ed25519),Ed448(57,X509PublicKeyAlgorithm.Ed448);
    internal fun validate(bytes: ByteArray): ByteArray {if(bytes.size!=size) error("CryptoError_PublicKeySizeInvalid");return bytes.copyOf()}
}
