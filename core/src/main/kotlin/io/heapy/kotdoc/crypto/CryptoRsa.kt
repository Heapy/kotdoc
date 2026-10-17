// Crypto.PubKey.RSA and RSA.Prim, crypton 2.0.1; Vincent Hanquez and contributors.
// Full notice: licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

import io.heapy.kotdoc.x509.X509RsaPublicKey
import io.heapy.kotdoc.x509.X509RsaPrivateKey
import io.heapy.kotdoc.x509.X509SecretIntegers
import java.math.BigInteger

/** RSA integer/key-generation primitives. Padding and signature schemes are separate modules. */
class CryptoRsa(private val prime: CryptoNumberPrime) {
    class Blinder private constructor(private val pair: Lazy<Pair<BigInteger, BigInteger>>) {
        constructor(r: BigInteger, inverse: BigInteger) : this(lazyOf(r to inverse))
        val r get() = pair.value.first
        val inverse get() = pair.value.second
        fun force() { pair.value }
        override fun equals(other: Any?) = other is Blinder && r == other.r && inverse == other.inverse
        override fun hashCode() = 31 * r.hashCode() + inverse.hashCode()
        override fun toString() = "Blinder ${argument(r)} ${argument(inverse)}"
        companion object {
            private fun argument(n: BigInteger) = if (n.signum() < 0) "($n)" else n.toString()
            internal fun deferred(pair: () -> Pair<BigInteger, BigInteger>) = Blinder(cryptoRandomMemo(pair))
        }
    }

    fun generateWith(p: BigInteger, q: BigInteger, size: Int, exponent: BigInteger) =
        generateWithDeferred(lazyOf(p), lazyOf(q), lazyOf(size.toLong()), lazyOf(exponent))

    fun generateWithDeferred(p: Lazy<BigInteger>, q: Lazy<BigInteger>, size: Lazy<Long>, exponent: Lazy<BigInteger>): CryptoMaybe<Pair<X509RsaPublicKey, X509RsaPrivateKey>> {
        val e = exponent.value
        if (e <= BigInteger.ONE) return CryptoMaybe.None
        val phi = cryptoRandomMemo { (p.value - BigInteger.ONE) * (q.value - BigInteger.ONE) }
        val t = mod(phi.value, e)
        if (t.signum() == 0) return CryptoMaybe.None
        val k = mod(e - CryptoModular.expSafe(t, e - BigInteger.TWO, e), e)
        val d = if (mod(k * t, e) == e - BigInteger.ONE) {
            cryptoRandomMemo { (BigInteger.ONE + k * phi.value) / e }
        } else when (val value = CryptoModular.inverse(e, phi.value)) {
            CryptoMaybe.None -> return CryptoMaybe.None
            is CryptoMaybe.Some -> cryptoRandomMemo { value.value }
        }
        return CryptoMaybe.Some.deferred {
            val public = X509RsaPublicKey.deferred({size.value}, {p.value * q.value}, {e})
            val qinv = cryptoRandomMemo {
                when (val value = CryptoModular.inverseSafe(q.value, p.value)) {
                    CryptoMaybe.None -> CryptoModular.inverseCoprimes(q.value, p.value)
                    is CryptoMaybe.Some -> value.value
                }
            }
            val fields = listOf(d, p, q, cryptoRandomMemo { mod(d.value, p.value - BigInteger.ONE) }, cryptoRandomMemo { mod(d.value, q.value - BigInteger.ONE) }, qinv)
            public to X509RsaPrivateKey(public, X509SecretIntegers(fields))
        }
    }

    fun generate(size: Int, exponent: BigInteger, random: (Int) -> ByteArray): Pair<X509RsaPublicKey, X509RsaPrivateKey> {
        while (true) {
            val half = Math.floorDiv(size, 2)
            val p = prime.generatePrime(Math.multiplyExact(8, half), random)
            var q: BigInteger
            do { q = prime.generatePrime(Math.multiplyExact(8, size - half), random) } while (p == q)
            when (val generated = generateWith(p, q, size, exponent)) {
                CryptoMaybe.None -> Unit
                is CryptoMaybe.Some -> return generated.value
            }
        }
    }

    fun generateBlinder(n: BigInteger, random: (Int) -> ByteArray): Blinder =
        generateBlinderDeferred(n, random).value

