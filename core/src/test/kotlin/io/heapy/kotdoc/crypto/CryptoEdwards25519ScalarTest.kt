package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.io.File
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class CryptoEdwards25519ScalarTest {
 private val lines by lazy {GZIPInputStream(javaClass.getResourceAsStream("/crypto-edwards25519-scalar/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}}
 private val manifest by lazy {Json.parseToJsonElement(lines.first()).jsonObject}
 private val rows by lazy {lines.drop(1).map {Json.parseToJsonElement(it).jsonObject}}
 private fun JsonObject.text(k: String)=getValue(k).jsonPrimitive.content
 private fun data(v: JsonElement)=v.jsonArray.map {it.jsonPrimitive.int.toByte()}.toByteArray()
 private fun word(s: String)=s.toULong(16).toLong()
 private fun hx(v: Long)=v.toULong().toString(16).padStart(16,'0')
 private fun words(a: LongArray)=JsonArray(a.map {JsonPrimitive(hx(it))})
 private fun bytes(a: ByteArray)=JsonArray(a.map {JsonPrimitive(it.toInt()and 255)})
 private fun decoded(f: CryptoEdwards25519Scalar,b: ByteArray)=CryptoEdwards25519Scalar.Region(LongArray(f.limbs)).also {f.expand(it,CryptoByteSpan(b))}
 private fun encoded(f: CryptoEdwards25519Scalar,a: CryptoEdwards25519Scalar.Region)=ByteArray(32).also {f.contract(CryptoByteSpan(it),a)}
 private fun observe(r: JsonObject): JsonObject {
  val bits=r.getValue("bits").jsonPrimitive.int;val f=CryptoEdwards25519Scalar(bits);val n=f.limbs;val op=r.text("op");val size=bits/8
  if(op=="constant")return buildJsonObject {put("words",words(f.constant(r.text("name"))))}
  if(op=="original_sample"){val sample=r.getValue("sample").jsonObject;return buildJsonObject {for(k in listOf("a","b","c"))put(k,bytes(encoded(f,decoded(f,data(sample.getValue(k))))))}}
  fun storage(key: String): CryptoEdwards25519Scalar.Region {val v=r[key]?.jsonArray?.map {word(it.jsonPrimitive.content)}?:emptyList();return CryptoEdwards25519Scalar.Region((listOf(0x12345678L)+v+List(n-v.size){0xa5a5a5a5L}+listOf(0x76543210L,0x88776655L)).toLongArray(),1)}
  val payload=r["bytes"]?.let(::data)?:ByteArray(0);val buffer=ByteArray(336){if(it<8)17 else if(it>=328)34 else 165.toByte()};payload.copyInto(buffer,8)
  var a=storage("a");var b=storage("b");var out=storage("out");val alias=r["alias"]?.jsonPrimitive?.content?:"none"
  if(alias=="byte-a"){for((i,v)in r.getValue("a").jsonArray.withIndex()){val value=word(v.jsonPrimitive.content);for(j in 0..<size)buffer[8+i*size+j]=(value ushr(8*j)).toByte()};a=CryptoEdwards25519Scalar.Region(buffer,8,bits)}
  if(alias=="byte-out")out=CryptoEdwards25519Scalar.Region(buffer,8,bits)
  if(alias in listOf("a-b","all"))b=a
  if(alias in listOf("out-a","all"))out=a else if(alias=="out-b")out=b
  val trace=mutableListOf<JsonElement>();val observer: ((String,LongArray)->Unit)?=if(r["trace"]?.jsonPrimitive?.boolean==true)({label,v->trace.add(buildJsonObject {put("label",label);put("limbs",words(v))})})else null
  var value: Long?=null
  when(op){
   "lt"->value=f.lt(word(r.text("left")),word(r.text("right")))
   "reduce"->f.reduce(out,observer)
   "barrett"->f.barrett(out,a,b,observer)
   "add"->{f.add(out,a,b);if(observer!=null)observer("reduce",LongArray(n){out[it]})}
   "mul"->f.multiply(out,a,b,observer)
   "expand"->f.expand(out,CryptoByteSpan(buffer,8,r.getValue("length").jsonPrimitive.int),observer)
   "expand_raw"->f.expandRaw(out,CryptoByteSpan(buffer,8,32))
   "contract"->f.contract(CryptoByteSpan(buffer,8,320),a)
   "window4"->f.window4(CryptoByteSpan(buffer,8,320),a)
   "sliding"->f.sliding(CryptoByteSpan(buffer,8,320),a,r.getValue("window").jsonPrimitive.int)
   "sub_batch"->f.subBatch(out,a,b,r.getValue("size").jsonPrimitive.int)
   "lt_batch"->value=f.ltBatch(a,b,r.getValue("size").jsonPrimitive.int).toLong()
   "lte_batch"->value=f.lteBatch(a,b,r.getValue("size").jsonPrimitive.int).toLong()
   "zero"->value=f.isZero(a).toLong()
   "one"->value=f.isOne(a).toLong()
   "128"->value=f.atMost128(a).toLong()
   else->error(op)
  }
  fun view(v: CryptoEdwards25519Scalar.Region)=words(LongArray(n+3){v[it-1]})
  return buildJsonObject {put("a",view(a));put("b",view(b));put("out",view(out));put("bytes",bytes(buffer));value?.let {put("value",hx(it))};if(observer!=null)put("trace",JsonArray(trace))}
 }
 @TestFactory fun independentRawObservations(): Stream<DynamicTest> = rows.mapIndexed {i,row->val r=row.getValue("request").jsonObject;DynamicTest.dynamicTest("$i ${r.text("bits")}/${r.text("op")}"){assertEquals(row.getValue("expected"),observe(r),r.toString())}}.stream()
 @TestFactory fun sourcePins(): Stream<DynamicTest> = manifest.getValue("sources").jsonObject.map {(p,h)->DynamicTest.dynamicTest(p){assertEquals(h.jsonPrimitive.content,MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),p).readBytes()).toHexString())}}.stream()
 @TestFactory fun allOriginalScalarPropertyFamilies(): Stream<DynamicTest> = rows.filter {it.getValue("request").jsonObject.text("op")=="original_sample"}.flatMap {row->val r=row.getValue("request").jsonObject;val bits=r.getValue("bits").jsonPrimitive.int;val f=CryptoEdwards25519Scalar(bits);val sample=r.getValue("sample").jsonObject;val a=decoded(f,data(sample.getValue("a")));val b=decoded(f,data(sample.getValue("b")));val c=decoded(f,data(sample.getValue("c")));val zero=decoded(f,ByteArray(0));val one=decoded(f,byteArrayOf(1))
  fun add(x: CryptoEdwards25519Scalar.Region,y: CryptoEdwards25519Scalar.Region)=CryptoEdwards25519Scalar.Region(LongArray(f.limbs)).also {f.add(it,x,y)}
  fun mul(x: CryptoEdwards25519Scalar.Region,y: CryptoEdwards25519Scalar.Region)=CryptoEdwards25519Scalar.Region(LongArray(f.limbs)).also {f.multiply(it,x,y)}
  fun eq(x: CryptoEdwards25519Scalar.Region,y: CryptoEdwards25519Scalar.Region)=assertArrayEquals(encoded(f,x),encoded(f,y))
  val families=listOf<()->Unit>({eq(a,decoded(f,encoded(f,a)))},{eq(a,add(zero,a));eq(a,add(a,zero))},{eq(add(a,add(b,c)),add(add(a,b),c))},{eq(add(a,b),add(b,a))},{eq(zero,mul(zero,a));eq(zero,mul(a,zero))},{eq(a,mul(one,a));eq(a,mul(a,one))},{eq(mul(a,mul(b,c)),mul(mul(a,b),c))},{eq(mul(a,b),mul(b,a))},{eq(add(mul(a,b),mul(a,c)),mul(a,add(b,c)));eq(add(mul(b,a),mul(c,a)),mul(add(b,c),a))})
  families.mapIndexed {i,action->DynamicTest.dynamicTest("$bits/${r.text("index")} original scalar family$i"){action()}}
 }.stream()
 @Test fun originalOrderLiteralAndOwnership(){for(bits in listOf(32,64)){val f=CryptoEdwards25519Scalar(bits);val m=f.constant("modm_m");val original=m.copyOf();m[0]=0;assertArrayEquals(original,f.constant("modm_m"));val bytes=encoded(f,CryptoEdwards25519Scalar.Region(original));assertArrayEquals(ByteArray(32),encoded(f,decoded(f,bytes)))};assertEquals(9,manifest.getValue("originalScalarProperties").jsonPrimitive.int);assertEquals(1,manifest.getValue("originalScalarLiterals").jsonPrimitive.int);assertEquals(200,manifest.getValue("originalSamples").jsonPrimitive.int);assertEquals(16,manifest.getValue("helpers").jsonArray.size)}
}
