// Edwards Donna scalar engines, crypton2.0.1; licenses/ed25519-donna-public-domain.txt.
package io.heapy.kotdoc.crypto
internal class CryptoEdwards25519Scalar(val bits: Int) {
 init {require(bits==32 || bits==64)}
 val limbs=if(bits==32)9 else 5
 private val radix=if(bits==32)30 else 56
 private val dm=if(bits==32)0xffffffffL else -1L
 private val mask=(1L shl radix)-1
 private val topRaw=256-(limbs-1)*radix
 private val top264=264-(limbs-1)*radix
 typealias Wide=CryptoP256Field.Wide
 class Region {
  private val words: LongArray?
  private val bytes: ByteArray?
  private val offset: Int
  private val bits: Int
  constructor(data: LongArray,offset: Int=0){words=data;bytes=null;this.offset=offset;bits=64}
  constructor(data: ByteArray,offset: Int,bits: Int){words=null;bytes=data;this.offset=offset;this.bits=bits}
  operator fun get(i: Int): Long {words?.let{return it[offset+i]};var v=0L;for(j in 0..<bits/8)v=v or((bytes!![offset+i*(bits/8)+j].toLong()and 255)shl(8*j));return v}
  operator fun set(i: Int,v: Long){words?.let{it[offset+i]=v;return};for(j in 0..<bits/8)bytes!![offset+i*(bits/8)+j]=(v ushr(8*j)).toByte()}
 }
 private fun fresh()=Region(LongArray(limbs))
 private fun u(v: Long)=v and dm
 private fun put(r: Region,i: Int,v: Long){r[i]=u(v)}
 private fun copy(r: Region,a: Region){for(i in 0..<limbs)put(r,i,a[i])}
 private val modm_m32=longArrayOf(0x1cf5d3edL,0x20498c69L,0x2f79cd65L,0x37be77a8L,0x00000014L,0x00000000L,0x00000000L,0x00000000L,0x00001000L)
 private val modm_mu32=longArrayOf(0x0a2c131bL,0x3673968cL,0x06329a7eL,0x01885742L,0x3fffeb21L,0x3fffffffL,0x3fffffffL,0x3fffffffL,0x000fffffL)
 private val modm_m64=longArrayOf(0x12631a5cf5d3edL,0xf9dea2f79cd658L,0x000000000014deL,0x00000000000000L,0x00000010000000L)
 private val modm_mu64=longArrayOf(0x9ce5a30a2c131bL,0x215d086329a7edL,0xffffffffeb2106L,0xffffffffffffffL,0x00000fffffffffL)
 fun constant(name: String): LongArray=when(name){"modm_m"->if(bits==32)modm_m32.copyOf()else modm_m64.copyOf();"modm_mu"->if(bits==32)modm_mu32.copyOf()else modm_mu64.copyOf();else->error("unknown scalar constant")}
 private val modulus get()=if(bits==32)modm_m32 else modm_m64
 private val mu get()=if(bits==32)modm_mu32 else modm_mu64
 fun lt(a: Long,b: Long)=u(a-b)ushr(bits-1)
 fun reduce(r: Region,observe: ((String,LongArray)->Unit)?=null){val t=fresh();var borrow=0L;for(i in 0..<limbs){val pb=u(modulus[i]+borrow);borrow=lt(r[i],pb);put(t,i,r[i]-pb+(borrow shl(if(i==limbs-1)topRaw else radix)))};val take=u(borrow-1);for(i in 0..<limbs)put(r,i,r[i]xor(take and(r[i]xor t[i])));observe?.invoke("reduce",LongArray(limbs){r[it]})}
 // Carries and sums retain the original64/128-bit accumulator widths.
 private fun degree(a: Region,b: Region,k: Int,carry: Long): Wide {var sum=Wide.of(carry);for(i in 0..<limbs){val j=k-i;if(j in 0..<limbs){val p=Wide.product(u(a[i]),u(b[j]));sum=if(bits==32)Wide.of(sum.low+p.low)else sum+p}};return sum}
 private fun carry(v: Wide)=if(bits==32)v.low ushr radix else(v ushr radix).low
 private fun low(v: Wide)=u(v.low)
 private fun repack(target: Region,k: Int,start: Int,cut: Int,v: Wide,tail: Boolean){val index=k-start;val f=low(v);val partialMask=(1L shl(radix-cut))-1
  if(index==0)put(target,0,(f ushr cut)and partialMask)
  else {put(target,index-1,(target[index-1]or(f shl(radix-cut)))and mask);put(target,index,(f ushr cut)and partialMask)}
  if(tail)put(target,index,target[index]or(carry(v)shl(radix-cut)))
 }
 fun barrett(r: Region,q1: Region,r1: Region,observe: ((String,LongArray)->Unit)?=null){
  val q3=fresh();val r2=fresh();val muRegion=Region(mu);val mRegion=Region(modulus)
  var c=degree(muRegion,q1,limbs-2,0);var over=carry(c)
  for(k in limbs-1..2*limbs-2){c=degree(muRegion,q1,k,over);repack(q3,k,limbs-1,264-(limbs-1)*radix,c,k==2*limbs-2);over=carry(c)}
  observe?.invoke("q3",LongArray(limbs){q3[it]})
  over=0
  for(k in 0..<limbs){c=degree(mRegion,q3,k,over);put(r2,k,low(c)and(if(k==limbs-1)(1L shl top264)-1 else mask));over=carry(c)}
  observe?.invoke("r2",LongArray(limbs){r2[it]})
  var borrow=0L;for(i in 0..<limbs){val pb=u(r2[i]+borrow);borrow=lt(r1[i],pb);put(r,i,r1[i]-pb+(borrow shl(if(i==limbs-1)top264 else radix)))}
  observe?.invoke("difference",LongArray(limbs){r[it]});reduce(r,observe);reduce(r,observe)
 }
 fun add(r: Region,a: Region,b: Region){var c=0L;for(i in 0..<limbs){c=u(c+u(a[i])+u(b[i]));put(r,i,if(i==limbs-1)c else c and mask);c=c ushr radix};reduce(r)}
 fun multiply(r: Region,a: Region,b: Region,observe: ((String,LongArray)->Unit)?=null){
  val q1=fresh();val r1=fresh();var over=0L
  for(k in 0..2*limbs-2){val c=degree(a,b,k,over);val f=low(c);if(k<limbs)put(r1,k,f and(if(k==limbs-1)(1L shl top264)-1 else mask));if(k>=limbs-1)repack(q1,k,limbs-1,248-(limbs-1)*radix,c,k==2*limbs-2 && bits==64);over=carry(c)}
  observe?.invoke("q1",LongArray(limbs){q1[it]});observe?.invoke("r1",LongArray(limbs){r1[it]});barrett(r,q1,r1,observe)
 }
 private fun extract(a: ByteArray,start: Int,width: Int): Long {var v=0L;for(i in 0..<width){val bit=start+i;if(bit/8<a.size)v=v or((((a[bit/8].toInt()and 255)ushr(bit%8))and 1).toLong()shl i)};return v}
 fun expand(r: Region,bytes: CryptoByteSpan,observe: ((String,LongArray)->Unit)?=null){require(bytes.size<=64);val work=ByteArray(64);bytes.data.copyInto(work,0,bytes.offset,bytes.offset+bytes.size);for(i in 0..<limbs)put(r,i,extract(work,i*radix,if(i==limbs-1)top264 else radix));if(bytes.size<32)return;val q1=fresh();for(i in 0..<limbs)put(q1,i,extract(work,248+i*radix,if(i==limbs-1)top264 else radix));barrett(r,q1,r,observe)}
 fun expandRaw(r: Region,bytes: CryptoByteSpan){val work=bytes.data.copyOfRange(bytes.offset,bytes.offset+32);for(i in 0..<limbs)put(r,i,extract(work,i*radix,if(i==limbs-1)topRaw else radix))}
 fun contract(out: CryptoByteSpan,a: Region){for(i in 0..<32/(bits/8)){val bit=i*bits;val k=bit/radix;val shift=bit%radix;val v=(u(a[k])ushr shift)or(u(a[k+1])shl(radix-shift));for(j in 0..<bits/8)out.data[out.offset+i*(bits/8)+j]=(v ushr(8*j)).toByte()}}
 fun window4(out: CryptoByteSpan,a: Region){var pos=0;fun emit(v: Long){out.data[out.offset+pos++]=(v and 15).toByte()}
  if(bits==32){for(i in 0..<8 step 2){var v=u(a[i]);repeat(7){emit(v);v=v ushr 4};v=u(v or(u(a[i+1])shl 2));repeat(8){emit(v);v=v ushr 4}};var v=u(a[8]);repeat(4){emit(v);v=v ushr 4}}
  else for(i in 0..<5){var v=a[i];repeat(if(i==4)8 else 14){emit(v);v=v ushr 4}}
  var carry=0;for(i in 0..<63){val p=out.offset+i;out.data[p]=(out.data[p].toInt()+carry).toByte();out.data[p+1]=(out.data[p+1].toInt()+(out.data[p].toInt()shr 4)).toByte();out.data[p]=(out.data[p].toInt()and 15).toByte();carry=out.data[p].toInt()shr 3;out.data[p]=(out.data[p].toInt()-(carry shl 4)).toByte()};out.data[out.offset+63]=(out.data[out.offset+63].toInt()+carry).toByte()
 }
 fun sliding(out: CryptoByteSpan,a: Region,window: Int){var pos=0;for(i in 0..<limbs){var v=u(a[i]);repeat(if(i==limbs-1)topRaw else radix){out.data[out.offset+pos++]=(v and 1).toByte();v=v ushr 1}}
  val m=(1 shl(window-1))-1
  for(j in 0..<256){val at=out.offset+j;if(out.data[at]==0.toByte())continue;for(b in 1..minOf(6,255-j)){val next=out.offset+j+b;val value=out.data[next].toInt()shl b;if(out.data[at]+value<=m){out.data[at]=(out.data[at]+value).toByte();out.data[next]=0}else if(out.data[at]-value>=-m){out.data[at]=(out.data[at]-value).toByte();for(k in j+b..<256){val p=out.offset+k;if(out.data[p]==0.toByte()){out.data[p]=1;break};out.data[p]=0}}else if(out.data[next]!=0.toByte())break}}
 }
 fun subBatch(r: Region,a: Region,b: Region,size: Int){val count=if(size in 0..<limbs)size+1 else 1;var borrow=0L;for(i in 0..<count){val v=u(a[i]-b[i]-borrow);borrow=v ushr(bits-1);put(r,i,if(i==count-1)v else v and mask)}}
 fun ltBatch(a: Region,b: Region,size: Int): Int {
  if(size !in 0..<limbs)return 0
  if(bits==32){for(i in size downTo 0){if(u(a[i])>u(b[i]))return 0;if(u(a[i])<u(b[i]))return 1};return 0}
  var borrow=0L;for(i in 0..size)borrow=u(a[i]-b[i]-borrow)ushr 63;return borrow.toInt()
 }
 fun lteBatch(a: Region,b: Region,size: Int): Int {if(bits==32){if(size !in 0..<limbs)return 1;for(i in size downTo 0){if(u(a[i])>u(b[i]))return 0;if(u(a[i])<u(b[i]))return 1};return 1};return 1-ltBatch(b,a,size)}
 fun isZero(a: Region)=if((0..<limbs).all {u(a[it])==0L})1 else 0
 fun isOne(a: Region)=if(u(a[0])==1L && (1..<limbs).all {u(a[it])==0L})1 else 0
 fun atMost128(a: Region): Int {var v=0L;if(bits==32){for(i in 5..<9)v=v or a[i];v=v or(a[4]and 0x3fffff00L)}else {v=a[4]or a[3]or(a[2]and 0xffffffffff0000L)};return if(u(v)==0L)1 else 0}
}
