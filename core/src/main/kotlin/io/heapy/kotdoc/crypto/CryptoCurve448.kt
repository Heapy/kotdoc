// Crypto.PubKey.Curve448, crypton2.0.1; licenses/crypton-BSD-3-Clause.txt.
// RAM0.22.1 byte storage observations; licenses/ram-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.formats.HaskellString
import java.lang.ref.Cleaner
import java.lang.ref.Reference

class CryptoCurve448(val digitBits: Int=64,val scalarBits: Int=digitBits,val vectorMasks32: Boolean=false) {
    init {require(digitBits==32 || digitBits==64)}
    private class SecretStorage(bytes: ByteArray) {
        private val raw=bytes.copyOf()
        private class Wipe(private val bytes: ByteArray): Runnable {override fun run(){bytes.fill(0)}}
        private val cleanup=cleaner.register(this,Wipe(raw))
        fun copy(): ByteArray=try {raw.copyOf()}finally {Reference.reachabilityFence(this)}
        fun same(other: SecretStorage): Boolean=try {var difference=0;for(i in 0..<56)difference=difference or(raw[i].toInt() xor other.raw[i].toInt());difference==0}finally {Reference.reachabilityFence(this);Reference.reachabilityFence(other)}
        companion object {private val cleaner=Cleaner.create()}
    }
    class SecretKey private constructor(private val storage: SecretStorage) {
        fun toByteArray()=storage.copy()
        fun <T> withByteArray(action: (ByteArray)->T)=action(toByteArray())
        fun force()=Unit
        fun show(precedence: Int=0)=constructor("SecretKey <scrubbed-bytes>",precedence)
        fun debugShow()="SecretKey "+toByteArray().toHexString()
        override fun toString()=show()
        override fun equals(other: Any?)=other is SecretKey && storage.same(other.storage)
        override fun hashCode()=0
        companion object {internal fun of(bytes: ByteArray)=SecretKey(SecretStorage(bytes))}
    }
    class DhSecret private constructor(private val storage: SecretStorage) {
        fun toByteArray()=storage.copy()
        fun <T> withByteArray(action: (ByteArray)->T)=action(toByteArray())
        fun force()=Unit
        fun show(precedence: Int=0)=constructor("DhSecret <scrubbed-bytes>",precedence)
        override fun toString()=show()
        override fun equals(other: Any?)=other is DhSecret && storage.same(other.storage)
        override fun hashCode()=0
        companion object {internal fun of(bytes: ByteArray)=DhSecret(SecretStorage(bytes))}
    }
    class PublicKey private constructor(private val raw: ByteArray) {
        fun toByteArray()=raw.copyOf()
        fun <T> withByteArray(action: (ByteArray)->T)=action(toByteArray())
        fun force()=Unit
        fun show(precedence: Int=0)=constructor("PublicKey "+HaskellString.quoteCodePoints(IntArray(raw.size){raw[it].toInt() and 255}),precedence)
        override fun toString()=show()
        override fun equals(other: Any?)=other is PublicKey && raw.contentEquals(other.raw)
        override fun hashCode()=raw.contentHashCode()
        companion object {internal fun of(bytes: ByteArray)=PublicKey(bytes.copyOf())}
    }
    fun secretKey(bytes: ByteArray): CryptoEcc.Failable<SecretKey> = if(bytes.size==56)CryptoEcc.Failable.Passed(SecretKey.of(bytes))else CryptoEcc.Failable.Failed(CryptoEcc.Error.CryptoError_SecretKeySizeInvalid)
    fun publicKey(bytes: ByteArray): CryptoEcc.Failable<PublicKey> = if(bytes.size==56)CryptoEcc.Failable.Passed(PublicKey.of(bytes))else CryptoEcc.Failable.Failed(CryptoEcc.Error.CryptoError_PublicKeySizeInvalid)
    fun dhSecret(bytes: ByteArray): CryptoEcc.Failable<DhSecret> = if(bytes.size==56)CryptoEcc.Failable.Passed(DhSecret.of(bytes))else CryptoEcc.Failable.Failed(CryptoEcc.Error.CryptoError_SharedSecretSizeInvalid)
    fun generateSecretKey(random: (Int)->ByteArray): SecretKey {
        val source=random(56)
        require(source.size==56){"random provider returned ${source.size} bytes for56-byte request"}
        return SecretKey.of(source)
    }
    fun toPublic(secret: SecretKey): PublicKey {
        val bytes=secret.toByteArray();val out=ByteArray(56)
        try {engine().derivePublicKey(CryptoByteSpan(out),CryptoByteSpan(bytes));return PublicKey.of(out)}finally {bytes.fill(0)}
    }
    fun dh(public: PublicKey,secret: SecretKey): DhSecret {
        val bytes=secret.toByteArray();val out=ByteArray(56)
        try {engine().x448(CryptoByteSpan(out),CryptoByteSpan(public.toByteArray()),CryptoByteSpan(bytes));return DhSecret.of(out)}finally {bytes.fill(0);out.fill(0)}
    }
    private fun engine()=CryptoDecaf448PointMultiplication(CryptoDecaf448Points(digitBits,scalarBits,vectorMasks32))
    companion object {private fun constructor(body: String,precedence: Int)=if(precedence>10)"($body)"else body}
}
