package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.io.File
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
class CryptoEdwards25519PointsTest {
 private val lines by lazy {GZIPInputStream(javaClass.getResourceAsStream("/crypto-edwards25519-points/data.jsonl.gz")!!).bufferedReader().use{it.readLines()}}
 private val manifest by lazy {Json.parseToJsonElement(lines.first()).jsonObject}
 private val rows by lazy {lines.drop(1).map{Json.parseToJsonElement(it).jsonObject}}
 private fun JsonObject.text(k: String)=getValue(k).jsonPrimitive.content
 private fun hx(v: Long)=v.toULong().toString(16).padStart(16,'0')
 private fun words(a: LongArray)=JsonArray(a.map{JsonPrimitive(hx(it))})
 private fun bytes(a: ByteArray)=JsonArray(a.map{JsonPrimitive(it.toInt()and 255)})
 internal fun observe(r: JsonObject): JsonObject {
  val bits=r.text("bits").toInt();val f=CryptoEdwards25519Points(bits);val n=f.limbs
  if(r.text("op")=="originalSample"){val sample=r.getValue("sample").jsonObject;return buildJsonObject{for(key in listOf("a","b","c","prime")){val p=f.point();assertEquals(1,f.decode(p,CryptoByteSpan(sample.getValue(key).jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray())));val encoded=ByteArray(32);f.pack(CryptoByteSpan(encoded),p);put(key,bytes(encoded))}}}
  fun storage(key: String)=CryptoEdwards25519Points.Point((listOf(0x12345678L)+(r[key]?.jsonArray?.map{it.jsonPrimitive.content.toULong(16).toLong()}?:List(4*n){0L})+listOf(0x76543210L)).toLongArray(),1,n)
  var a=storage("a");var b=storage("b");var out=storage("out");val alias=r["alias"]?.jsonPrimitive?.content?:"none"
  if(alias in listOf("a-b","all"))b=a
  if(alias in listOf("out-a","all"))out=a else if(alias=="out-b")out=b
  fun scalar(key: String)=CryptoEdwards25519Scalar.Region(r[key]?.jsonArray?.map{it.jsonPrimitive.content.toULong(16).toLong()}?.toLongArray()?:LongArray(f.scalar.limbs))
  val s1=scalar("s1");val s2=scalar("s2");val extra=r["extra"]?.jsonPrimitive?.long?:0L
  val input=ByteArray(34);input[0]=17;input[33]=34;r["bytes"]?.jsonArray?.forEachIndexed{i,v->input[i+1]=v.jsonPrimitive.int.toByte()}
  val output=ByteArray(34){165.toByte()};output[0]=17;output[33]=34;val ib=CryptoByteSpan(input,1,32);val ob=CryptoByteSpan(output,1,32)
  val mode=r["table"]?.jsonPrimitive?.int?:0;val table=if(mode==0)null else Array(256){i->ByteArray(96){j->(if(mode==1)0 else if(mode==2)255 else i*17+j*29).toByte()}}
  val trace=mutableListOf<JsonElement>();val observer: ((String,CryptoEdwards25519Points.Point)->Unit)?=if(r["trace"]?.jsonPrimitive?.boolean==true)({label,p->trace.add(buildJsonObject{put("label",label);put("limbs",words(p.data.copyOfRange(p.offset,p.offset+4*n)))})})else null
  var value=-1
  when(r.text("op")){
   "p1p1_to_partial"->f.p1p1_to_partial(out,a)
   "p1p1_to_full"->f.p1p1_to_full(out,a)
   "full_to_pniels"->f.full_to_pniels(out,a)
   "add_p1p1"->f.add_p1p1(out,a,b)
   "double_p1p1"->f.double_p1p1(out,a)
   "nielsadd2_p1p1"->f.nielsadd2_p1p1(out,a,b,extra.toInt())
   "pnielsadd_p1p1"->f.pnielsadd_p1p1(out,a,b,extra.toInt())
   "double_partial"->f.double_partial(out,a)
   "double","pointDouble"->f.double(out,a)
   "add","pointAdd"->f.add(out,a,b)
   "nielsadd2"->f.nielsadd2(out,b)
   "pnielsadd"->f.pnielsadd(out,a,b)
   "pack"->f.pack(ob,a)
   "unpackNegative"->value=f.unpackNegative(out,ib)
   "doubleScalar"->f.doubleScalar(out,a,s1,s2,observer)
   "windowEqual"->value=f.windowEqual(extra,s1[0]and 0xffffffffL).toInt()
   "chooseNiels"->f.chooseNiels(out,extra.toInt(),input[1].toInt(),table)
   "baseScalar","pointBaseScalar"->f.baseScalar(out,s1,observer,table)
   "decode"->value=f.decode(out,ib)
   "equal"->value=f.equal(a,b)
   "isIdentity"->value=f.isIdentity(a)
   "negate"->f.negate(out,a)
   "cofactor"->f.cofactor(out,a)
   "movePniels"->f.movePniels(out,a,extra)
   "choosePniels"->f.choosePniels(out,Array(15){if(it%2==1)a else b},extra)
   "multiply"->f.multiply(out,a,s1,observer)
   "baseDoubleScalar"->f.doubleScalar(out,a,s2,s1,observer)
   "hasPrimeOrder"->value=f.hasPrimeOrder(a)
   else->error(r.text("op"))
  }
  return buildJsonObject{put("a",words(a.data));put("b",words(b.data));put("out",words(out.data));put("bytes",bytes(output));put("value",value);if(observer!=null)put("trace",JsonArray(trace))}
 }
 @TestFactory fun originalRawObservations(): Stream<DynamicTest> = rows.mapIndexed{i,row->DynamicTest.dynamicTest("$i ${row.getValue("request").jsonObject.text("op")}"){assertEquals(row.getValue("expected"),observe(row.getValue("request").jsonObject),row.getValue("request").toString())}}.stream()
 @TestFactory fun sourcePins(): Stream<DynamicTest> = manifest.getValue("sources").jsonObject.map{(p,h)->DynamicTest.dynamicTest(p){assertEquals(h.jsonPrimitive.content,MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),p).readBytes()).toHexString())}}.stream()
 @TestFactory fun originalPointPropertyFamilies(): Stream<DynamicTest> = rows.filter{it.getValue("request").jsonObject.text("op")=="originalSample"}.flatMap {row->
  val r=row.getValue("request").jsonObject;val bits=r.text("bits").toInt();val f=CryptoEdwards25519Points(bits);val sample=r.getValue("sample").jsonObject
  fun raw(key: String)=sample.getValue(key).jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
  fun decode(bs: ByteArray)=f.point().also{assertEquals(1,f.decode(it,CryptoByteSpan(bs)))}
  fun encode(p: CryptoEdwards25519Points.Point)=ByteArray(32).also{f.pack(CryptoByteSpan(it),p)}
  fun sc(bs: ByteArray)=CryptoEdwards25519Scalar.Region(LongArray(f.scalar.limbs)).also{f.scalar.expand(it,CryptoByteSpan(bs))}
  fun small(v: Int)=sc(byteArrayOf(v.toByte()))
  fun base(a: CryptoEdwards25519Scalar.Region)=f.point().also{f.baseScalar(it,a)}
  fun mul(a: CryptoEdwards25519Scalar.Region,p: CryptoEdwards25519Points.Point)=f.point().also{f.multiply(it,p,a)}
  fun add(a: CryptoEdwards25519Points.Point,b: CryptoEdwards25519Points.Point)=f.point().also{f.add(it,a,b)}
  fun neg(a: CryptoEdwards25519Points.Point)=f.point().also{f.negate(it,a)}
  fun eq(a: CryptoEdwards25519Points.Point,b: CryptoEdwards25519Points.Point)=assertEquals(1,f.equal(a,b))
  val a=decode(raw("a"));val b=decode(raw("b"));val c=decode(raw("c"));val prime=decode(raw("prime"));val x=sc(raw("s1"));val y=sc(raw("s2"));val zero=base(small(0))
  val orderMinusOne="ecd3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010".hexToByteArray()
  val families=listOf<()->Unit>(
   {eq(a,decode(encode(a)))},
   {val bytes=encode(a);assertArrayEquals(bytes,encode(decode(bytes)))},
   {eq(a,add(zero,a));eq(a,add(a,zero))},
   {eq(add(a,add(b,c)),add(add(a,b),c))},
   {eq(add(a,b),add(b,a))},
   {eq(zero,add(a,neg(a)))},
   {eq(add(a,a),f.point().also{f.double(it,a)})},
   {eq(mul(small(8),a),f.point().also{f.cofactor(it,a)})},
   {assertEquals(1,f.hasPrimeOrder(prime))},
   {eq(zero,mul(small(0),a))},
   {eq(a,mul(small(1),a))},
   {eq(f.point().also{f.double(it,a)},mul(small(2),a))},
   {assertEquals(f.hasPrimeOrder(a)==1,f.equal(neg(a),mul(sc(orderMinusOne),a))==1)},
   {eq(mul(x,base(y)),mul(y,base(x)))},
   {val sum=CryptoEdwards25519Scalar.Region(LongArray(f.scalar.limbs));f.scalar.add(sum,x,y);eq(add(mul(x,prime),mul(y,prime)),mul(sum,prime))},
   {eq(add(base(x),mul(y,a)),f.point().also{f.doubleScalar(it,a,y,x)})}
  )
  families.mapIndexed{i,action->DynamicTest.dynamicTest("$bits/${r.text("index")} original point family$i"){action()}}
 }.stream()
 @Test fun originalPointLiterals(){for(bits in listOf(32,64)){val f=CryptoEdwards25519Points(bits)
  fun scalar(v: Int)=CryptoEdwards25519Scalar.Region(LongArray(f.scalar.limbs)).also{f.scalar.expand(it,CryptoByteSpan(byteArrayOf(v.toByte())))}
  fun base(v: Int)=f.point().also{f.baseScalar(it,scalar(v))}
  fun encode(p: CryptoEdwards25519Points.Point)=ByteArray(32).also{f.pack(CryptoByteSpan(it),p)}
  val expected=listOf(11 to "1337036ac32d8f30d4589c3c1c595812ce0fff40e37c6f5a97ab213f318290ad",123 to "c4b800c87010f9468303deea876503e886bfde1900e9e846fd4c3cd09c1cbc9f",134 to "5120abe03ca2af66c77ca320f0b21fb556f6b65fdd7e3264c14a30d97bf7a76f")
  for((v,hex)in expected)assertArrayEquals(hex.hexToByteArray(),encode(base(v)))
  assertEquals(1,f.equal(base(134),f.point().also{f.add(it,base(123),base(11))}))
  val torsion=f.point();assertEquals(1,f.decode(torsion,CryptoByteSpan(byteArrayOf(199.toByte(),23,106,112,61,77,216.toByte(),79,186.toByte(),60,11,118,13,16,103,15,42,32,83,250.toByte(),44,57,204.toByte(),198.toByte(),78,199.toByte(),253.toByte(),119,146.toByte(),172.toByte(),3,122))))
  assertEquals(0,f.equal(base(0),f.point().also{f.multiply(it,torsion,scalar(4))}));assertEquals(1,f.equal(base(0),f.point().also{f.multiply(it,torsion,scalar(8))}))
 }}
 @Test fun corpusIntegrity(){
  fun canonical(v: JsonElement): JsonElement=when(v){is JsonObject->JsonObject(v.toSortedMap().mapValues{canonical(it.value)});is JsonArray->JsonArray(v.map{canonical(it)});else->v}
  assertEquals(manifest.text("caseHash"),MessageDigest.getInstance("SHA-256").digest(canonical(JsonArray(rows)).toString().toByteArray()).toHexString())
  assertEquals(31,manifest.getValue("operations").jsonArray.size)
 }
}
