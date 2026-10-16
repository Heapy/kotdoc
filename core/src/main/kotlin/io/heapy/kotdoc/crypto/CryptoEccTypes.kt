// Crypto.PubKey.ECC.Types, crypton 2.0.1; licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
typealias EccCurve = X509EcCurve
typealias EccPoint = X509EcPoint
typealias EccCurvePrime = X509EcCurve.Prime
typealias EccCurveBinary = X509EcCurve.Binary
typealias EccPrivateNumber = java.math.BigInteger
typealias EccPublicPoint = X509EcPoint
typealias EccCurveCommon = X509EcCurveCommon
typealias EccCurveName = X509CurveName
/** Reuses the complete source-derived 33-curve parameter table and SEC OIDs. */
object CryptoEccTypes {
    fun getCurveByName(name: EccCurveName) = X509Ec.getCurveByName(name)
    fun curveSizeBits(curve: EccCurve) = X509Ec.curveSizeBits(curve)
    fun commonCurve(curve: EccCurve) = curve.common
    fun eccP(curve: EccCurvePrime) = curve.modulus
    fun eccFx(curve: EccCurveBinary) = curve.modulus
    fun forcePoint(point: EccPoint) { if(point is X509EcPoint.Affine) { point.x;point.y } }
    fun forceBinary(curve: EccCurveBinary) { curve.modulus;curve.common }
    fun curveOid(name: EccCurveName) = X509CurveOids.forCurve(name)
}