    /** Execute callback effects now, retaining the source's lazy returned value. */
    fun generateBlinderDeferred(n: BigInteger, random: (Int) -> ByteArray): Lazy<Blinder> {
        val r = CryptoNumberGenerate.generateMax(n, random)
        val bytes = random(16).copyOf()
        return cryptoRandomMemo {
            val u = try { CryptoSerialize.os2ip(bytes) } finally { bytes.fill(0) }
            val v = mod(r * u, n)
            Blinder(r, mod(CryptoModular.inverseCoprimes(v, n) * u, n))
        }
    }

    /** Actual pure State-like source instance; unused prime computations stay deferred. */
    fun <G: CryptoDrg<G>> generateAction(size: Lazy<Int>, exponent: Lazy<BigInteger>, operations: CryptoDrgOperations<G>): CryptoPseudoRandom<G, Pair<X509RsaPublicKey, X509RsaPrivateKey>> {
        fun compute(initial: Lazy<G>): Pair<Lazy<Pair<X509RsaPublicKey, X509RsaPrivateKey>>, Lazy<G>> {
            var generator = initial
            fun nextPrime(bits: Int): BigInteger = prime.generatePrime(bits) { count ->
                val previous = generator
                val result = cryptoRandomMemo { operations.generate(count, previous) }
                generator = cryptoRandomMemo { result.value.second.value }
                result.value.first.value
            }
            while (true) {
                generateAttemptObserver?.invoke()
                val e = exponent.value
                if (e <= BigInteger.ONE) {
                    while (true) generateAttemptObserver?.invoke()
                }
                val length = size.value
                val half = Math.floorDiv(length, 2)
                val p = nextPrime(Math.multiplyExact(8, half))
                var q: BigInteger
                do { q = nextPrime(Math.multiplyExact(8, length - half)) } while (q == p)
                when (val key = generateWithDeferred(lazyOf(p), lazyOf(q), lazyOf(length.toLong()), lazyOf(e))) {
                    CryptoMaybe.None -> Unit
                    is CryptoMaybe.Some -> return cryptoRandomMemo { key.value } to generator
                }
            }
        }
        return CryptoPseudoRandom { initial ->
            val result = cryptoRandomMemo { compute(initial) }
            cryptoRandomMemo { result.value.first.value } to cryptoRandomMemo { result.value.second.value }
        }
    }

    internal var generateAttemptObserver: (() -> Unit)? = null

    private fun <G: CryptoDrg<G>> maxAction(range: Lazy<BigInteger>, operations: CryptoDrgOperations<G>): CryptoPseudoRandom<G, BigInteger> = CryptoPseudoRandom { generator ->
        val n = range.value
        fun params(bits: Int) = CryptoPseudoRandom.getRandomBytes(((bits.toLong() + 7) / 8).toInt(), operations).map { bytes ->
            CryptoNumberGenerate.generateParams(bits, null, false) { bytes.value }
        }
        val action = if (n <= BigInteger.ONE) CryptoPseudoRandom.pure(BigInteger.ZERO) else {
            val bits = CryptoNumberBasic.numBits(n)
            if (n < BigInteger.valueOf(127)) params(bits).map { mod(it.value, n) } else {
                CryptoPseudoRandom { initial ->
                    val result = cryptoRandomMemo {
                        var state = initial
                        val value = CryptoNumberGenerate.generateMax(n) { count ->
                            val previous = state
                            val draw = cryptoRandomMemo { operations.generate(count, previous) }
                            state = cryptoRandomMemo { draw.value.second.value }
                            draw.value.first.value
                        }
                        value to state
                    }
                    cryptoRandomMemo { result.value.first } to cryptoRandomMemo { result.value.second.value }
                }
            }
        }
        action.run(generator)
    }

    fun <G: CryptoDrg<G>> generateBlinderAction(n: Lazy<BigInteger>, operations: CryptoDrgOperations<G>): CryptoPseudoRandom<G, Blinder> =
        maxAction(n, operations).flatMap { r ->
            CryptoPseudoRandom.getRandomBytes(16, operations).map { bytes ->
                val number = r.value
                val u = CryptoSerialize.os2ip(bytes.value)
                val v = mod(number * u, n.value)
                Blinder(number, mod(CryptoModular.inverseCoprimes(v, n.value) * u, n.value))
            }
        }

