// Crypto.PubKey.MaskGenFunction, crypton2.0.1; Vincent Hanquez.
// licenses/crypton-BSD-3-Clause.txt.
package io.heapy.kotdoc.crypto

object CryptoMaskGen {
    data class Plan(val chunks:Long,val remainder:Int,val maximumCounter:Long)
    fun plan(length:Int,digestLength:Int):Plan {
        if(digestLength==0)throw ArithmeticException("divide by zero")
        val chunks=Math.floorDiv(length,digestLength).toLong();val left=Math.floorMod(length,digestLength)
        return Plan(chunks,left,chunks+if(left>0)1 else 0)
    }
    fun interface Algorithm { fun generate(seed:ByteArray,length:Int):ByteArray }
    fun hash(algorithm:CryptoHmac.HashAlgorithm,message:ByteArray):ByteArray {
        val context=ByteArray(algorithm.contextSize);algorithm.initializeResult(context)
        if(message.isNotEmpty())algorithm.updateResult(context,message)
        return ByteArray(algorithm.digestSize).also{algorithm.finalizeResult(context.copyOf(),it)}
    }
    fun mgf1(algorithm:CryptoHmac.HashAlgorithm)=Algorithm{seed,length->mgf1(algorithm,seed,length)}
    fun mgf1(algorithm:CryptoHmac.HashAlgorithm,seed:ByteArray,length:Int):ByteArray = mgf1Deferred(algorithm,lazyOf(seed),lazyOf(length)).value
    fun mgf1Deferred(algorithm:CryptoHmac.HashAlgorithm,seed:Lazy<ByteArray>,length:Lazy<Int>):Lazy<ByteArray> = mgf1AccessDeferred(algorithm,cryptoRandomMemo{CryptoHmac.byteArrayAccess(seed.value)},length)
    fun mgf1(algorithm:CryptoHmac.HashAlgorithm,seed:CryptoHmac.ByteArrayAccess,length:Int):ByteArray=mgf1AccessDeferred(algorithm,lazyOf(seed),lazyOf(length)).value
    fun mgf1AccessDeferred(algorithm:CryptoHmac.HashAlgorithm,seed:Lazy<CryptoHmac.ByteArrayAccess>,length:Lazy<Int>):Lazy<ByteArray> = cryptoRandomMemo {
        // Bang-pattern seededCtx is evaluated even for an empty/negative result.
        val len=length.value
        val bytes=seed.value
        val empty=bytes.size==0
        val initial=ByteArray(algorithm.contextSize);algorithm.initializeResult(initial)
        val raw=if(empty)initial else initial.copyOf().also{context->bytes.withBytes{input->algorithm.updateResult(context,if(input.size==bytes.size)input else input.copyOfRange(0,bytes.size))}}
        val digestLength=algorithm.digestSize
        val maximum=plan(len,digestLength).maximumCounter
        if(len<=0||maximum<=0)return@cryptoRandomMemo ByteArray(0)
        val output=ByteArray(len);var offset=0;var counter=0L
        while(counter<maximum){
            val context=raw.copyOf()
            val encoded=byteArrayOf((counter ushr 24).toByte(),(counter ushr 16).toByte(),(counter ushr 8).toByte(),counter.toByte())
            algorithm.updateResult(context,encoded)
            val digest=ByteArray(digestLength);algorithm.finalizeResult(context.copyOf(),digest)
            val count=minOf(digest.size,len-offset);digest.copyInto(output,offset,0,count);offset+=count;counter++
        }
        output
    }
}
