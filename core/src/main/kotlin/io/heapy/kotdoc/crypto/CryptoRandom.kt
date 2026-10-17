// Crypto.Random.{ChaChaDRG,Types} and Crypto.Random, crypton2.0.1.
// licenses/crypton-BSD-3-Clause.txt; RAM storage licenses/ram-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto
import java.math.BigInteger
import java.nio.ByteOrder

fun interface CryptoDrgOperations<G> {fun generate(length: Int,generator: Lazy<G>): Pair<Lazy<ByteArray>,Lazy<G>>}
/** Memoized pure thunk: failures are retained and rethrown, never swallowed. */
internal fun <T> cryptoRandomMemo(evaluate: ()->T): Lazy<T> {
    val result=lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {Result.success(evaluate())} catch(failure: Throwable) {Result.failure(failure)}
    }
    return object: Lazy<T> {
        override val value: T get()=result.value.getOrThrow()
        override fun isInitialized()=result.isInitialized()
    }
}
interface CryptoDrg<G> {fun randomBytesGenerate(length: Int): Pair<ByteArray,G>}

class CryptoChaChaDrg private constructor(private val state: CryptoChaCha.StateSimple): CryptoDrg<CryptoChaChaDrg> {
    override fun randomBytesGenerate(length: Int): Pair<ByteArray,CryptoChaChaDrg> {
        if(length<=0)return ByteArray(0) to this
        val next=CryptoChaCha.generateSimple(state,length)
        return next.first to CryptoChaChaDrg(next.second)
    }
    fun force()=state.force()
    companion object {
        val operations=CryptoDrgOperations<CryptoChaChaDrg>{length,generator->
            if(length<=0)lazyOf(ByteArray(0)) to generator else {
                val result=cryptoRandomMemo{generator.value.randomBytesGenerate(length)}
                cryptoRandomMemo{result.value.first} to cryptoRandomMemo{result.value.second}
            }
        }
        fun initialize(seed: ByteArray)=CryptoChaChaDrg(CryptoChaCha.initializeSimple(seed))
        /** pokeElemOff Word64 is host endian, not a network-byte-order tuple. */
        fun initializeWords(a: ULong,b: ULong,c: ULong,d: ULong,e: ULong,byteOrder: ByteOrder=ByteOrder.nativeOrder()): CryptoChaChaDrg {
            val seed=ByteArray(40)
            for((i,word)in listOf(a,b,c,d,e).withIndex())for(j in 0..<8)seed[8*i+j]=(word shr(8*(if(byteOrder==ByteOrder.LITTLE_ENDIAN)j else 7-j))).toByte()
            return try {initialize(seed)} finally {seed.fill(0)}
        }
        fun generateDeferred(length: Int,previous: Lazy<CryptoChaChaDrg>): Pair<ByteArray,Lazy<CryptoChaChaDrg>> =
            if(length<=0)ByteArray(0) to previous else previous.value.randomBytesGenerate(length).let{it.first to lazyOf(it.second)}
    }
}

