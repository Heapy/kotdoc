package io.heapy.kotdoc.crypto
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*
import org.junit.jupiter.api.*
class CryptoHmacTest {
 private fun text(name:String)=GZIPInputStream(javaClass.getResourceAsStream("/crypto-hmac/$name.gz")!!).bufferedReader().use{it.readText()}
 private fun rows(name:String)=Json.parseToJsonElement(text(name)).jsonArray
 private fun bytes(v:JsonElement)=v.jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
 private fun alg(name:String)=if(name.startsWith("SHAKE"))CryptoHmacAlgorithms.shake(name.substringAfter("SHAKE").substringBefore('_').toInt(),name.substringAfter('_').toInt())else CryptoHmacAlgorithms.algorithm(CryptoHmacAlgorithms.Algorithm.valueOf(name))
 @TestFactory fun originalObservations()=rows("HaskellObservations.json").mapIndexed{i,v->DynamicTest.dynamicTest("original/$i"){val r=v.jsonObject;Assertions.assertEquals(r.getValue("expected"),HmacReplay.observe(r.getValue("request").jsonObject))}}.stream()
 @TestFactory fun originalDictionary()=rows("DictionaryObservations.json").mapIndexed{i,v->DynamicTest.dynamicTest("dictionary/$i"){val r=v.jsonObject;Assertions.assertEquals(r.getValue("expected"),HmacDictionaryReplay.observe(r.getValue("request").jsonObject))}}.stream()
 @TestFactory fun originalDictionaryExtra()=rows("DictionaryExtraObservations.json").mapIndexed{i,v->DynamicTest.dynamicTest("dictionary-extra/$i"){val r=v.jsonObject;Assertions.assertEquals(r.getValue("expected"),HmacDictionaryReplay.observe(r.getValue("request").jsonObject))}}.stream()
 @TestFactory fun originalAccess()=rows("AccessObservations.json").mapIndexed{i,v->DynamicTest.dynamicTest("access/$i"){val r=v.jsonObject;Assertions.assertEquals(r.getValue("expected"),HmacDictionaryReplay.observe(r.getValue("request").jsonObject))}}.stream()
 @TestFactory fun originalUnit()=rows("UnitObservations.json").mapIndexed{i,v->DynamicTest.dynamicTest("unit/$i"){val r=v.jsonObject;Assertions.assertEquals(r.getValue("expected"),HmacDictionaryReplay.observe(r.getValue("request").jsonObject))}}.stream()
 @TestFactory fun originalBorrow()=rows("BorrowObservations.json").mapIndexed{i,v->DynamicTest.dynamicTest("borrow/$i"){val r=v.jsonObject;Assertions.assertEquals(r.getValue("expected"),HmacBorrowReplay.observe(r.getValue("request").jsonObject))}}.stream()
 @TestFactory fun originalLiterals()=rows("LiteralObservations.json").flatMapIndexed{i,v->val r=v.jsonObject;val a=alg(r.getValue("algorithm").jsonPrimitive.content);val key=bytes(r.getValue("key"));val msg=bytes(r.getValue("message"));val expected=bytes(r.getValue("expected"));listOf(DynamicTest.dynamicTest("HMACSpec/one/$i"){Assertions.assertArrayEquals(expected,CryptoHmac.hmac(a,key,msg).bytes())},DynamicTest.dynamicTest("HMACSpec/incremental/$i"){Assertions.assertArrayEquals(expected,CryptoHmac.finalize(CryptoHmac.update(CryptoHmac.initialize(a,key),msg)).bytes())})}.stream()
 @TestFactory fun originalPropertyFamilies()=rows("HaskellObservations.json").filter{"sampleFamily" in it.jsonObject.getValue("request").jsonObject}.mapIndexed{i,v->DynamicTest.dynamicTest("HMACSpec/property/$i"){
  val r=v.jsonObject.getValue("request").jsonObject;val a=alg(r.getValue("algorithm").jsonPrimitive.content);val key=bytes(r.getValue("key"));val chunks=r.getValue("chunks").jsonArray.map(::bytes);val all=chunks.fold(byteArrayOf()){a,b->a+b}
  val expected=CryptoHmac.hmac(a,key,all)
  var ctx=CryptoHmac.initialize(a,key);for(c in chunks)ctx=CryptoHmac.update(ctx,c)
  Assertions.assertEquals(expected,CryptoHmac.finalize(ctx))
 }}.stream()
 @Test fun immutableOwnershipAndConstructor(){
  for(name in CryptoHmacAlgorithms.Algorithm.entries){val a=CryptoHmacAlgorithms.algorithm(name);val key=ByteArray(a.blockSize+1){it.toByte()};val initial=CryptoHmac.initialize(a,key);val o=initial.outer.bytes();val i=initial.inner.bytes();key.fill(0)
   val message=ByteArray(257){it.toByte()};val updated=CryptoHmac.update(initial,message);val first=CryptoHmac.finalize(updated);message.fill(0);Assertions.assertEquals(first,CryptoHmac.finalize(updated));Assertions.assertArrayEquals(o,initial.outer.bytes());Assertions.assertArrayEquals(i,initial.inner.bytes());Assertions.assertSame(initial.outer,updated.outer)
   val out=first.bytes();val digest=CryptoHmac.Digest(out);val mac=CryptoHmac.Mac(digest);out.fill(0);Assertions.assertSame(digest,mac.hmacGetDigest());Assertions.assertEquals(first,mac);Assertions.assertEquals(first.hashCode(),mac.hashCode());Assertions.assertEquals(0,digest.compareTo(first.hmacGetDigest()));Assertions.assertEquals(first.bytes().toHexString(),digest.toString());Assertions.assertEquals("<HMAC>",mac.toString());digest.force()
  }
 }
 @Test fun sharedSynchronousFailureAndSuccess(){
  val failure=IllegalStateException("shared");var calls=0
  val raw=object:CryptoHmac.HashAlgorithm{override val blockSize=8;override val digestSize=4;override val contextSize=8;override fun initialize(context:ByteArray){calls++;throw failure};override fun update(context:ByteArray,bytes:ByteArray)=Unit;override fun finalize(context:ByteArray,output:ByteArray)=Unit}
  val deferred=CryptoHmac.deferInitialize(raw,lazyOf(byteArrayOf()));repeat(2){Assertions.assertSame(failure,Assertions.assertThrows(IllegalStateException::class.java){deferred.value})};Assertions.assertEquals(1,calls)
  val a=alg("SHA256");val context=CryptoHmac.deferInitialize(a,lazyOf(byteArrayOf(1)));Assertions.assertSame(context.value,context.value)
  val bytes=byteArrayOf(1,2);val digest=CryptoHmac.deferMac(lazyOf(bytes));Assertions.assertSame(digest.value,digest.value)
 }
 @TestFactory fun sourcePins()=Json.parseToJsonElement(File(System.getProperty("kotdoc.root"),"tools/hmac-reference/SourcePins.json").readText()).jsonObject.map{(path,expected)->DynamicTest.dynamicTest(path){Assertions.assertEquals(expected.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
}
