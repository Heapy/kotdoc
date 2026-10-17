// Crypto.Random.Probabilistic, crypton2.0.1; licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

/** One instance represents the source's single process-secret lifetime.
 * The host supplies actual entropy; this class never substitutes a different DRG.
 */
class CryptoProbabilistic(entropy: (Int)->ByteArray) {
    private val secret by cryptoRandomMemo {
        val bytes=entropy(32)
        bytes.copyOf()
    }
    fun <A> probabilisticFrom(material: ByteArray,action: CryptoPseudoRandom<CryptoChaChaDrg,A>): A =
        probabilisticFromDeferred(lazyOf(material.copyOf()),lazyOf(action))
    fun <A> probabilisticFromDeferred(material: Lazy<ByteArray>,action: Lazy<CryptoPseudoRandom<CryptoChaChaDrg,A>>): A {
        val generator=lazy {
            val input=secret+material.value
            val digest=try {CryptoSha512Hash.hash(CryptoSha512Hash.Algorithm.SHA512,input).bytes()}finally {input.fill(0)}
            val seed=digest.copyOf(40)
            try {CryptoChaChaDrg.initialize(seed)}finally {seed.fill(0);digest.fill(0)}
        }
        return CryptoPseudoRandom.withDRG(generator,action.value).first.value
    }
}
