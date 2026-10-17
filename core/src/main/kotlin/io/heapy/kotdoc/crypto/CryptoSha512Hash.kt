// Crypto.Hash / SHA384 / SHA512 / SHA512t, crypton 2.0.1, BSD-3-Clause.
// Vincent Hanquez and contributors; licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

object CryptoSha512Hash {
    enum class Algorithm(val digestSize:Int,val prefix:Boolean) {
        SHA384(48,true), SHA512(64,true), SHA512t_224(28,false), SHA512t_256(32,false);
        val blockSize get()=128
        val contextSize get()=256
    }
    class Context internal constructor(val algorithm:Algorithm,internal val raw:CryptoSha512.Context) {
        fun debugBytes()=raw.data.copyOfRange(raw.offset,raw.offset+256)
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
        val raw=CryptoSha512.Context()
        when(algorithm) {Algorithm.SHA384->CryptoSha512.init384(raw);Algorithm.SHA512->CryptoSha512.init(raw);Algorithm.SHA512t_224->CryptoSha512.initT(raw,224);Algorithm.SHA512t_256->CryptoSha512.initT(raw,256)}
        return Context(algorithm,raw)
    }
    fun hashUpdate(context:Context,bytes:ByteArray):Context=hashUpdates(context,listOf(bytes))
    fun hashUpdates(context:Context,chunks:Iterable<ByteArray>):Context {
        val nonempty=chunks.filter{it.isNotEmpty()};if(nonempty.isEmpty())return context
        val copy=context.raw.copy();for(bytes in nonempty)CryptoSha512.update(copy,CryptoByteSpan(bytes))
        return Context(context.algorithm,copy)
    }
    fun hashFinalize(context:Context):Digest {
        val copy=context.raw.copy();val out=ByteArray(context.algorithm.digestSize)
        when(context.algorithm) {Algorithm.SHA384->CryptoSha512.finalize384(copy,CryptoByteSpan(out));Algorithm.SHA512->CryptoSha512.finalize(copy,CryptoByteSpan(out));else->CryptoSha512.finalizeT(copy,out.size*8,CryptoByteSpan(out))}
        return Digest(out)
    }
    fun hashFinalizePrefix(context:Context,bytes:ByteArray,length:Int):Digest {
        require(context.algorithm.prefix){"algorithm has no HashAlgorithmPrefix instance"}
        val out=ByteArray(context.algorithm.digestSize);val copy=context.raw.copy()
        if(context.algorithm==Algorithm.SHA384)CryptoSha512.finalizePrefix384(copy,CryptoByteSpan(bytes),length,CryptoByteSpan(out))else CryptoSha512.finalizePrefix(copy,CryptoByteSpan(bytes),length,CryptoByteSpan(out))
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
}
