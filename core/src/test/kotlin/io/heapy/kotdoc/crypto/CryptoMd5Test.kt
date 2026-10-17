package io.heapy.kotdoc.crypto
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*
import org.junit.jupiter.api.*
class CryptoMd5Test {
 private fun text(name:String)=GZIPInputStream(javaClass.getResourceAsStream("/crypto-md5/$name.gz")!!).bufferedReader().use{it.readText()}
 private fun rows(name:String)=text(name).lineSequence().filter{it.isNotEmpty()}.toList()
 @TestFactory fun raw()=rows("NativeObservations.tsv").mapIndexed{i,s->DynamicTest.dynamicTest("raw/$i"){Md5RawReplay.observe(s)}}.stream()
 @TestFactory fun counterWrap()=rows("CounterObservations.tsv").mapIndexed{i,s->DynamicTest.dynamicTest("counter/$i"){Md5RawReplay.observe(s)}}.stream()
 @TestFactory fun aliases()=rows("AliasObservations.tsv").mapIndexed{i,s->DynamicTest.dynamicTest("alias/$i"){Md5RawReplay.observe(s)}}.stream()
 @TestFactory fun rounds()=rows("TraceObservations.tsv").mapIndexed{i,s->DynamicTest.dynamicTest("round/$i"){Md5TraceObserve.observe(s)}}.stream()
 @TestFactory fun boundaries()=rows("PrefixBoundary.tsv").mapIndexed{i,s->DynamicTest.dynamicTest("virtual/$i"){Md5BoundaryObserve.observe(s)}}.stream()
 @TestFactory fun publicOriginal()=Json.parseToJsonElement(text("HaskellObservations.json")).jsonArray.mapIndexed{i,v->DynamicTest.dynamicTest("public/$i"){val row=v.jsonObject;Assertions.assertEquals(row.getValue("expected"),Md5PublicReplay.observe(row.getValue("request").jsonObject))}}.stream()
 @TestFactory fun literalOriginal()=Json.parseToJsonElement(text("LiteralObservations.json")).jsonArray.mapIndexed{i,v->DynamicTest.dynamicTest("literal/$i"){val row=v.jsonObject;Assertions.assertEquals(row.getValue("expected"),Md5PublicReplay.observe(row.getValue("request").jsonObject))}}.stream()
 @TestFactory fun demandOriginal()=Json.parseToJsonElement(text("DemandObservations.json")).jsonArray.mapIndexed{i,v->DynamicTest.dynamicTest("demand/$i"){val row=v.jsonObject;Assertions.assertEquals(row.getValue("expected").jsonPrimitive.content,Md5DemandReplay.observe(row))}}.stream()
 @TestFactory fun originalPropertyFamilies()=Json.parseToJsonElement(text("HaskellObservations.json")).jsonArray.filter{"sampleFamily" in it.jsonObject.getValue("request").jsonObject}.mapIndexed{i,v->DynamicTest.dynamicTest("HashSpec/$i"){
  val r=v.jsonObject.getValue("request").jsonObject;val alg=CryptoMd5Hash.Algorithm.valueOf(r.getValue("algorithm").jsonPrimitive.content)
  fun bytes(v:JsonElement)=v.jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
  val chunks=r.getValue("chunks").jsonArray.map(::bytes);val all=chunks.fold(byteArrayOf()){a,b->a+b}
  if(r.getValue("sampleFamily").jsonPrimitive.content=="chunk")Assertions.assertEquals(CryptoMd5Hash.hash(alg,all),CryptoMd5Hash.hashLazy(alg,chunks))else{val end=bytes(r.getValue("end"));val n=r.getValue("prefix").jsonPrimitive.int;Assertions.assertEquals(CryptoMd5Hash.hash(alg,all+end.take(n).toByteArray()),CryptoMd5Hash.hashFinalizePrefix(CryptoMd5Hash.hashUpdates(CryptoMd5Hash.hashInit(alg),chunks),end,n))}
 }}.stream()
 @Test fun ownershipAndFailureSharing(){
  for(alg in CryptoMd5Hash.Algorithm.entries){
   val initial=CryptoMd5Hash.hashInit(alg);val before=initial.debugBytes();Assertions.assertSame(initial,CryptoMd5Hash.hashUpdates(initial,listOf(byteArrayOf(),byteArrayOf())))
   val input=ByteArray(257){it.toByte()};val updated=CryptoMd5Hash.hashUpdate(initial,input);val digest=CryptoMd5Hash.hashFinalize(updated);input.fill(0);Assertions.assertEquals(digest,CryptoMd5Hash.hashFinalize(updated));Assertions.assertArrayEquals(before,initial.debugBytes())
   val bytes=digest.bytes();val copy=CryptoMd5Hash.digestFromByteString(alg,bytes)!!;bytes.fill(0);Assertions.assertEquals(digest,copy);Assertions.assertEquals(digest.hashCode(),copy.hashCode());Assertions.assertEquals(0,digest.compareTo(copy));digest.force();initial.force()
   var calls=0;val failure=IllegalStateException("shared");val result=CryptoMd5Hash.deferHashFinalize(lazy{calls++;throw failure});repeat(2){Assertions.assertSame(failure,Assertions.assertThrows(IllegalStateException::class.java){result.value})};Assertions.assertEquals(1,calls)
  }
 }
 @TestFactory fun sourcePins()=Json.parseToJsonElement(File(System.getProperty("kotdoc.root"),"tools/md5-reference/SourcePins.json").readText()).jsonObject.map{(path,expected)->DynamicTest.dynamicTest(path){Assertions.assertEquals(expected.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
}
