// Crypto.PubKey.Ed448, crypton2.0.1; licenses/crypton-BSD-3-Clause.txt.
// RAM0.22.1 byte storage observations; licenses/ram-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.formats.HaskellString
import java.lang.ref.Cleaner
import java.lang.ref.Reference

class CryptoEd448(val digitBits: Int=64,val scalarBits: Int=digitBits,val vectorMasks32: Boolean=false) {
    init {require(digitBits==32 || digitBits==64)}
    private class SecretStorage(bytes: ByteArray) {
        private val raw=bytes.copyOf()
        private class Wipe(private val bytes: ByteArray): Runnable {override fun run(){bytes.fill(0)}}
        private val cleanup=cleaner.register(this,Wipe(raw))
        fun copy(): ByteArray=try {raw.copyOf()}finally {Reference.reachabilityFence(this)}
        fun same(other: SecretStorage): Boolean=try {var difference=0;for(i in 0..<57)difference=difference or(raw[i].toInt() xor other.raw[i].toInt());difference==0}finally {Reference.reachabilityFence(this);Reference.reachabilityFence(other)}
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
    class Signature private constructor(private val raw: ByteArray) {
        fun toByteArray()=raw.copyOf()
        fun <T> withByteArray(action: (ByteArray)->T)=action(toByteArray())
        fun force()=Unit
        fun show(precedence: Int=0)=constructor("Signature "+HaskellString.quoteCodePoints(IntArray(raw.size){raw[it].toInt() and 255}),precedence)
        override fun toString()=show()
        override fun equals(other: Any?)=other is Signature && raw.contentEquals(other.raw)
        override fun hashCode()=raw.contentHashCode()
        companion object {internal fun of(bytes: ByteArray)=Signature(bytes.copyOf())}
    }
    fun secretKey(bytes: ByteArray): CryptoEcc.Failable<SecretKey> = if(bytes.size==secretKeySize)CryptoEcc.Failable.Passed(SecretKey.of(bytes))else CryptoEcc.Failable.Failed(CryptoEcc.Error.CryptoError_SecretKeyStructureInvalid)
    fun publicKey(bytes: ByteArray): CryptoEcc.Failable<PublicKey> = if(bytes.size==publicKeySize)CryptoEcc.Failable.Passed(PublicKey.of(bytes))else CryptoEcc.Failable.Failed(CryptoEcc.Error.CryptoError_PublicKeySizeInvalid)
    fun signature(bytes: ByteArray): CryptoEcc.Failable<Signature> = if(bytes.size==signatureSize)CryptoEcc.Failable.Passed(Signature.of(bytes))else CryptoEcc.Failable.Failed(CryptoEcc.Error.CryptoError_SecretKeyStructureInvalid)
    fun generateSecretKey(random: (Int)->ByteArray): SecretKey {
        val source=random(secretKeySize)
        require(source.size==secretKeySize){"random provider returned ${source.size} bytes for57-byte request"}
        return SecretKey.of(source)
    }
    fun toPublic(secret: SecretKey): PublicKey {
        val bytes=secret.toByteArray();val out=ByteArray(publicKeySize)
        try {engine().derivePublicKey(CryptoByteSpan(out),CryptoByteSpan(bytes));return PublicKey.of(out)}finally {bytes.fill(0)}
    }
    @Suppress("UNUSED_PARAMETER")
    fun sign(secret: SecretKey,public: PublicKey,message: ByteArray): Signature=unsafeSign(secret,toPublic(secret),message)
    fun unsafeSign(secret: SecretKey,public: PublicKey,message: ByteArray): Signature {
        val bytes=secret.toByteArray();val out=ByteArray(signatureSize)
        try {engine().sign(CryptoByteSpan(out),CryptoByteSpan(bytes),CryptoByteSpan(public.toByteArray()),CryptoByteSpan(message),0,CryptoByteSpan(ByteArray(0)));return Signature.of(out)}finally {bytes.fill(0)}
    }
    fun verify(public: PublicKey,message: ByteArray,signature: Signature)=engine().verify(CryptoByteSpan(signature.toByteArray()),CryptoByteSpan(public.toByteArray()),CryptoByteSpan(message),0,CryptoByteSpan(ByteArray(0)))!=0
    fun secretShowDeferred(value: Lazy<SecretKey>,precedence: Int=0)=constructor("SecretKey <scrubbed-bytes>",precedence)
    fun publicShowDeferred(value: Lazy<PublicKey>,precedence: Int=0)=value.value.show(precedence)
    fun signatureShowDeferred(value: Lazy<Signature>,precedence: Int=0)=value.value.show(precedence)
    fun secretForceDeferred(value: Lazy<SecretKey>)=value.value.force()
    fun publicForceDeferred(value: Lazy<PublicKey>)=value.value.force()
    fun signatureForceDeferred(value: Lazy<Signature>)=value.value.force()
    @Suppress("UNUSED_PARAMETER")
    fun signDeferred(secret: Lazy<SecretKey>,public: Lazy<PublicKey>,message: Lazy<ByteArray>): Signature {val bytes=message.value;val key=secret.value;return unsafeSign(key,toPublic(key),bytes)}
    fun unsafeSignDeferred(secret: Lazy<SecretKey>,public: Lazy<PublicKey>,message: Lazy<ByteArray>): Signature {val bytes=message.value;val key=secret.value;val point=public.value;return unsafeSign(key,point,bytes)}
    fun verifyDeferred(public: Lazy<PublicKey>,message: Lazy<ByteArray>,signature: Lazy<Signature>): Boolean {val point=public.value;val sig=signature.value;val bytes=message.value;return verify(point,bytes,sig)}
    private fun engine()=CryptoDecafEd448(digitBits,scalarBits,vectorMasks32)
    companion object {
        const val publicKeySize=57
        const val secretKeySize=57
        const val signatureSize=114
        private fun constructor(body: String,precedence: Int)=if(precedence>10)"($body)"else body
    }
}
