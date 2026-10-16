package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.io.File
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
class CryptoDecaf448PointsTest {
 private val lines by lazy{GZIPInputStream(javaClass.getResourceAsStream("/crypto-decaf448-points/data.jsonl.gz")!!).bufferedReader().use{it.readLines()}}
 private val manifest by lazy{Json.parseToJsonElement(lines.first()).jsonObject}
 private val rows by lazy{lines.drop(1).map{Json.parseToJsonElement(it).jsonObject}}
 private fun JsonObject.text(k: String)=getValue(k).jsonPrimitive.content
 private fun hx(v: Long)=v.toULong().toString(16).padStart(16,'0')
 private fun words(v: LongArray)=JsonArray(v.map{JsonPrimitive(hx(it))})
 internal fun observe(r: JsonObject): JsonObject {
  val bits=r.text("bits").toInt();val sb=r.text("scalarBits").toInt();val f=CryptoDecaf448Points(bits,sb,r["vectorMasks32"]?.jsonPrimitive?.boolean?:false);val n=f.limbs;val prefix=32/(bits/8);val op=r.text("op")
  if(op=="constant")return buildJsonObject{put("words",words(f.constant(r.text("name"))))}
  if(op=="profile")return buildJsonObject{put("values",JsonArray(listOf(bits,sb,n,256,32,192,256,15360,if(f.vectorMasks32)16 else bits/8,6144).map(::JsonPrimitive)));put("byteorder","little")}
  if(op=="table"){val a=f.tableField(r.text("index").toInt());val out=ByteArray(58){if(it==0)17 else if(it==57)34 else 165.toByte()};f.f.serialize(CryptoByteSpan(out,1,56),CryptoP256Words.Region(a));val raw=ByteArray(66){if(it==0)17 else if(it==65)34 else 165.toByte()};a.forEachIndexed{i,v->for(j in 0..<bits/8)raw[1+i*(bits/8)+j]=(v ushr(8*j)).toByte()};return buildJsonObject{put("words",words(a));put("bytes",JsonArray(out.map{JsonPrimitive(it.toInt()and 255)}));put("rawBytes",JsonArray(raw.map{JsonPrimitive(it.toInt()and 255)}))}}
  val flag=r["flag"]?.jsonPrimitive?.int?:0;val count=if(op=="prepareFixedWindow")maxOf(1,flag)else 1
  fun storage(key: String,length: Int): CryptoDecaf448Points.Shape {val value=r[key]?.jsonArray?.map{it.jsonPrimitive.content.toULong(16).toLong()}?:List(length){0L};return f.Shape((List(prefix){0x12345678L}+value+List(prefix){0x76543210L}).toLongArray(),prefix)}
  var a=storage("a",4*n);var b=storage("b",4*n);var out=storage("out",4*n*count);val alias=r["alias"]?.jsonPrimitive?.content?:"none"
  if(alias in listOf("a-b","all"))b=a
  if(alias in listOf("out-a","all"))out=a else if(alias=="out-b")out=b
  val payload=r["bytes"]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(128);val buffer=byteArrayOf(17)+payload+byteArrayOf(34);val span=CryptoByteSpan(buffer,1,56);val mask=r["mask"]?.jsonPrimitive?.content?.toULong(16)?.toLong()?:0L;val mask2=r["mask2"]?.jsonPrimitive?.content?.toULong(16)?.toLong()?:0L;val mask3=r["mask3"]?.jsonPrimitive?.content?.toULong(16)?.toLong()?:0L;var value=0L
  val trace=mutableListOf<JsonElement>()
  if(r["trace"]?.jsonPrimitive?.boolean==true)f.observe={label,a->trace.add(buildJsonObject{put("label",label);put("words",words(a))})}
  when(op){
   "invert"->f.invert(out.x,a.x,flag)
   "deisogenize"->f.deisogenize(out.x,out.y,out.z,a,mask,mask2,mask3)
   "encode"->f.encode(span,a)
   "decode"->value=f.decode(out,span,mask).toLong()
   "sub"->f.sub(out,a,b)
   "add"->f.add(out,a,b)
   "doubleInternal"->f.doubleInternal(out,a,flag)
   "double"->f.double(out,a)
   "negate"->f.negate(out,a)
   "condNegNiels"->f.condNegNiels(out,mask)
   "toPniels"->f.toPniels(out,a)
   "fromPniels"->f.fromPniels(out,a)
   "fromNiels"->f.fromNiels(out,a)
   "addNiels"->f.addNiels(out,a,flag)
   "subNiels"->f.subNiels(out,a,flag)
   "addPniels"->f.addPniels(out,a,flag)
   "subPniels"->f.subPniels(out,a,flag)
   "prepareFixedWindow"->f.prepareFixedWindow(Array(count){f.Shape(out.data,out.offset+it*4*n)},a,flag)
   "equal"->value=f.equal(a,b)
   "valid"->value=f.valid(a)
   "torque"->f.torque(out,a)
   "pscale"->f.pscale(out,a,span)
   "select"->f.select(out,a,b,mask)
   "encodeEd"->f.encodeEd(CryptoByteSpan(buffer,1,57),a)
   "decodeEd"->value=f.decodeEd(out,CryptoByteSpan(buffer,1,57)).toLong()
   "edPublicToX"->f.edPublicToX(span,CryptoByteSpan(buffer,65,57))
   "encodeX"->f.encodeX(span,a)
   "destroy"->f.destroy(out)
   "copy"->f.copy(out,a)
   else->error(op)
  }
  return buildJsonObject{put("a",words(a.data));put("b",words(b.data));put("out",words(out.data));put("bytes",JsonArray(buffer.map{JsonPrimitive(it.toInt()and 255)}));put("value",hx(value));if(r["trace"]?.jsonPrimitive?.boolean==true)put("trace",JsonArray(trace))}
 }
 @TestFactory fun independentOriginalObservations(): Stream<DynamicTest> = rows.mapIndexed{i,row->DynamicTest.dynamicTest("$i ${row.getValue("request").jsonObject.text("op")}"){assertEquals(row.getValue("expected"),observe(row.getValue("request").jsonObject),row.getValue("request").toString())}}.stream()
 @TestFactory fun sourcePins(): Stream<DynamicTest> = manifest.getValue("sources").jsonObject.map{(p,h)->DynamicTest.dynamicTest(p){assertEquals(h.jsonPrimitive.content,MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),p).readBytes()).toHexString())}}.stream()
 @TestFactory fun sourceAssertionDomains(): Stream<DynamicTest> = manifest.getValue("assertionProbes").jsonArray.map{v->DynamicTest.dynamicTest(v.toString()){val r=v.jsonObject;val p=CryptoDecaf448Points(r.text("bits").toInt(),r.text("scalarBits").toInt(),r["vectorMasks32"]?.jsonPrimitive?.boolean?:false);assertTrue(r.text("returncode").toInt()<0);assertThrows(IllegalStateException::class.java){if(r.text("case")=="0")p.invert(p.freshShape().x,p.freshShape().x,1)else p.encodeEd(CryptoByteSpan(ByteArray(57),0,57),p.freshShape())}}}.stream()
 @Test fun corpusIntegrity(){fun canonical(v: JsonElement): JsonElement=when(v){is JsonObject->JsonObject(v.toSortedMap().mapValues{canonical(it.value)});is JsonArray->JsonArray(v.map{canonical(it)});else->v}
  assertEquals(manifest.text("caseHash"),MessageDigest.getInstance("SHA-256").digest(canonical(JsonArray(rows)).toString().toByteArray()).toHexString());assertEquals(29,manifest.getValue("operations").jsonArray.size)}
}
