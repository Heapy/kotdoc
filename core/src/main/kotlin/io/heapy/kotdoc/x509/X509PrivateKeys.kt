// Data.X509.PrivateKey, crypton-x509 1.9.2; crypton 2.0.1 private-key records/DebugShow.
// Vincent Hanquez; licenses/crypton-x509-BSD-3-Clause.txt, crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import java.math.BigInteger
import java.lang.ref.Cleaner

internal class X509SecretIntegers(private val stored: List<Lazy<BigInteger>>) {
    operator fun get(index: Int)=stored[index].value
    fun same(other: X509SecretIntegers,range: IntRange)=range.all {this[it]==other[it]}
    companion object {fun owned(vararg values: BigInteger)=X509SecretIntegers(values.map(::lazyOf))}
}
class X509RsaPrivateKey internal constructor(val publicKey: X509RsaPublicKey,private val saved: X509SecretIntegers) {
    constructor(publicKey: X509RsaPublicKey,d: BigInteger,p: BigInteger,q: BigInteger,dP: BigInteger,dQ: BigInteger,qinv: BigInteger): this(publicKey,X509SecretIntegers.owned(d,p,q,dP,dQ,qinv))
    val d get()=saved[0];val p get()=saved[1];val q get()=saved[2];val dP get()=saved[3];val dQ get()=saved[4];val qinv get()=saved[5]
    override fun equals(other: Any?)=other is X509RsaPrivateKey && publicKey==other.publicKey && saved.same(other.saved,0..5)
    override fun hashCode()=listOf(publicKey,d,p,q,dP,dQ,qinv).hashCode()
    override fun toString()="PrivateKey {private_pub = $publicKey, private_d = <secret>, private_p = <secret>, private_q = <secret>, private_dP = <secret>, private_dQ = <secret>, private_qinv = <secret>}"
    fun debugShow()="PrivateKey {private_pub = $publicKey, private_d = $d, private_p = $p, private_q = $q, private_dP = $dP, private_dQ = $dQ, private_qinv = $qinv}"
}
class X509DsaPrivateKey internal constructor(val parameters: X509DsaParameters,private val xx: Lazy<BigInteger>) {
    constructor(parameters: X509DsaParameters,x: BigInteger): this(parameters,lazyOf(x))
    val x get()=xx.value
    override fun equals(other: Any?)=other is X509DsaPrivateKey && parameters==other.parameters && x==other.x
    override fun hashCode()=31*parameters.hashCode()+x.hashCode()
    override fun toString()="PrivateKey {private_params = $parameters, private_x = <secret>}"
    fun debugShow()="PrivateKey {private_params = $parameters, private_x = $x}"
}
sealed class X509EcPrivateKey {
    abstract val scalar: BigInteger
    abstract fun debugShow(): String
    class Named internal constructor(val curve: X509CurveName,private val dd: Lazy<BigInteger>): X509EcPrivateKey() {
        constructor(curve: X509CurveName,scalar: BigInteger): this(curve,lazyOf(scalar))
        override val scalar get()=dd.value
        override fun equals(other: Any?)=other is Named && curve==other.curve && scalar==other.scalar
        override fun hashCode()=31*curve.hashCode()+scalar.hashCode()
        override fun toString()="PrivKeyEC_Named {privkeyEC_name = $curve, privkeyEC_priv = <secret>}"
        override fun debugShow()="PrivKeyEC_Named {privkeyEC_name = $curve, privkeyEC_priv = $scalar}"
    }
    class Prime internal constructor(private val saved: X509SecretIntegers,val generator: X509SerializedPoint): X509EcPrivateKey() {
        constructor(scalar: BigInteger,a: BigInteger,b: BigInteger,prime: BigInteger,generator: X509SerializedPoint,order: BigInteger,cofactor: BigInteger,seed: BigInteger): this(X509SecretIntegers.owned(scalar,a,b,prime,order,cofactor,seed),generator)
        override val scalar get()=saved[0];val a get()=saved[1];val b get()=saved[2];val prime get()=saved[3];val order get()=saved[4];val cofactor get()=saved[5];val seed get()=saved[6]
        override fun equals(other: Any?)=other is Prime && saved.same(other.saved,0..3) && generator==other.generator && saved.same(other.saved,4..6)
        override fun hashCode()=listOf(scalar,a,b,prime,generator,order,cofactor,seed).hashCode()
        private fun rendered(secret: String)="PrivKeyEC_Prime {privkeyEC_priv = $secret, privkeyEC_a = $a, privkeyEC_b = $b, privkeyEC_prime = $prime, privkeyEC_generator = $generator, privkeyEC_order = $order, privkeyEC_cofactor = $cofactor, privkeyEC_seed = $seed}"
        override fun toString()=rendered("<secret>")
        override fun debugShow()=rendered(scalar.toString())
    }
}
private val x509SecretCleaner=Cleaner.create()
private class X509ClearSecret(private val bytes: ByteArray): Runnable {override fun run() {bytes.fill(0)}}
sealed class X509PrivateKey {
    abstract val algorithm: X509PublicKeyAlgorithm
    abstract fun debugShow(): String
    class Rsa(val key: X509RsaPrivateKey): X509PrivateKey() {
        override val algorithm=X509PublicKeyAlgorithm.RSA
        override fun equals(other: Any?)=other is Rsa && key==other.key
        override fun hashCode()=key.hashCode();override fun toString()="PrivKeyRSA ($key)"
        override fun debugShow()="PrivKeyRSA (${key.debugShow()})"
    }
    class Dsa(val key: X509DsaPrivateKey): X509PrivateKey() {
        override val algorithm=X509PublicKeyAlgorithm.DSA
        override fun equals(other: Any?)=other is Dsa && key==other.key
        override fun hashCode()=key.hashCode();override fun toString()="PrivKeyDSA ($key)"
        override fun debugShow()="PrivKeyDSA (${key.debugShow()})"
    }
    class Ec(val key: X509EcPrivateKey): X509PrivateKey() {
        override val algorithm=X509PublicKeyAlgorithm.EC
        override fun equals(other: Any?)=other is Ec && key==other.key
        override fun hashCode()=key.hashCode();override fun toString()="PrivKeyEC ($key)"
        override fun debugShow()="PrivKeyEC (${key.debugShow()})"
    }
    class Bytes private constructor(val type: X509BytePublicKeyKind,input: ()->ByteArray): X509PrivateKey() {
        private val stored by lazy {
            val bytes=validate(type,input()).copyOf()
            x509SecretCleaner.register(this,X509ClearSecret(bytes))
            bytes
        }
        constructor(type: X509BytePublicKeyKind,bytes: ByteArray): this(type,input=ownedSource(type,bytes))
        val bytes get()=stored.copyOf()
        override val algorithm get()=type.algorithm
        override fun equals(other: Any?): Boolean {
            if(other !is Bytes || type!=other.type) return false
            val a=stored;val b=other.stored;if(a.size!=b.size) return false
            var difference=0;for(i in a.indices) difference=difference or (a[i].toInt() xor b[i].toInt())
            return difference==0
        }
        override fun hashCode()=31*type.hashCode()+stored.contentHashCode()
        override fun toString()="PrivKey$type (SecretKey <scrubbed-bytes>)"
        override fun debugShow()="PrivKey$type (SecretKey ${stored.joinToString("") {(it.toInt() and 255).toString(16).padStart(2,'0')}})"
        companion object {
            private fun ownedSource(type: X509BytePublicKeyKind,bytes: ByteArray): ()->ByteArray {val owned=validate(type,bytes).copyOf();return {owned}}
            private fun validate(type: X509BytePublicKeyKind,bytes: ByteArray): ByteArray {
                if(bytes.size!=type.size) error(if(type==X509BytePublicKeyKind.Ed25519 || type==X509BytePublicKeyKind.Ed448) "CryptoError_SecretKeyStructureInvalid" else "CryptoError_SecretKeySizeInvalid")
                return bytes
            }
            internal fun deferred(type: X509BytePublicKeyKind,bytes: ()->ByteArray)=Bytes(type,bytes)
        }
    }
}