/** Pair components stay lazy, matching the pure State-like source monad. */
class CryptoPseudoRandom<G: CryptoDrg<G>,A> internal constructor(private val evaluate: (Lazy<G>)->Pair<Lazy<A>,Lazy<G>>) {
    internal fun run(generator: Lazy<G>): Pair<Lazy<A>,Lazy<G>> {
        val root=cryptoRandomMemo{evaluate(generator)}
        return cryptoRandomMemo{root.value.first.value} to cryptoRandomMemo{root.value.second.value}
    }
    fun <B> map(f: (Lazy<A>)->B)=CryptoPseudoRandom<G,B>{g->val pair=run(g);cryptoRandomMemo{f(pair.first)} to pair.second}
    fun <B> flatMap(f: (Lazy<A>)->CryptoPseudoRandom<G,B>)=CryptoPseudoRandom<G,B>{g->
        val result=cryptoRandomMemo{val first=run(g);f(first.first).run(first.second)}
        cryptoRandomMemo{result.value.first.value} to cryptoRandomMemo{result.value.second.value}
    }
    companion object {
        fun <G:CryptoDrg<G>,A> pure(value: A)=pureDeferred<G,A>(lazyOf(value))
        fun <G:CryptoDrg<G>,A> pureDeferred(value: Lazy<A>)=CryptoPseudoRandom<G,A>{g->value to g}
        fun <G:CryptoDrg<G>> getRandomBytes(length: Int,operations: CryptoDrgOperations<G>)=CryptoPseudoRandom<G,ByteArray>{g->operations.generate(length,g)}
        fun <G:CryptoDrg<G>,A,B> apply(function: CryptoPseudoRandom<G,(Lazy<A>)->B>,argument: CryptoPseudoRandom<G,A>)=CryptoPseudoRandom<G,B>{g->
            val first=function.run(g);val second=cryptoRandomMemo{argument.run(first.second)}
            cryptoRandomMemo{first.first.value(second.value.first)} to cryptoRandomMemo{second.value.second.value}
        }
        fun <G:CryptoDrg<G>,A> withDRG(generator: Lazy<G>,action: CryptoPseudoRandom<G,A>)=action.run(generator)
        fun <G:CryptoDrg<G>,A> withDRG(generator: G,action: CryptoPseudoRandom<G,A>)=action.run(lazyOf(generator))
    }
}

object CryptoRandom {
    class Seed internal constructor(bytes: ByteArray) {
        private val raw=bytes.copyOf()
        fun toByteArray()=raw.copyOf()
        fun <A> withByteArray(action: (ByteArray)->A)=action(toByteArray())
    }
    fun <G:CryptoDrg<G>,A> withDRG(generator: Lazy<G>,action: CryptoPseudoRandom<G,A>)=CryptoPseudoRandom.withDRG(generator,action)
    fun <G:CryptoDrg<G>,A> withDRG(generator: G,action: CryptoPseudoRandom<G,A>)=CryptoPseudoRandom.withDRG(generator,action)
    fun seedToInteger(seed: Seed)=CryptoSerialize.os2ip(seed.toByteArray())
    fun seedFromInteger(value: BigInteger): Seed = Seed(CryptoSerialize.i2ospOf_(40,value.mod(BigInteger.ONE.shiftLeft(320))))
    fun seedFromBinary(bytes: ByteArray): CryptoEcc.Failable<Seed> = if(bytes.size==40)CryptoEcc.Failable.Passed(Seed(bytes))else CryptoEcc.Failable.Failed(CryptoEcc.Error.CryptoError_SeedSizeInvalid)
    /** Entropy provider is an explicit missing OS-backend dependency, not a DRG replacement. */
    private fun scrubbedHash512(bytes: ByteArray): ByteArray {
        val context=CryptoSha512.Context();val digest=ByteArray(64)
        return try {
            CryptoSha512.init(context);CryptoSha512.update(context,CryptoByteSpan(bytes));CryptoSha512.finalize(context,CryptoByteSpan(digest))
            digest.copyOf(40)
        }finally {context.data.fill(0);digest.fill(0)}
    }
    fun seedNew(random: (Int)->ByteArray,insecureEntropy: Boolean=false): Seed {
        val requested=if(insecureEntropy)64 else 40
        val bytes=random(requested)
        return if(insecureEntropy)Seed(scrubbedHash512(bytes))else Seed(bytes)
    }
    fun drgNew(random: (Int)->ByteArray,insecureEntropy: Boolean=false)=drgNewSeed(seedNew(random,insecureEntropy))
    fun drgNewSeed(seed: Seed)=CryptoChaChaDrg.initialize(seed.toByteArray())
    fun drgNewTest(a: ULong,b: ULong,c: ULong,d: ULong,e: ULong)=CryptoChaChaDrg.initializeWords(a,b,c,d,e)
    fun <G:CryptoDrg<G>,A> withRandomBytes(generator: Lazy<G>,length: Int,operations: CryptoDrgOperations<G>,f: (Lazy<ByteArray>)->A): Pair<Lazy<A>,Lazy<G>> {
        val generated=cryptoRandomMemo{operations.generate(length,generator)}
        val bytes=cryptoRandomMemo{generated.value.first.value}
        return cryptoRandomMemo{f(bytes)} to cryptoRandomMemo{generated.value.second.value}
    }
}
