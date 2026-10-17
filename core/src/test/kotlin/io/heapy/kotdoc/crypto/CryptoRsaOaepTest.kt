package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import org.junit.jupiter.api.*
import java.io.File
import java.util.zip.GZIPInputStream
class CryptoRsaOaepTest {
 private fun rows(name:String)=Json.parseToJsonElement(GZIPInputStream(javaClass.getResourceAsStream("/crypto-rsa-oaep/$name.json.gz")!!).bufferedReader().use{it.readText()}).jsonArray
 private fun cases(name:String,observe:(JsonObject)->JsonObject)=rows(name).mapIndexed{i,v->DynamicTest.dynamicTest("$name/$i"){val r=v.jsonObject;Assertions.assertEquals(r["expected"],observe(r.getValue("request").jsonObject))}}.stream()
 @TestFactory fun ordinary()=cases("Observations",OaepReplay::observe)
 @TestFactory fun demand()=cases("DemandObservations",OaepDemandReplay::observe)
 @TestFactory fun pairedErrors()=cases("DemandExtraObservations",OaepDemandReplay::observe)
 @TestFactory fun pureRng()=cases("RandomObservations",OaepRandomReplay::observe)
 @TestFactory fun pureRngRetry()=cases("RandomExtraObservations",OaepRandomReplay::observe)
 @TestFactory fun callbackRng()=cases("CallbackObservations",OaepCallbackReplay::observe)
 @TestFactory fun callbackRngRetry()=cases("CallbackExtraObservations",OaepCallbackReplay::observe)
 @TestFactory fun virtualBoundaries()=cases("BoundaryObservations",OaepBoundaryReplay::observe)
 @TestFactory fun genericAccess()=cases("AccessObservations",MgfAccessReplay::observe)
 @Test fun ownedInputSnapshotsAndSharedResult(){
  val hash=CryptoHmacAlgorithms.algorithm(CryptoHmacAlgorithms.Algorithm.SHA1)
  val key=io.heapy.kotdoc.x509.X509RsaPublicKey(64,java.math.BigInteger.ONE.shiftLeft(512)-java.math.BigInteger.ONE,java.math.BigInteger.ONE)
  val seed=ByteArray(20){it.toByte()};val message=byteArrayOf(4,5,6);val params=CryptoRsaOaep.defaultParams(hash)
  val expected=(CryptoRsaOaep.encryptWithSeed(seed,params,key,message)as CryptoRsaOaep.Result.Success).bytes()
  val result=CryptoRsaOaep.encryptWithSeed(seed,params,key,message)as CryptoRsaOaep.Result.Success
  seed.fill(0);message.fill(0);Assertions.assertArrayEquals(expected,result.bytes());val exported=result.bytes();exported.fill(0);Assertions.assertArrayEquals(expected,result.bytes())
  val failure=IllegalStateException("shared");var calls=0
  val deferred=CryptoMaskGen.mgf1Deferred(hash,object:Lazy<ByteArray>{override val value:ByteArray get(){calls++;throw failure};override fun isInitialized()=false},lazyOf(4))
  repeat(2){Assertions.assertSame(failure,Assertions.assertThrows(IllegalStateException::class.java){deferred.value})};Assertions.assertEquals(1,calls)
 }
 @TestFactory fun sourcePins()=Json.parseToJsonElement(File(System.getProperty("kotdoc.root"),"tools/rsa-oaep-reference/SourcePins.json").readText()).jsonObject.map{(path,value)->DynamicTest.dynamicTest(path){Assertions.assertEquals(value.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
}
