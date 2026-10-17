// Translated from crypton 2.0.1 portable SHA1/SHA224/SHA256 and hash-prefix sources.
// Copyright (C) 2006-2009 Vincent Hanquez; (C) 2020 Olivier Chéron.
// BSD-2-Clause: licenses/crypton-sha32-BSD-2-Clause.txt.
package io.heapy.kotdoc.crypto

internal object CryptoSha32 {
    enum class Algorithm(val words:Int,val digestSize:Int,val contextSize:Int,val cSize:Int,val stateOffset:Int) {
        SHA1(5,20,96,96,72), SHA224(8,28,192,168,136), SHA256(8,32,192,168,136)
    }
    class Context(val algorithm:Algorithm,val data:ByteArray=ByteArray(algorithm.contextSize),val offset:Int=0) {
        fun copy()=Context(algorithm,data.copyOfRange(offset,offset+algorithm.contextSize))
        fun size():Long=loadLE64(data,offset)
        fun size(value:Long)=storeLE64(data,offset,value)
        fun state(index:Int)=loadLE32(data,offset+algorithm.stateOffset+4*index)
        fun state(index:Int,value:Int)=storeLE32(data,offset+algorithm.stateOffset+4*index,value)
    }
    private fun loadLE64(a:ByteArray,p:Int):Long {var v=0L;for(i in 0..<8)v=v or ((a[p+i].toLong() and 255) shl (8*i));return v}
    private fun storeLE64(a:ByteArray,p:Int,v:Long){for(i in 0..<8)a[p+i]=(v ushr (8*i)).toByte()}
    private fun loadLE32(a:ByteArray,p:Int):Int {var v=0;for(i in 0..<4)v=v or ((a[p+i].toInt() and 255) shl (8*i));return v}
    private fun storeLE32(a:ByteArray,p:Int,v:Int){for(i in 0..<4)a[p+i]=(v ushr (8*i)).toByte()}
    private fun loadBE32(a:ByteArray,p:Int):Int {var v=0;for(i in 0..<4)v=(v shl 8) or (a[p+i].toInt() and 255);return v}
    private fun storeBE32(a:ByteArray,p:Int,v:Int){for(i in 0..<4)a[p+i]=(v ushr (24-8*i)).toByte()}
    private fun storeBE64(a:ByteArray,p:Int,v:Long){for(i in 0..<8)a[p+i]=(v ushr (56-8*i)).toByte()}
    fun msb(a:Int)=-(a ushr 31)
    fun lt(a:Int,b:Int)=msb(a xor ((a xor b) or ((a-b) xor b)))
    fun ge(a:Int,b:Int)=lt(a,b).inv()
    fun isZero(a:Int)=msb(a.inv() and (a-1))
    fun eq(a:Int,b:Int)=isZero(a xor b)
    fun msb64(a:Long)=-(a ushr 63)
    fun lt64(a:Long,b:Long)=msb64(a xor ((a xor b) or ((a-b) xor b)))
    private val iv1=intArrayOf(0x67452301U.toInt(),0xefcdab89U.toInt(),0x98badcfeU.toInt(),0x10325476U.toInt(),0xc3d2e1f0U.toInt())
    private val iv224=intArrayOf(0xc1059ed8U.toInt(),0x367cd507U.toInt(),0x3070dd17U.toInt(),0xf70e5939U.toInt(),0xffc00b31U.toInt(),0x68581511U.toInt(),0x64f98fa7U.toInt(),0xbefa4fa4U.toInt())
    private val iv256=intArrayOf(0x6a09e667U.toInt(),0xbb67ae85U.toInt(),0x3c6ef372U.toInt(),0xa54ff53aU.toInt(),0x510e527fU.toInt(),0x9b05688cU.toInt(),0x1f83d9abU.toInt(),0x5be0cd19U.toInt())
    private val k=intArrayOf(0x428a2f98U.toInt(),0x71374491U.toInt(),0xb5c0fbcfU.toInt(),0xe9b5dba5U.toInt(),0x3956c25bU.toInt(),0x59f111f1U.toInt(),0x923f82a4U.toInt(),0xab1c5ed5U.toInt(),0xd807aa98U.toInt(),0x12835b01U.toInt(),0x243185beU.toInt(),0x550c7dc3U.toInt(),0x72be5d74U.toInt(),0x80deb1feU.toInt(),0x9bdc06a7U.toInt(),0xc19bf174U.toInt(),0xe49b69c1U.toInt(),0xefbe4786U.toInt(),0x0fc19dc6U.toInt(),0x240ca1ccU.toInt(),0x2de92c6fU.toInt(),0x4a7484aaU.toInt(),0x5cb0a9dcU.toInt(),0x76f988daU.toInt(),0x983e5152U.toInt(),0xa831c66dU.toInt(),0xb00327c8U.toInt(),0xbf597fc7U.toInt(),0xc6e00bf3U.toInt(),0xd5a79147U.toInt(),0x06ca6351U.toInt(),0x14292967U.toInt(),0x27b70a85U.toInt(),0x2e1b2138U.toInt(),0x4d2c6dfcU.toInt(),0x53380d13U.toInt(),0x650a7354U.toInt(),0x766a0abbU.toInt(),0x81c2c92eU.toInt(),0x92722c85U.toInt(),0xa2bfe8a1U.toInt(),0xa81a664bU.toInt(),0xc24b8b70U.toInt(),0xc76c51a3U.toInt(),0xd192e819U.toInt(),0xd6990624U.toInt(),0xf40e3585U.toInt(),0x106aa070U.toInt(),0x19a4c116U.toInt(),0x1e376c08U.toInt(),0x2748774cU.toInt(),0x34b0bcb5U.toInt(),0x391c0cb3U.toInt(),0x4ed8aa4aU.toInt(),0x5b9cca4fU.toInt(),0x682e6ff3U.toInt(),0x748f82eeU.toInt(),0x78a5636fU.toInt(),0x84c87814U.toInt(),0x8cc70208U.toInt(),0x90befffaU.toInt(),0xa4506cebU.toInt(),0xbef9a3f7U.toInt(),0xc67178f2U.toInt())
    fun init(ctx:Context) {
        ctx.data.fill(0,ctx.offset,ctx.offset+ctx.algorithm.cSize)
        val iv=when(ctx.algorithm){Algorithm.SHA1->iv1;Algorithm.SHA224->iv224;Algorithm.SHA256->iv256}
        for(i in iv.indices)ctx.state(i,iv[i])
    }
    var observe:((String,Int,IntArray)->Unit)?=null
    private fun r(v:Int,n:Int)=Integer.rotateRight(v,n)
    private fun l(v:Int,n:Int)=Integer.rotateLeft(v,n)
    fun chunk(ctx:Context,input:ByteArray,offset:Int=0) {
        if(ctx.algorithm==Algorithm.SHA1)chunk1(ctx,input,offset)else chunk256(ctx,input,offset)
    }
    private fun chunk1(ctx:Context,input:ByteArray,offset:Int) {
        val w=IntArray(16){loadBE32(input,offset+4*it)};val v=IntArray(5){ctx.state(it)}
        observe?.invoke("schedule",0,w.copyOf())
        for(i in 0..<80) {
            val wi=if(i<16)w[i]else l(w[i and 15] xor w[(i-14) and 15] xor w[(i-8) and 15] xor w[(i-3) and 15],1).also{w[i and 15]=it}
            val a=v[0];val b=v[1];val c=v[2];val d=v[3];val e=v[4]
            val f=when(i/20){0->d xor (b and (c xor d));1,3->b xor c xor d;else->(b and c)+(d and (b xor c))}
            val constant=when(i/20){0->0x5a827999;1->0x6ed9eba1;2->0x8f1bbcdcU.toInt();else->0xca62c1d6U.toInt()}
            v[0]=e+l(a,5)+f+constant+wi;v[1]=a;v[2]=l(b,30);v[3]=c;v[4]=d
            observe?.invoke("round",i,v.copyOf())
        }
        for(i in v.indices)ctx.state(i,ctx.state(i)+v[i])
    }
    private fun chunk256(ctx:Context,input:ByteArray,offset:Int) {
        val w=IntArray(64);for(i in 0..<16)w[i]=loadBE32(input,offset+4*i)
        for(i in 16..<64){val x=w[i-2];val y=w[i-15];w[i]=(r(x,17) xor r(x,19) xor (x ushr 10))+w[i-7]+(r(y,7) xor r(y,18) xor (y ushr 3))+w[i-16]}
        val v=IntArray(8){ctx.state(it)};observe?.invoke("schedule",0,w.copyOf())
        for(i in 0..<64){
            val a=v[0];val b=v[1];val c=v[2];val d=v[3];val e=v[4];val f=v[5];val g=v[6];val h=v[7]
            val t1=h+(r(e,6) xor r(e,11) xor r(e,25))+(g xor (e and (f xor g)))+k[i]+w[i]
            val t2=(r(a,2) xor r(a,13) xor r(a,22))+((a and b) or (c and (a or b)))
            v[0]=t1+t2;v[1]=a;v[2]=b;v[3]=c;v[4]=d+t1;v[5]=e;v[6]=f;v[7]=g
            observe?.invoke("round",i,v.copyOf())
        }
        for(i in v.indices)ctx.state(i,ctx.state(i)+v[i])
    }
    fun update(ctx:Context,input:CryptoByteSpan) {
        var len=input.size;var p=input.offset;var index=getIndex(ctx);val fill=64-index;ctx.size(ctx.size()+len)
        if(index!=0 && len>=fill){input.data.copyInto(ctx.data,ctx.offset+8+index,p,p+fill);chunk(ctx,ctx.data,ctx.offset+8);len-=fill;p+=fill;index=0}
        while(len>=64){chunk(ctx,input.data,p);len-=64;p+=64}
        if(len!=0)input.data.copyInto(ctx.data,ctx.offset+8+index,p,p+len)
    }
    fun finalize(ctx:Context,out:CryptoByteSpan) {
        val bits=ByteArray(8);storeBE64(bits,0,ctx.size() shl 3);val index=getIndex(ctx);val n=if(index<56)56-index else 120-index
        val padding=ByteArray(n);padding[0]=128.toByte();update(ctx,CryptoByteSpan(padding));update(ctx,CryptoByteSpan(bits))
        if(ctx.algorithm==Algorithm.SHA224){val tmp=ByteArray(32);for(i in 0..<8)storeBE32(tmp,4*i,ctx.state(i));tmp.copyInto(out.data,out.offset,0,28)}else for(i in 0..<ctx.algorithm.words)storeBE32(out.data,out.offset+4*i,ctx.state(i))
    }
    fun getIndex(ctx:Context)=(ctx.size() and 63).toInt()
    fun incrementSize(ctx:Context,n:Int):ByteArray {ctx.size(ctx.size()+(n.toLong() and 0xffffffffL));return ByteArray(8).also{storeBE64(it,0,ctx.size() shl 3)}}
    fun selectDigest(ctx:Context,out:CryptoByteSpan,mask:Int) {
        for(i in 0..<ctx.algorithm.words){val v=ctx.state(i) and mask;for(j in 0..<4){val p=out.offset+4*i+j;out.data[p]=(out.data[p].toInt() xor (v ushr (24-8*j))).toByte()}}
    }
    class PrefixControl(val length:Int,nInput:Int,counter:Long) {
        val n=nInput+((length-nInput) and lt(length,nInput))
        val initialIndex=(counter and 63).toInt()
        private val padIndex=((counter+n.toLong()) and 63).toInt()
        val padLength=120-padIndex-(64 and lt(padIndex,56))
        val limit=length.toLong()+72L
        private val paddingStart=n.toLong()+padLength
        private val digestEnd=paddingStart+8L
        fun continues(position:Long)=position<limit
        fun hasInput(position:Long)=position<length.toLong()
        fun inputMask(position:Long)=lt(position.toInt(),n)
        fun firstPaddingMask(position:Long)=eq(position.toInt(),n)
        fun sizeMask(position:Long)=ge(position.toInt(),paddingStart.toInt())
        fun outputMask(position:Long)=eq(position.toInt(),digestEnd.toInt())
    }
    fun finalizePrefix(ctx:Context,input:CryptoByteSpan,nInput:Int,out:CryptoByteSpan) {
        val control=PrefixControl(input.size,nInput,ctx.size());var index=control.initialIndex;val bits=incrementSize(ctx,control.n)
        val full=if(ctx.algorithm==Algorithm.SHA224)CryptoByteSpan(ByteArray(32))else out
        ctx.data.fill(0,ctx.offset+8+index,ctx.offset+72);full.data.fill(0,full.offset,full.offset+ctx.algorithm.words*4);var pos=0L
        while(control.continues(pos)){
            var b=if(control.hasInput(pos))(input.data[input.offset+pos.toInt()].toInt() and 255) and control.inputMask(pos)else 0
            b=b or (128 and control.firstPaddingMask(pos));if(index>=56)b=b or ((bits[index-56].toInt() and 255) and control.sizeMask(pos))
            val p=ctx.offset+8+index;ctx.data[p]=(ctx.data[p].toInt() xor b).toByte();index++;pos++
            if(index>=64){index=0;chunk(ctx,ctx.data,ctx.offset+8);ctx.data.fill(0,ctx.offset+8,ctx.offset+72);selectDigest(ctx,full,control.outputMask(pos))}
        }
        if(ctx.algorithm==Algorithm.SHA224)full.data.copyInto(out.data,out.offset,full.offset,full.offset+28)
    }
}
