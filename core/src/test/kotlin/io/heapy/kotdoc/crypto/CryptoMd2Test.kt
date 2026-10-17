package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import org.junit.jupiter.api.*
import java.io.File
import java.util.zip.GZIPInputStream
class CryptoMd2Test {
 private fun rows(name:String)=Json.parseToJsonElement(GZIPInputStream(javaClass.getResourceAsStream("/crypto-md2/$name.json.gz")!!).bufferedReader().use{it.readText()}).jsonArray
 private fun cases(name:String,observe:(JsonObject)->JsonElement)=rows(name).mapIndexed{i,v->DynamicTest.dynamicTest("$name/$i"){val r=v.jsonObject;Assertions.assertEquals(r.getValue("expected"),observe(if(name=="CounterObservations")r else r.getValue("request").jsonObject))}}.stream()
 @TestFactory fun originalNative()=cases("NativeObservations"){Md2Replay.raw(it)}
 @TestFactory fun originalTraces()=cases("TraceObservations"){Md2Replay.raw(it,true)}
 @TestFactory fun originalCounters()=cases("CounterObservations",Md2Replay::counter)
 @TestFactory fun originalHaskell()=cases("HaskellObservations",Md2Replay::haskell)
 @TestFactory fun originalLiterals()=cases("LiteralObservations",Md2Replay::haskell)
 @TestFactory fun originalDemand()=rows("DemandObservations").mapIndexed{i,v->DynamicTest.dynamicTest("demand/$i"){Assertions.assertEquals(v.jsonObject.getValue("expected").jsonPrimitive.content,Md2Replay.demand(v.jsonObject))}}.stream()
 @TestFactory fun originalBorrow()=cases("BorrowObservations",Md2BorrowReplay::observe)
 @TestFactory fun originalAccess()=cases("AccessObservations",Md2AccessReplay::observe)
 @TestFactory fun originalChunkProperty()=rows("HaskellObservations").filter{"sampleFamily" in it.jsonObject.getValue("request").jsonObject}.mapIndexed{i,v->DynamicTest.dynamicTest("HashSpec/chunk/$i"){val r=v.jsonObject.getValue("request").jsonObject;val chunks=r.getValue("chunks").jsonArray.map{p->p.jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()};val a=CryptoMd2Hash.Algorithm.MD2;Assertions.assertEquals(CryptoMd2Hash.hash(a,chunks.fold(ByteArray(0)){x,y->x+y}),CryptoMd2Hash.hashLazy(a,chunks))}}.stream()
 @Test fun ownershipAndSharedResults(){val a=CryptoMd2Hash.Algorithm.MD2;val input=ByteArray(57){it.toByte()};val initial=CryptoMd2Hash.hashInit(a);val before=initial.debugBytes();val next=CryptoMd2Hash.hashUpdate(initial,input);val expected=CryptoMd2Hash.hashFinalize(next);input.fill(0);Assertions.assertArrayEquals(before,initial.debugBytes());Assertions.assertEquals(expected,CryptoMd2Hash.hashFinalize(next));val bytes=expected.bytes();bytes.fill(0);Assertions.assertEquals(expected,CryptoMd2Hash.hashFinalize(next));Assertions.assertSame(initial,CryptoMd2Hash.hashUpdate(initial,ByteArray(0)));val error=IllegalStateException("shared");var calls=0;val bad=object:Lazy<CryptoMd2Hash.Context>{override val value:CryptoMd2Hash.Context get(){calls++;throw error};override fun isInitialized()=false};val d=CryptoMd2Hash.deferHashFinalize(bad);repeat(2){Assertions.assertSame(error,Assertions.assertThrows(IllegalStateException::class.java){d.value})};Assertions.assertEquals(1,calls)}
 @TestFactory fun sourcePins()=Json.parseToJsonElement(File(System.getProperty("kotdoc.root"),"tools/md2-reference/SourcePins.json").readText()).jsonObject.map{(path,value)->DynamicTest.dynamicTest(path){Assertions.assertEquals(value.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
}
