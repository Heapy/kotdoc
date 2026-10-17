package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import io.heapy.kotdoc.x509.*
import java.math.BigInteger
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.jupiter.api.*
class CryptoRsaPssTest {
 private fun rows(name:String)=Json.parseToJsonElement(GZIPInputStream(javaClass.getResourceAsStream("/crypto-rsa-pss/$name.json.gz")!!).bufferedReader().use{it.readText()}).jsonArray
 private fun cases(name:String,observe:(JsonObject)->JsonElement)=rows(name).mapIndexed{i,v->DynamicTest.dynamicTest("$name/$i"){val r=v.jsonObject;Assertions.assertEquals(r.getValue("expected"),observe(r.getValue("request").jsonObject))}}.stream()
 @TestFactory fun originalSource53()=cases("SourceCases",PssReplay::observe)
 @TestFactory fun ordinary()=cases("Observations",PssReplay::observe)
 @TestFactory fun pairedDemand()=cases("DemandObservations",PssDemandReplay::observe)
 @TestFactory fun genericDictionaries()=cases("DictionaryObservations",PssDictionaryReplay::observe)
 @TestFactory fun pureRandom()=cases("RandomObservations",PssRandomReplay::observe)
 @TestFactory fun pureRetry100()=cases("RandomExtraObservations",PssRandomReplay::observe)
 @TestFactory fun ioCallbacks()=cases("CallbackObservations",PssCallbackReplay::observe)
 @TestFactory fun malformedEncodings()=cases("MutationObservations",PssReplay::observe)
 @TestFactory fun digestApi()=cases("DigestObservations",PssReplay::observe)
 @TestFactory fun helpersAndVirtualBounds()=cases("BoundaryObservations",PssBoundaryReplay::observe)
 private fun key()=X509RsaPrivateKey(X509RsaPublicKey(64,BigInteger.ONE.shiftLeft(512)-BigInteger.ONE,BigInteger.ONE),BigInteger.ONE,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO)
 private fun expectedSalt7():ByteArray {val row=rows("MutationObservations").map{it.jsonObject}.first{r->val q=r.getValue("request").jsonObject;q["op"]?.jsonPrimitive?.content=="signWithSalt"&&q["size"]?.jsonPrimitive?.int==64&&q["n"]?.jsonPrimitive?.content==(BigInteger.ONE.shiftLeft(512)-BigInteger.ONE).toString()&&q["trailer"]?.jsonPrimitive?.int==188&&q["salt"]?.jsonArray?.size==20};return row.getValue("expected").jsonObject.getValue("bytes").jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()}
 @Test fun immutableInputAndOutput(){val message=byteArrayOf(1,2,3);val salt=ByteArray(20){7};val r=CryptoRsaPss.signWithSalt(salt,null,CryptoRsaPss.defaultParamsSHA1(),key(),message)as CryptoRsaPss.Result.Success;message.fill(0);salt.fill(0);val result=r.bytes();Assertions.assertArrayEquals(expectedSalt7(),result);result.fill(0);Assertions.assertArrayEquals(expectedSalt7(),r.bytes())}
 @Test fun callbackStorageIsSnapshotted(){val message=byteArrayOf(1,2,3);val salt=ByteArray(20){7};var calls=0;val r=CryptoRsaPss.sign(null,CryptoRsaPss.defaultParamsSHA1(),key(),message){n->calls++;Assertions.assertEquals(20,n);salt}as CryptoRsaPss.Result.Success;salt.fill(0);message.fill(0);Assertions.assertArrayEquals(expectedSalt7(),r.bytes());Assertions.assertEquals(1,calls)}
 @Test fun synchronousPayloadFailureShared(){val base=CryptoRsaPss.defaultParamsSHA1();val failure=IllegalStateException("shared-mask");var calls=0;val p=CryptoRsaPss.Params(base.dictionary,lazyOf(CryptoRsaPss.MaskAlgorithm{_,_->cryptoRandomMemo{calls++;throw failure}}),lazyOf(20L),lazyOf(188.toByte()));val r=CryptoRsaPss.signWithSalt(ByteArray(20){7},null,p,key(),byteArrayOf(1,2,3))as CryptoRsaPss.Result.Success;Assertions.assertEquals(0,calls);repeat(2){Assertions.assertSame(failure,Assertions.assertThrows(IllegalStateException::class.java){r.bytes()})};Assertions.assertEquals(1,calls)}
 @TestFactory fun currentSourcePins()=Json.parseToJsonElement(File(System.getProperty("kotdoc.root"),"tools/pss-reference/CurrentPins.json").readText()).jsonObject.map{(path,value)->DynamicTest.dynamicTest(path){Assertions.assertEquals(value.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
}
