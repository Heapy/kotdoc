package io.heapy.kotdoc.crypto
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.*
import org.junit.jupiter.api.*
class CryptoSha32Test {
 private fun text(name:String)=GZIPInputStream(javaClass.getResourceAsStream("/crypto-sha32/$name.gz")!!).bufferedReader().use{it.readText()}
 private fun rows(name:String)=text(name).lineSequence().filter{it.isNotEmpty()}.toList()
 @TestFactory fun raw()=rows("NativeObservations.tsv").mapIndexed{i,s->DynamicTest.dynamicTest("raw/$i"){Sha32RawReplay.observe(s)}}.stream()
 @TestFactory fun counterWrap()=rows("CounterObservations.tsv").mapIndexed{i,s->DynamicTest.dynamicTest("counter/$i"){Sha32RawReplay.observe(s)}}.stream()
 @TestFactory fun aliases()=rows("AliasObservations.tsv").mapIndexed{i,s->DynamicTest.dynamicTest("alias/$i"){Sha32RawReplay.observe(s)}}.stream()
 @TestFactory fun rounds()=rows("TraceObservations.tsv").mapIndexed{i,s->DynamicTest.dynamicTest("round/$i"){Sha32TraceObserve.observe(s)}}.stream()
 @TestFactory fun boundaries()=rows("PrefixBoundary.tsv").mapIndexed{i,s->DynamicTest.dynamicTest("virtual/$i"){Sha32BoundaryObserve.observe(s)}}.stream()
 @TestFactory fun publicOriginal()=Json.parseToJsonElement(text("HaskellObservations.json")).jsonArray.mapIndexed{i,v->DynamicTest.dynamicTest("public/$i"){val row=v.jsonObject;Assertions.assertEquals(row.getValue("expected"),Sha32PublicReplay.observe(row.getValue("request").jsonObject))}}.stream()
 @TestFactory fun literalOriginal()=Json.parseToJsonElement(text("LiteralObservations.json")).jsonArray.mapIndexed{i,v->DynamicTest.dynamicTest("literal/$i"){val row=v.jsonObject;Assertions.assertEquals(row.getValue("expected"),Sha32PublicReplay.observe(row.getValue("request").jsonObject))}}.stream()
 @TestFactory fun demandOriginal()=Json.parseToJsonElement(text("DemandObservations.json")).jsonArray.mapIndexed{i,v->DynamicTest.dynamicTest("demand/$i"){val row=v.jsonObject;Assertions.assertEquals(row.getValue("expected").jsonPrimitive.content,Sha32DemandReplay.observe(row))}}.stream()
 @TestFactory fun originalPropertyFamilies()=Json.parseToJsonElement(text("HaskellObservations.json")).jsonArray.filter{"sampleFamily" in it.jsonObject.getValue("request").jsonObject}.mapIndexed{i,v->DynamicTest.dynamicTest("HashSpec/$i"){
  val r=v.jsonObject.getValue("request").jsonObject;val alg=CryptoSha32Hash.Algorithm.valueOf(r.getValue("algorithm").jsonPrimitive.content)
  fun bytes(v:JsonElement)=v.jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()
  val chunks=r.getValue("chunks").jsonArray.map(::bytes);val all=chunks.fold(byteArrayOf()){a,b->a+b}
  if(r.getValue("sampleFamily").jsonPrimitive.content=="chunk")Assertions.assertEquals(CryptoSha32Hash.hash(alg,all),CryptoSha32Hash.hashLazy(alg,chunks))else{val end=bytes(r.getValue("end"));val n=r.getValue("prefix").jsonPrimitive.int;Assertions.assertEquals(CryptoSha32Hash.hash(alg,all+end.take(n).toByteArray()),CryptoSha32Hash.hashFinalizePrefix(CryptoSha32Hash.hashUpdates(CryptoSha32Hash.hashInit(alg),chunks),end,n))}
 }}.stream()
 @Test fun ownershipAndFailureSharing(){
  for(alg in CryptoSha32Hash.Algorithm.entries){
   val initial=CryptoSha32Hash.hashInit(alg);val before=initial.debugBytes();Assertions.assertSame(initial,CryptoSha32Hash.hashUpdates(initial,listOf(byteArrayOf(),byteArrayOf())))
   val input=ByteArray(257){it.toByte()};val updated=CryptoSha32Hash.hashUpdate(initial,input);val digest=CryptoSha32Hash.hashFinalize(updated);input.fill(0);Assertions.assertEquals(digest,CryptoSha32Hash.hashFinalize(updated));Assertions.assertArrayEquals(before,initial.debugBytes())
   val bytes=digest.bytes();val copy=CryptoSha32Hash.digestFromByteString(alg,bytes)!!;bytes.fill(0);Assertions.assertEquals(digest,copy);Assertions.assertEquals(digest.hashCode(),copy.hashCode());Assertions.assertEquals(0,digest.compareTo(copy));digest.force();initial.force()
   var calls=0;val failure=IllegalStateException("shared");val result=CryptoSha32Hash.deferHashFinalize(lazy{calls++;throw failure});repeat(2){Assertions.assertSame(failure,Assertions.assertThrows(IllegalStateException::class.java){result.value})};Assertions.assertEquals(1,calls)
  }
 }
 @TestFactory fun sourcePins()=Json.parseToJsonElement(File(System.getProperty("kotdoc.root"),"tools/sha32-reference/SourcePins.json").readText()).jsonObject.map{(path,expected)->DynamicTest.dynamicTest(path){Assertions.assertEquals(expected.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
}