    companion object {
        internal fun mod(a: BigInteger, b: BigInteger): BigInteger {
            if (b.signum() == 0) throw ArithmeticException("divide by zero")
            val r = a.remainder(b)
            return if (r.signum() != 0 && r.signum() != b.signum()) r + b else r
        }
        internal fun multiplication(a: BigInteger, b: BigInteger, m: BigInteger) = multiplicationDeferred(lazyOf(a), lazyOf(b), lazyOf(m))
        // Selected original GHC worker demands modulus, then right, then left operand.
        internal fun multiplicationDeferred(a: Lazy<BigInteger>, b: Lazy<BigInteger>, m: Lazy<BigInteger>): BigInteger {
            val modulus = m.value
            val right = b.value
            return mod(a.value * right, modulus)
        }
        private fun size(value: Long) = Math.toIntExact(value)

        fun ep(key: X509RsaPublicKey, message: ByteArray) = epDeferred(lazyOf(key), lazyOf(message))
        fun epDeferred(key: Lazy<X509RsaPublicKey>, message: Lazy<ByteArray>): ByteArray =
            CryptoSerialize.i2ospOfUnsafeDeferred(cryptoRandomMemo { size(key.value.size) }, cryptoRandomMemo {
                CryptoModular.expFastDeferred(cryptoRandomMemo { CryptoSerialize.os2ip(message.value) }, cryptoRandomMemo { key.value.exponent }, cryptoRandomMemo { key.value.modulus })
            })

        fun dp(blinder: Blinder?, key: X509RsaPrivateKey, cipher: ByteArray) = dpDeferred(lazyOf(blinder), lazyOf(key), lazyOf(cipher))
        fun dpDeferred(blinder: Lazy<Blinder?>, key: Lazy<X509RsaPrivateKey>, cipher: Lazy<ByteArray>): ByteArray =
            if (key.value.p.signum() != 0 && key.value.q.signum() != 0) {
                val b = blinder.value
                if (b == null) dpFastNoBlinder(key, cipher) else dpFast(b, key, cipher)
            } else dpSlow(key, cipher)

        internal fun dpSlow(key: Lazy<X509RsaPrivateKey>, cipher: Lazy<ByteArray>): ByteArray =
            CryptoSerialize.i2ospOfUnsafeDeferred(cryptoRandomMemo { size(key.value.publicKey.size) }, cryptoRandomMemo {
                CryptoModular.expSafeDeferred(cryptoRandomMemo { CryptoSerialize.os2ip(cipher.value) }, cryptoRandomMemo { key.value.d }, cryptoRandomMemo { key.value.publicKey.modulus })
            })

        internal fun dpFastNoBlinder(key: Lazy<X509RsaPrivateKey>, cipher: Lazy<ByteArray>): ByteArray {
            val c = cryptoRandomMemo { CryptoSerialize.os2ip(cipher.value) }
            val m1 = cryptoRandomMemo { CryptoModular.expSafeDeferred(c, cryptoRandomMemo { key.value.dP }, cryptoRandomMemo { key.value.p }) }
            val m2 = cryptoRandomMemo { CryptoModular.expSafeDeferred(c, cryptoRandomMemo { key.value.dQ }, cryptoRandomMemo { key.value.q }) }
            val h = cryptoRandomMemo { mod(key.value.qinv * (m1.value - m2.value), key.value.p) }
            return CryptoSerialize.i2ospOfUnsafeDeferred(cryptoRandomMemo { size(key.value.publicKey.size) }, cryptoRandomMemo { m2.value + h.value * key.value.q })
        }

        internal fun dpFast(blinder: Blinder, key: Lazy<X509RsaPrivateKey>, cipher: Lazy<ByteArray>): ByteArray {
            blinder.force()
            val re = cryptoRandomMemo { CryptoModular.expFast(blinder.r, key.value.publicKey.exponent, key.value.publicKey.modulus) }
            val c = cryptoRandomMemo { multiplicationDeferred(re, cryptoRandomMemo { CryptoSerialize.os2ip(cipher.value) }, cryptoRandomMemo { key.value.publicKey.modulus }) }
            val m1 = cryptoRandomMemo { CryptoModular.expSafeDeferred(c, cryptoRandomMemo { key.value.dP }, cryptoRandomMemo { key.value.p }) }
            val m2 = cryptoRandomMemo { CryptoModular.expSafeDeferred(c, cryptoRandomMemo { key.value.dQ }, cryptoRandomMemo { key.value.q }) }
            val h = cryptoRandomMemo { mod(key.value.qinv * (m1.value - m2.value), key.value.p) }
            return CryptoSerialize.i2ospOfUnsafeDeferred(cryptoRandomMemo { size(key.value.publicKey.size) }, cryptoRandomMemo { multiplicationDeferred(cryptoRandomMemo { blinder.inverse }, cryptoRandomMemo { m2.value + h.value * key.value.q }, cryptoRandomMemo { key.value.publicKey.modulus }) })
        }
    }
}
