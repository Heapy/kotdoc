// Ed25519-donna by Andrew M.; crypton extensions by Olivier Chéron. Public domain.
// See licenses/ed25519-donna-public-domain.txt and ed25519-crypton-exts-public-domain.txt.
package io.heapy.kotdoc.crypto
internal class CryptoEdwards25519Points(val bits: Int) {
 val field=CryptoEdwards25519Field(bits)
 val scalar=CryptoEdwards25519Scalar(bits)
 val limbs=field.limbs
 typealias Region=CryptoP256Words.Region
 typealias Scalar=CryptoEdwards25519Scalar.Region
 class Point(val data: LongArray,val offset: Int=0,val limbs: Int) {
  operator fun get(i: Int)=Region(data,offset+i*limbs)
  val x get()=get(0);val y get()=get(1);val z get()=get(2);val t get()=get(3)
 }
 fun point(coords: Int=4)=Point(LongArray(coords*limbs),limbs=limbs)
 private fun fresh()=Region(LongArray(limbs))
 private fun constant(name: String)=Region(CryptoEdwards25519Tables.get(bits,name))
 private val ec2d=constant("ge25519_ec2d")
 private val ecd=constant("ge25519_ecd")
 private val sqrtneg1=constant("ge25519_sqrtneg1")
 fun basepoint()=Point(CryptoEdwards25519Tables.get(bits,"ge25519_basepoint"),limbs=limbs)
 private fun zero(r: Point){r.data.fill(0,r.offset,r.offset+4*limbs)}
 private fun identity(r: Point){zero(r);r.y[0]=1;r.z[0]=1}
 private fun bytes(a: Region)=ByteArray(32).also {field.contract(CryptoByteSpan(it),a)}
 fun p1p1_to_partial(r: Point,p: Point) {
	field.multiply(r.x, p.x, p.t);
	field.multiply(r.y, p.y, p.z);
	field.multiply(r.z, p.z, p.t); 
 }
 fun p1p1_to_full(r: Point,p: Point) {
	field.multiply(r.x, p.x, p.t);
	field.multiply(r.y, p.y, p.z);
	field.multiply(r.z, p.z, p.t); 
	field.multiply(r.t, p.x, p.y); 
 }
 fun full_to_pniels(p: Point,r: Point) {
	field.sub(p[0], r.y, r.x);
	field.add(p[1], r.y, r.x);
	field.copy(p.z, r.z);
	field.multiply(p.t, r.t, ec2d);
 }
 fun add_p1p1(r: Point,p: Point,q: Point) {
	val a=fresh();val b=fresh();val c=fresh();val d=fresh();val t=fresh();val u=fresh();

	field.sub(a, p.y, p.x);
	field.add(b, p.y, p.x);
	field.sub(t, q.y, q.x);
	field.add(u, q.y, q.x);
	field.multiply(a, a, t);
	field.multiply(b, b, u);
	field.multiply(c, p.t, q.t);
	field.multiply(c, c, ec2d);
	field.multiply(d, p.z, q.z);
	field.add(d, d, d);
	field.sub(r.x, b, a);
	field.add(r.y, b, a);
	field.addAfterBasic(r.z, d, c);
	field.subAfterBasic(r.t, d, c);
 }
 fun double_p1p1(r: Point,p: Point) {
	val a=fresh();val b=fresh();val c=fresh();

	field.square(a, p.x);
	field.square(b, p.y);
	field.square(c, p.z);
	field.addReduce(c, c, c);
	field.add(r.x, p.x, p.y);
	field.square(r.x, r.x);
	field.add(r.y, b, a);
	field.sub(r.z, b, a);
	field.subAfterBasic(r.x, r.x, r.y);
	field.subAfterBasic(r.t, c, r.z);
 }
 fun nielsadd2_p1p1(r: Point,p: Point,q: Point,signbit: Int) {
	
	
	val a=fresh();val b=fresh();val c=fresh();

	field.sub(a, p.y, p.x);
	field.add(b, p.y, p.x);
	field.multiply(a, a, q[signbit]); 
	field.multiply(r.x, b, q[signbit xor 1]); 
	field.add(r.y, r.x, a);
	field.sub(r.x, r.x, a);
	field.multiply(c, p.t, q[2]);
	field.addReduce(r.t, p.z, p.z);
	field.copy(r.z, r.t);
	field.add(r[2+signbit], r[2+signbit], c); 
	field.sub(r[2+(signbit xor 1)], r[2+(signbit xor 1)], c); 
 }
 fun pnielsadd_p1p1(r: Point,p: Point,q: Point,signbit: Int) {
	
	
	val a=fresh();val b=fresh();val c=fresh();

	field.sub(a, p.y, p.x);
	field.add(b, p.y, p.x);
	field.multiply(a, a, q[signbit]); 
	field.multiply(r.x, b, q[signbit xor 1]); 
	field.add(r.y, r.x, a);
	field.sub(r.x, r.x, a);
	field.multiply(c, p.t, q.t);
	field.multiply(r.t, p.z, q.z);
	field.addReduce(r.t, r.t, r.t);
	field.copy(r.z, r.t);
	field.add(r[2+signbit], r[2+signbit], c); 
	field.sub(r[2+(signbit xor 1)], r[2+(signbit xor 1)], c); 
 }
 fun double_partial(r: Point,p: Point) {
	val t=point();
	double_p1p1(t, p);
	p1p1_to_partial(r, t);
 }
 fun double(r: Point,p: Point) {
	val t=point();
	double_p1p1(t, p);
	p1p1_to_full(r, t);
 }
 fun add(r: Point,p: Point,q: Point) {
	val t=point();
	add_p1p1(t, p, q);
	p1p1_to_full(r, t);
 }
 fun nielsadd2(r: Point,q: Point) {
	val a=fresh();val b=fresh();val c=fresh();val e=fresh();val f=fresh();val g=fresh();val h=fresh();

	field.sub(a, r.y, r.x);
	field.add(b, r.y, r.x);
	field.multiply(a, a, q[0]);
	field.multiply(e, b, q[1]);
	field.add(h, e, a);
	field.sub(e, e, a);
	field.multiply(c, r.t, q[2]);
	field.add(f, r.z, r.z);
	field.addAfterBasic(g, f, c);
	field.subAfterBasic(f, f, c);
	field.multiply(r.x, e, f);
	field.multiply(r.y, h, g);
	field.multiply(r.z, g, f);
	field.multiply(r.t, e, h);
 }
 fun pnielsadd(r: Point,p: Point,q: Point) {
	val a=fresh();val b=fresh();val c=fresh();val x=fresh();val y=fresh();val z=fresh();val t=fresh();

	field.sub(a, p.y, p.x);
	field.add(b, p.y, p.x);
	field.multiply(a, a, q[0]);
	field.multiply(x, b, q[1]);
	field.add(y, x, a);
	field.sub(x, x, a);
	field.multiply(c, p.t, q.t);
	field.multiply(t, p.z, q.z);
	field.add(t, t, t);
	field.addAfterBasic(z, t, c);
	field.subAfterBasic(t, t, c);
	field.multiply(r[1], x, t);
	field.multiply(r[0], y, z);
	field.multiply(r.z, z, t);
	field.multiply(r.t, x, y);
	field.copy(y, r[0]);
	field.sub(r[0], r[0], r[1]);
	field.add(r[1], r[1], y);
	field.multiply(r.t, r.t, ec2d);
 }
 fun pack(out: CryptoByteSpan,p: Point){val tx=fresh();val ty=fresh();val zi=fresh();field.reciprocal(zi,p.z);field.multiply(tx,p.x,zi);field.multiply(ty,p.y,zi);field.contract(out,ty);val parity=bytes(tx);out.data[out.offset+31]=(out.data[out.offset+31].toInt() xor ((parity[0].toInt()and 1)shl 7)).toByte()}
 fun unpackNegative(r: Point,p: CryptoByteSpan): Int {
  val parity=(p.data[p.offset+31].toInt()and 255)ushr 7
  val t=fresh();val root=fresh();val num=fresh();val den=fresh();val d3=fresh();val one=fresh();one[0]=1
  field.expand(r.y,p);field.copy(r.z,one);field.square(num,r.y);field.multiply(den,num,ecd);field.subReduce(num,num,r.z);field.add(den,den,r.z)
  field.square(t,den);field.multiply(d3,t,den);field.square(r.x,d3);field.multiply(r.x,r.x,den);field.multiply(r.x,r.x,num);field.pow252(r.x,r.x)
  field.multiply(r.x,r.x,d3);field.multiply(r.x,r.x,num);field.square(t,r.x);field.multiply(t,t,den);field.subReduce(root,t,num)
  if(bytes(root).any{it!=0.toByte()}){field.addReduce(t,t,num);if(bytes(t).any{it!=0.toByte()})return 0;field.multiply(r.x,r.x,sqrtneg1)}
  if((bytes(r.x)[0].toInt()and 1)==parity){field.copy(t,r.x);field.negate(r.x,t)}
  field.multiply(r.t,r.x,r.y);return 1
 }
 fun windowEqual(b: Long,c: Long)=(((b xor c)-1)and 0xffffffffL)ushr 31
 fun chooseNiels(t: Point,pos: Int,b: Int,table: Array<ByteArray>?=null){
  require(pos in 0..<32 && b in -128..127)
  val sign=(b and 255)ushr 7;val mask=(sign-1).inv().toLong()and 0xffffffffL;val u=((b.toLong()+mask)xor mask)and 0xffffffffL
  val packed=ByteArray(96);packed[0]=1;packed[32]=1
  for(i in 0..<8)field.moveBytes(CryptoByteSpan(packed),CryptoByteSpan(table?.get(pos*8+i)?:CryptoEdwards25519Tables.packed(pos*8+i)),windowEqual(u,(i+1).toLong()))
  for(i in 0..<3)field.expand(t[i],CryptoByteSpan(packed,i*32,32))
  field.swap(t[0],t[1],sign.toLong());val neg=fresh();field.negate(neg,t[2]);field.swap(t[2],neg,sign.toLong())
 }
 fun baseScalar(r: Point,s: Scalar,observe: ((String,Point)->Unit)?=null,table: Array<ByteArray>?=null){
  val b=ByteArray(64);scalar.window4(CryptoByteSpan(b),s);val t=point(3)
  chooseNiels(t,0,b[1].toInt(),table);field.subReduce(r.x,t[1],t[0]);field.addReduce(r.y,t[1],t[0]);r.z.zero(limbs);field.copy(r.t,t[2]);r.z[0]=2;observe?.invoke("base:init",r)
  for(i in 3..<64 step 2){chooseNiels(t,i/2,b[i].toInt(),table);nielsadd2(r,t);observe?.invoke("base:odd:$i",r)}
  repeat(3){double_partial(r,r)};double(r,r);observe?.invoke("base:double4",r)
  chooseNiels(t,0,b[0].toInt(),table);field.multiply(t[2],t[2],ecd);nielsadd2(r,t);observe?.invoke("base:even:0",r)
  for(i in 2..<64 step 2){chooseNiels(t,i/2,b[i].toInt(),table);nielsadd2(r,t);observe?.invoke("base:even:$i",r)}
 }
 fun doubleScalar(r: Point,p1: Point,s1: Scalar,s2: Scalar,observe: ((String,Point)->Unit)?=null){
  val slide1=ByteArray(256);val slide2=ByteArray(256);scalar.sliding(CryptoByteSpan(slide1),s1,5);scalar.sliding(CryptoByteSpan(slide2),s2,7)
  val pre1=Array(8){point()};val d1=point();val t=point();double(d1,p1);full_to_pniels(pre1[0],p1);for(i in 0..<7)pnielsadd(pre1[i+1],d1,pre1[i])
  val table=CryptoEdwards25519Tables.get(bits,"ge25519_niels_sliding_multiples")
  identity(r);var i=255;while(i>=0 && (slide1[i].toInt()or slide2[i].toInt())==0)i--
  while(i>=0){double_p1p1(t,r)
   if(slide1[i]!=0.toByte()){p1p1_to_full(r,t);pnielsadd_p1p1(t,r,pre1[kotlin.math.abs(slide1[i].toInt())/2],(slide1[i].toInt()and 255)ushr 7)}
   if(slide2[i]!=0.toByte()){p1p1_to_full(r,t);val q=Point(table,kotlin.math.abs(slide2[i].toInt())/2*3*limbs,limbs);nielsadd2_p1p1(t,r,q,(slide2[i].toInt()and 255)ushr 7)}
   if(i==0)p1p1_to_full(r,t)else p1p1_to_partial(r,t);observe?.invoke("double:$i",r);i--
  }
 }
 fun decode(r: Point,p: CryptoByteSpan): Int {val neg=ByteArray(32){p.data[p.offset+it]};neg[31]=(neg[31].toInt()xor 128).toByte();return unpackNegative(r,CryptoByteSpan(neg))}
 fun equal(p: Point,q: Point): Int {val a=fresh();val b=fresh();field.multiply(a,p.x,q.z);field.multiply(b,q.x,p.z);var eq=bytes(a).contentEquals(bytes(b));field.multiply(a,p.y,q.z);field.multiply(b,q.y,p.z);eq=eq and bytes(a).contentEquals(bytes(b));return if(eq)1 else 0}
 fun isIdentity(p: Point): Int {val d=fresh();val xZero=bytes(p.x).all{it==0.toByte()};field.subReduce(d,p.y,p.z);return if(xZero && bytes(d).all{it==0.toByte()})1 else 0}
 fun negate(r: Point,p: Point){field.negate(r.x,p.x);field.copy(r.y,p.y);field.copy(r.z,p.z);field.negate(r.t,p.t)}
 fun cofactor(r: Point,p: Point){double_partial(r,p);double_partial(r,r);double(r,r)}
 fun movePniels(out: Point,input: Point,flag: Long){val dm=if(bits==32)0xffffffffL else -1L;val nb=(flag-1)and dm;val b=nb.inv()and dm;for(i in 0..<4*limbs)out.data[out.offset+i]=((out.data[out.offset+i]and nb)or(input.data[input.offset+i]and b))and dm}
 fun choosePniels(t: Point,table: Array<Point>,pos: Long){zero(t);t[0][0]=1;t[1][0]=1;t[2][0]=1;for(i in 1..<16)movePniels(t,table[i-1],windowEqual(i.toLong(),pos))}
 fun multiply(r: Point,p: Point,s: Scalar,observe: ((String,Point)->Unit)?=null){
  val ss=ByteArray(32);scalar.contract(CryptoByteSpan(ss),s);identity(r)
  val mult=Array(15){point()};val pn=point();val t=point();full_to_pniels(mult[0],p);for(i in 1..<15)pnielsadd(mult[i],p,mult[i-1])
  for(i in 31 downTo 0){choosePniels(pn,mult,((ss[i].toInt()and 255)ushr 4).toLong());pnielsadd_p1p1(t,r,pn,0);p1p1_to_partial(r,t)
   repeat(3){double_partial(r,r)};double(r,r);observe?.invoke("multiply:high:$i",r)
   choosePniels(pn,mult,(ss[i].toInt()and 15).toLong());pnielsadd_p1p1(t,r,pn,0)
   if(i>0){p1p1_to_partial(r,t);repeat(3){double_partial(r,r)};double(r,r)}else p1p1_to_full(r,t)
   observe?.invoke("multiply:low:$i",r)
  }
 }
 fun hasPrimeOrder(p: Point): Int {val q=point();doubleScalar(q,p,Scalar(scalar.constant("modm_m")),Scalar(LongArray(scalar.limbs)));return isIdentity(q)}
}
