package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.jupiter.api.*
class CryptoRsaPkcs15Test {
 private fun rows(name:String)=Json.parseToJsonElement(GZIPInputStream(javaClass.getResourceAsStream("/crypto-rsa-pkcs15/$name.json.gz")!!).bufferedReader().use{it.readText()}).jsonArray
 private fun cases(name:String,observe:(JsonObject)->JsonElement)=rows(name).mapIndexed{i,v->DynamicTest.dynamicTest("$name/$i"){val r=v.jsonObject;Assertions.assertEquals(r.getValue("expected"),observe(r.getValue("request").jsonObject))}}.stream()
 @TestFactory fun originalOrdinary()=cases("Observations",Pkcs15Replay::observe)
 @TestFactory fun originalSource31()=cases("SourceCases",Pkcs15Replay::observe)
 @TestFactory fun originalDemand()=cases("DemandObservations",Pkcs15DemandReplay::observe)
 @TestFactory fun originalDictionary()=cases("DictionaryObservations",Pkcs15DictionaryReplay::observe)
 @TestFactory fun originalDefinedRandom()=cases("DefinedRandomObservations",Pkcs15RandomReplay::observe)
 @TestFactory fun originalIoCallbacks()=cases("CallbackObservations",Pkcs15CallbackReplay::observe)
 @TestFactory fun originalVirtualBounds()=cases("BoundaryObservations",Pkcs15BoundaryReplay::observe)
 @Test fun immutableInputAndOutput(){val input=byteArrayOf(0,2)+ByteArray(8){7}+byteArrayOf(0,42);val r=CryptoRsaPkcs15.unpad(input) as CryptoRsaPkcs15.Result.Success;input.fill(0);val out=r.bytes();Assertions.assertArrayEquals(byteArrayOf(42),out);out[0]=0;Assertions.assertArrayEquals(byteArrayOf(42),r.bytes())}
 @TestFactory fun currentSourcePins()=Json.parseToJsonElement(File(System.getProperty("kotdoc.root"),"tools/pkcs15-reference/CurrentPins.json").readText()).jsonObject.map{(path,value)->DynamicTest.dynamicTest(path){Assertions.assertEquals(value.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
}
