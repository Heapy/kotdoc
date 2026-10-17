// Crypto.Number.Prime, crypton 2.0.1; Vincent Hanquez and contributors.
// Full upstream notice: licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

import java.math.BigInteger

/** Integer-level source algorithm, using the pinned integer-gmp >= 1.1 fallback.
 * Retain one probabilistic context for the source process-secret lifetime.
 */
class CryptoNumberPrime internal constructor(private val probabilistic: CryptoProbabilistic, private val drawObserver: ((Int, ByteArray) -> Unit)?) {
    constructor(probabilistic: CryptoProbabilistic) : this(probabilistic, null)
    private val one = BigInteger.ONE
    private val two = BigInteger.TWO
    private val three = BigInteger.valueOf(3)

    fun isProbablyPrime(n: BigInteger): Boolean = probablyPrime(30, n)

    internal fun probablyPrime(rounds: Int, n: BigInteger): Boolean {
        if (n < two) return false
        for (p in firstPrimes) {
            val divisor = BigInteger.valueOf(p.toLong())
            if (divisor >= n) break
            if (divides(divisor, n)) return false
        }
        return n <= BigInteger.valueOf(2903) || primalityTestMillerRabin(rounds, n)
    }

    fun generatePrime(bits: Int, random: (Int) -> ByteArray): BigInteger {
        if (bits < 5) throw PrimeSizeInvalid()
        val limit = one.shiftLeft(bits)
        while (true) {
            val candidate = CryptoNumberGenerate.generateParams(bits, CryptoNumberGenerate.GenTopPolicy.SetTwoHighest, true, random)
            val prime = findPrimeFromDrawn(roundsForDrawn(bits), candidate)
            if (prime < limit) return prime
        }
    }

    fun generateSafePrime(bits: Int, random: (Int) -> ByteArray): BigInteger {
        if (bits < 6) throw PrimeSizeInvalid()
        val rounds = roundsForDrawn(bits)
        val limit = one.shiftLeft(bits)
        while (true) {
            val candidate = CryptoNumberGenerate.generateParams(bits, CryptoNumberGenerate.GenTopPolicy.SetTwoHighest, true, random)
            val prime = findPrimeFromWithRounds(rounds, { probablyPrime(rounds, two * it + one) }, candidate / two)
            val result = two * prime + one
            if (result < limit) return result
        }
    }

    internal fun findPrimeFromDrawn(rounds: Int, n: BigInteger): BigInteger = findPrimeFromWithRounds(rounds, { true }, n)

    fun findPrimeFrom(n: BigInteger): BigInteger = findPrimeFromWith({ true }, n)
    fun findPrimeFromWith(predicate: (BigInteger) -> Boolean, n: BigInteger): BigInteger = findPrimeFromWithRounds(30, predicate, n)
    internal fun findPrimeFromWithRounds(rounds: Int, predicate: (BigInteger) -> Boolean, n: BigInteger): BigInteger {
        var candidate = if (n.testBit(0)) n else n + one
        while (true) {
            if (probablyPrime(rounds, candidate) && predicate(candidate)) return candidate
            candidate += two
        }
    }

    fun primalityTestMillerRabin(tries: Int, n: BigInteger): Boolean = primalityTestMillerRabinDeferred(lazyOf(tries), lazyOf(n))
    fun primalityTestMillerRabinDeferred(triesInput: Lazy<Int>, nInput: Lazy<BigInteger>): Boolean {
        val n = nInput.value
        check(n > three) { "Miller-Rabin requires tested value to be > 3" }
        if (!n.testBit(0)) return false
        val tries = triesInput.value
        check(tries > 0) { "Miller-Rabin tries need to be > 0" }
        val nm1 = n - one
        val s = nm1.lowestSetBit
        val d = nm1.shiftRight(s)
        val action = CryptoPseudoRandom<CryptoChaChaDrg, Boolean> { initial ->
            val result = cryptoRandomMemo {
                var generator = initial
                val random: (Int) -> ByteArray = { count ->
                    val next = generator.value.randomBytesGenerate(count)
                    drawObserver?.invoke(count, next.first.copyOf())
                    generator = lazyOf(next.second)
                    next.first
                }
                var passed = true
                var trial = 0
                while (trial < tries && passed) {
                    val witness = CryptoNumberGenerate.generateBetween(two, n - two, random)
                    val x = CryptoModular.expSafe(witness, d, n)
                    if (x != one && x != nm1) {
                        var squared = x * x % n
                        var round = 1
                        while (round != s && squared != one && squared != nm1) {
                            squared = squared * squared % n
                            round++
                        }
                        if (round == s || squared == one) passed = false
                    }
                    trial++
                }
                passed to generator.value
            }
            cryptoRandomMemo { result.value.first } to cryptoRandomMemo { result.value.second }
        }
        return probabilistic.probabilisticFromDeferred(cryptoRandomMemo { CryptoSerialize.i2osp(n) }, lazyOf(action))
    }

    fun primalityTestFermat(iterations: Int, start: BigInteger, p: BigInteger): Boolean = primalityTestFermatDeferred(lazyOf(iterations), lazyOf(start), lazyOf(p))
    fun primalityTestFermatDeferred(iterations: Lazy<Int>, startInput: Lazy<BigInteger>, pInput: Lazy<BigInteger>): Boolean {
        val count = iterations.value
        val p = pInput.value
        val exponent = p - one
        val start = startInput.value
        var base = start
        val end = start + BigInteger.valueOf(count.toLong())
        while (base <= end) {
            if (CryptoModular.expSafe(base, exponent, p) != one) return false
            base += one
        }
        return true
    }

