package io.heapy.kotdoc.crypto
import java.math.BigInteger
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*
internal object NumberGenerateReplay {
 private fun json(b:ByteArray)=JsonArray(b.map{JsonPrimitive(it.toInt()and 255)})
 private fun arrays(v:JsonElement)=v.jsonArray.map{row->row.jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()}
 fun observe(r:JsonObject):JsonObject {
  fun flag(k:String)=r[k]?.jsonPrimitive?.boolean?:false
  fun integer(k:String)=lazy{if(flag(k+"Bottom"))error("$k-bottom");r[k]?.jsonPrimitive?.content?.toBigInteger()?:BigInteger.ZERO}
  fun policy(k:String="top"):CryptoNumberGenerate.GenTopPolicy?=when(r[k]?.jsonPrimitive?.content){"highest"->CryptoNumberGenerate.GenTopPolicy.SetHighest;"two"->CryptoNumberGenerate.GenTopPolicy.SetTwoHighest;"bottom"->error("top-bottom");else->null}
  val op=r.getValue("op").jsonPrimitive.content
  if(op=="policy"){val p=policy()!!;return buildJsonObject{put("show",p.toString());put("equal",p==policy("other"))}}
  val requests=mutableListOf<Int>();val payloads=mutableListOf<ByteArray>();val inputs=mutableListOf<ByteArray>();val chunks=(r["oracleRandomBytes"]?:r["chunks"])?.let(::arrays)
  val random:(Int)->ByteArray={n->val index=requests.size;requests+=n;if(flag("throw")||r["throwAt"]?.jsonPrimitive?.int==index)error("rng-failure")
   val b=if(chunks==null)ByteArray(n){(r["byte"]?.jsonPrimitive?.int?:0).toByte()}else if(index<chunks.size)chunks[index].copyOf()else{if(flag("exhaust"))error("rng-script-exhausted");chunks.last().copyOf()};payloads+=b.copyOf();inputs+=b;b
  }
  val bits=lazy{if(flag("bitsBottom"))error("bits-bottom");r["bits"]?.jsonPrimitive?.int?:0}
  var value:BigInteger?=null;var exception:String?=null
  try {value=when(op){
   "params"->CryptoNumberGenerate.generateParamsDeferred(bits,lazy{policy()},lazy{if(flag("oddBottom"))error("odd-bottom");flag("odd")},random)
   "prefix"->CryptoNumberGenerate.generatePrefixDeferred(bits,random)
   "max"->CryptoNumberGenerate.generateMaxDeferred(integer("range"),random)
   "between"->CryptoNumberGenerate.generateBetweenDeferred(integer("low"),integer("high"),random)
   else->error("operation")
  }}catch(e:IllegalStateException){exception=e.message?:""}
  return buildJsonObject{put("requests",JsonArray(requests.map(::JsonPrimitive)));put("payloads",JsonArray(payloads.map(::json)));put("inputBuffers",JsonArray(inputs.map(::json)));if(exception==null)put("value",value.toString())else put("exception",exception)}
 }
 @JvmStatic fun main(args:Array<String>){var count=0;GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{lines->lines.drop(1).forEach{val row=Json.parseToJsonElement(it).jsonObject;val actual=observe(row.getValue("request").jsonObject);check(actual==row.getValue("expected")){"row$count request=${row.getValue("request")} expected=${row.getValue("expected")} actual=$actual"};count++;if(count%1000==0)println("PASS$count")}};println("PASS$count Number.Generate RNG trace/output/ownership observations")}
}

