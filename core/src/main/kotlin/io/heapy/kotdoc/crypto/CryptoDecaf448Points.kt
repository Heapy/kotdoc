/**
 * Translated from pinned Decaf ed448goldilocks/decaf.c by Mike Hamburg.
 * Copyright (c) 2015-2016 Cryptography Research, Inc.
 * Released under MIT; see licenses/decaf-MIT.txt (upstream LICENSE.txt absent).
 * Geometry/codecs/Niels checkpoint; scalar loop/precomputation families remain separate.
 */
package io.heapy.kotdoc.crypto
internal class CryptoDecaf448Points(val bits: Int,val scalarBits: Int=bits,val vectorMasks32: Boolean=false) {
 init{require(scalarBits==32 || scalarBits==64);require(!vectorMasks32 || bits==64)}
 var observe: ((String,LongArray)->Unit)?=null
 private val engine=CryptoDecaf448Field(bits)
 val f=FieldOps()
 inner class FieldOps {
  val limbs get()=engine.limbs
  fun constant(name: String)=engine.constant(name)
  private fun emit(label: String,r: Region){observe?.invoke(label,LongArray(limbs){r[it]})}
  fun copy(r: Region,a: Region) {engine.copy(r,a);emit("copy",r)}
  fun add(r: Region,a: Region,b: Region) {engine.add(r,a,b);emit("add",r)}
  fun sub(r: Region,a: Region,b: Region) {engine.sub(r,a,b);emit("sub",r)}
  fun addNr(r: Region,a: Region,b: Region) {engine.addNr(r,a,b);emit("add_nr",r)}
  fun subNr(r: Region,a: Region,b: Region) {engine.subNr(r,a,b);emit("sub_nr",r)}
  fun multiply(r: Region,a: Region,b: Region) {engine.multiply(r,a,b);emit("mul",r)}
  fun square(r: Region,a: Region) {engine.square(r,a);emit("sqr",r)}
  fun subxNr(r: Region,a: Region,b: Region,n: Int){engine.subxNr(r,a,b,n);emit("subx_nr",r)}
  fun weakReduce(r: Region){engine.weakReduce(r);emit("weak_reduce",r)}
  fun mulw(r: Region,a: Region,w: Int){engine.mulw(r,a,w);emit("mulw",r)}
  fun isr(r: Region,a: Region): Long {val result=engine.isr(r,a);emit("isr",r);return result}
  fun equal(a: Region,b: Region)=engine.equal(a,b)
  fun lobit(a: Region)=engine.lobit(a)
  private fun mask(m: Long)=if(vectorMasks32 && bits==64)(m and 0xffffffffL)or(m shl 32)else m
  fun condNeg(a: Region,m: Long){engine.condNeg(a,mask(m));emit("cond_neg",a)}
  fun condSwap(a: Region,b: Region,m: Long){engine.condSwap(a,b,mask(m));emit("cond_swap0",a);emit("cond_swap1",b)}
  fun select(r: Region,a: Region,b: Region,m: Long){engine.select(r,a,b,mask(m));emit("cond_sel",r)}
  fun serialize(out: CryptoByteSpan,a: Region)=engine.serialize(out,a)
  fun deserialize(r: Region,a: CryptoByteSpan,m: Int): Long {val result=engine.deserialize(r,a,m);emit("deserialize",r);return result}
 }
 val limbs=f.limbs
 typealias Region=CryptoP256Words.Region
 private val dm=if(bits==32)0xffffffffL else -1L
 private val apiMask=if(scalarBits==32)0xffffffffL else -1L
 inner class Shape(val data: LongArray,val offset: Int=0) {
  val x get()=Region(data,offset);val y get()=Region(data,offset+limbs);val z get()=Region(data,offset+2*limbs);val t get()=Region(data,offset+3*limbs)
  val a get()=x;val b get()=y;val c get()=z
  // A projective Niels element stores (a,b,c,z), with z in its fourth field.
  val nz get()=t
 }
 fun freshShape()=Shape(LongArray(4*limbs))
 private fun fresh()=Region(LongArray(limbs))
 private fun clear(r: Region)=r.zero(limbs)
 private val zero=Region(f.constant("ZERO"));private val one=Region(f.constant("ONE"))
 val ristretto=Region(literal(longArrayOf(0x42ef0f45572736,0x7bf6aa20ce5296,0xf4fd6eded26033,0x968c14ba839a66,0xb8d54b64a2d780,0x6aa0a1f1a7b8a5,0x683bf68d722fa2,0x22d962fbeb24f7)))
 private fun literal(v: LongArray)=if(bits==64)v.copyOf()else v.flatMap{listOf(it and 0xfffffff,it ushr 28)}.toLongArray()
 fun constant(name: String): LongArray=when(name){
  "ristretto"->LongArray(limbs){ristretto[it]}
  "identity"->identity().data
  "xBase"->LongArray(56){if(it==0)5 else 0}
  "scalarAdjustment","precomputedAdjustment"->{val words=longArrayOf(0xc873d6d54a7bb0cfUL.toLong(),0xe933d8d723a70aadUL.toLong(),0xbb124b65129c96fdUL.toLong(),0x00000008335dc163,0,0,0);if(scalarBits==64)words else words.flatMap{listOf(it and 0xffffffff,it ushr 32)}.toLongArray()}
  else->error(name)
 }
 fun tableField(index: Int)=CryptoDecaf448PointsTables.field(index,bits)
 fun base(): Shape=Shape((0..<4).flatMap{tableField(it).asIterable()}.toLongArray())
 fun identity(): Shape {val p=freshShape();f.copy(p.y,one);f.copy(p.z,one);return p}
 fun copy(out: Shape,a: Shape){a.data.copyInto(out.data,out.offset,a.offset,a.offset+4*limbs)}
 fun destroy(out: Shape){out.data.fill(0,out.offset,out.offset+4*limbs)}
 fun invert(y: Region,x: Region,assertNonzero: Int=0){val t1=fresh();val t2=fresh();f.square(t1,x);val ret=f.isr(t2,t1);if(assertNonzero!=0)check(ret!=0L);f.square(t1,t2);f.multiply(t2,t1,x);f.copy(y,t2)}
 fun deisogenize(s: Region,invElSum: Region,invElM1: Region,p: Shape,toggleS: Long,toggleAltx: Long,toggleRotation: Long){
  val t1=fresh();val t2=s;val t3=invElSum;val t4=invElM1
  f.add(t1,p.x,p.t);f.sub(t2,p.x,p.t);f.multiply(t3,t1,t2);f.square(t2,p.x);f.multiply(t1,t2,t3);f.mulw(t2,t1,39081);f.isr(t1,t2);f.multiply(t2,t1,t3);f.multiply(t4,t2,ristretto)
  val negx=f.lobit(t4) xor toggleAltx
  f.condNeg(t2,negx);f.multiply(t3,t2,p.z);f.sub(t3,t3,p.t);f.multiply(t2,t3,p.x);f.mulw(t4,t2,39081);f.multiply(s,t4,t1)
  val lobs=f.lobit(s);f.condNeg(s,lobs);f.copy(invElM1,p.x);f.condNeg(invElM1,lobs.inv() xor negx xor toggleS);f.add(invElM1,invElM1,p.t)
  // toggleRotation is ignored by the pinned cofactor4/no-twist branch.
 }
 fun encode(out: CryptoByteSpan,p: Shape){val s=fresh();deisogenize(s,fresh(),fresh(),p,0,0,0);f.serialize(out,s)}
 fun decode(p: Shape,ser: CryptoByteSpan,allowIdentity: Long): Int {
  val s=fresh();val s2=fresh();val num=fresh();val tmp=fresh();val tmp2=s2;val ynum=p.z;val isr=p.x;val den=p.t
  var succ=f.deserialize(s,ser,0);succ=succ and((if((allowIdentity and apiMask)!=0L)dm else 0L)or f.equal(s,zero).inv());succ=succ and f.lobit(s).inv()
  f.square(s2,s);f.sub(den,one,s2);f.add(ynum,one,s2);f.mulw(num,s2,156328);f.square(tmp,den);f.add(num,tmp,num);f.multiply(tmp2,num,tmp);succ=succ and f.isr(isr,tmp2)
  f.multiply(tmp,isr,den);f.multiply(p.y,tmp,ynum);f.multiply(tmp2,tmp,s);f.add(tmp2,tmp2,tmp2);f.multiply(tmp,tmp2,isr);f.multiply(p.x,tmp,num);f.multiply(tmp,tmp2,ristretto);f.condNeg(p.x,f.lobit(tmp));f.copy(p.z,one);f.multiply(p.t,p.x,p.y)
  check(valid(p)!=0L || succ==0L);return if(succ!=0L)-1 else 0
 }
 fun sub(p: Shape,q: Shape,r: Shape){val a=fresh();val b=fresh();val c=fresh();val d=fresh()
  f.subNr(b,q.y,q.x);f.subNr(d,r.y,r.x);f.addNr(c,r.y,r.x);f.multiply(a,c,b);f.addNr(b,q.y,q.x);f.multiply(p.y,d,b);f.multiply(b,r.t,q.t);f.mulw(p.x,b,78164);f.addNr(b,a,p.y);f.subNr(c,p.y,a);f.multiply(a,q.z,r.z);f.addNr(a,a,a);if(bits==32)f.weakReduce(a);f.subNr(p.y,a,p.x);f.addNr(a,a,p.x);f.multiply(p.z,a,p.y);f.multiply(p.x,p.y,c);f.multiply(p.y,a,b);f.multiply(p.t,b,c)
 }
 fun add(p: Shape,q: Shape,r: Shape){val a=fresh();val b=fresh();val c=fresh();val d=fresh()
  f.subNr(b,q.y,q.x);f.subNr(c,r.y,r.x);f.addNr(d,r.y,r.x);f.multiply(a,c,b);f.addNr(b,q.y,q.x);f.multiply(p.y,d,b);f.multiply(b,r.t,q.t);f.mulw(p.x,b,78164);f.addNr(b,a,p.y);f.subNr(c,p.y,a);f.multiply(a,q.z,r.z);f.addNr(a,a,a);if(bits==32)f.weakReduce(a);f.addNr(p.y,a,p.x);f.subNr(a,a,p.x);f.multiply(p.z,a,p.y);f.multiply(p.x,p.y,c);f.multiply(p.y,a,b);f.multiply(p.t,b,c)
 }
 fun doubleInternal(p: Shape,q: Shape,beforeDouble: Int){val a=fresh();val b=fresh();val c=fresh();val d=fresh()
  f.square(c,q.x);f.square(a,q.y);f.addNr(d,c,a);f.addNr(p.t,q.y,q.x);f.square(b,p.t);f.subxNr(b,b,d,3);f.subNr(p.t,a,c);f.square(p.x,q.z);f.addNr(p.z,p.x,p.x);f.subxNr(a,p.z,p.t,4);f.multiply(p.x,a,b);f.multiply(p.z,p.t,a);f.multiply(p.y,p.t,d);if(beforeDouble==0)f.multiply(p.t,b,d)
 }
 fun double(p: Shape,q: Shape)=doubleInternal(p,q,0)
 fun negate(out: Shape,a: Shape){f.sub(out.x,zero,a.x);f.copy(out.y,a.y);f.copy(out.z,a.z);f.sub(out.t,zero,a.t)}
 fun condNegNiels(n: Shape,neg: Long){f.condSwap(n.a,n.b,neg);f.condNeg(n.c,neg)}
 fun toPniels(b: Shape,a: Shape){f.sub(b.a,a.y,a.x);f.add(b.b,a.x,a.y);f.mulw(b.c,a.t,-78164);f.add(b.nz,a.z,a.z)}
 fun fromPniels(e: Shape,d: Shape){val eu=fresh();f.add(eu,d.b,d.a);f.sub(e.y,d.b,d.a);f.multiply(e.t,e.y,eu);f.multiply(e.x,d.nz,e.y);f.multiply(e.y,d.nz,eu);f.square(e.z,d.nz)}
 fun fromNiels(e: Shape,n: Shape){f.add(e.y,n.b,n.a);f.sub(e.x,n.b,n.a);f.multiply(e.t,e.y,e.x);f.copy(e.z,one)}
 fun addNiels(d: Shape,e: Shape,beforeDouble: Int){val a=fresh();val b=fresh();val c=fresh()
  f.subNr(b,d.y,d.x);f.multiply(a,e.a,b);f.addNr(b,d.x,d.y);f.multiply(d.y,e.b,b);f.multiply(d.x,e.c,d.t);f.addNr(c,a,d.y);f.subNr(b,d.y,a);f.subNr(d.y,d.z,d.x);f.addNr(a,d.x,d.z);f.multiply(d.z,a,d.y);f.multiply(d.x,d.y,b);f.multiply(d.y,a,c);if(beforeDouble==0)f.multiply(d.t,b,c)
 }
 fun subNiels(d: Shape,e: Shape,beforeDouble: Int){val a=fresh();val b=fresh();val c=fresh()
  f.subNr(b,d.y,d.x);f.multiply(a,e.b,b);f.addNr(b,d.x,d.y);f.multiply(d.y,e.a,b);f.multiply(d.x,e.c,d.t);f.addNr(c,a,d.y);f.subNr(b,d.y,a);f.addNr(d.y,d.z,d.x);f.subNr(a,d.z,d.x);f.multiply(d.z,a,d.y);f.multiply(d.x,d.y,b);f.multiply(d.y,a,c);if(beforeDouble==0)f.multiply(d.t,b,c)
 }
 fun addPniels(p: Shape,pn: Shape,beforeDouble: Int){val l0=fresh();f.multiply(l0,p.z,pn.nz);f.copy(p.z,l0);addNiels(p,pn,beforeDouble)}
 fun subPniels(p: Shape,pn: Shape,beforeDouble: Int){val l0=fresh();f.multiply(l0,p.z,pn.nz);f.copy(p.z,l0);subNiels(p,pn,beforeDouble)}
 fun prepareFixedWindow(multiples: Array<Shape>,b: Shape,ntable: Int){val tmp=freshShape();val pn=freshShape();doubleInternal(tmp,b,0);toPniels(pn,tmp);toPniels(multiples[0],b);copy(tmp,b);for(i in 1..<ntable){addPniels(tmp,pn,0);toPniels(multiples[i],tmp)};destroy(pn);destroy(tmp)}
 fun equal(p: Shape,q: Shape): Long {val a=fresh();val b=fresh();f.multiply(a,p.y,q.x);f.multiply(b,q.y,p.x);return if(f.equal(a,b)!=0L)apiMask else 0}
 fun valid(p: Shape): Long {val a=fresh();val b=fresh();val c=fresh();f.multiply(a,p.x,p.y);f.multiply(b,p.z,p.t);var out=f.equal(a,b);f.square(a,p.x);f.square(b,p.y);f.sub(a,b,a);f.square(b,p.t);f.mulw(c,b,-39082);f.square(b,p.z);f.add(b,b,c);out=out and f.equal(a,b);out=out and f.equal(p.z,zero).inv();return if(out!=0L)apiMask else 0}
 fun torque(q: Shape,p: Shape){f.sub(q.x,zero,p.x);f.sub(q.y,zero,p.y);f.copy(q.z,p.z);f.copy(q.t,p.t)}
 fun pscale(q: Shape,p: Shape,factor: CryptoByteSpan){val gfac=fresh();val tmp=fresh();f.deserialize(gfac,factor,0);f.select(gfac,gfac,one,f.equal(gfac,zero));for(i in 0..<4){f.multiply(tmp,Region(p.data,p.offset+i*limbs),gfac);f.copy(Region(q.data,q.offset+i*limbs),tmp)}}
 fun select(out: Shape,a: Shape,b: Shape,pickB: Long){val mask=if((pickB and apiMask)!=0L)dm else 0L;for(i in 0..<4*limbs)out.data[out.offset+i]=((a.data[a.offset+i]and mask.inv())or(b.data[b.offset+i]and mask))and dm}
 fun encodeEd(out: CryptoByteSpan,p: Shape){val x=fresh();val y=fresh();val z=fresh();val t=fresh();val u=fresh();val q=freshShape();copy(q,p)
  f.square(x,q.x);f.square(t,q.y);f.add(u,x,t);f.add(z,q.y,q.x);f.square(y,z);f.sub(y,y,u);f.sub(z,t,x);f.square(x,q.z);f.add(t,x,x);f.sub(t,t,z);f.multiply(x,t,y);f.multiply(y,z,u);f.multiply(z,u,t);clear(u)
  invert(z,z,1);f.multiply(t,x,z);f.multiply(x,y,z);out.data[out.offset+56]=0;f.serialize(out,x);out.data[out.offset+56]=(0x80L and f.lobit(t)).toByte();clear(x);clear(y);clear(z);clear(t);destroy(q)
 }
 fun decodeEd(p: Shape,enc: CryptoByteSpan): Int {val enc2=enc.data.copyOfRange(enc.offset,enc.offset+57);val low=if(enc2[56].toInt()and 128!=0)dm else 0L;enc2[56]=(enc2[56].toInt()and 127).toByte();var succ=f.deserialize(p.y,CryptoByteSpan(enc2,0,56),0);succ=succ and(if(enc2[56]==0.toByte())dm else 0L)
  f.square(p.x,p.y);f.sub(p.z,one,p.x);f.mulw(p.t,p.x,-39081);f.sub(p.t,one,p.t);f.multiply(p.x,p.z,p.t);succ=succ and f.isr(p.t,p.x);f.multiply(p.x,p.t,p.z);f.condNeg(p.x,f.lobit(p.x)xor low);f.copy(p.z,one)
  val a=fresh();val b=fresh();val c=fresh();val d=fresh();f.square(c,p.x);f.square(a,p.y);f.add(d,c,a);f.add(p.t,p.y,p.x);f.square(b,p.t);f.sub(b,b,d);f.sub(p.t,a,c);f.square(p.x,p.z);f.add(p.z,p.x,p.x);f.sub(a,p.z,d);f.multiply(p.x,a,b);f.multiply(p.z,p.t,a);f.multiply(p.y,p.t,d);f.multiply(p.t,b,d);clear(a);clear(b);clear(c);clear(d);enc2.fill(0);check(valid(p)!=0L || succ==0L);return if(succ!=0L)-1 else 0
 }
 fun encodeX(out: CryptoByteSpan,p: Shape){val q=freshShape();copy(q,p);invert(q.t,q.x);f.multiply(q.z,q.t,q.y);f.square(q.y,q.z);f.serialize(out,q.y);destroy(q)}
 fun edPublicToX(out: CryptoByteSpan,ed: CryptoByteSpan){val y=fresh();val n=fresh();val d=fresh();f.deserialize(y,ed,0);f.square(n,y);f.sub(d,one,n);invert(d,d);f.multiply(y,n,d);f.mulw(d,n,-39081);f.sub(d,one,d);f.multiply(n,y,d);f.serialize(out,n);clear(y);clear(n);clear(d)}
}
