package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.io.File
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
class CryptoDecaf448ScalarTest {
 private val lines by lazy{GZIPInputStream(javaClass.getResourceAsStream("/crypto-decaf448-scalar/data.jsonl.gz")!!).bufferedReader().use{it.readLines()}}
 private val manifest by lazy{Json.parseToJsonElement(lines.first()).jsonObject}
 private val rows by lazy{lines.drop(1).map{Json.parseToJsonElement(it).jsonObject}}
 private fun JsonObject.text(k: String)=getValue(k).jsonPrimitive.content
 private fun hx(v: Long)=v.toULong().toString(16).padStart(16,'0')
 private fun words(v: LongArray)=JsonArray(v.map{JsonPrimitive(hx(it))})
 internal fun observe(r: JsonObject): JsonObject {
  val bits=r.text("bits").toInt();val f=CryptoDecaf448Scalar(bits);val n=f.limbs;val size=bits/8
  if(r.text("op")=="constant")return buildJsonObject{put("words",words(f.constant(r.text("name"))))}
  if(r.text("op")=="profile")return buildJsonObject{put("values",JsonArray(listOf(bits,bits,446,n,56,56,size,size).map(::JsonPrimitive)))}
  fun storage(key: String)=CryptoDecaf448Scalar.Region((listOf(0x12345678L)+(r[key]?.jsonArray?.map{it.jsonPrimitive.content.toULong(16).toLong()}?:List(n){0L})+listOf(0x76543210L,0x88776655L)).toLongArray(),1)
  var a=storage("a");var b=storage("b");var p=storage("p");var out=storage("out");val alias=r["alias"]?.jsonPrimitive?.content?:"none";val payload=r["bytes"]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(0);val length=r["length"]?.jsonPrimitive?.int?:payload.size;val body=maxOf(128,length+64);val buffer=ByteArray(body+16){if(it<8)17 else if(it>=body+8)34 else 165.toByte()};payload.copyInto(buffer,8)
  if(alias=="byte-a"){r.getValue("a").jsonArray.forEachIndexed{i,v->val word=v.jsonPrimitive.content.toULong(16).toLong();for(j in 0..<size)buffer[8+i*size+j]=(word ushr(8*j)).toByte()};a=CryptoDecaf448Scalar.Region(buffer,8,bits)}
  if(alias=="byte-out")out=CryptoDecaf448Scalar.Region(buffer,8,bits)
  if(alias in listOf("a-b","all"))b=a
  if(alias in listOf("out-a","all"))out=a else if(alias=="out-b")out=b else if(alias=="out-p")out=p
  if(alias=="p-a")p=a
  if(alias=="p-b")p=b
  val trace=mutableListOf<JsonElement>();val mode=r["traceMode"]?.jsonPrimitive?.int?:0
  val audit=if(mode==0)null else CryptoDecaf448Scalar.Audit(mode){label,index,v,extra->trace.add(buildJsonObject{put("label",label);put("index",index);put("words",words(v));put("extra",words(extra))})}
  val word=r["word"]?.jsonPrimitive?.content?.toULong(16)?.toLong()?:0L;val span=CryptoByteSpan(buffer,8+(r["byteShift"]?.jsonPrimitive?.int?:0),length);var value=0L
  when(r.text("op")){
   "subx"->f.subx(out,a,b,p,word,audit)
   "montmul"->f.montmul(out,a,b,audit)
   "multiply"->f.multiply(out,a,b,audit)
   "montsqr"->f.montsqr(out,a,audit)
   "invert"->value=f.invert(out,a,audit).toLong()
   "sub"->f.sub(out,a,b,audit)
   "add"->f.add(out,a,b,audit)
   "setUnsigned"->f.setUnsigned(out,word)
   "equal"->value=f.equal(a,b)
   "decodeShort"->f.decodeShort(out,span)
   "decode"->value=f.decode(out,span,audit).toLong()
   "destroy"->f.destroy(out)
   "decodeLong"->f.decodeLong(out,span,audit)
   "encode"->f.encode(span,a)
   "condSelect"->f.condSelect(out,a,b,word)
   "halve"->f.halve(out,a)
   "copy"->f.copy(out,a)
   "bzero"->f.bzero(span)
   else->error(r.text("op"))
  }
  fun view(v: CryptoDecaf448Scalar.Region)=words(LongArray(n+3){v[it-1]})
  return buildJsonObject{put("a",view(a));put("b",view(b));put("p",view(p));put("out",view(out));put("bytes",JsonArray(buffer.map{JsonPrimitive(it.toInt()and 255)}));put("value",hx(value));if(mode!=0)put("trace",JsonArray(trace))}
 }
 @TestFactory fun independentRawObservations(): Stream<DynamicTest> = rows.mapIndexed{i,row->DynamicTest.dynamicTest("$i ${row.getValue("request").jsonObject.text("bits")}/${row.getValue("request").jsonObject.text("op")}"){assertEquals(row.getValue("expected"),observe(row.getValue("request").jsonObject),row.getValue("request").toString())}}.stream()
 @TestFactory fun mixedApi32Arch64Observations(): Stream<DynamicTest> = rows.filter{"nativeMixed32Arch64" in it}.mapIndexed{i,row->DynamicTest.dynamicTest("mixed API32 ARCH64 $i"){
  val request=row.getValue("request").jsonObject;val expected=row.getValue("nativeMixed32Arch64").jsonObject
  if(request.text("op")=="profile"){val values=expected.getValue("values").jsonArray;assertEquals(32,values[0].jsonPrimitive.int);assertEquals(64,values[1].jsonPrimitive.int);assertEquals(14,values[3].jsonPrimitive.int);assertEquals(56,values[5].jsonPrimitive.int)}else assertEquals(expected,observe(request))
 }}.stream()
 @TestFactory fun sourcePins(): Stream<DynamicTest> = manifest.getValue("sources").jsonObject.map{(p,h)->DynamicTest.dynamicTest(p){assertEquals(h.jsonPrimitive.content,MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),p).readBytes()).toHexString())}}.stream()
 @TestFactory fun boundedDefinedInversePrefixes(): Stream<DynamicTest> = rows.filter{"definedPrefixStop" in it}.mapIndexed{i,row->DynamicTest.dynamicTest("inverse defined prefix$i"){
  val actual=observe(row.getValue("request").jsonObject).getValue("trace").jsonArray.map{it.jsonObject}.last{it.text("label")=="inverse:prefix"};val stop=row.getValue("definedPrefixStop").jsonObject
  assertEquals(stop.getValue("words"),actual.getValue("words"));assertEquals(stop.getValue("extra"),actual.getValue("extra"));assertEquals(-1,stop.getValue("index").jsonPrimitive.int);assertTrue(row.getValue("request").jsonObject.getValue("inverseCUndefined").jsonPrimitive.boolean)
 }}.stream()
 @Test fun corpusIntegrity(){fun canonical(v: JsonElement): JsonElement=when(v){is JsonObject->JsonObject(v.toSortedMap().mapValues{canonical(it.value)});is JsonArray->JsonArray(v.map{canonical(it)});else->v}
  assertEquals(manifest.text("caseHash"),MessageDigest.getInstance("SHA-256").digest(canonical(JsonArray(rows)).toString().toByteArray()).toHexString());assertEquals(18,manifest.getValue("operations").jsonArray.size)
 }
}
