// Crypto.PubKey.ECC.DH and Number.Generate, crypton 2.0.1; licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.X509EcPoint
import java.math.BigInteger
import java.lang.ref.Cleaner
import java.lang.ref.Reference

internal object EccLegacyRandom {
    fun generateMax(range: BigInteger,random: (Int)->ByteArray): BigInteger {
        if(range<=BigInteger.ONE) return BigInteger.ZERO
        val bits=CryptoNumberBasic.numBits(range)
        fun generate(n: Int): BigInteger {
            val bytes=random((n+7)/8).copyOf()
            require(bytes.size==(n+7)/8) { "random byte provider returned incorrect length" }
            bytes[0]=(bytes[0].toInt() and (255 ushr ((8-n%8)%8))).toByte()
            return BigInteger(1,bytes)
        }
        if(range<BigInteger.valueOf(127)) return generate(bits).mod(range)
        val over=bits>3 && !range.testBit(bits-2) && !range.testBit(bits-3)
        repeat(100) {
            val r=generate(bits+if(over) 1 else 0)
            if(r<range) return r
            if(over) { val r2=r-range;if(r2<range) return r2;val r3=r2-range;if(r3<range) return r3 }
        }
        error("internal: generateMax($range bits=$bits) (${if(over) "over" else "normal"}) doesn't seems to work properly")
    }
}
object CryptoEccDh {
    enum class Error { CryptoError_PointCoordinatesInvalid,CryptoError_PointSubgroupInvalid,CryptoError_ScalarMultiplicationInvalid }
    class SharedKey(bytes: ByteArray) {
        private val contents=bytes.copyOf()
        private class Wipe(private val bytes: ByteArray): Runnable { override fun run() { bytes.fill(0) } }
        private val cleanup=cleaner.register(this,Wipe(contents))
        fun toByteArray(): ByteArray = try { contents.copyOf() } finally { Reference.reachabilityFence(this) }
        fun force()=Unit
        override fun equals(other: Any?): Boolean {
            if(other !is SharedKey || contents.size!=other.contents.size) return false
            return try {
                var difference=0
                for(i in contents.indices) difference=difference or (contents[i].toInt() xor other.contents[i].toInt())
                difference==0
            } finally { Reference.reachabilityFence(this);Reference.reachabilityFence(other) }
        }
        override fun hashCode()=0
        companion object { private val cleaner=Cleaner.create() }
        override fun toString()="SharedKey <scrubbed-bytes>"
    }
    sealed interface Result {
        data class Passed(val key: SharedKey): Result
        data class Failed(val error: Error): Result
    }
    fun generatePrivate(c: EccCurve,randomBytes: (Int)->ByteArray) = EccLegacyRandom.generateMax(c.common.order,randomBytes)
    fun calculatePublic(c: EccCurve,d: BigInteger) = CryptoEccPrim.pointMul(c,d,c.common.generator)
    fun tryGetShared(c: EccCurve,d: BigInteger,q: EccPoint): Result {
        if(!CryptoEccPrim.isPointValid(c,q)) return Result.Failed(Error.CryptoError_PointCoordinatesInvalid)
        if(!CryptoEccPrim.isPointInSubgroup(c,q)) return Result.Failed(Error.CryptoError_PointSubgroupInvalid)
        return when(val p=CryptoEccPrim.pointMul(c,d,q)) {
            X509EcPoint.Infinity -> Result.Failed(Error.CryptoError_ScalarMultiplicationInvalid)
            is X509EcPoint.Affine -> Result.Passed(SharedKey(CryptoSerialize.i2ospOf_((CryptoEccTypes.curveSizeBits(c)+7)/8,p.x)))
        }
    }
    fun getShared(c: EccCurve,d: BigInteger,q: EccPoint): SharedKey = when(val r=tryGetShared(c,d,q)) {
        is Result.Passed -> r.key
        is Result.Failed -> error(r.error.name)
    }
}
