// Crypto.PubKey.ECC.Prim, crypton 2.0.1; licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto
import io.heapy.kotdoc.x509.*
import java.math.BigInteger

/** Exact legacy policy and fallback formulas; no JVM constant-time guarantee. */
object CryptoEccPrim {
    private val z = BigInteger.ZERO
    private val one = BigInteger.ONE
    private val p256Curve by lazy { CryptoEccTypes.getCurveByName(EccCurveName.SEC_p256r1) }
    private val p256 by lazy { CryptoP256() }
    private fun point(x: BigInteger,y: BigInteger) = X509EcPoint.Affine(x,y)
    private fun scalar(n: BigInteger): CryptoP256.Scalar? = if(n.signum()<0 || n.bitLength()>256) null else (p256.scalarFromInteger(n) as CryptoP256.Failable.Passed).value
    private fun reducedScalar(n: BigInteger): CryptoP256.Scalar? {
        val s = scalar(n) ?: return reducedScalar(n.mod(p256Curve.common.order))
        return p256.scalarReduce(s).takeUnless { p256.scalarIsZero(it) }
    }
    private fun toP256(p: EccPoint): CryptoP256.Point? {
        if(p !is X509EcPoint.Affine || p.x.signum()<0 || p.y.signum()<0 || p.x.bitLength()>256 || p.y.bitLength()>256) return null
        return p256.pointFromIntegers(p.x,p.y).takeIf { p256.pointIsValid(it) }
    }
    private fun fromP256(p: CryptoP256.Point): EccPoint = if(p256.pointIsAtInfinity(p)) X509EcPoint.Infinity else p256.pointToIntegers(p).let { point(it.first,it.second) }
    private fun <T> maybe(x: CryptoMaybe<T>): T? = when(x) { is CryptoMaybe.Some -> x.value; CryptoMaybe.None -> null }
    private fun binary(x: F2mMaybe): BigInteger? = when(x) { is F2mMaybe.Some -> x.value; F2mMaybe.None -> null }
    fun scalarGenerate(c: EccCurve,randomBytes: (Int)->ByteArray) = EccLegacyRandom.generateMax(c.common.order-one,randomBytes)+one
    fun scalarInverse(c: EccCurve,k: BigInteger): BigInteger? {
        if(c==p256Curve) reducedScalar(k)?.let { return p256.scalarToInteger(p256.scalarInvSafe(it)) }
        return maybe(CryptoModular.inverseSafe(k,c.common.order))
    }
    fun scalarAdd(c: EccCurve,a: BigInteger,b: BigInteger): BigInteger {
        if(c==p256Curve) { val x=scalar(a); val y=scalar(b); if(x!=null && y!=null) return p256.scalarToInteger(p256.scalarAdd(x,y)) }
        return (a+b).mod(c.common.order)
    }
    fun scalarMul(c: EccCurve,a: BigInteger,b: BigInteger): BigInteger {
        if(c==p256Curve) { val x=scalar(a); val y=scalar(b); if(x!=null && y!=null) return p256.scalarToInteger(p256.scalarMul(x,y)) }
        return (a*b).mod(c.common.order)
    }
    fun pointNegate(c: EccCurve,p: EccPoint): EccPoint = when(p) {
        X509EcPoint.Infinity -> p
        is X509EcPoint.Affine -> point(p.x,if(c is X509EcCurve.Prime) c.modulus-p.y else p.x.xor(p.y))
    }
    private fun div(a: BigInteger,b: BigInteger,p: BigInteger): BigInteger? = maybe(CryptoModular.inverse(b.mod(p),p))?.let { (a*it).mod(p) }
    fun pointAdd(c: EccCurve,p: EccPoint,q: EccPoint): EccPoint {
        if(p===X509EcPoint.Infinity) return q
        if(q===X509EcPoint.Infinity) return p
        if(p==q) return pointDouble(c,p)
        if(p==pointNegate(c,q)) return X509EcPoint.Infinity
        p as X509EcPoint.Affine; q as X509EcPoint.Affine
        val f=c.modulus
        return if(c is X509EcCurve.Prime) {
            val s=div(p.y-q.y,p.x-q.x,f) ?: return X509EcPoint.Infinity
            val x=(s*s-p.x-q.x).mod(f); point(x,(s*(p.x-x)-p.y).mod(f))
        } else {
            val s=binary(F2m.divF2m(f,p.y.xor(q.y),p.x.xor(q.x))) ?: return X509EcPoint.Infinity
            val x=F2m.mulF2m(f,s,s).xor(s).xor(p.x).xor(q.x).xor(c.common.a)
            point(x,F2m.mulF2m(f,s,p.x.xor(x)).xor(x).xor(p.y))
        }
    }
    fun pointDouble(c: EccCurve,p: EccPoint): EccPoint {
        if(p===X509EcPoint.Infinity) return p
        p as X509EcPoint.Affine; val f=c.modulus
        return if(c is X509EcCurve.Prime) {
            val s=div(BigInteger.valueOf(3)*p.x*p.x+c.common.a,BigInteger.TWO*p.y,f) ?: return X509EcPoint.Infinity
            val x=(s*s-BigInteger.TWO*p.x).mod(f); point(x,(s*(p.x-x)-p.y).mod(f))
        } else {
            if(p.x==z) return X509EcPoint.Infinity
            val s=p.x.xor(binary(F2m.divF2m(f,p.y,p.x)) ?: return X509EcPoint.Infinity)
            val x=F2m.mulF2m(f,s,s).xor(s).xor(c.common.a)
            point(x,F2m.mulF2m(f,p.x,p.x).xor(F2m.mulF2m(f,x,s.xor(one))))
        }
    }
    fun pointBaseMul(c: EccCurve,n: BigInteger) = pointMul(c,n,c.common.generator)
    fun pointMul(c: EccCurve,n: BigInteger,p: EccPoint): EccPoint {
        if(p===X509EcPoint.Infinity) return p
        if(c==p256Curve) {
            if(p==p256Curve.common.generator) return reducedScalar(n)?.let { fromP256(p256.toPoint(it)) } ?: X509EcPoint.Infinity
            toP256(p)?.let { q -> return reducedScalar(n)?.let { fromP256(p256.pointMul(it,q)) } ?: X509EcPoint.Infinity }
        }
        if(n.signum()<0) return pointMul(c,-n,pointNegate(c,p))
        if(n==z) return X509EcPoint.Infinity
        p as X509EcPoint.Affine
        if(isPointValid(c,p)) {
            val field=if(c is X509EcCurve.Prime) EccCurveField.Prime(c.modulus,c.common.a,c.common.b) else EccCurveField.Binary(c.modulus,c.common.b)
            when(val r=CryptoEccInternal.curveMul(field,c.common.order,n,p.x,p.y,p==c.common.generator && c is X509EcCurve.Prime)) {
                is EccMulResult.Point -> return point(r.x,r.y)
                EccMulResult.Infinity -> return X509EcPoint.Infinity
                EccMulResult.Unsupported -> Unit
            }
        }
        return if(c is X509EcCurve.Prime) jacobianMul(c.modulus,c.common.a,maxOf(n.bitLength(),c.common.order.bitLength()),n,p) else affineMul(c,n,p)
    }
    private fun affineMul(c: EccCurve,k: BigInteger,q: EccPoint): EccPoint = when {
        k==z -> X509EcPoint.Infinity
        k==one -> q
        k.testBit(0) -> pointAdd(c,q,affineMul(c,k-one,q))
        else -> affineMul(c,k/BigInteger.TWO,pointDouble(c,q))
    }
    private data class Field(val p: BigInteger,val k: Int,val c: BigInteger) {
        fun red(x: BigInteger): BigInteger {
            if(c==z || x.signum()<0) return x.mod(p)
            val mask=one.shiftLeft(k)-one; var v=x
            while(v>mask) v=v.shiftRight(k)*c+v.and(mask)
            while(v>=p) v-=p
            return v
        }
    }
    private fun field(p: BigInteger): Field { val k=p.abs().bitLength();val c=one.shiftLeft(k)-p;return if(p>z && c>z && 2*c.bitLength()<=k && k>=256) Field(p,k,c) else Field(p,0,z) }
    private data class J(val x: BigInteger,val y: BigInteger,val z: BigInteger)
    private fun double(f: Field,a: BigInteger,p: J?): J? {
        if(p==null || p.y==z) return null
        val yy=f.red(p.y*p.y);val delta=f.red(BigInteger.valueOf(4)*p.x*yy);val zz=f.red(p.z*p.z)
        val m=f.red(BigInteger.valueOf(3)*p.x*p.x+a*zz*zz);val x=f.red(m*m-BigInteger.TWO*delta)
        return J(x,f.red(m*(delta-x)-BigInteger.valueOf(8)*yy*yy),f.red(BigInteger.TWO*p.y*p.z))
    }
    private fun add(f: Field,a: BigInteger,p: J?,q: X509EcPoint.Affine): J? {
        if(p==null) return J(q.x,q.y,one)
        val zs=f.red(p.z*p.z);val u=f.red(q.x*zs);val s=f.red(q.y*zs*p.z);val h=f.red(u-p.x);val r=f.red(s-p.y)
        if(h==z) return if(r!=z) null else double(f,a,p)
        val h2=f.red(h*h);val h3=f.red(h2*h);val x=f.red(r*r-h3-BigInteger.TWO*p.x*h2)
        return J(x,f.red(r*(p.x*h2-x)-p.y*h3),f.red(h*p.z))
    }
    private fun jacobianMul(pr: BigInteger,a: BigInteger,bits: Int,n: BigInteger,p: X509EcPoint.Affine): EccPoint {
        val f=field(pr);var acc: J?=null
        for(i in bits-1 downTo 0) { val d=double(f,a,acc);val s=add(f,a,d,p);acc=if(n.testBit(i)) s else d }
        val q=acc ?: return X509EcPoint.Infinity;val zi=maybe(CryptoModular.inverse(q.z,f.p)) ?: return X509EcPoint.Infinity
        val zi2=f.red(zi*zi);return point(f.red(q.x*zi2),f.red(q.y*zi2*zi))
    }
    fun pointAddTwoMuls(c: EccCurve,n1: BigInteger,p1: EccPoint,n2: BigInteger,p2: EccPoint): EccPoint {
        if(c==p256Curve) {
            val swapped=if(p1==p256Curve.common.generator) Triple(n1,n2,p2) else if(p2==p256Curve.common.generator) Triple(n2,n1,p1) else null
            if(swapped!=null) { val a=reducedScalar(swapped.first);val b=reducedScalar(swapped.second);val q=toP256(swapped.third);if(a!=null && b!=null && q!=null) return fromP256(p256.pointsMulVarTime(a,b,q)) }
        }
        return pointAdd(c,pointMul(c,n1,p1),pointMul(c,n2,p2))
    }
    data class Decomposed(val index: BigInteger,val residue: BigInteger,val parity: Boolean)
    fun pointDecompose(c: EccCurve,p: EccPoint): Decomposed? {
        if(p===X509EcPoint.Infinity) return null
        p as X509EcPoint.Affine;val n=c.common.order;val residue=p.x.mod(n);val index=(p.x-residue)/n
        val parity=if(c is X509EcCurve.Prime) p.y.testBit(0) else if(p.x==z) false else binary(F2m.divF2m(c.modulus,p.y,p.x))?.testBit(0) ?: return null
        return Decomposed(index,residue,parity)
    }
    fun pointCompose(c: EccCurve,index: BigInteger,residue: BigInteger,parity: Boolean): EccPoint? {
        val cc=c.common;val x=residue+index*cc.order;val f=c.modulus
        val y=if(c is X509EcCurve.Prime) {
            val root=maybe(CryptoModular.squareRoot(f,x.pow(3)+cc.a*x+cc.b)) ?: return null
            if(root.testBit(0)==parity) root else f-root
        } else if(x==z) F2m.sqrtF2m(f,cc.b) else {
            val v=binary(F2m.divF2m(f,cc.b,F2m.squareF2m(f,x))) ?: return null
            val root=binary(F2m.quadraticF2m(f,x.xor(cc.a).xor(v))) ?: return null
            F2m.mulF2m(f,x,if(root.testBit(0)==parity) root else one.xor(root))
        }
        return point(x,y)
    }
    fun isPointAtInfinity(p: EccPoint) = p===X509EcPoint.Infinity
    fun isPointValid(c: EccCurve,p: EccPoint) = X509Ec.isPointValid(c,p)
    fun isPointInSubgroup(c: EccCurve,p: EccPoint) = c.common.cofactor==one || pointMul(c,c.common.order,p)===X509EcPoint.Infinity
}