class CryptoNumberGenerateTest {
 private val lines by lazy {GZIPInputStream(javaClass.getResourceAsStream("/crypto-number-generate/data.jsonl.gz")!!).bufferedReader().use{it.readLines()}}
 private val manifest by lazy {Json.parseToJsonElement(lines.first()).jsonObject}
 @org.junit.jupiter.api.TestFactory
 fun originalValuesAndRandomEffects():java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> = lines.drop(1).mapIndexed{index,line->org.junit.jupiter.api.DynamicTest.dynamicTest("original/$index"){
  val row=Json.parseToJsonElement(line).jsonObject;org.junit.jupiter.api.Assertions.assertEquals(row.getValue("expected"),NumberGenerateReplay.observe(row.getValue("request").jsonObject),row.getValue("request").toString())
 }}.stream()
 @org.junit.jupiter.api.TestFactory
 fun originalPropertyFamilies():java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> = lines.drop(1).filter{"original-generated-property-sample" in it}.mapIndexed{index,line->org.junit.jupiter.api.DynamicTest.dynamicTest("NumberSpec/$index"){
  val row=Json.parseToJsonElement(line).jsonObject;val r=row.getValue("request").jsonObject;val actual=NumberGenerateReplay.observe(r).getValue("value").jsonPrimitive.content.toBigInteger();val family=row.getValue("family").jsonPrimitive.int;val bits=r["bits"]?.jsonPrimitive?.int?:0
  when(family){
   0->org.junit.jupiter.api.Assertions.assertTrue(actual.signum()>=0&&CryptoNumberBasic.numBits(actual)==bits&&actual.testBit(bits-1))
   1->org.junit.jupiter.api.Assertions.assertTrue(actual.signum()>=0&&CryptoNumberBasic.numBits(actual)==bits&&actual.testBit(bits-1)&&actual.testBit(bits-2))
   2->org.junit.jupiter.api.Assertions.assertTrue(actual.signum()>=0&&actual.testBit(0))
   3->org.junit.jupiter.api.Assertions.assertTrue(actual.signum()>=0&&actual<r.getValue("range").jsonPrimitive.content.toBigInteger())
   else->error("family")
  }
 }}.stream()
 @org.junit.jupiter.api.TestFactory
 fun sourcePins():java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> = manifest.getValue("sources").jsonObject.map{(path,expected)->org.junit.jupiter.api.DynamicTest.dynamicTest(path){
  org.junit.jupiter.api.Assertions.assertEquals(expected.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())
 }}.stream()
 @org.junit.jupiter.api.Test
 fun logicalBoundsDifferenceIsExplicit() {
  val rows=Json.parseToJsonElement(File(System.getProperty("kotdoc.root"),"tools/number-generate-reference/GuardObservations.json").readText()).jsonArray;org.junit.jupiter.api.Assertions.assertEquals(120,rows.size);var changed=0
  for(value in rows){val row=value.jsonObject;val bits=row.getValue("bits").jsonPrimitive.int;val outside=bits==1&&row.getValue("top").jsonPrimitive.content=="two";val after=row.getValue("after").jsonArray
   org.junit.jupiter.api.Assertions.assertEquals(17,after.first().jsonPrimitive.int);org.junit.jupiter.api.Assertions.assertEquals(if(outside)162 else 34,after.last().jsonPrimitive.int)
   if(outside){changed++;org.junit.jupiter.api.Assertions.assertEquals("1",row.getValue("value").jsonPrimitive.content)}
  };org.junit.jupiter.api.Assertions.assertEquals(8,changed)
  for(byte in 0..255){val original=byteArrayOf(byte.toByte());val output=CryptoNumberGenerate.generateParams(1,CryptoNumberGenerate.GenTopPolicy.SetTwoHighest,false){original};org.junit.jupiter.api.Assertions.assertEquals(BigInteger.ONE,output);org.junit.jupiter.api.Assertions.assertEquals(byte.toByte(),original[0])}
 }
 @org.junit.jupiter.api.Test
 fun completeMatrixAndOriginalPartition() {
  val matrix=Json.parseToJsonElement(File(System.getProperty("kotdoc.root"),"tools/number-generate-reference/SourceMatrix.json").readText()).jsonObject
  org.junit.jupiter.api.Assertions.assertEquals(4,matrix.getValue("functions").jsonArray.size);org.junit.jupiter.api.Assertions.assertEquals(2,matrix.getValue("types").jsonObject.getValue("GenTopPolicy").jsonArray.size)
  org.junit.jupiter.api.Assertions.assertEquals(37127,manifest.getValue("cases").jsonPrimitive.int);org.junit.jupiter.api.Assertions.assertEquals(4,manifest.getValue("originalFamilies").jsonPrimitive.int);org.junit.jupiter.api.Assertions.assertEquals(800,manifest.getValue("originalSamples").jsonPrimitive.int)
  org.junit.jupiter.api.Assertions.assertEquals(512,lines.drop(1).count{"\"logicalOutOfBoundsStore\":true" in it})
 }
}