    fun primalityTestNaive(n: BigInteger): Boolean {
        val upper = CryptoNumberBasic.sqrti(n).second
        if (n <= one) return false
        if (n == two) return true
        if (!n.testBit(0)) return false
        var divisor = three
        while (divisor <= upper) {
            if (divides(divisor, n)) return false
            divisor += two
        }
        return true
    }

    internal fun divides(divisor: BigInteger, n: BigInteger): Boolean {
        if (divisor.signum() == 0) throw ArithmeticException("divide by zero")
        return n.remainder(divisor).signum() == 0
    }

    fun isCoprime(m: BigInteger, n: BigInteger): Boolean = CryptoNumberBasic.gcde(m, n).gcd == one

    class PrimeSizeInvalid : IllegalArgumentException("CryptoError_PrimeSizeInvalid")

    companion object {
        internal fun roundsForDrawn(bits: Int): Int = when {
            bits >= 1300 -> 6
            bits >= 850 -> 8
            bits >= 650 -> 10
            bits >= 550 -> 12
            bits >= 450 -> 14
            bits >= 400 -> 16
            bits >= 350 -> 18
            bits >= 300 -> 20
            bits >= 250 -> 24
            else -> 30
        }
        internal val firstPrimes: List<Int> = listOf(
            2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37,
            41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89,
            97, 101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151,
            157, 163, 167, 173, 179, 181, 191, 193, 197, 199, 211, 223,
            227, 229, 233, 239, 241, 251, 257, 263, 269, 271, 277, 281,
            283, 293, 307, 311, 313, 317, 331, 337, 347, 349, 353, 359,
            367, 373, 379, 383, 389, 397, 401, 409, 419, 421, 431, 433,
            439, 443, 449, 457, 461, 463, 467, 479, 487, 491, 499, 503,
            509, 521, 523, 541, 547, 557, 563, 569, 571, 577, 587, 593,
            599, 601, 607, 613, 617, 619, 631, 641, 643, 647, 653, 659,
            661, 673, 677, 683, 691, 701, 709, 719, 727, 733, 739, 743,
            751, 757, 761, 769, 773, 787, 797, 809, 811, 821, 823, 827,
            829, 839, 853, 857, 859, 863, 877, 881, 883, 887, 907, 911,
            919, 929, 937, 941, 947, 953, 967, 971, 977, 983, 991, 997,
            1009, 1013, 1019, 1021, 1031, 1033, 1039, 1049, 1051, 1061, 1063, 1069,
            1087, 1091, 1093, 1097, 1103, 1109, 1117, 1123, 1129, 1151, 1153, 1163,
            1171, 1181, 1187, 1193, 1201, 1213, 1217, 1223, 1229, 1231, 1237, 1249,
            1259, 1277, 1279, 1283, 1289, 1291, 1297, 1301, 1303, 1307, 1319, 1321,
            1327, 1361, 1367, 1373, 1381, 1399, 1409, 1423, 1427, 1429, 1433, 1439,
            1447, 1451, 1453, 1459, 1471, 1481, 1483, 1487, 1489, 1493, 1499, 1511,
            1523, 1531, 1543, 1549, 1553, 1559, 1567, 1571, 1579, 1583, 1597, 1601,
            1607, 1609, 1613, 1619, 1621, 1627, 1637, 1657, 1663, 1667, 1669, 1693,
            1697, 1699, 1709, 1721, 1723, 1733, 1741, 1747, 1753, 1759, 1777, 1783,
            1787, 1789, 1801, 1811, 1823, 1831, 1847, 1861, 1867, 1871, 1873, 1877,
            1879, 1889, 1901, 1907, 1913, 1931, 1933, 1949, 1951, 1973, 1979, 1987,
            1993, 1997, 1999, 2003, 2011, 2017, 2027, 2029, 2039, 2053, 2063, 2069,
            2081, 2083, 2087, 2089, 2099, 2111, 2113, 2129, 2131, 2137, 2141, 2143,
            2153, 2161, 2179, 2203, 2207, 2213, 2221, 2237, 2239, 2243, 2251, 2267,
            2269, 2273, 2281, 2287, 2293, 2297, 2309, 2311, 2333, 2339, 2341, 2347,
            2351, 2357, 2371, 2377, 2381, 2383, 2389, 2393, 2399, 2411, 2417, 2423,
            2437, 2441, 2447, 2459, 2467, 2473, 2477, 2503, 2521, 2531, 2539, 2543,
            2549, 2551, 2557, 2579, 2591, 2593, 2609, 2617, 2621, 2633, 2647, 2657,
            2659, 2663, 2671, 2677, 2683, 2687, 2689, 2693, 2699, 2707, 2711, 2713,
            2719, 2729, 2731, 2741, 2749, 2753, 2767, 2777, 2789, 2791, 2797, 2801,
            2803, 2819, 2833, 2837, 2843, 2851, 2857, 2861, 2879, 2887, 2897, 2903
        )
    }
}
