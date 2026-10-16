package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.io.File
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
class CryptoDecaf448FieldTest {
 private val lines by lazy{GZIPInputStream(javaClass.getResourceAsStream("/crypto-decaf448-field/data.jsonl.gz")!!).bufferedReader().use{it.readLines()}}
 private val manifest by lazy{Json.parseToJsonElement(lines.first()).jsonObject}
 private val rows by lazy{lines.drop(1).map{Json.parseToJsonElement(it).jsonObject}}
 private fun JsonObject.text(k: String)=getValue(k).jsonPrimitive.content
 private fun hx(v: Long)=v.toULong().toString(16).padStart(16,'0')
 private fun words(v: LongArray)=JsonArray(v.map{JsonPrimitive(hx(it))})
 internal fun observe(r: JsonObject): JsonElement {
  val bits=r.text("bits").toInt();val f=CryptoDecaf448Field(bits);val n=f.limbs;val prefix=32/(bits/8)
  if(r.text("op")=="profile")return buildJsonObject{put("values",JsonArray((if(bits==32)listOf(16,32,64,2,448,32,56,32)else listOf(8,64,128,9999,448,32,56,64)).map(::JsonPrimitive)))}
  if(r.text("op")=="constant")return buildJsonObject{put("words",words(f.constant(r.text("name"))))}
  fun storage(key: String)=CryptoP256Words.Region((List(prefix){0x12345678L}+(r[key]?.jsonArray?.map{it.jsonPrimitive.content.toULong(16).toLong()}?:List(n){0L})+List(prefix){0x76543210L}).toLongArray(),prefix)
  var a=storage("a");var b=storage("b");var out=storage("out");val alias=r["alias"]?.jsonPrimitive?.content?:"none"
  if(alias in listOf("a-b","all"))b=a
  if(alias in listOf("out-a","all"))out=a else if(alias=="out-b")out=b
  val amount=r["amount"]?.jsonPrimitive?.int?:0;val mask=r["mask"]?.jsonPrimitive?.content?.toULong(16)?.toLong()?:0L
  val output=ByteArray(58){165.toByte()};output[0]=17;output[57]=34;val input=ByteArray(58);input[0]=17;input[57]=34;r["bytes"]?.jsonArray?.forEachIndexed{i,v->input[i+1]=v.jsonPrimitive.int.toByte()}
  val trace=mutableListOf<JsonElement>();val observer: ((String,LongArray)->Unit)?=if(r["trace"]?.jsonPrimitive?.boolean==true)({label,v->trace.add(buildJsonObject{put("label",label);put("limbs",words(v))})})else null
  var value=0L
  when(r.text("op")){
   "copy"->f.copy(out,a)
   "addRaw"->f.addRaw(out,a,b)
   "addNr"->f.addNr(out,a,b)
   "subRaw"->f.subRaw(out,a,b)
   "bias"->f.bias(out,amount)
   "weakReduce"->f.weakReduce(out)
   "strongReduce"->f.strongReduce(out)
   "add"->f.add(out,a,b)
   "sub"->f.sub(out,a,b)
   "multiply"->f.multiply(out,a,b)
   "square"->f.square(out,a)
   "mulwUnsigned"->f.mulwUnsigned(out,a,mask)
   "isr"->value=f.isr(out,a,observer)
   "equal"->value=f.equal(a,b)
   "lobit"->value=f.lobit(a)
   "serialize"->f.serialize(CryptoByteSpan(output,1,56),a)
   "deserialize"->value=f.deserialize(out,CryptoByteSpan(input,1,56),mask.toInt())
   "squareN"->f.squareN(out,a,amount)
   "subNr"->f.subNr(out,a,b)
   "subxNr"->f.subxNr(out,a,b,amount)
   "mulw"->f.mulw(out,a,amount)
   "select"->f.select(out,a,b,mask)
   "condNeg"->f.condNeg(out,mask)
   "condSwap"->f.condSwap(out,a,mask)
   "mulQnr"->f.mulQnr(out,a)
   "divQnr"->f.divQnr(out,a)
   else->error(r.text("op"))
  }
  return buildJsonObject{put("a",words(a.data));put("b",words(b.data));put("out",words(out.data));put("bytes",JsonArray(output.map{JsonPrimitive(it.toInt()and 255)}));put("value",hx(value));if(observer!=null)put("trace",JsonArray(trace))}
 }
 @TestFactory fun originalRawObservations(): Stream<DynamicTest> = rows.mapIndexed{i,row->DynamicTest.dynamicTest("$i ${row.getValue("request").jsonObject.text("bits")}/${row.getValue("request").jsonObject.text("op")}"){assertEquals(row.getValue("expected"),observe(row.getValue("request").jsonObject),row.getValue("request").toString())}}.stream()
 @TestFactory fun sourcePins(): Stream<DynamicTest> = manifest.getValue("sources").jsonObject.map{(p,h)->DynamicTest.dynamicTest(p){assertEquals(h.jsonPrimitive.content,MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),p).readBytes()).toHexString())}}.stream()
 @Test fun corpusIntegrity(){fun canonical(v: JsonElement): JsonElement=when(v){is JsonObject->JsonObject(v.toSortedMap().mapValues{canonical(it.value)});is JsonArray->JsonArray(v.map{canonical(it)});else->v}
  assertEquals(manifest.text("caseHash"),MessageDigest.getInstance("SHA-256").digest(canonical(JsonArray(rows)).toString().toByteArray()).toHexString());assertEquals(26,manifest.getValue("operations").jsonArray.size)
 }
}
