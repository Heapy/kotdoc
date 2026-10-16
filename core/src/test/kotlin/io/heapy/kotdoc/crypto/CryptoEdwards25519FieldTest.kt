package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.io.File
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class CryptoEdwards25519FieldTest {
 private val lines by lazy {GZIPInputStream(javaClass.getResourceAsStream("/crypto-edwards25519-field/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}}
 private val manifest by lazy {Json.parseToJsonElement(lines.first()).jsonObject}
 private fun JsonObject.text(k: String)=getValue(k).jsonPrimitive.content
 private fun word(s: String)=s.toULong(16).toLong()
 private fun hx(v: Long)=v.toULong().toString(16).padStart(16,'0')
 private fun words(a: LongArray)=JsonArray(a.map {JsonPrimitive(hx(it))})
 private fun bytes(a: ByteArray)=JsonArray(a.map {JsonPrimitive(it.toInt() and 255)})
 private fun observe(r: JsonObject): JsonObject {
  val bits=r.getValue("bits").jsonPrimitive.int;val f=CryptoEdwards25519Field(bits);val n=f.limbs
  val op=r.text("op")
  if(op=="operation_trace") {var step=r;val trace=mutableListOf<JsonElement>();for(name in r.getValue("steps").jsonArray){step=JsonObject(step+("op" to name));val state=observe(step);trace+=buildJsonObject {put("op",name);put("state",state)};val payload=JsonArray(state.getValue("out").jsonArray.subList(1,1+n));step=JsonObject(step+mapOf("a" to payload,"out" to payload,"bytes" to JsonArray(state.getValue("bytes").jsonArray.subList(8,104))))};return buildJsonObject {put("trace",JsonArray(trace))}}
  if(op=="constant")return buildJsonObject {put("value",hx(f.constant(r.text("name"))))}
  if(op=="table")return buildJsonObject {put("words",words(CryptoEdwards25519Tables.get(bits,r.text("name"))))}
  if(op=="packed")return buildJsonObject {put("bytes",bytes(CryptoEdwards25519Tables.packed(r.getValue("index").jsonPrimitive.int)))}
  fun storage(key: String): LongArray {val v=r[key]?.jsonArray?.map {word(it.jsonPrimitive.content)}?:emptyList();return(listOf(0x12345678L)+v+List(n-v.size){0xa5a5a5a5L}+listOf(0x76543210L,0x88776655L)).toLongArray()}
  fun region(a: LongArray)=CryptoEdwards25519Field.Region(a,1)
  fun data(key: String): ByteArray {val v=r[key]?.jsonArray?.map {it.jsonPrimitive.int.toByte()}?:List(96){165.toByte()};return ByteArray(8){17}+v+ByteArray(8){34}}
  fun span(a: ByteArray)=CryptoByteSpan(a,8,96)
  var a=storage("a");var b=storage("b");var out=storage("out");val data=data("bytes");var other=data("other")
  val alias=r["alias"]?.jsonPrimitive?.content?:"none";if(alias in listOf("a-b","all"))b=a;if(alias in listOf("out-a","all"))out=a else if(alias=="out-b")out=b
  val ar=region(a);var br=region(b);val dst=region(out)
  val power=mutableListOf<JsonElement>();val observer: ((String,LongArray)->Unit)?=if(r["trace"]?.jsonPrimitive?.boolean==true)({label,v->power.add(buildJsonObject {put("label",label);put("limbs",words(v))})})else null
  when(op){
   "copy"->f.copy(dst,ar)
   "add"->f.add(dst,ar,br)
   "add_after_basic"->f.addAfterBasic(dst,ar,br)
   "add_reduce"->f.addReduce(dst,ar,br)
   "sub"->f.sub(dst,ar,br)
   "sub_after_basic"->f.subAfterBasic(dst,ar,br)
   "sub_reduce"->f.subReduce(dst,ar,br)
   "neg"->f.negate(dst,ar)
   "mul"->f.multiply(dst,ar,br)
   "mul_noinline"->f.multiplyNoInline(dst,ar,br)
   "square"->f.square(dst,ar)
   "square_times"->f.squareTimes(dst,ar,r.getValue("count").jsonPrimitive.long)
   "expand"->f.expand(dst,span(data))
   "contract"->f.contract(span(data),ar)
   "move"->{if(alias=="same")other=data;f.moveBytes(span(data),span(other),word(r.text("flag")))}
   "swap"->{if(alias=="same"){b=a;br=region(b)};f.swap(ar,br,word(r.text("flag")))}
   "pow250"->f.pow250(ar,observer)
   "recip"->f.reciprocal(dst,ar,observer)
   "pow252"->f.pow252(dst,ar,observer)
   else->error(op)
  }
  return buildJsonObject {put("a",words(a));put("b",words(b));put("out",words(out));put("bytes",bytes(data));put("other",bytes(other));if(observer!=null){put("powerTrace",JsonArray(power));var sq=0;var mul=0;for(v in power){val label=v.jsonObject.text("label").substringBefore(':');if(label=="mul")mul++ else sq+=label.removePrefix("square").toInt()};assertEquals(when(op){"pow250"->245;"recip"->254;else->251},sq);assertEquals(if(op=="pow250")7 else 11,mul)}}
 }
 @TestFactory fun independentOriginalObservations(): Stream<DynamicTest> = lines.drop(1).mapIndexed {i,line->val row=Json.parseToJsonElement(line).jsonObject;val r=row.getValue("request").jsonObject;DynamicTest.dynamicTest("$i ${r.text("bits")}/${r.text("op")}"){assertEquals(row.getValue("expected"),observe(r),r.toString())}}.stream()
 @TestFactory fun sourcePins(): Stream<DynamicTest> = manifest.getValue("sources").jsonObject.map {(p,h)->DynamicTest.dynamicTest(p){assertEquals(h.jsonPrimitive.content,MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),p).readBytes()).toHexString())}}.stream()
 @Test fun fullFieldInventoriesAndExplicitLimits(){val f=manifest.getValue("functions").jsonObject;assertEquals(15,f.getValue("32").jsonPrimitive.int);assertEquals(16,f.getValue("64").jsonPrimitive.int);assertEquals(3,f.getValue("powerHelpers").jsonPrimitive.int);assertTrue(manifest.text("limits").contains("signed decrement UB"));assertTrue(manifest.text("limits").contains("effective-type"));assertEquals(11177,lines.size-1)}
 @Test fun everyTableAndOwnedSnapshots(){for(bits in listOf(32,64)){val n=if(bits==32)10 else 5;var total=0;for((i,name)in listOf("ge25519_basepoint","ge25519_ecd","ge25519_ec2d","ge25519_sqrtneg1","ge25519_niels_sliding_multiples").withIndex()){val a=CryptoEdwards25519Tables.get(bits,name);assertEquals(listOf(4*n,n,n,n,96*n)[i],a.size);total+=a.size;val first=a[0];a[0]=a[0] xor -1L;assertEquals(first,CryptoEdwards25519Tables.get(bits,name)[0])};assertEquals(if(bits==32)1030 else 515,total)};for(i in 0..<256){val a=CryptoEdwards25519Tables.packed(i);assertEquals(96,a.size);val first=a[0];a[0]=(a[0].toInt() xor 255).toByte();assertEquals(first,CryptoEdwards25519Tables.packed(i)[0])}}
}
