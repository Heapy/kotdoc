// Translated from crypton2.0.1 crypton_sha3.c, Copyright(C)2012 Vincent Hanquez.
// BSD2-Clause: licenses/crypton-sha3-BSD-2-Clause.txt. Native code is dev oracle only.
package io.heapy.kotdoc.crypto
internal object CryptoSha3 {
 class Context(val data:ByteArray=ByteArray(376),val offset:Int=0) {
  fun copy()=Context(data.copyOfRange(offset,data.size))
  var index:Int get()=word32(0);set(v){put32(0,v)}
  var rate:Int get()=word32(4);set(v){put32(4,v)}
  private fun word32(i:Int)= (0..<4).fold(0){a,j->a or((data[offset+i+j].toInt()and 255)shl(8*j))}
  private fun put32(i:Int,v:Int){for(j in 0..<4)data[offset+i+j]=(v ushr(8*j)).toByte()}
  fun lane(i:Int)=load(data,offset+8+8*i)
  fun lane(i:Int,value:Long)=store(data,offset+8+8*i,value)
 }
 private val rounds=longArrayOf(0x1,0x8082,0x800000000000808aUL.toLong(),0x8000000080008000UL.toLong(),0x808b,0x80000001,0x8000000080008081UL.toLong(),0x8000000000008009UL.toLong(),0x8a,0x88,0x80008009,0x8000000a,0x8000808b,0x800000000000008bUL.toLong(),0x8000000000008089UL.toLong(),0x8000000000008003UL.toLong(),0x8000000000008002UL.toLong(),0x8000000000000080UL.toLong(),0x800a,0x800000008000000aUL.toLong(),0x8000000080008081UL.toLong(),0x8000000000008080UL.toLong(),0x80000001,0x8000000080008008UL.toLong())
 private val rotations=intArrayOf(1,3,6,10,15,21,28,36,45,55,2,14,27,41,56,8,25,43,62,18,39,61,20,44)
 private val positions=intArrayOf(10,7,11,17,18,3,5,16,8,21,24,4,15,23,19,13,12,2,20,14,22,9,6,1)
 var observe:((String,Int,LongArray)->Unit)?=null
 private fun load(data:ByteArray,offset:Int):Long {var value=0L;for(j in 0..<8)value=value or((data[offset+j].toLong()and 255)shl(8*j));return value}
 private fun store(data:ByteArray,offset:Int,value:Long){for(j in 0..<8)data[offset+j]=(value ushr(8*j)).toByte()}
 private fun chunk(ctx:Context,input:ByteArray?,offset:Int=0,count:Int=0){for(i in 0..<count)ctx.lane(i,ctx.lane(i)xor load(input!!,offset+i*8));val state=LongArray(25){ctx.lane(it)};val column=LongArray(5)
  observe?.invoke("absorb",count,state.copyOf())
  for(round in 0..<24){for(i in 0..<5)column[i]=state[i]xor state[i+5]xor state[i+10]xor state[i+15]xor state[i+20]
   for(i in 0..<5){val value=column[(i+4)%5]xor java.lang.Long.rotateLeft(column[(i+1)%5],1);for(j in 0..<25 step 5)state[j+i]=state[j+i]xor value}
   var value=state[1];for(i in 0..<24){val j=positions[i];val old=state[j];state[j]=java.lang.Long.rotateLeft(value,rotations[i]);value=old}
   for(j in 0..<25 step 5){for(i in 0..<5)column[i]=state[j+i];for(i in 0..<5)state[j+i]=state[j+i]xor(column[(i+1)%5].inv()and column[(i+2)%5])}
   state[0]=state[0]xor rounds[round];observe?.invoke("round",round,state.copyOf())
  };for(i in 0..<25)ctx.lane(i,state[i])
 }
 fun init(ctx:Context,securityBits:Int){val rate=200-2*(securityBits/8);ctx.data.fill(0,ctx.offset,ctx.offset+208+rate);ctx.rate=rate}
 fun update(ctx:Context,input:CryptoByteSpan){var length=input.size;var offset=input.offset;val fill=ctx.rate-ctx.index
  if(ctx.index==ctx.rate){chunk(ctx,ctx.data,ctx.offset+208,ctx.rate/8);ctx.index=0}
  if(ctx.index!=0&&length>=fill){input.data.copyInto(ctx.data,ctx.offset+208+ctx.index,offset,offset+fill);chunk(ctx,ctx.data,ctx.offset+208,ctx.rate/8);length-=fill;offset+=fill;ctx.index=0}
  while(length>=ctx.rate){chunk(ctx,input.data,offset,ctx.rate/8);length-=ctx.rate;offset+=ctx.rate}
  if(length!=0){input.data.copyInto(ctx.data,ctx.offset+208+ctx.index,offset,offset+length);ctx.index+=length}
 }
 fun finalizePad(ctx:Context,pad:Int){if(ctx.index==ctx.rate){chunk(ctx,ctx.data,ctx.offset+208,ctx.rate/8);ctx.index=0};ctx.data[ctx.offset+208+ctx.index]=pad.toByte();ctx.index++;ctx.data.fill(0,ctx.offset+208+ctx.index,ctx.offset+208+ctx.rate);val last=ctx.offset+208+ctx.rate-1;ctx.data[last]=(ctx.data[last].toInt()or 128).toByte();chunk(ctx,ctx.data,ctx.offset+208,ctx.rate/8);ctx.index=0}
 fun output(ctx:Context,out:CryptoByteSpan){var length=out.size;var offset=out.offset;val available=ctx.rate-ctx.index
  if(ctx.index==ctx.rate){chunk(ctx,null);ctx.index=0}
  fun bytes():ByteArray=ByteArray(200).also{a->for(i in 0..<25)store(a,8*i,ctx.lane(i))}
  if(ctx.index!=0&&length>=available){val data=bytes();data.copyInto(out.data,offset,ctx.index,ctx.index+available);chunk(ctx,null);length-=available;offset+=available;ctx.index=0}
  while(length>ctx.rate){bytes().copyInto(out.data,offset,0,ctx.rate);chunk(ctx,null);length-=ctx.rate;offset+=ctx.rate}
  if(length!=0){bytes().copyInto(out.data,offset,ctx.index,ctx.index+length);ctx.index+=length}
 }
 fun finalize(ctx:Context,bits:Int,out:CryptoByteSpan){finalizePad(ctx,6);output(ctx,CryptoByteSpan(out.data,out.offset,bits/8))}
 fun finalizeShake(ctx:Context)=finalizePad(ctx,31)
 fun finalizeCshake(ctx:Context)=finalizePad(ctx,4)
 fun keccakInit(ctx:Context,bits:Int)=init(ctx,bits)
 fun keccakUpdate(ctx:Context,input:CryptoByteSpan)=update(ctx,input)
 fun keccakFinalize(ctx:Context,bits:Int,out:CryptoByteSpan){finalizePad(ctx,1);output(ctx,CryptoByteSpan(out.data,out.offset,bits/8))}
 class Shake(val securityBits:Int,val outputBits:Int) {
  init{require(securityBits==128||securityBits==256);require(outputBits>=0)}
  val blockSize=200-2*(securityBits/8)
  val digestSize=outputBits/8+if(outputBits%8==0)0 else 1
  val contextSize=208+blockSize
  fun newContext()=Context(ByteArray(contextSize)).also{init(it,securityBits)}
  fun update(ctx:Context,input:CryptoByteSpan)=CryptoSha3.update(ctx,input)
  fun finalize(ctx:Context,out:CryptoByteSpan)=finish(ctx,out,31)
  fun cshakeFinalize(ctx:Context,out:CryptoByteSpan)=finish(ctx,out,4)
  private fun finish(ctx:Context,out:CryptoByteSpan,pad:Int){finalizePad(ctx,pad);output(ctx,CryptoByteSpan(out.data,out.offset,digestSize));if(outputBits%8!=0)out.data[out.offset+outputBits/8]=(out.data[out.offset+outputBits/8].toInt()and((1 shl(outputBits%8))-1)).toByte()}
  override fun toString()="SHAKE$securityBits"
 }
 fun digest(message:ByteArray,securityBits:Int,outputBits:Int,pad:Int=31):ByteArray {val ctx=Context(ByteArray(408-2*(securityBits/8)));init(ctx,securityBits);update(ctx,CryptoByteSpan(message));finalizePad(ctx,pad);val out=ByteArray(outputBits/8+if(outputBits%8==0)0 else 1);output(ctx,CryptoByteSpan(out));if(outputBits%8!=0)out[outputBits/8]=(out[outputBits/8].toInt()and((1 shl(outputBits%8))-1)).toByte();return out}
}
