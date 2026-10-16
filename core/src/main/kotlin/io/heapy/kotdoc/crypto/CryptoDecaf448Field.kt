// Decaf p448, Copyright2014-2016 Cryptography Research, Inc.; licenses/decaf-MIT.txt.
package io.heapy.kotdoc.crypto
internal class CryptoDecaf448Field(val bits: Int) {
 init{require(bits==32 || bits==64)}
 val limbs=if(bits==32)16 else 8
 val radix=if(bits==32)28 else 56
 private val dm=if(bits==32)0xffffffffL else -1L
 private val mask=(1L shl radix)-1
 typealias Region=CryptoP256Words.Region
 typealias Wide=CryptoP256Field.Wide
 private operator fun Wide.minus(b: Wide): Wide {val low=this.low-b.low;return Wide(low,high-b.high-(if(java.lang.Long.compareUnsigned(this.low,b.low)<0)1 else 0))}
 private fun fresh()=Region(LongArray(limbs))
 private fun put(r: Region,i: Int,v: Long){r[i]=v and dm}
 fun constant(name: String): LongArray=when(name){"ZERO"->LongArray(limbs);"ONE"->LongArray(limbs){if(it==0)1 else 0};"MODULUS"->LongArray(limbs){mask-if(it==limbs/2)1 else 0};else->error(name)}
 fun copy(r: Region,a: Region){for(i in 0..<limbs)put(r,i,a[i])}
 fun addRaw(r: Region,a: Region,b: Region){for(i in 0..<limbs)put(r,i,a[i]+b[i]);if(bits==64)weakReduce(r)}
 fun addNr(r: Region,a: Region,b: Region)=addRaw(r,a,b)
 fun subRaw(r: Region,a: Region,b: Region){for(i in 0..<limbs)put(r,i,a[i]-b[i]+if(bits==64)2*(mask-if(i==4)1 else 0)else 0);if(bits==64)weakReduce(r)}
 fun bias(r: Region,amount: Int){if(bits==64)return;val co1=(mask*amount)and dm;val co2=(co1-amount)and dm;for(i in 0..<limbs)put(r,i,r[i]+if(i==limbs/2)co2 else co1)}
 fun weakReduce(r: Region){val tmp=r[limbs-1]ushr radix;put(r,limbs/2,r[limbs/2]+tmp);for(i in limbs-1 downTo 1)put(r,i,(r[i]and mask)+(r[i-1]ushr radix));put(r,0,(r[0]and mask)+tmp)}
 fun strongReduce(r: Region){weakReduce(r);var scarry=0L;val p=constant("MODULUS");for(i in 0..<limbs){scarry+=r[i]-p[i];put(r,i,scarry and mask);scarry=scarry shr radix};check(scarry==0L || scarry==-1L);val scarry0=scarry and dm;var carry=0L;for(i in 0..<limbs){carry+=r[i]+(scarry0 and p[i]);put(r,i,carry and mask);carry=carry ushr radix};check(((carry+scarry0)and dm)==0L)}
 fun add(r: Region,a: Region,b: Region){addRaw(r,a,b);weakReduce(r)}
 fun sub(r: Region,a: Region,b: Region){subRaw(r,a,b);bias(r,2);weakReduce(r)}
 fun subNr(r: Region,a: Region,b: Region){subRaw(r,a,b);bias(r,2);if(bits==32)weakReduce(r)}
 fun subxNr(r: Region,a: Region,b: Region,amount: Int){subRaw(r,a,b);bias(r,amount);if((if(bits==32)2 else 9999)<amount+1)weakReduce(r)}
 fun equal(a: Region,b: Region): Long {val c=fresh();sub(c,a,b);strongReduce(c);var ret=0L;for(i in 0..<limbs)ret=ret or c[i];return if(ret==0L)dm else 0}
 fun lobit(a: Region): Long {val c=fresh();copy(c,a);strongReduce(c);return -(c[0]and 1)and dm}
 fun serialize(out: CryptoByteSpan,a: Region){val red=fresh();copy(red,a);strongReduce(red);var j=0;var fill=0;var buffer=0L;for(i in 0..<56){if(fill<8 && j<limbs){buffer=buffer or(red[j]shl fill);fill+=radix;j++};out.data[out.offset+i]=buffer.toByte();fill-=8;buffer=buffer ushr 8}}
 fun deserialize(r: Region,input: CryptoByteSpan,hiNmask: Int): Long {var j=0;var fill=0;var buffer=0L;var scarry=0L;val p=constant("MODULUS");for(i in 0..<limbs){while(fill<radix && j<56){var sj=input.data[input.offset+j].toInt()and 255;if(j==55)sj=sj and hiNmask.inv();buffer=buffer or(sj.toLong()shl fill);fill+=8;j++};put(r,i,if(i<limbs-1)buffer and mask else buffer);fill-=radix;buffer=buffer ushr radix;val sum=scarry+r[i]-p[i];scarry=if(bits==32)sum shr 32 else if(sum<0)-1 else 0};return if(buffer==0L && scarry!=0L)dm else 0}
 fun select(r: Region,a: Region,b: Region,take: Long){for(i in 0..<limbs)put(r,i,(take and b[i])or(take.inv()and a[i]))}
 fun condNeg(r: Region,neg: Long){val y=fresh();sub(y,Region(constant("ZERO")),r);select(r,r,y,neg)}
 fun condSwap(a: Region,b: Region,take: Long){for(i in 0..<limbs){val v=(a[i]xor b[i])and take;put(a,i,a[i]xor v);put(b,i,b[i]xor v)}}
 fun mulQnr(r: Region,a: Region)=sub(r,Region(constant("ZERO")),a)
 fun divQnr(r: Region,a: Region)=sub(r,Region(constant("ZERO")),a)
 private fun multiply32(r: Region,a: Region,b: Region){var accum0=0L;var accum1=0L;var accum2=0L;val aa=LongArray(8){(a[it]+a[it+8])and dm};val bb=LongArray(8){(b[it]+b[it+8])and dm}
  for(j in 0..<8){accum2=0;for(i in 0..j){accum2+=a[j-i]*b[i];accum1+=aa[j-i]*bb[i];accum0+=a[8+j-i]*b[8+i]};accum1-=accum2;accum0+=accum2;accum2=0
   for(i in j+1..<8){accum0-=a[8+j-i]*b[i];accum2+=aa[8+j-i]*bb[i];accum1+=a[16+j-i]*b[8+i]};accum1+=accum2;accum0+=accum2;put(r,j,accum0 and mask);put(r,j+8,accum1 and mask);accum0=accum0 ushr 28;accum1=accum1 ushr 28}
  accum0+=accum1;accum0+=r[8];accum1+=r[0];put(r,8,accum0 and mask);put(r,0,accum1 and mask);accum0=accum0 ushr 28;accum1=accum1 ushr 28;put(r,9,r[9]+(accum0 and dm));put(r,1,r[1]+(accum1 and dm))
 }
 private fun multiply64(r: Region,a: Region,b: Region){var accum0=Wide.of(0);var accum1=Wide.of(0);var accum2=Wide.of(0);val aa=LongArray(4){a[it]+a[it+4]};val bb=LongArray(4){b[it]+b[it+4]};val bbb=LongArray(4){bb[it]+b[it+4]}
accum2 = Wide.product(a[0], b[0])
accum1 += Wide.product(aa[0], bb[0])
accum0 += Wide.product(a[4], b[4])
accum2 += Wide.product(a[1], b[7])
accum1 += Wide.product(aa[1], bbb[3])
accum0 += Wide.product(a[5], bb[3])
accum2 += Wide.product(a[2], b[6])
accum1 += Wide.product(aa[2], bbb[2])
accum0 += Wide.product(a[6], bb[2])
accum2 += Wide.product(a[3], b[5])
accum1 += Wide.product(aa[3], bbb[1])
accum0 += Wide.product(a[7], bb[1])
accum1 -= accum2
accum0 += accum2
r[0] = accum0.low  and mask
r[4] = accum1.low  and mask
accum0 = accum0 ushr 56
accum1 = accum1 ushr 56
accum2 = Wide.product(a[0], b[1])
accum1 += Wide.product(aa[0], bb[1])
accum0 += Wide.product(a[4], b[5])
accum2 += Wide.product(a[1], b[0])
accum1 += Wide.product(aa[1], bb[0])
accum0 += Wide.product(a[5], b[4])
accum2 += Wide.product(a[2], b[7])
accum1 += Wide.product(aa[2], bbb[3])
accum0 += Wide.product(a[6], bb[3])
accum2 += Wide.product(a[3], b[6])
accum1 += Wide.product(aa[3], bbb[2])
accum0 += Wide.product(a[7], bb[2])
accum1 -= accum2
accum0 += accum2
r[1] = accum0.low  and mask
r[5] = accum1.low  and mask
accum0 = accum0 ushr 56
accum1 = accum1 ushr 56
accum2 = Wide.product(a[0], b[2])
accum1 += Wide.product(aa[0], bb[2])
accum0 += Wide.product(a[4], b[6])
accum2 += Wide.product(a[1], b[1])
accum1 += Wide.product(aa[1], bb[1])
accum0 += Wide.product(a[5], b[5])
accum2 += Wide.product(a[2], b[0])
accum1 += Wide.product(aa[2], bb[0])
accum0 += Wide.product(a[6], b[4])
accum2 += Wide.product(a[3], b[7])
accum1 += Wide.product(aa[3], bbb[3])
accum0 += Wide.product(a[7], bb[3])
accum1 -= accum2
accum0 += accum2
r[2] = accum0.low  and mask
r[6] = accum1.low  and mask
accum0 = accum0 ushr 56
accum1 = accum1 ushr 56
accum2 = Wide.product(a[0], b[3])
accum1 += Wide.product(aa[0], bb[3])
accum0 += Wide.product(a[4], b[7])
accum2 += Wide.product(a[1], b[2])
accum1 += Wide.product(aa[1], bb[2])
accum0 += Wide.product(a[5], b[6])
accum2 += Wide.product(a[2], b[1])
accum1 += Wide.product(aa[2], bb[1])
accum0 += Wide.product(a[6], b[5])
accum2 += Wide.product(a[3], b[0])
accum1 += Wide.product(aa[3], bb[0])
accum0 += Wide.product(a[7], b[4])
accum1 -= accum2
accum0 += accum2
r[3] = accum0.low  and mask
r[7] = accum1.low  and mask
accum0 = accum0 ushr 56
accum1 = accum1 ushr 56
accum0 += accum1
accum0 += Wide.of(r[4])
accum1 += Wide.of(r[0])
r[4] = accum0.low  and mask
r[0] = accum1.low  and mask
accum0 = accum0 ushr 56
accum1 = accum1 ushr 56
r[5] = r[5] + accum0.low
r[1] = r[1] + accum1.low
 }
 private fun square64(r: Region,a: Region){var accum0=Wide.of(0);var accum1=Wide.of(0);var accum2=Wide.of(0);val aa=LongArray(4){a[it]+a[it+4]}
accum2 = Wide.product(a[0],a[3])
accum0 = Wide.product(aa[0],aa[3])
accum1 = Wide.product(a[4],a[7])
accum2 += Wide.product(a[1], a[2])
accum0 += Wide.product(aa[1], aa[2])
accum1 += Wide.product(a[5], a[6])
accum0 -= accum2
accum1 += accum2
r[3] = accum1.low shl 1  and mask
r[7] = accum0.low shl 1  and mask
accum0 = accum0 ushr 55
accum1 = accum1 ushr 55
accum0 += Wide.product(2*aa[1],aa[3])
accum1 += Wide.product(2*a[5], a[7])
accum0 += Wide.product(aa[2], aa[2])
accum1 += accum0
accum0 -= Wide.product(2*a[1], a[3])
accum1 += Wide.product(a[6], a[6])
accum2 = Wide.product(a[0],a[0])
accum1 -= accum2
accum0 += accum2
accum0 -= Wide.product(a[2], a[2])
accum1 += Wide.product(aa[0], aa[0])
accum0 += Wide.product(a[4], a[4])
r[0] = accum0.low  and mask
r[4] = accum1.low  and mask
accum0 = accum0 ushr 56
accum1 = accum1 ushr 56
accum2 = Wide.product(2*aa[2],aa[3])
accum0 -= Wide.product(2*a[2], a[3])
accum1 += Wide.product(2*a[6], a[7])
accum1 += accum2
accum0 += accum2
accum2 = Wide.product(2*a[0],a[1])
accum1 += Wide.product(2*aa[0], aa[1])
accum0 += Wide.product(2*a[4], a[5])
accum1 -= accum2
accum0 += accum2
r[1] = accum0.low  and mask
r[5] = accum1.low  and mask
accum0 = accum0 ushr 56
accum1 = accum1 ushr 56
accum2 = Wide.product(aa[3],aa[3])
accum0 -= Wide.product(a[3], a[3])
accum1 += Wide.product(a[7], a[7])
accum1 += accum2
accum0 += accum2
accum2 = Wide.product(2*a[0],a[2])
accum1 += Wide.product(2*aa[0], aa[2])
accum0 += Wide.product(2*a[4], a[6])
accum2 += Wide.product(a[1], a[1])
accum1 += Wide.product(aa[1], aa[1])
accum0 += Wide.product(a[5], a[5])
accum1 -= accum2
accum0 += accum2
r[2] = accum0.low  and mask
r[6] = accum1.low  and mask
accum0 = accum0 ushr 56
accum1 = accum1 ushr 56
accum0 += Wide.of(r[3])
accum1 += Wide.of(r[7])
r[3] = accum0.low  and mask
r[7] = accum1.low  and mask
accum0 = accum0 ushr 56
accum1 = accum1 ushr 56
r[4] = r[4] + accum0.low + accum1.low
r[0] = r[0] + accum1.low
 }
 fun multiply(r: Region,a: Region,b: Region){if(bits==32)multiply32(r,a,b)else multiply64(r,a,b)}
 fun square(r: Region,a: Region){if(bits==32)multiply32(r,a,a)else square64(r,a)}
 fun mulwUnsigned(r: Region,a: Region,b: Long){if(bits==32){require(b in 0..<(1L shl 28));var accum0=0L;var accum8=0L;for(i in 0..<8){accum0+=b*a[i];accum8+=b*a[i+8];put(r,i,accum0 and mask);accum0=accum0 ushr 28;put(r,i+8,accum8 and mask);accum8=accum8 ushr 28};accum0+=accum8+r[8];put(r,8,accum0 and mask);put(r,9,r[9]+((accum0 ushr 28)and dm));accum8+=r[0];put(r,0,accum8 and mask);put(r,1,r[1]+((accum8 ushr 28)and dm))}
 else{var accum0=Wide.of(0);var accum4=Wide.of(0);for(i in 0..<4){accum0+=Wide.product(b and 0xffffffffL,a[i]);accum4+=Wide.product(b and 0xffffffffL,a[i+4]);r[i]=accum0.low and mask;accum0=accum0 ushr 56;r[i+4]=accum4.low and mask;accum4=accum4 ushr 56};accum0+=accum4+Wide.of(r[4]);r[4]=accum0.low and mask;r[5]+=(accum0 ushr 56).low;accum4+=Wide.of(r[0]);r[0]=accum4.low and mask;r[1]+=(accum4 ushr 56).low}}
 fun mulw(r: Region,a: Region,w: Int){require(w!=Int.MIN_VALUE);if(w>0)mulwUnsigned(r,a,w.toLong())else{mulwUnsigned(r,a,-w.toLong());sub(r,Region(constant("ZERO")),r)}}
 fun squareN(r: Region,a: Region,count: Int){require(count>0);val tmp=fresh();var n=count;if(n and 1!=0){square(r,a);n--}else{square(tmp,a);square(r,tmp);n-=2};while(n!=0){square(tmp,r);square(r,tmp);n-=2}}
 fun isr(r: Region,x: Region,observe: ((String,LongArray)->Unit)?=null): Long {val L0=fresh();val L1=fresh();val L2=fresh()
 square  (L1,     x );observe?.invoke("stage0",LongArray(limbs){L1[it]});
 multiply  (L2,     x,   L1 );observe?.invoke("stage1",LongArray(limbs){L2[it]});
 square  (L1,   L2 );observe?.invoke("stage2",LongArray(limbs){L1[it]});
 multiply  (L2,     x,   L1 );observe?.invoke("stage3",LongArray(limbs){L2[it]});
 squareN (L1,   L2,     3 );observe?.invoke("stage4",LongArray(limbs){L1[it]});
 multiply  (L0,   L2,   L1 );observe?.invoke("stage5",LongArray(limbs){L0[it]});
 squareN (L1,   L0,     3 );observe?.invoke("stage6",LongArray(limbs){L1[it]});
 multiply  (L0,   L2,   L1 );observe?.invoke("stage7",LongArray(limbs){L0[it]});
 squareN (L2,   L0,     9 );observe?.invoke("stage8",LongArray(limbs){L2[it]});
 multiply  (L1,   L0,   L2 );observe?.invoke("stage9",LongArray(limbs){L1[it]});
 square  (L0,   L1 );observe?.invoke("stage10",LongArray(limbs){L0[it]});
 multiply  (L2,     x,   L0 );observe?.invoke("stage11",LongArray(limbs){L2[it]});
 squareN (L0,   L2,    18 );observe?.invoke("stage12",LongArray(limbs){L0[it]});
 multiply  (L2,   L1,   L0 );observe?.invoke("stage13",LongArray(limbs){L2[it]});
 squareN (L0,   L2,    37 );observe?.invoke("stage14",LongArray(limbs){L0[it]});
 multiply  (L1,   L2,   L0 );observe?.invoke("stage15",LongArray(limbs){L1[it]});
 squareN (L0,   L1,    37 );observe?.invoke("stage16",LongArray(limbs){L0[it]});
 multiply  (L1,   L2,   L0 );observe?.invoke("stage17",LongArray(limbs){L1[it]});
 squareN (L0,   L1,   111 );observe?.invoke("stage18",LongArray(limbs){L0[it]});
 multiply  (L2,   L1,   L0 );observe?.invoke("stage19",LongArray(limbs){L2[it]});
 square  (L0,   L2 );observe?.invoke("stage20",LongArray(limbs){L0[it]});
 multiply  (L1,     x,   L0 );observe?.invoke("stage21",LongArray(limbs){L1[it]});
 squareN (L0,   L1,   223 );observe?.invoke("stage22",LongArray(limbs){L0[it]});
 multiply  (L1,   L2,   L0 );observe?.invoke("stage23",LongArray(limbs){L1[it]});
 square  (L2, L1);observe?.invoke("stage24",LongArray(limbs){L2[it]});
 multiply  (L0, L2, x);observe?.invoke("stage25",LongArray(limbs){L0[it]});
 copy(r,L1);observe?.invoke("stage26",LongArray(limbs){r[it]});
 return equal(L0,Region(constant("ONE")))
 }
}
