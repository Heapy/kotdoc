/**
 * Translated from pinned Decaf ed448goldilocks/decaf.c by Mike Hamburg.
 * Copyright (c) 2015-2016 Cryptography Research, Inc.; MIT, licenses/decaf-MIT.txt.
 * Completes the17 scalar/precomputation/X448 definitions following the geometry checkpoint.
 */
package io.heapy.kotdoc.crypto
internal class CryptoDecaf448PointMultiplication(val p: CryptoDecaf448Points) {
 typealias Shape=CryptoDecaf448Points.Shape
 typealias Region=CryptoP256Words.Region
 typealias Scalar=CryptoDecaf448Scalar.Region
 val bits=p.bits;val scalarBits=p.scalarBits;val limbs=p.limbs
 val scalar=CryptoDecaf448Scalar(scalarBits)
 private val f=p.f
 private val field=CryptoDecaf448Field(bits)
 private val dm=if(bits==32)0xffffffffL else -1L
 private val scalarMask=if(scalarBits==32)0xffffffffL else -1L
 private val one=Region(field.constant("ONE"));private val zero=Region(field.constant("ZERO"))
 var observe: ((String,Int,LongArray,LongArray)->Unit)?=null
 private fun snapshot(label: String,index: Int,a: Shape,vararg extra: Long){observe?.invoke(label,index,a.data.copyOfRange(a.offset,a.offset+4*limbs),extra.copyOf(3))}
 private fun fresh()=Region(LongArray(limbs))
 private fun identity(): Shape {val a=p.freshShape();a.data[a.offset+limbs]=1;a.data[a.offset+2*limbs]=1;return a}
 private fun clear(a: Region)=a.zero(limbs)
 private fun freshScalar()=Scalar(LongArray(scalar.limbs))
 private fun adjusted(s: Scalar,which: String="scalarAdjustment"): Scalar {val out=freshScalar();scalar.add(out,s,Scalar(p.constant(which)));scalar.halve(out,out);observe?.invoke("scalar:halve",0,LongArray(scalar.limbs){out[it]},LongArray(3));return out}
 private fun window(s: Scalar,i: Int): Int {var v=s[i/scalarBits] ushr(i%scalarBits);if(i%scalarBits>=scalarBits-5 && i/scalarBits<scalar.limbs-1)v=v xor((s[i/scalarBits+1]shl(scalarBits-i%scalarBits))and scalarMask);return(v and 31).toInt()}
 private fun copyFields(out: Shape,a: Shape,count: Int){a.data.copyInto(out.data,out.offset,a.offset,a.offset+count*limbs)}
 private fun indexMask(a: Int,b: Int): Long {val v=(a.toLong()xor b.toLong())and dm;return((((v or(-v and dm))and dm)ushr(bits-1))-1)and dm}
 private fun lookup(out: Shape,table: Array<Shape>,nelts: Int,index: Int,fields: Int){out.data.fill(0,out.offset,out.offset+fields*limbs);for(j in 0..<nelts){val m=indexMask(j,index);for(k in 0..<fields*limbs)out.data[out.offset+k]=out.data[out.offset+k]or(table[j].data[table[j].offset+k]and m)}}
 private fun insert(table: Array<Shape>,value: Shape,index: Int){for(j in table.indices){val m=indexMask(j,index);for(k in 0..<4*limbs)table[j].data[table[j].offset+k]=(table[j].data[table[j].offset+k]and m.inv())or(value.data[value.offset+k]and m)}}
 class Table(val data: LongArray,val offset: Int=0)
 fun freshTable(count: Int=80)=Table(LongArray(count*3*limbs))
 private fun views(table: Table,count: Int=80)=Array(count){p.Shape(table.data,table.offset+it*3*limbs)}
 fun baseTable(): Table=Table((4..<244).flatMap{p.tableField(it).asIterable()}.toLongArray())
 fun wnafBaseTable(): Table=Table((244..<340).flatMap{p.tableField(it).asIterable()}.toLongArray())
 fun lookupNiels(out: Shape,table: Table,nelts: Int,index: Int){lookup(out,views(table,nelts),nelts,index,3)}
 fun scalarmul(out: Shape,base: Shape,s: Scalar){val sx=adjusted(s);val pn=p.freshShape();val multiples=Array(16){p.freshShape()};val tmp=p.freshShape();p.prepareFixedWindow(multiples,base,16);var first=true
  for(i in 445 downTo 0 step 5){var value=window(sx,i);val inv=((value ushr 4)-1).toLong()and dm;value=value xor inv.toInt();lookup(pn,multiples,16,value and 15,4);p.condNegNiels(pn,inv)
   if(first){p.fromPniels(tmp,pn);first=false}else{repeat(4){p.doubleInternal(tmp,tmp,-1)};p.doubleInternal(tmp,tmp,0);p.addPniels(tmp,pn,if(i!=0)-1 else 0)}
   snapshot("scalarmul:window",i,tmp,value.toLong()and dm,inv,0)
  };p.copy(out,tmp);scalar.destroy(sx);p.destroy(pn);multiples.forEach(p::destroy);p.destroy(tmp)
 }
 fun doubleScalarmul(out: Shape,b: Shape,sb: Scalar,c: Shape,sc: Scalar){val sx=adjusted(sb);val sy=adjusted(sc);val pn=p.freshShape();val m1=Array(16){p.freshShape()};val m2=Array(16){p.freshShape()};val tmp=p.freshShape();p.prepareFixedWindow(m1,b,16);p.prepareFixedWindow(m2,c,16);var first=true
  for(i in 445 downTo 0 step 5){var v1=window(sx,i);var v2=window(sy,i);val inv1=((v1 ushr 4)-1).toLong()and dm;val inv2=((v2 ushr 4)-1).toLong()and dm;v1=v1 xor inv1.toInt();v2=v2 xor inv2.toInt();lookup(pn,m1,16,v1 and 15,4);p.condNegNiels(pn,inv1)
   if(first){p.fromPniels(tmp,pn);first=false}else{repeat(4){p.doubleInternal(tmp,tmp,-1)};p.doubleInternal(tmp,tmp,0);p.addPniels(tmp,pn,0)};lookup(pn,m2,16,v2 and 15,4);p.condNegNiels(pn,inv2);p.addPniels(tmp,pn,if(i!=0)-1 else 0);snapshot("double:window",i,tmp,v1.toLong()and dm,v2.toLong()and dm,0)
  };p.copy(out,tmp);scalar.destroy(sx);scalar.destroy(sy);p.destroy(pn);m1.forEach(p::destroy);m2.forEach(p::destroy);p.destroy(tmp)
 }
 fun dualScalarmul(a1: Shape,a2: Shape,b: Shape,s1: Scalar,s2: Scalar){val sx=adjusted(s1);val sy=adjusted(s2);val m1=Array(16){identity()};val m2=Array(16){identity()};val working=p.freshShape();val tmp=p.freshShape();val pn=p.freshShape();p.copy(working,b)
  for(i in 0..<446 step 5){if(i!=0){repeat(4){p.doubleInternal(working,working,-1)};p.doubleInternal(working,working,0)};var v1=window(sx,i);var v2=window(sy,i);val inv1=((v1 ushr 4)-1).toLong()and dm;val inv2=((v2 ushr 4)-1).toLong()and dm;v1=v1 xor inv1.toInt();v2=v2 xor inv2.toInt();p.toPniels(pn,working);lookup(tmp,m1,16,v1 and 15,4);p.condNegNiels(pn,inv1);p.addPniels(tmp,pn,0);insert(m1,tmp,v1 and 15);lookup(tmp,m2,16,v2 and 15,4);p.condNegNiels(pn,inv1 xor inv2);p.addPniels(tmp,pn,0);insert(m2,tmp,v2 and 15);snapshot("dual:window",i,working,v1.toLong()and dm,v2.toLong()and dm,0)}
  p.copy(working,m1[15]);p.copy(tmp,m2[15]);for(i in 15 downTo 2){p.add(m1[i-1],m1[i-1],m1[i]);p.add(m2[i-1],m2[i-1],m2[i]);p.add(working,working,m1[i-1]);p.add(tmp,tmp,m2[i-1])};p.add(m1[0],m1[0],m1[1]);p.add(m2[0],m2[0],m2[1]);p.doubleInternal(working,working,0);p.doubleInternal(tmp,tmp,0);p.add(a1,working,m1[0]);p.add(a2,tmp,m2[0]);scalar.destroy(sx);scalar.destroy(sy);p.destroy(pn);m1.forEach(p::destroy);m2.forEach(p::destroy);p.destroy(tmp);p.destroy(working)
 }
 fun batchInvert(out: Array<Region>,input: Array<Region>,count: Int){check(count>1);val t=fresh();f.copy(out[1],input[0]);for(i in 1..<count-1)f.multiply(out[i+1],out[i],input[i]);f.multiply(out[0],out[count-1],input[count-1]);p.invert(out[0],out[0],1);for(i in count-1 downTo 1){f.multiply(t,out[i],out[0]);f.copy(out[i],t);f.multiply(t,out[0],input[i]);f.copy(out[0],t)}}
 private fun strongReduce(out: Region){field.strongReduce(out);p.observe?.invoke("strong_reduce",LongArray(limbs){out[it]})}
 fun normalizeNiels(table: Table,zs: Array<Region>,zis: Array<Region>,count: Int){val product=fresh();batchInvert(zis,zs,count);val entries=views(table,count);for(i in 0..<count){val entry=entries[i];for(a in arrayOf(entry.a,entry.b,entry.c)){f.multiply(product,a,zis[i]);strongReduce(product);f.copy(a,product)}};clear(product)}
 fun precompute(table: Table,base: Shape){val working=p.freshShape();val start=p.freshShape();val doubles=Array(4){p.freshShape()};p.copy(working,base);val pn=p.freshShape();val zs=Array(80){fresh()};val zis=Array(80){fresh()};val entries=views(table)
  for(i in 0..<5){for(j in 0..<5){if(j!=0)p.add(start,start,working)else p.copy(start,working);if(j==4 && i==4)break;p.doubleInternal(working,working,0);if(j<4)p.copy(doubles[j],working);for(k in 0..<17)p.doubleInternal(working,working,if(k<16)1 else 0)}
   for(j in 0..<16){val gray=j xor(j ushr 1);val idx=(((i+1)shl 4)-1)xor gray;p.toPniels(pn,start);copyFields(entries[idx],pn,3);f.copy(zs[idx],pn.nz);snapshot("precompute:gray",i*16+j,start,idx.toLong(),gray.toLong(),0);if(j==15)break;var delta=(j+1)xor((j+1)ushr 1)xor gray;var k=0;while(delta>1){delta=delta ushr 1;k++};if(gray and(1 shl k)!=0)p.add(start,start,doubles[k])else p.sub(start,start,doubles[k])}
  };normalizeNiels(table,zs,zis,80);zs.forEach(::clear);zis.forEach(::clear);p.destroy(pn);p.destroy(working);p.destroy(start);doubles.forEach(p::destroy)
 }
 fun precomputedScalarmul(out: Shape,table: Table,s: Scalar){val sx=adjusted(s,"precomputedAdjustment");val ni=p.freshShape();val entries=views(table)
  for(i in 17 downTo 0){if(i!=17)p.doubleInternal(out,out,0);for(j in 0..<5){var tab=0;for(k in 0..<5){val bit=i+18*(k+j*5);if(bit<446)tab=tab or(((sx[bit/scalarBits]ushr(bit%scalarBits))and 1).toInt()shl k)};val inv=((tab ushr 4)-1).toLong()and dm;tab=(tab xor inv.toInt())and 15;lookup(ni,entries.copyOfRange(j*16,j*16+16),16,tab,3);p.condNegNiels(ni,inv);if(i!=17 || j!=0)p.addNiels(out,ni,if(j==4 && i!=0)1 else 0)else p.fromNiels(out,ni);snapshot("comb:window",i*5+j,out,tab.toLong(),inv,0)}};ni.data.fill(0,ni.offset,ni.offset+3*limbs);scalar.destroy(sx)
 }
 fun directScalarmul(out: CryptoByteSpan,base: CryptoByteSpan,s: Scalar,allowIdentity: Long,shortCircuit: Long): Int {val bp=p.freshShape();val result=p.decode(bp,base,allowIdentity);if(shortCircuit and scalarMask!=0L && result!=-1)return result;p.select(bp,p.base(),bp,result.toLong());scalarmul(bp,bp,s);p.encode(out,bp);p.destroy(bp);return result}
 fun x448(out: CryptoByteSpan,base: CryptoByteSpan,s: CryptoByteSpan): Int {val x1=fresh();val x2=fresh();val z2=fresh();val x3=fresh();val z3=fresh();val t1=fresh();val t2=fresh();f.deserialize(x1,base,0);f.copy(x2,one);f.copy(z2,zero);f.copy(x3,x1);f.copy(z3,one);var swap=0L
  for(t in 447 downTo 0){var sb=s.data[s.offset+t/8].toInt()and 255;if(t/8==0)sb=sb and 252 else if(t==447)sb=255;val kt=-((sb ushr(t%8))and 1).toLong()and dm;swap=swap xor kt;f.condSwap(x2,x3,swap);f.condSwap(z2,z3,swap);swap=kt
   f.addNr(t1,x2,z2);f.subNr(t2,x2,z2);f.subNr(z2,x3,z3);f.multiply(x2,t1,z2);f.addNr(z2,z3,x3);f.multiply(x3,t2,z2);f.subNr(z3,x2,x3);f.square(z2,z3);f.multiply(z3,x1,z2);f.addNr(z2,x2,x3);f.square(x3,z2);f.square(z2,t1);f.square(t1,t2);f.multiply(x2,z2,t1);f.subNr(t2,z2,t1);f.mulw(t1,t2,39081);f.addNr(t1,t1,z2);f.multiply(z2,t2,t1)
   observe?.invoke("x448:round",t,(listOf(x2,z2,x3,z3).flatMap{a->(0..<limbs).map{a[it]}}).toLongArray(),longArrayOf(swap,0,0))
  };f.condSwap(x2,x3,swap);f.condSwap(z2,z3,swap);p.invert(z2,z2);f.multiply(x1,x2,z2);f.serialize(out,x1);val nz=f.equal(x1,zero)==0L;arrayOf(x1,x2,z2,x3,z3,t1,t2).forEach(::clear);return if(nz)-1 else 0
 }
 fun derivePublicKey(out: CryptoByteSpan,s: CryptoByteSpan){val conditioned=s.data.copyOfRange(s.offset,s.offset+56);conditioned[0]=(conditioned[0].toInt()and 252).toByte();conditioned[55]=((conditioned[55].toInt()and 127)or 128).toByte();val sc=freshScalar();scalar.decodeLong(sc,CryptoByteSpan(conditioned,0,56));scalar.halve(sc,sc);observe?.invoke("scalar:halve",0,LongArray(scalar.limbs){sc[it]},LongArray(3));val point=p.freshShape();precomputedScalarmul(point,baseTable(),sc);p.encodeX(out,point);p.destroy(point)}
 fun generateKey(out: CryptoByteSpan,s: CryptoByteSpan)=derivePublicKey(out,s)
 class Controls(val data: IntArray,val offset: Int=0){fun power(i: Int)=data[offset+2*i];fun addend(i: Int)=data[offset+2*i+1];fun set(i: Int,power: Int,addend: Int){data[offset+2*i]=power;data[offset+2*i+1]=addend}}
 fun recodeWnaf(control: Controls,s: Scalar,tableBits: Int): Int {val size=446/(tableBits+1)+3;var position=size-1;control.set(position,-1,0);position--;var current=s[0]and 65535;val mask=(1 shl(tableBits+1))-1;val over16=scalarBits/16
  for(w in 1..<30){if(w<28)current+=((s[w/over16]ushr(16*(w%over16)))shl 16)and 0xffffffffL
   while(current and 65535!=0L){check(position>=0);val pos=Integer.numberOfTrailingZeros(current.toInt());val odd=current.toInt()ushr pos;var delta=odd and mask;if(odd and(1 shl(tableBits+1))!=0)delta-=1 shl(tableBits+1)
    // Source int32 negative left shift is UB. This is the explicit defined signed-multiply extension.
    observe?.invoke("wnaf:before",w,longArrayOf(current,position.toLong(),pos.toLong(),delta.toLong()),LongArray(3));current-=delta.toLong()shl pos;control.set(position,pos+16*(w-1),delta);position--;observe?.invoke("wnaf:after",w,longArrayOf(current,position.toLong(),pos.toLong(),delta.toLong()),LongArray(3))
   };current=current ushr 16
  };check(current==0L);position++;val count=size-position;for(i in 0..<count)control.set(i,control.power(i+position),control.addend(i+position));return count-1
 }
 fun prepareWnafTable(out: Array<Shape>,working: Shape,tableBits: Int){p.toPniels(out[0],working);if(tableBits==0)return;val tmp=p.freshShape();val twop=p.freshShape();p.double(tmp,working);p.toPniels(twop,tmp);p.addPniels(tmp,out[0],0);p.toPniels(out[1],tmp);for(i in 2..<(1 shl tableBits)){p.addPniels(tmp,twop,0);p.toPniels(out[i],tmp)};p.destroy(tmp);p.destroy(twop)}
 fun precomputeWnafs(out: Table,base: Shape){val tmp=Array(32){p.freshShape()};val zs=Array(32){fresh()};val zis=Array(32){fresh()};val entries=views(out,32);prepareWnafTable(tmp,base,5);for(i in 0..<32){copyFields(entries[i],tmp[i],3);f.copy(zs[i],tmp[i].nz)};normalizeNiels(out,zs,zis,32);tmp.forEach(p::destroy);zs.forEach(::clear);zis.forEach(::clear)}
 fun baseDoubleScalarmulNonSecret(out: Shape,s1: Scalar,base2: Shape,s2: Scalar){val cv=Controls(IntArray(2*(446/4+3)));val cp=Controls(IntArray(2*(446/6+3)));val ncp=recodeWnaf(cp,s1,5);val ncv=recodeWnaf(cv,s2,3);val table=Array(8){p.freshShape()};prepareWnafTable(table,base2,3);val fixed=views(wnafBaseTable(),32);var contp=0;var contv=0;var i=cv.power(0)
  if(i<0){p.copy(out,identity());return}else if(i>cp.power(0)){p.fromPniels(out,table[cv.addend(0)shr 1]);contv++}else if(i==cp.power(0) && i>=0){p.fromPniels(out,table[cv.addend(0)shr 1]);p.addNiels(out,fixed[cp.addend(0)shr 1],i);contv++;contp++}else{i=cp.power(0);p.fromNiels(out,fixed[cp.addend(0)shr 1]);contp++}
  i--;while(i>=0){val takeV=i==cv.power(contv);val takeP=i==cp.power(contp);p.doubleInternal(out,out,if(i!=0 && !(takeV||takeP))1 else 0)
   if(takeV){val addend=cv.addend(contv);check(addend!=0);if(addend>0)p.addPniels(out,table[addend shr 1],if(i!=0 && !takeP)1 else 0)else p.subPniels(out,table[(-addend)shr 1],if(i!=0 && !takeP)1 else 0);contv++}
   if(takeP){val addend=cp.addend(contp);check(addend!=0);if(addend>0)p.addNiels(out,fixed[addend shr 1],i)else p.subNiels(out,fixed[(-addend)shr 1],i);contp++};snapshot("nonsecret:round",i,out,contp.toLong(),contv.toLong(),0);i--
  };cv.data.fill(0);cp.data.fill(0);table.forEach(p::destroy);check(contv==ncv);check(contp==ncp)
 }
 fun precomputedDestroy(table: Table){table.data.fill(0,table.offset,table.offset+240*limbs)}
}
