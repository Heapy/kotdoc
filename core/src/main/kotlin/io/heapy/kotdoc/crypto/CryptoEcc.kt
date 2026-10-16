// Crypto.ECC prime-curve instances, crypton 2.0.1; licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.crypto.CryptoEccSimpleTypes.Tag
import io.heapy.kotdoc.crypto.CryptoEccSimpleTypes.Curve
import io.heapy.kotdoc.crypto.CryptoEccSimpleTypes.Scalar
import io.heapy.kotdoc.crypto.CryptoEccSimpleTypes.Point
import io.heapy.kotdoc.x509.X509EcPoint
import java.math.BigInteger

/** Prime adapters only; X25519/X448/Edwards are separate remaining checkpoints. */
object CryptoEcc {
    enum class Error { CryptoError_PointSizeInvalid,CryptoError_PointFormatInvalid,CryptoError_PublicKeySizeInvalid,CryptoError_SecretKeySizeInvalid,CryptoError_PointCoordinatesInvalid,CryptoError_PointSubgroupInvalid,CryptoError_ScalarMultiplicationInvalid,CryptoError_EcScalarOutOfBounds }
    sealed interface Failable<out T> {
        data class Passed<T>(val value: T): Failable<T>
        data class Failed(val error: Error): Failable<Nothing>
    }
    fun <T> Failable<T>.getOrThrow(): T=when(this) {is Failable.Passed->value;is Failable.Failed->error(error.name)}
    private fun <T,R> map(value: Failable<T>,f: (T)->R): Failable<R> = when(value) {is Failable.Passed->Failable.Passed(f(value.value));is Failable.Failed->value}
    class KeyPair<P,S>(val public: P,val private: S)
    class SharedSecret(bytes: ByteArray) {
        private val key=CryptoEccDh.SharedKey(bytes)
        fun toByteArray()=key.toByteArray()
        fun force()=key.force()
        operator fun plus(other: SharedSecret)=SharedSecret(toByteArray()+other.toByteArray())
        override fun equals(other: Any?)=other is SharedSecret && key==other.key
        override fun hashCode()=0
        companion object { fun empty()=SharedSecret(ByteArray(0)) }
    }
    interface EllipticCurve<P,S> {
        val sizeBits: Int
        val orderBits: Int
        fun generateScalar(random: (Int)->ByteArray): S
        fun generateKeyPair(random: (Int)->ByteArray): KeyPair<P,S> { val s=generateScalar(random);return KeyPair(scalarToPoint(s),s) }
        fun encodePoint(p: P): ByteArray
        fun decodePoint(bytes: ByteArray): Failable<P>
        fun encodeScalar(s: S): ByteArray
        fun decodeScalar(bytes: ByteArray): Failable<S>
        fun scalarToPoint(s: S): P
        fun pointAdd(a: P,b: P): P
        fun pointNegate(p: P): P
        fun pointSmul(s: S,p: P): P
        fun pointBaseSmul(s: S): P=scalarToPoint(s)
        fun pointsSmulVarTime(a: S,b: S,p: P): P=pointAdd(pointBaseSmul(a),pointSmul(b,p))
        fun scalarToInteger(s: S): BigInteger
        fun scalarFromInteger(n: BigInteger): Failable<S>
        fun scalarAdd(a: S,b: S): S
        fun scalarMul(a: S,b: S): S
        fun ecdh(s: S,p: P): Failable<SharedSecret>
        fun ecdhRaw(s: S,p: P): SharedSecret=ecdh(s,p).getOrThrow()
    }
    object P256: EllipticCurve<CryptoP256.Point,CryptoP256.Scalar> {
        private val raw=CryptoP256()
        override val sizeBits=256
        override val orderBits=256
        private fun <T> convert(v: CryptoP256.Failable<T>): Failable<T> = when(v) {is CryptoP256.Failable.Passed->Failable.Passed(v.value);is CryptoP256.Failable.Failed->Failable.Failed(Error.valueOf(v.error.name))}
        override fun generateScalar(random: (Int)->ByteArray)=raw.scalarGenerate(random)
        override fun encodePoint(p: CryptoP256.Point)=byteArrayOf(4)+raw.pointToBinary(p)
        override fun decodePoint(bytes: ByteArray): Failable<CryptoP256.Point> = when {
            bytes.isEmpty()->Failable.Failed(Error.CryptoError_PointSizeInvalid)
            bytes[0]!=4.toByte()->Failable.Failed(Error.CryptoError_PointFormatInvalid)
            else->convert(raw.pointFromBinary(bytes.copyOfRange(1,bytes.size)))
        }
        override fun encodeScalar(s: CryptoP256.Scalar)=raw.scalarToBinary(s)
        override fun decodeScalar(bytes: ByteArray)=convert(raw.scalarFromBinary(bytes))
        override fun scalarToPoint(s: CryptoP256.Scalar)=raw.toPoint(s)
        override fun pointAdd(a: CryptoP256.Point,b: CryptoP256.Point)=raw.pointAdd(a,b)
        override fun pointNegate(p: CryptoP256.Point)=raw.pointNegate(p)
        override fun pointSmul(s: CryptoP256.Scalar,p: CryptoP256.Point)=raw.pointMul(s,p)
        override fun pointsSmulVarTime(a: CryptoP256.Scalar,b: CryptoP256.Scalar,p: CryptoP256.Point)=raw.pointsMulVarTime(a,b,p)
        override fun scalarToInteger(s: CryptoP256.Scalar)=raw.scalarToInteger(s)
        override fun scalarFromInteger(n: BigInteger)=convert(raw.scalarFromInteger(n))
        override fun scalarAdd(a: CryptoP256.Scalar,b: CryptoP256.Scalar)=raw.scalarAdd(a,b)
        override fun scalarMul(a: CryptoP256.Scalar,b: CryptoP256.Scalar)=raw.scalarMul(a,b)
        override fun ecdhRaw(s: CryptoP256.Scalar,p: CryptoP256.Point)=SharedSecret(raw.pointDh(s,p))
        override fun ecdh(s: CryptoP256.Scalar,p: CryptoP256.Point): Failable<SharedSecret> = if(raw.pointIsAtInfinity(p) || raw.scalarIsZero(s) || raw.scalarCmp(s,raw.scalarN)==0) Failable.Failed(Error.CryptoError_ScalarMultiplicationInvalid) else Failable.Passed(ecdhRaw(s,p))
    }
    open class SimplePrime<C: Tag> internal constructor(val curve: Curve<C>): EllipticCurve<Point<C>,Scalar<C>> {
        override val sizeBits get()=curve.sizeBits
        override val orderBits get()=CryptoNumberBasic.numBits(curve.parameters.order)
        private val orderBytes get()=(orderBits+7)/8
        override fun generateScalar(random: (Int)->ByteArray)=CryptoEccSimplePrim.scalarGenerate(curve,random)
        override fun encodePoint(p: Point<C>): ByteArray {
            val q=p.raw
            if(q===X509EcPoint.Infinity) error("encodeECPoint: cannot serialize point at infinity")
            q as X509EcPoint.Affine
            return byteArrayOf(4)+CryptoSerialize.i2ospOf_(curve.sizeBytes,q.x)+CryptoSerialize.i2ospOf_(curve.sizeBytes,q.y)
        }
        override fun decodePoint(bytes: ByteArray): Failable<Point<C>> {
            if(bytes.isEmpty()) return Failable.Failed(Error.CryptoError_PointSizeInvalid)
            if(bytes[0]!=4.toByte()) return Failable.Failed(Error.CryptoError_PointFormatInvalid)
            val half=(bytes.size-1)/2
            return CryptoEccSimplePrim.pointFromIntegers(curve,CryptoSerialize.os2ip(bytes.copyOfRange(1,1+half)),CryptoSerialize.os2ip(bytes.copyOfRange(1+half,bytes.size)))
        }
        override fun encodeScalar(s: Scalar<C>)=CryptoSerialize.i2ospOf_(orderBytes,s.integer)
        override fun decodeScalar(bytes: ByteArray): Failable<Scalar<C>> = if(bytes.size!=orderBytes) Failable.Failed(Error.CryptoError_SecretKeySizeInvalid) else Failable.Passed(Scalar(CryptoSerialize.os2ip(bytes)))
        override fun scalarToPoint(s: Scalar<C>)=CryptoEccSimplePrim.pointBaseMul(curve,s)
        override fun pointAdd(a: Point<C>,b: Point<C>)=CryptoEccSimplePrim.pointAdd(curve,a,b)
        override fun pointNegate(p: Point<C>)=CryptoEccSimplePrim.pointNegate(curve,p)
        override fun pointSmul(s: Scalar<C>,p: Point<C>)=CryptoEccSimplePrim.pointMul(curve,s,p)
        override fun pointsSmulVarTime(a: Scalar<C>,b: Scalar<C>,p: Point<C>)=CryptoEccSimplePrim.pointAddTwoMuls(curve,a,curve.parameters.generator,b,p)
        override fun scalarToInteger(s: Scalar<C>)=s.integer
        override fun scalarFromInteger(n: BigInteger): Failable<Scalar<C>> = if(CryptoNumberBasic.numBits(n)>8*orderBytes) Failable.Failed(Error.CryptoError_SecretKeySizeInvalid) else Failable.Passed(Scalar(n))
        override fun scalarAdd(a: Scalar<C>,b: Scalar<C>)=Scalar<C>((a.integer+b.integer).mod(curve.parameters.order))
        override fun scalarMul(a: Scalar<C>,b: Scalar<C>)=Scalar<C>((a.integer*b.integer).mod(curve.parameters.order))
        override fun ecdh(s: Scalar<C>,p: Point<C>): Failable<SharedSecret> {
            val q=pointSmul(s,p).raw
            if(q===X509EcPoint.Infinity) return Failable.Failed(Error.CryptoError_ScalarMultiplicationInvalid)
            q as X509EcPoint.Affine
            return Failable.Passed(SharedSecret(CryptoSerialize.i2ospOf_(curve.sizeBytes,q.x)))
        }
    }
    object P384: SimplePrime<CryptoEccSimpleTypes.SEC_p384r1>(CryptoEccSimpleTypes.curve(CryptoEccSimpleTypes.SEC_p384r1))
    object P521: SimplePrime<CryptoEccSimpleTypes.SEC_p521r1>(CryptoEccSimpleTypes.curve(CryptoEccSimpleTypes.SEC_p521r1))
}
