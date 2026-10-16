// Crypto.ECC.Edwards25519, crypton2.0.1; licenses/crypton-BSD-3-Clause.txt.
// RAM0.22.1 Show/NFData observations; licenses/ram-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto
import java.lang.ref.Cleaner
import java.lang.ref.Reference
class CryptoEdwards25519(val digitBits: Int=64) {
 init {require(digitBits==32 || digitBits==64)}
 private val engine=CryptoEdwards25519Points(digitBits)
 private class Secret(words: LongArray) {
  private val raw=words.copyOf()
  private class Wipe(private val raw: LongArray): Runnable {override fun run(){raw.fill(0)}}
  private val cleanup=cleaner.register(this,Wipe(raw))
  fun copy()=try{raw.copyOf()}finally{Reference.reachabilityFence(this)}
  companion object {private val cleaner=Cleaner.create()}
 }
 class Scalar private constructor(private val secret: Secret,internal val bits: Int) {
  internal fun words()=secret.copy()
  fun force()=Unit
  fun show(precedence: Int=0)=if(precedence>10)"(Scalar <scrubbed-bytes>)"else"Scalar <scrubbed-bytes>"
  override fun toString()=show()
  override fun equals(other: Any?): Boolean {if(other !is Scalar)return false;val api=CryptoEdwards25519(bits);val a=api.scalarEncode(this);val b=api.scalarEncode(other);try{var difference=0;for(i in 0..<32)difference=difference or(a[i].toInt()xor b[i].toInt());return difference==0}finally{a.fill(0);b.fill(0)}}
  override fun hashCode()=0
  companion object {internal fun of(words: LongArray,bits: Int)=Scalar(Secret(words),bits)}
 }
 class Point private constructor(private val raw: LongArray,internal val bits: Int) {
  internal fun words()=raw.copyOf()
  fun force()=Unit
  fun show(precedence: Int=0): String {val body="Point \""+CryptoEdwards25519(bits).pointEncode(this).toHexString()+"\"";return if(precedence>10)"($body)"else body}
  override fun toString()=show()
  override fun equals(other: Any?): Boolean {if(other !is Point)return false;val api=CryptoEdwards25519(bits);return api.engine.equal(api.point(this),api.point(other))!=0}
  override fun hashCode()=CryptoEdwards25519(bits).pointEncode(this).contentHashCode()
  companion object {internal fun of(words: LongArray,bits: Int)=Point(words.copyOf(),bits)}
 }
 private fun scalar(s: Scalar): CryptoEdwards25519Scalar.Region {if(s.bits==digitBits)return CryptoEdwards25519Scalar.Region(s.words());return decodeScalarWords(CryptoEdwards25519(s.bits).scalarEncode(s))}
 private fun decodeScalarWords(bytes: ByteArray)=CryptoEdwards25519Scalar.Region(LongArray(engine.scalar.limbs)).also{engine.scalar.expand(it,CryptoByteSpan(bytes))}
 private fun point(p: Point): CryptoEdwards25519Points.Point {if(p.bits==digitBits)return CryptoEdwards25519Points.Point(p.words(),limbs=engine.limbs);return engine.point().also{check(engine.decode(it,CryptoByteSpan(CryptoEdwards25519(p.bits).pointEncode(p)))==1)}}
 private fun own(s: CryptoEdwards25519Scalar.Region)=Scalar.of(LongArray(engine.scalar.limbs){s[it]},digitBits)
 private fun own(p: CryptoEdwards25519Points.Point)=Point.of(p.data,digitBits)
 fun scalarDecodeLong(bytes: ByteArray): CryptoEcc.Failable<Scalar> = if(bytes.size>64)CryptoEcc.Failable.Failed(CryptoEcc.Error.CryptoError_EcScalarOutOfBounds)else CryptoEcc.Failable.Passed(own(decodeScalarWords(bytes)))
 fun scalarEncode(s: Scalar)=ByteArray(32).also{engine.scalar.contract(CryptoByteSpan(it),scalar(s))}
 fun scalarGenerate(random: (Int)->ByteArray): Scalar {val bytes=random(64);val copy=bytes.copyOf();try{if(copy.size>64)error("CryptoError_EcScalarOutOfBounds");return own(decodeScalarWords(copy))}finally{copy.fill(0)}}
 fun scalarAdd(a: Scalar,b: Scalar)=own(CryptoEdwards25519Scalar.Region(LongArray(engine.scalar.limbs)).also{engine.scalar.add(it,scalar(a),scalar(b))})
 fun scalarMul(a: Scalar,b: Scalar)=own(CryptoEdwards25519Scalar.Region(LongArray(engine.scalar.limbs)).also{engine.scalar.multiply(it,scalar(a),scalar(b))})
 fun toPoint(s: Scalar)=own(engine.point().also{engine.baseScalar(it,scalar(s))})
 fun pointDecode(bytes: ByteArray): CryptoEcc.Failable<Point> {if(bytes.size!=32)return CryptoEcc.Failable.Failed(CryptoEcc.Error.CryptoError_PointSizeInvalid);val p=engine.point();return if(engine.decode(p,CryptoByteSpan(bytes))==0)CryptoEcc.Failable.Failed(CryptoEcc.Error.CryptoError_PointCoordinatesInvalid)else CryptoEcc.Failable.Passed(own(p))}
 fun pointEncode(p: Point)=ByteArray(32).also{engine.pack(CryptoByteSpan(it),point(p))}
 fun pointHasPrimeOrder(p: Point)=engine.hasPrimeOrder(point(p))!=0
 fun pointNegate(p: Point)=own(engine.point().also{engine.negate(it,point(p))})
 fun pointAdd(a: Point,b: Point)=own(engine.point().also{engine.add(it,point(a),point(b))})
 fun pointDouble(p: Point)=own(engine.point().also{engine.double(it,point(p))})
 fun pointMul(s: Scalar,p: Point)=own(engine.point().also{engine.multiply(it,point(p),scalar(s))})
 fun pointMulByCofactor(p: Point)=own(engine.point().also{engine.cofactor(it,point(p))})
 fun pointsMulVarTime(s1: Scalar,s2: Scalar,p: Point)=own(engine.point().also{engine.doubleScalar(it,point(p),scalar(s2),scalar(s1))})
 fun scalarShowDeferred(value: Lazy<Scalar>,precedence: Int=0)=if(precedence>10)"(Scalar <scrubbed-bytes>)"else"Scalar <scrubbed-bytes>"
 fun pointShowDeferred(value: Lazy<Point>,precedence: Int=0)=value.value.show(precedence)
 fun scalarForceDeferred(value: Lazy<Scalar>)=value.value.force()
 fun pointForceDeferred(value: Lazy<Point>)=value.value.force()
}
