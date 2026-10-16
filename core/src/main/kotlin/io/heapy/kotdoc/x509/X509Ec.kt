// Data.X509.EC, crypton-x509 1.9.2; Crypto.PubKey.ECC.{Types,Prim}, crypton 2.0.1.
// Vincent Hanquez; full notices: licenses/crypton-x509-BSD-3-Clause.txt, crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.x509

import io.heapy.kotdoc.asn1.Asn1Maybe
import io.heapy.kotdoc.asn1.Asn1Oid
import java.math.BigInteger

sealed class X509EcPoint {
    data object Infinity : X509EcPoint() { override fun toString() = "PointO" }
    class Affine private constructor(private val xx: Lazy<BigInteger>, private val yy: Lazy<BigInteger>) : X509EcPoint() {
        constructor(x: BigInteger, y: BigInteger) : this(lazyOf(x), lazyOf(y))
        val x get() = xx.value; val y get() = yy.value
        override fun equals(other: Any?) = other is Affine && x == other.x && y == other.y
        override fun hashCode() = 31 * x.hashCode() + y.hashCode()
        override fun toString() = "Point ${x509Arg(x)} ${x509Arg(y)}"
        companion object { fun deferred(x: () -> BigInteger, y: () -> BigInteger) = Affine(lazy(x), lazy(y)) }
    }
}
class X509EcCurveCommon private constructor(private val aa: Lazy<BigInteger>, private val bb: Lazy<BigInteger>, private val gg: Lazy<X509EcPoint>, private val nn: Lazy<BigInteger>, private val hh: Lazy<BigInteger>) {
    constructor(a: BigInteger, b: BigInteger, generator: X509EcPoint, order: BigInteger, cofactor: BigInteger) : this(lazyOf(a), lazyOf(b), lazyOf(generator), lazyOf(order), lazyOf(cofactor))
    val a get() = aa.value; val b get() = bb.value; val generator get() = gg.value; val order get() = nn.value; val cofactor get() = hh.value
    override fun equals(other: Any?) = other is X509EcCurveCommon && a == other.a && b == other.b && generator == other.generator && order == other.order && cofactor == other.cofactor
    override fun hashCode() = listOf(a,b,generator,order,cofactor).hashCode()
    override fun toString(): String {
        val aText = a.toString(); val bText = b.toString(); val gText = generator.toString(); val nText = order.toString()
        return "CurveCommon {ecc_a = $aText, ecc_b = $bText, ecc_g = $gText, ecc_n = $nText, ecc_h = $cofactor}"
    }
    companion object { fun deferred(a: () -> BigInteger, b: () -> BigInteger, generator: () -> X509EcPoint, order: () -> BigInteger, cofactor: () -> BigInteger) = X509EcCurveCommon(lazy(a), lazy(b), lazy(generator), lazy(order), lazy(cofactor)) }
}
sealed class X509EcCurve {
    abstract val modulus: BigInteger
    abstract val common: X509EcCurveCommon
    class Prime private constructor(private val pp: Lazy<BigInteger>, private val cc: Lazy<X509EcCurveCommon>) : X509EcCurve() {
        constructor(prime: BigInteger, common: X509EcCurveCommon) : this(lazyOf(prime), lazyOf(common))
        override val modulus get() = pp.value; override val common get() = cc.value
        override fun equals(other: Any?) = other is Prime && modulus == other.modulus && common == other.common
        override fun hashCode() = 31 * modulus.hashCode() + common.hashCode()
        override fun toString(): String { val p = x509Arg(modulus); return "CurveFP (CurvePrime $p ($common))" }
        companion object { fun deferred(prime: () -> BigInteger, common: () -> X509EcCurveCommon) = Prime(lazy(prime),lazy(common)) }
    }
    class Binary private constructor(private val ff: Lazy<BigInteger>, private val cc: Lazy<X509EcCurveCommon>) : X509EcCurve() {
        constructor(polynomial: BigInteger, common: X509EcCurveCommon) : this(lazyOf(polynomial), lazyOf(common))
        override val modulus get() = ff.value; override val common get() = cc.value
        override fun equals(other: Any?) = other is Binary && modulus == other.modulus && common == other.common
        override fun hashCode() = 31 * modulus.hashCode() + common.hashCode()
        override fun toString(): String { val f = x509Arg(modulus); return "CurveF2m (CurveBinary $f ($common))" }
        companion object { fun deferred(polynomial: () -> BigInteger, common: () -> X509EcCurveCommon) = Binary(lazy(polynomial),lazy(common)) }
    }
}
object X509Ec {
    fun getCurveByName(name: X509CurveName) = X509NamedCurveParameters.curves[name.ordinal]
    fun lookupCurveNameByOid(oid: Asn1Oid) = X509CurveOids.byOid(oid)
    fun curveSizeBits(curve: X509EcCurve) = curve.modulus.abs().bitLength().coerceAtLeast(1) - if (curve is X509EcCurve.Binary) 1 else 0
    private fun binaryMod(f: BigInteger, input: BigInteger): BigInteger {
        if (f.signum() < 0 || input.signum() < 0) error("modF2m: negative number represent no binary polynomial")
        if (f.signum() == 0) error("modF2m: cannot divide by zero polynomial")
        var n = input
        while (n.signum() != 0 && n.bitLength() >= f.bitLength()) n = n.xor(f.shiftLeft(n.bitLength() - f.bitLength()))
        return n
    }
    private fun binaryMultiply(f: BigInteger, a: BigInteger, b: BigInteger): BigInteger {
        if (f.signum() < 0 || a.signum() < 0 || b.signum() < 0) error("mulF2m: negative number represent no binary polynomial")
        if (f.signum() == 0) error("mulF2m: cannot multiply modulo zero polynomial")
        var v = b; var shifted = a; var product = BigInteger.ZERO
        while (v.signum() != 0) { if (v.testBit(0)) product = product.xor(shifted); v = v.shiftRight(1); shifted = shifted.shiftLeft(1) }
        return binaryMod(f,product)
    }
    fun isPointValid(curve: X509EcCurve, point: X509EcPoint) = isPointValidDeferred(lazyOf(curve),point)
    fun isPointValidDeferred(curve: Lazy<X509EcCurve>, point: X509EcPoint): Boolean {
        if (point === X509EcPoint.Infinity) return true
        point as X509EcPoint.Affine
        val c = curve.value; val modulus by lazy { c.modulus }
        return when (c) {
            is X509EcCurve.Prime -> {
                val x = point.x
                if (x.signum() < 0 || x >= modulus) false else {
                    val y = point.y
                    if (y.signum() < 0 || y >= modulus) false else {
                        val left = y.pow(2).mod(modulus)
                        left == (x.pow(3) + c.common.a * x + c.common.b).mod(modulus)
                    }
                }
            }
            is X509EcCurve.Binary -> {
                if (modulus.signum() < 0) error("modF2m: negative number represent no binary polynomial")
                val x = point.x
                if (binaryMod(modulus,x) != x) false else {
                    val y = point.y
                    binaryMod(modulus,y) == y && binaryMultiply(modulus,
                        binaryMultiply(modulus,x.xor(c.common.a),x).xor(y),x).xor(c.common.b).xor(binaryMultiply(modulus,y,y)) == BigInteger.ZERO
                }
            }
        }
    }
    fun unserializePoint(curve: X509EcCurve, point: X509SerializedPoint) = unserializePointDeferred(lazyOf(curve),point)
    fun unserializePointDeferred(curve: Lazy<X509EcCurve>, point: X509SerializedPoint): Asn1Maybe<X509EcPoint> {
        val bytes = point.bytes
        if (bytes.isEmpty() || bytes[0] != 4.toByte()) return Asn1Maybe.None
        val size = (curveSizeBits(curve.value).toLong() + 7) / 8
        if (bytes.size.toLong() - 1 != 2 * size) return Asn1Maybe.None
        val end = 1 + size.toInt()
        fun unsigned(input: ByteArray) = if (input.isEmpty()) BigInteger.ZERO else BigInteger(1,input)
        val p = X509EcPoint.Affine(unsigned(bytes.copyOfRange(1,end)),unsigned(bytes.copyOfRange(end,bytes.size)))
        return if (isPointValidDeferred(curve,p)) Asn1Maybe.Some(p) else Asn1Maybe.None
    }
    fun ecPubKeyCurve(key: X509EcPublicKey): Asn1Maybe<X509EcCurve> = when(key) {
        is X509EcPublicKey.Named -> Asn1Maybe.Some.deferred {getCurveByName(key.curve)}
        is X509EcPublicKey.Prime -> primeCurve({key.a},{key.b},{key.prime},key.generator,{key.order},{key.cofactor})
    }
    fun ecPrivKeyCurve(key: X509EcPrivateKey): Asn1Maybe<X509EcCurve> = when(key) {
        is X509EcPrivateKey.Named -> Asn1Maybe.Some.deferred {getCurveByName(key.curve)}
        is X509EcPrivateKey.Prime -> primeCurve({key.a},{key.b},{key.prime},key.generator,{key.order},{key.cofactor})
    }
    private fun primeCurve(a: () -> BigInteger,b: () -> BigInteger,p: () -> BigInteger,g: X509SerializedPoint,n: () -> BigInteger,h: () -> BigInteger): Asn1Maybe<X509EcCurve> {
        fun build(generator: () -> X509EcPoint) = X509EcCurve.Prime.deferred(p) {X509EcCurveCommon.deferred(a,b,generator,n,h)}
        return when(val point = unserializePointDeferred(lazy {build {error("Prelude.undefined")}},g)) {
            Asn1Maybe.None -> Asn1Maybe.None
            is Asn1Maybe.Some -> Asn1Maybe.Some.deferred {build {point.value}}
        }
    }
    fun ecPubKeyCurveName(key: X509EcPublicKey): X509CurveName? = when(key) {
        is X509EcPublicKey.Named -> key.curve
        is X509EcPublicKey.Prime -> findPrime({key.a},{key.b},{key.order},{key.prime})
    }
    fun ecPrivKeyCurveName(key: X509EcPrivateKey): X509CurveName? = when(key) {
        is X509EcPrivateKey.Named -> key.curve
        is X509EcPrivateKey.Prime -> findPrime({key.a},{key.b},{key.order},{key.prime})
    }
    private fun findPrime(a: () -> BigInteger,b: () -> BigInteger,n: () -> BigInteger,p: () -> BigInteger) = X509CurveName.entries.firstOrNull {name ->
        val curve = getCurveByName(name)
        curve is X509EcCurve.Prime && curve.common.a == a() && curve.common.b == b() && curve.common.order == n() && curve.modulus == p()
    }
}
