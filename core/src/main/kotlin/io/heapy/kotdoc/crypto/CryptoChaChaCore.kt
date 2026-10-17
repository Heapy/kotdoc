// Translated from crypton 2.0.1 crypton_chacha.c, Copyright (c) 2014-2015 Vincent Hanquez.
// BSD-3-Clause: licenses/crypton-chacha-BSD-3-Clause.txt. Native code is a dev oracle only.
package io.heapy.kotdoc.crypto
internal object CryptoChaChaCore {
    class State(val data:ByteArray=ByteArray(64),val offset:Int=0) {
        fun word(i:Int)=load(data,offset+4*i)
        fun word(i:Int,value:Int)=store(data,offset+4*i,value)
    }
    class Context(val data:ByteArray=ByteArray(136),val offset:Int=0) {
        val state get()=State(data,offset)
        var previousOffset:Int get()=data[offset+128].toInt() and 255;set(v){data[offset+128]=v.toByte()}
        var previousLength:Int get()=data[offset+129].toInt() and 255;set(v){data[offset+129]=v.toByte()}
        var rounds:Int get()=data[offset+130].toInt() and 255;set(v){data[offset+130]=v.toByte()}
    }
    private fun load(a:ByteArray,p:Int):Int {var v=0;for(i in 0..<4)v=v or ((a[p+i].toInt() and 255) shl (8*i));return v}
    private fun store(a:ByteArray,p:Int,v:Int){for(i in 0..<4)a[p+i]=(v ushr (8*i)).toByte()}
    private val sigma="expand 32-byte k".toByteArray(Charsets.US_ASCII)
    private val tau="expand 16-byte k".toByteArray(Charsets.US_ASCII)
    var observe:((String,Int,IntArray)->Unit)?=null
    private fun quarter(x:IntArray,a:Int,b:Int,c:Int,d:Int){x[a]+=x[b];x[d]=java.lang.Integer.rotateLeft(x[d] xor x[a],16);x[c]+=x[d];x[b]=java.lang.Integer.rotateLeft(x[b] xor x[c],12);x[a]+=x[b];x[d]=java.lang.Integer.rotateLeft(x[d] xor x[a],8);x[c]+=x[d];x[b]=java.lang.Integer.rotateLeft(x[b] xor x[c],7)}
    private fun rounds(rounds:Int,state:State,label:String):IntArray {
        val x=IntArray(16){state.word(it)};observe?.invoke(label+"Input",rounds,x.copyOf());var remaining=rounds
        while(remaining>0) {
            quarter(x,0,4,8,12);quarter(x,1,5,9,13);quarter(x,2,6,10,14);quarter(x,3,7,11,15)
            quarter(x,0,5,10,15);quarter(x,1,6,11,12);quarter(x,2,7,8,13);quarter(x,3,4,9,14)
            observe?.invoke(label+"Round",remaining,x.copyOf());remaining-=2
        };return x
    }
    fun core(rounds:Int,out:CryptoByteSpan,state:State) {
        val x=rounds(rounds,state,"core");for(i in 0..<16)x[i]+=state.word(i)
        observe?.invoke("coreOutput",rounds,x.copyOf());for(i in 0..<16)store(out.data,out.offset+4*i,x[i])
    }
    fun hcore(rounds:Int,out:CryptoByteSpan,state:State) {val x=rounds(rounds,state,"hcore");for((i,j)in listOf(0,1,2,3,12,13,14,15).withIndex())store(out.data,out.offset+4*i,x[j])}
    fun initKeyState(state:State,keyLength:Int,key:CryptoByteSpan) {
        val constants=if(keyLength==32)sigma else tau;for(i in 0..<4)state.word(i,load(constants,4*i))
        for(i in 0..<4)state.word(i+4,load(key.data,key.offset+4*i))
        val second=if(keyLength==32)16 else 0;for(i in 0..<4)state.word(i+8,load(key.data,key.offset+second+4*i))
    }
    fun initCore(state:State,keyLength:Int,key:CryptoByteSpan,ivLength:Int,iv:CryptoByteSpan) {
        initKeyState(state,keyLength,key);state.word(12,0)
        when(ivLength){8->{state.word(13,0);state.word(14,load(iv.data,iv.offset));state.word(15,load(iv.data,iv.offset+4))};12->{for(i in 0..<3)state.word(i+13,load(iv.data,iv.offset+4*i))}}
    }
    fun hinitCore(state:State,key:CryptoByteSpan,iv:CryptoByteSpan) {initKeyState(state,32,key);for(i in 0..<4)state.word(i+12,load(iv.data,iv.offset+4*i))}
    fun init(ctx:Context,rounds:Int,keyLength:Int,key:CryptoByteSpan,ivLength:Int,iv:CryptoByteSpan){ctx.data.fill(0,ctx.offset,ctx.offset+136);ctx.rounds=rounds;initCore(ctx.state,keyLength,key,ivLength,iv)}
    fun hchacha(rounds:Int,key:CryptoByteSpan,iv:CryptoByteSpan,out:CryptoByteSpan){val state=State();hinitCore(state,key,iv);val temp=ByteArray(32);hcore(rounds and 255,CryptoByteSpan(temp),state);state.data.fill(0);temp.copyInto(out.data,out.offset);temp.fill(0)}
    fun initX(ctx:Context,rounds:Int,key:CryptoByteSpan,iv:CryptoByteSpan){ctx.data.fill(0,ctx.offset,ctx.offset+136);ctx.rounds=rounds;val subkey=ByteArray(32);hchacha(rounds,key,iv,CryptoByteSpan(subkey));initCore(ctx.state,32,CryptoByteSpan(subkey),8,CryptoByteSpan(iv.data,iv.offset+16,8))}
    fun counter64(state:State)=(state.word(12).toLong() and 0xffffffffL) or (state.word(13).toLong() shl 32)
    fun counter32(state:State)=state.word(12)
    fun setCounter64(state:State,value:Long){if(counter64(state)==value)return;state.word(12,value.toInt());state.word(13,(value ushr 32).toInt())}
    fun setCounter32(state:State,value:Int){if(counter32(state)==value)return;state.word(12,value)}
    private fun advance(state:State){state.word(12,state.word(12)+1);if(state.word(12)==0)state.word(13,state.word(13)+1)}
    fun simpleBlock(out:CryptoByteSpan,state:State,rounds:Int){core(rounds and 255,out,state);advance(state)}
    fun generate(out:CryptoByteSpan,ctx:Context)=stream(out,ctx,null)
    fun combine(out:CryptoByteSpan,ctx:Context,input:CryptoByteSpan)=stream(out,ctx,input)
    private fun stream(out:CryptoByteSpan,ctx:Context,input:CryptoByteSpan?) {
        var length=out.size;if(length==0)return;var dst=out.offset;var src=input?.offset?:0
        fun write(index:Int,value:Int){out.data[dst+index]=((input?.data?.get(src+index)?.toInt()?:0) xor value).toByte()}
        if(ctx.previousLength>0) {
            val count=minOf(ctx.previousLength,length);for(i in 0..<count)write(i,ctx.data[ctx.offset+64+ctx.previousOffset+i].toInt())
            ctx.data.fill(0,ctx.offset+64+ctx.previousOffset,ctx.offset+64+ctx.previousOffset+count);ctx.previousLength-=count;ctx.previousOffset+=count;src+=count;dst+=count;length-=count
        }
        if(length==0)return
        while(length>=64){
            if(input==null && dst%8==0){core(ctx.rounds,CryptoByteSpan(out.data,dst,64),ctx.state);advance(ctx.state)}
            else {val block=ByteArray(64);core(ctx.rounds,CryptoByteSpan(block),ctx.state);advance(ctx.state);for(i in 0..<64)write(i,block[i].toInt())}
            length-=64;src+=64;dst+=64
        }
        if(length>0){val block=ByteArray(64);core(ctx.rounds,CryptoByteSpan(block),ctx.state);advance(ctx.state);for(i in 0..<length)write(i,block[i].toInt());ctx.previousLength=64-length;ctx.previousOffset=length;for(i in length..<64)ctx.data[ctx.offset+64+i]=block[i]}
    }
    fun random(rounds:Int,out:CryptoByteSpan,state:State){var length=out.size;var dst=out.offset;if(length==0)return
        while(length>0){val block=ByteArray(64);core(rounds,CryptoByteSpan(block),state);val count=minOf(length,16);block.copyInto(out.data,dst,40,40+count);initCore(state,32,CryptoByteSpan(block,0,32),8,CryptoByteSpan(block,32,8));length-=count;dst+=count}
    }
}
