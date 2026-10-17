// crypton_ripemd.c, crypton 2.0.1; Copyright (C) 2006-2009 Vincent Hanquez.
// licenses/crypton-ripemd160-BSD-2-Clause.txt.
package io.heapy.kotdoc.crypto
internal object CryptoRipemd160 {
 enum class Algorithm(val contextSize:Int=128,val cSize:Int=96,val digestSize:Int=20){RIPEMD160}
 class Context(val algorithm:Algorithm=Algorithm.RIPEMD160,val data:ByteArray=ByteArray(algorithm.contextSize),val offset:Int=0){
  fun copy()=Context(algorithm,data.copyOfRange(offset,offset+algorithm.contextSize))
  fun size()=load64(data,offset)
  fun size(v:Long)=store64(data,offset,v)
  fun state(i:Int)=load32(data,offset+72+4*i)
  fun state(i:Int,v:Int)=store32(data,offset+72+4*i,v)
 }
 private fun load32(a:ByteArray,p:Int):Int {var v=0;for(i in 0..<4)v=v or((a[p+i].toInt()and 255)shl(8*i));return v}
 private fun store32(a:ByteArray,p:Int,v:Int){for(i in 0..<4)a[p+i]=(v ushr(8*i)).toByte()}
 private fun load64(a:ByteArray,p:Int):Long {var v=0L;for(i in 0..<8)v=v or((a[p+i].toLong()and 255)shl(8*i));return v}
 private fun store64(a:ByteArray,p:Int,v:Long){for(i in 0..<8)a[p+i]=(v ushr(8*i)).toByte()}
 fun init(c:Context){c.data.fill(0,c.offset,c.offset+96);val iv=intArrayOf(0x67452301,0xefcdab89U.toInt(),0x98badcfeU.toInt(),0x10325476,0xc3d2e1f0U.toInt());for(i in iv.indices)c.state(i,iv[i])}
 var observe:((String,Int,IntArray)->Unit)?=null
 private val constants=intArrayOf(0x0U.toInt(),0x5a827999U.toInt(),0x6ed9eba1U.toInt(),0x8f1bbcdcU.toInt(),0xa953fd4eU.toInt(),0x50a28be6U.toInt(),0x5c4dd124U.toInt(),0x6d703ef3U.toInt(),0x7a6d76e9U.toInt())
 private val operations=arrayOf(intArrayOf(0,1,2,3,4,1,1,0,11),
intArrayOf(4,0,1,2,3,1,1,1,14),
intArrayOf(3,4,0,1,2,1,1,2,15),
intArrayOf(2,3,4,0,1,1,1,3,12),
intArrayOf(1,2,3,4,0,1,1,4,5),
intArrayOf(0,1,2,3,4,1,1,5,8),
intArrayOf(4,0,1,2,3,1,1,6,7),
intArrayOf(3,4,0,1,2,1,1,7,9),
intArrayOf(2,3,4,0,1,1,1,8,11),
intArrayOf(1,2,3,4,0,1,1,9,13),
intArrayOf(0,1,2,3,4,1,1,10,14),
intArrayOf(4,0,1,2,3,1,1,11,15),
intArrayOf(3,4,0,1,2,1,1,12,6),
intArrayOf(2,3,4,0,1,1,1,13,7),
intArrayOf(1,2,3,4,0,1,1,14,9),
intArrayOf(0,1,2,3,4,1,1,15,8),
intArrayOf(4,0,1,2,3,2,2,7,7),
intArrayOf(3,4,0,1,2,2,2,4,6),
intArrayOf(2,3,4,0,1,2,2,13,8),
intArrayOf(1,2,3,4,0,2,2,1,13),
intArrayOf(0,1,2,3,4,2,2,10,11),
intArrayOf(4,0,1,2,3,2,2,6,9),
intArrayOf(3,4,0,1,2,2,2,15,7),
intArrayOf(2,3,4,0,1,2,2,3,15),
intArrayOf(1,2,3,4,0,2,2,12,7),
intArrayOf(0,1,2,3,4,2,2,0,12),
intArrayOf(4,0,1,2,3,2,2,9,15),
intArrayOf(3,4,0,1,2,2,2,5,9),
intArrayOf(2,3,4,0,1,2,2,2,11),
intArrayOf(1,2,3,4,0,2,2,14,7),
intArrayOf(0,1,2,3,4,2,2,11,13),
intArrayOf(4,0,1,2,3,2,2,8,12),
intArrayOf(3,4,0,1,2,3,3,3,11),
intArrayOf(2,3,4,0,1,3,3,10,13),
intArrayOf(1,2,3,4,0,3,3,14,6),
intArrayOf(0,1,2,3,4,3,3,4,7),
intArrayOf(4,0,1,2,3,3,3,9,14),
intArrayOf(3,4,0,1,2,3,3,15,9),
intArrayOf(2,3,4,0,1,3,3,8,13),
intArrayOf(1,2,3,4,0,3,3,1,15),
intArrayOf(0,1,2,3,4,3,3,2,14),
intArrayOf(4,0,1,2,3,3,3,7,8),
intArrayOf(3,4,0,1,2,3,3,0,13),
intArrayOf(2,3,4,0,1,3,3,6,6),
intArrayOf(1,2,3,4,0,3,3,13,5),
intArrayOf(0,1,2,3,4,3,3,11,12),
intArrayOf(4,0,1,2,3,3,3,5,7),
intArrayOf(3,4,0,1,2,3,3,12,5),
intArrayOf(2,3,4,0,1,4,4,1,11),
intArrayOf(1,2,3,4,0,4,4,9,12),
intArrayOf(0,1,2,3,4,4,4,11,14),
intArrayOf(4,0,1,2,3,4,4,10,15),
intArrayOf(3,4,0,1,2,4,4,0,14),
intArrayOf(2,3,4,0,1,4,4,8,15),
intArrayOf(1,2,3,4,0,4,4,12,9),
intArrayOf(0,1,2,3,4,4,4,4,8),
intArrayOf(4,0,1,2,3,4,4,13,9),
intArrayOf(3,4,0,1,2,4,4,3,14),
intArrayOf(2,3,4,0,1,4,4,7,5),
intArrayOf(1,2,3,4,0,4,4,15,6),
intArrayOf(0,1,2,3,4,4,4,14,8),
intArrayOf(4,0,1,2,3,4,4,5,6),
intArrayOf(3,4,0,1,2,4,4,6,5),
intArrayOf(2,3,4,0,1,4,4,2,12),
intArrayOf(1,2,3,4,0,5,5,4,9),
intArrayOf(0,1,2,3,4,5,5,0,15),
intArrayOf(4,0,1,2,3,5,5,5,5),
intArrayOf(3,4,0,1,2,5,5,9,11),
intArrayOf(2,3,4,0,1,5,5,7,6),
intArrayOf(1,2,3,4,0,5,5,12,8),
intArrayOf(0,1,2,3,4,5,5,2,13),
intArrayOf(4,0,1,2,3,5,5,10,12),
intArrayOf(3,4,0,1,2,5,5,14,5),
intArrayOf(2,3,4,0,1,5,5,1,12),
intArrayOf(1,2,3,4,0,5,5,3,13),
intArrayOf(0,1,2,3,4,5,5,8,14),
intArrayOf(4,0,1,2,3,5,5,11,11),
intArrayOf(3,4,0,1,2,5,5,6,8),
intArrayOf(2,3,4,0,1,5,5,15,5),
intArrayOf(1,2,3,4,0,5,5,13,6),
intArrayOf(5,6,7,8,9,5,6,5,8),
intArrayOf(9,5,6,7,8,5,6,14,9),
intArrayOf(8,9,5,6,7,5,6,7,9),
intArrayOf(7,8,9,5,6,5,6,0,11),
intArrayOf(6,7,8,9,5,5,6,9,13),
intArrayOf(5,6,7,8,9,5,6,2,15),
intArrayOf(9,5,6,7,8,5,6,11,15),
intArrayOf(8,9,5,6,7,5,6,4,5),
intArrayOf(7,8,9,5,6,5,6,13,7),
intArrayOf(6,7,8,9,5,5,6,6,7),
intArrayOf(5,6,7,8,9,5,6,15,8),
intArrayOf(9,5,6,7,8,5,6,8,11),
intArrayOf(8,9,5,6,7,5,6,1,14),
intArrayOf(7,8,9,5,6,5,6,10,14),
intArrayOf(6,7,8,9,5,5,6,3,12),
intArrayOf(5,6,7,8,9,5,6,12,6),
intArrayOf(9,5,6,7,8,4,7,6,9),
intArrayOf(8,9,5,6,7,4,7,11,13),
intArrayOf(7,8,9,5,6,4,7,3,15),
intArrayOf(6,7,8,9,5,4,7,7,7),
intArrayOf(5,6,7,8,9,4,7,0,12),
intArrayOf(9,5,6,7,8,4,7,13,8),
intArrayOf(8,9,5,6,7,4,7,5,9),
intArrayOf(7,8,9,5,6,4,7,10,11),
intArrayOf(6,7,8,9,5,4,7,14,7),
intArrayOf(5,6,7,8,9,4,7,15,7),
intArrayOf(9,5,6,7,8,4,7,8,12),
intArrayOf(8,9,5,6,7,4,7,12,7),
intArrayOf(7,8,9,5,6,4,7,4,6),
intArrayOf(6,7,8,9,5,4,7,9,15),
intArrayOf(5,6,7,8,9,4,7,1,13),
intArrayOf(9,5,6,7,8,4,7,2,11),
intArrayOf(8,9,5,6,7,3,8,15,9),
intArrayOf(7,8,9,5,6,3,8,5,7),
intArrayOf(6,7,8,9,5,3,8,1,15),
intArrayOf(5,6,7,8,9,3,8,3,11),
intArrayOf(9,5,6,7,8,3,8,7,8),
intArrayOf(8,9,5,6,7,3,8,14,6),
intArrayOf(7,8,9,5,6,3,8,6,6),
intArrayOf(6,7,8,9,5,3,8,9,14),
intArrayOf(5,6,7,8,9,3,8,11,12),
intArrayOf(9,5,6,7,8,3,8,8,13),
intArrayOf(8,9,5,6,7,3,8,12,5),
intArrayOf(7,8,9,5,6,3,8,2,14),
intArrayOf(6,7,8,9,5,3,8,10,13),
intArrayOf(5,6,7,8,9,3,8,0,13),
intArrayOf(9,5,6,7,8,3,8,4,7),
intArrayOf(8,9,5,6,7,3,8,13,5),
intArrayOf(7,8,9,5,6,2,9,8,15),
intArrayOf(6,7,8,9,5,2,9,6,5),
intArrayOf(5,6,7,8,9,2,9,4,8),
intArrayOf(9,5,6,7,8,2,9,1,11),
intArrayOf(8,9,5,6,7,2,9,3,14),
intArrayOf(7,8,9,5,6,2,9,11,14),
intArrayOf(6,7,8,9,5,2,9,15,6),
intArrayOf(5,6,7,8,9,2,9,0,14),
intArrayOf(9,5,6,7,8,2,9,5,6),
intArrayOf(8,9,5,6,7,2,9,12,9),
intArrayOf(7,8,9,5,6,2,9,2,12),
intArrayOf(6,7,8,9,5,2,9,13,9),
intArrayOf(5,6,7,8,9,2,9,9,12),
intArrayOf(9,5,6,7,8,2,9,7,5),
intArrayOf(8,9,5,6,7,2,9,10,15),
intArrayOf(7,8,9,5,6,2,9,14,8),
intArrayOf(6,7,8,9,5,1,1,12,8),
intArrayOf(5,6,7,8,9,1,1,15,5),
intArrayOf(9,5,6,7,8,1,1,10,12),
intArrayOf(8,9,5,6,7,1,1,4,9),
intArrayOf(7,8,9,5,6,1,1,1,12),
intArrayOf(6,7,8,9,5,1,1,5,5),
intArrayOf(5,6,7,8,9,1,1,8,14),
intArrayOf(9,5,6,7,8,1,1,7,6),
intArrayOf(8,9,5,6,7,1,1,6,8),
intArrayOf(7,8,9,5,6,1,1,2,13),
intArrayOf(6,7,8,9,5,1,1,13,6),
intArrayOf(5,6,7,8,9,1,1,14,5),
intArrayOf(9,5,6,7,8,1,1,0,15),
intArrayOf(8,9,5,6,7,1,1,3,13),
intArrayOf(7,8,9,5,6,1,1,9,11),
intArrayOf(6,7,8,9,5,1,1,11,11))
 fun chunk(c:Context,input:ByteArray,offset:Int=0){
  val w=IntArray(16){load32(input,offset+4*it)};val v=IntArray(10){c.state(it%5)}
  for((i,r)in operations.withIndex()){
   val b=v[r[1]];val x=v[r[2]];val d=v[r[3]]
   val f=when(r[5]){1->b xor x xor d;2->d xor(b and(x xor d));3->(b or x.inv())xor d;4->x xor(d and(b xor x));else->b xor(x or d.inv())}
   v[r[0]]=Integer.rotateLeft(v[r[0]]+f+w[r[7]]+constants[r[6]-1],r[8])+v[r[4]]
   v[r[2]]=Integer.rotateLeft(v[r[2]],10)
   observe?.invoke("round",i,v.copyOf())
  }
  v[8]+=v[2]+c.state(1)
  c.state(1,c.state(2)+v[3]+v[9]);c.state(2,c.state(3)+v[4]+v[5]);c.state(3,c.state(4)+v[0]+v[6]);c.state(4,c.state(0)+v[1]+v[7]);c.state(0,v[8])
 }
 fun counterAfter(size:Long,length:UInt)=size+length.toLong()
 fun update(c:Context,input:CryptoByteSpan){
  var len=input.size;var p=input.offset;var index=(c.size()and 63).toInt();val fill=64-index;c.size(counterAfter(c.size(),len.toUInt()))
  if(index!=0&&len>=fill){input.data.copyInto(c.data,c.offset+8+index,p,p+fill);chunk(c,c.data,c.offset+8);len-=fill;p+=fill;index=0}
  while(len>=64){chunk(c,input.data,p);len-=64;p+=64}
  if(len!=0)input.data.copyInto(c.data,c.offset+8+index,p,p+len)
 }
 fun finalize(c:Context,out:CryptoByteSpan){
  val bits=ByteArray(8);store64(bits,0,c.size()shl 3);val index=(c.size()and 63).toInt();val n=if(index<56)56-index else 120-index;val padding=ByteArray(n);padding[0]=128.toByte();update(c,CryptoByteSpan(padding));update(c,CryptoByteSpan(bits))
  for(i in 0..<5)store32(out.data,out.offset+4*i,c.state(i))
 }
}
