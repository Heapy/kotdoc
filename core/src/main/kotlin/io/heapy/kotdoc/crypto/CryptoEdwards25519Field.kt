// Edwards Donna, crypton2.0.1; licenses/ed25519-donna-public-domain.txt.
package io.heapy.kotdoc.crypto
internal class CryptoEdwards25519Field(val bits: Int) {
 init {require(bits==32 || bits==64)}
 val limbs=if(bits==32)10 else 5
 typealias Region=CryptoP256Words.Region
 typealias Wide=CryptoP256Field.Wide
 private val dm=if(bits==32)0xffffffffL else -1L
 private val reduce_mask_25=0x1ffffffL
 private val reduce_mask_26=0x3ffffffL
 private val mask51=0x7ffffffffffffL
 private fun width(i: Int)=if(bits==32)26-i%2 else 51
 private fun mask(i: Int)=(1L shl width(i))-1
 private fun put(out: Region,i: Int,v: Long){out[i]=v and dm}
 private fun fresh()=Region(LongArray(limbs))
 fun copy(out: Region,a: Region){for(i in 0..<limbs)put(out,i,a[i])}
 fun add(out: Region,a: Region,b: Region){for(i in 0..<limbs)put(out,i,a[i]+b[i])}
 fun addAfterBasic(out: Region,a: Region,b: Region){if(bits==32)addReduce(out,a,b)else add(out,a,b)}
 fun addReduce(out: Region,a: Region,b: Region){var c=0L;for(i in 0..<limbs){val v=(a[i]+b[i]+c)and dm;c=v ushr width(i);out[i]=v and mask(i)};put(out,0,out[0]+c*19)}
 private fun offset(i: Int,four: Boolean): Long=if(bits==32){if(four){if(i==0)0x0fffffb4 else if(i%2==1)0x07fffffc else 0x0ffffffc}else{if(i==0)0x07ffffda else if(i%2==1)0x03fffffe else 0x07fffffe}}else if(four){if(i==0)0x1fffffffffffb4 else 0x1ffffffffffffc}else{if(i==0)0x0fffffffffffda else 0x0ffffffffffffe}
 fun sub(out: Region,a: Region,b: Region){
  if(bits==64){for(i in 0..<5)out[i]=a[i]+offset(i,false)-b[i]}
  else {var c=0L;for(i in 0..<10){val v=(offset(i,false)+a[i]-b[i]+c)and dm;if(i<4){c=v ushr width(i);out[i]=v and mask(i)}else {out[i]=v;c=0}}}
 }
 fun subAfterBasic(out: Region,a: Region,b: Region){if(bits==32)subReduce(out,a,b)else for(i in 0..<5)out[i]=a[i]+offset(i,true)-b[i]}
 fun subReduce(out: Region,a: Region,b: Region){var c=0L;for(i in 0..<limbs){val v=(a[i]+offset(i,true)-b[i]+c)and dm;c=v ushr width(i);out[i]=v and mask(i)};put(out,0,out[0]+c*19)}
 fun negate(out: Region,a: Region){var c=0L;for(i in 0..<limbs){val v=(offset(i,false)-a[i]+c)and dm;c=v ushr width(i);out[i]=v and mask(i)};put(out,0,out[0]+c*19)}
 fun constant(name: String): Long=when(name){
  "reduce_mask_25"->reduce_mask_25
  "reduce_mask_26"->reduce_mask_26
  "reduce_mask_40"->(1L shl 40)-1
  "reduce_mask_51"->mask51
  "reduce_mask_56"->(1L shl 56)-1
  "twoP0"->offset(0,false)
  "twoP13579","twoP1234"->offset(1,false)
  "twoP2468"->offset(2,false)
  "fourP0"->offset(0,true)
  "fourP13579","fourP1234"->offset(1,true)
  "fourP2468"->offset(2,true)
  else->error("unknown field constant")
 }
 private fun m32(a: Long,b: Long)=(a and 0xffffffffL)*(b and 0xffffffffL)
 private fun multiply32(out: Region,a: Region,b: Region) {
  var r0=0L
  var r1=0L
  var r2=0L
  var r3=0L
  var r4=0L
  var r5=0L
  var r6=0L
  var r7=0L
  var r8=0L
  var r9=0L
  var s0=0L
  var s1=0L
  var s2=0L
  var s3=0L
  var s4=0L
  var s5=0L
  var s6=0L
  var s7=0L
  var s8=0L
  var s9=0L
  var m0=0L
  var m1=0L
  var m2=0L
  var m3=0L
  var m4=0L
  var m5=0L
  var m6=0L
  var m7=0L
  var m8=0L
  var m9=0L
  var c=0L
  var p=0L
  r0 = (b[0]) and 0xffffffffL
  r1 = (b[1]) and 0xffffffffL
  r2 = (b[2]) and 0xffffffffL
  r3 = (b[3]) and 0xffffffffL
  r4 = (b[4]) and 0xffffffffL
  r5 = (b[5]) and 0xffffffffL
  r6 = (b[6]) and 0xffffffffL
  r7 = (b[7]) and 0xffffffffL
  r8 = (b[8]) and 0xffffffffL
  r9 = (b[9]) and 0xffffffffL
  s0 = (a[0]) and 0xffffffffL
  s1 = (a[1]) and 0xffffffffL
  s2 = (a[2]) and 0xffffffffL
  s3 = (a[3]) and 0xffffffffL
  s4 = (a[4]) and 0xffffffffL
  s5 = (a[5]) and 0xffffffffL
  s6 = (a[6]) and 0xffffffffL
  s7 = (a[7]) and 0xffffffffL
  s8 = (a[8]) and 0xffffffffL
  s9 = (a[9]) and 0xffffffffL
  m1 = m32(r0, s1) + m32(r1, s0)
  m3 = m32(r0, s3) + m32(r1, s2) + m32(r2, s1) + m32(r3, s0)
  m5 = m32(r0, s5) + m32(r1, s4) + m32(r2, s3) + m32(r3, s2) + m32(r4, s1) + m32(r5, s0)
  m7 = m32(r0, s7) + m32(r1, s6) + m32(r2, s5) + m32(r3, s4) + m32(r4, s3) + m32(r5, s2) + m32(r6, s1) + m32(r7, s0)
  m9 = m32(r0, s9) + m32(r1, s8) + m32(r2, s7) + m32(r3, s6) + m32(r4, s5) + m32(r5, s4) + m32(r6, s3) + m32(r7, s2) + m32(r8, s1) + m32(r9, s0)
  r1 = (r1 * 2) and 0xffffffffL
  r3 = (r3 * 2) and 0xffffffffL
  r5 = (r5 * 2) and 0xffffffffL
  r7 = (r7 * 2) and 0xffffffffL
  m0 = m32(r0, s0)
  m2 = m32(r0, s2) + m32(r1, s1) + m32(r2, s0)
  m4 = m32(r0, s4) + m32(r1, s3) + m32(r2, s2) + m32(r3, s1) + m32(r4, s0)
  m6 = m32(r0, s6) + m32(r1, s5) + m32(r2, s4) + m32(r3, s3) + m32(r4, s2) + m32(r5, s1) + m32(r6, s0)
  m8 = m32(r0, s8) + m32(r1, s7) + m32(r2, s6) + m32(r3, s5) + m32(r4, s4) + m32(r5, s3) + m32(r6, s2) + m32(r7, s1) + m32(r8, s0)
  r1 = (r1 * 19) and 0xffffffffL
  r2 = (r2 * 19) and 0xffffffffL
  r3 = ((r3 / 2) * 19) and 0xffffffffL
  r4 = (r4 * 19) and 0xffffffffL
  r5 = ((r5 / 2) * 19) and 0xffffffffL
  r6 = (r6 * 19) and 0xffffffffL
  r7 = ((r7 / 2) * 19) and 0xffffffffL
  r8 = (r8 * 19) and 0xffffffffL
  r9 = (r9 * 19) and 0xffffffffL
  m1 = m1 + (m32(r9, s2) + m32(r8, s3) + m32(r7, s4) + m32(r6, s5) + m32(r5, s6) + m32(r4, s7) + m32(r3, s8) + m32(r2, s9))
  m3 = m3 + (m32(r9, s4) + m32(r8, s5) + m32(r7, s6) + m32(r6, s7) + m32(r5, s8) + m32(r4, s9))
  m5 = m5 + (m32(r9, s6) + m32(r8, s7) + m32(r7, s8) + m32(r6, s9))
  m7 = m7 + (m32(r9, s8) + m32(r8, s9))
  r3 = (r3 * 2) and 0xffffffffL
  r5 = (r5 * 2) and 0xffffffffL
  r7 = (r7 * 2) and 0xffffffffL
  r9 = (r9 * 2) and 0xffffffffL
  m0 = m0 + (m32(r9, s1) + m32(r8, s2) + m32(r7, s3) + m32(r6, s4) + m32(r5, s5) + m32(r4, s6) + m32(r3, s7) + m32(r2, s8) + m32(r1, s9))
  m2 = m2 + (m32(r9, s3) + m32(r8, s4) + m32(r7, s5) + m32(r6, s6) + m32(r5, s7) + m32(r4, s8) + m32(r3, s9))
  m4 = m4 + (m32(r9, s5) + m32(r8, s6) + m32(r7, s7) + m32(r6, s8) + m32(r5, s9))
  m6 = m6 + (m32(r9, s7) + m32(r8, s8) + m32(r7, s9))
  m8 = m8 + (m32(r9, s9))
  r0 = (m0 and reduce_mask_26) and 0xffffffffL
  c = (m0 ushr 26)
  m1 = m1 + c
  r1 = (m1 and reduce_mask_25) and 0xffffffffL
  c = (m1 ushr 25)
  m2 = m2 + c
  r2 = (m2 and reduce_mask_26) and 0xffffffffL
  c = (m2 ushr 26)
  m3 = m3 + c
  r3 = (m3 and reduce_mask_25) and 0xffffffffL
  c = (m3 ushr 25)
  m4 = m4 + c
  r4 = (m4 and reduce_mask_26) and 0xffffffffL
  c = (m4 ushr 26)
  m5 = m5 + c
  r5 = (m5 and reduce_mask_25) and 0xffffffffL
  c = (m5 ushr 25)
  m6 = m6 + c
  r6 = (m6 and reduce_mask_26) and 0xffffffffL
  c = (m6 ushr 26)
  m7 = m7 + c
  r7 = (m7 and reduce_mask_25) and 0xffffffffL
  c = (m7 ushr 25)
  m8 = m8 + c
  r8 = (m8 and reduce_mask_26) and 0xffffffffL
  c = (m8 ushr 26)
  m9 = m9 + c
  r9 = (m9 and reduce_mask_25) and 0xffffffffL
  p = ((m9 ushr 25)) and 0xffffffffL
  m0 = r0 + m32(p,19)
  r0 = (m0 and reduce_mask_26) and 0xffffffffL
  p = ((m0 ushr 26)) and 0xffffffffL
  r1 = (r1 + p) and 0xffffffffL
  out[0] = (r0) and 0xffffffffL
  out[1] = (r1) and 0xffffffffL
  out[2] = (r2) and 0xffffffffL
  out[3] = (r3) and 0xffffffffL
  out[4] = (r4) and 0xffffffffL
  out[5] = (r5) and 0xffffffffL
  out[6] = (r6) and 0xffffffffL
  out[7] = (r7) and 0xffffffffL
  out[8] = (r8) and 0xffffffffL
  out[9] = (r9) and 0xffffffffL
 }
 private fun square32(out: Region,a: Region) {
  var r0=0L
  var r1=0L
  var r2=0L
  var r3=0L
  var r4=0L
  var r5=0L
  var r6=0L
  var r7=0L
  var r8=0L
  var r9=0L
  var d6=0L
  var d7=0L
  var d8=0L
  var d9=0L
  var m0=0L
  var m1=0L
  var m2=0L
  var m3=0L
  var m4=0L
  var m5=0L
  var m6=0L
  var m7=0L
  var m8=0L
  var m9=0L
  var c=0L
  var p=0L
  r0 = (a[0]) and 0xffffffffL
  r1 = (a[1]) and 0xffffffffL
  r2 = (a[2]) and 0xffffffffL
  r3 = (a[3]) and 0xffffffffL
  r4 = (a[4]) and 0xffffffffL
  r5 = (a[5]) and 0xffffffffL
  r6 = (a[6]) and 0xffffffffL
  r7 = (a[7]) and 0xffffffffL
  r8 = (a[8]) and 0xffffffffL
  r9 = (a[9]) and 0xffffffffL
  m0 = m32(r0, r0)
  r0 = (r0 * 2) and 0xffffffffL
  m1 = m32(r0, r1)
  m2 = m32(r0, r2) + m32(r1, r1 * 2)
  r1 = (r1 * 2) and 0xffffffffL
  m3 = m32(r0, r3) + m32(r1, r2 )
  m4 = m32(r0, r4) + m32(r1, r3 * 2) + m32(r2, r2)
  r2 = (r2 * 2) and 0xffffffffL
  m5 = m32(r0, r5) + m32(r1, r4 ) + m32(r2, r3)
  m6 = m32(r0, r6) + m32(r1, r5 * 2) + m32(r2, r4) + m32(r3, r3 * 2)
  r3 = (r3 * 2) and 0xffffffffL
  m7 = m32(r0, r7) + m32(r1, r6 ) + m32(r2, r5) + m32(r3, r4 )
  m8 = m32(r0, r8) + m32(r1, r7 * 2) + m32(r2, r6) + m32(r3, r5 * 2) + m32(r4, r4 )
  m9 = m32(r0, r9) + m32(r1, r8 ) + m32(r2, r7) + m32(r3, r6 ) + m32(r4, r5 * 2)
  d6 = (r6 * 19) and 0xffffffffL
  d7 = (r7 * 2 * 19) and 0xffffffffL
  d8 = (r8 * 19) and 0xffffffffL
  d9 = (r9 * 2 * 19) and 0xffffffffL
  m0 = m0 + (m32(d9, r1 ) + m32(d8, r2 ) + m32(d7, r3 ) + m32(d6, r4 * 2) + m32(r5, r5 * 2 * 19))
  m1 = m1 + (m32(d9, r2 / 2) + m32(d8, r3 ) + m32(d7, r4 ) + m32(d6, r5 * 2))
  m2 = m2 + (m32(d9, r3 ) + m32(d8, r4 * 2) + m32(d7, r5 * 2) + m32(d6, r6 ))
  m3 = m3 + (m32(d9, r4 ) + m32(d8, r5 * 2) + m32(d7, r6 ))
  m4 = m4 + (m32(d9, r5 * 2) + m32(d8, r6 * 2) + m32(d7, r7 ))
  m5 = m5 + (m32(d9, r6 ) + m32(d8, r7 * 2))
  m6 = m6 + (m32(d9, r7 * 2) + m32(d8, r8 ))
  m7 = m7 + (m32(d9, r8 ))
  m8 = m8 + (m32(d9, r9 ))
  r0 = (m0 and reduce_mask_26) and 0xffffffffL
  c = (m0 ushr 26)
  m1 = m1 + c
  r1 = (m1 and reduce_mask_25) and 0xffffffffL
  c = (m1 ushr 25)
  m2 = m2 + c
  r2 = (m2 and reduce_mask_26) and 0xffffffffL
  c = (m2 ushr 26)
  m3 = m3 + c
  r3 = (m3 and reduce_mask_25) and 0xffffffffL
  c = (m3 ushr 25)
  m4 = m4 + c
  r4 = (m4 and reduce_mask_26) and 0xffffffffL
  c = (m4 ushr 26)
  m5 = m5 + c
  r5 = (m5 and reduce_mask_25) and 0xffffffffL
  c = (m5 ushr 25)
  m6 = m6 + c
  r6 = (m6 and reduce_mask_26) and 0xffffffffL
  c = (m6 ushr 26)
  m7 = m7 + c
  r7 = (m7 and reduce_mask_25) and 0xffffffffL
  c = (m7 ushr 25)
  m8 = m8 + c
  r8 = (m8 and reduce_mask_26) and 0xffffffffL
  c = (m8 ushr 26)
  m9 = m9 + c
  r9 = (m9 and reduce_mask_25) and 0xffffffffL
  p = ((m9 ushr 25)) and 0xffffffffL
  m0 = r0 + m32(p,19)
  r0 = (m0 and reduce_mask_26) and 0xffffffffL
  p = ((m0 ushr 26)) and 0xffffffffL
  r1 = (r1 + p) and 0xffffffffL
  out[0] = (r0) and 0xffffffffL
  out[1] = (r1) and 0xffffffffL
  out[2] = (r2) and 0xffffffffL
  out[3] = (r3) and 0xffffffffL
  out[4] = (r4) and 0xffffffffL
  out[5] = (r5) and 0xffffffffL
  out[6] = (r6) and 0xffffffffL
  out[7] = (r7) and 0xffffffffL
  out[8] = (r8) and 0xffffffffL
  out[9] = (r9) and 0xffffffffL
 }
 private fun products64(a: Region,b: Region,square: Boolean): Array<Wide> {
  val r=LongArray(5){if(square)a[it]else b[it]};val v=LongArray(5){a[it]}
  if(!square){val t=Array(5){Wide.of(0)};for(i in 0..<5)for(j in 0..<5){val k=i+j;t[k%5]=t[k%5]+Wide.product(if(k>=5)r[i]*19 else r[i],v[j])};return t}
  val d0=r[0]*2;val d1=r[1]*2;val d2=r[2]*2*19;val d419=r[4]*19;val d4=d419*2
  return arrayOf(Wide.product(r[0],r[0])+Wide.product(d4,r[1])+Wide.product(d2,r[3]),Wide.product(d0,r[1])+Wide.product(d4,r[2])+Wide.product(r[3],r[3]*19),Wide.product(d0,r[2])+Wide.product(r[1],r[1])+Wide.product(d4,r[3]),Wide.product(d0,r[3])+Wide.product(d1,r[2])+Wide.product(r[4],d419),Wide.product(d0,r[4])+Wide.product(d1,r[3])+Wide.product(r[2],r[2]))
 }
 private fun reduce64(out: Region,t: Array<Wide>){var c=0L;for(i in 0..<5){val v=t[i]+Wide.of(c);out[i]=v.low and mask51;c=(v ushr 51).low};out[0]+=c*19;c=out[0] ushr 51;out[0]=out[0]and mask51;out[1]+=c}
 fun multiply(out: Region,a: Region,b: Region){if(bits==32)multiply32(out,a,b)else reduce64(out,products64(a,b,false))}
 fun multiplyNoInline(out: Region,a: Region,b: Region)=multiply(out,a,b)
 fun square(out: Region,a: Region){if(bits==32)square32(out,a)else reduce64(out,products64(a,a,true))}
 fun squareTimes(out: Region,a: Region,count: Long){
  val r=fresh();copy(r,a)
  if(bits==32){var left=count.toInt();do{square32(r,r);left--}while(left!=0)}
  else {var left=count;do{val t=products64(r,r,true);for(i in 0..<5)r[i]=t[i].low and mask51;for(i in 1..<5)r[i]+=(t[i-1] ushr 51).low;r[0]+=(t[4] ushr 51).low*19;var c=0L;for(i in 0..<5){r[i]+=c;c=r[i] ushr 51;r[i]=r[i]and mask51};r[0]+=c*19;left--}while(left!=0L)}
  copy(out,r)
 }
 private fun load(bytes: CryptoByteSpan,start: Int,size: Int): Long {var v=0L;for(i in 0..<size)v=v or((bytes.data[bytes.offset+start+i].toLong()and 255)shl(8*i));return v}
 fun expand(out: Region,bytes: CryptoByteSpan){
  if(bits==32){val starts=intArrayOf(0,3,6,9,12,16,19,22,25,28);val shifts=intArrayOf(0,2,3,5,6,0,1,3,4,6);for(i in 0..<10)out[i]=(load(bytes,starts[i],4)ushr shifts[i])and mask(i)}
  else {val starts=intArrayOf(0,6,12,19,24);val shifts=intArrayOf(0,3,6,1,12);for(i in 0..<5)out[i]=(load(bytes,starts[i],8)ushr shifts[i])and mask51}
 }
 fun contract(bytes: CryptoByteSpan,a: Region){
  val t=LongArray(limbs){a[it]and dm}
  fun carry(full: Boolean){for(i in 0..<limbs-1){t[i+1]=(t[i+1]+(t[i]ushr width(i)))and dm;t[i]=t[i]and mask(i)};if(full)t[0]=(t[0]+19*(t.last()ushr width(limbs-1)))and dm;t[limbs-1]=t.last()and mask(limbs-1)}
  carry(true);carry(true);t[0]=(t[0]+19)and dm;carry(true)
  for(i in t.indices)t[i]=(t[i]+mask(i)+1-(if(i==0)19 else 1))and dm
  carry(false)
  if(bits==32){val shifts=intArrayOf(0,2,3,5,6,0,1,3,4,6);val starts=intArrayOf(0,3,6,9,12,16,19,22,25,28);bytes.data[bytes.offset]=0;bytes.data[bytes.offset+16]=0;for(i in 0..<10){val v=(t[i]shl shifts[i])and dm;val p=bytes.offset+starts[i];bytes.data[p]=(bytes.data[p].toInt()or(v.toInt()and 255)).toByte();for(j in 1..<4)bytes.data[p+j]=(v ushr(8*j)).toByte()}}
  else for(i in 0..<4){val shift=13*i;val v=(t[i]ushr shift)or(t[i+1]shl(51-shift));for(j in 0..<8)bytes.data[bytes.offset+i*8+j]=(v ushr(8*j)).toByte()}
 }
 fun moveBytes(out: CryptoByteSpan,a: CryptoByteSpan,flag: Long){val nb=(flag-1)and dm;val b=nb.inv()and dm;val size=bits/8;for(i in 0..<96 step size){val v=(load(out,i,size)and nb)or(load(a,i,size)and b);for(j in 0..<size)out.data[out.offset+i+j]=(v ushr(8*j)).toByte()}}
 fun swap(a: Region,b: Region,flag: Long){val take=if(bits==32)(-flag.toInt()).toLong()and dm else -flag;for(i in 0..<limbs){val v=take and(a[i]xor b[i]);put(a,i,a[i]xor v);put(b,i,b[i]xor v)}}
 fun pow250(b: Region,observe: ((String,LongArray)->Unit)?=null) {
	val t0=fresh();val c=fresh();

	 
	 squareTimes(t0,b,5);observe?.invoke("square5:t0",LongArray(limbs){t0[it]});
	 multiplyNoInline(b,t0,b);observe?.invoke("mul:b",LongArray(limbs){b[it]});
	 squareTimes(t0,b,10);observe?.invoke("square10:t0",LongArray(limbs){t0[it]});
	 multiplyNoInline(c,t0,b);observe?.invoke("mul:c",LongArray(limbs){c[it]});
	 squareTimes(t0,c,20);observe?.invoke("square20:t0",LongArray(limbs){t0[it]});
	 multiplyNoInline(t0,t0,c);observe?.invoke("mul:t0",LongArray(limbs){t0[it]});
	 squareTimes(t0,t0,10);observe?.invoke("square10:t0",LongArray(limbs){t0[it]});
	 multiplyNoInline(b,t0,b);observe?.invoke("mul:b",LongArray(limbs){b[it]});
	 squareTimes(t0,b,50);observe?.invoke("square50:t0",LongArray(limbs){t0[it]});
	 multiplyNoInline(c,t0,b);observe?.invoke("mul:c",LongArray(limbs){c[it]});
	 squareTimes(t0,c,100);observe?.invoke("square100:t0",LongArray(limbs){t0[it]});
	 multiplyNoInline(t0,t0,c);observe?.invoke("mul:t0",LongArray(limbs){t0[it]});
	 squareTimes(t0,t0,50);observe?.invoke("square50:t0",LongArray(limbs){t0[it]});
	 multiplyNoInline(b,t0,b);observe?.invoke("mul:b",LongArray(limbs){b[it]});
 }
 fun reciprocal(out: Region,z: Region,observe: ((String,LongArray)->Unit)?=null) {
	val a=fresh();val t0=fresh();val b=fresh();

	 squareTimes(a,z,1);observe?.invoke("square1:a",LongArray(limbs){a[it]}); 
	 squareTimes(t0,a,2);observe?.invoke("square2:t0",LongArray(limbs){t0[it]});
	 multiplyNoInline(b,t0,z);observe?.invoke("mul:b",LongArray(limbs){b[it]}); 
	 multiplyNoInline(a,b,a);observe?.invoke("mul:a",LongArray(limbs){a[it]}); 
	 squareTimes(t0,a,1);observe?.invoke("square1:t0",LongArray(limbs){t0[it]});
	 multiplyNoInline(b,t0,b);observe?.invoke("mul:b",LongArray(limbs){b[it]});
	 pow250(b,observe);
	 squareTimes(b,b,5);observe?.invoke("square5:b",LongArray(limbs){b[it]});
	 multiplyNoInline(out,b,a);observe?.invoke("mul:out",LongArray(limbs){out[it]});
 }
 fun pow252(two252m3: Region,z: Region,observe: ((String,LongArray)->Unit)?=null) {
	val b=fresh();val c=fresh();val t0=fresh();

	 squareTimes(c,z,1);observe?.invoke("square1:c",LongArray(limbs){c[it]}); 
	 squareTimes(t0,c,2);observe?.invoke("square2:t0",LongArray(limbs){t0[it]}); 
	 multiplyNoInline(b,t0,z);observe?.invoke("mul:b",LongArray(limbs){b[it]}); 
	 multiplyNoInline(c,b,c);observe?.invoke("mul:c",LongArray(limbs){c[it]}); 
	 squareTimes(t0,c,1);observe?.invoke("square1:t0",LongArray(limbs){t0[it]});
	 multiplyNoInline(b,t0,b);observe?.invoke("mul:b",LongArray(limbs){b[it]});
	 pow250(b,observe);
	 squareTimes(b,b,2);observe?.invoke("square2:b",LongArray(limbs){b[it]});
	 multiplyNoInline(two252m3,b,z);observe?.invoke("mul:two252m3",LongArray(limbs){two252m3[it]});
 }
}
