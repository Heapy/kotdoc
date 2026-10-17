// Translated from crypton 2.0.1 portable MD5 and hash-prefix sources.
// Copyright (C) 2006-2009 Vincent Hanquez; (C) 2020 Olivier Chéron.
// BSD-2-Clause: licenses/crypton-md5-BSD-2-Clause.txt.
package io.heapy.kotdoc.crypto

internal object CryptoMd5 {
    enum class Algorithm(val words:Int=4,val digestSize:Int=16,val contextSize:Int=96,val cSize:Int=88,val stateOffset:Int=72){MD5}
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
    fun init(ctx:Context){ctx.data.fill(0,ctx.offset,ctx.offset+88);val iv=intArrayOf(0x67452301,0xefcdab89U.toInt(),0x98badcfeU.toInt(),0x10325476);for(i in iv.indices)ctx.state(i,iv[i])}
    var observe:((String,Int,IntArray)->Unit)?=null
    private val indices=intArrayOf(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,1,6,11,0,5,10,15,4,9,14,3,8,13,2,7,12,5,8,11,14,1,4,7,10,13,0,3,6,9,12,15,2,0,7,14,5,12,3,10,1,8,15,6,13,4,11,2,9)
    private val constants=intArrayOf(0xd76aa478U.toInt(),0xe8c7b756U.toInt(),0x242070dbU.toInt(),0xc1bdceeeU.toInt(),0xf57c0fafU.toInt(),0x4787c62aU.toInt(),0xa8304613U.toInt(),0xfd469501U.toInt(),0x698098d8U.toInt(),0x8b44f7afU.toInt(),0xffff5bb1U.toInt(),0x895cd7beU.toInt(),0x6b901122U.toInt(),0xfd987193U.toInt(),0xa679438eU.toInt(),0x49b40821U.toInt(),0xf61e2562U.toInt(),0xc040b340U.toInt(),0x265e5a51U.toInt(),0xe9b6c7aaU.toInt(),0xd62f105dU.toInt(),0x02441453U.toInt(),0xd8a1e681U.toInt(),0xe7d3fbc8U.toInt(),0x21e1cde6U.toInt(),0xc33707d6U.toInt(),0xf4d50d87U.toInt(),0x455a14edU.toInt(),0xa9e3e905U.toInt(),0xfcefa3f8U.toInt(),0x676f02d9U.toInt(),0x8d2a4c8aU.toInt(),0xfffa3942U.toInt(),0x8771f681U.toInt(),0x6d9d6122U.toInt(),0xfde5380cU.toInt(),0xa4beea44U.toInt(),0x4bdecfa9U.toInt(),0xf6bb4b60U.toInt(),0xbebfbc70U.toInt(),0x289b7ec6U.toInt(),0xeaa127faU.toInt(),0xd4ef3085U.toInt(),0x04881d05U.toInt(),0xd9d4d039U.toInt(),0xe6db99e5U.toInt(),0x1fa27cf8U.toInt(),0xc4ac5665U.toInt(),0xf4292244U.toInt(),0x432aff97U.toInt(),0xab9423a7U.toInt(),0xfc93a039U.toInt(),0x655b59c3U.toInt(),0x8f0ccc92U.toInt(),0xffeff47dU.toInt(),0x85845dd1U.toInt(),0x6fa87e4fU.toInt(),0xfe2ce6e0U.toInt(),0xa3014314U.toInt(),0x4e0811a1U.toInt(),0xf7537e82U.toInt(),0xbd3af235U.toInt(),0x2ad7d2bbU.toInt(),0xeb86d391U.toInt())
    private val shifts=intArrayOf(7,12,17,22,7,12,17,22,7,12,17,22,7,12,17,22,5,9,14,20,5,9,14,20,5,9,14,20,5,9,14,20,4,11,16,23,4,11,16,23,4,11,16,23,4,11,16,23,6,10,15,21,6,10,15,21,6,10,15,21,6,10,15,21)
    fun chunk(ctx:Context,input:ByteArray,offset:Int=0){
        val w=IntArray(16){loadLE32(input,offset+4*it)};val v=IntArray(4){ctx.state(it)};observe?.invoke("schedule",0,w.copyOf())
        for(i in 0..<64){
            val a=v[0];val b=v[1];val c=v[2];val d=v[3]
            val f=when(i/16){0->d xor (b and (c xor d));1->c xor (d and (b xor c));2->b xor c xor d;else->c xor (b or d.inv())}
            val value=b+Integer.rotateLeft(a+f+w[indices[i]]+constants[i],shifts[i]);v[0]=d;v[1]=value;v[2]=b;v[3]=c
            observe?.invoke("round",i,v.copyOf())
        };for(i in 0..<4)ctx.state(i,ctx.state(i)+v[i])
    }
    fun update(ctx:Context,input:CryptoByteSpan) {
        var len=input.size;var p=input.offset;var index=getIndex(ctx);val fill=64-index;ctx.size(ctx.size()+len)
        if(index!=0 && len>=fill){input.data.copyInto(ctx.data,ctx.offset+8+index,p,p+fill);chunk(ctx,ctx.data,ctx.offset+8);len-=fill;p+=fill;index=0}
        while(len>=64){chunk(ctx,input.data,p);len-=64;p+=64}
        if(len!=0)input.data.copyInto(ctx.data,ctx.offset+8+index,p,p+len)
    }
    fun finalize(ctx:Context,out:CryptoByteSpan) {
        val bits=ByteArray(8);storeLE64(bits,0,ctx.size() shl 3);val index=getIndex(ctx);val n=if(index<56)56-index else 120-index
        val padding=ByteArray(n);padding[0]=128.toByte();update(ctx,CryptoByteSpan(padding));update(ctx,CryptoByteSpan(bits))
        for(i in 0..<4)storeLE32(out.data,out.offset+4*i,ctx.state(i))
    }
    fun getIndex(ctx:Context)=(ctx.size() and 63).toInt()
    fun incrementSize(ctx:Context,n:Int):ByteArray {ctx.size(ctx.size()+(n.toLong() and 0xffffffffL));return ByteArray(8).also{storeLE64(it,0,ctx.size() shl 3)}}
    fun selectDigest(ctx:Context,out:CryptoByteSpan,mask:Int) {
        for(i in 0..<ctx.algorithm.words){val v=ctx.state(i) and mask;for(j in 0..<4){val p=out.offset+4*i+j;out.data[p]=(out.data[p].toInt() xor (v ushr (8*j))).toByte()}}
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
        val full=out
        ctx.data.fill(0,ctx.offset+8+index,ctx.offset+72);full.data.fill(0,full.offset,full.offset+ctx.algorithm.words*4);var pos=0L
        while(control.continues(pos)){
            var b=if(control.hasInput(pos))(input.data[input.offset+pos.toInt()].toInt() and 255) and control.inputMask(pos)else 0
            b=b or (128 and control.firstPaddingMask(pos));if(index>=56)b=b or ((bits[index-56].toInt() and 255) and control.sizeMask(pos))
            val p=ctx.offset+8+index;ctx.data[p]=(ctx.data[p].toInt() xor b).toByte();index++;pos++
            if(index>=64){index=0;chunk(ctx,ctx.data,ctx.offset+8);ctx.data.fill(0,ctx.offset+8,ctx.offset+72);selectDigest(ctx,full,control.outputMask(pos))}
        }
    }
}
