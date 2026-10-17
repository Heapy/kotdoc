// Crypto.Hash / SHA1 / SHA224 / SHA256, crypton 2.0.1, BSD-3-Clause.
// Vincent Hanquez and contributors; licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

object CryptoSha32Hash {
    enum class Algorithm(val digestSize:Int,val contextSize:Int) {
        SHA1(20,96), SHA224(28,192), SHA256(32,192);
        val blockSize get()=64
        val prefix get()=true
        internal val raw get()=CryptoSha32.Algorithm.valueOf(name)
    }
    class Context internal constructor(val algorithm:Algorithm,internal val raw:CryptoSha32.Context) {
        fun debugBytes()=raw.data.copyOfRange(raw.offset,raw.offset+algorithm.contextSize)
        fun force()=Unit
    }
    class Digest internal constructor(bytes:ByteArray) : Comparable<Digest> {
        private val value=bytes.copyOf()
        fun bytes()=value.copyOf()
        override fun toString()=value.toHexString()
        override fun equals(other:Any?)=other is Digest && value.contentEquals(other.value)
        override fun hashCode()=value.contentHashCode()
        override fun compareTo(other:Digest):Int {for(i in 0..<minOf(value.size,other.value.size)){val c=(value[i].toInt() and 255).compareTo(other.value[i].toInt() and 255);if(c!=0)return c};return value.size.compareTo(other.value.size)}
        fun force()=Unit
    }
    fun hashInit(algorithm:Algorithm):Context {
        val raw=CryptoSha32.Context(algorithm.raw)
        CryptoSha32.init(raw)
        return Context(algorithm,raw)
    }
    fun hashUpdate(context:Context,bytes:ByteArray):Context=hashUpdates(context,listOf(bytes))
    fun hashUpdates(context:Context,chunks:Iterable<ByteArray>):Context {
        val iterator=chunks.iterator();var first:ByteArray?=null
        while(iterator.hasNext()){val next=iterator.next();if(next.isNotEmpty()){first=next;break}}
        if(first==null)return context
        val copy=context.raw.copy();CryptoSha32.update(copy,CryptoByteSpan(first))
        while(iterator.hasNext()){val bytes=iterator.next();if(bytes.isNotEmpty())CryptoSha32.update(copy,CryptoByteSpan(bytes))}
        return Context(context.algorithm,copy)
    }
    fun hashFinalize(context:Context):Digest {
        val copy=context.raw.copy();val out=ByteArray(context.algorithm.digestSize)
        CryptoSha32.finalize(copy,CryptoByteSpan(out))
        return Digest(out)
    }
    fun hashFinalizePrefix(context:Context,bytes:ByteArray,length:Int):Digest {
        require(context.algorithm.prefix){"algorithm has no HashAlgorithmPrefix instance"}
        val out=ByteArray(context.algorithm.digestSize);val copy=context.raw.copy()
        CryptoSha32.finalizePrefix(copy,CryptoByteSpan(bytes),length,CryptoByteSpan(out))
        return Digest(out)
    }
    fun hash(algorithm:Algorithm,bytes:ByteArray)=hashFinalize(hashUpdate(hashInit(algorithm),bytes))
    fun hashLazy(algorithm:Algorithm,chunks:Iterable<ByteArray>)=hashFinalize(hashUpdates(hashInit(algorithm),chunks))
    fun hashPrefix(algorithm:Algorithm,bytes:ByteArray,length:Int)=hashFinalizePrefix(hashInit(algorithm),bytes,length)
    fun digestFromByteString(algorithm:Algorithm,bytes:ByteArray):Digest?=if(bytes.size==algorithm.digestSize)Digest(bytes)else null
    fun readsDigest(algorithm:Algorithm,input:String):Pair<Digest,String>? {
        val count=algorithm.digestSize*2;if(input.length<count)return null
        val out=ByteArray(algorithm.digestSize)
        fun digit(c:Char)=when(c){in '0'..'9'->c-'0';in 'a'..'f'->c-'a'+10;in 'A'..'F'->c-'A'+10;else->-1}
        for(i in out.indices){val a=digit(input[2*i]);val b=digit(input[2*i+1]);if(a<0||b<0)return null;out[i]=(a*16+b).toByte()}
        return Digest(out) to input.substring(count)
    }
    fun hashInitWithDeferred(algorithm:Algorithm,witness:Lazy<Any?>)=hashInit(algorithm)
    fun hashWithDeferred(algorithm:Algorithm,witness:Lazy<Any?>,bytes:ByteArray)=hash(algorithm,bytes)
    fun hashPrefixWithDeferred(algorithm:Algorithm,witness:Lazy<Any?>,bytes:ByteArray,length:Int)=hashPrefix(algorithm,bytes,length)
    fun hashInitWith(algorithm:Algorithm)=hashInit(algorithm)
    fun hashWith(algorithm:Algorithm,bytes:ByteArray)=hash(algorithm,bytes)
    fun hashPrefixWith(algorithm:Algorithm,bytes:ByteArray,length:Int)=hashPrefix(algorithm,bytes,length)
    class MutableContext internal constructor(val algorithm:Algorithm,internal val raw:CryptoSha32.Context) {
        fun debugBytes()=raw.data.copyOfRange(raw.offset,raw.offset+algorithm.contextSize)
    }
    fun hashMutableInit(algorithm:Algorithm)=MutableContext(algorithm,CryptoSha32.Context(algorithm.raw).also(CryptoSha32::init))
    fun hashMutableInitWith(algorithm:Algorithm)=hashMutableInit(algorithm)
    fun hashMutableInitWithDeferred(algorithm:Algorithm,witness:Lazy<Any?>)=hashMutableInit(algorithm)
    fun hashMutableUpdate(context:MutableContext,bytes:ByteArray)=CryptoSha32.update(context.raw,CryptoByteSpan(bytes))
    fun hashMutableFinalize(context:MutableContext):Digest=Digest(ByteArray(context.algorithm.digestSize).also{CryptoSha32.finalize(context.raw,CryptoByteSpan(it))})
    fun hashMutableReset(context:MutableContext)=CryptoSha32.init(context.raw)
    // Explicit deferred carriers model source pure demand; failures are shared as well as values.
    private fun <A> memo(block:()->A):Lazy<A> = object:Lazy<A> {
        private var result:Result<A>?=null
        override val value:A get()=synchronized(this){val r=result?:try{Result.success(block())}catch(t:Throwable){Result.failure<A>(t)}.also{result=it};r.getOrThrow()}
        override fun isInitialized()=result!=null
    }
    fun deferHashInit(algorithm:Algorithm,witness:Lazy<Any?>)=memo{hashInitWithDeferred(algorithm,witness)}
    fun deferHashUpdate(context:Lazy<Context>,bytes:Lazy<ByteArray>)=memo{val b=bytes.value;if(b.isEmpty())context.value else hashUpdate(context.value,b)}
    fun deferHashUpdates(context:Lazy<Context>,chunks:Lazy<Iterable<ByteArray>>)=memo{
        val iterator=chunks.value.iterator();var first:ByteArray?=null
        while(iterator.hasNext()){val next=iterator.next();if(next.isNotEmpty()){first=next;break}}
        val c=context.value
        if(first==null)c else {
            val copy=c.raw.copy();CryptoSha32.update(copy,CryptoByteSpan(first))
            while(iterator.hasNext()){val next=iterator.next();if(next.isNotEmpty())CryptoSha32.update(copy,CryptoByteSpan(next))}
            Context(c.algorithm,copy)
        }
    }
    fun deferHashFinalize(context:Lazy<Context>)=memo{hashFinalize(context.value)}
    fun deferHashFinalizePrefix(context:Lazy<Context>,bytes:Lazy<ByteArray>,length:Lazy<Int>)=memo{val c=context.value;val b=bytes.value;hashFinalizePrefix(c,b,length.value)}
}
