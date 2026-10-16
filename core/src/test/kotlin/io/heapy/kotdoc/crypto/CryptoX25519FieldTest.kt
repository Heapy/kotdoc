package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.io.File
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class CryptoX25519FieldTest {
 private val lines by lazy {GZIPInputStream(javaClass.getResourceAsStream("/crypto-x25519-field/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}}
 private val manifest by lazy {Json.parseToJsonElement(lines.first()).jsonObject}
 private fun JsonObject.text(k: String)=getValue(k).jsonPrimitive.content
 private fun word(s: String)=s.toULong(16).toLong()
 private fun hex(n: Long)=n.toULong().toString(16).padStart(16,'0')
 private fun words(a: LongArray)=JsonArray(a.map {JsonPrimitive(hex(it))})
 private fun bytes(a: ByteArray)=JsonArray(a.map {JsonPrimitive(it.toInt()and 255)})
 private fun observe(r: JsonObject): JsonObject {
  if(r.text("op")=="trace") {
   var step=r;val states=mutableListOf<JsonElement>()
   for(op in r.getValue("steps").jsonArray) {
    step=JsonObject(step+("op" to op));val state=observe(step)
    states+=buildJsonObject {put("op",op);put("state",state)}
    step=JsonObject(step+mapOf("a" to JsonArray(state.getValue("out").jsonArray.subList(1,20)),"out" to JsonArray(state.getValue("out").jsonArray.subList(1,20)),"bytes" to JsonArray(state.getValue("bytes").jsonArray.subList(3,35))))
   }
   return buildJsonObject {put("trace",JsonArray(states))}
  }
  val f=CryptoX25519Field(r.getValue("bits").jsonPrimitive.int)
  fun storage(key: String): LongArray {
   val values=r[key]?.jsonArray?.map {word(it.jsonPrimitive.content)}?:emptyList()
   return (listOf(word("0123456789abcdef"))+values+List(19-values.size){word("a5a5a5a5a5a5a5a5") }+listOf(word("fedcba9876543210"),word("8877665544332211"))).toLongArray()
  }
  var a=storage("a");var b=storage("b");var out=storage("out")
  val alias=r["alias"]?.jsonPrimitive?.content?:"none"
  if(alias=="a-b"||alias=="all") b=a
  if(alias=="out-a"||alias=="all")out=a else if(alias=="out-b")out=b
  val ar=CryptoX25519Field.Region(a,1);val br=CryptoX25519Field.Region(b,1);val dst=CryptoX25519Field.Region(out,1)
  val payload=r["bytes"]?.jsonArray?.map {it.jsonPrimitive.int.toByte()}?:List(32){165.toByte()}
  val data=(listOf<Byte>(17,34,51)+payload+listOf<Byte>(68,85,102,119)).toByteArray();val span=CryptoByteSpan(data,3,32)
  val scalar=r["scalar"]?.jsonPrimitive?.content?.let(::word)?:0L
  var value: Long?=null
  when(r.text("op")) {
   "sum"->f.sum(dst,ar)
   "difference"->f.difference(dst,ar)
   "scalar"->f.scalar(dst,ar,scalar)
   "mul"->f.multiply(dst,ar,br)
   "product"->f.product(dst,ar,br)
   "reduce_degree"->f.reduceDegree(dst)
   "reduce_coefficients"->f.reduceCoefficients(dst)
   "square_inner"->f.squareInner(dst,ar)
   "square"->f.square(dst,ar,word(r.text("count")))
   "expand"->f.expand(dst,span)
   "contract"->f.contract(span,ar)
   "div25"->value=f.div25(scalar)
   "div26"->value=f.div26(scalar)
   "eq32"->value=f.eq32(word(r.text("x")).toInt(),word(r.text("y")).toInt()).toLong()
   "gte32"->value=f.gte32(word(r.text("x")).toInt(),word(r.text("y")).toInt()).toLong()
   "load"->value=f.load(span)
   "store"->f.store(span,scalar)
   else->error(r.text("op"))
  }
  return buildJsonObject {put("out",words(out));put("a",words(a));put("b",words(b));put("bytes",bytes(data));value?.let {put("value",hex(it))}}
 }
 @TestFactory fun originalRawBufferObservations(): Stream<DynamicTest> = lines.drop(1).mapIndexed {i,line->val row=Json.parseToJsonElement(line).jsonObject;val r=row.getValue("request").jsonObject;DynamicTest.dynamicTest("$i ${r.text("bits")}/${r.text("op")}") {assertEquals(row.getValue("expected"),observe(r),r.toString())}}.stream()
 @TestFactory fun sourcePins(): Stream<DynamicTest> = manifest.getValue("sources").jsonObject.map {(path,expected)->DynamicTest.dynamicTest(path) {val bytes=File(System.getProperty("kotdoc.root"),path).readBytes();assertEquals(expected.jsonPrimitive.content,MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {(it.toInt()and 255).toString(16).padStart(2,'0')})}}.stream()
 @Test fun completeFieldPrefixesAndExplicitLimits() {
  val p=manifest.getValue("profiles").jsonObject;assertEquals(15,p.getValue("32").jsonArray.size);assertEquals(9,p.getValue("64").jsonArray.size)
  val limits=manifest.getValue("limits").jsonObject;assertTrue(limits.text("32").contains("ISO-C UB"));assertTrue(limits.text("64").contains("count0"))
  assertEquals(manifest.getValue("cases").jsonPrimitive.int,lines.size-1)
 }
}
