// Crypto.Hash / MD2, crypton 2.0.1, BSD-3-Clause.
// Vincent Hanquez and contributors; licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

object CryptoMd2Hash {
    enum class Algorithm(val digestSize:Int,val contextSize:Int) {
        MD2(16,96);
        val blockSize get()=16
        internal val raw get()=CryptoMd2.Algorithm.valueOf(name)
    }
    class Context internal constructor(val algorithm:Algorithm,internal val raw:CryptoMd2.Context):CryptoHmac.ByteArrayAccess {
        override val size get()=algorithm.contextSize
        override fun <A> withBytes(action:(ByteArray)->Lazy<A>)=action(raw.data)
        fun debugBytes()=raw.data.copyOfRange(raw.offset,raw.offset+algorithm.contextSize)
        fun force()=Unit
    }
    class Digest private constructor(private val storage:Lazy<ByteArray>) : Comparable<Digest>,CryptoHmac.ByteArrayAccess {
        internal constructor(bytes:ByteArray):this(lazyOf(bytes))
        private val value get()=storage.value
        companion object {internal fun deferred(bytes:()->ByteArray)=Digest(memo(bytes))}
        override val size get()=value.size
        override fun <A> withBytes(action:(ByteArray)->Lazy<A>)=action(value)
        fun bytes()=value.copyOf()
        override fun toString()=value.toHexString()
        override fun equals(other:Any?)=other is Digest && value.contentEquals(other.value)
        override fun hashCode()=value.contentHashCode()
        override fun compareTo(other:Digest):Int {for(i in 0..<minOf(value.size,other.value.size)){val c=(value[i].toInt() and 255).compareTo(other.value[i].toInt() and 255);if(c!=0)return c};return value.size.compareTo(other.value.size)}
        fun force(){storage.value}
    }
    fun hashInit(algorithm:Algorithm):Context {
        val raw=CryptoMd2.Context(algorithm.raw)
        CryptoMd2.init(raw)
        return Context(algorithm,raw)
    }
    fun hashUpdate(context:Context,bytes:ByteArray):Context=hashUpdates(context,listOf(bytes))
    fun hashUpdates(context:Context,chunks:Iterable<ByteArray>):Context {
        val iterator=chunks.iterator();var first:ByteArray?=null
        while(iterator.hasNext()){val next=iterator.next();if(next.isNotEmpty()){first=next;break}}
        if(first==null)return context
        val copy=context.raw.copy();CryptoMd2.update(copy,CryptoByteSpan(first))
        while(iterator.hasNext()){val bytes=iterator.next();if(bytes.isNotEmpty())CryptoMd2.update(copy,CryptoByteSpan(bytes))}
        return Context(context.algorithm,copy)
    }
    fun hashUpdateAccess(context:Context,bytes:CryptoHmac.ByteArrayAccess)=hashUpdatesAccess(context,listOf(bytes))
    fun hashUpdatesAccess(context:Context,chunks:Iterable<CryptoHmac.ByteArrayAccess>):Context {
        val iterator=chunks.iterator();var first:CryptoHmac.ByteArrayAccess?=null
        while(iterator.hasNext()){val b=iterator.next();if(b.size!=0){first=b;break}}
        if(first==null)return context
        val raw=context.raw.copy()
        fun process(b:CryptoHmac.ByteArrayAccess)=b.withBytes{input->CryptoMd2.update(raw,CryptoByteSpan(input,0,b.size));lazyOf(Unit)}
        process(first)
        while(iterator.hasNext()){val b=iterator.next();if(b.size!=0)process(b)}
        return Context(context.algorithm,raw)
    }
    fun digestFromAccess(algorithm:Algorithm,bytes:CryptoHmac.ByteArrayAccess):Digest? {
        if(bytes.size!=algorithm.digestSize)return null
        return Digest.deferred{val output=ByteArray(bytes.size);bytes.withBytes{it.copyInto(output,0,0,bytes.size);lazyOf(Unit)};output}
    }
    fun hashFinalize(context:Context):Digest {
        val copy=context.raw.copy();val out=ByteArray(context.algorithm.digestSize)
        CryptoMd2.finalize(copy,CryptoByteSpan(out))
        return Digest(out)
    }
    fun hash(algorithm:Algorithm,bytes:ByteArray)=hashFinalize(hashUpdate(hashInit(algorithm),bytes))
    fun hashLazy(algorithm:Algorithm,chunks:Iterable<ByteArray>)=hashFinalize(hashUpdates(hashInit(algorithm),chunks))
    fun digestFromByteString(algorithm:Algorithm,bytes:ByteArray):Digest?=if(bytes.size==algorithm.digestSize)Digest(bytes.copyOf())else null
    fun readsDigest(algorithm:Algorithm,input:String):Pair<Digest,String>? {
        val count=algorithm.digestSize*2;if(input.length<count)return null
        val out=ByteArray(algorithm.digestSize)
        fun digit(c:Char)=when(c){in '0'..'9'->c-'0';in 'a'..'f'->c-'a'+10;in 'A'..'F'->c-'A'+10;else->-1}
        for(i in out.indices){val a=digit(input[2*i]);val b=digit(input[2*i+1]);if(a<0||b<0)return null;out[i]=(a*16+b).toByte()}
        return Digest(out) to input.substring(count)
    }
    fun hashInitWithDeferred(algorithm:Algorithm,witness:Lazy<Any?>)=hashInit(algorithm)
    fun hashWithDeferred(algorithm:Algorithm,witness:Lazy<Any?>,bytes:ByteArray)=hash(algorithm,bytes)
    fun hashInitWith(algorithm:Algorithm)=hashInit(algorithm)
    fun hashWith(algorithm:Algorithm,bytes:ByteArray)=hash(algorithm,bytes)
    class MutableContext internal constructor(val algorithm:Algorithm,internal val raw:CryptoMd2.Context):CryptoHmac.ByteArrayAccess {
        override val size get()=algorithm.contextSize
        override fun <A> withBytes(action:(ByteArray)->Lazy<A>)=action(raw.data)
        fun debugBytes()=raw.data.copyOfRange(raw.offset,raw.offset+algorithm.contextSize)
    }
    fun hashMutableInit(algorithm:Algorithm)=MutableContext(algorithm,CryptoMd2.Context(algorithm.raw).also(CryptoMd2::init))
    fun hashMutableInitWith(algorithm:Algorithm)=hashMutableInit(algorithm)
    fun hashMutableInitWithDeferred(algorithm:Algorithm,witness:Lazy<Any?>)=hashMutableInit(algorithm)
    fun hashMutableUpdate(context:MutableContext,bytes:ByteArray)=CryptoMd2.update(context.raw,CryptoByteSpan(bytes))
    fun hashMutableUpdateAccess(context:MutableContext,bytes:CryptoHmac.ByteArrayAccess){bytes.withBytes{CryptoMd2.update(context.raw,CryptoByteSpan(it,0,bytes.size));lazyOf(Unit)}}
    fun hashMutableFinalize(context:MutableContext):Digest=Digest(ByteArray(context.algorithm.digestSize).also{CryptoMd2.finalize(context.raw,CryptoByteSpan(it))})
    fun hashMutableReset(context:MutableContext)=CryptoMd2.init(context.raw)
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
            val copy=c.raw.copy();CryptoMd2.update(copy,CryptoByteSpan(first))
            while(iterator.hasNext()){val next=iterator.next();if(next.isNotEmpty())CryptoMd2.update(copy,CryptoByteSpan(next))}
            Context(c.algorithm,copy)
        }
    }
    fun deferHashFinalize(context:Lazy<Context>)=memo{hashFinalize(context.value)}
}
