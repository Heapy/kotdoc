/**
 * Translated from pinned Decaf ed448goldilocks/scalar.c by Mike Hamburg.
 * Copyright (c) 2015-2016 Cryptography Research, Inc.
 * Released under the MIT License; see licenses/decaf-MIT.txt.
 * The referenced upstream LICENSE.txt is absent from the pinned archive.
 */
package io.heapy.kotdoc.crypto
internal class CryptoDecaf448Scalar(val bits: Int) {
 init{require(bits==32 || bits==64)}
 val limbs=448/bits
 private val dm=if(bits==32)0xffffffffL else -1L
 typealias Wide=CryptoP256Field.Wide
 class Region {
  private val words: LongArray?;private val bytes: ByteArray?;private val offset: Int;private val bits: Int
  constructor(words: LongArray,offset: Int=0){this.words=words;bytes=null;this.offset=offset;bits=64}
  constructor(bytes: ByteArray,offset: Int,bits: Int){words=null;this.bytes=bytes;this.offset=offset;this.bits=bits}
  operator fun get(i: Int): Long {words?.let{return it[offset+i]};var v=0L;for(j in 0..<bits/8)v=v or((bytes!![offset+i*(bits/8)+j].toLong()and 255)shl(8*j));return v}
  operator fun set(i: Int,v: Long){words?.let{it[offset+i]=v;return};for(j in 0..<bits/8)bytes!![offset+i*(bits/8)+j]=(v ushr(8*j)).toByte()}
 }
 class Audit(val mode: Int,val emit: (String,Int,LongArray,LongArray)->Unit)
 private fun snapshot(audit: Audit?,mode: Int,label: String,index: Int,a: Region,length: Int=limbs,e0: Long=0,e1: Long=0,e2: Long=0){if(audit?.mode==mode)audit.emit(label,index,LongArray(length){a[it]},longArrayOf(e0,e1,e2))}
 private fun put(r: Region,i: Int,v: Long){r[i]=v and dm}
 private fun fresh()=Region(LongArray(limbs))
 private operator fun Wide.minus(b: Wide): Wide {val low=this.low-b.low;return Wide(low,high-b.high-if(java.lang.Long.compareUnsigned(this.low,b.low)<0)1 else 0)}
 private fun from64(v: LongArray)=if(bits==64)v.copyOf()else v.flatMap{listOf(it and dm,it ushr 32)}.toLongArray()
 fun constant(name: String): LongArray=when(name){
  "factor"->longArrayOf(0x3bd440fae918bc5L and dm)
  "p"->from64(longArrayOf(0x2378c292ab5844f3L,0x216cc2728dc58f55L,0xc44edb49aed63690UL.toLong(),0xffffffff7cca23e9UL.toLong(),-1L,-1L,0x3fffffffffffffffL))
  "r2"->from64(longArrayOf(0xe3539257049b9b60UL.toLong(),0x7af32c4bc1b195d9L,0x0d66de2388ea1859L,0xae17cf725ee4d838UL.toLong(),0x1a9cc14ba3c47c44L,0x2052bcb7e4d070afL,0x3402a939f823b729L))
  "one"->LongArray(limbs){if(it==0)1 else 0}
  "zero"->LongArray(limbs)
  else->error(name)
 }
 private val modulus=Region(constant("p"));private val r2=Region(constant("r2"));private val one=Region(constant("one"));private val zero=Region(constant("zero"));private val factor=constant("factor")[0]
 fun copy(out: Region,a: Region){for(i in 0..<limbs)put(out,i,a[i])}
 fun subx(out: Region,a: Region,sub: Region,p: Region,extra: Long,audit: Audit?=null){
  var chain=Wide.of(0)
  for(i in 0..<limbs){chain=(chain+Wide.of(a[i]))-Wide.of(sub[i]);put(out,i,chain.low);chain=if(bits==32){val next=chain.low shr 32;Wide(next,if(next<0)-1 else 0)}else Wide(chain.high,if(chain.high<0)-1 else 0)}
  val borrow=(chain.low+extra)and dm;snapshot(audit,1,"subx:diff",-1,out,e0=borrow)
  chain=Wide.of(0);for(i in 0..<limbs){chain+=Wide.of(out[i])+Wide.of(p[i]and borrow);put(out,i,chain.low);chain=chain ushr bits};snapshot(audit,1,"subx:sum",-1,out)
 }
 fun montmul(out: Region,a: Region,b: Region,audit: Audit?=null){val accum=Region(LongArray(limbs+1));var hiCarry=0L
  for(i in 0..<limbs){var mand=a[i];var chain=Wide.of(0);for(j in 0..<limbs){chain+=Wide.product(mand,b[j])+Wide.of(accum[j]);put(accum,j,chain.low);chain=chain ushr bits};put(accum,limbs,chain.low)
   mand=(accum[0]*factor)and dm;chain=Wide.of(0);for(j in 0..<limbs){chain+=Wide.product(mand,modulus[j])+Wide.of(accum[j]);if(j!=0)put(accum,j-1,chain.low);chain=chain ushr bits};chain+=Wide.of(accum[limbs])+Wide.of(hiCarry);put(accum,limbs-1,chain.low);hiCarry=(chain ushr bits).low;snapshot(audit,1,"mont",i,accum,limbs+1,hiCarry)
  };subx(out,accum,modulus,modulus,hiCarry,audit)
 }
 fun multiply(out: Region,a: Region,b: Region,audit: Audit?=null){montmul(out,a,b,audit);montmul(out,out,r2,audit)}
 fun montsqr(out: Region,a: Region,audit: Audit?=null)=montmul(out,a,a,audit)
 fun invert(out: Region,a: Region,audit: Audit?=null): Int {val pre=Array(8){fresh()};montmul(pre[0],a,r2);montmul(pre[7],pre[0],pre[0]);for(i in 1..7)montmul(pre[i],pre[i-1],pre[7]);for(i in 0..7)snapshot(audit,2,"inverse:table",i,pre[i])
  var residue=0;var trailing=0;var started=0
  for(i in 445 downTo -3){if(started!=0)montsqr(out,out);var w=if(i>=0)modulus[i/bits]else 0L;if(i>=0 && i<bits){check(java.lang.Long.compareUnsigned(w,2)>=0);w-=2}
   // The original negative-count C shift is UB. These zero tail bits are its explicit source-intended extension.
   residue=(residue shl 1)or(if(i>=0)((w ushr(i%bits))and 1).toInt()else 0)
   if(residue ushr 3!=0){check(trailing==0);trailing=residue;residue=0}
   if(trailing>0 && trailing and 7==0){if(started!=0)montmul(out,out,pre[trailing ushr 4])else{copy(out,pre[trailing ushr 4]);started=1};trailing=0};trailing=trailing shl 1;snapshot(audit,2,if(i>=0)"inverse:prefix"else"inverse:zero-tail",i,out,e0=residue.toLong(),e1=trailing.toLong(),e2=started.toLong())
  };check(residue==0 && trailing==0);montmul(out,out,one);for(p in pre)destroy(p);snapshot(audit,2,"inverse:final",-1,out);return if(equal(out,zero)==0L)-1 else 0
 }
 fun sub(out: Region,a: Region,b: Region,audit: Audit?=null)=subx(out,a,b,modulus,0,audit)
 fun add(out: Region,a: Region,b: Region,audit: Audit?=null){var chain=Wide.of(0);for(i in 0..<limbs){chain+=Wide.of(a[i])+Wide.of(b[i]);put(out,i,chain.low);chain=chain ushr bits};subx(out,out,modulus,modulus,chain.low,audit)}
 fun setUnsigned(out: Region,w: Long){destroy(out);put(out,0,w);if(bits==32)put(out,1,w ushr 32)}
 fun equal(a: Region,b: Region): Long {var diff=0L;for(i in 0..<limbs)diff=diff or(a[i]xor b[i]);return if(diff==0L)dm else 0}
 fun decodeShort(out: Region,input: CryptoByteSpan){var k=0;for(i in 0..<limbs){var v=0L;var j=0;while(j<bits/8 && k<input.size){v=v or((input.data[input.offset+k].toLong()and 255)shl(8*j));j++;k++};put(out,i,v)}}
 fun decode(out: Region,input: CryptoByteSpan,audit: Audit?=null): Int {decodeShort(out,CryptoByteSpan(input.data,input.offset,56));var carry=Wide.of(0);for(i in 0..<limbs){carry=(carry+Wide.of(out[i]))-Wide.of(modulus[i]);carry=if(bits==32){val next=carry.low shr 32;Wide(next,if(next<0)-1 else 0)}else Wide(carry.high,if(carry.high<0)-1 else 0)};val valid=carry.low!=0L;multiply(out,out,one,audit);return if(valid)-1 else 0}
 fun bzero(bytes: CryptoByteSpan){bytes.data.fill(0,bytes.offset,bytes.offset+bytes.size)}
 fun destroy(out: Region){for(i in 0..<limbs)out[i]=0}
 fun decodeLong(out: Region,input: CryptoByteSpan,audit: Audit?=null){if(input.size==0){copy(out,zero);return};var i=input.size-input.size%56;if(i==input.size)i-=56;val t1=fresh();val t2=fresh();decodeShort(t1,CryptoByteSpan(input.data,input.offset+i,input.size-i));snapshot(audit,3,"long:initial",i,t1)
  if(input.size==56){check(i==0);multiply(out,t1,one);destroy(t1);snapshot(audit,3,"long:final",-1,out);return}
  while(i!=0){i-=56;montmul(t1,t1,r2);decode(t2,CryptoByteSpan(input.data,input.offset+i,56));add(t1,t1,t2);snapshot(audit,3,"long:chunk",i,t1)};copy(out,t1);destroy(t1);destroy(t2);snapshot(audit,3,"long:final",-1,out)
 }
 fun encode(out: CryptoByteSpan,s: Region){var k=0;for(i in 0..<limbs)for(j in 0..<bits/8)out.data[out.offset+k++]=(s[i]ushr(8*j)).toByte()}
 fun condSelect(out: Region,a: Region,b: Region,pickB: Long){val take=if((pickB and dm)!=0L)dm else 0L;for(i in 0..<limbs)put(out,i,(a[i]and take.inv())or(b[i]and take))}
 fun halve(out: Region,a: Region){val mask=-(a[0]and 1)and dm;var chain=Wide.of(0);for(i in 0..<limbs){chain+=Wide.of(a[i])+Wide.of(modulus[i]and mask);put(out,i,chain.low);chain=chain ushr bits};for(i in 0..<limbs-1)put(out,i,(out[i]ushr 1)or(out[i+1]shl(bits-1)));put(out,limbs-1,(out[limbs-1]ushr 1)or(chain.low shl(bits-1)))}
}
