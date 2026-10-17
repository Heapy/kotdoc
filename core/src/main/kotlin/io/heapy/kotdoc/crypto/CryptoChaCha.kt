// Crypto.Cipher.ChaCha, crypton 2.0.1; licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto
import java.lang.ref.Cleaner
import java.lang.ref.Reference

/** Immutable public states. Logical cleanup is not a JVM physical-erasure guarantee. */
object CryptoChaCha {
    private class Storage(bytes: ByteArray) {
        private val raw=bytes.copyOf()
        private class Wipe(private val data: ByteArray): Runnable {override fun run(){data.fill(0)}}
        private val cleanup=cleaner.register(this,Wipe(raw))
        fun copy()=try {raw.copyOf()} finally {Reference.reachabilityFence(this)}
        companion object {private val cleaner=Cleaner.create()}
    }
    interface ChaChaState<S> {
        fun getCounter64(): ULong
        fun getCounter32(): UInt
        fun setCounter64(counter: ULong): S
        fun setCounter32(counter: UInt): S
        fun force()
    }
    class State internal constructor(bytes: ByteArray): ChaChaState<State> {
        private val storage=Storage(bytes)
        internal fun copyStorage() = storage.copy()
        override fun getCounter64(): ULong {val copy=storage.copy();return try {CryptoChaChaCore.counter64(CryptoChaChaCore.State(copy)).toULong()}finally {copy.fill(0)}}
        override fun getCounter32(): UInt {val copy=storage.copy();return try {CryptoChaChaCore.counter32(CryptoChaChaCore.State(copy)).toUInt()}finally {copy.fill(0)}}
        override fun setCounter64(counter: ULong) = State(copyStorage().also { CryptoChaChaCore.setCounter64(CryptoChaChaCore.State(it),counter.toLong()) })
        override fun setCounter32(counter: UInt) = State(copyStorage().also { CryptoChaChaCore.setCounter32(CryptoChaChaCore.State(it),counter.toInt()) })
        override fun force() = Unit
    }
    class StateSimple internal constructor(bytes: ByteArray): ChaChaState<StateSimple> {
        private val storage=Storage(bytes)
        internal fun copyStorage() = storage.copy()
        override fun getCounter64(): ULong {val copy=storage.copy();return try {CryptoChaChaCore.counter64(CryptoChaChaCore.State(copy)).toULong()}finally {copy.fill(0)}}
        override fun getCounter32(): UInt {val copy=storage.copy();return try {CryptoChaChaCore.counter32(CryptoChaChaCore.State(copy)).toUInt()}finally {copy.fill(0)}}
        override fun setCounter64(counter: ULong) = StateSimple(copyStorage().also { CryptoChaChaCore.setCounter64(CryptoChaChaCore.State(it),counter.toLong()) })
        override fun setCounter32(counter: UInt) = StateSimple(copyStorage().also { CryptoChaChaCore.setCounter32(CryptoChaChaCore.State(it),counter.toInt()) })
        override fun force() = Unit
    }
    fun initialize(rounds: Int,key: ByteArray,nonce: ByteArray): State {
        if(key.size !in listOf(16,32)) error("ChaCha: key length should be 128 or 256 bits")
        if(nonce.size !in listOf(8,12)) error("ChaCha: nonce length should be 64 or 96 bits")
        if(rounds !in listOf(8,12,20)) error("ChaCha: rounds should be 8, 12 or 20")
        // C sizeof(context)=136 on the observed ABI; Haskell's logical132 allocation
        // is not reproduced as an unsafe JVM write. The independently recorded guard
        // observation is retained in the raw checkpoint.
        val bytes=ByteArray(136)
        CryptoChaChaCore.init(CryptoChaChaCore.Context(bytes),rounds,key.size,CryptoByteSpan(key),nonce.size,CryptoByteSpan(nonce))
        return State(bytes)
    }
    fun initializeX(rounds: Int,key: ByteArray,nonce: ByteArray): State {
        if(key.size!=32) error("XChaCha: key length should be 256 bits")
        if(nonce.size!=24) error("XChaCha: nonce length should be 192 bits")
        if(rounds !in listOf(8,12,20)) error("XChaCha: rounds should be 8, 12 or 20")
        val bytes=ByteArray(136)
        CryptoChaChaCore.initX(CryptoChaChaCore.Context(bytes),rounds,CryptoByteSpan(key),CryptoByteSpan(nonce))
        return State(bytes)
    }
    fun initializeSimple(seed: ByteArray): StateSimple {
        if(seed.size<40) error("ChaCha Random: seed length should be 40 bytes")
        val bytes=ByteArray(64)
        CryptoChaChaCore.initCore(CryptoChaChaCore.State(bytes),32,CryptoByteSpan(seed),8,CryptoByteSpan(seed,32,8))
        return StateSimple(bytes)
    }
    fun generate(previous: State,length: Int): Pair<ByteArray,State> {
        if(length<=0) return ByteArray(0) to previous
        val next=previous.copyStorage();val out=ByteArray(length)
        CryptoChaChaCore.generate(CryptoByteSpan(out),CryptoChaChaCore.Context(next))
        return out to State(next)
    }
    fun combine(previous: State,input: ByteArray): Pair<ByteArray,State> {
        if(input.isEmpty()) return ByteArray(0) to previous
        val next=previous.copyStorage();val out=ByteArray(input.size)
        CryptoChaChaCore.combine(CryptoByteSpan(out),CryptoChaChaCore.Context(next),CryptoByteSpan(input))
        return out to State(next)
    }
    fun generateSimple(previous: StateSimple,length: Int): Pair<ByteArray,StateSimple> {
        val next=previous.copyStorage();val out=ByteArray(length)
        CryptoChaChaCore.random(8,CryptoByteSpan(out),CryptoChaChaCore.State(next))
        return out to StateSimple(next)
    }
    fun generateSimpleBlock(rounds: Int,previous: StateSimple): Pair<ByteArray,StateSimple> {
        val word8=rounds and 255
        if(word8 !in listOf(8,12,20)) error("ChaCha: rounds should be 8, 12 or 20")
        val next=previous.copyStorage();val out=ByteArray(64)
        CryptoChaChaCore.simpleBlock(CryptoByteSpan(out),CryptoChaChaCore.State(next),word8)
        return out to StateSimple(next)
    }
    fun generateSimpleBlockDeferred(rounds: Int,previous: Lazy<StateSimple>): Pair<ByteArray,StateSimple> {
        val state=previous.value
        return generateSimpleBlock(rounds,state)
    }
    /** Separate adapters preserve lazy Haskell argument demand where Kotlin is eager. */
    fun generateDeferred(previous: Lazy<State>,length: Int): Pair<ByteArray,Lazy<State>> =
        if(length<=0) ByteArray(0) to previous else generate(previous.value,length).let {it.first to lazyOf(it.second)}
    fun combineDeferred(previous: Lazy<State>,input: ByteArray): Pair<ByteArray,Lazy<State>> =
        if(input.isEmpty()) ByteArray(0) to previous else combine(previous.value,input).let {it.first to lazyOf(it.second)}
    fun initializeDeferred(rounds: Lazy<Int>,key: Lazy<ByteArray>,nonce: Lazy<ByteArray>): State {
        val r=rounds.value
        val k=key.value
        if(k.size !in listOf(16,32)) error("ChaCha: key length should be 128 or 256 bits")
        val n=nonce.value
        if(n.size !in listOf(8,12)) error("ChaCha: nonce length should be 64 or 96 bits")
        return initialize(r,k,n)
    }
    fun initializeXDeferred(rounds: Lazy<Int>,key: Lazy<ByteArray>,nonce: Lazy<ByteArray>): State {
        val r=rounds.value
        val k=key.value
        if(k.size!=32) error("XChaCha: key length should be 256 bits")
        val n=nonce.value
        if(n.size!=24) error("XChaCha: nonce length should be 192 bits")
        return initializeX(r,k,n)
    }
}
