// Crypto.ECC.Simple.Types, crypton 2.0.1; licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import java.math.BigInteger

object CryptoEccSimpleTypes {
    sealed interface Tag { val name: EccCurveName }
    data object SEC_p112r1: Tag { override val name=EccCurveName.SEC_p112r1;override fun toString()="SEC_p112r1" }
    data object SEC_p112r2: Tag { override val name=EccCurveName.SEC_p112r2;override fun toString()="SEC_p112r2" }
    data object SEC_p128r1: Tag { override val name=EccCurveName.SEC_p128r1;override fun toString()="SEC_p128r1" }
    data object SEC_p128r2: Tag { override val name=EccCurveName.SEC_p128r2;override fun toString()="SEC_p128r2" }
    data object SEC_p160k1: Tag { override val name=EccCurveName.SEC_p160k1;override fun toString()="SEC_p160k1" }
    data object SEC_p160r1: Tag { override val name=EccCurveName.SEC_p160r1;override fun toString()="SEC_p160r1" }
    data object SEC_p160r2: Tag { override val name=EccCurveName.SEC_p160r2;override fun toString()="SEC_p160r2" }
    data object SEC_p192k1: Tag { override val name=EccCurveName.SEC_p192k1;override fun toString()="SEC_p192k1" }
    data object SEC_p192r1: Tag { override val name=EccCurveName.SEC_p192r1;override fun toString()="SEC_p192r1" }
    data object SEC_p224k1: Tag { override val name=EccCurveName.SEC_p224k1;override fun toString()="SEC_p224k1" }
    data object SEC_p224r1: Tag { override val name=EccCurveName.SEC_p224r1;override fun toString()="SEC_p224r1" }
    data object SEC_p256k1: Tag { override val name=EccCurveName.SEC_p256k1;override fun toString()="SEC_p256k1" }
    data object SEC_p256r1: Tag { override val name=EccCurveName.SEC_p256r1;override fun toString()="SEC_p256r1" }
    data object SEC_p384r1: Tag { override val name=EccCurveName.SEC_p384r1;override fun toString()="SEC_p384r1" }
    data object SEC_p521r1: Tag { override val name=EccCurveName.SEC_p521r1;override fun toString()="SEC_p521r1" }
    data object SEC_t113r1: Tag { override val name=EccCurveName.SEC_t113r1;override fun toString()="SEC_t113r1" }
    data object SEC_t113r2: Tag { override val name=EccCurveName.SEC_t113r2;override fun toString()="SEC_t113r2" }
    data object SEC_t131r1: Tag { override val name=EccCurveName.SEC_t131r1;override fun toString()="SEC_t131r1" }
    data object SEC_t131r2: Tag { override val name=EccCurveName.SEC_t131r2;override fun toString()="SEC_t131r2" }
    data object SEC_t163k1: Tag { override val name=EccCurveName.SEC_t163k1;override fun toString()="SEC_t163k1" }
    data object SEC_t163r1: Tag { override val name=EccCurveName.SEC_t163r1;override fun toString()="SEC_t163r1" }
    data object SEC_t163r2: Tag { override val name=EccCurveName.SEC_t163r2;override fun toString()="SEC_t163r2" }
    data object SEC_t193r1: Tag { override val name=EccCurveName.SEC_t193r1;override fun toString()="SEC_t193r1" }
    data object SEC_t193r2: Tag { override val name=EccCurveName.SEC_t193r2;override fun toString()="SEC_t193r2" }
    data object SEC_t233k1: Tag { override val name=EccCurveName.SEC_t233k1;override fun toString()="SEC_t233k1" }
    data object SEC_t233r1: Tag { override val name=EccCurveName.SEC_t233r1;override fun toString()="SEC_t233r1" }
    data object SEC_t239k1: Tag { override val name=EccCurveName.SEC_t239k1;override fun toString()="SEC_t239k1" }
    data object SEC_t283k1: Tag { override val name=EccCurveName.SEC_t283k1;override fun toString()="SEC_t283k1" }
    data object SEC_t283r1: Tag { override val name=EccCurveName.SEC_t283r1;override fun toString()="SEC_t283r1" }
    data object SEC_t409k1: Tag { override val name=EccCurveName.SEC_t409k1;override fun toString()="SEC_t409k1" }
    data object SEC_t409r1: Tag { override val name=EccCurveName.SEC_t409r1;override fun toString()="SEC_t409r1" }
    data object SEC_t571k1: Tag { override val name=EccCurveName.SEC_t571k1;override fun toString()="SEC_t571k1" }
    data object SEC_t571r1: Tag { override val name=EccCurveName.SEC_t571r1;override fun toString()="SEC_t571r1" }
    class Curve<C: Tag>(val tag: C) {
        val legacy: EccCurve get()=CryptoEccTypes.getCurveByName(tag.name)
        val parameters: Parameters<C> get()=legacy.common.let { Parameters(it.a,it.b,Point(it.generator),it.order,it.cofactor) }
        val type: CurveType get()=if(legacy is X509EcCurve.Prime) CurveType.Prime(legacy.modulus) else CurveType.Binary(legacy.modulus)
        val sizeBits: Int get()=CryptoEccTypes.curveSizeBits(legacy)
        val sizeBytes: Int get()=(sizeBits+7)/8
    }
    data class Parameters<C: Tag>(val a: BigInteger,val b: BigInteger,val generator: Point<C>,val order: BigInteger,val cofactor: BigInteger) {
        override fun toString()="CurveParameters {curveEccA = $a, curveEccB = $b, curveEccG = $generator, curveEccN = $order, curveEccH = $cofactor}"
    }
    sealed interface CurveType {
        data class Prime(val modulus: BigInteger): CurveType { override fun toString()="CurvePrime (CurvePrimeParam $modulus)" }
        data class Binary(val polynomial: BigInteger): CurveType { override fun toString()="CurveBinary (CurveBinaryParam $polynomial)" }
    }
    data class Scalar<C: Tag>(val integer: BigInteger) {
        fun force()=Unit
        fun show(precedence: Int=0): String {
            val body="Scalar "+if(integer.signum()<0) "($integer)" else integer.toString()
            return if(precedence>10) "($body)" else body
        }
        override fun toString()=show()
    }
    data class Point<C: Tag>(val raw: EccPoint) {
        constructor(x: BigInteger,y: BigInteger): this(X509EcPoint.Affine(x,y))
        fun force()=CryptoEccTypes.forcePoint(raw)
        override fun toString()=raw.toString()
        companion object { fun <C: Tag> infinity()=Point<C>(X509EcPoint.Infinity) }
    }
    fun <C: Tag> curve(tag: C)=Curve(tag)
    private val tags: List<Tag> = listOf(SEC_p112r1,SEC_p112r2,SEC_p128r1,SEC_p128r2,SEC_p160k1,SEC_p160r1,SEC_p160r2,SEC_p192k1,SEC_p192r1,SEC_p224k1,SEC_p224r1,SEC_p256k1,SEC_p256r1,SEC_p384r1,SEC_p521r1,SEC_t113r1,SEC_t113r2,SEC_t131r1,SEC_t131r2,SEC_t163k1,SEC_t163r1,SEC_t163r2,SEC_t193r1,SEC_t193r2,SEC_t233k1,SEC_t233r1,SEC_t239k1,SEC_t283k1,SEC_t283r1,SEC_t409k1,SEC_t409r1,SEC_t571k1,SEC_t571r1)
    fun forName(name: EccCurveName)=Curve(tags[name.ordinal])
}
