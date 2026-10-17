package io.heapy.kotdoc.crypto
import kotlinx.serialization.json.*
import org.junit.jupiter.api.*
import java.io.File
import java.util.zip.GZIPInputStream
class CryptoRipemd160Test {
 private fun rows(name:String)=Json.parseToJsonElement(GZIPInputStream(javaClass.getResourceAsStream("/crypto-ripemd160/$name.json.gz")!!).bufferedReader().use{it.readText()}).jsonArray
 private fun cases(name:String,observe:(JsonObject)->JsonElement)=rows(name).mapIndexed{i,v->DynamicTest.dynamicTest("$name/$i"){val r=v.jsonObject;Assertions.assertEquals(r.getValue("expected"),observe(if(name=="CounterObservations")r else r.getValue("request").jsonObject))}}.stream()
 @TestFactory fun originalNative()=cases("NativeObservations"){Ripemd160Replay.raw(it)}
 @TestFactory fun originalTraces()=cases("TraceObservations"){Ripemd160Replay.raw(it,true)}
 @TestFactory fun originalCounters()=cases("CounterObservations",Ripemd160Replay::counter)
 @TestFactory fun originalHaskell()=cases("HaskellObservations",Ripemd160Replay::haskell)
 @TestFactory fun originalLiterals()=cases("LiteralObservations",Ripemd160Replay::haskell)
 @TestFactory fun originalDemand()=rows("DemandObservations").mapIndexed{i,v->DynamicTest.dynamicTest("demand/$i"){Assertions.assertEquals(v.jsonObject.getValue("expected").jsonPrimitive.content,Ripemd160Replay.demand(v.jsonObject))}}.stream()
 @TestFactory fun originalBorrow()=cases("BorrowObservations",Ripemd160BorrowReplay::observe)
 @TestFactory fun originalAccess()=cases("AccessObservations",Ripemd160AccessReplay::observe)
 @TestFactory fun originalChunkProperty()=rows("HaskellObservations").filter{"sampleFamily" in it.jsonObject.getValue("request").jsonObject}.mapIndexed{i,v->DynamicTest.dynamicTest("HashSpec/chunk/$i"){val r=v.jsonObject.getValue("request").jsonObject;val chunks=r.getValue("chunks").jsonArray.map{p->p.jsonArray.map{it.jsonPrimitive.int.toByte()}.toByteArray()};val a=CryptoRipemd160Hash.Algorithm.RIPEMD160;Assertions.assertEquals(CryptoRipemd160Hash.hash(a,chunks.fold(ByteArray(0)){x,y->x+y}),CryptoRipemd160Hash.hashLazy(a,chunks))}}.stream()
 @Test fun ownershipAndSharedResults(){val a=CryptoRipemd160Hash.Algorithm.RIPEMD160;val input=ByteArray(57){it.toByte()};val initial=CryptoRipemd160Hash.hashInit(a);val before=initial.debugBytes();val next=CryptoRipemd160Hash.hashUpdate(initial,input);val expected=CryptoRipemd160Hash.hashFinalize(next);input.fill(0);Assertions.assertArrayEquals(before,initial.debugBytes());Assertions.assertEquals(expected,CryptoRipemd160Hash.hashFinalize(next));val bytes=expected.bytes();bytes.fill(0);Assertions.assertEquals(expected,CryptoRipemd160Hash.hashFinalize(next));Assertions.assertSame(initial,CryptoRipemd160Hash.hashUpdate(initial,ByteArray(0)));val error=IllegalStateException("shared");var calls=0;val bad=object:Lazy<CryptoRipemd160Hash.Context>{override val value:CryptoRipemd160Hash.Context get(){calls++;throw error};override fun isInitialized()=false};val d=CryptoRipemd160Hash.deferHashFinalize(bad);repeat(2){Assertions.assertSame(error,Assertions.assertThrows(IllegalStateException::class.java){d.value})};Assertions.assertEquals(1,calls)}
 @TestFactory fun sourcePins()=Json.parseToJsonElement(File(System.getProperty("kotdoc.root"),"tools/ripemd160-reference/SourcePins.json").readText()).jsonObject.map{(path,value)->DynamicTest.dynamicTest(path){Assertions.assertEquals(value.jsonPrimitive.content,java.security.MessageDigest.getInstance("SHA-256").digest(File(System.getProperty("kotdoc.root"),path).readBytes()).toHexString())}}.stream()
}
