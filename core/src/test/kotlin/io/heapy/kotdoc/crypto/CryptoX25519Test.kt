package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.io.File
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream

class CryptoX25519Test {
 private val lines by lazy {GZIPInputStream(javaClass.getResourceAsStream("/crypto-x25519/data.jsonl.gz")!!).bufferedReader().use {it.readLines()}}
 private val manifest by lazy {Json.parseToJsonElement(lines.first()).jsonObject}
 private fun JsonObject.text(k: String)=getValue(k).jsonPrimitive.content
 private fun word(v: String)=v.toULong(16).toLong()
 private fun hx(v: Long)=v.toULong().toString(16).padStart(16,'0')
 private fun words(a: LongArray)=JsonArray(a.map {JsonPrimitive(hx(it))})
 private fun bytes(a: ByteArray)=JsonArray(a.map {JsonPrimitive(it.toInt() and 255)})
 private fun observe(r: JsonObject): JsonObject {
  val bits=r.getValue("bits").jsonPrimitive.int;val f=CryptoX25519(bits);val n=if(bits==32)10 else 5
  fun storage(v: JsonArray?=null): LongArray {val a=v?.map {word(it.jsonPrimitive.content)}?:emptyList();return (listOf(word("0123456789abcdef"))+a+List(19-a.size){word("a5a5a5a5a5a5a5a5")}+listOf(word("fedcba9876543210"),word("8877665544332211"))).toLongArray()}
  fun region(a: LongArray)=CryptoX25519.Region(a,1)
  fun bytebuf(s: String): ByteArray=(byteArrayOf(17,34,51)+s.hexToByteArray()+byteArrayOf(68,85,102,119))
  fun span(a: ByteArray)=CryptoByteSpan(a,3,32)
  var a=storage(r["a"]?.jsonArray);var b=storage(r["b"]?.jsonArray);var out=storage();var z=storage()
  val alias=r["alias"]?.jsonPrimitive?.content?:"none";val result=mutableMapOf<String,JsonElement>()
  val rounds=mutableListOf<JsonElement>();val inverse=mutableListOf<JsonElement>()
  val round: (Int,List<LongArray>)->Unit={i,v->assertEquals(rounds.size,i);rounds.add(JsonArray(v.map(::words)))}
  val inv: (String,LongArray)->Unit={label,v->inverse+=buildJsonObject {put("label",label);put("limbs",words(v))}}
  val trace=r["trace"]?.jsonPrimitive?.boolean?:false
  when(r.text("op")) {
   "swap"->{if(alias=="same")b=a;f.swap(region(a),region(b),word(r.text("bit")));result["a"]=words(a);result["b"]=words(b)}
   "reciprocal"->{if(alias=="out-a")out=a;f.reciprocal(region(out),region(a),if(trace)inv else null);result["out"]=words(out);result["a"]=words(a)}
   "monty"->{val v=r.getValue("vectors").jsonArray.map {storage(it.jsonArray)};val outputs=List(4){storage()};f.monty(region(outputs[0]),region(outputs[1]),region(outputs[2]),region(outputs[3]),region(v[0]),region(v[1]),region(v[2]),region(v[3]),region(v[4]));result["vectors"]=JsonArray(v.map(::words));result["out"]=JsonArray(outputs.map(::words))}
   "multiply"->{val sec=bytebuf(r.text("secret"));val point=bytebuf(r.text("point"));val q=storage();CryptoX25519Field(bits).expand(region(q),span(point));if(alias=="out-q")out=q;if(alias=="x-z")z=out;f.multiply(region(out),region(z),span(sec),region(q),if(trace)round else null);result.putAll(mapOf("out" to words(out),"z" to words(z),"q" to words(q),"secret" to bytes(sec),"point" to bytes(point)))}
   "donna"->{val sec=bytebuf(r.text("secret"));var point=bytebuf(r.text("point"));var dst=bytebuf("a5".repeat(32));when(alias){"all"->{point=sec;dst=sec};"out-secret"->dst=sec;"out-point"->dst=point};f.donna(span(dst),span(sec),span(point),if(trace)round else null,if(trace)inv else null);result.putAll(mapOf("out" to bytes(dst),"secret" to bytes(sec),"point" to bytes(point)));r["katExpected"]?.let {assertEquals(it.jsonPrimitive.content,dst.copyOfRange(3,35).toHexString())}}
   else->error(r.text("op"))
  }
  if(trace) {if(r.text("op") in listOf("multiply","donna")){assertEquals(256,rounds.size);result["rounds"]=JsonArray(rounds)};if(r.text("op") in listOf("reciprocal","donna")){var sq=0;var mul=0;for(v in inverse){val label=v.jsonObject.text("label").substringBefore(':');if(label=="mul")mul++ else sq+=label.removePrefix("square").ifEmpty {"1"}.toInt()};assertEquals(254,sq);assertEquals(11,mul);result["inverse"]=JsonArray(inverse)}}
  return JsonObject(result)
 }
 @TestFactory fun originalRawObservations(): Stream<DynamicTest> = lines.drop(1).mapIndexed {i,line->val row=Json.parseToJsonElement(line).jsonObject;val r=row.getValue("request").jsonObject;DynamicTest.dynamicTest("$i ${r.text("bits")}/${r.text("op")}"){assertEquals(row.getValue("expected"),observe(r),r.toString())}}.stream()
 @TestFactory fun sourcePins(): Stream<DynamicTest> = manifest.getValue("sources").jsonObject.map {(path,hash)->DynamicTest.dynamicTest(path){assertEquals(hash.jsonPrimitive.content,MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
 @Test fun sourceSchedulesAndOriginalKats() {assertEquals(256,manifest.getValue("rounds").jsonPrimitive.int);assertEquals(254,manifest.getValue("inversion").jsonObject.getValue("squares").jsonPrimitive.int);val kats=lines.drop(1).map {Json.parseToJsonElement(it).jsonObject.getValue("request").jsonObject}.filter {"kat" in it};assertEquals(8,kats.size);assertEquals(setOf("0","1","2","3"),kats.map {it.text("kat")}.toSet());assertTrue(manifest.text("limits").contains("ISO-C UB"))}
}
