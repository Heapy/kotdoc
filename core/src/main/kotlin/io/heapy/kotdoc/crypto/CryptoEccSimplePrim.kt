// Crypto.ECC.Simple.Prim, crypton 2.0.1; licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import io.heapy.kotdoc.crypto.CryptoEccSimpleTypes.Tag
import io.heapy.kotdoc.crypto.CryptoEccSimpleTypes.Curve
import io.heapy.kotdoc.crypto.CryptoEccSimpleTypes.Scalar
import io.heapy.kotdoc.crypto.CryptoEccSimpleTypes.Point
import java.math.BigInteger

object CryptoEccSimplePrim {
    fun <C: Tag> scalarGenerate(c: Curve<C>,random: (Int)->ByteArray)=Scalar<C>(EccLegacyRandom.generateMax(c.parameters.order-BigInteger.ONE,random)+BigInteger.ONE)
    fun <C: Tag> scalarFromInteger(c: Curve<C>,n: BigInteger): CryptoEcc.Failable<Scalar<C>> = if(n.signum()<0 || n>=c.legacy.modulus) CryptoEcc.Failable.Failed(CryptoEcc.Error.CryptoError_EcScalarOutOfBounds) else CryptoEcc.Failable.Passed(Scalar(n))
    fun <C: Tag> pointAdd(c: Curve<C>,p: Point<C>,q: Point<C>)=Point<C>(CryptoEccPrim.pointAdd(c.legacy,p.raw,q.raw))
    fun <C: Tag> pointNegate(c: Curve<C>,p: Point<C>)=Point<C>(CryptoEccPrim.pointNegate(c.legacy,p.raw))
    fun <C: Tag> pointDouble(c: Curve<C>,p: Point<C>)=Point<C>(CryptoEccPrim.pointDouble(c.legacy,p.raw))
    fun <C: Tag> pointBaseMul(c: Curve<C>,n: Scalar<C>)=pointMul(c,n,c.parameters.generator)
    fun <C: Tag> pointMul(c: Curve<C>,n: Scalar<C>,p: Point<C>): Point<C> {
        if(p.raw===X509EcPoint.Infinity || n.integer==BigInteger.ZERO) return Point.infinity()
        if(n.integer.signum()<0) return pointNegate(c,pointMul(c,Scalar(-n.integer),p))
        val q=p.raw as X509EcPoint.Affine
        if(CryptoEccPrim.isPointValid(c.legacy,q)) {
            val cc=c.legacy.common
            val field=if(c.legacy is X509EcCurve.Prime) EccCurveField.Prime(c.legacy.modulus,cc.a,cc.b) else EccCurveField.Binary(c.legacy.modulus,cc.b)
            when(val r=CryptoEccInternal.curveMul(field,cc.order,n.integer,q.x,q.y,c.legacy is X509EcCurve.Prime && q==cc.generator)) {
                is EccMulResult.Point->return Point(r.x,r.y)
                EccMulResult.Infinity->return Point.infinity()
                EccMulResult.Unsupported->Unit
            }
        }
        // On the 33 named curves a valid P256 point is supported above. An
        // off-curve P256 point bypasses the legacy specialized dispatch too.
        return Point(CryptoEccPrim.pointMul(c.legacy,n.integer,q))
    }
    fun <C: Tag> pointAddTwoMuls(c: Curve<C>,a: Scalar<C>,p: Point<C>,b: Scalar<C>,q: Point<C>)=pointAdd(c,pointMul(c,a,p),pointMul(c,b,q))
    fun <C: Tag> pointFromIntegers(c: Curve<C>,x: BigInteger,y: BigInteger): CryptoEcc.Failable<Point<C>> {
        if(!isPointValid(c,x,y)) return CryptoEcc.Failable.Failed(CryptoEcc.Error.CryptoError_PointCoordinatesInvalid)
        val p=Point<C>(x,y)
        if(!isPointInSubgroup(c,p)) return CryptoEcc.Failable.Failed(CryptoEcc.Error.CryptoError_PointSubgroupInvalid)
        return CryptoEcc.Failable.Passed(p)
    }
    fun <C: Tag> isPointAtInfinity(p: Point<C>)=p.raw===X509EcPoint.Infinity
    fun <C: Tag> isPointValid(c: Curve<C>,x: BigInteger,y: BigInteger)=CryptoEccPrim.isPointValid(c.legacy,X509EcPoint.Affine(x,y))
    fun <C: Tag> isPointInSubgroup(c: Curve<C>,p: Point<C>)=c.parameters.cofactor==BigInteger.ONE || isPointAtInfinity(pointMul(c,Scalar(c.parameters.order),p))
}
