package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import java.io.File
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import org.junit.jupiter.api.*
object NumberGenerateHistoricalBindings {
 private val paths=mapOf("tools/number-generate-reference/README.md" to "tools/number-generate-length-reference/history/README.md","tools/import-number-generate-fixtures.py" to "tools/number-generate-length-reference/history/import-number-generate-fixtures.py")
 fun sourceFile(root:String,path:String):File {
  val actual=Json.parseToJsonElement(File(root,"tools/number-generate-length-reference/HistoricalBindings.json").readText()).jsonObject.mapValues{it.value.jsonPrimitive.content}
  check(actual==paths){"historical whitelist changed"}
  return File(root,paths[path]?:path)
 }
}
class CryptoNumberGenerateLengthTest {
 private fun rows(name:String)=Json.parseToJsonElement(GZIPInputStream(javaClass.getResourceAsStream("/crypto-number-generate-length/$name.json.gz")!!).bufferedReader().use{it.readText()}).jsonArray
 @TestFactory fun originalDefinedBufferLengths()=rows("Observations").mapIndexed{i,v->DynamicTest.dynamicTest("defined/$i"){val r=v.jsonObject;Assertions.assertTrue(r.getValue("domain").jsonObject.getValue("defined").jsonPrimitive.boolean);Assertions.assertEquals(r.getValue("expected"),NumberGenerateReplay.observe(r.getValue("request").jsonObject))}}.stream()
 @TestFactory fun excludedBeforeOriginalExecution()=rows("ExcludedBounds").mapIndexed{i,v->DynamicTest.dynamicTest("excluded/$i"){val r=v.jsonObject;val d=r.getValue("domain").jsonObject;Assertions.assertFalse(d.getValue("defined").jsonPrimitive.boolean);val size=d.getValue("actual").jsonPrimitive.int;Assertions.assertTrue(d.getValue("access").jsonArray.any{it.jsonPrimitive.int !in 0..<size});Assertions.assertFalse("expected" in r)}}.stream()
 private data class Drg(val index:Int):CryptoDrg<Drg>{override fun randomBytesGenerate(length:Int):Pair<ByteArray,Drg> = error("dictionary only")}
 @TestFactory fun sourceDefinedSaferStateConsumers()=rows("PkcsDefinedRepros").mapIndexed{i,v->DynamicTest.dynamicTest("safer-state/$i"){
  val row=v.jsonObject;val r=row.getValue("request").jsonObject;val short=r.getValue("mode").jsonPrimitive.content=="short";val draws=mutableListOf<Int>()
  val ops=CryptoDrgOperations<Drg>{n,previous->val index=previous.value.index;draws+=n;if(index>=8)error("rng-budget");lazyOf(ByteArray(n+if(short)-1 else 1){7}) to lazyOf(Drg(index+1))}
  val size=r.getValue("size").jsonPrimitive.int;val n=BigInteger.ONE.shiftLeft(8*size)-BigInteger.ONE;val rsa=CryptoRsa(CryptoNumberPrime(CryptoProbabilistic{error("unused entropy")}))
  // Both PKCS15 safer operations use this accepted source blinder action;
  // their state projections do not demand the eventual sign/decrypt payload.
  val pair=CryptoPseudoRandom.withDRG(Drg(1),rsa.generateBlinderAction(lazyOf(n),ops));val result=cryptoRandomMemo<JsonElement>{JsonPrimitive(pair.second.value.index)}
  val results=List(2){try{result.value}catch(t:Throwable){buildJsonObject{put("exception",t.message?:t.javaClass.simpleName)}}}
  val actual=buildJsonObject{put("results",JsonArray(results));put("draws",JsonArray(draws.map(::JsonPrimitive)))}
  Assertions.assertEquals(row.getValue("expected"),actual)
 }}.stream()
 @Test fun unsafeHostStorageIsRejectedWithoutSourceExecution(){
  for((bits,top,odd,input)in listOf(listOf(16,null,false,ByteArray(0)),listOf(9,CryptoNumberGenerate.GenTopPolicy.SetTwoHighest,false,byteArrayOf(7)),listOf(16,null,true,byteArrayOf(7)))){
   val bytes=input as ByteArray;val before=bytes.copyOf();Assertions.assertThrows(IllegalArgumentException::class.java){CryptoNumberGenerate.generateParams(bits as Int,top as CryptoNumberGenerate.GenTopPolicy?,odd as Boolean){bytes}};Assertions.assertArrayEquals(before,bytes)
  }
 }
 @Test fun historicalCorpusIsByteIdentical(){val root=System.getProperty("kotdoc.root");Assertions.assertArrayEquals(File(root,"tools/number-generate-length-reference/history/data.jsonl.gz").readBytes(),File(root,"core/src/test/resources/crypto-number-generate/data.jsonl.gz").readBytes())}
 @TestFactory fun currentSourcePins()=Json.parseToJsonElement(File(System.getProperty("kotdoc.root"),"tools/number-generate-length-reference/CurrentPins.json").readText()).jsonObject.map{(path,value)->DynamicTest.dynamicTest(path){Assertions.assertEquals(value.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
}
