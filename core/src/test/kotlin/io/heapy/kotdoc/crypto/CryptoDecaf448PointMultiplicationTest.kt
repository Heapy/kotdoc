package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
internal object DecafMultiplicationReplay {
 var boundedControl:JsonArray?=null
 var boundedLogic:JsonArray?=null
 private fun JsonObject.text(k:String)=getValue(k).jsonPrimitive.content
 private fun hx(x:Long)=x.toULong().toString(16).padStart(16,'0')
 private fun words(a:LongArray)=JsonArray(a.map{JsonPrimitive(hx(it))})
 fun observe(r:JsonObject):JsonObject {
  val bits=r.text("bits").toInt();val sb=r.text("scalarBits").toInt();val p=CryptoDecaf448Points(bits,sb,r.getValue("vectorMasks32").jsonPrimitive.boolean);val m=CryptoDecaf448PointMultiplication(p);val n=p.limbs;val prefix=32/(bits/8);val sp=32/(sb/8);val op=r.text("op");val count=r["count"]?.jsonPrimitive?.int?:0;val alias=r.text("alias")
  fun storage(k:String,len:Int,pad:Int=prefix):LongArray {val input=r[k]?.jsonArray?.map{it.jsonPrimitive.content.toULong(16).toLong()}?:emptyList();return LongArray(len+2*pad){if(it<pad)0x12345678L else if(it>=pad+len)0x76543210L else input.getOrElse(it-pad){0x51515151L}}}
  var a=storage("a",4*n);var b=storage("b",4*n);var out=storage("out",4*n*if(op=="prepareWnafTable")maxOf(1,1 shl count)else 1);var out2=storage("out2",4*n);val s1=storage("s1",448/sb,sp);var s2=storage("s2",448/sb,sp)
  val table=if(op in listOf("normalizeNiels","precompute","lookupNiels","precomputedScalarmul","precomputeWnafs","precomputedDestroy"))storage("table",240*n)else null
  var gi=if(op in listOf("batchInvert","normalizeNiels"))storage("gfIn",maxOf(2,count)*n)else null;val go=gi?.let{storage("gfOut",maxOf(2,count)*n)}
  val controls=if(op=="recodeWnaf")IntArray(2*(446/(count+1)+3)+2){if(it==0)0x12345678 else if(it==2*(446/(count+1)+3)+1)0x76543210 else 0x51515151}else null
  if(alias in listOf("a-b","all"))b=a
  if(alias in listOf("out-a","all"))out=a else if(alias=="out-b")out=b
  if(alias in listOf("out2-a","out-all"))out2=a
  if(alias in listOf("out-out2","out-all"))out=out2
  if(alias=="s1-s2")s2=s1
  if(alias=="table-a"){r.getValue("a").jsonArray.forEachIndexed{i,v->table!![prefix+i]=v.jsonPrimitive.content.toULong(16).toLong()};a=table!!}
  if(alias=="out-table")out=table!!
  if(alias=="gf-table")gi=table
  val bytes=mutableMapOf<String,ByteArray>();for(k in listOf("bytes","baseBytes","scalarBytes")){val v=r[k]?.jsonArray?.map{it.jsonPrimitive.int.toByte()}?.toByteArray()?:ByteArray(56){165.toByte()};bytes[k]=byteArrayOf(17)+v+byteArrayOf(34)}
  if(alias in listOf("base-scalar","byte-all"))bytes["scalarBytes"]=bytes.getValue("baseBytes")
  if(alias in listOf("out-base","byte-all"))bytes["bytes"]=bytes.getValue("baseBytes") else if(alias=="out-scalar")bytes["bytes"]=bytes.getValue("scalarBytes")
  fun shape(v:LongArray)=p.Shape(v,prefix)
  fun scalar(v:LongArray)=CryptoDecaf448Scalar.Region(v,sp)
  fun span(k:String)=CryptoByteSpan(bytes.getValue(k),1,56)
  fun table()=CryptoDecaf448PointMultiplication.Table(table!!,prefix)
  fun fields(v:LongArray)=Array(maxOf(2,count)){CryptoP256Words.Region(v,prefix+it*n)}
  boundedControl=null;boundedLogic=null
  val digest=MessageDigest.getInstance("SHA-256");var writes=0;val stats=linkedMapOf<String,LongArray>();val states=mutableListOf<JsonElement>();val logic=mutableListOf<JsonElement>()
  p.observe={label,values->
   val record=ByteBuffer.allocate(160).order(ByteOrder.LITTLE_ENDIAN);record.put(label.toByteArray());record.position(32);values.forEach(record::putLong);digest.update(record.array());writes++
   val stat=stats.getOrPut(label){LongArray(3)};stat[0]++;values.forEach{if(it.toULong()>stat[1].toULong())stat[1]=it;if(it.toULong()>((1UL shl(if(bits==32)28 else 56))-1UL))stat[2]++}
   if(r["details"]?.jsonPrimitive?.boolean==true)states.add(buildJsonObject{put("label",label);put("words",words(values))})
  }
  if(r["audit"]?.jsonPrimitive?.boolean==true||op=="recodeWnaf")m.observe={label,index,values,extra->
   val event=buildJsonObject{put("label",label);put("index",index);put("words",words(values));put("extra",words(extra))};logic.add(event)
   if(op=="recodeWnaf"&&label=="wnaf:before"&&values[3]<0&&boundedControl==null){boundedControl=JsonArray(controls!!.map(::JsonPrimitive));boundedLogic=JsonArray(logic+JsonObject(event+mapOf("label" to JsonPrimitive("wnaf:bounded-stop"))))}
  }
  var value=0L
  when(op){
   "scalarmul"->m.scalarmul(shape(out),shape(a),scalar(s1))
   "doubleScalarmul"->m.doubleScalarmul(shape(out),shape(a),scalar(s1),shape(b),scalar(s2))
   "dualScalarmul"->m.dualScalarmul(shape(out),shape(out2),shape(a),scalar(s1),scalar(s2))
   "batchInvert"->m.batchInvert(fields(go!!),fields(gi!!),count)
   "normalizeNiels"->m.normalizeNiels(table(),fields(gi!!),fields(go!!),count)
   "precompute"->m.precompute(table(),shape(a))
   "lookupNiels"->m.lookupNiels(shape(out),table(),count,r.text("index").toInt())
   "precomputedScalarmul"->m.precomputedScalarmul(shape(out),table(),scalar(s1))
   "directScalarmul"->value=m.directScalarmul(span("bytes"),span("baseBytes"),scalar(s1),r.text("allow").toULong(16).toLong(),r.text("shortCircuit").toULong(16).toLong()).toLong()
   "x448"->value=m.x448(span("bytes"),span("baseBytes"),span("scalarBytes")).toLong()
   "generateKey"->m.generateKey(span("bytes"),span("scalarBytes"))
   "derivePublicKey"->m.derivePublicKey(span("bytes"),span("scalarBytes"))
   "recodeWnaf"->value=m.recodeWnaf(CryptoDecaf448PointMultiplication.Controls(controls!!,1),scalar(s1),count).toLong()
   "prepareWnafTable"->m.prepareWnafTable(Array(maxOf(1,1 shl count)){p.Shape(out,prefix+it*4*n)},shape(a),count)
   "precomputeWnafs"->m.precomputeWnafs(table(),shape(a))
   "baseDoubleScalarmulNonSecret"->m.baseDoubleScalarmulNonSecret(shape(out),scalar(s1),shape(a),scalar(s2))
   "precomputedDestroy"->m.precomputedDestroy(table())
   else->error(op)
  }
  p.observe=null;m.observe=null
  return buildJsonObject {
   put("fieldAudit",buildJsonObject{put("count",writes);put("sha256",digest.digest().toHexString());put("stats",buildJsonObject{for((label,v)in stats)put(label,buildJsonObject{put("calls",v[0]);put("maxWord",hx(v[1]));put("aboveRadix",v[2])})});if(r["details"]?.jsonPrimitive?.boolean==true)put("states",JsonArray(states))})
   if(r["audit"]?.jsonPrimitive?.boolean==true||op=="recodeWnaf")put("logic",JsonArray(logic))
   for((k,v)in mapOf("a" to a,"b" to b,"out" to out,"out2" to out2,"s1" to s1,"s2" to s2,"table" to table,"gfIn" to gi,"gfOut" to go))if(v!=null)put(k,words(v))
   for((k,v)in bytes)put(k,JsonArray(v.map{JsonPrimitive(it.toInt()and 255)}))
   put("value",hx(value));if(controls!=null)put("control",JsonArray(controls.map(::JsonPrimitive)))
   if(op in listOf("scalarmul","doubleScalarmul","dualScalarmul","precomputedScalarmul","baseDoubleScalarmulNonSecret")){
    fun encoded(k:String,v:LongArray){val data=ByteArray(58){if(it==0)17 else if(it==57)34 else 165.toByte()};p.encode(CryptoByteSpan(data,1,56),shape(v));put(k,JsonArray(data.map{JsonPrimitive(it.toInt()and 255)}))}
    encoded("encodedOut",out);put("validOut",hx(p.valid(shape(out))));if(op=="dualScalarmul"){encoded("encodedOut2",out2);put("validOut2",hx(p.valid(shape(out2))))}
   }
  }
 }
 fun checkRow(row:JsonObject,index:Int){val request=row.getValue("request").jsonObject;val expected=row.getValue("expected").jsonObject;val actual=observe(request);check(expected==actual){"row$index request=$request\nexpected=$expected\nactual=$actual"}
  if(request.text("op")=="recodeWnaf"){val bounded=row.getValue("boundedOriginalPrefix").jsonObject;check(bounded.getValue("control")== (boundedControl?:actual.getValue("control"))){"bounded controls row$index"};check(bounded.getValue("logic")== (boundedLogic?:actual.getValue("logic"))){"bounded logical prefix row$index"}}
  request["literal"]?.let{check(it==JsonArray(actual.getValue("bytes").jsonArray.drop(1).dropLast(1)))}
 }
 @JvmStatic fun main(args:Array<String>){var count=0;GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{lines->lines.drop(1).forEach{checkRow(Json.parseToJsonElement(it).jsonObject,count);count++;if(count%100==0)println("PASS$count")}};println("PASS$count raw/trace/bounded rows")}
}
class CryptoDecaf448PointMultiplicationTest {
 private val lines by lazy{GZIPInputStream(javaClass.getResourceAsStream("/crypto-decaf448-point-multiplication/data.jsonl.gz")!!).bufferedReader().use{it.readLines()}}
 private val manifest by lazy{Json.parseToJsonElement(lines.first()).jsonObject}
 private val rows by lazy{lines.drop(1)}
 @org.junit.jupiter.api.TestFactory fun originalObservations()=rows.mapIndexed{i,line->org.junit.jupiter.api.DynamicTest.dynamicTest("original observation $i"){DecafMultiplicationReplay.checkRow(Json.parseToJsonElement(line).jsonObject,i)}}.stream()
 @org.junit.jupiter.api.TestFactory fun exactSourcePins()=manifest.getValue("pins").jsonObject.map{(path,hash)->org.junit.jupiter.api.DynamicTest.dynamicTest(path){val root=System.getProperty("kotdoc.root");check(MessageDigest.getInstance("SHA-256").digest(File(root,path).readBytes()).toHexString()==hash.jsonPrimitive.content)}}.stream()
 @org.junit.jupiter.api.TestFactory fun assertionDomains()=manifest.getValue("assertionProbes").jsonArray.map{entry->org.junit.jupiter.api.DynamicTest.dynamicTest(entry.toString()){
  val r=entry.jsonObject;val p=CryptoDecaf448Points(r.getValue("bits").jsonPrimitive.int,r.getValue("scalarBits").jsonPrimitive.int,r.getValue("vectorMasks32").jsonPrimitive.boolean);val m=CryptoDecaf448PointMultiplication(p);check(r.getValue("returncode").jsonPrimitive.int<0)
  org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java){val fields=Array(2){CryptoP256Words.Region(LongArray(p.limbs))};when(r.getValue("case").jsonPrimitive.int){0->m.batchInvert(fields,fields,1);1->m.batchInvert(fields,Array(2){CryptoP256Words.Region(LongArray(p.limbs))},2);else->m.precompute(m.freshTable(),p.freshShape())}}
 }}.stream()
 @org.junit.jupiter.api.Test fun completeOperationMatrix(){val expected=manifest.getValue("operations").jsonArray.map{it.jsonPrimitive.content}.toSet();check(expected.size==17);for(profile in manifest.getValue("profiles").jsonArray){val p=profile.jsonArray;val operations=rows.asSequence().map{Json.parseToJsonElement(it).jsonObject.getValue("request").jsonObject}.filter{it.getValue("bits")==p[0]&&it.getValue("scalarBits")==p[1]&&it.getValue("vectorMasks32")==p[2]}.map{it.getValue("op").jsonPrimitive.content}.toSet();check(operations==expected)}}
}
