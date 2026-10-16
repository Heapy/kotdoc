// crypton 2.0.1 Crypto/PubKey/ECC/P256.hs; full notice: licenses/crypton-BSD-3-Clause.txt.
// ram 0.22.1 byte-wrapper observations: licenses/ram-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

import io.heapy.kotdoc.formats.HaskellString
import java.math.BigInteger
import java.lang.ref.Cleaner
import java.lang.ref.Reference

/** Public P256 values and policies. Arithmetic uses the specialized Kotlin engines. */
class CryptoP256(val digitBits: Int = 64) {
    init { require(digitBits == 32 || digitBits == 64) }
    enum class Error { CryptoError_SecretKeySizeInvalid, CryptoError_PublicKeySizeInvalid, CryptoError_PointCoordinatesInvalid }
    sealed interface Failable<out T> {
        data class Passed<T>(val value: T) : Failable<T>
        data class Failed(val error: Error) : Failable<Nothing>
    }
    class Scalar private constructor(private val raw: ByteArray) {
        private class Wipe(private val bytes: ByteArray) : Runnable { override fun run() { bytes.fill(0) } }
        private val cleanup = cleaner.register(this, Wipe(raw))
        internal fun rawCopy(): ByteArray = try { raw.copyOf() } finally { Reference.reachabilityFence(this) }
        fun <T> withByteArray(action: (ByteArray) -> T): T = action(rawCopy())
        val size: Int get() = 32
        fun force() = Unit
        fun show(precedence: Int = 0): String = if (precedence > 10) "(Scalar <scrubbed-bytes>)" else "Scalar <scrubbed-bytes>"
        override fun toString() = show()
        override fun equals(other: Any?): Boolean {
            if (other !is Scalar) return false
            return try {
                var difference = 0
                for (i in 0..<32) difference = difference or (raw[i].toInt() xor other.raw[i].toInt())
                difference == 0
            } finally {
                Reference.reachabilityFence(this)
                Reference.reachabilityFence(other)
            }
        }
        override fun hashCode() = 0
        companion object {
            private val cleaner = Cleaner.create()
            internal fun of(raw: ByteArray) = Scalar(raw.copyOf())
        }
    }
    class Point private constructor(private val raw: ByteArray) {
        internal fun rawCopy() = raw.copyOf()
        fun force() = Unit
        fun show(precedence: Int = 0): String {
            val body = "Point " + HaskellString.quoteCodePoints(IntArray(raw.size) { raw[it].toInt() and 255 })
            return if (precedence > 10) "($body)" else body
        }
        override fun toString() = show()
        override fun equals(other: Any?) = other is Point && raw.contentEquals(other.raw)
        override fun hashCode() = raw.contentHashCode()
        companion object { internal fun of(raw: ByteArray) = Point(raw.copyOf()) }
    }
    class Audit {
        val effects = mutableListOf<String>()
        val temporaryContents = mutableListOf<ByteArray>()
    }
    private val words = CryptoP256Words(digitBits)
    private val points = CryptoP256Points(digitBits)
    private val digitBytes = digitBits / 8
    private fun region(raw: ByteArray, offset: Int = 0) = CryptoP256Words.Region(LongArray(words.digits) { i ->
        var value = 0L
        repeat(digitBytes) { value = value or ((raw[offset + i * digitBytes + it].toLong() and 255) shl (8 * it)) }
        value
    })
    private fun raw(a: CryptoP256Words.Region): ByteArray = ByteArray(32) { (a[it / digitBytes] ushr (8 * (it % digitBytes))).toByte() }
    private fun fresh() = CryptoP256Words.Region(LongArray(words.digits))
    private fun scalar(a: CryptoP256Words.Region) = Scalar.of(raw(a))
    private fun point(x: CryptoP256Words.Region, y: CryptoP256Words.Region) = Point.of(raw(x) + raw(y))
    private fun pair(a: Point): Pair<CryptoP256Words.Region, CryptoP256Words.Region> {
        val bytes = a.rawCopy(); return region(bytes) to region(bytes, 32)
    }
    val scalarZero: Scalar by lazy { scalar(fresh().also { words.init(it) }) }
    val scalarN: Scalar by lazy { scalar(words.n) }
    val pointBase: Point by lazy { toPoint(Scalar.of(byteArrayOf(1) + ByteArray(31))) }
    fun scalarFromBinary(bytes: ByteArray): Failable<Scalar> {
        if (bytes.size != 32) return Failable.Failed(Error.CryptoError_SecretKeySizeInvalid)
        val out = fresh(); words.fromBinary(CryptoByteSpan(bytes), out); return Failable.Passed(scalar(out))
    }
    fun scalarToBinary(a: Scalar): ByteArray = ByteArray(32).also { words.toBinary(region(a.rawCopy()), CryptoByteSpan(it)) }
    fun scalarFromInteger(value: BigInteger): Failable<Scalar> {
        if (value.signum() < 0 || value.bitLength() > 256) return Failable.Failed(Error.CryptoError_SecretKeySizeInvalid)
        return scalarFromBinary(integerBytes(value))
    }
    fun scalarToInteger(a: Scalar): BigInteger = BigInteger(1, scalarToBinary(a))
    private fun integerBytes(value: BigInteger): ByteArray {
        val bytes = value.toByteArray(); val tail = if (bytes.size == 33 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, 33) else bytes
        return ByteArray(32 - tail.size) + tail
    }
    fun scalarGenerate(randomBytes: (Int) -> ByteArray, audit: Audit? = null): Scalar {
        audit?.effects?.add("getRandomBytes:32")
        return when (val value = scalarFromBinary(randomBytes(32))) {
            is Failable.Passed -> value.value
            is Failable.Failed -> error("scalarGenerate: assumption failed")
        }
    }
    fun scalarIsZero(a: Scalar) = words.zero(region(a.rawCopy()))
    fun scalarReduce(a: Scalar): Scalar = scalar(fresh().also { words.mod(words.n, region(a.rawCopy()), it) })
    fun scalarAdd(a: Scalar, b: Scalar): Scalar = scalar(fresh().also { words.modAdd(words.n, region(a.rawCopy()), region(b.rawCopy()), it) })
    fun scalarSub(a: Scalar, b: Scalar): Scalar = scalar(fresh().also { words.modSub(words.n, region(a.rawCopy()), region(b.rawCopy()), it) })
    fun scalarMul(a: Scalar, b: Scalar): Scalar = scalar(fresh().also { words.modMul(words.n, region(a.rawCopy()), 0, region(b.rawCopy()), it) })
    fun scalarInv(a: Scalar): Scalar = scalar(fresh().also { words.inverseVarTime(words.n, region(a.rawCopy()), it) })
    fun scalarInvSafe(a: Scalar): Scalar = scalar(fresh().also { words.inverseSafe(region(a.rawCopy()), it) })
    fun scalarCmp(a: Scalar, b: Scalar) = words.cmp(region(a.rawCopy()), region(b.rawCopy()))
    fun toPoint(a: Scalar): Point {
        if (scalarIsZero(a)) error("cannot create point from zero")
        val x = fresh(); val y = fresh(); points.basePointMultiply(region(a.rawCopy()), x, y); return point(x, y)
    }
    fun pointIsAtInfinity(a: Point): Boolean {
        var combined = 0
        for (byte in a.rawCopy()) combined = combined or byte.toInt()
        return combined == 0
    }
    fun pointIsValid(a: Point): Boolean { val (x, y) = pair(a); return words.valid(x, y) }
    fun pointAdd(a: Point, b: Point): Point {
        if (pointIsAtInfinity(a)) return b
        if (pointIsAtInfinity(b)) return a
        val (ax, ay) = pair(a); val (bx, by) = pair(b); val x = fresh(); val y = fresh()
        points.pointAdd(ax, ay, bx, by, x, y); return point(x, y)
    }
    fun pointNegate(a: Point): Point {
        val (ax, ay) = pair(a); val x = fresh(); val y = fresh(); points.pointNegate(ax, ay, x, y); return point(x, y)
    }
    fun pointMul(a: Scalar, b: Point): Point {
        val (bx, by) = pair(b); val x = fresh(); val y = fresh(); points.pointMultiply(region(a.rawCopy()), bx, by, x, y); return point(x, y)
    }
    fun pointsMulVarTime(a: Scalar, b: Scalar, p: Point): Point {
        val (px, py) = pair(p); val x = fresh(); val y = fresh()
        points.pointsMultiplyVariable(region(a.rawCopy()), region(b.rawCopy()), px, py, x, y); return point(x, y)
    }
    fun pointDh(a: Scalar, b: Point, audit: Audit? = null): ByteArray {
        audit?.effects?.add("allocate-output:32"); audit?.effects?.add("allocate-scrubbed:64")
        val temporary = ByteArray(64)
        try {
            val (bx, by) = pair(b); val x = fresh(); val y = fresh()
            points.pointMultiply(region(a.rawCopy()), bx, by, x, y)
            raw(x).copyInto(temporary); raw(y).copyInto(temporary, 32)
            audit?.temporaryContents?.add(temporary.copyOf()); audit?.effects?.add("serialize-x:32")
            return ByteArray(32).also { words.toBinary(region(temporary), CryptoByteSpan(it)) }
        } finally {
            // Deterministic local cleanup; no claim of JVM/GC/hardware erasure equivalence.
            temporary.fill(0); audit?.effects?.add("release-scrubbed:64"); audit?.temporaryContents?.add(temporary.copyOf())
        }
    }
    fun pointX(a: Point): Scalar? {
        if (pointIsAtInfinity(a)) return null
        val (x, _) = pair(a); val out = fresh(); words.mod(words.n, x, out); return scalar(out)
    }
    fun pointToIntegers(a: Point): Pair<BigInteger, BigInteger> {
        val bytes = pointToBinary(a); return BigInteger(1, bytes.copyOfRange(0, 32)) to BigInteger(1, bytes.copyOfRange(32, 64))
    }
    fun pointFromIntegers(x: BigInteger, y: BigInteger): Point {
        if (x.signum() < 0 || x.bitLength() > 256) error("pointFromIntegers: filling failed")
        val xb = integerBytes(x)
        if (y.signum() < 0 || y.bitLength() > 256) error("pointFromIntegers: filling failed")
        return (unsafePointFromBinary(xb + integerBytes(y)) as Failable.Passed).value
    }
    fun pointToBinary(a: Point): ByteArray {
        val (x, y) = pair(a); return ByteArray(64).also { words.toBinary(x, CryptoByteSpan(it, 0, 32)); words.toBinary(y, CryptoByteSpan(it, 32, 32)) }
    }
    fun unsafePointFromBinary(bytes: ByteArray): Failable<Point> {
        if (bytes.size != 64) return Failable.Failed(Error.CryptoError_PublicKeySizeInvalid)
        val x = fresh(); val y = fresh(); words.fromBinary(CryptoByteSpan(bytes, 0, 32), x); words.fromBinary(CryptoByteSpan(bytes, 32, 32), y)
        return Failable.Passed(point(x, y))
    }
    fun pointFromBinary(bytes: ByteArray): Failable<Point> = when (val p = unsafePointFromBinary(bytes)) {
        is Failable.Failed -> p
        is Failable.Passed -> if (pointIsValid(p.value)) p else Failable.Failed(Error.CryptoError_PointCoordinatesInvalid)
    }
}
