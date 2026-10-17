// Crypto.PubKey.RSA.Types, crypton 2.0.1; Vincent Hanquez and contributors.
// BSD-3-Clause; full notice retained in licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.x509.*
import java.math.BigInteger

/** Lazy source records. Conversion to accepted arithmetic records is explicit. */
object CryptoRsaTypes {
    // These source identities are already used by the accepted arithmetic/padding ports.
    fun blinder(r:BigInteger,inverse:BigInteger)=CryptoRsa.Blinder(r,inverse)
    val errors get()=CryptoRsaOaep.Error.entries
    class PublicKey private constructor(private val fields:List<Lazy<Any?>>) {
        constructor(size:Long,n:BigInteger,e:BigInteger):this(listOf(lazyOf(size),lazyOf(n),lazyOf(e)))
        val size get()=field(0) as Long
        val n get()=field(1) as BigInteger
        val e get()=field(2) as BigInteger
        internal fun field(i:Int)=fields[i].value
        override fun equals(other:Any?)=other is PublicKey&&size==other.size&&n==other.n&&e==other.e
        override fun hashCode()=31*(31*size.hashCode()+n.hashCode())+e.hashCode()
        fun showsPrec(precedence:Long)=paren(precedence>10,"PublicKey {public_size = $size, public_n = $n, public_e = $e}")
        override fun toString()=showsPrec(0)
        fun asX509()=X509RsaPublicKey.deferred({size},{n},{e})
        companion object {
            fun deferred(size:()->Long,n:()->BigInteger,e:()->BigInteger)=PublicKey(listOf(cryptoRandomMemo(size),cryptoRandomMemo(n),cryptoRandomMemo(e)))
            internal fun source(fields:List<Lazy<Any?>>)=PublicKey(fields)
            fun fromX509(value:X509RsaPublicKey)=deferred({value.size},{value.modulus},{value.exponent})
        }
    }
    class PrivateKey private constructor(private val fields:List<Lazy<Any?>>) {
        constructor(pub:PublicKey,d:BigInteger,p:BigInteger,q:BigInteger,dP:BigInteger,dQ:BigInteger,qinv:BigInteger):this(listOf(lazyOf(pub),lazyOf(d),lazyOf(p),lazyOf(q),lazyOf(dP),lazyOf(dQ),lazyOf(qinv)))
        val publicKey get()=field(0) as PublicKey
        val d get()=field(1) as BigInteger
        val p get()=field(2) as BigInteger
        val q get()=field(3) as BigInteger
        val dP get()=field(4) as BigInteger
        val dQ get()=field(5) as BigInteger
        val qinv get()=field(6) as BigInteger
        internal fun field(i:Int)=fields[i].value
        override fun equals(other:Any?):Boolean {if(other !is PrivateKey)return false;val a=publicKey;val sz=a.size;val b=other.publicKey;if(sz!=b.size||a.n!=b.n||a.e!=b.e)return false;for(i in 1..6)if(field(i)!=other.field(i))return false;return true}
        override fun hashCode()=(0..6).fold(1){h,i->31*h+field(i).hashCode()}
        fun showsPrec(precedence:Long)=paren(precedence>10,"PrivateKey {private_pub = $publicKey, private_d = <secret>, private_p = <secret>, private_q = <secret>, private_dP = <secret>, private_dQ = <secret>, private_qinv = <secret>}")
        override fun toString()=showsPrec(0)
        fun debugShow()="PrivateKey {private_pub = $publicKey, private_d = $d, private_p = $p, private_q = $q, private_dP = $dP, private_dQ = $dQ, private_qinv = $qinv}"
        /** Host root conversion demands pub; use deferred conversion when its root must remain ignored. */
        fun asX509():X509RsaPrivateKey = X509RsaPrivateKey(publicKey.asX509(),X509SecretIntegers((1..6).map{i->cryptoRandomMemo{field(i) as BigInteger}}))
        companion object {
            fun deferred(pub:()->PublicKey,d:()->BigInteger,p:()->BigInteger,q:()->BigInteger,dP:()->BigInteger,dQ:()->BigInteger,qinv:()->BigInteger)=PrivateKey(listOf(cryptoRandomMemo(pub),cryptoRandomMemo(d),cryptoRandomMemo(p),cryptoRandomMemo(q),cryptoRandomMemo(dP),cryptoRandomMemo(dQ),cryptoRandomMemo(qinv)))
            internal fun source(fields:List<Lazy<Any?>>)=PrivateKey(fields)
            fun fromX509(v:X509RsaPrivateKey)=deferred({PublicKey.fromX509(v.publicKey)},{v.d},{v.p},{v.q},{v.dP},{v.dQ},{v.qinv})
        }
    }
    /** Source newtype: intrinsic WHNF demands the represented PrivateKey root. */
    class KeyPair private constructor(private val privateKey:Lazy<PrivateKey>):GenericRepRoot {
        constructor(value:PrivateKey):this(lazyOf(value))
        fun toPrivateKey()=privateKey.value
        fun toPublicKey()=toPrivateKey().publicKey
        override fun forceGenericRepRoot(){privateKey.value}
        override fun equals(other:Any?):Boolean {if(other !is KeyPair)return false;val a=toPrivateKey();a.publicKey.size;return a==other.toPrivateKey()}
        override fun hashCode()=toPrivateKey().hashCode()
        fun showsPrec(precedence:Long)=paren(precedence>10,"KeyPair ${toPrivateKey().showsPrec(11)}")
        override fun toString()=showsPrec(0)
        fun debugShow()="KeyPair (${toPrivateKey().debugShow()})"
        companion object {fun deferred(value:()->PrivateKey)=KeyPair(cryptoRandomMemo(value))}
    }
    fun toPublicKey(pair:KeyPair)=pair.toPublicKey()
    fun toPrivateKey(pair:KeyPair)=pair.toPrivateKey()
    fun privateSize(key:PrivateKey)=key.publicKey.size
    fun privateN(key:PrivateKey)=key.publicKey.n
    fun privateE(key:PrivateKey)=key.publicKey.e
    private fun paren(needed:Boolean,value:String)=if(needed)"($value)"else value
    val publicNFData:NFData<PublicKey> = NFData {value->val p=value();p.n;p.e;p.size;Unit}
    val privateNFData:NFData<PrivateKey> = NFData {value->val p=value();publicNFData.rnf{p.publicKey};p.d;p.p;p.q;p.dP;p.dQ;p.qinv;Unit}
    val pairNFData:NFData<KeyPair> = NFData {value->privateNFData.rnf{value().toPrivateKey()}}
}
