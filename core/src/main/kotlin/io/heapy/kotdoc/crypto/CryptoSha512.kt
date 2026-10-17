// Translated from crypton 2.0.1 crypton_sha512.c and crypton_hash_prefix.{c,h}.
// Copyright (C) 2006-2009 Vincent Hanquez; (C) 2020 Olivier Chéron.
// BSD-2-Clause notices: licenses/crypton-sha512-BSD-2-Clause.txt.
package io.heapy.kotdoc.crypto

internal object CryptoSha512 {
    class Context(val data: ByteArray = ByteArray(256), val offset: Int = 0) {
        fun copy() = Context(data.copyOfRange(offset, offset + 256))
        fun word(index: Int) = loadLE(data, offset + index * 8)
        fun word(index: Int, value: Long) = storeLE(data, offset + index * 8, value)
        fun state(index: Int) = word(18 + index)
        fun state(index: Int, value: Long) = word(18 + index, value)
    }
    private fun loadLE(a: ByteArray, p: Int): Long { var v=0L; for(i in 0..<8) v=v or ((a[p+i].toLong() and 255) shl (8*i)); return v }
    private fun storeLE(a: ByteArray,p: Int,v: Long) { for(i in 0..<8)a[p+i]=(v ushr (8*i)).toByte() }
    private fun loadBE(a: ByteArray,p: Int): Long { var v=0L;for(i in 0..<8)v=(v shl 8) or (a[p+i].toLong() and 255);return v }
    private fun storeBE(a: ByteArray,p: Int,v: Long) { for(i in 0..<8)a[p+i]=(v ushr (56-8*i)).toByte() }
    fun msb(a:Int)=-(a ushr 31)
    fun lt(a:Int,b:Int)=msb(a xor ((a xor b) or ((a-b) xor b)))
    fun ge(a:Int,b:Int)=lt(a,b).inv()
    fun isZero(a:Int)=msb(a.inv() and (a-1))
    fun eq(a:Int,b:Int)=isZero(a xor b)
    fun msb64(a:Long)=-(a ushr 63)
    fun lt64(a:Long,b:Long)=msb64(a xor ((a xor b) or ((a-b) xor b)))
    private val k=longArrayOf(0x428a2f98d728ae22UL.toLong(),0x7137449123ef65cdUL.toLong(),0xb5c0fbcfec4d3b2fUL.toLong(),0xe9b5dba58189dbbcUL.toLong(),0x3956c25bf348b538UL.toLong(),0x59f111f1b605d019UL.toLong(),0x923f82a4af194f9bUL.toLong(),0xab1c5ed5da6d8118UL.toLong(),0xd807aa98a3030242UL.toLong(),0x12835b0145706fbeUL.toLong(),0x243185be4ee4b28cUL.toLong(),0x550c7dc3d5ffb4e2UL.toLong(),0x72be5d74f27b896fUL.toLong(),0x80deb1fe3b1696b1UL.toLong(),0x9bdc06a725c71235UL.toLong(),0xc19bf174cf692694UL.toLong(),0xe49b69c19ef14ad2UL.toLong(),0xefbe4786384f25e3UL.toLong(),0x0fc19dc68b8cd5b5UL.toLong(),0x240ca1cc77ac9c65UL.toLong(),0x2de92c6f592b0275UL.toLong(),0x4a7484aa6ea6e483UL.toLong(),0x5cb0a9dcbd41fbd4UL.toLong(),0x76f988da831153b5UL.toLong(),0x983e5152ee66dfabUL.toLong(),0xa831c66d2db43210UL.toLong(),0xb00327c898fb213fUL.toLong(),0xbf597fc7beef0ee4UL.toLong(),0xc6e00bf33da88fc2UL.toLong(),0xd5a79147930aa725UL.toLong(),0x06ca6351e003826fUL.toLong(),0x142929670a0e6e70UL.toLong(),0x27b70a8546d22ffcUL.toLong(),0x2e1b21385c26c926UL.toLong(),0x4d2c6dfc5ac42aedUL.toLong(),0x53380d139d95b3dfUL.toLong(),0x650a73548baf63deUL.toLong(),0x766a0abb3c77b2a8UL.toLong(),0x81c2c92e47edaee6UL.toLong(),0x92722c851482353bUL.toLong(),0xa2bfe8a14cf10364UL.toLong(),0xa81a664bbc423001UL.toLong(),0xc24b8b70d0f89791UL.toLong(),0xc76c51a30654be30UL.toLong(),0xd192e819d6ef5218UL.toLong(),0xd69906245565a910UL.toLong(),0xf40e35855771202aUL.toLong(),0x106aa07032bbd1b8UL.toLong(),0x19a4c116b8d2d0c8UL.toLong(),0x1e376c085141ab53UL.toLong(),0x2748774cdf8eeb99UL.toLong(),0x34b0bcb5e19b48a8UL.toLong(),0x391c0cb3c5c95a63UL.toLong(),0x4ed8aa4ae3418acbUL.toLong(),0x5b9cca4f7763e373UL.toLong(),0x682e6ff3d6b2b8a3UL.toLong(),0x748f82ee5defb2fcUL.toLong(),0x78a5636f43172f60UL.toLong(),0x84c87814a1f0ab72UL.toLong(),0x8cc702081a6439ecUL.toLong(),0x90befffa23631e28UL.toLong(),0xa4506cebde82bde9UL.toLong(),0xbef9a3f7b2c67915UL.toLong(),0xc67178f2e372532bUL.toLong(),0xca273eceea26619cUL.toLong(),0xd186b8c721c0c207UL.toLong(),0xeada7dd6cde0eb1eUL.toLong(),0xf57d4f7fee6ed178UL.toLong(),0x06f067aa72176fbaUL.toLong(),0x0a637dc5a2c898a6UL.toLong(),0x113f9804bef90daeUL.toLong(),0x1b710b35131c471bUL.toLong(),0x28db77f523047d84UL.toLong(),0x32caab7b40c72493UL.toLong(),0x3c9ebe0a15c9bebcUL.toLong(),0x431d67c49c100d4cUL.toLong(),0x4cc5d4becb3e42b6UL.toLong(),0x597f299cfc657e2aUL.toLong(),0x5fcb6fab3ad6faecUL.toLong(),0x6c44198c4a475817UL.toLong())
    private val iv384=longArrayOf(0xcbbb9d5dc1059ed8UL.toLong(),0x629a292a367cd507UL.toLong(),0x9159015a3070dd17UL.toLong(),0x152fecd8f70e5939UL.toLong(),0x67332667ffc00b31UL.toLong(),0x8eb44a8768581511UL.toLong(),0xdb0c2e0d64f98fa7UL.toLong(),0x47b5481dbefa4fa4UL.toLong())
    private val iv512=longArrayOf(0x6a09e667f3bcc908UL.toLong(),0xbb67ae8584caa73bUL.toLong(),0x3c6ef372fe94f82bUL.toLong(),0xa54ff53a5f1d36f1UL.toLong(),0x510e527fade682d1UL.toLong(),0x9b05688c2b3e6c1fUL.toLong(),0x1f83d9abfb41bd6bUL.toLong(),0x5be0cd19137e2179UL.toLong())
    private val iv224=longArrayOf(0x8c3d37c819544da2UL.toLong(),0x73e1996689dcd4d6UL.toLong(),0x1dfab7ae32ff9c82UL.toLong(),0x679dd514582f9fcfUL.toLong(),0x0f6d2b697bd44da8UL.toLong(),0x77e36f7304c48942UL.toLong(),0x3f9d85a86a1d36c8UL.toLong(),0x1112e6ad91d692a1UL.toLong())
    private val iv256=longArrayOf(0x22312194fc2bf72cUL.toLong(),0x9f555fa3c84c64c2UL.toLong(),0x2393b86b6f53b151UL.toLong(),0x963877195940eabdUL.toLong(),0x96283ee2a88effe3UL.toLong(),0xbe5e1e2553863992UL.toLong(),0x2b0199fc2c85b8aaUL.toLong(),0x0eb72ddc81c52ca2UL.toLong())
    private fun initialize(ctx:Context,iv:LongArray) { ctx.data.fill(0,ctx.offset,ctx.offset+208); for(i in 0..<8)ctx.state(i,iv[i]) }
    fun init(ctx:Context)=initialize(ctx,iv512)
    fun init384(ctx:Context)=initialize(ctx,iv384)
    var observe: ((String,Int,LongArray)->Unit)? = null
    private fun r(v:Long,n:Int)=java.lang.Long.rotateRight(v,n)
    private fun s0(v:Long)=r(v,1) xor r(v,8) xor (v ushr 7)
    private fun s1(v:Long)=r(v,19) xor r(v,61) xor (v ushr 6)
    fun chunk(ctx:Context,input:ByteArray,offset:Int=0) {
        val w=LongArray(80);for(i in 0..<16)w[i]=loadBE(input,offset+8*i)
        for(i in 16..<80)w[i]=s1(w[i-2])+w[i-7]+s0(w[i-15])+w[i-16]
        val v=LongArray(8){ctx.state(it)}
        observe?.invoke("schedule",0,w.copyOf())
        for(i in 0..<80) {
            val a=v[0];val b=v[1];val c=v[2];val d=v[3];val e=v[4];val f=v[5];val g=v[6];val h=v[7]
            val t1=h+(r(e,14) xor r(e,18) xor r(e,41))+(g xor (e and (f xor g)))+k[i]+w[i]
            val t2=(r(a,28) xor r(a,34) xor r(a,39))+((a and b) or (c and (a or b)))
            v[0]=t1+t2;v[1]=a;v[2]=b;v[3]=c;v[4]=d+t1;v[5]=e;v[6]=f;v[7]=g
            observe?.invoke("round",i,v.copyOf())
        }
        for(i in 0..<8)ctx.state(i,ctx.state(i)+v[i])
    }
    fun update(ctx:Context,input:CryptoByteSpan) {
        var len=input.size;var p=input.offset;var index=(ctx.word(0) and 127).toInt();val fill=128-index
        val size=ctx.word(0)+len;ctx.word(0,size);if(java.lang.Long.compareUnsigned(size,len.toLong())<0)ctx.word(1,ctx.word(1)+1)
        if(index!=0 && len>=fill) { input.data.copyInto(ctx.data,ctx.offset+16+index,p,p+fill);chunk(ctx,ctx.data,ctx.offset+16);len-=fill;p+=fill;index=0 }
        while(len>=128) { chunk(ctx,input.data,p);len-=128;p+=128 }
        if(len!=0)input.data.copyInto(ctx.data,ctx.offset+16+index,p,p+len)
    }
    fun update384(ctx:Context,input:CryptoByteSpan)=update(ctx,input)
    fun updateT(ctx:Context,input:CryptoByteSpan)=update(ctx,input)
    fun finalize(ctx:Context,out:CryptoByteSpan) {
        val bits=ByteArray(16);storeBE(bits,0,(ctx.word(1) shl 3) or (ctx.word(0) ushr 61));storeBE(bits,8,ctx.word(0) shl 3)
        val index=(ctx.word(0) and 127).toInt();val n=if(index<112)112-index else 240-index
        val padding=ByteArray(n);padding[0]=128.toByte();update(ctx,CryptoByteSpan(padding));update(ctx,CryptoByteSpan(bits))
        for(i in 0..<8)storeBE(out.data,out.offset+8*i,ctx.state(i))
    }
    fun finalize384(ctx:Context,out:CryptoByteSpan) { val temp=ByteArray(64);finalize(ctx,CryptoByteSpan(temp));temp.copyInto(out.data,out.offset,0,48) }
    fun getIndex(ctx:Context)=(ctx.word(0) and 127).toInt()
    fun incrementSize(ctx:Context,n:Int):ByteArray {
        ctx.word(0,ctx.word(0)+(n.toLong() and 0xffffffffL));ctx.word(1,ctx.word(1)+(lt64(ctx.word(0),n.toLong() and 0xffffffffL) and 1))
        return ByteArray(16).also {storeBE(it,0,(ctx.word(1) shl 3) or (ctx.word(0) ushr 61));storeBE(it,8,ctx.word(0) shl 3)}
    }
    fun selectDigest(ctx:Context,out:CryptoByteSpan,mask:Int) {
        val m=(mask.toLong() and 0xffffffffL).let {it or (it shl 32)}
        for(i in 0..<8) {val v=ctx.state(i) and m;for(j in 0..<8) {val p=out.offset+8*i+j;out.data[p]=(out.data[p].toInt() xor (v ushr (56-8*j)).toInt()).toByte()} }
    }
    /** Source uint32 positions can exceed signed Int while padding a valid JVM buffer. */
    class PrefixControl(val length:Int,nInput:Int,lowCounter:Long) {
        val n=nInput+((length-nInput) and lt(length,nInput))
        val initialIndex=(lowCounter and 127).toInt()
        private val padIndex=((lowCounter+n.toLong()) and 127).toInt()
        val padLength=240-padIndex-(128 and lt(padIndex,112))
        val limit=length.toLong()+144L
        private val paddingStart=n.toLong()+padLength
        private val digestEnd=paddingStart+16L
        fun continues(position:Long)=position<limit
        fun hasInput(position:Long)=position<length.toLong()
        // The source masked helpers operate on uint32, not signed positions.
        fun inputMask(position:Long)=lt(position.toInt(),n)
        fun firstPaddingMask(position:Long)=eq(position.toInt(),n)
        fun sizeMask(position:Long)=ge(position.toInt(),paddingStart.toInt())
        fun outputMask(position:Long)=eq(position.toInt(),digestEnd.toInt())
    }
    fun finalizePrefix(ctx:Context,input:CryptoByteSpan,nInput:Int,out:CryptoByteSpan) {
        val control=PrefixControl(input.size,nInput,ctx.word(0));var index=control.initialIndex
        val bits=incrementSize(ctx,control.n)
        ctx.data.fill(0,ctx.offset+16+index,ctx.offset+144);out.data.fill(0,out.offset,out.offset+64)
        var pos=0L
        while(control.continues(pos)) {
            // Narrow only within the real input; padding positions may exceed Int.MAX_VALUE.
            var b=if(control.hasInput(pos))(input.data[input.offset+pos.toInt()].toInt() and 255) and control.inputMask(pos) else 0
            b=b or (128 and control.firstPaddingMask(pos));if(index>=112)b=b or ((bits[index-112].toInt() and 255) and control.sizeMask(pos))
            val p=ctx.offset+16+index;ctx.data[p]=(ctx.data[p].toInt() xor b).toByte();index++;pos++
            if(index>=128) {index=0;chunk(ctx,ctx.data,ctx.offset+16);ctx.data.fill(0,ctx.offset+16,ctx.offset+144);selectDigest(ctx,out,control.outputMask(pos))}
        }
    }
    fun finalizePrefix384(ctx:Context,input:CryptoByteSpan,n:Int,out:CryptoByteSpan) {val temp=ByteArray(64);finalizePrefix(ctx,input,n,CryptoByteSpan(temp));temp.copyInto(out.data,out.offset,0,48)}
    fun initT(ctx:Context,bits:Int) {
        ctx.data.fill(0,ctx.offset,ctx.offset+208)
        if(java.lang.Integer.compareUnsigned(bits,512)>=0)return
        when(bits) {224->initialize(ctx,iv224);256->initialize(ctx,iv256);else->{init(ctx);for(i in 0..<8)ctx.state(i,ctx.state(i) xor 0xa5a5a5a5a5a5a5a5UL.toLong());update(ctx,CryptoByteSpan("SHA-512/$bits".toByteArray(Charsets.US_ASCII)));val out=ByteArray(64);finalize(ctx,CryptoByteSpan(out));ctx.data.fill(0,ctx.offset,ctx.offset+208);for(i in 0..<8)ctx.state(i,loadBE(out,8*i))}}
    }
    fun finalizeT(ctx:Context,bits:Int,out:CryptoByteSpan) {require(bits in 0..512);val temp=ByteArray(64);finalize(ctx,CryptoByteSpan(temp));temp.copyInto(out.data,out.offset,0,bits/8)}
}
